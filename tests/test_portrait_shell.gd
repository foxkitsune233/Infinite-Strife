extends SceneTree

const ScreenRouterScript = preload("res://scripts/ui/ScreenRouter.gd")

var _failed := false

func _init() -> void:
	_assert_equal(ProjectSettings.get_setting("display/window/handheld/orientation"), 1, "Android must lock portrait, not only use a portrait canvas")
	_assert_equal(ProjectSettings.get_setting("display/window/size/viewport_width"), 720, "portrait viewport width")
	_assert_equal(ProjectSettings.get_setting("display/window/size/viewport_height"), 1280, "portrait viewport height")
	_assert_equal(ProjectSettings.get_setting("display/window/stretch/mode"), "canvas_items", "mobile UI stretch mode")
	_assert_equal(ScreenRouterScript.path_for("battle"), "res://scenes/Battle.tscn", "registered battle route")
	_assert_equal(ScreenRouterScript.path_for("unknown"), "", "unknown route is rejected")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
