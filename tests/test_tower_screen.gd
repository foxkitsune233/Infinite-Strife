extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_tower_screen.json"
	SaveRepository.erase(session.save_path)
	var state := PreparationState.new("juggernaut")
	session.preparation_state = state
	var claimed := []
	var nodes := {}
	for quest in preload("res://scripts/progression/AndroidQuestCatalog.gd").all():
		if not nodes.has(quest.node):
			nodes[quest.node] = true
			claimed.append(quest.id)
	state.quest_progress["claimed_quests"] = claimed
	var consumable := preload("res://scripts/content/AndroidContent.gd").create_instance("tower-faerie", "faerie_fire")
	state.add_item(consumable)
	assert(LoadoutRules.place(state, consumable.id, ItemInstance.Kind.EQUIPMENT, 0).accepted)
	ScreenRouter.open(self, "tower")
	await scene_changed
	current_scene._action("enter_tower")
	await process_frame
	assert(get_nodes_in_group("story_player")[0].chapter.id == "T00")
	get_nodes_in_group("story_player")[0]._move(true)
	await process_frame
	current_scene._fight()
	await scene_changed
	current_scene.set_process(false)
	assert(current_scene._tower_battle)
	assert(current_scene._engine.time_limit_ms == 2000000)
	assert(current_scene.right_actor.texture != null)
	current_scene._engine._left.hp = 100
	session.save_path = "user://missing-tower-consumable-directory/save.json"
	assert(not current_scene._use_consumable(consumable.id).accepted)
	assert(state.item(consumable.id) != null and current_scene._engine._left.hp == 100)
	session.save_path = "user://test_tower_screen.json"
	assert(current_scene._use_consumable(consumable.id).accepted)
	assert(state.item(consumable.id) == null)
	current_scene._engine.step(100)
	current_scene._checkpoint()
	var elapsed: int = current_scene._engine.snapshot().elapsed_ms
	ScreenRouter.open(self, "tower")
	await scene_changed
	assert(session.restore_progress().accepted)
	ScreenRouter.open(self, "battle")
	await scene_changed
	current_scene.set_process(false)
	assert(current_scene._engine.snapshot().elapsed_ms == elapsed)
	current_scene._engine._right.hp = 1
	current_scene._engine._left.attack_damage = 100000
	current_scene._engine.step(5000)
	assert(current_scene._engine.finished())
	current_scene._on_result_button_pressed()
	await scene_changed
	state = session.preparation_state
	assert(current_scene.name == "Tower" and state.quest_progress.tower_run.floor == 2)
	state.quest_progress.tower_run.floor = 20
	state.quest_progress.tower_run.cleared_floors = 19
	state.quest_progress.tower_run.current_hp = null
	current_scene._fight()
	await scene_changed
	current_scene.set_process(false)
	current_scene._engine._right.hp = 1
	current_scene._engine._left.attack_damage = 100000
	assert(current_scene.use_player_active({"id": "tower-test-dagon", "kind": "magic_damage", "damage": 100000, "cooldown_ms": 1000}).accepted)
	assert(current_scene._engine.finished())
	assert(current_scene.result_button.visible)
	current_scene._on_result_button_pressed()
	await scene_changed
	await process_frame
	assert(state.quest_progress.tower_rapier)
	assert(get_nodes_in_group("story_player")[0].chapter.id == "T20")
	get_nodes_in_group("story_player")[0]._move(true)
	await process_frame
	await process_frame
	assert(get_nodes_in_group("story_player")[0].chapter.id == "E01")
	assert(not get_nodes_in_group("story_player")[0]._stage.visible)
	get_nodes_in_group("story_player")[0]._move(true)
	await process_frame
	SaveRepository.erase(session.save_path)
	print("Tower UI, battle resume, result return and earned ending playback passed")
	quit()
