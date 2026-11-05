import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("renderer_build", ROOT / "client-runtime/native/renderer/build.py")
renderer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(renderer)


class RendererProbeInputTests(unittest.TestCase):
    def fixture(self, root, *, mod_id="pylon", version="0.2.2-beta.1.arcadium.1", omit=None):
        path = root / "ArcadiumPylon-1.8.9.jar"
        members = {"pl/tomgirl/pylon/window/DisplaySdl.class", "pl/tomgirl/pylon/window/GlSurface.class",
                   "pl/tomgirl/pylon/window/MainThread.class", "org/lwjgl/sdl/SDLVideo.class"}
        members |= {f"macos/{arch}/org/lwjgl/{name}" for arch in ("arm64", "x64")
                    for name in ("liblwjgl.dylib", "opengl/liblwjgl_opengl.dylib", "sdl/libSDL3.dylib")}
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": mod_id, "version": version}))
            for member in sorted(members - {omit}):
                archive.writestr(member, b"input-validation fixture")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        (root / "distribution").mkdir()
        (root / "distribution/oneclient-1.8.9.lock.json").write_text(json.dumps({"arcadiumComponents": [{
            "file": path.name, "modId": "pylon", "bytes": path.stat().st_size, "sha256": digest}]}))
        properties = root / "client-legacy-mods/pylon/gradle.properties"
        properties.parent.mkdir(parents=True)
        properties.write_text("version = 0.2.2-beta.1.arcadium.1\n")
        return path, digest

    def test_matches_both_explicit_digest_and_source_metadata(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-renderer-input-") as temporary:
            root = Path(temporary)
            path, digest = self.fixture(root)
            result = renderer.validate_pylon_input(path, digest, root)
            self.assertEqual(result["version"], "0.2.2-beta.1.arcadium.1")
            self.assertTrue(result["sourceCatalogVerified"])

    def test_rejects_wrong_explicit_digest(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-renderer-input-") as temporary:
            root = Path(temporary)
            path, _ = self.fixture(root)
            with self.assertRaisesRegex(RuntimeError, "checksums"):
                renderer.validate_pylon_input(path, "0" * 64, root)

    def test_rejects_tampered_bytes(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-renderer-input-") as temporary:
            root = Path(temporary)
            path, digest = self.fixture(root)
            path.write_bytes(path.read_bytes() + b"changed")
            with self.assertRaisesRegex(RuntimeError, "checksums"):
                renderer.validate_pylon_input(path, digest, root)

    def test_rejects_unpinned_source_digest(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-renderer-input-") as temporary:
            root = Path(temporary)
            path, digest = self.fixture(root)
            lock_path = root / "distribution/oneclient-1.8.9.lock.json"
            lock = json.loads(lock_path.read_text())
            lock["arcadiumComponents"][0]["sha256"] = "0" * 64
            lock_path.write_text(json.dumps(lock))
            with self.assertRaisesRegex(RuntimeError, "checksums"):
                renderer.validate_pylon_input(path, digest, root)

    def test_rejects_wrong_identity_or_version(self):
        for values in ({"mod_id": "other"}, {"version": "0.1.7"}):
            with self.subTest(values=values), tempfile.TemporaryDirectory(prefix="arcadium-renderer-input-") as temporary:
                root = Path(temporary)
                path, digest = self.fixture(root, **values)
                with self.assertRaisesRegex(RuntimeError, "identity or version"):
                    renderer.validate_pylon_input(path, digest, root)

    def test_rejects_missing_mac_native_library(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-renderer-input-") as temporary:
            root = Path(temporary)
            path, digest = self.fixture(root, omit="macos/arm64/org/lwjgl/sdl/libSDL3.dylib")
            with self.assertRaisesRegex(RuntimeError, "native libraries"):
                renderer.validate_pylon_input(path, digest, root)


if __name__ == "__main__":
    unittest.main()
