extends SceneTree

func _init() -> void:
	_test_same_seed_produces_same_log()
	_test_armor_twelve_reduces_one_hundred_damage_to_fifty_eight()
	_test_attack_can_kill_a_fighter()
	_test_match_times_out_at_sixty_seconds()
	_test_snapshot_exposes_display_state_without_mutable_fighters()
	quit(0)

func _test_same_seed_produces_same_log() -> void:
	var first := _simulate(7001)
	var second := _simulate(7001)
	_assert_equal(first, second, "same seed must produce the same battle log")

func _test_armor_twelve_reduces_one_hundred_damage_to_fifty_eight() -> void:
	var left := FighterState.new("ember", 500, 100, 1000, 0)
	var right := FighterState.new("tide", 500, 1, 1000, 12)
	var engine := BattleEngine.new(7, left, right)
	var events := engine.step(1000)
	for event in events:
		if event.source_id == "ember":
			_assert_equal(event.amount, 58, "armor 12 must use the documented physical multiplier")
			return
	push_error("expected an attack from ember")
	quit(1)

func _test_attack_can_kill_a_fighter() -> void:
	var left := FighterState.new("ember", 100, 200, 1000, 0)
	var right := FighterState.new("tide", 100, 1, 1000, 0)
	var engine := BattleEngine.new(9, left, right)
	engine.step(1000)
	_assert_equal(engine.finished(), true, "lethal attack must finish the match")
	_assert_equal(engine.result().winner_id, "ember", "killer must win")

func _test_match_times_out_at_sixty_seconds() -> void:
	var left := FighterState.new("ember", 99999, 1, 1000, 0)
	var right := FighterState.new("tide", 99999, 1, 1000, 0)
	var engine := BattleEngine.new(11, left, right)
	engine.step(60000)
	_assert_equal(engine.finished(), true, "match must finish at sixty seconds")
	_assert_equal(engine.result().kind, "timeout", "sixty-second result must be a timeout")

func _test_snapshot_exposes_display_state_without_mutable_fighters() -> void:
	var engine := BattleEngine.new(13, FighterState.new("ember", 300, 80, 1000, 0), FighterState.new("tide", 300, 1, 1000, 0))
	engine.step(1000)
	var snapshot: Dictionary = engine.snapshot()
	_assert_equal(snapshot.elapsed_ms, 1000, "snapshot must expose elapsed match time")
	_assert_equal(snapshot.left.id, "ember", "snapshot must identify the left fighter")
	_assert_equal(snapshot.left.hp, 299, "snapshot must expose current left health")

func _simulate(seed: int) -> Array[Dictionary]:
	var left := FighterState.new("ember", 800, 70, 900, 4)
	var right := FighterState.new("tide", 820, 64, 1000, 7)
	var engine := BattleEngine.new(seed, left, right)
	var log: Array[Dictionary] = []
	while not engine.finished():
		log.append_array(engine.step(100))
	return log

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
