class_name BattleEngine
extends RefCounted

const TIME_LIMIT_MS := 60000
var time_limit_ms := TIME_LIMIT_MS
var timeout_left_loses := false
const AndroidRuntime = preload("res://scripts/battle/AndroidHeroRuntime.gd")
const AghanimRuntime = preload("res://scripts/battle/HeroAghanimRuntime.gd")
const EffectRuntime = preload("res://scripts/combat/runtime/CombatEffectRuntime.gd")

var _left: FighterState
var _right: FighterState
var _log := BattleLog.new()
var _left_attacks_first_on_tie := true
var _random: BattleSeed
var _elapsed_ms := 0
var _next_left_attack_ms := 1000
var _next_right_attack_ms := 1000
var _result: Dictionary = {}
var _hero_runtime: AndroidRuntime
var _android_mode := false
var _android_pending_ms := 0
var _effect_runtime := EffectRuntime.new()

func _init(seed: int, left: FighterState, right: FighterState) -> void:
	_left = left
	_right = right
	_random = BattleSeed.new(seed)
	_left_attacks_first_on_tie = _random.next_bool()
	_hero_runtime = AndroidRuntime.new(seed)
	_android_mode = AndroidRuntime.supports(left) or AndroidRuntime.supports(right)
	_hero_runtime.initialize(left)
	_hero_runtime.initialize(right)
	for fighter in [left, right]:
		if not fighter.hero_state.has("aghanim_hp_history"):
			AghanimRuntime.advance(_hero_runtime, fighter, right if fighter == left else left, 0, 0, false)
	if AndroidRuntime.supports(left): _next_left_attack_ms = 100
	if AndroidRuntime.supports(right): _next_right_attack_ms = 100

func step(delta_ms: int) -> Array[Dictionary]:
	if _android_mode:
		return _step_android(delta_ms)
	var emitted: Array[Dictionary] = []
	if finished() or delta_ms <= 0:
		return emitted
	_left.advance_time(delta_ms)
	_right.advance_time(delta_ms)
	_advance_dots(delta_ms, emitted)
	if finished():
		return emitted
	var target_ms: int = min(_elapsed_ms + delta_ms, time_limit_ms)
	while not finished() and min(_next_left_attack_ms, _next_right_attack_ms) <= target_ms:
		_elapsed_ms = min(_next_left_attack_ms, _next_right_attack_ms)
		if _next_left_attack_ms == _next_right_attack_ms:
			if _left_attacks_first_on_tie:
				_attack(_left, _right, emitted)
				if not finished():
					_attack(_right, _left, emitted)
			else:
				_attack(_right, _left, emitted)
				if not finished():
					_attack(_left, _right, emitted)
		elif _next_left_attack_ms < _next_right_attack_ms:
			_attack(_left, _right, emitted)
		else:
			_attack(_right, _left, emitted)
	_elapsed_ms = target_ms
	if not finished() and _elapsed_ms >= time_limit_ms:
		_finish_timeout()
	return emitted

# Android uses deterministic 100ms simulation ticks. Large calls are subdivided;
# sub-tick input is retained rather than skipping duration/poison boundaries.
func _step_android(delta_ms: int) -> Array[Dictionary]:
	var emitted: Array[Dictionary] = []
	if finished() or delta_ms <= 0: return emitted
	_android_pending_ms += delta_ms
	while _android_pending_ms >= 100 and not finished():
		_android_pending_ms -= 100
		var stopped_left := _left.has_status("time_stop")
		var stopped_right := _right.has_status("time_stop")
		if _left.hero_state.has("tower_supply_base_regen"):
			_left.health_regen = float(_left.hero_state.tower_supply_base_regen) + (_left.max_hp * 0.01 if _elapsed_ms < int(_left.hero_state.get("tower_supply_until_ms", 0)) else 0.0)
		_left.advance_time(100)
		_right.advance_time(100)
		if stopped_left: _next_left_attack_ms += 100
		if stopped_right: _next_right_attack_ms += 100
		_elapsed_ms += 100
		_hero_runtime.set_time(_elapsed_ms)
		var left_first := _hero_runtime.left_first_this_tick()
		_advance_dots(100, emitted)
		if finished(): break
		_hero_runtime.advance(_left, _right, 100, _elapsed_ms, stopped_left, stopped_right)
		_drain_hero_events(emitted)
		_check_defeat()
		if finished(): break
		var order: Array = [_left, _right] if left_first else [_right, _left]
		for source in order:
			var next_attack := _next_left_attack_ms if source == _left else _next_right_attack_ms
			if next_attack <= _elapsed_ms:
				_attack(source, _right if source == _left else _left, emitted)
				if finished(): break
		AghanimRuntime.advance(_hero_runtime, _left, _right, 100, _elapsed_ms, stopped_left)
		AghanimRuntime.advance(_hero_runtime, _right, _left, 100, _elapsed_ms, stopped_right)
		if not finished() and _elapsed_ms >= time_limit_ms: _finish_timeout()
	if finished(): _android_pending_ms = 0
	return emitted

func _drain_hero_events(emitted: Array[Dictionary]) -> void:
	for event in _hero_runtime.drain_events():
		emitted.append(_log.record_event(event))

func _check_defeat() -> void:
	if not _left.is_alive() or not _right.is_alive():
		var winner := "draw" if not _left.is_alive() and not _right.is_alive() else (_right.id if not _left.is_alive() else _left.id)
		_result = {"kind": "defeat", "winner_id": winner, "finished_at_ms": _elapsed_ms}

func finished() -> bool:
	return not _result.is_empty()

func result() -> Dictionary:
	return _result.duplicate(true)

func full_log() -> Array[Dictionary]:
	return _log.events.duplicate(true)

func register_combat_effects(owner_id: String, effects: Array) -> void:
	_effect_runtime.register_effects(owner_id, effects)
	_execute_effect_actions(owner_id, _effect_runtime.fire(owner_id, "BATTLE_START", _elapsed_ms))

func _execute_effect_actions(owner_id: String, actions: Array[Dictionary]) -> void:
	var source := _fighter_for_id(owner_id)
	if source == null: return
	for action in actions:
		match str(action.get("action", "")):
			"GRANT_SHIELD": source.shield += maxf(0.0, float(action.get("amount", 0.0)))
			"RESTORE_MANA": source.mp = minf(source.max_mp, source.mp + float(action.get("amount", 0.0)))

func command(command_data: Dictionary) -> Dictionary:
	match command_data.get("kind", ""):
		"apply_status":
			var target := _fighter_for_id(str(command_data.get("target_id", "")))
			if target == null:
				return {"accepted": false, "reason": "unknown_target"}
			target.add_status(str(command_data.get("status", "")), int(command_data.get("duration_ms", 0)))
			return {"accepted": true}
		"cast_skill":
			return _cast_skill(command_data)
		"cast_aghanim":
			return _cast_aghanim(command_data)
		"apply_dot":
			return _apply_dot(command_data)
		"use_active":
			return _use_active(command_data)
		_:
			return {"accepted": false, "reason": "unknown_command"}

func snapshot() -> Dictionary:
	return {
		"elapsed_ms": _elapsed_ms,
		"left": _fighter_snapshot(_left),
		"right": _fighter_snapshot(_right),
		"finished": finished(),
	}

func export_state() -> Dictionary:
	# Decimal strings preserve 64-bit Godot RNG state through JSON number conversion.
	var state := {
		"version": 1, "left": _left.to_data(), "right": _right.to_data(),
		"elapsed_ms": _elapsed_ms, "next_left_attack_ms": _next_left_attack_ms,
		"time_limit_ms": time_limit_ms, "timeout_left_loses": timeout_left_loses,
		"next_right_attack_ms": _next_right_attack_ms, "left_first": _left_attacks_first_on_tie,
		"rng_seed": str(_random._random.seed), "rng_state": str(_random._random.state),
		"android_mode": _android_mode, "android_pending_ms": _android_pending_ms,
		"hero_runtime": _hero_runtime.to_data(), "result": _result.duplicate(true), "log": _log.events.duplicate(true),
	}
	# Default JSON.stringify rounds floats. A Variant payload is lossless and also
	# preserves typed numeric values inside log/control/counter dictionaries.
	return {"version": 1, "encoding": "godot_variant_base64", "payload": Marshalls.raw_to_base64(var_to_bytes(state))}

static func from_state(snapshot_data: Dictionary) -> BattleEngine:
	if int(snapshot_data.get("version", 0)) != 1 or snapshot_data.get("encoding", "") != "godot_variant_base64": return null
	if not snapshot_data.get("payload", null) is String or str(snapshot_data.payload).is_empty(): return null
	var decoded: Variant = bytes_to_var(Marshalls.base64_to_raw(str(snapshot_data.payload)))
	if not decoded is Dictionary: return null
	var data: Dictionary = decoded
	if int(data.get("version", 0)) != 1: return null
	for key in ["left", "right", "hero_runtime", "result"]:
		if not data.get(key, null) is Dictionary: return null
	for key in ["elapsed_ms", "next_left_attack_ms", "next_right_attack_ms", "android_pending_ms"]:
		if not (data.get(key, null) is int or data.get(key, null) is float): return null
		if not is_finite(float(data[key])) or float(data[key]) < 0: return null
	var limit: Variant = data.get("time_limit_ms", TIME_LIMIT_MS)
	if not limit is int or limit < 100 or limit > 2000000: return null
	if not data.get("timeout_left_loses", false) is bool: return null
	if int(data.elapsed_ms) > int(limit) or int(data.android_pending_ms) >= 100: return null
	if not data.get("left_first", null) is bool or not data.get("android_mode", null) is bool: return null
	if not str(data.get("rng_seed", "")).is_valid_int() or not str(data.get("rng_state", "")).is_valid_int(): return null
	if not data.get("log", null) is Array: return null
	for event in data.log:
		if not event is Dictionary: return null
	var left := FighterState.from_data(data.left)
	var right := FighterState.from_data(data.right)
	if left == null or right == null: return null
	var restored := BattleEngine.new(1, left, right)
	restored.time_limit_ms = int(limit)
	restored.timeout_left_loses = bool(data.get("timeout_left_loses", false))
	if not restored._hero_runtime.restore_data(data.hero_runtime): return null
	restored._elapsed_ms = int(data.elapsed_ms)
	restored._next_left_attack_ms = int(data.next_left_attack_ms)
	restored._next_right_attack_ms = int(data.next_right_attack_ms)
	restored._left_attacks_first_on_tie = bool(data.left_first)
	restored._random._random.seed = int(str(data.rng_seed))
	restored._random._random.state = int(str(data.rng_state))
	restored._android_mode = bool(data.android_mode)
	restored._android_pending_ms = int(data.android_pending_ms)
	restored._result = data.result.duplicate(true)
	restored._log.events.assign(data.log.duplicate(true))
	return restored

func _attack(source: FighterState, target: FighterState, emitted: Array[Dictionary]) -> void:
	if _android_mode:
		if not source.action_block("attack").is_empty():
			if source == _left: _next_left_attack_ms = _elapsed_ms + 100
			else: _next_right_attack_ms = _elapsed_ms + 100
			return
		_hero_runtime.attack(source, target, _elapsed_ms)
		_drain_hero_events(emitted)
		var interval := source.effective_attack_interval_ms()
		if source.hero_id == "juggernaut" and source.timer("invulnerable") > 0:
			interval = maxi(1, roundi(float(interval) / 1.4))
		if source == _left:
			_next_left_attack_ms = _elapsed_ms + interval
		else:
			_next_right_attack_ms = _elapsed_ms + interval
		_check_defeat()
		return
	if source == _left:
		_next_left_attack_ms += source.effective_attack_interval_ms()
	else:
		_next_right_attack_ms += source.effective_attack_interval_ms()
	if source.has_status("stun"):
		return
	var critical := source.critical_chance_percent >= 100
	if not critical and source.critical_chance_percent > 0:
		critical = _random.next_percent() <= source.critical_chance_percent
	var raw_damage := source.attack_damage
	if critical:
		raw_damage = roundi(float(raw_damage) * source.critical_multiplier)
	var amount: int = target.receive_physical_damage(raw_damage)
	var healed := _heal_from_lifesteal(source, amount)
	emitted.append(_log.record_attack(_elapsed_ms, source, target, amount, critical, healed))
	_check_defeat()

func _finish_timeout() -> void:
	var left_ratio: float = float(_left.hp) / float(_left.max_hp)
	var right_ratio: float = float(_right.hp) / float(_right.max_hp)
	var winner_id := "draw"
	if left_ratio > right_ratio:
		winner_id = _left.id
	elif right_ratio > left_ratio:
		winner_id = _right.id
	if timeout_left_loses:
		winner_id = _right.id
	_result = {"kind": "timeout", "winner_id": winner_id, "finished_at_ms": time_limit_ms}

func _fighter_snapshot(fighter: FighterState) -> Dictionary:
	return {
		"id": fighter.id,
		"hp": fighter.hp,
		"max_hp": fighter.max_hp,
		"mp": fighter.mp,
		"max_mp": fighter.max_mp,
		"shield": fighter.shield,
		"total_shields": fighter.total_shields(),
		"cooldowns": fighter.cooldowns.duplicate(true),
		"item_block": fighter.action_block("item") if _android_mode else ("stunned" if fighter.has_status("stun") else ""),
		"consumable_block": fighter.action_block("consumable"),
		"skill_block": fighter.action_block("skill") if _android_mode else ("stunned" if fighter.has_status("stun") else "silenced" if fighter.has_status("silence") else ""),
		"statuses": fighter.status_snapshot(),
		"dots": fighter.dots.duplicate(true),
		"hero_id": fighter.hero_id,
		"health_regen": fighter.health_regen,
		"mana_regen": fighter.mana_regen,
		"armor": fighter.armor,
		"magic_shield": fighter.magic_shield,
		"hero_timers": fighter.hero_timers.duplicate(true),
		"hero_state": fighter.hero_state.duplicate(true),
		"timed_controls": fighter.timed_controls.duplicate(true),
		"hero_poisons": fighter.hero_poisons.duplicate(true),
	}

func _fighter_for_id(id: String) -> FighterState:
	if id == "left" or id == _left.id:
		return _left
	if id == "right" or id == _right.id:
		return _right
	return null

func use_consumable(state: PreparationState, item_id: String) -> Dictionary:
	var item := state.item(item_id)
	if finished() or state.phase != PreparationState.Phase.BATTLE:
		return {"accepted": false, "reason": "当前不是进行中的战斗"}
	if item == null or item.location != ItemInstance.Location.EQUIPMENT or item.slot < 0 or item.slot >= 6 or item.kind != ItemInstance.Kind.CONSUMABLE or item.definition_id not in ["faerie_fire", "cheese"]:
		return {"accepted": false, "reason": "只能使用前六格装备中的仙灵之火或奶酪"}
	var blocked := _left.action_block("consumable")
	if not blocked.is_empty():
		return {"accepted": false, "reason": blocked}
	var healing := _left.heal(100.0 if item.definition_id == "faerie_fire" else _left.max_hp * 0.4)
	if item.definition_id == "cheese":
		_left.mp = minf(_left.max_mp, _left.mp + _left.max_mp * 0.3 * (1.5 if _left.timer("ghost") > 0 else 1.0))
	var definition := preload("res://scripts/content/ContentCatalog.gd").item(item.definition_id)
	if definition != null:
		_left.attack_damage -= float(definition.source_data.get("stats", {}).get("attack", 0.0))
	state.remove_item(item_id)
	var event := _log.record_event({"kind": "consumable_used", "source_id": _left.id, "target_id": _left.id, "amount": healing, "item_id": item.definition_id, "time_ms": _elapsed_ms})
	return {"accepted": true, "reason": "", "event": event}

func _cast_aghanim(command_data: Dictionary) -> Dictionary:
	if finished(): return {"accepted": false, "reason": "finished"}
	var source := _fighter_for_id(str(command_data.get("source_id", "")))
	if source == null: return {"accepted": false, "reason": "unknown_source"}
	var target := _right if source == _left else _left
	_hero_runtime.set_time(_elapsed_ms)
	var result: Dictionary = AghanimRuntime.cast(_hero_runtime, source, target, _elapsed_ms)
	if result.accepted:
		var events: Array[Dictionary] = []
		_drain_hero_events(events)
		result.events = events
		result.event = _log.record_event(result.event)
		if source.hero_id == "juggernaut":
			if source == _left: _next_left_attack_ms = mini(_next_left_attack_ms, _elapsed_ms + 100)
			else: _next_right_attack_ms = mini(_next_right_attack_ms, _elapsed_ms + 100)
		_check_defeat()
	return result

func _cast_skill(command_data: Dictionary) -> Dictionary:
	var source := _fighter_for_id(str(command_data.get("source_id", "")))
	if source == null:
		return {"accepted": false, "reason": "unknown_source"}
	var skill: Dictionary = command_data.get("skill", {})
	if AndroidRuntime.supports(source) and str(skill.get("runtime_mode", "")) in ["generic_placeholder", "android_hero"]:
		if finished(): return {"accepted": false, "reason": "finished"}
		var opponent := _right if source == _left else _left
		var result := _hero_runtime.cast(source, opponent, skill, _elapsed_ms)
		if bool(result.accepted):
			var events: Array[Dictionary] = []
			_drain_hero_events(events)
			result.events = events
			result.event = _log.record_event(result.event)
			if source.hero_id == "juggernaut":
				if source == _left: _next_left_attack_ms = mini(_next_left_attack_ms, _elapsed_ms + 100)
				else: _next_right_attack_ms = mini(_next_right_attack_ms, _elapsed_ms + 100)
			_check_defeat()
		return result
	if _android_mode:
		var block := source.action_block("skill")
		if not block.is_empty(): return {"accepted": false, "reason": block}
	if source.has_status("silence"):
		return {"accepted": false, "reason": "silenced"}
	if source.has_status("stun"):
		return {"accepted": false, "reason": "stunned"}
	var skill_id := str(skill.get("id", ""))
	if skill_id.is_empty():
		return {"accepted": false, "reason": "invalid_skill"}
	var mana_cost: int = max(0, int(skill.get("mana_cost", 0)))
	if source.mp < mana_cost:
		return {"accepted": false, "reason": "insufficient_mana"}
	if int(source.cooldowns.get(skill_id, 0)) > 0:
		return {"accepted": false, "reason": "cooldown"}
	var target := _right if source == _left else _left
	source.mp -= mana_cost
	source.cooldowns[skill_id] = max(0, int(skill.get("cooldown_ms", 0)))
	source.hero_state["ultimate_key"] = skill_id
	var resolved_source := source
	var reflected := false
	if _android_mode:
		_hero_runtime.set_time(_elapsed_ms)
		reflected = _hero_runtime.reflect_skill(source, target, "skill:" + skill_id)
		if reflected:
			resolved_source = target
			target = source
	var amount: int
	if _android_mode:
		var hit := _hero_runtime.external_damage(resolved_source, target, maxf(0, float(skill.get("magic_damage", 0))), "magic", "generic_skill:reflected" if reflected else "generic_skill", _elapsed_ms, false)
		amount = roundi(float(hit.life_damage))
		var events: Array[Dictionary] = []
		_drain_hero_events(events)
	else:
		amount = target.receive_magic_damage(max(0, int(skill.get("magic_damage", 0))))
	var stun_ms: int = max(0, int(skill.get("stun_ms", 0)))
	if stun_ms > 0:
		if _android_mode:
			_hero_runtime.control(resolved_source, target, "stun", stun_ms, "skill:" + skill_id + (":reflected" if reflected else ""))
			var events: Array[Dictionary] = []
			_drain_hero_events(events)
		else: target.add_status("stun", stun_ms)
	var event := _log.record_event({
		"tick_ms": _elapsed_ms,
		"source_id": resolved_source.id,
		"target_id": target.id,
		"kind": "skill_cast",
		"skill_id": skill_id,
		"amount": amount,
		"school_id": "arcane",
		"effect_id": str(skill.get("effect_id", "effect_arcane_cast")),
		"remaining_hp": target.hp,
	})
	_check_defeat()
	return {"accepted": true, "event": event}

func _apply_dot(command_data: Dictionary) -> Dictionary:
	var target := _fighter_for_id(str(command_data.get("target_id", "")))
	var source := _fighter_for_id(str(command_data.get("source_id", "")))
	if target == null or source == null:
		return {"accepted": false, "reason": "unknown_fighter"}
	target.add_dot(
		str(command_data.get("effect_id", "")), source.id,
		int(command_data.get("damage", 0)), int(command_data.get("duration_ms", 0)), int(command_data.get("interval_ms", 1000))
	)
	return {"accepted": true}

func _use_active(command_data: Dictionary) -> Dictionary:
	if finished(): return {"accepted": false, "reason": "finished"}
	var source := _fighter_for_id(str(command_data.get("source_id", "")))
	if source == null:
		return {"accepted": false, "reason": "unknown_source"}
	if _android_mode:
		var block := source.action_block("item")
		if not block.is_empty(): return {"accepted": false, "reason": block}
	if source.has_status("stun"):
		return {"accepted": false, "reason": "stunned"}
	var item: Dictionary = command_data.get("item", {})
	var item_id := str(item.get("id", ""))
	if item_id.is_empty():
		return {"accepted": false, "reason": "invalid_item"}
	var cooldown_id := "active:" + item_id
	if int(source.cooldowns.get(cooldown_id, 0)) > 0:
		return {"accepted": false, "reason": "cooldown"}
	var target := _right if source == _left else _left
	var kind := str(item.get("kind", ""))
	var automatic := bool(command_data.get("automatic", false))
	var life_damage := 0.0
	match kind:
		"shield":
			source.grant_shield("active:" + item_id, "UNIVERSAL", float(item.get("shield", 80)), int(item.get("duration_ms", 0)))
		"aeon_disk":
			if not _hero_runtime.try_aeon_disk(source, item, false):
				return {"accepted": false, "reason": "hp_threshold"}
			target = source
		"magic_damage", "ethereal":
			if automatic and kind == "ethereal" and (target.timer("ghost") > 0 or target.timer("invulnerable") > 0):
				return {"accepted": false, "reason": "target_not_ready"}
			if _android_mode or kind == "ethereal":
				var damage_type := "pure" if kind == "ethereal" else "magic"
				var hit := _hero_runtime.external_damage(source, target, float(item.get("damage", 0)), damage_type, "item:" + item_id, _elapsed_ms)
				life_damage = float(hit.life_damage)
				var events: Array[Dictionary] = []
				_drain_hero_events(events)
			else:
				life_damage = target.receive_magic_damage(int(item.get("damage", 0)))
			if kind == "ethereal" and target.is_alive():
				target.hero_timers["ghost"] = maxi(target.timer("ghost"), 3000)
		"heal":
			if _android_mode: source.heal(maxf(0, float(item.get("heal", 80))))
			else: source.hp = min(source.max_hp, source.hp + max(0, int(item.get("heal", 80))))
		"mana":
			source.mp = min(source.max_mp, source.mp + max(0, int(item.get("mana", 50))))
		"poison":
			target.add_dot(item_id, source.id, max(1, int(item.get("damage", 16))), int(item.get("duration_ms", 3000)), int(item.get("interval_ms", 1000)))
		"slow":
			if automatic and item_id == "shivas_guard" and (target.has_status("slow") or target.timer("invulnerable") > 0):
				return {"accepted": false, "reason": "target_not_ready"}
			var prior := float(target.hero_state.get("slow_amount", 30.0)) if target.has_status("slow") else 0.0
			target.hero_state["slow_amount"] = maxf(prior, float(item.get("slow_percent", 30.0)))
			target.add_status("slow", int(item.get("duration_ms", 2000)))
		"break":
			if target.has_status("debuff_immune"):
				return {"accepted": false, "reason": "target_immune"}
			if automatic and (target.has_status("break") or target.timer("invulnerable") > 0):
				return {"accepted": false, "reason": "target_not_ready"}
			target.add_status("break", int(item.get("duration_ms", 0)))
		"purge_positive":
			if target.has_status("debuff_immune"):
				return {"accepted": false, "reason": "target_immune"}
			if automatic and (target.timer("invulnerable") > 0 or not target.has_dispellable_positive()):
				return {"accepted": false, "reason": "target_not_ready"}
			target.purge_positive()
			target.add_status("positive_block", int(item.get("duration_ms", 0)))
		"reflect":
			target = source
			source.hero_state["reflect_ratio"] = float(item.get("reflect_ratio", 0.0))
			source.add_status("blade_mail", int(item.get("duration_ms", 0)))
		"control":
			var control_kind := str(item.get("control", ""))
			if control_kind not in ["silence", "stun", "hex", "banish", "disarm"]:
				return {"accepted": false, "reason": "unsupported_control"}
			if automatic:
				if target.has_status(control_kind) or (control_kind == "silence" and (target.has_status("stun") or target.has_status("hex"))):
					return {"accepted": false, "reason": "target_not_ready"}
				if control_kind == "disarm" and not target.action_block("attack").is_empty():
					return {"accepted": false, "reason": "target_not_ready"}
			var duration_key := "ranged_duration_ms" if target.hero_id in ["skywrath", "necrophos", "viper"] else "duration_ms"
			var duration := int(item.get(duration_key, item.get("duration_ms", 0)))
			if not target.apply_control(control_kind, duration, "gear:" + item_id,
				source.id, str(item.get("dispel_class", "basic"))):
				return {"accepted": false, "reason": "control_blocked"}
		"haste":
			source.add_status("haste", int(item.get("duration_ms", 2000)))
		"cleanse":
			if _android_mode: source.dispel(false)
			else: source.statuses.clear()
		"evasion":
			source.add_status("evasion", int(item.get("duration_ms", 2000)))
		_:
			return {"accepted": false, "reason": "unsupported_active"}
	source.cooldowns[cooldown_id] = max(0, int(item.get("cooldown_ms", 12000)))
	var event := _log.record_event({
		"tick_ms": _elapsed_ms,
		"source_id": source.id,
		"target_id": target.id,
		"kind": "active_used",
		"item_id": item_id,
		"amount": roundi(life_damage),
		"exact_amount": life_damage,
		"school_id": kind,
		"control": str(item.get("control", "")),
		"effect_id": "effect_" + kind,
		"remaining_hp": target.hp,
	})
	_check_defeat()
	return {"accepted": true, "event": event}

func _advance_dots(delta_ms: int, emitted: Array[Dictionary]) -> void:
	for target in [_left, _right]:
		for dot in target.advance_dots(delta_ms):
			var source := _fighter_for_id(str(dot["source_id"]))
			if source == null:
				continue
			var amount: int
			var tick_ms := _elapsed_ms if _android_mode else _elapsed_ms + delta_ms
			if _android_mode:
				var hit := _hero_runtime.external_damage(source, target, float(dot.damage), "magic", str(dot.effect_id), tick_ms, false)
				amount = roundi(float(hit.life_damage))
				_drain_hero_events(emitted)
			else: amount = target.receive_magic_damage(int(dot["damage"]))
			emitted.append(_log.record_event({
				"tick_ms": tick_ms,
				"source_id": source.id,
				"target_id": target.id,
				"kind": "dot_tick",
				"dot_effect_id": dot["effect_id"],
				"amount": amount,
				"school_id": "dot",
				"effect_id": "effect_dot_tick",
				"remaining_hp": target.hp,
			}))
			if _android_mode:
				_check_defeat()
				if finished(): return
			elif not target.is_alive():
				_result = {"kind": "defeat", "winner_id": source.id, "finished_at_ms": tick_ms}
				return

func _heal_from_lifesteal(source: FighterState, applied_damage: int) -> int:
	if source.lifesteal_percent <= 0 or applied_damage <= 0:
		return 0
	var healed := roundi(float(applied_damage) * float(source.lifesteal_percent) / 100.0)
	var before := source.hp
	source.hp = min(source.max_hp, source.hp + healed)
	return roundi(source.hp - before)
