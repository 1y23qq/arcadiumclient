from pathlib import Path
import tempfile
import unittest

from build_client import invalidate_local_core_remaps


class LocalCoreRemapBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.cache = self.root / "client-modern/.gradle/loom-cache/remapped_mods/remapped/org/polyfrost/oneconfig"
        self.version = self.cache / "1.21.11-fabric-4abd26ae/1.4.4-arcadium"

    def test_only_local_core_remaps_are_removed(self):
        self.version.mkdir(parents=True)
        jar = self.version / "platform.jar"
        jar.write_bytes(b"stale")
        pom = self.version / "platform.pom"
        pom.write_text("metadata")
        unrelated = self.cache / "another-library/1.0/library.jar"
        unrelated.parent.mkdir(parents=True)
        unrelated.write_bytes(b"dependency")
        source = self.root / "source.java"
        source.write_text("class Source {}")
        invalidate_local_core_remaps(self.root)
        self.assertFalse(jar.exists())
        self.assertTrue(pom.exists())
        self.assertEqual(unrelated.read_bytes(), b"dependency")
        self.assertEqual(source.read_text(), "class Source {}")

    def test_missing_caches_do_not_create_or_remove_files(self):
        invalidate_local_core_remaps(self.root)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_linked_source_artifact_is_rejected(self):
        self.version.mkdir(parents=True)
        source = self.root / "source.jar"
        source.write_bytes(b"original")
        try:
            (self.version / "platform.jar").symlink_to(source)
        except OSError:
            self.skipTest("Symbolic links are unavailable")
        with self.assertRaisesRegex(ValueError, "outside its cache"):
            invalidate_local_core_remaps(self.root)
        self.assertEqual(source.read_bytes(), b"original")


if __name__ == "__main__":
    unittest.main()
