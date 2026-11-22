extends SceneTree

const Runtime = preload("res://scripts/combat/runtime/CombatEffectRuntime.gd")

func _init() -> void:
	var runtime := Runtime.new()
	runtime.register_effects("left", [
		{"source_key": "gear:a:shield", "trigger": "BATTLE_START", "action": "GRANT_SHIELD", "amount": 40},
		{"source_key": "card:a:burn", "trigger": "ON_HIT", "action": "APPLY_DOT", "damage": 8, "duration_ms": 1000, "interval_ms": 500, "internal_cooldown_ms": 800},
	])
	var opened: Array[Dictionary] = runtime.fire("left", "BATTLE_START", 0)
	assert(opened.size() == 1 and opened[0].action == "GRANT_SHIELD")
	assert(runtime.fire("left", "ON_HIT", 0).size() == 1)
	assert(runtime.fire("left", "ON_HIT", 400).is_empty())
	assert(runtime.fire("left", "ON_HIT", 800).size() == 1)
	quit(0)
