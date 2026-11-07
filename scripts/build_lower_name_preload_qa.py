#!/usr/bin/env python3
"""Build the opt-in prelaunch companion for the lower name-rendering QA probes."""

import argparse
import hashlib
import json
from pathlib import Path
import zipfile


CLASS = "io/github/arcadiumclient/runtime/qa/lowerpreload073/LowerNamePreLaunch073.class"
METADATA = {
    "schemaVersion": 1,
    "id": "arcadium_lower_preload_qa073",
    "version": "1.0.0",
    "environment": "client",
    "entrypoints": {
        "preLaunch": ["io.github.arcadiumclient.runtime.qa.lowerpreload073.LowerNamePreLaunch073"]
    },
    "depends": {
        "fabricloader": ">=0.19.5",
        "java": ">=25",
        "arcadium_runtime": "*",
        "arcadium_release_qa": "*",
    },
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(qa_jar, output):
    if qa_jar.resolve() == output.resolve() or output.exists():
        raise ValueError("Use a new output file distinct from the input QA archive")
    original = digest(qa_jar)
    with zipfile.ZipFile(qa_jar) as source:
        payload = source.read(CLASS)
    if not payload.startswith(b"\xca\xfe\xba\xbe") or len(payload) > 65536:
        raise ValueError("Unexpected prelaunch class")
    if b"net/minecraft/" in payload:
        raise ValueError("The prelaunch companion must not reference a Minecraft class")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED) as archive:
        for name, data in (("fabric.mod.json", json.dumps(METADATA).encode()), (CLASS, payload)):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    if digest(qa_jar) != original:
        raise RuntimeError("Input QA archive changed during generation")
    return {
        "path": str(output.resolve()),
        "sha256": digest(output),
        "bytes": output.stat().st_size,
        "sourceQaSha256": original,
        "classSha256": hashlib.sha256(payload).hexdigest(),
        "inputUnmodified": True,
        "canonicalEntrypointsChanged": False,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qa-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.qa_jar, args.output)))
