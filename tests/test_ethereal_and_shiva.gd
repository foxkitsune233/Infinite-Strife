extends SceneTree

var _failed := false

func _init() -> void:
	var target := FighterState.new("enemy", 600, 10, 1000, 0, 0, 300, 0.25)
	target.hero_id = "axe"
	var prep := PreparationState.new("juggernaut")
	var item := ItemInstance.new("ethereal_test", "ethereal_blade", ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	item = ItemInstance.new("shiva_test", "shivas_guard", ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 1)
	var build := BattleLoadoutBuilder.build(prep)
	if build.active_items.size() != 2:
		push_error("Both equipped actives must be available")
		quit(1)
		return
	var engine := BattleEngine.new(7, build.fighter, target)
	var ethereal_use := {"kind": "use_active", "source_id": "left", "item": build.active_items[0], "automatic": true}
	var shiva_use := {"kind": "use_active", "source_id": "left", "item": build.active_items[1], "automatic": true}
	target.hero_timers["invulnerable"] = 100
	_check(not engine.command(ethereal_use).accepted and not engine.command(shiva_use).accepted, "Both actives wait out invulnerability")
	target.hero_timers.erase("invulnerable")
	target.hero_timers["ghost"] = 100
	target.add_status("slow", 100)
	_check(not engine.command(ethereal_use).accepted and not engine.command(shiva_use).accepted, "Automatic use waits while its effect is already present")
	_check(engine.snapshot().left.cooldowns.is_empty(), "Waiting does not spend cooldown")
	target.advance_time(100)
	var ethereal := engine.command(ethereal_use)
	_check(ethereal.accepted and target.hp == 380.0 and target.shield == 300.0, "Ethereal deals 220 pure damage through shields")
	_check(target.timer("ghost") == 3000 and target.receive_android_damage(100, "physical").blocked, "Ethereal grants three seconds of physical immunity")
	_check(is_equal_approx(target.receive_android_damage(100, "magic").shield_damage, 90.0), "Ghost amplifies magic before shield absorption")
	target.advance_time(3000)
	var baseline := target.effective_attack_interval_ms()
	target.hero_state["slow_amount"] = 90.0
	var shiva := engine.command(shiva_use)
	_check(shiva.accepted and target.status_snapshot().get("slow") == 3000, "Shiva slows for three seconds")
	_check(target.hero_state.slow_amount == 35.0 and target.effective_attack_interval_ms() > baseline, "Shiva uses the exported 35 attack-speed slow")
	_check(engine.snapshot().left.cooldowns.get("active:ethereal_blade") == 12000 and engine.snapshot().left.cooldowns.get("active:shivas_guard") == 12000, "Both use the exported twelve-second cooldown")
	target.dispel(false)
	_check(target.effective_attack_interval_ms() == baseline, "Basic dispel clears Shiva's slow")
	quit(1 if _failed else 0)

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
