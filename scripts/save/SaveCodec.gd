class_name SaveCodec
extends RefCounted

const SCHEMA_VERSION := 2
const ItemInstanceType = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationStateType = preload("res://scripts/prepare/PreparationState.gd")
const JourneyStateType = preload("res://scripts/journey/JourneyState.gd")
const LoadoutPresetType = preload("res://scripts/prepare/LoadoutPreset.gd")

static func encode(preparation: PreparationState, journey: JourneyState) -> Dictionary:
	var item_records: Array[Dictionary] = []
	for instance in preparation.items.values():
		item_records.append(_item_to_data(instance))
	var presets := {}
	for hero_id in preparation.hero_presets:
		var entries: Array = preparation.hero_presets[hero_id]
		var saved: Array[Dictionary] = []
		for preset in entries:
			saved.append({"equipment_ids": preset.equipment_ids.duplicate(), "card_ids": preset.card_ids.duplicate()})
		presets[hero_id] = saved
	var loot_records: Array[Dictionary] = []
	for item in journey.loot:
		loot_records.append(_item_to_data(item))
	return {
		"schema_version": SCHEMA_VERSION,
		"preparation": {
			"phase": int(preparation.phase), "active_hero_id": preparation.active_hero_id, "gold": preparation.gold,
			"backpack_size": [preparation.backpack_size.x, preparation.backpack_size.y], "safe_size": [preparation.safe_size.x, preparation.safe_size.y],
			"craft_serial": preparation.craft_serial, "equipment_slots": preparation.equipment_slots.duplicate(), "card_slots": preparation.card_slots.duplicate(),
			"items": item_records, "hero_presets": presets, "selected_preset_by_hero": preparation.selected_preset_by_hero.duplicate(true),
			"hero_levels": preparation.hero_levels.duplicate(true), "quest_progress": preparation.quest_progress.duplicate(true), "progression_serial": preparation.progression_serial,
			"unlocked_heroes": preparation.unlocked_heroes.duplicate(),
		},
		"journey": {
			"seed": journey.seed, "difficulty": journey.difficulty, "run_gold": journey.run_gold, "current_node": journey.current_node, "resolved": journey.resolved, "battle_started": journey.battle_started,
			"appraisal_ms": journey.appraisal_ms, "reveal_at": journey.reveal_at.duplicate(),
			"frozen_event_id": journey.frozen_event_id, "frozen_event_options": journey.frozen_event_options.duplicate(true), "drop_serial": journey.drop_serial, "loot": loot_records,
			"event_result": journey.event_result.duplicate(),
			"target_chest": journey.target_chest.duplicate(true),
			"nodes": journey.nodes.duplicate(true), "battle_snapshot": journey.battle_snapshot.duplicate(true), "player_hp_ratio": journey.player_hp_ratio, "ending": journey.ending,
			"recent_event_kinds": journey.recent_event_kinds.duplicate(), "event_rng": journey.event_rng,
		},
	}

static func decode(data: Dictionary, repair_task_heroes: bool = true) -> Dictionary:
	var schema := int(data.get("schema_version", 0))
	if schema > SCHEMA_VERSION:
		return _rejected("存档来自更高版本，当前客户端无法读取")
	if schema < 0:
		return _rejected("存档版本无效")
	var prepared_data: Dictionary = data.get("preparation", {})
	if prepared_data.is_empty():
		return _rejected("存档缺少整备数据")
	var preparation := PreparationStateType.new(str(prepared_data.get("active_hero_id", "juggernaut")))
	preparation.phase = int(prepared_data.get("phase", PreparationState.Phase.HOME)) as PreparationState.Phase
	preparation.gold = int(prepared_data.get("gold", 1200))
	preparation.backpack_size = _vector(prepared_data.get("backpack_size", [4, 3]), Vector2i(4, 3))
	preparation.safe_size = _vector(prepared_data.get("safe_size", [2, 2]), Vector2i(2, 2))
	preparation.craft_serial = int(prepared_data.get("craft_serial", 0))
	preparation.hero_levels = (prepared_data.get("hero_levels", {}) as Dictionary).duplicate(true)
	preparation.quest_progress = (prepared_data.get("quest_progress", {"quest_counters": {}, "claimed_quests": []}) as Dictionary).duplicate(true)
	preparation.progression_serial = int(prepared_data.get("progression_serial", 0))
	if prepared_data.has("unlocked_heroes"):
		preparation.unlocked_heroes.assign(prepared_data.unlocked_heroes)
	# Older Godot task snapshots omitted four hero rewards. Repair only confirmed
	# receipts; never replay gold/items, revoke ownership, or infer from counters.
	var quest_catalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")
	var receipts: Variant = preparation.quest_progress.get("claimed_quests", [])
	if not receipts is Array: return _rejected("存档任务领奖记录无效")
	if repair_task_heroes:
		for quest in quest_catalog.all():
			if quest.id not in receipts: continue
			for reward in quest.rewards:
				if reward.type == "HERO_UNLOCK" and str(reward.hero_id) not in preparation.unlocked_heroes:
					preparation.unlocked_heroes.append(str(reward.hero_id))
	for item_data in prepared_data.get("items", []):
		preparation.add_item(_item_from_data(item_data as Dictionary, schema))
	preparation.equipment_slots = _string_array(prepared_data.get("equipment_slots", []), PreparationState.EQUIPMENT_SLOT_COUNT)
	preparation.card_slots = _string_array(prepared_data.get("card_slots", []), PreparationState.CARD_SLOT_COUNT)
	preparation.hero_presets.clear()
	preparation.selected_preset_by_hero = prepared_data.get("selected_preset_by_hero", {}).duplicate(true)
	for hero_id in (prepared_data.get("hero_presets", {}) as Dictionary):
		var restored: Array[LoadoutPreset] = []
		for entry in (prepared_data.hero_presets[hero_id] as Array):
			restored.append(LoadoutPresetType.new(_string_array(entry.get("equipment_ids", []), PreparationState.EQUIPMENT_SLOT_COUNT), _string_array(entry.get("card_ids", []), PreparationState.CARD_SLOT_COUNT)))
		preparation.hero_presets[hero_id] = restored
	if preparation.hero_presets.is_empty():
		preparation.preset(preparation.active_hero_id, 0)
	var journey_data: Dictionary = data.get("journey", {})
	var journey := JourneyStateType.new(int(journey_data.get("seed", 7001)))
	journey.difficulty = clampi(int(journey_data.get("difficulty", 0)), 0, 6)
	journey.run_gold = clampi(int(journey_data.get("run_gold", 0)), 0, maxi(0, 2147483647 - preparation.gold))
	journey.appraisal_ms = clampf(float(journey_data.get("appraisal_ms", 0.0)), 0.0, 20000.0)
	journey.current_node = int(journey_data.get("current_node", 0))
	journey.resolved = bool(journey_data.get("resolved", false))
	journey.battle_started = bool(journey_data.get("battle_started", false))
	journey.frozen_event_id = str(journey_data.get("frozen_event_id", ""))
	journey.frozen_event_options = _dictionary_array(journey_data.get("frozen_event_options", []))
	var saved_event: Variant = journey_data.get("event_result", {})
	if saved_event is Dictionary: journey.event_result = saved_event.duplicate()
	var saved_target: Variant = journey_data.get("target_chest", {})
	if saved_target is Dictionary: journey.target_chest = saved_target.duplicate(true)
	journey.drop_serial = int(journey_data.get("drop_serial", 0))
	if journey_data.has("nodes"):
		journey.nodes = _dictionary_array(journey_data.nodes)
	elif schema < 2:
		journey.nodes = [{"type": 0, "label": "巡逻守卫"}, {"type": 1, "label": "月井回响"}, {"type": 0, "label": "遗迹猎手"}, {"type": 2, "label": "封印宝箱"}, {"type": 3, "label": "堡垒首领"}]
	journey.battle_snapshot = (journey_data.get("battle_snapshot", {}) as Dictionary).duplicate(true)
	journey.player_hp_ratio = clampf(float(journey_data.get("player_hp_ratio", 1.0)), 0.0, 1.0)
	journey.ending = str(journey_data.get("ending", ""))
	var saved_recent: Variant = journey_data.get("recent_event_kinds", [])
	if saved_recent is Array:
		journey.recent_event_kinds.clear()
		for kind in saved_recent:
			journey.recent_event_kinds.append(str(kind))
	journey.event_rng = int(journey_data.get("event_rng", journey.seed))
	for item_data in journey_data.get("loot", []):
		journey.loot.append(_item_from_data(item_data as Dictionary, schema))
	var deadlines: Variant = journey_data.get("reveal_at", {})
	if deadlines is Dictionary:
		for item in journey.loot:
			var deadline: Variant = deadlines.get(item.id)
			if not item.identified and (deadline is int or deadline is float) and deadline > 0 and deadline <= 20000:
				journey.reveal_at[item.id] = deadline
	for item in journey.loot:
		if not journey.reveal_at.has(item.id): item.identified = true
	# Remove the three unshipped prototype materials from older migration saves.
	var retired: Array[String] = []
	for item: ItemInstance in preparation.items.values():
		if item.definition_id in preload("res://scripts/content/ContentCatalog.gd").RETIRED_MATERIALS:
			retired.append(item.id)
	for id in retired:
		preparation.remove_item(id)
	for presets: Array in preparation.hero_presets.values():
		for preset: LoadoutPreset in presets:
			for slots: Array in [preset.equipment_ids, preset.card_ids]:
				for index in slots.size():
					if slots[index] in retired: slots[index] = ""
	for item in journey.loot.duplicate():
		if item.definition_id in preload("res://scripts/content/ContentCatalog.gd").RETIRED_MATERIALS:
			journey.loot.erase(item)
			journey.reveal_at.erase(item.id)
	return {"accepted": true, "reason": "", "preparation": preparation, "journey": journey}

static func _item_to_data(item: ItemInstance) -> Dictionary:
	return {"id": item.id, "definition_id": item.definition_id, "kind": int(item.kind), "location": int(item.location), "slot": item.slot, "locked": item.locked, "bound": item.bound, "star": item.star, "footprint": [item.footprint.x, item.footprint.y], "grid_position": [item.grid_position.x, item.grid_position.y], "rotated": item.rotated, "value": item.value, "identified": item.identified}

static func _item_from_data(data: Dictionary, schema: int) -> ItemInstance:
	var item := ItemInstanceType.new(str(data.get("id", "missing")), str(data.get("definition_id", "missing")), int(data.get("kind", ItemInstance.Kind.EQUIPMENT)) as ItemInstance.Kind)
	item.location = int(data.get("location", ItemInstance.Location.WAREHOUSE)) as ItemInstance.Location
	item.slot = int(data.get("slot", -1))
	item.locked = bool(data.get("locked", false))
	item.bound = bool(data.get("bound", false))
	item.star = clampi(int(data.get("star", 1)), 1, 3)
	item.footprint = _vector(data.get("footprint", [1, 1]), Vector2i.ONE)
	item.grid_position = _vector(data.get("grid_position", [0, 0]), Vector2i.ZERO)
	item.rotated = bool(data.get("rotated", false))
	item.value = int(data.get("value", 0))
	item.identified = bool(data.get("identified", true if schema == 0 else true))
	return item

static func _vector(value: Variant, fallback: Vector2i) -> Vector2i:
	var values := value as Array
	return Vector2i(int(values[0]), int(values[1])) if values.size() >= 2 else fallback

static func _string_array(value: Variant, count: int) -> Array[String]:
	var result: Array[String] = []
	for entry in value as Array:
		result.append(str(entry))
	while result.size() < count:
		result.append("")
	return result.slice(0, count)

static func _dictionary_array(value: Variant) -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	for entry in value as Array:
		if entry is Dictionary:
			result.append((entry as Dictionary).duplicate(true))
	return result

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
