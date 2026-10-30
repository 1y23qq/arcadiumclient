from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys

from package_client import legacy_ports

ROOT = Path(__file__).resolve().parents[1]
CORE_MODULES = ("config", "config-impl", "commands", "hud", "events", "notifications", "ui", "internal", "utils", "relocator", "poly-compose", "compose-bundle")


def invalidate_local_core_remaps(root: Path = ROOT) -> None:
    import re
    workspace = root.resolve()
    for project in ("client-runtime", "client-modern"):
        cache = root / project / ".gradle/loom-cache/remapped_mods/remapped/org/polyfrost/oneconfig"
        if not cache.exists():
            continue
        resolved = cache.resolve()
        if not resolved.is_relative_to(workspace):
            raise ValueError("Local Core remap cache points outside the workspace.")
        for family in cache.iterdir():
            if not re.fullmatch(r"1\.(?:7\.10|8\.9|21\.11)-(?:fabric|ornithe)-[0-9a-f]+", family.name):
                continue
            for artifact in family.glob("*/*.jar"):
                if not artifact.resolve().is_relative_to(resolved):
                    raise ValueError("Local Core remap artifact points outside its cache.")
                artifact.unlink()


def gradle(project: str, *tasks: str) -> list[str]:
    wrapper_project = "client-modern" if project == "client-next" else "game-branding" if project in {"forge-compat", "client-legacy-performance"} or project.startswith("client-legacy-mods/") else project
    wrapper = ROOT / wrapper_project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    prefix = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
    legacy = project.startswith("client-legacy-mods/")
    arguments = ["--configure-on-demand", "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8", "-Dorg.gradle.parallel=false"] if legacy else []
    return prefix + ["-p", str(ROOT / project), f"--max-workers={1 if legacy else 2}", "--no-configuration-cache", *arguments, *tasks]


def build_commands() -> list[list[str]]:
    ports = sorted(legacy_ports(ROOT), key=lambda port: port["directory"] != "overflowanimations")
    return [
        gradle("client-ui", ":modules:internal:jar", ":modules:compose-bundle:jar",
               ":minecraft:1.8.9-ornithe:remapJar", ":bootstrap:1.8.9-ornithe:build",
               ":minecraft:1.7.10-ornithe:remapJar", ":bootstrap:1.7.10-ornithe:build",
               ":minecraft:1.21.11-fabric:remapJar", ":bootstrap:1.21.11-fabric:build",
               ":minecraft:26.2-fabric:jar", ":bootstrap:26.2-fabric:build"),
        gradle("client-ui", *(f":modules:{module}:publishToMavenLocal" for module in CORE_MODULES),
               ":minecraft:1.8.9-ornithe:publishToMavenLocal", ":minecraft:1.7.10-ornithe:publishToMavenLocal",
               ":minecraft:1.21.11-fabric:publishToMavenLocal", ":minecraft:26.2-fabric:publishToMavenLocal"),
        gradle("client-runtime", "--refresh-dependencies", "build", "exportTestClasspath"),
        gradle("client-runtime", "-PminecraftVersion=1.7.10", "build", "exportTestClasspath"),
        gradle("client-modern", "--refresh-dependencies", "test", "remapJar"),
        gradle("client-next", "--refresh-dependencies", "test", "jar"),
        gradle("forge-compat", "build"),
        gradle("game-branding", "build"),
        gradle("game-branding", "-PminecraftVersion=1.7.10", "build"),
        gradle("client-legacy-mods/pylon", "build"),
        *(gradle("client-legacy-mods/" + port["directory"], port["task"]) for port in ports),
        [sys.executable, str(ROOT / "scripts/package_client.py")],
    ]


def build(java_home: str | None = None, offline: bool = False) -> None:
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
    for index, command in enumerate(build_commands()):
        if offline and index == 2:
            invalidate_local_core_remaps()
        if offline and command[0] != sys.executable:
            command = [argument for argument in command if argument != "--refresh-dependencies"] + ["--offline"]
        print("Building: " + " ".join(command), flush=True)
        subprocess.run(command, cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Build all Minecraft client components from source before compiling the launcher.")
    parser.add_argument("--java-home")
    parser.add_argument("--offline", action="store_true", help="Use already cached Gradle dependencies without refreshing remote metadata.")
    args = parser.parse_args()
    build(args.java_home, args.offline)
