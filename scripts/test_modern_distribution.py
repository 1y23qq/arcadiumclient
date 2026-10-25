from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import modern_distribution as modern
import port_checklist


NAME = "hud-1.21.11-fabric.mrpack"


def entry(name: str, enabled: bool = True, raw: bytes = b"dependency fixture") -> dict:
    return {"path": f"mods/{name}", "enabled": enabled, "hidden": False, "fileSize": len(raw),
            "hashes": {"sha1": hashlib.sha1(raw).hexdigest(), "sha512": hashlib.sha512(raw).hexdigest()},
            "downloads": [f"https://cdn.modrinth.com/data/fixture/{name}"]}


def bundle(entries: list[dict], extra: dict[str, bytes] | None = None) -> bytes:
    index = {"formatVersion": 1, "game": "minecraft", "name": "OneClient 1.21.11 Fabric [HUD]",
             "versionId": "1.0.0", "updateUrl": "oneclient/generated/old.mrpack", "files": entries,
             "dependencies": {"fabric-loader": "0.19.5", "minecraft": "1.21.11"}}
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("modrinth.index.json", json.dumps(index))
        for name, raw in (extra or {}).items():
            archive.writestr(name, raw)
    return output.getvalue()


def locked_entry(name: str, enabled: bool = True, raw: bytes = b"dependency fixture") -> dict:
    value = entry(name, enabled, raw)
    return value | value["hashes"] | {"bytes": value["fileSize"]}


class ModernDistributionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.catalog = port_checklist.load_catalog(port_checklist.DEFAULT_CATALOG)

    def pin(self, raw):
        patcher = patch.object(modern, "PINNED_BUNDLES", {NAME: hashlib.sha1(raw).hexdigest()})
        patcher.start()
        self.addCleanup(patcher.stop)

    def completed_state(self):
        state = port_checklist.new_state(self.catalog)
        state["answers"] = dict.fromkeys(state["answers"], True)
        return state

    def test_incomplete_review_cannot_be_frozen(self):
        with self.assertRaisesRegex(ValueError, "incomplete"):
            modern.build_scope(self.catalog, port_checklist.new_state(self.catalog), b"fixture")

    def test_scope_keeps_explicit_exclusions_and_no_optional_optimizer_work(self):
        state = self.completed_state()
        state["answers"]["integration.forge-189"] = False
        scope = modern.build_scope(self.catalog, state, b"fixture")
        choices = {feature["id"]: feature["port"] for feature in scope["features"]}
        self.assertFalse(choices["integration.forge-189"])
        self.assertTrue(choices["hud.fps"])
        self.assertEqual(scope["review"]["excluded"], 1)
        self.assertFalse(scope["optionalOptimizerSelector"])
        self.assertEqual(scope["review"]["resultSha256"], hashlib.sha256(b"fixture").hexdigest())

    def test_scope_rejects_different_catalog(self):
        state = self.completed_state()
        state["catalog_sha256"] = "different"
        with self.assertRaisesRegex(ValueError, "different"):
            modern.build_scope(self.catalog, state, b"fixture")

    def test_baseline_excludes_restricted_replaced_components_and_keeps_defaults(self):
        raw = bundle([entry("feature.jar"), entry("optional.jar", False), entry("polyplus-1.jar"),
                      entry("OneConfig-1.jar"), entry("compose-bundle-1.jar"), entry("chattweaks-1.jar"),
                      entry("polysprint-1.jar"), entry("CrashPatch-1.jar")])
        self.pin(raw)
        output, selected, excluded = modern.adapt_bundle(raw, NAME)
        self.assertEqual([e["path"] for e in selected], ["mods/feature.jar", "mods/optional.jar"])
        self.assertFalse(selected[1]["enabled"])
        self.assertEqual(len(excluded), 6)
        with zipfile.ZipFile(io.BytesIO(output)) as archive:
            index = json.loads(archive.read("modrinth.index.json"))
            self.assertEqual(index["updateUrl"], f"arcadium/bundles/{NAME}")
            self.assertEqual(index["versionId"], "0.5.0")
            self.assertNotIn("OneClient", index["name"])

    def test_upstream_tampering_is_rejected(self):
        raw = bundle([entry("feature.jar")])
        self.pin(raw)
        with self.assertRaisesRegex(ValueError, "checksum"):
            modern.verify_source_bundle(raw + b"tampering", NAME)

    def test_binary_payload_and_unreviewed_overrides_are_rejected(self):
        for extra in [{"mods/embedded.jar": b"binary"}, {"overrides/config/unknown.json": b"{}"},
                      {"overrides/config/fabric_loader_dependencies.json": b"{}"}]:
            raw = bundle([entry("feature.jar")], extra)
            with self.subTest(extra=list(extra)), patch.object(modern, "PINNED_BUNDLES", {NAME: hashlib.sha1(raw).hexdigest()}):
                with self.assertRaises(ValueError):
                    modern.verify_source_bundle(raw, NAME)

    def test_bundle_generation_is_reproducible(self):
        raw = bundle([entry("feature.jar")])
        self.pin(raw)
        self.assertEqual(modern.adapt_bundle(raw, NAME)[0], modern.adapt_bundle(raw, NAME)[0])

    def test_dependency_rules_survive_either_bundle_extraction_order(self):
        path = "overrides/config/fabric_loader_dependencies.json"
        extracted = []
        for name, original in modern.EXPECTED_OVERRIDES.items():
            raw = bundle([entry("feature.jar")], {path: json.dumps(original).encode()})
            with patch.object(modern, "PINNED_BUNDLES", {name: hashlib.sha1(raw).hexdigest()}):
                output = modern.adapt_bundle(raw, name)[0]
            with zipfile.ZipFile(io.BytesIO(output)) as archive:
                extracted.append(json.loads(archive.read(path)))
        for last in extracted:
            self.assertEqual(set(last["overrides"]), {
                "gnetum", "zfastnoise", "cubes-without-borders", "idontwannascrollagain", "nbtac", "packed_packs"
            })
            self.assertEqual(last["overrides"]["gnetum"]["+breaks"]["vulkanmod"], "*")
            self.assertEqual(last["overrides"]["idontwannascrollagain"]["-depends"]["minecraft"], "IGNORED")

    def test_failed_prepare_does_not_write_partial_distribution(self):
        raw = bundle([entry("feature.jar")])
        self.pin(raw)
        cache = self.root / "cache"
        cache.mkdir()
        (cache / NAME).write_bytes(b"corrupted")
        with self.assertRaisesRegex(ValueError, "checksum"):
            modern.prepare(self.root, self.catalog, self.completed_state(), b"fixture", cache)
        self.assertFalse((self.root / "distribution").exists())

    def test_snapshot_only_publishes_metadata(self):
        raw = bundle([entry("feature.jar"), entry("optional.jar", False)])
        self.pin(raw)
        cache = self.root / "cache"
        cache.mkdir()
        (cache / NAME).write_bytes(raw)
        result = modern.prepare(self.root, self.catalog, self.completed_state(), b"fixture", cache)
        target = self.root / modern.ASSET_PATH / NAME
        self.assertTrue(target.exists())
        self.assertEqual(result["loader"], "Fabric")
        self.assertEqual(result["bundles"][0]["upstreamSha256"], hashlib.sha256(raw).hexdigest())
        self.assertEqual(result["bundles"][0]["mods"][1]["enabled"], False)
        with zipfile.ZipFile(target) as archive:
            self.assertEqual(archive.namelist(), ["modrinth.index.json"])

    def test_mod_download_is_verified_deduplicated_and_does_not_enable_optional_mods(self):
        raw = b"download fixture"
        selected, disabled = locked_entry("feature.jar", raw=raw), locked_entry("optional.jar", False, raw)
        lock = {"bundles": [{"mods": [selected, disabled]}, {"mods": [selected]}]}
        with patch.object(modern.urllib.request, "urlopen", return_value=io.BytesIO(raw)) as network:
            paths = modern.fetch_dependencies(lock, self.root / "mods")
        self.assertEqual(network.call_count, 1)
        self.assertEqual([p.name for p in paths], ["feature.jar"])
        self.assertEqual(paths[0].read_bytes(), raw)
        self.assertFalse((self.root / "mods/optional.jar").exists())

    def test_corrupt_download_is_not_staged(self):
        lock = {"bundles": [{"mods": [locked_entry("feature.jar")]}]}
        with patch.object(modern.urllib.request, "urlopen", return_value=io.BytesIO(b"corrupted")):
            with self.assertRaisesRegex(ValueError, "checksum"):
                modern.fetch_dependencies(lock, self.root / "mods")
        self.assertEqual(list((self.root / "mods").iterdir()), [])

    def test_corrupt_cache_is_not_silently_used_or_overwritten(self):
        destination = self.root / "mods"
        destination.mkdir()
        cached = destination / "feature.jar"
        cached.write_bytes(b"corrupted")
        lock = {"bundles": [{"mods": [locked_entry("feature.jar")]}]}
        with self.assertRaisesRegex(ValueError, "damaged"):
            modern.fetch_dependencies(lock, destination)
        self.assertEqual(cached.read_bytes(), b"corrupted")


if __name__ == "__main__":
    unittest.main()
