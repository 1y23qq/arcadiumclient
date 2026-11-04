#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


def main() -> None:
    source = Path(__file__).resolve().parent
    root = source.parents[2]
    parser = argparse.ArgumentParser(description="Build and verify the isolated resident Metal prototype.")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--java-home", type=Path, default=Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    parser.add_argument("--arch", choices=("arm64", "x64"), default="arm64" if platform.machine().lower() in {"arm64", "aarch64"} else "x64")
    arguments = parser.parse_args()
    output = (arguments.output or root / "build/resident-renderer" / arguments.arch).resolve()
    output.mkdir(parents=True, exist_ok=True)
    commands: list[list[str]] = []

    def run(command: list[str], name: str, allowed: tuple[int, ...] = (0,)) -> int:
        commands.append(command)
        with (output / name).open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode not in allowed:
            raise RuntimeError(f"{name} failed with exit code {result.returncode}; inspect {output / name}")
        return result.returncode

    configure = ["cmake", "-S", str(source), "-B", str(output / "native")]
    if sys.platform == "darwin":
        configure += ["-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_OSX_ARCHITECTURES=" + ("arm64" if arguments.arch == "arm64" else "x86_64"),
                      "-DCMAKE_OSX_DEPLOYMENT_TARGET=" + ("13.0" if arguments.arch == "arm64" else "12.0")]
    elif sys.platform == "win32":
        if arguments.arch != "x64":
            raise RuntimeError("The Windows ABI test currently targets x64")
        configure += ["-G", "Visual Studio 17 2022", "-A", "x64"]
    run(configure, "configure.log")
    run(["cmake", "--build", str(output / "native"), "--config", "Release", "--parallel", "2"], "build.log")
    run(["ctest", "--test-dir", str(output / "native"), "-C", "Release", "--output-on-failure", "--output-junit", str(output / "native-tests.xml")], "native-tests.log")
    test_cases = ET.parse(output / "native-tests.xml").getroot().findall(".//testcase")
    metal = next((case for case in test_cases if case.get("name") == "metal_resident_scene"), None)
    metal_status = "not-applicable" if metal is None else "skipped-no-device" if metal.find("skipped") is not None else "passed"
    home = arguments.java_home
    java = str(home / "bin" / ("java.exe" if sys.platform == "win32" else "java")) if home else shutil.which("java")
    javac = str(home / "bin" / ("javac.exe" if sys.platform == "win32" else "javac")) if home else shutil.which("javac")
    if not java or not javac:
        raise RuntimeError("JDK 25 is required for the native ABI probe")
    suffix = ".dll" if sys.platform == "win32" else ".dylib" if sys.platform == "darwin" else ".so"
    libraries = list((output / "native").rglob("*arcadium_renderer_metal" + suffix))
    if len(libraries) != 1:
        raise RuntimeError(f"Expected exactly one native library, found {libraries}")
    library = libraries[0]
    java_source = root / "client-runtime/src/main/java/io/github/arcadiumclient/runtime/client/optimizations/backend"
    java_files = [java_source / name for name in ("ResidentSceneBackend.java", "SceneBufferChecks.java", "MetalResidentSceneBackend.java")]
    java_files += sorted((source / "tests").glob("*.java"))
    java_output = output / "java"
    run([javac, "-d", str(java_output), *map(str, java_files)], "javac.log")
    probe = "MetalSceneBindingsProbe" if sys.platform == "darwin" else "NativeSceneBindingsProbe"
    probe_command = [java, "--enable-native-access=ALL-UNNAMED", "-cp", str(java_output),
                     "io.github.arcadiumclient.runtime.client.optimizations.backend." + probe, str(library)]
    if sys.platform == "darwin": probe_command.append(str(source / "tests/reference_scene.metal"))
    probe_code = run(probe_command, "java-native-probe.log", (0, 77) if sys.platform == "darwin" else (0,))
    report = {"passed": True, "platform": sys.platform, "architecture": arguments.arch,
              "nativeCompiled": True, "metalDeviceTest": metal_status,
              "javaMetalDeviceTest": "skipped-no-device" if probe_code == 77 else "passed" if sys.platform == "darwin" else "not-applicable",
              "javaNativeAbiProbe": probe_code == 0, "presentationIntegrated": False,
              "nativeLibrary": str(library), "commands": commands}
    (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"commands", "nativeLibrary"}}, indent=2))


if __name__ == "__main__":
    main()
