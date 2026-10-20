from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSET_PATH = Path("packages/arcadium_common/assets/distribution")
MINECRAFT_VERSIONS = ("1.8.9", "1.7.10")
LOCK_PATHS = {"1.8.9": Path("distribution/oneclient-1.8.9.lock.json"),
              "1.7.10": Path("distribution/arcadium-1.7.10.lock.json")}
EXCLUDED_PREFIXES = ("chattweaks-", "crashpatch-", "polyplus-", "polysprint-", "newdefault+")
REPLACED_LIBRARY_PREFIXES = ("oneconfig-", "compose-bundle-")
EXCLUDED_MOD_IDS = {"chattweaks", "crashpatch", "polyplus", "polysprint"}
EXTERNAL_ONLY_MOD_IDS = {"argentum", "argentum-extras", "cera"}
NATIVE_SHADER_PROGRAMS = {
    "polyblur": ("phosphor_hand", "phosphor_motion_blur", "unity_motion_blur"),
    "colorsaturation": ("color_saturation",),
    "blur": ("arcadium_gui_blur",),
}
POLICY = json.loads((ROOT / "distribution/bundle-policy.json").read_text(encoding="utf-8"))
EXCLUDED_HASHES = {artifact["sha1"].lower() for entry in POLICY["excluded"] for artifact in entry.get("historicalArtifacts", [])}


def read_properties(path: Path) -> dict[str, str]:
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def excluded_bundle_entry(entry: dict) -> bool:
    name = PurePosixPath(entry["path"]).name.lower()
    checksum = str(entry.get("sha1") or entry.get("hashes", {}).get("sha1", "")).lower()
    return name.startswith(EXCLUDED_PREFIXES + REPLACED_LIBRARY_PREFIXES) or checksum in EXCLUDED_HASHES


def mixin_json(raw: bytes) -> dict:
    source = raw.decode("utf-8-sig")
    source = re.sub(r'"(?:\\.|[^"\\])*"|/\*[\s\S]*?\*/|//[^\r\n]*',
                    lambda match: match.group() if match.group().startswith('"') else "", source)
    tokens = list(re.finditer(r'"(?:\\.|[^"\\])*"|[^"\s]', source))
    remove = {token.start() for index, token in enumerate(tokens[:-1])
              if token.group() == "," and tokens[index + 1].group() in {"}", "]"}}
    return json.loads("".join(char for index, char in enumerate(source) if index not in remove))


def component_classes(archive: zipfile.ZipFile) -> set[str]:
    classes = {name for name in archive.namelist() if name.endswith(".class")}
    for entry in archive.namelist():
        if entry.startswith("META-INF/jars/") and entry.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as nested:
                classes.update(component_classes(nested))
    return classes


def verify_mixin_classes(archive: zipfile.ZipFile, metadata: dict, label: str) -> None:
    names = set(archive.namelist())
    classes_available = component_classes(archive)
    for declared in metadata.get("mixins", []):
        path = declared if isinstance(declared, str) else declared["config"]
        if path not in names:
            raise RuntimeError(f"Missing Mixin configuration in {label}: {path}")
        config = mixin_json(archive.read(path))
        prefix = config.get("package", "").replace(".", "/")
        classes = [value for category in ("mixins", "client", "server") for value in config.get(category, [])]
        if config.get("plugin"):
            classes.append("/" + config["plugin"].replace(".", "/"))
        for name in classes:
            entry = (name[1:] if name.startswith("/") else prefix + "/" + name.replace(".", "/")) + ".class"
            if entry not in classes_available:
                raise RuntimeError(f"Missing Mixin class in {label} ({path}): {entry}")


def verify_native_languages(archive: zipfile.ZipFile, metadata: dict, label: str) -> None:
    if metadata.get("depends", {}).get("minecraft") != "1.7.10":
        return
    for entry in archive.namelist():
        if not re.fullmatch(r"assets/[^/]+/lang/[^/]+\.json", entry):
            continue
        translations = json.loads(archive.read(entry), strict=False)
        if not isinstance(translations, dict) or any(not isinstance(value, str) for value in translations.values()):
            raise RuntimeError(f"Minecraft 1.7.10 requires flat string translations in {label}: {entry}")


def verify_native_shaders(archive: zipfile.ZipFile, metadata: dict, label: str) -> None:
    if metadata.get("depends", {}).get("minecraft") != "1.7.10":
        return
    names = set(archive.namelist())
    for program in NATIVE_SHADER_PROGRAMS.get(metadata.get("id"), ()):
        namespace = "blur" if metadata["id"] == "blur" else "minecraft"
        paths = (f"assets/{namespace}/shaders/post/{program}.json",
                 f"assets/minecraft/shaders/program/{program}.json",
                 f"assets/minecraft/shaders/program/{program}.fsh")
        for path in paths:
            if path not in names:
                raise RuntimeError(f"Missing authored native shader resource in {label}: {path}")
            source = archive.read(path)
            if path.endswith(".json"):
                json.loads(source)
            elif not re.match(rb"\A\s*(?:(?://[^\n]*(?:\n|$)|/\*[\s\S]*?\*/)\s*)*#version\s+120(?:\s|$)", source):
                raise RuntimeError(f"The native shader must use GLSL 1.20 in {label}: {path}")
        definition = json.loads(archive.read(paths[1]))
        if definition.get("fragment") != program:
            raise RuntimeError(f"The native shader program references the wrong fragment in {label}: {paths[1]}")


def inspect_component(raw: bytes, label: str, expected_id: str | None = None) -> dict:
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = archive.namelist()
        metadata = json.loads(archive.read("fabric.mod.json")) if "fabric.mod.json" in names else {}
        if expected_id is not None and metadata.get("id") != expected_id:
            raise RuntimeError(f"Incorrect mod identity for {label}: {metadata.get('id')}")
        if metadata.get("id") in EXTERNAL_ONLY_MOD_IDS:
            raise RuntimeError(f"Separately licensed dependency must remain an official external download, not embedded in {label}: {metadata['id']}")
        if metadata.get("id") in EXCLUDED_MOD_IDS:
            raise RuntimeError(f"Excluded component in {label}: {metadata['id']}")
        verify_mixin_classes(archive, metadata, label)
        verify_native_languages(archive, metadata, label)
        verify_native_shaders(archive, metadata, label)
        for name in names:
            lowered = name.lower()
            if (name.endswith(".class") and name.startswith("net/minecraft/")
                    and not (metadata.get("id") == "arcadium_forge_compat" and name.startswith("net/minecraft/launchwrapper/"))):
                raise RuntimeError(f"Minecraft bytecode must not be redistributed in {label}: {name}")
            if name.endswith(".class") and name.startswith("org/polyfrost/polyplus/"):
                raise RuntimeError(f"PolyPlus bytecode is not permitted in {label}: {name}")
            if ("eossdk" in lowered or "eos_sdk" in lowered or lowered.startswith("com/epicgames/eos/")
                    or PurePosixPath(lowered).name in {"forge-runtime.jar", "forge.srg.gz", "binpatches.pack.lzma"}):
                raise RuntimeError(f"Excluded proprietary or MCP payload in {label}: {name}")
            if (lowered.startswith(("lzma/", "javax/vecmath/")) and lowered.endswith(".class")
                    or lowered.endswith(".jar") and PurePosixPath(lowered).name.startswith(("lzma-", "vecmath-"))):
                raise RuntimeError(f"Download-only Forge support library must remain external in {label}: {name}")
            if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                inspect_component(archive.read(name), f"{label}!/{name}")
        return metadata


def component_sources(root: Path, version: str, minecraft_version: str = "1.8.9") -> dict[str, tuple[Path, str]]:
    core = read_properties(root / "client-ui/gradle.properties")["version"]
    versions = tomllib.loads((root / "client-ui/gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]
    compose = versions["compose"]
    if isinstance(compose, dict):
        compose = compose["version"]
    runtime = (f"client-runtime/build/libs/ArcadiumRuntime-{version}.jar" if minecraft_version == "1.8.9"
               else f"client-runtime/build/{minecraft_version}/libs/ArcadiumRuntime-{version}-{minecraft_version}.jar")
    result = {
        f"ArcadiumCore-{minecraft_version}.jar": (root / f"client-ui/bootstrap/versions/{minecraft_version}-ornithe/build/libs/OneConfig-{minecraft_version}-ornithe-{core}.jar", "oneconfig"),
        f"ArcadiumFeatures-{minecraft_version}.jar": (root / runtime, "arcadium_runtime"),
        f"ArcadiumRenderer-{minecraft_version}.jar": (root / f"client-ui/modules/compose-bundle/build/libs/compose-bundle-1.0.5+compose.{compose}.jar", "compose-multiplatform"),
    }
    if minecraft_version == "1.8.9":
        result["ArcadiumForgeCompat-1.8.9.jar"] = (root / f"forge-compat/build/libs/ArcadiumForgeCompat-{version}.jar", "arcadium_forge_compat")
    else:
        result[f"ArcadiumPerformance-{minecraft_version}.jar"] = (
            root / f"client-legacy-performance/build/libs/ArcadiumLegacyPerformance-{version}-{minecraft_version}.jar", "arcadium-legacy-performance")
        for port in legacy_ports(root):
            directory = root / "client-legacy-mods" / port["directory"] / port["outputDirectory"]
            candidates = [path for path in directory.glob("*.jar")
                          if not path.name.endswith(("-sources.jar", "-javadoc.jar", "-dev.jar", "-test.jar"))]
            if len(candidates) != 1:
                raise RuntimeError(f"Build the Minecraft 1.7.10 port {port['directory']} before packaging (expected one runtime JAR in {directory}).")
            result[port["file"]] = (candidates[0], port["modId"])
    return result


def legacy_ports(root: Path = ROOT) -> list[dict]:
    path = root / "client-legacy-mods/ports.json"
    if not path.is_file():
        return []
    ports = json.loads(path.read_text(encoding="utf-8"))["ports"]
    for port in ports:
        if (not re.fullmatch(r"[a-z0-9-]+", port["directory"])
                or port["modId"] in EXCLUDED_MOD_IDS | EXTERNAL_ONLY_MOD_IDS
                or port["file"] != f"ArcadiumLegacy-{port['directory']}-1.7.10.jar"
                or not re.fullmatch(r"[a-f0-9]{40}", port["upstream"]["commit"])):
            raise RuntimeError("Invalid legacy dependency port catalog.")
        license_file = root / "client-legacy-mods" / port["directory"] / port["licenseFile"]
        if hashlib.sha256(license_file.read_bytes()).hexdigest() != port["licenseSha256"]:
            raise RuntimeError("Legacy dependency license does not match its catalog: " + port["directory"])
    return ports


def write_legacy_notices(destination: Path, root: Path = ROOT) -> None:
    import shutil
    destination.mkdir(parents=True, exist_ok=True)
    ports = legacy_ports(root)
    shutil.copy2(root / "client-legacy-mods/ports.json", destination / "ports.json")
    for port in ports:
        source = root / "client-legacy-mods" / port["directory"]
        for name in ["LICENSE", "NOTICE", "COPYING", "HEADER"]:
            if (source / name).is_file():
                shutil.copy2(source / name, destination / f"{port['directory']}-{name}.txt")
    (destination / "README.txt").write_text(
        "Minecraft 1.7.10 dependency ports retain their original authors and licenses.\n"
        "The complete modified corresponding sources and build scripts are included in the same version's source archive.\n"
        "Source: https://github.com/0aurorq3/arcadiumclient\n"
        "Releases: https://github.com/0aurorq3/arcadium-public/releases\n", encoding="utf-8")


def clean_bundle(raw: bytes, version: str) -> bytes:
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = [(info, archive.read(info)) for info in archive.infolist()]
    index_entry = next((data for info, data in entries if info.filename == "modrinth.index.json"), None)
    if index_entry is None:
        raise RuntimeError("Bundle does not contain modrinth.index.json")
    index = json.loads(index_entry)
    index["name"] = index["name"].replace("OneClient", "Arcadium Client")
    index["versionId"] = version
    index["files"] = [entry for entry in index["files"] if not excluded_bundle_entry(entry)]
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for info, data in entries:
            if info.filename == "modrinth.index.json":
                data = json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            archive.writestr(info, data)
    return output.getvalue()


def record(raw: bytes, filename: str, metadata: dict) -> dict:
    return {"file": filename, "sha1": hashlib.sha1(raw).hexdigest(), "sha256": hashlib.sha256(raw).hexdigest(),
            "bytes": len(raw), "modId": metadata.get("id"), "license": metadata.get("license"),
            "authors": metadata.get("authors", []), "contact": metadata.get("contact", {})}


def refresh_bundles(root: Path, version: str, minecraft_version: str = "1.8.9") -> dict:
    assets = root / ASSET_PATH
    lock_path = root / LOCK_PATHS[minecraft_version]
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    for bundle in lock["bundles"]:
        path = assets / bundle["file"]
        raw = clean_bundle(path.read_bytes(), version)
        path.write_bytes(raw)
        bundle["arcadiumSha1"] = hashlib.sha1(raw).hexdigest()
        bundle["mods"] = [entry for entry in bundle["mods"] if not excluded_bundle_entry(entry)]
    manifest_path = assets / "bundles.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    by_name = {entry["file"]: entry for entry in lock["bundles"]}
    for bundle in manifest["versions"][minecraft_version]["ornithe"]:
        bundle["sha1"] = by_name[PurePosixPath(bundle["path"]).name]["arcadiumSha1"]
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    lock_path.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    return lock


def write_component_catalog(root: Path) -> None:
    catalog = {}
    for minecraft_version, relative in LOCK_PATHS.items():
        path = root / relative
        if path.is_file():
            catalog[minecraft_version] = json.loads(path.read_text(encoding="utf-8")).get("arcadiumComponents", [])
    (root / ASSET_PATH / "client-components.json").write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")


def distribution_locks(root: Path = ROOT) -> dict[str, dict]:
    return {minecraft: json.loads((root / relative).read_text(encoding="utf-8"))
            for minecraft, relative in LOCK_PATHS.items() if (root / relative).is_file()}


def shipped_components(root: Path = ROOT) -> list[dict]:
    result = []
    for minecraft, lock in distribution_locks(root).items():
        kinds = ["Core", "Features", "Renderer", "ForgeCompat"] if minecraft == "1.8.9" else ["Core", "Features", "Renderer", "Performance", "Brand"]
        expected = {f"Arcadium{kind}-{minecraft}.jar" for kind in kinds}
        if minecraft == "1.7.10":
            expected.update(port["file"] for port in legacy_ports(root))
        components = lock.get("arcadiumComponents", [])
        if len(components) != len(expected) or {component["file"] for component in components} != expected:
            raise RuntimeError(f"Client components for Minecraft {minecraft} are incomplete. Build both Minecraft versions before packaging.")
        result.extend(components)
    return result


def verify_components(root: Path = ROOT) -> dict:
    locks = distribution_locks(root)
    if set(locks) != set(MINECRAFT_VERSIONS):
        raise RuntimeError("Both Minecraft distribution catalogs are required.")
    catalog = json.loads((root / ASSET_PATH / "client-components.json").read_text(encoding="utf-8"))
    if catalog != {minecraft: lock["arcadiumComponents"] for minecraft, lock in locks.items()}:
        raise RuntimeError("The launcher component catalog does not match the distribution locks.")
    components = shipped_components(root)
    for component in components:
        source = root / ASSET_PATH / component["file"]
        raw = source.read_bytes()
        if (hashlib.sha256(raw).hexdigest() != component["sha256"]
                or hashlib.sha1(raw).hexdigest() != component["sha1"] or len(raw) != component["bytes"]):
            raise RuntimeError("Client component is missing or damaged: " + component["file"])
        inspect_component(raw, component["file"], component["modId"])
    return {"minecraftVersions": list(locks), "components": components}


def package(root: Path = ROOT, minecraft_version: str = "1.8.9") -> list[dict]:
    version = tomllib.loads((root / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
    sources = component_sources(root, version, minecraft_version)
    branding_source = root / (f"game-branding/build/libs/ArcadiumBrand-{version}.jar" if minecraft_version == "1.8.9"
                              else f"game-branding/build/{minecraft_version}/libs/ArcadiumBrand-{version}-{minecraft_version}.jar")
    branding_name = f"ArcadiumBrand-{minecraft_version}.jar"
    all_sources = {**sources, branding_name: (branding_source, "arcadiumclient")}
    prepared = {}
    for name, (source, expected_id) in all_sources.items():
        if not source.is_file():
            raise RuntimeError(f"Build output missing: {source}. Run scripts/build_client.py first.")
        raw = source.read_bytes()
        prepared[name] = (raw, record(raw, name, inspect_component(raw, name, expected_id)))
    components = [prepared[name][1] for name in sources]
    _, branding = prepared[branding_name]
    if minecraft_version != "1.8.9":
        components.append(branding)
    for name, (raw, _) in prepared.items():
        (root / ASSET_PATH / name).write_bytes(raw)
    lock = refresh_bundles(root, version, minecraft_version)
    lock["arcadiumComponents"] = components
    lock["arcadiumBranding"] = {**branding, "source": "game-branding"}
    (root / LOCK_PATHS[minecraft_version]).write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    write_component_catalog(root)
    if minecraft_version != "1.8.9":
        return components
    source = root / "packages/arcadium_common/src/distribution.rs"
    text = source.read_text(encoding="utf-8")
    block = "pub const COMPONENTS: &[(&str, &str)] = &[\n" + "".join(
        f'    ("{component["file"]}", "{component["sha1"]}"),\n' for component in components) + "];"
    text, changed = re.subn(r"pub const COMPONENTS:.*?\];", lambda _: block, text, count=1, flags=re.S)
    if changed != 1:
        raise RuntimeError("Rust component catalog marker is missing")
    text, changed = re.subn(r'pub const BRANDING_SHA1: &str = "[a-f0-9]+";',
                           f'pub const BRANDING_SHA1: &str = "{branding["sha1"]}";', text, count=1)
    if changed != 1:
        raise RuntimeError("Rust branding hash marker is missing")
    source.write_text(text, encoding="utf-8")
    return components


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Package source-built components while preserving all third-party JAR metadata.")
    parser.add_argument("--refresh-bundles", action="store_true", help="Refresh only the bundle exclusion policy and hashes.")
    parser.add_argument("--minecraft-version", choices=MINECRAFT_VERSIONS, action="append")
    parser.add_argument("--verify-components", action="store_true")
    parser.add_argument("--prepare-legacy-notices", type=Path)
    args = parser.parse_args()
    if args.prepare_legacy_notices:
        write_legacy_notices(args.prepare_legacy_notices)
    elif args.verify_components:
        print(json.dumps(verify_components(), indent=2))
    elif args.refresh_bundles:
        version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
        for minecraft in args.minecraft_version or MINECRAFT_VERSIONS:
            refresh_bundles(ROOT, version, minecraft)
    else:
        print(json.dumps({minecraft: package(ROOT, minecraft) for minecraft in args.minecraft_version or MINECRAFT_VERSIONS}, indent=2))
