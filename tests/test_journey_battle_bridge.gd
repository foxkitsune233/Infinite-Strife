extends SceneTree

const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const JourneyState = preload("res://scripts/journey/JourneyState.gd")
const JourneyRules = preload("res://scripts/journey/JourneyRules.gd")
const AndroidEquipmentCatalog = preload("res://scripts/content/AndroidEquipmentCatalog.gd")

var _failed := false

func _init() -> void:
	var preparation := PreparationState.new("juggernaut")
	var journey := JourneyState.new(7001)
	var enemy := BattleLoadoutBuilder.enemy_for_node(journey.seed, 0, "juggernaut")
	var repeated := BattleLoadoutBuilder.enemy_for_node(journey.seed, 0, "juggernaut")
	_assert_equal(enemy.fighter.hero_id, "necrophos", "fixed seed selects an Android hero from the sorted roster")
	_assert_equal(enemy.fighter.to_data(), repeated.fighter.to_data(), "re-entering the same battle preserves enemy build")
	_assert_equal(enemy.school_id in ["restore", "ultimate", "poison"], true, "enemy exposes selected school")
	_assert_equal(enemy.school_id, repeated.school_id, "enemy school is stable across previews")
	_assert_equal(enemy.skill.runtime_mode, "generic_placeholder", "encounter uses the real hero dispatch path")
	var boss := BattleLoadoutBuilder.enemy_for_node(journey.seed, 8, "abaddon")
	_assert_equal(boss.fighter.hero_id == "abaddon", false, "enemy selection excludes the player hero")
	_assert_equal(journey.seed, 7001, "enemy preview leaves the journey seed intact")
	var battle_one := BattleLoadoutBuilder._standard_enemy_loadout("necrophos", 0, 7001)
	var battle_five := BattleLoadoutBuilder._standard_enemy_loadout("necrophos", 4, 7001)
	_assert_equal(battle_one.card_slots.filter(func(id: String) -> bool: return not id.is_empty()).size(), 1, "opening round has one card")
	_assert_equal(battle_five.card_slots.filter(func(id: String) -> bool: return not id.is_empty()).size(), 4, "final round has four cards")
	_assert_equal(battle_five.item(battle_five.card_slots[0]).star, 2, "first final-round card has two stars")
	var gear_cost := 0
	for id in battle_five.equipment_slots:
		if id.is_empty(): continue
		gear_cost += int(AndroidEquipmentCatalog.item(battle_five.item(id).definition_id).price)
	_assert_equal(gear_cost > 0 and gear_cost <= 540, true, "final-round gear respects its budget")
	var inferno := BattleLoadoutBuilder.enemy_for_node(7001, 8, "juggernaut", 6)
	var draw := 7001
	for index in 9: draw = draw * 48271 % 2147483647
	var base: FighterState = BattleLoadoutBuilder.build(
		BattleLoadoutBuilder._standard_enemy_loadout(inferno.fighter.hero_id, 4, draw, 6)).fighter
	_assert_equal(inferno.fighter.max_hp, roundi(base.max_hp * 5.54), "Inferno round five uses Android HP scaling")
	_assert_equal(inferno.fighter.attack_damage, roundi(base.attack_damage * 3.45), "Inferno round five uses Android attack scaling")
	_assert_equal(inferno.fighter.armor, base.armor + 22.0, "Inferno round five adds armor")
	_assert_equal(inferno.fighter.hero_state.base_speed, base.hero_state.base_speed + 96.0, "Inferno round five adds attack speed")
	_assert_equal(inferno.fighter.attack_interval_ms < base.attack_interval_ms, true, "added speed shortens actual attack interval")
	_assert_equal(inferno.fighter.max_mp, base.max_mp, "difficulty does not change maximum mana")
	_assert_equal(is_equal_approx(BattleLoadoutBuilder._enemy_scale(0, 0).health, 0.66), true, "Easy first round uses the Android multiplier")
	_assert_equal(inferno.active_items.size() >= 4, true, "Inferno final round reserves four usable actives")
	var inferno_cost := 0
	for slot in inferno.equipment_slots.values():
		inferno_cost += int(AndroidEquipmentCatalog.item(slot.id).price)
	_assert_equal(inferno_cost > 540 and inferno_cost <= 46000, true, "Inferno gear stays within its distinct budget")
	journey.difficulty = 6
	var saved := SaveCodec.encode(preparation, journey)
	_assert_equal(SaveCodec.decode(saved).journey.difficulty, 6, "difficulty survives save restore")
	saved.journey.erase("difficulty")
	_assert_equal(SaveCodec.decode(saved).journey.difficulty, 0, "old saves default to the first difficulty")
	_assert_equal(JourneyRules.begin_battle(journey).accepted, true, "battle node can start a real match")
	_assert_equal(JourneyRules.complete_battle(preparation, journey, true).accepted, true, "victory resolves node")
	_assert_equal(journey.loot.is_empty(), false, "victory produces ground loot")
	JourneyRules.advance_appraisal(journey, 10000)
	_assert_equal(JourneyRules.leave_node(journey).accepted, true, "appraised victory can leave node")
	_assert_equal(JourneyRules.begin_battle(journey).accepted, false, "event node cannot enter battle scene")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
