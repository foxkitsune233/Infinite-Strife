extends Control

const CorePageLayout = preload("res://scripts/ui/CorePageLayout.gd")

@onready var status_label: Label = %StatusLabel
@onready var continue_button: Button = %Continue

func _ready() -> void:
	var session := _session()
	if session.get("preparation_state") == null:
		var restored: Dictionary = session.call("restore_progress")
		if not restored.accepted and not FileAccess.file_exists(str(session.get("save_path"))) and not FileAccess.file_exists(str(session.get("save_path")) + ".bak"):
			# Only a genuinely new save gets the automatic prologue.
			var queued: Dictionary = session.call("queue_story", "P00")
			if not queued.accepted:
				# Keep the unsaved prologue in memory; its player retries saves before advancing.
				preload("res://scripts/story/StoryRules.gd").enqueue(session.call("ensure_preparation_state"), "P00")
	_build_camp()
	if not str(session.get("save_block_reason")).is_empty():
		status_label.text = str(session.get("save_block_reason"))
	continue_button.visible = _can_continue()
	preload("res://scripts/story/StoryPlayer.gd").show_pending.call_deferred(self, _session())

func _can_continue() -> bool:
	var session := _session()
	var state: PreparationState = session.get("preparation_state")
	var journey: JourneyState = session.get("journey_state")
	if state != null and journey != null and state.phase != PreparationState.Phase.HOME and not journey.finished():
		return true
	var saved := SaveRepository.read(str(session.get("save_path")))
	return bool(saved.accepted) and saved.preparation.phase != PreparationState.Phase.HOME and not saved.journey.finished()

func _build_camp() -> void:
	var state: PreparationState = _session().call("ensure_preparation_state")
	CorePageLayout.install(self, "home", "远征营地", "Infinite Strife · 单人远征")
	$CampBackdrop.texture = CorePageLayout.RUINS
	var panel := $Center/Panel as PanelContainer
	panel.custom_minimum_size = Vector2.ZERO
	panel.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	var content := $Center/Panel/Content as VBoxContainer
	content.add_theme_constant_override("separation", 12)
	$Center/Panel/Content/Title.hide()
	$Center/Panel/Content/Subtitle.hide()
	status_label.text = ""
	var in_journey := state.phase != PreparationState.Phase.HOME
	var banner := CorePageLayout.banner(state.active_hero_id, "继续远征" if in_journey else "远征寻宝",
		"返回当前挑战" if in_journey else "五场挑战\n通关带回目标宝箱")
	content.add_child(banner)
	content.move_child(banner, 2)
	var stats := HBoxContainer.new()
	stats.name = "CampStats"
	for entry in [[state.gold, "营地金币"], [int(state.quest_progress.get("clears", 0)), "成功通关"], [state.unlocked_heroes.size(), "已解锁英雄"]]:
		var tile := PanelContainer.new()
		tile.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		tile.add_theme_stylebox_override("panel", CorePageLayout.frame(Color("09394c"), Color("30a7c7")))
		var label := Label.new()
		label.text = "%d\n%s" % entry
		label.add_theme_font_size_override("font_size", 21)
		label.add_theme_color_override("font_color", Color("ffe297"))
		tile.add_child(label)
		stats.add_child(tile)
	content.add_child(stats)
	content.move_child(stats, 3)
	var expedition := $Center/Panel/Content/Expedition as Button
	expedition.text = "继续远征" if in_journey else "挑选目标宝箱"
	content.move_child(expedition, 4)
	$Center/Panel/Content/BeginSkirmish.hide()
	var shortcuts := HBoxContainer.new()
	content.add_child(shortcuts)
	content.move_child(shortcuts, 5)
	for action in [$Center/Panel/Content/Preparation, $Center/Panel/Content/Progression]:
		action.reparent(shortcuts)
		action.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for node in content.get_children():
		if node is Button:
			var button := node as Button
			button.custom_minimum_size.y = 62
			CorePageLayout.paint_button(button, button == expedition)
	for button in shortcuts.get_children():
		CorePageLayout.paint_button(button)
	var departure := PanelContainer.new()
	departure.add_theme_stylebox_override("panel", CorePageLayout.frame(Color("0c667e"), Color("ffca3f")))
	content.add_child(departure)
	content.move_child(departure, expedition.get_index())
	expedition.reparent(departure)
	var tower := Button.new()
	tower.name = "TowerEntry"
	tower.text = "继续转生之塔" if state.quest_progress.has("tower_run") else "转生之塔"
	tower.custom_minimum_size.y = 62
	CorePageLayout.paint_button(tower)
	tower.pressed.connect(func() -> void: ScreenRouter.open(get_tree(), "tower"))
	content.add_child(tower)
	var settings := Button.new()
	settings.name = "SettingsEntry"
	settings.text = "设置 · 存档 · 图鉴"
	settings.custom_minimum_size.y = 62
	CorePageLayout.paint_button(settings)
	settings.pressed.connect(func() -> void: ScreenRouter.open(get_tree(), "settings"))
	content.add_child(settings)

func _on_begin_skirmish_pressed() -> void:
	start_default_match()

func _on_preparation_pressed() -> void:
	ScreenRouter.open(get_tree(), "preparation")

func _on_expedition_pressed() -> void:
	ScreenRouter.open(get_tree(), "expedition")

func _on_progression_pressed() -> void:
	ScreenRouter.open(get_tree(), "progression")

func _on_continue_pressed() -> void:
	var session := _session()
	var state: PreparationState = session.get("preparation_state")
	var journey: JourneyState = session.get("journey_state")
	if state == null or journey == null or state.phase == PreparationState.Phase.HOME or journey.finished():
		var result: Dictionary = session.call("restore_progress")
		if not bool(result.get("accepted", false)):
			status_label.text = str(result.get("reason", "无法恢复存档"))
			continue_button.hide()
			return
		state = session.get("preparation_state")
		journey = session.get("journey_state")
	if state.phase != PreparationState.Phase.HOME and not journey.finished():
		ScreenRouter.open(get_tree(), "battle" if bool(session.get("journey_battle_pending")) else "expedition")
		return
	continue_button.hide()
	status_label.text = "上次远征已经结束，请选择新的目标宝箱。"

func _session() -> Node:
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if session == null:
		session = preload("res://scripts/AppSession.gd").new()
		session.name = "AppSession"
		get_tree().root.add_child(session)
	return session

func start_default_match() -> void:
	ScreenRouter.open(get_tree(), "battle")
