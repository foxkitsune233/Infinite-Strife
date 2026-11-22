extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	var state := PreparationState.new("juggernaut")
	state.gold = 516
	state.hero_levels["juggernaut"] = 10
	state.quest_progress["tower_highest_floor"] = 2
	state.unlocked_heroes.erase("skywrath")
	session.preparation_state = state
	var screen := preload("res://scripts/ui/ProgressionScreen.gd").new()
	root.add_child(screen)
	await process_frame
	var buttons: Array = screen._rows.get_children().filter(func(node: Node) -> bool: return node is Button and node.text.contains("等级"))
	assert(buttons.size() == AndroidHeroCatalog.ids().size())
	assert(buttons[0].text.contains("等级10/15") and buttons[0].text.contains("516金币"))
	assert(not buttons[0].disabled)
	assert(buttons[1].disabled and buttons[1].text.contains("未解锁"))
	state.gold = 515
	screen._refresh()
	buttons = screen._rows.get_children().filter(func(node: Node) -> bool: return node is Button and node.text.contains("等级"))
	assert(buttons[0].disabled)
	state.gold = 10000
	state.hero_levels["juggernaut"] = 15
	screen._refresh()
	buttons = screen._rows.get_children().filter(func(node: Node) -> bool: return node is Button and node.text.contains("等级"))
	assert(buttons[0].disabled and buttons[0].text.contains("已达上限"))
	screen.queue_free()
	await process_frame
	print("Hero level UI: tower cap, exponential fee, affordability and locked hero passed")
	quit()
