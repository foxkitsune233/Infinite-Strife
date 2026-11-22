extends SceneTree

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	var journey := JourneyState.new(7)
	assert(journey.nodes.size() == 9)
	var battles := 0
	for node in journey.nodes:
		if node.type in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
			battles += 1
	assert(battles == 5)
	journey.current_node = 8
	assert(JourneyRules.begin_battle(journey).accepted)
	assert(JourneyRules.complete_battle(state, journey, true).accepted)
	assert(not JourneyRules.complete_battle(state, journey, true).accepted)
	var rest := JourneyState.new(10)
	assert(not JourneyRules.extract(state, rest).accepted)
	rest.current_node = 5
	assert(JourneyRules.extract(state, rest).accepted)
	assert(rest.finished() and rest.ending == "extracted")
	assert(not JourneyRules.extract(state, rest).accepted)
	var failed := JourneyState.new(11)
	var gear := ItemInstance.new("lost", "broadsword", ItemInstance.Kind.EQUIPMENT)
	state.add_item(gear)
	LoadoutRules.place(state, gear.id, gear.kind, 0)
	var safe := ItemInstance.new("saved", "iron_branch", ItemInstance.Kind.EQUIPMENT)
	state.add_item(safe)
	PackingRules.place(state, safe.id, ItemInstance.Location.SAFE, Vector2i.ZERO, false)
	JourneyRules.begin_battle(failed)
	assert(not JourneyRules.complete_battle(state, failed, false).accepted)
	assert(failed.finished() and state.item("lost") == null and state.item("saved") != null)
	assert(not JourneyRules.begin_battle(failed).accepted)
	quit(0)
