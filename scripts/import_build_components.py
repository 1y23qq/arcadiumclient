import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from package_client import ASSET_PATH, distribution_locks, inspect_component

ROOT = Path(__file__).resolve().parents[1]


def entries(root=ROOT):
    components = {}
    for catalog in distribution_locks(root).values():
        branding = catalog.get("arcadiumBranding")
        for entry in [*catalog["arcadiumComponents"], *([branding] if branding else [])]:
            if entry["file"] in components and entry["sha256"] != components[entry["file"]]["sha256"]:
                raise RuntimeError("Conflicting source component: " + entry["file"])
            components[entry["file"]] = entry
    return components


def validate(raw, entry):
    if hashlib.sha256(raw).hexdigest() != entry["sha256"]:
        raise RuntimeError("Client component does not match the source catalog: " + entry["file"])
    inspect_component(raw, entry["file"])


def write_bundle(destination, root=ROOT):
    components = entries(root)
    prepared = {name: (root / ASSET_PATH / name).read_bytes() for name in components}
    for name, raw in prepared.items():
        validate(raw, components[name])
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, raw in prepared.items():
            archive.writestr(name, raw)
    return hashlib.sha256(destination.read_bytes()).hexdigest()


def import_bundle(source, expected, root=ROOT):
    if not re.fullmatch(r"[a-f0-9]{64}", expected) or hashlib.sha256(source.read_bytes()).hexdigest() != expected:
        raise RuntimeError("Build input archive checksum does not match")
    components = entries(root)
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        if len(names) != len(components) or set(names) != set(components):
            raise RuntimeError("Build input archive has unexpected components")
        uncompressed = sum(entry.file_size for entry in archive.infolist())
        if uncompressed > 512 * 1024 * 1024:
            raise RuntimeError("Build input archive is too large")
        if uncompressed != sum(entry["bytes"] for entry in components.values()):
            raise RuntimeError("Build input archive sizes do not match the source catalogs")
        prepared = {name: archive.read(name) for name in components}
        for name, raw in prepared.items():
            validate(raw, components[name])
    destination = root / ASSET_PATH
    destination.mkdir(parents=True, exist_ok=True)
    for name, raw in prepared.items():
        (destination / name).write_bytes(raw)
    print("Verified and imported", len(prepared), "source-built client components")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Share verified JVM components between platform builds.")
    parser.add_argument("archive", type=Path)
    parser.add_argument("--sha256")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if args.write:
        print(write_bundle(args.archive))
    else:
        import_bundle(args.archive, args.sha256 or "")
