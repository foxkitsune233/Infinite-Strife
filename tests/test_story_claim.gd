extends SceneTree

const Session = preload("res://scripts/AppSession.gd")
const Rules = preload("res://scripts/story/StoryRules.gd")

func _init() -> void:
	var session := Session.new()
	var state := session.ensure_preparation_state()
	var gold := state.gold
	session.save_path = "user://missing-story-directory/save.json"
	assert(not session.claim_quest("journey.1").accepted)
	state = session.ensure_preparation_state()
	assert(state.gold == gold and Rules.pending(state).is_empty())
	assert(state.quest_progress.get("claimed_quests", []).is_empty())
	session.save_path = "user://test_story_claim.json"
	SaveRepository.erase(session.save_path)
	assert(session.claim_quest("journey.1").accepted)
	assert(Rules.pending(state) == "J01" and state.gold == gold + 100)
	assert(not session.claim_quest("journey.1").accepted)
	assert(session.restore_progress().accepted)
	state = session.ensure_preparation_state()
	assert(Rules.pending(state) == "J01")
	assert(session.advance_story("J01", true).finished)
	assert(Rules.pending(state).is_empty())
	assert(Rules.can_replay(state, "J01") and state.gold == gold + 100)
	SaveRepository.erase(session.save_path)
	session.free()
	print("Story claim transaction, skip and replay passed")
	quit()
