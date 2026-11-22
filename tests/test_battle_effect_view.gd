extends SceneTree

const EffectView := preload("res://scripts/ui/BattleEffectView.gd")

func _init() -> void:
	_test_effect_style_is_stable_for_combat_events()
	_test_battle_scene_has_effect_and_status_layers()
	quit(0)

func _test_effect_style_is_stable_for_combat_events() -> void:
	var critical: Dictionary = EffectView.style_for({"kind": "physical_attack", "critical": true, "amount": 200})
	_assert_equal(critical["text"], "暴击 200", "critical damage must have a clear player-facing label")
	_assert_equal(critical["duration_ms"], 520, "critical effect duration must be deterministic")
	var shield: Dictionary = EffectView.style_for({"kind": "active_used", "school_id": "shield"})
	_assert_equal(shield["text"], "护盾", "shield active must receive a distinct effect label")
	_assert_equal(EffectView.style_for({"kind": "skill_cast", "runtime_mode": "hero_aghanim", "effect_id": "hero:anti_mage:aghanim"}).text, "反射窗口", "Aghanim buff never displays misleading zero damage")
	_assert_equal(EffectView.style_for({"kind": "skill_reflected"}).text, "技能反射", "reflection receives visible feedback")
	var view := EffectView.new()
	view.set_stage_anchors(Vector2(120, 240), Vector2(680, 240))
	_assert_equal(view.event_position({"target_id": "right"}), Vector2(680, 240), "effect must use the right fighter anchor")
	_assert_equal(view.event_position({"target_id": "left"}), Vector2(120, 240), "effect must use the left fighter anchor")
	view.set_stage_anchors(Vector2(120, 240), Vector2(680, 240), "ruin_enemy")
	_assert_equal(view.event_position({"target_id": "ruin_enemy"}), Vector2(680, 240), "journey enemy IDs retain right-side effects")
	view.free()

func _test_battle_scene_has_effect_and_status_layers() -> void:
	var scene := preload("res://scenes/Battle.tscn").instantiate()
	_assert_equal(scene.get_node_or_null("%EffectsLayer") != null, true, "battle scene must host transient combat effects")
	_assert_equal(scene.get_node_or_null("%LeftStatusIcons") != null, true, "battle scene must host left status chips")
	_assert_equal(scene.get_node_or_null("%RightStatusIcons") != null, true, "battle scene must host right status chips")
	scene.queue_free()

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
