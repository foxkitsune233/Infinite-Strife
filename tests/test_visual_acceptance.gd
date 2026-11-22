extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
var failures := 0
func check(ok: bool, why: String) -> void:
	if not ok:
		failures += 1
		push_error(why)
func _init() -> void:
	call_deferred("_run")
func _run() -> void:
	var existed := FileAccess.file_exists(Settings.PATH)
	var original := FileAccess.get_file_as_bytes(Settings.PATH) if existed else PackedByteArray()
	Settings.set_text_level(2)
	Settings.set_flag("statusIcons", true)
	var battle := preload("res://scenes/Battle.tscn").instantiate()
	root.add_child(battle)
	battle.set_process(false)
	for fighter in [battle._engine._left, battle._engine._right]:
		fighter.hero_state.has_shard = true
		fighter.hero_state.has_aghanim = true
	for status in ["debuff_immune", "break", "silence"]:
		battle._engine._left.add_status(status, 999999)
	battle._refresh_hud()
	await process_frame
	await process_frame
	var viewport := root.get_visible_rect()
	for path in ["TopHud", "TopHud/LeftHud", "TopHud/RightHud"]:
		var rect: Rect2 = battle.get_node(path).get_global_rect()
		check(rect.position.x >= 0 and rect.end.x <= viewport.end.x + 1, "largest HUD stays inside viewport: " + path)
	check(battle.get_node("TopHud").get_global_rect().end.y <= battle.get_node("Bottom").global_position.y, "HUD stays above controls")
	check(battle._aghanim_button.visible, "owned Aghanim appears at largest font")
	check(battle._aghanim_button.get_global_rect().end.y <= viewport.end.y, "Aghanim remains on screen")
	check(battle.get_node("Bottom/ManualControls").get_global_rect().end.y <= viewport.end.y, "all manual controls remain on screen")
	battle.queue_free()
	await process_frame
	var screen := preload("res://scenes/Settings.tscn").instantiate()
	root.add_child(screen)
	screen._tab = 2
	screen._rebuild()
	await process_frame
	var grid := screen._body.get_children().filter(func(node: Node) -> bool: return node is GridContainer)[0] as GridContainer
	for cell in grid.get_children():
		check((cell.get_child(0) as TextureRect).stretch_mode == TextureRect.STRETCH_KEEP_ASPECT_CENTERED, "gallery preserves proportions")
	screen._tab = 1
	screen._rebuild()
	await process_frame
	for node in screen._body.get_children():
		if node is Button: check(node.get_theme_stylebox("normal") is StyleBoxTexture and node.custom_minimum_size.y >= 56, "storage buttons use accessible themed frames")
	screen.queue_free()
	await process_frame
	var state := PreparationState.new("juggernaut")
	state.add_item(preload("res://scripts/content/AndroidContent.gd").create_instance("blade", "broadsword"))
	var detail := preload("res://scripts/ui/ItemDetailSheet.gd").show(root, state, "blade")
	check(detail.get_theme_stylebox("panel") is StyleBoxTexture, "detail uses themed panel")
	check(detail.get_ok_button().get_theme_stylebox("normal") is StyleBoxTexture, "detail close uses themed frame")
	detail.queue_free()
	await process_frame
	if existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(original)
		file.close()
	else: DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	print("Visual acceptance failures: ", failures)
	quit(1 if failures else 0)
