from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import tomllib
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from test_forge_compat_runtime import child_environment, stop_owned


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def forced_stop_receipts(evidence):
    receipts = []
    for name in ("owned-termination.json", "forced-stop.json", "forced-termination.json", "controller-termination.json"):
        path = Path(evidence) / name
        if not path.exists():
            continue
        try:
            if path.stat().st_size > 65536:
                raise ValueError("Oversized termination receipt")
            value = json.loads(path.read_text(encoding="utf-8-sig"))
            if not isinstance(value, dict):
                raise ValueError("Invalid termination receipt")
            if name.startswith("forced-") or any(value.get(key) is True for key in ("forcedTermination", "forcedStop", "forced", "forceKilled", "killed")):
                receipts.append(name)
        except (OSError, ValueError):
            receipts.append(name)
    return receipts


def prepare(args):
    version = tomllib.loads((ROOT / "Cargo.toml").read_text())["workspace"]["package"]["version"]
    arguments = json.loads(args.reference.read_text(encoding="utf-8-sig"))
    assert arguments[arguments.index("--accessToken") + 1] == "0"
    assert arguments[arguments.index("--username") + 1] == "ArcadiumTest"
    assert not any(item == "--server" or item.startswith(("--server=", "--quickPlayMultiplayer")) for item in arguments), "Reference requests an external server"
    native = args.native_mods.resolve(strict=True)
    cera_quality = getattr(args, "cera_quality", False)
    frozen071 = getattr(args, "frozen071", False)
    assert not frozen071 or cera_quality, "Frozen071 mode is reserved for the matching Cera reference"
    catalog_path = ROOT / "build/release072/baseline/catalog.json" if frozen071 else ROOT / "packages/arcadium_common/assets/distribution/client-components.json"
    catalog_data = json.loads(catalog_path.read_text())
    catalog = catalog_data["components"] if frozen071 else catalog_data["1.8.9"]
    component_root = catalog_path.parent / "components" if frozen071 else catalog_path.parent
    brand = (args.brand_jar or (component_root if frozen071 else native) / "ArcadiumBrand-1.8.9.jar").resolve(strict=True)
    assert args.brand_sha256 and digest(brand) == args.brand_sha256, "Use the selected control's verified normal Brand jar and SHA-256"
    with zipfile.ZipFile(brand) as archive:
        brand_meta = json.loads(archive.read("fabric.mod.json"))
    assert brand_meta["id"] == "arcadiumclient", "The normal Arcadium Brand component is required"
    runtime = args.runtime_jar or (component_root / "ArcadiumFeatures-1.8.9.jar" if frozen071 else ROOT / f"client-runtime/build/libs/ArcadiumRuntime-{version}.jar")
    qa = args.qa_jar or ROOT / f"client-runtime/build/libs/ArcadiumRuntime-{version}-release-qa.jar"
    if frozen071:
        assert catalog_data["version"] == "0.7.1"
        pins = {entry["file"]: entry["sha256"] for entry in catalog}
        assert digest(runtime) == pins["ArcadiumFeatures-1.8.9.jar"] and args.brand_sha256 == pins["ArcadiumBrand-1.8.9.jar"]
    native_quality = args.modern_quality or args.resolved_quality or cera_quality
    probe = args.probe_jar.resolve(strict=True) if not native_quality else None
    for path in [runtime, qa]:
        assert path.is_file(), str(path)
    fixture = args.fixture or Path(tempfile.gettempdir()) / ("ArcadiumForgePose072-" + uuid.uuid4().hex[:10])
    fixture = fixture.resolve()
    assert not fixture.exists(), "Use a fresh fixture path"
    game, mods = fixture / "game", fixture / "mods"
    game.mkdir(parents=True); mods.mkdir()
    receipt = fixture / "owned-fixture.json"
    kind = "cera-material-quality" if cera_quality else "resolved-instance-quality" if args.resolved_quality else "modern-glint-quality" if args.modern_quality else "forge-render-probe"
    write(receipt, {"syntheticOffline": True, "kind": kind, "root": str(fixture)})
    own = {entry["file"] for entry in catalog} | {"ArcadiumBrand-1.8.9.jar", "ArcadiumReleaseQa.jar", "ArcadiumFeatureTest-dev.jar"}
    for source in native.glob("*.jar"):
        if source.name in own or source.name.startswith(("ArcadiumGuiQa", "ArcadiumFeatureTest", "ArcadiumRenderProbe")):
            continue
        with zipfile.ZipFile(source) as archive:
            if "fabric.mod.json" not in archive.namelist():
                continue
            if json.loads(archive.read("fabric.mod.json")).get("id") == "arcadiumclient":
                continue
        shutil.copy2(source, mods / source.name)
    for entry in catalog:
        if entry["file"] in {"ArcadiumFeatures-1.8.9.jar", "ArcadiumBrand-1.8.9.jar"}:
            continue
        source = component_root / entry["file"]
        assert digest(source) == entry["sha256"]
        shutil.copy2(source, mods / source.name)
    shutil.copy2(runtime, mods / "ArcadiumFeatures-1.8.9.jar")
    shutil.copy2(brand, mods / "ArcadiumBrand-1.8.9.jar")
    if probe:
        shutil.copy2(probe, mods / "ArcadiumRenderProbe-dev.jar")
    with zipfile.ZipFile(qa) as source, zipfile.ZipFile(mods / "ArcadiumReleaseQa.jar", "w", zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            raw = source.read(entry)
            if entry.filename == "arcadium-release-qa.mixins.json":
                meta = json.loads(raw)
                meta["client"] = ["ForgePose072GameProbe", "ForgePose072WorldProbe", "ForgePose072GpuProbe", "ForgePose072CaptureProbe", "ForgePose072BatchProbe",
                                  "ForgePose072PartReference", "ForgePose072SubmitReference", "ForgePose072GlintProbe", "ForgePose072NativeEntry"]
                if args.modern_quality:
                    meta["client"] = ["ModernGlint072GameProbe", "ModernGlint072WorldProbe", "ModernGlint072ClockProbe", "ModernGlint072DrawProbe",
                                      "ModernGlint072NativePartProbe", "ModernGlint072CaptureProbe", "ModernGlint072NativePoseProbe", "ModernGlint072GlintProbe", "ModernGlint072PlayerProbe",
                                      "ModernGlint072ShadowProbe", "ModernGlint072ShadowBatchProbe", "ModernGlint072ColdCopyProbe",
                                      "PlayerPipeline072ChunksAccess", "PlayerPipeline072ChunkStateAccess", "PlayerPipeline072ChunkMapAccess"]
                if args.resolved_quality:
                    meta["client"] = ["ResolvedInstance072GameProbe", "ResolvedInstance072WorldProbe", "ResolvedInstance072BufferProbe", "ResolvedInstance072DriverProbe",
                                      "ResolvedInstance072ServiceProbe", "ResolvedInstance072MutableProbe", "ResolvedInstance072SortProbe", "ResolvedInstance072MaterialProbe", "ResolvedInstance072ScreenClick"]
                if cera_quality:
                    meta["client"] = ["CeraAdmission072GameProbe", "CeraAdmission072WorldProbe", "CeraAdmission072PlayerProbe", "CeraAdmission072SubmitProbe",
                                      "CeraAdmission072ClockProbe", "CeraAdmission072VoxelProbe", "CeraAdmission072NativeListProbe", "CeraAdmission072AnalyticProbe",
                                      "CeraAdmission072PoolProbe", "CeraAdmission072DrawProbe", "CeraAdmission072PreparationProbe", "CeraAdmission072LeaseProbe", "PlayerPipeline072ChunksAccess", "PlayerPipeline072ChunkStateAccess", "PlayerPipeline072ChunkMapAccess"]
                for mixin in meta["client"]:
                    assert meta["package"].replace(".", "/") + "/" + mixin + ".class" in source.namelist(), "Selected QA class is absent: " + mixin
                raw = json.dumps(meta).encode()
            target.writestr(entry.filename, raw)
    cache = game / ".arcadium/forge-compat"
    cache.mkdir(parents=True)
    official = ROOT / "build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"
    assert digest(official) == "596512ad5f12f95d8a3170321543d4455d23b8fe649c68580c5f828fe74f6668"
    shutil.copy2(official, cache / "forge-1.8.9-11.15.1.2318-universal.jar")
    if args.forge_cache:
        for name, checksum in [("lzma-0.0.1.jar", "0c3085cb93c3ef95d731c274b6f2dd195aa0405fe9328d69cab7f5e899d5e124"),
                               ("vecmath-1.5.2.jar", "09adf7955baa6bddf57a4bdbf9a91cf690261314969b59a18085ece1e05eccf9")]:
            source = args.forge_cache / "libraries" / name
            assert digest(source) == checksum
            (cache / "libraries").mkdir(exist_ok=True)
            shutil.copy2(source, cache / "libraries" / name)
    arguments = [item for item in arguments if not item.startswith(("-Darcadium.", "-Dfabric.modsFolder=", "-Dfabric.gameJarPath.", "-Duser.home=", "-Djava.io.tmpdir="))]
    cp = arguments.index("-cp") + 1
    entries = arguments[cp].split(os.pathsep)
    compat = mods / "ArcadiumForgeCompat-1.8.9.jar"
    entries = [str(compat) if Path(item).name.startswith("ArcadiumForgeCompat") else item for item in entries]
    if str(compat) not in entries:
        entries.append(str(compat))
    arguments[cp] = os.pathsep.join(entries)
    mains = [item for item in arguments if item in {"io.github.arcadiumclient.forgecompat.ArcadiumLaunch", "net.fabricmc.loader.impl.launch.knot.KnotClient"}]
    assert len(mains) == 1
    arguments[arguments.index(mains[0])] = "net.fabricmc.loader.impl.launch.knot.KnotClient" if native_quality else "io.github.arcadiumclient.forgecompat.ArcadiumLaunch"
    home, temporary = fixture / "home", fixture / "tmp"
    home.mkdir(); temporary.mkdir()
    evidence = (args.evidence or ROOT / "build/release072/forge-render" / fixture.name).resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    properties = {"fabric.modsFolder": str(mods), "arcadium.launcherDataDir": str(fixture / "profile"), "arcadium.launcherExecutable": "",
                  "arcadium.usageEndpoint": "", "arcadium.presenceEndpoint": "", "arcadium.forgePose072.output": str(evidence / "result.json"),
                  "user.home": str(home), "java.io.tmpdir": str(temporary), "http.proxyHost": "127.0.0.1", "http.proxyPort": "9",
                  "https.proxyHost": "127.0.0.1", "https.proxyPort": "9", "socksProxyHost": "127.0.0.1", "socksProxyPort": "9"}
    if args.modern_quality:
        properties.pop("arcadium.forgePose072.output")
        properties["arcadium.modernGlint072.output"] = str(evidence / "result.json")
        properties["arcadium.modernGlint072.experimental"] = "true"
        properties["mixin.dumpTargetOnFailure"] = "true"
    if args.resolved_quality:
        properties.pop("arcadium.forgePose072.output")
        properties["arcadium.resolved072.output"] = str(evidence / "result.json")
        properties["mixin.dumpTargetOnFailure"] = "true"
    if cera_quality:
        properties.pop("arcadium.forgePose072.output")
        properties["arcadium.ceraAdmission072.output"] = str(evidence / "result.json")
        properties["arcadium.ceraAdmission072.baseline071"] = str(frozen071).lower()
        baseline_directory = getattr(args, "baseline_reference_dir", None)
        if not frozen071:
            assert baseline_directory is not None, "A successfully completed matching frozen071 Cera reference is required"
            baseline_directory = baseline_directory.resolve(strict=True)
            reference_result = json.loads((baseline_directory / "result.json").read_text())
            reference_run = json.loads((baseline_directory / "run.json").read_text())
            assert reference_result.get("frozen071Control") and reference_result.get("passed") and reference_run.get("passed") and reference_run.get("naturalExit") and not reference_run.get("forcedTermination")
            properties["arcadium.ceraAdmission072.baselineDirectory"] = str(baseline_directory)
    arguments[1:1] = [f"-D{key}={value}" for key, value in properties.items()]
    if args.armor_glint:
        arguments.insert(1, "-Darcadium.forgePose072.armorGlint=" + args.armor_glint)
    for key, value in {"--gameDir": str(game), "--username": "ArcadiumTest", "--accessToken": "0", "--uuid": uuid.uuid3(uuid.NAMESPACE_OID, fixture.name).hex,
                       "--version": "Arcadium-ForgePose072-QA", "--width": "1280", "--height": "720"}.items():
        if key in arguments: arguments[arguments.index(key) + 1] = value
        else: arguments.extend([key, value])
    (game / "options.txt").write_text("lang:en_US\npauseOnLostFocus:false\nrenderDistance:3\nguiScale:2\nmaxFps:120\nenableVsync:false\nshowInventoryAchievementHint:false\n", encoding="utf-8")
    command = fixture / "command.json"; write(command, arguments)
    state = {"prepared": True, "syntheticOffline": True, "fixture": str(fixture), "game": str(game), "evidence": str(evidence),
             "command": str(command), "commandSha256": digest(command), "runtimeSha256": digest(runtime), "qaSha256": digest(qa),
             "probeSha256": digest(probe) if probe else None, "actualForgeLaunch": not native_quality,
             "normalBrand": {"file": "ArcadiumBrand-1.8.9.jar", "id": "arcadiumclient", "version": str(brand_meta["version"]), "sha256": args.brand_sha256, "matchesSelectedControl": True},
             "frozen071Control": frozen071, "componentCatalogSha256": digest(catalog_path),
             "kind": kind, "externalServerConnectionRequested": False}
    write(evidence / "fixture.json", state)
    print(json.dumps(state, indent=2), flush=True)
    return evidence / "fixture.json"


def run(receipt, timeout):
    state = json.loads(Path(receipt).read_text())
    assert state["syntheticOffline"] and not state["externalServerConnectionRequested"]
    fixture = Path(state["fixture"]); evidence = Path(state["evidence"]); command = Path(state["command"])
    kind = json.loads((fixture / "owned-fixture.json").read_text())["kind"]
    assert kind in {"forge-render-probe", "modern-glint-quality", "resolved-instance-quality", "cera-material-quality"}
    assert state["actualForgeLaunch"] == (kind == "forge-render-probe")
    brand = state.get("normalBrand")
    assert brand and brand["id"] == "arcadiumclient" and brand["matchesSelectedControl"], "Prepare a Brand-inclusive fixture"
    brand_path = fixture / "mods" / brand["file"]
    assert digest(brand_path) == brand["sha256"], "The normal Brand component changed after preparation"
    assert digest(command) == state["commandSha256"]
    assert not (evidence / "console.log").exists(), "This prepared fixture was already run"
    arguments = json.loads(command.read_text())
    started = time.monotonic()
    with (evidence / "console.log").open("wb") as log:
        process = subprocess.Popen(arguments, cwd=state["game"], env=child_environment(Path(state["game"])), stdin=subprocess.DEVNULL,
                                   stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        write(evidence / "process.json", {"pid": process.pid, "fixture": str(fixture)})
        try:
            while process.poll() is None and time.monotonic() - started < timeout:
                time.sleep(1)
            natural = process.poll() is not None
            if not natural:
                marker = evidence / "controller-termination.json"
                if not marker.exists():
                    write(marker, {"pid": process.pid, "forcedTermination": True, "reason": "Controller timeout", "timeoutSeconds": timeout})
                stop_owned(process)
            code = process.wait(timeout=15)
        finally: stop_owned(process)
    for image in (Path(state["game"]) / "screenshots").glob("forge-pose072-*.png"):
        shutil.copy2(image, evidence / image.name)
    result = json.loads((evidence / "result.json").read_text()) if (evidence / "result.json").is_file() else {}
    scene_passed = bool(result.get("passed", False))
    forced = forced_stop_receipts(evidence)
    natural = natural and not forced
    verified = process.poll() is not None
    proof = {"passed": scene_passed and natural and code == 0 and verified, "scenePassed": scene_passed, "naturalExit": natural,
             "forcedTermination": bool(forced), "terminationEvidence": forced,
             "terminationVerified": verified, "exitCode": code, "seconds": time.monotonic() - started}
    write(evidence / "run.json", proof)
    print(json.dumps(proof, indent=2), flush=True)
    if not proof["passed"]: raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description="Prepare or run an isolated real Forge player-render callback test.")
    parser.add_argument("--reference", type=Path)
    parser.add_argument("--native-mods", type=Path)
    parser.add_argument("--brand-jar", type=Path)
    parser.add_argument("--brand-sha256", help="SHA-256 of the selected control's normal Arcadium Brand component.")
    parser.add_argument("--forge-cache", type=Path)
    parser.add_argument("--runtime-jar", type=Path)
    parser.add_argument("--qa-jar", type=Path)
    parser.add_argument("--probe-jar", type=Path, default=ROOT / "build/forge-render-probe/ArcadiumRenderProbe-dev.jar")
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--evidence", type=Path)
    parser.add_argument("--run-existing", type=Path)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--armor-glint", choices=["VANILLA", "V1_8", "V1_15", "V1_19_4"])
    quality = parser.add_mutually_exclusive_group()
    quality.add_argument("--modern-quality", action="store_true", help="No Forge guests; compare original and captured modern armor glint within each displayed frame.")
    quality.add_argument("--resolved-quality", action="store_true", help="Check native resolved instance reuse, unchanged upload side effects and exact GPU data/images.")
    quality.add_argument("--cera-quality", action="store_true", help="Compare owned emissive skin/armor callbacks, ordinary cached-skin GPU draws and full native reference images.")
    parser.add_argument("--frozen071", action="store_true", help="Use every component from the pinned0.7.1 Cera reference catalog.")
    parser.add_argument("--baseline-reference-dir", type=Path)
    parser.add_argument("--timeout", type=int, default=420)
    args = parser.parse_args()
    if args.run_existing:
        run(args.run_existing, args.timeout)
    else:
        if not args.reference or not args.native_mods: parser.error("Preparation requires --reference and --native-mods")
        receipt = prepare(args)
        if args.run: run(receipt, args.timeout)


if __name__ == "__main__":
    main()
