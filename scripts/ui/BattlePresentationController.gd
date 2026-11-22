class_name BattlePresentationController
extends RefCounted
## Presentation-only event interpreter. It never mutates BattleEngine state.

const SHAKE_WINDOW_MS := 500
const SHAKE_COOLDOWN_MS := 500
const SHAKE_HP_FRACTION := 0.05
const SHAKE_FULL_HP_FRACTION := 0.40

var animations_enabled := true
var _left := _empty_side()
var _right := _empty_side()
var _shake := 0.0
var _shake_remaining := 0.0
var _slow_motion_remaining_ms := 0.0
var _damage_windows := {}
var _next_shake_ms := 0

func _init(enabled: bool = true) -> void:
	animations_enabled = enabled

func push_event(event: Dictionary, target_max_hp: float = 0.0) -> Dictionary:
	var source := _side_for(str(event.get("source_id", "")), "left")
	var target := _opposite(source) if str(event.get("target_id", "")).is_empty() else _side_for(str(event.target_id), _opposite(source))
	var kind := str(event.get("kind", ""))
	var critical := bool(event.get("critical", false))
	var directive := {
		"source": source, "target": target, "source_pose": "attack" if kind == "physical_attack" else "cast" if kind == "skill_cast" else "idle",
		"target_flash": 0.0, "target_knockback": 0.0, "camera_shake": 0.0,
		"slow_motion_ms": 0.0, "damage_scale": 1.0, "projectile": "", "keep_number": true,
	}
	if not animations_enabled:
		return directive
	match kind:
		"physical_attack":
			directive.target_flash = 0.14
			directive.target_knockback = 16.0
			if critical:
				directive.slow_motion_ms = 90.0
				directive.damage_scale = 1.45
		"skill_cast":
			directive.target_flash = 0.22
			directive.target_knockback = 24.0
			directive.slow_motion_ms = 140.0
			directive.damage_scale = 1.2
			directive.projectile = "arcane"
		"dot_tick":
			directive.target_flash = 0.08
			directive.projectile = "poison"
		"active_used":
			directive.projectile = "aura"
	directive.camera_shake = _burst_shake(event, str(event.get("target_id", target)), target_max_hp)
	_apply(source, target, directive)
	return directive

func _burst_shake(event: Dictionary, target: String, max_hp: float) -> float:
	if max_hp <= 0.0 or event.get("kind") not in ["physical_attack", "hero_damage", "skill_cast", "dot_tick", "active_used"]:
		return 0.0
	var damage := float(event.get("exact_amount", event.get("amount", 0)))
	if damage <= 0.0: return 0.0
	var now := int(event.get("tick_ms", 0))
	var hits: Array = _damage_windows.get(target, [])
	while not hits.is_empty() and now - int(hits[0].time) > SHAKE_WINDOW_MS:
		hits.pop_front()
	hits.append({"time": now, "damage": damage})
	_damage_windows[target] = hits
	var total := 0.0
	for hit in hits: total += float(hit.damage)
	var fraction := total / max_hp
	if fraction < SHAKE_HP_FRACTION or now < _next_shake_ms: return 0.0
	hits.clear()
	_next_shake_ms = now + SHAKE_COOLDOWN_MS
	var strength := clampf(inverse_lerp(SHAKE_HP_FRACTION, SHAKE_FULL_HP_FRACTION, fraction), 0.0, 1.0)
	return lerpf(4.0, 12.0, strength)

func advance(delta_seconds: float) -> void:
	var delta := maxf(0.0, delta_seconds)
	for state in [_left, _right]:
		state.flash = maxf(0.0, float(state.flash) - delta)
		state.knockback = move_toward(float(state.knockback), 0.0, delta * 80.0)
		if float(state.flash) <= 0.0:
			state.pose = "idle"
	_shake_remaining = maxf(0.0, _shake_remaining - delta)
	if _shake_remaining <= 0.0:
		_shake = 0.0
	_slow_motion_remaining_ms = maxf(0.0, _slow_motion_remaining_ms - delta * 1000.0)

func side_state(side: String) -> Dictionary:
	return (_left if side == "left" else _right).duplicate(true)

func camera_offset() -> Vector2:
	if _shake <= 0.0 or _shake_remaining <= 0.0:
		return Vector2.ZERO
	var direction := -1.0 if int(ceil(_shake_remaining * 50.0)) % 2 == 0 else 1.0
	return Vector2(_shake * direction, _shake * 0.35)

func visual_time_scale() -> float:
	return 0.55 if _slow_motion_remaining_ms > 0.0 else 1.0

func _apply(source: String, target: String, directive: Dictionary) -> void:
	var from := _left if source == "left" else _right
	var to := _left if target == "left" else _right
	from.pose = directive.source_pose
	to.flash = float(directive.target_flash)
	to.knockback = float(directive.target_knockback) * (-1.0 if target == "left" else 1.0)
	if float(directive.camera_shake) > 0.0:
		_shake = float(directive.camera_shake)
		_shake_remaining = 0.16
	_slow_motion_remaining_ms = maxf(_slow_motion_remaining_ms, float(directive.slow_motion_ms))

static func _empty_side() -> Dictionary:
	return {"flash": 0.0, "knockback": 0.0, "pose": "idle"}

static func _opposite(side: String) -> String:
	return "right" if side == "left" else "left"

static func _side_for(id: String, fallback: String) -> String:
	if id in ["left", "cinder", "player"]:
		return "left"
	if id in ["right", "gale", "enemy"]:
		return "right"
	return fallback
