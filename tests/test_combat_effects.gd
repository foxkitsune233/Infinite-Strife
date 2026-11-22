extends SceneTree

func _init() -> void:
	_test_critical_strike_and_lifesteal_use_actual_damage()
	_test_dot_ticks_at_fixed_intervals()
	_test_active_equipment_applies_effect_and_respects_cooldown()
	_test_unified_runtime_applies_battle_start_and_hit_actions()
	_test_slow_and_haste_change_attack_schedule()
	quit(0)

func _test_critical_strike_and_lifesteal_use_actual_damage() -> void:
	var left := FighterState.new("left", 500, 100, 1000, 0)
	left.hp = 400
	left.critical_chance_percent = 100
	left.lifesteal_percent = 20
	var right := FighterState.new("right", 500, 0, 1000, 0)
	var engine := BattleEngine.new(9, left, right)
	var events := engine.step(1000)
	_assert_equal(engine.snapshot()["right"]["hp"], 300, "guaranteed critical must double a physical attack")
	_assert_equal(engine.snapshot()["left"]["hp"], 440, "lifesteal must heal from applied health damage")
	_assert_equal(events[0]["critical"], true, "combat log must identify critical strikes")

func _test_dot_ticks_at_fixed_intervals() -> void:
	var engine := _zero_damage_engine()
	var result: Dictionary = engine.command({
		"kind": "apply_dot", "target_id": "right", "source_id": "left",
		"effect_id": "ember_burn", "damage": 15, "duration_ms": 2500, "interval_ms": 1000,
	})
	_assert_equal(result["accepted"], true, "valid damage-over-time effect must be accepted")
	engine.step(2000)
	_assert_equal(engine.snapshot()["right"]["hp"], 470, "two whole DOT intervals must apply two ticks")

func _test_active_equipment_applies_effect_and_respects_cooldown() -> void:
	var engine := _zero_damage_engine()
	var item := {"id": "ward_totem", "kind": "shield", "shield": 90, "cooldown_ms": 1200}
	var first: Dictionary = engine.command({"kind": "use_active", "source_id": "left", "item": item})
	_assert_equal(first["accepted"], true, "ready active item must activate")
	_assert_equal(engine.snapshot()["left"]["shield"], 90, "shield active must grant shield")
	var second: Dictionary = engine.command({"kind": "use_active", "source_id": "left", "item": item})
	_assert_equal(second["reason"], "cooldown", "active item must reject repeated activation during cooldown")

func _test_unified_runtime_applies_battle_start_and_hit_actions() -> void:
	var engine := _zero_damage_engine()
	engine.register_combat_effects("left", [{"source_key": "gear:shield", "trigger": "BATTLE_START", "action": "GRANT_SHIELD", "amount": 55}])
	_assert_equal(engine.snapshot()["left"]["shield"], 55, "unified battle-start action must grant its shield")

func _test_slow_and_haste_change_attack_schedule() -> void:
	var slowed_left := FighterState.new("left", 500, 1, 1000, 0)
	slowed_left.add_status("slow", 5000)
	var slowed := BattleEngine.new(3, slowed_left, FighterState.new("right", 500, 0, 1000, 0))
	slowed.step(2200)
	_assert_equal(_attack_count(slowed.full_log(), "left"), 1, "slow must postpone the second attack")
	var hasted_left := FighterState.new("left", 500, 1, 1000, 0)
	hasted_left.add_status("haste", 5000)
	var hasted := BattleEngine.new(3, hasted_left, FighterState.new("right", 500, 0, 1000, 0))
	hasted.step(1800)
	_assert_equal(_attack_count(hasted.full_log(), "left"), 2, "haste must bring the second attack forward")

func _zero_damage_engine() -> BattleEngine:
	return BattleEngine.new(3, FighterState.new("left", 500, 0, 1000, 0), FighterState.new("right", 500, 0, 1000, 0))

func _attack_count(log: Array[Dictionary], source_id: String) -> int:
	var count := 0
	for event in log:
		if event["kind"] == "physical_attack" and event["source_id"] == source_id:
			count += 1
	return count

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
