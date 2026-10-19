import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import time


def smoke(app):
    with tempfile.TemporaryDirectory(prefix="arcadium-launcher-smoke-") as temporary:
        root = Path(temporary)
        ready = root / "ready"
        log_path = root / "launcher.log"
        environment = dict(os.environ, ARCADIUM_DATA_DIR=str(root / "profile"),
                           ARCADIUM_DISABLE_AUTOUPDATE="1", ARCADIUM_UPDATE_BOOT_READY=str(ready))
        with log_path.open("wb") as log:
            process = subprocess.Popen([str(app / "Contents/MacOS/Arcadium")], env=environment,
                                       stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 60
                while not ready.exists():
                    if process.poll() is not None or time.monotonic() >= deadline:
                        raise RuntimeError(log_path.read_text(errors="replace"))
                    time.sleep(.2)
                time.sleep(3)
                if process.poll() is not None:
                    raise RuntimeError(log_path.read_text(errors="replace"))
                print("Verified native macOS launcher window startup.", flush=True)
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--app", type=Path, required=True)
    smoke(parser.parse_args().app.resolve())
