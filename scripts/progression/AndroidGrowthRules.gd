class_name AndroidGrowthRules
extends RefCounted
## Deterministic growth transactions; no persistence, UI, or reward spawning.

const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const Equipment = preload("res://scripts/content/AndroidEquipmentCatalog.gd")
const Progression = preload("res://scripts/progression/ProgressionRules.gd")

const BACKPACK_SIZES := [Vector2i(4, 2), Vector2i(4, 3), Vector2i(5, 3), Vector2i(6, 3), Vector2i(6, 4)]
const SAFE_SIZES := [Vector2i(2, 2), Vector2i(3, 2), Vector2i(3, 3)]

static func entries() -> Array[Dictionary]:
	return [
		_entry("hero:skywrath", "解锁天怒法师", "hero", 1, 300, {"essence": 3}, 0, true, 0, "skywrath"),
		_entry("hero:viper", "解锁冥界亚龙", "hero", 1, 500, {"essence": 5}, 1, false, 0, "viper"),
		_entry("hero:phantom_assassin", "解锁幻影刺客", "hero", 1, 700, {"essence": 7}, 3, false, 0, "phantom_assassin"),
		_entry("hero:necrophos", "解锁瘟疫法师", "hero", 1, 900, {"essence": 9}, 5, false, 0, "necrophos"),
		_entry("skills:1", "技能栏4→5", "skills", 1, 250, {"essence": 3}, 0, true),
		_entry("skills:2", "技能栏5→6", "skills", 2, 500, {"essence": 5}, 1),
		_entry("skills:3", "技能栏6→7", "skills", 3, 900, {"essence": 8, "skill_shard": 2}, 3),
		_entry("skills:4", "技能栏7→8", "skills", 4, 1500, {"essence": 12, "skill_shard": 4}, 6),
		_entry("backpack:1", "背包4×2→4×3", "backpack", 1, 200, {"backpack_fabric": 2}, 0, true),
		_entry("backpack:2", "背包4×3→5×3", "backpack", 2, 400, {"backpack_fabric": 3}, 1),
		_entry("backpack:3", "背包5×3→6×3", "backpack", 3, 700, {"backpack_fabric": 5}, 3),
		_entry("backpack:4", "背包6×3→6×4", "backpack", 4, 1000, {"backpack_fabric": 8}, 6),
		_entry("safe:1", "安全箱2×2→3×2", "safe", 1, 600, {"safe_lock": 1}, 1),
		_entry("safe:2", "安全箱3×2→3×3", "safe", 2, 1200, {"safe_frame": 1}, 5, false, 1),
		_entry("equip_guard:1", "装备保护 1 格", "equipment_guard", 1, 300, {"equipment_guard_core": 1}, 2),
		_entry("equip_guard:2", "装备保护 2 格", "equipment_guard", 2, 700, {"equipment_guard_core": 2}, 4),
		_entry("equip_guard:3", "装备保护 3 格", "equipment_guard", 3, 1200, {"equipment_guard_core": 3}, 6),
		_entry("skill_guard:1", "技能保护 1 格", "skill_guard", 1, 300, {"skill_guard_core": 1}, 2),
		_entry("skill_guard:2", "技能保护 2 格", "skill_guard", 2, 700, {"skill_guard_core": 2}, 4),
		_entry("skill_guard:3", "技能保护 3 格", "skill_guard", 3, 1200, {"skill_guard_core": 3}, 6),
		_entry("skill_guard:4", "技能保护 4 格", "skill_guard", 4, 1800, {"skill_guard_core": 4}, 8),
	]

## Metadata lookup only: categories select the next tier, exact IDs resolve as-is.
static func next_entry(state: PreparationState, kind: String) -> Dictionary:
	return _resolve(state, kind)

static func upgrade(state: PreparationState, kind: String) -> Dictionary:
	var invalid := _validate(state)
	if not invalid.is_empty():
		return _reject(invalid)
	if state.phase != PreparationState.Phase.HOME or state.quest_progress.get("run") != null:
		return _reject("仅局外可购买成长")
	var entry := _resolve(state, kind)
	if entry.is_empty():
		return _reject("成长项目不存在或已达上限")
	var category: String = entry.type
	if category == "hero":
		if str(entry.hero_id) in state.unlocked_heroes:
			return _reject("英雄已解锁")
	elif _level(state, category) != int(entry.target) - 1:
		return _reject("需按顺序升级，不能重复购买或跳档")
	for material in entry.materials:
		if str(Equipment.item(material).get("kind", "")) != "MATERIAL":
			return _reject("成长材料配置缺失：%s" % material)
	# Collect a complete deterministic plan before touching gold, items or progress.
	var plan := _material_plan(state, entry.materials)
	var gaps: Array[String] = []
	if state.gold < int(entry.cost_gold):
		gaps.append("金币还差%d" % (int(entry.cost_gold) - state.gold))
	for material in plan.missing:
		gaps.append("%s还差%d份（仅计算仓库未锁定材料）" % [material, plan.missing[material]])
	var requirements: Dictionary = entry.requirements
	if requirements.extraction and not bool(state.quest_progress.get("extracted", false)):
		gaps.append("需要完成一次安全撤离或通关")
	var missing_clears := int(requirements.min_clears) - int(state.quest_progress.get("clears", 0))
	if missing_clears > 0:
		gaps.append("还需通关%d次" % missing_clears)
	if _level(state, "backpack") < int(requirements.backpack_level):
		gaps.append("背包至少扩至4×3")
	if not gaps.is_empty():
		return _reject("\n".join(gaps))
	# No checks below this point may reject. Growth expands containers and removes
	# only warehouse materials, so retained placements and slot references stay valid.
	var progress := state.quest_progress.duplicate(true)
	match category:
		"skills": progress["enabled_card_slots"] = 4 + int(entry.target)
		"equipment_guard": progress["equipment_guard_level"] = int(entry.target)
		"skill_guard": progress["skill_guard_level"] = int(entry.target)
	state.gold -= int(entry.cost_gold)
	for instance_id in plan.consumed:
		state.items.erase(instance_id)
	match category:
		"backpack": state.backpack_size = BACKPACK_SIZES[int(entry.target)]
		"safe": state.safe_size = SAFE_SIZES[int(entry.target)]
		"hero": state.unlocked_heroes.append(str(entry.hero_id))
	state.quest_progress = progress
	Progression.observe(state)
	return {"accepted": true, "reason": "%s已完成" % entry.name,
		"id": entry.id, "type": category, "target": entry.target,
		"consumed_instance_ids": plan.consumed.duplicate()}

static func _entry(id: String, name: String, type: String, target: int, cost_gold: int, materials: Dictionary, min_clears: int = 0, extraction: bool = false, backpack_level: int = 0, hero_id: Variant = null) -> Dictionary:
	return {"id": id, "name": name, "type": type, "target": target,
		"cost_gold": cost_gold, "materials": materials.duplicate(true), "hero_id": hero_id,
		"requirements": {"min_clears": min_clears, "extraction": extraction, "backpack_level": backpack_level}}

static func _level(state: PreparationState, kind: String) -> int:
	match kind:
		"backpack": return BACKPACK_SIZES.find(state.backpack_size)
		"safe": return SAFE_SIZES.find(state.safe_size)
		"skills": return int(state.quest_progress.get("enabled_card_slots", 4)) - 4
		"equipment_guard": return int(state.quest_progress.get("equipment_guard_level", 0))
		"skill_guard": return int(state.quest_progress.get("skill_guard_level", 0))
	return 0

static func _resolve(state: PreparationState, kind: String) -> Dictionary:
	for entry in entries():
		if entry.id == kind:
			return entry
	# A category means the next tier, not a request to re-buy its starting tier.
	# Hero unlocks must name their stable ID to avoid implicit hero selection.
	for entry in entries():
		if entry.type == kind and kind != "hero" and int(entry.target) == _level(state, kind) + 1:
			return entry
	return {}

static func _material_plan(state: PreparationState, costs: Dictionary) -> Dictionary:
	var definitions: Array = costs.keys()
	definitions.sort()
	var consumed: Array[String] = []
	var missing := {}
	for definition in definitions:
		var available: Array[ItemInstance] = []
		for instance: ItemInstance in state.items.values():
			if instance.definition_id == definition and instance.kind == ItemInstance.Kind.MATERIAL and instance.location == ItemInstance.Location.WAREHOUSE and not instance.locked:
				available.append(instance)
		available.sort_custom(func(a: ItemInstance, b: ItemInstance) -> bool:
			if a.bound != b.bound:
				return a.bound
			return a.id < b.id)
		var need := int(costs[definition])
		if available.size() < need:
			missing[definition] = need - available.size()
		else:
			for i in range(need):
				consumed.append(available[i].id)
	# A failed plan deliberately exposes no partial consumable list.
	return {"consumed": consumed if missing.is_empty() else [], "missing": missing}

static func _validate(state: PreparationState) -> String:
	if state.gold < 0 or state.gold > 2147483647:
		return "金币数据无效"
	if not BACKPACK_SIZES.has(state.backpack_size) or not SAFE_SIZES.has(state.safe_size) or _level(state, "skills") < 0 or _level(state, "skills") > 4:
		return "容器容量无效"
	if state.equipment_slots.size() != 9 or state.card_slots.size() != 8:
		return "栏位容量无效"
	if _level(state, "equipment_guard") < 0 or _level(state, "equipment_guard") > 3 or _level(state, "skill_guard") < 0 or _level(state, "skill_guard") > 4:
		return "保护档位无效"
	if int(state.quest_progress.get("clears", 0)) < 0:
		return "通关次数无效"
	if state.items.size() > 10000:
		return "物品超出数量限制"
	for key in state.items:
		var value: Variant = state.items[key]
		if not value is ItemInstance:
			return "物品实例无效"
		var instance: ItemInstance = value
		if instance.id.strip_edges().is_empty() or instance.id.length() > 128 or key != instance.id or instance.definition_id.strip_edges().is_empty():
			return "物品实例重复或属性无效"
	return ""

static func _reject(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
