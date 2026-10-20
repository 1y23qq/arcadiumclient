import hashlib
import io
import json
from pathlib import Path
import plistlib
import struct
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import prepare_public_release as release


class PublicReleaseTests(unittest.TestCase):
    def test_public_update_manifest_stays_below_client_limit_with_full_audit_retained(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            for platform in release.PLATFORMS:
                name = "package-manifest.json" if platform == "windows-x64" else f"package-manifest-{platform}.json"
                audit = root / name
                original = json.dumps({"files": [{"path": f"resource-{i}", "sha256": "a" * 64} for i in range(2000)]})
                audit.write_text(original, encoding="utf-8")
                self.assertGreater(audit.stat().st_size, 65536)
                package = {"manifest": str(audit), "update": {"sha256": "b" * 64},
                           "download": {"sha256": "c" * 64}}
                published = release.write_update_manifest("0.3.7", platform, package, root / "updates")
                self.assertEqual(published.name, name)
                self.assertLess(published.stat().st_size, 65536)
                self.assertEqual(json.loads(published.read_text(encoding="utf-8")), {
                    "product": "Arcadium Client", "launcherVersion": "0.3.7",
                    "platform": platform, "installerSha256": "b" * 64})
                self.assertEqual(audit.read_text(encoding="utf-8"), original)

    def fixture(self, root, platform="macos-arm64", extra=None, binary_cpu=None, binary_minimum=None, plist_minimum=None):
        directory = root / platform
        directory.mkdir()
        archive = directory / f"ArcadiumClient-0.2.8-{platform}.app.tar.gz"
        cpu = binary_cpu or (0x0100000C if platform == "macos-arm64" else 0x01000007)
        minimum = release.MACOS_MINIMUMS[platform]
        minimum_parts = list(map(int, (binary_minimum or minimum).split(".")))
        minimum_parts += [0] * (3 - len(minimum_parts))
        encoded_minimum = (minimum_parts[0] << 16) | (minimum_parts[1] << 8) | minimum_parts[2]
        header = struct.pack("<IIIIIIII", 0xfeedfacf, cpu, 0 if cpu == 0x0100000C else 3, 2, 1, 24, 0, 0)
        commands = struct.pack("<IIIIII", 0x32, 24, 1, encoded_minimum, 0x000f0000, 0)
        files = {
            "Contents/Info.plist": plistlib.dumps({"CFBundleShortVersionString": "0.2.8", "LSMinimumSystemVersion": plist_minimum or minimum}),
            "Contents/MacOS/Arcadium": header + commands,
        }
        with tarfile.open(archive, "w:gz") as package:
            for name, content in files.items():
                info = tarfile.TarInfo(f"{release.APP_NAME}/{name}")
                info.size = len(content)
                info.mode = 0o755
                package.addfile(info, io.BytesIO(content))
            if extra:
                package.addfile(extra, io.BytesIO(b""))
        dmg = directory / f"ArcadiumClient-0.2.8-{platform}.dmg"
        dmg.write_bytes(b"fixture" + b"koly" + bytes(508))
        manifest = {
            "product": "Arcadium Client", "launcherVersion": "0.2.8", "platform": platform,
            "archive": archive.name, "installerSha256": release.sha256(archive),
            "minimumSystemVersion": minimum,
            "dmg": dmg.name, "dmgSha256": release.sha256(dmg),
            "files": [{"path": name, "sha256": hashlib.sha256(content).hexdigest()}
                      for name, content in files.items()],
        }
        path = directory / f"package-manifest-{platform}.json"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        return path, archive, dmg

    def test_verifies_both_processor_archives_and_download_hashes(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            for platform in ["macos-arm64", "macos-x64"]:
                self.fixture(Path(temporary), platform)
                result = release.verify_macos("0.2.8", Path(temporary), platform)
                self.assertEqual(result["verified_payload_files"], 2)
                self.assertTrue(result["download"]["file"].endswith(".dmg"))
                self.assertTrue(result["update"]["file"].endswith(".app.tar.gz"))

    def test_rejects_wrong_cpu_and_stale_versions(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            self.fixture(root, binary_cpu=0x01000007)
            with self.assertRaisesRegex(RuntimeError, "architecture mismatch"):
                release.verify_macos("0.2.8", root, "macos-arm64")
            with self.assertRaisesRegex(RuntimeError, "does not match this release"):
                release.verify_macos("0.2.9", root, "macos-arm64")

    def test_intel_package_rejects_a_newer_native_os_requirement(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            self.fixture(root, "macos-x64", binary_minimum="13.0")
            with self.assertRaisesRegex(RuntimeError, "exceeds package support"):
                release.verify_macos("0.2.8", root, "macos-x64")

    def test_minimum_os_must_match_plist_and_manifest(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            self.fixture(root, "macos-x64", plist_minimum="13.0")
            with self.assertRaisesRegex(RuntimeError, "package minimum OS mismatch"):
                release.verify_macos("0.2.8", root, "macos-x64")
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            manifest, _, _ = self.fixture(root, "macos-x64")
            values = json.loads(manifest.read_text())
            values["minimumSystemVersion"] = "13.0"
            manifest.write_text(json.dumps(values))
            with self.assertRaisesRegex(RuntimeError, "package minimum OS mismatch"):
                release.verify_macos("0.2.8", root, "macos-x64")

    def test_unchanged_arm_package_can_use_its_existing_manifest(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            manifest, _, _ = self.fixture(root, "macos-arm64")
            values = json.loads(manifest.read_text())
            values.pop("minimumSystemVersion")
            manifest.write_text(json.dumps(values))
            self.assertEqual(release.verify_macos("0.2.8", root, "macos-arm64")["minimumSystemVersion"], "13.0")

    def test_rejects_corrupt_binary_before_site_changes(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            _, _, dmg = self.fixture(root)
            dmg.write_bytes(dmg.read_bytes() + b"corrupt")
            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                release.verify_macos("0.2.8", root, "macos-arm64")

    def test_rejects_links_path_traversal_and_unlisted_source(self):
        entries = []
        link = tarfile.TarInfo(f"{release.APP_NAME}/escape")
        link.type = tarfile.SYMTYPE
        link.linkname = "/tmp/outside"
        entries.append(link)
        entries.append(tarfile.TarInfo(f"{release.APP_NAME}/../outside"))
        entries.append(tarfile.TarInfo(f"{release.APP_NAME}/source.rs"))
        for entry in entries:
            with self.subTest(path=entry.name), tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
                root = Path(temporary)
                self.fixture(root, extra=entry)
                with self.assertRaises(RuntimeError):
                    release.verify_macos("0.2.8", root, "macos-arm64")

    def test_windows_manifest_paths_are_normalized_without_duplicates(self):
        digest = "a" * 64
        manifest = {"platform": "windows-x64", "files": [
            {"path": "components\\ArcadiumCore-1.8.9.jar", "sha256": digest}]}
        self.assertEqual(release.payload_files(manifest), {"components/ArcadiumCore-1.8.9.jar": digest})
        manifest["files"].append({"path": "components/ArcadiumCore-1.8.9.jar", "sha256": digest})
        with self.assertRaisesRegex(RuntimeError, "Invalid manifest payload"):
            release.payload_files(manifest)

    def test_catalog_checks_accept_checkout_line_endings_and_reject_stale_data(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-release-") as temporary:
            root = Path(temporary)
            (root / "distribution").mkdir()
            catalog = json.dumps({"arcadiumComponents": [
                {"file": "ArcadiumCore-1.8.9.jar", "sha256": "a" * 64}
            ]}, indent=2).encode() + b"\n"
            (root / "distribution/oneclient-1.8.9.lock.json").write_bytes(catalog.replace(b"\n", b"\r\n"))
            manifest_path = root / "manifest.json"
            prefix = "Contents/Resources/"
            files = [{"path": prefix + "components/ArcadiumCore-1.8.9.jar", "sha256": "a" * 64}]
            platforms = {"macos-arm64": {"manifest": str(manifest_path)}}
            with patch.object(release, "ROOT", root):
                for content in [catalog, catalog.replace(b"\n", b"\r\n")]:
                    manifest_path.write_text(json.dumps({"files": files + [
                        {"path": prefix + "distribution-lock.json", "sha256": hashlib.sha256(content).hexdigest()}
                    ]}), encoding="utf-8")
                    release.verify_current_components(platforms)
                manifest_path.write_text(json.dumps({"files": files + [
                    {"path": prefix + "distribution-lock.json", "sha256": "b" * 64}
                ]}), encoding="utf-8")
                with self.assertRaisesRegex(RuntimeError, "outdated client catalog"):
                    release.verify_current_components(platforms)


if __name__ == "__main__":
    unittest.main()
