class_name BattlePresentation
extends RefCounted

const _DEFAULT_PROFILE := {
	"art_id": "hero_default_placeholder",
	"foot_anchor": Vector2(0.5, 1.0),
	"weapon_anchor": Vector2(0.74, 0.56),
	"effect_anchor": Vector2(0.5, 0.48),
}

const _PROFILES := {
	"cinder": {
		"art_id": "hero_cinder_idle_v1",
		"foot_anchor": Vector2(0.5, 1.0),
		"weapon_anchor": Vector2(0.78, 0.54),
		"effect_anchor": Vector2(0.52, 0.43),
	},
	"gale": {
		"art_id": "hero_gale_idle_v1",
		"foot_anchor": Vector2(0.5, 1.0),
		"weapon_anchor": Vector2(0.24, 0.52),
		"effect_anchor": Vector2(0.48, 0.42),
	},
}

static func profile_for(hero_id: String) -> Dictionary:
	return _PROFILES.get(hero_id, _DEFAULT_PROFILE).duplicate(true)
