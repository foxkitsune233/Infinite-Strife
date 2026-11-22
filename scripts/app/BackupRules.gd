class_name BackupRules
extends RefCounted
## 玩家存档备份：导出为带 SHA256 校验的 JSON 文本，恢复前验证格式/校验和/规则。
## 对齐 Android SettingsActivity 的 export/restore 流程；不生成物品、不修改金币。

const SaveCodec = preload("res://scripts/save/SaveCodec.gd")
const SaveRepository = preload("res://scripts/save/SaveRepository.gd")
const SCHEMA := 1
const MAX_BYTES := 1024 * 1024 + 64 * 1024

static func export_text(preparation: PreparationState, journey: JourneyState) -> String:
	var payload := SaveCodec.encode(preparation, journey)
	var content := JSON.stringify(payload, "", false)
	var envelope := {
		"backup_schema": SCHEMA,
		"game_schema": SaveCodec.SCHEMA_VERSION,
		"checksum": content.sha256_text(),
		"payload": content,
	}
	return JSON.stringify(envelope, "", false)

static func inspect(text: String) -> Dictionary:
	if text.length() > MAX_BYTES:
		return _rejected("备份文件超过大小上限")
	var json := JSON.new()
	if json.parse(text) != OK or not (json.data is Dictionary):
		return _rejected("备份不是有效的 JSON")
	var envelope: Dictionary = json.data
	if int(envelope.get("backup_schema", 0)) != SCHEMA:
		return _rejected("备份版本不兼容")
	if int(envelope.get("game_schema", 0)) > SaveCodec.SCHEMA_VERSION:
		return _rejected("备份来自更高版本的游戏")
	var content := str(envelope.get("payload", ""))
	if str(envelope.get("checksum", "")) != content.sha256_text():
		return _rejected("备份校验和不匹配，文件可能已损坏")
	var payload_json := JSON.new()
	if payload_json.parse(content) != OK or not (payload_json.data is Dictionary):
		return _rejected("备份内容无效")
	var decoded := SaveCodec.decode(payload_json.data as Dictionary)
	if not bool(decoded.get("accepted", false)):
		return _rejected(str(decoded.get("reason", "备份存档无法解码")))
	var preparation: PreparationState = decoded.preparation
	return {
		"accepted": true, "reason": "",
		"gold": preparation.gold, "items": preparation.items.size(),
		"stage": "营地" if preparation.phase == PreparationState.Phase.HOME else "挑战中",
		"decoded": decoded,
	}

static func restore(text: String, path: String = SaveRepository.DEFAULT_PATH) -> Dictionary:
	var checked := inspect(text)
	if not bool(checked.get("accepted", false)):
		return checked
	# 恢复前把当前有效存档保留为 .bak（对齐 Android 保留上一代备份）。仅在原文件存在时备份。
	var absolute := ProjectSettings.globalize_path(path)
	var backup := absolute + ".bak"
	var had_original := FileAccess.file_exists(path)
	if had_original:
		if FileAccess.file_exists(backup) and DirAccess.remove_absolute(backup) != OK:
			return _rejected("无法更新存档备份")
		if DirAccess.rename_absolute(absolute, backup) != OK:
			return _rejected("无法备份当前存档")
	# 写入必须无条件执行：没有现存存档时也要真正落盘，不能只返回成功。
	var decoded: Dictionary = checked.decoded
	var written := SaveRepository.write(path, decoded.preparation, decoded.journey)
	if not bool(written.get("accepted", false)):
		if had_original and FileAccess.file_exists(backup):
			DirAccess.rename_absolute(backup, absolute)
		return written
	return {"accepted": true, "reason": "备份已恢复"}

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
