#!/usr/bin/env python3
"""Prepare or run isolated offline Forge compatibility game fixtures."""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import tomllib
import unittest
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[1]
VERSION = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
MAIN = "io.github.arcadiumclient.forgecompat.ArcadiumLaunch"
COMPONENTS = ("ArcadiumBrand-1.8.9.jar", "ArcadiumCore-1.8.9.jar",
              "ArcadiumFeatures-1.8.9.jar", "ArcadiumRenderer-1.8.9.jar")
EXCLUDED_IDS = {"arcadiumclient", "arcadium_runtime", "oneconfig", "polyplus", "compose-multiplatform",
                "arcadium_feature_test", "arcadium_forge_compat"}
DIAGNOSTIC_SUPPORT = {"fabric-language-kotlin", "osl", "pylon"}
REGISTRY_SMOKE_ID = "arcadiumregistrysmoke"
GUESTS = {
    "JEI": ("jei_1.8.9-2.28.18.187.jar", "97cb1d5f6b4181315fea9e68f56dc5d38de977b63645f5756719835b9c17d3ca"),
    "journeymap": ("journeymap-1.8.9-5.1.5-unlimited.jar", "eff59b762290eb9842ba95ce976e41079b5caf6de7c7f5c1569c95ea97b09063"),
    "nei": ("NotEnoughItems-1.8.9-2.0.2.null-universal.jar", "1375fee5865235bcd0d95243fa1c95a99c478ef647d2ce486e805b6baa77fa24"),
    "keystrokes": ("raven-bS-17 (13).jar", "aa52dfa16d865ef8ed64d894ca83fb4087cfb0e73d53192c9940981c0f7616d8"),
    "wawla": ("Wawla-1.8.9-1.1.4.171.jar", "3abbbe0816d007f5ef81e98256c1efba70d8bccb24520796ef1e4902649772d2"),
    "CodeChickenCore": ("CodeChickenCore-1.8.9-2.0.2.null-universal.jar", "10eda9be9979f33a0f7f2e6d211ff4e7f332269d122e76e2cd14457b9943c9b3"),
    "CodeChickenLib": ("CodeChickenLib-1.8.9-2.0.2.1-universal.jar", "d34d7c499e0e89869412dd7a65cb181d3d4fda6aac798302f94b7e8bf1574f4d"),
    "Waila": ("Waila-1.6.0-B3_1.8.8.jar", "24d180953eed3491801cb02337e63b6edd2550f2fafcac9cd3aa16bfa9354f46"),
}


@dataclasses.dataclass(frozen=True)
class CacheReferences:
    classpath: tuple[Path, ...]
    natives: Path
    assets: Path


def read_json(path: Path, maximum: int = 2 * 1024 * 1024):
    if not path.is_file() or path.stat().st_size > maximum:
        raise ValueError(f"Missing or oversized JSON: {path.name}")
    with path.open("rb") as source:
        data = source.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError(f"Oversized JSON: {path.name}")
    return json.loads(data.decode("utf-8-sig"))


def extract_cache_references(path: Path) -> CacheReferences:
    arguments = read_json(path)
    if not isinstance(arguments, list) or not all(isinstance(value, str) for value in arguments):
        raise ValueError("The cache reference must contain an argv list")
    classpath_values = [arguments[i + 1] for i, value in enumerate(arguments[:-1])
                        if value in ("-cp", "-classpath", "--class-path")]
    native_values = [value.removeprefix("-Djava.library.path=") for value in arguments
                     if value.startswith("-Djava.library.path=")]
    asset_values = [arguments[i + 1] for i, value in enumerate(arguments[:-1]) if value == "--assetsDir"]
    if len(classpath_values) != 1 or len(native_values) != 1 or len(asset_values) != 1:
        raise ValueError("The reference must identify one classpath, native directory, and assets directory")
    raw = classpath_values[0]
    separator = ";" if ";" in raw else os.pathsep
    classpath = tuple(Path(value).resolve(strict=True) for value in raw.split(separator) if value)
    if not classpath or len(classpath) != len(set(classpath)) or not all(value.is_file() for value in classpath):
        raise ValueError("The cached JVM classpath is invalid")
    natives = Path(native_values[0]).resolve(strict=True)
    assets = Path(asset_values[0]).resolve(strict=True)
    if not natives.is_dir() or not assets.is_dir():
        raise ValueError("The native or assets cache is missing")
    return CacheReferences(classpath, natives, assets)


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def mod_metadata(path: Path) -> dict:
    if not path.is_file():
        raise ValueError(f"Required mod archive is missing: {path.name}")
    with zipfile.ZipFile(path) as archive:
        try:
            entry = archive.getinfo("fabric.mod.json")
        except KeyError:
            return {}
        if entry.file_size > 2 * 1024 * 1024:
            raise ValueError(f"Oversized mod metadata: {path.name}")
        metadata = json.loads(archive.read(entry))
    if not isinstance(metadata, dict) or not isinstance(metadata.get("id"), str):
        raise ValueError(f"Invalid Fabric mod metadata: {path.name}")
    return metadata


def excluded_native(path: Path) -> bool:
    name = path.name.casefold()
    if name.startswith(("arcadiumbrand-", "arcadiumcore-", "arcadiumfeatures-", "arcadiumrenderer-",
                        "arcadiumfeaturetest-", "arcadiumforgecompat-")):
        return True
    from package_client import EXCLUDED_MOD_IDS
    return mod_metadata(path).get("id") in EXCLUDED_IDS | EXCLUDED_MOD_IDS


def java_executable(argument: Path | None) -> Path:
    if argument is not None:
        return argument.resolve(strict=True)
    configured = os.environ.get("JAVA_HOME")
    if configured:
        candidate = Path(configured) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if candidate.is_file():
            return candidate.resolve()
    command = shutil.which("java")
    if command is None:
        raise ValueError("Java 25 was not found; supply --java")
    return Path(command).resolve(strict=True)


def write_json(path: Path, value) -> None:
    temporary = path.with_name(path.name + ".pending")
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temporary.replace(path)


def copy_verified(source: Path, destination: Path, expected: str | None = None) -> dict:
    if destination.exists():
        raise ValueError(f"Refusing to replace an existing fixture file: {destination.name}")
    digest = sha256(source)
    if expected is not None and digest != expected:
        raise ValueError(f"Guest archive checksum does not match the audited input: {source.name}")
    shutil.copy2(source, destination)
    if sha256(destination) != digest:
        raise ValueError(f"Fixture copy verification failed: {source.name}")
    return {"file": destination.name, "bytes": destination.stat().st_size, "sha256": digest}


def command(java: Path, references: CacheReferences, game: Path, runtime: Path,
            expected: list[str], native_ids: list[str], timeout: int, registry_smoke: bool = False,
            export_mixins: bool = False, render_stress: bool = False, resource_pack_qa: bool = False,
            play_menu_qa: bool = False) -> list[str]:
    home = game / "home"
    home.mkdir()
    properties = {
        "fabric.fixPackageAccess": "true",
        "fabric.gameVersion": "1.8.9",
        "user.language": "en",
        "user.country": "US",
        "java.library.path": str(references.natives),
        "org.lwjgl.librarypath": str(references.natives),
        "net.java.games.input.librarypath": str(references.natives),
        "user.home": str(home),
        "arcadium.presenceEndpoint": "",
        "arcadium.forgeQa": "true",
        "arcadium.forgeQa.gameDir": str(game),
        "arcadium.forgeQa.expected": ",".join(expected),
        "arcadium.forgeQa.nativeIds": ",".join(native_ids),
        "arcadium.forgeQa.timeoutSeconds": str(max(30, timeout - 15)),
        "arcadium.featureTest.exit": "true",
        "http.proxyHost": "127.0.0.1", "http.proxyPort": "9",
        "https.proxyHost": "127.0.0.1", "https.proxyPort": "9",
        "socksProxyHost": "127.0.0.1", "socksProxyPort": "9",
    }
    if registry_smoke:
        properties["arcadium.forgeQa.registrySmoke"] = "true"
    if render_stress:
        properties["arcadium.forgeQa.renderStress"] = "true"
    if resource_pack_qa:
        properties["arcadium.resourcePackQa"] = "true"
    if play_menu_qa:
        properties["arcadium.playMenuQa"] = "true"
    if export_mixins:
        properties.update({"mixin.debug.export": "true", "mixin.debug.export.filter": "net.minecraft.unmapped.*",
                           "mixin.debug.export.decompile": "false"})
    arguments = [str(java), "-Xms512M", "-Xmx3G", "--enable-native-access=ALL-UNNAMED",
                 "--add-opens=java.base/java.lang=ALL-UNNAMED",
                 "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED"]
    if sys.platform == "darwin":
        arguments.append("-XstartOnFirstThread")
    arguments.extend(f"-D{key}={value}" for key, value in properties.items())
    arguments.extend(["-cp", os.pathsep.join(map(str, (*references.classpath, runtime))), MAIN,
                      "--username", "ArcadiumTest", "--accessToken", "0",
                      "--uuid", uuid.uuid3(uuid.NAMESPACE_OID, str(game)).hex,
                      "--gameDir", str(game), "--assetsDir", str(references.assets),
                      "--assetIndex", "1.8", "--version", "Arcadium-" + VERSION,
                      "--versionType", "custom", "--userType", "legacy", "--userProperties", "{}",
                      "--width", "1280", "--height", "720"])
    return arguments


def child_environment(game: Path) -> dict[str, str]:
    allowed = {"PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "COMSPEC", "SYSTEMDRIVE", "PROGRAMFILES",
               "PROGRAMFILES(X86)", "PROCESSOR_ARCHITECTURE", "NUMBER_OF_PROCESSORS", "LANG",
               "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR", "DBUS_SESSION_BUS_ADDRESS"}
    result = {key: value for key, value in os.environ.items() if key.upper() in allowed or key.startswith("LC_")}
    folders = {"HOME": "home", "USERPROFILE": "home", "APPDATA": "fixture-env/appdata",
               "LOCALAPPDATA": "fixture-env/localappdata", "XDG_CONFIG_HOME": "fixture-env/config",
               "XDG_CACHE_HOME": "fixture-env/cache", "TMPDIR": "fixture-env/tmp", "TEMP": "fixture-env/tmp",
               "TMP": "fixture-env/tmp"}
    for key, relative in folders.items():
        directory = game / relative
        directory.mkdir(parents=True, exist_ok=True)
        result[key] = str(directory)
    return result


def attach_registry_smoke(name: str, source: Path | None, mods: Path, expected: list[str], files: list[dict]) -> bool:
    if name == "baseline" or source is None:
        return False
    if not source.is_file() or source.stat().st_size > 2 * 1024 * 1024:
        raise ValueError("The private registry smoke archive is missing or oversized")
    with zipfile.ZipFile(source) as archive:
        try:
            entry = archive.getinfo("mcmod.info")
        except KeyError as error:
            raise ValueError("The private registry smoke metadata is missing") from error
        if entry.file_size > 64 * 1024:
            raise ValueError("The private registry smoke metadata is oversized")
        metadata = json.loads(archive.read(entry))
        if not isinstance(metadata, list) or len(metadata) != 1 or not isinstance(metadata[0], dict) or metadata[0].get("modid") != REGISTRY_SMOKE_ID:
            raise ValueError("The private registry smoke archive has the wrong identity")
        required = {"arcadium/registry/smoke/RegistrySmoke.class", "arcadium/registry/smoke/RegistrySmokeBlock.class"}
        if not required.issubset(archive.namelist()):
            raise ValueError("The private registry smoke classes are missing")
    files.append(copy_verified(source, mods / "ArcadiumRegistrySmoke-dev.jar"))
    expected.append(REGISTRY_SMOKE_ID)
    return True


def prepare_case(base: Path, name: str, args, references: CacheReferences, java: Path) -> Path:
    case = base / name
    game = case / "game"
    mods = game / "mods"
    mods.mkdir(parents=True, exist_ok=False)
    files = []
    native_ids = []
    for source in sorted(args.native_mods.glob("*.jar")):
        if not excluded_native(source):
            if args.diagnostic_minimal and mod_metadata(source).get("id") not in DIAGNOSTIC_SUPPORT:
                continue
            files.append(copy_verified(source, mods / source.name))
    if not files:
        raise ValueError("The native mod cache is empty")
    for filename in COMPONENTS:
        source = args.components / filename
        metadata = mod_metadata(source)
        if metadata.get("id") not in EXCLUDED_IDS:
            raise ValueError(f"Unexpected component identity: {filename}")
        if filename == COMPONENTS[0] and metadata.get("version") != VERSION:
            raise ValueError(f"The current {VERSION} branding component must be packaged before game QA")
        native_ids.append(metadata["id"])
        files.append(copy_verified(source, mods / filename))
    if mod_metadata(args.qa_jar).get("id") != "arcadium_feature_test":
        raise ValueError("The development QA archive has the wrong identity")
    files.append(copy_verified(args.qa_jar, mods / "ArcadiumFeatureTest-dev.jar"))
    runtime_metadata = mod_metadata(args.compat_jar)
    if runtime_metadata.get("id") != "arcadium_forge_compat" or runtime_metadata.get("version") != VERSION:
        raise ValueError(f"The Forge compatibility component is not the current {VERSION} build")
    runtime = mods / "ArcadiumForgeCompat-1.8.9.jar"
    files.append(copy_verified(args.compat_jar, runtime))
    native_ids.append(runtime_metadata["id"])
    selected = [] if name == "baseline" else ["JEI"] if name == "jei" else ["keystrokes"] if name == "raven" else list(GUESTS)
    for mod_id in selected:
        filename, expected_hash = GUESTS[mod_id]
        source = (args.dependencies if mod_id in ("CodeChickenCore", "CodeChickenLib", "Waila") else args.guests) / filename
        files.append(copy_verified(source, mods / filename, expected_hash))
    expected = ["NotEnoughItems" if mod_id == "nei" else mod_id for mod_id in selected if mod_id != "CodeChickenLib"]
    registry_smoke = attach_registry_smoke(name, getattr(args, "registry_smoke_jar", None), mods, expected, files)
    vanilla_cache = getattr(args, "vanilla_client_cache", None)
    if vanilla_cache is not None and name != "baseline":
        with zipfile.ZipFile(vanilla_cache) as archive:
            world = archive.read("net/minecraft/unmapped/C_64041439.class")
            if b"getSkyColorBody" in world:
                raise ValueError("The seeded client cache must be an unpatched Minecraft 1.8.9 game")
        cache = game / ".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar"
        cache.parent.mkdir(parents=True, exist_ok=False)
        files.append(copy_verified(vanilla_cache, cache))
        realms = vanilla_cache.with_name("realms-intermediary.jar")
        if not realms.is_file():
            raise ValueError("The seeded vanilla cache must include its Realms companion archive")
        files.append(copy_verified(realms, cache.with_name(realms.name)))
    if not (references.assets / "indexes/1.8.json").is_file():
        raise ValueError("The Minecraft 1.8 asset index is missing")
    (game / "options.txt").write_text("lang:en_US\npauseOnLostFocus:false\nrenderDistance:4\nguiScale:2\nmaxFps:60\nenableVsync:false\nshowInventoryAchievementHint:false\n", encoding="utf-8")
    arguments = command(java, references, game.resolve(), runtime.resolve(), expected, native_ids, args.timeout, registry_smoke,
                        args.export_mixins, args.render_stress, args.resource_pack_qa, args.play_menu_qa)
    if getattr(args, "nei_overlay_probe", False):
        arguments.insert(1, "-Darcadium.forgeQa.neiOverlayProbe=true")
    write_json(case / "command.json", arguments)
    write_json(case / "fixture.json", {"format": 1, "status": "prepared", "case": name, "version": VERSION,
               "nativeProfile": "minimal-diagnostic" if args.diagnostic_minimal else "full",
               "offlineUsername": "ArcadiumTest", "accessToken": "0", "expectedForgeModIds": expected,
               "registrySmoke": registry_smoke,
               "nativeFabricModIds": native_ids, "nativeModCount": len(files) - len(selected) - int(registry_smoke),
               "guestArchiveCount": len(selected) + int(registry_smoke),
               "classpathCacheEntries": len(references.classpath), "files": files,
               "externalServerConnectionRequested": False, "accountHelperConfigured": False})
    return case


def png_dimensions(path: Path) -> tuple[int, int]:
    if not path.is_file() or path.stat().st_size < 100:
        raise ValueError(f"Screenshot is missing or incomplete: {path.name}")
    with path.open("rb") as source:
        header = source.read(24)
        source.seek(-12, os.SEEK_END)
        tail = source.read(12)
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR" or tail[4:8] != b"IEND":
        raise ValueError(f"Screenshot is not a completed PNG: {path.name}")
    return struct.unpack(">II", header[16:24])


def stop_owned(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def run_case(case: Path, timeout: int) -> dict:
    game = case / "game"
    arguments = read_json(case / "command.json")
    start = time.monotonic()
    status = {"case": case.name, "passed": False}
    output = game / "forge-qa/result.json"
    with (case / "process.log").open("wb") as log:
        process = subprocess.Popen(arguments, cwd=game, env=child_environment(game), stdin=subprocess.DEVNULL,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        write_json(case / "process.json", {"pid": process.pid, "gameDir": str(game), "startedUtc": dt.datetime.now(dt.timezone.utc).isoformat()})
        phase = None
        try:
            while process.poll() is None:
                if time.monotonic() - start >= timeout:
                    raise TimeoutError("The isolated game fixture exceeded its runtime limit")
                if any((game / "crash-reports").glob("crash-*.txt")):
                    raise ValueError("Minecraft wrote a crash report; inspect game/crash-reports")
                if output.is_file():
                    try:
                        current = read_json(output).get("phase")
                        if current != phase:
                            phase = current
                            print(f"{case.name}: {current}", flush=True)
                    except (OSError, ValueError):
                        pass
                else:
                    with (case / "process.log").open("rb") as reader:
                        reader.seek(max(0, os.fstat(reader.fileno()).st_size - 65536))
                        tail = reader.read()
                    if b"A mod crashed on startup!" in tail or b"net.fabricmc.loader.impl.FormattedException:" in tail:
                        raise ValueError("Fabric reported a fatal startup error; inspect process.log")
                time.sleep(0.25)
            status["exitCode"] = process.returncode
            if not output.is_file():
                raise ValueError("The game exited before writing its QA result; inspect process.log")
            result = read_json(output)
            if result.get("passed") is not True:
                raise ValueError("The in-game QA reported failure; inspect game/forge-qa/result.json")
            if process.returncode != 0:
                raise ValueError("The game reported success but did not shut down normally")
            with (case / "process.log").open("rb") as stream:
                if any(re.search(rb"\[[^\]\r\n]*/FATAL\]", line) for line in stream):
                    raise ValueError("The game logged a fatal task or lifecycle failure; inspect process.log")
            captures = result.get("screenshots", [])
            required = {"01-main-menu.png", "02-world.png", "03-inventory.png", "04-main-menu-returned.png"}
            checks = result.get("checks", {})
            if "nativeFpsModuleAvailable" not in result:
                raise ValueError("The QA artifact does not verify native HUD rendering; rebuild remapFeatureTestJar")
            if result["nativeFpsModuleAvailable"]:
                fps = checks.get("arcadiumFpsHud", {})
                if fps.get("visible") is not True or fps.get("callsPerFrame") != 1 or fps.get("framesChecked", 0) <= 10:
                    raise ValueError("The QA artifact did not verify one native FPS HUD render per world frame")
                if checks.get("arcadiumFpsPixels", {}).get("whiteTextPixels", 0) <= 8:
                    raise ValueError("The QA artifact did not verify visible FPS text in the screenshot")
            if "-Darcadium.forgeQa.registrySmoke=true" in arguments and checks.get("registrySmoke", {}).get("worldSnapshotRoundtrip") is not True:
                raise ValueError("The QA artifact did not verify the private registry smoke roundtrip; rebuild remapFeatureTestJar")
            if checks.get("arcadiumOptions", {}).get("configs", 0) <= 0:
                raise ValueError("The QA artifact did not verify the native Arcadium Options screen; rebuild remapFeatureTestJar")
            required.add("05-arcadium-options.png")
            if "JEI" in result.get("expectedForgeModIds", []):
                if checks.get("JEIRecipe", {}).get("layouts", 0) <= 0:
                    raise ValueError("The QA artifact did not verify the real JEI recipe screen; rebuild remapFeatureTestJar")
                required.add("03b-jei-recipe.png")
            if "journeymap" in result.get("expectedForgeModIds", []):
                if checks.get("journeymapFullscreen", {}).get("mapping") is not True:
                    raise ValueError("The QA artifact did not verify the real JourneyMap fullscreen map")
                required.add("03c-journeymap.png")
            if "NotEnoughItems" in result.get("expectedForgeModIds", []):
                if checks.get("NEIRecipe", {}).get("recipes", 0) <= 0:
                    raise ValueError("The QA artifact did not verify the real NEI crafting recipe GUI")
                required.add("03bb-nei-recipe.png")
            if "keystrokes" in result.get("expectedForgeModIds", []):
                if checks.get("keystrokesGui", {}).get("categories", 0) <= 0:
                    raise ValueError("The QA artifact did not verify the real KeystrokesMod GUI")
                key = checks.get("ravenRightShift", {})
                if key.get("nativeInputQueue") is not True or key.get("opened") is not True:
                    raise ValueError("The QA artifact did not open Raven through real Right Shift input")
                required.add("03d-keystrokes.png")
            if not required.issubset(captures):
                raise ValueError("The in-game QA did not capture every required scene")
            images = []
            for name in captures:
                if Path(name).name != name:
                    raise ValueError("Unsafe screenshot path in the QA result")
                image = output.parent / name
                dimensions = png_dimensions(image)
                if min(dimensions) < 480:
                    raise ValueError("The QA screenshot resolution is too small")
                images.append({"file": name, "sha256": sha256(image), "dimensions": dimensions})
            if "-Darcadium.resourcePackQa=true" in arguments:
                if checks.get("resourcePacks", {}).get("completed") is not True:
                    raise ValueError("The game did not complete the real nested resource pack checks")
                resource_images = []
                for name in ("resource-packs-01-available.png", "resource-packs-02-active.png",
                             "resource-packs-03-search.png", "resource-packs-04-small.png"):
                    image = game / "screenshots" / name
                    resource_images.append({"file": name, "sha256": sha256(image), "dimensions": png_dimensions(image)})
                status["resourcePackScreenshots"] = resource_images
            if "-Darcadium.playMenuQa=true" in arguments:
                if checks.get("playMenus", {}).get("completed") is not True:
                    raise ValueError("The game did not complete the world/server menu checks")
                status["playMenuScreenshots"] = [
                    {"file": name, "sha256": sha256(game / "screenshots" / name), "dimensions": png_dimensions(game / "screenshots" / name)}
                    for name in ("play-menus-01-worlds.png", "play-menus-02-newer-warning.png", "play-menus-03-worlds-small.png",
                                 "play-menus-04-servers.png", "play-menus-05-servers-small.png", "play-menus-06-pack-font.png", "play-menus-07-create-world.png")
                ]
            status.update(passed=True, screenshots=images)
        except (OSError, ValueError, TimeoutError) as error:
            status["failure"] = str(error)
        finally:
            stop_owned(process)
    status["elapsedSeconds"] = round(time.monotonic() - start, 2)
    write_json(case / "verification.json", status)
    return status


def self_test() -> int:
    class Tests(unittest.TestCase):
        def test_json_reader_accepts_utf8_bom_and_rejects_oversized_input(self):
            with tempfile.TemporaryDirectory() as temporary:
                current = Path(temporary) / "result.json"
                current.write_bytes(b'\xef\xbb\xbf{"phase": "world"}')
                self.assertEqual({"phase": "world"}, read_json(current))
                with self.assertRaises(ValueError): read_json(current, maximum=8)

        def test_extracts_only_the_three_allowed_cache_references(self):
            with tempfile.TemporaryDirectory(prefix="arcadium-forge-script-test-") as temporary:
                base = Path(temporary)
                library = base / "library.jar"; library.write_bytes(b"fixture")
                native = base / "natives"; native.mkdir()
                assets = base / "assets"; assets.mkdir()
                source = base / "source.json"
                source.write_text(json.dumps(["untrusted-java", "-Dprivate.setting=discard", "-cp", str(library),
                    "-Djava.library.path=" + str(native), "Main", "--assetsDir", str(assets),
                    "--username", "DoNotReuse", "--accessToken", "discard", "--gameDir", "do-not-use"]), encoding="utf-8")
                refs = extract_cache_references(source)
                self.assertEqual(CacheReferences((library.resolve(),), native.resolve(), assets.resolve()), refs)
                game = base / "new-game"; game.mkdir()
                argv = command(library, refs, game, library, ["JEI"], ["arcadiumclient"], 90)
                self.assertNotIn("DoNotReuse", argv)
                self.assertFalse(any("discard" in value or "do-not-use" in value for value in argv))
                self.assertEqual("ArcadiumTest", argv[argv.index("--username") + 1])
                self.assertEqual("0", argv[argv.index("--accessToken") + 1])
                self.assertEqual(3, uuid.UUID(argv[argv.index("--uuid") + 1]).version)
                self.assertEqual(MAIN, argv[argv.index("-cp") + 2])

        def test_rejects_ambiguous_or_missing_reference_values(self):
            with tempfile.TemporaryDirectory() as temporary:
                source = Path(temporary) / "source.json"
                for value in ({}, ["-cp", "missing", "-cp", "duplicate"], ["--accessToken", "discard"]):
                    source.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaises(ValueError): extract_cache_references(source)

        def test_excludes_components_by_identity_and_checks_copy_hashes(self):
            with tempfile.TemporaryDirectory() as temporary:
                base = Path(temporary)
                source = base / "renamed.jar"
                with zipfile.ZipFile(source, "w") as archive:
                    archive.writestr("fabric.mod.json", json.dumps({"id": "arcadium_feature_test", "version": "old"}))
                self.assertTrue(excluded_native(source))
                with self.assertRaises(ValueError): copy_verified(source, base / "copy.jar", "0" * 64)
                self.assertFalse((base / "copy.jar").exists())
                info = copy_verified(source, base / "copy.jar", sha256(source))
                self.assertEqual(sha256(source), info["sha256"])
                with self.assertRaises(ValueError): copy_verified(source, base / "copy.jar")

        def test_child_environment_uses_fixture_storage_and_ignores_java_injection(self):
            with tempfile.TemporaryDirectory() as temporary:
                previous = os.environ.get("JAVA_TOOL_OPTIONS")
                try:
                    os.environ["JAVA_TOOL_OPTIONS"] = "discard"
                    environment = child_environment(Path(temporary))
                    self.assertNotIn("JAVA_TOOL_OPTIONS", environment)
                    for key in ("HOME", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "TEMP"):
                        self.assertTrue(Path(environment[key]).is_relative_to(Path(temporary)))
                finally:
                    if previous is None: os.environ.pop("JAVA_TOOL_OPTIONS", None)
                    else: os.environ["JAVA_TOOL_OPTIONS"] = previous

        def test_registry_smoke_is_optional_and_never_changes_the_baseline(self):
            with tempfile.TemporaryDirectory() as temporary:
                base = Path(temporary)
                files, expected = [], []
                self.assertFalse(attach_registry_smoke("baseline", base / "missing.jar", base, expected, files))
                self.assertFalse(attach_registry_smoke("jei", None, base, expected, files))
                self.assertEqual([], expected)
                self.assertEqual([], files)
                library = base / "library.jar"
                library.write_bytes(b"metadata fixture")
                references = CacheReferences((library,), base, base)
                normal, smoke = base / "normal", base / "smoke"
                normal.mkdir(); smoke.mkdir()
                self.assertFalse(any("registrySmoke" in arg for arg in command(library, references, normal, library, [], [], 90)))
                self.assertIn("-Darcadium.forgeQa.registrySmoke=true", command(library, references, smoke, library, [REGISTRY_SMOKE_ID], [], 90, True))

        def test_registry_smoke_copy_checks_its_real_metadata_identity_and_records_its_hash(self):
            with tempfile.TemporaryDirectory() as temporary:
                base = Path(temporary)
                source = base / "smoke.jar"
                def archive(identity):
                    with zipfile.ZipFile(source, "w") as jar:
                        jar.writestr("mcmod.info", json.dumps([{"modid": identity}]))
                        jar.writestr("arcadium/registry/smoke/RegistrySmoke.class", b"metadata-only test input")
                        jar.writestr("arcadium/registry/smoke/RegistrySmokeBlock.class", b"metadata-only test input")
                archive("unrelated")
                with self.assertRaises(ValueError): attach_registry_smoke("jei", source, base, [], [])
                archive(REGISTRY_SMOKE_ID)
                files, expected = [], ["JEI"]
                self.assertTrue(attach_registry_smoke("jei", source, base, expected, files))
                self.assertEqual(["JEI", REGISTRY_SMOKE_ID], expected)
                self.assertEqual(sha256(source), files[0]["sha256"])
                self.assertEqual(sha256(source), sha256(base / "ArcadiumRegistrySmoke-dev.jar"))

    return 0 if unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Tests)).wasSuccessful() else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--guest-set", choices=("jei", "raven", "all"), default="jei")
    parser.add_argument("--nei-overlay-probe", action="store_true", help="Temporarily hide JEI only while capturing the NEI recipe screen, then restore the fresh fixture setting")
    parser.add_argument("--cases", choices=("both", "baseline", "guests"), default="both")
    parser.add_argument("--run", action="store_true", help="Launch the prepared game fixtures; the default only prepares inputs")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--diagnostic-minimal", action="store_true", help="Isolate Forge startup with Arcadium components and required support libraries; does not verify the full distribution")
    parser.add_argument("--java", type=Path)
    parser.add_argument("--reference", type=Path, help="Captured command.json from an isolated local game fixture")
    parser.add_argument("--native-mods", type=Path, help="Original pinned native mods from an isolated local game instance")
    parser.add_argument("--components", type=Path, default=ROOT / "packages/arcadium_common/assets/distribution")
    parser.add_argument("--qa-jar", type=Path, default=ROOT / f"game-branding/build/libs/ArcadiumFeatureTest-{VERSION}.jar")
    parser.add_argument("--compat-jar", type=Path, default=ROOT / "packages/arcadium_common/assets/distribution/ArcadiumForgeCompat-1.8.9.jar")
    parser.add_argument("--guests", type=Path, default=ROOT / "forge-mod-test")
    parser.add_argument("--dependencies", type=Path, default=ROOT / "build/forge-compat/dependencies")
    parser.add_argument("--registry-smoke-jar", type=Path, help="Add the private block/item registration probe to guest cases only")
    parser.add_argument("--export-mixins", action="store_true", help="Save final transformed Minecraft classes in the fresh fixture")
    parser.add_argument("--render-stress", action="store_true", help="Measure an offline first-person and rear third-person renderer stress scene with the Raven fixture")
    parser.add_argument("--resource-pack-qa", action="store_true", help="Exercise the real resource pack screen with nested fixture packs")
    parser.add_argument("--play-menu-qa", action="store_true", help="Exercise Arcadium world/server menus, newer-world filtering and Minecraft resource-pack text")
    parser.add_argument("--vanilla-client-cache", type=Path, help="Seed an existing vanilla remap cache before starting Forge guests")
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--output-root", type=Path, default=ROOT / "build/forge-compat/runtime-qa",
                        help="Parent directory for fresh isolated game fixtures; use a short path on Windows")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.reference is None or args.native_mods is None:
        parser.error("Provide --reference and --native-mods from an isolated local game fixture.")
    if not 60 <= args.timeout <= 1200:
        parser.error("--timeout must be between 60 and 1200 seconds")
    if args.render_stress and args.guest_set not in ("raven", "all"):
        parser.error("--render-stress requires --guest-set raven or all")
    references = extract_cache_references(args.reference)
    java = java_executable(args.java)
    probe = subprocess.run([str(java), "-version"], capture_output=True, text=True, timeout=10)
    version = re.search(r'version "(\d+)', probe.stdout + probe.stderr)
    if probe.returncode != 0 or version is None or int(version.group(1)) < 25:
        raise ValueError("Runtime QA requires Java 25 or newer; supply --java or correct JAVA_HOME")
    identifier = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:10]
    base = args.output_root.resolve() / identifier
    base.mkdir(parents=True, exist_ok=False)
    names = ["baseline", args.guest_set] if args.cases == "both" else ["baseline" if args.cases == "baseline" else args.guest_set]
    prepared = [prepare_case(base, name, args, references, java) for name in names]
    print("Prepared isolated runtime QA: " + str(base), flush=True)
    if not args.run:
        write_json(base / "summary.json", {"status": "prepared", "cases": names, "gameLaunched": False})
        return 0
    results = []
    for case in prepared:
        print("Starting " + case.name, flush=True)
        result = run_case(case, args.timeout)
        if args.resource_pack_qa and result["passed"]:
            game = case / "game"
            proof = case / "resource-pack-initial-process"
            proof.mkdir()
            (game / "forge-qa/result.json").replace(proof / "result.json")
            shutil.copy2(case / "process.log", proof / "process.log")
            shutil.copy2(case / "verification.json", proof / "verification.json")
            for image in result["resourcePackScreenshots"]:
                shutil.copy2(game / "screenshots" / image["file"], proof / image["file"])
            if not (game / "forge-qa/resource-pack-restart.json").is_file():
                result.update(passed=False, failure="The resource pack restart checkpoint was not written")
            else:
                print(case.name + ": restarting to verify saved resource packs", flush=True)
                initial = result
                result = run_case(case, args.timeout)
                result["initialProcess"] = initial
                if result["passed"]:
                    checks = read_json(game / "forge-qa/result.json").get("checks", {}).get("resourcePacks", {})
                    if checks.get("restoredAfterProcessRestart") is not True or checks.get("restartRequired") is not False:
                        result.update(passed=False, failure="A new game process did not verify persisted resource pack selection")
        results.append(result)
        print(case.name + (": PASS" if result["passed"] else ": FAIL"), flush=True)
        if not result["passed"]:
            break
    passed = len(results) == len(prepared) and all(result["passed"] for result in results)
    write_json(base / "summary.json", {"status": "completed", "passed": passed,
               "nativeProfile": "minimal-diagnostic" if args.diagnostic_minimal else "full", "cases": results})
    return 0 if passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        print("Forge runtime QA: " + str(failure), file=sys.stderr)
        raise SystemExit(2)
