import io
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

import check_macos_compatibility as compatibility


def packed_version(value):
    major, minor, patch_version = compatibility.version_tuple(value)
    return major << 16 | minor << 8 | patch_version


def library_command(name, weak=False):
    value = name.encode() + b"\0"
    size = (24 + len(value) + 7) & ~7
    return struct.pack("<6I", 0x80000018 if weak else 0xC, size, 24, 0, 0, 0) + value + bytes(size - 24 - len(value))


def macho(minimum="12.0", cpu=0x01000007, imports=(), chained=None, bindings=None, weak_library=False):
    commands = [struct.pack("<6I", 0x32, 24, 1, packed_version(minimum), packed_version("15.5"), 0)]
    commands.append(library_command("/usr/lib/libSystem.B.dylib", weak_library))
    count = len(commands) + bool(imports) + (chained is not None) + (bindings is not None)
    payload_start = 32 + sum(map(len, commands)) + (24 if imports else 0) + (16 if chained is not None else 0) + (48 if bindings is not None else 0)
    payload = bytearray()
    if imports:
        symbols = bytearray()
        strings = bytearray(b"\0")
        for name, ordinal, weak in imports:
            symbols.extend(struct.pack("<IBBHQ", len(strings), 1, 0, ordinal << 8 | (0x40 if weak else 0), 0))
            strings.extend(name.encode() + b"\0")
        commands.append(struct.pack("<6I", 2, 24, payload_start, len(imports), payload_start + len(symbols), len(strings)))
        payload.extend(symbols + strings)
    if chained is not None:
        commands.append(struct.pack("<4I", 0x80000034, 16, payload_start + len(payload), len(chained)))
        payload.extend(chained)
    if bindings is not None:
        commands.append(struct.pack("<12I", 0x80000022, 48, 0, 0, payload_start + len(payload), len(bindings), 0, 0, 0, 0, 0, 0))
        payload.extend(bindings)
    return struct.pack("<8I", 0xFEEDFACF, cpu, 3, 2, count, sum(map(len, commands)), 0, 0) + b"".join(commands) + payload


TBD = """--- !tapi-tbd
tbd-version: 4
targets: [ x86_64-macos, arm64-macos ]
install-name: '/usr/lib/libSystem.B.dylib'
reexported-libraries:
  - targets: [ x86_64-macos ]
    libraries: [ '/usr/lib/system/libsystem_c.dylib' ]
exports:
  - targets: [ arm64-macos ]
    symbols: [ _arm_only ]
  - targets: [ x86_64-macos, arm64-macos ]
    symbols: [ _shared, '$ld$hide$os12.0$_newer' ]
    weak-symbols: [ _weak_definition ]
    objc-classes: [ NSExample ]
  - targets: [ x86_64-macos ]
    symbols: [ _newer, '_quoted,symbol' ]
--- !tapi-tbd
tbd-version: 4
targets: [ x86_64-macos ]
install-name: '/usr/lib/system/libsystem_c.dylib'
reexported-libraries:
  - targets: [ x86_64-macos ]
    libraries: [ '/usr/lib/libSystem.B.dylib' ]
exports:
  - targets: [ x86_64-macos ]
    symbols: [ _malloc ]
...
"""


def static_archive(name, content):
    name_bytes = name.encode()
    member = name_bytes + content
    header = f"{'#1/' + str(len(name_bytes)):<16}{0:<12}{0:<6}{0:<6}{'100644':<8}{len(member):<10}`\n".encode()
    return b"!<arch>\n" + header + member + (b"\n" if len(member) % 2 else b"")


class MacOSCompatibilityTests(unittest.TestCase):
    def test_reads_architecture_and_deployment_commands(self):
        result = compatibility.inspect_macho(macho())[0]
        self.assertEqual(result["architecture"], "x86_64")
        self.assertEqual(result["minimumSystemVersion"], "12.0.0")
        self.assertEqual(result["sdkVersion"], "15.5.0")

    def test_reads_universal_slices_independently(self):
        intel, arm = macho(), macho("13.0", cpu=0x0100000C)
        table = struct.pack(">II", 0xCAFEBABE, 2)
        table += struct.pack(">5I", 0x01000007, 3, 48, len(intel), 0)
        table += struct.pack(">5I", 0x0100000C, 0, 48 + len(intel), len(arm), 0)
        result = compatibility.inspect_macho(table + intel + arm)
        self.assertEqual([(item["architecture"], item["minimumSystemVersion"]) for item in result],
                         [("x86_64", "12.0.0"), ("arm64", "13.0.0")])

    def test_distinguishes_weak_and_required_imports(self):
        result = compatibility.inspect_macho(macho(imports=[("_required", 1, False), ("_optional", 1, True)]))[0]
        self.assertEqual([item["name"] for item in result["requiredImports"]], ["_required"])
        self.assertEqual([item["name"] for item in result["weakImports"]], ["_optional"])

    def test_weak_library_preserves_optional_binding(self):
        result = compatibility.inspect_macho(macho(imports=[("_optional", 1, False)], weak_library=True))[0]
        self.assertFalse(result["requiredImports"])
        self.assertEqual(result["weakImports"][0]["library"], "/usr/lib/libSystem.B.dylib")

    def test_reads_imports_missing_from_symbol_table(self):
        name = b"_hidden_binding\0"
        fixups = struct.pack("<7I", 0, 0, 28, 32, 1, 1, 0) + struct.pack("<I", 1) + name
        result = compatibility.inspect_macho(macho(chained=fixups))[0]
        self.assertEqual(result["requiredImports"][0]["name"], "_hidden_binding")
        self.assertIn("chained-fixups", result["importSources"])

    def test_reads_classic_lazy_and_direct_bindings(self):
        bindings = b"\x11\x40_required\0\x90\0\x11\x41_optional\0\x90\0"
        result = compatibility.inspect_macho(macho(bindings=bindings))[0]
        self.assertEqual(result["requiredImports"][0]["name"], "_required")
        self.assertEqual(result["weakImports"][0]["name"], "_optional")

    def test_required_binding_wins_over_duplicate_weak_symbol(self):
        fixups = struct.pack("<7I", 0, 0, 28, 32, 1, 1, 0) + struct.pack("<I", 1) + b"_same\0"
        result = compatibility.inspect_macho(macho(imports=[("_same", 1, True)], chained=fixups))[0]
        self.assertEqual(len(result["requiredImports"]), 1)
        self.assertFalse(result["weakImports"])

    def test_rejects_malformed_commands_and_truncated_imports(self):
        corrupt = bytearray(macho())
        struct.pack_into("<I", corrupt, 36, 0xFFFF)
        with self.assertRaises(ValueError):
            compatibility.inspect_macho(bytes(corrupt))
        with self.assertRaises(ValueError):
            compatibility.inspect_macho(macho(imports=[("_required", 1, False)])[:-5])

    def test_tbd_filters_target_expands_objc_and_keeps_quoted_commas(self):
        documents = list(compatibility.tbd_documents(TBD, "x86_64", "12.0"))
        name, symbols, libraries = documents[0]
        self.assertEqual(name, "/usr/lib/libSystem.B.dylib")
        self.assertNotIn("_arm_only", symbols)
        self.assertNotIn("_newer", symbols)
        self.assertIn("_quoted,symbol", symbols)
        self.assertIn("_OBJC_CLASS_$_NSExample", symbols)
        self.assertIn("_OBJC_METACLASS_$_NSExample", symbols)
        self.assertIn("/usr/lib/system/libsystem_c.dylib", libraries)

    def test_audits_every_required_symbol_and_follows_cyclic_reexports(self):
        with tempfile.TemporaryDirectory() as temporary:
            Path(temporary, "system.tbd").write_text(TBD)
            sdk = compatibility.SdkExports(temporary, "x86_64", "12.0")
            metadata = compatibility.inspect_macho(macho(imports=[("_malloc", 1, False),
                ("_unavailable", 1, False), ("_optional", 1, True)]))[0]
            self.assertEqual([item["name"] for item in compatibility.audit_imports(metadata, sdk)], ["_unavailable"])

    def test_rejects_strong_library_absent_from_old_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            Path(temporary, "system.tbd").write_text(TBD.replace("libSystem.B.dylib", "libDifferent.dylib"))
            sdk = compatibility.SdkExports(temporary, "x86_64", "12.0")
            self.assertTrue(compatibility.audit_imports(compatibility.inspect_macho(macho())[0], sdk))

    def test_static_archive_reads_long_names_and_rejects_newer_object(self):
        content = static_archive("long-member-name.o", macho("13.0"))
        self.assertEqual(list(compatibility.archive_objects(content))[0][0], "long-member-name.o")
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary, "build/skia/out/libskia.a")
            path.parent.mkdir(parents=True)
            path.write_bytes(content)
            with self.assertRaisesRegex(ValueError, "Incompatible native object"):
                compatibility.audit_static_archives(temporary, "x86_64", "12.0")

    def test_uninspected_static_library_does_not_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "Skia static archive was not inspected"):
                compatibility.audit_static_archives(temporary, "x86_64", "12.0")

    def test_sdk_package_requires_exact_pinned_digest_before_expanding(self):
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            list(compatibility.sdk_payload(b"unexpected package"))

    def test_sdk_download_does_not_execute_or_install_package(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(compatibility.urllib.request, "urlopen") as download:
            download.return_value.__enter__.return_value = io.BytesIO(b"bad package")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                compatibility.fetch_sdk(temporary)
            self.assertFalse(Path(temporary, "sdk-source.json").exists())


if __name__ == "__main__":
    unittest.main()
