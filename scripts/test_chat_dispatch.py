from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import zipfile

import test_feature_data as runner


ROOT = Path(__file__).resolve().parent.parent
TEST = "org.polyfrost.oneconfig.internal.network.ChatSendTraceTest"
SOURCES = (
    ROOT / "client-ui/minecraft/src/main/java/org/polyfrost/oneconfig/internal/network/ChatSendTrace.java",
    ROOT / "client-ui/minecraft/src/test/java/org/polyfrost/oneconfig/internal/network/ChatSendTraceTest.java",
)


def dependency(cache: Path, group: str, module: str, version: str | None = None) -> Path:
    directory = cache / group / module
    if version is not None:
        directory /= version
    candidates = sorted(
        (path for path in directory.glob("**/*.jar") if not path.name.endswith(("-sources.jar", "-javadoc.jar"))),
        reverse=True,
    )
    if not candidates:
        raise RuntimeError(f"Missing cached test dependency: {group}:{module}:{version or '*'}")
    return candidates[0]


def dependencies(cache: Path) -> list[Path]:
    selected: list[Path] = []
    for junit, platform in (("5.12.2", "1.12.2"), ("5.10.2", "1.10.2"), ("6.1.3", "6.1.3")):
        try:
            selected = [
                dependency(cache, "org.junit.jupiter", "junit-jupiter-api", junit),
                dependency(cache, "org.junit.jupiter", "junit-jupiter-engine", junit),
                dependency(cache, "org.junit.platform", "junit-platform-launcher", platform),
                dependency(cache, "org.junit.platform", "junit-platform-engine", platform),
                dependency(cache, "org.junit.platform", "junit-platform-commons", platform),
            ]
            break
        except RuntimeError:
            continue
    if not selected:
        raise RuntimeError("A complete cached JUnit Platform is required; this command does not download dependencies.")
    logging: list[Path] = []
    for version in ("2.23.1", "2.25.3", "2.26.1"):
        try:
            logging = [dependency(cache, "org.apache.logging.log4j", module, version) for module in ("log4j-api", "log4j-core")]
            break
        except RuntimeError:
            continue
    if not logging:
        raise RuntimeError("A matching cached Log4j API and implementation are required.")
    return selected + [
        dependency(cache, "io.netty", "netty-all", "4.0.23.Final"),
        dependency(cache, "org.opentest4j", "opentest4j"),
        dependency(cache, "org.apiguardian", "apiguardian-api"),
        dependency(cache, "com.google.code.gson", "gson"),
    ] + logging


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Compile and run the chat timing regressions without Gradle or Minecraft.")
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")))
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/chat-dispatch-tests")
    args = parser.parse_args()
    java, javac = runner.find_jdk(args.java_home)
    jars = dependencies(args.gradle_home / "caches/modules-2/files-2.1")
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = output / "results.json"
    report.write_text('{"success":false,"status":"pending"}\n', "utf-8")
    temporary = Path(tempfile.mkdtemp(prefix="arcadium-chat-tests-")).resolve()
    try:
        classes = temporary / "classes"
        classes.mkdir()
        source = temporary / "FeatureDataTestLauncher.java"
        source.write_text(runner.LAUNCHER, "utf-8")
        launcher = temporary / "launcher.jar"
        paths = [classes, ROOT / "client-ui/minecraft/src/test/resources", *jars]
        metadata = runner.manifest([
            "Manifest-Version: 1.0", "Main-Class: FeatureDataTestLauncher",
            "Class-Path: " + " ".join(path.as_uri() + ("/" if path.is_dir() else "") for path in paths),
        ])
        with zipfile.ZipFile(launcher, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", metadata)
        compiled = runner.run([
            str(javac), "--release", "17", "-encoding", "UTF-8", "-proc:none",
            "-classpath", str(launcher), "-d", str(classes), *map(str, SOURCES), str(source),
        ], ROOT)
        (output / "compile.log").write_text(compiled.stdout, "utf-8")
        if compiled.returncode:
            print(compiled.stdout, end="")
            return compiled.returncode
        with zipfile.ZipFile(launcher, "a") as archive:
            archive.write(classes / "FeatureDataTestLauncher.class", "FeatureDataTestLauncher.class")
        executed = runner.run([
            str(java), "-Dio.netty.noUnsafe=true", "-Dfile.encoding=UTF-8", "-jar", str(launcher), report.as_uri(), TEST,
        ], ROOT)
        (output / "run.log").write_text(executed.stdout, "utf-8")
        print(executed.stdout, end="")
        if report.is_file():
            result = json.loads(report.read_text("utf-8"))
            result["sourceSha256"] = {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in SOURCES}
            report.write_text(json.dumps(result, indent=2) + "\n", "utf-8")
        print("Results: " + str(report))
        return executed.returncode
    finally:
        if temporary.parent != Path(tempfile.gettempdir()).resolve() or not temporary.name.startswith("arcadium-chat-tests-"):
            raise RuntimeError("Unexpected temporary directory; preserving it: " + str(temporary))
        shutil.rmtree(runner.native_path(temporary))


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError) as error:
        print("Chat dispatch tests: " + str(error), file=sys.stderr)
        raise SystemExit(1)
