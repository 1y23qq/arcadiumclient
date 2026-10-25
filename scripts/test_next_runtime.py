import argparse
from contextlib import contextmanager
import json
from pathlib import Path

from test_modern_runtime import run, verified_command


@contextmanager
def renderer_fixture(profile: Path, game_directory: Path, renderer: str):
    path = game_directory.resolve() / "options.txt"
    if not path.resolve().is_relative_to(profile.resolve()):
        raise ValueError("Renderer test options are outside the synthetic profile.")
    original = path.read_bytes() if path.exists() else None
    lines = (original or b"").decode("utf-8").splitlines()
    if original is None or not original.strip():
        lines.insert(0, "version:4903")
    lines = [line for line in lines if not line.startswith("preferredGraphicsBackend:")]
    lines.append("preferredGraphicsBackend:" + json.dumps(renderer))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    try:
        yield
    finally:
        if not path.resolve().is_relative_to(profile.resolve()):
            raise ValueError("Renderer test options moved outside the synthetic profile.")
        if original is None:
            path.unlink(missing_ok=True)
        else:
            path.write_bytes(original)


def main():
    parser = argparse.ArgumentParser(description="Test the exact 26.2 candidate in a synthetic native-launcher profile.")
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--command", type=Path, required=True)
    parser.add_argument("--renderer", choices=("opengl", "vulkan"), required=True)
    parser.add_argument("--world", action="store_true")
    parser.add_argument("--world-only", action="store_true")
    parser.add_argument("--runtime-only", action="store_true")
    parser.add_argument("--fonts", action="store_true")
    parser.add_argument("--launcher", type=Path)
    args = parser.parse_args()
    _, verification = verified_command(args.profile, args.command, "26.2")
    with renderer_fixture(args.profile, Path(verification["userGameDirectory"]), args.renderer):
        world = args.world or args.world_only or args.runtime_only
        result = run(args.profile, args.command, fonts=args.fonts, world=world, world_only=args.world_only or args.runtime_only, launcher=args.launcher, target="26.2", runtime=world, runtime_only=args.runtime_only)
    observed = str(result.get("graphicsBackend", "")).lower()
    if args.renderer not in observed:
        result.update(passed=False, rendererError=f"Requested {args.renderer}; actual native backend was {observed or 'not reported'}")
        Path(result["candidate"], "run.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, ensure_ascii=True, indent=2), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
