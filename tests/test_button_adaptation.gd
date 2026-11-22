extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const Layout = preload("res://scripts/ui/CorePageLayout.gd")
var failures := 0
var checks := 0

func check(ok: bool, why: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(why)

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var existed := FileAccess.file_exists(Settings.PATH)
	var original := FileAccess.get_file_as_bytes(Settings.PATH) if existed else PackedByteArray()
	var session := root.get_node("AppSession")
	session.preparation_state = PreparationState.new("juggernaut")
	for height in [1280, 1600, 960]:
		for level in [0, 2]:
			Settings.set_text_level(level)
			var viewport := SubViewport.new()
			viewport.size = Vector2i(720, height)
			root.add_child(viewport)
			var battle := preload("res://scenes/Battle.tscn").instantiate()
			viewport.add_child(battle)
			battle.set_process(false)
			battle._engine._left.hero_state.has_aghanim = true
			battle._engine._left.hero_state.has_shard = true
			battle._refresh_hud()
			await process_frame
			await process_frame
			var buttons: Array = [battle.manual_skill_button, battle._aghanim_button]
			buttons.append_array(battle.manual_items.get_children())
			for button: Button in buttons:
				var rect := button.get_global_rect()
				check(rect.size.y * 320.0 / 720.0 >= 48.0, "320-wide touch height: %s/%d/%d" % [button.name, height, level])
				check(rect.size.x * 320.0 / 720.0 >= 48.0, "320-wide touch width: %s" % button.name)
				check(rect.position.x >= 0 and rect.end.x <= 721 and rect.position.y >= 0 and rect.end.y <= height + 1, "button inside viewport: %s/%d/%d" % [button.name, height, level])
			check(battle.get_node("TopHud").get_global_rect().end.y + 5 <= battle.get_node("Bottom").position.y, "HUD clears controls")
			check(battle.get_node("BattleStage").get_global_rect().end.y <= battle.get_node("TopHud").position.y, "arena clears HUD")
			viewport.size.y = 960
			await process_frame
			await process_frame
			await process_frame
			check(battle.get_node("Bottom/ManualControls").get_global_rect().end.y <= 961, "live resize keeps controls visible")
			check(battle.manual_items.get_global_rect().end.y >= 890, "compact reflow releases obsolete vertical space")
			viewport.size.y = height
			await process_frame
			await process_frame
			check(not battle.manual_skill_button.get_global_rect().intersects(battle._aghanim_button.get_global_rect()), "skill and Aghanim targets never overlap")
			battle._engine._right.hp = 0
			battle._engine._check_defeat()
			battle._show_result()
			await process_frame
			await process_frame
			check(battle.result_button.get_global_rect().end.y <= height + 1, "result button stays inside viewport")
			battle.queue_free()
			await process_frame
			var page := Control.new()
			viewport.add_child(page)
			page.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
			Layout.install(page, "home", "营地", "按钮适配验收")
			await process_frame
			await process_frame
			for button: Button in page.get_node("PageNavigation").get_children():
				check(button.size.y * 320.0 / 720.0 >= 48.0, "navigation touch height")
				check(button.get_global_rect().end.y <= height + 1, "navigation inside viewport")
				check(button.get_theme_stylebox("normal") != button.get_theme_stylebox("pressed"), "press feedback is distinct")
				check(button.has_theme_stylebox_override("focus"), "keyboard focus is visible")
			page.queue_free()
			await process_frame
			for scene_path in ["res://scenes/MainMenu.tscn", "res://scenes/Expedition.tscn", "res://scenes/Preparation.tscn"]:
				var screen: Control = load(scene_path).instantiate()
				viewport.add_child(screen)
				await process_frame
				await process_frame
				var content: Control = screen.get_node("Center" if scene_path.contains("MainMenu") else ("Scroll" if scene_path.contains("Expedition") else "Layout"))
				check(content.get_global_rect().end.y <= screen.get_node("PageNavigation").position.y, "page content clears navigation: " + scene_path + "/" + str(height))
				if scene_path.contains("Preparation"):
					for mode in ["skills", "supplies"]:
						screen._on_mode_pressed(mode)
						await process_frame
						await process_frame
						check(content.get_global_rect().end.y <= screen.get_node("PageNavigation").position.y, "preparation mode clears navigation: " + mode + "/" + str(height))
						if mode == "supplies":
							screen._on_camp_pressed()
							screen._on_inventory_more_pressed()
							await process_frame
							await process_frame
							check(content.get_global_rect().end.y <= screen.get_node("PageNavigation").position.y, "expanded tools clear navigation")
							viewport.size.y = 960
							await process_frame
							await process_frame
							check(content.get_global_rect().end.y <= screen.get_node("PageNavigation").position.y, "live resize with expanded tools clears navigation")
							viewport.size.y = height
							await process_frame
							await process_frame
				screen.queue_free()
				await process_frame
			viewport.queue_free()
			await process_frame
	if existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(original)
		file.close()
	else: DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	print("Button adaptation: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
