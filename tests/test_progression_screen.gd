extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var screen := preload("res://scenes/Progression.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	assert(screen._rows.get_child_count() == 17)
	assert(screen._status.text.contains("金币"))
	var visible_quest := screen._rows.get_child(0) as Label
	assert(visible_quest.text.contains("整装出发"))
	assert((screen._rows.get_child(1) as Button).text == "领取奖励")
	assert(ScreenRouter.path_for("progression") == "res://scenes/Progression.tscn")
	screen.queue_free()
	await process_frame
	quit(0)
