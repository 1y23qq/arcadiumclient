from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
svg = ET.parse(root / "design/branding/arcadium.svg").getroot()
size = 215
scale = size * 4 / float(svg.attrib["width"])
image = Image.new("RGBA", (size * 4, size * 4))
draw = ImageDraw.Draw(image)

for element in svg:
    tag = element.tag.rsplit("}", 1)[-1]
    if tag == "rect":
        width, height = float(element.attrib["width"]), float(element.attrib["height"])
        draw.rounded_rectangle((0, 0, width * scale, height * scale), radius=float(element.attrib.get("rx", 0)) * scale, fill=element.attrib["fill"])
    elif tag == "path":
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

output = root / "design/branding/arcadium-entra-logo.png"
image.resize((size, size), Image.Resampling.LANCZOS).save(output, optimize=True)
print(output)
