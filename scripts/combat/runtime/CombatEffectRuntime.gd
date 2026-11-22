class_name CombatEffectRuntime
extends RefCounted
## Engine-neutral trigger registry. BattleEngine consumes returned actions.

var _effects_by_owner: Dictionary = {}
var _cooldown_until: Dictionary = {}

func register_effects(owner_id: String, effects: Array) -> void:
	var accepted: Array[Dictionary] = []
	for raw in effects:
		if not raw is Dictionary: continue
		var effect: Dictionary = raw.duplicate(true)
		if str(effect.get("source_key", "")).is_empty() or str(effect.get("trigger", "")).is_empty() or str(effect.get("action", "")).is_empty():
			continue
		accepted.append(effect)
	accepted.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return str(a.source_key) < str(b.source_key))
	_effects_by_owner[owner_id] = accepted

func fire(owner_id: String, trigger: String, now_ms: int) -> Array[Dictionary]:
	var actions: Array[Dictionary] = []
	for effect in _effects_by_owner.get(owner_id, []):
		if str(effect.trigger) != trigger: continue
		var key: String = "%s:%s" % [owner_id, str(effect.source_key)]
		if now_ms < int(_cooldown_until.get(key, 0)): continue
		var action: Dictionary = effect.duplicate(true)
		action.erase("trigger")
		action["owner_id"] = owner_id
		actions.append(action)
		var cooldown: int = max(0, int(effect.get("internal_cooldown_ms", 0)))
		if cooldown > 0: _cooldown_until[key] = now_ms + cooldown
	return actions

func export_state() -> Dictionary:
	return {"effects": _effects_by_owner.duplicate(true), "cooldowns": _cooldown_until.duplicate(true)}

func restore_state(data: Dictionary) -> bool:
	if not data.get("effects") is Dictionary or not data.get("cooldowns") is Dictionary: return false
	_effects_by_owner = data.effects.duplicate(true)
	_cooldown_until = data.cooldowns.duplicate(true)
	return true
