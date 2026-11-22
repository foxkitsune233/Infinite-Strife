extends SceneTree

const REGISTER_PATH := "res://assets/ASSET_REGISTER.md"

func _init() -> void:
	if not FileAccess.file_exists(REGISTER_PATH):
		push_error("Asset provenance register is missing")
		quit(1)
		return
	var lines := FileAccess.get_file_as_string(REGISTER_PATH).split("\n", false)
	for line in lines:
		if not line.begins_with("|") or line.begins_with("| path") or line.begins_with("|---"):
			continue
		var fields := line.split("|", false)
		var has_blank_field := fields.size() != 6
		for field in fields:
			has_blank_field = has_blank_field or field.strip_edges().is_empty()
		if has_blank_field:
			push_error("Asset provenance row has a blank required field: " + line)
			quit(1)
			return
	quit(0)
