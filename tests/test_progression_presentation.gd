extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
var checks := 0
var failures := 0
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
	root.get_node("AppSession").preparation_state = PreparationState.new("juggernaut")
	root.get_node("AppSession").preparation_state.backpack_size = Vector2i(4, 2)
	for level in [0, 2]:
		Settings.set_text_level(level)
		for height in [960, 1280, 1600]:
			var viewport := SubViewport.new()
			viewport.size = Vector2i(720, height)
			root.add_child(viewport)
			var page := preload("res://scenes/Progression.tscn").instantiate()
			viewport.add_child(page)
			await process_frame
			await process_frame
			for child in page._rows.get_children():
				if child is Button:
					check(child.get_theme_font_size("font_size") >= Settings.scaled_font_size(26), "button respects text size")
					check(child.size.y >= 108, "touch target height")
					check(not child.text.contains("{") and not child.text.contains("_"), "no raw dictionary or internal material IDs")
					check(child.get_global_rect().end.x <= 720, "row fits width")
				elif child is Label:
					check(child.get_theme_font_size("font_size") >= Settings.scaled_font_size(24), "quest copy readable")
			var growth: Button = page._rows.get_child(12)
			check(growth.text.contains("背包织件") and growth.text.contains("×2"), "Chinese material requirements")
			check(page._reward_text([{"type": "MATERIAL", "material_id": "backpack_fabric", "amount": 2}]) == "背包织件×2", "quest rewards use the same item name")
			# Scroll to the last row; every growth action must remain reachable.
			var scroll := page._rows.get_parent() as ScrollContainer
			scroll.scroll_vertical = int(scroll.get_v_scroll_bar().max_value)
			await process_frame
			await process_frame
			check(page._rows.get_child(-1).get_global_rect().end.y <= scroll.get_global_rect().end.y + 1, "last growth action scrolls fully into view")
			check(scroll.get_global_rect().end.y <= height - 24, "scroll remains inside page")
			check(scroll.get_global_rect().end.y <= page.get_node("Layout/Footer").global_position.y, "content clears footer")
			for button: Button in page.get_node("Layout/Footer").get_children():
				check(button.get_global_rect().end.y <= height - 23 and button.size.y >= 108, "footer reachable")
			page._grow("backpack")
			check(page._status.text.contains("背包织件") and not page._status.text.contains("backpack_fabric"), "shortage feedback uses Chinese names")
			viewport.queue_free()
			await process_frame
	if existed:
		var file := FileAccess.open(Settings.PATH, FileAccess.WRITE)
		file.store_buffer(original)
		file.close()
	else: DirAccess.remove_absolute(ProjectSettings.globalize_path(Settings.PATH))
	print("Progression presentation: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
