import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("encrypted_macos_worker", ROOT / "ci/encrypted-macos/worker.py")
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)
PYTHON_SUCCESS = b"...............\nRan 15 tests in 0.01s\n\nOK\n"
RUST_SUCCESS = (
    "\n".join(f"test game::launch::retired_mod_tests::case_{index} ... ok" for index in range(6))
    + "\n\ntest result: ok. 6 passed; 0 failed; 0 ignored; 0 measured; 400 filtered out; finished in 0.01s\n"
).encode()


class EncryptedMacosHotfixTests(unittest.TestCase):
    def run_success(self, command, **kwargs):
        output = None
        if "test_macos_compatibility.py" in command:
            output = PYTHON_SUCCESS
        elif worker.HYINFO_TEST_MODULE in command:
            output = RUST_SUCCESS
        return subprocess.CompletedProcess(command, 0, stdout=output)

    def test_hotfix_commands_select_only_changed_rust_tests_and_required_checks(self):
        for target, minimum in [("aarch64-apple-darwin", "13.0"), ("x86_64-apple-darwin", "12.0")]:
            with self.subTest(target=target):
                target_dir = Path("owned-target")
                steps = worker.hotfix_steps(target, target_dir, minimum)
                self.assertEqual([step[0] for step in steps], ["pythonCompatibility", "rustHyInfoRetirement",
                                 "macosUpdater", "package", "binaryCompatibility", "nativeHelpers"])
                rust = steps[1][1]
                self.assertEqual(rust, ["cargo", "test", "--locked", "--profile", "macos-testing", "--target", target,
                                       "-p", "arcadium_core", "--lib", worker.HYINFO_TEST_MODULE,
                                       "--", "--test-threads=1"])
                binary = str(target_dir / target / "macos-testing/Arcadium")
                self.assertIn(binary, steps[4][1])
                self.assertIn(binary, steps[5][1])
                self.assertEqual(steps[4][1][steps[4][1].index("--minimum") + 1], minimum)
                commands = " ".join(" ".join(step[1]) for step in steps)
                for skipped in ["gradlew", "arcadium_auth", "account_service", "test_native_cookie_consent.py"]:
                    self.assertNotIn(skipped, commands)

    def test_hotfix_executes_without_jvm_fixture_and_records_actual_test_counts(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            (source / "build").mkdir()
            (source / "build/forge-compat-ci-fixtures.zip").write_bytes(b"must not be opened in hotfix mode")
            environment = {}
            with patch.object(worker, "verify_components") as verify, \
                    patch.object(worker.subprocess, "run", side_effect=self.run_success) as run, \
                    contextlib.redirect_stdout(io.StringIO()):
                result = worker.execute(source, "macos-arm64", "hotfix", io.BytesIO(), environment)
            self.assertEqual(result, 0)
            self.assertEqual(verify.call_count, 2)
            self.assertEqual(run.call_count, 6)
            checks = json.loads((source / "build/hotfix-checks.json").read_text())
            self.assertTrue(checks["passed"])
            self.assertTrue(checks["componentHashesBefore"])
            self.assertTrue(checks["componentHashesAfter"])
            self.assertEqual(checks["pythonCompatibility"], {"tests": 15, "passed": 15, "failed": 0})
            self.assertEqual(checks["rustHyInfoRetirement"]["passed"], 6)
            self.assertEqual(len(checks["rustHyInfoRetirement"]["testNames"]), 6)
            self.assertEqual(set(checks["skipped"]), {"broadRustSuites", "jvmSuite", "nativeCookieConsent"})
            self.assertTrue(all(step["status"] == "passed" and step["exitCode"] == 0 for step in checks["steps"]))
            self.assertEqual(environment["MACOSX_DEPLOYMENT_TARGET"], "13.0")
            self.assertEqual(environment["CARGO_TARGET_DIR"], str(source / "target/macos-13.0"))

    def test_failed_command_stops_packaging_and_records_failure(self):
        def failed_rust(command, **kwargs):
            if worker.HYINFO_TEST_MODULE in command:
                return subprocess.CompletedProcess(command, 17, stdout=b"retirement regression failed\n")
            return self.run_success(command, **kwargs)

        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            with patch.object(worker, "verify_components") as verify, \
                    patch.object(worker.subprocess, "run", side_effect=failed_rust) as run, \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(worker.execute(source, "macos-x64", "hotfix", io.BytesIO(), {}), 17)
            checks = json.loads((source / "build/hotfix-checks.json").read_text())
            self.assertFalse(checks["passed"])
            self.assertFalse(checks["componentHashesAfter"])
            self.assertNotIn("rustHyInfoRetirement", checks)
            self.assertEqual(checks["steps"][-1]["status"], "failed")
            self.assertEqual(checks["steps"][-1]["exitCode"], 17)
            self.assertEqual(run.call_count, 2)
            verify.assert_called_once_with(source)

    def test_zero_selected_rust_tests_cannot_pass_with_successful_exit(self):
        def no_tests(command, **kwargs):
            if worker.HYINFO_TEST_MODULE in command:
                return subprocess.CompletedProcess(command, 0, stdout=b"test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 400 filtered out\n")
            return self.run_success(command, **kwargs)

        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            with patch.object(worker, "verify_components"), \
                    patch.object(worker.subprocess, "run", side_effect=no_tests) as run, \
                    contextlib.redirect_stdout(io.StringIO()), \
                    self.assertRaisesRegex(ValueError, "six named tests"):
                worker.execute(source, "macos-arm64", "hotfix", io.BytesIO(), {})
            checks = json.loads((source / "build/hotfix-checks.json").read_text())
            self.assertFalse(checks["passed"])
            self.assertEqual(checks["steps"][-1]["status"], "failed")
            self.assertIn("six named tests", checks["steps"][-1]["error"])
            self.assertEqual(run.call_count, 2)

    def test_counts_reject_missing_failing_short_or_unrelated_test_output(self):
        invalid = [
            ("pythonCompatibility", b"Ran 14 tests in 0.01s\nOK\n"),
            ("pythonCompatibility", b"Ran 15 tests in 0.01s\nFAILED (failures=1)\n"),
            ("rustHyInfoRetirement", RUST_SUCCESS.replace(b"game::launch::retired_mod_tests::", b"unrelated::")),
            ("rustHyInfoRetirement", RUST_SUCCESS.replace(b"0 failed", b"1 failed")),
            ("rustHyInfoRetirement", RUST_SUCCESS.replace(b"case_5", b"case_4")),
        ]
        for name, output in invalid:
            with self.subTest(name=name, output=output), self.assertRaises(ValueError):
                worker.hotfix_test_counts(name, output)

    def test_component_verification_failure_prevents_commands(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            with patch.object(worker, "verify_components", side_effect=ValueError("checksum mismatch")), \
                    patch.object(worker.subprocess, "run") as run, \
                    self.assertRaisesRegex(ValueError, "checksum mismatch"):
                worker.execute(source, "macos-arm64", "hotfix", io.BytesIO(), {})
            run.assert_not_called()
            checks = json.loads((source / "build/hotfix-checks.json").read_text())
            self.assertFalse(checks["passed"])
            self.assertFalse(checks["componentHashesBefore"])

    def test_full_package_mode_keeps_broad_rust_consent_and_jvm_checks(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            (source / "build").mkdir()
            (source / "build/forge-compat-ci-fixtures.zip").touch()
            with patch.object(worker, "verify_components") as verify, \
                    patch.object(worker, "java_fixture_environment", return_value={}) as java_env, \
                    patch.object(worker.subprocess, "run", side_effect=self.run_success) as run, \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(worker.execute(source, "macos-arm64", "package", io.BytesIO(), {}), 0)
            commands = [call.args[0] for call in run.call_args_list]
            self.assertEqual(len(commands), 12)
            self.assertEqual(verify.call_count, 2)
            java_env.assert_called_once()
            self.assertIn(["sh", "game-branding/gradlew", "-p", "forge-compat", "test", "--no-daemon"], commands)
            joined = " ".join(" ".join(command) for command in commands)
            for expected in ["arcadium_auth", "arcadium_update", "arcadium_java", "arcadium_core",
                             "arcadium_content", "arcadium_discord", "account_service", "test_native_cookie_consent.py"]:
                self.assertIn(expected, joined)
            self.assertNotIn(worker.HYINFO_TEST_MODULE, joined)
            self.assertFalse((source / "build/hotfix-checks.json").exists())

    def test_main_restores_cache_only_for_package_and_hotfix(self):
        for mode in ["package", "hotfix", "helpers", "consent"]:
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temporary:
                with patch.dict(os.environ, {"ARCADIUM_BUILD_KEY": "61" * 32, "RUNNER_TEMP": temporary}), \
                        patch.object(sys, "argv", ["worker.py", "--platform", "macos-arm64", "--mode", mode]), \
                        patch.object(worker, "unpack"), \
                        patch.object(worker, "compiler_cache", return_value=None) as cache, \
                        patch.object(worker, "execute", return_value=0), \
                        patch.object(worker, "seal") as seal, \
                        contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(worker.main(), 0)
                    self.assertEqual(cache.call_count, 1 if mode in {"package", "hotfix"} else 0)
                    self.assertEqual(seal.call_args.args[-1], mode)

    def test_hotfix_encrypted_results_include_explicit_evidence_without_stale_skipped_suites(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            (source / "build").mkdir(parents=True)
            for name in ["hotfix-checks.json", "forge-compat-ci-tests.json", "native-cookie-consent-tests.json",
                         "macos-native-helper-tests.json", "macos-compatibility-tests.json"]:
                (source / "build" / name).write_text("{}")
            xml = source / "forge-compat/build/test-results/test/TEST-stale.xml"
            xml.parent.mkdir(parents=True)
            xml.write_text("<testsuite tests='318'/>")
            log = root / "worker.log"
            log.write_text("private build fixture")
            encrypted = root / "result.enc"
            worker.seal(source, log, encrypted, "macos-arm64", b"a" * 32, 0, "hotfix")
            blob = encrypted.read_bytes()
            result = AESGCM(b"a" * 32).decrypt(blob[:12], blob[12:], worker.RESULT_AAD)
            with zipfile.ZipFile(io.BytesIO(result)) as archive:
                names = set(archive.namelist())
                self.assertEqual(json.loads(archive.read("status.json"))["mode"], "hotfix")
                self.assertIn("build/hotfix-checks.json", names)
                self.assertIn("build/macos-native-helper-tests.json", names)
                self.assertIn("build/macos-compatibility-tests.json", names)
                self.assertNotIn("build/forge-compat-ci-tests.json", names)
                self.assertNotIn("build/native-cookie-consent-tests.json", names)
                self.assertFalse(any(name.endswith(".xml") for name in names))
                self.assertFalse(any(name.endswith(".py") for name in names))
            self.assertNotIn(b"private build fixture", blob)

    def test_workflow_keeps_java_package_only_and_enables_hotfix_cache(self):
        workflow = (ROOT / "ci/encrypted-macos/macos.yml").read_text(encoding="utf-8")
        self.assertIn("options: [package, hotfix, helpers, consent]", workflow)
        java = workflow.split("uses: actions/setup-java@", 1)[1].split("      - ", 1)[0]
        cache = workflow.split("name: Restore encrypted compiler cache", 1)[1].split("      - ", 1)[0]
        rust = workflow.split("uses: dtolnay/rust-toolchain@", 1)[1].split("      - ", 1)[0]
        self.assertIn("if: inputs.mode == 'package'", java)
        self.assertNotIn("hotfix", java)
        self.assertIn("if: inputs.mode == 'package' || inputs.mode == 'hotfix'", cache)
        self.assertIn("if: inputs.mode != 'helpers'", rust)
        self.assertIn("--mode '${{ inputs.mode }}'", workflow)


if __name__ == "__main__":
    unittest.main()
