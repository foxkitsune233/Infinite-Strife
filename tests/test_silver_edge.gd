extends SceneTree

var _failed := false

func _init() -> void:
	var preparation := PreparationState.new("juggernaut")
	var item := ItemInstance.new("silver_test", "silver_edge", ItemInstance.Kind.EQUIPMENT)
	preparation.add_item(item)
	LoadoutRules.place(preparation, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	var build := BattleLoadoutBuilder.build(preparation)
	if build.active_items.size() != 1:
		push_error("Equipped Silver Edge must expose its active action")
		quit(1)
		return
	var target := FighterState.new("viper", 500, 1, 1000, 0)
	target.hero_id = "viper"
	var engine := BattleEngine.new(7, build.fighter, target)
	var command := {"kind": "use_active", "source_id": "left", "item": build.active_items[0]}
	_check(is_equal_approx(target.receive_android_damage(100, "magic").life_damage, 85), "Viper passive mitigates magic before Break")
	target.add_status("debuff_immune", 100)
	_check(not engine.command(command).accepted, "Immunity rejects Silver Edge")
	_check(engine.snapshot().left.cooldowns.is_empty(), "Rejected activation does not start cooldown")
	target.advance_time(100)
	_check(engine.command(command).accepted, "Silver Edge activates after immunity expires")
	_check(engine.snapshot().right.statuses.get("break") == 4000, "Break lasts four seconds")
	_check(engine.snapshot().left.cooldowns.get("active:silver_edge") == 12000, "Active cooldown lasts twelve seconds")
	_check(is_equal_approx(target.receive_android_damage(100, "magic").life_damage, 100), "Break suppresses Viper passive")
	target.advance_time(4000)
	_check(is_equal_approx(target.receive_android_damage(100, "magic").life_damage, 85), "Hero passive returns when Break expires")
	quit(1 if _failed else 0)

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
