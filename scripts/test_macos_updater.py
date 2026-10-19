import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "packages/arcadium_app/src/updater/macos_apply.sh"
SOURCE = r'''
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
int main(void) {
    const char *ready = getenv("ARCADIUM_UPDATE_BOOT_READY");
    if (ready) {
        if (FAIL) return 23;
        FILE *file = fopen(ready, "w");
        if (!file) return 24;
        fputs("ready", file); fclose(file);
    }
    sleep(12);
    return 0;
}
'''


def app(path, version, fail):
    contents = path / "Contents"
    (contents / "MacOS").mkdir(parents=True)
    metadata = {"CFBundleIdentifier": "io.github.arcadiumclient.launcher",
                "CFBundleExecutable": "Arcadium", "CFBundleShortVersionString": version,
                "CFBundleVersion": version, "CFBundlePackageType": "APPL"}
    (contents / "Info.plist").write_bytes(plistlib.dumps(metadata))
    source = path.parent / f"source-{version}.c"
    source.write_text(SOURCE)
    subprocess.run(["clang", str(source), f"-DFAIL={int(fail)}", "-o", str(contents / "MacOS/Arcadium")], check=True)
    subprocess.run(["codesign", "--force", "--sign", "-", str(path)], check=True)


def verify(fail):
    with tempfile.TemporaryDirectory(prefix="arcadium-helper-") as temporary:
        root = Path(temporary)
        install = root / "Applications with spaces"
        install.mkdir()
        target = install / "Arcadium Client.app"
        staged = root / "staged.app"
        work = root / "workspace-123"
        work.mkdir()
        app(target, "1.0.0", False)
        app(staged, "1.0.1", fail)
        parent = subprocess.Popen(["/bin/sleep", "60"])
        arch = subprocess.check_output(["uname", "-m"], text=True).strip()
        helper = subprocess.Popen(["/bin/sh", str(HELPER), str(parent.pid), str(staged), str(target), str(work), "1.0.1", arch])
        try:
            deadline = time.monotonic() + 20
            while not (work / "installer-ready").exists():
                if helper.poll() is not None or time.monotonic() >= deadline:
                    raise AssertionError((work / "updater.log").read_text())
                time.sleep(.1)
            assert plistlib.loads((target / "Contents/Info.plist").read_bytes())["CFBundleVersion"] == "1.0.0"
            parent.terminate()
            parent.wait()
            code = helper.wait(timeout=25)
            if code != (5 if fail else 0):
                raise AssertionError((work / "updater.log").read_text())
            assert plistlib.loads((target / "Contents/Info.plist").read_bytes())["CFBundleVersion"] == ("1.0.0" if fail else "1.0.1")
            assert not list(install.glob(".Arcadium-backup-*"))
            print("Verified", "rollback" if fail else "successful update", flush=True)
        finally:
            if parent.poll() is None:
                parent.terminate()
                parent.wait()


if __name__ == "__main__":
    verify(False)
    verify(True)
