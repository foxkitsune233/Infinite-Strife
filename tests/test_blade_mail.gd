extends SceneTree

var _failed := false

func _init() -> void:
	var prep := PreparationState.new("juggernaut")
	var item := ItemInstance.new("blade_test", "blade_mail", ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	var build := BattleLoadoutBuilder.build(prep)
	if build.active_items.size() != 1:
		push_error("Blade Mail must be usable from a real loadout")
		quit(1)
		return
	var defender: FighterState = build.fighter
	defender.magic_resistance = 0.0
	defender.grant_shield("test", "UNIVERSAL", 70.0)
	var attacker := FighterState.new("enemy", 500, 10, 1000, 0, 0, 200)
	var engine := BattleEngine.new(7, defender, attacker)
	for side in ["left", "right"]:
		_check(engine.command({"kind": "use_active", "source_id": side, "item": build.active_items[0]}).accepted, "Blade Mail activates on either fighter")
	_check(engine.snapshot().left.cooldowns.get("active:blade_mail") == 12000, "Cooldown is twelve seconds")
	var cast := {"kind": "cast_skill", "source_id": "right", "skill": {"id": "probe", "magic_damage": 100}}
	var hp_before := defender.hp
	_check(engine.command(cast).accepted, "Probe spell is accepted")
	_check(defender.hp == hp_before - 30.0 and defender.shield == 0.0, "Incoming hit spends 70 shield and 30 HP")
	_check(attacker.hp == 400.0 and attacker.shield == 200.0, "Reflection counts shield loss and bypasses the attacker's shield without recursion")
	defender.advance_time(4000)
	engine.command(cast)
	_check(attacker.hp == 400.0, "Reflection ends after four seconds")
	quit(1 if _failed else 0)

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
