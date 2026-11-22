extends SceneTree

const Stats = preload("res://scripts/battle/AndroidLoadoutStats.gd")

func _init() -> void:
	for hero in ["abaddon", "skywrath", "necrophos", "juggernaut"]:
		var state := PreparationState.new(hero)
		var base := Stats.calculate(state)
		var forged := Stats.calculate(state, 8)
		if hero == "abaddon":
			assert(is_equal_approx(forged.hp - base.hp, 22.0 * 8))
			assert(is_equal_approx(forged.attack - base.attack, 8 * 0.45))
		elif hero in ["skywrath", "necrophos"]:
			assert(is_equal_approx(forged.mana - base.mana, 12.0 * 8))
			assert(is_equal_approx(forged.attack - base.attack, 8.0))
		else:
			assert(forged.interval < base.interval)
			assert(is_equal_approx(forged.attack - base.attack, 8.0))
		assert(preload("res://scripts/battle/BattleLoadoutBuilder.gd").build(state, 8).fighter.max_hp == roundi(forged.hp))
	print("Tower forging primary attributes follow Android hero formulas")
	quit()
