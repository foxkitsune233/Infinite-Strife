"""Build the approved missing-item generation manifest; never reads credentials."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "art-source/independent-items-v3"
OUT.mkdir(parents=True, exist_ok=True)
groups = [
[("blades_of_attack", "jagged steel attack claws with red grip"), ("demon_edge", "broad red demonic sword with black spike guard"), ("staff_of_wizardry", "silver wizard staff with blue crystal"), ("reaver", "red double headed giant axe with gold fittings"), ("eaglesong", "emerald eagle shaped bow with gold fittings"), ("mystic_staff", "violet archmage crystal staff"), ("hyperstone", "blue attack speed gem in a golden ring"), ("orb_of_venom", "green liquid poisonous pearl"), ("helm_of_iron_will", "black iron helmet with thick gold horns")],
[("armlet_of_mordiggian", "black steel spiked arm bracer with glowing red heart"), ("bracer", "red leather wrist guard with golden plate"), ("crimson_guard", "huge crimson kite shield with golden lion crest"), ("crystalys", "short pink crystal sword with gold guard"), ("daedalus", "enormous red crossbow with gold bolts"), ("dragon_lance", "blue dragon shaped spear with gold blade"), ("expert_array", "three matched arrows and a red leather quiver"), ("maelstrom", "blue lightning war hammer with short handle"), ("morbid_mask", "red vampiric face mask with two ivory fangs")],
[("energy_booster", "bright blue magical orb in silver wings"), ("vitality_booster", "bright red vitality orb in a bronze cradle"), ("point_booster", "violet and blue two tone magical orb with golden orbit"), ("soul_booster", "three red blue purple soul gems bound by golden rings"), ("void_stone", "deep purple faceted stone with inner cyan flame"), ("octarine_core", "violet glowing core in an eight pointed golden cage"), ("heart_of_tarrasque", "red anatomical monster heart armored with gold ribs"), ("talisman_of_evasion", "emerald butterfly wing shaped gold amulet"), ("perseverance", "silver ceremonial crown with blue and red gems")],
[("oblivion_staff", "dark silver long staff ending in a red eye"), ("pipe_of_insight", "curved wooden smoking pipe with gold bowl and green smoke"), ("vanguard_shield", "stout dark iron round shield with a red central gem"), ("null_talisman", "purple triangular jewel pendant on golden chain"), ("wraith_band", "emerald ghost face woven into a dark leather wristband"), ("shawl", "folded turquoise cloth shawl with gold embroidery"), ("salve", "round red glass healing potion with cork and cream label"), ("tango", "three green healing leaves bound by red string"), ("faerie_fire", "orange magical twig with bright golden sparks")],
[("backpack_fabric", "folded thick tan fabric with needle and green stitching"), ("essence", "small bottled swirling bright cyan essence"), ("safe_frame", "heavy square golden iron reinforced chest frame"), ("safe_lock", "large ornate bronze vault lock and small key"), ("skill_shard", "broken glowing violet magical tablet shard"), ("loot_copper_coin", "worn single copper coin with an old profile"), ("loot_tin_cup", "dented dull tin drinking cup"), ("loot_wood_box", "empty open small wooden jewelry box"), ("loot_porcelain_vase", "beautiful blue green porcelain vase with leaf pattern")],
[("loot_bronze_clock", "carved bronze mantel clock with cream dial"), ("loot_faded_painting", "small faded mountain painting in plain wooden frame"), ("loot_golden_statue", "golden armored lion statue on a small plinth"), ("loot_jade_statue", "polished jade green dragon figurine"), ("loot_oil_painting", "rich colored royal portrait in ornate golden frame")],
]
audit = json.loads((ROOT / "art-source/item-art-audit.json").read_text(encoding="utf-8-sig"))
scrolls = [r for r in audit["items"] if r["status"] == "composite-scroll"]
for start in range(0, len(scrolls), 9):
    groups.append([(r["id"], "a distinct rolled parchment equipment recipe, ornate colored wax seal, clear central painted emblem for " + r["id"].removeprefix("recipe_").replace("_", " ")) for r in scrolls[start:start+9]])
style = ("Production inventory sprite sheet, exactly 3 columns by 3 rows, equal square cells. "
         "Each object centered completely within its own cell with wide margins. "
         "Entire background uniform deep navy #082C3B. No frames, grid lines, lettering, numbers or watermarks. "
         "Simple high saturation FLAT 2D CARTOON fantasy mobile game art, chunky dark outlines, "
         "angular paper cut shapes, just three flat cel shading tones, bold gold and teal accents. "
         "No realistic textures, no tiny details, no perspective scene. Large recognizable physical objects. "
         "Read cells from left to right then top to bottom. ")
manifest = {"provider": "Bailian", "cliVersion": "2.3.1", "groups": []}
for i, group in enumerate(groups, 1):
    prompt = style + " ".join(f"Cell {j+1}: {desc}." for j, (_, desc) in enumerate(group))
    if len(group) < 9:
        prompt += f" Cells {len(group)+1} to 9 remain blank navy."
    manifest["groups"].append({"key": f"objects-{i:02d}", "items": group, "prompt": prompt})
(OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=True, indent=2), encoding="utf-8")
print(f"{len(groups)} sheets / {sum(len(g) for g in groups)} independent items")
