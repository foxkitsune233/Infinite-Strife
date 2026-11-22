class_name AppSettings
extends RefCounted
## 展示偏好，独立于远征存档（对齐 Android AppSettings，存 user://presentation-settings-v1.cfg）。

const PATH := "user://presentation-settings-v1.cfg"
const DEFAULTS := {
	"animations": true, "haptics": true, "battleLog": true, "statusIcons": true,
	"sound": true, "music": true, "jingles": true, "textLevel": 0, "lastSeenRelease": "",
}
const FLAGS := ["animations", "haptics", "battleLog", "statusIcons", "sound", "jingles", "music"]
const TEXT_SCALES := [1.0, 1.15, 1.3]

static func _read() -> Dictionary:
	var result := DEFAULTS.duplicate()
	var config := ConfigFile.new()
	if config.load(PATH) == OK:
		for key in DEFAULTS.keys():
			if config.has_section_key("presentation", key):
				result[key] = config.get_value("presentation", key)
	result["textLevel"] = clampi(int(result.get("textLevel", 0)), 0, 2)
	return result

static func get_flag(key: String) -> bool:
	if key not in FLAGS:
		return false
	return bool(_read().get(key, true))

static func set_flag(key: String, value: bool) -> bool:
	if key not in FLAGS:
		return false
	return _write(key, value)

static func text_level() -> int:
	return clampi(int(_read().get("textLevel", 0)), 0, 2)

static func text_scale() -> float:
	return TEXT_SCALES[text_level()]

static func set_text_level(level: int) -> bool:
	if level < 0 or level > 2:
		return false
	return _write("textLevel", clampi(level, 0, 2))

## 把设计基准字号按玩家选择缩放，供剧情 / 战斗 / 设置文本统一调用。
static func scaled_font_size(base_size: int) -> int:
	return maxi(1, roundi(float(base_size) * text_scale()))

## 触感反馈。桌面平台无振动硬件，静默跳过，不影响玩法。
static func bump(duration_ms: int = 18) -> void:
	if not get_flag("haptics"):
		return
	if OS.has_feature("android") or OS.has_feature("iOS") or OS.has_feature("mobile"):
		Input.vibrate_handheld(duration_ms)

static func last_seen_release() -> String:
	return str(_read().get("lastSeenRelease", ""))

static func set_last_seen_release(version: String) -> bool:
	return _write("lastSeenRelease", version)

static func reset() -> bool:
	var config := ConfigFile.new()
	return config.save(PATH) == OK

static func _write(key: String, value: Variant) -> bool:
	var config := ConfigFile.new()
	config.load(PATH)
	config.set_value("presentation", key, value)
	return config.save(PATH) == OK
