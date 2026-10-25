from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

from package_client import inspect_component
from package_modern import ROOT, TARGET, PROJECTS, component_sources


def fatal_log_error(log: str) -> str | None:
    markers = ("Critical injection failure:", "Incompatible mods found!", "Minecraft has crashed!")
    for line in log.splitlines():
        for marker in markers:
            if marker in line:
                return line[line.index(marker):].strip()[:1024]
    return None


def report_failures(output: Path, names: list[str], sections: dict[str, list[str]] | None = None) -> list[str]:
    failures = []
    for name in names:
        try:
            evidence = json.loads((output / name).read_text(encoding="utf-8"))
            if evidence.get("passed") is not True:
                failures.append(f"{name}: {evidence.get('error', 'not passed')}")
            for section in (sections or {}).get(name, []):
                child = evidence.get(section)
                if not isinstance(child, dict) or child.get("passed") is not True:
                    failures.append(f"{name}/{section}: missing or not passed")
        except (OSError, ValueError, AttributeError) as error:
            failures.append(f"{name}: missing or invalid ({type(error).__name__})")
    return failures


@contextmanager
def isolated_local_voice(profile: Path, user_directory: Path):
    profile = profile.resolve()
    path = (user_directory / "config/voicechat/voicechat-client.properties").resolve()
    if not path.is_relative_to(profile):
        raise ValueError("VoiceChat test configuration is outside the isolated profile.")
    original = path.read_bytes() if path.exists() else None
    raw = original or b""
    pattern = rb"(?m)^[ \t]*run_local_server[ \t]*=[^\r\n]*"
    changed, count = re.subn(pattern, b"run_local_server=false", raw)
    if not count:
        changed = raw + (b"" if not raw or raw.endswith(b"\n") else b"\n") + b"run_local_server=false\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(changed)
    state = {"localVoiceRecording": "excluded", "setting": "run_local_server=false", "restored": False}
    try:
        yield state
    finally:
        if not path.resolve().is_relative_to(profile):
            raise ValueError("VoiceChat test configuration moved outside the isolated profile.")
        if original is None:
            path.unlink(missing_ok=True)
        else:
            path.write_bytes(original)
        state["restored"] = True


def verified_command(profile: Path, command_file: Path, target: str = TARGET) -> tuple[list[str], dict]:
    profile = profile.resolve()
    if not (profile / ".arcadium-verification-profile").is_file():
        raise ValueError("Use an isolated profile created by verify_arcadium --offline-probe.")
    verification = json.loads((profile / "verification.json").read_text(encoding="utf-8"))
    if verification["minecraftVersion"] != target or not verification["offlineProbe"]:
        raise ValueError(f"The prepared profile is not a synthetic Minecraft {target} profile.")
    command = json.loads(command_file.read_text(encoding="utf-8"))
    if not isinstance(command, list) or not all(isinstance(value, str) for value in command):
        raise ValueError("Expected the actual argument array recorded by the native verifier.")
    for flag, expected in (("--username", "ArcadiumTest"), ("--accessToken", "0")):
        if flag not in command or command.index(flag) + 1 >= len(command) or command[command.index(flag) + 1] != expected:
            raise ValueError("Only the native verifier's synthetic offline account may be used.")
    game_directory = Path(verification["userGameDirectory"]).resolve()
    runtime_directory = Path(verification["gameDirectory"]).resolve()
    if not game_directory.is_relative_to(profile) or not runtime_directory.is_relative_to(profile):
        raise ValueError("The game directory is outside the isolated profile.")
    if ("--gameDir" not in command or command.index("--gameDir") + 1 >= len(command)
            or Path(command[command.index("--gameDir") + 1]).resolve() != game_directory):
        raise ValueError("Recorded arguments target a different game directory.")
    if Path(command[0]).name.lower() not in {"java", "javaw", "java.exe", "javaw.exe", "minecraft.exe"}:
        raise ValueError("Recorded executable is not a Minecraft Java runtime.")
    return command, verification


def run(profile: Path, command_file: Path, fonts: bool = False, world: bool = False, launcher: Path | None = None,
        world_only: bool = False, benchmark: bool = False, glint_only: bool = False,
        effects_only: bool = False, zoom_profiles: bool = False, glint_debug: bool = False,
        idle_only: bool = False, fonts_only: bool = False, packs_only: bool = False, chat_debug: bool = False,
        skins_only: bool = False, legacy_items: bool = False, native_profiles_only: bool = False,
        osl_profiles: bool = False, legacy_native_profiles: bool = False, legacy_visuals: bool = False,
        shared_options: bool = False, target: str = TARGET, runtime: bool = False, runtime_only: bool = False) -> dict:
    command, verification = verified_command(profile, command_file, target)
    profile = profile.resolve()
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
    output = profile / "qa-runs" / timestamp
    output.mkdir(parents=True)
    mods = Path(verification["modsDirectory"]).resolve()
    if not mods.is_relative_to(profile):
        raise ValueError("The mods directory is outside the isolated profile.")
    mods.mkdir(parents=True, exist_ok=True)
    candidates = component_sources(ROOT, target)
    runtime_jar = candidates[f"ArcadiumFeatures-{target}.jar"][0]
    qa = runtime_jar.with_name(f"{runtime_jar.stem}-gui-qa.jar")
    candidates["ArcadiumModernGuiQa.jar"] = qa, "arcadium_gui_qa"
    prepared, fingerprints = {}, {}
    for name, (source, expected_id) in candidates.items():
        raw = source.read_bytes()
        inspect_component(raw, name, expected_id)
        prepared[name] = raw
        fingerprints[name] = hashlib.sha256(raw).hexdigest()
    for name, raw in prepared.items():
        (mods / name).write_bytes(raw)
    command = [value for value in command if not value.startswith((
        "-Darcadium.guiQa.output=", "-Darcadium.guiQa.fonts=", "-Darcadium.guiQa.play=",
        "-Darcadium.guiQa.playOnly=", "-Darcadium.guiQa.benchmark=", "-Darcadium.guiQa.benchmarkSeconds=",
        "-Darcadium.guiQa.glint=", "-Darcadium.guiQa.glintOnly=", "-Darcadium.guiQa.runtimeEffects=",
        "-Darcadium.guiQa.runtimeEffectsOnly=", "-Darcadium.guiQa.zoomProfiles=",
        "-Darcadium.glint.debug=",
        "-Darcadium.chat.debug=", "-Darcadium.guiQa.idle=", "-Darcadium.guiQa.idleOnly=",
        "-Darcadium.guiQa.fontsOnly=", "-Darcadium.guiQa.packsOnly=",
        "-Darcadium.guiQa.skins=", "-Darcadium.guiQa.skinsOnly=",
        "-Darcadium.guiQa.legacyItems=",
        "-Darcadium.guiQa.nativeProfiles=", "-Darcadium.guiQa.nativeProfilesOnly=",
        "-Darcadium.guiQa.oslProfiles=",
        "-Darcadium.guiQa.legacyNativeProfiles=",
        "-Darcadium.guiQa.legacyVisuals=",
        "-Darcadium.guiQa.sharedOptions=",
        "-Darcadium.guiQa.microphoneIsolation=",
        "-Darcadium.guiQa.runtime=",
        "-Darcadium.guiQa.runtimeOnly=",
    ))]
    command.insert(1, f"-Darcadium.guiQa.output={output}")
    command.insert(1, "-Darcadium.guiQa.microphoneIsolation=true")
    if fonts:
        command.insert(1, "-Darcadium.guiQa.fonts=true")
    if world or zoom_profiles or legacy_items:
        command.insert(1, "-Darcadium.guiQa.play=true")
    if runtime:
        command.insert(1, "-Darcadium.guiQa.runtime=true")
    if runtime_only:
        command.insert(1, "-Darcadium.guiQa.runtimeOnly=true")
    if world_only or benchmark or glint_only or effects_only or idle_only or packs_only or skins_only:
        command.insert(1, "-Darcadium.guiQa.playOnly=true")
    if benchmark:
        command.insert(1, "-Darcadium.guiQa.benchmark=true")
        for flag, value in (("--width", "1920"), ("--height", "1080")):
            if flag in command:
                command[command.index(flag) + 1] = value
            else:
                command.extend((flag, value))
    if glint_only:
        command.insert(1, "-Darcadium.guiQa.glintOnly=true")
    if effects_only:
        command.insert(1, "-Darcadium.guiQa.runtimeEffectsOnly=true")
    if zoom_profiles:
        command.insert(1, "-Darcadium.guiQa.zoomProfiles=true")
    if glint_debug:
        command.insert(1, "-Darcadium.glint.debug=true")
    if chat_debug:
        command.insert(1, "-Darcadium.chat.debug=true")
    if legacy_items:
        command.insert(1, "-Darcadium.guiQa.legacyItems=true")
    for enabled, property_name in ((idle_only, "idleOnly"), (fonts_only, "fontsOnly"), (packs_only, "packsOnly"), (skins_only, "skinsOnly")):
        if enabled:
            command.insert(1, f"-Darcadium.guiQa.{property_name}=true")
    if native_profiles_only:
        command.insert(1, "-Darcadium.guiQa.nativeProfilesOnly=true")
    if osl_profiles:
        command.insert(1, "-Darcadium.guiQa.oslProfiles=true")
    if legacy_native_profiles:
        command.insert(1, "-Darcadium.guiQa.legacyNativeProfiles=true")
    if legacy_visuals:
        command.insert(1, "-Darcadium.guiQa.legacyVisuals=true")
    if shared_options:
        command.insert(1, "-Darcadium.guiQa.sharedOptions=true")
    (output / "candidate.json").write_text(json.dumps({"minecraftVersion": target, "components": fingerprints}, indent=2) + "\n", encoding="utf-8")
    (output / "command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    print(f"Testing exact source-built candidate: {output}", flush=True)
    started = time.monotonic()
    wall_started = time.time()
    startup_error = None
    forced_termination = False
    report = output / "result.json"
    game_log = Path(verification["gameDirectory"]) / "logs/latest.log"
    environment = os.environ.copy()
    if launcher is not None:
        if not launcher.is_file():
            raise FileNotFoundError(launcher)
        environment["ARCADIUM_VERIFICATION_LAUNCHER"] = str(launcher.resolve())
    with isolated_local_voice(profile, Path(verification["userGameDirectory"])) as voice_fixture, (output / "console.log").open("wb") as console:
        process = subprocess.Popen(command, cwd=verification["gameDirectory"], stdout=console, stderr=subprocess.STDOUT,
                                   stdin=subprocess.DEVNULL, env=environment,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        (output / "process.json").write_text(json.dumps({"pid": process.pid}) + "\n", encoding="utf-8")
        try:
            while time.monotonic() - started < (480 if benchmark else 300):
                if report.is_file() or process.poll() is not None:
                    break
                if game_log.exists() and game_log.stat().st_mtime >= wall_started - 1:
                    text = game_log.read_text(encoding="utf-8", errors="replace")
                    startup_error = fatal_log_error(text)
                    if startup_error:
                        break
                time.sleep(0.5)
            if report.is_file():
                try:
                    process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    inspector = Path(command[0]).with_name("jcmd.exe" if os.name == "nt" else "jcmd")
                    if inspector.is_file():
                        with (output / "shutdown-threads.txt").open("wb") as diagnostic:
                            try:
                                subprocess.run([str(inspector), str(process.pid), "Thread.print"], stdout=diagnostic,
                                               stderr=subprocess.STDOUT, timeout=10, check=False)
                            except (OSError, subprocess.TimeoutExpired):
                                pass
        finally:
            if process.poll() is None:
                forced_termination = True
                process.terminate()
                process.wait(timeout=15)
    if game_log.exists() and game_log.stat().st_mtime >= wall_started - 1:
        shutil.copyfile(game_log, output / "latest.log")
    result = json.loads(report.read_text(encoding="utf-8")) if report.is_file() else {"passed": False, "error": "Game stopped or timed out before its QA report"}
    if startup_error:
        result.update(passed=False, error=startup_error)
    expected = []
    for requested, name in ((world or world_only or benchmark or glint_only or effects_only or idle_only or packs_only or skins_only or zoom_profiles or legacy_items, "play.json"),
                            (fonts or fonts_only, "fonts-26.2.json" if target == "26.2" else "fonts.json"), (zoom_profiles, "zoom-profiles.json"),
                            (benchmark, "benchmark.json"), (glint_only, "glint.json"),
                            (effects_only, "runtime-effects.json"), (idle_only, "idle.json"),
                            (skins_only, "skins.json"), (native_profiles_only, "native-profiles.json"),
                            (legacy_native_profiles, "legacy-native-profiles.json"),
                            (legacy_visuals, "legacy-visuals.json"), (shared_options, "shared-options.json")):
        if requested:
            expected.append(name)
    if runtime:
        expected.extend(["glint.json", "idle.json", "runtime-controls.json"])
    sections = {"native-profiles.json": ["animations"] + (["oslProfiles"] if osl_profiles else [])} if native_profiles_only else None
    missing_evidence = report_failures(output, expected, sections)
    if missing_evidence:
        result.update(passed=False, evidenceFailures=missing_evidence)
    result["exitCode"] = process.returncode
    result["forcedTermination"] = forced_termination
    result["seconds"] = round(time.monotonic() - started, 2)
    result["candidate"] = str(output)
    result["testIsolation"] = {"voiceChat": voice_fixture}
    result["passed"] = result.get("passed") is True and process.returncode == 0
    (output / "run.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=True, indent=2), flush=True)
    print(f"Native verification: {'PASS' if result['passed'] else 'FAIL'}", flush=True)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test fresh 1.21.11 source-built candidates using exact native-verifier synthetic launch arguments.")
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--command", type=Path, required=True)
    parser.add_argument("--fonts", action="store_true")
    parser.add_argument("--world", action="store_true")
    parser.add_argument("--world-only", action="store_true")
    special = parser.add_mutually_exclusive_group()
    special.add_argument("--benchmark", action="store_true", help="Run isolated world frame-time measurements after builds are idle")
    special.add_argument("--glint-only", action="store_true")
    special.add_argument("--effects-only", action="store_true")
    special.add_argument("--idle-only", action="store_true")
    special.add_argument("--fonts-only", action="store_true")
    special.add_argument("--packs-only", action="store_true")
    special.add_argument("--skins-only", action="store_true")
    special.add_argument("--native-profiles-only", action="store_true")
    parser.add_argument("--zoom-profiles", action="store_true")
    parser.add_argument("--glint-debug", action="store_true")
    parser.add_argument("--chat-debug", action="store_true")
    parser.add_argument("--legacy-items", action="store_true")
    parser.add_argument("--osl-profiles", action="store_true")
    parser.add_argument("--legacy-native-profiles", action="store_true")
    parser.add_argument("--legacy-visuals", action="store_true")
    parser.add_argument("--shared-options", action="store_true")
    parser.add_argument("--launcher", type=Path, help="Native Arcadium account-service helper used by the verifier relay")
    args = parser.parse_args()
    if args.osl_profiles and not args.native_profiles_only:
        parser.error("--osl-profiles requires --native-profiles-only")
    if args.legacy_native_profiles and not args.native_profiles_only:
        parser.error("--legacy-native-profiles requires --native-profiles-only")
    if args.legacy_visuals and not args.effects_only:
        parser.error("--legacy-visuals requires --effects-only")
    raise SystemExit(0 if run(args.profile, args.command, args.fonts, args.world, args.launcher,
                            args.world_only, args.benchmark, args.glint_only, args.effects_only,
                            args.zoom_profiles, args.glint_debug, args.idle_only, args.fonts_only,
                            args.packs_only, args.chat_debug, args.skins_only, args.legacy_items,
                            args.native_profiles_only, args.osl_profiles, args.legacy_native_profiles, args.legacy_visuals, args.shared_options)["passed"] else 1)
