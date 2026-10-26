from __future__ import annotations

import argparse
import hashlib
import io
import json
import lzma
import os
from pathlib import Path
import re
import subprocess
import tempfile
import tomllib
import zipfile

from prepare_forge1710 import ROOT, verified_download


BUILD = ROOT / "build/forge1710"
ASSETS = ROOT / "packages/arcadium_common/assets/distribution"
PLATFORM = "io/github/arcadiumclient/forge1710/platform/"
CLASS_ALIASES = {
    "net/fabricmc/loader/api/FabricLoader": PLATFORM + "PortLoader",
    "net/fabricmc/loader/api/ModContainer": PLATFORM + "PortModContainer",
    "net/fabricmc/loader/api/Version": PLATFORM + "PortVersion",
    "net/fabricmc/loader/api/SemanticVersion": PLATFORM + "PortSemanticVersion",
    "net/fabricmc/loader/api/VersionParsingException": PLATFORM + "PortVersionParsingException",
    "net/fabricmc/loader/impl/game/minecraft/McVersionLookup": PLATFORM + "MinecraftVersionNormalizer",
    "net/fabricmc/loader/api/metadata/ModMetadata": PLATFORM + "PortMetadata",
    "net/fabricmc/loader/api/metadata/Person": PLATFORM + "PortPerson",
    "net/fabricmc/loader/api/entrypoint/EntrypointContainer": PLATFORM + "PortEntrypoint",
    "net/fabricmc/loader/impl/launch/FabricLauncher": PLATFORM + "PortLauncher",
    "net/fabricmc/loader/impl/launch/FabricLauncherBase": PLATFORM + "PortLauncherBridge",
    "net/fabricmc/loader/launch/common/FabricLauncher": PLATFORM + "LegacyPortLauncher",
    "net/fabricmc/loader/launch/common/FabricLauncherBase": PLATFORM + "LegacyPortLauncherBridge",
    "net/fabricmc/api/ClientModInitializer": PLATFORM + "ClientInitializer",
    "net/fabricmc/api/ModInitializer": PLATFORM + "CommonInitializer",
    "net/fabricmc/loader/api/entrypoint/PreLaunchEntrypoint": PLATFORM + "PreLaunchInitializer",
    "pl/tomgirl/pylon/window/DisplaySdl": PLATFORM + "WindowHost",
    "org/lwjgl/input/Mouse": "org/lwjglx/input/Mouse",
    "org/lwjgl/input/Keyboard": "org/lwjglx/input/Keyboard",
    "org/lwjgl/opengl/Display": "org/lwjglx/opengl/Display",
    "org/lwjgl/opengl/DisplayMode": "org/lwjglx/opengl/DisplayMode",
    "org/lwjgl/opengl/GLContext": "org/lwjglx/opengl/GLContext",
    "org/lwjgl/opengl/ContextCapabilities": "org/lwjglx/opengl/ContextCapabilities",
}

OSL_MODULES = {
    "osl-core": "0.11.0+mca1.0.1_01-mc14w26c",
    "osl-config": "0.6.2+mc13w41a-mc1.7.10",
    "osl-entrypoints": "0.6.1+mc13w16a-mc1.14.4",
    "osl-keybinds": "0.3.0+mc13w36a-mc17w15a",
    "osl-lifecycle-events": "0.6.1+mc13w36a-mc19w07a",
    "osl-localization": "0.1.2+mc13w26a-mc18w01a",
    "osl-networking": "0.10.1+mc13w41a-mc18w30b",
    "osl-networking-impl": "0.2.1+mc13w41a-mc14w20b",
    "osl-resource-loader": "0.8.1+mc13w26a-mc1.8.2-pre4",
    "osl-text-components": "0.1.0-alpha.6+mc13w36a-mc1.14.4",
}


def descriptor(value: str, classes: dict[str, str]) -> str:
    return re.sub(r"L([^;]+);", lambda match: "L" + classes.get(match[1], match[1]) + ";", value)


def mappings(calamus: Path, forge: Path) -> tuple[dict, dict, dict, dict]:
    with zipfile.ZipFile(calamus) as jar:
        tiny = jar.read("mappings/mappings.tiny").decode("utf-8")
    (BUILD / "calamus.tiny").write_text(tiny, encoding="utf-8")
    with zipfile.ZipFile(forge) as jar:
        srg = lzma.decompress(jar.read("deobfuscation_data-1.7.10.lzma"), format=lzma.FORMAT_ALONE).decode("utf-8")
    official_classes, official_fields, official_methods = {}, {}, {}
    for line in srg.splitlines():
        row = line.split()
        if not row:
            continue
        if row[0] == "CL:": official_classes[row[1]] = row[2]
        if row[0] == "FD:": official_fields[row[1]] = row[2].rsplit("/", 1)[-1]
        if row[0] == "MD:": official_methods[(row[1], row[2])] = row[3].rsplit("/", 1)[-1]
    classes, fields, methods, official_intermediary, original_members = {}, {}, {}, {}, {}
    for line in tiny.splitlines()[1:]:
        row = line.split("\t")
        if row[0] == "c": official_intermediary[row[1]] = row[2]
    for line in tiny.splitlines()[1:]:
        row = line.split("\t")
        if row[0] == "c":
            owner, target = row[1:3]
            classes[target] = official_classes.get(owner, owner)
        elif len(row) > 4 and row[1] in {"f", "m"}:
            desc, name, mapped = row[2:5]
            key = (official_intermediary[owner], mapped, descriptor(desc, official_intermediary))
            original_members[(row[1], *key)] = (name, desc)
            if row[1] == "f": fields[key] = official_fields.get(owner + "/" + name, name)
            else: methods[key] = official_methods.get((owner + "/" + name, desc), name)
    classes.update(CLASS_ALIASES)
    lines = ["tiny\t2\t0\tintermediary\tsrg"]
    by_class = {}
    for kind, members in (("f", fields), ("m", methods)):
        for (owner, name, desc), mapped in members.items():
            by_class.setdefault(owner, []).append(f"\t{kind}\t{desc}\t{name}\t{mapped}")
    for name, mapped in sorted(classes.items()):
        lines.append(f"c\t{name}\t{mapped}")
        lines.extend(sorted(by_class.get(name, [])))
    (BUILD / "intermediary-srg.tiny").write_text("\n".join(lines) + "\n", encoding="utf-8")
    original_classes = {intermediary: official for official, intermediary in official_intermediary.items()}
    lines = ["tiny\t2\t0\tofficial\tsrg"]
    grouped = {}
    for (kind, owner, intermediary, desc), (name, original_desc) in original_members.items():
        renamed = (fields if kind == "f" else methods)[(owner, intermediary, desc)]
        grouped.setdefault(owner, []).append(f"\t{kind}\t{original_desc}\t{name}\t{renamed}")
    for intermediary, official in sorted(original_classes.items()):
        lines.append(f"c\t{official}\t{classes[intermediary]}")
        lines.extend(sorted(grouped.get(intermediary, [])))
    (BUILD / "official-srg.tiny").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return classes, fields, methods, original_members


def build(java_home: Path, calamus: Path, components: Path) -> Path:
    BUILD.mkdir(parents=True, exist_ok=True)
    version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    wrapper = ROOT / "game-branding" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run([str(wrapper), "-p", str(ROOT / "client-forge1710"), "classes", "exportClasspath",
                    "--max-workers=2", "--no-configuration-cache"], env=environment, check=True)
    forge = next((BUILD / "cache/libraries/net/minecraftforge/forge").rglob("*-universal.jar"))
    classes, fields, methods, original_members = mappings(calamus, forge)
    metadata, entries, services, access, configs, collisions = [], {}, {}, {}, [], []
    component_hashes = {}

    def add(raw: bytes, label: str, library: bool = False) -> None:
        with zipfile.ZipFile(io.BytesIO(raw)) as jar:
            names = set(jar.namelist())
            info = json.loads(jar.read("fabric.mod.json")) if "fabric.mod.json" in names else {}
            if info.get("id", "").startswith("osl-") and OSL_MODULES.get(info["id"]) != info["version"]:
                for name in names:
                    if name.startswith("META-INF/jars/") and name.endswith(".jar"): add(jar.read(name), Path(name).name, True)
                return
            if info.get("id"):
                previous = component_hashes.get(info["id"])
                digest = hashlib.sha256(raw).hexdigest()
                if previous is not None:
                    if previous != digest:
                        raise RuntimeError(f"Conflicting input components for {info['id']}: {label}")
                    return
                component_hashes[info["id"]] = digest
                metadata.append(info)
                for config in [] if info.get("id") == "osl-entrypoints" else info.get("mixins", []):
                    config = config if isinstance(config, str) else config["config"]
                    if "mixinsquared.fabric" not in config:
                        configs.append(config)
            widener = info.get("accessWidener")
            if widener:
                for line in jar.read(widener).decode().splitlines()[1:]:
                    row = line.split()
                    if not row or row[0].startswith("#"): continue
                    if row[0] in {"transitive-inject-interface", "inject-interface"}:
                        access.setdefault(classes.get(row[1], row[1]), {}).setdefault("interfaces", []).append(row[2])
                    elif row[0] in {"accessible", "transitive-accessible"}:
                        kind, owner = row[1:3]
                        rule = access.setdefault(classes.get(owner, owner), {})
                        if kind == "class": rule["class"] = True
                        elif kind == "field":
                            mapped = fields.get((owner, row[3], row[4]), row[3])
                            rule.setdefault("fields", []).append(mapped)
                            original = original_members.get(("f", owner, row[3], row[4]))
                            if original: rule["fields"].append(original[0])
                        elif kind == "method":
                            mapped = methods.get((owner, row[3], row[4]), row[3])
                            rule.setdefault("methods", []).append(mapped + descriptor(row[4], classes))
                            original = original_members.get(("m", owner, row[3], row[4]))
                            if original: rule["methods"].append(original[0] + original[1])
            for name in sorted(names):
                if name.endswith("/"): continue
                if info.get("id") == "fabric-language-kotlin" and not name.startswith("META-INF/jars/"):
                    continue
                if info.get("id") == "osl-entrypoints" and name.endswith(".class") and not name.startswith("net/ornithemc/osl/entrypoints/api/"):
                    continue
                raw_entry = jar.read(name)
                if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                    add(raw_entry, Path(name).name, True)
                    continue
                if name in {"fabric.mod.json", "ornithe.mod.json", "mcmod.info", "META-INF/MANIFEST.MF", "module-info.class"} or name.startswith(("META-INF/versions/", "META-INF/maven/")):
                    continue
                if name.upper().endswith((".SF", ".RSA", ".DSA")): continue
                if name.startswith("META-INF/services/"):
                    services.setdefault(name, set()).update(raw_entry.decode().splitlines()); continue
                if "LICENSE" in name.upper() or "NOTICE" in name.upper() or name.upper().endswith("COPYING"):
                    name = f"META-INF/arcadium/licenses/{label}/{name}"
                if name in entries and entries[name] != raw_entry:
                    collisions.append({"path": name, "source": label})
                entries[name] = raw_entry

    component_inputs = sorted(components.glob("Arcadium*-1.7.10.jar"))
    if not component_inputs:
        raise RuntimeError("Build the original source components before reobfuscating the Forge client.")
    for path in component_inputs:
        if path.name.startswith("ArcadiumPerformance-"): continue
        with zipfile.ZipFile(path) as original:
            if "arcadium-forge-components.json" in original.namelist():
                raise RuntimeError("The merged Forge output cannot be reused as a source component.")
        add(path.read_bytes(), path.name)
    for record in json.loads((ROOT / "distribution/forge-1.7.10.port-libraries.json").read_text(encoding="utf-8")):
        target = BUILD / "cache/port-libraries" / Path(record["path"]).name
        verified_download({**record, "url": record["downloads"][0]}, target)
        add(target.read_bytes(), target.name, True)
    for name, lines in services.items(): entries[name] = ("\n".join(sorted(lines)) + "\n").encode()
    for name in configs:
        config = entries[name].decode("utf-8")
        for mixin in ("SdlCaptureMixin", "events.Mixin_ModernWindowFocusEvent", "events.Mixin_HudRenderEvent",
                      "events.Mixin_KeyInputEvent", "events.Mixin_MouseInputEvent"):
            config = config.replace('"' + mixin + '",', "")
        entries[name] = config.encode("utf-8")
    tooltip_config = json.loads(entries["toolscroll.mixins.json"])
    tooltip_config["client"].remove("ScreenMixin")
    entries["toolscroll.mixins.json"] = json.dumps(tooltip_config).encode("utf-8")
    hurtcam_config = json.loads(entries["betterhurtcam.mixins.json"])
    hurtcam_config["client"].remove("GameGuiMixin")
    entries["betterhurtcam.mixins.json"] = json.dumps(hurtcam_config).encode("utf-8")
    animations_config = json.loads(entries["overflowanimations.mixins.json"])
    animations_config["client"].remove("legacy.native1710.MixinGameGui1710")
    entries["overflowanimations.mixins.json"] = json.dumps(animations_config).encode("utf-8")
    weather_config = json.loads(entries["mixins.polyweather.json"])
    weather_config["client"] = [name for name in weather_config["client"] if name not in {
        "client.Mixin_FixWorldColors", "client.Mixin_ModifyPrecipitationStrength", "client.Mixin_ModifyThunderStrength",
        "client.Mixin_ModifyWeatherFog", "client.Mixin_WeatherSound"}]
    entries["mixins.polyweather.json"] = json.dumps(weather_config).encode("utf-8")
    runtime_config = json.loads(entries["arcadium-runtime-1710.mixins.json"])
    runtime_config["client"].remove("client.MixinGuiScaleHud")
    entries["arcadium-runtime-1710.mixins.json"] = json.dumps(runtime_config).encode("utf-8")
    input_jar = BUILD / "arcadium-intermediary.jar"
    with zipfile.ZipFile(input_jar, "w", zipfile.ZIP_DEFLATED) as jar:
        for name, raw in sorted(entries.items()): jar.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), raw, compress_type=zipfile.ZIP_DEFLATED)
    (BUILD / "input-collisions.json").write_text(json.dumps(collisions, indent=2) + "\n", encoding="utf-8")
    runtime_cp = (ROOT / "client-runtime/build/1.7.10/test-classpath.txt").read_text(encoding="utf-8")
    runtime_cp = "\n".join(line for line in runtime_cp.splitlines() if Path(line).is_file() and "minecraft-merged" not in line)
    tool_cp = os.pathsep.join((ROOT / "client-forge1710/build/tool-classpath.txt").read_text(encoding="utf-8").splitlines())
    reference_cp = os.pathsep.join((ROOT / "client-forge1710/build/version-reference-classpath.txt").read_text(encoding="utf-8").splitlines())
    java = str(java_home / "bin" / ("java.exe" if os.name == "nt" else "java"))
    subprocess.run([java, "-cp", tool_cp + os.pathsep + reference_cp,
                    "io.github.arcadiumclient.forge1710.tool.MinecraftVersionTable", str(input_jar),
                    str(BUILD / "minecraft-versions.json")], check=True, env=environment)
    tool = [java, "-Xmx3G", "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn", "-cp", tool_cp, "io.github.arcadiumclient.forge1710.tool.ForgeReobfuscator"]
    empty_cp = BUILD / "empty-classpath.txt"
    empty_cp.write_text("", encoding="utf-8")
    intermediary_game = BUILD / "minecraft-intermediary.jar"
    if not intermediary_game.is_file():
        subprocess.run([*tool, str(BUILD / "calamus.tiny"), str(BUILD / "cache/versions/1.7.10/1.7.10.jar"),
                        str(intermediary_game), str(empty_cp), "official", "intermediary"], check=True, env=environment)
    (BUILD / "remap-classpath.txt").write_text(str(intermediary_game) + "\n" + runtime_cp, encoding="utf-8")
    reobfuscated = BUILD / "arcadium-srg.jar"
    subprocess.run([*tool, str(BUILD / "intermediary-srg.tiny"),
                    str(input_jar), str(reobfuscated), str(BUILD / "remap-classpath.txt")], check=True, env=environment)
    subprocess.run([java, "-cp", tool_cp, "io.github.arcadiumclient.forge1710.tool.KotlinMappingAudit", str(reobfuscated),
                    str(BUILD / "kotlin-mapping-audit.json"), "--require-forge"], check=True, env=environment)
    vanilla = BUILD / "cache/versions/1.7.10/1.7.10.jar"
    original_cp = BUILD / "original-classpath.txt"
    original_cp.write_text(str(vanilla), encoding="utf-8")
    for source, name, classpath in ((vanilla, "minecraft-srg-api.jar", empty_cp), (forge, "forge-srg-api.jar", original_cp)):
        destination = BUILD / name
        if not destination.is_file():
            subprocess.run([*tool, str(BUILD / "official-srg.tiny"), str(source), str(destination), str(classpath),
                            "official", "srg"], check=True, env=environment)
    subprocess.run([str(wrapper), "-p", str(ROOT / "client-forge1710"), "compileForgeMixinsJava",
                    "--max-workers=2", "--no-configuration-cache"], env=environment, check=True)
    output = BUILD / "ArcadiumForge-1.7.10.jar"
    mixins_base = ROOT / "client-forge1710/build/classes/java/forgeMixins"
    overrides = {file.relative_to(mixins_base).as_posix() for file in mixins_base.rglob("*.class")}
    with zipfile.ZipFile(reobfuscated) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as jar:
        for name in source.namelist():
            if not name.startswith("io/github/arcadiumclient/forge1710/") and name not in overrides and name != "version.properties": jar.writestr(name, source.read(name))
        version_properties = source.read("version.properties").decode("utf-8") if "version.properties" in source.namelist() else ""
        jar.writestr("version.properties", version_properties + "\narcadiumclient.version=" + version + "\n")
        base = ROOT / "client-forge1710/build/classes/java/main"
        for file in sorted(base.rglob("*.class")):
            if any(part in {"build", "tool"} for part in file.relative_to(base).parts): continue
            jar.writestr(file.relative_to(base).as_posix(), file.read_bytes())
        for file in sorted(mixins_base.rglob("*.class")):
            jar.writestr(file.relative_to(mixins_base).as_posix(), file.read_bytes())
        jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nFMLCorePlugin: io.github.arcadiumclient.forge1710.ArcadiumLoadingPlugin\nFMLCorePluginContainsFMLMod: true\n\n")
        jar.writestr("arcadium-forge-components.json", json.dumps(metadata))
        jar.writestr("mcmod.info", json.dumps([{"modid": "arcadiumclient", "name": "Arcadium Client", "version": version, "mcversion": "1.7.10", "url": "https://arcadiumclient.com", "authorList": ["Arcadium contributors"]}]))
        jar.writestr("arcadium-minecraft-versions.json", (BUILD / "minecraft-versions.json").read_bytes())
        jar.writestr("arcadium-forge-access.json", json.dumps(access))
        jar.writestr("arcadium-forge-mixins.json", json.dumps(list(dict.fromkeys(configs))))
        jar.writestr("mixins.arcadium-forge.json", json.dumps({"required": True, "minVersion": "0.8", "package": "io.github.arcadiumclient.forge1710.mixin", "compatibilityLevel": "JAVA_25", "client": ["ForgeTooltipScrollMixin", "ForgeHeartBlinkMixin", "ForgeHudRenderMixin", "ForgeAnimationsHudMixin", "ForgeWeatherMixin", "ForgeMinecraftExecutorMixin", "ForgeMinecraftInputMixin", "ForgeScreenInputMixin", "ForgeUnfocusedFpsMixin", "ForgeSkiaStateMixin", "ForgeVideoSettingsMixin", "ForgeGuiScaleHudMixin", "AngelicaNoRenderMixin"]}))
    print(f"Forge client built: {output} ({output.stat().st_size} bytes)")
    build_bootstrap(version)
    return output


def build_bootstrap(version: str) -> Path:
    bootstrap = BUILD / "ArcadiumBootstrap-1.7.10.jar"
    with zipfile.ZipFile(bootstrap, "w", zipfile.ZIP_DEFLATED) as jar:
        base = ROOT / "client-forge1710/build/classes/java/main"
        for file in sorted((base / "io/github/arcadiumclient/forge1710/bootstrap").glob("*.class")):
            jar.writestr(file.relative_to(base).as_posix(), file.read_bytes())
        defaults = ROOT / "client-forge1710/src/main/resources/arcadium-defaults"
        for file in sorted(defaults.glob("*")):
            jar.writestr("arcadium-defaults/" + file.name, file.read_bytes())
        dependencies = json.loads((ROOT / "distribution/forge-1.7.10.dependencies.json").read_text(encoding="utf-8"))
        engine = next(library for library in dependencies["libraries"] if library["name"].startswith("net.fabricmc:sponge-mixin:"))
        jar.writestr("arcadium-mixin-runtime.sha256", engine["sha256"] + "\n")
        container = next(mod for mod in dependencies["mods"] if mod["file"].startswith("+unimixins-all-"))
        jar.writestr("arcadium-unimixins-runtime.sha256", container["sha256"] + "\n")
        jar.writestr("META-INF/rfb-plugin/arcadium-forge.properties", "name=Arcadium Forge Bootstrap\nversion=" + version + "\nclassName=io.github.arcadiumclient.forge1710.bootstrap.ArcadiumRfbPlugin\nloadBefore=gtnhlib;lwjgl3ify;angelica;hodgepodge\ntransformerExclusions=io.github.arcadiumclient.forge1710.bootstrap.\n")
        jar.writestr("LICENSE", (ROOT / "LICENSE").read_bytes())
    return bootstrap


def build_from_sources(java_home: Path) -> Path:
    from prepare_forge1710 import prepare_dependencies
    from package_client import component_sources, inspect_component
    version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
    sources = component_sources(ROOT, version, "1.7.10")
    sources["ArcadiumBrand-1.7.10.jar"] = (ROOT / f"game-branding/build/1.7.10/libs/ArcadiumBrand-{version}-1.7.10.jar", "arcadiumclient")
    dependencies = json.loads((ROOT / "distribution/forge-1.7.10.dependencies.json").read_text(encoding="utf-8"))
    prepare_dependencies(BUILD / "cache")
    calamus = verified_download(dependencies["mappings"], BUILD / "cache/mappings" / dependencies["mappings"]["file"])
    BUILD.mkdir(parents=True, exist_ok=True)
    provenance = []
    with tempfile.TemporaryDirectory(prefix="source-components-", dir=BUILD) as directory:
        components = Path(directory)
        for name, (source, expected_id) in sources.items():
            raw = source.read_bytes()
            inspect_component(raw, name, expected_id)
            (components / name).write_bytes(raw)
            provenance.append({"file": name, "source": source.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest()})
        output = build(java_home, calamus, components)
    (BUILD / "source-inputs.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    return output


def stage(output: Path) -> list[dict]:
    from package_client import inspect_component, record, write_component_catalog
    dependencies = json.loads((ROOT / "distribution/forge-1.7.10.dependencies.json").read_text(encoding="utf-8"))
    components = []
    for file, identity, bootstrap in ((output, "arcadiumclient", False), (BUILD / "ArcadiumBootstrap-1.7.10.jar", "arcadium_forge_bootstrap", True)):
        raw = file.read_bytes()
        entry = record(raw, file.name, inspect_component(raw, file.name, identity))
        if bootstrap: entry["bootstrap"] = True
        components.append(entry)
        (ASSETS / file.name).write_bytes(raw)
    mods = []
    for entry in dependencies["mods"]:
        source = verified_download(entry, BUILD / "cache/mods" / entry["file"])
        mods.append({"path": "mods/" + entry["file"], "sha1": hashlib.sha1(source.read_bytes()).hexdigest(),
                     "sha256": entry["sha256"], "bytes": entry["bytes"], "downloads": [entry["url"]], "enabled": True})
    version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
    index = {"formatVersion": 1, "game": "minecraft", "versionId": version, "name": "Arcadium Client 1.7.10 Forge [Core]",
        "enabled": True, "id": "core-1.7.10-forge", "category": "Core", "polyFormat": "1", "updateUrl": "", "javaVersionOverride": 25, "files": [
        {"path": mod["path"], "hashes": {"sha1": mod["sha1"], "sha256": mod["sha256"]}, "fileSize": mod["bytes"],
         "downloads": mod["downloads"], "enabled": True, "env": {"client": "required", "server": "unsupported"}} for mod in mods],
        "dependencies": {"minecraft": "1.7.10", "forge": dependencies["loaderVersion"]}}
    bundle_name = "core-1.7.10-forge.mrpack"
    bundle = io.BytesIO()
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(zipfile.ZipInfo("modrinth.index.json", (1980, 1, 1, 0, 0, 0)), json.dumps(index, indent=2), compress_type=zipfile.ZIP_DEFLATED)
    raw = bundle.getvalue()
    (ASSETS / bundle_name).write_bytes(raw)
    bundle_sha1 = hashlib.sha1(raw).hexdigest()
    lock = {"minecraftVersion": "1.7.10", "javaVersion": 25, "loader": "Forge", "loaderVersion": dependencies["loaderVersion"],
            "bundles": [{"file": bundle_name, "arcadiumSha1": bundle_sha1, "mods": mods}], "arcadiumComponents": components}
    (ROOT / "distribution/arcadium-1.7.10.lock.json").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    path = ASSETS / "bundles.json"
    catalog = json.loads(path.read_text(encoding="utf-8"))
    catalog["versions"]["1.7.10"] = {"forge": [{"path": "/arcadium/bundles/" + bundle_name, "sha1": bundle_sha1}]}
    path.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    write_component_catalog(ROOT)
    return components


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Rebuild Arcadium 1.7.10 components for native Forge SRG mappings.")
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--calamus", type=Path)
    parser.add_argument("--components", type=Path)
    parser.add_argument("--stage", action="store_true")
    args = parser.parse_args()
    if bool(args.calamus) != bool(args.components): parser.error("--calamus and --components must be provided together")
    output = build(args.java_home.resolve(), args.calamus.resolve(), args.components.resolve()) if args.components else build_from_sources(args.java_home.resolve())
    if args.stage: stage(output)
