from pathlib import Path
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def render(size):
    svg = ET.parse(ROOT / "design/branding/arcadium.svg").getroot()
    scale = size * 4 / float(svg.attrib["width"])
    image = Image.new("RGBA", (size * 4, size * 4))
    draw = ImageDraw.Draw(image)
    for element in svg:
        kind = element.tag.rsplit("}", 1)[-1]
        if kind == "rect":
            draw.rounded_rectangle(
                (0, 0, float(element.attrib["width"]) * scale, float(element.attrib["height"]) * scale),
                radius=float(element.attrib.get("rx", 0)) * scale, fill=element.attrib["fill"],
            )
        elif kind == "path":
            tokens = re.findall(r"[A-Za-z]|-?\d+(?:\.\d+)?", element.attrib["d"])
            points, index, x, y = [], 0, 0.0, 0.0
            while index < len(tokens):
                command = tokens[index]
                index += 1
                if command in ("M", "L"):
                    x, y = float(tokens[index]), float(tokens[index + 1])
                    index += 2
                elif command == "H":
                    x = float(tokens[index])
                    index += 1
                elif command == "V":
                    y = float(tokens[index])
                    index += 1
                elif command == "Z":
                    draw.polygon(points, fill=element.attrib["fill"])
                    points = []
                    continue
                else:
                    raise ValueError(f"Unsupported SVG command: {command}")
                points.append((x * scale, y * scale))
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == "__main__":
    icons = ROOT / "packages/arcadium_app/icons"
    for size, name in [(32, "32x32.png"), (64, "64x64.png"), (128, "128x128.png"),
                       (256, "128x128@2x.png"), (512, "512x512.png"), (1024, "1024x1024.png")]:
        render(size).save(icons / name, optimize=True)
    render(1024).save(icons / "icon.icns", format="ICNS")
    print("Generated correctly sized Arcadium icons from the brand SVG.")
