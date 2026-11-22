extends SceneTree

const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")

# Independent Android fixtures: ID, category, tier, gold, materials, clears,
# extraction, minimum backpack tier, name, optional hero ID.
const FIXTURES := [
	["hero:skywrath", "hero", 1, 300, {"essence": 3}, 0, true, 0, "解锁天怒法师", "skywrath"],
	["hero:viper", "hero", 1, 500, {"essence": 5}, 1, false, 0, "解锁冥界亚龙", "viper"],
	["hero:phantom_assassin", "hero", 1, 700, {"essence": 7}, 3, false, 0, "解锁幻影刺客", "phantom_assassin"],
	["hero:necrophos", "hero", 1, 900, {"essence": 9}, 5, false, 0, "解锁瘟疫法师", "necrophos"],
	["skills:1", "skills", 1, 250, {"essence": 3}, 0, true, 0, "技能栏4→5", null],
	["skills:2", "skills", 2, 500, {"essence": 5}, 1, false, 0, "技能栏5→6", null],
	["skills:3", "skills", 3, 900, {"essence": 8, "skill_shard": 2}, 3, false, 0, "技能栏6→7", null],
	["skills:4", "skills", 4, 1500, {"essence": 12, "skill_shard": 4}, 6, false, 0, "技能栏7→8", null],
	["backpack:1", "backpack", 1, 200, {"backpack_fabric": 2}, 0, true, 0, "背包4×2→4×3", null],
	["backpack:2", "backpack", 2, 400, {"backpack_fabric": 3}, 1, false, 0, "背包4×3→5×3", null],
	["backpack:3", "backpack", 3, 700, {"backpack_fabric": 5}, 3, false, 0, "背包5×3→6×3", null],
	["backpack:4", "backpack", 4, 1000, {"backpack_fabric": 8}, 6, false, 0, "背包6×3→6×4", null],
	["safe:1", "safe", 1, 600, {"safe_lock": 1}, 1, false, 0, "安全箱2×2→3×2", null],
	["safe:2", "safe", 2, 1200, {"safe_frame": 1}, 5, false, 1, "安全箱3×2→3×3", null],
	["equip_guard:1", "equipment_guard", 1, 300, {"equipment_guard_core": 1}, 2, false, 0, "装备保护 1 格", null],
	["equip_guard:2", "equipment_guard", 2, 700, {"equipment_guard_core": 2}, 4, false, 0, "装备保护 2 格", null],
	["equip_guard:3", "equipment_guard", 3, 1200, {"equipment_guard_core": 3}, 6, false, 0, "装备保护 3 格", null],
	["skill_guard:1", "skill_guard", 1, 300, {"skill_guard_core": 1}, 2, false, 0, "技能保护 1 格", null],
	["skill_guard:2", "skill_guard", 2, 700, {"skill_guard_core": 2}, 4, false, 0, "技能保护 2 格", null],
	["skill_guard:3", "skill_guard", 3, 1200, {"skill_guard_core": 3}, 6, false, 0, "技能保护 3 格", null],
	["skill_guard:4", "skill_guard", 4, 1800, {"skill_guard_core": 4}, 8, false, 0, "技能保护 4 格", null],
]

var rules: Script
var checks := 0
var failures := 0

func _init() -> void:
	if not FileAccess.file_exists("res://scripts/progression/AndroidGrowthRules.gd"):
		check(false, "Android growth implementation must exist")
		quit(1)
		return
	rules = load("res://scripts/progression/AndroidGrowthRules.gd")
	if rules == null or not rules.can_instantiate():
		check(false, "Android growth script must compile")
		quit(1)
		return
	test_entries()
	test_next_entry()
	test_every_purchase_and_boundary()
	test_material_selection_and_rollback()
	test_tracks_and_observation()
	test_invalid_inputs()
	print("Android growth: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)

func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(label)

func add_material(state: PreparationState, id: String, definition: String, bound: bool = false, location: int = ItemInstance.Location.WAREHOUSE, locked: bool = false) -> ItemInstance:
	var instance := ItemInstance.new(id, definition, ItemInstance.Kind.MATERIAL)
	instance.bound = bound
	instance.location = location
	instance.locked = locked
	state.add_item(instance)
	return instance

func funded(row: Array) -> PreparationState:
	var state := PreparationState.new("juggernaut")
	state.gold = row[3]
	state.quest_progress["clears"] = row[5]
	state.quest_progress["extracted"] = row[6]
	state.quest_progress["unknown_future"] = {"values": [71]}
	state.quest_progress.quest_counters["future.quest"] = 17
	state.progression_serial = 92
	match row[1]:
		"backpack":
			state.backpack_size = [Vector2i(4, 2), Vector2i(4, 3), Vector2i(5, 3), Vector2i(6, 3)][int(row[2]) - 1]
		"safe":
			state.safe_size = [Vector2i(2, 2), Vector2i(3, 2)][int(row[2]) - 1]
		"skills":
			state.quest_progress["enabled_card_slots"] = 4 + int(row[2]) - 1
		"equipment_guard":
			state.quest_progress["equipment_guard_level"] = int(row[2]) - 1
		"skill_guard":
			state.quest_progress["skill_guard_level"] = int(row[2]) - 1
		"hero":
			state.unlocked_heroes.erase(row[9])
	for definition in row[4]:
		for i in range(row[4][definition]):
			add_material(state, "%s_%02d" % [definition, i], definition)
	return state

func snapshot(state: PreparationState) -> Dictionary:
	var items := {}
	for key in state.items:
		var item: ItemInstance = state.items[key]
		if item == null:
			items[key] = null
		else:
			items[key] = [item.id, item.definition_id, item.kind, item.bound, item.locked, item.location, item.slot, item.footprint, item.grid_position, item.rotated, item.value, item.identified]
	return {"gold": state.gold, "phase": state.phase, "hero": state.active_hero_id,
		"items": items, "backpack": state.backpack_size, "safe": state.safe_size,
		"equipment_slots": state.equipment_slots.duplicate(), "card_slots": state.card_slots.duplicate(),
		"progress": state.quest_progress.duplicate(true), "levels": state.hero_levels.duplicate(true),
		"heroes": state.unlocked_heroes.duplicate(), "serial": state.progression_serial,
		"craft_serial": state.craft_serial, "presets": state.hero_presets.duplicate(true),
		"selected_presets": state.selected_preset_by_hero.duplicate(true)}

func rejected_unchanged(state: PreparationState, kind: String, label: String) -> Dictionary:
	var before := snapshot(state)
	var result: Dictionary = rules.upgrade(state, kind)
	check(not result.accepted and not result.reason.is_empty(), "reject " + label)
	check(snapshot(state) == before, "no mutation on rejection " + label)
	return result

func test_entries() -> void:
	var entries: Array = rules.entries()
	check(entries.size() == 21, "all Android growth entries including four original hero purchases")
	for i in range(mini(entries.size(), FIXTURES.size())):
		var entry: Dictionary = entries[i]
		var row: Array = FIXTURES[i]
		check([entry.id, entry.type, entry.target, entry.cost_gold, entry.materials,
			entry.requirements.min_clears, entry.requirements.extraction,
			entry.requirements.backpack_level, entry.name, entry.hero_id] == row, "exact source entry " + row[0])
	entries[0].materials.essence = 999
	entries[0].requirements.min_clears = 999
	check(rules.entries()[0].materials.essence == 3 and rules.entries()[0].requirements.min_clears == 0, "catalog calls return detached data")

func test_next_entry() -> void:
	if not rules.has_method("next_entry"):
		check(false, "UI next_entry API must exist")
		return
	var state := PreparationState.new("juggernaut")
	state.gold = 0
	var before := snapshot(state)
	for pair in [["backpack", "backpack:2"], ["safe", "safe:1"], ["skills", "skills:1"], ["equipment_guard", "equip_guard:1"], ["skill_guard", "skill_guard:1"]]:
		var entry: Dictionary = rules.next_entry(state, pair[0])
		check(entry.id == pair[1], "UI offers next tier despite unmet purchase requirements " + pair[0])
	check(snapshot(state) == before, "next_entry leaves all state unchanged")
	var detached: Dictionary = rules.next_entry(state, "backpack")
	detached.materials.backpack_fabric = 999
	detached.requirements.min_clears = 999
	var fresh: Dictionary = rules.next_entry(state, "backpack")
	check(fresh.materials.backpack_fabric == 3 and fresh.requirements.min_clears == 1, "UI result metadata is detached")
	check(rules.next_entry(state, "missing").is_empty(), "unknown UI category has no offer")
	check(rules.next_entry(state, "hero").is_empty(), "UI does not choose implicit hero")
	check(rules.next_entry(state, "backpack:1").id == "backpack:1", "exact ID lookup wraps resolver without purchase validation")
	for row in FIXTURES:
		if row[1] == "hero":
			continue
		state = funded(row)
		check(rules.next_entry(state, row[1]).id == row[0], "UI resolves intermediate tier " + row[0])
	state.backpack_size = Vector2i(6, 4)
	state.safe_size = Vector2i(3, 3)
	state.quest_progress.enabled_card_slots = 8
	state.quest_progress.equipment_guard_level = 3
	state.quest_progress.skill_guard_level = 4
	for category in ["backpack", "safe", "skills", "equipment_guard", "skill_guard"]:
		check(rules.next_entry(state, category).is_empty(), "capped UI category has no offer " + category)

func test_every_purchase_and_boundary() -> void:
	for row in FIXTURES:
		var state := funded(row)
		var heroes := state.unlocked_heroes.duplicate()
		var before_progress := state.quest_progress.duplicate(true)
		var result: Dictionary = rules.upgrade(state, row[0])
		check(result.accepted, "purchase exact threshold " + row[0] + ": " + str(result.reason))
		check(state.gold == 0 and state.items.is_empty(), "exact payment " + row[0])
		check(state.equipment_slots.size() == 9 and state.card_slots.size() == 8, "fixed physical slot arrays " + row[0])
		check(state.progression_serial == 92, "purchase creates no instances " + row[0])
		check(state.quest_progress.unknown_future == {"values": [71]} and state.quest_progress.quest_counters["future.quest"] == 17, "preserve unrelated progress " + row[0])
		check(state.quest_progress.get("clears", 0) == before_progress.clears and state.quest_progress.get("extracted", false) == before_progress.extracted, "purchase does not fabricate qualifications " + row[0])
		if row[1] == "hero":
			check(row[9] in state.unlocked_heroes and state.unlocked_heroes.size() == heroes.size() + 1, "hero channel preserved " + row[0])
		else:
			check(state.unlocked_heroes == heroes, "nonhero upgrade preserves heroes " + row[0])
		rejected_unchanged(state, row[0], "repeat " + row[0])
		state = funded(row)
		state.gold -= 1
		rejected_unchanged(state, row[0], "one gold short " + row[0])
		for material in row[4]:
			state = funded(row)
			state.items.erase("%s_00" % material)
			rejected_unchanged(state, row[0], "one material short " + row[0] + " " + material)
		if row[5] > 0:
			state = funded(row)
			state.quest_progress.clears -= 1
			rejected_unchanged(state, row[0], "one clear short " + row[0])
		if row[6]:
			state = funded(row)
			state.quest_progress.extracted = false
			state.quest_progress.clears = 99
			rejected_unchanged(state, row[0], "clears cannot replace extraction flag " + row[0])
		if row[2] > 1 and row[1] != "hero":
			state = funded(row)
			match row[1]:
				"backpack": state.backpack_size = Vector2i(4, 2)
				"safe": state.safe_size = Vector2i(2, 2)
				"skills": state.quest_progress.enabled_card_slots = 4
				"equipment_guard": state.quest_progress.equipment_guard_level = 0
				"skill_guard": state.quest_progress.skill_guard_level = 0
			rejected_unchanged(state, row[0], "skip tier " + row[0])
	var safe := funded(FIXTURES[13])
	safe.backpack_size = Vector2i(4, 2)
	rejected_unchanged(safe, "safe:2", "safe tier two backpack prerequisite")

func test_material_selection_and_rollback() -> void:
	var state := funded(FIXTURES[0])
	state.items.clear()
	add_material(state, "u2", "essence")
	add_material(state, "b2", "essence", true)
	add_material(state, "u1", "essence")
	add_material(state, "b1", "essence", true)
	var locked := add_material(state, "a_locked", "essence", true, ItemInstance.Location.WAREHOUSE, true)
	var carried := add_material(state, "a_backpack", "essence", true, ItemInstance.Location.BACKPACK)
	var safe := add_material(state, "a_safe", "essence", true, ItemInstance.Location.SAFE)
	var result: Dictionary = rules.upgrade(state, "hero:skywrath")
	check(result.accepted, "mixed bound and unlocked material purchase")
	check(result.consumed_instance_ids == ["b1", "b2", "u1"], "deterministic bound-first then instance ID consumption")
	check(state.items.keys() == ["u2", "a_locked", "a_backpack", "a_safe"], "only selected warehouse instances erased")
	check(state.item("a_locked") == locked and state.item("a_backpack") == carried and state.item("a_safe") == safe, "retained instance identity and state unchanged")
	state = funded(FIXTURES[0])
	state.item("essence_00").locked = true
	rejected_unchanged(state, "hero:skywrath", "locked materials excluded")
	state = funded(FIXTURES[0])
	state.item("essence_00").location = ItemInstance.Location.BACKPACK
	rejected_unchanged(state, "hero:skywrath", "carried material excluded")
	state = funded(FIXTURES[0])
	state.item("essence_00").kind = ItemInstance.Kind.EQUIPMENT
	rejected_unchanged(state, "hero:skywrath", "nonmaterial instance excluded")
	state = funded(FIXTURES[6])
	state.items.erase("skill_shard_00")
	var gaps := rejected_unchanged(state, "skills:3", "second material shortage does not consume first kind")
	check(gaps.reason.contains("skill_shard"), "shortage identifies material")
	state = funded(FIXTURES[6])
	state.gold = 0
	state.items.clear()
	state.quest_progress.clears = 0
	gaps = rejected_unchanged(state, "skills:3", "aggregate resource gaps")
	check(gaps.reason.contains("金币") and gaps.reason.contains("essence") and gaps.reason.contains("skill_shard") and gaps.reason.contains("通关"), "report every applicable resource gap")

func test_tracks_and_observation() -> void:
	for category in ["backpack", "safe", "skills", "equipment_guard", "skill_guard"]:
		var state := PreparationState.new("juggernaut")
		state.gold = 50000
		state.quest_progress["clears"] = 25
		state.quest_progress["extracted"] = true
		state.hero_levels["juggernaut"] = 5
		for definition in ["essence", "skill_shard", "backpack_fabric", "safe_lock", "safe_frame", "equipment_guard_core", "skill_guard_core"]:
			for i in range(50):
				add_material(state, "%s_%02d" % [definition, i], definition)
		var count: int = {"backpack": 3, "safe": 2, "skills": 4, "equipment_guard": 3, "skill_guard": 4}[category]
		for i in range(count):
			check(rules.upgrade(state, category).accepted, "category next tier " + category)
		rejected_unchanged(state, category, "category cap " + category)
		match category:
			"backpack":
				check(state.backpack_size == Vector2i(6, 4), "backpack full track")
				check(state.quest_progress.quest_counters.get("journey.16") == 2, "backpack quest milestone observed")
			"safe":
				check(state.safe_size == Vector2i(3, 3), "safe full track")
				check(state.quest_progress.quest_counters.get("journey.20") == 2, "safe quest milestone observed")
			"skills":
				check(state.quest_progress.enabled_card_slots == 8, "skills unlocks all eight existing slots")
				check(state.quest_progress.quest_counters.get("journey.14") == 1, "skill quest milestone observed")
			"equipment_guard":
				check(state.quest_progress.equipment_guard_level == 3, "equipment protection cap is three")
			"skill_guard":
				check(state.quest_progress.skill_guard_level == 4, "skill protection cap is four")
		check(state.equipment_slots.size() == 9 and state.card_slots.size() == 8, "growth does not resize physical arrays")
		check(state.quest_progress.quest_counters.get("journey.24.2", 0) == 0, "observation does not fabricate wins")
	var initial := funded(FIXTURES[9])
	var result: Dictionary = rules.upgrade(initial, "backpack")
	check(result.accepted and initial.backpack_size == Vector2i(5, 3), "4x3 default begins at Android tier one")

func test_invalid_inputs() -> void:
	for id in ["missing", "hero", "backpack:0", "backpack:5", "equipment_guard:8", "skills:99", "hero:unknown"]:
		rejected_unchanged(funded(FIXTURES[4]), id, "unknown or out-of-range " + id)
	var state := funded(FIXTURES[4])
	state.phase = PreparationState.Phase.JOURNEY
	rejected_unchanged(state, "skills", "not at home")
	state = funded(FIXTURES[4])
	state.quest_progress["run"] = {"round": 3}
	rejected_unchanged(state, "skills", "active run despite home phase")
	state = funded(FIXTURES[4])
	state.backpack_size = Vector2i(2, 9)
	rejected_unchanged(state, "skills", "invalid container size")
	state = funded(FIXTURES[4])
	state.quest_progress["enabled_card_slots"] = 9
	rejected_unchanged(state, "skills", "invalid enabled skill count")
	state = funded(FIXTURES[4])
	state.quest_progress["skill_guard_level"] = 5
	rejected_unchanged(state, "skills", "invalid protection tier")
	state = funded(FIXTURES[4])
	state.quest_progress.clears = -1
	rejected_unchanged(state, "skills", "negative clear count")
	state = funded(FIXTURES[4])
	state.items["alias"] = state.item("essence_00")
	rejected_unchanged(state, "skills", "duplicate instance alias")
	state = funded(FIXTURES[4])
	state.items["null"] = null
	rejected_unchanged(state, "skills", "invalid item reference")
