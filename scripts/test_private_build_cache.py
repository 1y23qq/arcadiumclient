import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

from cryptography.exceptions import InvalidTag
from private_build_cache import _EncryptedWriter, inventory, restore_cache, save_cache


class PrivateBuildCacheTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.target = self.root / "target"
        self.target.mkdir()
        self.cache = self.root / "cache.enc"
        self.restored = self.root / "restored"
        self.key = bytes(range(32))
        self.identity = {"rustc": "test", "target": "aarch64-apple-darwin", "minimum": "13.0"}

    def file(self, name, content=b"compiler output"):
        path = self.target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def save(self):
        return save_cache(self.target, self.cache, self.key, self.identity)

    def crafted(self, entries):
        with self.cache.open("wb") as output:
            writer = _EncryptedWriter(output, self.key, self.identity)
            with tarfile.open(mode="w|gz", fileobj=writer) as archive:
                metadata = json.dumps({"format": 1, "identity": self.identity}).encode()
                for name, raw, kind in [("cache.json", metadata, tarfile.REGTYPE), *entries]:
                    info = tarfile.TarInfo(name)
                    info.size, info.type = len(raw), kind
                    archive.addfile(info, io.BytesIO(raw))
            writer.finish()

    def test_roundtrip_preserves_native_outputs_and_timestamps(self):
        path = self.file("aarch64-apple-darwin/macos-testing/deps/libfreya-123.rlib", b"output" * 4096)
        os.utime(path, (1_600_000_000, 1_600_000_000))
        path.chmod(0o755)
        saved = self.save()
        restored = restore_cache(self.cache, self.restored, self.key, self.identity)
        result = self.restored / path.relative_to(self.target)
        self.assertEqual(result.read_bytes(), path.read_bytes())
        self.assertEqual(int(result.stat().st_mtime), 1_600_000_000)
        self.assertEqual(restored, {key: saved[key] for key in ("files", "bytes")})

    def test_workspace_outputs_are_always_rebuilt(self):
        for name in ["deps/libarcadium_auth-ab.rlib", ".fingerprint/arcadium_app-ab/lib.json",
                     "build/arcadium_common-ab/output", "Arcadium", "incremental/dependency/x"]:
            self.file(name)
        self.file("deps/libserde-ab.rlib")
        self.assertEqual(self.save()["files"], 1)
        restore_cache(self.cache, self.restored, self.key, self.identity)
        self.assertEqual([p.relative_to(self.restored).as_posix() for p in self.restored.rglob("*") if p.is_file()],
                         ["deps/libserde-ab.rlib"])

    def test_ciphertext_does_not_contain_output(self):
        marker = b"private compiler output that must remain encrypted"
        self.file("deps/libtest.rlib", marker)
        self.save()
        self.assertNotIn(marker, self.cache.read_bytes())

    def test_inventory_ignores_workspace_rebuilds_but_detects_dependency_changes(self):
        self.file("deps/libserde-ab.rlib")
        before = inventory(self.target)
        self.file("deps/libarcadium_app-ab.rlib")
        self.file(".rustc_info.json")
        self.assertEqual(inventory(self.target), before)
        self.file("deps/libserde-ab.rlib", b"new dependency output")
        self.assertNotEqual(inventory(self.target), before)

    def test_changed_identity_or_key_cannot_restore(self):
        self.file("deps/libtest.rlib")
        self.save()
        for key, identity in [(bytes(32), self.identity), (self.key, dict(self.identity, minimum="12.0"))]:
            with self.subTest(identity=identity), self.assertRaises(InvalidTag):
                restore_cache(self.cache, self.restored, key, identity)
            self.assertFalse(self.restored.exists())

    def test_corruption_and_truncation_cannot_restore_partial_files(self):
        self.file("deps/libtest.rlib")
        self.save()
        raw = self.cache.read_bytes()
        for data in [raw[:-1], raw[:8], raw[:40] + bytes([raw[40] ^ 1]) + raw[41:]]:
            self.cache.write_bytes(data)
            with self.subTest(length=len(data)), self.assertRaises((InvalidTag, ValueError)):
                restore_cache(self.cache, self.restored, self.key, self.identity)
            self.assertFalse(self.restored.exists())

    def test_traversal_absolute_and_owned_entries_are_rejected(self):
        for name in ["target/../outside", "/outside", "target//double", "target/C:/outside",
                     "target/deps/libarcadium_app-ab.rlib", "target/a\\b"]:
            self.crafted([(name, b"data", tarfile.REGTYPE)])
            with self.subTest(name=name), self.assertRaises(ValueError):
                restore_cache(self.cache, self.restored, self.key, self.identity)
            self.assertFalse(self.restored.exists())

    def test_duplicate_and_link_entries_are_rejected(self):
        for entries in [[("target/one", b"x", tarfile.REGTYPE)] * 2,
                        [("target/link", b"", tarfile.SYMTYPE)], [("target/link", b"", tarfile.LNKTYPE)]]:
            self.crafted(entries)
            with self.assertRaises(ValueError):
                restore_cache(self.cache, self.restored, self.key, self.identity)

    def test_existing_destination_is_preserved(self):
        self.file("deps/libtest.rlib")
        self.save()
        self.restored.mkdir()
        existing = self.restored / "user-file"
        existing.write_bytes(b"preserve")
        with self.assertRaises(ValueError):
            restore_cache(self.cache, self.restored, self.key, self.identity)
        self.assertEqual(existing.read_bytes(), b"preserve")

    def test_failed_save_does_not_replace_previous_cache(self):
        self.file("deps/libtest.rlib")
        self.cache.write_bytes(b"previous")
        with patch("private_build_cache.MAX_PAYLOAD", 1), self.assertRaises(ValueError):
            self.save()
        self.assertEqual(self.cache.read_bytes(), b"previous")

    def test_empty_or_recursive_cache_is_rejected(self):
        with self.assertRaises(ValueError):
            self.save()
        self.file("deps/libtest.rlib")
        with self.assertRaises(ValueError):
            save_cache(self.target, self.target / "recursive.enc", self.key, self.identity)

    def test_restore_enforces_uncompressed_size_limit(self):
        self.crafted([("target/large", b"x" * 1024, tarfile.REGTYPE)])
        with patch("private_build_cache.MAX_PAYLOAD", 10), self.assertRaises(ValueError):
            restore_cache(self.cache, self.restored, self.key, self.identity)
        self.assertFalse(self.restored.exists())


if __name__ == "__main__":
    unittest.main()
