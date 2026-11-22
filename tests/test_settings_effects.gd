extends SceneTree

const Settings = preload("res://scripts/app/AppSettings.gd")
const Controller = preload("res://scripts/ui/BattlePresentationController.gd")

var _failed := false

func _assert(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	Settings.reset()
	# 字号缩放：基准字号随 textLevel 单调增大。
	var base := Settings.scaled_font_size(28)
	_assert(base >= 28, "default text scale keeps base size")
	_assert(Settings.set_text_level(2), "set largest text")
	_assert(Settings.scaled_font_size(28) > base, "text level enlarges font size")
	Settings.reset()

	# 动画开关：关闭时 presentation 只保留静态指令（无投射物/击退/震屏），数值结算不受影响。
	var on := Controller.new(true)
	var normal: Dictionary = on.push_event({"kind": "skill_cast", "source_id": "left", "target_id": "right", "amount": 40})
	_assert(normal.projectile != "" and normal.target_knockback > 0.0, "animations on emits visual directives")
	var off := Controller.new(false)
	var quiet: Dictionary = off.push_event({"kind": "skill_cast", "source_id": "left", "target_id": "right", "amount": 40})
	_assert(quiet.projectile.is_empty() and is_zero_approx(quiet.target_knockback), "animations off suppresses visual directives")
	_assert(quiet.keep_number, "animations off still keeps damage numbers")

	# 音效开关确实被 AppSettings 持久化并可被消费方读取。
	_assert(Settings.get_flag("sound"), "sound defaults on")
	_assert(Settings.set_flag("sound", false), "sound toggle saved")
	_assert(not Settings.get_flag("sound"), "sound off readable by audio bus")
	Settings.reset()
	quit(1 if _failed else 0)
