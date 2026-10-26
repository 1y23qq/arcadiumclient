"""Measure isolated legacy-client startup using rendered-title QA markers."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "packages/arcadium_common/assets/distribution"
MARKER = ".arcadium-startup-benchmark"


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def copy(source: Path, target: Path, immutable: bool = False) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        return
    if immutable:
        try:
            os.link(source, target)
            return
        except OSError:
            pass
    shutil.copy2(source, target)


def prepare(game: str, profile: Path, java_home: Path, native_dependencies: Path | None = None,
            component_directory: Path = ASSETS) -> list[str]:
    if profile.exists() and not (profile / MARKER).is_file():
        raise RuntimeError("Use a new directory or a marked startup benchmark profile")
    if (profile / "command.json").is_file():
        return json.loads((profile / "command.json").read_text(encoding="utf-8"))
    profile.mkdir(parents=True, exist_ok=True)
    (profile / MARKER).touch()
    mods = profile / "mods"
    mods.mkdir(exist_ok=True)
    if game == "1.8.9":
        source = Path("C:/Temp/Arcadium051Qa/clusters/Arcadium 1.8.9/mods")
        reference = ROOT / "build/release052/native-external-command.json"
        command = json.loads(reference.read_text(encoding="utf-8"))
        shared = Path(command[command.index("--gameDir") + 1])
        for item in source.glob("*.jar"):
            if "ReleaseQa" not in item.name and "Fixture" not in item.name:
                copy(item, mods / item.name, immutable=not item.name.startswith("Arcadium"))
        qa = max((ROOT / "client-runtime/build/libs").glob("*-release-qa.jar"), key=lambda p: p.stat().st_mtime)
        copy(qa, mods / "ArcadiumStartupQa.jar")
        command = [arg for arg in command if not arg.startswith(("-Darcadium.release", "-Dfabric.modsFolder="))]
        for option, value in [("--gameDir", str(profile)), ("--username", "ArcadiumTest"), ("--accessToken", "0")]:
            command[command.index(option) + 1] = value
        old_compat = next(arg for arg in command if "ArcadiumForgeCompat-1.8.9.jar" in arg and ";" in arg)
        command[command.index(old_compat)] = ";".join(
            str(mods / "ArcadiumForgeCompat-1.8.9.jar") if Path(entry).name == "ArcadiumForgeCompat-1.8.9.jar" else entry
            for entry in old_compat.split(";"))
        command.insert(1, f"-Dfabric.modsFolder={mods}")
    else:
        shared = Path("C:/Temp/ArcadiumForge042")
        reference = shared / "launch.json"
        data = json.loads(reference.read_text(encoding="utf-8"))
        command = [str(java_home / "bin/java.exe"), *data["javaArguments"]]
        dependencies = native_dependencies or ROOT / "distribution/forge-1.7.10.dependencies.json"
        from prepare_forge1710 import verified_download
        for item in json.loads(dependencies.read_text(encoding="utf-8"))["mods"]:
            source = ROOT / "build/forge1710/cache/mods" / item["file"]
            verified_download(item, source)
            copy(source, mods / source.name, immutable=True)
        copy(component_directory / "ArcadiumForge-1.7.10.jar", mods / "ArcadiumForge-1.7.10.jar")
        copy(component_directory / "ArcadiumBootstrap-1.7.10.jar", profile / "ArcadiumBootstrap-1.7.10.jar")
        qa = max((ROOT / "client-forge1710/build/libs").glob("ArcadiumForgeQa-*.jar"), key=lambda p: p.stat().st_mtime)
        copy(qa, mods / "ArcadiumForgeQa.jar")
        command[command.index("-cp") + 1] = str(profile / "ArcadiumBootstrap-1.7.10.jar") + os.pathsep + command[command.index("-cp") + 1]
        command = [arg.replace(str(shared), str(profile)).replace(str(shared).replace("/", "\\"), str(profile)) for arg in command]
        command[command.index("--gameDir") + 1] = str(profile)
        command.insert(1, f"-Darcadium.userGameDir={profile}")
        command.insert(1, "-Darcadium.qa.startupOnly=true")
        (profile / ".arcadium-forge-probe").touch()
    for name in ["config", "oneconfig", "profiles", "arcadium", "angelica", "falsepattern"]:
        if (shared / name).is_dir():
            shutil.copytree(shared / name, profile / name, dirs_exist_ok=True)
    for name in ["options.txt", "optionsnf.txt"]:
        if (shared / name).is_file():
            copy(shared / name, profile / name)
    (profile / "home").mkdir(exist_ok=True)
    common = [f"-Duser.home={profile / 'home'}", "-Darcadium.usageEndpoint=", "-Darcadium.presenceEndpoint=",
              "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=9", "-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=9"]
    command[1:1] = common
    write(profile / "command.json", command)
    write(profile / "inputs.json", {"game": game, "isolated": True, "synthetic": True, "files": [
        {"name": p.name, "sha256": hashlib.file_digest(p.open("rb"), "sha256").hexdigest()} for p in sorted(mods.glob("*.jar"))]})
    return command


def run(profile: Path, command: list[str], name: str, record_jfr: bool) -> dict:
    if not (profile / MARKER).is_file():
        raise RuntimeError("The benchmark profile is not marked")
    output = ROOT / "build/optimization055" / profile.name
    output.mkdir(parents=True, exist_ok=True)
    result = output / f"{name}.json"
    if result.exists():
        raise RuntimeError("Refusing to overwrite benchmark evidence")
    command = [*command]
    command.insert(1, f"-Darcadium.startupQa.output={result}")
    if record_jfr:
        command.insert(1, f"-XX:StartFlightRecording=filename={(profile / (name + '.jfr')).as_posix()},settings=profile,dumponexit=true")
    write(output / f"{name}-command.json", command)
    started = time.perf_counter()
    with (output / f"{name}.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=profile, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        write(output / f"{name}-process.json", {"pid": process.pid, "profile": str(profile), "owned": True})
        last_progress = started
        try:
            while process.poll() is None and not result.is_file():
                now = time.perf_counter()
                if now - started > 300:
                    raise TimeoutError("The rendered title did not become ready in five minutes")
                if now - last_progress > 15:
                    print(f"{profile.name}/{name}: waiting for title ({now - started:.0f}s)", flush=True)
                    last_progress = now
                time.sleep(.25)
            ready_ms = (time.perf_counter() - started) * 1000
            try:
                process.wait(timeout=35)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=15)
            if not result.is_file():
                raise RuntimeError(f"The game exited before rendering the title: {output / (name + '.log')}")
            value = json.loads(result.read_text(encoding="utf-8"))
            if not value.get("passed"):
                raise RuntimeError("Startup QA failed")
            value.update(wallReadyMillis=ready_ms, exitCode=process.returncode, recordedJfr=record_jfr)
            recording = profile / (name + ".jfr")
            if record_jfr and recording.is_file():
                shutil.copy2(recording, output / recording.name)
            write(result, value)
            print(f"{profile.name}/{name}: title ready at {value['uptimeMillis']} ms JVM uptime", flush=True)
            return value
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=15)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game", choices=["1.8.9", "1.7.10"], required=True)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, default=Path.home() / "AppData/Local/Temp/arcadium-jdk25/jdk-25.0.4.1+1")
    parser.add_argument("--native-dependencies", type=Path)
    parser.add_argument("--component-directory", type=Path, default=ASSETS)
    parser.add_argument("--prefix", default="baseline")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--jfr", action="store_true")
    args = parser.parse_args()
    profile = args.profile.resolve()
    command = prepare(args.game, profile, args.java_home, args.native_dependencies, args.component_directory)
    for index in range(args.runs):
        run(profile, command, f"{args.prefix}-{index + 1}", args.jfr)
