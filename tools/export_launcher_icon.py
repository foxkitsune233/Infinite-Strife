"""Package the approved portrait as Android launcher resources; no character repainting."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/art/branding/juggernaut-launcher-portrait-v1.png"
RES = ROOT / "app/src/main/res"
image = Image.open(SOURCE).convert("RGBA")
assert image.width == image.height, "Launcher portrait must be square"
portrait_dir = RES / "drawable-nodpi"
portrait_dir.mkdir(parents=True, exist_ok=True)
image.resize((512, 512), Image.Resampling.LANCZOS).save(portrait_dir / "ic_launcher_portrait.png", optimize=True)
for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    directory = RES / f"mipmap-{density}"
    directory.mkdir(parents=True, exist_ok=True)
    icon = image.resize((size, size), Image.Resampling.LANCZOS)
    icon.save(directory / "ic_launcher.png", optimize=True)
    # Android 7.1 round fallback; Android 8+ uses the system adaptive mask instead.
    mask = Image.new("L", (size * 4, size * 4))
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    icon.putalpha(mask.resize((size, size), Image.Resampling.LANCZOS))
    icon.save(directory / "ic_launcher_round.png", optimize=True)

# Simulate the Android adaptive-icon safe area for visual review, not a device screenshot.
side = 432
adaptive = Image.new("RGBA", (side, side), "#06232E")
inset = round(side * 8 / 108)
adaptive.alpha_composite(image.resize((side - 2 * inset, side - 2 * inset), Image.Resampling.LANCZOS), (inset, inset))
margin = round(side * 18 / 108)
visible = adaptive.crop((margin, margin, side - margin, side - margin))
preview = Image.new("RGB", (640, 360), "#142532")
draw = ImageDraw.Draw(preview)
for index, rounded in enumerate((False, True)):
    tile = visible.resize((240, 240), Image.Resampling.LANCZOS)
    mask = Image.new("L", (240, 240))
    pen = ImageDraw.Draw(mask)
    if rounded:
        pen.ellipse((0, 0, 239, 239), fill=255)
    else:
        pen.rounded_rectangle((0, 0, 239, 239), radius=52, fill=255)
    preview.paste(tile, (40 + index * 320, 30), mask)
    small = tile.resize((48, 48), Image.Resampling.LANCZOS)
    preview.paste(small, (136 + index * 320, 296), mask.resize((48, 48), Image.Resampling.LANCZOS))
target = ROOT / "docs/design/launcher-icon-preview-v1.png"
target.parent.mkdir(parents=True, exist_ok=True)
preview.save(target)
print("Exported launcher portrait, 10 density icons and adaptive-mask preview")
