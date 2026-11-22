extends SceneTree

func _init() -> void:
	call_deferred("_capture")

func _capture() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://migration-ui-preview.json"
	SaveRepository.erase(session.save_path)
	var state: PreparationState = session.ensure_preparation_state()
	for id in ["C1", "S1", "R1", "P1", "F1"]:
		var item := preload("res://scripts/content/AndroidContent.gd").create_instance("preview-" + id, id)
		if item != null: state.add_item(item)
	var preparation := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(preparation)
	preparation._on_mode_pressed("skills")
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png("res://docs/migration/skills-migration-preview.png")
	preparation.queue_free()
	await process_frame
	var tower := preload("res://scenes/Tower.tscn").instantiate()
	root.add_child(tower)
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png("res://docs/migration/tower-migration-preview.png")
	tower.queue_free()
	await process_frame
	var shop := preload("res://scenes/Shop.tscn").instantiate()
	root.add_child(shop)
	await process_frame
	await process_frame
	await RenderingServer.frame_post_draw
	root.get_texture().get_image().save_png("res://docs/migration/shop-migration-preview.png")
	shop.queue_free()
	await process_frame
	SaveRepository.erase(session.save_path)
	print("Migration UI rendered previews saved")
	quit()
