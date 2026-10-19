import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


SOURCE_AAD = b"Arcadium private build input v1"
RESULT_AAD = b"Arcadium private build result v1"
PART_BYTES = 50 * 1024 * 1024


def read_key():
    text = sys.stdin.read().strip()
    try:
        key = bytes.fromhex(text)
    except ValueError:
        raise ValueError("Expected a 256-bit hexadecimal key on standard input") from None
    if len(key) != 32:
        raise ValueError("Expected a 256-bit hexadecimal key on standard input")
    return key


def prepare_source(archive, output, key):
    if output.exists():
        unexpected = [path.name for path in output.iterdir()
                      if not path.is_file() or not (path.name == "manifest.json"
                                                   or re.fullmatch(r"part-\d{3}\.enc", path.name))]
        if unexpected:
            raise ValueError("Encrypted input directory contains unrelated files")
    output.mkdir(parents=True, exist_ok=True)
    compressed = gzip.compress(archive.read_bytes(), compresslevel=1, mtime=0)
    nonce = os.urandom(12)
    encrypted = nonce + AESGCM(key).encrypt(nonce, compressed, SOURCE_AAD)
    if len(encrypted) > PART_BYTES * 100:
        raise ValueError("Encrypted source exceeds runner limits")
    parts = []
    for index, offset in enumerate(range(0, len(encrypted), PART_BYTES)):
        name = f"part-{index:03}.enc"
        (output / name).write_bytes(encrypted[offset:offset + PART_BYTES])
        parts.append(name)
    for path in output.glob("part-*.enc"):
        if path.name not in parts:
            path.unlink()
    (output / "manifest.json").write_text(json.dumps({
        "format": 1, "sha256": hashlib.sha256(encrypted).hexdigest(), "parts": parts,
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared encrypted input: {len(encrypted)} bytes in {len(parts)} pieces")


def decrypt_result(source, output, key):
    encrypted = source.read_bytes()
    if len(encrypted) < 28:
        raise ValueError("Encrypted result is incomplete")
    archive = AESGCM(key).decrypt(encrypted[:12], encrypted[12:], RESULT_AAD)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(archive)
    print("Decrypted result archive")


def main():
    parser = argparse.ArgumentParser(description="Encrypt private build inputs and decrypt build results.")
    commands = parser.add_subparsers(dest="command", required=True)
    source = commands.add_parser("source")
    source.add_argument("--archive", required=True, type=Path, help="Uncompressed git archive TAR.")
    source.add_argument("--output", required=True, type=Path)
    result = commands.add_parser("result")
    result.add_argument("--input", required=True, type=Path)
    result.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    key = read_key()
    if args.command == "source":
        prepare_source(args.archive.resolve(), args.output.resolve(), key)
    else:
        decrypt_result(args.input.resolve(), args.output.resolve(), key)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Encrypted build operation failed. Check the local input files and key.", file=sys.stderr)
        raise SystemExit(1) from None
