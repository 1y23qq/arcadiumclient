import argparse
from collections import Counter
import hashlib
import io
import json
import lzma
from pathlib import Path, PurePosixPath
import re
import struct
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zlib


SDK_URL = ("https://swcdn.apple.com/content/downloads/24/42/002-83793-A_74JRE8GVAT/"
           "rlnkct919wgc5c0pjq986z5bb9h62uvni2/CLTools_macOSNMOS_SDK.pkg")
SDK_BYTES = 49_692_505
SDK_SHA256 = "4ebf5509e08fe6e766874f54ff33d01b6738932d4b5ec701c0f7e0a51cbcb9db"
SDK_VERSION = "12.3"
CPUS = {0x01000007: "x86_64", 0x0100000C: "arm64", 7: "i386", 12: "arm"}
DYLIB_COMMANDS = {0xC, 0x80000018, 0x8000001F, 0x20, 0x80000023}


def version_tuple(value):
    parts = tuple(int(part) for part in value.split("."))
    return parts + (0,) * (3 - len(parts))


def version_string(value):
    return f"{value >> 16}.{value >> 8 & 255}.{value & 255}"


def checked_range(data, offset, size):
    if offset < 0 or size < 0 or offset + size > len(data):
        raise ValueError("Mach-O range exceeds the file")
    return data[offset:offset + size]


def cstring(data, offset):
    if not 0 <= offset < len(data):
        raise ValueError("Invalid Mach-O string offset")
    end = data.find(b"\0", offset)
    if end < 0:
        raise ValueError("Unterminated Mach-O string")
    return data[offset:end].decode("utf-8")


def read_leb(data, position, signed=False):
    value = shift = 0
    for _ in range(10):
        byte = checked_range(data, position, 1)[0]
        position += 1
        value |= (byte & 127) << shift
        shift += 7
        if not byte & 128:
            if signed and byte & 64:
                value -= 1 << shift
            return value, position
    raise ValueError("Invalid Mach-O LEB128 value")


def binding_imports(data):
    position = ordinal = 0
    name = None
    weak = False
    while position < len(data):
        byte = data[position]
        position += 1
        opcode, immediate = byte & 240, byte & 15
        if opcode == 0:
            ordinal, name, weak = 0, None, False
        elif opcode == 0x10:
            ordinal = immediate
        elif opcode == 0x20:
            ordinal, position = read_leb(data, position)
        elif opcode == 0x30:
            ordinal = immediate - 16 if immediate else 0
        elif opcode == 0x40:
            name = cstring(data, position)
            position += len(name.encode()) + 1
            weak = bool(immediate & 1)
        elif opcode == 0x50:
            pass
        elif opcode in {0x60, 0x70, 0x80}:
            _, position = read_leb(data, position, signed=opcode == 0x60)
        elif opcode in {0x90, 0xA0, 0xB0, 0xC0}:
            if name is None:
                raise ValueError("Mach-O binding has no symbol")
            yield name, ordinal, weak
            if opcode == 0xA0:
                _, position = read_leb(data, position)
            elif opcode == 0xC0:
                _, position = read_leb(data, position)
                _, position = read_leb(data, position)
        else:
            raise ValueError(f"Unsupported Mach-O binding opcode: {opcode:#x}")


def chained_imports(data):
    if len(data) < 28:
        raise ValueError("Truncated Mach-O chained fixups")
    version, _, imports, symbols, count, format_id, symbol_format = struct.unpack_from("<7I", data)
    if version != 0 or count > 1_000_000:
        raise ValueError("Unsupported Mach-O chained fixups")
    names = checked_range(data, symbols, len(data) - symbols)
    if symbol_format == 1:
        names = zlib.decompress(names)
    elif symbol_format != 0:
        raise ValueError("Unsupported Mach-O chained symbol format")
    size = {1: 4, 2: 8, 3: 16}.get(format_id)
    if size is None:
        raise ValueError("Unsupported Mach-O chained import format")
    checked_range(data, imports, count * size)
    for index in range(count):
        if format_id == 3:
            item = struct.unpack_from("<Q", data, imports + index * size)[0]
            ordinal, weak, name_offset = item & 65535, bool(item & 65536), item >> 32
            if ordinal >= 0xFFF0:
                ordinal -= 65536
        else:
            item = struct.unpack_from("<I", data, imports + index * size)[0]
            ordinal, weak, name_offset = item & 255, bool(item & 256), item >> 9
            if ordinal >= 0xF0:
                ordinal -= 256
        yield cstring(names, name_offset), ordinal, weak


def inspect_macho(data):
    """Return metadata for every slice, including optional and required bindings."""
    if data[:4] in {b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"}:
        wide = data[:4] == b"\xca\xfe\xba\xbf"
        count = struct.unpack_from(">I", checked_range(data, 0, 8), 4)[0]
        if count > 64:
            raise ValueError("Invalid Mach-O slice count")
        results = []
        for index in range(count):
            header = checked_range(data, 8 + index * (32 if wide else 20), 32 if wide else 20)
            offset, size = struct.unpack_from(">QQ" if wide else ">II", header, 8)
            results.extend(inspect_macho(checked_range(data, offset, size)))
        return results
    formats = {b"\xcf\xfa\xed\xfe": ("<", True), b"\xce\xfa\xed\xfe": ("<", False),
               b"\xfe\xed\xfa\xcf": (">", True), b"\xfe\xed\xfa\xce": (">", False)}
    if data[:4] not in formats:
        raise ValueError("Not a Mach-O file")
    endian, wide = formats[data[:4]]
    header_size = 32 if wide else 28
    checked_range(data, 0, header_size)
    cpu, _, file_type, count, command_size, _ = struct.unpack_from(endian + "6I", data, 4)
    commands = checked_range(data, header_size, command_size)
    result = {"architecture": CPUS.get(cpu, f"cpu-{cpu:#x}"), "fileType": file_type,
              "minimumSystemVersion": None, "sdkVersion": None, "importedLibraries": [],
              "requiredImports": [], "weakImports": [], "codeSignature": False, "importSources": []}
    symbols = fixups = None
    binding_streams = []
    position = 0
    for _ in range(count):
        command, size = struct.unpack_from(endian + "II", checked_range(commands, position, 8))
        if size < 8:
            raise ValueError("Invalid Mach-O load command size")
        payload = checked_range(commands, position, size)
        if command in DYLIB_COMMANDS:
            name_offset = struct.unpack_from(endian + "I", payload, 8)[0]
            result["importedLibraries"].append({"path": cstring(payload, name_offset),
                                                "weak": command == 0x80000018,
                                                "reexport": command == 0x8000001F})
        elif command in {0x24, 0x32}:
            offset = 12 if command == 0x32 else 8
            minimum, sdk = struct.unpack_from(endian + "II", payload, offset)
            if command == 0x32 and struct.unpack_from(endian + "I", payload, 8)[0] != 1:
                raise ValueError("Mach-O slice does not target macOS")
            result["minimumSystemVersion"], result["sdkVersion"] = version_string(minimum), version_string(sdk)
        elif command == 2:
            symbols = struct.unpack_from(endian + "4I", payload, 8)
        elif command in {0x22, 0x80000022}:
            fields = struct.unpack_from(endian + "10I", payload, 8)
            binding_streams.extend((fields[2:4], fields[6:8]))
        elif command == 0x80000034:
            fixups = struct.unpack_from(endian + "II", payload, 8)
        elif command == 0x1D:
            offset, length = struct.unpack_from(endian + "II", payload, 8)
            result["codeSignature"] = length > 0 and bool(checked_range(data, offset, length))
        position += size
    if position != len(commands):
        raise ValueError("Mach-O load command count mismatch")
    imports = {}

    def add_import(name, ordinal, weak):
        key = (name, ordinal)
        imports[key] = imports.get(key, True) and weak

    if symbols:
        offset, count, string_offset, string_size = symbols
        strings = checked_range(data, string_offset, string_size)
        table = checked_range(data, offset, count * (16 if wide else 12))
        for index in range(count):
            name_offset, kind, _, flags = struct.unpack_from(endian + "IBBH", table, index * (16 if wide else 12))
            if kind & 0xE0 == 0 and kind & 0x0E == 0 and kind & 1 and name_offset:
                ordinal = flags >> 8
                ordinal = ordinal - 256 if ordinal >= 0xF0 else ordinal
                add_import(cstring(strings, name_offset), ordinal, bool(flags & 0x40))
        result["importSources"].append("symbol-table")
    if fixups:
        for item in chained_imports(checked_range(data, *fixups)):
            add_import(*item)
        result["importSources"].append("chained-fixups")
    for offset, size in binding_streams:
        if size:
            for item in binding_imports(checked_range(data, offset, size)):
                add_import(*item)
            if "dyld-bindings" not in result["importSources"]:
                result["importSources"].append("dyld-bindings")
    for (name, ordinal), weak in sorted(imports.items()):
        library = result["importedLibraries"][ordinal - 1] if 0 < ordinal <= len(result["importedLibraries"]) else None
        entry = {"name": name, "ordinal": ordinal, "library": library["path"] if library else None}
        result["weakImports" if weak or library and library["weak"] else "requiredImports"].append(entry)
    return [result]


def archive_objects(data):
    if data[:8] != b"!<arch>\n":
        raise ValueError("Invalid native static archive")
    position = 8
    long_names = b""
    while position + 60 <= len(data):
        header = checked_range(data, position, 60)
        if header[58:] != b"`\n":
            raise ValueError("Invalid static archive member")
        size = int(header[48:58])
        name = header[:16].decode().strip()
        member = checked_range(data, position + 60, size)
        position += 60 + size + size % 2
        if name.startswith("#1/"):
            length = int(name[3:])
            name, member = member[:length].rstrip(b"\0").decode(), member[length:]
        elif name == "//":
            long_names = member
            continue
        elif name.startswith("/") and name[1:].isdigit():
            start = int(name[1:])
            name = long_names[start:long_names.index(b"\n", start)].rstrip(b"/").decode()
        if name in {"/", "/SYM64/"} or name.startswith("__.SYMDEF"):
            continue
        yield name.rstrip("/"), member
    if position != len(data):
        raise ValueError("Static archive length mismatch")


def flow_values(value):
    value = value.strip()
    if not value.startswith("[") or not value.endswith("]"):
        raise ValueError("Unsupported SDK export list")
    tokens = re.findall(r"'((?:[^']|'')*)'|\"((?:[^\"\\]|\\.)*)\"|([^,\s\[\]]+)", value[1:-1])
    return [single.replace("''", "'") or double or plain for single, double, plain in tokens]


def tbd_documents(text, architecture, minimum):
    for document in re.split(r"^---[^\n]*\n", text, flags=re.MULTILINE):
        if not document.strip() or "install-name:" not in document:
            continue
        root_fields = dict(re.findall(r"^([\w-]+):[ \t]*(.*(?:\n[ ]+[^\n]*)*)", document, re.MULTILINE))
        if root_fields.get("tbd-version", "").strip() != "4":
            raise ValueError("Unsupported SDK text stub version")
        target = architecture + "-macos"
        if target not in flow_values(root_fields["targets"]):
            continue
        name = root_fields["install-name"].strip().strip("'\"")
        exports, hidden, libraries = set(), set(), set()
        for section in ["exports", "reexports", "reexported-libraries"]:
            groups = re.split(r"\n  - targets:\s*", "\n" + root_fields.get(section, ""))[1:]
            for group in groups:
                targets, _, properties = group.partition("]")
                if target not in flow_values(targets + "]"):
                    continue
                fields = dict(re.findall(r"^    ([\w-]+):[ \t]*(.*(?:\n[ ]{6,}[^\n]*)*)", properties, re.MULTILINE))
                for key, value in fields.items():
                    items = flow_values(value)
                    if key == "libraries":
                        libraries.update(items)
                    elif key in {"symbols", "weak-symbols", "weak-def-symbols", "thread-local-symbols"}:
                        for item in items:
                            directive = re.fullmatch(r"\$ld\$(hide|add)\$os([0-9.]+)\$(.+)", item)
                            if directive:
                                if version_tuple(directive[2]) == version_tuple(minimum):
                                    (hidden if directive[1] == "hide" else exports).add(directive[3])
                            elif not item.startswith("$ld$"):
                                exports.add(item)
                    elif key == "objc-classes":
                        for item in items:
                            exports.update({"_OBJC_CLASS_$_" + item, "_OBJC_METACLASS_$_" + item})
                    elif key in {"objc-ivars", "objc-eh-types"}:
                        prefix = "_OBJC_IVAR_$_" if key == "objc-ivars" else "_OBJC_EHTYPE_$_"
                        exports.update(prefix + item for item in items)
                    else:
                        raise ValueError(f"Unsupported SDK export field: {key}")
        yield name, exports - hidden, libraries


class SdkExports:
    def __init__(self, root, architecture, minimum):
        self.exports, self.reexports = {}, {}
        files = sorted(Path(root).rglob("*.tbd"))
        for path in files:
            if path.is_symlink():
                continue
            text = path.read_text(encoding="utf-8")
            if not text.startswith("---"):
                continue
            for name, symbols, libraries in tbd_documents(text, architecture, minimum):
                self.exports.setdefault(name, set()).update(symbols)
                self.reexports.setdefault(name, set()).update(libraries)
        if not self.exports:
            raise ValueError("SDK contains no matching exports")

    def contains(self, library, symbol, visited=None):
        visited = set() if visited is None else visited
        if library in visited:
            return False
        visited.add(library)
        if symbol in self.exports.get(library, set()):
            return True
        return any(self.contains(child, symbol, visited) for child in self.reexports.get(library, set()))


def sdk_payload(package):
    if len(package) != SDK_BYTES or hashlib.sha256(package).hexdigest() != SDK_SHA256:
        raise ValueError("Apple SDK package checksum mismatch")
    magic, header_size, version, compressed, uncompressed, _ = struct.unpack_from(">IHHQQI", package)
    if magic != 0x78617221 or version != 1 or uncompressed > 16 * 1024 * 1024:
        raise ValueError("Invalid Apple SDK package")
    table = ET.fromstring(zlib.decompress(checked_range(package, header_size, compressed)))
    entry = next(item for item in table.findall(".//file") if item.findtext("name") == "Payload")
    start = header_size + compressed + int(entry.findtext("data/offset"))
    payload = checked_range(package, start, int(entry.findtext("data/length")))
    if payload[:4] != b"pbzx":
        raise ValueError("Unsupported Apple SDK payload")
    position, total = 12, 0
    while position < len(payload):
        _, size = struct.unpack_from(">QQ", checked_range(payload, position, 16))
        position += 16
        chunk = checked_range(payload, position, size)
        position += size
        decoded = lzma.decompress(chunk) if chunk.startswith(b"\xfd7zXZ\x00") else chunk
        total += len(decoded)
        if total > 1024 * 1024 * 1024:
            raise ValueError("Apple SDK payload exceeds expected size")
        yield decoded


def fetch_sdk(destination):
    destination = Path(destination)
    complete = destination / "sdk-source.json"
    expected = {"version": SDK_VERSION, "url": SDK_URL, "sha256": SDK_SHA256, "bytes": SDK_BYTES}
    if complete.is_file():
        if json.loads(complete.read_text()) != expected:
            raise ValueError("SDK reference metadata mismatch")
        return destination
    destination.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(SDK_URL, timeout=120) as response:
        package = response.read(SDK_BYTES + 1)
    with tempfile.TemporaryFile() as payload:
        for chunk in sdk_payload(package):
            payload.write(chunk)
        payload.seek(0)
        count = 0
        while header := payload.read(76):
            if len(header) != 76 or header[:6] != b"070707":
                raise ValueError("Unsupported Apple SDK cpio entry")
            mode, name_size, size = int(header[18:24], 8), int(header[59:65], 8), int(header[65:76], 8)
            if not 0 < name_size < 4096 or size > 64 * 1024 * 1024:
                raise ValueError("Invalid Apple SDK cpio entry size")
            raw_name = payload.read(name_size)
            if len(raw_name) != name_size or not raw_name.endswith(b"\0"):
                raise ValueError("Invalid Apple SDK cpio entry name")
            name = raw_name[:-1].decode()
            if name == "TRAILER!!!":
                break
            relative = PurePosixPath(name.removeprefix("./"))
            if relative.is_absolute() or ".." in relative.parts or "\\" in name:
                raise ValueError("Invalid Apple SDK cpio path")
            if mode & 0o170000 == 0o100000 and relative.suffix == ".tbd":
                content = payload.read(size)
                if len(content) != size:
                    raise ValueError("Truncated Apple SDK cpio data")
                path = destination.joinpath(*relative.parts)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
                count += 1
            else:
                payload.seek(size, io.SEEK_CUR)
        if count < 1000:
            raise ValueError("Apple SDK export reference is incomplete")
    complete.write_text(json.dumps(expected, indent=2) + "\n")
    return destination


def audit_imports(metadata, sdk):
    missing = []
    libraries = metadata["importedLibraries"]
    for library in libraries:
        if not library["weak"] and library["path"] not in sdk.exports:
            missing.append({"library": library["path"], "name": None})
    for symbol in metadata["requiredImports"]:
        candidates = [symbol["library"]] if symbol["library"] else [item["path"] for item in libraries]
        if not any(sdk.contains(library, symbol["name"]) for library in candidates):
            missing.append(symbol)
    return missing


def audit_static_archives(root, architecture, minimum):
    archives, objects, minimums = [], 0, Counter()
    for path in sorted(Path(root).rglob("*.a")):
        if "build" not in path.parts or "out" not in path.parts:
            continue
        count = 0
        for name, member in archive_objects(path.read_bytes()):
            metadata = inspect_macho(member)
            matching = [item for item in metadata if item["architecture"] == architecture]
            if not matching:
                raise ValueError(f"Native archive contains a different architecture: {path.name}:{name}")
            for item in matching:
                value = item["minimumSystemVersion"]
                if value is None or version_tuple(value) > version_tuple(minimum):
                    raise ValueError(f"Incompatible native object: {path.name}:{name} minimum={value}")
                minimums[value] += 1
                count += 1
        archives.append({"path": str(path), "objects": count, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        objects += count
    if not any(Path(item["path"]).name == "libskia.a" and item["objects"] > 1000 for item in archives):
        raise ValueError("The consumed Skia static archive was not inspected")
    return {"archives": archives, "objects": objects, "minimumSystemVersions": dict(minimums)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--target", choices=["aarch64-apple-darwin", "x86_64-apple-darwin"], required=True)
    parser.add_argument("--minimum", required=True)
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--fetch-sdk", action="store_true")
    parser.add_argument("--static-root", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    architecture = "arm64" if args.target == "aarch64-apple-darwin" else "x86_64"
    sdk_path = args.sdk
    if args.fetch_sdk:
        sdk_path = fetch_sdk(sdk_path or Path("build/macos-sdk-12.3-reference"))
    if sdk_path is None:
        parser.error("--sdk or --fetch-sdk is required")
    sdk = SdkExports(sdk_path, architecture, args.minimum)
    slices = inspect_macho(args.binary.read_bytes())
    matching = [item for item in slices if item["architecture"] == architecture]
    if not matching:
        raise ValueError("Executable architecture mismatch")
    failures = []
    for item in matching:
        if (not item["minimumSystemVersion"]
                or version_tuple(item["minimumSystemVersion"]) > version_tuple(args.minimum)):
            failures.append({"minimumSystemVersion": item["minimumSystemVersion"]})
        failures.extend(audit_imports(item, sdk))
    statics = audit_static_archives(args.static_root, architecture, args.minimum) if args.static_root else None
    result = {"passed": not failures, "target": args.target, "minimumSystemVersion": args.minimum,
              "sdkExportBaseline": SDK_VERSION, "sdkSource": SDK_URL, "sdkPackageSha256": SDK_SHA256,
              "binarySha256": hashlib.sha256(args.binary.read_bytes()).hexdigest(), "slices": slices,
              "staticArchives": statics, "missingImports": failures,
              "runtimeValidation": "Not run on macOS 12; SDK export and object deployment checks only.",
              "dynamicObjectiveCSelectors": "Require source availability guards; absent from dyld imports."}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"macOS compatibility audit: {len(matching)} slice(s), "
          f"{sum(len(item['requiredImports']) for item in matching)} required imports, {len(failures)} failure(s)")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
