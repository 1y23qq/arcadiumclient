import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import forge_compat_ci as ci


def jar(entries, year=1980):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        for name, content in entries.items():
            archive.writestr(zipfile.ZipInfo(name, (year, 1, 1, 0, 0, 0)), content)
    return output.getvalue()


class ForgeCompatCiTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.audit_path = self.root / "audit.json"
        self.bundle = self.root / "bundle.zip"
        self.source = self.root / "source 日本語 + 100%"
        self.home = self.root / "home"
        self.classpath = "${SOURCE}/build/forge-compat/mac-audit/runtime-libraries/library.jar"
        self.content = jar({"sample/Actual.class": b"\xca\xfe\xba\xbeactual"})
        self.item = self.input("library.jar", self.content, [self.classpath,
            "${HOME}/.gradle/caches/modules-2/files-2.1/example/library/1/hash/library.jar"])
        self.audit = {"schema": 1, "files": [self.item],
                      "testClasses": [{"class": "example.RealTest", "testMethods": 2}],
                      "profiles": {"full": {"testMethods": 2}, "core": {"testMethods": 2, "payloadSha256": [self.item["payloadSha256"]]}},
                      "commands": {"core": ["test", "--tests", "example.RealTest"]},
                      "trustedClasspathDestinations": [self.classpath]}

    def input(self, name, content, destinations, entry=None):
        path = self.root / name
        path.write_bytes(content)
        payload = zipfile.ZipFile(io.BytesIO(content)).read(entry) if entry else content
        result = {"source": str(path), "sourceBytes": len(content), "sourceSha256": hashlib.sha256(content).hexdigest(),
                  "payloadBytes": len(payload), "payloadSha256": hashlib.sha256(payload).hexdigest(),
                  "role": "trusted-headless-public-runtime-library", "destinations": destinations}
        if entry:
            result["archiveEntry"] = entry
        return result

    def export(self, **options):
        self.audit_path.write_text(json.dumps(self.audit), encoding="utf-8")
        return ci.export_bundle(self.audit_path, self.bundle, **options)

    def rewrite(self, change=None, extra=None, payload_change=None):
        with zipfile.ZipFile(self.bundle) as archive:
            entries = {item.filename: archive.read(item) for item in archive.infolist()}
        manifest = json.loads(entries["manifest.json"])
        if change:
            change(manifest)
        entries["manifest.json"] = json.dumps(manifest).encode()
        if extra:
            entries.update(extra)
        if payload_change:
            for name in entries:
                if name.startswith("blobs/"):
                    entries[name] = payload_change(entries[name])
        self.bundle.write_bytes(jar(entries))

    def report(self, content, name="TEST-example.RealTest.xml"):
        directory = self.root / "xml"
        directory.mkdir(exist_ok=True)
        (directory / name).write_text(content, encoding="utf-8")
        return directory

    def test_exports_one_blob_for_two_destinations_and_restores_actual_absolute_paths(self):
        result = self.export()
        self.assertEqual((1, 2), (result["payloads"], result["tests"]))
        with zipfile.ZipFile(self.bundle) as archive:
            self.assertEqual(2, len(archive.namelist()))
            self.assertNotIn(str(self.root).encode(), archive.read("manifest.json"))
        restored = ci.restore_bundle(self.bundle, self.source, self.home)
        self.assertEqual((2, 1), (restored["destinations"], restored["classpathEntries"]))
        target = self.source / "build/forge-compat/mac-audit/runtime-libraries/library.jar"
        self.assertEqual(self.content, target.read_bytes())
        self.assertEqual(self.content, (self.home / ".gradle/caches/modules-2/files-2.1/example/library/1/hash/library.jar").read_bytes())
        metadata = self.source / "build/forge-compat/fml-runtime-review/trusted-classpath.json"
        self.assertEqual([target.resolve().as_posix()], json.loads(metadata.read_text(encoding="utf-8")))
        self.assertTrue(ci.restore_bundle(self.bundle, self.source, self.home, True)["ok"])
        self.assertTrue(ci.restore_bundle(self.bundle, self.source, self.home)["ok"])

    def test_subset_keeps_only_the_exact_original_class_entry(self):
        entry = "sample/Preserved.class"
        content = b"\xca\xfe\xba\xbeoriginal class bytes"
        subset = self.input("feature.jar", jar({entry: content, "unrelated/resource": b"not exported"}),
                            ["${SOURCE}/build/feature-qa-028/mods/feature.jar"], entry)
        self.audit["files"].append(subset)
        self.export()
        ci.restore_bundle(self.bundle, self.source, self.home)
        with zipfile.ZipFile(self.source / "build/feature-qa-028/mods/feature.jar") as archive:
            self.assertEqual([entry], archive.namelist())
            self.assertEqual(content, archive.read(entry))
        self.assertTrue(ci.restore_bundle(self.bundle, self.source, self.home, True)["ok"])

    def test_source_hash_change_and_private_nonfixture_inputs_fail_before_export(self):
        Path(self.item["source"]).write_bytes(b"changed")
        with self.assertRaisesRegex(ci.FixtureError, "source checksum"):
            self.export()
        self.item["source"] = str(self.root / "credentials.txt")
        with self.assertRaisesRegex(ci.FixtureError, "approved JAR"):
            self.export()
        self.assertFalse(self.bundle.exists())

    def test_corrupted_payload_is_rejected_before_any_restore_write(self):
        self.export()
        self.rewrite(payload_change=lambda value: b"x" + value[1:])
        with self.assertRaisesRegex(ci.FixtureError, "checksum"):
            ci.restore_bundle(self.bundle, self.source, self.home)
        self.assertFalse(self.source.exists())
        self.assertFalse(self.home.exists())

    def test_zip_traversal_and_unlisted_blob_entries_are_rejected(self):
        for extra in [{"../escape": b"bad"}, {"blobs/" + "0" * 64: b"unlisted"}]:
            with self.subTest(extra=list(extra)):
                self.export()
                self.rewrite(extra=extra)
                with self.assertRaises(ci.FixtureError):
                    ci.restore_bundle(self.bundle, self.source, self.home)
                self.assertFalse(self.source.exists())

    def test_bundle_symlink_entry_and_missing_blob_are_rejected(self):
        self.export()
        with zipfile.ZipFile(self.bundle) as archive:
            entries = {item.filename: archive.read(item) for item in archive.infolist()}
        with zipfile.ZipFile(self.bundle, "w") as archive:
            for name, content in entries.items():
                info = zipfile.ZipInfo(name)
                info.create_system = 3
                info.external_attr = (0o120777 if name.startswith("blobs/") else 0o100644) << 16
                archive.writestr(info, content)
        with self.assertRaisesRegex(ci.FixtureError, "links"):
            ci.restore_bundle(self.bundle, self.source, self.home)
        self.export()
        self.rewrite(lambda manifest: manifest["files"][0].update(sha256="0" * 64, stagedSha256="0" * 64))
        with self.assertRaisesRegex(ci.FixtureError, "size disagrees"):
            ci.restore_bundle(self.bundle, self.source, self.home)

    def test_destination_traversal_home_overwrite_and_duplicate_aliases_are_rejected(self):
        for destination in ["${SOURCE}/build/../outside.jar", "${HOME}/.ssh/secret.jar", "${SOURCE}/build/forge-compat/logs/profile.jar", "/tmp/outside.jar"]:
            with self.subTest(destination=destination):
                self.export()
                self.rewrite(lambda manifest: manifest["files"][0]["destinations"].append(destination))
                with self.assertRaises(ci.FixtureError):
                    ci.restore_bundle(self.bundle, self.source, self.home)
                self.assertFalse(self.source.exists())
        self.export()
        self.rewrite(lambda manifest: manifest["files"][0]["destinations"].append(self.classpath))
        with self.assertRaisesRegex(ci.FixtureError, "Duplicate fixture destination"):
            ci.restore_bundle(self.bundle, self.source, self.home)

    def test_existing_file_and_classpath_mismatch_are_never_overwritten(self):
        self.export()
        target = self.source / "build/forge-compat/mac-audit/runtime-libraries/library.jar"
        target.parent.mkdir(parents=True)
        target.write_bytes(b"owned sentinel")
        with self.assertRaisesRegex(ci.FixtureError, "different existing fixture"):
            ci.restore_bundle(self.bundle, self.source, self.home)
        self.assertEqual(b"owned sentinel", target.read_bytes())
        target.unlink()
        metadata = self.source / "build/forge-compat/fml-runtime-review/trusted-classpath.json"
        metadata.parent.mkdir(parents=True)
        metadata.write_text('["other"]')
        with self.assertRaisesRegex(ci.FixtureError, "different existing classpath"):
            ci.restore_bundle(self.bundle, self.source, self.home)
        self.assertFalse(target.exists())
        self.assertFalse(self.home.exists())

    def test_symlink_destination_cannot_escape_its_root(self):
        self.export()
        outside = self.root / "outside"
        outside.mkdir()
        self.source.mkdir()
        try:
            (self.source / "build").symlink_to(outside, target_is_directory=True)
        except OSError:
            self.skipTest("The OS does not permit owned symlink creation.")
        with self.assertRaisesRegex(ci.FixtureError, "link or reparse"):
            ci.restore_bundle(self.bundle, self.source, self.home)
        self.assertEqual([], list(outside.iterdir()))

    def test_explicit_entry_identical_alias_preserves_jar_bytes_and_records_original_hash(self):
        second = self.input("second.jar", jar({"sample/Actual.class": b"\xca\xfe\xba\xbeactual"}, 2000),
                            ["${SOURCE}/build/forge-compat/dependencies/second.jar"])
        self.audit["files"].append(second)
        self.audit["optionalEntryIdenticalJarAliases"] = [{"canonicalPayloadSha256": self.item["payloadSha256"],
            "entryNameAndUncompressedSha256MapFingerprint": ci._entry_fingerprint(self.content),
            "destinations": [self.classpath, second["destinations"][0]]}]
        ordinary = self.export()
        self.assertEqual(2, ordinary["payloads"])
        aliased = self.export(entry_identical_aliases=True)
        self.assertEqual((1, 1), (aliased["payloads"], aliased["aliases"]))
        ci.restore_bundle(self.bundle, self.source, self.home)
        self.assertEqual(self.content, (self.source / "build/forge-compat/dependencies/second.jar").read_bytes())
        with zipfile.ZipFile(self.bundle) as archive:
            alias = json.loads(archive.read("manifest.json"))["entryIdenticalAliases"][0]
        self.assertEqual(second["payloadSha256"], alias["originalSha256"])
        self.audit["optionalEntryIdenticalJarAliases"][0]["entryNameAndUncompressedSha256MapFingerprint"] = "0" * 64
        with self.assertRaisesRegex(ci.FixtureError, "archive contents changed"):
            self.export(entry_identical_aliases=True)

    def test_current_source_discovery_updates_full_test_binding_and_ignores_fixture_strings(self):
        tests = self.root / "tests"
        tests.mkdir()
        (tests / "RealTest.java").write_text('package example; class RealTest { String s="@Test"; /* @Test */ @Test void a(){} @Test void b(){} }')
        (tests / "NewTest.java").write_text('package example; class NewTest { @org.junit.jupiter.api.Test void c(){} }')
        result = self.export(test_sources=tests)
        self.assertEqual((2, 3), (result["classes"], result["tests"]))
        with zipfile.ZipFile(self.bundle) as archive:
            manifest = json.loads(archive.read("manifest.json"))
        self.assertEqual(2, len(manifest["testSources"]))
        core = self.export(profile="core", test_sources=tests)
        self.assertEqual((1, 2), (core["classes"], core["tests"]))
        (tests / "NewTest.java").write_text('package example; class NewTest { @ParameterizedTest void c(){} }')
        with self.assertRaisesRegex(ci.FixtureError, "explicit count model"):
            self.export(test_sources=tests)

    def test_junit_accepts_only_complete_exact_zero_skip_results_without_exporting_logs(self):
        self.export()
        results = self.report('<testsuite name="example.RealTest" tests="2" failures="0" errors="0" skipped="0"><testcase name="a" classname="example.RealTest"/><testcase name="b" classname="example.RealTest"/><system-out>not copied to summary</system-out></testsuite>')
        result = ci.summarize_junit(self.bundle, results)
        self.assertTrue(result["ok"])
        self.assertEqual((1, 2, 0), (result["executedClasses"], result["tests"], result["skipped"]))
        self.assertNotIn("not copied", json.dumps(result))

    def test_junit_rejects_skips_failure_error_count_lies_duplicates_and_missing_classes(self):
        self.export()
        bodies = [
            '<testcase name="a"/><testcase name="b"><skipped/></testcase>',
            '<testcase name="a"><failure message="ignored"/></testcase><testcase name="b"/>',
            '<testcase name="a"><error/></testcase><testcase name="b"/>',
            '<testcase name="a"/>',
            '<testcase name="a"/><testcase name="a"/>',
            '<testcase name="a" classname="example.OtherTest"/><testcase name="b" classname="example.OtherTest"/>',
        ]
        for body in bodies:
            with self.subTest(body=body):
                directory = self.report('<testsuite name="example.RealTest" tests="2" skipped="0">' + body + '</testsuite>')
                self.assertFalse(ci.summarize_junit(self.bundle, directory)["ok"])

    def test_junit_rejects_external_entities_and_unbounded_documents(self):
        self.export()
        directory = self.report('<!DOCTYPE testsuite [<!ENTITY x SYSTEM "file:///ignored">]><testsuite name="example.RealTest"/>')
        with self.assertRaisesRegex(ci.FixtureError, "declarations"):
            ci.summarize_junit(self.bundle, directory)
        (directory / "TEST-example.RealTest.xml").write_bytes('<!DOCTYPE testsuite><testsuite/>'.encode("utf-16"))
        with self.assertRaisesRegex(ci.FixtureError, "UTF-8"):
            ci.summarize_junit(self.bundle, directory)
        self.report('<testsuite>')
        with self.assertRaisesRegex(ci.FixtureError, "Malformed"):
            ci.summarize_junit(self.bundle, directory)
        with patch.object(ci, "MAX_TOTAL_BYTES", 2):
            with self.assertRaisesRegex(ci.FixtureError, "size limit"):
                ci.restore_bundle(self.bundle, self.source, self.home)

    def test_cli_persists_a_failed_machine_summary_and_returns_nonzero(self):
        self.export()
        directory = self.report('<testsuite name="example.RealTest" tests="1"><testcase name="a"/></testsuite>')
        output = self.root / "result.json"
        with patch("sys.stdout", new=io.StringIO()):
            code = ci.main(["junit", "--bundle", str(self.bundle), "--results", str(directory), "--output", str(output)])
        self.assertEqual(1, code)
        result = json.loads(output.read_text(encoding="utf-8"))
        self.assertFalse(result["ok"])
        self.assertEqual(2, result["expectedTests"])


if __name__ == "__main__":
    unittest.main()
