import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import struct
import zipfile


MAX_CLASS_BYTES = 16 * 1024 * 1024
DESCRIPTOR_CLASS = re.compile(r"L([A-Za-z0-9_$/]+)(?=[;<])")
GROUPS = [("launchwrapper", "net/minecraft/launchwrapper/"), ("minecraft", "net/minecraft/"),
          ("forge", "net/minecraftforge/"), ("codechicken", "codechicken/"),
          ("waila", "mcp/mobius/waila/"), ("mixin", "org/spongepowered/asm/"),
          ("asm", "org/objectweb/asm/"), ("lwjgl", "org/lwjgl/"),
          ("netty", "io/netty/"), ("guava", "com/google/common/"),
          ("java", "java/"), ("javax", "javax/"), ("jdk-internal", "sun/")]


def group(name):
    return next((label for label, prefix in GROUPS if name.startswith(prefix)), "other")


class Reader:
    def __init__(self, data):
        self.data, self.position = data, 0

    def take(self, size):
        if size < 0 or self.position + size > len(self.data):
            raise ValueError("Truncated class file")
        result = self.data[self.position:self.position + size]
        self.position += size
        return result

    def number(self, size):
        return int.from_bytes(self.take(size), "big")


def inspect_class(data):
    if len(data) > MAX_CLASS_BYTES:
        raise ValueError("Class file exceeds inventory limit")
    reader = Reader(data)
    if reader.take(4) != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid Java class magic")
    minor, major = reader.number(2), reader.number(2)
    count = reader.number(2)
    pool = [None] * count
    index = 1
    while index < count:
        tag = reader.number(1)
        if tag == 1:
            value = reader.take(reader.number(2)).decode("utf-8", errors="replace")
        elif tag in {3, 4}:
            value = struct.unpack(">i" if tag == 3 else ">f", reader.take(4))[0]
        elif tag in {5, 6}:
            value = struct.unpack(">q" if tag == 5 else ">d", reader.take(8))[0]
        elif tag in {7, 8, 16, 19, 20}:
            value = reader.number(2)
        elif tag in {9, 10, 11, 12, 17, 18}:
            value = (reader.number(2), reader.number(2))
        elif tag == 15:
            value = (reader.number(1), reader.number(2))
        else:
            raise ValueError(f"Unsupported constant-pool tag {tag}")
        pool[index] = tag, value
        index += 2 if tag in {5, 6} else 1

    def item(index, tag=None):
        if not 0 < index < len(pool) or pool[index] is None:
            raise ValueError("Invalid constant-pool index")
        value = pool[index]
        if tag is not None and value[0] != tag:
            raise ValueError("Unexpected constant-pool type")
        return value[1]

    def text(index):
        return item(index, 1)

    def class_name(index):
        return text(item(index, 7)) if index else None

    def annotation_value(source):
        tag = chr(source.number(1))
        if tag in "BCDFIJSZs":
            value = item(source.number(2))
            return bool(value) if tag == "Z" else value
        if tag == "e":
            return {"enumType": text(source.number(2)), "value": text(source.number(2))}
        if tag == "c":
            return {"class": text(source.number(2))}
        if tag == "@":
            return annotation(source)
        if tag == "[":
            return [annotation_value(source) for _ in range(source.number(2))]
        raise ValueError("Unknown annotation value tag")

    def annotation(source):
        name = text(source.number(2))
        values = {}
        for _ in range(source.number(2)):
            key = text(source.number(2))
            values[key] = annotation_value(source)
        return {"type": name, "values": values}

    def attributes(source):
        result = {"annotations": []}
        for _ in range(source.number(2)):
            name = text(source.number(2))
            payload = Reader(source.take(source.number(4)))
            if name in {"RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"}:
                result["annotations"].extend(annotation(payload) for _ in range(payload.number(2)))
                if payload.position != len(payload.data):
                    raise ValueError("Trailing annotation bytes")
            elif name == "Signature":
                result["signature"] = text(payload.number(2))
            elif name == "ConstantValue":
                constant_index = payload.number(2)
                value = item(constant_index)
                result["constant"] = text(value) if pool[constant_index][0] == 8 else value
            elif name == "AnnotationDefault":
                result["annotationDefault"] = annotation_value(payload)
        return result

    access, own, parent = reader.number(2), class_name(reader.number(2)), class_name(reader.number(2))
    interfaces = [class_name(reader.number(2)) for _ in range(reader.number(2))]

    def members():
        output = []
        for _ in range(reader.number(2)):
            flags, name, descriptor = reader.number(2), text(reader.number(2)), text(reader.number(2))
            output.append({"name": name, "descriptor": descriptor, "access": flags, **attributes(reader)})
        return output

    fields, methods = members(), members()
    attrs = attributes(reader)
    if reader.position != len(data):
        raise ValueError("Trailing Java class bytes")
    references, member_references, strings = set(), [], []
    for entry in pool[1:]:
        if not entry:
            continue
        tag, value = entry
        if tag == 7:
            name = text(value)
            references.update(DESCRIPTOR_CLASS.findall(name) if name.startswith("[") else [name])
        elif tag in {9, 10, 11}:
            owner, name_type = value
            name, descriptor = item(name_type, 12)
            member_references.append({"owner": class_name(owner), "name": text(name), "descriptor": text(descriptor),
                                      "kind": "field" if tag == 9 else "interface" if tag == 11 else "method"})
        elif tag == 12:
            references.update(DESCRIPTOR_CLASS.findall(text(value[1])))
        elif tag == 16:
            references.update(DESCRIPTOR_CLASS.findall(text(value)))
        elif tag == 8:
            strings.append(text(value))
    def annotation_references(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key in {"type", "class", "enumType"} and isinstance(child, str):
                    references.update(DESCRIPTOR_CLASS.findall(child))
                annotation_references(child)
        elif isinstance(value, list):
            for child in value:
                annotation_references(child)
    for member in fields + methods + [attrs]:
        for key in ["descriptor", "signature"]:
            references.update(DESCRIPTOR_CLASS.findall(member.get(key, "")))
        annotation_references(member.get("annotations", []))
        annotation_references(member.get("annotationDefault", {}))
    return {"name": own, "majorVersion": major, "minorVersion": minor, "access": access,
            "super": parent, "interfaces": interfaces, "fields": fields, "methods": methods, **attrs,
            "references": sorted(references), "memberReferences": member_references,
            "reflectionClassStrings": sorted({value for value in strings
                if re.fullmatch(r"(?:net\.minecraft(?:forge)?|codechicken|mcp\.mobius\.waila|org\.spongepowered)\.[\w.$]+", value)}),
            "dependencyStrings": sorted({value for value in strings
                if any(marker in value for marker in ["required-after:", "required-before:", "after:", "before:",
                                                       "CodeChickenCore", "CodeChickenLib", "Waila", "@["])}),
            "versionStrings": sorted({value for value in strings if re.fullmatch(r"(?:0\.[0-9.]+(?:[-+][\w.]+)?|[0-9]+\.[0-9]+\.[0-9]+)", value)})}


def manifest_attributes(data):
    result, current = {}, None
    for line in data.decode("utf-8", errors="replace").splitlines():
        if not line:
            break
        elif line.startswith(" ") and current:
            result[current] += line[1:]
        elif ": " in line:
            current, value = line.split(": ", 1)
            result[current] = value
    return result


def inspect_jar(path):
    path = Path(path)
    with path.open("rb") as stream:
        sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    with path.open("rb") as stream:
        sha1 = hashlib.file_digest(stream, "sha1").hexdigest()
    classes, metadata, resources = [], {}, []
    names, total = set(), 0
    with zipfile.ZipFile(path) as jar:
        for entry in jar.infolist():
            relative = PurePosixPath(entry.filename)
            if (not relative.parts or relative.is_absolute() or ".." in relative.parts or "\\" in entry.filename
                    or entry.filename in names or len(names) > 50000):
                raise ValueError("Invalid or duplicate JAR entry")
            names.add(entry.filename)
            total += entry.file_size
            if total > 1024 * 1024 * 1024 or entry.file_size > 64 * 1024 * 1024:
                raise ValueError("JAR exceeds offline inventory limits")
            if entry.is_dir():
                continue
            name = entry.filename
            if name.endswith(".class"):
                if entry.file_size > MAX_CLASS_BYTES:
                    raise ValueError("Class file exceeds inventory limit")
                classes.append({"entry": name, **inspect_class(jar.read(entry))})
            elif name.upper() == "META-INF/MANIFEST.MF":
                metadata["manifest"] = manifest_attributes(jar.read(entry))
            elif name == "mcmod.info" or "mixin" in name.lower() and name.endswith(".json"):
                try:
                    metadata[name] = json.loads(jar.read(entry).decode("utf-8-sig"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    metadata[name] = {"invalidJson": True}
            elif name.lower().endswith("_at.cfg"):
                metadata[name] = [line.strip() for line in jar.read(entry).decode().splitlines()
                                  if line.strip() and not line.lstrip().startswith("#")]
            else:
                resources.append(name)
    defined = {entry["name"] for entry in classes}
    referenced = set().union(*(set(entry["references"]) for entry in classes)) if classes else set()
    external = sorted(referenced - defined)
    member_map = {}
    for entry in classes:
        for member in entry["memberReferences"]:
            if member["owner"] in defined:
                continue
            key = member["owner"], member["name"], member["descriptor"], member["kind"]
            row = member_map.setdefault(key, {**member, "group": group(member["owner"]), "callers": []})
            if entry["name"] not in row["callers"]:
                row["callers"].append(entry["name"])
    mod_annotations, injection_points, coremod_classes, mixin_classes = [], [], [], []
    for entry in classes:
        for annotation in entry["annotations"]:
            if annotation["type"] == "Lnet/minecraftforge/fml/common/Mod;":
                mod_annotations.append({"class": entry["name"], **annotation["values"]})
            if annotation["type"] == "Lorg/spongepowered/asm/mixin/Mixin;":
                mixin_classes.append({"class": entry["name"], **annotation["values"]})
        for kind in ["fields", "methods"]:
            for member in entry[kind]:
                for annotation in member["annotations"]:
                    if annotation["type"].startswith(("Lnet/minecraftforge/", "Lorg/spongepowered/asm/mixin/")):
                        injection_points.append({"class": entry["name"], "member": member["name"],
                                                 "descriptor": member["descriptor"], "kind": kind,
                                                 "annotation": annotation})
        if any(name.endswith(("IFMLLoadingPlugin", "IClassTransformer", "IClassNameTransformer")) for name in entry["interfaces"]):
            coremod_classes.append({key: entry[key] for key in ["name", "super", "interfaces", "annotations", "methods"]})
    counts = Counter("/".join(name.split("/")[:3]) for name in defined)
    return {"file": str(path), "bytes": path.stat().st_size, "sha256": sha256, "sha1": sha1,
            "classCount": len(classes), "classVersionCounts": dict(Counter(str(c["majorVersion"]) for c in classes)),
            "metadata": metadata, "modAnnotations": mod_annotations, "coremodClasses": coremod_classes,
            "mixinClasses": mixin_classes, "injectionPoints": injection_points,
            "bundledPackageCounts": dict(sorted(counts.items())), "externalClassReferences": external,
            "externalClassCounts": dict(Counter(group(name) for name in external)),
            "externalMemberReferences": [member_map[key] for key in sorted(member_map)],
            "resources": resources, "classes": classes}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jars", type=Path, nargs="+")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    reports = [inspect_jar(path) for path in args.jars]
    definitions = defaultdict(list)
    for report in reports:
        for entry in report["classes"]:
            definitions[entry["name"]].append(report["file"])
    output = {"format": 1, "execution": "Static ZIP/class-file parsing only; no JAR code was loaded or executed.",
              "referenceCoverage": "Constant pool and annotation inventory; reflection and reachability need separate analysis.",
              "mods": reports, "duplicateDefinitions": {name: jars for name, jars in definitions.items() if len(jars) > 1}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for report in reports:
        print(json.dumps({key: report[key] for key in ["file", "classCount", "classVersionCounts", "modAnnotations", "externalClassCounts"]}))


if __name__ == "__main__":
    main()
