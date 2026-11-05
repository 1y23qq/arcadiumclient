from pathlib import Path
import argparse
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="Build the development-only real Forge render-event observer.")
    parser.add_argument("--java-home", type=Path, default=Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    parser.add_argument("--minecraft-client", type=Path, help="Optional original obfuscated Minecraft 1.8.9 client API for javac.")
    parser.add_argument("--forge-api", type=Path, default=ROOT / "build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar")
    parser.add_argument("--output", type=Path, default=ROOT / "build/forge-render-probe/ArcadiumRenderProbe-dev.jar")
    args = parser.parse_args()
    if args.java_home is None:
        parser.error("Supply --java-home or JAVA_HOME pointing to a JDK.")
    javac = args.java_home / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    sources = ROOT / "forge-compat/src/renderProbe"
    classes = ROOT / "build/forge-render-probe/classes"
    classes.mkdir(parents=True, exist_ok=True)
    classpath = [args.forge_api.resolve(strict=True)]
    if args.minecraft_client:
        classpath.append(args.minecraft_client.resolve(strict=True))
    subprocess.run([str(javac), "--release", "8", "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, classpath)),
                    "-d", str(classes), *map(str, sorted((sources / "java").rglob("*.java")))], check=True, cwd=ROOT)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as archive:
        for directory in [classes, sources / "resources"]:
            for path in sorted(directory.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(directory).as_posix())
    print(args.output)


if __name__ == "__main__":
    main()
