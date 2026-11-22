extends Control

const Rules = preload("res://scripts/tower/TowerRules.gd")
const Combat = preload("res://scripts/tower/TowerCombat.gd")
const Heroes = preload("res://scripts/content/AndroidHeroCatalog.gd")
const Layout = preload("res://scripts/ui/CorePageLayout.gd")
const Player = preload("res://scripts/story/StoryPlayer.gd")
const ART := "res://assets/android/art/tower/v0.1.7/"
var _session: Node
var _rows: VBoxContainer
var _status: Label

func _ready() -> void:
	_session = get_tree().root.get_node("AppSession")
	var background := TextureRect.new()
	background.texture = Layout.RUINS
	background.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	background.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	add_child(background)
	background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	background.mouse_filter = Control.MOUSE_FILTER_IGNORE
	Layout.install(self, "tower", "转生之塔", "二十层挑战 · 各自的归途")
	var scroll := ScrollContainer.new()
	add_child(scroll)
	scroll.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	scroll.offset_left = 24
	scroll.offset_right = -24
	scroll.offset_top = 100
	scroll.offset_bottom = -90
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_rows = VBoxContainer.new()
	_rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_rows.add_theme_constant_override("separation", 16)
	scroll.add_child(_rows)
	_refresh()
	Player.show_pending.call_deferred(self, _session)

func _refresh() -> void:
	for node in _rows.get_children():
		node.free()
	var state: PreparationState = _session.call("ensure_preparation_state")
	var run: Dictionary = state.quest_progress.get("tower_run", {})
	_label("历史最高 %d / 20 层 · 当前等级上限 %d" % [int(state.quest_progress.get("tower_highest_floor", 0)), HeroLevelRules.level_cap(state)])
	if run.is_empty():
		_art("enter", 280)
		_label("本轮出战：%s\n道中战利品在死亡时按已完成层数折算金币；装备保留。" % _name(state.active_hero_id))
		var locked := Rules.locked_reason(state)
		var enter := _button("进入转生之塔", func() -> void: _action("enter_tower"))
		enter.name = "EnterTower"
		enter.disabled = not locked.is_empty() or state.phase != PreparationState.Phase.HOME
		if not locked.is_empty(): _label(locked)
	else:
		_art("floor", 220)
		var builds := Combat.build(state, run)
		var fighter: FighterState = builds.left.fighter
		var loot_value := 0
		for item in run.loot: loot_value += int(item.value)
		_label("第 %d / 20 层 · %s\n生命 %d / %d · 本轮战利品价值 %d\n下一名对手：%s" % [int(run.floor), _name(str(run.hero_id)), int(fighter.hp), fighter.max_hp, loot_value, builds.right.display_name])
		if bool(run.get("battle_started", false)):
			_button("继续当前楼层战斗", func() -> void: ScreenRouter.open(get_tree(), "battle"))
		elif bool(run.pending_waypoint):
			_label("选择一项道中收益")
			for entry in [["REST", "休整 · 恢复30%最大生命", "rest"], ["SUPPLY", "应急补给 · 下一场前60秒每秒回复1%生命", "supply"], ["FORGE", "锻造遗迹 · 本轮主属性+8（上限48）", "forge"]]:
				var choice := str(entry[0])
				var button := _button(str(entry[1]), _waypoint.bind(choice))
				button.icon = load(ART + str(entry[2]) + "-v1.png")
				button.expand_icon = true
				button.add_theme_constant_override("icon_max_width", 90)
		else:
			var fight := _button("挑战第 %d 层" % int(run.floor), _fight)
			fight.name = "FightTower"
	_label("营地授予 · 金币 %d" % state.gold)
	_label("%s的强化\n%s" % [_name(state.active_hero_id), preload("res://scripts/content/HeroAugmentCatalog.gd").summary(state, state.active_hero_id)])
	for spec in [["SHARD", "pending_shard_heroes", "魔晶", "shard"], ["AGHANIM", "pending_aghanim_heroes", "神杖", "aghanim"]]:
		var pending: Array = state.quest_progress.get(spec[1], [])
		var shown := {}
		for hero in pending:
			if shown.has(hero): continue
			shown[hero] = true
			var button := _button("授予%s · %s（待授予%d份）" % [spec[2], _name(str(hero)), pending.count(hero)], _grant.bind(str(spec[0]), str(hero)))
			button.disabled = state.phase != PreparationState.Phase.HOME
			button.icon = load(ART + str(spec[3]) + "-v1.png")
			var descriptions: Dictionary = preload("res://scripts/content/HeroAugmentCatalog.gd").SHARDS if spec[0] == "SHARD" else preload("res://scripts/content/HeroAugmentCatalog.gd").AGHANIMS
			button.tooltip_text = str(descriptions.get(str(hero), ""))
			button.expand_icon = true
			button.add_theme_constant_override("icon_max_width", 72)
	_label("转生道具 %d 份 · 圣剑：%s" % [state.quest_progress.get("pending_rebirth_items", []).size(), "已保留" if state.quest_progress.get("tower_rapier", false) else "尚未获得"])
	for spec in [["tower_shard", "已授予魔晶"], ["tower_aghs", "已授予神杖"]]:
		var names: Array[String] = []
		for hero in state.quest_progress.get(spec[0], []): names.append(_name(str(hero)))
		if not names.is_empty(): _label(str(spec[1]) + "：" + "、".join(names))
	_status = _label("")
	_status.hide()
	_button("返回营地", func() -> void: ScreenRouter.open(get_tree(), "home"))

func _action(method: String) -> void:
	var result: Dictionary = _session.call(method)
	_refresh()
	_set_status(str(result.reason))
	if result.accepted: Player.show_pending(self, _session)

func _fight() -> void:
	var result: Dictionary = _session.call("begin_tower_battle")
	if result.accepted:
		ScreenRouter.open(get_tree(), "battle")
	else:
		_set_status(str(result.reason))

func _waypoint(choice: String) -> void:
	var result: Dictionary = _session.call("choose_tower_waypoint", choice)
	_refresh()
	_set_status(str(result.reason))

func _grant(kind: String, hero: String) -> void:
	var result: Dictionary = _session.call("grant_tower_reward", kind, hero)
	_refresh()
	var descriptions: Dictionary = preload("res://scripts/content/HeroAugmentCatalog.gd").SHARDS if kind == "SHARD" else preload("res://scripts/content/HeroAugmentCatalog.gd").AGHANIMS
	_set_status("%s已获得强化：%s\n重复奖励不会叠加。" % [_name(hero), descriptions.get(hero, "")] if result.accepted else str(result.reason))

func _set_status(value: String) -> void:
	_status.text = value
	_status.visible = not value.is_empty()

func _label(value: String) -> Label:
	var label := Label.new()
	label.text = value
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.add_theme_font_size_override("font_size", 23)
	label.add_theme_color_override("font_color", Color("fff4cc"))
	label.add_theme_stylebox_override("normal", Layout.frame(Color("09394c"), Color("30a7c7")))
	_rows.add_child(label)
	return label

func _button(value: String, action: Callable) -> Button:
	var button := Button.new()
	button.text = value
	button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	button.custom_minimum_size.y = 80
	Layout.paint_button(button)
	button.pressed.connect(action)
	_rows.add_child(button)
	return button

func _art(id: String, height: float) -> void:
	var image := TextureRect.new()
	image.texture = load(ART + id + "-v1.png")
	image.custom_minimum_size.y = height
	image.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	image.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_rows.add_child(image)

func _name(hero: String) -> String:
	return str(Heroes.hero(hero).get("display_name", hero))
