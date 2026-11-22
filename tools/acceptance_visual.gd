extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const OUT := "res://docs/acceptance/2026-11-20/after-fix/"
var _settings_existed := false
var _settings_bytes: PackedByteArray

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
	_settings_existed = FileAccess.file_exists(Settings.PATH)
	if _settings_existed: _settings_bytes = FileAccess.get_file_as_bytes(Settings.PATH)
	var session := root.get_node("AppSession")
	session.save_path = "user://acceptance-visual-only.json"
	session.preparation_state = PreparationState.new("juggernaut")
	Settings.reset()
	var screen := preload("res://scenes/Settings.tscn").instantiate()
	root.add_child(screen)
	await shot("settings-normal")
	screen._tab = 1
	screen._rebuild()
	await shot("settings-backup")
	screen._tab = 2
	screen._rebuild()
	await shot("settings-gallery")
	Settings.set_text_level(2)
	screen._tab = 0
	screen._rebuild()
	await shot("settings-largest")
	screen.queue_free()
	await process_frame
	var item := preload("res://scripts/content/AndroidContent.gd").create_instance("acceptance-sword", "broadsword")
	session.preparation_state.add_item(item)
	var detail := preload("res://scripts/ui/ItemDetailSheet.gd").show(root, session.preparation_state, item.id)
	await shot("item-original")
	(detail.find_child("RotatePreview", true, false) as Button).pressed.emit()
	await shot("item-rotated")
	assert(not item.rotated and item.location == ItemInstance.Location.WAREHOUSE)
	detail.queue_free()
	await process_frame
	Settings.set_flag("animations", false)
	Settings.set_flag("sound", false)
	Settings.set_flag("battleLog", false)
	Settings.set_flag("statusIcons", false)
	var player = preload("res://scripts/story/StoryPlayer.gd")
	player.show_chapter(root, session, "J07")
	await shot("story-largest")
	get_nodes_in_group("story_player")[0]._move(true)
	await process_frame
	player.show_chapter(root, session, "E02")
	assert(not get_nodes_in_group("story_player")[0].revealing)
	await shot("ending-no-animation")
	get_nodes_in_group("story_player")[0]._move(true)
	await process_frame
	var battle := preload("res://scenes/Battle.tscn").instantiate()
	root.add_child(battle)
	battle.set_process(false)
	battle.battle_audio.play_cue("hit")
	assert(battle.battle_audio._frames_left == 0)
	assert(not battle._presentation.animations_enabled)
	await shot("battle-largest-no-effects")
	battle.queue_free()
	await process_frame
	if _settings_existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(_settings_bytes)
		file.close()
	else:
		DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	SaveRepository.erase(session.save_path)
	print("ACCEPTANCE_CAPTURE_COMPLETE; original preferences restored")
	quit()
