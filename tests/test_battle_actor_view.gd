extends SceneTree

const ActorView := preload("res://scripts/ui/BattleActorView.gd")

func _init() -> void:
	_test_event_motion_classifies_attack_cast_and_hit()
	_test_battle_scene_has_two_actor_views()
	quit(0)

func _test_event_motion_classifies_attack_cast_and_hit() -> void:
	_assert_equal(ActorView.motion_for({"kind": "physical_attack", "source_id": "cinder", "target_id": "gale"}, "cinder"), "attack", "attack source needs attack motion")
	_assert_equal(ActorView.motion_for({"kind": "skill_cast", "source_id": "cinder", "target_id": "gale"}, "cinder"), "cast", "skill source needs cast motion")
	_assert_equal(ActorView.motion_for({"kind": "dot_tick", "source_id": "cinder", "target_id": "gale", "amount": 15}, "gale"), "hit", "damaged target needs hit motion")

func _test_battle_scene_has_two_actor_views() -> void:
	var scene := preload("res://scenes/Battle.tscn").instantiate()
	_assert_equal(scene.get_node_or_null("%LeftActor") != null, true, "battle scene must host left actor view")
	_assert_equal(scene.get_node_or_null("%RightActor") != null, true, "battle scene must host right actor view")
	scene.queue_free()

func _assert_equal(actual: Variant, expected: Variant, message: String) -> void:
	if actual != expected:
		push_error(message + "; expected=%s actual=%s" % [str(expected), str(actual)])
		quit(1)
