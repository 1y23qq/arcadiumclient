from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

from modern_distribution import ASSET_PATH, ROOT, adapt_bundle, fetch_dependencies, get_bundle, verify_source_bundle


TARGET = "26.2"
LOADER = "0.20.1"
LAUNCHER_COMMIT = "48e719d7c4eddcda1f0d88eef9bdd2eaf66a6837"
DATA_COMMIT = "45403497daf6ebfcd284c05ac1252d02a427dca8"
PINNED_BUNDLES = {
    "hud-26.2-fabric.mrpack": "e6a991bea892d1e6af828dc36f398c2c782a2c84",
    "performance-26.2-fabric.mrpack": "6c32d5359c347a9522ad82d26260059497a65a45",
    "qol-26.2-fabric.mrpack": "8284853d3f233166fcd427496ebd5e1bf285a2d6",
    "skyblock-26.2-fabric.mrpack": "cf0f1cf3270eb6c0276cbc426577aefc71c7247f",
    "utility-26.2-fabric.mrpack": "c21929c2b4c9f50b9e357dd51072db5444ce07fb",
}
EXPECTED_OVERRIDES = {
    "performance-26.2-fabric.mrpack": {"version": 1, "overrides": {"zfastnoise": {"+breaks": {"noisium": "*"}}}},
    "qol-26.2-fabric.mrpack": {"version": 1, "overrides": {
        "cubes-without-borders": {"+breaks": {"chloride": "*"}},
        "idontwannascrollagain": {"-depends": {"minecraft": "IGNORED"}},
        "nbtac": {"+breaks": {"nbt_ac": "*"}},
        "packed_packs": {"+breaks": {"async-pack-scan": "<26.2.1"}},
    }},
}
SNAPSHOT = {"pinned": PINNED_BUNDLES, "minecraft": TARGET, "loader": LOADER, "expected_overrides": EXPECTED_OVERRIDES}
NVIDIUM = {
    "project": "SfMw2IZN", "version": "2VTUVLLs", "modVersion": "0.4.4-beta6-26.2",
    "file": "nvidium-0.4.4-beta6-26.2.jar", "bytes": 166469,
    "url": "https://cdn.modrinth.com/data/SfMw2IZN/versions/2VTUVLLs/nvidium-0.4.4-beta6-26.2.jar",
    "sha1": "108723a7ebe97fcf0dbc16e154358c7f0553496e",
    "sha512": "f6a70717d3ceb9b8082a1f6e5857740755cf6e6e9b5054c98833f1659caa13e1e973422ee90bb0e9afaa07907bece978fdcd7cb725ff5ebba2b8e0fcc0dbb618",
    "license": "LGPL-3.0-only", "source": "https://github.com/MCRcortex/nvidium", "commit": "bda9ff4",
    "minecraft": TARGET, "sodiumVersions": ["0.9.1", "0.9.2"], "requiresOpenGL": True,
    "enabledByDefault": False,
}


def inherited_scope(root: Path) -> dict:
    source_path = root / "distribution/port-scope-1.21.11.json"
    raw = source_path.read_bytes()
    scope = copy.deepcopy(json.loads(raw))
    choices = scope["features"]
    if len({entry["id"] for entry in choices}) != len(choices) or any(type(entry["port"]) is not bool for entry in choices):
        raise ValueError("Invalid approved feature selections.")
    scope["minecraftVersion"] = TARGET
    scope["baseline"] = "OneClient26.2 Fabric HUD, Performance, QoL, Utility and optional SkyBlock; upstream default states and existing Arcadium exclusions are retained."
    scope["selectionInheritedFrom"] = {"path": source_path.relative_to(root).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(), "minecraftVersion": "1.21.11"}
    scope["nvidiumRecommendation"] = {"firstLaunch": True, "eligibility": "At least one supported NVIDIA GPU in the complete adapter inventory", "installRequiresUserChoice": True}
    return scope


def prepare(root: Path, cache: Path, download: bool = False) -> dict:
    scope = inherited_scope(root)
    scope_raw = (json.dumps(scope, ensure_ascii=False, indent=2) + "\n").encode()
    sources = {name: get_bundle(cache, name, download, **SNAPSHOT) for name in PINNED_BUNDLES}
    prepared = {name: adapt_bundle(raw, name, **SNAPSHOT) for name, raw in sources.items()}
    lock = {
        "upstream": "https://github.com/Polyfrost/OneLauncher", "commit": LAUNCHER_COMMIT,
        "dataStorage": {"repository": "https://github.com/Polyfrost/DataStorageV2", "commit": DATA_COMMIT, "authors": ["Polyfrost"], "license": "GPL-3.0-only"},
        "snapshotDate": "2026-10-25", "minecraftVersion": TARGET, "javaVersion": 25, "loader": "Fabric", "loaderVersion": LOADER,
        "portScope": {"path": f"distribution/port-scope-{TARGET}.json", "sha256": hashlib.sha256(scope_raw).hexdigest()},
        "optionalNvidium": NVIDIUM, "bundles": [],
    }
    previous_path = root / f"distribution/oneclient-{TARGET}.lock.json"
    if previous_path.is_file():
        previous = json.loads(previous_path.read_text())
        for field in ("arcadiumComponents", "componentScopeSha256"):
            if field in previous: lock[field] = previous[field]
    for name, (raw, selected, excluded) in prepared.items():
        original, _ = verify_source_bundle(sources[name], name, **SNAPSHOT)
        lock["bundles"].append({
            "file": name, "enabled": original["enabled"], "category": original["category"],
            "sourceUrl": f"https://data-v2.polyfrost.org/oneclient/bundles/generated/{name}",
            "upstreamSha1": PINNED_BUNDLES[name], "upstreamSha256": hashlib.sha256(sources[name]).hexdigest(),
            "arcadiumSha1": hashlib.sha1(raw).hexdigest(), "arcadiumSha256": hashlib.sha256(raw).hexdigest(),
            "mods": [{"path": entry["path"], "sha1": entry["hashes"]["sha1"], "sha512": entry["hashes"]["sha512"],
                      "downloads": entry["downloads"], "enabled": entry["enabled"], "hidden": entry.get("hidden", False), "type": entry.get("type", ""), "bytes": entry["fileSize"]} for entry in selected],
            "excluded": [{"path": entry["path"], "sha1": entry["hashes"]["sha1"], "reason": "Existing Arcadium source/license replacement policy"} for entry in excluded],
        })
    (root / "distribution").mkdir(parents=True, exist_ok=True)
    assets = root / ASSET_PATH
    assets.mkdir(parents=True, exist_ok=True)
    (root / f"distribution/port-scope-{TARGET}.json").write_bytes(scope_raw)
    for name, (raw, _, _) in prepared.items(): (assets / name).write_bytes(raw)
    previous_path.write_text(json.dumps(lock, indent=2) + "\n")
    return lock


def main() -> None:
    parser = argparse.ArgumentParser(description="Pin the OneClient26.2 baseline and inherit the approved Arcadium feature selections.")
    parser.add_argument("--upstream-cache", type=Path, default=ROOT / "build/modern262/upstream")
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--fetch-dependencies", type=Path)
    parser.add_argument("--include-disabled", action="store_true")
    args = parser.parse_args()
    lock = prepare(ROOT, args.upstream_cache, args.download)
    print("Prepared five pinned26.2 bundles; optional SkyBlock remains disabled.")
    if args.fetch_dependencies:
        print(f"Verified {len(fetch_dependencies(lock, args.fetch_dependencies, args.include_disabled))} mod artifacts.")


if __name__ == "__main__": main()
