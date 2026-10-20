import argparse
import hashlib
from html import escape
import json
from pathlib import Path, PurePosixPath
import plistlib
import re
import subprocess
import tarfile
import tomllib

from build_macos import MACOS_MINIMUMS
from check_macos_compatibility import inspect_macho

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_REPOSITORY = "https://github.com/0aurorq3/arcadium-public"
PLATFORMS = ("windows-x64", "macos-arm64", "macos-x64")
APP_NAME = "Arcadium Client.app"


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def payload_files(manifest):
    files = {}
    for entry in manifest.get("files", []):
        name = entry.get("path", "")
        if manifest.get("platform") == "windows-x64":
            name = name.replace("\\", "/")
        path = PurePosixPath(name)
        digest = entry.get("sha256", "")
        if (not name or "\\" in name or path.is_absolute()
                or any(part in {".", ".."} for part in path.parts)
                or str(path) != name or name in files
                or not re.fullmatch(r"[a-f0-9]{64}", digest)):
            raise RuntimeError(f"Invalid manifest payload: {name}")
        files[name] = digest
    if not files:
        raise RuntimeError("Package manifest contains no payload files.")
    return files


def load_manifest(path, version, platform):
    manifest = json.loads(path.read_text(encoding="utf-8-sig"))
    if (manifest.get("product") != "Arcadium Client"
            or manifest.get("launcherVersion") != version
            or manifest.get("platform") != platform):
        raise RuntimeError(f"Package manifest does not match this release: {path}")
    return manifest, payload_files(manifest)


def asset(path, expected):
    digest = sha256(path)
    if digest != expected:
        raise RuntimeError(f"Package checksum mismatch: {path}")
    return {
        "file": path.name,
        "bytes": path.stat().st_size,
        "sha256": digest,
        "path": str(path),
    }


def verify_windows(version, installers):
    manifest_path = installers / "package-manifest.json"
    manifest, files = load_manifest(manifest_path, version, "windows-x64")
    installer = installers / f"ArcadiumClientSetup-{version}-x64.exe"
    download = asset(installer, manifest["installerSha256"])
    with installer.open("rb") as stream:
        if stream.read(2) != b"MZ":
            raise RuntimeError("Windows installer is not an executable.")
    stages=[ROOT / "build/package/windows-x64"]+sorted((ROOT / "build/package").glob("windows-x64-*"),key=lambda path:path.stat().st_mtime,reverse=True)
    staged=next((stage.resolve() for stage in stages if (stage / "Arcadium.exe").is_file() and sha256(stage / "Arcadium.exe")==files.get("Arcadium.exe")),None)
    if staged is None:
        raise RuntimeError("The verified Windows staging directory is missing.")
    for name, digest in files.items():
        path = (staged / name).resolve()
        if not path.is_relative_to(staged) or sha256(path) != digest:
            raise RuntimeError(f"Invalid staged Windows payload: {name}")
    actual = {path.relative_to(staged).as_posix() for path in staged.rglob("*") if path.is_file()}
    if actual != set(files):
        raise RuntimeError("Windows staged file list does not match its manifest.")
    return {"download": download, "update": download, "manifest": str(manifest_path),
            "verified_payload_files": len(files)}


def verify_macos(version, installers, platform):
    directory = installers / platform
    manifest_path = directory / f"package-manifest-{platform}.json"
    manifest, files = load_manifest(manifest_path, version, platform)
    archive_name = f"ArcadiumClient-{version}-{platform}.app.tar.gz"
    dmg_name = f"ArcadiumClient-{version}-{platform}.dmg"
    if manifest.get("archive") != archive_name or manifest.get("dmg") != dmg_name:
        raise RuntimeError(f"Unexpected macOS asset names: {platform}")
    update = asset(directory / archive_name, manifest["installerSha256"])
    download = asset(directory / dmg_name, manifest["dmgSha256"])
    with (directory / dmg_name).open("rb") as stream:
        stream.seek(-512, 2)
        if stream.read(4) != b"koly":
            raise RuntimeError(f"Invalid macOS disk image: {platform}")
    seen = set()
    seen_files = set()
    total = 0
    metadata = None
    executable_header = None
    native_minimum = None
    with tarfile.open(directory / archive_name, "r:gz") as archive:
        for entry in archive:
            path = PurePosixPath(entry.name)
            if ("\\" in entry.name or path.is_absolute()
                    or any(part in {".", ".."} for part in path.parts)
                    or str(path) != entry.name or not path.parts or path.parts[0] != APP_NAME
                    or entry.name in seen or not (entry.isfile() or entry.isdir())):
                raise RuntimeError(f"Invalid app archive entry: {entry.name}")
            seen.add(entry.name)
            total += entry.size
            if len(seen) > 10_000 or total > 1024 * 1024 * 1024:
                raise RuntimeError("macOS update archive exceeds updater limits.")
            if entry.isdir():
                continue
            relative = path.relative_to(APP_NAME).as_posix()
            if relative not in files:
                raise RuntimeError(f"Unlisted app payload: {relative}")
            stream = archive.extractfile(entry)
            if hashlib.file_digest(stream, "sha256").hexdigest() != files[relative]:
                raise RuntimeError(f"Invalid app payload: {relative}")
            seen_files.add(relative)
            if relative == "Contents/Info.plist":
                metadata = plistlib.loads(archive.extractfile(entry).read())
            if relative == "Contents/MacOS/Arcadium":
                if not entry.mode & 0o111:
                    raise RuntimeError("macOS launcher is not executable.")
                executable = archive.extractfile(entry).read()
                executable_header = executable[:8]
                try:
                    slices = inspect_macho(executable)
                    if len(slices) == 1:
                        native_minimum = slices[0].get("minimumSystemVersion")
                except (ValueError, KeyError):
                    raise RuntimeError(f"Invalid macOS executable: {platform}") from None
    if seen_files != set(files):
        raise RuntimeError(f"macOS app payload list does not match its manifest: {platform}")
    if metadata is None or metadata.get("CFBundleShortVersionString") != version:
        raise RuntimeError(f"macOS app version mismatch: {platform}")
    cpu = 0x0100000C if platform == "macos-arm64" else 0x01000007
    if (executable_header is None or executable_header[:4] != b"\xcf\xfa\xed\xfe"
            or int.from_bytes(executable_header[4:8], "little") != cpu):
        raise RuntimeError(f"macOS launcher architecture mismatch: {platform}")
    minimum = MACOS_MINIMUMS[platform]
    if (metadata.get("LSMinimumSystemVersion") != minimum
            or manifest.get("minimumSystemVersion", minimum) != minimum):
        raise RuntimeError(f"macOS package minimum OS mismatch: {platform}")
    def os_version(value):
        if not isinstance(value, str) or not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", value):
            raise RuntimeError(f"macOS executable minimum OS is missing: {platform}")
        parts = tuple(map(int, value.split(".")))
        return parts + (0,) * (3 - len(parts))
    if os_version(native_minimum) > os_version(minimum):
        raise RuntimeError(f"macOS executable minimum OS exceeds package support: {platform}")
    return {"download": download, "update": update, "manifest": str(manifest_path),
            "minimumSystemVersion": minimum,
            "verified_payload_files": len(files)}


def verify_current_components(platforms):
    lock_path = ROOT / "distribution/oneclient-1.8.9.lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    catalog = lock_path.read_bytes().replace(b"\r\n", b"\n")
    lock_digests = {hashlib.sha256(value).hexdigest()
                    for value in [catalog, catalog.replace(b"\n", b"\r\n")]}
    for platform, package in platforms.items():
        manifest = json.loads(Path(package["manifest"]).read_text(encoding="utf-8-sig"))
        files = payload_files(manifest)
        prefix = "" if platform == "windows-x64" else "Contents/Resources/"
        if files.get(prefix + "distribution-lock.json") not in lock_digests:
            raise RuntimeError(f"Package was built with an outdated client catalog: {platform}")
        expected_components = {prefix + "components/" + component["file"]: component["sha256"]
                               for component in lock["arcadiumComponents"]}
        actual_components = {name: digest for name, digest in files.items()
                             if name.startswith(prefix + "components/")}
        if actual_components != expected_components:
            raise RuntimeError(f"Package client components do not match this release: {platform}")


def update_site(version, stable, platforms):
    website = ROOT / "sites/arcadium-official/public"
    metadata = {"version": version, "prerelease": not stable, "platforms": {}}
    redirects = []
    for platform, package in platforms.items():
        download = package["download"]
        values = {key: download[key] for key in ["file", "bytes", "sha256"]}
        if "minimumSystemVersion" in package:
            values["minimumSystemVersion"] = package["minimumSystemVersion"]
        values["url"] = f"{PUBLIC_REPOSITORY}/releases/download/v{version}/{download['file']}"
        metadata["platforms"][platform] = values
        route = "windows" if platform == "windows-x64" else platform
        redirects.append(f"/download/{route} {values['url']} 302")
    metadata.update({key: metadata["platforms"]["windows-x64"][key] for key in ["bytes", "sha256", "url"]})
    (website / "release.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    (website / "_redirects").write_text("\n".join(redirects) + "\n", encoding="utf-8")
    common = {"version": version, "label": version + ("" if stable else " Beta"),
              "channel": "Stable" if stable else "Beta"}
    for page in [website / "index.html", website / "download/index.html"]:
        content = page.read_text(encoding="utf-8")
        for key, value in common.items():
            pattern = rf"(\bdata-release-{key}(?:\s[^>]*)?>)[^<]*(</[a-zA-Z][^>]*>)"
            content = re.sub(pattern, lambda match: match[1] + escape(value) + match[2], content)
        for platform, package in platforms.items():
            download = package["download"]
            values = {"file": download["file"], "size": f"{download['bytes'] / 1000000:.1f} MB",
                      "sha256": download["sha256"]}
            for key, value in values.items():
                pattern = rf'(\bdata-release-{key}="{platform}"(?:\s[^>]*)?>)[^<]*(</[a-zA-Z][^>]*>)'
                content = re.sub(pattern, lambda match: match[1] + escape(value) + match[2], content)
        content = re.sub(r'href="https://github.com/0aurorq3/arcadium-public/releases/tag/v[0-9.]+"',
                         f'href="{PUBLIC_REPOSITORY}/releases/tag/v{version}"', content)
        page.write_text(content, encoding="utf-8")


def write_update_manifest(version, platform, package, directory):
    manifest = {"product": "Arcadium Client", "launcherVersion": version,
                "platform": platform, "installerSha256": package["update"]["sha256"]}
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / Path(package["manifest"]).name
    target.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return target


def prepare(stable, advance_site=False):
    version = tomllib.loads((ROOT / "Cargo.toml").read_text(encoding="utf-8"))["workspace"]["package"]["version"]
    installers = ROOT / "build/installers"
    platforms = {"windows-x64": verify_windows(version, installers)}
    for platform in PLATFORMS[1:]:
        platforms[platform] = verify_macos(version, installers, platform)
    verify_current_components(platforms)
    checkout = ROOT / "build/arcadium-public-checkout"
    remote = subprocess.check_output(["git", "remote", "get-url", "origin"], cwd=checkout, text=True).strip().removesuffix(".git")
    if remote != PUBLIC_REPOSITORY:
        raise RuntimeError("Public checkout has an unexpected Git remote.")
    unexpected = [p.name for p in checkout.iterdir() if p.name not in {".git", "README.md"}]
    tracked = subprocess.check_output(["git", "ls-files"], cwd=checkout, text=True).splitlines()
    if unexpected or set(tracked) - {"README.md"}:
        raise RuntimeError("Public checkout must contain only README.md and its Git metadata.")
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    section = re.search(rf"^## {re.escape(version)}\s*\n(.*?)(?=^## |\Z)", changelog, re.MULTILINE | re.DOTALL)
    if section is None:
        raise RuntimeError("Add the release's changes to CHANGELOG.md before preparing notes.")
    (checkout / "README.md").write_text(
        "# Arcadium Client\n\n"
        "Minecraft 1.8.9 client and launcher.\n\n"
        "[Official website and downloads](https://arcadiumclient.com/)\n\n"
        "[Source code](https://github.com/0aurorq3/arcadiumclient)\n\n"
        "This repository hosts official installer releases.\n", encoding="utf-8")
    binaries = []
    manifests = []
    notes_dir = ROOT / "build/public-releases"
    notes_dir.mkdir(exist_ok=True)
    for platform, package in platforms.items():
        binaries.append(package["download"])
        if package["update"]["path"] != package["download"]["path"]:
            binaries.append(package["update"])
        manifests.append(write_update_manifest(version, platform, package, notes_dir / f"v{version}-updates"))
    checksums = installers / "SHA256SUMS.txt"
    checksums.write_text("".join(f"{item['sha256']}  {item['file']}\n" for item in binaries), encoding="utf-8")
    notes = notes_dir / f"v{version}-notes.md"
    notes.write_text(section.group(1).strip() + "\n", encoding="utf-8")
    inventory = {"version": version, "prerelease": not stable, "platforms": platforms,
                 "assets": [item["path"] for item in binaries] + [str(path) for path in manifests] + [str(checksums)],
                 "notes": str(notes), "public_checkout": str(checkout)}
    inventory_path = notes_dir / f"v{version}-assets.json"
    inventory_path.write_text(json.dumps(inventory, indent=2) + "\n", encoding="utf-8")
    if advance_site:
        update_site(version, stable, platforms)
    print(json.dumps({**inventory, "inventory": str(inventory_path)}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Verify Windows and both macOS packages and prepare public release assets.")
    parser.add_argument("--stable", action="store_true", help="Mark site metadata as stable instead of beta.")
    parser.add_argument("--update-site", action="store_true", help="Advance site download metadata after an approved public release.")
    args = parser.parse_args()
    prepare(args.stable, args.update_site)
