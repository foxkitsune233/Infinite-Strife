extends SceneTree

const BattlePresentationScript := preload("res://scripts/battle/BattlePresentation.gd")

func _init() -> void:
	_test_hero_profiles_have_stable_presentation_anchors()
	_test_battle_scene_exposes_resource_state_labels()
	quit(0)

func _test_hero_profiles_have_stable_presentation_anchors() -> void:
	var cinder: Dictionary = BattlePresentationScript.profile_for("cinder")
	_assert_equal(cinder["art_id"], "hero_cinder_idle_v1", "cinder must resolve to its registered project art")
	_assert_equal(cinder["foot_anchor"], Vector2(0.5, 1.0), "foot anchor must remain normalized")
	_assert_equal(cinder["weapon_anchor"], Vector2(0.78, 0.54), "weapon anchor must remain stable")

func _test_battle_scene_exposes_resource_state_labels() -> void:
	var scene := preload("res://scenes/Battle.tscn").instantiate()
	_assert_equal(scene.get_node_or_null("%LeftMpLabel") != null, true, "battle HUD must display mana")
	_assert_equal(scene.get_node_or_null("%RightStatusLabel") != null, true, "battle HUD must display statuses")
	scene.queue_free()

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
