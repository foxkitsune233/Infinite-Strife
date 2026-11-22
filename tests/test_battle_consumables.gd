extends SceneTree

func _init() -> void:
	var state := PreparationState.new("necrophos")
	var cheese := ItemInstance.new("cheese_test", "cheese", ItemInstance.Kind.CONSUMABLE)
	state.add_item(cheese)
	assert(LoadoutRules.place(state, cheese.id, ItemInstance.Kind.EQUIPMENT, 0).accepted)
	var left := FighterState.new("left", 1000, 40, 1000, 0, 200)
	left.hero_id = "necrophos"
	var enemy := FighterState.new("right", 1000, 40, 1000, 0, 200)
	var engine := BattleEngine.new(7001, left, enemy)
	left.hp = 100
	left.mp = 0
	state.phase = PreparationState.Phase.BATTLE
	left.add_status("stun", 1000)
	assert(not engine.use_consumable(state, cheese.id).accepted)
	assert(state.item(cheese.id) == cheese and left.hp == 100)
	left.statuses.clear()
	left.hero_timers["ghost"] = 1000
	assert(engine.use_consumable(state, cheese.id).accepted)
	assert(is_equal_approx(left.hp, 700.0))
	assert(is_equal_approx(left.mp, 90.0))
	assert(state.item(cheese.id) == null and state.equipment_slots[0].is_empty())
	assert(not engine.use_consumable(state, cheese.id).accepted)
	var restored := BattleEngine.from_state(engine.export_state())
	assert(restored != null and is_equal_approx(float(restored.snapshot().left.hp), 700.0))
	var salve := ItemInstance.new("salve_test", "salve", ItemInstance.Kind.CONSUMABLE)
	salve.location = ItemInstance.Location.BACKPACK
	state.add_item(salve)
	assert(not engine.use_consumable(state, salve.id).accepted)
	quit()
