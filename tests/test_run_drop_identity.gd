extends SceneTree

func _init() -> void:
	var first := JourneyState.new(7001)
	var second := JourneyState.new(7002)
	JourneyRules._add_loot(first, ["essence"])
	JourneyRules._add_loot(second, ["essence"])
	assert(first.loot[0].id != second.loot[0].id)
	var state := PreparationState.new("juggernaut")
	state.add_item(first.loot[0])
	state.add_item(second.loot[0])
	assert(state.items.size() == 2)
	print("Consecutive run drops have distinct persistent identities")
	quit()
