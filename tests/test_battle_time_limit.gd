extends SceneTree

func _init() -> void:
	var left := FighterState.new("left", 1000, 0, 1000, 0)
	var right := FighterState.new("right", 1000, 0, 1000, 0)
	var engine := BattleEngine.new(42, left, right)
	engine.time_limit_ms = 300
	engine.timeout_left_loses = true
	engine.step(100)
	var restored := BattleEngine.from_state(engine.export_state())
	assert(restored != null and restored.time_limit_ms == 300 and restored.timeout_left_loses)
	restored.step(200)
	assert(restored.finished() and restored.result().winner_id == "right")
	assert(restored.result().finished_at_ms == 300)
	engine = BattleEngine.new(42, FighterState.new("a", 1000, 0, 1000, 0), FighterState.new("b", 1000, 0, 1000, 0))
	engine.time_limit_ms = 2000000
	engine.step(60100)
	assert(not engine.finished())
	assert(BattleEngine.from_state(engine.export_state()) != null)
	print("Configurable tower battle time limit and save roundtrip passed")
	quit()
