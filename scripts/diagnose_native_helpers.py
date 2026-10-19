import argparse
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import time

from smoke_native_helpers import AccountService, account_smoke, discord_smoke, fixture_snapshot


def compile_probes(directory):
    if platform.system() != "Darwin":
        return None, None
    sources = Path(__file__).resolve().parent / "helper_diagnostics"
    probe = directory / "keychain-probe"
    shim = directory / "keychain-status.dylib"
    subprocess.run(["/usr/bin/xcrun", "swiftc", str(sources / "keychain_probe.swift"), "-o", str(probe)],
                   check=True, capture_output=True, timeout=120)
    subprocess.run(["/usr/bin/clang", "-dynamiclib", str(sources / "keychain_status.c"),
                    "-framework", "Security", "-framework", "CoreFoundation", "-o", str(shim)],
                   check=True, capture_output=True, timeout=60)
    subprocess.run(["/usr/bin/codesign", "--force", "--sign", "-", str(shim)],
                   check=True, capture_output=True, timeout=30)
    return probe, shim


def read_only_snapshot(service, label):
    try:
        value = fixture_snapshot(service, label)
        print(json.dumps({"readOnly": label, "passed": True, "users": len(value["users"])}), flush=True)
    except AssertionError as error:
        message = str(error)
        reason = message if message.startswith("Account helper request failed:") else "Fixture assertion failed"
        print(json.dumps({"readOnly": label, "passed": False, "reason": reason}), flush=True)
    except Exception as error:
        print(json.dumps({"readOnly": label, "passed": False, "reason": type(error).__name__}), flush=True)


def failure_inspection(executable, probe, services, directory):
    marker = directory / "auth.dat"
    prefix = b"ARCADIUM_KEYCHAIN_V1\n"
    print(json.dumps({"readOnly": "credential marker", "validMarker": marker.read_bytes().startswith(prefix)}), flush=True)
    if probe:
        result = subprocess.run([str(probe), str(marker)], capture_output=True, text=True, timeout=15)
        try:
            value = json.loads(result.stdout)
            if set(value) <= {"probe", "status", "users"}:
                print(json.dumps(value), flush=True)
        except json.JSONDecodeError:
            print(json.dumps({"probe": "keychain", "reason": "No structured result"}), flush=True)
    for delay in [.25, 1.0]:
        time.sleep(delay)
        for index, service in enumerate(services):
            read_only_snapshot(service, f"existing helper {index + 1} after {delay}s")
    fresh = AccountService(executable, directory)
    try:
        read_only_snapshot(fresh, "fresh helper after failure")
    finally:
        fresh.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--executable", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    executable = args.executable.resolve()
    results = []
    with tempfile.TemporaryDirectory(prefix="arcadium-helper-diagnostic-") as temporary:
        root = Path(temporary)
        try:
            probe, shim = compile_probes(root)
        except Exception as error:
            probe, shim = None, None
            print(json.dumps({"case": "keychain probe compilation", "passed": False,
                              "reason": type(error).__name__}), flush=True)
        cases = [
            ("concurrent-original", True, False, 0, False),
            ("concurrent-snapshots", True, True, 0, False),
            ("concurrent-delay-250ms", True, False, .25, False),
            ("concurrent-delay-1s", True, False, 1.0, False),
            ("single-service", False, True, 0, False),
        ]
        if shim:
            cases.append(("concurrent-native-statuses", True, True, 0, True))
        for name, concurrent, snapshots, delay, injected in cases:
            if snapshots:
                os.environ["ARCADIUM_HELPER_DIAGNOSTICS"] = "1"
            else:
                os.environ.pop("ARCADIUM_HELPER_DIAGNOSTICS", None)
            if injected:
                os.environ["DYLD_INSERT_LIBRARIES"] = str(shim)
            else:
                os.environ.pop("DYLD_INSERT_LIBRARIES", None)
            try:
                checks = account_smoke(executable, root / name, concurrent=concurrent, post_write_delay=delay,
                                       on_failure=lambda services, directory: failure_inspection(executable, probe, services, directory))
                result = {"case": name, "passed": True, "checks": checks}
            except AssertionError as error:
                message = str(error)
                reason = message if message.startswith("Account helper request failed:") else "Fixture assertion failed"
                result = {"case": name, "passed": False, "reason": reason}
            except Exception as error:
                result = {"case": name, "passed": False, "reason": type(error).__name__}
            results.append(result)
            print(json.dumps(result), flush=True)
        os.environ.pop("DYLD_INSERT_LIBRARIES", None)
        try:
            checks = discord_smoke(executable, root)
            result = {"case": "discord-handoff", "passed": True, "checks": checks}
        except Exception as error:
            result = {"case": "discord-handoff", "passed": False, "reason": type(error).__name__}
        results.append(result)
        print(json.dumps(result), flush=True)
    passed = all(result["passed"] for result in results)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"passed": passed, "cases": results,
                                      "accounts": "isolated fixtures only"}, indent=2) + "\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
