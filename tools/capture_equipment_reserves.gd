extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var out := "res://docs/acceptance/2026-11-21/hero-reserves/"
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(out))
	var session := root.get_node("AppSession")
	session.save_path = "user://capture-equipment-reserves.json"
	var state := PreparationState.new("juggernaut")
	session.preparation_state = state
	var content = preload("res://scripts/content/AndroidContent.gd")
	for pair in [["broadsword", 0], ["dagon", 1], ["monkey_king_bar", 6], ["aeon_disk", 7], ["cheese", 8]]:
		var item := content.create_instance("capture_" + str(pair[0]), str(pair[0]))
		state.add_item(item)
		assert(LoadoutRules.place(state, item.id, ItemInstance.Kind.EQUIPMENT, int(pair[1])).accepted)
	var page := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(page)
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out + "equipment-active-reserve.png")
	page.queue_free()
	await process_frame
	var camp := preload("res://scenes/MainMenu.tscn").instantiate()
	root.add_child(camp)
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(out + "camp-two-heroes.png")
	camp.queue_free()
	await process_frame
	print("RESERVE_CAPTURE_COMPLETE")
	quit()
