from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from typing import Callable
import unicodedata


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CATALOG = ROOT / "distribution/port-checklist-1.21.11.json"
DEFAULT_RESULTS = ROOT / "build/port-checklist/1.21.11.json"
SCHEMA_VERSION = 1


class ChecklistError(ValueError):
    pass


def load_catalog(path: Path) -> dict:
    catalog = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(catalog, dict):
        raise ChecklistError("The feature catalog must be a JSON object.")
    features = catalog.get("features")
    if catalog.get("schema_version") != SCHEMA_VERSION or not isinstance(features, list) or not features:
        raise ChecklistError("Unsupported or empty feature catalog.")
    seen = set()
    for feature in features:
        if not isinstance(feature, dict):
            raise ChecklistError("Invalid feature entry.")
        for key in ("id", "category", "title", "description"):
            if not isinstance(feature.get(key), str) or not feature[key].strip():
                raise ChecklistError(f"Invalid feature {key}.")
        if feature["id"] in seen:
            raise ChecklistError(f"Duplicate feature ID: {feature['id']}")
        seen.add(feature["id"])
    for key in ("source_minecraft", "target_minecraft", "source_client", "scope"):
        if not isinstance(catalog.get(key), str) or not catalog[key]:
            raise ChecklistError(f"Missing catalog field: {key}")
    return catalog


def catalog_hash(catalog: dict) -> str:
    raw = json.dumps(catalog, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def new_state(catalog: dict) -> dict:
    return {
        "schema_version": SCHEMA_VERSION,
        "source_minecraft": catalog["source_minecraft"],
        "target_minecraft": catalog["target_minecraft"],
        "source_client": catalog["source_client"],
        "catalog_sha256": catalog_hash(catalog),
        "answers": {feature["id"]: None for feature in catalog["features"]},
        "cursor": catalog["features"][0]["id"],
        "updated_at": None,
    }


def load_state(path: Path, catalog: dict) -> dict:
    if not path.exists():
        return new_state(catalog)
    state = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(state, dict):
        raise ChecklistError("Saved results must be a JSON object.")
    expected = new_state(catalog)
    for key in ("schema_version", "source_minecraft", "target_minecraft", "source_client", "catalog_sha256"):
        if state.get(key) != expected[key]:
            raise ChecklistError(f"Saved results do not match this catalog ({key}). Use --results with another file.")
    answers = state.get("answers")
    if not isinstance(answers, dict) or answers.keys() != expected["answers"].keys():
        raise ChecklistError("Saved results contain missing or unknown feature IDs.")
    if any(value is not None and type(value) is not bool for value in answers.values()):
        raise ChecklistError("Saved choices must be true, false, or null.")
    if state.get("cursor") is not None and state["cursor"] not in answers:
        raise ChecklistError("Invalid saved cursor.")
    return state


def counts(state: dict) -> tuple[int, int, int]:
    values = state["answers"].values()
    return sum(v is True for v in values), sum(v is False for v in values), sum(v is None for v in values)


def export_text(catalog: dict, state: dict) -> str:
    yes, no, pending = counts(state)
    lines = [
        f"Arcadium {catalog['source_client']}: Minecraft {catalog['source_minecraft']} -> {catalog['target_minecraft']}",
        f"移植する: {yes} / 移植しない: {no} / 未回答: {pending}",
        f"状態: {'完了' if pending == 0 else '回答途中'}",
        f"更新: {state['updated_at'] or '未回答'}",
        "",
        catalog["scope"],
    ]
    for decision, label in ((True, "移植する"), (False, "移植しない"), (None, "未回答")):
        lines.extend(("", f"[{label}]"))
        category = None
        for feature in catalog["features"]:
            if state["answers"][feature["id"]] is not decision:
                continue
            if category != feature["category"]:
                category = feature["category"]
                lines.append(f"  {category}")
            lines.append(f"    {feature['title']} ({feature['id']})")
            lines.append(f"      {feature['description']}")
    return "\n".join(lines) + "\n"


def atomic_write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="\n", dir=path.parent,
                                         prefix=f".{path.name}.", suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def save_state(path: Path, catalog: dict, state: dict) -> None:
    state["updated_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    yes, no, pending = counts(state)
    result = dict(state)
    result["complete"] = pending == 0
    result["counts"] = {"port": yes, "do_not_port": no, "unanswered": pending}
    result["features"] = [dict(feature, port=state["answers"][feature["id"]]) for feature in catalog["features"]]
    atomic_write(path, json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    atomic_write(path.with_suffix(".txt"), export_text(catalog, state))


def read_key() -> str:
    if os.name == "nt":
        import msvcrt

        key = msvcrt.getwch()
        if key in ("\x00", "\xe0"):
            msvcrt.getwch()
            return ""
    else:
        import termios
        import tty

        fd = sys.stdin.fileno()
        previous = termios.tcgetattr(fd)
        try:
            tty.setraw(fd)
            key = sys.stdin.read(1)
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, previous)
    if key == "\x03":
        raise KeyboardInterrupt
    if key == "\x04":
        return "q"
    return key.lower()


def enable_clear_screen() -> bool:
    if not sys.stdout.isatty():
        return False
    if os.name != "nt":
        return os.environ.get("TERM") != "dumb"
    import ctypes
    from ctypes import wintypes

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.GetStdHandle.argtypes = [wintypes.DWORD]
    kernel.GetStdHandle.restype = wintypes.HANDLE
    kernel.GetConsoleMode.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel.SetConsoleMode.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    mode = wintypes.DWORD()
    handle = kernel.GetStdHandle(-11 & 0xFFFFFFFF)
    return bool(kernel.GetConsoleMode(handle, ctypes.byref(mode)) and kernel.SetConsoleMode(handle, mode.value | 4))


def wrap_line(text: str, width: int) -> list[str]:
    lines, line, length = [], "", 0
    for char in text:
        size = 0 if unicodedata.combining(char) else 2 if unicodedata.east_asian_width(char) in ("W", "F") else 1
        if char == "\n" or length + size > width:
            lines.append(line)
            line, length = "", 0
            if char == "\n":
                continue
        line += char
        length += size
    lines.append(line)
    return lines


def run_checklist(catalog: dict, state: dict, path: Path, get_key: Callable[[], str] = read_key,
                  clear: bool = False, start_id: str | None = None) -> None:
    features = catalog["features"]
    ids = [feature["id"] for feature in features]
    cursor = start_id if start_id is not None else state["cursor"]
    position = ids.index(cursor) if cursor is not None else len(features)
    details = False
    message = ""
    state["cursor"] = ids[position] if position < len(features) else None
    save_state(path, catalog, state)
    try:
        while True:
            if clear:
                print("\033[2J\033[H", end="")
            width = max(30, min(100, shutil.get_terminal_size((100, 30)).columns - 2))

            def show(text: str = "") -> None:
                print("\n".join(wrap_line(text, width)))

            yes, no, pending = counts(state)
            show(f"Arcadium | Minecraft {catalog['source_minecraft']} -> {catalog['target_minecraft']}")
            show(f"移植する {yes}   移植しない {no}   未回答 {pending} / 全{len(features)}項目")
            show("1: 移植する   2: 移植しない   Enter不要・選択すると自動で次へ")
            show("B / Backspace: 前へ   D: 詳細   Q: 保存して終了")
            show()
            if position == len(features):
                show("全項目の回答が完了しました。" if pending == 0 else "一覧の最後です。未回答の項目があります。")
                show(f"JSON: {path}")
                show(f"一覧: {path.with_suffix('.txt')}")
                show("R: 最初から見直す   B: 最後の項目に戻る   Q: 終了")
            else:
                feature = features[position]
                show(f"[{position + 1}/{len(features)}] {feature['category']}")
                show(feature["title"])
                show()
                show(feature["description"])
                show()
                choice = state["answers"][feature["id"]]
                show(f"現在: {'未回答' if choice is None else '1 移植する' if choice else '2 移植しない'}")
                if details:
                    show(f"ID: {feature['id']}")
                    for source in feature.get("sources", []):
                        show(f"参照: {source}")
                    show(catalog["scope"])
            if message:
                show(message)
                message = ""
            print("\n> ", end="", flush=True)
            key = get_key()
            print()
            if key == "q":
                break
            if key in ("b", "\b", "\x7f"):
                position = max(0, position - 1)
                details = False
            elif key == "d":
                details = not details
                continue
            elif key == "r" and position == len(features):
                position = 0
                details = False
            elif key in ("1", "2") and position < len(features):
                state["answers"][features[position]["id"]] = key == "1"
                position += 1
                details = False
            else:
                message = "1 または 2 を押してください。" if position < len(features) else "R / B / Q を押してください。"
                continue
            state["cursor"] = ids[position] if position < len(features) else None
            save_state(path, catalog, state)
    except KeyboardInterrupt:
        print()
    state["cursor"] = ids[position] if position < len(features) else None
    save_state(path, catalog, state)
    print(f"保存しました: {path}")


def main(argv: list[str] | None = None) -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="1/2キーでArcadiumの1.21.11移植対象を選択します。Enter不要。")
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG, help="機能カタログJSON")
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS, help="回答JSONの保存先。回答ごとに保存し、次回は続きから再開。")
    parser.add_argument("--list", action="store_true", help="全機能を表示して終了")
    parser.add_argument("--summary", action="store_true", help="保存済みの移植する/しない/未回答一覧を表示して終了")
    parser.add_argument("--edit", metavar="FEATURE_ID", help="指定したIDから回答を見直す")
    args = parser.parse_args(argv)
    path = args.results.resolve()
    if path.suffix.lower() != ".json":
        parser.error("--results must be a .json file.")
    if path == args.catalog.resolve():
        parser.error("Results must not overwrite the catalog.")
    try:
        catalog = load_catalog(args.catalog)
        if args.list:
            print(f"全{len(catalog['features'])}項目\n{catalog['scope']}\n")
            for feature in catalog["features"]:
                print(f"[{feature['category']}] {feature['title']} ({feature['id']})\n  {feature['description']}\n")
            return 0
        state = load_state(path, catalog)
        if args.summary:
            print(export_text(catalog, state), end="")
            return 0
        if args.edit and args.edit not in state["answers"]:
            raise ChecklistError(f"Unknown feature ID: {args.edit}")
        if not sys.stdin.isatty():
            raise ChecklistError("Open Port-Checklist.cmd in an interactive terminal. --list and --summary also work without a terminal.")
        run_checklist(catalog, state, path, clear=enable_clear_screen(), start_id=args.edit)
        return 0
    except (OSError, ValueError) as error:
        print(f"Checklist error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
