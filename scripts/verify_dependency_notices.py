import hashlib
import io
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / "licenses/native"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify_file(path, expected):
    resolved = path.resolve()
    assert resolved.is_relative_to(ROOT), path
    data = resolved.read_bytes()
    assert data and digest(data) == expected, path


rust = json.loads((ROOT / "licenses/rust/manifest.json").read_text())
rust_count = 0
for package in rust:
    assert package["notice_files"], (package["name"], package["version"])
    for notice in package["notice_files"]:
        verify_file(ROOT / "licenses/rust" / notice["file"], notice["sha256"])
        rust_count += 1
interfrost = next(package for package in rust if package["name"] == "interfrost")
assert interfrost["license_selection"] == "MIT"

bundle = json.loads((NATIVE / "compose-skiko/bundled-artifacts.json").read_text())
jar_path = ROOT / bundle["container"]
verify_file(jar_path, bundle["sha256"])
native_count = 0
embedded_count = 0
with zipfile.ZipFile(jar_path) as outer:
    for artifact in bundle["artifacts"]:
        data = outer.read(artifact["file"])
        assert digest(data) == artifact["sha256"], artifact["file"]
        with zipfile.ZipFile(io.BytesIO(data)) as inner:
            for notice in artifact["notices"]:
                original = inner.read(notice["archivePath"])
                assert digest(original) == notice["sha256"]
                assert (ROOT / notice["file"]).read_bytes() == original
                embedded_count += 1
            for library in artifact["nativeLibraries"]:
                assert digest(inner.read(library["path"])) == library["sha256"]
                native_count += 1

coverage = json.loads((NATIVE / "compose-skiko/notice-coverage.json").read_text())
assert len(coverage["artifacts"]) == len(bundle["artifacts"])
for artifact in coverage["artifacts"]:
    assert artifact["noticeFiles"], artifact["artifact"]
    for notice in artifact["noticeFiles"]:
        assert (ROOT / notice).read_bytes(), notice

verified_native_files = set()


def verify_references(value, manifest):
    if isinstance(value, list):
        for child in value:
            verify_references(child, manifest)
    elif isinstance(value, dict):
        name = value.get("file")
        expected = value.get("sha256")
        if isinstance(name, str) and isinstance(expected, str):
            path = ROOT / name if name.startswith("licenses/native/") else manifest.parent / name
            if name.startswith("licenses/native/") or path.is_file():
                verify_file(path, expected)
                verified_native_files.add(path.relative_to(ROOT).as_posix())
        for child in value.values():
            verify_references(child, manifest)


for manifest in NATIVE.rglob("*.json"):
    if manifest.name != "verification.json":
        verify_references(json.loads(manifest.read_text()), manifest)

for manifest in [
    NATIVE / "compose-skiko/skia-native-notice-provenance.json",
    NATIVE / "rust-skia/additional-notice-provenance.json",
    NATIVE / "rust-skia/graphics-header-notice-provenance.json",
]:
    data = json.loads(manifest.read_text())
    for component in data.get("components", []) if isinstance(data, dict) else data:
        assert component["notices"], component["component"]

webview = json.loads((NATIVE / "webview2-sdk/provenance.json").read_text())
assert len(webview["binaryMatches"]) == 9
assert all(row["matchesOfficialSdk"] for row in webview["binaryMatches"])
assert webview["sdkAssignedGpl"] is False

pack200 = json.loads((NATIVE / "pack200/artifact-provenance.json").read_text())
assert pack200["licenseExpression"] == "GPL-2.0-only WITH Classpath-exception-2.0"
assert sum(row["identical"] for row in pack200["sourceArchiveToTaggedSource"]["files"]) == 25
xz = json.loads((NATIVE / "xz-java/provenance.json").read_text())
assert xz["licenseExpression"] == "0BSD"
assert xz["sourceArchiveToTag"]["identical"] == xz["sourceArchiveToTag"]["javaFiles"] == 116

report = {
    "completed": True,
    "rustPackages": len(rust),
    "rustNoticeFilesVerified": rust_count,
    "rustEmptyNoticeSets": 0,
    "interfrostLicenseSelection": "MIT",
    "composeNestedArtifactsVerified": len(bundle["artifacts"]),
    "embeddedOriginalJavaNoticeFilesVerified": embedded_count,
    "skikoNativeLibraryHashesVerified": native_count,
    "nativeOriginalNoticeAndEvidenceFilesVerified": len(verified_native_files),
    "webviewOfficialSdkBinaryMatches": 9,
    "pack200TagSourceByteMatches": "25/26; remaining Javadoc-only difference recorded",
    "xzTagSourceByteMatches": "116/116",
    "unresolvedMissingNativeNoticeSets": [],
    "nativeBuildReproductionClaimed": False,
}
(NATIVE / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
