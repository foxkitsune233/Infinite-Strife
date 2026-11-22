extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const OUT := "res://docs/acceptance/2026-11-21/hero-augments/"

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
	Settings.set_flag("animations", true)
	Settings.set_flag("statusIcons", true)
	Settings.set_flag("sound", false)
	var session := root.get_node("AppSession")
	session.save_path = "user://capture-hero-augments.json"
	session.preparation_state = PreparationState.new("axe")
	var state: PreparationState = session.preparation_state
	state.unlocked_heroes.append("axe")
	state.quest_progress.tower_shard = ["axe"]
	state.quest_progress.tower_aghs = ["axe"]
	assert(session.enter_journey().accepted)
	assert(session.begin_journey_battle().accepted)
	var battle := preload("res://scenes/Battle.tscn").instantiate()
	root.add_child(battle)
	battle.set_process(false)
	await shot("battle-ready-largest")
	battle.effects_layer.set_stage_anchors(battle.stage_anchor("left"), battle.stage_anchor("right"), battle._engine._right.id)
	assert(battle.use_player_aghanim().accepted)
	await shot("battle-used-largest")
	battle.queue_free()
	await process_frame
	if existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(original)
		file.close()
	else: DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	SaveRepository.erase(session.save_path)
	print("AUGMENT_CAPTURE_COMPLETE")
	quit()
