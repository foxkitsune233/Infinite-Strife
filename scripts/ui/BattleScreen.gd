class_name BattleScreen
extends Control

const FIXED_STEP_MS := 100
const BattleLoadoutBuilder = preload("res://scripts/battle/BattleLoadoutBuilder.gd")
const PresentationController = preload("res://scripts/ui/BattlePresentationController.gd")
const AppSettings = preload("res://scripts/app/AppSettings.gd")
const Layout = preload("res://scripts/ui/CorePageLayout.gd")

@onready var left_hp_bar: ProgressBar = %LeftHpBar
@onready var right_hp_bar: ProgressBar = %RightHpBar
@onready var left_hp_label: Label = %LeftHpLabel
@onready var right_hp_label: Label = %RightHpLabel
@onready var left_mp_label: Label = %LeftMpLabel
@onready var right_mp_label: Label = %RightMpLabel
@onready var left_status_label: Label = %LeftStatusLabel
@onready var right_status_label: Label = %RightStatusLabel
@onready var left_status_icons: HBoxContainer = %LeftStatusIcons
@onready var right_status_icons: HBoxContainer = %RightStatusIcons
@onready var effects_layer: BattleEffectView = %EffectsLayer
@onready var left_actor: BattleActorView = %LeftActor
@onready var right_actor: BattleActorView = %RightActor
@onready var timer_label: Label = %TimerLabel
@onready var result_label: Label = %ResultLabel
@onready var result_button: Button = %ResultButton
@onready var left_name: Label = %LeftName
@onready var right_name: Label = %RightName
@onready var manual_skill_button: Button = %ManualSkillButton
@onready var manual_items: HBoxContainer = %ManualItems
@onready var battle_audio: Node = %BattleAudio

var _engine: BattleEngine
var _elapsed_fraction_ms := 0.0
var _recent_events: Array[String] = []
var _left_skill: Dictionary = {}
var _right_skill: Dictionary = {}
var _left_active_items: Array = []
var _item_buttons: Array[Button] = []
var _equipment_slots := {}
var _left_mana_bar: ProgressBar
var _right_mana_bar: ProgressBar
var _enemy_skill_label: Label
var _log_dialog: AcceptDialog
var _log_button: Button
var _right_active_items: Array = []
var _journey_battle := false
var _tower_battle := false
var _aghanim_button: Button
var _augment_badges: Array[Label] = []
var _compact_actions: HBoxContainer
var _checkpoint_elapsed := 0
var _presentation := PresentationController.new(AppSettings.get_flag("animations"))

func _ready() -> void:
	_prepare_hud()
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if session != null and bool(session.call("is_tower_battle_pending")):
		_tower_battle = true
		var state: PreparationState = session.call("ensure_preparation_state")
		var run: Dictionary = state.quest_progress.tower_run
		var combat := preload("res://scripts/tower/TowerCombat.gd")
		var builds := combat.build(state, run)
		start_match_fighters(int(run.rng), builds.left, builds.right)
		combat.configure(_engine, bool(run.get("supply_active", false)))
		if not run.get("battle_snapshot", {}).is_empty():
			_restore_battle_snapshot(run.battle_snapshot)
	elif session != null and bool(session.get("journey_battle_pending")):
		_journey_battle = true
		var journey: Variant = session.call("ensure_journey_state")
		var player_build: Dictionary = BattleLoadoutBuilder.build(session.call("ensure_preparation_state") as PreparationState)
		var enemy_build := BattleLoadoutBuilder.enemy_for_node(int(journey.seed), int(journey.current_node), str(player_build.fighter.id), int(journey.difficulty))
		(player_build.fighter as FighterState).hp *= float(journey.player_hp_ratio)
		start_match_fighters(int(journey.seed) + int(journey.current_node), player_build, enemy_build)
		if not journey.battle_snapshot.is_empty():
			var saved: Dictionary = journey.battle_snapshot
			var restored := BattleEngine.from_state(saved.get("engine", {}))
			if restored != null:
				_engine = restored
				_left_skill = saved.get("left_skill", _left_skill)
				_right_skill = saved.get("right_skill", _right_skill)
				_left_active_items = saved.get("left_active_items", _left_active_items)
				_right_active_items = saved.get("right_active_items", _right_active_items)
				left_name.text = str(saved.get("left_name", left_name.text))
				right_name.text = str(saved.get("right_name", right_name.text))
				left_actor.actor_id = str(_engine.snapshot().left.id)
				right_actor.actor_id = str(_engine.snapshot().right.id)
				_build_item_bar()
				_refresh_hud()
	else:
		start_match(7001, "juggernaut", "axe")
	result_button.visible = false
	if _engine.finished():
		_show_result()

func _restore_battle_snapshot(saved: Dictionary) -> void:
	var restored := BattleEngine.from_state(saved.get("engine", {}))
	if restored == null:
		return
	_engine = restored
	_left_skill = saved.get("left_skill", _left_skill)
	_right_skill = saved.get("right_skill", _right_skill)
	_left_active_items = saved.get("left_active_items", _left_active_items)
	_right_active_items = saved.get("right_active_items", _right_active_items)
	left_name.text = str(saved.get("left_name", left_name.text))
	right_name.text = str(saved.get("right_name", right_name.text))
	left_actor.actor_id = str(_engine.snapshot().left.id)
	right_actor.actor_id = str(_engine.snapshot().right.id)
	_build_item_bar()
	_refresh_hud()

func _prepare_hud() -> void:
	var heading := Label.new()
	var session := get_tree().root.get_node_or_null("AppSession")
	heading.text = "演练场"
	if session != null and bool(session.get("journey_battle_pending")):
		var journey: Variant = session.call("ensure_journey_state")
		heading.text = "第 %d / 5 战" % int(journey.current_node / 2 + 1)
	if session != null and bool(session.call("is_tower_battle_pending")):
		var state: PreparationState = session.call("ensure_preparation_state")
		heading.text = "转生之塔 · 第 %d / 20 层" % int(state.quest_progress.tower_run.floor)
	heading.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	heading.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(28))
	heading.add_theme_color_override("font_color", Color("e9bc66"))
	add_child(heading)
	heading.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
	heading.offset_top = 8.0
	var log_button := Button.new()
	log_button.text = "记录"
	log_button.custom_minimum_size = Vector2(Layout.TOUCH_HEIGHT, Layout.TOUCH_HEIGHT)
	log_button.visible = AppSettings.get_flag("battleLog")
	_log_button = log_button
	add_child(log_button)
	log_button.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT)
	log_button.offset_left = -124.0
	log_button.offset_right = -16.0
	log_button.offset_top = 8.0
	log_button.offset_bottom = 116.0
	Layout.paint_button(log_button)
	_log_dialog = AcceptDialog.new()
	_log_dialog.title = "最近战斗记录"
	add_child(_log_dialog)
	log_button.pressed.connect(func() -> void:
		_refresh_log_dialog()
		_log_dialog.popup_centered(Vector2i(500, 260))
	)
	timer_label.reparent($Bottom, false)
	$Bottom.move_child(timer_label, 0)
	_left_mana_bar = _add_mana_bar(left_hp_bar, Color("64c783"))
	_right_mana_bar = _add_mana_bar(right_hp_bar, Color("dd7266"))
	_enemy_skill_label = Label.new()
	_enemy_skill_label.add_theme_font_size_override("font_size", 20)
	_enemy_skill_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	right_mp_label.get_parent().add_child(_enemy_skill_label)
	right_mp_label.get_parent().move_child(_enemy_skill_label, right_mp_label.get_index() + 1)
	for label in [left_name, right_name, left_hp_label, right_hp_label, left_mp_label, right_mp_label]:
		label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		label.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(24 if label in [left_name, right_name] else 20))
	_enemy_skill_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	var icons_enabled := AppSettings.get_flag("statusIcons")
	left_status_icons.visible = icons_enabled
	right_status_icons.visible = icons_enabled
	for label in [left_status_label, right_status_label]:
		label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		label.max_lines_visible = 2
		label.show()
		label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		label.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(18))
		label.mouse_filter = Control.MOUSE_FILTER_STOP
		label.gui_input.connect(_on_status_input.bind(label))
	manual_skill_button.custom_minimum_size.y = Layout.TOUCH_HEIGHT
	manual_skill_button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	Layout.paint_button(manual_skill_button)
	result_button.custom_minimum_size.y = Layout.TOUCH_HEIGHT
	result_button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	Layout.paint_button(result_button, true)
	manual_skill_button.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(24))
	_aghanim_button = Button.new()
	_aghanim_button.name = "AghanimAbility"
	_aghanim_button.custom_minimum_size.y = Layout.TOUCH_HEIGHT
	_aghanim_button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_aghanim_button.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(22))
	_aghanim_button.icon = preload("res://assets/android/art/tower/v0.1.7/aghanim-v1.png")
	_aghanim_button.expand_icon = true
	_aghanim_button.add_theme_constant_override("icon_max_width", 44)
	preload("res://scripts/ui/CorePageLayout.gd").paint_button(_aghanim_button)
	_aghanim_button.pressed.connect(use_player_aghanim)
	manual_skill_button.get_parent().add_child(_aghanim_button)
	manual_skill_button.get_parent().move_child(_aghanim_button, manual_skill_button.get_index() + 1)
	_aghanim_button.hide()
	_compact_actions = HBoxContainer.new()
	_compact_actions.name = "CompactSkillActions"
	_compact_actions.add_theme_constant_override("separation", 10)
	$Bottom/ManualControls.add_child(_compact_actions)
	$Bottom/ManualControls.move_child(_compact_actions, 0)
	_compact_actions.hide()
	for name_label: Label in [left_name, right_name]:
		var badge := Label.new()
		badge.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(16))
		badge.add_theme_color_override("font_color", Color("7eddf0"))
		badge.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		name_label.get_parent().add_child(badge)
		name_label.get_parent().move_child(badge, name_label.get_index() + 1)
		_augment_badges.append(badge)
		badge.hide()
	resized.connect(_layout_combat_hud)
	$TopHud.resized.connect(_layout_combat_hud)
	$Bottom.resized.connect(_layout_combat_hud)
	$Bottom.minimum_size_changed.connect(func() -> void: _layout_combat_hud.call_deferred())
	_layout_combat_hud.call_deferred()

func _layout_combat_hud() -> void:
	# Text size, status lines and owned augments can change the panel height.
	# Keep its lower edge above the manual controls instead of overlapping them.
	var hud: Control = $TopHud
	var bottom: Control = $Bottom
	var compact := size.y < 1100
	if compact and manual_skill_button.get_parent() != _compact_actions:
		manual_skill_button.reparent(_compact_actions, false)
		_aghanim_button.reparent(_compact_actions, false)
		manual_skill_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_aghanim_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_compact_actions.show()
	elif not compact and manual_skill_button.get_parent() == _compact_actions:
		manual_skill_button.reparent($Bottom/ManualControls, false)
		_aghanim_button.reparent($Bottom/ManualControls, false)
		$Bottom/ManualControls.move_child(manual_skill_button, 0)
		$Bottom/ManualControls.move_child(_aghanim_button, 1)
		_compact_actions.hide()
	if _engine != null: _refresh_manual_controls()
	bottom.anchor_top = 1.0
	bottom.anchor_bottom = 1.0
	bottom.offset_top = -bottom.get_combined_minimum_size().y - 18.0
	bottom.offset_bottom = -18.0
	hud.anchor_top = 0.0
	hud.anchor_bottom = 0.0
	var height := hud.get_combined_minimum_size().y
	hud.offset_top = minf(size.y * 0.47 + 12.0, $Bottom.position.y - height - 12.0)
	hud.offset_bottom = hud.offset_top + height
	$BattleStage.anchor_bottom = minf(0.47, maxf(0.1, (hud.position.y - 12.0) / maxf(1.0, size.y)))

func _add_mana_bar(health: ProgressBar, health_color: Color) -> ProgressBar:
	var track := StyleBoxFlat.new()
	track.bg_color = Color("263d4a")
	var health_fill := StyleBoxFlat.new()
	health_fill.bg_color = health_color
	health.add_theme_stylebox_override("background", track)
	health.add_theme_stylebox_override("fill", health_fill)
	var mana := ProgressBar.new()
	var mana_fill := StyleBoxFlat.new()
	mana_fill.bg_color = Color("46b9e8")
	mana.show_percentage = false
	mana.custom_minimum_size.y = 10
	mana.add_theme_stylebox_override("background", track)
	mana.add_theme_stylebox_override("fill", mana_fill)
	health.get_parent().add_child(mana)
	health.get_parent().move_child(mana, health.get_index() + 1)
	return mana

func _on_status_input(event: InputEvent, label: Label) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		var popup := AcceptDialog.new()
		popup.title = "战斗状态"
		popup.dialog_text = label.tooltip_text
		add_child(popup)
		popup.close_requested.connect(popup.queue_free)
		popup.confirmed.connect(popup.queue_free)
		popup.popup_centered()

func _shield_text(fighter: Dictionary) -> String:
	return "护盾 通%d · 物%d · 法%d" % [fighter.shield, maxf(0.0, float(fighter.total_shields) - float(fighter.shield) - float(fighter.magic_shield)), fighter.magic_shield]

func _skill_remaining(fighter: Dictionary, skill: Dictionary) -> int:
	var key := str(skill.get("id", ""))
	if str(skill.get("runtime_mode", "")) in ["generic_placeholder", "android_hero"] and not str(fighter.hero_id).is_empty():
		key = "hero:%s:ultimate" % fighter.hero_id
	return int(fighter.cooldowns.get(key, 0))

func _skill_text(fighter: Dictionary, skill: Dictionary) -> String:
	if skill.is_empty(): return "无主动技能"
	var name_text := str(skill.get("display_name", "技能"))
	var remaining := _skill_remaining(fighter, skill)
	if remaining > 0: return "%s · %.1fs" % [name_text, remaining / 1000.0]
	return "%s · %d MP" % [name_text, int(skill.get("mana_cost", 0))]

func start_match(seed: int, left_preset: String, right_preset: String) -> void:
	var loadout := PreparationState.new(left_preset)
	var demo_item := ItemInstance.new("demo_dagon", "dagon", ItemInstance.Kind.EQUIPMENT)
	loadout.add_item(demo_item)
	LoadoutRules.place(loadout, demo_item.id, ItemInstance.Kind.EQUIPMENT, 0)
	start_match_fighters(seed, BattleLoadoutBuilder.build(loadout), BattleLoadoutBuilder.build(PreparationState.new(right_preset)))

func start_match_fighters(seed: int, left_build: Dictionary, right_build: Dictionary) -> void:
	_engine = BattleEngine.new(seed, left_build.fighter as FighterState, right_build.fighter as FighterState)
	_presentation = PresentationController.new(AppSettings.get_flag("animations"))
	_left_skill = left_build.skill as Dictionary
	_right_skill = right_build.skill as Dictionary
	_left_active_items = left_build.get("active_items", []) as Array
	_equipment_slots = left_build.get("equipment_slots", {})
	_right_active_items = right_build.get("active_items", []) as Array
	left_name.text = "我方 · " + str(left_build.display_name)
	right_name.text = "对手 · " + str(right_build.display_name)
	left_actor.actor_id = (left_build.fighter as FighterState).id
	right_actor.actor_id = (right_build.fighter as FighterState).id
	var art := preload("res://scripts/ui/AndroidArtCatalog.gd")
	var player_texture := art.hero_texture((left_build.fighter as FighterState).hero_id if not (left_build.fighter as FighterState).hero_id.is_empty() else left_actor.actor_id)
	var enemy_texture := art.hero_texture((right_build.fighter as FighterState).hero_id if not (right_build.fighter as FighterState).hero_id.is_empty() else right_actor.actor_id)
	if player_texture != null:
		left_actor.texture = player_texture
	if enemy_texture != null:
		right_actor.texture = enemy_texture
	_elapsed_fraction_ms = 0.0
	_checkpoint_elapsed = 0
	_recent_events.clear()
	result_label.text = ""
	_build_item_bar()
	_refresh_hud()
	_refresh_manual_controls()

func _process(delta: float) -> void:
	if _engine == null:
		return
	if _engine.finished():
		if not result_button.visible:
			_checkpoint()
			_show_result()
		return
	effects_layer.set_stage_anchors(stage_anchor("left"), stage_anchor("right"), _engine._right.id)
	_presentation.advance(delta)
	_apply_presentation()
	_elapsed_fraction_ms += delta * 1000.0 * _presentation.visual_time_scale()
	while _elapsed_fraction_ms >= FIXED_STEP_MS and not _engine.finished():
		_elapsed_fraction_ms -= FIXED_STEP_MS
		_try_auto_actives("right", _right_active_items)
		_try_auto_cast("right", _right_skill)
		if bool(_engine._right.hero_state.get("has_aghanim", false)) and not bool(_engine._right.hero_state.get("aghanim_used", false)):
			var aghanim_result := _engine.command({"kind": "cast_aghanim", "source_id": "right"})
			if aghanim_result.accepted: _present_manual_result(aghanim_result)
		for event in _engine.step(FIXED_STEP_MS):
			_recent_events.append("%s → %s  %d" % [event["source_id"], event["target_id"], event["amount"]])
			effects_layer.show_event(event)
			battle_audio.play_event(event)
			_present_impact(event)
			_play_actor_motion(event)
			if _recent_events.size() > 6:
				_recent_events.pop_front()
		_checkpoint_elapsed += FIXED_STEP_MS
	_refresh_hud()
	if (_journey_battle or _tower_battle) and (_checkpoint_elapsed >= 1000 or _engine.finished()):
		_checkpoint()
	if _engine.finished():
		_show_result()

func _show_result() -> void:
	result_label.text = "结果：%s" % _engine.result()["winner_id"]
	result_button.visible = true
	result_button.text = "返回远征" if _journey_battle else "返回主页"

func _checkpoint() -> Dictionary:
	if (not _journey_battle and not _tower_battle) or _engine == null:
		return {"accepted": true, "reason": ""}
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if _tower_battle and session != null and bool(session.call("is_tower_battle_pending")):
		var state: PreparationState = session.call("ensure_preparation_state")
		state.quest_progress.tower_run.battle_snapshot = {"engine": _engine.export_state(), "left_skill": _left_skill.duplicate(true), "right_skill": _right_skill.duplicate(true), "left_active_items": _left_active_items.duplicate(true), "right_active_items": _right_active_items.duplicate(true), "left_name": left_name.text, "right_name": right_name.text}
		var tower_saved: Dictionary = session.call("save_progress")
		if not tower_saved.accepted: result_label.text = str(tower_saved.reason)
		_checkpoint_elapsed = 0
		return tower_saved
	if session == null or not bool(session.get("journey_battle_pending")):
		return {"accepted": true, "reason": ""}
	var journey: JourneyState = session.call("ensure_journey_state")
	journey.battle_snapshot = {"engine": _engine.export_state(), "left_skill": _left_skill.duplicate(true), "right_skill": _right_skill.duplicate(true), "left_active_items": _left_active_items.duplicate(true), "right_active_items": _right_active_items.duplicate(true), "left_name": left_name.text, "right_name": right_name.text}
	var saved: Dictionary = session.call("save_progress")
	if not saved.accepted:
		result_label.text = str(saved.reason)
	_checkpoint_elapsed = 0
	return saved

func _notification(what: int) -> void:
	if what in [NOTIFICATION_APPLICATION_PAUSED, NOTIFICATION_WM_CLOSE_REQUEST]:
		_checkpoint()

func _refresh_hud() -> void:
	var snapshot: Dictionary = _engine.snapshot()
	var left: Dictionary = snapshot["left"]
	var right: Dictionary = snapshot["right"]
	left_hp_bar.max_value = left["max_hp"]
	left_hp_bar.value = left["hp"]
	right_hp_bar.max_value = right["max_hp"]
	right_hp_bar.value = right["hp"]
	left_hp_label.text = "%d / %d HP\n%d / %d MP" % [left.hp, left.max_hp, left.mp, left.max_mp]
	right_hp_label.text = "%d / %d HP\n%d / %d MP" % [right.hp, right.max_hp, right.mp, right.max_mp]
	_left_mana_bar.max_value = maxf(1.0, float(left.max_mp))
	_left_mana_bar.value = left.mp
	_right_mana_bar.max_value = maxf(1.0, float(right.max_mp))
	_right_mana_bar.value = right.mp
	left_mp_label.text = _shield_text(left)
	right_mp_label.text = _shield_text(right)
	_enemy_skill_label.text = _skill_text(right, _right_skill)
	_refresh_buff_bar(left_status_icons, left_status_label, left)
	_refresh_buff_bar(right_status_icons, right_status_label, right)
	var catalog := preload("res://scripts/content/HeroAugmentCatalog.gd")
	for index in 2:
		var fighter: FighterState = _engine._left if index == 0 else _engine._right
		var entries: Array[String] = []
		var details: Array[String] = []
		if bool(fighter.hero_state.get("has_shard", false)):
			entries.append("魔晶生效")
			details.append(str(catalog.SHARDS.get(fighter.hero_id, "")))
		if bool(fighter.hero_state.get("has_aghanim", false)):
			entries.append("神杖已使用" if fighter.hero_state.get("aghanim_used", false) else "神杖就绪")
			details.append(str(catalog.AGHANIMS.get(fighter.hero_id, "")))
		_augment_badges[index].text = " · ".join(entries)
		_augment_badges[index].tooltip_text = "\n".join(details)
		_augment_badges[index].visible = not entries.is_empty()
	timer_label.text = "%0.1f / %0.1f 秒" % [float(snapshot["elapsed_ms"]) / 1000.0, _engine.time_limit_ms / 1000.0]
	if _log_dialog.visible: _refresh_log_dialog()
	_refresh_manual_controls()

func _refresh_log_dialog() -> void:
	_log_dialog.dialog_text = "暂无战斗记录" if _recent_events.is_empty() else "\n".join(_recent_events)

func _refresh_manual_controls() -> void:
	if _engine == null: return
	var owner := _engine._left
	_aghanim_button.visible = bool(owner.hero_state.get("has_aghanim", false))
	var used := bool(owner.hero_state.get("aghanim_used", false))
	_aghanim_button.text = ("神杖 · 已使用" if used else "神杖 · 施放") if size.y < 1100 else ("神杖 · 本场已使用" if used else "神杖 · 每场一次 · 无消耗")
	_aghanim_button.tooltip_text = str(preload("res://scripts/content/HeroAugmentCatalog.gd").AGHANIMS.get(owner.hero_id, ""))
	_aghanim_button.disabled = used or _engine.finished() or owner.has_status("stun") or not owner.action_block("skill").is_empty()
	var left: Dictionary = _engine.snapshot().left
	var mana_cost := int(_left_skill.get("mana_cost", 0))
	manual_skill_button.text = _skill_text(left, _left_skill)
	manual_skill_button.disabled = _left_skill.is_empty() or float(left.mp) < mana_cost or _engine.finished() or _skill_remaining(left, _left_skill) > 0 or not str(left.skill_block).is_empty()
	for button in _item_buttons:
		var item: Dictionary = button.get_meta("active")
		if item.is_empty(): continue
		var remaining := int(left.cooldowns.get("active:" + str(item.id), 0))
		if item.get("kind") == "consumable":
			button.text = ""
			button.disabled = _engine.finished() or not str(left.consumable_block).is_empty()
			continue
		button.text = "%.1f" % (remaining / 1000.0) if remaining > 0 else ""
		button.disabled = remaining > 0 or _engine.finished() or not str(left.item_block).is_empty()
		if item.get("kind") == "aeon_disk" and float(left.hp) / float(left.max_hp) >= float(item.threshold):
			button.disabled = true

func _build_item_bar() -> void:
	for child in manual_items.get_children():
		manual_items.remove_child(child)
		child.queue_free()
	_item_buttons.clear()
	for slot in 6:
		var entry: Dictionary = _equipment_slots.get(slot, {})
		if _equipment_slots.is_empty() and slot < _left_active_items.size():
			var active: Dictionary = _left_active_items[slot]
			entry = {"id": active.id, "display_name": active.get("display_name", active.id), "active": active}
		var item: Dictionary = entry.get("active", {})
		var button := Button.new()
		button.set_meta("active", item)
		button.tooltip_text = str(entry.get("display_name", "空装备格"))
		if entry.has("id"): button.icon = ItemIconFactory.icon_for(str(entry.id), ItemInstance.Kind.EQUIPMENT)
		else: button.text = str(slot + 1)
		button.expand_icon = true
		button.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
		button.add_theme_constant_override("icon_max_width", 60)
		button.custom_minimum_size = Vector2(48, Layout.TOUCH_HEIGHT)
		Layout.paint_button(button)
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.disabled = item.is_empty()
		if not item.is_empty(): button.pressed.connect(use_player_active.bind(item))
		manual_items.add_child(button)
		_item_buttons.append(button)

func use_player_aghanim() -> Dictionary:
	if _engine == null: return {"accepted": false, "reason": "battle_not_ready"}
	var before := _engine.export_state()
	var session := get_tree().root.get_node_or_null("AppSession")
	var progress: Dictionary = session.ensure_preparation_state().quest_progress.duplicate(true)
	var journey: JourneyState = session.ensure_journey_state()
	var old_checkpoint := journey.battle_snapshot.duplicate(true)
	var result := _engine.command({"kind": "cast_aghanim", "source_id": "left"})
	if result.accepted:
		var saved := _checkpoint()
		if not saved.accepted:
			_engine = BattleEngine.from_state(before)
			session.ensure_preparation_state().quest_progress = progress
			journey.battle_snapshot = old_checkpoint
			_refresh_hud()
			result = saved
	_present_manual_result(result)
	return result

func cast_player_skill() -> Dictionary:
	if _engine == null: return {"accepted": false, "reason": "battle_not_ready"}
	var result: Dictionary = _engine.command({"kind": "cast_skill", "source_id": "left", "skill": _left_skill})
	_present_manual_result(result)
	return result

func use_player_active(item: Dictionary) -> Dictionary:
	if _engine == null: return {"accepted": false, "reason": "battle_not_ready"}
	if item.get("kind") == "consumable":
		return _use_consumable(str(item.instance_id))
	var result: Dictionary = _engine.command({"kind": "use_active", "source_id": "left", "item": item})
	_present_manual_result(result)
	return result

func _use_consumable(item_id: String) -> Dictionary:
	var session := get_tree().root.get_node_or_null("AppSession")
	if (not _journey_battle and not _tower_battle) or session == null:
		return {"accepted": false, "reason": "仅正式战斗可消耗物品"}
	var state: PreparationState = session.call("ensure_preparation_state")
	var instance := state.item(item_id)
	var before_engine := _engine.export_state()
	var journey: JourneyState = session.call("ensure_journey_state")
	var before_checkpoint: Dictionary = state.quest_progress.tower_run.get("battle_snapshot", {}).duplicate(true) if _tower_battle else journey.battle_snapshot.duplicate(true)
	var result := _engine.use_consumable(state, item_id)
	if not result.accepted:
		_present_manual_result(result)
		return result
	var old_slot := -1
	for slot in _equipment_slots:
		if _equipment_slots[slot].get("active", {}).get("instance_id") == item_id:
			old_slot = slot
			break
	if old_slot >= 0:
		_equipment_slots.erase(old_slot)
	_build_item_bar()
	var checkpoint := {"engine": _engine.export_state(), "left_skill": _left_skill, "right_skill": _right_skill, "left_active_items": _left_active_items, "right_active_items": _right_active_items, "left_name": left_name.text, "right_name": right_name.text}
	if _tower_battle:
		state.quest_progress.tower_run.battle_snapshot = checkpoint
	else:
		journey.battle_snapshot = checkpoint
	var saved: Dictionary = session.call("save_progress")
	if not saved.accepted:
		_engine = BattleEngine.from_state(before_engine)
		state.add_item(instance)
		instance.location = ItemInstance.Location.EQUIPMENT
		instance.slot = old_slot
		if old_slot >= 0:
			state.equipment_slots[old_slot] = instance.id
		_equipment_slots = BattleLoadoutBuilder.build(state).equipment_slots
		if _tower_battle:
			state.quest_progress.tower_run.battle_snapshot = before_checkpoint
		else:
			journey.battle_snapshot = before_checkpoint
		_build_item_bar()
		result = saved
	_present_manual_result(result)
	return result

func _present_manual_result(result: Dictionary) -> void:
	if not bool(result.get("accepted", false)):
		result_label.text = "无法施放：%s" % str(result.get("reason", "不可用"))
		return
	var event: Dictionary = result.event
	for extra in result.get("events", []):
		if extra.get("kind") == "skill_reflected": effects_layer.show_event(extra)
	effects_layer.show_event(event)
	battle_audio.play_event(event)
	_present_impact(event)
	_refresh_hud()
	_play_actor_motion(event)
	_recent_events.append("%s施放 %s" % ["我方" if event.get("source_id") == "left" else "敌方", str(event.get("skill_id", event.get("item_id", "技能")))])
	if _recent_events.size() > 6: _recent_events.pop_front()
	if _engine.finished():
		_checkpoint()
		_show_result()

func _on_manual_skill_pressed() -> void:
	cast_player_skill()

func stage_anchor(side: String) -> Vector2:
	var actor := left_actor if side == "left" else right_actor
	return actor.position + actor.size * Vector2(0.5, 0.46)

func _try_auto_cast(source_id: String, skill: Dictionary) -> void:
	if skill.is_empty() or int(skill.get("mana_cost", 0)) <= 0:
		return
	var result: Dictionary = _engine.command({"kind": "cast_skill", "source_id": source_id, "skill": skill})
	if result.get("accepted", false):
		var event: Dictionary = result["event"]
		for extra in result.get("events", []):
			if extra.get("kind") == "skill_reflected": effects_layer.show_event(extra)
		_recent_events.append("%s 施放 %s  %d" % [event["source_id"], event["skill_id"], event["amount"]])
		effects_layer.show_event(event)
		battle_audio.play_event(event)
		_present_impact(event)
		_play_actor_motion(event)
		if _recent_events.size() > 6:
			_recent_events.pop_front()

func _try_auto_actives(source_id: String, items: Array) -> void:
	for item in items:
		var result: Dictionary = _engine.command({"kind": "use_active", "source_id": source_id, "item": item as Dictionary, "automatic": true})
		if not bool(result.get("accepted", false)):
			continue
		var event: Dictionary = result["event"]
		_recent_events.append("%s 使用 %s" % [event["source_id"], event["item_id"]])
		effects_layer.show_event(event)
		battle_audio.play_event(event)
		_present_impact(event)
		if _recent_events.size() > 6:
			_recent_events.pop_front()

func _refresh_buff_bar(container: HBoxContainer, label: Label, fighter: Dictionary) -> void:
	var statuses: Dictionary = fighter.statuses.duplicate()
	for key in ["invulnerable", "ghost", "armor_buff", "speed_buff", "critical_ready", "curse_speed",
		"aghanim_omni", "aghanim_crit_lock", "aghanim_viper", "aghanim_reflect", "aghanim_legion", "aghanim_borrowed"]:
		var remaining := int(fighter.hero_timers.get(key, 0))
		if remaining > 0: statuses[key] = remaining
	for source in fighter.hero_state.get("shield_sources", {}).values():
		var remaining := int(source.remaining_ms)
		if remaining > 0: statuses["timed_shield"] = maxi(remaining, int(statuses.get("timed_shield", 0)))
	var details: Array[String] = []
	var keys := statuses.keys()
	keys.sort()
	for key in keys:
		details.append("%s %.1fs" % [_status_name(str(key)), float(statuses[key]) / 1000.0])
	label.text = "BUFF · 无状态" if details.is_empty() else "BUFF · %d 项  ›" % details.size()
	label.tooltip_text = "当前没有持续状态" if details.is_empty() else "\n".join(details)
	for index in 3:
		if index == container.get_child_count():
			var chip := Label.new()
			chip.custom_minimum_size = Vector2(70, 27)
			chip.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
			chip.add_theme_font_size_override("font_size", 17)
			var background := StyleBoxFlat.new()
			background.bg_color = Color("203544")
			background.content_margin_left = 5.0
			background.content_margin_right = 5.0
			chip.add_theme_stylebox_override("normal", background)
			container.add_child(chip)
		var chip := container.get_child(index) as Label
		chip.visible = index < details.size() or index == 0
		chip.text = details[index] if index < details.size() else "—"
		chip.tooltip_text = chip.text
		chip.add_theme_color_override("font_color", _status_color(str(keys[index])) if index < keys.size() else Color("8aa5b3"))

func _status_name(status_id: String) -> String:
	return {"stun": "眩晕", "silence": "沉默", "slow": "减速", "haste": "加速", "evasion": "闪避",
		"ghost": "幽灵", "invulnerable": "无敌", "armor_buff": "护甲", "speed_buff": "攻速",
		"critical_ready": "暴击", "curse_speed": "诅咒攻速", "timed_shield": "护盾", "mute": "禁装备",
		"disarm": "缴械", "hex": "妖术", "break": "破被动", "banish": "放逐", "time_stop": "时停",
		"freeze": "冻结", "taunt": "嘲讽", "debuff_immune": "免控", "skin_slow": "腐蚀减速",
		"positive_block": "禁增益", "blade_mail": "反伤", "shard_dilation": "时间膨胀",
		"shard_hunger": "战斗饥渴", "shard_poison_armor": "毒素破甲",
		"aghanim_omni": "小无敌斩", "aghanim_crit_lock": "暴击封锁",
		"aghanim_viper": "神杖减伤", "aghanim_reflect": "技能反射",
		"aghanim_legion": "神杖攻速", "aghanim_borrowed": "回光返照"}.get(status_id, status_id)

func _status_color(status_id: String) -> Color:
	return {
		"stun": Color(1.0, 0.78, 0.28), "silence": Color(0.76, 0.46, 0.96),
		"slow": Color(0.4, 0.78, 1.0), "haste": Color(1.0, 0.46, 0.26),
		"evasion": Color(0.58, 0.94, 0.62),
	}.get(status_id, Color(0.86, 0.9, 0.94))

func _present_impact(event: Dictionary) -> void:
	var target_id := str(event.get("target_id", ""))
	var state := _engine.snapshot()
	var target_max_hp := 0.0
	for fighter in [state.left, state.right]:
		if fighter.id == target_id:
			target_max_hp = float(fighter.max_hp)
			break
	_presentation.push_event(event, target_max_hp)

func _play_actor_motion(event: Dictionary) -> void:
	var source := _engine._fighter_for_id(str(event.get("source_id", "")))
	if source != null:
		if source.id == left_actor.actor_id:
			left_actor.attack_interval_ms = source.effective_attack_interval_ms()
		if source.id == right_actor.actor_id:
			right_actor.attack_interval_ms = source.effective_attack_interval_ms()
	left_actor.play_event(event)
	right_actor.play_event(event)

func _apply_presentation() -> void:
	left_actor.apply_presentation(_presentation.side_state("left"))
	right_actor.apply_presentation(_presentation.side_state("right"))
	var shake := _presentation.camera_offset()
	$BattleStage.offset_left = 14.0 + shake.x
	$BattleStage.offset_right = -14.0 + shake.x
	$BattleStage.offset_top = shake.y
	$BattleStage.offset_bottom = shake.y

func _on_result_button_pressed() -> void:
	if _tower_battle:
		var session: Node = get_tree().root.get_node("AppSession")
		var snapshot := _engine.snapshot()
		var victory := str(_engine.result().get("winner_id", "")) == str(snapshot.left.id)
		var result: Dictionary = session.call("complete_tower_battle", victory, float(snapshot.left.hp), float(snapshot.left.max_hp))
		if not result.accepted:
			result_label.text = str(result.reason)
			return
		ScreenRouter.open(get_tree(), "tower")
		return
	if _journey_battle:
		var session: Node = get_tree().root.get_node_or_null("AppSession")
		if session != null:
			var victory := str(_engine.result().get("winner_id", "")) == str(_engine.snapshot().left.id)
			var journey: JourneyState = session.call("ensure_journey_state")
			journey.player_hp_ratio = clampf(float(_engine.snapshot().left.hp) / float(_engine.snapshot().left.max_hp), 0.0, 1.0)
			var result: Dictionary = session.call("complete_journey_battle", victory)
			if not result.accepted and journey.ending != "death":
				result_label.text = str(result.reason)
				return
		ScreenRouter.open(get_tree(), "expedition")
		return
	ScreenRouter.open(get_tree(), "home")
