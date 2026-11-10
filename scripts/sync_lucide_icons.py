from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "3fef7aca71a50acc54bd2f24f0cc4747c22aa6d2"
UPSTREAM = f"https://raw.githubusercontent.com/lucide-icons/lucide/{COMMIT}"
UI = Path("client-ui/modules/ui/src/main/resources")
CANONICAL = UI / "assets/arcadium/lucide"
MANIFEST = Path("licenses/assets/client-lucide.json")

CONTROLS = {
    "alert-circle": "circle-exclamation-point", "align-center": "text-align-center", "align-left": "text-align-start",
    "align-right": "text-align-end", "align": "align-horizontal-distribute-center", "bell": "bell",
    "bold": "letter-b", "capitalized": "case-sensitive", "chat": "messages-square",
    "check-circle": "circle-check", "clipboard": "clipboard", "close": "x", "cloud": "cloud",
    "cog": "settings", "combat": "swords", "console": "terminal", "copy-plus": "copy-plus",
    "copy": "copy", "dots-grid": "grip", "dots-vertical": "ellipsis-vertical",
    "down": "chevron-down", "eye-off": "eye-off", "eye": "eye", "folder": "folder",
    "hud": "panels-top-left", "info": "info", "italic": "italic", "keyboard": "keyboard",
    "layers": "layers", "left-arrow": "arrow-left", "lightning-01": "zap", "lock": "lock",
    "lowercase": "case-lower", "maximise": "maximize", "minus": "minus", "moon": "moon",
    "move": "move", "optimizations": "gauge", "pad-bottom": "align-vertical-justify-end",
    "pad-left": "align-horizontal-justify-start", "pad-right": "align-horizontal-justify-end",
    "pad-top": "align-vertical-justify-start", "paintbrush": "paintbrush", "plus": "plus",
    "preferences": "sliders-horizontal", "profiles": "users-round", "qol": "wrench",
    "redo": "redo-2", "refresh": "refresh-cw", "right-arrow": "arrow-right", "scissors": "scissors",
    "search": "search", "select-all": "square-dashed-mouse-pointer", "settings": "sliders-horizontal",
    "shuffle": "shuffle", "spanner": "wrench", "star-filled": "star", "star": "star",
    "strikethrough": "strikethrough", "sun": "sun", "text-input": "text-cursor-input",
    "text": "type", "tick": "check", "trash": "trash", "underline": "underline",
    "undo": "undo-2", "unlock": "lock-open", "up": "chevron-up", "uppercase": "case-upper",
    "x-circle": "circle-x",
}
LAUNCHER = {
    "alert-circle": "circle-exclamation-point", "alert-triangle": "triangle-exclamation-point", "announcement-01": "megaphone",
    "arrow-left": "arrow-left", "arrow-right": "arrow-right", "bar-chart-square-02": "chart-column",
    "bell-01": "bell", "brush-01": "paintbrush", "calendar": "calendar", "check-circle": "circle-check",
    "check": "check", "chevron-down": "chevron-down", "chevron-right": "chevron-right",
    "chevrons-left": "chevrons-left", "chevrons-right": "chevrons-right", "clipboard-check": "clipboard-check",
    "clock-rewind": "clock-arrow-left", "code-snippet-02": "code", "colors": "palette", "copy-01": "copy",
    "database-01": "database", "dots-grid": "grid-3x3", "dots-vertical": "ellipsis-vertical",
    "download-01": "download", "download-cloud-02": "cloud-download", "eye": "eye", "file-02": "file",
    "file-plus-02": "file-plus-corner", "file-x-02": "file-x-corner", "folder-check": "folder-check",
    "folder-download": "folder-down", "folder": "folder", "globe-01": "globe", "help-circle": "circle-question-mark",
    "info-circle": "info", "key-01": "key-round", "layout-top": "panels-top-left",
    "line-chart-up-01": "chart-no-axes-combined", "link-03": "link", "link-external-01": "external-link",
    "loading-02": "loader-circle", "log-out-01": "log-out", "maximize-01": "maximize",
    "message-text-square-01": "message-square-text", "minus": "minus", "moon-01": "moon",
    "onboarding-account": "user-round", "onboarding-complete": "circle-check", "onboarding-language": "languages",
    "onboarding-preferences": "sliders-horizontal", "onboarding-welcome": "hand", "paint-pour": "paint-bucket",
    "paragraph-wrap": "text-wrap", "pencil-01": "pencil", "play": "play", "plus": "plus",
    "refresh-ccw-02": "refresh-ccw", "refresh-cw-01": "refresh-cw", "rocket-02": "rocket",
    "search-md": "search", "settings-01": "settings", "settings-02": "settings-2", "settings-04": "settings",
    "sliders-04": "sliders-horizontal", "square": "square", "terminal": "terminal", "trash-01": "trash",
    "users-01": "users-round", "x-close": "x", "x": "x",
}
MODULES = {
    "3d-skin": "box", "animations": "clapperboard", "auto-text": "text-cursor-input",
    "bedwars-overlay": "bed-double", "cape": "flag", "chat-filters": "list-filter",
    "custom-crosshair": "crosshair", "crosshairs": "crosshair", "custom-f3": "square-terminal",
    "cute-visuals": "sparkles", "gui-scale": "scaling", "health-hud": "heart", "health": "heart",
    "height-limit-hud": "ruler", "height-limit": "ruler", "kill-effects": "flame", "level-head": "chart-column-increasing",
    "light-level": "lightbulb", "menus": "panels-top-left", "nick-hider": "venetian-mask",
    "no-hit-delay": "mouse-pointer-click", "no-render": "eye-off", "optimizations-beta": "gauge",
    "password-hider": "key-round", "rod-thickness": "fishing-hook", "star-hider": "star-off",
    "tab-background": "table-properties", "toggle-sprint-hud": "footprints", "zoom-keys": "zoom-in",
    "glintcolorizer": "paintbrush", "tipper": "hand-coins", "hychatter": "messages-square",
    "hylobby": "door-open", "smoothscrollingrefurbished": "mouse", "polyweather": "cloud-sun",
    "polytime": "clock", "polyhitbox": "scan", "overflowparticles": "sparkles", "chatting": "messages-square",
    "smoothskies": "cloud-sun", "shaketweaks": "vibrate", "scrolltweaks": "mouse", "sciophobia": "sun",
    "rendertweaksv2": "monitor-cog", "overlaytweaks": "panels-top-left", "numericalenchantments": "book-open",
    "mountopacity": "eye", "blur": "blend", "droppeditemtweaksv2": "package", "confirmdisconnect": "unplug",
    "custom-block-highlight": "box", "betterscreens": "panels-top-left", "betternightvision": "moon-star",
}
HUDS = {
    "fps": "gauge", "cps": "mouse-pointer-click", "keystrokes": "keyboard", "ping": "wifi",
    "coordinates": "map-pin", "coords": "map-pin", "armorstatus": "shield", "armor": "shield",
    "itemcounter": "boxes", "blockcounter": "boxes", "potioneffects": "flask-conical",
    "potions": "flask-conical", "direction": "compass", "compass": "compass", "clock": "clock",
    "time": "clock", "memory": "memory-stick", "combo": "swords", "speed": "gauge",
    "reach": "ruler", "day": "sun", "biome": "trees", "blockinfo": "box", "hotbar": "rows-3",
    "scoreboard": "list-ordered", "serveraddress": "server", "serverip": "server", "actionbar": "type",
    "bossbar": "activity", "resourcedisplay": "boxes", "bedwarsresources": "boxes", "weather": "cloud-sun",
    "health": "heart", "heightlimit": "ruler", "togglesprint": "footprints", "lightlevel": "lightbulb",
}


def aliases():
    result = {}
    for path in (ROOT / UI / "assets/oneconfig/ico").rglob("*.svg"):
        if path.stem in CONTROLS:
            result[path.relative_to(ROOT).as_posix()] = {"icon": CONTROLS[path.stem], "filled": path.stem == "star-filled"}
    for name, icon in {**CONTROLS, "singleplayer": "gamepad-2", "multiplayer": "globe"}.items():
        result[(UI / f"assets/oneconfig/ico/{name}.svg").as_posix()] = {"icon": icon, "filled": name == "star-filled"}
    for name, icon in LAUNCHER.items():
        result[f"packages/arcadium_app/assets/icons/{name}.svg"] = {"icon": icon, "filled": False}
    for name, icon in {"user": "user-round", "shirt": "shirt", "cape": "flag"}.items():
        result[f"client-runtime/src/main/resources/assets/arcadium/icons/{name}.svg"] = {"icon": icon, "filled": False}
    result["client-ui/modules/internal/src/main/resources/assets/oneconfig/ico/default_mod.svg"] = {"icon": "puzzle", "filled": False}
    return result


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate_svg(data):
    document = ET.fromstring(data)
    assert document.attrib["viewBox"] == "0 0 24 24"
    for node in document.iter():
        assert node.tag.rsplit("}", 1)[-1] in {"svg", "path", "circle", "line", "rect", "ellipse", "polygon", "polyline"}
        assert not any(key.startswith("on") or key in {"href", "style"} for key in node.attrib)


def sync():
    paths = aliases()
    names = sorted({row["icon"] for row in paths.values()} | set(MODULES.values()) | set(HUDS.values()))
    folder = ROOT / CANONICAL
    folder.mkdir(parents=True, exist_ok=True)

    def fetch(name):
        path = folder / (name + ".svg")
        with urlopen(Request(f"{UPSTREAM}/icons/{name}.svg", headers={"User-Agent": "ArcadiumLucideImporter/1.0"}), timeout=30) as response:
            data = response.read()
        validate_svg(data)
        path.write_bytes(data)
        return name, {"sha256": digest(data), "bytes": len(data), "url": f"{UPSTREAM}/icons/{name}.svg"}

    with ThreadPoolExecutor(max_workers=6) as executor:
        icons = dict(executor.map(fetch, names))
    with urlopen(f"{UPSTREAM}/LICENSE", timeout=30) as response:
        license_bytes = response.read()
    for relative in [CANONICAL / "LICENSE.txt", UI / "META-INF/licenses/lucide/LICENSE.txt",
                     Path("packages/arcadium_app/assets/licenses/lucide/LICENSE.txt"), Path("licenses/assets/lucide/LICENSE.txt")]:
        target = ROOT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(license_bytes)
    for relative, row in paths.items():
        target = ROOT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        data = (folder / (row["icon"] + ".svg")).read_bytes()
        if row["filled"]:
            data = data.replace(b'fill="none"', b'fill="currentColor"', 1)
        target.write_bytes(data)
        row["sha256"] = digest(data)
    manifest = {"repository": "https://github.com/lucide-icons/lucide", "commit": COMMIT,
                "licenseSha256": digest(license_bytes), "canonical": CANONICAL.as_posix(),
                "icons": icons, "aliases": paths, "modules": MODULES, "huds": HUDS,
                "adaptations": "Geometry unchanged. Existing resource aliases retained. Selected star uses currentColor fill."}
    (ROOT / MANIFEST).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Imported {len(icons)} Lucide icons into {len(paths)} shared UI and launcher resource paths.")


def check():
    manifest = json.loads((ROOT / MANIFEST).read_text(encoding="utf-8"))
    assert manifest["commit"] == COMMIT
    for name, row in manifest["icons"].items():
        data = (ROOT / CANONICAL / (name + ".svg")).read_bytes()
        validate_svg(data)
        assert digest(data) == row["sha256"], name
    for name, row in manifest["aliases"].items():
        assert digest((ROOT / name).read_bytes()) == row["sha256"], name
    for path in [CANONICAL / "LICENSE.txt", UI / "META-INF/licenses/lucide/LICENSE.txt",
                 Path("packages/arcadium_app/assets/licenses/lucide/LICENSE.txt"), Path("licenses/assets/lucide/LICENSE.txt")]:
        assert digest((ROOT / path).read_bytes()) == manifest["licenseSha256"], path
    print(f"Verified {len(manifest['icons'])} canonical icons, {len(manifest['aliases'])} aliases and all license copies.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    check() if args.check else sync()
