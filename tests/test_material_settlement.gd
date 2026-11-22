extends SceneTree

const Session = preload("res://scripts/AppSession.gd")
const Pity = preload("res://scripts/journey/MaterialProgression.gd")
const Chests = preload("res://scripts/journey/ChestRules.gd")

func _init() -> void:
	var session := Session.new()
	session.save_path = "user://test_material_settlement.json"
	SaveRepository.erase(session.save_path)
	var state := PreparationState.new("juggernaut")
	state.backpack_size = Vector2i(4, 2)
	state.quest_progress["extracted"] = true
	session.preparation_state = state
	for run in 4:
		var journey := JourneyState.new(100 + run)
		journey.current_node = 8
		journey.resolved = true
		journey.target_chest = {"theme": "backpack", "run_start_item_ids": state.items.keys()}
		session.journey_state = journey
		state.phase = PreparationState.Phase.JOURNEY
		assert(session.leave_journey_node().accepted)
		assert(int(state.quest_progress.material_pity.backpack.misses) == run + 1)
	assert(state.quest_progress.material_pity.backpack.pending)
	var target := {"name": "材料箱", "hint": "", "quality": "白", "difficulty": 0, "ticket": 120}
	assert(Chests.generate(state, target, "backpack"))
	assert("backpack_fabric" in target.rewards and not state.quest_progress.material_pity.backpack.pending)
	state.quest_progress.material_pity.backpack = {"misses": 3, "pending": false}
	var journey := JourneyState.new(800)
	journey.target_chest = {"theme": "backpack", "run_start_item_ids": state.items.keys()}
	journey.current_node = 5
	session.journey_state = journey
	state.phase = PreparationState.Phase.JOURNEY
	assert(session.extract_journey().accepted)
	assert(state.quest_progress.material_pity.backpack.misses == 3)
	journey = JourneyState.new(801)
	journey.target_chest = {"theme": "backpack", "run_start_item_ids": state.items.keys()}
	session.journey_state = journey
	state.phase = PreparationState.Phase.JOURNEY
	var item := preload("res://scripts/content/AndroidContent.gd").create_instance("new-safe-fabric", "backpack_fabric")
	item.location = ItemInstance.Location.SAFE
	state.add_item(item)
	assert(session.begin_journey_battle().accepted)
	session.complete_journey_battle(false)
	assert(state.quest_progress.material_pity.backpack.misses == 0)
	assert(state.item(item.id) != null)
	SaveRepository.erase(session.save_path)
	session.free()
	print("Material pity settlement and safe death retention passed")
	quit()
