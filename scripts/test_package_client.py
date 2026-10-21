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


def jar(mod_id="example", *, files=None, authors=None, license="LGPL-3.0-only", mixins=None, minecraft=None):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": mod_id, "name": "Upstream library", "authors": authors or ["Original author"], "license": license, "mixins": mixins or [], "depends": {"minecraft": minecraft} if minecraft else {}}))
        archive.writestr("LICENSE", "Original license and copyright")
        for name, raw in (files or {}).items():
            archive.writestr(name, raw)
    return output.getvalue()


class PackagingTests(unittest.TestCase):
    def test_native_shader_closure_is_required_for_each_effect_provider(self):
        for mod_id, programs in package.NATIVE_SHADER_PROGRAMS.items():
            with self.subTest(mod_id=mod_id):
                files = {}
                for name in programs:
                    namespace = "blur" if mod_id == "blur" else "minecraft"
                    files[f"assets/{namespace}/shaders/post/{name}.json"] = json.dumps({"passes": [{"name": name}]})
                    files[f"assets/minecraft/shaders/program/{name}.json"] = json.dumps({"vertex": "sobel", "fragment": name})
                    files[f"assets/minecraft/shaders/program/{name}.fsh"] = "#version 120\nvoid main() {}"
                package.inspect_component(jar(mod_id, minecraft="1.7.10", files=files), "native.jar")
                files.pop(f"assets/minecraft/shaders/program/{programs[-1]}.fsh")
                with self.assertRaisesRegex(RuntimeError, "Missing authored native shader resource"):
                    package.inspect_component(jar(mod_id, minecraft="1.7.10", files=files), "native.jar")
                files[f"assets/minecraft/shaders/program/{programs[-1]}.fsh"] = "#version 150\nvoid main() {}"
                with self.assertRaisesRegex(RuntimeError, "must use GLSL 1.20"):
                    package.inspect_component(jar(mod_id, minecraft="1.7.10", files=files), "native.jar")

    def test_native_legacy_locale_rejects_nested_values_before_game_startup(self):
        raw = jar(minecraft="1.7.10", files={"assets/example/lang/en_us.json": '{"example":{"label":"Setting"}}'})
        with self.assertRaisesRegex(RuntimeError, "requires flat string translations"):
            package.inspect_component(raw, "native.jar")
        raw = jar(minecraft="1.7.10", files={"assets/example/lang/en_us.json": '{"example.label":"Setting"}'})
        self.assertEqual(package.inspect_component(raw, "native.jar")["id"], "example")

    def test_missing_native_mixin_config_and_class_fail_packaging(self):
        with self.assertRaisesRegex(RuntimeError, "Missing Mixin configuration"):
            package.inspect_component(jar(mixins=["native.json"]), "native.jar")
        raw = jar(mixins=["native.json"], files={"native.json": json.dumps({"package": "example.mixin", "client": ["Missing"]})})
        with self.assertRaisesRegex(RuntimeError, "Missing Mixin class.*Missing.class"):
            package.inspect_component(raw, "native.jar")

    def test_mixin_integrity_accepts_upstream_comments_trailing_commas_and_plugin(self):
        raw = jar(mixins=[{"config": "native.json", "environment": "client"}], files={
            "native.json": '{"package":"example.mixin",/*note*/"plugin":"example.Plugin","client":["Native",],"url":"https://example.invalid/x,]"}',
            "example/mixin/Native.class": b"fixture", "example/Plugin.class": b"fixture"})
        self.assertEqual(package.inspect_component(raw, "native.jar")["id"], "example")

    def test_mixin_plugin_can_be_supplied_by_the_actual_nested_dependency(self):
        raw = jar(mixins=["native.json"], files={
            "native.json": '{"package":"example.mixin","plugin":"external.Plugin","client":["Native"]}',
            "example/mixin/Native.class": b"fixture",
            "META-INF/jars/plugin.jar": jar("plugin_provider", files={"external/Plugin.class": b"fixture"})})
        self.assertEqual(package.inspect_component(raw, "native.jar")["id"], "example")

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

    def test_forge_source_inputs_exclude_the_retired_performance_engine(self):
        with patch.object(package, "legacy_ports", return_value=[]):
            legacy = package.component_sources(package.ROOT, "0.4.0", "1.7.10")
        modern = package.component_sources(package.ROOT, "0.4.0", "1.8.9")
        self.assertEqual(set(legacy), {"ArcadiumCore-1.7.10.jar", "ArcadiumFeatures-1.7.10.jar",
                                      "ArcadiumRenderer-1.7.10.jar"})
        self.assertIn("ArcadiumForgeCompat-1.8.9.jar", modern)
        self.assertEqual(legacy["ArcadiumRenderer-1.7.10.jar"][0], modern["ArcadiumRenderer-1.8.9.jar"][0])
        self.assertIn("build/1.7.10/libs/", legacy["ArcadiumFeatures-1.7.10.jar"][0].as_posix())
        self.assertNotEqual(legacy["ArcadiumCore-1.7.10.jar"][0], modern["ArcadiumCore-1.8.9.jar"][0])
        text = [" ".join(command) for command in build_client.build_commands()]
        self.assertTrue(any("client-runtime" in command and "-PminecraftVersion=1.7.10" in command for command in text))
        self.assertTrue(any("game-branding" in command and "-PminecraftVersion=1.7.10" in command for command in text))
        self.assertFalse(any("client-legacy-performance" in command and "build" in command for command in text))

    def test_legacy_packaging_preserves_current_primary_catalog_and_brand_hash(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets = root / package.ASSET_PATH
            assets.mkdir(parents=True)
            (root / "distribution").mkdir()
            (root / "client-ui/gradle").mkdir(parents=True)
            (root / "client-ui/gradle.properties").write_text("version=1.2.23-arcadium\n")
            (root / "client-ui/gradle/libs.versions.toml").write_text('[versions]\ncompose="1.13.0-alpha02"\n')
            (root / "Cargo.toml").write_text('[workspace.package]\nversion="0.4.0"\n')
            (root / "distribution/oneclient-1.8.9.lock.json").write_text('{"arcadiumComponents":[{"file":"primary.jar"}]}')
            (root / "distribution/arcadium-1.7.10.lock.json").write_text('{"bundles":[]}')
            (assets / "bundles.json").write_text('{"versions":{"1.8.9":{"ornithe":[]},"1.7.10":{"ornithe":[]}}}')
            sources = package.component_sources(root, "0.4.0", "1.7.10")
            for source, mod_id in sources.values():
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_bytes(jar(mod_id))
            brand = root / "game-branding/build/1.7.10/libs/ArcadiumBrand-0.4.0-1.7.10.jar"
            brand.parent.mkdir(parents=True)
            brand.write_bytes(jar("arcadiumclient"))
            primary = (root / "distribution/oneclient-1.8.9.lock.json").read_bytes()
            components = package.package(root, "1.7.10")
            self.assertEqual(len(components), 4)
            self.assertTrue(all("1.7.10" in component["file"] for component in components))
            self.assertFalse(any("Forge" in component["file"] for component in components))
            self.assertEqual((assets / "ArcadiumBrand-1.7.10.jar").read_bytes(), brand.read_bytes())
            catalog = json.loads((assets / "client-components.json").read_text())
            self.assertEqual(catalog["1.7.10"], components)
            self.assertEqual((root / "distribution/oneclient-1.8.9.lock.json").read_bytes(), primary)
            self.assertEqual(catalog["1.8.9"], [{"file": "primary.jar"}])

    def test_incomplete_legacy_payload_cannot_silently_ship_a_primary_only_installer(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "distribution").mkdir()
            (root / "distribution/arcadium-1.7.10.lock.json").write_text('{"arcadiumComponents":[]}')
            with self.assertRaisesRegex(RuntimeError, "1.7.10.*incomplete"):
                package.shipped_components(root)

    def test_legacy_dependency_sources_keep_original_licenses_and_no_excluded_providers(self):
        ports = package.legacy_ports()
        self.assertEqual(len(ports), 39)
        self.assertEqual(len({port["file"] for port in ports}), len(ports))
        self.assertTrue({"evergreenhud", "vanillahud", "overflowanimations", "blockoverlay", "damagetint", "polyhitbox"}.issubset({port["directory"] for port in ports}))
        self.assertFalse(package.EXCLUDED_MOD_IDS.intersection(port["modId"] for port in ports))
        text = [" ".join(command) for command in build_client.build_commands()]
        for port in ports:
            self.assertTrue(any("client-legacy-mods" in command and port["directory"] in command
                                and port["task"] in command for command in text), port["directory"])
            self.assertRegex(port["upstream"]["commit"], r"^[a-f0-9]{40}$")


if __name__ == "__main__":
    unittest.main()
