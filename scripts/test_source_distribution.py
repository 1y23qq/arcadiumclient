import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import zipfile

import check_source_distribution as gate


def bundle(extra=None, minecraft_version="1.8.9"):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("modrinth.index.json", json.dumps({"dependencies": {"minecraft": minecraft_version}, "files": []}))
        for name, raw in (extra or {}).items():
            archive.writestr(name, raw)
    return output.getvalue()


class SourcePublicationTests(unittest.TestCase):
    def test_original_metadata_compatibility_override_is_allowed_without_binary_payloads(self):
        raw = bundle({"overrides/": b"", "overrides/config/": b"", "overrides/config/fabric_loader_dependencies.json": json.dumps({"version": 1, "overrides": {"pylon": {"+breaks": {"lenis": "*"}}}})})
        self.assertEqual(gate.check_file(next(name for name in sorted(gate.BUNDLES) if "1.8.9" in name), raw), [])

    def test_legacy_bundle_requires_its_own_version_and_no_payload(self):
        name = "packages/arcadium_common/assets/distribution/core-1.7.10-ornithe.mrpack"
        self.assertEqual(gate.check_file(name, bundle(minecraft_version="1.7.10")), [])
        self.assertTrue(gate.check_file(name, bundle()))
        self.assertTrue(gate.check_file(name, bundle({"mods/old.jar": b"data"}, "1.7.10")))

    def test_game_mod_payload_or_unreviewed_override_cannot_enter_source_mrpack(self):
        for name in ["mods/forbidden.jar", "overrides/config/unreviewed.json", "overrides/saves/level.dat"]:
            with self.subTest(name=name):
                self.assertTrue(gate.check_file(sorted(gate.BUNDLES)[0], bundle({name: b"payload"})))

    def test_changed_loader_override_requires_explicit_review(self):
        self.assertTrue(gate.check_file(sorted(gate.BUNDLES)[0], bundle({"overrides/config/fabric_loader_dependencies.json": '{"version":1,"overrides":{}}'})))

    def test_private_source_generated_outputs_and_account_files_are_rejected(self):
        for name in ["packages/arcadium_polyplus/src/lib.rs", "client-renderer/COPYING", "client-runtime/build/output.txt", "target/output", "scripts/accounts.json", "packages/arcadium_common/assets/distribution/ArcadiumCore-1.8.9.jar"]:
            with self.subTest(name=name):
                self.assertTrue(gate.check_file(name, b"data"))

    def test_source_and_owned_assets_are_allowed(self):
        for name, raw in [("client-runtime/src/main/java/Example.java", b"package example;"), ("packages/arcadium_app/assets/logo.png", b"\x89PNG\x00"), ("client-ui/sounds/ui/click.ogg", b"OggS\x00")]:
            with self.subTest(name=name):
                self.assertEqual(gate.check_file(name, raw), [])

    def test_native_log_module_sources_are_preserved(self):
        self.assertEqual(len(gate.NATIVE_LOG_SOURCES), 5)
        for name in gate.NATIVE_LOG_SOURCES:
            with self.subTest(name=name):
                self.assertEqual(gate.check_file(name, b"pub fn read_log() {}"), [])

    def test_runtime_logs_cannot_use_the_native_source_exception(self):
        for name in ["client-runtime/logs/latest.log", "packages/arcadium_cluster/src/logs/session.txt",
                     "packages/arcadium_cluster/build/logs/mod.rs"]:
            with self.subTest(name=name):
                self.assertTrue(gate.check_file(name, b"runtime data"))

    def test_missing_native_log_sources_fail_publication(self):
        with patch.object(gate, "publication_files", return_value=[]):
            report = gate.inspect(Path("unused"))
        self.assertFalse(report["passed"])
        self.assertEqual({entry["path"] for entry in report["failures"]}, gate.NATIVE_LOG_SOURCES)

    def test_credential_literals_are_rejected_without_rejecting_synthetic_home_examples(self):
        private_token = ("ghp_" + "x" * 36).encode()
        self.assertTrue(gate.check_file("scripts/helper.py", private_token))
        self.assertEqual(gate.check_file("scripts/helper.py", ('"/' + 'Users/me/example"').encode()), [])
        self.assertTrue(gate.check_file("scripts/helper.py", ('"/' + 'Users/actual-person/private"').encode()))

    def test_standalone_video_source_cannot_silently_retain_inactive_shield_branch(self):
        self.assertTrue(gate.check_file(sorted(gate.RESTORED_VIDEO)[0], b"//? if = 1.8.9 {\n/*import unused\n*///?}"))


if __name__ == "__main__":
    unittest.main()
