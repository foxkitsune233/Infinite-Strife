extends SceneTree

const Relief = preload("res://scripts/journey/ReliefRules.gd")
const Save = preload("res://scripts/save/SaveRepository.gd")
const PATH := "user://test_relief_screen.json"

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = PATH
	Save.erase(PATH)
	var state := PreparationState.new("juggernaut")
	state.gold = 0
	session.preparation_state = state
	var screen := preload("res://scenes/Expedition.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	await process_frame
	var claim := screen.target_content.get_node("ClaimRelief") as Button
	assert(not claim.disabled)
	assert(claim.get_theme_stylebox("normal") != null)
	claim.pressed.emit()
	assert(state.items.size() == 3)
	assert(int(state.quest_progress.relief.vouchers) == 1)
	var target: Dictionary = Relief.current_offers(state).values()[0]
	screen._show_target_detail(target)
	var buttons: Array[Node] = screen.target_content.get_children().filter(func(node: Node) -> bool: return node is Button)
	var depart := buttons.back() as Button
	assert(depart.text == "确认出战配置" and not depart.disabled)
	screen._confirm_departure(target)
	var dialogs := screen.get_children().filter(func(node: Node) -> bool: return node is ConfirmationDialog)
	var confirm := dialogs.back() as ConfirmationDialog
	assert(confirm.dialog_text.contains("入场金币  0") and confirm.dialog_text.contains("入场补助券一张"))
	confirm.confirmed.emit()
	assert(state.phase == PreparationState.Phase.JOURNEY)
	assert(not screen.choosing_target)
	assert(int(state.quest_progress.relief.vouchers) == 0)
	Save.erase(PATH)
	screen.queue_free()
	await process_frame
	print("Relief UI claim, subsidized confirmation and departure passed")
	quit()
