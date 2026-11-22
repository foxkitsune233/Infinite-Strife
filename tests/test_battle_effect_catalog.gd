extends SceneTree

const EffectCatalog := preload("res://scripts/battle/BattleEffectCatalog.gd")

func _init() -> void:
	_test_critical_attack_maps_to_critical_effect()
	_test_skill_effect_id_has_priority_over_generic_mapping()
	_test_dot_and_active_events_have_stable_fallbacks()
	quit(0)

func _test_critical_attack_maps_to_critical_effect() -> void:
	_assert_equal(EffectCatalog.effect_for({"kind": "physical_attack", "critical": true}), "effect_critical_strike", "critical attacks need a distinct effect")

func _test_skill_effect_id_has_priority_over_generic_mapping() -> void:
	_assert_equal(EffectCatalog.effect_for({"kind": "skill_cast", "effect_id": "effect_ember_burst"}), "effect_ember_burst", "skill record must retain its authored effect")

func _test_dot_and_active_events_have_stable_fallbacks() -> void:
	_assert_equal(EffectCatalog.effect_for({"kind": "dot_tick"}), "effect_dot_tick", "DOT needs a stable fallback")
	_assert_equal(EffectCatalog.effect_for({"kind": "active_used", "school_id": "shield"}), "effect_shield", "shield active must map to shield effect")

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
