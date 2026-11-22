extends SceneTree
const Scene = preload("res://scenes/Expedition.tscn")
var checks := 0
var failures := 0
func check(ok: bool, message: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(message)
func contents(screen: Control) -> Array:
	var result := []
	for card in screen.target_content.get_child(3).get_child(0).get_children():
		var callback: Callable = card.pressed.get_connections()[0].callable
		result.append(callback.get_bound_arguments()[0].duplicate(true))
	return result
func _init() -> void:
	call_deferred("run")
func run() -> void:
	var session := root.get_node("AppSession")
	var state := PreparationState.new("juggernaut")
	state.backpack_size = Vector2i(4, 2)
	state.quest_progress["clears"] = 1
	state.quest_progress["extracted"] = true
	state.quest_progress["chest_seed"] = 789
	state.quest_progress["material_pity"] = {"safe": {"misses": 6, "pending": true}, "backpack": {"misses": 4, "pending": true}}
	var claimed: Array = []
	var nodes := {}
	for quest in QuestRules.Catalog.all():
		if int(quest.node) in range(1, 7) and not nodes.has(quest.node):
			nodes[quest.node] = true
			claimed.append(quest.id)
	state.quest_progress.claimed_quests = claimed
	check(QuestRules.max_difficulty(state.quest_progress) == 1, "normal difficulty fixture")
	state.progression_serial = 37
	var original := state.quest_progress.duplicate(true)
	session.preparation_state = state
	session.journey_state = JourneyState.new(7001)
	session.save_path = "user://missing_batch_atomic_directory/save.json"
	var screen := Scene.instantiate()
	root.add_child(screen)
	await process_frame
	var expected := contents(screen)
	check(state.quest_progress == original and state.progression_serial == 37, "failure restores full progress, seed, pity, serial")
	check(expected.size() == 4, "four offers with one clear")
	check(expected[2].theme == "safe" and expected[2].difficulty == 1, "safe prioritized and raised from simple to normal")
	check(expected[2].guaranteed_categories == ["safe"] and "safe_lock" in expected[2].rewards, "safe guarantee packed")
	for card in screen.target_content.get_child(3).get_child(0).get_children():
		check(card.disabled, "unsaved card disabled")
	check(screen.target_content.get_node("ClaimRelief").disabled, "relief disabled on failed batch")
	for attempt in 2:
		screen.target_content.get_node("RetryChestBatch").pressed.emit()
		check(contents(screen) == expected, "failed retry contents deterministic")
		check(state.quest_progress == original and state.progression_serial == 37, "failed retry restores progress")
		await process_frame
	var good_path := "user://test_chest_batch_atomic.json"
	SaveRepository.erase(good_path)
	session.save_path = good_path
	screen.target_content.get_node("RetryChestBatch").pressed.emit()
	check(contents(screen) == expected, "successful retry keeps deterministic contents")
	check(not state.quest_progress.material_pity.safe.pending and not state.quest_progress.material_pity.backpack.pending, "successful retry consumes guarantees")
	check(screen.target_content.get_node_or_null("RetryChestBatch") == null, "successful retry removes retry action")
	check(not screen.target_content.get_child(3).get_child(0).get_child(0).disabled, "saved cards enabled")
	var saved := SaveRepository.read(good_path)
	check(saved.accepted and not saved.preparation.quest_progress.material_pity.safe.pending, "disk stores content and consumption together")
	var committed := state.quest_progress.duplicate(true)
	screen._show_target_list()
	check(state.quest_progress == committed, "rerender existing offers does not regenerate")
	SaveRepository.erase(good_path)
	state.quest_progress = {"quest_counters": {}, "claimed_quests": []}
	screen._show_target_list()
	var fresh := contents(screen)
	check(fresh.size() == 4, "no progress retains four cards")
	check([fresh[0].theme, fresh[1].theme, fresh[2].theme, fresh[3].theme] == ["supplies", "arsenal", "arcane", "backpack"], "no progress preserves theme order and carousel index three")
	SaveRepository.erase(good_path)
	screen.queue_free()
	await process_frame
	print("Chest batch atomic: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
