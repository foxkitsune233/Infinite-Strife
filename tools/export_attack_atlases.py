"""Deterministic crop/registration/packing of approved artwork; never paints or invents pixels.

Usage: python tools/export_attack_atlases.py art-source/heroes/attack-v3/calibration.json
Manual landmark coordinates are in the unmodified source image, not a rescaled preview.
"""
from pathlib import Path
import hashlib
import json
import math
import sys
from PIL import Image, ImageDraw, ImageFilter
import numpy as np
from inspect_attack_components import components

ROOT = Path(__file__).resolve().parents[1]
SIZE = 512
ANCHOR = (256, 464)
MARGIN = 16


def export(calibration):
    out = ROOT / "app/src/main/assets/art/heroes/atlases"
    preview = ROOT / "art-source/heroes/attack-v3/previews"
    frames_dir = ROOT / "art-source/heroes/attack-v3/frames"
    for directory in (out, preview, frames_dir):
        directory.mkdir(parents=True, exist_ok=True)
    manifest = {"version": 3, "facing": "right", "order": ["ready", "windup", "strike", "recovery"], "heroes": {}}
    report = {}
    all_frames = []
    for hero, entry in calibration["heroes"].items():
        source_path = ROOT / entry["source"]
        source = Image.open(source_path).convert("RGBA")
        if source.getchannel("A").getextrema() != (0, 255):
            raise ValueError(f"{hero}: source must contain real transparent and opaque pixels")
        records = []
        labels, parts = components(source)
        selected = []
        for index, mark in enumerate(entry["frames"]):
            label = int(labels[mark["seed"][1], mark["seed"][0]])
            part = next((p for p in parts if p["label"] == label), None)
            if part is None or part["count"] < 50000 or label in selected:
                raise ValueError(f"{hero}/{index}: seed must select a distinct complete character")
            selected.append(label)
            ownership = Image.fromarray(np.where(labels == label, 255, 0).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))
            isolated = source.copy()
            isolated.putalpha(Image.fromarray(np.where(np.asarray(ownership) > 0, np.asarray(source.getchannel('A')), 0).astype(np.uint8)))
            b = isolated.getchannel("A").getbbox()
            box = (max(0, b[0] - 8), max(0, b[1] - 8), min(source.width, b[2] + 8), min(source.height, b[3] + 8))
            mark = dict(mark, crop=list(box))
            image = isolated.crop(box)
            bbox = image.getchannel("A").getbbox()
            if bbox is None or bbox[0] < 2 or bbox[1] < 2 or bbox[2] > image.width - 2 or bbox[3] > image.height - 2:
                raise ValueError(f"{hero}/{index}: silhouette touches crop boundary; do not silently clip")
            root = (mark["root"][0] - box[0], mark["root"][1] - box[1])
            body_height = mark.get("scaleHeight", entry.get("scaleHeight", mark["root"][1] - mark["head"][1]))
            if body_height <= 0:
                raise ValueError(f"{hero}/{index}: invalid head/foot registration")
            scale = 316 / body_height
            records.append((image, bbox, root, scale, mark, body_height))
        if len(records) != 4:
            raise ValueError(f"{hero}: exactly four poses required")
        # One common fit factor for all frames preserves the calibrated body scale.
        fit = 1.0
        for _, b, root, scale, _, _ in records:
            extents = (root[0] - b[0], b[2] - root[0], root[1] - b[1], b[3] - root[1])
            spaces = (ANCHOR[0] - MARGIN, SIZE - MARGIN - ANCHOR[0], ANCHOR[1] - MARGIN, SIZE - MARGIN - ANCHOR[1])
            for extent, space in zip(extents, spaces):
                if extent > 0:
                    fit = min(fit, space / (extent * scale))
        atlas = Image.new("RGBA", (SIZE * 2, SIZE * 2))
        output_frames = []
        metrics = []
        bounds = []
        for index, (image, _, root, scale, mark, body_height) in enumerate(records):
            scale *= fit
            resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
            # Keep the foot pivot subpixel error below half a source output pixel.
            dx, dy = round(ANCHOR[0] - root[0] * scale), round(ANCHOR[1] - root[1] * scale)
            frame = Image.new("RGBA", (SIZE, SIZE))
            frame.alpha_composite(resized, (dx, dy))
            box = frame.getchannel("A").getbbox()
            if not box or min(box[0], box[1], SIZE - box[2], SIZE - box[3]) < MARGIN - 3:
                raise ValueError(f"{hero}/{index}: exported silhouette outside safe padding")
            bounds.append(box)
            frame.save(frames_dir / f"{hero}-{index}.png")
            atlas.alpha_composite(frame, ((index % 2) * SIZE, (index // 2) * SIZE))
            output_frames.append(frame)
            weapons = []
            for weapon in mark.get("weapons", []):
                grip, guard, tip = weapon["grip"], weapon["guard"], weapon["tip"]
                weapons.append({"name": weapon["name"], "bladeToBody": math.dist(guard, tip) / body_height,
                    "grip": [round((grip[0] - mark["crop"][0]) * scale + dx, 3), round((grip[1] - mark["crop"][1]) * scale + dy, 3)]})
            metrics.append({"frame": index, "bounds": box, "scale": scale,
                "rootError": [round(dx + root[0] * scale - ANCHOR[0], 4), round(dy + root[1] * scale - ANCHOR[1], 4)], "weapons": weapons})
            if index == 2:
                origin = mark.get("attackOrigin", mark["root"])
                attack_origin = [round((origin[0] - mark["crop"][0]) * scale + dx, 3), round((origin[1] - mark["crop"][1]) * scale + dy, 3)]
        filename = f"{hero}-attack-atlas-v3.png"
        atlas.save(out / filename)
        content_width = max(b[2] for b in bounds) - min(b[0] for b in bounds)
        manifest["heroes"][entry["runtimeId"]] = {"file": filename, "frameWidth": SIZE, "frameHeight": SIZE,
            "rootX": ANCHOR[0], "rootY": ANCHOR[1], "bodyHeight": round(316 * fit, 4), "contentWidth": content_width,
            "attackOriginX": attack_origin[0], "attackOriginY": attack_origin[1],
            "weaponGrips": [f["weapons"] for f in metrics]}
        report[hero] = {"sourceSha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
            "atlasSha256": hashlib.sha256((out / filename).read_bytes()).hexdigest(), "frames": metrics}
        all_frames.append((hero, output_frames))
    proof = Image.new("RGB", (1024, len(all_frames) * 280), "#102532")
    pen = ImageDraw.Draw(proof)
    for row, (hero, frames) in enumerate(all_frames):
        for column, frame in enumerate(frames):
            small = frame.resize((256, 256), Image.Resampling.LANCZOS)
            proof.paste(small, (column * 256, row * 280 + 22), small)
            pen.line((column * 256, row * 280 + 254, (column + 1) * 256, row * 280 + 254), fill="#b09c68")
            pen.text((column * 256 + 8, row * 280 + 5), f"{hero} / {column}", fill="#ead5a6")
    proof.save(preview / "registered-frames-v3.png")
    loop_frames = []
    for index in (0, 1, 2, 3, 0):
        loop = Image.new("RGB", (768, 560), "#102532")
        pen = ImageDraw.Draw(loop)
        for n, (hero, frames) in enumerate(all_frames):
            x, y = n % 3 * 256, n // 3 * 280
            small = frames[index].resize((256, 256), Image.Resampling.LANCZOS)
            loop.paste(small, (x, y + 22), small)
            pen.line((x, y + 254, x + 256, y + 254), fill="#b09c68")
            pen.text((x + 8, y + 5), hero, fill="#ead5a6")
        loop_frames.append(loop)
    loop_frames[0].save(preview / "attack-loop-v3.gif", save_all=True, append_images=loop_frames[1:],
                        duration=[60, 84, 142, 134, 650], loop=0, disposal=2)
    target = ROOT / "app/src/main/assets/art/heroes/attack-atlas-v3.json"
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (preview.parent / "export-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(all_frames)} atlases, {len(all_frames) * 4} registered frames; manifest: {target}")


if __name__ == "__main__":
    export(json.loads((ROOT / sys.argv[1]).read_text(encoding="utf-8")))
