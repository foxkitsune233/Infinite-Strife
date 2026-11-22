extends Control
## 设置 / 存档诊断 / 图鉴与来源，三页签对齐 Android SettingsActivity。

const CorePageLayout = preload("res://scripts/ui/CorePageLayout.gd")
const AppSettings = preload("res://scripts/app/AppSettings.gd")
const BackupRules = preload("res://scripts/app/BackupRules.gd")
const SaveRepository = preload("res://scripts/save/SaveRepository.gd")
const ItemIconFactory = preload("res://scripts/ui/ItemIconFactory.gd")

const SOURCES_TEXT := "Infinite Strife · 单人远征课设（Godot 迁移版）\n\n" + \
	"角色、卡面、彩色物品和场景：项目内 AI 辅助美术，保留提示词与源图。\n" + \
	"状态 / Buff / DOT：Game-icons.net，Lorc 和 Delapouite，CC BY 3.0；已转换格式、移除底色并着色。\n" + \
	"界面边框：项目内矢量绘制。参考：Dota 2 英雄、装备与电子斗蛐蛐玩法。\n\n" + \
	"音效 / 胜负短乐：Kenney — Interface Sounds、Music Jingles（CC0 1.0）。\n" + \
	"背景音乐：Once Upon a Time (loop)，TAD，CC0 1.0。\n" + \
	"https://opengameart.org/content/once-upon-a-time-loop\nhttps://kenney.nl/assets/interface-sounds"

var _tab := 0
var _layout: VBoxContainer
var _body: VBoxContainer
var _gallery_filter := "全部"

func _ready() -> void:
	_layout = $Layout
	_rebuild()

func _rebuild() -> void:
	for child in _layout.get_children():
		child.queue_free()
	var header := Label.new()
	header.text = "设置 · 存档 · 图鉴"
	header.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(32))
	header.add_theme_color_override("font_color", Color("ffca3f"))
	header.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_layout.add_child(header)
	var tabs := HBoxContainer.new()
	for i in range(3):
		var names := ["游戏体验", "存档与工具", "图鉴与来源"]
		var button := Button.new()
		button.text = names[i]
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.custom_minimum_size.y = 56
		CorePageLayout.paint_button(button, i == _tab)
		button.pressed.connect(func() -> void: _tab = i; _rebuild())
		tabs.add_child(button)
	_layout.add_child(tabs)
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_body = VBoxContainer.new()
	_body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_body.add_theme_constant_override("separation", 12)
	scroll.add_child(_body)
	_layout.add_child(scroll)
	match _tab:
		0: _build_gameplay()
		1: _build_storage()
		2: _build_gallery()
	var back := Button.new()
	back.text = "返回游戏"
	back.custom_minimum_size.y = 56
	CorePageLayout.paint_button(back, true)
	back.pressed.connect(func() -> void: ScreenRouter.open(get_tree(), "home"))
	_layout.add_child(back)

func _section(title: String, subtitle: String = "") -> void:
	var label := Label.new()
	label.text = title if subtitle.is_empty() else "%s\n%s" % [title, subtitle]
	label.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(22))
	label.add_theme_color_override("font_color", Color("ffe297"))
	_body.add_child(label)

func _toggle(title: String, detail: String, key: String) -> void:
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", CorePageLayout.frame(Color("09394c"), Color("30a7c7")))
	var row := HBoxContainer.new()
	panel.add_child(row)
	var text := VBoxContainer.new()
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var name_label := Label.new()
	name_label.text = title
	name_label.add_theme_color_override("font_color", Color("fff4cc"))
	text.add_child(name_label)
	var detail_label := Label.new()
	detail_label.text = detail
	detail_label.add_theme_font_size_override("font_size", 14)
	detail_label.add_theme_color_override("font_color", Color("b4dbe1"))
	detail_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.add_child(detail_label)
	row.add_child(text)
	var check := CheckButton.new()
	check.set_pressed_no_signal(AppSettings.get_flag(key))
	check.toggled.connect(func(value: bool) -> void:
		if not AppSettings.set_flag(key, value):
			_status("设置保存失败，请重试")
			return
		AppSettings.bump()
		# 音效/短乐变更后即时生效： Godot 版音效由 BattleAudioBus 程序化生成。
		if key in ["sound", "jingles"]:
			var audio := get_tree().root.get_node_or_null("BattleAudio")
			if audio != null and audio.has_method("refresh_settings"):
				audio.call("refresh_settings")
	)
	row.add_child(check)
	_body.add_child(panel)

func _build_gameplay() -> void:
	_section("战斗与交互", "显示偏好立即保存，不影响战斗规则")
	_toggle("技能与普攻动画", "控制战斗角色动作与特效。", "animations")
	_toggle("操作振动", "按钮与拖动的触感反馈。", "haptics")
	_toggle("战斗记录", "显示战场最近发生的事件。", "battleLog")
	_toggle("状态图标摘要", "开启为横向图标，关闭为两行文字。", "statusIcons")
	_toggle("游戏音效", "点击、命中、技能与鉴定提示；关闭后立即静音。", "sound")
	_toggle("背景音乐", "循环播放轻奇幻器乐（迁移版暂无音乐音源，偏好仅保存）。", "music")
	_toggle("胜负短乐", "战斗结束的简短提示音乐（迁移版暂无短乐音源，偏好仅保存）。", "jingles")
	_section("界面文字", "在系统字体大小基础上调整；不改变物品占格或伤害")
	var row := HBoxContainer.new()
	var current := AppSettings.text_level()
	for i in range(3):
		var label: String = ["标准", "较大", "最大"][i]
		var button := Button.new()
		button.text = label
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		CorePageLayout.paint_button(button, i == current)
		button.pressed.connect(func() -> void:
			if AppSettings.set_text_level(i): _rebuild()
			else: _status("设置保存失败，请重试")
		)
		row.add_child(button)
	_body.add_child(row)
	var reset := Button.new()
	CorePageLayout.paint_button(reset)
	reset.custom_minimum_size.y = 56
	reset.text = "恢复默认体验设置"
	reset.pressed.connect(func() -> void:
		if AppSettings.reset(): _rebuild()
		else: _status("设置保存失败，请重试")
	)
	_body.add_child(reset)

func _build_storage() -> void:
	_section("存档与备份", "备份包含金币、物品与挑战，恢复前会校验并要求确认")
	var export_button := Button.new()
	CorePageLayout.paint_button(export_button)
	export_button.custom_minimum_size.y = 56
	export_button.text = "导出当前存档备份"
	export_button.pressed.connect(_on_export)
	_body.add_child(export_button)
	var restore_button := Button.new()
	CorePageLayout.paint_button(restore_button)
	restore_button.custom_minimum_size.y = 56
	restore_button.text = "从备份文本恢复"
	restore_button.pressed.connect(_on_restore)
	_body.add_child(restore_button)
	_section("诊断", "可导出当前存档内容用于问题排查")
	var diag := Button.new()
	CorePageLayout.paint_button(diag)
	diag.custom_minimum_size.y = 56
	diag.text = "查看当前存档摘要"
	diag.pressed.connect(_on_diag)
	_body.add_child(diag)

func _build_gallery() -> void:
	_section("完整图鉴", "按种类浏览全部物品与技能卡图标")
	var row := HBoxContainer.new()
	for filter in ["全部", "装备", "卡牌", "材料"]:
		var button := Button.new()
		button.text = filter
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		CorePageLayout.paint_button(button, filter == _gallery_filter)
		button.pressed.connect(func() -> void: _gallery_filter = filter; _rebuild())
		row.add_child(button)
	_body.add_child(row)
	var grid := GridContainer.new()
	grid.columns = 5
	for definition in preload("res://scripts/content/AndroidEquipmentCatalog.gd").items():
		var kind := str(definition.get("kind", ""))
		if _gallery_filter == "装备" and kind != "EQUIPMENT": continue
		if _gallery_filter == "卡牌" and kind != "CARD": continue
		if _gallery_filter == "材料" and kind not in ["MATERIAL", "CONSUMABLE", "SCROLL", "LOOT"]: continue
		var cell := VBoxContainer.new()
		cell.custom_minimum_size = Vector2(120, 120)
		var icon := TextureRect.new()
		icon.texture = ItemIconFactory.icon_for(str(definition.id), _kind_enum(kind))
		icon.custom_minimum_size = Vector2(64, 64)
		icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		icon.add_theme_constant_override("hseparation", 4)
		cell.add_child(icon)
		var name_label := Label.new()
		name_label.text = str(definition.get("name", definition.id))
		name_label.add_theme_font_size_override("font_size", 12)
		name_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		name_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		cell.add_child(name_label)
		grid.add_child(cell)
	_body.add_child(grid)
	_section("制作与素材来源")
	var sources := Label.new()
	sources.text = SOURCES_TEXT
	sources.add_theme_font_size_override("font_size", 14)
	sources.add_theme_color_override("font_color", Color("b4dbe1"))
	sources.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_body.add_child(sources)

func _kind_enum(kind: String) -> ItemInstance.Kind:
	match kind:
		"CARD": return ItemInstance.Kind.CARD
		"CONSUMABLE": return ItemInstance.Kind.CONSUMABLE
		"MATERIAL", "SCROLL", "LOOT": return ItemInstance.Kind.MATERIAL
		_: return ItemInstance.Kind.EQUIPMENT

func _status(text: String) -> void:
	var label := Label.new()
	label.text = text
	label.add_theme_color_override("font_color", Color("ff9d8a"))
	_body.add_child(label)

func _on_export() -> void:
	var session := _session()
	var preparation: PreparationState = session.call("ensure_preparation_state")
	var journey: JourneyState = session.call("ensure_journey_state")
	var text := BackupRules.export_text(preparation, journey)
	DisplayServer.clipboard_set(text)
	_status("备份已复制到剪贴板（%d 字符），请妥善保存。" % text.length())

func _on_restore() -> void:
	var text := DisplayServer.clipboard_get()
	if text.is_empty():
		_status("剪贴板为空，请先复制备份文本。")
		return
	var checked := BackupRules.inspect(text)
	if not bool(checked.get("accepted", false)):
		_status("备份不可用：%s" % str(checked.reason))
		return
	var dialog := ConfirmationDialog.new()
	dialog.title = "恢复此备份？"
	dialog.dialog_text = "备份已通过格式与校验和检查。\n金币 %d · 物品 %d 份 · %s\n\n将替换当前金币、物资与挑战进度；当前存档保留为上一代备份。" % [int(checked.gold), int(checked.items), str(checked.stage)]
	dialog.confirmed.connect(func() -> void:
		var result: Dictionary = _session().call("restore_from_backup", text)
		_status(str(result.reason))
	)
	add_child(dialog)
	dialog.popup_centered(Vector2i(460, 280))

func _on_diag() -> void:
	var session := _session()
	var preparation: PreparationState = session.call("ensure_preparation_state")
	var journey: JourneyState = session.call("ensure_journey_state")
	_status("金币 %d · 物品 %d · 远征节点 %d · 已解锁英雄 %d" % [preparation.gold, preparation.items.size(), journey.current_node, preparation.unlocked_heroes.size()])

func _session() -> Node:
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if session == null:
		session = preload("res://scripts/AppSession.gd").new()
		session.name = "AppSession"
		get_tree().root.add_child(session)
	return session
