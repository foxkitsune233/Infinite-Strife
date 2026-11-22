class_name SaveRepository
extends RefCounted

const SaveCodec = preload("res://scripts/save/SaveCodec.gd")
const DEFAULT_PATH := "user://infinite_strife_save.json"

static func write(path: String, preparation: PreparationState, journey: JourneyState) -> Dictionary:
	# Never replace a file written by a newer client, even after a failed restore.
	if FileAccess.file_exists(path):
		var existing: Variant = JSON.parse_string(FileAccess.get_file_as_string(path))
		if existing is Dictionary and int(existing.get("schema_version", 0)) > SaveCodec.SCHEMA_VERSION:
			return _rejected("存档来自更高版本，已保留原文件")
	var temporary := path + ".tmp"
	var file := FileAccess.open(temporary, FileAccess.WRITE)
	if file == null:
		return _rejected("无法写入存档")
	file.store_string(JSON.stringify(SaveCodec.encode(preparation, journey)))
	file.close()
	var absolute := ProjectSettings.globalize_path(path)
	var backup := absolute + ".bak"
	var had_original := FileAccess.file_exists(path)
	if had_original:
		if FileAccess.file_exists(backup) and DirAccess.remove_absolute(backup) != OK:
			return _rejected("无法更新存档备份")
		if DirAccess.rename_absolute(absolute, backup) != OK:
			return _rejected("无法备份当前存档")
	if DirAccess.rename_absolute(ProjectSettings.globalize_path(temporary), absolute) != OK:
		if had_original:
			DirAccess.rename_absolute(backup, absolute)
		return _rejected("无法提交存档，原文件已保留")
	return {"accepted": true, "reason": ""}

static func read(path: String = DEFAULT_PATH) -> Dictionary:
	var primary := _read_single(path)
	if primary.accepted or bool(primary.get("future_version", false)):
		return primary
	var backup := _read_single(path + ".bak")
	if backup.accepted:
		backup["recovered_backup"] = true
		return backup
	return primary

static func _read_single(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return _rejected("没有可恢复的存档")
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null:
		return _rejected("无法读取存档")
	var json := JSON.new()
	var parse_error := json.parse(file.get_as_text())
	file.close()
	if parse_error != OK or not (json.data is Dictionary):
		return _rejected("存档内容无效")
	var decoded := SaveCodec.decode(json.data as Dictionary)
	if int(json.data.get("schema_version", 0)) > SaveCodec.SCHEMA_VERSION:
		decoded["future_version"] = true
	return decoded

static func erase(path: String = DEFAULT_PATH) -> void:
	if FileAccess.file_exists(path):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(path))
	for suffix in [".tmp", ".bak"]:
		if FileAccess.file_exists(path + suffix):
			DirAccess.remove_absolute(ProjectSettings.globalize_path(path + suffix))

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
