extends SceneTree

const Router = preload("res://scripts/ui/ScreenRouter.gd")

var _failed := false

func _assert(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var settings := preload("res://scenes/Settings.tscn").instantiate()
	root.add_child(settings)
	await process_frame
	await process_frame
	_assert(settings.get_child_count() > 0, "settings scene instantiates")
	var layout := settings.get_node("Layout") as VBoxContainer
	_assert(layout != null and layout.get_child_count() >= 4, "settings renders tabs, body and back button")
	# 三个页签切换不报错且内容变化。
	var tabs := layout.get_child(1) as HBoxContainer
	_assert(tabs != null and tabs.get_child_count() == 3, "three settings tabs present")
	var scroll := layout.get_child(2) as ScrollContainer
	var gameplay_count := (scroll.get_child(0) as VBoxContainer).get_child_count()
	(tabs.get_child(1) as Button).pressed.emit()
	await process_frame
	var storage_count := (settings.get_node("Layout").get_child(2).get_child(0) as VBoxContainer).get_child_count()
	_assert(storage_count > 0 and storage_count != gameplay_count, "storage tab renders distinct content")
	tabs = settings.get_node("Layout").get_child(1) as HBoxContainer
	(tabs.get_child(2) as Button).pressed.emit()
	await process_frame
	_assert((settings.get_node("Layout").get_child(2).get_child(0) as VBoxContainer).get_child_count() > 0, "gallery tab renders")
	_assert(Router.path_for("settings").ends_with("Settings.tscn"), "camp settings entry resolves")
	settings.free()
	quit(1 if _failed else 0)
