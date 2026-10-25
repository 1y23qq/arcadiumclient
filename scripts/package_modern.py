from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tomllib

from package_client import ASSET_PATH, inspect_component, read_properties, record


ROOT = Path(__file__).resolve().parents[1]
TARGET = "1.21.11"
LOCK_PATH = Path(f"distribution/oneclient-{TARGET}.lock.json")
PROJECTS = {"1.21.11": ("client-modern", "ArcadiumModern"), "26.2": ("client-next", "ArcadiumNext")}


def component_sources(root: Path, target: str = TARGET) -> dict[str, tuple[Path, str]]:
    project, archive = PROJECTS[target]
    properties = read_properties(root / project / "gradle.properties")
    version = properties["modVersion"]
    core = properties["coreVersion"]
    compose = tomllib.loads((root / "client-ui/gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]["compose"]
    return {
        f"ArcadiumCore-{target}.jar": (root / f"client-ui/bootstrap/versions/{target}-fabric/build/libs/OneConfig-{target}-fabric-{core}.jar", "oneconfig"),
        f"ArcadiumFeatures-{target}.jar": (root / f"{project}/build/libs/{archive}-{version}.jar", "arcadium_runtime"),
        f"ArcadiumRenderer-{target}.jar": (root / f"client-ui/modules/compose-bundle/build/libs/compose-bundle-1.0.5+compose.{compose}.jar", "compose-multiplatform"),
    }


def stage(root: Path = ROOT, target: str = TARGET) -> list[dict]:
    lock_path = root / f"distribution/oneclient-{target}.lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    scope = lock["portScope"]
    scope_raw = (root / scope["path"]).read_bytes()
    if hashlib.sha256(scope_raw).hexdigest() != scope["sha256"]:
        raise ValueError(f"Approved {target} port scope has changed. Prepare the distribution snapshot first.")
    assets = root / ASSET_PATH
    prepared = {}
    for name, (source, expected_id) in component_sources(root, target).items():
        if not source.is_file():
            raise FileNotFoundError(f"Build the {target} source component first: {source}")
        raw = source.read_bytes()
        metadata = inspect_component(raw, name, expected_id)
        if name.startswith("ArcadiumFeatures-") and metadata.get("depends", {}).get("minecraft") != target:
            raise ValueError("The modern runtime was built for a different Minecraft version.")
        prepared[name] = raw, record(raw, name, metadata)
    components = [value[1] for value in prepared.values()]
    catalog_path = assets / "client-components.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8")) if catalog_path.exists() else {}
    for name, (raw, _) in prepared.items():
        (assets / name).write_bytes(raw)
    lock["arcadiumComponents"] = components
    lock["componentScopeSha256"] = scope["sha256"]
    lock_path.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    catalog[target] = components
    catalog_path.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    return components


def main() -> None:
    parser = argparse.ArgumentParser(description="Stage verified source-built modern Minecraft components without publishing a release.")
    parser.add_argument("--minecraft-version", choices=PROJECTS, default=TARGET)
    args = parser.parse_args()
    for component in stage(target=args.minecraft_version):
        print(f"Staged {component['file']} ({component['bytes']} bytes)")


if __name__ == "__main__":
    main()
