extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_core_navigation.json"
	session.preparation_state = PreparationState.new("juggernaut")
	session.journey_state = JourneyState.new(7001)
	for route in ["preparation", "expedition", "home", "preparation", "home"]:
		print("OPEN ", route)
		assert(ScreenRouter.open(self, route) == OK)
		await scene_changed
		await process_frame
		print("READY ", route)
		assert(current_scene != null)
		assert(current_scene.has_node("PageNavigation"))
	print("NAV BUTTON")
	(current_scene.get_node("PageNavigation").get_child(1) as Button).pressed.emit()
	await scene_changed
	(current_scene.get_node("PageNavigation").get_child(2) as Button).pressed.emit()
	await scene_changed
	await process_frame
	assert(current_scene.name == "MainMenu")
	assert(not current_scene.continue_button.visible)
	assert(session.enter_journey().accepted)
	(current_scene.get_node("PageNavigation").get_child(0) as Button).pressed.emit()
	await scene_changed
	await process_frame
	assert(not current_scene.target_scroll.visible)
	(current_scene.get_node("PageNavigation").get_child(2) as Button).pressed.emit()
	await scene_changed
	await process_frame
	assert(current_scene.continue_button.visible)
	current_scene.continue_button.pressed.emit()
	await scene_changed
	await process_frame
	assert(current_scene.name == "Expedition")
	assert(session.begin_journey_battle().accepted)
	assert(not session.complete_journey_battle(false).accepted)
	assert(ScreenRouter.open(self, "home") == OK)
	await scene_changed
	await process_frame
	assert(not current_scene.continue_button.visible)
	(current_scene.get_node("PageNavigation").get_child(0) as Button).pressed.emit()
	await scene_changed
	await process_frame
	assert(current_scene.target_scroll.visible)
	SaveRepository.erase(session.save_path)
	quit()
