extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const OUT := "res://docs/acceptance/2026-11-21/progression-presentation/"

func _init() -> void:
	call_deferred("_run")

func shot(name: String) -> void:
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png(OUT + name + ".png")
	print("CAPTURE ", name)

func _run() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(OUT))
	var existed := FileAccess.file_exists(Settings.PATH)
	var original := FileAccess.get_file_as_bytes(Settings.PATH) if existed else PackedByteArray()
	var session := root.get_node("AppSession")
	session.save_path = "user://capture-progression-only.json"
	var state := PreparationState.new("juggernaut")
	state.backpack_size = Vector2i(4, 2)
	session.preparation_state = state
	for level in [0, 2]:
		Settings.set_text_level(level)
		var height := 1280 if level == 0 else 960
		root.content_scale_size = Vector2i(720, height)
		root.size = Vector2i(360, height / 2)
		await process_frame
		var page := preload("res://scenes/Progression.tscn").instantiate()
		root.add_child(page)
		await shot("tasks-" + str(level))
		var scroll := page._rows.get_parent() as ScrollContainer
		scroll.scroll_vertical = int(scroll.get_v_scroll_bar().max_value)
		await shot("growth-" + str(level))
		page.queue_free()
		await process_frame
	if existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(original)
		file.close()
	else: DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	print("PROGRESSION_CAPTURE_COMPLETE")
	quit()
