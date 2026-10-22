#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Prepare owner-supplied cubemaps without reducing their resolution."""

import argparse
import hashlib
import json
import math
from pathlib import Path

from PIL import Image


SCENES = [
    ("aurora-village", "Aurora Village", "panorama 1"),
    ("kelp-forest", "Kelp Forest", "panorama 2"),
    ("glasswater", "Glasswater", "panorama 3"),
    ("ocean-monument", "Ocean Monument", "panorama 4"),
    ("woodland-halls", "Woodland Halls", "panorama 5"),
    ("lakeside-morning", "Lakeside Morning", "panorama 6"),
    ("canopy-sunrise", "Canopy Sunrise", "EXTRA/1"),
    ("duskfall", "Duskfall", "EXTRA/3"),
    ("crimson-depths", "Crimson Depths", "EXTRA/EXTRA/11"),
    ("soulfire-valley", "Soulfire Valley", "EXTRA/6"),
    ("emberwood", "Emberwood", "EXTRA/EXTRA/Aa"),
    ("ender-spires", "Ender Spires", "EXTRA/EXTRA/New folder (2)"),
    ("end-city", "End City", "EXTRA/EXTRA/New folder (3)"),
    ("ancient-foundry", "Ancient Foundry", "EXTRA/EXTRA/best 4"),
    ("warped-hollow", "Warped Hollow", "EXTRA/EXTRA/best 5"),
]


def preview(faces):
    width, height = 768, 432
    image = Image.new("RGB", (width, height))
    target = image.load()
    pixels = [face.load() for face in faces]
    size = faces[0].width
    horizontal = math.tan(math.radians(70) / 2) * width / height
    vertical = horizontal * height / width
    pitch = math.radians(10)
    for py in range(height):
        ry = (1 - 2 * (py + .5) / height) * vertical
        y = ry * math.cos(pitch) - math.sin(pitch)
        z = ry * math.sin(pitch) + math.cos(pitch)
        for px in range(width):
            x = (2 * (px + .5) / width - 1) * horizontal
            ax, ay, az = abs(x), abs(y), abs(z)
            if az >= ax and az >= ay:
                if z > 0:
                    face, u, v = 0, x / az, -y / az
                else:
                    face, u, v = 2, -x / az, -y / az
            elif ax >= ay:
                if x > 0:
                    face, u, v = 1, -z / ax, -y / ax
                else:
                    face, u, v = 3, z / ax, -y / ax
            elif y > 0:
                face, u, v = 4, x / ay, z / ay
            else:
                face, u, v = 5, x / ay, -z / ay
            sx = max(0, min(size - 1, int((u + 1) * .5 * size)))
            sy = max(0, min(size - 1, int((v + 1) * .5 * size)))
            target[px, py] = pixels[face][sx, sy]
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / "client-runtime/src/main/resources/assets/arcadium/panoramas"
    provenance = []
    for scene_id, name, directory in SCENES:
        folder = args.source / directory
        sources = [folder / f"panorama_{face}.png" for face in range(6)]
        faces = [Image.open(path).convert("RGB") for path in sources]
        assert all(image.size == (1024, 1024) for image in faces), directory
        destination = output / scene_id
        destination.mkdir(parents=True, exist_ok=True)
        for index, face in enumerate(faces):
            face.save(destination / f"{index}.jpg", quality=93, subsampling=0, optimize=True)
        preview(faces).save(destination / "preview.jpg", quality=90, subsampling=0, optimize=True)
        provenance.append({"id": scene_id, "name": name, "source": directory,
            "originalSha256": [hashlib.sha256(path.read_bytes()).hexdigest() for path in sources]})
        print(name, sum(p.stat().st_size for p in destination.iterdir()), flush=True)
    (output / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    print("Total bytes:", sum(p.stat().st_size for p in output.rglob("*") if p.is_file()))


if __name__ == "__main__":
    main()
