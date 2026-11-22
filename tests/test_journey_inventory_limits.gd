extends SceneTree

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	var item := ItemInstance.new("bound", "broadsword", ItemInstance.Kind.EQUIPMENT)
	item.bound = true
	state.add_item(item)
	state.phase = PreparationState.Phase.JOURNEY
	assert(not PackingRules.place(state, item.id, ItemInstance.Location.BACKPACK, Vector2i.ZERO, false).accepted)
	assert(not LoadoutRules.place(state, item.id, ItemInstance.Kind.EQUIPMENT, 0).accepted)
	assert(not LoadoutRules.activate_hero(state, "axe").accepted)
	assert(state.active_hero_id == "juggernaut")
	state.phase = PreparationState.Phase.HOME
	assert(PackingRules.place(state, item.id, ItemInstance.Location.BACKPACK, Vector2i.ZERO, false).accepted)
	state.phase = PreparationState.Phase.BATTLE
	assert(not PackingRules.rotate(state, item.id).accepted)
	assert(not LoadoutRules.place(state, item.id, ItemInstance.Kind.EQUIPMENT, 0).accepted)
	var journey := JourneyState.new(7001)
	journey.battle_started = true
	JourneyRules.complete_battle(state, journey, false)
	assert(state.item(item.id) == null)
	var safe := ItemInstance.new("safe", "iron_branch", ItemInstance.Kind.EQUIPMENT)
	safe.location = ItemInstance.Location.SAFE
	state.add_item(safe)
	var retreat := JourneyState.new(7002)
	retreat.current_node = 5
	state.phase = PreparationState.Phase.JOURNEY
	assert(JourneyRules.extract(state, retreat).accepted)
	assert(safe.location == ItemInstance.Location.WAREHOUSE)
	var medicine := ItemInstance.new("medicine", "salve", ItemInstance.Kind.CONSUMABLE)
	medicine.location = ItemInstance.Location.BACKPACK
	state.add_item(medicine)
	var run := JourneyState.new(7003)
	state.phase = PreparationState.Phase.JOURNEY
	run.player_hp_ratio = 0.5
	assert(JourneyRules.use_consumable(state, run, medicine.id).accepted)
	assert(run.player_hp_ratio > 0.5 and state.item(medicine.id) == null)
	quit()
