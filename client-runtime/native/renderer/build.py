#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


def validate_pylon_input(path: Path, expected: str, root: Path) -> dict:
    if len(expected) != 64 or any(value not in "0123456789abcdef" for value in expected):
        raise RuntimeError("Pass the exact lowercase Pylon SHA-256")
    lock = json.loads((root / "distribution/oneclient-1.8.9.lock.json").read_text(encoding="utf-8-sig"))
    entries = [entry for entry in lock["arcadiumComponents"] if entry["file"] == "ArcadiumPylon-1.8.9.jar"]
    if len(entries) != 1 or entries[0].get("modId") != "pylon":
        raise RuntimeError("The source Pylon component pin is missing")
    pin = entries[0]
    if (not path.is_file() or path.stat().st_size != pin["bytes"] or pin["bytes"] > 64 * 1024 * 1024
            or hashlib.sha256(path.read_bytes()).hexdigest() != expected or expected != pin["sha256"]):
        raise RuntimeError("Pylon input does not match the explicit and source component checksums")
    properties = dict(tuple(part.strip() for part in line.split("=", 1)) for line in
                      (root / "client-legacy-mods/pylon/gradle.properties").read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.lstrip().startswith("#"))
    with zipfile.ZipFile(path) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata.get("id") != "pylon" or metadata.get("version") != properties["version"].strip():
            raise RuntimeError("The Pylon input has the wrong mod identity or version")
        required = {"pl/tomgirl/pylon/window/DisplaySdl.class", "pl/tomgirl/pylon/window/GlSurface.class",
                    "pl/tomgirl/pylon/window/MainThread.class", "org/lwjgl/sdl/SDLVideo.class"}
        required |= {f"macos/{arch}/org/lwjgl/{name}" for arch in ("arm64", "x64")
                     for name in ("liblwjgl.dylib", "opengl/liblwjgl_opengl.dylib", "sdl/libSDL3.dylib")}
        if not required.issubset(archive.namelist()):
            raise RuntimeError("The Pylon input lacks its actual SDL/GL classes or Mac native libraries")
    return {"path": str(path.resolve()), "sha256": expected, "bytes": pin["bytes"],
            "modId": metadata["id"], "version": metadata["version"], "sourceCatalogVerified": True}


def pylon_case_plan() -> list[dict]:
    cases = [{"label": case + ("-surface-below" if order == -1 else ""), "probeCase": case,
              "surfaceOrder": order, "matched": False}
             for order in (0, -1) for case in ("transport", "transport-sync0", "transport-sync1", "fallback")]
    schedules = [(1, "sync0", ("sleep2", "cf0-sleep2", "cf2")),
                 (1, "alternating", ("cf2", "cf0-sleep2", "sleep2")),
                 (2, "alternating", ("sleep2", "cf0-sleep2", "cf2")),
                 (2, "sync0", ("cf2", "cf0-sleep2", "sleep2"))]
    suffix = {"sleep2": "", "cf0-sleep2": "-runloop0", "cf2": "-runloop"}
    for repeat, pacing, styles in schedules:
        for style in styles:
            cases.append({"label": f"matched-r{repeat}-{pacing}-{style}", "probeCase": ("transport-sync0" if pacing == "sync0" else "transport") + suffix[style],
                          "surfaceOrder": 0, "matched": True, "repeat": repeat, "pacing": pacing, "pumpStyle": style})
    return [{"ordinal": i + 1, **case} for i, case in enumerate(cases)]


def scene_handoff_plan() -> list[dict]:
    return [{"cohort": cohort, "libraryRole": role, "freshJvm": True,
             "sceneSizes": [[192, 128], [224, 160], [208, 144]], "resizeCount": 2,
             "legacyGlSourceControl": True, "actualDisplayedDeadlineNanoseconds": 5_000_000_000,
             "actualDrawableReadback": cohort == "pixels", "readbackIsDisplayedEvidence": False,
             "pacing": 1, "pumpStyle": "sdl-poll-sleep2", "performanceClaim": False}
            for cohort, role in (("ticket", "production"), ("pixels", "instrumented-test-only"))]


def readiness_case_plan() -> list[dict]:
    result = []
    for repeat, modes in ((1, ("off", "observe", "ready")), (2, ("ready", "observe", "off"))):
        for mode in modes:
            result.append({"ordinal": len(result) + 1, "repeat": repeat, "mode": mode,
                           "label": f"r{repeat}-{mode}", "surfaceOrder": 0, "nativeFrames": 3, "pylonFrames": 6,
                           "freshNativeProcess": True, "freshJvm": True, "displayedDeadlineNanoseconds": 5_000_000_000})
    return result


def run_readiness_controls(run, output: Path, source: Path, java: str, pylon_output: Path, pylon: dict, library: Path) -> str:
    plan = readiness_case_plan()
    (output / "legacy-readiness-case-plan.json").write_text(json.dumps({"cases": plan, "casePairs": 6,
        "nativeProcesses": 6, "jvms": 6, "oldTwentyAndHandoffAlreadyPreserved": True,
        "readinessIsDisplayedProof": False, "resubmissions": 0}, indent=2) + "\n")
    outcomes = []
    for case in plan:
        label, mode = case["label"], case["mode"]
        native = [str(output / "native/arcadium_metal_presentation_test"), "0"]
        if mode != "off":
            native.append(mode)
        code = run(native, f"native-readiness-{label}.log", None, timeout=45)
        native_result = {**case, "exitCode": code, "passed": code == 0, "processGuardSeconds": 45,
                         "processTimedOut": code == 124, "performanceClaim": False}
        (output / f"native-readiness-{label}-result.json").write_text(json.dumps(native_result, indent=2) + "\n")
        result_path = output / f"java-pylon-readiness-{label}-result.json"
        java_code = run([java, "-XstartOnFirstThread", "--enable-native-access=ALL-UNNAMED",
             "-Darcadium.present072.expectedHostedLayer=false", "-Darcadium.present073.testPreflight=" + mode, "-cp",
             os.pathsep.join((str(pylon_output), pylon["path"])),
             "io.github.arcadiumclient.runtime.client.optimizations.backend.MetalPylonPresentationProbe",
             str(library), str(source / "tests/reference_scene.metal"), str(result_path), "transport", "0"],
            f"java-pylon-readiness-{label}.log", None, timeout=90)
        try:
            result = json.loads(result_path.read_text(encoding="utf-8")) if result_path.is_file() else {}
        except (ValueError, OSError) as error:
            result = {"passed": False, "resultReadError": type(error).__name__}
        java_passed = java_code == 0 and result.get("passed") is True and result.get("testPreflightMode") == mode and result.get("displayedCurrentFrames") == 6
        outcomes.append({**case, "nativePassed": code == 0, "nativeExitCode": code, "javaExitCode": java_code,
                         "javaPassed": java_passed, "javaProcessTimedOut": java_code == 124,
                         "javaProcessGuardSeconds": 90, "javaResultPresent": result_path.is_file(),
                         "passed": code == 0 and java_passed})
    suite = {"passed": all(case["passed"] for case in outcomes), "cases": outcomes,
             "oldFailuresStillFailOverall": True, "currentDisplayedGateUnchanged": True,
             "resubmissions": 0, "worldOrFpsOrCompatibilityClaim": False}
    (output / "legacy-readiness-suite-result.json").write_text(json.dumps(suite, indent=2) + "\n")
    return "passed" if suite["passed"] else "failed"


def main() -> None:
    source = Path(__file__).resolve().parent
    root = source.parents[2]
    parser = argparse.ArgumentParser(description="Build and verify the isolated resident Metal prototype.")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--java-home", type=Path, default=Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    parser.add_argument("--arch", choices=("arm64", "x64"), default="arm64" if platform.machine().lower() in {"arm64", "aarch64"} else "x64")
    parser.add_argument("--pylon-jar", type=Path)
    parser.add_argument("--pylon-sha256")
    parser.add_argument("--hosted-layer-experiment", action="store_true")
    parser.add_argument("--skiko-inputs", type=Path)
    parser.add_argument("--skiko-sha256")
    parser.add_argument("--legacy-readiness-diagnostics", action="store_true")
    arguments = parser.parse_args()
    if arguments.legacy_readiness_diagnostics and (sys.platform != "darwin" or not arguments.pylon_jar or not arguments.skiko_inputs or arguments.hosted_layer_experiment):
        parser.error("Legacy readiness diagnostics require Darwin, both pinned inputs, and the original backed control")
    if bool(arguments.pylon_jar) != bool(arguments.pylon_sha256):
        parser.error("--pylon-jar and --pylon-sha256 must be supplied together")
    pylon = validate_pylon_input(arguments.pylon_jar, arguments.pylon_sha256, root) if arguments.pylon_jar else None
    if bool(arguments.skiko_inputs) != bool(arguments.skiko_sha256):
        parser.error("--skiko-inputs and --skiko-sha256 must be supplied together")
    skiko = None
    if arguments.skiko_inputs:
        from skiko_probe_inputs import validate
        skiko = validate(arguments.skiko_inputs, arguments.skiko_sha256, root)
    output = (arguments.output or root / "build/resident-renderer" / arguments.arch).resolve()
    output.mkdir(parents=True, exist_ok=True)
    commands: list[list[str]] = []

    def run(command: list[str], name: str, allowed: tuple[int, ...] | None = (0,), timeout: int | None = None) -> int:
        commands.append(command)
        with (output / name).open("w", encoding="utf-8") as log:
            try:
                result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
            except subprocess.TimeoutExpired:
                log.write("Owned diagnostic child exceeded its process guard; subprocess.run killed and waited for that child.\n")
                result = subprocess.CompletedProcess(command, 124)
        if allowed is not None and result.returncode not in allowed:
            raise RuntimeError(f"{name} failed with exit code {result.returncode}; inspect {output / name}")
        return result.returncode

    presentation_testing = sys.platform == "darwin" and pylon is not None and skiko is not None
    configure = ["cmake", "-S", str(source), "-B", str(output / "native"),
                 "-DARC_PRESENT_HOSTED_EXPERIMENT=" + ("ON" if arguments.hosted_layer_experiment else "OFF"),
                 "-DARC_BUILD_PRESENTATION_TESTING=" + ("ON" if presentation_testing else "OFF")]
    if sys.platform == "darwin":
        configure += ["-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_OSX_ARCHITECTURES=" + ("arm64" if arguments.arch == "arm64" else "x86_64"),
                      "-DCMAKE_OSX_DEPLOYMENT_TARGET=" + ("13.0" if arguments.arch == "arm64" else "12.0")]
    elif sys.platform == "win32":
        if arguments.arch != "x64":
            raise RuntimeError("The Windows ABI test currently targets x64")
        configure += ["-G", "Visual Studio 17 2022", "-A", "x64"]
    run(configure, "configure.log")
    run(["cmake", "--build", str(output / "native"), "--config", "Release", "--parallel", "2"], "build.log")
    ctest_code = run(["ctest", "--test-dir", str(output / "native"), "-C", "Release", "--output-on-failure", "--output-junit", str(output / "native-tests.xml")], "native-tests.log", None)
    test_cases = ET.parse(output / "native-tests.xml").getroot().findall(".//testcase")
    failed_tests = [case.get("name") for case in test_cases if case.find("failure") is not None or case.find("error") is not None]
    if ctest_code == 0 and failed_tests:
        raise RuntimeError("CTest status disagrees with its failed native XML results")
    pass_result = output / "native/metal-pass-result.json"
    if pass_result.is_file():
        shutil.copyfile(pass_result, output / "metal-pass-result.json")
    lease_result = output / "native/metal-lease-result.json"
    if lease_result.is_file():
        shutil.copyfile(lease_result, output / "metal-lease-result.json")
    if ctest_code != 0 and failed_tests != ["metal_current_frame_presentation"]:
        raise RuntimeError("Native contract or scene tests failed; do not continue independent presentation probes")
    below_code = None
    if sys.platform == "darwin":
        below_code = run([str(output / "native/arcadium_metal_presentation_test"), "-1"], "native-surface-below.log", None)
        (output / "native-surface-below-result.json").write_text(json.dumps({"exitCode": below_code,
            "passed": below_code == 0, "requestedSurfaceOrder": -1, "exactPixelAndDisplayedGatesUnchanged": True}, indent=2) + "\n")
    metal = next((case for case in test_cases if case.get("name") == "metal_resident_scene"), None)
    metal_status = "not-applicable" if metal is None else "skipped-no-device" if metal.find("skipped") is not None else "passed"
    presentation = next((case for case in test_cases if case.get("name") == "metal_current_frame_presentation"), None)
    presentation_status = "not-applicable" if presentation is None else "failed" if presentation.get("name") in failed_tests else "skipped-no-device-or-context" if presentation.find("skipped") is not None else "passed"
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
    java_files = [java_source / name for name in ("ResidentSceneBackend.java", "PassSceneBackend.java", "ScenePasses.java", "MetalDeviceLease.java", "MetalSceneTicket.java", "SceneBufferChecks.java", "MetalResidentSceneBackend.java",
                                               "MetalPresentationCalls.java", "MetalPresentFrame.java")]
    java_files += sorted((source / "tests").glob("*.java"))
    java_output = output / "java"
    run([javac, "-d", str(java_output), *map(str, java_files)], "javac.log")
    probe = "MetalSceneBindingsProbe" if sys.platform == "darwin" else "NativeSceneBindingsProbe"
    probe_command = [java, "--enable-native-access=ALL-UNNAMED", "-cp", str(java_output),
                     "io.github.arcadiumclient.runtime.client.optimizations.backend." + probe, str(library)]
    if sys.platform == "darwin": probe_command.append(str(source / "tests/reference_scene.metal"))
    probe_code = run(probe_command, "java-native-probe.log", (0, 77) if sys.platform == "darwin" else (0,))
    presentation_probe = None
    if sys.platform != "darwin":
        presentation_probe = run([java, "--enable-native-access=ALL-UNNAMED", "-cp", str(java_output),
                                  "io.github.arcadiumclient.runtime.client.optimizations.backend.UnsupportedPresentationBindingsProbe", str(library)],
                                 "java-presentation-probe.log") == 0
    skiko_status = "not-requested"
    if skiko is not None:
        from skiko_probe_inputs import unpack, run_owned
        skiko_directory = output / "skiko-inputs"
        unpack(arguments.skiko_inputs, arguments.skiko_sha256, root, skiko_directory)
        skiko_classes = output / "skiko-java"
        skiko_cp = os.pathsep.join([str(java_output), *map(str, sorted(skiko_directory.glob("*.jar")))])
        run([javac, "-cp", skiko_cp, "-d", str(skiko_classes), str(source / "tests/skiko/MetalSkikoLeaseProbe.java")], "java-skiko-compile.log")
        skiko_status = "compiled-not-applicable"
        if sys.platform == "darwin":
            fixture = output / "skiko-smoke"
            result_path = output / "java-skiko-metal-result.json"
            properties = {"user.home": str(fixture / "home"), "java.io.tmpdir": str(fixture / "tmp"), "java.awt.headless": "true",
                "skiko.gc.auto": "false", "skiko.rendering.useScreenMenuBar": "false", "skiko.rendering.laf.global": "false",
                "http.proxyHost": "127.0.0.1", "http.proxyPort": "9", "https.proxyHost": "127.0.0.1", "https.proxyPort": "9",
                "socksProxyHost": "127.0.0.1", "socksProxyPort": "9"}
            command = [java, "-XstartOnFirstThread", "--enable-native-access=ALL-UNNAMED", *["-D" + k + "=" + v for k, v in properties.items()],
                "-cp", os.pathsep.join([str(skiko_classes), skiko_cp]),
                "io.github.arcadiumclient.runtime.client.optimizations.backend.MetalSkikoLeaseProbe", str(library),
                hashlib.sha256(library.read_bytes()).hexdigest(), str(source / "tests/reference_scene.metal"),
                str(skiko_directory / "Poppins-Regular.ttf"), str(result_path)]
            process = run_owned(command, fixture, output / "java-skiko-metal.log", output / "java-skiko-process.json")
            result = json.loads(result_path.read_text()) if result_path.exists() else {}
            skiko_status = "passed" if process["passed"] and result.get("passed") is True and result.get("actualMetalDirectContext") is True else "failed"
    pylon_status = "not-requested"
    handoff_status = "not-requested"
    if pylon is not None:
        pylon_output = output / "pylon-java"
        pylon_sources = [java_source / name for name in ("ResidentSceneBackend.java", "PassSceneBackend.java", "ScenePasses.java", "MetalDeviceLease.java", "MetalSceneTicket.java", "SceneBufferChecks.java",
                         "MetalResidentSceneBackend.java", "MetalPresentationCalls.java", "MetalPresentFrame.java", "MetalCompositeSurface.java")]
        pylon_sources.append(source / "tests/pylon/MetalPylonPresentationProbe.java")
        run([javac, "-cp", pylon["path"], "-d", str(pylon_output), *map(str, pylon_sources)], "java-pylon-compile.log")
        if skiko is not None:
            handoff_classes = output / "scene-handoff-java"
            handoff_cp = os.pathsep.join([str(pylon_output), pylon["path"], *map(str, sorted(skiko_directory.glob("*.jar")))])
            run([javac, "-cp", handoff_cp, "-d", str(handoff_classes), str(source / "tests/skiko/MetalSceneHandoffProbe.java")], "java-scene-handoff-compile.log")
            handoff_status = "compiled-not-applicable"
            if sys.platform == "darwin" and probe_code == 0:
                readback_libraries = list((output / "native").rglob("*arcadium_renderer_metal_readback_qa" + suffix))
                if len(readback_libraries) != 1:
                    raise RuntimeError("Expected exactly one separate instrumented presentation library")
                plan = scene_handoff_plan()
                (output / "java-scene-handoff-plan.json").write_text(json.dumps({"cohorts": plan,
                    "oldPylonControlsRetained": len(pylon_case_plan()), "freshJvmPerDylib": True,
                    "crossDylibHandlesPermitted": False, "minecraftWorldOrCeraQualified": False}, indent=2) + "\n")
                outcomes = []
                for planned in plan:
                    cohort = planned["cohort"]
                    selected_library = library if cohort == "ticket" else readback_libraries[0]
                    fixture = output / ("scene-handoff-" + cohort)
                    result_path = output / f"java-scene-handoff-{cohort}-result.json"
                    properties = {"user.home": str(fixture / "home"), "java.io.tmpdir": str(fixture / "tmp"), "java.awt.headless": "true",
                        "skiko.gc.auto": "false", "skiko.rendering.useScreenMenuBar": "false", "skiko.rendering.laf.global": "false",
                        "http.proxyHost": "127.0.0.1", "http.proxyPort": "9", "https.proxyHost": "127.0.0.1", "https.proxyPort": "9",
                        "socksProxyHost": "127.0.0.1", "socksProxyPort": "9"}
                    command = [java, "-XstartOnFirstThread", "--enable-native-access=ALL-UNNAMED", *["-D" + k + "=" + v for k, v in properties.items()],
                        "-cp", os.pathsep.join([str(handoff_classes), handoff_cp]),
                        "io.github.arcadiumclient.runtime.client.optimizations.backend.MetalSceneHandoffProbe",
                        str(selected_library), hashlib.sha256(selected_library.read_bytes()).hexdigest(), str(source / "tests/reference_passes.metal"),
                        str(skiko_directory / "Poppins-Regular.ttf"), str(result_path), cohort]
                    process = run_owned(command, fixture, output / f"java-scene-handoff-{cohort}.log", output / f"java-scene-handoff-{cohort}-process.json")
                    result = json.loads(result_path.read_text()) if result_path.is_file() else {}
                    outcomes.append({**planned, "passed": process["passed"] and result.get("passed") is True,
                        "nativeLibrarySha256": hashlib.sha256(selected_library.read_bytes()).hexdigest(),
                        "processPassed": process["passed"], "resultPresent": result_path.is_file(),
                        "actualDisplayedFailures": result.get("displayFailures"), "resultFailure": result.get("failure")})
                handoff_status = "passed" if all(case["passed"] for case in outcomes) else "failed"
                (output / "java-scene-handoff-suite-result.json").write_text(json.dumps({"passed": handoff_status == "passed",
                    "cohorts": outcomes, "freshJvmPerDylib": True, "displayedNeverSubstitutedByPixelsOrCompleted": True,
                    "minecraftWorldOrCeraQualified": False, "clientUiSelected": False, "performanceClaim": False}, indent=2) + "\n")
        if sys.platform == "darwin" and probe_code == 0:
            outcomes = []
            cases = pylon_case_plan()
            (output / "java-pylon-case-plan.json").write_text(json.dumps({"freshJvmPerCase": True, "matchedRepeats": 2, "cases": cases}, indent=2) + "\n")
            for planned in cases:
                case, order, label = planned["probeCase"], planned["surfaceOrder"], planned["label"]
                result_path = output / f"java-pylon-{label}-result.json"
                code = run([java, "-XstartOnFirstThread", "--enable-native-access=ALL-UNNAMED",
                     "-Darcadium.present072.expectedHostedLayer=" + str(arguments.hosted_layer_experiment).lower(), "-cp",
                     os.pathsep.join((str(pylon_output), pylon["path"])),
                     "io.github.arcadiumclient.runtime.client.optimizations.backend.MetalPylonPresentationProbe",
                     str(library), str(source / "tests/reference_scene.metal"), str(result_path), case, str(order)],
                    f"java-pylon-{label}.log", None)
                result = json.loads(result_path.read_text(encoding="utf-8")) if result_path.is_file() else {}
                outcomes.append({**planned, "case": label, "exitCode": code, "passed": code == 0 and result.get("passed", False),
                                 "resultPresent": result_path.is_file(), "observedPumpStyle": result.get("pumpStyle"), "ownedProcessId": result.get("ownedProcessId")})
            suite = {"passed": all(result["passed"] for result in outcomes), "freshJvmPerCase": True,
                     "currentDisplayedGateUnchanged": True, "cases": outcomes}
            (output / "java-pylon-suite-result.json").write_text(json.dumps(suite, indent=2) + "\n", encoding="utf-8")
            pylon_status = "passed" if suite["passed"] else "failed"
        else:
            pylon_status = "skipped-no-device" if sys.platform == "darwin" else "compiled-not-applicable"
    readiness_status = "not-requested"
    if arguments.legacy_readiness_diagnostics:
        readiness_status = run_readiness_controls(run, output, source, java, pylon_output, pylon, library) if probe_code == 0 else "blocked-no-device"
    report = {"passed": ctest_code == 0 and below_code in (None, 0, 77) and skiko_status != "failed" and pylon_status != "failed" and handoff_status != "failed" and readiness_status in ("not-requested", "passed"), "platform": sys.platform, "architecture": arguments.arch,
              "orderedPassNativeProof": json.loads(pass_result.read_text()) if pass_result.is_file() else None,
              "metalLifetimeNativeProof": json.loads(lease_result.read_text()) if lease_result.is_file() else None,
              "actualSkikoMetalLeaseSmoke": skiko_status, "skikoInputSha256": skiko["sha256"] if skiko else None,
              "nativeCTestExitCode": ctest_code, "failedNativeTests": failed_tests,
              "hostedLayerExperiment": arguments.hosted_layer_experiment,
              "nativeSurfaceBelowProbe": below_code,
              "nativeCompiled": True, "metalDeviceTest": metal_status,
              "javaMetalDeviceTest": "skipped-no-device" if probe_code == 77 else "passed" if sys.platform == "darwin" else "not-applicable",
              "javaNativeAbiProbe": probe_code == 0, "presentationIntegrated": False,
              "nativeWindowPresentation": presentation_status, "presentationPixelReadbackVariant": presentation is not None,
              "unsupportedPresentationFfiProbe": presentation_probe,
              "pylonInput": pylon, "javaSdlMetalSurface": pylon_status,
              "actualSceneTextureHandoff": handoff_status,
              "legacyReadinessDiagnostics": readiness_status,
              "presentationTestingLibraryRequested": presentation_testing,
              "minecraftOrSkikoOrCeraIntegrated": False,
              "nativeLibrary": str(library), "commands": commands}
    (output / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"commands", "nativeLibrary"}}, indent=2))
    if ctest_code != 0 or below_code not in (None, 0, 77):
        raise RuntimeError("Native presentation DISPLAYED gate failed; independent probe results are preserved, not accepted as a suite pass")
    if skiko_status == "failed":
        raise RuntimeError("The actual pinned Skiko Metal lease smoke failed; preserve the owned terminal receipt")
    if pylon_status == "failed" or handoff_status == "failed":
        raise RuntimeError("Actual current-ticket presentation failed; all independent cohorts and original controls were preserved")
    if readiness_status not in ("not-requested", "passed"):
        raise RuntimeError("Opt-in readiness diagnostics failed; original control failures remain failures")


if __name__ == "__main__":
    main()
