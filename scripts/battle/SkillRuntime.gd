class_name SkillRuntime
extends RefCounted

const _SKILLS := {
	"cinder": {
		"id": "meteor_step",
		"mana_cost": 70,
		"cooldown_ms": 9000,
		"magic_damage": 110,
		"stun_ms": 250,
		"effect_id": "effect_ember_burst",
	},
	"gale": {
		"id": "sky_lance",
		"mana_cost": 85,
		"cooldown_ms": 10000,
		"magic_damage": 125,
		"stun_ms": 0,
		"effect_id": "effect_gale_lance",
	},
}

const _FALLBACK := {
	"id": "basic_pulse",
	"mana_cost": 0,
	"cooldown_ms": 0,
	"magic_damage": 0,
	"stun_ms": 0,
	"effect_id": "",
}

static func for_hero_id(hero_id: String) -> Dictionary:
	return _SKILLS.get(hero_id, _FALLBACK).duplicate(true)
