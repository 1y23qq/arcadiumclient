from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import tempfile
import zipfile

import test_chat_dispatch as cached
import test_feature_data as runner


ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    parser = argparse.ArgumentParser(description="Run shared theme accent regressions with the built Core JAR.")
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/theme-accent-tests")
    args = parser.parse_args()
    java, javac = runner.find_jdk(args.java_home)
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    jars = cached.dependencies(cache)
    internal = ROOT / "client-ui/modules/internal/build/libs/internal-1.0.0-alpha.413.jar"
    candidates = [p for p in internal.parent.glob("*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if len(candidates) != 1:
        raise RuntimeError("Build the Core internal module before running theme tests")
    jars.insert(0, candidates[0])
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix="arcadium-theme-tests-")).resolve()
    try:
        classes = temporary / "classes"
        classes.mkdir()
        launcher_source = temporary / "FeatureDataTestLauncher.java"
        launcher_source.write_text(runner.LAUNCHER, "utf-8")
        launcher = temporary / "launcher.jar"
        paths = [classes, *jars]
        with zipfile.ZipFile(launcher, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", runner.manifest([
                "Manifest-Version: 1.0", "Main-Class: FeatureDataTestLauncher",
                "Class-Path: " + " ".join(path.as_uri() + ("/" if path.is_dir() else "") for path in paths),
            ]))
        source = ROOT / "client-ui/modules/internal/src/test/java/org/polyfrost/oneconfig/internal/ui/themes/ThemeAccentTest.java"
        compile_result = runner.run([str(javac), "--release", "21", "-encoding", "UTF-8", "-proc:none",
                                     "-classpath", str(launcher), "-d", str(classes), str(source), str(launcher_source)], ROOT)
        (output / "compile.log").write_text(compile_result.stdout, "utf-8")
        if compile_result.returncode:
            print(compile_result.stdout)
            return compile_result.returncode
        result = runner.run([str(java), "-Dfile.encoding=UTF-8", "-jar", str(launcher),
                             (output / "results.json").as_uri(), "org.polyfrost.oneconfig.internal.ui.themes.ThemeAccentTest"], ROOT)
        (output / "test.log").write_text(result.stdout, "utf-8")
        print(result.stdout)
        if result.returncode:
            return result.returncode
        report = json.loads((output / "results.json").read_text("utf-8"))
        return 0 if report.get("success") and report.get("testsStarted", 0) >= 8 and report.get("testsSkipped") == 0 else 1
    finally:
        if temporary.parent != Path(tempfile.gettempdir()).resolve() or not temporary.name.startswith("arcadium-theme-tests-"):
            raise RuntimeError("Unexpected theme test directory; preserving it")
        shutil.rmtree(temporary)


if __name__ == "__main__":
    raise SystemExit(main())
