from pathlib import Path
import tempfile
import unittest

from test_next_runtime import renderer_fixture


class RendererFixtureTests(unittest.TestCase):
    def test_preserves_every_existing_byte_after_a_failed_game_test(self):
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory)
            game = profile / ".minecraft"
            game.mkdir()
            path = game / "options.txt"
            original = 'lang:ja_jp\r\nunknown:日本語\r\npreferredGraphicsBackend:"opengl"\r\n'.encode("utf-8")
            path.write_bytes(original)
            with self.assertRaisesRegex(RuntimeError, "native failure"):
                with renderer_fixture(profile, game, "vulkan"):
                    changed = path.read_text(encoding="utf-8")
                    self.assertIn('preferredGraphicsBackend:"vulkan"', changed)
                    self.assertIn('unknown:日本語', changed)
                    raise RuntimeError("native failure")
            self.assertEqual(path.read_bytes(), original)

    def test_missing_options_file_is_restored_to_missing_after_probe(self):
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory)
            game = profile / ".minecraft"
            with renderer_fixture(profile, game, "opengl"):
                self.assertTrue((game / "options.txt").is_file())
                self.assertIn("version:4903", (game / "options.txt").read_text())
            self.assertFalse((game / "options.txt").exists())

    def test_refuses_to_write_outside_the_isolated_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = root / "profile"
            outside = root / "outside"
            outside.mkdir()
            with self.assertRaisesRegex(ValueError, "outside"):
                with renderer_fixture(profile, outside, "opengl"):
                    self.fail("outside options must not be written")
            self.assertFalse((outside / "options.txt").exists())


if __name__ == "__main__":
    unittest.main()
