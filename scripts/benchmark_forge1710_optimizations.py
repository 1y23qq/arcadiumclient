"""Compare isolated Forge 1.7.10 rendering and world-retention scenarios."""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import statistics
import struct
import subprocess
import time


ROOT = Path(__file__).resolve().parents[1]
MARKER = ".arcadium-optimization-benchmark"


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def copy(source: Path, target: Path, immutable: bool = False) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if immutable:
        try:
            os.link(source, target)
            return
        except OSError:
            pass
    shutil.copy2(source, target)


def prepare(args: argparse.Namespace) -> list[str]:
    source, profile = args.template.resolve(), args.profile.resolve()
    if not (source / ".arcadium-startup-benchmark").is_file():
        raise RuntimeError("The template must be an isolated startup benchmark profile")
    if profile.exists():
        if args.reuse_profile and (profile / MARKER).is_file():
            if args.components or args.configs:
                raise RuntimeError("Cache-reuse checks may add mods but must preserve components and configs")
            command = json.loads((profile / "command.json").read_text(encoding="utf-8"))
            command = [arg for arg in command if not arg.startswith("-Darcadium.qa.optimization.")]
            for jar in args.extra_mod:
                target = profile / "mods" / jar.name
                if target.exists():
                    if digest(target) != digest(jar):
                        raise RuntimeError("A cache-reuse input changed under an existing filename")
                else:
                    copy(jar, target, immutable=True)
            configure(command, args)
            snapshot(profile, source, args.variant)
            return command
        raise RuntimeError("Use a new optimization benchmark directory")
    profile.mkdir(parents=True)
    (profile / MARKER).touch()
    (profile / ".arcadium-forge-probe").touch()
    (profile / "mods").mkdir()
    for directory in ["config", "oneconfig", "profiles", "arcadium", "angelica", "falsepattern"]:
        if (source / directory).is_dir():
            shutil.copytree(source / directory, profile / directory)
    for filename in ["options.txt", "optionsnf.txt"]:
        if (source / filename).is_file():
            copy(source / filename, profile / filename)
    all_unimixins = any(jar.name.lower().startswith("+unimixins-all-") for jar in args.extra_mod)
    for jar in (source / "mods").glob("*.jar"):
        if all_unimixins and jar.name.lower().startswith("+unimixins-"):
            continue
        if "Qa" not in jar.name and not jar.name.startswith(("NotEnoughItems-", "CodeChickenCore-")):
            copy(jar, profile / "mods" / jar.name, immutable=not jar.name.startswith("Arcadium"))
    component_source = args.components.resolve() if args.components else source
    for filename, destination in [("ArcadiumForge-1.7.10.jar", profile / "mods/ArcadiumForge-1.7.10.jar"),
                                   ("ArcadiumBootstrap-1.7.10.jar", profile / "ArcadiumBootstrap-1.7.10.jar")]:
        original = component_source / filename
        if not original.is_file() and not args.components:
            original = component_source / "mods" / filename
        if not original.is_file():
            raise RuntimeError(f"Missing component: {original}")
        copy(original, destination)
    qa = max((ROOT / "client-forge1710/build/libs").glob("ArcadiumForgeQa-*.jar"), key=lambda path: path.stat().st_mtime)
    copy(qa, profile / "mods/ArcadiumForgeQa.jar")
    for jar in args.extra_mod:
        copy(jar, profile / "mods" / jar.name, immutable=True)
    if args.configs:
        shutil.copytree(args.configs, profile / "config", dirs_exist_ok=True)
    command = json.loads((source / "command.json").read_text(encoding="utf-8"))
    command = [arg for arg in command if not arg.startswith(("-Darcadium.startupQa.", "-Darcadium.qa.startupOnly="))]
    command = [arg.replace(str(source), str(profile)).replace(source.as_posix(), profile.as_posix()) for arg in command]
    for option, value in [("--gameDir", str(profile)), ("--username", "ArcadiumForgeQA"), ("--accessToken", "0"),
                           ("--width", "1280"), ("--height", "720")]:
        if option in command:
            command[command.index(option) + 1] = value
        else:
            command.extend([option, value])
    configure(command, args)
    snapshot(profile, source, args.variant)
    return command


def configure(command: list[str], args: argparse.Namespace) -> None:
    command.insert(1, f"-Darcadium.qa.optimization.variant={args.variant}")
    command.insert(1, f"-Darcadium.qa.optimization.warmupSeconds={args.warmup}")
    command.insert(1, f"-Darcadium.qa.optimization.sampleSeconds={args.seconds}")
    if args.expected_mod:
        command.insert(1, "-Darcadium.qa.optimization.expectedMods=" + ",".join(args.expected_mod))
    write(args.profile.resolve() / "command.json", command)


def snapshot(profile: Path, source: Path, variant: str) -> None:
    write(profile / "inputs.json", {
        "synthetic": True, "template": str(source), "variant": variant,
        "world": "integrated superflat, seed 71; 753 authoritative stone blocks, 96 client items, fixed client light updates",
        "files": [{"file": p.name, "sha256": digest(p)} for p in sorted((profile / "mods").glob("*.jar"))],
        "bootstrapSha256": digest(profile / "ArcadiumBootstrap-1.7.10.jar"),
        "configs": [{"file": p.relative_to(profile / "config").as_posix(), "sha256": digest(p)}
                    for p in sorted((profile / "config").rglob("*")) if p.is_file()],
    })


class MemorySampler:
    class Counters(ctypes.Structure):
        _fields_ = [("cb", wintypes.DWORD), ("pageFaultCount", wintypes.DWORD),
                    *[(name, ctypes.c_size_t) for name in ["peakWorkingSet", "workingSet", "quotaPeakPagedPool",
                      "quotaPagedPool", "quotaPeakNonPagedPool", "quotaNonPagedPool", "pagefileUsage",
                      "peakPagefileUsage", "privateUsage"]]]

    def __init__(self, pid: int):
        self.kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        self.psapi = ctypes.WinDLL("psapi", use_last_error=True)
        self.kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        self.kernel.OpenProcess.restype = wintypes.HANDLE
        self.psapi.GetProcessMemoryInfo.argtypes = [wintypes.HANDLE, ctypes.POINTER(self.Counters), wintypes.DWORD]
        self.kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        self.handle = self.kernel.OpenProcess(0x1000 | 0x0010, False, pid)

    def sample(self) -> dict | None:
        counters = self.Counters()
        counters.cb = ctypes.sizeof(counters)
        if not self.handle or not self.psapi.GetProcessMemoryInfo(self.handle, ctypes.byref(counters), counters.cb):
            return None
        return {"timeMillis": time.time() * 1000, "workingSetBytes": counters.workingSet,
                "privateBytes": counters.privateUsage, "peakWorkingSetBytes": counters.peakWorkingSet}

    def close(self) -> None:
        if self.handle:
            self.kernel.CloseHandle(self.handle)


def validate(output: Path, result: dict) -> None:
    if not result.get("passed") or result.get("phase") != "complete":
        raise RuntimeError(f"The optimization scenario failed: {result.get('error')}")
    if result.get("renderDistance") != 8 or result.get("fpsLimitSetting") != 1000 or result.get("vsync") is not False:
        raise RuntimeError("Unexpected video settings in the benchmark")
    if result.get("width") != 1280 or result.get("height") != 720:
        raise RuntimeError("The benchmark did not render at 1280x720")
    if [sample["scene"] for sample in result["samples"]] != ["terrain", "items", "light-updates"]:
        raise RuntimeError("A benchmark scene is missing")
    if not result.get("hudRendered") or not result.get("noRenderPreservesBlockData") or len(result["unloads"]) != 3:
        raise RuntimeError("A world/HUD regression check did not complete")
    for sample in result["samples"]:
        path = output.with_name(output.name + "." + sample["scene"] + ".frames")
        values = [item[0] / 1_000_000 for item in struct.iter_unpack(">q", path.read_bytes())]
        if len(values) != sample["frames"] or min(values) <= 0:
            raise RuntimeError("Invalid raw frame evidence")
        if not math.isclose(statistics.fmean(values), sample["meanFrameMs"], rel_tol=1e-8):
            raise RuntimeError("Frame summary disagrees with raw samples")
        ordered = sorted(values)
        expected = {"averageFps": 1000 / statistics.fmean(values),
                    "frameMsP50": ordered[len(ordered) // 2],
                    "frameMsP95": ordered[int((len(ordered) - 1) * .95)],
                    "frameMsP99": ordered[int((len(ordered) - 1) * .99)],
                    "onePercentLowFps": 1000 / statistics.fmean(ordered[-max(1, len(ordered) // 100):])}
        if any(not math.isclose(value, sample[key], rel_tol=1e-8) for key, value in expected.items()):
            raise RuntimeError("A frame percentile disagrees with raw samples")
        if sample["scene"] == "items" and sample.get("activeFixtureItems") != result["fixtureItems"]:
            raise RuntimeError("The item fixture did not remain loaded")
        if sample.get("worldEntityCount") != 1 + sample.get("activeFixtureItems", 0):
            raise RuntimeError("Unexpected natural entities changed the benchmark workload")
        if any(not math.isclose(actual, expected, abs_tol=.01) for actual, expected in zip(sample["camera"], result["camera"])):
            raise RuntimeError("The benchmark camera moved during a sampled scene")
        if sample.get("geometryBlocks") != 753:
            raise RuntimeError("The authoritative benchmark geometry changed")
        if sample["scene"] == "light-updates" and sample.get("lightChanges", 0) <= 0:
            raise RuntimeError("The light-update fixture did not run")


def run(profile: Path, command: list[str], output: Path) -> dict:
    if not (profile / MARKER).is_file():
        raise RuntimeError("Refusing to run an unmarked benchmark directory")
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise RuntimeError("Refusing to overwrite benchmark evidence")
    command = [*command]
    command.insert(1, f"-Darcadium.qa.optimization.output={output}")
    write(output.with_name(output.stem + "-command.json"), command)
    shutil.copy2(profile / "inputs.json", output.with_name(output.stem + "-inputs.json"))
    memory = []
    gpu = None
    try:
        from benchmark_forge_compat import GpuSampler
        gpu = GpuSampler()
    except (OSError, AssertionError, AttributeError):
        pass
    with output.with_suffix(".log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=profile, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        write(output.with_name(output.stem + "-process.json"), {"pid": process.pid, "profile": str(profile), "owned": True})
        sampler = MemorySampler(process.pid) if os.name == "nt" else None
        if gpu:
            gpu.thread.start()
        started = time.monotonic()
        previous = None
        try:
            while process.poll() is None:
                if time.monotonic() - started > 360:
                    raise TimeoutError("The optimization scenario exceeded six minutes")
                if sampler:
                    value = sampler.sample()
                    if value:
                        memory.append(value)
                if output.is_file():
                    try:
                        value = json.loads(output.read_text(encoding="utf-8"))
                        phase = (value.get("phase"), value.get("stage"))
                        if phase != previous:
                            print(f"{profile.name}: {phase}", flush=True)
                            previous = phase
                    except (OSError, ValueError):
                        pass
                time.sleep(.25)
            if not output.is_file():
                raise RuntimeError(f"The game exited without a result: {output.with_suffix('.log')}")
            result = json.loads(output.read_text(encoding="utf-8"))
            result["exitCode"] = process.returncode
            validate(output, result)
            if process.returncode != 0:
                raise RuntimeError("The benchmark process did not exit successfully")
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=20)
            if sampler:
                sampler.close()
            if gpu:
                gpu.close()
            write(output.with_name(output.stem + "-memory.json"), memory)
            if gpu:
                write(output.with_name(output.stem + "-gpu.json"), gpu.data)
    for sample in result["samples"]:
        active = [value for value in memory if sample["startedMillis"] <= value["timeMillis"] <= sample["endedMillis"]]
        if active:
            sample["peakWorkingSetBytes"] = max(value["workingSetBytes"] for value in active)
            sample["meanPrivateBytes"] = statistics.fmean(value["privateBytes"] for value in active)
        active_gpu = [value for value in gpu.data if sample["startedMillis"] <= value["timeMillis"] <= sample["endedMillis"]] if gpu else []
        if active_gpu:
            sample["meanGpuPercent"] = statistics.fmean(value["gpuPercent"] for value in active_gpu)
            sample["meanGpuClockMHz"] = statistics.fmean(value["clockMHz"] for value in active_gpu)
    result["postRunConfigs"] = [{"file": p.relative_to(profile / "config").as_posix(), "sha256": digest(p)}
                                 for p in sorted((profile / "config").rglob("*")) if p.is_file()]
    write(output, result)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--variant", required=True)
    parser.add_argument("--components", type=Path)
    parser.add_argument("--extra-mod", type=Path, action="append", default=[])
    parser.add_argument("--configs", type=Path)
    parser.add_argument("--warmup", type=int, default=8)
    parser.add_argument("--seconds", type=int, default=12)
    parser.add_argument("--reuse-profile", action="store_true")
    parser.add_argument("--expected-mod", action="append", default=[])
    args = parser.parse_args()
    command = prepare(args)
    run(args.profile.resolve(), command, args.output.resolve())
