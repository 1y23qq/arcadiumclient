from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tomllib

from prepare_forge1710 import ROOT


def launch(directory: Path, java_home: Path, shared: Path | None = None, debug_mixins: bool = False, qa: bool = False, menus: bool = False, world: bool = False) -> subprocess.Popen:
    directory = directory.resolve()
    if not (directory / ".arcadium-forge-probe").is_file():
        raise RuntimeError("Prepare an isolated Forge profile before launching the verification build.")
    build = ROOT / "build/forge1710"
    shutil.copy2(build / "ArcadiumForge-1.7.10.jar", directory / "mods/ArcadiumForge-1.7.10.jar")
    if qa or menus or world:
        version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
        fixture = ROOT / f"client-forge1710/build/libs/ArcadiumForgeQa-{version}.jar"
        if not fixture.is_file(): raise RuntimeError("Build the Forge QA fixture before launching with --qa.")
        shutil.copy2(fixture, directory / "mods/ArcadiumForgeQa.jar")
    profile = json.loads((directory / "launch.json").read_text(encoding="utf-8"))
    arguments = profile["javaArguments"]
    cp_index = arguments.index("-cp") + 1
    arguments[cp_index] = str(build / "ArcadiumBootstrap-1.7.10.jar") + os.pathsep + arguments[cp_index]
    extra = [f"-Darcadium.userGameDir={shared.resolve() if shared else directory}"]
    if menus: extra.append("-Darcadium.qa.menuFlow=true")
    if world: extra.append("-Darcadium.qa.worldFlow=true")
    if debug_mixins:
        extra += ["-Dmixin.dumpTargetOnFailure=true", "-Dmixin.debug.export=true"]
    if shared:
        if not (shared / ".arcadium-forge-probe").is_file():
            raise RuntimeError("The shared data test must also use a marked, isolated profile.")
        extra.append(f"-Darcadium.runtimeGameDir={directory}")
        tweak = arguments.index("--tweakClass") + 1
        arguments[tweak] = "io.github.arcadiumclient.forge1710.bootstrap.ArcadiumForgeTweaker"
    java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    with (directory / "arcadium-console.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen([str(java), *extra, *arguments], cwd=directory, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    (directory / "process-id.txt").write_text(str(process.pid), encoding="ascii")
    print(f"Arcadium Forge verification process: {process.pid}", flush=True)
    return process


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Launch the isolated Arcadium Forge verification build.")
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--shared-directory", type=Path)
    parser.add_argument("--debug-mixins", action="store_true")
    parser.add_argument("--qa", action="store_true")
    parser.add_argument("--menus", action="store_true")
    parser.add_argument("--world", action="store_true")
    args = parser.parse_args()
    launch(args.directory, args.java_home, args.shared_directory, args.debug_mixins, args.qa, args.menus, args.world)
