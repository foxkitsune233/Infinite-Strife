extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const OUT := "res://docs/acceptance/2026-11-21/button-adaptation/"

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
	Settings.set_text_level(2)
	Settings.set_flag("sound", false)
	var session := root.get_node("AppSession")
	session.save_path = "user://capture-button-adaptation.json"
	session.preparation_state = PreparationState.new("juggernaut")
	for height in [1280, 1600, 960]:
		root.content_scale_size = Vector2i(720, height)
		root.size = Vector2i(360, height / 2)
		await process_frame
		var battle := preload("res://scenes/Battle.tscn").instantiate()
		root.add_child(battle)
		battle.set_process(false)
		battle._engine._left.hero_state.has_aghanim = true
		battle._engine._left.hero_state.has_shard = true
		battle._refresh_hud()
		await shot("battle-" + str(height))
		battle.queue_free()
		await process_frame
		if height == 960:
			var preparation := preload("res://scenes/Preparation.tscn").instantiate()
			root.add_child(preparation)
			await shot("preparation-960")
			preparation.queue_free()
			await process_frame
	if existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(original)
		file.close()
	else: DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	print("BUTTON_CAPTURE_COMPLETE")
	quit()
