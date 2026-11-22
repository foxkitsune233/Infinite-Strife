extends RefCounted
## Capture at run start, record after settlement has removed lost items, before quest rewards.
const Items = preload("res://scripts/content/AndroidEquipmentCatalog.gd")
const Growth = preload("res://scripts/progression/AndroidGrowthRules.gd")
const THRESHOLDS := {"backpack": 4, "skills": 4, "safe": 6}

static func category(id: String) -> String:
	return str({"backpack_fabric": "backpack", "skill_shard": "skills", "safe_lock": "safe", "safe_frame": "safe"}.get(id, ""))

static func needed_item(state: PreparationState, cat: String) -> String:
	var clears := int(state.quest_progress.get("clears", 0))
	var slots := int(state.quest_progress.get("enabled_card_slots", 4))
	var candidate := ""
	match cat:
		"backpack":
			if bool(state.quest_progress.get("extracted", false)) and state.backpack_size != Vector2i(6, 4):
				candidate = "backpack_fabric"
		"skills":
			if slots in [6, 7] and clears >= (3 if slots == 6 else 6):
				candidate = "skill_shard"
		"safe":
			if state.safe_size.x == 2 and clears >= 1:
				candidate = "safe_lock"
			elif state.safe_size == Vector2i(3, 2) and clears >= 5 and state.backpack_size.y >= 3:
				candidate = "safe_frame"
	if candidate.is_empty():
		return ""
	var upgrade := Growth.next_entry(state, cat)
	if upgrade.is_empty():
		return ""
	var req: Dictionary = upgrade.requirements
	if clears < int(req.min_clears) or bool(req.extraction) and not bool(state.quest_progress.get("extracted", false)) or Growth.BACKPACK_SIZES.find(state.backpack_size) < int(req.backpack_level):
		return ""
	var cost := int(upgrade.materials.get(candidate, 0))
	var stored := 0
	for item in state.items.values():
		if item.definition_id == candidate and item.location == ItemInstance.Location.WAREHOUSE:
			stored += 1
	return candidate if stored < cost else ""

static func eligible(state: PreparationState, theme: String = "", difficulty: int = 2) -> Array[String]:
	var result: Array[String] = []
	for cat in THRESHOLDS:
		var material := needed_item(state, cat)
		var definition := Items.item(material)
		if not material.is_empty() and definition.get("kind") == "MATERIAL" and int(definition.get("minimumDropClears", 0)) <= int(state.quest_progress.get("clears", 0)) and (theme.is_empty() or theme == cat) and difficulty >= (1 if cat == "safe" else 0):
			result.append(cat)
	result.sort()
	return result

static func capture(state: PreparationState, target: Dictionary) -> Dictionary:
	return {"item_ids": state.items.keys(), "eligible": eligible(state, str(target.get("theme", "supplies")), int(target.get("difficulty", 0))), "counters": state.quest_progress.get("material_pity", {}).duplicate(true), "recorded": false}

static func record_after(snapshot: Dictionary, settled: PreparationState, cleared: bool) -> Dictionary:
	if bool(snapshot.get("recorded", false)):
		return settled.quest_progress.get("material_pity", {}).duplicate(true)
	var brought: Array[String] = []
	for item in settled.items.values():
		if item.id not in snapshot.item_ids:
			var cat := category(item.definition_id)
			if not cat.is_empty() and cat not in brought:
				brought.append(cat)
	var counters: Dictionary = snapshot.counters.duplicate(true)
	for cat in brought:
		if counters.has(cat):
			counters[cat] = {"misses": 0, "pending": false}
	if cleared:
		for cat in snapshot.eligible:
			if cat in brought:
				continue
			var misses := mini(int(THRESHOLDS[cat]), int(counters.get(cat, {}).get("misses", 0)) + 1)
			counters[cat] = {"misses": misses, "pending": misses == int(THRESHOLDS[cat])}
	settled.quest_progress["material_pity"] = counters
	snapshot.recorded = true
	return counters.duplicate(true)

## Main integration: run_start_item_ids is saved on target_chest at run entry.
static func snapshot(state: PreparationState, journey: JourneyState) -> Dictionary:
	var captured := capture(state, journey.target_chest)
	captured["eligible"] = eligible(state, str(journey.target_chest.get("theme", "supplies")), journey.difficulty)
	captured["item_ids"] = journey.target_chest.get("run_start_item_ids", state.items.keys()).duplicate()
	# Missing entry baseline must never credit existing items as newly acquired.
	journey.target_chest["material_pity_snapshot"] = captured
	return captured

static func record(state: PreparationState, journey: JourneyState, cleared: bool) -> Dictionary:
	var captured: Dictionary = journey.target_chest.get("material_pity_snapshot", {})
	if captured.is_empty():
		return state.quest_progress.get("material_pity", {}).duplicate(true)
	return record_after(captured, state, cleared)
