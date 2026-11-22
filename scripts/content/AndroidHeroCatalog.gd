class_name AndroidHeroCatalog
extends RefCounted

# Source of truth: Android shared FirstRunContent.heroes (JourneyModels.kt).
# id, name, STR, AGI, INT, baseAttack, BAT seconds, baseArmor, manaRegen,
# healthRegen, ultimate manaCost, ultimate cooldown seconds.
const _SPECS := [
	["juggernaut", "主宰", 24, 26, 16, 35, 1.5, 1, 5.0, 0.5, 80, 16.0],
	["skywrath", "天怒法师", 18, 18, 30, 30, 1.7, 0, 9.0, 0.5, 90, 8.0],
	["phantom_assassin", "幻影刺客", 22, 28, 15, 40, 1.7, 1, 5.0, 0.5, 70, 10.0],
	["abaddon", "亚巴顿", 28, 18, 22, 22, 1.7, 2, 7.0, 1.0, 140, 9.0],
	["necrophos", "瘟疫法师", 24, 16, 27, 30, 1.7, 1, 7.0, 1.0, 85, 18.0],
	["viper", "冥界亚龙", 24, 24, 20, 34, 1.7, 1, 6.0, 0.5, 75, 9.0],
	["anti_mage", "敌法师", 20, 30, 18, 36, 1.45, 1, 5.0, 0.5, 75, 12.0],
	["axe", "斧王", 32, 20, 16, 30, 1.70, 3, 4.0, 1.5, 90, 14.0],
	["faceless_void", "虚空假面", 26, 28, 18, 34, 1.60, 2, 5.0, 0.75, 85, 16.0],
	["legion_commander", "军团指挥官", 30, 24, 18, 33, 1.55, 2, 4.5, 1.0, 80, 15.0],
]

# Skill IDs below are Godot adapter IDs, not Android persisted skill identifiers.
# HeroContent constants are metadata only; BattleEngine does not interpret them.
const _ABILITIES := {
	"juggernaut": {"id": "omnislash", "name": "无敌斩", "config": {"duration_sec": 2.0, "attack_speed_percent": 200.0}, "gap": "Invulnerability, strong self-dispel, boosted full attacks and on-hit triggers are not implemented."},
	"skywrath": {"id": "mystic_flare", "name": "神秘之耀", "config": {"hits": 8, "damage_per_hit": 60.0}, "gap": "Eight timed magic hits at 0.25-second intervals and hero passives are not implemented."},
	"phantom_assassin": {"id": "coup_de_grace", "name": "恩赐解脱", "config": {}, "gap": "The four-second critical-attack window and hero passives are not implemented."},
	"abaddon": {"id": "aphotic_shield", "name": "无光之盾", "config": {"shield": 180.0}, "gap": "Strong self-dispel, shield ledger, reflection and curse passive are not implemented."},
	"necrophos": {"id": "ghost_shroud", "name": "幽魂护罩", "config": {}, "gap": "Ghost shroud physical immunity, half magic damage and hero passives are not implemented."},
	"viper": {"id": "viper_strike", "name": "蝮蛇突袭", "config": {}, "gap": "Android poison-core stacking, break, slow and hero passives are not implemented."},
	"anti_mage": {"id": "mana_void", "name": "法力虚空", "config": {"mana_burn_percent": 0.02, "mana_burn_interval_sec": 1.0, "ult_mana_void_base": 100.0, "ult_mana_void_coefficient": 0.6, "ult_mana_void_stun_sec": 0.2}, "gap": "Mana burn tracking, cumulative-burn damage and source-aware stun are not implemented."},
	"axe": {"id": "berserkers_call", "name": "狂战士之吼", "config": {"spin_every_hits": 4, "spin_base_damage": 55.0, "spin_armor_coefficient": 1.2, "taunt_duration_sec": 2.5, "self_armor": 12.0}, "gap": "Taunt, temporary armor and every-four-hits pure-damage counter helix are not implemented."},
	"faceless_void": {"id": "chronosphere", "name": "时间结界", "config": {"time_lock_chance": 0.18, "time_lock_stun_sec": 0.35, "time_lock_cd_add_sec": 1.0, "time_stop_duration_sec": 3.0}, "gap": "Undispellable time stop, time-lock procs and enemy cooldown extension are not implemented."},
	"legion_commander": {"id": "duel", "name": "决斗", "config": {"dual_strike_chance": 0.25, "dual_strike_heal": 22.0, "duel_taunt_sec": 4.0, "duel_attack_speed_pct": 60.0}, "gap": "Forced mutual attacks, duel speed bonus and nonrecursive extra attacks with healing are not implemented."},
}

static func ids() -> Array[String]:
	var result: Array[String] = []
	for spec in _SPECS:
		result.append(str(spec[0]))
	return result

static func heroes() -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	for id in ids():
		result.append(hero(id))
	return result

static func hero(id: String) -> Dictionary:
	for spec in _SPECS:
		if spec[0] != id:
			continue
		var ability: Dictionary = _ABILITIES[id]
		return {
			"id": id, "display_name": spec[1],
			"strength": spec[2], "agility": spec[3], "intelligence": spec[4],
			"base_attack": spec[5], "bat": spec[6], "base_armor": spec[7],
			"base_mana_regen": spec[8], "base_health_regen": spec[9],
			"mana_cost": spec[10], "cooldown_sec": spec[11],
			"skill_id": ability.id, "skill_name": ability.name,
			"skill_config": ability.config.duplicate(true),
			"source": "android_shared", "ability_parity": false, "ability_gap": ability.gap,
		}
	return {}

# Naked level-one JourneyRules.fighter; retain doubles until engine projection.
# Levels belong to HeroLevelRules and are applied by BattleLoadoutBuilder.
static func base_stats(id: String) -> Dictionary:
	var spec := hero(id)
	if spec.is_empty():
		return {}
	var strength := float(spec.strength)
	var agility := float(spec.agility)
	var intelligence := float(spec.intelligence)
	var main := agility
	if id == "abaddon":
		main = (strength + agility + intelligence) * 0.45
	elif id in ["skywrath", "necrophos"]:
		main = intelligence
	var mana := 75.0 + 12.0 * intelligence
	var speed := 100.0 + agility
	return {
		"hp": 500.0 + 22.0 * strength,
		"mana": mana, "initial_mana": minf(mana, 30.0),
		"attack": float(spec.base_attack) + main,
		"attack_speed": speed, "bat": spec.bat,
		"attack_interval_ms": float(spec.bat) * 100.0 / maxf(20.0, speed) * 1000.0,
		"armor": float(spec.base_armor) + agility / 6.0,
		"magic_resistance": 0.25 + intelligence * 0.001,
		"health_regen": float(spec.base_health_regen) + strength * 0.1,
		"mana_regen": float(spec.base_mana_regen) + intelligence * 0.05,
	}
