extends SceneTree

const Fighter = preload("res://scripts/battle/FighterState.gd")
const EngineScript = preload("res://scripts/battle/BattleEngine.gd")

var failed := false

func _init() -> void:
	var negative := Fighter.new("negative", 1000, 0, 1000, -10)
	_equal(negative.receive_physical_damage(100), 138, "negative armor follows Android abs-denominator formula")
	if not _has_property(negative, "hero_id"):
		_check(false, "FighterState exposes opt-in hero_id")
		quit(1)
		return
	_run()
	if not failed:
		print("PASS: Android hero combat, regeneration, controls, and generic compatibility")
	quit(1 if failed else 0)

func _run() -> void:
	_test_regen_and_precision()
	_test_skills()
	_test_controls()
	_test_passives()
	_test_chunking()
	_test_restore()
	_test_control_and_passive_edges()

func _fighter(hero: String = "", attack: int = 0, hp: int = 10000) -> FighterState:
	var fighter := Fighter.new("unit_" + hero, hp, attack, 1000, 0, 1000)
	fighter.hero_id = hero
	return fighter

func _cast(engine: BattleEngine, side: String = "left", id: String = "adapter_label") -> Dictionary:
	return engine.command({"kind": "cast_skill", "source_id": side, "skill": {"id": id, "runtime_mode": "generic_placeholder", "mana_cost": 0, "cooldown_ms": 0, "magic_damage": 9999}})

func _test_regen_and_precision() -> void:
	var left := _fighter("anti_mage")
	left.hp = 500
	left.mp = 0
	left.health_regen = 2.5
	left.mana_regen = 5.9
	var engine := EngineScript.new(1, left, _fighter())
	engine.step(1000)
	_near(left.hp, 502.5, "fractional HP regeneration")
	_near(left.mp, 5.9, "fractional mana regeneration")
	left.armor = 0.5
	var before := left.hp
	left.receive_android_damage(100.0, "physical")
	_near(before - left.hp, 100.0 / 1.03, "fractional armor retained")
	left.hp = 0
	engine.step(100)
	_near(left.hp, 0, "regeneration never resurrects")

func _test_skills() -> void:
	for hero in ["juggernaut", "skywrath", "phantom_assassin", "abaddon", "necrophos", "viper", "anti_mage", "axe", "faceless_void", "legion_commander"]:
		var left := _fighter(hero, 10)
		var right := _fighter()
		var engine := EngineScript.new(5, left, right)
		var cast := _cast(engine)
		_check(cast.accepted, hero + " casts through arbitrary UI adapter ID")
		_check(left.mp < 1000, hero + " uses Android mana cost, ignores placeholder payload")
		_check(right.hp > 1, hero + " does not execute placeholder damage")
		_equal(_cast(engine, "left", "different_ui_id").reason, "cooldown", hero + " cooldown cannot be bypassed with another adapter ID")
	var jug := _fighter("juggernaut", 10)
	jug.add_status("silence", 500)
	var target := _fighter()
	var duel := EngineScript.new(1, jug, target)
	_equal(_cast(duel).reason, "silenced", "silence blocks juggernaut")
	jug.statuses.clear()
	jug.add_status("slow", 500)
	_cast(duel)
	_check(not jug.has_status("slow"), "omnislash strongly dispels self")
	_near(jug.receive_android_damage(100, "pure").life_damage, 0, "omnislash invulnerability")
	duel.step(1900)
	_check(target.hp < 9990, "omnislash schedules repeated full attacks")
	var sky := _fighter("skywrath")
	target = _fighter()
	duel = EngineScript.new(2, sky, target)
	_cast(duel)
	duel.step(2100)
	_near(target.hp, 9520, "skywrath eight actual timed hits")
	_near(sky.magic_shield, 144, "skywrath converts 30 percent life damage to magical shield")
	var flare_times: Array[int] = []
	for event in duel.full_log():
		if event.effect_id == "hero:skywrath:flare": flare_times.append(int(event.tick_ms))
	_equal(flare_times, [100, 300, 500, 800, 1000, 1300, 1500, 1800], "Skywrath cadence follows signed Android flare countdown")
	var pa := _fighter("phantom_assassin", 100)
	target = _fighter()
	duel = EngineScript.new(3, pa, target)
	_cast(duel)
	duel.step(1000)
	_near(target.hp, 9670, "PA next attack is 3.3x with passive")
	duel.step(1000)
	_near(target.hp, 9570, "PA critical readiness consumed once")
	var aba := _fighter("abaddon")
	aba.add_status("stun", 3000)
	target = _fighter()
	duel = EngineScript.new(4, aba, target)
	_check(_cast(duel).accepted, "Abaddon may cast through stun")
	_check(not aba.has_status("stun"), "Abaddon strong dispel clears stun")
	_near(aba.shield, 180, "Abaddon shield")
	_near(target.hp, 9946, "Abaddon immediate 30 percent shield damage")
	var necro := _fighter("necrophos")
	necro.mana_regen = 10
	necro.health_regen = 10
	necro.hp = 500
	necro.mp = 500
	target = _fighter()
	duel = EngineScript.new(5, necro, target)
	_cast(duel)
	_near(necro.receive_android_damage(100, "physical").life_damage, 0, "ghost blocks physical")
	_near(necro.receive_android_damage(100, "magic").life_damage, 120, "actual Ghost Shroud magic vulnerability")
	duel.step(1000)
	_near(necro.hp, 395, "ghost amplifies healing 50 percent")
	_near(necro.mp, 430, "ghost amplifies mana regeneration 50 percent")
	_near(target.hp, 9920, "heartstopper removes 0.8 percent max HP per second")

func _test_controls() -> void:
	var axe := _fighter("axe")
	var target := _fighter("anti_mage", 10)
	var engine := EngineScript.new(1, axe, target)
	_cast(engine)
	_check(target.has_status("taunt"), "axe applies TAUNT")
	_check(not target.has_status("stun"), "taunt is not stun")
	_check(_cast(engine, "right").accepted, "Android duel taunt does not block skills")
	_check(engine.command({"kind": "use_active", "source_id": "right", "item": {"id": "x", "kind": "heal"}}).accepted, "Android duel taunt does not block items")
	engine.step(1000)
	_check(axe.hp < 10000, "taunted fighter still attacks")
	_near(axe.effective_armor(), 12, "axe temporary armor")
	engine.step(1500)
	_near(axe.effective_armor(), 0, "axe armor expires")
	var void_hero := _fighter("faceless_void")
	target = _fighter("anti_mage", 100)
	target.cooldowns["hero:anti_mage:ultimate"] = 5000
	target.cooldowns["active:item"] = 5000
	engine = EngineScript.new(2, void_hero, target)
	_cast(engine)
	_check(target.has_status("time_stop"), "void applies TIME_STOP")
	target.dispel(true)
	_check(target.has_status("time_stop"), "time stop survives strong dispel")
	_equal(_cast(engine, "right").reason, "time_stopped", "time stop blocks casts")
	engine.step(2000)
	_equal(target.cooldowns["hero:anti_mage:ultimate"], 5000, "time stop pauses ultimate cooldown")
	_equal(target.cooldowns["active:item"], 5000, "time stop pauses item cooldown")
	_near(void_hero.hp, 10000, "time stop prevents attacks")
	engine.step(1000)
	_check(not target.has_status("time_stop"), "time stop expires on battle clock")
	engine.step(500)
	_equal(target.cooldowns["hero:anti_mage:ultimate"], 4500, "cooldowns resume after time stop")

func _test_passives() -> void:
	var am := _fighter("anti_mage", 10)
	var target := _fighter()
	var engine := EngineScript.new(1, am, target)
	engine.step(1000)
	_near(target.mp, 980, "AM burns current mana on landed attack")
	engine.step(1000)
	_near(target.mp, 960.4, "AM burn preserves fractional current-mana calculation")
	var hp_before := target.hp
	_cast(engine)
	_near(hp_before - target.hp, 123.76, "mana void uses actual cumulative burn")
	_check(target.has_status("stun"), "mana void stuns")
	var axe := _fighter("axe")
	var attacker := _fighter("", 10)
	engine = EngineScript.new(2, attacker, axe)
	engine.step(4000)
	_near(attacker.hp, 9945, "axe fourth damaging basic attack triggers pure spin")
	var viper := _fighter("viper")
	target = _fighter()
	engine = EngineScript.new(3, target, viper)
	engine.command({"kind": "cast_skill", "source_id": "left", "skill": {"id": "generic", "magic_damage": 100}})
	_near(viper.hp, 9915, "viper independent 15 percent magic resistance")
	_check(not target.hero_poisons.is_empty(), "viper retaliatory normal poison")
	engine = EngineScript.new(4, viper, _fighter())
	_cast(engine)
	_check(engine.snapshot().right.statuses.has("break"), "viper ultimate applies break")
	var enemy := engine._right
	engine.step(1000)
	_near(enemy.hp, 10000, "virulent poison spawns without immediate damage")
	engine.step(1000)
	_near(enemy.hp, 9997, "virulent poison spawns ordinary three-tick poison")
	var legion := _fighter("legion_commander", 10)
	legion.hp = 500
	target = _fighter()
	engine = EngineScript.new(11, legion, target)
	_cast(engine)
	_check(target.has_status("taunt"), "duel applies taunt")
	_check(legion.effective_attack_interval_ms() < 1000, "duel grants attack speed")
	engine.step(3000)
	_check(legion.hp > 500, "legion extra-attack proc heals")
	var found_lock := false
	var found_crit := false
	for seed_value in range(1, 70):
		var void_hero := _fighter("faceless_void", 10)
		target = _fighter("anti_mage")
		target.cooldowns["hero:anti_mage:ultimate"] = 10000
		engine = EngineScript.new(seed_value, void_hero, target)
		engine.step(1000)
		for event in engine.full_log():
			if event.kind == "cooldown_extended":
				found_lock = true
				_equal(target.cooldowns["hero:anti_mage:ultimate"], 10000, "time lock extends only active ultimate cooldown")
		var jug := _fighter("juggernaut", 100)
		target = _fighter()
		engine = EngineScript.new(seed_value, jug, target)
		engine.step(5000)
		for event in engine.full_log():
			if event.kind == "physical_attack" and event.source_id == jug.id and event.critical:
				found_crit = true
	_check(found_lock, "fixed seeds exercise void time lock")
	_check(found_crit, "fixed seeds exercise juggernaut PRD critical")

func _test_chunking() -> void:
	var a := _fighter("skywrath")
	var b := _fighter("skywrath")
	var first := EngineScript.new(5, a, _fighter())
	var second := EngineScript.new(5, b, _fighter())
	_cast(first)
	_cast(second)
	first.step(2100)
	for index in 21:
		second.step(100)
	_equal(first.full_log(), second.full_log(), "Android progression invariant to whole 100ms chunking")
	_equal(first.snapshot(), second.snapshot(), "Android timers invariant to whole 100ms chunking")

func _test_restore() -> void:
	var engine_script = load("res://scripts/battle/BattleEngine.gd")
	var left := _fighter("juggernaut", 20)
	left.hp = 500.25
	left.mp = 222.125
	left.health_regen = 0.75
	left.mana_regen = 1.125
	var right := _fighter("viper", 15)
	var original := EngineScript.new(1729, left, right)
	_cast(original)
	_cast(original, "right")
	original.step(1250)
	if not original.has_method("export_state"):
		_check(false, "engine exports full resumable state")
		return
	var saved: Dictionary = original.call("export_state")
	# Exercise actual JSON number/array conversion, not only dictionary copies.
	var restored: BattleEngine = engine_script.from_state(JSON.parse_string(JSON.stringify(saved)))
	_check(restored != null, "engine restores JSON snapshot")
	if restored == null: return
	_equal(restored.snapshot(), original.snapshot(), "restored fractional fighters and timers")
	_equal(restored.step(5750), original.step(5750), "resume keeps RNG and remaining attack schedule")
	_equal(restored.export_state(), original.export_state(), "resume preserves full runtime, log and pending tick fraction")
	saved.payload = "detached copy"
	_check(original.snapshot().left.hp != 1, "export is detached from live battle")
	var generic := EngineScript.new(9, _fighter("", 100), _fighter("", 50))
	generic._left.critical_chance_percent = 35
	generic.step(1200)
	var generic_copy: BattleEngine = engine_script.from_state(JSON.parse_string(JSON.stringify(generic.export_state())))
	_equal(generic_copy.step(5000), generic.step(5000), "generic RNG also resumes without reroll")
	var lethal := EngineScript.new(1, _fighter("juggernaut", 200), _fighter("", 0, 50))
	lethal.step(1000)
	var finished_copy: BattleEngine = engine_script.from_state(lethal.export_state())
	_equal(finished_copy.result(), lethal.result(), "finished battle result preserved")
	_equal(finished_copy.step(1000), [], "finished restored match cannot advance")
	_equal(engine_script.from_state({}), null, "incomplete snapshot rejected without restarting battle")
	for hero in ["juggernaut", "skywrath", "phantom_assassin", "abaddon", "necrophos", "viper", "anti_mage", "axe", "faceless_void", "legion_commander"]:
		var actor := _fighter(hero, 13)
		actor.hp = 1000.125
		actor.health_regen = 0.625
		actor.mana_regen = 0.875
		var battle := EngineScript.new(7001, actor, _fighter("", 3))
		_cast(battle)
		battle.step(350)
		var copy: BattleEngine = engine_script.from_state(JSON.parse_string(JSON.stringify(battle.export_state())))
		_check(copy != null, hero + " snapshot restores")
		if copy == null: continue
		_equal(copy.step(4000), battle.step(4000), hero + " runtime/poison/control continuation")
		_equal(copy.export_state(), battle.export_state(), hero + " exact resumed state")

func _test_control_and_passive_edges() -> void:
	var aba := _fighter("abaddon", 10)
	var target := _fighter()
	var engine := EngineScript.new(1, aba, target)
	engine.step(100)
	_check(not target.hero_poisons.is_empty(), "Abaddon basic attack applies curse")
	_check(aba.effective_attack_interval_ms() < 1000, "curse boosts Abaddon speed")
	var damage_before := target.hp
	engine.step(500)
	_near(damage_before - target.hp, 6.0, "curse deals continuous twelve magic damage per second")
	var void_hero := _fighter("faceless_void")
	target = _fighter("anti_mage")
	target.mp = 0
	target.mana_regen = 2.5
	target.hp = 500
	target.health_regen = 1.5
	engine = EngineScript.new(2, void_hero, target)
	_cast(engine)
	engine.step(1000)
	_near(target.hp, 501.5, "time stop does not pause health regeneration")
	_near(target.mp, 2.5, "time stop does not pause mana regeneration")
	var am := _fighter("anti_mage")
	target = _fighter()
	target.add_status("debuff_immune", 3000)
	engine = EngineScript.new(3, am, target)
	_cast(engine)
	_check(not target.has_status("stun"), "debuff immunity rejects hero control")
	var necro := _fighter("necrophos")
	target = _fighter()
	target.shield = 100
	target.magic_resistance = 0.25
	engine = EngineScript.new(4, necro, target)
	engine.step(1000)
	_near(target.hp, 10000, "actual HP-removal pipeline still respects shields")
	_near(target.shield, 40, "heartstopper uses actual magic mitigation, not pure damage")
	necro.add_status("break", 1000)
	engine.step(500)
	_near(target.shield, 40, "break suppresses heartstopper")
	var axe := _fighter("axe")
	var attacker := _fighter("", 100, 10)
	engine = EngineScript.new(5, attacker, axe)
	engine.step(4000)
	_near(attacker.hp, 1, "actual helix self-damage flag caps target HP at one")

func _has_property(object: Object, name: String) -> bool:
	for property in object.get_property_list():
		if property.name == name:
			return true
	return false

func _near(actual: float, expected: float, label: String) -> void:
	_check(absf(actual - expected) < 0.000001, "%s expected=%s actual=%s" % [label, expected, actual])

func _equal(actual: Variant, expected: Variant, label: String) -> void:
	_check(actual == expected, "%s expected=%s actual=%s" % [label, expected, actual])

func _check(value: bool, label: String) -> void:
	if not value:
		failed = true
		push_error(label)
