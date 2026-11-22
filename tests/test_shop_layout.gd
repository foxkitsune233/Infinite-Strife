extends SceneTree

var failures := 0

func check(ok: bool, label: String) -> void:
	if not ok:
		failures += 1
		push_error(label)

func _init() -> void:
	_run.call_deferred()

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "res://tests/.shop-layout-save.json"
	SaveRepository.erase(session.save_path)
	for viewport in [Vector2i(720, 1280), Vector2i(360, 640)]:
		root.content_scale_size = Vector2i.ZERO
		root.size = viewport
		var screen := preload("res://scenes/Shop.tscn").instantiate()
		root.add_child(screen)
		await process_frame
		await process_frame
		await process_frame
		var grid: GridContainer = screen.offers
		var scroll: ScrollContainer = screen.get_node("Layout/Scroll")
		check(scroll.size.y >= 150, "one full purchase target fits at " + str(viewport))
		check(scroll.get_global_rect().end.x <= viewport.x, "scroll fits viewport " + str(viewport))
		check(grid.size.x <= scroll.size.x + 1, "no horizontal overflow " + str(viewport))
		check(screen.get_node("Layout/Gold").get_global_rect().position.y >= screen.get_node("PageHeader").get_global_rect().end.y, "wallet below shared header " + str(viewport))
		check(screen.get_node("PageNavigation").get_global_rect().end.x <= viewport.x, "navigation fits screen " + str(viewport))
		check(screen.get_node("Layout/Status").get_global_rect().end.y <= screen.get_node("PageNavigation").get_global_rect().position.y, "status above navigation " + str(viewport))
		check(grid.get_child_count() >= 57, "full starter pool accessible " + str(viewport))
		for button in grid.get_children():
			check(button.icon != null and button.size.y >= 100, "usable icon purchase target")
		if "--capture-shop" in OS.get_cmdline_user_args():
			await RenderingServer.frame_post_draw
			var image_path := ProjectSettings.globalize_path("res://tests/.shop-layout-%d.png" % viewport.x)
			check(root.get_texture().get_image().save_png(image_path) == OK, "rendered shop screenshot")
			print("Shop screenshot: ", image_path)
		screen.free()
	SaveRepository.erase(session.save_path)
	print("Shop layout failures: ", failures)
	quit(1 if failures else 0)
