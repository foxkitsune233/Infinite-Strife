extends SceneTree

const Session = preload("res://scripts/AppSession.gd")
const Catalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")

func _init() -> void:
	var session := Session.new()
	session.save_path = "user://test_tower_session.json"
	SaveRepository.erase(session.save_path)
	var state := session.ensure_preparation_state()
	assert(not session.enter_tower().accepted)
	var nodes := {}
	var claimed: Array = []
	for quest in Catalog.all():
		if not nodes.has(quest.node):
			nodes[quest.node] = true
			claimed.append(quest.id)
	state.quest_progress["claimed_quests"] = claimed
	assert(session.enter_tower().accepted)
	assert(not session.enter_journey().accepted)
	assert(state.quest_progress.story.pending == ["T00"])
	for floor_index in range(1, 21):
		var run: Dictionary = state.quest_progress.tower_run
		if run.pending_waypoint:
			assert(session.choose_tower_waypoint("REST").accepted)
		session.save_path = "user://missing-tower-directory/save.json"
		var before := SaveCodec.encode(state, session.ensure_journey_state())
		assert(not session.begin_tower_battle().accepted)
		assert(SaveCodec.encode(state, session.ensure_journey_state()) == before)
		session.save_path = "user://test_tower_session.json"
		assert(session.begin_tower_battle().accepted and session.is_tower_battle_pending())
		assert(state.phase == PreparationState.Phase.BATTLE)
		assert(session.complete_tower_battle(true, 500, 1000).accepted)
		assert(state.quest_progress.tower_highest_floor == floor_index)
		assert(not session.complete_tower_battle(true, 500, 1000).accepted)
	assert(state.quest_progress.get("tower_run") == null)
	assert(state.quest_progress.level_cap == 100 and state.quest_progress.tower_rapier)
	assert(state.quest_progress.pending_rebirth_items.size() == 10)
	assert("E01" in state.quest_progress.story.pending)
	assert(state.gold == 600 + 14200)
	assert(session.grant_tower_reward("SHARD", "juggernaut").accepted)
	assert(not session.grant_tower_reward("SHARD", "juggernaut").accepted)
	assert(session.restore_progress().accepted)
	assert("E01" in session.preparation_state.quest_progress.story.pending)
	SaveRepository.erase(session.save_path)
	session.free()
	print("20 floor session, checkpoints, rewards and ending queue passed")
	quit()
