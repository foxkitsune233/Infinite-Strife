class_name AndroidHeroRuntime
extends RefCounted

const Shards = preload("res://scripts/battle/HeroShardConfig.gd")
const Catalog = preload("res://scripts/content/AndroidHeroCatalog.gd")

var _seed: int
var _events: Array[Dictionary] = []
var _now_ms := 0

func _init(seed_value: int) -> void:
	_seed = clampi(seed_value, 1, 2147483646)

func to_data() -> Dictionary:
	return {"seed": str(_seed), "now_ms": _now_ms, "events": _events.duplicate(true)}

func restore_data(data: Dictionary) -> bool:
	if not data.has("seed") or not str(data.seed).is_valid_int() or not data.get("events", null) is Array: return false
	var value := int(str(data.seed))
	if value < 1 or value > 2147483646: return false
	for event in data.events:
		if not event is Dictionary: return false
	_seed = value
	_now_ms = int(data.get("now_ms", 0))
	_events.assign(data.events.duplicate(true))
	return true

static func supports(fighter: FighterState) -> bool:
	return fighter.hero_id in Catalog.ids()

static func ultimate_key(fighter: FighterState) -> String:
	return "hero:%s:ultimate" % fighter.hero_id

func initialize(fighter: FighterState) -> void:
	if supports(fighter) and not fighter.hero_state.has("base_speed"):
		fighter.hero_state["base_speed"] = Catalog.base_stats(fighter.hero_id).attack_speed

	if _shard(fighter) and not fighter.hero_state.get("shard_initialized", false):
		fighter.hero_state["shard_initialized"] = true
		if fighter.hero_id in ["faceless_void", "legion_commander"]:
			fighter.hero_timers["shard_period"] = int(Shards.get_value(fighter.hero_id, "period_ms"))

func _shard(f: FighterState) -> bool:
	return bool(f.hero_state.get("has_shard", false)) and not f.has_status("break")

# Shared active-skill hook for hero casts and Aghanim control. Never recurse.
func reflect_skill(source: FighterState, target: FighterState, effect: String) -> bool:
	if source == target or not source.is_alive() or not target.is_alive() or ":reflected" in effect: return false
	var first := target.hero_id == "anti_mage" and _shard(target) and not bool(target.hero_state.get("shard_reflect_used", false))
	if not first and target.timer("aghanim_reflect") <= 0: return false
	if first: target.hero_state["shard_reflect_used"] = true
	_event(target, source, "skill_reflected", 0, "control", effect + ":reflected")
	return true

func _hostile_skill(effect: String) -> bool:
	return not ":reflected" in effect and (effect.ends_with(":ult") or effect.ends_with(":ultimate") or effect.ends_with(":aghanim") or effect == "hero:skywrath:flare" or (not effect.begins_with("hero:") and not effect.begins_with("gear:") and not effect.begins_with("item:") and not effect.begins_with("dot:")))

func drain_events() -> Array[Dictionary]:
	var result := _events
	_events = []
	return result

func _roll() -> float:
	_seed = (_seed * 48271) % 2147483647
	return float(_seed) / 2147483647.0

func left_first_this_tick() -> bool:
	return _roll() < 0.5

func set_time(now_ms: int) -> void:
	_now_ms = now_ms

func _config(fighter: FighterState) -> Dictionary:
	return Catalog.hero(fighter.hero_id).get("skill_config", {})

func _event(source: FighterState, target: FighterState, kind: String, amount: float, damage_type: String, effect: String, critical: bool = false) -> void:
	_events.append({"tick_ms": _now_ms, "source_id": source.id, "target_id": target.id,
		"kind": kind, "amount": roundi(amount), "exact_amount": amount,
		"school_id": damage_type, "damage_type": damage_type, "effect_id": effect,
		"critical": critical, "healed": 0, "remaining_hp": target.hp})

func control(source: FighterState, target: FighterState, kind: String, duration_ms: int, source_id: String, dispel_class: String = "basic") -> bool:
	var applied := target.apply_control(kind, duration_ms, source_id, source.id, dispel_class)
	_event(source, target, "control_applied" if applied else "control_blocked", 0, kind, source_id)
	return applied

func damage(source: FighterState, target: FighterState, raw: float, damage_type: String, effect: String, basic: bool = false, critical: bool = false, ordinary: bool = true, nonlethal: bool = false, record_hit: bool = true, reflectable: bool = true) -> Dictionary:
	if reflectable and not basic and ordinary and raw > 0 and _hostile_skill(effect) and reflect_skill(source, target, effect):
		damage(target, source, raw, damage_type, effect + ":reflected", false, critical, ordinary, nonlethal, true)
		return {"life_damage": 0.0, "shield_damage": 0.0, "blocked": true}
	if target.hero_id == "anti_mage" and _shard(target) and damage_type in ["magic", "hp_removal"]:
		raw *= 1.0 - Shards.get_value("anti_mage", "magic_resist")
	if target.has_status("shard_hunger"): raw *= 1.0 + Shards.get_value("axe", "vulnerability")
	var old_armor := target.armor
	if target.has_status("shard_poison_armor"): target.armor -= Shards.get_value("viper", "armor_loss")
	var hit := target.receive_android_damage(raw, damage_type, nonlethal)
	target.armor = old_armor
	if record_hit: _event(source, target, "physical_attack" if basic else "hero_damage", hit.life_damage, damage_type, effect, critical)
	var loss := float(hit.life_damage) + float(hit.shield_damage)
	if loss > 0.0 and source != target and not nonlethal and target.has_status("blade_mail"):
		var reflected := loss * float(target.hero_state.get("reflect_ratio", 0.0))
		# Apply the returned hit directly so it cannot trigger reflection or on-hit passives.
		var reflected_hit := source.receive_android_damage(reflected, "pure")
		_event(target, source, "hero_damage", reflected_hit.life_damage, "pure", "gear:blade_mail:reflect")
	if ordinary and damage_type == "magic" and float(hit.life_damage) > 0 and source.hero_id == "skywrath" and not source.has_status("break"):
		source.magic_shield = minf(source.max_hp, source.magic_shield + float(hit.life_damage) * 0.3)
	if ordinary and float(hit.life_damage) > 0 and target.hero_id == "viper" and not target.has_status("break") and target.timer("skin_ready") == 0:
		target.hero_timers["skin_ready"] = 500
		_add_poison(target, source, "normal", 6.0, "hero:viper:skin")
		if not source.has_status("debuff_immune"):
			source.add_status("skin_slow", 3000)
	if basic and float(hit.life_damage) + float(hit.shield_damage) > 0 and target.hero_id == "axe" and not target.has_status("break"):
		var hits := int(target.hero_state.get("axe_hits", 0)) + 1
		var cfg := _config(target)
		target.hero_state["axe_hits"] = hits
		if hits >= int(cfg.spin_every_hits):
			target.hero_state["axe_hits"] = 0
			# Android marks helix SELF_DAMAGE: no responses/lifesteal, nonlethal.
			damage(target, source, float(cfg.spin_base_damage) + target.effective_armor() * float(cfg.spin_armor_coefficient), "pure", "hero:axe:helix", false, false, false, true)
	if loss > 0.0: try_aeon_disk(target)
	return hit

func try_aeon_disk(fighter: FighterState, item: Dictionary = {}, record := true) -> bool:
	var effect: Dictionary = item if not item.is_empty() else fighter.hero_state.get("aeon_disk", {})
	if effect.is_empty() or not fighter.action_block("item").is_empty(): return false
	if fighter.hp / fighter.max_hp >= float(effect.threshold) or int(fighter.cooldowns.get("active:aeon_disk", 0)) > 0:
		return false
	fighter.dispel(true)
	fighter.grant_shield("gear:aeon_disk", "UNIVERSAL", float(effect.shield), int(effect.duration_ms))
	fighter.cooldowns["active:aeon_disk"] = int(effect.cooldown_ms)
	if record: _event(fighter, fighter, "active_used", 0, "shield", "gear:aeon_disk")
	return true

# Existing generic skill/DOT commands still pass through Android damage responses
# in a mixed battle, while retaining their original single event in BattleEngine.
# is_skill=false is required for arbitrary-named DOTs; item:/gear:/dot: IDs are also excluded.
func external_damage(source: FighterState, target: FighterState, raw: float, damage_type: String, effect: String, now_ms: int, is_skill: bool = true) -> Dictionary:
	_now_ms = now_ms
	return damage(source, target, raw, damage_type, effect, false, false, true, false, false, is_skill)

func attack(source: FighterState, target: FighterState, now_ms: int, extra: bool = false) -> void:
	_now_ms = now_ms
	if not source.is_alive() or not target.is_alive() or not source.action_block("attack").is_empty(): return
	if target.timer("invulnerable") > 0 or target.timer("ghost") > 0 or target.has_status("banish") or target.has_status("attack_protection"):
		_event(source, target, "attack_blocked", 0, "physical", "effect_physical_strike")
		return
	var mkb: Dictionary = source.hero_state.get("monkey_king_bar", {})
	var evasion := float(target.hero_state.get("evasion_chance", 0.0))
	if target.hero_id == "phantom_assassin" and _shard(target):
		evasion = 1.0 - (1.0 - evasion) * (1.0 - Shards.get_value("phantom_assassin", "evasion"))
	if mkb.is_empty() and evasion > 0.0 and _roll() >= 1.0 - evasion:
		_event(source, target, "attack_evaded", 0, "physical", "effect_physical_strike")
		return
	var candidates: Array[Dictionary] = []
	if source.critical_chance_percent > 0:
		candidates.append({"multiplier": source.critical_multiplier, "chance": float(source.critical_chance_percent) / 100.0, "prd": false})
	if source.hero_id == "juggernaut" and not source.has_status("break"):
		# CriticalPrd.calibrate(0.25), using the same rising-failure algorithm.
		var threshold := minf(1.0, 0.08474409185231699 * (int(source.hero_state.get("blade_failures", 0)) + 1))
		candidates.append({"multiplier": 1.8, "chance": threshold, "prd": true})
	if source.timer("critical_ready") > 0 and source.timer("aghanim_crit_lock") == 0:
		candidates.append({"multiplier": 3.0, "chance": 1.0, "prd": false})
		source.hero_timers["critical_ready"] = 0
	var gear_failures: Dictionary = source.hero_state.get("gear_crit_failures", {})
	for gear in source.hero_state.get("bloodthorn_crit", []):
		var key := str(gear.key)
		# Android's calibrated starting chance for a 20% long-run PRD.
		var chance := minf(1.0, 0.05570404294978186 * (int(gear_failures.get(key, 0)) + 1))
		candidates.append({"multiplier": gear.multiplier, "chance": chance, "prd": false, "gear_key": key})
	if bool(source.hero_state.get("aghanim_force_crit", false)): candidates.append({"multiplier": 3.0, "chance": 1.0, "prd": false})
	if source.timer("aghanim_crit_lock") > 0: candidates.clear()
	candidates.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return float(a.multiplier) > float(b.multiplier))
	var multiplier := 1.0
	for candidate in candidates:
		var success := float(candidate.chance) >= 1.0 or _roll() < float(candidate.chance)
		if candidate.prd:
			source.hero_state["blade_failures"] = 0 if success else int(source.hero_state.get("blade_failures", 0)) + 1
		if candidate.has("gear_key"):
			var key := str(candidate.gear_key)
			gear_failures[key] = 0 if success else int(gear_failures.get(key, 0)) + 1
			source.hero_state["gear_crit_failures"] = gear_failures
		if success:
			multiplier = float(candidate.multiplier) + (0.3 if source.hero_id == "phantom_assassin" and not source.has_status("break") else 0.0)
			break
	var hit := damage(source, target, float(source.attack_damage) * multiplier, "physical", "effect_critical_strike" if multiplier > 1.0 else "effect_physical_strike", true, multiplier > 1.0)
	if bool(hit.blocked): return
	if float(hit.life_damage) + float(hit.shield_damage) > 0:
		if not mkb.is_empty() and source.is_alive() and target.is_alive() and _roll() < float(mkb.chance):
			damage(source, target, float(mkb.damage), "magic", "gear:monkey_king_bar:proc")
		if source.hero_id == "anti_mage" and not source.has_status("break") and source.timer("burn_ready") == 0 and target.mp > 0:
			# Actual DuelEngine uses current mana, not maximum mana.
			var cfg := _config(source)
			var burn := target.mp * float(cfg.mana_burn_percent)
			target.mp -= burn
			source.hero_state["mana_burned"] = float(source.hero_state.get("mana_burned", 0.0)) + burn
			source.hero_timers["burn_ready"] = roundi(float(cfg.mana_burn_interval_sec) * 1000.0)
			_event(source, target, "mana_burn", burn, "mana", "hero:anti_mage:burn")
		if source.hero_id == "faceless_void" and not source.has_status("break"):
			var cfg := _config(source)
			if _roll() < float(cfg.time_lock_chance):
				if control(source, target, "stun", roundi(float(cfg.time_lock_stun_sec) * 1000.0), "hero:faceless_void:timelock"):
					var key := str(target.hero_state.get("ultimate_key", ultimate_key(target)))
					if int(target.cooldowns.get(key, 0)) > 0:
						target.cooldowns[key] = int(target.cooldowns[key]) + roundi(float(cfg.time_lock_cd_add_sec) * 1000.0)
						_event(source, target, "cooldown_extended", float(cfg.time_lock_cd_add_sec) * 1000.0, "control", "hero:faceless_void:timelock")
		if source.hero_id == "legion_commander" and not source.has_status("break") and not extra:
			var cfg := _config(source)
			if _roll() < float(cfg.dual_strike_chance):
				source.heal(float(cfg.dual_strike_heal))
				attack(source, target, now_ms, true)
	if _shard(source):
		if source.hero_id == "juggernaut" and multiplier > 1.0:
			source.heal(float(hit.life_damage) * Shards.get_value("juggernaut", "critical_lifesteal"))
		if source.hero_id == "abaddon" and target.is_alive():
			damage(source, target, Shards.get_value("abaddon", "frost_damage"), "magic", "hero:abaddon:shard_frost")
			if _roll() < Shards.get_value("abaddon", "freeze_chance"):
				control(source, target, "freeze", int(Shards.get_value("abaddon", "freeze_ms")), "hero:abaddon:shard_frost", "strong_only")
		if source.hero_id == "axe" and target.is_alive():
			control(source, target, "shard_hunger", int(Shards.get_value("axe", "duration_ms")), "hero:axe:shard_hunger")
	source.heal((float(hit.life_damage) + float(hit.shield_damage) * 0.5) * float(source.lifesteal_percent) / 100.0)
	if source.hero_id == "abaddon" and not source.has_status("break") and target.is_alive():
		target.hero_poisons = target.hero_poisons.filter(func(entry: Dictionary) -> bool: return entry.kind != "curse")
		_add_poison(source, target, "curse", 12.0, "curse")
		source.hero_timers["curse_speed"] = 2000
	_try_abyssal_bash(source, target)

func _try_abyssal_bash(source: FighterState, target: FighterState) -> void:
	var bash: Dictionary = source.hero_state.get("abyssal_bash", {})
	if bash.is_empty() or not source.is_alive() or not target.is_alive(): return
	if int(source.cooldowns.get("proc:basher", 0)) > 0 or target.has_status("debuff_immune"): return
	var ranged := source.hero_id in ["skywrath", "necrophos", "viper"]
	if _roll() >= float(bash.ranged_chance if ranged else bash.chance): return
	var duration := int(bash.ranged_duration_ms if target.hero_id in ["skywrath", "necrophos", "viper"] else bash.duration_ms)
	if control(source, target, "stun", duration, "gear:abyssal.stun", "strong_only"):
		source.cooldowns["proc:basher"] = int(bash.cooldown_ms)

func cast(source: FighterState, target: FighterState, skill: Dictionary, now_ms: int) -> Dictionary:
	_now_ms = now_ms
	var block := source.action_block("skill")
	if not block.is_empty(): return {"accepted": false, "reason": block}
	var spec := Catalog.hero(source.hero_id)
	var key := ultimate_key(source)
	if int(source.cooldowns.get(key, 0)) > 0: return {"accepted": false, "reason": "cooldown"}
	var free_flare := source.hero_id == "skywrath" and bool(source.hero_state.get("has_shard", false))
	if not free_flare and source.mp < float(spec.mana_cost): return {"accepted": false, "reason": "insufficient_mana"}
	if not free_flare: source.mp -= float(spec.mana_cost)
	source.cooldowns[key] = roundi(maxf(10.0 if source.hero_id == "necrophos" else 0.5, float(spec.cooldown_sec)) * 1000.0)
	source.hero_state["ultimate_key"] = key
	var cfg: Dictionary = spec.skill_config
	# Redirect hostile control and damage together after paying the original caster once.
	if source.hero_id in ["anti_mage", "axe", "faceless_void", "legion_commander", "viper", "abaddon"] and reflect_skill(source, target, "hero:%s:ult" % source.hero_id):
		target = source
	var hp_before := target.hp
	match source.hero_id:
		"juggernaut":
			source.dispel(true)
			source.hero_timers["invulnerable"] = roundi(float(cfg.duration_sec) * 1000.0)
		"skywrath":
			source.hero_state["flare_left"] = int(cfg.hits)
			source.hero_state["flare_clock_ms"] = 0
			if free_flare:
				source.hero_state["shard_flare_active"] = true
				source.hero_state["shard_flare_elapsed"] = 0
				source.hero_state["shard_flare_drain_clock"] = 0
				source.hero_state["shard_flare_cost"] = Shards.get_value("skywrath", "drain_base")
		"phantom_assassin": source.hero_timers["critical_ready"] = 4000
		"abaddon":
			source.dispel(true)
			# Replace this hero's shield source rather than stacking it.
			if not source.has_status("positive_block"):
				source.grant_shield("hero:aphotic", "UNIVERSAL", float(cfg.shield))
			damage(source, target, source.total_shields() * 0.3, "magic", "hero:abaddon:ult")
		"necrophos":
			if not source.has_status("positive_block"):
				source.hero_timers["ghost"] = 3000
		"viper":
			_add_poison(source, target, "virulent", 3.0, "hero:viper:ultimate")
			if target.timer("invulnerable") == 0 and not target.has_status("debuff_immune"):
				target.add_status("break", 3000)
				var prior := float(target.hero_state.get("slow_amount", 30.0)) if target.has_status("slow") else 0.0
				target.hero_state["slow_amount"] = maxf(prior, 30.0)
				target.add_status("slow", 3000)
		"anti_mage":
			damage(source, target, float(cfg.ult_mana_void_base) + float(source.hero_state.get("mana_burned", 0.0)) * float(cfg.ult_mana_void_coefficient), "magic", "hero:anti_mage:ult")
			control(source, target, "stun", roundi(float(cfg.ult_mana_void_stun_sec) * 1000.0), "hero:anti_mage:ult")
		"axe":
			if control(source, target, "taunt", roundi(float(cfg.taunt_duration_sec) * 1000.0), "hero:axe:ult"):
				source.hero_state["armor_bonus"] = cfg.self_armor
				source.hero_timers["armor_buff"] = roundi(float(cfg.taunt_duration_sec) * 1000.0)
		"faceless_void": control(source, target, "time_stop", roundi(float(cfg.time_stop_duration_sec) * 1000.0), "hero:faceless_void:ult", "undispellable")
		"legion_commander":
			if control(source, target, "taunt", roundi(float(cfg.duel_taunt_sec) * 1000.0), "hero:legion_commander:ult"):
				source.hero_state["speed_bonus"] = cfg.duel_attack_speed_pct
				source.hero_timers["speed_buff"] = roundi(float(cfg.duel_taunt_sec) * 1000.0)
	var event := {"tick_ms": now_ms, "source_id": source.id, "target_id": target.id, "kind": "skill_cast",
		"skill_id": str(skill.get("id", spec.skill_id)), "runtime_mode": "android_hero", "amount": roundi(hp_before - target.hp),
		"exact_amount": hp_before - target.hp,
		"school_id": "hero", "effect_id": str(skill.get("effect_id", "effect_arcane_cast")), "remaining_hp": target.hp}
	return {"accepted": true, "event": event}

func advance(left: FighterState, right: FighterState, delta_ms: int, now_ms: int, stopped_left: bool, stopped_right: bool) -> void:
	_now_ms = now_ms
	if delta_ms <= 0: return
	_advance_poison(left, right, delta_ms)
	_advance_poison(right, left, delta_ms)
	for source in [left, right]:
		var target: FighterState = right if source == left else left
		if not source.is_alive() or not target.is_alive(): continue
		if (stopped_left if source == left else stopped_right): continue
		initialize(source)
		try_aeon_disk(source)
		_advance_shard(source, target, delta_ms)
		if source.hero_id == "necrophos" and not source.has_status("break"):
			var heart := damage(source, target, target.max_hp * 0.008 * float(delta_ms) / 1000.0, "hp_removal", "hero:necrophos:heartstopper", false, false, false)
			if _shard(source): source.heal(float(heart.life_damage) * Shards.get_value("necrophos", "heartstopper_heal"))
		if bool(source.hero_state.get("shard_flare_active", false)) or int(source.hero_state.get("flare_left", 0)) > 0:
			source.hero_state["flare_clock_ms"] = int(source.hero_state.get("flare_clock_ms", 0)) - delta_ms
			if int(source.hero_state.flare_clock_ms) <= 0:
				if not bool(source.hero_state.get("shard_flare_active", false)):
					source.hero_state["flare_left"] = int(source.hero_state.flare_left) - 1
				source.hero_state["flare_clock_ms"] = int(source.hero_state.flare_clock_ms) + 250
				damage(source, target, float(_config(source).damage_per_hit), "magic", "hero:skywrath:flare")

func _add_poison(source: FighterState, target: FighterState, kind: String, amount: float, source_id: String) -> void:
	if not target.is_alive() or target.timer("invulnerable") > 0 or target.has_status("debuff_immune"): return
	if source.hero_id == "viper" and _shard(source):
		control(source, target, "shard_poison_armor", int(Shards.get_value("viper", "armor_ms")), "hero:viper:shard_armor")
	target.hero_poisons.append({"kind": kind, "amount": amount, "next_ms": 1000, "ticks_left": 3,
		"remaining_ms": 2000, "source_id": source_id, "caster_id": source.id,
		"dispel_class": "strong_only" if kind == "virulent" else "basic"})

func _advance_poison(target: FighterState, opponent: FighterState, delta_ms: int) -> void:
	var survivors: Array[Dictionary] = []
	var spawned: Array[Dictionary] = []
	for entry in target.hero_poisons:
		if entry.kind == "curse":
			var span := mini(delta_ms, int(entry.remaining_ms))
			damage(opponent, target, float(entry.amount) * float(span) / 1000.0, "magic", "hero:abaddon:curse")
			entry.remaining_ms = int(entry.remaining_ms) - delta_ms
			if int(entry.remaining_ms) > 0: survivors.append(entry)
			continue
		entry.next_ms = int(entry.next_ms) - delta_ms
		if int(entry.next_ms) <= 0:
			entry.next_ms = int(entry.next_ms) + 1000
			if entry.kind == "virulent":
				if not target.has_status("debuff_immune"):
					var normal := entry.duplicate(true)
					normal.kind = "normal"
					normal.dispel_class = "basic"
					normal.next_ms = 1000
					normal.ticks_left = 3
					spawned.append(normal)
			else:
				damage(opponent, target, float(entry.amount), "magic", str(entry.source_id))
				entry.ticks_left = int(entry.ticks_left) - 1
		if entry.kind == "virulent" or int(entry.ticks_left) > 0: survivors.append(entry)
	survivors.append_array(spawned)
	target.hero_poisons = survivors

# Exponential absolute cost, rather than a fraction of remaining mana, reaches zero.
# A 20s hard ceiling also prevents regeneration from sustaining the channel forever.
func _advance_shard(source: FighterState, target: FighterState, delta_ms: int) -> void:
	if bool(source.hero_state.get("shard_flare_active", false)):
		source.hero_state["shard_flare_elapsed"] = int(source.hero_state.get("shard_flare_elapsed", 0)) + delta_ms
		source.hero_state["shard_flare_drain_clock"] = int(source.hero_state.get("shard_flare_drain_clock", 0)) + delta_ms
		while int(source.hero_state.shard_flare_drain_clock) >= 1000 and source.mp > 0:
			source.hero_state.shard_flare_drain_clock -= 1000
			source.mp = maxf(0, source.mp - float(source.hero_state.shard_flare_cost))
			source.hero_state.shard_flare_cost = float(source.hero_state.shard_flare_cost) * Shards.get_value("skywrath", "drain_growth")
		if source.mp <= 0 or int(source.hero_state.shard_flare_elapsed) >= int(Shards.get_value("skywrath", "max_channel_ms")):
			source.hero_state["shard_flare_active"] = false
			source.hero_state["flare_left"] = 0
	if not _shard(source): return
	if source.hero_id in ["faceless_void", "legion_commander"] and source.timer("shard_period") == 0:
		source.hero_timers["shard_period"] = int(Shards.get_value(source.hero_id, "period_ms"))
		if source.hero_id == "legion_commander":
			source.dispel(true)
		elif control(source, target, "shard_dilation", int(Shards.get_value("faceless_void", "duration_ms")), "hero:faceless_void:shard_dilation"):
			for key in target.cooldowns.keys():
				if int(target.cooldowns[key]) > 0: target.cooldowns[key] = int(target.cooldowns[key]) + int(Shards.get_value("faceless_void", "cooldown_add_ms"))
	if source.hero_id == "faceless_void" and target.has_status("shard_dilation"):
		damage(source, target, Shards.get_value("faceless_void", "damage_per_sec") * delta_ms / 1000.0, "magic", "hero:faceless_void:shard_dilation", false, false, false)
	if source.hero_id == "axe" and target.has_status("shard_hunger"):
		damage(source, target, Shards.get_value("axe", "damage_per_sec") * delta_ms / 1000.0, "magic", "hero:axe:shard_hunger", false, false, false)
