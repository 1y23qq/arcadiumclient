import argparse
import datetime
import hashlib
import json
import os
import platform
from pathlib import Path
import queue
import re
import subprocess
import tempfile
import threading
import time
import uuid


class AccountService:
    def __init__(self, executable, directory):
        environment = dict(os.environ, ARCADIUM_DATA_DIR=str(directory), ARCADIUM_DISABLE_AUTOUPDATE="1")
        environment["ARCADIUM_DIAGNOSTIC_KEYCHAIN_ACCOUNT"] = hashlib.sha256(str(directory / "auth.dat").encode("utf-8")).hexdigest()
        flags={"creationflags":subprocess.CREATE_NO_WINDOW} if platform.system()=="Windows" else {}
        self.process = subprocess.Popen([str(executable), "--account-service"], env=environment,
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                        text=True, encoding="utf-8", **flags)
        self.responses = queue.Queue()
        self.next_id = 0
        threading.Thread(target=self.read, daemon=True).start()
        if os.environ.get("ARCADIUM_HELPER_DIAGNOSTICS") == "1":
            threading.Thread(target=self.read_status, daemon=True).start()

    def read(self):
        for line in self.process.stdout:
            try:
                self.responses.put(json.loads(line))
            except json.JSONDecodeError:
                continue
        self.responses.put(None)

    def read_status(self):
        try:
            for line in self.process.stderr:
                match = re.fullmatch(r"ARCADIUM_KEYCHAIN_STATUS (copy|add|update) (-?\d+)\s*", line)
                if match:
                    print(json.dumps({"nativeKeychain": match[1], "status": int(match[2]),
                                      "helper": self.process.pid}), flush=True)
        except (OSError, ValueError):
            pass

    def request(self, operation, **values):
        self.next_id += 1
        self.process.stdin.write(json.dumps({"id": self.next_id, "op": operation, **values}) + "\n")
        self.process.stdin.flush()
        response = self.responses.get(timeout=20)
        if response is None:
            raise AssertionError(f"Account helper request failed: {operation} (process closed)")
        if response.get("id") != self.next_id:
            raise AssertionError(f"Account helper request failed: {operation} (response ID mismatch)")
        if not response.get("ok"):
            allowed = {
                "Account is no longer saved", "Could not open the protected account store",
                "Sign in with Microsoft before using an offline account", "Could not lock credentials",
                "Session has expired. Sign in again before switching accounts", "Invalid account",
                "An account with that name is already saved", "Account is missing",
            }
            message = response.get("error")
            reason = message if isinstance(message, str) and message in allowed else "Unrecognized account error"
            raise AssertionError(f"Account helper request failed: {operation} ({reason})")
        return response["result"]

    def expect_error(self, operation, expected, **values):
        self.next_id += 1
        self.process.stdin.write(json.dumps({"id": self.next_id, "op": operation, **values}) + "\n")
        self.process.stdin.flush()
        response = self.responses.get(timeout=20)
        assert response is not None and response.get("id") == self.next_id
        assert response.get("ok") is False
        assert response.get("error") == expected, "Unexpected account error"

    def close(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write(json.dumps({"id": 999, "op": "shutdown"}) + "\n")
                self.process.stdin.flush()
                self.process.stdin.close()
                self.process.wait(timeout=10)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait(timeout=10)
        self.process.stdout.close()
        self.process.stderr.close()


def require_metadata_only(value):
    text = json.dumps(value)
    if any(secret in text for secret in ["access_token", "refresh_token", "fixture-access", "fixture-refresh"]):
        raise AssertionError("Account list exposes credentials")


def fixture_snapshot(service, stage):
    value = service.request("list")
    require_metadata_only(value)
    if os.environ.get("ARCADIUM_HELPER_DIAGNOSTICS") == "1":
        names = {"ArcadiumFixture", "ArcadiumTestOne", "ArcadiumTestTwo"}
        users = []
        default = None
        for account in value["users"]:
            name = account["username"] if account["username"] in names else "unexpected account"
            users.append({"fixture": name, "kind": account.get("kind")})
            if account["id"] == value["default_user"]:
                default = name
        print(json.dumps({"stage": stage, "users": users, "default": default}), flush=True)
    return value


def account_smoke(executable, directory, concurrent=True, post_write_delay=0, on_failure=None):
    directory.mkdir()
    identifier = str(uuid.uuid4())
    expiry = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1)).isoformat()
    seed = {"users": {identifier: {
        "id": identifier, "username": "ArcadiumFixture", "kind": "microsoft",
        "access_token": "fixture-access", "refresh_token": "fixture-refresh", "expires": expiry,
    }}, "default_user": identifier}
    auth_file = directory / "auth.dat"
    raw=json.dumps(seed).encode("utf-8")
    if platform.system()=="Windows":
        import ctypes
        class Blob(ctypes.Structure):
            _fields_=[("size",ctypes.c_ulong),("data",ctypes.POINTER(ctypes.c_ubyte))]
        storage=(ctypes.c_ubyte*len(raw)).from_buffer_copy(raw)
        incoming=Blob(len(raw),storage); outgoing=Blob()
        if not ctypes.windll.crypt32.CryptProtectData(ctypes.byref(incoming),None,None,None,None,1,ctypes.byref(outgoing)):
            raise ctypes.WinError()
        try: raw=ctypes.string_at(outgoing.data,outgoing.size)
        finally:
            ctypes.windll.kernel32.LocalFree.argtypes=[ctypes.c_void_p]
            ctypes.windll.kernel32.LocalFree(outgoing.data)
    auth_file.write_bytes(raw)
    services = []
    try:
        first = AccountService(executable, directory)
        services.append(first)
        first_list = fixture_snapshot(first, "first service startup")
        require_metadata_only(first_list)
        assert len(first_list["users"]) == 1
        second = AccountService(executable, directory) if concurrent else first
        if concurrent:
            services.append(second)
        require_metadata_only(fixture_snapshot(second, "second service startup"))
        one = first.request("offline", username="ArcadiumTestOne")
        if os.environ.get("ARCADIUM_HELPER_DIAGNOSTICS") == "1":
            fixture_snapshot(first, "first service after first account add")
            fixture_snapshot(second, "second service after first account add")
        two = second.request("offline", username="ArcadiumTestTwo")
        if post_write_delay:
            time.sleep(post_write_delay)
        if os.environ.get("ARCADIUM_HELPER_DIAGNOSTICS") == "1":
            fixture_snapshot(first, "first service after second account add")
            fixture_snapshot(second, "second service after second account add")
        require_metadata_only(one)
        require_metadata_only(two)
        selected = first.request("switch", account=one["id"])
        assert selected["username"] == "ArcadiumTestOne" and selected["kind"] == "offline"
        assert selected["access_token"] != "fixture-access"
        assert "refresh_token" not in selected
        if platform.system() == "Darwin" and concurrent:
            for _ in range(16):
                for service, account in [(first, one), (second, two)]:
                    selected = service.request("switch", account=account["id"])
                    assert selected["kind"] == "offline" and "refresh_token" not in selected
                    for observer in [first, second]:
                        snapshot = observer.request("list")
                        require_metadata_only(snapshot)
                        assert snapshot["default_user"] == account["id"]
            first.request("switch", account=one["id"])
        current = second.request("list")
        require_metadata_only(current)
        assert len(current["users"]) == 3 and current["default_user"] == one["id"]
        second.request("remove", account=two["id"])
        current = first.request("list")
        assert {user["id"] for user in current["users"]} == {identifier, one["id"]}
        first.close()
        second.close()
        reopened = AccountService(executable, directory)
        services.append(reopened)
        current = reopened.request("list")
        require_metadata_only(current)
        assert current["default_user"] == one["id"]
        reopened.request("remove", account=one["id"])
        reopened.request("remove", account=identifier)
        assert not reopened.request("list")["users"]
        protected = auth_file.read_bytes()
        if platform.system()=="Darwin": assert protected.startswith(b"ARCADIUM_KEYCHAIN_V1\n")
        else: assert not protected.startswith(b"{")
        assert b"fixture-access" not in protected and b"fixture-refresh" not in protected
    except Exception:
        if on_failure:
            try:
                on_failure(services, directory)
            except Exception as error:
                print(json.dumps({"diagnostic": "failure inspection", "reason": type(error).__name__}), flush=True)
        raise
    finally:
        for service in services:
            service.close()
        marker = auth_file.read_bytes()
        if marker.startswith(b"ARCADIUM_KEYCHAIN_V1\n"):
            account = marker.split(b"\n", 1)[1].decode("ascii")
            prefix = hashlib.sha256(str(directory / "auth.dat").encode("utf-8")).hexdigest()
            owned = account == prefix or (re.fullmatch(r"[a-f0-9]{96}", account) and account.startswith(prefix))
            if owned:
                subprocess.run(["/usr/bin/security", "delete-generic-password", "-s",
                                "io.github.arcadiumclient.launcher.credentials", "-a", account],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return ["headless account helper", "credential-free account lists", "offline add and switch",
            "concurrent account stores", "account removal", "persisted selection", "protected credential storage"]


def cracked_and_session_smoke(executable, directory):
    directory.mkdir()
    services = []
    try:
        service = AccountService(executable, directory)
        services.append(service)
        assert service.request("list")["users"] == []
        offline = service.request("offline", username="OfflineFixture")
        require_metadata_only(offline)
        identifier = bytearray(hashlib.md5(b"OfflinePlayer:OfflineFixture").digest())
        identifier[6] = (identifier[6] & 15) | 48
        identifier[8] = (identifier[8] & 63) | 128
        assert offline["id"] == str(uuid.UUID(bytes=bytes(identifier)))
        selected = service.request("switch", account=offline["id"])
        assert selected["access_token"] == "0" and selected["kind"] == "offline"
        assert "refresh_token" not in selected
        service.expect_error("session", "Enter a valid Minecraft access token", access_token="fixture\rprivate-token")
        current = service.request("list")
        require_metadata_only(current)
        assert len(current["users"]) == 1 and current["default_user"] == offline["id"]
        service.close()
        reopened = AccountService(executable, directory)
        services.append(reopened)
        assert reopened.request("switch", account=offline["id"])["username"] == "OfflineFixture"
        reopened.request("remove", account=offline["id"])
        assert reopened.request("list")["users"] == []
        protected = (directory / "auth.dat").read_bytes()
        assert b"private-token" not in protected
    finally:
        for service in services:
            service.close()
        auth_file = directory / "auth.dat"
        if auth_file.is_file():
            marker = auth_file.read_bytes()
            if marker.startswith(b"ARCADIUM_KEYCHAIN_V1\n"):
                account = marker.split(b"\n", 1)[1].decode("ascii")
                prefix = hashlib.sha256(str(auth_file).encode("utf-8")).hexdigest()
                owned = account == prefix or (re.fullmatch(r"[a-f0-9]{96}", account) and account.startswith(prefix))
                if owned:
                    subprocess.run(["/usr/bin/security", "delete-generic-password", "-s",
                                    "io.github.arcadiumclient.launcher.credentials", "-a", account],
                                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return ["first-run Cracked account without Microsoft", "vanilla offline UUID and legacy token",
            "Session token validation without credential output", "persisted Cracked switching and removal"]


def discord_smoke(executable, directory):
    lease = str(uuid.uuid4())
    owner_file = directory / "discord-owner.json"
    owner_file.write_text(lease, encoding="utf-8")
    payload = {"owner_file": str(owner_file), "lease": lease, "games": []}
    result = subprocess.run([str(executable), "--discord-game"],
                            input=json.dumps(payload) + "\nGO\n", text=True, encoding="utf-8",
                            capture_output=True, timeout=15)
    assert result.returncode == 0 and result.stdout.strip() == "READY"
    invalid = subprocess.run([str(executable), "--discord-game"], input="invalid\n",
                             text=True, capture_output=True, timeout=15)
    assert invalid.returncode != 0
    return ["headless Discord helper", "READY and GO handoff", "invalid handoff rejection"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--executable", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    executable = args.executable.resolve()
    with tempfile.TemporaryDirectory(prefix="arcadium-native-helpers-") as temporary:
        directory = Path(temporary)
        checks = account_smoke(executable, directory / "profile")
        checks += cracked_and_session_smoke(executable, directory / "cracked-profile")
        checks += discord_smoke(executable, directory)
    result = {"passed": True, "checks": checks, "accounts": "isolated fixtures only"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("Verified native account and Discord helper startup.", flush=True)


if __name__ == "__main__":
    main()
