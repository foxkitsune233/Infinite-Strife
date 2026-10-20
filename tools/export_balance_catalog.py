"""Export Android's literal base catalog for the external JVM sampler. No simulation."""
import csv
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
config = json.loads((ROOT / "app/src/main/assets/config/inventory.json").read_text(encoding="utf-8"))
stats = ["strength", "agility", "intelligence", "health", "healthRegen", "armor", "attackSpeed", "manaPercent", "manaRegen", "attack"]
with (ROOT / "tools/balance-base.tsv").open("w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f, delimiter="\t", lineterminator="\n")
    writer.writerow(["id", "name", "kind", "width", "height", "quality", "maxStack", "combatUsable", *stats,
                     "price", "shopStock", "shopShelf", "minimumShopClears", "boundOnAcquire", "minimumDropClears"])
    for d in config["definitions"]:
        writer.writerow([d["id"], d["name"], d["kind"], d["width"], d["height"], d["quality"], d.get("maxStack", 1),
                         str(d.get("combatUsable", False)).lower(), *[d.get("stats", {}).get(k, 0) for k in stats],
                         d.get("price", 0), d.get("shopStock", 0), d.get("shopShelf", "STAPLE"), d.get("minimumShopClears", 0),
                         str(d.get("boundOnAcquire", False)).lower(), d.get("minimumDropClears", 0)])
print("Exported literal base catalog; extended cards/gear/loot are read from actual shared Kotlin catalogs")
