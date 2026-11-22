extends SceneTree

const Rules = preload("res://scripts/story/StoryRules.gd")

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_story_boot.json"
	SaveRepository.erase(session.save_path)
	session.preparation_state = null
	session.journey_state = null
	assert(ScreenRouter.open(self, "home") == OK)
	await scene_changed
	await process_frame
	var state: PreparationState = session.preparation_state
	assert(Rules.pending(state) == "P00")
	assert(get_nodes_in_group("story_player").size() == 1)
	var player: Node = get_nodes_in_group("story_player")[0]
	player.advance()
	player.advance()
	assert(player.page_index == 1)
	assert(ScreenRouter.open(self, "preparation") == OK)
	await scene_changed
	session.preparation_state = null
	session.journey_state = null
	assert(ScreenRouter.open(self, "home") == OK)
	await scene_changed
	await process_frame
	assert(get_nodes_in_group("story_player").size() == 1)
	player = get_nodes_in_group("story_player")[0]
	assert(player.page_index == 1)
	player._move(true)
	await process_frame
	assert(Rules.pending(session.preparation_state).is_empty())
	SaveRepository.erase(session.save_path)
	print("New prologue and startup resume passed")
	quit()
