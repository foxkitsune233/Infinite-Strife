"""Read-only catalog/art audit. Does not run the game or modify assets.

Usage: python tools/audit_item_art.py
Parses literal catalog declarations; dynamic declarations must be reviewed separately.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / "app/src/main/assets/config/inventory.json").read_text(encoding="utf-8"))
items = {d["id"]: (d["name"], d["kind"]) for d in catalog["definitions"]}
equipment = (ROOT / "shared/src/commonMain/kotlin/com/example/douqiuqiu/content/EquipmentContent.kt").read_text(encoding="utf-8")
for ident, name in re.findall(r'(?:component|scroll)\("([^"]+)",\s*"([^"]+)"', equipment):
    items.setdefault(ident, (name, "SCROLL" if ident.startswith("recipe_") else "EQUIPMENT"))
for ident, name in re.findall(r'EquipmentDefinition\(\s*id\s*=\s*"([^"]+)",\s*name\s*=\s*"([^"]+)"', equipment):
    items.setdefault(ident, (name, "EQUIPMENT"))
controls = (ROOT / "shared/src/commonMain/kotlin/com/example/douqiuqiu/content/ControlEquipmentContent.kt")
if controls.is_file():
    source = controls.read_text(encoding="utf-8")
    for ident, name in re.findall(r'EquipmentDefinition\("([^"]+)",\s*"([^"]+)"', source):
        if "$" not in ident:
            items.setdefault(ident, (name, "EQUIPMENT"))
    for ident, name in re.findall(r'scroll\("([^"]+)",\s*"([^"]+)"', source):
        items.setdefault("recipe_" + ident, (name + "卷轴", "SCROLL"))
loot = (ROOT / "shared/src/commonMain/kotlin/com/example/douqiuqiu/loot/ValuableLootCatalog.kt").read_text(encoding="utf-8")
for ident, name in re.findall(r'entry\("([^"]+)",\s*"([^"]+)"', loot):
    items.setdefault("loot_" + ident, (name, "LOOT"))
renderer = (ROOT / "app/src/main/java/com/example/douqiuqiu/ui/components/PaintedItemIcons.kt").read_text(encoding="utf-8")
mapped = set()
for block in re.findall(r'private val (?:specific|components) = setOf\(([^)]*)\)', renderer):
    mapped.update(re.findall(r'"([^"]+)"', block))
assets = ROOT / "app/src/main/assets/art/items/illustrated-v1"
direct_lookup = 'GameArt.item(c, d.id)' in renderer
rows = []
for ident, (name, kind) in sorted(items.items()):
    if kind == "CARD":
        continue
    exists = (assets / (ident + ".png")).is_file()
    state = "independent" if exists and (direct_lookup or ident in mapped) else "unwired" if exists else "composite-scroll" if kind == "SCROLL" else "shared"
    rows.append(dict(id=ident, name=name, kind=kind, status=state))
print(json.dumps({"scope": "literal catalog declarations, excludes cards", "counts": {
    state: sum(r["status"] == state for r in rows)
    for state in ("independent", "unwired", "composite-scroll", "shared")}, "items": rows}, ensure_ascii=True, indent=2))
