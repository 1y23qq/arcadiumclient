import json
from pathlib import Path
import tempfile
import unittest

from test_modern_runtime import fatal_log_error, isolated_local_voice, report_failures, verified_command


class NativeCandidateBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / ".arcadium-verification-profile").write_text("synthetic")
        self.verification = {"minecraftVersion": "1.21.11", "offlineProbe": True,
                             "gameDirectory": str(self.root / "runtime"), "userGameDirectory": str(self.root / ".minecraft")}
        self.command = ["java.exe", "--username", "ArcadiumTest", "--accessToken", "0", "--gameDir", self.verification["userGameDirectory"]]
        self.arguments = self.root / "command.json"
        self.save()

    def save(self):
        (self.root / "verification.json").write_text(json.dumps(self.verification), encoding="utf-8")
        self.arguments.write_text(json.dumps(self.command), encoding="utf-8")

    def test_shared_player_data_and_separate_runtime_directory_are_valid(self):
        actual, metadata = verified_command(self.root, self.arguments)
        self.assertEqual(actual, self.command)
        self.assertNotEqual(metadata["gameDirectory"], metadata["userGameDirectory"])

    def test_old_qa_jar_cannot_pass_a_requested_mode_without_its_report(self):
        (self.root / "result.json").write_text('{"passed": true}')
        self.assertTrue(report_failures(self.root, ["native-profiles.json"]))

    def test_failed_or_truncated_feature_report_is_not_accepted(self):
        (self.root / "fonts.json").write_text('{"passed": false, "error": "width mismatch"}')
        self.assertIn("width mismatch", report_failures(self.root, ["fonts.json"])[0])
        (self.root / "fonts.json").write_text('{"passed":')
        self.assertTrue(report_failures(self.root, ["fonts.json"]))

    def test_successful_feature_report_is_required_independently(self):
        (self.root / "fonts.json").write_text('{"passed": true, "restored": true}')
        self.assertEqual(report_failures(self.root, ["fonts.json"]), [])

    def test_optional_branch_must_supply_its_own_successful_evidence(self):
        path = self.root / "native-profiles.json"
        path.write_text('{"passed": true}')
        required = {"native-profiles.json": ["oslProfiles"]}
        self.assertTrue(report_failures(self.root, [path.name], required))
        path.write_text('{"passed": true, "oslProfiles": {"passed": true}}')
        self.assertEqual(report_failures(self.root, [path.name], required), [])

    def test_regular_user_profile_is_not_modified_or_launched(self):
        (self.root / ".arcadium-verification-profile").unlink()
        with self.assertRaisesRegex(ValueError, "isolated"):
            verified_command(self.root, self.arguments)

    def test_non_synthetic_token_is_rejected(self):
        self.command[self.command.index("--accessToken") + 1] = "not-a-synthetic-token"
        self.save()
        with self.assertRaisesRegex(ValueError, "synthetic"):
            verified_command(self.root, self.arguments)

    def test_external_runtime_or_game_path_is_rejected(self):
        self.verification["gameDirectory"] = str(self.root.parent / "other-runtime")
        self.save()
        with self.assertRaisesRegex(ValueError, "outside"):
            verified_command(self.root, self.arguments)

    def test_different_recorded_game_directory_is_rejected(self):
        self.command[-1] = str(self.root / "other-game")
        self.save()
        with self.assertRaisesRegex(ValueError, "different"):
            verified_command(self.root, self.arguments)

    def test_truncated_command_is_rejected(self):
        self.command = ["java.exe", "--username"]
        self.save()
        with self.assertRaises(ValueError):
            verified_command(self.root, self.arguments)

    def test_critical_mixin_failure_cannot_be_hidden_by_a_surviving_menu(self):
        log = "[Render thread/ERROR]: Critical injection failure: Callback armor failed (0/1) succeeded.\n[INFO]: Recovered screen"
        self.assertEqual(fatal_log_error(log), "Critical injection failure: Callback armor failed (0/1) succeeded.")

    def test_optional_mod_warning_and_absent_audio_device_are_not_startup_failures(self):
        log = "[WARN]: @Mixin optional target was not found\n[ERROR]: Failed to start microphone thread: no audio input"
        self.assertIsNone(fatal_log_error(log))

    def test_voice_recording_fixture_preserves_exact_original_configuration(self):
        user = self.root / ".minecraft"
        path = user / "config/voicechat/voicechat-client.properties"
        path.parent.mkdir(parents=True)
        original = b"# Existing settings\r\nrun_local_server=true\r\noutput_device=Example\r\n"
        path.write_bytes(original)
        with isolated_local_voice(self.root, user) as state:
            self.assertIn(b"run_local_server=false", path.read_bytes())
            self.assertIn(b"output_device=Example\r\n", path.read_bytes())
            self.assertFalse(state["restored"])
        self.assertEqual(path.read_bytes(), original)
        self.assertTrue(state["restored"])

    def test_voice_recording_fixture_never_touches_external_user_data(self):
        with self.assertRaisesRegex(ValueError, "outside"):
            with isolated_local_voice(self.root, self.root.parent / "external-minecraft"):
                self.fail("External profile was opened")


if __name__ == "__main__":
    unittest.main()
