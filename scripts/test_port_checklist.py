from __future__ import annotations

from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

import port_checklist as cli


def small_catalog() -> dict:
    catalog = cli.load_catalog(cli.DEFAULT_CATALOG)
    return dict(catalog, features=catalog["features"][:3])


class ChecklistTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "choices.json"
        self.catalog = small_catalog()
        self.ids = [feature["id"] for feature in self.catalog["features"]]

    def run_keys(self, keys, state=None, **kwargs):
        current = cli.new_state(self.catalog) if state is None else state
        events = iter(keys)
        output = io.StringIO()
        with redirect_stdout(output):
            cli.run_checklist(self.catalog, current, self.path, get_key=lambda: next(events), **kwargs)
        return current, output.getvalue()

    def test_single_digits_advance_and_autosave_without_enter(self):
        events = iter("12q")
        reads = 0

        def key():
            nonlocal reads
            if reads:
                saved = json.loads(self.path.read_text(encoding="utf-8"))
                self.assertEqual(saved["counts"]["unanswered"], 3 - reads)
            reads += 1
            return next(events)

        with redirect_stdout(io.StringIO()):
            cli.run_checklist(self.catalog, cli.new_state(self.catalog), self.path, get_key=key)
        saved = cli.load_state(self.path, self.catalog)
        self.assertEqual(list(saved["answers"].values()), [True, False, None])
        self.assertEqual(saved["cursor"], self.ids[2])

    def test_resume_does_not_reask_answered_items(self):
        self.run_keys("1q")
        state = cli.load_state(self.path, self.catalog)
        state, output = self.run_keys("2q", state)
        self.assertIn("[2/3]", output)
        self.assertNotIn("[1/3]", output)
        self.assertEqual(list(state["answers"].values()), [True, False, None])

    def test_back_changes_previous_answer(self):
        state, _ = self.run_keys("1b2q")
        self.assertEqual(list(state["answers"].values()), [False, None, None])
        self.assertEqual(state["cursor"], self.ids[1])

    def test_details_invalid_keys_and_enter_do_not_answer(self):
        state, output = self.run_keys(["d", "x", "\r", "\n", "", "q"])
        self.assertEqual(cli.counts(state), (0, 0, 3))
        self.assertIn(f"ID: {self.ids[0]}", output)

    def test_all_no_is_complete_and_exports_labels_and_ids(self):
        state, output = self.run_keys("222q")
        saved = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertTrue(saved["complete"])
        self.assertEqual(saved["counts"], {"port": 0, "do_not_port": 3, "unanswered": 0})
        self.assertIsNone(state["cursor"])
        self.assertIn("全項目の回答が完了", output)
        self.assertEqual(saved["features"][0]["port"], False)
        self.assertEqual(saved["features"][0]["id"], self.ids[0])
        self.assertIn("[移植しない]", self.path.with_suffix(".txt").read_text(encoding="utf-8"))

    def test_completion_can_revisit_without_resetting_answers(self):
        state, _ = self.run_keys("111r2q")
        self.assertEqual(list(state["answers"].values()), [False, True, True])

    def test_edit_specific_feature_preserves_other_answers(self):
        state, _ = self.run_keys("111q")
        state, output = self.run_keys("2q", state, start_id=self.ids[1])
        self.assertIn("[2/3]", output)
        self.assertEqual(list(state["answers"].values()), [True, False, True])

    def test_ctrl_c_saves_current_position(self):
        calls = 0

        def key():
            nonlocal calls
            calls += 1
            if calls == 1:
                return "1"
            raise KeyboardInterrupt

        with redirect_stdout(io.StringIO()):
            cli.run_checklist(self.catalog, cli.new_state(self.catalog), self.path, get_key=key)
        saved = cli.load_state(self.path, self.catalog)
        self.assertEqual(list(saved["answers"].values()), [True, None, None])
        self.assertEqual(saved["cursor"], self.ids[1])

    def test_partial_result_is_not_complete(self):
        self.run_keys("1q")
        saved = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertFalse(saved["complete"])
        self.assertIsNone(saved["features"][1]["port"])
        self.assertIn("回答途中", self.path.with_suffix(".txt").read_text(encoding="utf-8"))

    def test_read_only_summary_and_listing_do_not_create_results(self):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(cli.main(["--summary", "--results", str(self.path)]), 0)
            self.assertEqual(cli.main(["--list", "--results", str(self.path)]), 0)
        self.assertFalse(self.path.exists())

    def test_mismatched_catalog_does_not_overwrite_results(self):
        self.run_keys("1q")
        original = self.path.read_bytes()
        changed = dict(self.catalog, source_client="another")
        with self.assertRaises(cli.ChecklistError):
            cli.load_state(self.path, changed)
        self.assertEqual(original, self.path.read_bytes())

    def test_corrupt_results_are_not_overwritten(self):
        self.path.write_text("not json", encoding="utf-8")
        with self.assertRaises(ValueError):
            cli.load_state(self.path, self.catalog)
        self.assertEqual(self.path.read_text(encoding="utf-8"), "not json")

    def test_non_boolean_saved_choice_is_rejected(self):
        state = cli.new_state(self.catalog)
        state["answers"][self.ids[0]] = 1
        self.path.write_text(json.dumps(state), encoding="utf-8")
        with self.assertRaises(cli.ChecklistError):
            cli.load_state(self.path, self.catalog)

    def test_non_object_results_are_rejected_without_overwriting(self):
        self.path.write_text("[]", encoding="utf-8")
        with self.assertRaises(cli.ChecklistError):
            cli.load_state(self.path, self.catalog)
        self.assertEqual(self.path.read_text(encoding="utf-8"), "[]")

    def test_atomic_write_failure_preserves_previous_results(self):
        cli.atomic_write(self.path, "original")
        with patch.object(cli.os, "replace", side_effect=OSError("test write failure")):
            with self.assertRaises(OSError):
                cli.atomic_write(self.path, "replacement")
        self.assertEqual(self.path.read_text(encoding="utf-8"), "original")
        self.assertEqual(list(self.path.parent.iterdir()), [self.path])

    def test_rejects_saving_over_catalog(self):
        with redirect_stdout(io.StringIO()), patch("sys.stderr", new=io.StringIO()):
            with self.assertRaises(SystemExit):
                cli.main(["--results", str(cli.DEFAULT_CATALOG)])

    def test_japanese_wrapping_uses_display_width(self):
        self.assertEqual(cli.wrap_line("FPS 日本語ABC", 8), ["FPS 日本", "語ABC"])
        self.assertEqual(cli.wrap_line("a\nb", 8), ["a", "b"])


class CatalogCoverageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = cli.load_catalog(cli.DEFAULT_CATALOG)
        cls.ids = {feature["id"] for feature in cls.catalog["features"]}

    def test_source_references_exist(self):
        paths = set(self.catalog["inventory_sources"])
        paths.update(source for feature in self.catalog["features"] for source in feature.get("sources", []))
        for source in paths:
            with self.subTest(source=source):
                self.assertTrue((cli.ROOT / source).exists())

    def test_every_locked_bundle_mod_has_review_or_library_classification(self):
        lock = json.loads((cli.ROOT / "distribution/oneclient-1.8.9.lock.json").read_text(encoding="utf-8"))
        prefixes = self.catalog["bundle_coverage"].keys() | self.catalog["support_libraries"].keys()
        for bundle in lock["bundles"]:
            for mod in bundle["mods"]:
                name = mod["path"].rsplit("/", 1)[-1].lower()
                with self.subTest(mod=name):
                    self.assertTrue(any(name.startswith(prefix) for prefix in prefixes), name)

    def test_runtime_components_are_all_covered(self):
        path = cli.ROOT / "client-runtime/src/main/kotlin/io/github/arcadiumclient/runtime/ArcadiumRuntime.kt"
        source = path.read_text(encoding="utf-8")
        components = set(re.findall(r'"([^"]+)"\s+to\s+\{', source))
        self.assertGreater(len(components), 20)
        self.assertEqual(components, self.catalog["runtime_coverage"].keys())

    def test_every_evergreen_hud_has_a_question(self):
        path = cli.ROOT / "client-legacy-mods/evergreenhud/src/main/kotlin/org/polyfrost/evergreenhud/client/EvergreenHudClient.kt"
        source = path.read_text(encoding="utf-8")
        section = source.split("val huds = arrayOf(", 1)[1].split("huds.forEach", 1)[0]
        classes = set(re.findall(r"\b(\w+Hud)\(\)", section))
        classes.update(re.findall(r"::(\w+Hud)\)", source))
        self.assertEqual(classes, self.catalog["hud_coverage"].keys())

    def test_all_coverage_targets_are_valid(self):
        targets = list(self.catalog["hud_coverage"].values())
        for field in ("bundle_coverage", "runtime_coverage"):
            targets.extend(target for group in self.catalog[field].values() for target in group)
        self.assertFalse(set(targets) - self.ids)

    def test_removed_modules_are_not_offered(self):
        for feature in self.catalog["features"]:
            title = feature["title"].lower()
            self.assertNotIn("crash patch", title)
            self.assertNotIn("hyinfo", title)
            self.assertNotIn("polyplus", title)


if __name__ == "__main__":
    unittest.main()
