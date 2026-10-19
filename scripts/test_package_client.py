import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_client
import package_client as package


def jar(mod_id="example", *, files=None, authors=None, license="LGPL-3.0-only"):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": mod_id, "name": "Upstream library", "authors": authors or ["Original author"], "license": license}))
        archive.writestr("LICENSE", "Original license and copyright")
        for name, raw in (files or {}).items():
            archive.writestr(name, raw)
    return output.getvalue()


class PackagingTests(unittest.TestCase):
    def test_component_inspection_preserves_license_author_and_exact_jar_bytes(self):
        raw = jar(authors=["Original author", "Another contributor"])
        metadata = package.inspect_component(raw, "library.jar", "example")
        self.assertEqual(metadata["authors"], ["Original author", "Another contributor"])
        self.assertEqual(metadata["license"], "LGPL-3.0-only")
        self.assertEqual(package.record(raw, "renamed.jar", metadata)["sha1"], hashlib.sha1(raw).hexdigest())

    def test_excluded_mod_cannot_be_hidden_by_renaming_jar(self):
        for mod_id in package.EXCLUDED_MOD_IDS:
            with self.subTest(mod_id=mod_id), self.assertRaises(RuntimeError):
                package.inspect_component(jar(mod_id), "permitted-looking.jar")

    def test_excluded_mod_cannot_be_hidden_in_nested_library(self):
        raw = jar(files={"META-INF/jars/helper.jar": jar("polyplus")})
        with self.assertRaisesRegex(RuntimeError, "Excluded component"):
            package.inspect_component(raw, "core.jar")

    def test_original_renderer_and_tipper_remain_external_bundle_dependencies(self):
        for filename in ["argentum-1.0.1.jar", "argentum-extras-1.0.1.jar", "cera-1.0.1.jar", "tipper-1.0.1+mc1.8.9.jar"]:
            self.assertFalse(package.excluded_bundle_entry({"path": "mods/" + filename}))

    def test_reserved_rights_renderer_is_not_embedded_inside_project_components(self):
        for mod_id in package.EXTERNAL_ONLY_MOD_IDS:
            with self.subTest(mod_id=mod_id), self.assertRaisesRegex(RuntimeError, "official external download"):
                package.inspect_component(jar(files={"META-INF/jars/helper.jar": jar(mod_id)}), "runtime.jar")

    def test_renaming_a_retired_bundle_artifact_does_not_bypass_hash_policy(self):
        checksum = "e234afa5498ac6a5b5a986f2d547ad3761b31b8d"
        self.assertTrue(package.excluded_bundle_entry({"path": "mods/renamed.jar", "hashes": {"sha1": checksum}}))
        self.assertTrue(package.excluded_bundle_entry({"path": "mods/renamed.jar", "sha1": checksum.upper()}))

    def test_eos_mcp_and_polyplus_bytecode_are_rejected(self):
        for name in ["net/minecraft/client/Minecraft.class", "org/polyfrost/polyplus/Main.class", "native/EOSSDK-Win64.dll", "eos_sdk.zip", "forge-runtime.jar", "forge.srg.gz", "binpatches.pack.lzma"]:
            with self.subTest(name=name), self.assertRaises(RuntimeError):
                package.inspect_component(jar(files={name: b"not distributable"}), "core.jar")

    def test_copyleft_library_is_not_relicensed_or_rebranded(self):
        raw = jar("upstream_library", authors=["Original author"], license="LGPL-3.0-only")
        self.assertEqual(package.inspect_component(raw, "library.jar", "upstream_library")["name"], "Upstream library")

    def test_download_only_forge_libraries_cannot_be_embedded_in_project_components(self):
        for name in ["META-INF/arcadium/libraries/lzma-0.0.1.jar", "META-INF/arcadium/libraries/vecmath-1.5.2.jar",
                     "LZMA/LzmaInputStream.class", "javax/vecmath/Matrix4f.class"]:
            with self.subTest(name=name), self.assertRaisesRegex(RuntimeError, "must remain external"):
                package.inspect_component(jar("arcadium_forge_compat", files={name: b"download-only fixture"}), "forge.jar")

    def test_bundle_exclusions_and_data_preservation_are_idempotent(self):
        blocked = [prefix + "1.0.jar" for prefix in package.EXCLUDED_PREFIXES + package.REPLACED_LIBRARY_PREFIXES]
        entries = [{"path": "mods/" + name, "downloads": ["https://example.invalid/" + name]} for name in blocked]
        allowed = {"path": "mods/allowed-1.0.jar", "hashes": {"sha1": "abc"}, "downloads": ["https://example.invalid/allowed.jar"]}
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            archive.writestr("modrinth.index.json", json.dumps({"name": "OneClient", "versionId": "old", "files": entries + [allowed]}))
            archive.writestr("overrides/options.txt", "existing user defaults")
        cleaned = package.clean_bundle(output.getvalue(), "0.3.6")
        self.assertEqual(cleaned, package.clean_bundle(cleaned, "0.3.6"))
        with zipfile.ZipFile(io.BytesIO(cleaned)) as archive:
            self.assertEqual(json.loads(archive.read("modrinth.index.json"))["files"], [allowed])
            self.assertEqual(archive.read("overrides/options.txt"), b"existing user defaults")

    def test_package_copies_unmodified_source_outputs_and_generates_matching_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets = root / package.ASSET_PATH
            assets.mkdir(parents=True)
            (root / "distribution").mkdir()
            (root / "packages/arcadium_common/src").mkdir()
            (root / "game-branding/build/libs").mkdir(parents=True)
            (root / "Cargo.toml").write_text('[workspace.package]\nversion="0.3.6"\n', encoding="utf-8")
            (root / "distribution/oneclient-1.8.9.lock.json").write_text('{"bundles":[]}', encoding="utf-8")
            (assets / "bundles.json").write_text('{"versions":{"1.8.9":{"ornithe":[]}}}', encoding="utf-8")
            rust = root / "packages/arcadium_common/src/distribution.rs"
            rust.write_text('pub const BRANDING_SHA1: &str = "abc";\npub const COMPONENTS: &[(&str, &str)] = &[];\n', encoding="utf-8")
            source = root / "runtime.jar"
            source.write_bytes(jar("arcadium_runtime"))
            brand = root / "game-branding/build/libs/ArcadiumBrand-0.3.6.jar"
            brand.write_bytes(jar("arcadiumclient"))
            with patch.object(package, "component_sources", return_value={"ArcadiumFeatures-1.8.9.jar": (source, "arcadium_runtime")}):
                components = package.package(root)
            self.assertEqual((assets / "ArcadiumFeatures-1.8.9.jar").read_bytes(), source.read_bytes())
            self.assertEqual((assets / "ArcadiumBrand-1.8.9.jar").read_bytes(), brand.read_bytes())
            self.assertIn(components[0]["sha1"], rust.read_text(encoding="utf-8"))
            self.assertEqual(components[0]["modId"], "arcadium_runtime")
            self.assertEqual(components[0]["authors"], ["Original author"])

    def test_each_source_build_command_uses_a_checked_in_wrapper(self):
        for command in build_client.build_commands()[:-1]:
            wrapper = Path(command[0] if command[0] != "sh" else command[1])
            self.assertTrue(wrapper.is_file(), str(wrapper))

    def test_native_build_inputs_are_generated_from_source_first(self):
        commands = build_client.build_commands()
        text = [" ".join(command) for command in commands]
        self.assertTrue(any("client-runtime" in command and "build" in command for command in text))
        self.assertFalse(any("client-renderer" in command for command in text))
        self.assertTrue(text[-1].endswith("scripts/package_client.py") or text[-1].endswith("scripts\\package_client.py"))
        self.assertFalse(any("client-features" in command for command in text))


if __name__ == "__main__":
    unittest.main()
