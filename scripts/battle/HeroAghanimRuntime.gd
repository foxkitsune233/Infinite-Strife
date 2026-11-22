class_name HeroAghanimRuntime
extends RefCounted

# Godot initial balance choices where user design leaves numbers unspecified.
# These are NOT claimed as concrete Android source values.
const OMNI_INTERVAL_MS := 100
const VIPER_DAMAGE_MULTIPLIER := 0.7
const VIPER_MANA_MULTIPLIER := 1.5
const NEC_PULSE_ATTACK_MULTIPLIER := 2.0
const LEGION_SPEED_BONUS := 50.0
const HEROES := ["juggernaut", "skywrath", "abaddon", "phantom_assassin", "viper", "necrophos", "anti_mage", "faceless_void", "axe", "legion_commander"]

static func cast(runtime: RefCounted, source: FighterState, target: FighterState, now_ms: int) -> Dictionary:
	if not source.is_alive() or not target.is_alive(): return {"accepted": false, "reason": "dead"}
	if not source.hero_state.get("has_aghanim", false): return {"accepted": false, "reason": "not_owned"}
	if source.hero_state.get("aghanim_used", false): return {"accepted": false, "reason": "already_used"}
	if source.hero_id not in HEROES: return {"accepted": false, "reason": "unsupported_hero"}
	var blocked := source.action_block("skill")
	# Normal Abaddon ultimate permits stun; Aghanim explicitly does not.
	if source.has_status("stun"): blocked = "stunned"
	if not blocked.is_empty(): return {"accepted": false, "reason": blocked}
	runtime.set_time(now_ms)
	source.hero_state.aghanim_used = true
	var before := target.hp
	var effect := "hero:%s:aghanim" % source.hero_id
	match source.hero_id:
		"juggernaut":
			source.hero_timers.invulnerable = maxi(source.timer("invulnerable"), 1000)
			source.hero_timers.aghanim_omni = 1000
			runtime.attack(source, target, now_ms)
		"skywrath":
			if runtime.reflect_skill(source, target, effect):
				runtime.control(target, source, "silence", 2000, effect + ":reflected")
			else:
				runtime.control(source, target, "silence", 2000, effect)
		"abaddon": source.hero_state.aghanim_borrowed_armed = true
		"phantom_assassin":
			var had_ready := source.hero_timers.has("critical_ready")
			var ready := source.timer("critical_ready")
			for index in range(5):
				if not target.is_alive(): break
				source.hero_timers.critical_ready = 1
				runtime.attack(source, target, now_ms, true)
			if had_ready: source.hero_timers.critical_ready = ready
			else: source.hero_timers.erase("critical_ready")
			source.hero_timers.aghanim_crit_lock = 5000
		"viper":
			source.hero_timers.aghanim_viper = 5000
			source.mp = minf(source.max_mp, source.mp * VIPER_MANA_MULTIPLIER)
		"necrophos":
			var hit: Dictionary = runtime.damage(source, target, source.attack_damage * NEC_PULSE_ATTACK_MULTIPLIER, "magic", effect)
			source.heal(float(hit.life_damage))
		"anti_mage": source.hero_timers.aghanim_reflect = 500
		"faceless_void":
			var history: Array = source.hero_state.get("aghanim_hp_history", [])
			if not history.is_empty():
				var saved: Dictionary = history[0]
				for sample in history:
					if int(sample.at_ms) <= now_ms - 2000: saved = sample
				source.hp = clampf(float(saved.hp), 1.0, float(source.max_hp))
			source.dispel(true)
		"axe": runtime.damage(source, target, 400.0, "pure", effect)
		"legion_commander":
			runtime.damage(source, target, 150.0, "physical", effect)
			source.hero_timers.aghanim_legion = 3000
	var amount := before - target.hp
	return {"accepted": true, "event": {"tick_ms": now_ms, "source_id": source.id, "target_id": target.id,
		"kind": "skill_cast", "skill_id": "aghanim", "runtime_mode": "hero_aghanim", "effect_id": effect,
		"school_id": "hero", "damage_type": "hero", "amount": roundi(amount), "exact_amount": amount, "remaining_hp": target.hp}}

# Engine calls after its regular tick, even while time-stopped. FighterState owns
# timer expiry, so this hook never decrements timers a second time.
static func advance(_runtime: RefCounted, source: FighterState, _target: FighterState, _delta_ms: int, now_ms: int, _stopped: bool) -> void:
	if source.hero_id != "faceless_void" or not source.hero_state.get("has_aghanim", false): return
	var history: Array = source.hero_state.get("aghanim_hp_history", [])
	if not history.is_empty() and int(history[-1].at_ms) == now_ms:
		history[-1].hp = source.hp
	else:
		history.append({"at_ms": now_ms, "hp": source.hp})
	while history.size() > 1 and int(history[1].at_ms) <= now_ms - 2000:
		history.pop_front()
	source.hero_state.aghanim_hp_history = history

static func description(hero: String) -> String:
	return {
		"juggernaut": "神杖：1秒无敌斩，立即攻击并将攻击间隔限制为100毫秒。",
		"skywrath": "神杖：沉默敌人2秒。",
		"abaddon": "神杖：抵挡下一次致死伤害，保留1点生命；随后1秒承伤累计为治疗，结束时结算。",
		"phantom_assassin": "神杖：立即五次必定暴击攻击，随后5秒普攻无法暴击。",
		"viper": "神杖：5秒减伤30%，当前魔力立即变为1.5倍（不超过上限）。",
		"necrophos": "神杖：造成攻击力200%的魔法脉冲伤害，实际生命伤害等量治疗自身。",
		"anti_mage": "神杖：0.5秒内反弹敌方技能。",
		"faceless_void": "神杖：回到2秒前的生命值并强驱散；不足2秒取最早记录，无记录保持当前生命。",
		"axe": "神杖：造成400点纯粹伤害。",
		"legion_commander": "神杖：造成150点物理伤害，获得50攻速，持续3秒。"
	}.get(hero, "")
