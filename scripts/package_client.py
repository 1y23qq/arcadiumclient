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
EXCLUDED_PREFIXES = ("chattweaks-", "crashpatch-", "polyplus-", "polysprint-", "newdefault+")
REPLACED_LIBRARY_PREFIXES = ("oneconfig-", "compose-bundle-")
EXCLUDED_MOD_IDS = {"chattweaks", "crashpatch", "polyplus", "polysprint"}
EXTERNAL_ONLY_MOD_IDS = {"argentum", "argentum-extras", "cera"}
POLICY = json.loads((ROOT / "distribution/bundle-policy.json").read_text(encoding="utf-8"))
EXCLUDED_HASHES = {artifact["sha1"].lower() for entry in POLICY["excluded"] for artifact in entry.get("historicalArtifacts", [])}


def read_properties(path: Path) -> dict[str, str]:
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def excluded_bundle_entry(entry: dict) -> bool:
    name = PurePosixPath(entry["path"]).name.lower()
    checksum = str(entry.get("sha1") or entry.get("hashes", {}).get("sha1", "")).lower()
    return name.startswith(EXCLUDED_PREFIXES + REPLACED_LIBRARY_PREFIXES) or checksum in EXCLUDED_HASHES


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


def component_sources(root: Path, version: str) -> dict[str, tuple[Path, str]]:
    core = read_properties(root / "client-ui/gradle.properties")["version"]
    versions = tomllib.loads((root / "client-ui/gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]
    compose = versions["compose"]
    if isinstance(compose, dict):
        compose = compose["version"]
    return {
        "ArcadiumCore-1.8.9.jar": (root / f"client-ui/bootstrap/versions/1.8.9-ornithe/build/libs/OneConfig-1.8.9-ornithe-{core}.jar", "oneconfig"),
        "ArcadiumFeatures-1.8.9.jar": (root / f"client-runtime/build/libs/ArcadiumRuntime-{version}.jar", "arcadium_runtime"),
        "ArcadiumRenderer-1.8.9.jar": (root / f"client-ui/modules/compose-bundle/build/libs/compose-bundle-1.0.5+compose.{compose}.jar", "compose-multiplatform"),
        "ArcadiumForgeCompat-1.8.9.jar": (root / f"forge-compat/build/libs/ArcadiumForgeCompat-{version}.jar", "arcadium_forge_compat"),
    }


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


def refresh_bundles(root: Path, version: str) -> dict:
    assets = root / ASSET_PATH
    lock_path = root / "distribution/oneclient-1.8.9.lock.json"
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
    for bundle in manifest["versions"]["1.8.9"]["ornithe"]:
        bundle["sha1"] = by_name[PurePosixPath(bundle["path"]).name]["arcadiumSha1"]
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    lock_path.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    return lock


def package(root: Path = ROOT) -> list[dict]:
    version = tomllib.loads((root / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
    sources = component_sources(root, version)
    branding_source = root / f"game-branding/build/libs/ArcadiumBrand-{version}.jar"
    all_sources = {**sources, "ArcadiumBrand-1.8.9.jar": (branding_source, "arcadiumclient")}
    prepared = {}
    for name, (source, expected_id) in all_sources.items():
        if not source.is_file():
            raise RuntimeError(f"Build output missing: {source}. Run scripts/build_client.py first.")
        raw = source.read_bytes()
        prepared[name] = (raw, record(raw, name, inspect_component(raw, name, expected_id)))
    components = [prepared[name][1] for name in sources]
    _, branding = prepared["ArcadiumBrand-1.8.9.jar"]
    for name, (raw, _) in prepared.items():
        (root / ASSET_PATH / name).write_bytes(raw)
    lock = refresh_bundles(root, version)
    lock["arcadiumComponents"] = components
    lock["arcadiumBranding"] = {**branding, "source": "game-branding"}
    (root / "distribution/oneclient-1.8.9.lock.json").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
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
    args = parser.parse_args()
    if args.refresh_bundles:
        version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
        refresh_bundles(ROOT, version)
    else:
        print(json.dumps(package(), indent=2))
