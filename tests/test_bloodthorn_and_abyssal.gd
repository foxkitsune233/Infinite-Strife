extends SceneTree

var _failed := false

func _init() -> void:
	var attacker := _fighter("axe", "bloodthorn")
	var target := FighterState.new("target", 1000, 1, 1000, 0)
	attacker.add_status("break", 1000)
	AndroidHeroRuntime.new(1).attack(attacker, target, 0)
	_check(target.hp == 825.0, "Bloodthorn equipment crit survives Break")
	attacker.critical_chance_percent = 100
	attacker.critical_multiplier = 2.0
	AndroidHeroRuntime.new(1).attack(attacker, target, 0)
	_check(target.hp == 625.0, "The stronger crit wins without stacking")
	for hero in ["axe", "viper"]:
		var source := _fighter(hero, "abyssal_blade")
		var dummy := FighterState.new("dummy", 1000, 1, 1000, 0)
		AndroidHeroRuntime.new(7000).attack(source, dummy, 0)
		_check(dummy.has_status("stun") == (hero == "axe"), "The same roll passes 25% melee chance and fails 10% ranged chance")
		if hero != "axe": continue
		_check(dummy.status_snapshot().get("stun") == 900, "Bash stuns for 900ms")
		_check(source.cooldowns.get("proc:basher") == 3000, "Bash starts its separate three-second cooldown")
		dummy.dispel(true)
		AndroidHeroRuntime.new(1).attack(source, dummy, 0)
		_check(not dummy.has_status("stun"), "Bash does not repeat during cooldown")
		source.advance_time(3000)
		dummy.add_status("debuff_immune", 100)
		AndroidHeroRuntime.new(1).attack(source, dummy, 0)
		_check(not dummy.has_status("stun") and not source.cooldowns.has("proc:basher"), "Immunity blocks Bash without spending cooldown")
	quit(1 if _failed else 0)

func _fighter(hero: String, id: String) -> FighterState:
	var prep := PreparationState.new(hero)
	var item := ItemInstance.new("test", id, ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	var fighter: FighterState = BattleLoadoutBuilder.build(prep).fighter
	fighter.attack_damage = 100
	return fighter

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
