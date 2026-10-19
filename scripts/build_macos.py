import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import plistlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import tomllib

ROOT = Path(__file__).resolve().parents[1]
APP = "Arcadium Client.app"
MACOS_MINIMUMS = {"macos-arm64": "13.0", "macos-x64": "12.0"}


def run(*args, **kwargs):
    subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def digest(path):
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def create_dmg(image_root, destination):
    command = ["/usr/bin/hdiutil", "create", "-volname", "Arcadium Client", "-srcfolder", str(image_root),
               "-format", "UDZO", "-ov", str(destination)]
    for attempt in range(3):
        result = subprocess.run(command, capture_output=True, text=True)
        if result.stdout:
            print(result.stdout, end="")
        if result.returncode == 0:
            return
        if "resource busy" not in result.stderr.lower() or attempt == 2:
            raise subprocess.CalledProcessError(result.returncode, command, result.stdout, result.stderr)
        time.sleep(2 * (attempt + 1))


def icon(path):
    source = ROOT / "packages/arcadium_app/icons/1024x1024.png"
    with tempfile.TemporaryDirectory(prefix="arcadium-icons-") as temporary:
        iconset = Path(temporary) / "Arcadium.iconset"
        iconset.mkdir()
        for size in [16, 32, 128, 256, 512]:
            for multiplier, suffix in [(1, ""), (2, "@2x")]:
                pixels = size * multiplier
                run("/usr/bin/sips", "--resampleHeightWidth", pixels, pixels, source,
                    "--out", iconset / f"icon_{size}x{size}{suffix}.png", stdout=subprocess.DEVNULL)
        run("/usr/bin/iconutil", "--convert", "icns", "--output", path, iconset)
        decoded = Path(temporary) / "Verified.iconset"
        run("/usr/bin/iconutil", "--convert", "iconset", "--output", decoded, path)
        for file in decoded.glob("*.png"):
            name = re.fullmatch(r"icon_(\d+)x\d+(@2x)?\.png", file.name)
            if not name:
                continue
            expected = int(name[1]) * (2 if name[2] else 1)
            png = file.read_bytes()
            if png[:8] != b"\x89PNG\r\n\x1a\n" or int.from_bytes(png[16:20], "big") != expected or int.from_bytes(png[20:24], "big") != expected:
                raise RuntimeError(f"Incorrect icon dimensions: {file.name}")
        if not (decoded / "icon_512x512@2x.png").exists():
            raise RuntimeError("The application icon is missing its Retina representation.")


def dependency_notices(target, destination):
    inventory = json.loads(subprocess.check_output(
        ["cargo", "metadata", "--locked", "--format-version", "1", "--filter-platform", target], cwd=ROOT))
    notices = ["Arcadium Client third-party dependency notices", "External dependencies retain their own licenses."]
    for package in sorted(inventory["packages"], key=lambda item: (item["name"], item["version"])):
        if package["name"].startswith("arcadium_"):
            continue
        notices.extend(["", f"=== {package['name']} {package['version']} ===",
                        f"License: {package.get('license')}", f"Repository: {package.get('repository')}",
                        "Authors: " + ", ".join(package.get("authors", []))])
        directory = Path(package["manifest_path"]).parent
        files = {file for file in directory.iterdir()
                 if file.is_file() and re.match(r"^(LICENSE|COPYING|NOTICE|COPYRIGHT)([._-].*)?$", file.name)}
        if package.get("license_file"):
            files.add(directory / package["license_file"])
        for file in sorted(files):
            if file.is_file():
                notices.append(file.read_text(encoding="utf-8", errors="replace"))
    destination.write_text("\n".join(notices) + "\n", encoding="utf-8")


def build(args):
    if platform.system() != "Darwin":
        raise RuntimeError("Build macOS installers on a Mac or a macOS GitHub runner.")
    version = tomllib.loads((ROOT / "Cargo.toml").read_text())["workspace"]["package"]["version"]
    target = args.target or ("aarch64-apple-darwin" if platform.machine() == "arm64" else "x86_64-apple-darwin")
    arch = "arm64" if target == "aarch64-apple-darwin" else "x86_64"
    platform_id = "macos-arm64" if arch == "arm64" else "macos-x64"
    minimum = MACOS_MINIMUMS[platform_id]
    minimum_tuple = tuple(map(int, minimum.split("."))) + (0,)
    os.environ["SQLX_OFFLINE"] = "true"
    os.environ["MACOSX_DEPLOYMENT_TARGET"] = minimum
    target_dir = Path(os.environ.get("CARGO_TARGET_DIR", ROOT / "target")).resolve()
    if not args.skip_compile:
        run(sys.executable, ROOT / "scripts/build_client.py", cwd=ROOT)
        run("cargo", "build", "--locked", "--profile", args.profile, "--target", target,
            "-p", "arcadium_app", "--bin", "Arcadium", cwd=ROOT)
    binary = target_dir / target / args.profile / "Arcadium"
    run("/usr/bin/lipo", binary, "-verify_arch", arch)
    commands = subprocess.check_output(["/usr/bin/otool", "-l", str(binary)], text=True)
    minimums = re.findall(r"\bminos\s+(\d+(?:\.\d+)*)", commands)
    if not minimums or any(tuple(map(int, value.split("."))) > minimum_tuple for value in minimums):
        raise RuntimeError(f"Executable has an incompatible macOS deployment target: {minimums}")
    out = ROOT / "build/installers" / platform_id
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="macos-stage-", dir=ROOT / "build") as temporary:
        stage = Path(temporary)
        app = stage / APP
        contents = app / "Contents"
        resources = contents / "Resources"
        (contents / "MacOS").mkdir(parents=True)
        resources.mkdir()
        shutil.copy2(binary, contents / "MacOS/Arcadium")
        (contents / "MacOS/Arcadium").chmod(0o755)
        icon(resources / "icon.icns")
        shutil.copy2(ROOT / "packages/arcadium_app/icons/512x512.png", resources / "game-icon.png")
        components = resources / "components"
        components.mkdir()
        lock = json.loads((ROOT / "distribution/oneclient-1.8.9.lock.json").read_text())
        for component in lock["arcadiumComponents"]:
            source = ROOT / "packages/arcadium_common/assets/distribution" / component["file"]
            if digest(source) != component["sha256"]:
                raise RuntimeError(f"Client component hash mismatch: {source.name}")
            shutil.copy2(source, components / source.name)
        for name in ["LICENSE", "NOTICE.md", "README.md"]:
            shutil.copy2(ROOT / name, resources / name)
        shutil.copytree(ROOT / "licenses", resources / "licenses")
        for font in ["JetBrainsMono", "Poppins"]:
            shutil.copy2(ROOT / "packages/arcadium_app/assets/fonts" / font / "OFL.txt",
                         resources / "licenses" / f"{font}-OFL.txt")
        dependency_notices(target, resources / "licenses/Rust-dependencies.txt")
        shutil.copy2(ROOT / "distribution/oneclient-1.8.9.lock.json", resources / "distribution-lock.json")
        metadata = {
            "CFBundleInfoDictionaryVersion": "6.0",
            "CFBundleName": "Arcadium Client", "CFBundleDisplayName": "Arcadium Client",
            "CFBundleExecutable": "Arcadium", "CFBundleIdentifier": "io.github.arcadiumclient.launcher",
            "CFBundlePackageType": "APPL", "CFBundleShortVersionString": version,
            "CFBundleVersion": version, "CFBundleIconFile": "icon.icns",
            "LSMinimumSystemVersion": minimum, "NSHighResolutionCapable": True,
            "NSMicrophoneUsageDescription": "A Minecraft mod is requesting access to your microphone.",
            "CFBundleURLTypes": [{"CFBundleURLName": "io.github.arcadiumclient.launcher",
                                  "CFBundleURLSchemes": ["arcadium"]}],
        }
        (contents / "Info.plist").write_bytes(plistlib.dumps(metadata))
        run("/usr/bin/codesign", "--force", "--sign", "-", "--identifier", metadata["CFBundleIdentifier"],
            "--options", "runtime", "--entitlements", ROOT / "packages/arcadium_app/distribution/App.entitlements", app)
        run("/usr/bin/codesign", "--verify", "--deep", "--strict", app)
        run("cargo", "build", "--locked", "--profile", args.profile, "--target", target,
            "-p", "arcadium_app", "--example", "macos_window_controls", cwd=ROOT)
        run(target_dir / target / args.profile / "examples/macos_window_controls", timeout=120)
        run("python3", ROOT / "scripts/smoke_macos_launcher.py", "--app", app)
        archive = out / f"ArcadiumClient-{version}-{platform_id}.app.tar.gz"
        with tarfile.open(archive, "w:gz", format=tarfile.PAX_FORMAT) as tar:
            tar.add(app, arcname=APP)
        image_root = stage / "image"
        image_root.mkdir()
        shutil.move(app, image_root / APP)
        (image_root / "Applications").symlink_to("/Applications", target_is_directory=True)
        dmg = out / f"ArcadiumClient-{version}-{platform_id}.dmg"
        create_dmg(image_root, dmg)
        run("/usr/bin/hdiutil", "verify", dmg)
        app = image_root / APP
        files = [{"path": file.relative_to(app).as_posix(), "sha256": digest(file)}
                 for file in sorted(app.rglob("*")) if file.is_file()]
        manifest = {"product": "Arcadium Client", "minecraftVersion": "1.8.9",
                    "launcherVersion": version, "platform": platform_id,
                    "minimumSystemVersion": minimum,
                    "installerSha256": digest(archive), "archive": archive.name,
                    "dmg": dmg.name, "dmgSha256": digest(dmg), "files": files,
                    "signing": "ad-hoc-testing"}
        (out / f"package-manifest-{platform_id}.json").write_text(json.dumps(manifest, indent=2) + "\n")
        (out / "SHA256SUMS.txt").write_text(f"{digest(dmg)}  {dmg.name}\n{digest(archive)}  {archive.name}\n")
        testing = ROOT / "docs/macos-testing.md"
        if testing.is_file():
            shutil.copy2(testing, out / "TESTING.md")
        print(json.dumps({"version": version, "target": target, "output": str(out), "dmg": dmg.name,
                          "sha256": digest(dmg), "notarized": False}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=["aarch64-apple-darwin", "x86_64-apple-darwin"])
    parser.add_argument("--profile", default="macos-testing")
    parser.add_argument("--skip-compile", action="store_true")
    build(parser.parse_args())
