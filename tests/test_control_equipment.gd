extends SceneTree

var _failed := false

func _init() -> void:
	_check_item("bloodthorn", "silence", 4000, 14000, false)
	_check_item("abyssal_blade", "stun", 1200, 12000, true)
	_check_item("scythe_of_vyse", "hex", 2200, 15000, true)
	_check_item("euls_scepter", "banish", 1800, 12000, false)
	quit(1 if _failed else 0)

func _check_item(id: String, kind: String, duration: int, cooldown: int, strong_dispellable: bool) -> void:
	var prep := PreparationState.new("juggernaut")
	var item := ItemInstance.new("test", id, ItemInstance.Kind.EQUIPMENT)
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	var build := BattleLoadoutBuilder.build(prep)
	if build.active_items.size() != 1:
		_check(false, id + " must expose an active action")
		return
	var target := FighterState.new("enemy", 500, 10, 1000, 0, 100)
	var engine := BattleEngine.new(7, build.fighter, target)
	var use := {"kind": "use_active", "source_id": "left", "item": build.active_items[0], "automatic": true}
	target.add_status("debuff_immune", 100)
	_check(not engine.command(use).accepted and engine.snapshot().left.cooldowns.is_empty(), id + " respects immunity without spending cooldown")
	target.advance_time(100)
	target.apply_control(kind, 100, "fixture", "other")
	_check(not engine.command(use).accepted, id + " auto use waits while that control is active")
	target.advance_time(100)
	_check(engine.command(use).accepted, id + " activates once eligible")
	_check(target.status_snapshot().get(kind) == duration, id + " uses Android duration")
	_check(engine.snapshot().left.cooldowns.get("active:" + id) == cooldown, id + " uses Android cooldown")
	_check(not target.action_block("skill").is_empty(), id + " blocks skill use")
	if kind == "banish":
		_check(not target.action_block("attack").is_empty() and not target.action_block("item").is_empty(), "Banish blocks attacks and items")
		_check(target.receive_android_damage(100, "pure").life_damage == 0, "Banish blocks even pure damage")
	target.dispel(false)
	_check(target.has_status(kind), id + " survives basic dispel")
	target.dispel(true)
	_check(target.has_status(kind) != strong_dispellable, id + " honors strong dispel class")
	target.advance_time(duration)
	_check(not target.has_status(kind), id + " expires normally")

func _check(condition: bool, message: String) -> void:
	if not condition:
		_failed = true
		push_error(message)
