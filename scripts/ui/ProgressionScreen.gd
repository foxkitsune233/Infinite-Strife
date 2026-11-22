extends Control

const Heroes = preload("res://scripts/content/AndroidHeroCatalog.gd")
const Quests = preload("res://scripts/progression/QuestRules.gd")
const Levels = preload("res://scripts/progression/HeroLevelRules.gd")
const Settings = preload("res://scripts/app/AppSettings.gd")
const Layout = preload("res://scripts/ui/CorePageLayout.gd")
const Content = preload("res://scripts/content/AndroidContent.gd")

var _session: Node
var _rows: VBoxContainer
var _status: Label

func _ready() -> void:
	_session = get_tree().root.get_node("AppSession")
	var background := ColorRect.new()
	background.color = Color("091b2b")
	background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	background.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(background)
	var layout := VBoxContainer.new()
	layout.name = "Layout"
	layout.add_theme_constant_override("separation", 12)
	add_child(layout)
	layout.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layout.offset_left = 24
	layout.offset_right = -24
	layout.offset_top = 24
	layout.offset_bottom = -24
	var title := Label.new()
	title.name = "PageTitle"
	title.text = "委托任务与英雄等级"
	title.add_theme_font_size_override("font_size", Settings.scaled_font_size(32))
	title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	title.add_theme_color_override("font_color", Color("ffca3f"))
	layout.add_child(title)
	_status = Label.new()
	_status.add_theme_font_size_override("font_size", Settings.scaled_font_size(22))
	_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	layout.add_child(_status)
	var scroll := ScrollContainer.new()
	scroll.name = "BodyScroll"
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	layout.add_child(scroll)
	_rows = VBoxContainer.new()
	_rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_rows.add_theme_constant_override("separation", 14)
	scroll.add_child(_rows)
	var footer := HBoxContainer.new()
	footer.name = "Footer"
	footer.add_theme_constant_override("separation", 12)
	layout.add_child(footer)
	var back := _button("返回主页")
	back.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	back.pressed.connect(func() -> void: ScreenRouter.open(get_tree(), "home"))
	footer.add_child(back)
	var journal := _button("剧情回顾")
	journal.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	journal.pressed.connect(func() -> void: preload("res://scripts/story/StoryJournal.gd").open(self, _session))
	footer.add_child(journal)
	_refresh()
	preload("res://scripts/story/StoryPlayer.gd").show_pending.call_deferred(self, _session)

func _refresh() -> void:
	for child in _rows.get_children():
		child.free()
	var state: PreparationState = _session.call("ensure_preparation_state")
	preload("res://scripts/progression/ProgressionRules.gd").observe(state)
	_status.text = "金币 %d · 已领取 %d/24 节点 · 已开放%s" % [state.gold, Quests.completed_nodes(state.quest_progress), preload("res://scripts/progression/AndroidQuestCatalog.gd").DIFFICULTY_NAMES[Quests.max_difficulty(state.quest_progress)]]
	for quest in Quests.visible(state.quest_progress):
		var progress := int(state.quest_progress.get("quest_counters", {}).get(quest.id, 0))
		var text := Label.new()
		text.add_theme_font_size_override("font_size", Settings.scaled_font_size(24))
		text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		text.text = "节点%d：%s（%d/%d）\n%s\n奖励：%s" % [quest.node, quest.name, progress, quest.objective.target, quest.description, _reward_text(quest.rewards)]
		_rows.add_child(text)
		var claim := _button("领取奖励")
		claim.disabled = progress < int(quest.objective.target) or state.phase != PreparationState.Phase.HOME
		claim.pressed.connect(_claim.bind(str(quest.id)))
		_rows.add_child(claim)
	for hero in Heroes.heroes():
		var level := Levels.level(state, str(hero.id))
		var unlocked: bool = str(hero.id) in state.unlocked_heroes
		var capped := Levels.is_capped(state, level)
		var upgrade := _button("%s · 等级%d/%d · %s" % [hero.display_name, level, Levels.level_cap(state), "未解锁" if not unlocked else "已达上限" if capped else "升级 %d金币" % Levels.upgrade_cost(level)])
		upgrade.disabled = not unlocked or capped or state.gold < Levels.upgrade_cost(level) or state.phase != PreparationState.Phase.HOME
		upgrade.pressed.connect(_upgrade.bind(str(hero.id)))
		_rows.add_child(upgrade)
	var growth := preload("res://scripts/progression/AndroidGrowthRules.gd")
	for kind in ["backpack", "safe", "skills", "equipment_guard", "skill_guard"]:
		var entry: Dictionary = growth.next_entry(state, kind)
		if entry.is_empty():
			continue
		var button := _button("%s · %d金币\n材料：%s" % [entry.name, entry.cost_gold, _material_text(entry.materials)])
		button.disabled = state.phase != PreparationState.Phase.HOME
		button.pressed.connect(_grow.bind(str(kind)))
		_rows.add_child(button)

func _claim(id: String) -> void:
	var result: Dictionary = _session.call("claim_quest", id)
	_refresh()
	_status.text += "\n" + str(result.reason)
	if result.accepted:
		preload("res://scripts/story/StoryPlayer.gd").show_pending(self, _session)

func _upgrade(id: String) -> void:
	var result: Dictionary = _session.call("upgrade_hero", id)
	_refresh()
	_status.text += "\n" + str(result.reason)

func _grow(kind: String) -> void:
	var entry: Dictionary = preload("res://scripts/progression/AndroidGrowthRules.gd").next_entry(_session.call("ensure_preparation_state"), kind)
	var result: Dictionary = _session.call("upgrade_growth", kind)
	_refresh()
	var message := str(result.reason)
	for id in entry.get("materials", {}):
		message = message.replace(str(id), _item_name(str(id)))
	_status.text += "\n" + message

func _reward_text(rewards: Array) -> String:
	var entries: Array[String] = []
	for reward in rewards:
		match str(reward.type):
			"GOLD": entries.append("金币%d" % int(reward.amount))
			"HERO_UNLOCK": entries.append(str(Heroes.hero(str(reward.hero_id)).get("display_name", reward.hero_id)))
			_: entries.append("%s×%d" % [_item_name(str(reward.material_id)), int(reward.amount)])
	return "、".join(entries)

func _item_name(id: String) -> String:
	return str(Content.item(id).get("name", "未知物品"))

func _material_text(materials: Dictionary) -> String:
	var result: Array[String] = []
	var keys := materials.keys()
	keys.sort()
	for id in keys:
		result.append("%s×%d" % [_item_name(str(id)), int(materials[id])])
	return "、".join(result) if not result.is_empty() else "无需材料"

func _button(text: String) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size.y = Layout.TOUCH_HEIGHT
	button.add_theme_font_size_override("font_size", Settings.scaled_font_size(26))
	button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	preload("res://scripts/ui/CorePageLayout.gd").paint_button(button)
	return button
