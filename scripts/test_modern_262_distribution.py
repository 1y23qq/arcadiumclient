import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import check_source_distribution as source_gate
import modern_262_distribution as target
import package_modern
from test_package_client import jar


class Modern262DistributionTests(unittest.TestCase):
    def test_inherits_every_approved_choice_without_reviving_excluded_features(self):
        original = json.loads((target.ROOT / "distribution/port-scope-1.21.11.json").read_text(encoding="utf-8"))
        inherited = target.inherited_scope(target.ROOT)
        self.assertEqual(inherited["features"], original["features"])
        self.assertEqual(sum(entry["port"] for entry in inherited["features"]), 197)
        self.assertEqual(sum(not entry["port"] for entry in inherited["features"]), 16)
        self.assertEqual(inherited["minecraftVersion"], "26.2")
        self.assertEqual(original["minecraftVersion"], "1.21.11")

    def test_invalid_or_duplicate_choices_cannot_be_inherited(self):
        original = json.loads((target.ROOT / "distribution/port-scope-1.21.11.json").read_text(encoding="utf-8"))
        for change in ("duplicate", "string"):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / "distribution").mkdir()
                scope = copy.deepcopy(original)
                if change == "duplicate":
                    scope["features"].append(scope["features"][0])
                else:
                    scope["features"][0]["port"] = "false"
                (root / "distribution/port-scope-1.21.11.json").write_text(json.dumps(scope), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "Invalid approved"):
                    target.inherited_scope(root)

    def test_pinned_snapshot_keeps_upstream_defaults_and_nvidium_optional(self):
        lock = json.loads((target.ROOT / "distribution/oneclient-26.2.lock.json").read_text(encoding="utf-8"))
        self.assertEqual(lock["loaderVersion"], "0.20.1")
        bundles = {entry["category"]: entry for entry in lock["bundles"]}
        self.assertEqual({name: entry["enabled"] for name, entry in bundles.items()},
                         {"HUD": True, "Performance": True, "QoL": True, "SkyBlock": False, "Utility": True})
        self.assertEqual(lock["optionalNvidium"], target.NVIDIUM)
        self.assertFalse(target.NVIDIUM["enabledByDefault"])
        self.assertTrue(target.NVIDIUM["requiresOpenGL"])
        defaults = [entry for bundle in bundles.values() if bundle["enabled"] for entry in bundle["mods"] if entry["enabled"]]
        self.assertFalse(any("nvidium" in entry["path"].lower() for entry in defaults))
        self.assertTrue(any("sodium-fabric-0.9.2+mc26.2" in entry["path"] for entry in defaults))
        self.assertTrue(any(not entry["enabled"] and "controlify" in entry["path"].lower() for entry in bundles["Utility"]["mods"]))

    def test_source_bundles_remain_metadata_only_with_reviewed_compatibility_overrides(self):
        for filename in target.PINNED_BUNDLES:
            relative = target.ASSET_PATH / filename
            raw = (target.ROOT / relative).read_bytes()
            self.assertEqual(source_gate.check_file(relative.as_posix(), raw), [], filename)
            malicious = io.BytesIO(raw)
            with zipfile.ZipFile(malicious, "a") as archive:
                archive.writestr("overrides/mods/unreviewed.jar", b"fixture")
            self.assertTrue(source_gate.check_file(relative.as_posix(), malicious.getvalue()), filename)

    def test_staging_262_preserves_12111_and_rejects_wrong_runtime_before_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / target.ASSET_PATH
            assets.mkdir(parents=True)
            (root / "distribution").mkdir()
            scope = b'{"minecraftVersion":"26.2","features":[]}'
            (root / "distribution/port-scope-26.2.json").write_bytes(scope)
            (root / "distribution/oneclient-26.2.lock.json").write_text(json.dumps({
                "portScope": {"path": "distribution/port-scope-26.2.json", "sha256": hashlib.sha256(scope).hexdigest()},
            }), encoding="utf-8")
            catalog = assets / "client-components.json"
            catalog.write_text('{"1.21.11":[{"file":"retained.jar"}]}', encoding="utf-8")
            sources = {}
            for name, identity in (("Core", "oneconfig"), ("Features", "arcadium_runtime"), ("Renderer", "compose-multiplatform")):
                source = root / f"{name}.jar"
                source.write_bytes(jar(identity, minecraft="26.2"))
                sources[f"Arcadium{name}-26.2.jar"] = source, identity
            with patch.object(package_modern, "component_sources", return_value=sources):
                package_modern.stage(root, "26.2")
                self.assertEqual(json.loads(catalog.read_text())["1.21.11"], [{"file": "retained.jar"}])
                before = catalog.read_bytes()
                sources["ArcadiumFeatures-26.2.jar"][0].write_bytes(jar("arcadium_runtime", minecraft="1.21.11"))
                with self.assertRaisesRegex(ValueError, "different Minecraft"):
                    package_modern.stage(root, "26.2")
                self.assertEqual(catalog.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
