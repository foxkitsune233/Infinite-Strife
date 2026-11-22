class_name FighterState
extends RefCounted

var id: String
var hero_id: String = ""
var health_regen: float = 0.0
var mana_regen: float = 0.0
var max_hp: int
var hp: float
var attack_damage: int
var attack_interval_ms: int
var armor: float
var max_mp: int
var mp: float
var shield: float
var magic_shield: float = 0.0
var hero_timers: Dictionary = {}
var hero_state: Dictionary = {}
var timed_controls: Array[Dictionary] = []
var hero_poisons: Array[Dictionary] = []
var magic_resistance: float
var critical_chance_percent: int = 0
var critical_multiplier: float = 2.0
var lifesteal_percent: int = 0
var cooldowns: Dictionary = {}
var statuses: Dictionary = {}
var dots: Array[Dictionary] = []

const _DATA_FIELDS := ["id", "hero_id", "health_regen", "mana_regen", "max_hp", "hp", "attack_damage", "attack_interval_ms", "armor", "max_mp", "mp", "shield", "magic_shield", "magic_resistance", "critical_chance_percent", "critical_multiplier", "lifesteal_percent", "cooldowns", "statuses", "dots", "hero_timers", "hero_state", "timed_controls", "hero_poisons"]

func to_data() -> Dictionary:
	var result := {}
	for field in _DATA_FIELDS:
		var value: Variant = get(field)
		result[field] = value.duplicate(true) if value is Dictionary or value is Array else value
	return result

static func from_data(data: Dictionary) -> FighterState:
	for field in _DATA_FIELDS:
		if not data.has(field): return null
	if not data.id is String or not data.hero_id is String: return null
	for field in ["health_regen", "mana_regen", "max_hp", "hp", "attack_damage", "attack_interval_ms", "armor", "max_mp", "mp", "shield", "magic_shield", "magic_resistance", "critical_chance_percent", "critical_multiplier", "lifesteal_percent"]:
		if not (data[field] is int or data[field] is float) or not is_finite(float(data[field])): return null
	if float(data.max_hp) < 1 or float(data.attack_interval_ms) < 1 or float(data.max_mp) < 0: return null
	if float(data.hp) < 0 or float(data.hp) > float(data.max_hp) or float(data.mp) < 0 or float(data.mp) > float(data.max_mp): return null
	for field in ["cooldowns", "statuses", "hero_timers", "hero_state"]:
		if not data[field] is Dictionary: return null
	for field in ["dots", "timed_controls", "hero_poisons"]:
		if not data[field] is Array: return null
		for entry in data[field]:
			if not entry is Dictionary: return null
	var result := FighterState.new(str(data.id), int(data.max_hp), int(data.attack_damage), int(data.attack_interval_ms), float(data.armor), int(data.max_mp))
	for field in _DATA_FIELDS:
		if field in ["dots", "timed_controls", "hero_poisons"]:
			var entries: Array[Dictionary] = []
			for entry in data[field]: entries.append(entry.duplicate(true))
			result.set(field, entries)
		elif data[field] is Dictionary:
			result.set(field, data[field].duplicate(true))
		else:
			result.set(field, data[field])
	return result

func _init(
	p_id: String,
	p_max_hp: int,
	p_attack_damage: int,
	p_attack_interval_ms: int,
	p_armor: float,
	p_max_mp: int = 0,
	p_shield: int = 0,
	p_magic_resistance: float = 0.0
) -> void:
	id = p_id
	max_hp = max(1, p_max_hp)
	hp = max_hp
	attack_damage = max(0, p_attack_damage)
	attack_interval_ms = max(1, p_attack_interval_ms)
	armor = p_armor
	max_mp = max(0, p_max_mp)
	mp = max_mp
	shield = max(0, p_shield)
	magic_resistance = clampf(p_magic_resistance, 0.0, 0.95)

func receive_physical_damage(raw_damage: int) -> int:
	if has_status("banish"): return 0
	var value := effective_armor()
	var multiplier := 1.0 - 0.06 * value / (1.0 + 0.06 * absf(value))
	if not hero_id.is_empty():
		return roundi(float(receive_android_damage(raw_damage, "physical").life_damage))
	return _receive_damage(max(0, roundi(float(raw_damage) * multiplier)))

func receive_magic_damage(raw_damage: int) -> int:
	if has_status("banish"): return 0
	if not hero_id.is_empty():
		return roundi(float(receive_android_damage(raw_damage, "magic").life_damage))
	return _receive_damage(max(0, roundi(float(raw_damage) * (1.0 - magic_resistance))))

# Android damage keeps current HP/mana/shields as doubles; max HP/attack remain ints.
func receive_android_damage(raw_damage: float, damage_type: String, nonlethal: bool = false) -> Dictionary:
	var blocked := raw_damage <= 0.0 or hp <= 0.0 or timer("invulnerable") > 0 or has_status("banish")
	blocked = blocked or (damage_type == "physical" and timer("ghost") > 0)
	if blocked:
		return {"life_damage": 0.0, "shield_damage": 0.0, "blocked": true}
	var amount := raw_damage
	if damage_type == "physical":
		var value := effective_armor()
		amount *= 1.0 - 0.06 * value / (1.0 + 0.06 * absf(value))
	elif damage_type in ["magic", "hp_removal"]:
		amount *= 1.0 - magic_resistance
		if hero_id == "viper" and not has_status("break"):
			amount *= 0.85
		if timer("ghost") > 0:
			amount *= 1.2
	if timer("aghanim_viper") > 0:
		amount *= 0.7 # Godot initial balance: 30% reduction.
	if timer("aghanim_borrowed") > 0:
		hero_state["aghanim_borrowed_healing"] = float(hero_state.get("aghanim_borrowed_healing", 0.0)) + amount
		return {"life_damage": 0.0, "shield_damage": 0.0, "blocked": false}
	var absorbed := 0.0
	if damage_type == "physical":
		absorbed = _consume_shield_sources("PHYSICAL", amount)
		amount -= absorbed
	if damage_type in ["magic", "hp_removal"]:
		var magic_absorbed := minf(magic_shield, amount)
		_consume_shield_sources("MAGICAL", magic_absorbed)
		magic_shield -= magic_absorbed
		amount -= magic_absorbed
		absorbed += magic_absorbed
	if damage_type != "pure":
		var general_absorbed := minf(shield, amount)
		var tracked_absorbed := _consume_shield_sources("UNIVERSAL", general_absorbed)
		shield -= general_absorbed
		if hero_state.has("aphotic_remaining"):
			hero_state["aphotic_remaining"] = maxf(0.0, float(hero_state.aphotic_remaining) - (general_absorbed - tracked_absorbed))
		amount -= general_absorbed
		absorbed += general_absorbed
	if not nonlethal and amount >= hp and hero_state.get("aghanim_borrowed_armed", false):
		hero_state.aghanim_borrowed_armed = false
		hero_state.aghanim_borrowed_healing = 0.0
		hero_timers.aghanim_borrowed = 1000
		var loss := maxf(0.0, hp - 1.0)
		hp = 1.0
		return {"life_damage": loss, "shield_damage": absorbed, "blocked": false}
	var applied := minf(amount, maxf(0.0, hp - (1.0 if nonlethal else 0.0)))
	hp -= applied
	return {"life_damage": applied, "shield_damage": absorbed, "blocked": false}

func grant_shield(source_key: String, kind: String, amount: float, duration_ms: int = 0) -> void:
	if amount <= 0.0 or kind not in ["UNIVERSAL", "MAGICAL", "PHYSICAL"]:
		return
	if source_key == "hero:aphotic" and hero_state.has("aphotic_remaining"):
		shield = maxf(0.0, shield - float(hero_state.aphotic_remaining))
		hero_state.erase("aphotic_remaining")
	if not hero_state.has("shield_sources"):
		hero_state["shield_sources"] = {}
	var sources: Dictionary = hero_state.shield_sources
	if sources.has(source_key):
		var old: Dictionary = sources[source_key]
		_adjust_shield_total(str(old.kind), -float(old.amount))
	sources[source_key] = {"kind": kind, "amount": amount, "remaining_ms": duration_ms}
	_adjust_shield_total(kind, amount)

func _adjust_shield_total(kind: String, amount: float) -> void:
	if kind == "UNIVERSAL": shield = maxf(0.0, shield + amount)
	if kind == "MAGICAL": magic_shield = maxf(0.0, magic_shield + amount)

func total_shields() -> float:
	var total := shield + magic_shield
	for entry in hero_state.get("shield_sources", {}).values():
		if entry.kind == "PHYSICAL": total += float(entry.amount)
	return total

func has_dispellable_positive() -> bool:
	return total_shields() > 0.0 or timer("ghost") > 0 or timer("armor_buff") > 0 or timer("speed_buff") > 0

func purge_positive() -> void:
	shield = 0.0
	magic_shield = 0.0
	hero_state.erase("shield_sources")
	hero_state.erase("aphotic_remaining")
	for key in ["ghost", "armor_buff", "speed_buff"]:
		hero_timers.erase(key)
	hero_state.erase("armor_bonus")
	hero_state.erase("speed_bonus")
	statuses.erase("haste")

func _consume_shield_sources(kind: String, damage: float) -> float:
	var sources: Dictionary = hero_state.get("shield_sources", {})
	var keys := sources.keys().filter(func(key: Variant) -> bool: return sources[key].kind == kind)
	keys.sort_custom(func(a: Variant, b: Variant) -> bool:
		var first := int(sources[a].remaining_ms)
		var second := int(sources[b].remaining_ms)
		if first == second: return str(a) < str(b)
		if first == 0: return false
		return second == 0 or first < second
	)
	var remaining := damage
	for key in keys:
		if remaining <= 0.0: break
		var entry: Dictionary = sources[key]
		var absorbed := minf(remaining, float(entry.amount))
		entry.amount = float(entry.amount) - absorbed
		remaining -= absorbed
		if entry.amount <= 0.0: sources.erase(key)
	return damage - remaining

func timer(key: String) -> int:
	return int(hero_timers.get(key, 0))

func effective_armor() -> float:
	return armor + (float(hero_state.get("armor_bonus", 0.0)) if timer("armor_buff") > 0 else 0.0)

func heal(amount: float) -> float:
	if hp <= 0 or amount <= 0:
		return 0.0
	var before := hp
	hp = minf(max_hp, hp + amount * (1.5 if timer("ghost") > 0 else 1.0))
	return hp - before

func action_block(action: String) -> String:
	if not is_alive(): return "dead"
	if has_status("banish"): return "banished"
	if has_status("time_stop"): return "time_stopped"
	if has_status("stun") and not (action == "skill" and hero_id == "abaddon"): return "stunned"
	if has_status("hex"): return "hexed"
	if has_status("freeze"): return "frozen"
	if action == "skill" and has_status("silence"): return "silenced"
	if action == "item" and has_status("mute"): return "muted"
	if action == "attack" and (has_status("disarm") or timer("ghost") > 0): return "disarmed"
	return ""

func apply_control(kind: String, duration_ms: int, source_id: String, caster_id: String, dispel_class: String = "basic") -> bool:
	if not is_alive() or timer("invulnerable") > 0 or has_status("banish") or has_status("debuff_immune"):
		return false
	var duration := clampi(ceili(float(duration_ms) / 100.0) * 100, 100, 60000)
	for entry in timed_controls:
		if entry.kind == kind and entry.source_id == source_id and entry.caster_id == caster_id:
			entry.remaining_ms = maxi(int(entry.remaining_ms), duration)
			return true
	if timed_controls.size() >= 24: return false
	timed_controls.append({"kind": kind, "remaining_ms": duration, "source_id": source_id, "caster_id": caster_id, "dispel_class": dispel_class})
	return true

func dispel(strong: bool = false) -> void:
	timed_controls = timed_controls.filter(func(entry: Dictionary) -> bool: return entry.dispel_class == "undispellable" or (entry.dispel_class == "strong_only" and not strong))
	for key in ["silence", "mute", "disarm", "slow", "skin_slow", "taunt"]:
		statuses.erase(key)
	if strong:
		for key in ["stun", "hex"]: statuses.erase(key)
	hero_poisons = hero_poisons.filter(func(entry: Dictionary) -> bool: return entry.get("dispel_class", "basic") == "undispellable" or (entry.get("dispel_class", "basic") == "strong_only" and not strong))
	dots = dots.filter(func(entry: Dictionary) -> bool: return entry.get("dispel_class", "basic") == "undispellable")

func status_snapshot() -> Dictionary:
	var result := statuses.duplicate(true)
	for entry in timed_controls:
		result[entry.kind] = maxi(int(result.get(entry.kind, 0)), int(entry.remaining_ms))
	return result

func add_status(status_id: String, duration_ms: int) -> void:
	if duration_ms > 0:
		statuses[status_id] = max(duration_ms, int(statuses.get(status_id, 0)))

func add_dot(effect_id: String, source_id: String, damage: int, duration_ms: int, interval_ms: int) -> void:
	if effect_id.is_empty() or damage <= 0 or duration_ms <= 0 or interval_ms <= 0:
		return
	dots.append({
		"effect_id": effect_id,
		"source_id": source_id,
		"damage": damage,
		"remaining_ms": duration_ms,
		"until_tick_ms": interval_ms,
		"interval_ms": interval_ms,
	})

func has_status(status_id: String) -> bool:
	if int(statuses.get(status_id, 0)) > 0: return true
	for entry in timed_controls:
		if entry.kind == status_id and int(entry.remaining_ms) > 0: return true
	return false

func effective_attack_interval_ms() -> int:
	var multiplier := 1.0
	if not hero_id.is_empty():
		var speed := float(hero_state.get("base_speed", 100.0))
		var effective_speed := speed
		if timer("aghanim_legion") > 0: effective_speed += 50.0
		if timer("speed_buff") > 0: effective_speed += float(hero_state.get("speed_bonus", 0.0))
		if has_status("slow"): effective_speed -= float(hero_state.get("slow_amount", 30.0))
		if has_status("skin_slow"): effective_speed -= 15.0
		if hero_id == "juggernaut" and timer("invulnerable") > 0: effective_speed += speed * 2.0
		if hero_id == "abaddon" and timer("curse_speed") > 0 and not has_status("break"): effective_speed += 20.0
		multiplier *= speed / maxf(20.0, effective_speed)
	elif has_status("slow"):
		multiplier *= 1.30
	if has_status("haste"):
		multiplier *= 0.70
	var interval: int = max(1, roundi(float(attack_interval_ms) * multiplier))
	return mini(interval, 100) if timer("aghanim_omni") > 0 else interval

func advance_time(delta_ms: int) -> void:
	if delta_ms <= 0:
		return
	var sources: Dictionary = hero_state.get("shield_sources", {})
	for key in sources.keys():
		var entry: Dictionary = sources[key]
		if int(entry.remaining_ms) <= 0: continue
		entry.remaining_ms = int(entry.remaining_ms) - delta_ms
		if int(entry.remaining_ms) <= 0:
			_adjust_shield_total(str(entry.kind), -float(entry.amount))
			sources.erase(key)
	var stopped := has_status("time_stop")
	if is_alive():
		heal(health_regen * float(delta_ms) / 1000.0)
		mp = minf(max_mp, mp + mana_regen * float(delta_ms) / 1000.0 * (1.5 if timer("ghost") > 0 else 1.0))
	for key in hero_timers.keys():
		if stopped and key == "flare_next": continue
		hero_timers[key] = maxi(0, int(hero_timers[key]) - delta_ms)
	if timer("aghanim_borrowed") == 0 and hero_state.has("aghanim_borrowed_healing"):
		heal(float(hero_state.aghanim_borrowed_healing))
		hero_state.erase("aghanim_borrowed_healing")
	for skill_id in cooldowns.keys():
		if stopped: continue
		var remaining: int = max(0, int(cooldowns[skill_id]) - delta_ms)
		if remaining == 0:
			cooldowns.erase(skill_id)
		else:
			cooldowns[skill_id] = remaining
	for status_id in statuses.keys():
		var remaining: int = max(0, int(statuses[status_id]) - delta_ms)
		if remaining == 0:
			statuses.erase(status_id)
		else:
			statuses[status_id] = remaining
	for entry in timed_controls:
		entry.remaining_ms = maxi(0, int(entry.remaining_ms) - delta_ms)
	timed_controls = timed_controls.filter(func(entry: Dictionary) -> bool: return int(entry.remaining_ms) > 0)

func advance_dots(delta_ms: int) -> Array[Dictionary]:
	var ticks: Array[Dictionary] = []
	if delta_ms <= 0:
		return ticks
	var remaining_dots: Array[Dictionary] = []
	for dot in dots:
		var data: Dictionary = dot.duplicate(true)
		var remaining_step := delta_ms
		while remaining_step > 0 and int(data["remaining_ms"]) > 0:
			var until_tick: int = int(data["until_tick_ms"])
			var advance: int = min(remaining_step, until_tick, int(data["remaining_ms"]))
			data["remaining_ms"] = int(data["remaining_ms"]) - advance
			data["until_tick_ms"] = until_tick - advance
			remaining_step -= advance
			if int(data["until_tick_ms"]) == 0:
				ticks.append(data.duplicate(true))
				data["until_tick_ms"] = int(data["interval_ms"])
		if int(data["remaining_ms"]) > 0:
			remaining_dots.append(data)
	dots = remaining_dots
	return ticks

func _receive_damage(post_mitigation_damage: int) -> int:
	var absorbed := minf(shield, post_mitigation_damage)
	shield -= absorbed
	var applied := float(post_mitigation_damage) - absorbed
	hp = max(0, hp - applied)
	return roundi(applied)

func is_alive() -> bool:
	return hp > 0
