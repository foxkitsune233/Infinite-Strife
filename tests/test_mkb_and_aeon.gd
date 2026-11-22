extends SceneTree

var _failed := false

func _init() -> void:
	var attacker: FighterState = _equipped("monkey_king_bar").fighter
	attacker.attack_damage = 100
	var target := FighterState.new("enemy", 500, 1, 1000, 0)
	target.hero_state["evasion_chance"] = 1.0
	target.add_status("attack_protection", 100)
	AndroidHeroRuntime.new(1).attack(attacker, target, 0)
	_check(target.hp == 500.0, "True strike does not pierce attack protection")
	target.advance_time(100)
	AndroidHeroRuntime.new(1).attack(attacker, target, 100)
	_check(target.hp == 330.0, "True strike bypasses evasion and the fixed seed procs 70 damage")
	var plain := FighterState.new("plain", 500, 100, 1000, 0)
	AndroidHeroRuntime.new(1).attack(plain, target, 100)
	_check(target.hp == 330.0, "A normal attack misses the same target")
	var defender: FighterState = _equipped("aeon_disk").fighter
	var runtime := AndroidHeroRuntime.new(1)
	defender.hp = defender.max_hp * 0.35
	_check(not runtime.try_aeon_disk(defender), "Aeon Disk does not trigger above threshold")
	defender.apply_control("disarm", 3000, "test", "enemy", "strong_only")
	runtime.external_damage(plain, defender, defender.max_hp * 0.06, "pure", "probe", 200)
	_check(defender.shield == 400.0 and not defender.has_status("disarm"), "Low HP triggers a 400 shield and strong dispel")
	_check(defender.cooldowns.get("active:aeon_disk") == 60000, "Aeon Disk starts a 60-second cooldown")
	defender.advance_time(2499)
	_check(defender.shield == 400.0, "Shield survives until its expiry boundary")
	defender.advance_time(1)
	_check(defender.shield == 0.0 and not runtime.try_aeon_disk(defender), "Shield expires after 2.5 seconds and cannot immediately retrigger")
	quit(1 if _failed else 0)

func _equipped(id: String) -> Dictionary:
	var prep := PreparationState.new("axe")
	var item := ItemInstance.new("test", id, ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	return BattleLoadoutBuilder.build(prep)

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
