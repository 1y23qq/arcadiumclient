from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CORE_MODULES = ("config", "config-impl", "commands", "hud", "events", "notifications", "ui", "internal", "utils", "relocator", "poly-compose", "compose-bundle")


def gradle(project: str, *tasks: str) -> list[str]:
    wrapper_project = "game-branding" if project == "forge-compat" else project
    wrapper = ROOT / wrapper_project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    prefix = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
    return prefix + ["-p", str(ROOT / project), "--max-workers=2", "--no-configuration-cache", *tasks]


def build_commands() -> list[list[str]]:
    return [
        gradle("client-ui", ":modules:internal:jar", ":modules:compose-bundle:jar", ":minecraft:1.8.9-ornithe:remapJar", ":bootstrap:1.8.9-ornithe:build"),
        gradle("client-ui", *(f":modules:{module}:publishToMavenLocal" for module in CORE_MODULES), ":minecraft:1.8.9-ornithe:publishToMavenLocal"),
        gradle("client-runtime", "build", "exportTestClasspath"),
        gradle("forge-compat", "build"),
        gradle("game-branding", "build"),
        [sys.executable, str(ROOT / "scripts/package_client.py")],
    ]


def build(java_home: str | None = None) -> None:
    environment = os.environ.copy()
    if java_home:
        environment["JAVA_HOME"] = str(Path(java_home).resolve())
    if environment.get("JAVA_HOME"):
        environment["PATH"] = str(Path(environment["JAVA_HOME"]) / "bin") + os.pathsep + environment.get("PATH", "")
    java = str(Path(environment["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if environment.get("JAVA_HOME") else "java"
    result = subprocess.run([java, "-version"], capture_output=True, text=True, check=True, env=environment)
    import re
    if not re.search(r'version "25(?:[.\-+"]|$)', result.stdout + result.stderr):
        raise RuntimeError("A JDK 25 installation is required. Set JAVA_HOME or pass --java-home.")
    for command in build_commands():
        print("Building: " + " ".join(command), flush=True)
        subprocess.run(command, cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Build all Minecraft client components from source before compiling the launcher.")
    parser.add_argument("--java-home")
    build(parser.parse_args().java_home)
