extends SceneTree

const Levels = preload("res://scripts/progression/HeroLevelRules.gd")
const Codec = preload("res://scripts/save/SaveCodec.gd")

func _init() -> void:
	assert(Levels.upgrade_cost(2) == 120, "Android exponential fee replaces linear fee")
	assert(Levels.upgrade_cost(0) == 100)
	assert(Levels.upgrade_cost(-10) == 100)
	assert(Levels.upgrade_cost(9) == 430)
	assert(Levels.upgrade_cost(10) == 516)
	assert(Levels.upgrade_cost(100) == 2147483647)
	var state := PreparationState.new("juggernaut")
	state.gold = 100
	assert(Levels.upgrade(state, "juggernaut").accepted)
	assert(state.gold == 0 and Levels.level(state, "juggernaut") == 2)
	assert(not Levels.upgrade(state, "juggernaut").accepted)
	assert(state.gold == 0 and Levels.level(state, "juggernaut") == 2)
	assert(not Levels.upgrade(state, "unknown").accepted)
	state.gold = 10000
	state.phase = PreparationState.Phase.JOURNEY
	assert(not Levels.upgrade(state, "juggernaut").accepted)
	state.phase = PreparationState.Phase.HOME
	state.hero_levels["juggernaut"] = 10
	assert(not Levels.upgrade(state, "juggernaut").accepted)
	var bonus := Levels.bonuses(10)
	assert(is_equal_approx(bonus.attack_multiplier, 1.155))
	assert(is_equal_approx(bonus.health_multiplier, 1.20))
	assert(is_equal_approx(bonus.armor_bonus, 3.6))
	assert(is_equal_approx(bonus.magic_resist_bonus, 6.5))
	var encoded := Codec.encode(state, JourneyState.new(7001))
	var decoded := Codec.decode(encoded)
	assert(decoded.accepted and decoded.preparation.hero_levels["juggernaut"] == 10)
	encoded.schema_version = 1
	encoded.preparation.erase("hero_levels")
	encoded.preparation.erase("quest_progress")
	assert(Codec.decode(encoded).preparation.hero_levels.is_empty())
	_test_dynamic_cap()
	print("Hero levels: exponential fees, bounds, tower caps, locked heroes, affordability, phase, bonuses and restore passed")
	quit(0)

func _test_dynamic_cap() -> void:
	var state := PreparationState.new("juggernaut")
	assert(Levels.level_cap(state) == 10)
	assert(Levels.level(state, "juggernaut") == 1)
	for entry in [[-1, 10], [0, 10], [1, 10], [2, 15], [3, 20], [18, 95], [19, 100], [20, 100], [100, 100]]:
		state.quest_progress["tower_highest_floor"] = entry[0]
		assert(Levels.level_cap(state) == entry[1])
	state.quest_progress["tower_highest_floor"] = 2
	state.hero_levels["juggernaut"] = 10
	state.gold = 515
	assert(not Levels.upgrade(state, "juggernaut").accepted)
	assert(state.gold == 515 and Levels.level(state, "juggernaut") == 10)
	state.gold = 516
	assert(Levels.upgrade(state, "juggernaut").accepted)
	assert(state.gold == 0 and Levels.level(state, "juggernaut") == 11)
	state.gold = 2147483647
	state.hero_levels["juggernaut"] = 15
	assert(not Levels.upgrade(state, "juggernaut").accepted)
	state.quest_progress["level_cap"] = 20
	assert(Levels.level_cap(state) == 20)
	assert(Levels.upgrade(state, "juggernaut").accepted)
	assert(Levels.level(state, "juggernaut") == 16)
	state.unlocked_heroes.erase("skywrath")
	var previous_gold := state.gold
	assert(not Levels.upgrade(state, "skywrath").accepted)
	assert(state.gold == previous_gold and not state.hero_levels.has("skywrath"))
	for phase in [PreparationState.Phase.JOURNEY, PreparationState.Phase.BATTLE]:
		state.phase = phase
		assert(not Levels.upgrade(state, "juggernaut").accepted)
		assert(state.gold == previous_gold and Levels.level(state, "juggernaut") == 16)
	state.phase = PreparationState.Phase.HOME
	var bonus := Levels.bonuses(20)
	assert(is_equal_approx(bonus.attack_multiplier, 1.325))
	assert(is_equal_approx(bonus.health_multiplier, 1.42))
	assert(is_equal_approx(bonus.armor_bonus, 7.6))
	assert(is_equal_approx(bonus.magic_resist_bonus, 13.5))
	var decoded := Codec.decode(Codec.encode(state, JourneyState.new(7001)))
	assert(decoded.accepted and Levels.level(decoded.preparation, "juggernaut") == 16)
	assert(Levels.level_cap(decoded.preparation) == 20)
	assert(decoded.preparation.quest_progress.tower_highest_floor == 2)
	var stats = preload("res://scripts/battle/AndroidLoadoutStats.gd")
	var leveled: Dictionary = stats.calculate(decoded.preparation)
	decoded.preparation.hero_levels["juggernaut"] = 1
	var base: Dictionary = stats.calculate(decoded.preparation)
	assert(is_equal_approx(leveled.hp / base.hp, 1.33), "battle stats retain levels above ten after restore")
	assert(is_equal_approx(leveled.attack / base.attack, 1.255))
	state.quest_progress["level_cap"] = 0
	assert(Levels.level_cap(state) == 1)
	state.quest_progress["level_cap"] = 101
	assert(Levels.level_cap(state) == 100)
	state.hero_levels["juggernaut"] = 99
	state.gold = 2147483647
	assert(Levels.upgrade(state, "juggernaut").accepted)
	assert(state.gold == 0 and Levels.level(state, "juggernaut") == 100)
	assert(not Levels.upgrade(state, "juggernaut").accepted)
