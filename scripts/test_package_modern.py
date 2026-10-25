import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import package_modern
from test_package_client import jar


class ModernPackagingTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.assets = self.root / package_modern.ASSET_PATH
        self.assets.mkdir(parents=True)
        self.distribution = self.root / "distribution"
        self.distribution.mkdir()
        self.scope = self.distribution / "port-scope-1.21.11.json"
        self.scope.write_text('{"features":[]}', encoding="utf-8")
        self.lock_path = self.root / package_modern.LOCK_PATH
        self.lock_path.write_text(json.dumps({"minecraftVersion": "1.21.11", "portScope": {
            "path": "distribution/port-scope-1.21.11.json", "sha256": hashlib.sha256(self.scope.read_bytes()).hexdigest()
        }, "bundles": []}), encoding="utf-8")
        self.sources = {}
        for name, mod_id in [("Core", "oneconfig"), ("Features", "arcadium_runtime"), ("Renderer", "compose-multiplatform")]:
            source = self.root / f"{name}.jar"
            source.write_bytes(jar(mod_id, minecraft="1.21.11"))
            self.sources[f"Arcadium{name}-1.21.11.jar"] = source, mod_id
        self.catalog = self.assets / "client-components.json"
        self.catalog.write_text('{"1.8.9":[{"file":"old.jar"}]}', encoding="utf-8")

    def test_stages_exact_verified_bytes_and_preserves_legacy_catalog(self):
        with patch.object(package_modern, "component_sources", return_value=self.sources):
            components = package_modern.stage(self.root)
        self.assertEqual(len(components), 3)
        catalog = json.loads(self.catalog.read_text(encoding="utf-8"))
        self.assertEqual(catalog["1.8.9"], [{"file": "old.jar"}])
        self.assertEqual(catalog["1.21.11"], components)
        for component in components:
            source, _ = self.sources[component["file"]]
            self.assertEqual((self.assets / component["file"]).read_bytes(), source.read_bytes())
        lock = json.loads(self.lock_path.read_text(encoding="utf-8"))
        self.assertEqual(lock["componentScopeSha256"], lock["portScope"]["sha256"])

    def test_changed_scope_prevents_staging(self):
        self.scope.write_text("changed", encoding="utf-8")
        with patch.object(package_modern, "component_sources", return_value=self.sources):
            with self.assertRaisesRegex(ValueError, "scope has changed"):
                package_modern.stage(self.root)
        self.assertEqual(list(self.assets.glob("*.jar")), [])

    def test_wrong_minecraft_runtime_does_not_overwrite_catalog(self):
        source, _ = self.sources["ArcadiumFeatures-1.21.11.jar"]
        source.write_bytes(jar("arcadium_runtime", minecraft="1.8.9"))
        before = self.catalog.read_bytes()
        with patch.object(package_modern, "component_sources", return_value=self.sources):
            with self.assertRaisesRegex(ValueError, "different Minecraft"):
                package_modern.stage(self.root)
        self.assertEqual(self.catalog.read_bytes(), before)
        self.assertEqual(list(self.assets.glob("*.jar")), [])


if __name__ == "__main__":
    unittest.main()
