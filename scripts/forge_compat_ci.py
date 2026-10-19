"""Prepare private, portable Forge JVM fixtures and check complete JUnit results."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
import xml.etree.ElementTree as ET
import zipfile


MAX_FILE_BYTES = 128 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024
MAX_MANIFEST_BYTES = 4 * 1024 * 1024
MAX_FILES = 2048
HASH = re.compile(r"[a-f0-9]{64}\Z")
CLASS = re.compile(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+\Z")
ROLES = {
    "official-game-and-mappings", "pinned-forge-and-fixture-dependencies",
    "supplied-private-test-mod", "managed-native-jar-bytecode-fixture",
    "hardcoded-gradle-cache-input", "historical-runtime-bytecode-fixture",
    "historical-patched-official-game-fixture", "native-annotation-fixture",
    "processed-native-bytecode-fixture", "unchanged-single-class-subset",
    "historical-remapped-forge-api-fixture", "trusted-headless-public-runtime-library",
}
FORBIDDEN = {"accounts", "cookies", "example-cookie", "credentials", "saves", "worlds", "logs"}
CLASSPATH = "${SOURCE}/build/forge-compat/fml-runtime-review/trusted-classpath.json"


class FixtureError(ValueError):
    pass


def _require(condition, message):
    if not condition:
        raise FixtureError(message)


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _json(data):
    return (json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def _fs(path):
    value = str(path)
    if os.name == "nt" and path.is_absolute() and not value.startswith("\\\\?\\"):
        return Path("\\\\?\\" + value)
    return path


def _regular(path):
    metadata = _fs(path).lstat()
    _require(stat.S_ISREG(metadata.st_mode) and not getattr(metadata, "st_file_attributes", 0) & 1024,
             "An input must be a regular file, not a link.")
    return metadata


def _read(path, limit=MAX_FILE_BYTES):
    metadata = _regular(path)
    _require(metadata.st_size <= limit, "An input exceeds its size limit.")
    with _fs(path).open("rb") as stream:
        data = stream.read(limit + 1)
    _require(len(data) <= limit, "An input grew beyond its size limit.")
    return data


def _load_json(path):
    try:
        return json.loads(_read(path, MAX_MANIFEST_BYTES))
    except (UnicodeError, json.JSONDecodeError) as failure:
        raise FixtureError("Invalid fixture JSON.") from failure


def _parts(value):
    _require(isinstance(value, str) and value and not any(ord(c) < 32 for c in value), "Invalid fixture path.")
    _require("\\" not in value and ":" not in value, "Fixture paths must use relative POSIX names.")
    parts = value.split("/")
    _require(all(part not in {"", ".", ".."} for part in parts), "Unsafe fixture path.")
    _require(not FORBIDDEN.intersection(part.casefold() for part in parts), "Private runtime data is not a fixture input.")
    return parts


def _destination(value, generated=False):
    _require(isinstance(value, str) and "/" in value, "Invalid fixture destination.")
    anchor, relative = value.split("/", 1)
    parts = _parts(relative)
    _require(anchor in {"${SOURCE}", "${HOME}"}, "Unknown fixture destination root.")
    if anchor == "${HOME}":
        _require(relative.startswith((".gradle/caches/fabric-loom/1.8.9/", ".gradle/caches/modules-2/files-2.1/")),
                 "Only explicit Gradle fixture caches may be restored under HOME.")
    else:
        _require(relative.startswith(("forge-mod-test/", "build/forge-compat/", "build/feature-qa-028/mods/", "build/download-cache/packages/mods/")),
                 "Destination is outside the explicit fixture directories.")
    _require((generated and value == CLASSPATH) or PurePosixPath(relative).suffix.lower() in {".jar", ".srg", ".tiny"},
             "Only JAR and mapping fixture inputs are supported.")
    return anchor, parts


def _integer(value, maximum=MAX_FILE_BYTES):
    _require(type(value) is int and 0 <= value <= maximum, "Invalid fixture size or count.")
    return value


def _digest(value):
    _require(isinstance(value, str) and HASH.fullmatch(value), "Invalid SHA256 digest.")
    return value


def _tests(values):
    _require(isinstance(values, list) and 0 < len(values) <= MAX_FILES, "Expected test classes are missing or excessive.")
    result = {}
    for item in values:
        name = item["class"]
        _require(isinstance(name, str) and CLASS.fullmatch(name) and name not in result, "Invalid or duplicate test class.")
        count = _integer(item["testMethods"], 10000)
        _require(count > 0, "A selected test class has no tests.")
        result[name] = count
    return result


def _class_jar(entry, content):
    _parts(entry)
    _require(entry.endswith(".class") and content.startswith(b"\xca\xfe\xba\xbe"), "Invalid unchanged class fixture.")
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as archive:
        info = zipfile.ZipInfo(entry, (1980, 1, 1, 0, 0, 0))
        info.create_system = 3
        info.external_attr = 0o100644 << 16
        archive.writestr(info, content)
    return output.getvalue()


def _entry_fingerprint(content):
    hashes = {}
    total = 0
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            _require(len(archive.infolist()) <= 20000, "Too many archive entries in a fixture alias.")
            for info in archive.infolist():
                _parts(info.filename.rstrip("/"))
                _require(info.filename not in hashes and info.file_size <= MAX_FILE_BYTES, "Invalid fixture alias archive.")
                total += info.file_size
                _require(total <= MAX_TOTAL_BYTES, "Fixture alias archive exceeds its size limit.")
                hashes[info.filename] = _sha(archive.read(info))
    except (zipfile.BadZipFile, RuntimeError) as failure:
        raise FixtureError("Invalid fixture alias archive.") from failure
    return _sha(json.dumps(hashes, sort_keys=True, separators=(",", ":")).encode())


def _payload(item):
    source = Path(item["source"])
    _require(source.suffix.lower() in {".jar", ".srg", ".tiny"} and item["role"] in ROLES,
             "Only explicit approved JAR or mapping inputs may be exported.")
    _require(not FORBIDDEN.intersection(part.casefold() for part in source.parts), "Private runtime data cannot be exported.")
    content = _read(source)
    _require(len(content) == _integer(item["sourceBytes"]) and _sha(content) == _digest(item["sourceSha256"]),
             "An audited source checksum changed.")
    if "archiveEntry" in item:
        entry = item["archiveEntry"]
        _parts(entry)
        _require(source.suffix.lower() == ".jar" and entry.endswith(".class"), "Only an explicit unchanged class entry may be extracted.")
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            matches = [info for info in archive.infolist() if info.filename == entry]
            _require(len(matches) == 1 and matches[0].file_size <= MAX_FILE_BYTES, "Audited class entry is missing or excessive.")
            content = archive.read(matches[0])
    _require(len(content) == _integer(item["payloadBytes"]) and _sha(content) == _digest(item["payloadSha256"]),
             "An audited fixture payload checksum changed.")
    return content


def _atomic(path, content):
    path = path.absolute()
    _fs(path.parent).mkdir(parents=True, exist_ok=True)
    _require(not _fs(path).is_symlink(), "Output must not be a link.")
    descriptor, temporary = tempfile.mkstemp(prefix=".forge-ci-", dir=_fs(path.parent))
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
        os.replace(temporary, _fs(path))
    finally:
        Path(temporary).unlink(missing_ok=True)


def discover_tests(directory):
    result, sources = {}, []
    for path in sorted(directory.rglob("*.java")):
        content = _read(path, 2 * 1024 * 1024)
        text = content.decode("utf-8")
        text = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*', " ", text)
        _require(not re.search(r"@(?:[\w.]+\.)?(?:Nested|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b", text),
                 "Dynamic, nested, or parameterized tests require an explicit count model.")
        count = len(re.findall(r"@(?:org\.junit\.jupiter\.api\.)?Test\b", text))
        if not count:
            continue
        package = re.search(r"\bpackage\s+([\w.]+)\s*;", text)
        _require(package is not None, "A test source has no package.")
        name = package.group(1) + "." + path.stem
        _require(name not in result and CLASS.fullmatch(name), "Duplicate or invalid test source class.")
        result[name] = count
        sources.append({"class": name, "path": path.relative_to(directory).as_posix(), "bytes": len(content), "sha256": _sha(content)})
    _require(result, "No JUnit tests were discovered in the source tree.")
    return result, sources


def export_bundle(audit_path, output, profile="full", entry_identical_aliases=False, test_sources=None):
    audit = _load_json(audit_path)
    _require(audit.get("schema") == 1 and profile in {"full", "core"}, "Unsupported audit or profile.")
    expected = _tests(audit["testClasses"])
    sources = []
    if test_sources is not None:
        expected, sources = discover_tests(test_sources)
    if profile == "core":
        command = audit["commands"]["core"]
        selected = {command[index + 1] for index, value in enumerate(command[:-1]) if value == "--tests"}
        expected = {name: count for name, count in expected.items() if name in selected}
        _require(set(expected) == selected, "Core test selection is inconsistent.")
    if test_sources is None:
        _require(sum(expected.values()) == audit["profiles"][profile]["testMethods"], "Expected test count disagrees with the audit profile.")
    selected_hashes = set(audit["profiles"][profile].get("payloadSha256", [])) if profile == "core" else None
    items = [item for item in audit["files"] if selected_hashes is None or item["payloadSha256"] in selected_hashes]
    _require(0 < len(items) <= MAX_FILES, "Fixture profile is empty or excessive.")
    _require(selected_hashes is None or {item["payloadSha256"] for item in items} == selected_hashes, "Core fixture selection is incomplete.")
    blobs, specifications, destinations = {}, {}, {}
    for item in items:
        content = _payload(item)
        digest = _sha(content)
        kind = "class-entry-jar" if "archiveEntry" in item else "file"
        staged = _class_jar(item["archiveEntry"], content) if kind == "class-entry-jar" else content
        specification = {"sha256": digest, "bytes": len(content), "kind": kind,
                         "stagedSha256": _sha(staged), "stagedBytes": len(staged), "destinations": []}
        if kind == "class-entry-jar":
            specification["archiveEntry"] = item["archiveEntry"]
        _require(digest not in specifications, "Duplicate audited payload specification.")
        for destination in item["destinations"]:
            _destination(destination)
            _require(destination not in destinations, "Duplicate fixture destination.")
            _require(kind != "class-entry-jar" or destination.endswith(".jar"), "Class fixture destination must be a JAR.")
            destinations[destination] = digest
            specification["destinations"].append(destination)
        _require(specification["destinations"], "Fixture payload has no destination.")
        blobs[digest], specifications[digest] = content, specification
        _require(sum(map(len, blobs.values())) <= MAX_TOTAL_BYTES, "Fixture bundle exceeds its size limit.")
    aliases = []
    if entry_identical_aliases:
        for approval in audit.get("optionalEntryIdenticalJarAliases", []):
            canonical = _digest(approval["canonicalPayloadSha256"])
            if canonical not in specifications:
                continue
            fingerprint = _digest(approval["entryNameAndUncompressedSha256MapFingerprint"])
            _require(specifications[canonical]["kind"] == "file" and _entry_fingerprint(blobs[canonical]) == fingerprint,
                     "Approved canonical archive contents changed.")
            for destination in approval["destinations"]:
                _destination(destination)
                _require(destination in destinations, "Approved alias is outside this fixture profile.")
                original = destinations[destination]
                _require(specifications[original]["kind"] == "file" and _entry_fingerprint(blobs[original]) == fingerprint,
                         "Approved alias archive contents changed.")
                if original == canonical:
                    continue
                specifications[original]["destinations"].remove(destination)
                specifications[canonical]["destinations"].append(destination)
                destinations[destination] = canonical
                aliases.append({"destination": destination, "originalSha256": original, "canonicalSha256": canonical,
                                "entryFingerprint": fingerprint})
    active = {digest: item for digest, item in specifications.items() if item["destinations"]}
    classpath = audit["trustedClasspathDestinations"]
    _require(len(classpath) == len(set(classpath)) and all(value in destinations for value in classpath), "Trusted classpath references missing fixture inputs.")
    manifest = {"schema": 1, "type": "arcadium-forge-ci-fixtures", "profile": profile,
                "auditSha256": _sha(_read(audit_path, MAX_MANIFEST_BYTES)),
                "tests": [{"class": name, "testMethods": count} for name, count in sorted(expected.items())],
                "testSources": [item for item in sources if item["class"] in expected],
                "files": [dict(item, destinations=sorted(item["destinations"])) for _, item in sorted(active.items())],
                "classpath": classpath, "entryIdenticalAliases": aliases}
    manifest_bytes = _json(manifest)
    _require(len(manifest_bytes) <= MAX_MANIFEST_BYTES, "Fixture manifest exceeds its size limit.")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".forge-fixtures-", dir=_fs(output.absolute().parent))
    os.close(descriptor)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as archive:
            for name, content in [("manifest.json", manifest_bytes)] + [("blobs/" + digest, blobs[digest]) for digest in sorted(active)]:
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = 0o100600 << 16
                archive.writestr(info, content)
        _require(not _fs(output).is_symlink(), "Output must not be a link.")
        os.replace(temporary, _fs(output))
    finally:
        Path(temporary).unlink(missing_ok=True)
    return {"ok": True, "operation": "export", "profile": profile, "classes": len(expected), "tests": sum(expected.values()),
            "payloads": len(active), "payloadBytes": sum(len(blobs[digest]) for digest in active),
            "aliases": len(aliases), "bundleBytes": _regular(output).st_size,
            "bundleSha256": _sha(_read(output, MAX_TOTAL_BYTES + MAX_MANIFEST_BYTES))}


def _manifest(archive):
    infos = archive.infolist()
    _require(0 < len(infos) <= MAX_FILES + 1, "Invalid fixture bundle entry count.")
    names = set()
    total = 0
    for info in infos:
        _require(info.filename not in names and (info.filename == "manifest.json" or re.fullmatch(r"blobs/[a-f0-9]{64}", info.filename)),
                 "Unexpected or duplicate fixture bundle entry.")
        _require(not info.flag_bits & 1 and stat.S_IFMT(info.external_attr >> 16) in {0, stat.S_IFREG}, "Fixture bundle links or encrypted entries are not supported.")
        _require(info.file_size <= (MAX_MANIFEST_BYTES if info.filename == "manifest.json" else MAX_FILE_BYTES), "Fixture bundle entry exceeds its size limit.")
        total += info.file_size if info.filename != "manifest.json" else 0
        names.add(info.filename)
    _require(total <= MAX_TOTAL_BYTES and "manifest.json" in names, "Fixture bundle exceeds its size limit or has no manifest.")
    try:
        manifest = json.loads(archive.read("manifest.json"))
    except (UnicodeError, json.JSONDecodeError) as failure:
        raise FixtureError("Invalid bundle manifest JSON.") from failure
    _require(manifest.get("schema") == 1 and manifest.get("type") == "arcadium-forge-ci-fixtures", "Unsupported fixture bundle.")
    _tests(manifest["tests"])
    files = manifest["files"]
    _require(isinstance(files, list) and 0 < len(files) <= MAX_FILES, "Invalid fixture file list.")
    destinations, digests = {}, set()
    for item in files:
        digest = _digest(item["sha256"])
        _require(digest not in digests and item["kind"] in {"file", "class-entry-jar"}, "Duplicate or unsupported fixture payload.")
        digests.add(digest)
        _integer(item["bytes"])
        _integer(item["stagedBytes"])
        _digest(item["stagedSha256"])
        _require("blobs/" + digest in names and archive.getinfo("blobs/" + digest).file_size == item["bytes"], "Fixture payload size disagrees with its manifest.")
        if item["kind"] == "file":
            _require(item["stagedSha256"] == digest and item["stagedBytes"] == item["bytes"], "File staging must preserve its exact bytes.")
        else:
            _parts(item["archiveEntry"])
            _require(item["archiveEntry"].endswith(".class"), "Invalid class fixture entry.")
        _require(item["destinations"], "A fixture payload has no destination.")
        for destination in item["destinations"]:
            _destination(destination)
            _require(destination not in destinations, "Duplicate fixture destination.")
            _require(item["kind"] != "class-entry-jar" or destination.endswith(".jar"), "Class fixture destination must be a JAR.")
            destinations[destination] = item
    _require(names == {"manifest.json"} | {"blobs/" + digest for digest in digests}, "Fixture bundle contains unlisted payloads.")
    _require(len(manifest["classpath"]) == len(set(manifest["classpath"])) and all(value in destinations for value in manifest["classpath"]),
             "Trusted classpath is not fully restored by this bundle.")
    for alias in manifest.get("entryIdenticalAliases", []):
        _require(alias["destination"] in destinations and destinations[alias["destination"]]["sha256"] == _digest(alias["canonicalSha256"]),
                 "An explicit archive alias disagrees with its destination.")
        _digest(alias["originalSha256"])
        _digest(alias["entryFingerprint"])
    return manifest


def _target(value, roots, generated=False):
    anchor, parts = _destination(value, generated)
    current = roots[anchor]
    for part in parts:
        current = current / part
        try:
            metadata = _fs(current).lstat()
        except FileNotFoundError:
            continue
        _require(not stat.S_ISLNK(metadata.st_mode) and not getattr(metadata, "st_file_attributes", 0) & 1024,
                 "Fixture destination contains a link or reparse point.")
        if current != roots[anchor].joinpath(*parts):
            _require(stat.S_ISDIR(metadata.st_mode), "Fixture destination parent is not a directory.")
    return current


def restore_bundle(bundle, source_root, home, verify_only=False):
    roots = {"${SOURCE}": source_root.absolute().resolve(), "${HOME}": home.absolute().resolve()}
    with zipfile.ZipFile(_fs(bundle)) as archive:
        manifest = _manifest(archive)
        for item in manifest["files"]:
            content = archive.read("blobs/" + item["sha256"])
            _require(_sha(content) == item["sha256"], "Fixture payload checksum mismatch.")
            if item["kind"] == "class-entry-jar":
                content = _class_jar(item["archiveEntry"], content)
            _require(len(content) == item["stagedBytes"] and _sha(content) == item["stagedSha256"], "Staged fixture checksum mismatch.")
            for destination in item["destinations"]:
                path = _target(destination, roots)
                if _fs(path).exists():
                    _require(_sha(_read(path)) == item["stagedSha256"], "Refusing to replace a different existing fixture file.")
                else:
                    _require(not verify_only, "A restored fixture file is missing.")
        classpath = [_target(value, roots).as_posix() for value in manifest["classpath"]]
        metadata = _target(CLASSPATH, roots, True)
        if _fs(metadata).exists():
            _require(_load_json(metadata) == classpath, "Refusing to replace a different existing classpath.")
        if verify_only:
            _require(_load_json(metadata) == classpath, "Restored trusted classpath is missing or differs.")
        else:
            for item in manifest["files"]:
                content = archive.read("blobs/" + item["sha256"])
                if item["kind"] == "class-entry-jar":
                    content = _class_jar(item["archiveEntry"], content)
                for destination in item["destinations"]:
                    path = _target(destination, roots)
                    if not _fs(path).exists():
                        _atomic(path, content)
            _atomic(metadata, _json(classpath))
    expected = _tests(manifest["tests"])
    return {"ok": True, "operation": "verify" if verify_only else "restore", "profile": manifest["profile"],
            "classes": len(expected), "tests": sum(expected.values()), "payloads": len(manifest["files"]),
            "destinations": sum(len(item["destinations"]) for item in manifest["files"]),
            "classpathEntries": len(classpath), "entryIdenticalAliases": len(manifest.get("entryIdenticalAliases", []))}


def summarize_junit(bundle, results):
    with zipfile.ZipFile(_fs(bundle)) as archive:
        manifest = _manifest(archive)
    expected = _tests(manifest["tests"])
    files = [results] if results.is_file() else sorted(results.glob("TEST-*.xml"))
    _require(0 < len(files) <= MAX_FILES, "JUnit XML reports are missing or excessive.")
    classes, seen, issues = {}, set(), []
    total_bytes = 0
    for path in files:
        content = _read(path, 16 * 1024 * 1024)
        total_bytes += len(content)
        _require(total_bytes <= 64 * 1024 * 1024, "JUnit XML reports exceed their size limit.")
        try:
            text = content.decode("utf-8-sig")
        except UnicodeError as failure:
            raise FixtureError("JUnit XML must use UTF-8.") from failure
        _require(not re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", text, re.I), "JUnit XML declarations are not supported.")
        try:
            root = ET.fromstring(text)
        except ET.ParseError as failure:
            raise FixtureError("Malformed JUnit XML.") from failure
        _require(root.tag in {"testsuite", "testsuites"}, "Unsupported JUnit XML root.")
        for suite in root.iter():
            if suite.tag not in {"testsuite", "testsuites"}:
                continue
            cases = list(suite.iter("testcase"))
            actual = {"tests": len(cases), "failures": sum(case.find("failure") is not None for case in cases),
                      "errors": sum(case.find("error") is not None for case in cases),
                      "skipped": sum(case.find("skipped") is not None for case in cases)}
            for field, value in actual.items():
                declared = suite.get(field)
                if declared is not None and (not declared.isdecimal() or int(declared) != value):
                    issues.append("inconsistent_xml_counts")
        def walk(node, suite_name=None):
            if node.tag == "testsuite":
                suite_name = node.get("name")
            if node.tag == "testcase":
                name = node.get("classname") or suite_name
                _require(isinstance(name, str) and CLASS.fullmatch(name), "JUnit test class name is invalid.")
                case_name = node.get("name")
                _require(isinstance(case_name, str) and 0 < len(case_name) <= 4096, "JUnit testcase name is missing or excessive.")
                identity = (name, case_name)
                if identity in seen:
                    issues.append("duplicate_testcase")
                seen.add(identity)
                counts = classes.setdefault(name, {"tests": 0, "failures": 0, "errors": 0, "skipped": 0})
                counts["tests"] += 1
                for field, tag in [("failures", "failure"), ("errors", "error"), ("skipped", "skipped")]:
                    counts[field] += node.find(tag) is not None
            for child in node:
                walk(child, suite_name)
        walk(root)
    totals = {field: sum(item[field] for item in classes.values()) for field in ("tests", "failures", "errors", "skipped")}
    missing = sorted(set(expected) - set(classes))
    unexpected = sorted(set(classes) - set(expected))
    counts_match = all(classes.get(name, {}).get("tests") == count for name, count in expected.items())
    checks = {"allExpectedClasses": not missing, "noUnexpectedClasses": not unexpected,
              "exactClassTestCounts": counts_match, "exactTotalTestCount": totals["tests"] == sum(expected.values()),
              "zeroFailures": totals["failures"] == 0, "zeroErrors": totals["errors"] == 0,
              "zeroSkipped": totals["skipped"] == 0, "consistentXml": not issues}
    return {"ok": all(checks.values()), "operation": "junit", "profile": manifest["profile"],
            "expectedClasses": len(expected), "executedClasses": len(classes), "expectedTests": sum(expected.values()),
            **totals, "xmlFiles": len(files), "checks": checks, "missingClasses": missing,
            "unexpectedClasses": unexpected, "issues": sorted(set(issues)), "classes": classes}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    export = commands.add_parser("export")
    export.add_argument("--audit", type=Path, required=True)
    export.add_argument("--output", type=Path, required=True)
    export.add_argument("--profile", choices=["core", "full"], default="full")
    export.add_argument("--entry-identical-aliases", action="store_true")
    export.add_argument("--test-sources", type=Path)
    export.add_argument("--summary", type=Path)
    for operation in ("restore", "verify"):
        command = commands.add_parser(operation)
        command.add_argument("--bundle", type=Path, required=True)
        command.add_argument("--source-root", type=Path, required=True)
        command.add_argument("--home", type=Path, required=True)
        command.add_argument("--summary", type=Path)
    junit = commands.add_parser("junit")
    junit.add_argument("--bundle", type=Path, required=True)
    junit.add_argument("--results", type=Path, required=True)
    junit.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "export":
            result = export_bundle(args.audit, args.output, args.profile, args.entry_identical_aliases, args.test_sources)
        elif args.command in {"restore", "verify"}:
            result = restore_bundle(args.bundle, args.source_root, args.home, args.command == "verify")
        else:
            result = summarize_junit(args.bundle, args.results)
    except (FixtureError, OSError, KeyError, TypeError, zipfile.BadZipFile, RuntimeError) as failure:
        result = {"ok": False, "operation": args.command,
                  "error": str(failure) if isinstance(failure, FixtureError) else "Fixture operation failed: " + type(failure).__name__}
    destination = args.output if args.command == "junit" else args.summary
    if destination:
        _atomic(destination, _json(result))
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
