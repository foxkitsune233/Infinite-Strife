extends SceneTree

var _failed := false

func _init() -> void:
	_run.call_deferred()

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "res://tests/.shop-screen-save.json"
	SaveRepository.erase(session.save_path)
	var screen := preload("res://scenes/Shop.tscn").instantiate()
	root.add_child.call_deferred(screen)
	await process_frame
	await process_frame
	var offers := screen.get_node("Layout/Scroll/Offers") as GridContainer
	_assert_true(offers.get_child_count() >= 6, "shop renders the complete starter shelf")
	_assert_true((offers.get_child(0) as Button).text.contains("购买"), "offer exposes purchase action")
	_assert_true((screen.get_node("Layout/Gold") as Label).text.contains("金币"), "shop exposes current gold")
	screen.free()
	SaveRepository.erase(session.save_path)
	quit(1 if _failed else 0)

func _assert_true(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)
