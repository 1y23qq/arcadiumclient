from pathlib import Path
import tempfile
import unittest

from package_client import source_artifact_version


class SourceArtifactVersionsTest(unittest.TestCase):
    def test_runtime_can_keep_legacy_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            project = root / "client-runtime"
            project.mkdir()
            (project / "build.gradle.kts").write_text('version = if (minecraftVersion == "1.7.10") "0.7.1" else "0.7.2"')
            self.assertEqual("0.7.1", source_artifact_version(root, "client-runtime", "0.7.2", "1.7.10"))
            self.assertEqual("0.7.2", source_artifact_version(root, "client-runtime", "0.7.2"))

    def test_independent_module_version_and_invalid_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            project = root / "forge-compat"
            project.mkdir()
            (project / "build.gradle.kts").write_text('version = "0.7.1"')
            self.assertEqual("0.7.1", source_artifact_version(root, "forge-compat", "0.7.2"))
            (project / "gradle.properties").write_text('modVersion=../outside')
            with self.assertRaises(ValueError):
                source_artifact_version(root, "forge-compat", "0.7.2")


if __name__ == "__main__":
    unittest.main()
