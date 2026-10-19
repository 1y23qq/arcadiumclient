#!/usr/bin/env python3
"""Compare the shipped pre-Forge client and current compatibility component."""

import argparse
import ctypes
import datetime
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import statistics
import struct
import subprocess
import threading
import time
import uuid
import zipfile

from test_forge_compat_runtime import VERSION, child_environment, copy_verified, excluded_native, extract_cache_references, java_executable, read_json, sha256, stop_owned, write_json


ROOT = Path(__file__).resolve().parents[1]
COMPONENTS = ("ArcadiumBrand-1.8.9.jar", "ArcadiumCore-1.8.9.jar", "ArcadiumFeatures-1.8.9.jar", "ArcadiumRenderer-1.8.9.jar")
VARIANTS = ("before", "after", "empty-forge", "unfixed-forge")


class GpuSampler:
    def __init__(self):
        self.data = []
        self.stop = threading.Event()
        self.library = ctypes.CDLL(str(Path(os.environ["SYSTEMROOT"]) / "System32/nvml.dll"))
        self.library.nvmlDeviceGetHandleByIndex_v2.argtypes = [ctypes.c_uint, ctypes.POINTER(ctypes.c_void_p)]
        assert self.library.nvmlInit_v2() == 0
        self.handle = ctypes.c_void_p()
        assert self.library.nvmlDeviceGetHandleByIndex_v2(0, ctypes.byref(self.handle)) == 0
        self.thread = threading.Thread(target=self.run, daemon=True)

    def run(self):
        class Rates(ctypes.Structure):
            _fields_ = [("gpu", ctypes.c_uint), ("memory", ctypes.c_uint)]
        while not self.stop.is_set():
            rates, temperature, clock, power = Rates(), ctypes.c_uint(), ctypes.c_uint(), ctypes.c_uint()
            results = [self.library.nvmlDeviceGetUtilizationRates(self.handle, ctypes.byref(rates)),
                       self.library.nvmlDeviceGetTemperature(self.handle, 0, ctypes.byref(temperature)),
                       self.library.nvmlDeviceGetClockInfo(self.handle, 0, ctypes.byref(clock)),
                       self.library.nvmlDeviceGetPowerUsage(self.handle, ctypes.byref(power))]
            if not any(results):
                self.data.append({"timeMillis": time.time() * 1000, "gpuPercent": rates.gpu,
                                  "memoryPercent": rates.memory, "temperature": temperature.value,
                                  "clockMHz": clock.value, "powerWatts": power.value / 1000})
            self.stop.wait(0.5)

    def close(self):
        self.stop.set()
        self.thread.join(timeout=3)
        self.library.nvmlShutdown()


def inputs(base, args):
    destination = base / "baseline-components"
    destination.mkdir()
    components = []
    for name in COMPONENTS:
        data = (args.baseline_components / name).read_bytes()
        (destination / name).write_bytes(data)
        components.append({"file": name, "sha256": hashlib.sha256(data).hexdigest()})
    write_json(base / "baseline.json", {"source": "user-provided baseline components", "components": components})
    source = base / "empty-mod-src/io/github/arcadiumclient/perftest/EmptyMod.java"
    source.parent.mkdir(parents=True)
    source.write_text('package io.github.arcadiumclient.perftest;\n'
                      '@net.minecraftforge.fml.common.Mod(modid="arcadiumemptyfixture", name="Arcadium Empty Fixture", version="1.0", clientSideOnly=true)\n'
                      'public final class EmptyMod { public EmptyMod() {} }\n', encoding="utf-8")
    classes = base / "empty-mod-classes"
    java = java_executable(args.java)
    subprocess.run([str(java.parent / "javac.exe"), "--release", "8", "-cp",
                    str(ROOT / "build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"),
                    "-d", str(classes), str(source)], check=True, capture_output=True)
    with zipfile.ZipFile(base / "empty-mod.jar", "w", zipfile.ZIP_DEFLATED) as archive:
        path = classes / "io/github/arcadiumclient/perftest/EmptyMod.class"
        archive.write(path, "io/github/arcadiumclient/perftest/EmptyMod.class")
        archive.writestr("mcmod.info", '[{"modid":"arcadiumemptyfixture","name":"Arcadium Empty Fixture","version":"1.0"}]')


def prepare(base, name, variant, args):
    case = base / name
    game = case / "game"
    mods = game / "mods"
    mods.mkdir(parents=True)
    records = []
    for source in sorted(args.native_mods.glob("*.jar")):
        if not excluded_native(source):
            records.append(copy_verified(source, mods / source.name))
    native_count = len(records)
    components = base / "baseline-components" if variant == "before" else ROOT / "packages/arcadium_common/assets/distribution"
    for filename in COMPONENTS:
        records.append(copy_verified(components / filename, mods / filename))
    records.append(copy_verified(ROOT / f"game-branding/build/libs/ArcadiumPerformanceTest-{VERSION}.jar", mods / "ArcadiumPerformanceTest.jar"))
    references = extract_cache_references(args.reference)
    main = "net.fabricmc.loader.impl.launch.knot.KnotClient"
    classpath = list(references.classpath)
    if variant != "before":
        runtime = mods / "ArcadiumForgeCompat-1.8.9.jar"
        source = args.unfixed_compat if variant == "unfixed-forge" else components / runtime.name
        records.append(copy_verified(source, runtime))
        classpath.append(runtime)
        main = "io.github.arcadiumclient.forgecompat.ArcadiumLaunch"
    if variant in ("empty-forge", "unfixed-forge"):
        records.append(copy_verified(base / "empty-mod.jar", mods / "ArcadiumEmptyFixture.jar"))
    (game / "home").mkdir()
    (game / "options.txt").write_text("lang:en_US\npauseOnLostFocus:false\nrenderDistance:8\nguiScale:2\nmaxFps:260\nenableVsync:false\nfancyGraphics:true\nao:2\nfov:0.0\nshowInventoryAchievementHint:false\n", encoding="utf-8")
    properties = {"fabric.fixPackageAccess": "true", "fabric.gameVersion": "1.8.9",
                  "user.language": "en", "user.country": "US", "user.home": str(game / "home"),
                  "java.library.path": str(references.natives), "org.lwjgl.librarypath": str(references.natives),
                  "net.java.games.input.librarypath": str(references.natives), "arcadium.presenceEndpoint": "",
                  "arcadium.performanceTest": "true", "arcadium.performanceTest.gameDir": str(game),
                  "arcadium.performanceTest.variant": variant, "arcadium.performanceTest.warmup": str(args.warmup),
                  "arcadium.performanceTest.seconds": str(args.seconds),
                  "http.proxyHost": "127.0.0.1", "http.proxyPort": "9", "https.proxyHost": "127.0.0.1",
                  "https.proxyPort": "9", "socksProxyHost": "127.0.0.1", "socksProxyPort": "9"}
    command = [str(java_executable(args.java)), "-Xms1G", "-Xmx3G", "--enable-native-access=ALL-UNNAMED",
               "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED"]
    command.extend("-D" + key + "=" + value for key, value in properties.items())
    command.extend(["-cp", os.pathsep.join(map(str, classpath)), main, "--username", "ArcadiumTest", "--accessToken", "0",
                    "--uuid", "a3f11ad978693443923ef57284db7b7e", "--gameDir", str(game), "--assetsDir", str(references.assets),
                    "--assetIndex", "1.8", "--version", "Arcadium-Performance", "--versionType", "custom",
                    "--userType", "legacy", "--userProperties", "{}", "--width", "1280", "--height", "720"])
    write_json(case / "command.json", command)
    write_json(case / "fixture.json", {"variant": variant, "files": records, "nativeArchives": native_count,
                                       "emptyModOnly": variant in ("empty-forge", "unfixed-forge"), "world": "deterministic superflat with fixed stone columns"})
    return case


def run(case):
    game = case / "game"
    sampler = GpuSampler()
    started = time.time() * 1000
    with (case / "process.log").open("wb") as log:
        process = subprocess.Popen(read_json(case / "command.json"), cwd=game, env=child_environment(game),
                                   stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        write_json(case / "process.json", {"pid": process.pid, "startedMillis": started, "ownedGame": str(game)})
        sampler.thread.start()
        phase = None
        try:
            while process.poll() is None:
                if time.time() * 1000 - started > 370_000:
                    raise TimeoutError("Benchmark process exceeded its runtime limit")
                if any((game / "crash-reports").glob("crash-*.txt")):
                    raise RuntimeError("The benchmark game crashed")
                with (case / "process.log").open("rb") as stream:
                    stream.seek(max(0, os.fstat(stream.fileno()).st_size - 65536))
                    tail = stream.read()
                if b"net.fabricmc.loader.impl.FormattedException:" in tail:
                    raise RuntimeError("Fabric rejected the benchmark fixture; inspect process.log")
                result = game / "performance/result.json"
                if result.is_file():
                    try:
                        current = read_json(result)["phase"]
                        if current != phase:
                            phase = current
                            print(case.name + ": " + phase, flush=True)
                    except (OSError, ValueError):
                        pass
                time.sleep(0.5)
        finally:
            stop_owned(process)
            sampler.close()
    write_json(case / "gpu.json", sampler.data)
    result = read_json(game / "performance/result.json")
    if process.returncode != 0 or result.get("passed") is not True:
        raise RuntimeError("Benchmark failed: inspect " + str(case / "process.log"))
    result["processMillis"] = time.time() * 1000 - started
    return result


def summarize(case):
    result = read_json(case / "game/performance/result.json")
    assert result["passed"] is True and result["phase"] == "complete"
    assert result["serverObstructions"] == 0 and result["clientObstructions"] == 0
    expected = read_json(case / "fixture.json")["variant"]
    if result["forgePrepared"] != (expected in ("empty-forge", "unfixed-forge")):
        raise RuntimeError("Unexpected Forge activation state")
    gpu = read_json(case / "gpu.json")
    summary = {}
    for sample in result["samples"]:
        name = sample["scene"]
        raw = (case / "game/performance" / (name + ".frames")).read_bytes()
        nanos = [item[0] for item in struct.iter_unpack(">q", raw)]
        assert len(nanos) == sample["frames"] and min(nanos) > 0
        assert sample["width"] == 1280 and sample["height"] == 720 and sample["viewDistance"] == 8
        assert sample["fpsLimit"] == 260 and sample["vsync"] is False and sample["fov"] == 70
        assert sample["fixturePlayers"] == (100 if name == "players" else 0)
        assert sample["nonEmptyInventorySlots"] == 0
        assert sample["camera"] == [8.5, 8.5, 8.5, 0.0, 15.0]
        values = sorted(value / 1_000_000 for value in nanos)
        active_gpu = [row for row in gpu if sample["startedMillis"] <= row["timeMillis"] <= sample["endedMillis"]]
        mean = statistics.fmean(values)
        summary[name] = {"frames": len(values), "fps": 1000 / mean, "meanMs": mean,
                         "p50Ms": values[int((len(values) - 1) * .5)],
                         "p95Ms": values[int((len(values) - 1) * .95)],
                         "p99Ms": values[int((len(values) - 1) * .99)],
                         "onePercentLowFps": 1000 / statistics.fmean(values[-max(1, len(values) // 100):]),
                         "renderCpuMsPerFrame": sample["renderThreadCpuNanos"] / len(values) / 1_000_000,
                         "inactiveChecks": sample["inactiveChecks"], "gcMillis": sample["gcMillis"],
                         "jitCompilationMillis": sample["jitCompilationMillis"],
                         "gpuPercent": statistics.fmean(row["gpuPercent"] for row in active_gpu) if active_gpu else None,
                         "clockMHz": statistics.fmean(row["clockMHz"] for row in active_gpu) if active_gpu else None,
                         "temperature": statistics.fmean(row["temperature"] for row in active_gpu) if active_gpu else None,
                         "entityDebug": sample["entityDebug"], "chunkDebug": sample["chunkDebug"]}
    write_json(case / "summary.json", summary)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path)
    parser.add_argument("--java", type=Path, default=Path.home() / "AppData/Local/Temp/arcadium-jdk25/jdk-25.0.4.1+1/bin/java.exe")
    parser.add_argument("--pilot", action="store_true")
    parser.add_argument("--warmup", type=float, default=35)
    parser.add_argument("--seconds", type=float, default=25)
    parser.add_argument("--variants", nargs="+", choices=VARIANTS)
    parser.add_argument("--unfixed-compat", type=Path)
    parser.add_argument("--baseline-components", type=Path, required=True, help="Existing baseline components to compare; no private Git history is required")
    parser.add_argument("--native-mods", type=Path, required=True, help="Original pinned native mods from an isolated local game instance")
    parser.add_argument("--reference", type=Path, required=True, help="Captured command.json from an isolated local game fixture")
    args = parser.parse_args()
    if args.variants and "unfixed-forge" in args.variants and (args.unfixed_compat is None or not args.unfixed_compat.is_file()):
        parser.error("--unfixed-compat must point to the recorded pre-fix compatibility jar")
    if args.base is None:
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d-%H%M%S")
        base = ROOT / "build/forge-compat/performance" / (stamp + "-" + uuid.uuid4().hex[:8])
        base.mkdir(parents=True)
        inputs(base, args)
    else:
        base = args.base.resolve()
        base.relative_to((ROOT / "build/forge-compat/performance").resolve())
    print("Performance directory: " + str(base), flush=True)
    order = args.variants or (["before", "after", "empty-forge"] if args.pilot else ["before", "after", "empty-forge", "after", "empty-forge", "before", "empty-forge", "before", "after"])
    cases = []
    for index, variant in enumerate(order):
        name = ("pilot" if args.pilot else "trial") + "-" + str(index + 1) + "-" + variant + "-" + uuid.uuid4().hex[:5]
        case = prepare(base, name, variant, args)
        run(case)
        summary = summarize(case)
        cases.append({"case": name, "variant": variant, "summary": summary})
        print(json.dumps({"case": name, "fps": {scene: round(data["fps"], 1) for scene, data in summary.items()}}), flush=True)
        report = "pilot-results.json" if args.pilot else "results.json"
        write_json(base / report, {"baselineCommit": BASELINE, "warmupSeconds": args.warmup,
                                  "sampleSeconds": args.seconds, "order": order, "cases": cases})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
