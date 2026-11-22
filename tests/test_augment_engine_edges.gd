extends SceneTree

const Fighter = preload("res://scripts/battle/FighterState.gd")
const EngineScript = preload("res://scripts/battle/BattleEngine.gd")
var failures := 0
var checks := 0

func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(label)

func unit(id: String, hero: String = "", attack := 0) -> FighterState:
	var fighter := Fighter.new(id, 1000, attack, 10000, 0, 1000)
	fighter.hero_id = hero
	return fighter

func cast_spell(engine: BattleEngine, damage: int, stun_ms: int) -> Dictionary:
	return engine.command({"kind": "cast_skill", "source_id": "left", "skill": {
		"id": "edge_spell", "magic_damage": damage, "stun_ms": stun_ms,
		"mana_cost": 25, "cooldown_ms": 3000,
	}})

func reflection_count(engine: BattleEngine) -> int:
	var count := 0
	for event in engine.full_log():
		if event.kind == "skill_reflected": count += 1
	return count

func _init() -> void:
	_test_damage_and_control()
	_test_pure_control()
	_test_reflected_caster_lethal()
	_test_dot_excluded()
	_test_void_initial_history()
	_test_void_post_attack_history()
	print("Augment engine edges: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)

func _test_damage_and_control() -> void:
	var caster := unit("caster")
	var defender := unit("defender", "anti_mage")
	defender.hero_state.has_shard = true
	var engine := EngineScript.new(42, caster, defender)
	check(cast_spell(engine, 100, 1000).accepted, "damage/control cast accepted")
	check(caster.hp == 900 and defender.hp == 1000, "damage/control reflects damage")
	check(caster.has_status("stun") and not defender.has_status("stun"), "damage/control reflects stun with damage")
	check(caster.mp == 975 and int(caster.cooldowns.get("edge_spell", 0)) == 3000, "reflected cast charges original caster once")
	check(reflection_count(engine) == 1, "damage/control reflection resolves once")

func _test_pure_control() -> void:
	var caster := unit("caster")
	var defender := unit("defender", "anti_mage")
	defender.hero_state.has_shard = true
	var engine := EngineScript.new(42, caster, defender)
	check(cast_spell(engine, 0, 1000).accepted, "pure control cast accepted")
	check(caster.has_status("stun") and not defender.has_status("stun"), "pure control reflects without damage")
	check(bool(defender.hero_state.get("shard_reflect_used", false)), "pure control spends first reflection")
	check(reflection_count(engine) == 1, "pure control emits one reflection")

func _test_reflected_caster_lethal() -> void:
	var caster := unit("caster")
	caster.hp = 50
	var defender := unit("defender", "anti_mage")
	defender.hero_timers.aghanim_reflect = 500
	var engine := EngineScript.new(42, caster, defender)
	check(cast_spell(engine, 100, 0).accepted, "lethal reflected cast accepted")
	check(caster.hp == 0 and defender.hp == 1000, "reflection kills original caster only")
	check(engine.finished(), "caster death finishes battle immediately during command")
	check(engine.result().get("winner_id", "") == defender.id, "reflected caster death awards defender victory")
	check(engine.result().get("finished_at_ms", -1) == 0, "reflected defeat records command timestamp")

func _test_dot_excluded() -> void:
	var caster := unit("caster")
	var defender := unit("defender", "anti_mage")
	defender.hero_state.has_shard = true
	var engine := EngineScript.new(42, caster, defender)
	check(engine.command({"kind": "apply_dot", "source_id": "left", "target_id": "right",
		"effect_id": "arbitrary_poison_name", "damage": 100, "duration_ms": 1000, "interval_ms": 1000}).accepted, "DOT command accepted")
	engine.step(1000)
	check(is_equal_approx(defender.hp, 915), "DOT damages AM with passive resistance")
	check(caster.hp == 1000, "DOT is never reflected onto caster")
	check(not bool(defender.hero_state.get("shard_reflect_used", false)), "DOT preserves first skill reflection")
	check(reflection_count(engine) == 0, "DOT emits no reflection event")

func _test_void_initial_history() -> void:
	var source := unit("void", "faceless_void")
	source.hp = 725
	source.hero_state.has_aghanim = true
	var engine := EngineScript.new(42, source, unit("enemy"))
	var history: Array = source.hero_state.get("aghanim_hp_history", [])
	check(history.size() == 1 and int(history[0].get("at_ms", -1)) == 0 and float(history[0].get("hp", 0)) == 725,
		"Void history initialized at battle time zero")
	var restored: BattleEngine = EngineScript.from_state(JSON.parse_string(JSON.stringify(engine.export_state())))
	check(restored != null, "initial Void history snapshot restores")
	if restored != null:
		check(restored.snapshot().left.hero_state.get("aghanim_hp_history", []) == history, "restore preserves initial history without reseeding")

func _test_void_post_attack_history() -> void:
	var source := unit("void", "faceless_void")
	source.hero_state.has_aghanim = true
	var attacker := unit("attacker", "anti_mage", 100)
	var engine := EngineScript.new(42, source, attacker)
	engine.step(100)
	check(source.hp == 900, "Void took scheduled attack at 100ms")
	var history: Array = source.hero_state.get("aghanim_hp_history", [])
	var sampled_hp := -1.0
	for sample in history:
		if int(sample.at_ms) == 100: sampled_hp = float(sample.hp)
	check(sampled_hp == 900, "100ms history contains HP after 100ms attacks")
	engine.step(2000)
	var restored: BattleEngine = EngineScript.from_state(JSON.parse_string(JSON.stringify(engine.export_state())))
	check(restored != null, "post attack history JSON snapshot restores")
	check(engine.command({"kind": "cast_aghanim", "source_id": "left"}).accepted, "Void rewind accepted at 2100ms")
	check(source.hp == 900, "2100ms rewind restores completed 100ms state")
	if restored != null:
		check(restored.command({"kind": "cast_aghanim", "source_id": "left"}).accepted, "restored Void rewind accepted")
		check(restored.snapshot().left.hp == 900, "restored rewind also uses post attack sample")
