from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import time
import tomllib
import uuid

ROOT = Path(__file__).resolve().parents[1]
CRT = ("concrt140.dll", "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll",
       "msvcp140_atomic_wait.dll", "msvcp140_codecvt_ids.dll", "vccorlib140.dll",
       "vcruntime140.dll", "vcruntime140_1.dll", "vcruntime140_threads.dll")


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def hidden() -> int:
    return subprocess.CREATE_NO_WINDOW


def wait_for(condition, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = condition()
        if value:
            return value
        time.sleep(.002)
    raise TimeoutError("The isolated update fixture did not reach the expected state")


class ReadLock:
    def __init__(self, path: Path):
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                      ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p]
        kernel.CreateFileW.restype = wintypes.HANDLE
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        self.kernel = kernel
        self.handle = kernel.CreateFileW(str(path), 0x80000000, 1, None, 3, 0x80, None)
        if self.handle == ctypes.c_void_p(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())

    def close(self):
        if self.handle is not None:
            self.kernel.CloseHandle(self.handle)
            self.handle = None


def manifest(stage: Path, target: Path, declared_bytes: int | None = None, incomplete=False):
    files = sorted((path for path in stage.rglob("*") if path.is_file()), key=lambda path: str(path))
    size = sum(path.stat().st_size for path in files) if declared_bytes is None else declared_bytes
    lines = [f'!define ARCADIUM_PAYLOAD_BYTES "{size}"',
             f'!define ARCADIUM_PAYLOAD_KIB {(size + 1023) // 1024}',
             "!macro ArcadiumManagedPayloadFiles action"]
    for path in files:
        name = str(path.relative_to(stage))
        length = path.stat().st_size + (1 if incomplete and name == "Arcadium.exe" else 0)
        lines.append(f'!insertmacro ${{action}} "{name}" "{length}"')
    lines.extend(["!macroend", "!macro ArcadiumManagedUpdateFiles action"])
    lines.extend(f'!insertmacro ${{action}} "{path.relative_to(stage)}"' for path in files)
    lines.extend(['!insertmacro ${action} "Uninstall.exe"', "!macroend",
                  "!macro ArcadiumManagedUpdateDirectories action"])
    directories = sorted((path for path in stage.rglob("*") if path.is_dir()),
                         key=lambda path: len(path.parts), reverse=True)
    lines.extend(f'!insertmacro ${{action}} "{path.relative_to(stage)}"' for path in directories)
    lines.append("!macroend")
    target.write_text("\n".join(lines) + "\n", encoding="utf-8-sig")


def fixture(directory: Path, makensis: Path, kind: str):
    stage = directory / "source-stage"
    target = directory / "installed app"
    stage.mkdir(parents=True)
    target.mkdir()
    payload = {"Arcadium.exe": b"MZ-new-fixture-payload\n", "LICENSE": b"new license\n",
               "NOTICE.md": b"new notices\n", "README.md": b"new readme\n",
               "distribution-lock.json": b'{"minecraftVersion":"1.8.9"}\n',
               "distribution-lock-1.7.10.json": b'{"minecraftVersion":"1.7.10"}\n',
               "components/a-primary.jar": b"new primary component\n",
               "components/native-1.7.10.jar": b"new native component\n",
               "licenses/fixture.txt": b"fixture license\n"}
    payload.update({name: ("fixture " + name).encode() for name in CRT})
    if kind == "extraction_io":
        payload["Arcadium.exe"] = b"MZ" + bytes(64 * 1024 * 1024)
    if kind == "restore_failure":
        payload.update({f"licenses/tail/{index:04}.txt": b"fixture\n" for index in range(600)})
    old = {"Arcadium.exe": b"MZ-old-fixture-launcher\n", "Uninstall.exe": b"old uninstaller\n",
           "components/a-primary.jar": b"old primary component\n", "LICENSE": b"old license\n",
           "distribution-lock.json": b"old distribution lock\n", "userdata/keep.txt": b"untouched user sentinel\n"}
    if kind == "manual_install":
        old = {"userdata/keep.txt": old["userdata/keep.txt"]}
    for base, entries in ((stage, payload), (target, old)):
        for name, raw in entries.items():
            path = base / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
    include = directory / "managed.nsh"
    manifest(stage, include, declared_bytes=1 << 40 if kind == "low_space" else None,
             incomplete=kind == "incomplete_payload")
    installer = directory / "installer.exe"
    version = tomllib.loads((ROOT / "Cargo.toml").read_text())["workspace"]["package"]["version"]
    command = [str(makensis), f"/DARCADIUM_VERSION={version}", f"/DSTAGEDIR={stage}",
               f"/DOUTPUT={installer}", f"/DUPDATEFILES={include}",
               f"/DICON={ROOT / 'packages/arcadium_app/icons/icon.ico'}", str(ROOT / "scripts/windows-installer.nsi")]
    result = subprocess.run(command, capture_output=True, text=True, errors="replace")
    (directory / "compile.log").write_text(result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError("Fixture installer compilation failed: " + str(directory / "compile.log"))
    return installer, target, payload, old


def run_manual_case(root: Path, makensis: Path):
    directory = root / "manual_install"
    directory.mkdir()
    installer, target, payload, old = fixture(directory, makensis, "manual_install")
    command = subprocess.list2cmdline([str(installer), "/S", "/TESTINSTALL"]) + " /D=" + str(target)
    installed = subprocess.run(command, executable=installer, cwd=directory,
                               creationflags=hidden(), timeout=30)
    if installed.returncode != 0:
        raise AssertionError("The manual fresh installation failed")
    for name, raw in {**payload, **old}.items():
        if (target / name).read_bytes() != raw:
            raise AssertionError("The manual installer changed or lost a file: " + name)
    if not (target / "Uninstall.exe").read_bytes().startswith(b"MZ"):
        raise AssertionError("The manual installer did not commit its prepared uninstaller")
    if list(target.glob(".arcadium-update-*")):
        raise AssertionError("The manual installer retained a completed transaction")
    result = {"case": "manual_install", "passed": True, "filesVerified": len(payload),
              "userdataUntouched": True, "directory": str(directory)}
    (directory / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    return result


def run_case(root: Path, makensis: Path, kind: str):
    directory = root / kind
    directory.mkdir()
    installer, target, payload, old = fixture(directory, makensis, kind)
    ready = directory / "ready"
    environment = os.environ.copy()
    environment["ARCADIUM_UPDATE_READY"] = str(ready)
    environment["ARCADIUM_DATA_DIR"] = str(directory / "synthetic-profile")
    environment["ARCADIUM_DISABLE_AUTOUPDATE"] = "1"
    parent = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(120)"], creationflags=hidden())
    arguments = [str(installer), "/S", "/TESTINSTALL", "/AUTOUPDATE", f"/UPDATEPID={parent.pid}"]
    command = subprocess.list2cmdline(arguments) + " /D=" + str(target)
    locks = []
    watcher_failure = []
    stop_watcher = threading.Event()
    watch_result = threading.Event()
    if kind == "locked_old_file":
        locks.append(ReadLock(target / "components/a-primary.jar"))
    if kind == "extraction_io":
        def block_extraction():
            try:
                while not stop_watcher.is_set():
                    for transaction in target.glob(".arcadium-update-*"):
                        stage = transaction / "payload"
                        if (stage / "Arcadium.exe").exists():
                            (stage / "concrt140.dll").mkdir()
                            watch_result.set()
                            return
                    time.sleep(.001)
            except Exception as error:
                watcher_failure.append(str(error))
        threading.Thread(target=block_extraction, daemon=True).start()
    process = subprocess.Popen(command, executable=installer, cwd=directory, env=environment, creationflags=hidden())
    handoff = False
    transaction = None
    try:
        wait_for(lambda: ready.exists() or process.poll() is not None, 60)
        handoff = ready.exists()
        for name, raw in old.items():
            if (target / name).read_bytes() != raw:
                raise AssertionError("Live files changed before the parent exited: " + name)
        if handoff:
            transaction = next(target.glob(".arcadium-update-*"))
            if kind in ("partial_commit", "restore_failure"):
                locks.append(ReadLock(transaction / "payload/Arcadium.exe"))
            if kind == "restore_failure":
                def block_restore():
                    try:
                        changed = target / "components/a-primary.jar"
                        while not stop_watcher.is_set():
                            try:
                                if changed.read_bytes() == payload["components/a-primary.jar"]:
                                    if (target / "Arcadium.exe").read_bytes() != old["Arcadium.exe"]:
                                        raise AssertionError("The old executable was removed before the final binary swap")
                                    locks.append(ReadLock(changed))
                                    watch_result.set()
                                    return
                            except (FileNotFoundError, PermissionError):
                                pass
                            time.sleep(.001)
                    except Exception as error:
                        watcher_failure.append(str(error))
                threading.Thread(target=block_restore, daemon=True).start()
            parent.terminate()
            parent.wait(timeout=10)
        code = process.wait(timeout=90)
        if watcher_failure:
            raise AssertionError(watcher_failure)
        expected_handoff = kind not in ("low_space", "incomplete_payload", "extraction_io")
        if handoff != expected_handoff:
            raise AssertionError(f"Unexpected handoff for {kind}: {handoff}, exit {code}")
        if kind == "success":
            if code != 0:
                raise AssertionError("The successful update returned " + str(code))
            for name, raw in payload.items():
                if (target / name).read_bytes() != raw:
                    raise AssertionError("Installed payload mismatch: " + name)
            if not (target / "Uninstall.exe").read_bytes().startswith(b"MZ"):
                raise AssertionError("The staged native uninstaller was not committed")
            if list(target.glob(".arcadium-update-*")):
                raise AssertionError("A completed transaction was not cleaned")
        elif kind == "restore_failure":
            if code == 0 or not watch_result.is_set() or transaction is None:
                raise AssertionError("The intended restore failure was not exercised")
            for name in ("Arcadium.exe", "components/a-primary.jar"):
                if (transaction / "backup" / name).read_bytes() != old[name]:
                    raise AssertionError("A failed restore lost its preserved backup: " + name)
            if not (transaction / "failure.txt").exists():
                raise AssertionError("A failed restore did not preserve its recovery record")
        else:
            if code == 0:
                raise AssertionError("The injected failure was reported as success")
            for name, raw in old.items():
                if (target / name).read_bytes() != raw:
                    raise AssertionError("Rollback changed the original file: " + name)
            for name in payload.keys() - old.keys():
                if (target / name).exists():
                    raise AssertionError("Rollback left a newly introduced live file: " + name)
            if not handoff and parent.poll() is not None:
                raise AssertionError("The original parent exited before a failed staging handoff")
            if kind == "extraction_io" and not watch_result.is_set():
                raise AssertionError("The intended extraction write failure was not exercised")
        if (target / "userdata/keep.txt").read_bytes() != old["userdata/keep.txt"]:
            raise AssertionError("User data was changed")
        result = {"case": kind, "passed": True, "handoff": handoff, "exitCode": code,
                  "oldParentKeptOnPreflightFailure": not handoff and parent.poll() is None,
                  "userdataUntouched": True, "directory": str(directory)}
        if kind == "restore_failure":
            result["oldExecutableKeptDuringPayloadCommit"] = watch_result.is_set()
        (directory / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        return result
    finally:
        stop_watcher.set()
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=10)
        if parent.poll() is None:
            parent.terminate()
            parent.wait(timeout=10)
        for lock in locks:
            lock.close()


def start_account_helper(executable: Path, environment: dict[str, str], log: Path):
    errors = log.open("wb")
    process = subprocess.Popen([str(executable), "--account-service"], env=environment,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors,
                               creationflags=hidden())
    errors.close()
    try:
        process.stdin.write(b'{"id":1,"op":"list"}\n')
        process.stdin.flush()
        responses = queue.Queue()
        threading.Thread(target=lambda: responses.put(process.stdout.readline()), daemon=True).start()
        response = json.loads(responses.get(timeout=30))
        if response.get("ok") is not True:
            raise AssertionError("The isolated native account helper did not initialize")
        return process
    except BaseException:
        if process.poll() is None:
            process.terminate()
        process.wait(timeout=10)
        raise


def stop_account_helper(process):
    if process.poll() is None:
        process.stdin.write(b'{"id":2,"op":"shutdown"}\n')
        process.stdin.flush()
        process.stdin.close()
        if process.wait(timeout=15) != 0:
            raise AssertionError("The isolated native helper did not shut down cleanly")


def run_full_fixture(root: Path, makensis: Path, old_installer: Path, stage: Path, package_manifest: Path,
                     shipping_installer: Path | None = None):
    directory = root / "full-update"
    directory.mkdir()
    target = directory / "installed app"
    metadata = json.loads(package_manifest.read_text(encoding="utf-8-sig"))
    for entry in metadata["files"]:
        if digest(stage / entry["path"]) != entry["sha256"]:
            raise AssertionError("The supplied full source stage was not verified: " + entry["path"])
    version = metadata["launcherVersion"]
    if shipping_installer is not None:
        installer = shipping_installer
        if digest(installer) != metadata["installerSha256"]:
            raise AssertionError("The supplied shipping installer does not match its package manifest")
    else:
        include = directory / "managed.nsh"
        manifest(stage, include)
        installer = directory / "transaction-installer.exe"
        command = [str(makensis), f"/DARCADIUM_VERSION={version}", f"/DSTAGEDIR={stage}",
                   f"/DOUTPUT={installer}", f"/DUPDATEFILES={include}",
                   f"/DICON={ROOT / 'packages/arcadium_app/icons/icon.ico'}", str(ROOT / "scripts/windows-installer.nsi")]
        compiled = subprocess.run(command, capture_output=True, text=True, errors="replace")
        (directory / "compile.log").write_text(compiled.stdout + compiled.stderr)
        if compiled.returncode:
            raise RuntimeError("Full fixture compilation failed: " + str(directory / "compile.log"))
    install_command = subprocess.list2cmdline([str(old_installer), "/S", "/TESTINSTALL"]) + " /D=" + str(target)
    previous = subprocess.run(install_command, executable=old_installer, cwd=directory,
                              creationflags=hidden(), timeout=180)
    if previous.returncode != 0 or not (target / "Arcadium.exe").is_file():
        raise AssertionError("The isolated old installer did not complete")
    sentinel = target / "unmanaged-user-data/sentinel.txt"
    sentinel.parent.mkdir()
    sentinel.write_bytes(b"user data must remain untouched\n")
    old_files = {str(path.relative_to(target)): digest(path) for path in target.rglob("*") if path.is_file()}
    ready = directory / "ready"
    environment = os.environ.copy()
    environment.update({"ARCADIUM_UPDATE_READY": str(ready),
                        "ARCADIUM_DATA_DIR": str(directory / "synthetic-profile"),
                        "ARCADIUM_DISABLE_AUTOUPDATE": "1"})
    old_helper = start_account_helper(target / "Arcadium.exe", environment, directory / "old-helper.log")
    arguments = [str(installer), "/S", "/TESTINSTALL", "/AUTOUPDATE", f"/UPDATEPID={old_helper.pid}"]
    update_command = subprocess.list2cmdline(arguments) + " /D=" + str(target)
    started = time.monotonic()
    updater = subprocess.Popen(update_command, executable=installer, cwd=directory,
                               env=environment, creationflags=hidden())
    new_helper = None
    try:
        wait_for(lambda: ready.exists() or updater.poll() is not None, 60)
        if not ready.is_file() or old_helper.poll() is not None:
            raise AssertionError("The full payload was not staged while the original launcher remained running")
        handoff_seconds = time.monotonic() - started
        for name, checksum in old_files.items():
            if digest(target / name) != checksum:
                raise AssertionError("The full update changed live files before handoff: " + name)
        stop_account_helper(old_helper)
        if updater.wait(timeout=180) != 0:
            raise AssertionError("The full automatic transaction failed")
        for entry in metadata["files"]:
            if digest(target / entry["path"]) != entry["sha256"]:
                raise AssertionError("The full installed payload is incorrect: " + entry["path"])
        if sentinel.read_bytes() != b"user data must remain untouched\n":
            raise AssertionError("The full update changed unmanaged user data")
        if list(target.glob(".arcadium-update-*")):
            raise AssertionError("The full transaction retained an unnecessary backup")
        new_helper = start_account_helper(target / "Arcadium.exe", environment, directory / "new-helper.log")
        stop_account_helper(new_helper)
        result = {"passed": True, "case": "full-real-payload", "newVersion": version,
                  "filesVerified": len(metadata["files"]), "handoffSeconds": round(handoff_seconds, 3),
                  "payloadBytes": sum(path.stat().st_size for path in stage.rglob("*") if path.is_file()),
                  "oldExecutableKeptRunningUntilStageComplete": True, "oldFilesUnchangedBeforeHandoff": True,
                  "newNativeHelperStarted": True, "userdataUntouched": True,
                  "installerSha256": digest(installer), "installer": str(installer),
                  "verifiedShippingInstaller": shipping_installer is not None, "directory": str(directory)}
        (directory / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        return result
    finally:
        if updater.poll() is None:
            updater.terminate()
            updater.wait(timeout=15)
        if old_helper.poll() is None:
            stop_account_helper(old_helper)
        if new_helper is not None and new_helper.poll() is None:
            new_helper.terminate()
            new_helper.wait(timeout=15)


def main():
    parser = argparse.ArgumentParser(description="Exercise the actual NSIS update transaction in fresh isolated directories.")
    parser.add_argument("--makensis", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--case", action="append", choices=("manual_install", "success", "low_space", "incomplete_payload",
                        "extraction_io", "locked_old_file", "partial_commit", "restore_failure"))
    parser.add_argument("--old-installer", type=Path)
    parser.add_argument("--stage", type=Path)
    parser.add_argument("--package-manifest", type=Path)
    parser.add_argument("--installer", type=Path, help="Use the exact shipping installer verified by the package manifest.")
    parser.add_argument("--full-only", action="store_true")
    args = parser.parse_args()
    if os.name != "nt":
        raise RuntimeError("These installer integration checks require Windows")
    compiler = args.makensis or Path(shutil.which("makensis.exe") or "")
    if not compiler.is_file():
        raise RuntimeError("Pass --makensis pointing to NSIS3 makensis.exe")
    output = (args.output or ROOT / "build/installer-transactions" / uuid.uuid4().hex).resolve()
    if output.exists():
        raise RuntimeError("The test output directory must be new")
    output.mkdir(parents=True)
    cases = [] if args.full_only else args.case or ["manual_install", "success", "low_space", "incomplete_payload", "extraction_io",
                                                   "locked_old_file", "partial_commit", "restore_failure"]
    results = []
    for case in cases:
        result = run_manual_case(output, compiler.resolve()) if case == "manual_install" else run_case(output, compiler.resolve(), case)
        results.append(result)
        print(json.dumps(result), flush=True)
    if any((args.old_installer, args.stage, args.package_manifest, args.full_only, args.installer)):
        if not all((args.old_installer, args.stage, args.package_manifest)):
            raise RuntimeError("The full fixture requires --old-installer, --stage and --package-manifest")
        result = run_full_fixture(output, compiler.resolve(), args.old_installer.resolve(),
                                  args.stage.resolve(), args.package_manifest.resolve(),
                                  args.installer.resolve() if args.installer else None)
        results.append(result)
        print(json.dumps(result), flush=True)
    (output / "report.json").write_text(json.dumps({"passed": True, "cases": results}, indent=2) + "\n")


if __name__ == "__main__":
    main()
