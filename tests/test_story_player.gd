extends SceneTree

const Player = preload("res://scripts/story/StoryPlayer.gd")
const Rules = preload("res://scripts/story/StoryRules.gd")
const Catalog = preload("res://scripts/story/StoryCatalog.gd")

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_story_player.json"
	SaveRepository.erase(session.save_path)
	var state := PreparationState.new("juggernaut")
	session.preparation_state = state
	Rules.enqueue(state, "J01")
	Player.show_pending(root, session)
	await process_frame
	var player: Node = get_nodes_in_group("story_player")[0]
	assert(player._speaker.text == "主宰")
	assert(player._portraits.juggernaut.texture != null and player._portraits.abaddon.texture != null)
	assert(player._portraits.juggernaut.modulate == Color.WHITE)
	assert(player._portraits.abaddon.modulate.r < 0.6)
	session.save_path = "user://missing-story-directory/save.json"
	player.advance()
	assert(player.page_index == 0 and Rules.page(state, "J01") == 0)
	session.save_path = "user://test_story_player.json"
	player.advance()
	assert(player.page_index == 1 and player._speaker.text == "亚巴顿")
	assert(player._portraits.abaddon.modulate == Color.WHITE)
	assert(player._portraits.juggernaut.modulate.r < 0.6)
	assert(session.restore_progress().accepted)
	assert(Rules.page(session.preparation_state, "J01") == 1)
	player.advance()
	await process_frame
	await process_frame
	assert(get_nodes_in_group("story_player").is_empty())
	var ending := Catalog.ending("abaddon")
	Player.show_chapter(root, session, ending.id)
	player = get_nodes_in_group("story_player")[0]
	assert(not player._stage.visible and player.revealing)
	player.advance()
	assert(player.page_index == 0 and not player.revealing)
	for index in ending.pages.size() - 1:
		player.advance()
		if player.revealing:
			player.advance()
	assert(player.page_index == 14 and player._next.text == "结束 · 返回")
	assert(not player.is_queued_for_deletion())
	player.advance()
	await process_frame
	assert(get_nodes_in_group("story_player").is_empty())
	SaveRepository.erase(session.save_path)
	print("Story portraits, save failure, restore, subtitle reveal and final-page dismissal passed")
	quit()
