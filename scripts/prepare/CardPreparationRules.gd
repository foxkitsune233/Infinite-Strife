extends RefCounted
const Catalog = preload("res://scripts/battle/AndroidEffectCatalog.gd")
const SCHOOLS := {"basic": "普攻", "ultimate": "大招", "crit": "暴击", "shield": "护盾", "restore": "恢复", "poison": "中毒", "rage": "怒火", "frost": "寒霜"}

static func enabled_slots(state: PreparationState) -> int:
	return clampi(int(state.quest_progress.get("enabled_card_slots", 4)), 4, 8)

static func definition(id: String) -> Dictionary:
	Catalog._load()
	return Catalog._cards.get(id, {})

## Each card appears once in its first source school; all tags still count for synergy.
static func pool(state: PreparationState) -> Dictionary:
	var groups := {}
	for school in SCHOOLS:
		groups[school] = []
	var ids: Array = state.items.keys()
	ids.sort()
	for id in ids:
		var item := state.item(id)
		if item.kind != ItemInstance.Kind.CARD or item.location != ItemInstance.Location.WAREHOUSE:
			continue
		var card := definition(item.definition_id)
		if not card.is_empty():
			groups[str(card.tags[0])].append(item.id)
	return groups

static func synergy(state: PreparationState) -> Array[Dictionary]:
	var seen := {}
	for index in enabled_slots(state):
		var item := state.item(state.card_slots[index])
		if item == null:
			continue
		for tag in definition(item.definition_id).get("tags", []):
			if not seen.has(tag):
				seen[tag] = {}
			seen[tag][item.definition_id] = true
	Catalog._load()
	var result: Array[Dictionary] = []
	for tier in Catalog._snapshot.get("synergy_tiers", []):
		var entry: Dictionary = tier.duplicate(true)
		entry["count"] = seen.get(tier.schoolId, {}).size()
		entry["active"] = int(entry.count) >= int(tier.tier)
		result.append(entry)
	return result

static func merge_candidates(state: PreparationState, id: String) -> Array[String]:
	var first := state.item(id)
	var result: Array[String] = []
	if first == null:
		return result
	result.append(id)
	var ids: Array = state.items.keys()
	ids.sort()
	for other in ids:
		var item := state.item(other)
		if other != id and item.kind == ItemInstance.Kind.CARD and item.definition_id == first.definition_id and item.star == first.star and item.location == ItemInstance.Location.WAREHOUSE and not item.locked:
			result.append(other)
		if result.size() == 3:
			break
	return result

static func merge_cards(state: PreparationState, ids: Array[String]) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return {"accepted": false, "reason": "仅局外可升星"}
	if ids.size() != 3 or ids[0] == ids[1] or ids[0] == ids[2] or ids[1] == ids[2]:
		return {"accepted": false, "reason": "需要三个不同实例"}
	var first := state.item(ids[0])
	if first == null or first.kind != ItemInstance.Kind.CARD or definition(first.definition_id).is_empty() or first.star not in [1, 2]:
		return {"accepted": false, "reason": "需要仓库内三张同名同星卡，最高三星"}
	var bound := false
	for id in ids:
		var item := state.item(id)
		if item == null or item.kind != ItemInstance.Kind.CARD or item.definition_id != first.definition_id or item.star != first.star or item.location != ItemInstance.Location.WAREHOUSE or item.locked:
			return {"accepted": false, "reason": "需要仓库内三张未锁定同名同星卡"}
		bound = bound or item.bound
	first.star += 1
	first.bound = bound
	var removed := [ids[1], ids[2]]
	for id in removed:
		state.remove_item(id)
	for presets in state.hero_presets.values():
		for preset in presets:
			for index in preset.card_ids.size():
				if preset.card_ids[index] in removed:
					preset.card_ids[index] = ""
	return {"accepted": true, "reason": "已升至%d星（免费）" % first.star, "id": first.id, "removed": removed}

static func effect_text(effects: Array) -> String:
	var lines: PackedStringArray = []
	for effect in effects:
		var values: PackedStringArray = []
		for key in effect.get("params", {}):
			values.append("%s=%s" % [key, effect.params[key].get("value", "")])
		for key in ["intervalMs", "internalCooldownMs", "maxStacks", "stackDurationMs"]:
			if effect.get(key) != null:
				values.append("%s=%s" % [key, effect[key]])
		lines.append("%s · %s · %s" % [effect.get("kind", ""), effect.get("trigger", ""), ", ".join(values)])
	return "\n".join(lines)

static func details(item: ItemInstance) -> String:
	var card := definition(item.definition_id)
	if card.is_empty():
		return item.definition_id
	var tags: PackedStringArray = []
	for tag in card.tags:
		tags.append(str(SCHOOLS.get(tag, tag)))
	var lines := "%s · %d星 · %s\n%s" % [card.name, item.star, " / ".join(tags), card.get("description", "")]
	for star in card.get("stars", []):
		lines += "\n%d星%s\n%s" % [int(star.star), "（当前）" if int(star.star) == item.star else "", effect_text(star.effects)]
	return lines
