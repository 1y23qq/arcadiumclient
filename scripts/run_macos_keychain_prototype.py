import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import tempfile
import threading
import uuid


class NativeStore:
    def __init__(self, executable, protected=False):
        command = [str(executable)] + (["protected"] if protected else [])
        self.process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, text=True, encoding="utf-8")
        self.results = queue.Queue()
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        for line in self.process.stdout:
            self.results.put(json.loads(line))
        self.results.put(None)

    def request(self, operation, account, epoch=0):
        if operation not in {"SAVE", "READ", "DELETE"} or not re.fullmatch(r"[a-f0-9]{64}", account):
            raise ValueError("Invalid fixture request")
        self.process.stdin.write(f"{operation} {account} {epoch}\n")
        self.process.stdin.flush()
        result = self.results.get(timeout=15)
        if result is None:
            raise RuntimeError("Native prototype closed unexpectedly")
        return result

    def close(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write("QUIT\n")
                self.process.stdin.flush()
                self.process.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait(timeout=5)
        self.process.stdin.close()
        self.process.stdout.close()


def reference(prefix, generation):
    return hashlib.sha256(f"{prefix}:{generation}".encode("ascii")).hexdigest()


def read_marker(path):
    value = path.read_text(encoding="ascii")
    if not value.startswith("ARCADIUM_KEYCHAIN_PROTO\n"):
        raise ValueError("Invalid fixture marker")
    account = value.split("\n", 1)[1]
    if not re.fullmatch(r"[a-f0-9]{64}", account):
        raise ValueError("Invalid fixture reference")
    return account


def publish_marker(path, account):
    temporary = path.with_suffix(".pending")
    temporary.write_text("ARCADIUM_KEYCHAIN_PROTO\n" + account, encoding="ascii")
    os.replace(temporary, path)


def verify_read(stores, account, epoch):
    statuses = [store.request("READ", account, epoch) for store in stores]
    return all(result["status"] == 0 and result["matches"] for result in statuses), statuses


def run_case(executable, directory, iterations, immutable, protected=False):
    prefix = hashlib.sha256(uuid.uuid4().bytes).hexdigest()
    marker = directory / "auth.dat"
    stores = [NativeStore(executable, protected), NativeStore(executable, protected)]
    owned = set()
    case = "immutable-records" if immutable else "mutable-record"
    if protected:
        case = "data-protection"
    records = []
    initial = reference(prefix, 0)
    current = initial
    passed = False
    try:
        owned.add(initial)
        result = stores[0].request("SAVE", initial, 0)
        if result["status"] != 0:
            return {"case": case, "passed": False, "initialStatus": result["status"]}
        publish_marker(marker, initial)
        good, status = verify_read(stores, initial, 0)
        if not good:
            return {"case": case, "passed": False, "initialReads": status}
        for generation in range(1, iterations + 1):
            writer = stores[(generation + 1) % 2]
            next_account = reference(prefix, generation) if immutable else initial
            owned.add(next_account)
            saved = writer.request("SAVE", next_account, generation)
            if saved["status"] != 0:
                records.append({"generation": generation, "saveStatus": saved["status"]})
                break
            publish_marker(marker, next_account)
            deleted = None
            if immutable and next_account != current:
                deleted = writer.request("DELETE", current)["status"]
                if deleted != 0:
                    records.append({"generation": generation, "deleteStatus": deleted})
                    break
                owned.remove(current)
            current = next_account
            good, reads = verify_read(stores, read_marker(marker), generation)
            records.append({"generation": generation, "updated": saved["updated"],
                            "deletedPrevious": deleted, "readStatuses": [item["status"] for item in reads],
                            "matches": [item["matches"] for item in reads]})
            if not good:
                break
        else:
            passed = True
        failed_commit_preserved = None
        if immutable and passed:
            unpublished = reference(prefix, iterations + 1)
            owned.add(unpublished)
            saved = stores[0].request("SAVE", unpublished, iterations + 1)
            pending = directory / "failure.pending"
            pending.write_text("ARCADIUM_KEYCHAIN_PROTO\n" + unpublished, encoding="ascii")
            try:
                os.replace(pending, directory / "missing-parent/auth.dat")
            except FileNotFoundError:
                good, _ = verify_read(stores, read_marker(marker), iterations)
                failed_commit_preserved = good and read_marker(marker) == current and saved["status"] == 0
            passed = passed and failed_commit_preserved is True
        return {"case": case, "passed": passed, "iterations": len(records), "records": records,
                "failedCommitPreservedOldData": failed_commit_preserved}
    finally:
        for store in stores:
            store.close()
        cleanup = NativeStore(executable, protected)
        try:
            for account in owned:
                status = cleanup.request("DELETE", account)["status"]
                if status not in {0, -25300, -34018}:
                    print(json.dumps({"case": case, "cleanupStatus": status}), flush=True)
        finally:
            cleanup.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--iterations", type=int, default=32)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not 20 <= args.iterations <= 100:
        raise ValueError("Expected 20 to 100 fixture updates")
    results = []
    with tempfile.TemporaryDirectory(prefix="arcadium-keychain-prototype-") as temporary:
        root = Path(temporary)
        executable = root / "keychain-versioned"
        source = Path(__file__).resolve().parent / "helper_diagnostics/keychain_versioned.c"
        subprocess.run(["/usr/bin/clang", str(source), "-framework", "Security", "-framework", "CoreFoundation",
                        "-o", str(executable)], check=True, timeout=60)
        for name, immutable, protected in [("mutable", False, False), ("immutable", True, False),
                                          ("data-protection", False, True)]:
            directory = root / name
            directory.mkdir()
            try:
                result = run_case(executable, directory, args.iterations, immutable, protected)
            except Exception as error:
                result = {"case": name, "passed": False, "reason": type(error).__name__}
            results.append(result)
            print(json.dumps(result), flush=True)
    passed = any(result["case"] == "immutable-records" and result["passed"] for result in results)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"passed": passed, "cases": results,
                                      "accounts": "isolated fixtures only"}, indent=2) + "\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
