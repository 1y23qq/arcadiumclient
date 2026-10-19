import argparse
import concurrent.futures
import hashlib
import io
import json
import re
import subprocess
import tarfile
import threading
import urllib.parse
import urllib.request
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parent.parent
LOCK = threading.Lock()
ARCHIVES = {}


def license_name(path):
    name = PurePosixPath(path).name.upper()
    return name.startswith(("LICENSE", "LICENCE", "COPYING", "NOTICE", "UNLICENSE", "COPYRIGHT")) and not name.endswith((".RS", ".C", ".H"))


def normalized_repository(url):
    url = (url or "").removeprefix("git+").removesuffix("/").removesuffix(".git")
    url = url.split("?")[0].split("#")[0]
    url = re.sub(r"^http://", "https://", url)
    parsed = urllib.parse.urlparse(url)
    parts = parsed.path.strip("/").split("/")
    if parsed.netloc.lower() == "github.com" and len(parts) > 2 and parts[2] in ("tree", "blob"):
        url = "https://github.com/" + "/".join(parts[:2])
    return url


def published_origin(package, override=None):
    if override and override.get("revision"):
        return normalized_repository(override["repository"]), override["revision"], override.get("path_in_vcs", ""), False
    folder = Path(package["manifest_path"]).parent
    vcs_path = folder / ".cargo_vcs_info.json"
    if vcs_path.exists():
        vcs = json.loads(vcs_path.read_text("utf-8"))
        revision = vcs.get("git", {}).get("sha1")
        package_path = vcs.get("path_in_vcs", "")
        return normalized_repository(package.get("repository") or package.get("homepage")), revision, package_path, vcs.get("git", {}).get("dirty", False)
    source = package.get("source") or ""
    if source.startswith("git+") and "#" in source:
        revision = source.rsplit("#", 1)[1]
        repository = normalized_repository(source)
        git_root = Path(subprocess.check_output(["git", "-C", str(folder), "rev-parse", "--show-toplevel"], text=True, encoding="utf-8").strip())
        actual = subprocess.check_output(["git", "-C", str(git_root), "rev-parse", "HEAD"], text=True, encoding="utf-8").strip()
        if actual != revision:
            raise ValueError("Git checkout does not match the Cargo source revision")
        return repository, revision, folder.relative_to(git_root).as_posix(), False
    return normalized_repository(package.get("repository") or package.get("homepage")), None, "", False


def source_archive(repository, revision, cache):
    key = (repository, revision)
    with LOCK:
        future = ARCHIVES.get(key)
        if future is None:
            future = concurrent.futures.Future()
            ARCHIVES[key] = future
            owner = True
        else:
            owner = False
    if not owner:
        return future.result()
    try:
        parsed = urllib.parse.urlparse(repository)
        parts = parsed.path.strip("/").split("/")
        if parsed.netloc.lower() != "github.com" or len(parts) != 2:
            raise ValueError("Unsupported pinned repository: " + repository)
        url = f"https://codeload.github.com/{parts[0]}/{parts[1]}/tar.gz/{revision}"
        cache_path = cache / (hashlib.sha256((repository + "@" + revision).encode()).hexdigest() + ".tar.gz")
        if cache_path.exists():
            data = cache_path.read_bytes()
        else:
            request = urllib.request.Request(url, headers={"User-Agent": "Arcadium-license-audit"})
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read(100 * 1024 * 1024 + 1)
            if len(data) > 100 * 1024 * 1024:
                raise ValueError("Source archive exceeded the audit size limit")
            cache.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(data)
        files = {}
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
            for member in archive.getmembers():
                if not member.isfile():
                    continue
                relative = PurePosixPath(member.name).parts[1:]
                if not relative or ".." in relative or member.size > 2 * 1024 * 1024:
                    continue
                name = "/".join(relative)
                if license_name(name):
                    files[name] = archive.extractfile(member).read()
        result = {"url": url, "sha256": hashlib.sha256(data).hexdigest(), "notices": files}
        future.set_result(result)
        return result
    except Exception as error:
        future.set_exception(error)
        raise


def resolve_entry(entry, package, output, cache, override=None):
    name, version = entry["name"], entry["version"]
    repository, revision, package_path, dirty = published_origin(package, override)
    if not revision:
        return {"name": name, "version": version, "resolved": False, "reason": "Published crate has no Git revision; release/tag evidence is required", "repository": repository}
    if not re.fullmatch(r"[0-9a-fA-F]{40}", revision):
        raise ValueError("Unexpected source revision")
    relative = PurePosixPath(package_path)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("Unsafe path_in_vcs")
    if override and override.get("mode") == "raw-files":
        files = {}
        for source in override["files"]:
            if PurePosixPath(source).is_absolute() or ".." in PurePosixPath(source).parts:
                raise ValueError("Unsafe notice path")
            parsed = urllib.parse.urlparse(repository)
            url = "https://raw.githubusercontent.com" + parsed.path + "/" + revision + "/" + source
            request = urllib.request.Request(url, headers={"User-Agent": "Arcadium-license-audit"})
            files[source] = urllib.request.urlopen(request, timeout=30).read()
        archive = {"url": repository + "/tree/" + revision, "sha256": None, "notices": files}
    else:
        archive = source_archive(repository, revision, cache)
    directories = [relative] + list(relative.parents)
    candidates = []
    for directory in directories:
        prefix = "" if directory.as_posix() == "." else directory.as_posix() + "/"
        for source, data in archive["notices"].items():
            if not source.startswith(prefix):
                continue
            tail = source[len(prefix):]
            if "/" not in tail or PurePosixPath(tail).parts[0].upper() in ("LICENSES", "LICENCES"):
                candidates.append((source, data))
        if candidates:
            break
    if not candidates:
        return {"name": name, "version": version, "resolved": False, "reason": "No original notice at the pinned crate/workspace ancestors", "repository": repository, "revision": revision, "path_in_vcs": package_path}
    records = []
    for source, data in sorted(candidates):
        destination = output / name / version / "upstream" / PurePosixPath(source).name
        if destination.exists() and destination.read_bytes() != data:
            destination = destination.with_name(hashlib.sha256(source.encode()).hexdigest()[:8] + "-" + destination.name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        records.append({"file": destination.relative_to(output).as_posix(), "sha256": hashlib.sha256(data).hexdigest(), "source": repository + "/blob/" + revision + "/" + source, "upstream_path": source})
    entry["notice_files"] = records
    if override and override.get("license_selection"):
        entry["license_selection"] = override["license_selection"]
    if name == "interfrost":
        entry["license_selection"] = "MIT"
        entry.pop("selected_license", None)
    return {"name": name, "version": version, "resolved": True, "repository": repository, "revision": revision, "path_in_vcs": package_path, "published_source_marked_dirty": dirty, "archive": {"url": archive["url"], "sha256": archive["sha256"]}, "notice_files": records}


def resolve_sibling(entry, package, donor, donor_package, output):
    target_vcs = json.loads((Path(package["manifest_path"]).parent / ".cargo_vcs_info.json").read_text("utf-8"))
    donor_vcs = json.loads((Path(donor_package["manifest_path"]).parent / ".cargo_vcs_info.json").read_text("utf-8"))
    if target_vcs["git"]["sha1"] != donor_vcs["git"]["sha1"]:
        raise ValueError("Donor crate has a different published workspace revision")
    target_path = PurePosixPath(target_vcs.get("path_in_vcs", ""))
    donor_path = PurePosixPath(donor_vcs.get("path_in_vcs", ""))
    if donor_path != target_path and donor_path not in target_path.parents:
        raise ValueError("Donor is not a published ancestor workspace crate")
    records = []
    for notice in donor["notice_files"]:
        data = (output / notice["file"]).read_bytes()
        if hashlib.sha256(data).hexdigest() != notice["sha256"]:
            raise ValueError("Donor notice hash differs from the original export")
        destination = output / entry["name"] / entry["version"] / "upstream" / Path(notice["file"]).name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        records.append({"file": destination.relative_to(output).as_posix(), "sha256": notice["sha256"], "source": f"https://crates.io/api/v1/crates/{donor['name']}/{donor['version']}/download", "donor_notice": notice["file"]})
    entry["notice_files"] = records
    return {"name": entry["name"], "version": entry["version"], "resolved": True, "mode": "published-same-workspace-crate", "revision": target_vcs["git"]["sha1"], "donor_crate": {"name": donor["name"], "version": donor["version"], "archive_sha256": donor["archive_sha256"]}, "notice_files": records}


def resolve_declaration(entry, package, output, override):
    selected = override["license_selection"]
    reference = output / "_standard" / (selected + ".txt")
    data = reference.read_bytes()
    destination = output / entry["name"] / entry["version"] / "declared-license"
    destination.mkdir(parents=True, exist_ok=True)
    license_file = destination / (selected + "-REFERENCE.txt")
    license_file.write_bytes(data)
    folder = Path(package["manifest_path"]).parent
    original_manifest = folder / "Cargo.toml.orig"
    if not original_manifest.exists():
        original_manifest = folder / "Cargo.toml"
    declaration = destination / "PUBLISHED-Cargo.toml.txt"
    declaration.write_bytes(original_manifest.read_bytes())
    explanation = (
        f"Package: {entry['name']} {entry['version']}\n"
        f"Published license expression: {entry['license_expression']}\n"
        f"Published authors: {json.dumps(entry['authors'], ensure_ascii=False)}\n"
        f"Locked crate SHA256: {entry['archive_sha256']}\n"
        f"Source: https://crates.io/api/v1/crates/{entry['name']}/{entry['version']}/download\n\n"
        "The published crate and its identified source omit a standalone original license file.\n"
        "The package explicitly declares the SPDX license above; the canonical license text is\n"
        "supplied as a reference. It is not represented as a recovered upstream copyright notice.\n"
        "No author names, copyright years or copyright ownership statements have been invented.\n"
    )
    for source in override.get("copyright_sources", []):
        text = (folder / source).read_text("utf-8")
        if text.startswith("/*") and "*/" in text:
            explanation += "\nOriginal source header from " + source + ":\n" + text[:text.index("*/") + 2] + "\n"
    notice_file = destination / "NOTICE.txt"
    notice_file.write_text(explanation, encoding="utf-8")
    records = [{"file": path.relative_to(output).as_posix(), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "kind": "published-SPDX-declaration-and-canonical-reference"} for path in [license_file, declaration, notice_file]]
    entry["notice_files"] = records
    entry["notice_resolution"] = "published-SPDX-declaration-and-canonical-reference"
    entry["license_selection"] = selected
    return {"name": entry["name"], "version": entry["version"], "resolved": True, "mode": "published-SPDX-declaration-and-canonical-reference", "original_standalone_notice_recovered": False, "source_revision": override.get("revision"), "notice_files": records}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", type=Path, help="Existing cargo metadata --format-version 1 JSON")
    parser.add_argument("--output", type=Path, default=ROOT / "licenses/rust")
    parser.add_argument("--cache", type=Path, default=ROOT / "build/license-audit/rust-source-cache")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--overrides", type=Path, default=ROOT / "licenses/rust/upstream-overrides.json")
    args = parser.parse_args()
    if args.metadata:
        metadata = json.loads(args.metadata.read_text("utf-8"))
    else:
        metadata = json.loads(subprocess.check_output(["cargo", "metadata", "--locked", "--format-version", "1"], cwd=ROOT, text=True, encoding="utf-8"))
    output = args.output.resolve()
    if not output.is_relative_to(ROOT):
        raise ValueError("Notice output must remain inside this source tree")
    manifest_file = output / "manifest.json"
    manifest = json.loads(manifest_file.read_text("utf-8"))
    overrides = json.loads(args.overrides.read_text("utf-8")) if args.overrides.exists() else []
    overrides = {(item["name"], item["version"]): item for item in overrides}
    packages = {(p["name"], p["version"]): p for p in metadata["packages"]}
    manifest_entries = {(entry["name"], entry["version"]): entry for entry in manifest}
    pending = [entry for entry in manifest if not entry["notice_files"]]

    def work(entry):
        try:
            package = packages[entry["name"], entry["version"]]
            override = overrides.get((entry["name"], entry["version"]))
            if override and override.get("mode") == "sibling-crate":
                donor_id = (override["donor"]["name"], override["donor"]["version"])
                return resolve_sibling(entry, package, manifest_entries[donor_id], packages[donor_id], output)
            if override and override.get("mode") == "declared-license":
                return resolve_declaration(entry, package, output, override)
            return resolve_entry(entry, package, output, args.cache, override)
        except Exception as error:
            return {"name": entry["name"], "version": entry["version"], "resolved": False, "reason": str(error)}

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        results = list(executor.map(work, pending))
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    existing_file = output / "upstream-resolutions.json"
    existing = json.loads(existing_file.read_text("utf-8")) if existing_file.exists() else []
    by_id = {(result["name"], result["version"]): result for result in existing + results}
    existing_file.write_text(json.dumps(sorted(by_id.values(), key=lambda row: (row["name"], row["version"])), indent=2) + "\n", encoding="utf-8")
    unresolved = [entry for entry in manifest if not entry["notice_files"]]
    (output / "unresolved.json").write_text(json.dumps(unresolved, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"attempted": len(pending), "resolved": sum(result["resolved"] for result in results), "remaining": len(unresolved)}))
    for result in results:
        if not result["resolved"]:
            print(result["name"], result["version"], result["reason"])


if __name__ == "__main__":
    main()
