extends SceneTree

func _init() -> void:
	_test_shield_absorbs_before_health()
	_test_skill_spends_mana_and_starts_cooldown()
	_test_silence_rejects_skill_cast()
	_test_stun_expires_after_its_duration()
	quit(0)

func _test_shield_absorbs_before_health() -> void:
	var engine := _engine(100, 80)
	engine.step(1000)
	var right: Dictionary = engine.snapshot()["right"]
	_assert_equal(right["shield"], 0, "shield must absorb damage first")
	_assert_equal(right["hp"], 480, "only damage beyond the shield may reduce health")

func _test_skill_spends_mana_and_starts_cooldown() -> void:
	var engine := _engine(100, 0)
	var result: Dictionary = engine.command({
		"kind": "cast_skill",
		"source_id": "left",
		"skill": {"id": "arc_burst", "mana_cost": 30, "cooldown_ms": 1200, "magic_damage": 50, "stun_ms": 500},
	})
	_assert_equal(result["accepted"], true, "a ready skill with sufficient mana must cast")
	var snapshot: Dictionary = engine.snapshot()
	_assert_equal(snapshot["left"]["mp"], 70, "casting must spend mana")
	_assert_equal(snapshot["left"]["cooldowns"]["arc_burst"], 1200, "casting must begin cooldown")
	_assert_equal(snapshot["right"]["hp"], 450, "magic damage must reach the target")

func _test_silence_rejects_skill_cast() -> void:
	var engine := _engine(100, 0)
	engine.command({"kind": "apply_status", "target_id": "left", "status": "silence", "duration_ms": 500})
	var result: Dictionary = engine.command({
		"kind": "cast_skill",
		"source_id": "left",
		"skill": {"id": "arc_burst", "mana_cost": 30, "cooldown_ms": 1200, "magic_damage": 50},
	})
	_assert_equal(result["accepted"], false, "silenced fighter must not cast")
	_assert_equal(result["reason"], "silenced", "silence rejection must be explicit")

func _test_stun_expires_after_its_duration() -> void:
	var engine := _engine(100, 0)
	engine.command({"kind": "apply_status", "target_id": "left", "status": "stun", "duration_ms": 250})
	engine.step(200)
	_assert_equal(engine.snapshot()["left"]["statuses"]["stun"], 50, "status duration must tick down")
	engine.step(100)
	_assert_equal(engine.snapshot()["left"]["statuses"].has("stun"), false, "expired status must be removed")

func _engine(left_mp: int, right_shield: int) -> BattleEngine:
	var left := FighterState.new("left", 500, 100, 1000, 0, left_mp)
	var right := FighterState.new("right", 500, 1, 1000, 0, 0, right_shield)
	return BattleEngine.new(1, left, right)

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
