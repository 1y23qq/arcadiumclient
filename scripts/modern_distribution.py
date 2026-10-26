from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import urllib.request
import tempfile
import zipfile

from package_client import ASSET_PATH, excluded_bundle_entry
from port_checklist import DEFAULT_CATALOG, DEFAULT_RESULTS, ROOT, catalog_hash, counts, load_catalog, load_state


TARGET = "1.21.11"
CLIENT = "0.5.2"
UPSTREAM = "https://github.com/Polyfrost/DataStorageV2"
UPSTREAM_COMMIT = "a8e6ec3ce5c80ca7a69245f43912adefdd1b862f"
LAUNCHER_COMMIT = "4859c96cc8348aaa3fd269b71b71c6b87b2135ab"
PINNED_BUNDLES = {
    "hud-1.21.11-fabric.mrpack": "e7e3a11f643f85da50346e12ab1d7814fafea3e3",
    "performance-1.21.11-fabric.mrpack": "16bfc37a5361efe363aa242c0f9345b24df72425",
    "qol-1.21.11-fabric.mrpack": "ee836e011db357721a5aefc13663de27375b9e49",
    "utility-1.21.11-fabric.mrpack": "01db8e06f6f6039367d49cd92bd9cfbff0545be1",
}
ALLOWED_MEMBERS = {"modrinth.index.json", "overrides/", "overrides/config/", "overrides/config/fabric_loader_dependencies.json"}
EXPECTED_OVERRIDES = {
    "performance-1.21.11-fabric.mrpack": {
        "version": 1,
        "overrides": {"gnetum": {"+breaks": {"vulkanmod": "*"}}, "zfastnoise": {"+breaks": {"noisium": "*"}}},
    },
    "qol-1.21.11-fabric.mrpack": {
        "version": 1,
        "overrides": {
            "cubes-without-borders": {"+breaks": {"chloride": "*"}},
            "idontwannascrollagain": {"-depends": {"minecraft": "IGNORED"}},
            "nbtac": {"+breaks": {"nbt_ac": "*"}},
            "packed_packs": {"+breaks": {"async-pack-scan": "*"}},
        },
    },
}
DISTRIBUTION_OVERRIDES = {
    "version": 1,
    "overrides": {mod: rules for source in EXPECTED_OVERRIDES.values() for mod, rules in source["overrides"].items()},
}
NVIDIUM = {
    "project": "SfMw2IZN", "version": "4x9ci9sF", "modVersion": "0.4.3-beta13-1.21.11",
    "file": "nvidium-0.4.3-beta13-1.21.11.jar", "bytes": 174412,
    "url": "https://cdn.modrinth.com/data/SfMw2IZN/versions/4x9ci9sF/nvidium-0.4.3-beta13-1.21.11.jar",
    "sha1": "08fa4be6b87c0b586efe7d5ee462f46e7a5d647d",
    "sha512": "12ea425d80d33eff9713876daf03d52874a08cb3ce50848eb861cf5f6008a57fa6fee863f610a823eac055f9ba154ae4054109fd46abc47b74ea2e7a8a9ff69f",
    "published": "2026-07-13T21:33:12.976699Z",
    "license": "LGPL-3.0-only", "source": "https://github.com/MCRcortex/nvidium", "commit": "e600501",
    "minecraft": TARGET, "sodiumVersions": ["0.8.11", "0.8.12"], "requiresOpenGL": True,
    "enabledByDefault": False,
    "compatibilityMods": [
        {
            "modId": "sodium", "replacesSha1": "4a8733fe544168068d12fa94b2ab03e6c0c7bf91",
            "project": "AANobbMI", "version": "NFkjnzWE", "modVersion": "0.8.12+mc1.21.11",
            "file": "sodium-fabric-0.8.12+mc1.21.11.jar", "bytes": 1905890,
            "url": "https://cdn.modrinth.com/data/AANobbMI/versions/NFkjnzWE/sodium-fabric-0.8.12%2Bmc1.21.11.jar",
            "sha1": "4d80592bef36674e7469ae2737a7704b826289ae",
            "sha512": "17fdb8240670d069e9bb4d00cd3d17afe28ea3f7ea7daf27fd32844f302516e7a889686429db81f2c7b73ad4afce703cac8963a8c3943cfebf45d2c570bd8256",
            "published": "2026-05-18T22:12:48.468154Z", "license": "LicenseRef-Polyform-Shield-1.0.0",
            "source": "https://github.com/CaffeineMC/sodium",
        },
        {
            "modId": "sodium-extra", "replacesSha1": "5ee4535324b48628424866eca29a4dceb364d27a",
            "project": "PtjYWJkn", "version": "zmsIDdn6", "modVersion": "0.9.1+mc1.21.11",
            "file": "sodium-extra-fabric-0.9.1+mc1.21.11.jar", "bytes": 442450,
            "url": "https://cdn.modrinth.com/data/PtjYWJkn/versions/zmsIDdn6/sodium-extra-fabric-0.9.1%2Bmc1.21.11.jar",
            "sha1": "49783b5a7f2bec3330ab4e776f91620e5646ac87",
            "sha512": "6fb4228c2466efd5f2a4766f5c29ba31123e75842fdcfd3699f45646abe1ed17418f47d1468066f4e059e6d8f20d583b6d77696e78f3dd8a0af5e041dec84487",
            "published": "2026-06-29T18:42:56.903414Z", "license": "LGPL-3.0-only",
            "source": "https://github.com/FlashyReese/sodium-extra-fabric",
        },
    ],
}


def build_scope(catalog: dict, state: dict, result_raw: bytes) -> dict:
    yes, no, unanswered = counts(state)
    if unanswered:
        raise ValueError(f"Port review is incomplete: {unanswered} unanswered features.")
    if catalog["target_minecraft"] != TARGET or state["catalog_sha256"] != catalog_hash(catalog):
        raise ValueError("Port review targets a different feature catalog or Minecraft version.")
    return {
        "schemaVersion": 1,
        "minecraftVersion": TARGET,
        "plannedClientVersion": CLIENT,
        "sourceMinecraftVersion": catalog["source_minecraft"],
        "sourceClientVersion": catalog["source_client"],
        "review": {
            "completedAt": state["updated_at"],
            "catalogSha256": state["catalog_sha256"],
            "resultSha256": hashlib.sha256(result_raw).hexdigest(),
            "selected": yes,
            "excluded": no,
        },
        "baseline": "OneClient 1.21.11 Fabric HUD, Performance, QoL and Utility; original default enabled states are retained, subject to the existing Arcadium distribution exclusions.",
        "optionalOptimizerSelector": False,
        "features": [{key: feature[key] for key in ("id", "category", "title", "description")} |
                     {"port": state["answers"][feature["id"]]} for feature in catalog["features"]],
    }


def verify_source_bundle(raw: bytes, name: str, *, pinned: dict | None = None,
                         minecraft: str = TARGET, loader: str = "0.19.5",
                         expected_overrides: dict | None = None) -> tuple[dict, dict[str, bytes]]:
    expected = (PINNED_BUNDLES if pinned is None else pinned).get(name)
    if expected is None or hashlib.sha1(raw).hexdigest() != expected:
        raise ValueError(f"Upstream bundle checksum mismatch: {name}")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names) or set(names) - ALLOWED_MEMBERS:
            raise ValueError(f"Unexpected payload in metadata-only upstream bundle: {name}")
        files = {entry: archive.read(entry) for entry in names if not entry.endswith("/")}
    index = json.loads(files["modrinth.index.json"])
    if index.get("dependencies") != {"fabric-loader": loader, "minecraft": minecraft}:
        raise ValueError(f"Unexpected Minecraft or loader baseline: {name}")
    override_path = "overrides/config/fabric_loader_dependencies.json"
    override = json.loads(files[override_path]) if override_path in files else None
    overrides = EXPECTED_OVERRIDES if expected_overrides is None else expected_overrides
    if override != overrides.get(name):
        raise ValueError(f"Unreviewed dependency override: {name}")
    for entry in index["files"]:
        path = PurePosixPath(entry["path"])
        if (path.is_absolute() or len(path.parts) != 2 or "\\" in entry["path"] or
                path.parts[0] not in {"mods", "resourcepacks", "shaderpacks"} or
                path.suffix != (".jar" if path.parts[0] == "mods" else ".zip")):
            raise ValueError(f"Invalid upstream content path: {entry['path']}")
        if any(not url.startswith("https://") for url in entry.get("downloads", [])) or not entry.get("downloads"):
            raise ValueError(f"Invalid upstream download URL: {path}")
        if not isinstance(entry.get("enabled"), bool):
            raise ValueError(f"Missing upstream default state: {path}")
    return index, files


def adapt_bundle(raw: bytes, name: str, **snapshot) -> tuple[bytes, list[dict], list[dict]]:
    index, files = verify_source_bundle(raw, name, **snapshot)
    override_path = "overrides/config/fabric_loader_dependencies.json"
    if override_path in files:
        originals = snapshot.get("expected_overrides", EXPECTED_OVERRIDES)
        combined = {"version": 1, "overrides": {mod: rules for source in originals.values() for mod, rules in source["overrides"].items()}}
        files[override_path] = json.dumps(combined, separators=(",", ":")).encode("utf-8")
    excluded = [entry for entry in index["files"] if excluded_bundle_entry(entry)]
    selected = [entry for entry in index["files"] if not excluded_bundle_entry(entry)]
    index["files"] = selected
    index["name"] = index["name"].replace("OneClient", "Arcadium Client")
    index["versionId"] = CLIENT
    index["updateUrl"] = f"arcadium/bundles/{name}"
    files["modrinth.index.json"] = json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path, content in sorted(files.items()):
            info = zipfile.ZipInfo(path, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content)
    return output.getvalue(), selected, excluded


def get_bundle(cache: Path, name: str, download: bool, **snapshot) -> bytes:
    target = cache / name
    if not target.exists():
        if not download:
            raise FileNotFoundError(f"Missing pinned bundle: {target}. Pass --download to fetch it.")
        url = f"https://data-v2.polyfrost.org/oneclient/bundles/generated/{name}"
        request = urllib.request.Request(url, headers={"User-Agent": "Arcadium-source-build/0.5.2"})
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read(4 * 1024 * 1024 + 1)
        if len(raw) > 4 * 1024 * 1024:
            raise ValueError(f"Unexpected metadata bundle size: {name}")
        verify_source_bundle(raw, name, **snapshot)
        cache.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    raw = target.read_bytes()
    verify_source_bundle(raw, name, **snapshot)
    return raw


def prepare(root: Path, catalog: dict, state: dict, result_raw: bytes, cache: Path, download: bool = False) -> dict:
    scope = build_scope(catalog, state, result_raw)
    sources = {name: get_bundle(cache, name, download) for name in PINNED_BUNDLES}
    prepared = {name: adapt_bundle(raw, name) for name, raw in sources.items()}
    scope_raw = (json.dumps(scope, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    lock = {
        "upstream": "https://github.com/Polyfrost/OneLauncher",
        "commit": LAUNCHER_COMMIT,
        "dataStorage": {"repository": UPSTREAM, "commit": UPSTREAM_COMMIT, "authors": ["Polyfrost"], "license": "GPL-3.0-only"},
        "snapshotDate": "2026-10-24",
        "minecraftVersion": TARGET,
        "javaVersion": 25,
        "loader": "Fabric",
        "loaderVersion": "0.19.5",
        "portScope": {"path": f"distribution/port-scope-{TARGET}.json", "sha256": hashlib.sha256(scope_raw).hexdigest()},
        "optionalNvidium": NVIDIUM,
        "bundles": [],
    }
    previous_path = root / f"distribution/oneclient-{TARGET}.lock.json"
    if previous_path.is_file():
        previous = json.loads(previous_path.read_text(encoding="utf-8"))
        for field in ("arcadiumComponents", "componentScopeSha256"):
            if field in previous:
                lock[field] = previous[field]
    for name, (raw, selected, excluded) in prepared.items():
        source = sources[name]
        lock["bundles"].append({
            "file": name,
            "sourceUrl": f"https://data-v2.polyfrost.org/oneclient/bundles/generated/{name}",
            "upstreamSha1": PINNED_BUNDLES[name],
            "upstreamSha256": hashlib.sha256(source).hexdigest(),
            "arcadiumSha1": hashlib.sha1(raw).hexdigest(),
            "arcadiumSha256": hashlib.sha256(raw).hexdigest(),
            "mods": [{"path": entry["path"], "sha1": entry["hashes"]["sha1"],
                      "sha512": entry["hashes"]["sha512"], "downloads": entry["downloads"],
                      "enabled": entry["enabled"], "hidden": entry.get("hidden", False),
                      "type": entry.get("type", ""), "bytes": entry["fileSize"]} for entry in selected],
            "excluded": [{"path": entry["path"], "sha1": entry["hashes"]["sha1"],
                          "reason": "Replaced by independently built Arcadium components" if
                          PurePosixPath(entry["path"]).name.lower().startswith(("oneconfig-", "compose-bundle-")) else
                          "Excluded by distribution/bundle-policy.json"} for entry in excluded],
        })
    distribution = root / "distribution"
    assets = root / ASSET_PATH
    distribution.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    (distribution / f"port-scope-{TARGET}.json").write_bytes(scope_raw)
    for name, (raw, _, _) in prepared.items():
        (assets / name).write_bytes(raw)
    (distribution / f"oneclient-{TARGET}.lock.json").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    return lock


def fetch_dependencies(lock: dict, destination: Path, include_disabled: bool = False) -> list[Path]:
    unique = {}
    for bundle in lock["bundles"]:
        if not include_disabled and not bundle.get("enabled", True):
            continue
        for entry in bundle["mods"]:
            if not entry["enabled"] and not include_disabled:
                continue
            path = PurePosixPath(entry["path"])
            if path.parts[0] != "mods":
                continue
            prior = unique.setdefault(entry["path"], entry)
            if prior["sha512"] != entry["sha512"]:
                raise ValueError(f"Conflicting dependency snapshots: {entry['path']}")
    destination.mkdir(parents=True, exist_ok=True)

    def fetch(entry: dict) -> Path:
        name = PurePosixPath(entry["path"]).name
        target = destination / name
        if target.exists():
            raw = target.read_bytes()
            if len(raw) != entry["bytes"] or hashlib.sha512(raw).hexdigest() != entry["sha512"]:
                raise ValueError(f"Cached dependency is damaged: {target}")
            return target
        errors = []
        for url in entry["downloads"]:
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "Arcadium-source-build/0.5.2"})
                with urllib.request.urlopen(request, timeout=60) as response:
                    raw = response.read(entry["bytes"] + 1)
                if len(raw) != entry["bytes"] or hashlib.sha512(raw).hexdigest() != entry["sha512"]:
                    raise ValueError(f"Dependency checksum mismatch: {name}")
                temporary = None
                try:
                    with tempfile.NamedTemporaryFile(dir=destination, prefix=f".{name}.", suffix=".part", delete=False) as stream:
                        temporary = Path(stream.name)
                        stream.write(raw)
                    os.replace(temporary, target)
                finally:
                    if temporary is not None and temporary.exists():
                        temporary.unlink()
                print(f"Verified {name}", flush=True)
                return target
            except (OSError, ValueError) as error:
                errors.append(str(error))
        raise ValueError(f"Could not download {name}: {'; '.join(errors)}")

    with ThreadPoolExecutor(max_workers=6) as pool:
        return list(pool.map(fetch, unique.values()))


def main() -> None:
    parser = argparse.ArgumentParser(description="Pin the approved 1.21.11 scope and prepare metadata-only OneClient baseline bundles.")
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--upstream-cache", type=Path, default=ROOT / "build/modern050/upstream")
    parser.add_argument("--download", action="store_true", help="Download only exact pinned metadata bundles missing from the cache")
    parser.add_argument("--fetch-dependencies", type=Path, help="Fetch SHA-512-verified enabled mod JARs to an ignored development cache")
    parser.add_argument("--include-disabled", action="store_true", help="Also fetch optional baseline mods without enabling them")
    args = parser.parse_args()
    catalog = load_catalog(args.catalog)
    state = load_state(args.results, catalog)
    lock = prepare(ROOT, catalog, state, args.results.read_bytes(), args.upstream_cache, args.download)
    unique = {mod["path"] for bundle in lock["bundles"] for mod in bundle["mods"]}
    print(f"Prepared {len(lock['bundles'])} pinned bundles, {len(unique)} unique content files and the approved port scope.")
    if args.fetch_dependencies:
        paths = fetch_dependencies(lock, args.fetch_dependencies, args.include_disabled)
        print(f"Verified {len(paths)} development dependencies.")


if __name__ == "__main__":
    main()
