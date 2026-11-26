extends SceneTree
func _init() -> void:
	call_deferred("_run")
func _run() -> void:
	var folder := "res://docs/acceptance/2026-11-26/journey-ui/"
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(folder))
	var session := root.get_node("AppSession")
	session.save_path = "user://capture-journey-ui.json"
	session.preparation_state = PreparationState.new("juggernaut")
	session.preparation_state.phase = PreparationState.Phase.JOURNEY
	session.journey_state = JourneyState.new(7001)
	session.journey_state.resolved = true
	session.journey_state.drop_serial = 1
	var sword := AndroidContent.create_instance("preview_sword", "broadsword")
	sword.identified = true
	session.journey_state.loot.append(sword)
	var screen := preload("res://scenes/Expedition.tscn").instantiate()
	root.add_child(screen)
	screen.selected_loot = sword.id
	screen._refresh()
	await process_frame
	await process_frame
	var scroll := screen.get_node("Scroll") as ScrollContainer
	scroll.ensure_control_visible(screen.packing_panel)
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(folder + "quick-equip.png")
	quit()
