extends SceneTree

func _init() -> void:
	call_deferred("_capture")

func _capture() -> void:
	var session := root.get_node("AppSession")
	session.preparation_state = PreparationState.new("juggernaut")
	var player_script := preload("res://scripts/story/StoryPlayer.gd")
	player_script.show_chapter(root, session, "J07")
	await process_frame
	await RenderingServer.frame_post_draw
	var image := root.get_texture().get_image()
	image.save_png("res://docs/migration/story-dialogue-preview.png")
	var player: Node = get_nodes_in_group("story_player")[0]
	player._move(true)
	await process_frame
	player_script.show_chapter(root, session, "E02")
	await create_timer(0.9).timeout
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png("res://docs/migration/story-ending-preview.png")
	player = get_nodes_in_group("story_player")[0]
	player._move(true)
	await process_frame
	print("Story rendered previews saved")
	quit()
