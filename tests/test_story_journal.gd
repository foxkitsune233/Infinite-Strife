extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_story_journal.json"
	SaveRepository.erase(session.save_path)
	var state: PreparationState = session.ensure_preparation_state()
	var screen := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(screen)
	var chooser := AcceptDialog.new()
	screen.add_child(chooser)
	screen._select_hero("abaddon", chooser)
	await process_frame
	var players := get_nodes_in_group("story_player")
	assert(players.size() == 1 and players[0].chapter.id == "H02")
	players[0]._move(true)
	await process_frame
	chooser = AcceptDialog.new()
	screen.add_child(chooser)
	screen._select_hero("abaddon", chooser)
	await process_frame
	assert(get_nodes_in_group("story_player").is_empty())
	preload("res://scripts/story/StoryJournal.gd").open(screen, session)
	var windows := screen.get_children().filter(func(node: Node) -> bool: return node is AcceptDialog and not node.is_queued_for_deletion())
	assert(windows.size() == 1)
	var journal: AcceptDialog = windows[0]
	var scroll := journal.get_children().filter(func(node: Node) -> bool: return node is ScrollContainer)[0] as ScrollContainer
	var buttons := scroll.get_child(0).get_children()
	assert(buttons.size() == 47) # 41 chapters minus 4 repeat fragments plus 10 endings.
	assert(not buttons[0].disabled)
	assert(buttons.back().disabled)
	var before := SaveCodec.encode(state, session.ensure_journey_state())
	buttons[0].pressed.emit()
	await process_frame
	assert(get_nodes_in_group("story_player").size() == 1)
	get_nodes_in_group("story_player")[0]._move(true)
	await process_frame
	assert(SaveCodec.encode(state, session.ensure_journey_state()) == before)
	screen.queue_free()
	SaveRepository.erase(session.save_path)
	await process_frame
	print("Hero first encounter, one-time trigger, journal locking and replay purity passed")
	quit()
