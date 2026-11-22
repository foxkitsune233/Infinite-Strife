extends SceneTree

const SkillRuntimeScript := preload("res://scripts/battle/SkillRuntime.gd")

func _init() -> void:
	_test_cinder_skill_has_complete_runtime_values()
	_test_unknown_skill_receives_safe_zero_damage_fallback()
	quit(0)

func _test_cinder_skill_has_complete_runtime_values() -> void:
	var skill: Dictionary = SkillRuntimeScript.for_hero_id("cinder")
	_assert_equal(skill["id"], "meteor_step", "cinder must resolve its original skill")
	_assert_equal(skill["mana_cost"], 70, "hero skill must preserve its mana cost")
	_assert_equal(skill["magic_damage"], 110, "hero skill must define deterministic combat damage")
	_assert_equal(skill["effect_id"], "effect_ember_burst", "hero skill must expose a presentation effect id")

func _test_unknown_skill_receives_safe_zero_damage_fallback() -> void:
	var skill: Dictionary = SkillRuntimeScript.for_hero_id("missing")
	_assert_equal(skill["id"], "basic_pulse", "unknown hero must have a safe fallback skill")
	_assert_equal(skill["magic_damage"], 0, "fallback must not invent damage")

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
