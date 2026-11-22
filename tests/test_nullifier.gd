extends SceneTree

var _failed := false

func _init() -> void:
	var prep := PreparationState.new("juggernaut")
	var item := ItemInstance.new("nullifier_test", "nullifier", ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	var build := BattleLoadoutBuilder.build(prep)
	if build.active_items.size() != 1:
		push_error("Equipped Nullifier must expose its active action")
		quit(1)
		return
	for hero in ["abaddon", "necrophos"]:
		var enemy := BattleLoadoutBuilder.build(PreparationState.new(hero))
		var target: FighterState = enemy.fighter
		var engine := BattleEngine.new(9, FighterState.from_data(build.fighter.to_data()), target)
		var use := {"kind": "use_active", "source_id": "left", "item": build.active_items[0], "automatic": true}
		_check(not engine.command(use).accepted, "Automatic Nullifier waits for a dispellable effect")
		target.grant_shield("test:shield", "UNIVERSAL", 50)
		target.hero_timers["ghost"] = 1000
		target.add_status("break", 5000)
		target.add_status("debuff_immune", 100)
		_check(not engine.command(use).accepted and target.total_shields() == 50, "Immunity preserves the shield")
		target.advance_time(100)
		_check(engine.command(use).accepted, "Nullifier can purge a buff or shield")
		_check(target.total_shields() == 0 and target.timer("ghost") == 0, "Shield and ghost are both purged")
		_check(target.has_status("break") and target.status_snapshot().get("positive_block") == 4000, "Negative effects survive and the lock lasts four seconds")
		_check(engine.snapshot().left.cooldowns.get("active:nullifier") == 12000, "Cooldown starts on success")
		var cast := {"kind": "cast_skill", "source_id": "right", "skill": enemy.skill}
		target.mp = target.max_mp
		_check(engine.command(cast).accepted, "Hero can still cast while positive effects are blocked")
		_check(target.total_shields() == 0 and target.timer("ghost") == 0, "Hero cannot reapply its buff during the lock")
		target.advance_time(4000)
		target.cooldowns.clear()
		target.mp = target.max_mp
		_check(engine.command(cast).accepted, "Hero can cast again in the expiry fixture")
		_check(target.total_shields() > 0 if hero == "abaddon" else target.timer("ghost") > 0, "Hero buff returns after the lock expires")
	quit(1 if _failed else 0)

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
