from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import threading
import time
import uuid


def varint(value: int) -> bytes:
    output = bytearray()
    while value > 127:
        output.append((value & 127) | 128)
        value >>= 7
    output.append(value)
    return bytes(output)


def string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return varint(len(raw)) + raw


def read_int(stream) -> int:
    value = 0
    for shift in range(0, 35, 7):
        raw = stream.read(1)
        if not raw:
            raise EOFError()
        value |= (raw[0] & 127) << shift
        if raw[0] < 128:
            return value
    raise ValueError("Oversized protocol integer")


def read_string(stream) -> str:
    size = read_int(stream)
    if size > 65536:
        raise ValueError("Oversized test string")
    raw = stream.read(size)
    if len(raw) != size:
        raise EOFError()
    return raw.decode("utf-8")


def read_packet(stream):
    import io
    size = read_int(stream)
    if size > 1024 * 1024:
        raise ValueError("Oversized test packet")
    raw = stream.read(size)
    if len(raw) != size:
        raise EOFError()
    data = io.BytesIO(raw)
    return read_int(data), data


class LocalServer:
    def __init__(self, label: str):
        self.label = label
        self.socket = socket.socket()
        self.socket.bind(("127.0.0.1", 0))
        self.socket.listen()
        self.socket.settimeout(0.5)
        self.port = self.socket.getsockname()[1]
        self.stopping = threading.Event()
        self.sessions = []
        self.connections = []
        self.errors = []
        self.thread = threading.Thread(target=self.accept, daemon=True)
        self.thread.start()

    def accept(self):
        while not self.stopping.is_set():
            try:
                client, _ = self.socket.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            self.connections.append(client)
            threading.Thread(target=self.client, args=(client,), daemon=True).start()

    def client(self, client):
        lock = threading.Lock()
        session = None
        ended = threading.Event()

        def send(identity, body=b""):
            raw = varint(identity) + body
            with lock:
                client.sendall(varint(len(raw)) + raw)

        try:
            client.settimeout(30)
            client.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            with client, client.makefile("rb") as stream:
                identity, handshake = read_packet(stream)
                assert identity == 0
                read_int(handshake)
                read_string(handshake)
                handshake.read(2)
                state = read_int(handshake)
                if state == 1:
                    read_packet(stream)
                    send(0, string(json.dumps({"version": {"name": "1.8.9", "protocol": 47}, "players": {"online": 0, "max": 8}, "description": {"text": self.label}})))
                    identity, ping = read_packet(stream)
                    if identity == 1:
                        send(1, ping.read())
                    return
                assert state == 2
                identity, login = read_packet(stream)
                assert identity == 0 and read_string(login) == "ArcadiumTest"
                session = {"label": self.label, "commands": [], "closed": False, "joinedAt": time.monotonic()}
                self.sessions.append(session)
                profile = uuid.UUID(bytes=hashlib.md5(b"OfflinePlayer:ArcadiumTest").digest(), version=3)
                send(2, string(str(profile)) + string("ArcadiumTest"))
                time.sleep(0.05)
                send(1, struct.pack(">iBbBB", 100 + len(self.sessions), 0, 0, 0, 8) + string("flat") + b"\0")
                send(0x38, varint(0) + varint(1) + profile.bytes + string("ArcadiumTest") + varint(0) + varint(0) + varint(0) + b"\0")
                send(5, struct.pack(">q", 64 << 26))
                section = b"\x10\0" * 256 + b"\0" * (8192 - 512) + b"\0" * 2048 + b"\xff" * 2048 + b"\1" * 256
                for x in range(-1, 2):
                    for z in range(-1, 2):
                        send(0x21, struct.pack(">iiBH", x, z, 1, 1 << 4) + varint(len(section)) + section)
                time.sleep(0.05)
                send(8, struct.pack(">dddffB", 0.5, 65, 0.5, 0, 0, 0))

                def chat():
                    index = 0
                    while not ended.wait(0.3) and not self.stopping.is_set():
                        try:
                            send(2, string(json.dumps({"text": f"LIVE_{self.label}_{index}"})) + b"\0")
                            index += 1
                        except OSError:
                            return

                threading.Thread(target=chat, daemon=True).start()
                while not self.stopping.is_set():
                    identity, data = read_packet(stream)
                    if identity == 1:
                        session["commands"].append(read_string(data))
        except (EOFError, ConnectionError, OSError):
            pass
        except Exception as error:
            self.errors.append(repr(error))
        finally:
            ended.set()
            if session is not None:
                session["closed"] = True
                session["closedAt"] = time.monotonic()

    def close(self):
        self.stopping.set()
        self.socket.close()
        for connection in self.connections:
            try:
                connection.shutdown(socket.SHUT_RDWR)
                connection.close()
            except OSError:
                pass
        self.thread.join(timeout=2)


def run(profile: Path, command_file: Path, qa_jar: Path):
    profile = profile.resolve()
    if not (profile / ".arcadium-verification-profile").is_file():
        raise ValueError("Use an isolated offline verification profile")
    command = json.loads(command_file.read_text(encoding="utf-8-sig"))
    for flag, expected in (("--username", "ArcadiumTest"), ("--accessToken", "0")):
        if command[command.index(flag) + 1] != expected:
            raise ValueError("Only the synthetic offline session is allowed")
    game = Path(command[command.index("--gameDir") + 1]).resolve()
    mods = Path(next(value.split("=", 1)[1] for value in command if value.startswith("-Dfabric.modsFolder="))).resolve()
    if not game.is_relative_to(profile) or not mods.is_relative_to(profile):
        raise ValueError("The synthetic command targets another profile")
    output = profile / ("release051-" + str(time.time_ns()))
    output.mkdir()
    target = mods / "ArcadiumReleaseQa.jar"
    if target.exists():
        raise ValueError("Remove the previous owned QA mod before another run")
    shutil.copyfile(qa_jar, target)
    servers = [LocalServer("A"), LocalServer("B")]
    command[1:1] = [f"-Darcadium.release051.server{index}=127.0.0.1:{server.port}" for index, server in enumerate(servers)] + [f"-Darcadium.release051.output={output / 'result.json'}"]
    process = None
    try:
        with (output / "console.log").open("wb") as log:
            process = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT,
                                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            (output / "process.json").write_text(json.dumps({"pid": process.pid}), encoding="utf-8")
            try:
                process.wait(timeout=180)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=15)
                raise RuntimeError(f"Native QA did not complete: {output}")
        deadline = time.monotonic() + 2
        while any(not session["closed"] for server in servers for session in server.sessions) and time.monotonic() < deadline:
            time.sleep(0.01)
        report = json.loads((output / "result.json").read_text(encoding="utf-8")) if (output / "result.json").exists() else {"passed": False, "error": "The game exited before its QA report"}
        report["exitCode"] = process.returncode
        report["servers"] = [server.sessions for server in servers]
        report["serverErrors"] = [server.errors for server in servers]
        expected = ["/login Release051Dummy"]
        report["passwordSentUnchanged"] = bool(servers[0].sessions) and servers[0].sessions[0]["commands"] == expected
        report["oldSessionsClosed"] = len(servers[0].sessions) >= 2 and len(servers[1].sessions) >= 1 and all(session["closed"] for server in servers for session in server.sessions)
        report["passed"] = report["passed"] and process.returncode == 0 and report["passwordSentUnchanged"] and report["oldSessionsClosed"] and not any(server.errors for server in servers)
        (output / "run.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"passed": report["passed"], "evidence": str(output), "error": report.get("error")}), flush=True)
        return report["passed"]
    finally:
        for server in servers:
            server.close()
        if process is not None and process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
        target.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test legacy session switching, private command rendering, native input and background frame limits on loopback servers.")
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--command", type=Path, required=True)
    parser.add_argument("--qa-jar", type=Path, required=True)
    args = parser.parse_args()
    raise SystemExit(0 if run(args.profile, args.command, args.qa_jar) else 1)
