"""Register genuinely generated alternate-hand poses; only crop, scale and pack pixels.

The artist-reviewed source landmarks are recorded in attack-v4/calibration.json.
Existing idle/primary/recovery pixels remain untouched. No flips or synthetic arm edits.
"""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
from inspect_attack_components import components

ROOT = Path(__file__).resolve().parents[1]
SIZE = 512


def export():
    folder = ROOT / "art-source/heroes/attack-v4"
    config = json.loads((folder / "calibration.json").read_text(encoding="utf-8"))
    source = Image.open(ROOT / config["source"]).convert("RGBA")
    labels, parts = components(source)
    manifest_path = ROOT / "app/src/main/assets/art/heroes/attack-atlas-v3.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    report = {"provider": "OpenAI built-in image_gen", "sourceSha256": hashlib.sha256((ROOT / config["source"]).read_bytes()).hexdigest(), "heroes": {}}
    preview = Image.new("RGB", (1536, 560), "#102532")
    pen = ImageDraw.Draw(preview)
    loops = []
    for row, (hero, marks) in enumerate(config["heroes"].items()):
        entry = manifest["heroes"][hero]
        frames = []
        metrics = []
        atlas = Image.new("RGBA", (1024, 512))
        for index, mark in enumerate(marks):
            label = int(labels[mark["seed"][1], mark["seed"][0]])
            part = next(p for p in parts if p["label"] == label)
            assert part["count"] > 50000, "seed must select complete character"
            mask = Image.fromarray(np.where(labels == label, 255, 0).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))
            isolated = source.copy()
            isolated.putalpha(Image.fromarray(np.where(np.asarray(mask) > 0, np.asarray(source.getchannel("A")), 0).astype(np.uint8)))
            box = isolated.getbbox()
            crop = isolated.crop(box)
            scale = entry["bodyHeight"] / mark["scaleHeight"]
            resized = crop.resize((round(crop.width * scale), round(crop.height * scale)), Image.Resampling.LANCZOS)
            dx = round(entry["rootX"] - (mark["root"][0] - box[0]) * scale)
            dy = round(entry["rootY"] - (mark["root"][1] - box[1]) * scale)
            frame = Image.new("RGBA", (SIZE, SIZE))
            frame.alpha_composite(resized, (dx, dy))
            bounds = frame.getbbox()
            assert bounds and min(bounds[0], bounds[1], SIZE - bounds[2], SIZE - bounds[3]) >= 10, "weapons must fit without clipping"
            atlas.alpha_composite(frame, (index * SIZE, 0))
            frames.append(frame)
            metrics.append({"bounds": bounds, "scale": scale, "root": [entry["rootX"], entry["rootY"]], "sourceMark": mark})
        filename = f"{hero.replace('_', '-')}-offhand-atlas-v4.png"
        target = ROOT / "app/src/main/assets/art/heroes/atlases" / filename
        atlas.save(target)
        base = Image.open(ROOT / "app/src/main/assets/art/heroes/atlases" / entry["file"]).convert("RGBA")
        primary = [base.crop((i % 2 * SIZE, i // 2 * SIZE, i % 2 * SIZE + SIZE, i // 2 * SIZE + SIZE)) for i in range(4)]
        all_frames = primary + frames
        bounds = [f.getbbox() for f in all_frames]
        entry["alternateFile"] = filename
        entry["contentWidth"] = max(b[2] for b in bounds) - min(b[0] for b in bounds)
        for index, frame in enumerate(all_frames):
            small = frame.resize((256, 256), Image.Resampling.LANCZOS)
            preview.paste(small, (index * 256, row * 280 + 22), small)
            pen.text((index * 256 + 5, row * 280 + 5), f"{hero} / {index}", fill="#ead5a6")
            pen.line((index * 256, row * 280 + 254, index * 256 + 256, row * 280 + 254), fill="#b09c68")
        report["heroes"][hero] = {"file": filename, "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "frames": metrics}
        loops.append(all_frames)
    preview.save(folder / "offhand-registered-preview.png")
    animation = []
    order = [0, 1, 2, 3, 0, 4, 5, 3, 0]
    for index in order:
        canvas = Image.new("RGB", (512, 280), "#102532")
        for row, frames in enumerate(loops):
            small = frames[index].resize((256, 256), Image.Resampling.LANCZOS)
            canvas.paste(small, (row * 256, 22), small)
        animation.append(canvas)
    animation[0].save(folder / "alternating-hands-preview.gif", save_all=True, append_images=animation[1:],
                      duration=[250, 100, 140, 140, 250, 100, 140, 140, 400], loop=0, disposal=2)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (folder / "export-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Packed 4 generated offhand frames, 2 atlases and registered comparison/GIF previews.")


if __name__ == "__main__":
    export()
