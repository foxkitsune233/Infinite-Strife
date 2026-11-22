extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const Backup = preload("res://scripts/app/BackupRules.gd")
const SaveRepository = preload("res://scripts/save/SaveRepository.gd")
const Router = preload("res://scripts/ui/ScreenRouter.gd")

var _failed := false

func _assert(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	# 偏好持久化：默认值、写入、读取、重置。
	Settings.reset()
	_assert(Settings.get_flag("sound"), "sound defaults on")
	_assert(Settings.text_level() == 0, "text level defaults to standard")
	_assert(Settings.set_flag("sound", false), "flag write accepted")
	_assert(not Settings.get_flag("sound"), "flag persists after write")
	_assert(Settings.set_text_level(2), "text level write accepted")
	_assert(is_equal_approx(Settings.text_scale(), 1.3), "text scale tracks level")
	Settings.reset()
	_assert(Settings.get_flag("sound"), "reset restores defaults")
	# 备份导出/校验/恢复往返。
	var session := root.get_node("AppSession")
	session.save_path = "user://test_settings_backup.json"
	SaveRepository.erase(session.save_path)
	var state := PreparationState.new("juggernaut")
	state.gold = 777
	state.quest_progress["clears"] = 3
	var journey := JourneyState.new(4242)
	session.preparation_state = state
	session.journey_state = journey
	var backup_text := Backup.export_text(state, journey)
	var checked := Backup.inspect(backup_text)
	_assert(bool(checked.get("accepted", false)), "valid backup passes inspection")
	_assert(int(checked.get("gold", 0)) == 777, "backup reports gold")
	_assert(int(checked.get("items", -1)) == state.items.size(), "backup reports item count")
	# 篡改校验和必须被拒绝。
	var tampered := backup_text.replace("\"checksum\":", "\"checksum\":9")
	var rejected := Backup.inspect(tampered)
	_assert(not bool(rejected.get("accepted", true)), "tampered backup rejected")
	# 先写入一份当前档，再从备份恢复。
	state.gold = 1
	var saved := SaveRepository.write(session.save_path, state, journey)
	_assert(bool(saved.get("accepted", false)), "current save written before restore")
	var restored := Backup.restore(backup_text, session.save_path)
	_assert(bool(restored.get("accepted", false)), "restore accepted")
	var reread := SaveRepository.read(session.save_path)
	_assert(bool(reread.get("accepted", false)) and reread.preparation.gold == 777, "restored gold matches backup")
	_assert(FileAccess.file_exists(session.save_path + ".bak"), "restore preserves previous save as backup")
	# 恢复后内存同步：当前会话金币必须从 1 变为备份的 777，再保存不回退。
	session.preparation_state = state
	session.journey_state = journey
	var adopted: Dictionary = session.call("restore_from_backup", backup_text)
	_assert(bool(adopted.get("accepted", false)), "session restore_from_backup accepted")
	_assert(session.preparation_state.gold == 777, "in-memory gold synced after restore (not stale 1)")
	var resave: Dictionary = session.call("save_progress")
	_assert(bool(resave.get("accepted", false)), "resave after restore accepted")
	_assert(SaveRepository.read(session.save_path).preparation.gold == 777, "resave keeps restored gold, no stale overwrite")
	# 无现存存档时恢复必须真正落盘（不能"假成功"）。
	var fresh_path := "user://test_settings_fresh_restore.json"
	SaveRepository.erase(fresh_path)
	_assert(not FileAccess.file_exists(fresh_path), "fresh path starts empty")
	var fresh_result := Backup.restore(backup_text, fresh_path)
	_assert(bool(fresh_result.get("accepted", false)), "restore to absent save accepted")
	_assert(FileAccess.file_exists(fresh_path), "restore to absent save actually writes file")
	_assert(SaveRepository.read(fresh_path).preparation.gold == 777, "fresh restored file contains backup gold")
	SaveRepository.erase(fresh_path)
	# 路由注册。
	_assert(Router.path_for("settings").ends_with("Settings.tscn"), "settings route registered")
	SaveRepository.erase(session.save_path)
	if FileAccess.file_exists(session.save_path + ".bak"):
		DirAccess.remove_absolute(ProjectSettings.globalize_path(session.save_path + ".bak"))
	quit(1 if _failed else 0)
