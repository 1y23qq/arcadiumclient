from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIRECTORIES = {".cargo", ".github", ".sqlx", "ci", "client-runtime", "client-legacy-performance", "client-legacy-mods", "client-ui", "distribution", "docs", "forge-compat", "game-branding", "licenses", "packages", "scripts", "services", "sites"}
SOURCE_ROOT_FILES = {".editorconfig", ".gitattributes", ".gitignore", ".mailmap", "ATTRIBUTION.md", "CHANGELOG.md", "CODE_OF_CONDUCT.md", "CONTRIBUTING.md", "Cargo.lock", "Cargo.toml", "LICENSE", "NOTICE.md", "README.md", "clippy.toml", "rust-toolchain.toml", "rustfmt.toml"}
WRAPPERS = {f"{project}/gradle/wrapper/gradle-wrapper.jar" for project in ["client-runtime", "client-legacy-performance", "client-ui", "game-branding"]}
WRAPPERS.add("client-legacy-mods/guiblur/gradle/wrapper/gradle-wrapper.jar")
BUNDLES = {f"packages/arcadium_common/assets/distribution/{name}-1.8.9-ornithe.mrpack" for name in ["hud", "performance", "qol", "utility"]}
BUNDLES.add("packages/arcadium_common/assets/distribution/core-1.7.10-ornithe.mrpack")
GENERATED_PARTS = {".git", ".gradle", ".kotlin", "__pycache__", "node_modules", "build", "target", "run", "logs", "example-cookie", "forge-mod-test", "arcadium_polyplus", "client-features", "client-renderer"}
NATIVE_LOG_SOURCES = {f"packages/arcadium_cluster/src/logs/{name}.rs" for name in ["error", "manage", "mclogs", "mod", "parse"]}
BINARY_ASSETS = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".icns", ".tiff", ".ttf", ".otf", ".woff", ".woff2", ".ogg"}
BINARY_OUTPUTS = {".jar", ".class", ".exe", ".dll", ".so", ".dylib", ".pdb", ".zip", ".gz", ".tar", ".xz", ".lzma", ".7z", ".rar", ".aar", ".dmg", ".enc", ".db", ".sqlite", ".sqlite3", ".pfx", ".p12", ".jks", ".keystore", ".onnx", ".pt"}
CREDENTIAL_FILES = {"accounts.json", "cookies.txt", "cookies.json", "credentials.json", "usage-identity.json", ".env"}
SECRET_PATTERNS = [re.compile(rb"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"), re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{36,}\b"), re.compile(rb"\bgithub_pat_[A-Za-z0-9_]{70,}\b"), re.compile(rb"\bAKIA[0-9A-Z]{16}\b"), re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\r\n]+[A-Za-z0-9+/]{32,}")]
SYNTHETIC_HOMES = {"someone", "me", "o", "user", "username", "example", "test", "testuser", "fixture", "<username>"}
RESTORED_VIDEO = {"client-runtime/src/main/kotlin/io/github/arcadiumclient/runtime/client/gui/VideoSettingsModel.kt", "client-runtime/src/main/kotlin/io/github/arcadiumclient/runtime/client/gui/ArcadiumVideoSettingsScreen.kt"}


def check_file(name: str, raw: bytes) -> list[str]:
    path = PurePosixPath(name)
    failures = []
    if path.is_absolute() or "\\" in name or any(part in {"", ".", ".."} for part in path.parts):
        return ["Invalid publication path"]
    generated_parts = GENERATED_PARTS - ({"logs"} if name in NATIVE_LOG_SOURCES else set())
    if any(part in generated_parts for part in path.parts):
        failures.append("Generated, private or excluded source directory")
    if (len(path.parts) == 1 and name not in SOURCE_ROOT_FILES) or (len(path.parts) > 1 and path.parts[0] not in SOURCE_DIRECTORIES):
        failures.append("Not in the source publication allowlist")
    if path.name in CREDENTIAL_FILES or path.name.startswith(".env.") and path.name not in {".env.example", ".env.template"}:
        failures.append("Local account, cookie or credential file")
    if name in BUNDLES:
        try:
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                metadata_override = "overrides/config/fabric_loader_dependencies.json"
                allowed = {"modrinth.index.json", "overrides/", "overrides/config/", metadata_override}
                if set(archive.namelist()) - allowed or "modrinth.index.json" not in archive.namelist():
                    failures.append("Published MRPACK contains a payload or unreviewed override")
                for entry in archive.infolist():
                    if stat.S_ISLNK(entry.external_attr >> 16):
                        failures.append("MRPACK metadata is a link")
                index = json.loads(archive.read("modrinth.index.json"))
                expected_version = "1.7.10" if name.endswith("-1.7.10-ornithe.mrpack") else "1.8.9"
                if index.get("dependencies", {}).get("minecraft") != expected_version:
                    failures.append("Unexpected Minecraft version in source bundle index")
                if metadata_override in archive.namelist():
                    override = json.loads(archive.read(metadata_override))
                    if override != {"version": 1, "overrides": {"pylon": {"+breaks": {"lenis": "*"}}}}:
                        failures.append("Fabric dependency metadata override needs explicit review")
        except (ValueError, KeyError, zipfile.BadZipFile):
            failures.append("Invalid MRPACK metadata")
        return failures
    if name in WRAPPERS:
        if not zipfile.is_zipfile(io.BytesIO(raw)):
            failures.append("Invalid Gradle wrapper archive")
        return failures
    if path.suffix.lower() in BINARY_OUTPUTS:
        return failures + ["Compiled/archive/credential payload is not source"]
    if path.suffix.lower() in BINARY_ASSETS:
        if not ("assets" in path.parts or "resources" in path.parts or "icons" in path.parts or path.parts[0] == "sites" or name.startswith(("client-ui/sounds/", "packages/arcadium_app/distribution/"))):
            failures.append("Binary asset outside an asset source directory")
        return failures
    if b"\0" in raw:
        return failures + ["Unrecognized binary payload"]
    for pattern in SECRET_PATTERNS:
        if pattern.search(raw):
            failures.append("Credential/private key literal")
    text = raw.decode("utf-8-sig", errors="replace")
    homes = re.findall(r"[A-Za-z]:[\\/]+Users[\\/]+([^\\/\s\"'<>]+)|/" + r"Users/([^/\s\"'<>]+)", text)
    if any((first or second).lower() not in SYNTHETIC_HOMES for first, second in homes):
        failures.append("Personal machine home path")
    if name in RESTORED_VIDEO and (re.search(r"^\s*//\?\s*if", text, re.M) or "OnboardingFeatures" in text or "SocialText" in text):
        failures.append("Restored standalone video source is not fully migrated")
    return failures


def publication_files(root: Path) -> list[str]:
    actual_root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], cwd=root, text=True, encoding="utf-8").strip()).resolve()
    if actual_root != root.resolve():
        raise RuntimeError("Run the publication gate against the isolated source repository, not its parent checkout.")
    raw = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root)
    return sorted({name.decode("utf-8") for name in raw.split(b"\0") if name})


def inspect(root: Path = ROOT) -> dict:
    files, failures = [], []
    candidates = publication_files(root)
    for name in sorted(NATIVE_LOG_SOURCES - set(candidates)):
        failures.append({"path": name, "errors": ["Required native log module is missing from the source distribution"]})
    for name in candidates:
        path = root / name
        if not path.exists():
            failures.append({"path": name, "errors": ["Tracked file is missing"]})
            continue
        if path.is_symlink():
            failures.append({"path": name, "errors": ["Source publication links require explicit review"]})
            continue
        raw = path.read_bytes()
        errors = check_file(name, raw)
        if errors:
            failures.append({"path": name, "errors": errors})
        files.append({"path": name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
    return {"passed": not failures, "sourceFileCount": len(files), "files": files, "failures": failures,
            "binaryExceptions": {"gradleWrappers": sorted(WRAPPERS), "metadataOnlyBundles": sorted(BUNDLES)}}


def write_archive(root: Path, destination: Path, report: dict) -> None:
    if not report["passed"]:
        raise RuntimeError("Refusing to archive source that failed publication checks.")
    if not destination.resolve().is_relative_to((root / "build").resolve()) or destination.exists():
        raise RuntimeError("Write a new source archive inside this checkout's build directory.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(destination, "w:gz", format=tarfile.PAX_FORMAT) as archive:
        for entry in report["files"]:
            source = root / entry["path"]
            raw = source.read_bytes()
            if hashlib.sha256(raw).hexdigest() != entry["sha256"]:
                raise RuntimeError("Source changed after publication inspection: " + entry["path"])
            info = tarfile.TarInfo(entry["path"])
            info.mode = 0o755 if source.name == "gradlew" or source.suffix == ".sh" else 0o644
            info.size = len(raw)
            archive.addfile(info, io.BytesIO(raw))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Inspect a source-only publication file list; this command never publishes or deletes source.")
    parser.add_argument("--report", type=Path, default=ROOT / "build/source-publication-gate.json")
    parser.add_argument("--archive", type=Path)
    args = parser.parse_args()
    report = inspect()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if args.archive:
        write_archive(ROOT, args.archive.resolve(), report)
    print(json.dumps({key: report[key] for key in ["passed", "sourceFileCount", "failures"]}, indent=2))
    raise SystemExit(0 if report["passed"] else 1)
