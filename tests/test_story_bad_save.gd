extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_story_bad_save.json"
	SaveRepository.erase(session.save_path)
	var file := FileAccess.open(session.save_path, FileAccess.WRITE)
	file.store_string("damaged-save-do-not-replace")
	file.close()
	session.preparation_state = null
	session.journey_state = null
	ScreenRouter.open(self, "home")
	await scene_changed
	assert(not session.save_progress().accepted)
	assert(current_scene.status_label.text.contains("阻止覆盖"))
	assert(not session.claim_quest("journey.1").accepted)
	assert(FileAccess.get_file_as_string(session.save_path) == "damaged-save-do-not-replace")
	SaveRepository.erase(session.save_path)
	session.save_path = "user://missing-prologue-directory/save.json"
	session.preparation_state = null
	session.journey_state = null
	ScreenRouter.open(self, "home")
	await scene_changed
	await process_frame
	assert(preload("res://scripts/story/StoryRules.gd").pending(session.preparation_state) == "P00")
	assert(get_nodes_in_group("story_player").size() == 1)
	var player: Node = get_nodes_in_group("story_player")[0]
	player.advance()
	player.advance()
	assert(player.page_index == 0)
	session.save_path = "user://test_story_recovered.json"
	SaveRepository.erase(session.save_path)
	player.advance()
	assert(player.page_index == 1)
	assert(session.restore_progress().accepted)
	SaveRepository.erase(session.save_path)
	print("Unreadable-save preservation and failed first-save prologue retry passed")
	quit()
