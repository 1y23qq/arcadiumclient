"""Keep reusable compiler outputs encrypted outside the private build directory."""

import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import tarfile
import tempfile

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes


MAGIC = b"Arcadium native cache\x01"
CHUNK = 1024 * 1024
MAX_ARCHIVE = 6 * 1024 ** 3
MAX_PAYLOAD = 12 * 1024 ** 3
MAX_FILE = 1024 ** 3
MAX_FILES = 100_000


def reusable(name):
    parts = PurePosixPath(name).parts
    return bool(parts) and not any(
        part.lower() in {"incremental", "examples", "arcadium", "arcadium.exe"}
        or part.lower().startswith(("arcadium_", "arcadium-", "libarcadium_"))
        for part in parts
    )


def _identity(identity):
    return json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()


def inventory(target):
    target = Path(target)
    result = {}
    for path in target.rglob("*"):
        name = path.relative_to(target).as_posix()
        if path.is_symlink() or not path.is_file() or not reusable(name) or path.name == ".rustc_info.json":
            continue
        metadata = path.stat()
        result[name] = (metadata.st_size, metadata.st_mtime_ns)
    return result


class _EncryptedWriter:
    def __init__(self, stream, key, identity):
        nonce = os.urandom(12)
        self.stream = stream
        self.encryptor = Cipher(algorithms.AES(key), modes.GCM(nonce)).encryptor()
        self.encryptor.authenticate_additional_data(MAGIC + _identity(identity))
        self.stream.write(MAGIC + nonce)
        self.size = 0

    def write(self, data):
        self.size += len(data)
        if self.size > MAX_ARCHIVE:
            raise ValueError("Compiler cache exceeds its archive limit")
        self.stream.write(self.encryptor.update(data))
        return len(data)

    def flush(self):
        self.stream.flush()

    def finish(self):
        self.stream.write(self.encryptor.finalize())
        self.stream.write(self.encryptor.tag)


def save_cache(target, destination, key, identity):
    target, destination = Path(target).resolve(), Path(destination).resolve()
    if not target.is_dir() or len(key) != 32:
        raise ValueError("Invalid compiler cache source or key")
    if destination.is_relative_to(target):
        raise ValueError("Compiler cache must be outside its source")
    destination.parent.mkdir(parents=True, exist_ok=True)
    count = total = 0
    with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as stream:
        temporary = Path(stream.name)
        try:
            writer = _EncryptedWriter(stream, key, identity)
            with tarfile.open(mode="w|gz", fileobj=writer, compresslevel=1) as archive:
                metadata = _identity({"format": 1, "identity": identity})
                info = tarfile.TarInfo("cache.json")
                info.size = len(metadata)
                archive.addfile(info, io.BytesIO(metadata))
                for directory, folders, files in os.walk(target, followlinks=False):
                    folders[:] = sorted(name for name in folders if not (Path(directory) / name).is_symlink()
                                         and reusable((Path(directory) / name).relative_to(target).as_posix()))
                    for name in sorted(files):
                        path = Path(directory) / name
                        relative = path.relative_to(target).as_posix()
                        metadata = path.lstat()
                        if not stat.S_ISREG(metadata.st_mode) or not reusable(relative):
                            continue
                        count += 1
                        total += metadata.st_size
                        if metadata.st_size > MAX_FILE or count > MAX_FILES or total > MAX_PAYLOAD:
                            raise ValueError("Compiler cache exceeds its payload limit")
                        info = tarfile.TarInfo("target/" + relative)
                        info.size = metadata.st_size
                        info.mtime = metadata.st_mtime
                        info.mode = stat.S_IMODE(metadata.st_mode) & 0o777
                        with path.open("rb") as source:
                            archive.addfile(info, source)
            if count == 0:
                raise ValueError("No reusable compiler outputs were found")
            writer.finish()
        except BaseException:
            stream.close()
            temporary.unlink(missing_ok=True)
            raise
    try:
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return {"files": count, "bytes": total, "encryptedBytes": destination.stat().st_size}


def restore_cache(source, target, key, identity):
    source, target = Path(source).resolve(), Path(target).resolve()
    if target.exists() and any(target.iterdir()):
        raise ValueError("Compiler cache destination must be empty")
    if len(key) != 32 or source.stat().st_size > MAX_ARCHIVE + len(MAGIC) + 28:
        raise ValueError("Invalid compiler cache size or key")
    with source.open("rb") as encrypted, tempfile.TemporaryFile() as plain:
        if encrypted.read(len(MAGIC)) != MAGIC:
            raise ValueError("Invalid compiler cache format")
        nonce = encrypted.read(12)
        remaining = source.stat().st_size - len(MAGIC) - 28
        if remaining < 0 or len(nonce) != 12:
            raise ValueError("Incomplete compiler cache")
        encrypted.seek(-16, os.SEEK_END)
        tag = encrypted.read(16)
        encrypted.seek(len(MAGIC) + 12)
        decryptor = Cipher(algorithms.AES(key), modes.GCM(nonce, tag)).decryptor()
        decryptor.authenticate_additional_data(MAGIC + _identity(identity))
        while remaining:
            chunk = encrypted.read(min(remaining, CHUNK))
            if not chunk:
                raise ValueError("Incomplete compiler cache")
            plain.write(decryptor.update(chunk))
            remaining -= len(chunk)
        plain.write(decryptor.finalize())
        plain.seek(0)
        with tarfile.open(fileobj=plain, mode="r:gz") as archive:
            seen = set()
            total = 0
            members = []
            for entry in archive:
                if not entry.isfile() or entry.name in seen:
                    raise ValueError("Invalid compiler cache entry")
                seen.add(entry.name)
                if entry.name == "cache.json":
                    if len(seen) != 1 or entry.size > 4096:
                        raise ValueError("Invalid compiler cache metadata")
                    metadata = json.load(archive.extractfile(entry))
                    if metadata != {"format": 1, "identity": identity}:
                        raise ValueError("Compiler cache identity changed")
                    continue
                path = PurePosixPath(entry.name)
                parts = entry.name.split("/")
                if (not entry.name.startswith("target/") or path.as_posix() != entry.name
                        or any(part in {"", ".", ".."} for part in parts)
                        or "\\" in entry.name or ":" in entry.name
                        or any(ord(char) < 32 for char in entry.name)
                        or not reusable("/".join(parts[1:]))):
                    raise ValueError("Invalid compiler cache path")
                total += entry.size
                if entry.size > MAX_FILE or len(seen) > MAX_FILES + 1 or total > MAX_PAYLOAD:
                    raise ValueError("Compiler cache exceeds its payload limit")
                members.append(entry)
            if "cache.json" not in seen or not members:
                raise ValueError("Compiler cache metadata or payload is missing")
            created = []
            try:
                for entry in members:
                    path = target.joinpath(*PurePosixPath(entry.name).parts[1:])
                    path.parent.mkdir(parents=True, exist_ok=True)
                    with path.open("xb") as output, archive.extractfile(entry) as input_file:
                        created.append(path)
                        shutil.copyfileobj(input_file, output, CHUNK)
                    path.chmod(entry.mode & 0o777)
                    os.utime(path, (entry.mtime, entry.mtime))
            except BaseException:
                for path in reversed(created):
                    path.unlink(missing_ok=True)
                raise
    return {"files": len(members), "bytes": total}
