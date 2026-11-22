extends SceneTree

const Combat = preload("res://scripts/tower/TowerCombat.gd")

func _init() -> void:
	var state := PreparationState.new("skywrath")
	state.hero_levels["juggernaut"] = 12
	var run := {"hero_id": "juggernaut", "floor": 10, "rng": 1729, "current_hp": 100.0, "tower_primary_bonus": 8, "pending_supply_regen": true}
	var build := Combat.build(state, run)
	assert(build.left.fighter.hero_id == "juggernaut" and build.left.fighter.hp == 100.0)
	assert(build.right.fighter.hero_id == "juggernaut" and build.right.fighter.id != build.left.fighter.id)
	var naked := preload("res://scripts/battle/BattleLoadoutBuilder.gd").build(PreparationState.new("juggernaut"))
	assert(build.right.fighter.max_hp == roundi(naked.fighter.max_hp * 2.08))
	assert(build.right.fighter.attack_damage == roundi(naked.fighter.attack_damage * 1.72))
	assert(state.active_hero_id == "skywrath")
	var ninth := Combat.build(state, {"hero_id": "juggernaut", "floor": 9, "current_hp": null})
	assert(ninth.right.fighter.hero_id == "abaddon" and ninth.right.fighter.attack_damage == 86)
	var engine := BattleEngine.new(1729, build.left.fighter, build.right.fighter)
	Combat.configure(engine, true)
	var base := engine._left.health_regen
	engine.step(100)
	assert(is_equal_approx(engine._left.health_regen, base + engine._left.max_hp * 0.01))
	assert(engine.time_limit_ms == 2000000 and engine.timeout_left_loses)
	# Supply must heal during tick 0, then stop before tick 600.
	var healer := FighterState.new("healer", 1000, 0, 999999, 0)
	healer.hero_id = "juggernaut"
	healer.hp = 100
	healer.health_regen = 0
	var dummy := FighterState.new("dummy", 1000, 0, 999999, 0)
	engine = BattleEngine.new(2, healer, dummy)
	Combat.configure(engine, true)
	engine.step(100)
	assert(is_equal_approx(healer.hp, 101.0))
	engine._elapsed_ms = 60000
	engine.step(100)
	assert(is_equal_approx(healer.hp, 101.0) and healer.health_regen == 0)
	print("Tower locked hero, mirror opponent, scale and supply passed")
	quit()
