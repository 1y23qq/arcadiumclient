import gzip
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import zipfile

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

import encrypted_build


SPEC = importlib.util.spec_from_file_location("macos_build_worker", Path(__file__).resolve().parents[1] / "ci/macos-build/worker.py")
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)


class EncryptedBuildTests(unittest.TestCase):
    def source(self, path, malicious=False):
        with tarfile.open(path, "w") as archive:
            entry = tarfile.TarInfo("../outside" if malicious else "private/source.rs")
            data = b"private application source fixture"
            entry.size = len(data)
            entry.mode = 0o644
            archive.addfile(entry, io.BytesIO(data))

    def test_source_roundtrip_keeps_private_content_out_of_public_parts(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-encrypted-build-") as temporary:
            root = Path(temporary)
            archive = root / "source.tar"
            self.source(archive)
            encrypted_build.prepare_source(archive, root / "payload", b"a" * 32)
            manifest = json.loads((root / "payload/manifest.json").read_text())
            self.assertEqual(manifest["parts"], ["part-000.enc"])
            encrypted = (root / "payload/part-000.enc").read_bytes()
            self.assertNotIn(b"application source fixture", encrypted)
            destination = root / "unpacked"
            destination.mkdir()
            worker.unpack(root, destination, b"a" * 32)
            self.assertEqual((destination / "private/source.rs").read_bytes(), b"private application source fixture")

    def test_wrong_keys_and_tampered_inputs_never_unpack_source(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-encrypted-build-") as temporary:
            root = Path(temporary)
            archive = root / "source.tar"
            self.source(archive)
            encrypted_build.prepare_source(archive, root / "payload", b"a" * 32)
            destination = root / "unpacked"
            destination.mkdir()
            with self.assertRaises(InvalidTag):
                worker.unpack(root, destination, b"b" * 32)
            part = root / "payload/part-000.enc"
            part.write_bytes(part.read_bytes()[:-1] + b"tampered")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                worker.unpack(root, destination, b"a" * 32)
            self.assertEqual(list(destination.iterdir()), [])

    def test_authenticated_source_cannot_escape_its_directory(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-encrypted-build-") as temporary:
            root = Path(temporary)
            archive = root / "source.tar"
            self.source(archive, malicious=True)
            encrypted_build.prepare_source(archive, root / "payload", b"a" * 32)
            destination = root / "unpacked"
            destination.mkdir()
            with self.assertRaisesRegex(ValueError, "source entry"):
                worker.unpack(root, destination, b"a" * 32)
            self.assertFalse((root / "outside").exists())

    def test_result_roundtrip_includes_only_package_outputs_and_private_logs(self):
        with tempfile.TemporaryDirectory(prefix="arcadium-encrypted-build-") as temporary:
            root = Path(temporary)
            source = root / "private"
            directory = source / "build/installers/macos-arm64"
            directory.mkdir(parents=True)
            (directory / "ArcadiumClient.dmg").write_bytes(b"fixture")
            (directory / "source.rs").write_bytes(b"should stay private")
            log = root / "private.log"
            log.write_text("private compiler log")
            encrypted = root / "result.enc"
            worker.seal(source, log, encrypted, "macos-arm64", b"a" * 32, 0)
            self.assertNotIn(b"private compiler log", encrypted.read_bytes())
            result = root / "result.zip"
            encrypted_build.decrypt_result(encrypted, result, b"a" * 32)
            with zipfile.ZipFile(result) as archive:
                self.assertEqual(json.loads(archive.read("status.json"))["exitCode"], 0)
                self.assertEqual(archive.read("worker.log"), b"private compiler log")
                self.assertTrue(any(name.endswith(".dmg") for name in archive.namelist()))
                self.assertFalse(any(name.endswith(".rs") for name in archive.namelist()))

    def test_result_aad_cannot_be_used_as_source(self):
        encrypted = AESGCM(b"a" * 32).encrypt(b"n" * 12, gzip.compress(b"fixture"), encrypted_build.RESULT_AAD)
        with self.assertRaises(InvalidTag):
            AESGCM(b"a" * 32).decrypt(b"n" * 12, encrypted, encrypted_build.SOURCE_AAD)


if __name__ == "__main__":
    unittest.main()
