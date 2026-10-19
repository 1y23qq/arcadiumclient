from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parent.parent
TEST_CLASSES = (
    "io.github.arcadiumclient.runtime.test.AnimationModeBridgeTest",
    "io.github.arcadiumclient.runtime.test.HeightLimitTextTest",
    "io.github.arcadiumclient.runtime.test.AutoTextDataTest",
    "io.github.arcadiumclient.runtime.test.ChatFilterPolicyTest",
    "io.github.arcadiumclient.runtime.test.UnicodeShortcodesTest",
    "io.github.arcadiumclient.runtime.client.bedwars.BedwarsOverlayDataTest",
)
STANDALONE_CHECKS = (
    "io.github.arcadiumclient.runtime.client.features.CuteVisualsPureTest",
    "io.github.arcadiumclient.runtime.client.features.VisualFeaturesPureTest",
    "io.github.arcadiumclient.runtime.client.skins.SkinServicesPureTest",
)

LAUNCHER = r'''
import com.google.gson.GsonBuilder;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class FeatureDataTestLauncher {
    public static void main(String[] args) throws Exception {
        Path report = Path.of(URI.create(args[0]));
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> origins = new LinkedHashMap<>();
        result.put("classOrigins", origins);
        try {
            var request = LauncherDiscoveryRequestBuilder.request()
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "false");
            for (int i = 1; i < args.length; i++) {
                Class<?> type = Class.forName(args[i], false, FeatureDataTestLauncher.class.getClassLoader());
                String origin = type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                origins.put(args[i], origin);
                System.out.println("Data test: " + args[i] + " [" + origin + "]");
                request.selectors(DiscoverySelectors.selectClass(type));
            }
            var config = LauncherConfig.builder()
                .enableLauncherSessionListenerAutoRegistration(false)
                .enableLauncherDiscoveryListenerAutoRegistration(false)
                .enableTestExecutionListenerAutoRegistration(false)
                .enableTestEngineAutoRegistration(false)
                .enablePostDiscoveryFilterAutoRegistration(false)
                .addTestEngines(new JupiterTestEngine()).build();
            var listener = new SummaryGeneratingListener();
            var launcher = LauncherFactory.create(config);
            launcher.registerTestExecutionListeners(listener);
            launcher.execute(request.build());
            var summary = listener.getSummary();
            summary.printTo(new PrintWriter(System.out));
            summary.printFailuresTo(new PrintWriter(System.out));
            result.put("testsFound", summary.getTestsFoundCount());
            result.put("testsStarted", summary.getTestsStartedCount());
            result.put("testsSucceeded", summary.getTestsSucceededCount());
            result.put("testsFailed", summary.getTestsFailedCount());
            result.put("testsSkipped", summary.getTestsSkippedCount());
            result.put("testsAborted", summary.getTestsAbortedCount());
            result.put("containersFailed", summary.getContainersFailedCount());
            var failures = new ArrayList<Map<String, String>>();
            summary.getFailures().forEach(failure -> failures.add(Map.of(
                "test", failure.getTestIdentifier().getDisplayName(), "error", failure.getException().toString())));
            result.put("failures", failures);
            result.put("success", summary.getTestsFoundCount() > 0 && summary.getTestsStartedCount() == summary.getTestsFoundCount()
                && summary.getTotalFailureCount() == 0 && summary.getTestsAbortedCount() == 0
                && summary.getContainersAbortedCount() == 0);
        } catch (Throwable failure) {
            result.put("success", false);
            result.put("runnerError", failure.toString());
            failure.printStackTrace();
        }
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(result));
        if (!Boolean.TRUE.equals(result.get("success"))) System.exit(1);
    }
}
'''


def native_path(path: Path) -> str:
    value = str(path.resolve())
    if os.name == "nt" and not value.startswith("\\\\?\\"):
        return "\\\\?\\UNC\\" + value[2:] if value.startswith("\\\\") else "\\\\?\\" + value
    return value


def run(command: list[str], directory: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=directory, text=True, encoding="utf-8", errors="replace",
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)


def find_jdk(explicit: Path | None) -> tuple[Path, Path]:
    executable = "java.exe" if os.name == "nt" else "java"
    compiler = "javac.exe" if os.name == "nt" else "javac"
    candidates: list[Path] = []
    if explicit is not None:
        candidates.append(explicit / "bin" / executable)
    else:
        if os.environ.get("JAVA_HOME"):
            candidates.append(Path(os.environ["JAVA_HOME"]) / "bin" / executable)
        found = shutil.which("javac")
        if found:
            candidates.append(Path(found).resolve().parent / executable)
        candidates.extend(sorted(Path("C:/Program Files/Java").glob("jdk-*/bin/java.exe"), reverse=True))
        candidates.extend(sorted(Path("/Library/Java/JavaVirtualMachines").glob("*/Contents/Home/bin/java"), reverse=True))
        candidates.extend(sorted(Path("/usr/lib/jvm").glob("*/bin/java"), reverse=True))
    for java in candidates:
        javac = java.parent / compiler
        if not java.is_file() or not javac.is_file():
            continue
        version = run([str(java), "-version"], ROOT)
        match = re.search(r'version\s+"(\d+)', version.stdout)
        if version.returncode == 0 and match and int(match.group(1)) >= 25:
            return java, javac
    raise RuntimeError("Use --java-home with a JDK 25 or newer installation.")


def manifest(lines: list[str]) -> bytes:
    output: list[str] = []
    for value in lines:
        first = True
        while value:
            width = 70 if first else 69
            output.append(("" if first else " ") + value[:width])
            value = value[width:]
            first = False
    return ("\r\n".join(output) + "\r\n\r\n").encode("ascii")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Run compiled feature data tests without starting Minecraft or Gradle.")
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--classpath-file", type=Path, default=ROOT / "client-runtime/build/test-classpath.txt")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/feature-data-tests")
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = output / "results.json"
    report.write_text('{"success": false, "status": "pending"}\n', "utf-8")
    java, javac = find_jdk(args.java_home)
    if not args.classpath_file.is_file():
        raise RuntimeError("The test classpath is missing. Compile :1.8.9-ornithe:testClasses and generate its runtime classpath first.")
    paths = [Path(value).resolve() for value in args.classpath_file.read_text("utf-8-sig").splitlines() if value.strip()]
    for test in TEST_CLASSES:
        relative = Path(*test.split(".")).with_suffix(".class")
        if not any(os.path.isfile(native_path(path / relative)) for path in paths):
            raise RuntimeError("Compiled test class is missing: " + test + ". Run :1.8.9-ornithe:testClasses first.")
    temporary = Path(tempfile.mkdtemp(prefix="arcadium-feature-data-")).resolve()
    try:
        uris: list[str] = []
        for index, path in enumerate(paths):
            if not os.path.exists(native_path(path)):
                if path.suffix.lower() == ".jar":
                    raise RuntimeError("Test dependency is missing: " + str(path))
                continue
            directory = os.path.isdir(native_path(path))
            if os.name == "nt" and not directory and len(str(path)) >= 240:
                target = temporary / f"dependency-{index}.jar"
                shutil.copyfile(native_path(path), native_path(target))
                path = target
            uris.append(path.as_uri() + ("/" if directory else ""))
        launcher = temporary / "launcher.jar"
        metadata = manifest(["Manifest-Version: 1.0", "Main-Class: FeatureDataTestLauncher", "Class-Path: " + " ".join(uris)])
        with zipfile.ZipFile(launcher, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", metadata)
        source = temporary / "FeatureDataTestLauncher.java"
        source.write_text(LAUNCHER, "utf-8")
        compiled = run([str(javac), "-encoding", "UTF-8", "-proc:none", "-classpath", str(launcher), "-d", str(temporary), str(source)], temporary)
        (output / "compile.log").write_text(compiled.stdout, "utf-8")
        if compiled.returncode:
            print(compiled.stdout, end="")
            return compiled.returncode
        with zipfile.ZipFile(launcher, "a") as archive:
            archive.write(temporary / "FeatureDataTestLauncher.class", "FeatureDataTestLauncher.class")
        executed = run([str(java), "-Dfile.encoding=UTF-8", "-jar", str(launcher), report.as_uri(), *TEST_CLASSES], temporary)
        (output / "run.log").write_text(executed.stdout, "utf-8")
        print(executed.stdout, end="")
        if executed.returncode:
            return executed.returncode
        result = json.loads(report.read_text("utf-8"))
        result["standaloneChecks"] = []
        for test in STANDALONE_CHECKS:
            checked = run([str(java), "-Dfile.encoding=UTF-8", "-cp", str(launcher), test], temporary)
            (output / (test.rsplit(".", 1)[-1] + ".log")).write_text(checked.stdout, "utf-8")
            print(checked.stdout, end="")
            result["standaloneChecks"].append({"class": test, "passed": checked.returncode == 0})
            if checked.returncode:
                result["success"] = False
            report.write_text(json.dumps(result, indent=2) + "\n", "utf-8")
            if checked.returncode:
                return checked.returncode
        print("Results: " + str(report))
        return executed.returncode
    finally:
        if temporary.parent != Path(tempfile.gettempdir()).resolve() or not temporary.name.startswith("arcadium-feature-data-"):
            raise RuntimeError("Unexpected temporary directory; preserving it: " + str(temporary))
        shutil.rmtree(native_path(temporary))


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError) as error:
        print("Feature data tests: " + str(error), file=sys.stderr)
        raise SystemExit(1)
