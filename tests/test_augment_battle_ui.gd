extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_augment_battle_ui.json"
	SaveRepository.erase(session.save_path)
	var state := PreparationState.new("axe")
	state.unlocked_heroes.append("axe")
	state.quest_progress["tower_shard"] = ["axe"]
	state.quest_progress["tower_aghs"] = ["axe"]
	session.preparation_state = state
	assert(session.enter_journey().accepted)
	assert(session.begin_journey_battle().accepted)
	var battle := preload("res://scenes/Battle.tscn").instantiate()
	root.add_child(battle)
	battle.set_process(false)
	await process_frame
	await process_frame
	assert(battle._aghanim_button.visible and not battle._aghanim_button.disabled)
	assert(battle._augment_badges[0].text.contains("魔晶"))
	battle._engine._left.mp = 0
	battle._engine._left.add_status("silence", 1000)
	assert(not battle.use_player_aghanim().accepted)
	assert(not battle._engine._left.hero_state.get("aghanim_used", false))
	battle._engine._left.dispel(true)
	session.save_path = "user://missing-augment-ui-directory/save.json"
	var hp: float = battle._engine._right.hp
	assert(not battle.use_player_aghanim().accepted)
	assert(battle._engine._right.hp == hp and not battle._engine._left.hero_state.get("aghanim_used", false))
	session.save_path = "user://test_augment_battle_ui.json"
	assert(battle.use_player_aghanim().accepted)
	assert(battle._engine._left.mp == 0)
	assert(battle._engine._right.hp == hp - 400)
	assert(battle._aghanim_button.disabled and battle._aghanim_button.text.contains("已使用"))
	assert(not battle.use_player_aghanim().accepted)
	assert(session.restore_progress().accepted)
	var restored := BattleEngine.from_state(session.journey_state.battle_snapshot.engine)
	assert(restored._left.hero_state.aghanim_used)
	assert(not restored.command({"kind": "cast_aghanim", "source_id": "left"}).accepted)
	battle.queue_free()
	await process_frame
	SaveRepository.erase(session.save_path)
	print("Augment HUD, control gate, zero mana, exactly once, rollback and resume passed")
	quit()
