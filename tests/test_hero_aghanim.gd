extends SceneTree

const Fighter = preload("res://scripts/battle/FighterState.gd")
const Runtime = preload("res://scripts/battle/AndroidHeroRuntime.gd")
var failed := false

func check(value: bool, label: String) -> void:
	if not value:
		failed = true
		push_error(label)

func fighter(hero: String) -> FighterState:
	var f := Fighter.new(hero, 10000, 100, 1000, 0, 1000)
	f.hero_id = hero
	f.hero_state.has_aghanim = true
	return f

func _init() -> void:
	if not ResourceLoader.exists("res://scripts/battle/HeroAghanimRuntime.gd"):
		check(false, "Aghanim runtime must exist")
		quit(1)
		return
	var agh = load("res://scripts/battle/HeroAghanimRuntime.gd")
	var runtime := Runtime.new(42)
	for hero in ["juggernaut", "skywrath", "abaddon", "phantom_assassin", "viper", "necrophos", "anti_mage", "faceless_void", "axe", "legion_commander"]:
		var src := fighter(hero)
		var tgt := fighter("axe")
		src.hp = 8000
		src.mp = 0
		src.cooldowns.test = 9999
		for control in ["stun", "hex", "silence"]:
			src.add_status(control, 1000)
			check(not agh.cast(runtime, src, tgt, 0).accepted, hero + " control denied " + control)
			check(not src.hero_state.get("aghanim_used", false), "failed control preserves use")
			src.statuses.clear()
		src.hero_state.has_aghanim = false
		check(not agh.cast(runtime, src, tgt, 0).accepted, "unowned denied")
		src.hero_state.has_aghanim = true
		var result: Dictionary = agh.cast(runtime, src, tgt, 0)
		check(result.accepted, hero + " cast accepted")
		check(result.event.kind == "skill_cast" and result.event.skill_id == "aghanim", "event contract")
		check(src.mp == 0 and src.cooldowns.test == 9999, "no mana or CD consumption")
		check(not agh.cast(runtime, src, tgt, 0).accepted, "once per fight")
		check(not agh.description(hero).is_empty(), "description")
		check(Fighter.from_data(src.to_data()).hero_state.aghanim_used, "save preserves use")
		match hero:
			"juggernaut": check(src.timer("invulnerable") == 1000 and tgt.hp < 10000 and src.effective_attack_interval_ms() <= 100, "mini omni")
			"skywrath": check(tgt.has_status("silence"), "silence")
			"abaddon":
				src.hp = 10
				src.receive_android_damage(100, "pure")
				check(src.is_alive() and src.timer("aghanim_borrowed") == 1000, "lethal interception")
				src.receive_android_damage(50, "pure")
				check(src.hp == 1, "deferred healing")
				src.advance_time(1000)
				check(src.hp == 51, "settled survival")
				src.receive_android_damage(100, "pure")
				check(not src.is_alive(), "borrowed window does not rearm")
			"phantom_assassin": check(tgt.hp == 8350 and src.timer("aghanim_crit_lock") == 5000, "five guaranteed criticals")
			"viper":
				check(src.receive_android_damage(100, "pure").life_damage == 70, "viper reduction")
			"necrophos": check(tgt.hp == 9800 and src.hp == 8200, "pulse damage heals actual life loss")
			"anti_mage": check(src.timer("aghanim_reflect") == 500, "reflection window")
			"axe": check(tgt.hp == 9600, "400 pure")
			"legion_commander": check(tgt.hp == 9850 and src.effective_attack_interval_ms() < 1000, "legion damage and speed")
	var void_unit := fighter("faceless_void")
	var enemy := fighter("axe")
	void_unit.hp = 7000
	agh.advance(runtime, void_unit, enemy, 100, 100, true)
	void_unit.hp = 3000
	agh.advance(runtime, void_unit, enemy, 100, 2100, true)
	void_unit.add_status("slow", 5000)
	check(agh.cast(runtime, void_unit, enemy, 2100).accepted and void_unit.hp == 7000 and not void_unit.has_status("slow"), "stopped history rewind and dispel")
	var viper := fighter("viper")
	viper.mp = 200
	agh.cast(runtime, viper, enemy, 0)
	check(viper.mp == 300, "mana enhancement")
	viper.advance_time(5000)
	check(viper.receive_android_damage(100, "pure").life_damage == 100, "reduction expires")
	var borrowed := fighter("abaddon")
	agh.cast(runtime, borrowed, enemy, 0)
	borrowed.hp = 10
	borrowed.receive_android_damage(20, "pure")
	borrowed.receive_android_damage(75, "pure")
	var restored := Fighter.from_data(borrowed.to_data())
	restored.advance_time(1000)
	check(restored.hp == 76, "pending borrowed healing survives snapshot")
	var dead := fighter("axe")
	dead.hp = 0
	check(not agh.cast(runtime, dead, enemy, 0).accepted and not dead.hero_state.get("aghanim_used", false), "dead source preserves use")
	var pa := fighter("phantom_assassin")
	pa.hero_timers.critical_ready = 2500
	agh.cast(runtime, pa, enemy, 0)
	check(pa.timer("critical_ready") == 2500, "PA restores prior ultimate timer")
	var rewind := Fighter.from_data(void_unit.to_data())
	check(rewind.hero_state.aghanim_hp_history == void_unit.hero_state.aghanim_hp_history, "history survives snapshot")
	runtime.drain_events()
	var sky := fighter("skywrath")
	var am := fighter("anti_mage")
	am.hero_timers.aghanim_reflect = 500
	sky.hero_timers.aghanim_reflect = 500
	check(agh.cast(runtime, sky, am, 0).accepted, "reflected silence still casts")
	check(sky.has_status("silence") and not am.has_status("silence"), "Sky silence redirected to caster")
	var reflections := 0
	for event in runtime.drain_events():
		if event.kind == "skill_reflected": reflections += 1
	check(reflections == 1, "silence reflection does not recurse")
	check(sky.hero_state.aghanim_used, "reflected cast consumes one use")
	var plain := fighter("abaddon")
	plain.receive_android_damage(20000, "pure")
	check(not plain.is_alive(), "unactivated abaddon unchanged")
	print("PASS: hero aghanim" if not failed else "FAIL: hero aghanim")
	quit(1 if failed else 0)
