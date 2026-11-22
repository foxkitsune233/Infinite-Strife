extends SceneTree

const Chests = preload("res://scripts/journey/ChestRules.gd")

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	var target := {"name": "补给", "hint": "补给物品", "quality": "白", "difficulty": 0, "ticket": 120}
	assert(Chests.generate(state, target, "supplies"))
	state.quest_progress["chest_offers"] = {"test": target}
	var starting_gold := state.gold
	for index in 3:
		assert(Chests.replace(state, target, 1000).accepted)
		assert(target.difficulty == 0 and target.appraisals == 0)
		assert(int(state.quest_progress.chest_refresh.charges) == 2 - index)
	assert(state.gold == starting_gold)
	var fee := Chests.refresh_cost(state, target, 1000)
	assert(fee > 0)
	assert(Chests.replace(state, target, 1000).accepted)
	assert(state.gold == starting_gold - fee)
	var saved := SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(7001)))
	assert(int(Chests.refresh_status(saved.preparation, 1801000).charges) == 1)
	assert(int(Chests.refresh_status(saved.preparation, 0).charges) == 0)
	state.gold = 0
	var previous := target.duplicate(true)
	assert(not Chests.replace(state, target, 1000).accepted and target == previous)
	state.phase = PreparationState.Phase.JOURNEY
	assert(not Chests.replace(state, target, 1801000).accepted)
	quit()
