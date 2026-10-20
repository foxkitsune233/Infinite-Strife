"""Slice the approved control sheet; preserve superseded files for review."""
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art-source/control-items-v1"
DEST = ROOT / "app/src/main/assets/art/items/illustrated-v1"
ids = ["skull_basher", "orchid", "heavens_halberd", "recipe_skull_basher", "recipe_orchid", "recipe_heavens_halberd",
       "eaglesong", "recipe_rod_of_atos", "recipe_daedalus"]
sheet = Image.open(SOURCE / "controls.png").convert("RGBA")
backup = SOURCE / "previous"
backup.mkdir(exist_ok=True)
contact = Image.new("RGB", (3 * 256, 3 * 280), "#082c3b")
draw = ImageDraw.Draw(contact)
for i, ident in enumerate(ids):
    x, y = i % 3, i // 3
    tile = sheet.crop((round(x * sheet.width/3), round(y * sheet.height/3),
                       round((x+1) * sheet.width/3), round((y+1) * sheet.height/3))).resize((256,256), Image.Resampling.LANCZOS)
    target = DEST / (ident + ".png")
    if target.exists() and not (backup / target.name).exists():
        shutil.copy2(target, backup / target.name)
    tile.save(target, optimize=True)
    contact.paste(tile, (x * 256, y * 280))
    draw.text((x * 256 + 4, y * 280 + 258), ident, fill="#ffe297")
contact.save(SOURCE / "contact-sheet.jpg", quality=90)
(SOURCE / "manifest.json").write_text(json.dumps({"provider":"Bailian", "model":"qwen-image-3.0",
    "source":"controls.png", "grid":[3,3], "ids":ids, "successfulImages":1,
    "publicPriceEstimateCNY":0.18, "actualBilledCNY":None}, indent=2), encoding="utf-8")
print("Exported 6 new assets and 3 corrected assets")
