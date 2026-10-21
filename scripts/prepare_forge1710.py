from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "distribution/forge-1.7.10.dependencies.json"
PROFILE = ROOT / "packages/arcadium_common/assets/distribution/forge-1.7.10.profile.json"


def verified_download(record: dict, destination: Path) -> Path:
    algorithm = "sha256" if record.get("sha256") else "sha1"
    expected = record[algorithm]
    size = record.get("bytes", record.get("size"))

    def valid(path: Path) -> bool:
        if not path.is_file() or size and path.stat().st_size != size:
            return False
        with path.open("rb") as source:
            return hashlib.file_digest(source, algorithm).hexdigest() == expected

    if valid(destination):
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".download")
    request = urllib.request.Request(record["url"], headers={"User-Agent": "ArcadiumBuild"})
    with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
        shutil.copyfileobj(response, output)
    if not valid(temporary):
        raise RuntimeError(f"Dependency verification failed: {destination.name}")
    temporary.replace(destination)
    return destination


def library_path(name: str) -> Path:
    group, artifact, version, *classifier = name.split(":")
    suffix = "-" + classifier[0] if classifier else ""
    return Path(group.replace(".", "/")) / artifact / version / f"{artifact}-{version}{suffix}.jar"


def rules_allow(rules: list[dict], system: str, arch: str) -> bool:
    if not rules:
        return True
    allowed = False
    for rule in rules:
        target = rule.get("os", {})
        if target.get("name", system) != system or target.get("arch", arch) != arch:
            continue
        if rule.get("features"):
            continue
        allowed = rule["action"] == "allow"
    return allowed


def prepare_dependencies(cache: Path) -> tuple[dict, dict, list, Path]:
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    profile_raw = PROFILE.read_bytes()
    if hashlib.sha256(profile_raw).hexdigest() != lock["profile"]["sha256"]:
        raise RuntimeError("The pinned Forge launch profile failed verification.")
    profile = json.loads(profile_raw)
    system = {"Windows": "windows", "Darwin": "osx", "Linux": "linux"}[platform.system()]
    arch = "aarch64" if platform.machine().lower() in {"arm64", "aarch64"} else "x86_64"
    selected = [lib for lib in profile["libraries"] if rules_allow(lib.get("rules", []), system, arch)]
    libraries = [(record, cache / "libraries" / record["path"]) for record in lock.get("libraries", [])]
    libraries += [(lib["downloads"]["artifact"], cache / "libraries" / library_path(lib["name"]))
                 for lib in selected if lib.get("downloads", {}).get("artifact", {}).get("url")]
    client = cache / "versions/1.7.10/1.7.10.jar"
    downloads = libraries + [(profile["downloads"]["client"], client)]
    downloads += [(record, cache / "mods" / record["file"]) for record in lock["mods"]]
    with ThreadPoolExecutor(max_workers=8) as executor:
        list(executor.map(lambda item: verified_download(*item), downloads))
    return lock, profile, libraries, client


def prepare(directory: Path, cache: Path) -> dict:
    directory = directory.resolve()
    if directory.exists() and any(directory.iterdir()) and not (directory / ".arcadium-forge-probe").is_file():
        raise RuntimeError("Use an empty directory for the isolated Forge verification profile.")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / ".arcadium-forge-probe").touch()
    lock, profile, libraries, client = prepare_dependencies(cache)
    system = {"Windows": "windows", "Darwin": "osx", "Linux": "linux"}[platform.system()]
    arch = "aarch64" if platform.machine().lower() in {"arm64", "aarch64"} else "x86_64"
    (directory / "mods").mkdir(exist_ok=True)
    for record in lock["mods"]:
        shutil.copy2(cache / "mods" / record["file"], directory / "mods" / record["file"])
    index_record = profile["assetIndex"]
    index_path = cache / "assets/indexes" / (index_record["id"] + ".json")
    verified_download(index_record, index_path)
    index = json.loads(index_path.read_bytes())
    objects = []
    for obj in {obj["hash"]: obj for obj in index["objects"].values()}.values():
        digest = obj["hash"]
        objects.append(({"url": f"https://resources.download.minecraft.net/{digest[:2]}/{digest}",
                         "sha1": digest, "size": obj["size"]}, cache / "assets/objects" / digest[:2] / digest))
    with ThreadPoolExecutor(max_workers=12) as executor:
        list(executor.map(lambda item: verified_download(*item), objects))
    natives = directory / "natives"
    natives.mkdir(exist_ok=True)
    replacements = {
        "${natives_directory}": str(natives),
        "${classpath}": os.pathsep.join(str(path) for _, path in libraries) + os.pathsep + str(client),
        "${auth_player_name}": "ArcadiumForgeQA",
        "${version_name}": profile["id"],
        "${game_directory}": str(directory),
        "${assets_root}": str(cache / "assets"),
        "${assets_index_name}": index_record["id"],
        "${auth_uuid}": "00000000-0000-0000-0000-000000000001",
        "${auth_access_token}": "0",
        "${user_properties}": "{}",
        "${user_type}": "legacy",
    }

    def arguments(key: str) -> list[str]:
        result = []
        for arg in profile["arguments"][key]:
            if isinstance(arg, dict):
                if not rules_allow(arg.get("rules", []), system, arch):
                    continue
                arg = arg["value"]
            for value in arg if isinstance(arg, list) else [arg]:
                for token, replacement in replacements.items():
                    value = value.replace(token, replacement)
                result.append(value)
        return result

    command = ["-Xms512M", "-Xmx3G", *arguments("jvm"), profile["mainClass"], *arguments("game"),
               "--width", "1280", "--height", "720"]
    result = {"directory": str(directory), "javaArguments": command, "libraries": len(libraries),
              "mods": [entry["file"] for entry in lock["mods"]]}
    (directory / "launch.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Prepare an isolated Forge + Angelica verification profile.")
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=ROOT / "build/forge1710/cache")
    args = parser.parse_args()
    prepared = prepare(args.directory, args.cache.resolve())
    print(json.dumps({key: value for key, value in prepared.items() if key != "javaArguments"}, indent=2))
