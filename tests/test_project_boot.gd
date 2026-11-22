extends SceneTree

func _init() -> void:
	if not FileAccess.file_exists("res://scenes/MainMenu.tscn"):
		push_error("MainMenu startup scene is missing")
		quit(1)
		return
	quit(0)
