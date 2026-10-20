"""Mechanical sprite-sheet slicing only; no repainting or background replacement."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art-source/independent-items-v3"
DEST = ROOT / "app/src/main/assets/art/items/illustrated-v1"
manifest = json.loads((SOURCE / "manifest.json").read_text(encoding="utf-8"))
tiles = []
for group in manifest["groups"]:
    key = "objects-01-flat" if group["key"] == "objects-01" else group["key"]
    source = SOURCE / "originals" / (key + ".png")
    sheet = Image.open(source).convert("RGBA")
    for index, (ident, description) in enumerate(group["items"]):
        x, y = index % 3, index // 3
        tile = sheet.crop((round(x * sheet.width / 3), round(y * sheet.height / 3),
                           round((x + 1) * sheet.width / 3), round((y + 1) * sheet.height / 3)))
        tile = tile.resize((256, 256), Image.Resampling.LANCZOS)
        target = DEST / (ident + ".png")
        if target.exists():
            raise RuntimeError(f"Refusing to overwrite previous artwork: {ident}")
        tile.save(target, optimize=True)
        tiles.append((ident, tile))
contact = Image.new("RGB", (9 * 128, ((len(tiles) + 8) // 9) * 151), "#082c3b")
draw = ImageDraw.Draw(contact)
for index, (ident, tile) in enumerate(tiles):
    x, y = (index % 9) * 128, (index // 9) * 151
    contact.paste(tile.resize((128, 128)), (x, y))
    draw.text((x + 2, y + 130), ident[:22], fill="#ffe297")
contact.save(SOURCE / "contact-sheet.jpg", quality=90)
print(f"Exported {len(tiles)} independent assets")
