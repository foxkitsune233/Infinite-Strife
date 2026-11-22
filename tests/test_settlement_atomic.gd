extends SceneTree

const Session = preload("res://scripts/AppSession.gd")

func _init() -> void:
	var session := Session.new()
	var state := session.ensure_preparation_state()
	var journey := session.ensure_journey_state()
	journey.current_node = 8
	journey.resolved = true
	journey.run_gold = 100
	journey.target_chest = {"rewards": ["essence"]}
	state.phase = PreparationState.Phase.JOURNEY
	session.save_path = "user://missing-settlement-directory/save.json"
	var before := SaveCodec.encode(state, journey)
	assert(not session.leave_journey_node().accepted)
	assert(SaveCodec.encode(state, journey) == before)
	session.save_path = "user://test_settlement_atomic.json"
	SaveRepository.erase(session.save_path)
	assert(session.leave_journey_node().accepted)
	var gold := state.gold
	var count := state.items.size()
	assert(not session.leave_journey_node().accepted)
	assert(state.gold == gold and state.items.size() == count)
	journey.current_node = 5
	journey.ending = ""
	journey.run_gold = 80
	state.phase = PreparationState.Phase.JOURNEY
	session.save_path = "user://missing-settlement-directory/save.json"
	before = SaveCodec.encode(state, journey)
	assert(not session.extract_journey().accepted)
	assert(SaveCodec.encode(state, journey) == before)
	session.save_path = "user://test_settlement_atomic.json"
	assert(session.extract_journey().accepted)
	assert(state.gold == gold + 80)
	journey.current_node = 0
	journey.ending = ""
	journey.resolved = false
	journey.run_gold = 90
	state.phase = PreparationState.Phase.JOURNEY
	assert(session.begin_journey_battle().accepted)
	assert(not session.begin_journey_battle().accepted and session.journey_battle_pending)
	before = SaveCodec.encode(state, journey)
	session.save_path = "user://missing-settlement-directory/save.json"
	assert(not session.complete_journey_battle(false).accepted)
	assert(SaveCodec.encode(state, journey) == before and session.journey_battle_pending)
	session.save_path = "user://test_settlement_atomic.json"
	session.complete_journey_battle(false)
	assert(journey.ending == "death" and journey.run_gold == 0)
	before = SaveCodec.encode(state, journey)
	assert(not session.complete_journey_battle(false).accepted)
	assert(SaveCodec.encode(state, journey) == before)
	SaveRepository.erase(session.save_path)
	session.free()
	print("Settlement failure rollback and exactly-once banking passed")
	quit()
