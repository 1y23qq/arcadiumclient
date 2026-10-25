import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import import_build_components as components


class BuildComponentTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as archive:
            archive.writestr("fabric.mod.json", '{"id":"arcadium_runtime"}')
        self.raw = raw.getvalue()
        self.asset = self.root / components.ASSET_PATH / "ArcadiumFeatures-1.8.9.jar"
        self.asset.parent.mkdir(parents=True)
        self.asset.write_bytes(self.raw)
        entry = {"file": self.asset.name, "sha256": hashlib.sha256(self.raw).hexdigest(), "bytes": len(self.raw)}
        (self.root / "distribution").mkdir()
        (self.root / "distribution/oneclient-1.8.9.lock.json").write_text(json.dumps({"arcadiumComponents": [], "arcadiumBranding": entry}))
        self.bundle = self.root / "inputs.zip"

    def test_verified_round_trip(self):
        digest = components.write_bundle(self.bundle, self.root)
        self.asset.unlink()
        components.import_bundle(self.bundle, digest, self.root)
        self.assertEqual(self.asset.read_bytes(), self.raw)

    def test_native_forge_catalog_does_not_need_a_separate_branding_jar(self):
        entry = {"file": self.asset.name, "sha256": hashlib.sha256(self.raw).hexdigest(), "bytes": len(self.raw)}
        (self.root / "distribution/arcadium-1.7.10.lock.json").write_text(json.dumps({"loader": "Forge", "arcadiumComponents": [entry]}))
        digest = components.write_bundle(self.bundle, self.root)
        components.import_bundle(self.bundle, digest, self.root)
        self.assertEqual(len(components.entries(self.root)), 1)

    def test_wrong_archive_checksum_cannot_write(self):
        components.write_bundle(self.bundle, self.root)
        with self.assertRaises(RuntimeError):
            components.import_bundle(self.bundle, "0" * 64, self.root)

    def test_new_archive_hash_cannot_bypass_the_pinned_catalog(self):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr(self.asset.name, b"changed")
        with self.assertRaises(RuntimeError):
            components.import_bundle(self.bundle, hashlib.sha256(self.bundle.read_bytes()).hexdigest(), self.root)
        self.assertEqual(self.asset.read_bytes(), self.raw)

    def test_unexpected_archive_paths_cannot_write(self):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("../outside.jar", self.raw)
        with self.assertRaises(RuntimeError):
            components.import_bundle(self.bundle, hashlib.sha256(self.bundle.read_bytes()).hexdigest(), self.root)
        self.assertFalse((self.root.parent / "outside.jar").exists())

    def test_uncompressed_size_must_match_pinned_catalog(self):
        digest = components.write_bundle(self.bundle, self.root)
        path = self.root / "distribution/oneclient-1.8.9.lock.json"
        lock = json.loads(path.read_text())
        lock["arcadiumBranding"]["bytes"] += 1
        path.write_text(json.dumps(lock))
        with self.assertRaisesRegex(RuntimeError, "sizes do not match"):
            components.import_bundle(self.bundle, digest, self.root)


if __name__ == "__main__":
    unittest.main()
