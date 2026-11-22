extends SceneTree

const Session = preload("res://scripts/AppSession.gd")
const Save = preload("res://scripts/save/SaveRepository.gd")
const QuestCatalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")
const PATH := "user://test_migration_campaign.json"

func _init() -> void:
	Save.erase(PATH)
	var session := Session.new()
	session.save_path = PATH
	assert(not session.enter_journey(1).accepted and session.journey_state == null)
	assert(not session.enter_journey(6).accepted)
	var wallet := session.ensure_preparation_state().gold
	var goal := {"ticket": 120, "rewards": ["essence"]}
	assert(not session.enter_journey(0, {"ticket": wallet + 1}).accepted)
	assert(session.enter_journey(0, goal).accepted)
	var starting_gold := session.preparation_state.gold
	assert(starting_gold == wallet - 120)
	assert(session.preparation_state.phase == PreparationState.Phase.JOURNEY)
	assert(session.begin_journey_battle().accepted)
	var restored := Session.new()
	restored.save_path = PATH
	assert(restored.restore_progress().accepted)
	assert(int(restored.journey_state.target_chest.ticket) == 120)
	assert(restored.journey_battle_pending)
	assert(not restored.begin_journey_battle().accepted)
	assert(session.complete_journey_battle(true).accepted)
	assert(session.journey_state.run_gold == 20 and session.preparation_state.gold == starting_gold)
	assert(not session.complete_journey_battle(true).accepted and session.journey_state.run_gold == 20)
	assert(restored.restore_progress().accepted and restored.journey_state.run_gold == 20)
	JourneyRules.advance_appraisal(session.journey_state, 10000)
	assert(session.leave_journey_node().accepted)
	while not session.journey_state.finished():
		var journey: JourneyState = session.journey_state
		if journey.current().type in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
			assert(session.begin_journey_battle().accepted)
			assert(session.complete_journey_battle(true).accepted)
		else:
			assert(JourneyRules.resolve_current(session.preparation_state, journey).accepted)
			if journey.current().type == JourneyState.NodeType.EVENT:
				assert(JourneyRules.choose_event(session.preparation_state, journey, 2).accepted)
		JourneyRules.advance_appraisal(journey, 10000)
		assert(session.leave_journey_node().accepted)
	assert(session.journey_state.ending == "cleared")
	assert(session.preparation_state.items.values().any(func(item: ItemInstance) -> bool: return item.id.begins_with("chest_") and item.definition_id == "essence" and item.location == ItemInstance.Location.WAREHOUSE))
	assert(session.journey_state.run_gold == 0 and session.preparation_state.gold == starting_gold + 235)
	assert(session.preparation_state.phase == PreparationState.Phase.HOME)
	assert(session.preparation_state.quest_progress.clears == 1)
	assert(session.preparation_state.quest_progress.quest_counters["journey.13.2"] == 5)
	assert(not session.leave_journey_node().accepted)
	assert(session.preparation_state.quest_progress.clears == 1)
	var claimed: Array = []
	var seen := {}
	for quest in QuestCatalog.all():
		if int(quest.node) > 6 or seen.has(quest.node): continue
		seen[quest.node] = true
		claimed.append(quest.id)
	session.preparation_state.quest_progress["claimed_quests"] = claimed
	assert(session.enter_journey(1).accepted and session.journey_state.difficulty == 1)
	assert(session.enter_journey(0).accepted and session.journey_state.difficulty == 1)
	var journey := session.journey_state
	journey.run_gold = 30
	assert(session.begin_journey_battle().accepted)
	assert(not session.complete_journey_battle(false).accepted and journey.run_gold == 0)
	var retreat := JourneyState.new(7001)
	retreat.current_node = 5
	retreat.run_gold = 95
	assert(JourneyRules.extract(session.preparation_state, retreat).accepted)
	assert(retreat.run_gold == 0 and session.preparation_state.gold == starting_gold + 330)
	Save.erase(PATH)
	session.free()
	restored.free()
	quit(0)
