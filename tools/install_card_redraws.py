"""Copy GPT originals and export game-format WebP; no repainting, filters or compositing."""
import hashlib
import json
import shutil
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
log_path = Path(sys.argv[1]).resolve()
data = json.loads(log_path.read_text(encoding="utf-8-sig"))
manifest_path = ROOT / "app/src/main/assets/art/cards/v0.1.5/manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
rows = {row["id"]: row for row in manifest["art"]}
backup = log_path.parent / "previous-game-assets"
backup.mkdir(exist_ok=True)
installed = []
for asset in data["generated"]:
    ident = asset["id"]
    assert ident in rows, ident
    source = Path(asset["path"])
    assert source.is_file(), source
    target = ROOT / "app/src/main/assets/art/cards" / rows[ident]["file"]
    original = log_path.parent / f"{ident}.png"
    with Image.open(source) as im:
        im.load()
        assert abs(im.width / im.height - 2 / 3) < 0.01, (ident, im.size)
        assert im.width >= 1024 and im.height >= 1536, (ident, im.size)
        size = Image.open(target).size
        if not (backup / target.name).exists():
            shutil.copy2(target, backup / target.name)
        shutil.copy2(source, original)
        im.convert("RGB").resize(size, Image.Resampling.LANCZOS).save(target, "WEBP", quality=94, method=6)
    row = rows[ident]
    row.setdefault("previousSource", row.get("source"))
    row.update(source=original.relative_to(ROOT).as_posix(),
        sourceSha256=hashlib.sha256(original.read_bytes()).hexdigest(),
        provider="GPT built-in image_gen", redrawnAt="2026-10-21", style="angular-flat-paper-cut-dark-fantasy")
    installed.append({"id": ident, "source": row["source"], "gameFile": row["file"],
        "gameSha256": hashlib.sha256(target.read_bytes()).hexdigest(), "gameSize": size})
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
(log_path.parent / "installed.json").write_text(json.dumps(installed, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print("Installed", ", ".join(x["id"] for x in installed))
