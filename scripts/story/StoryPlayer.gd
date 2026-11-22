extends CanvasLayer

signal closed
const Catalog = preload("res://scripts/story/StoryCatalog.gd")
const Rules = preload("res://scripts/story/StoryRules.gd")
const Art = preload("res://scripts/ui/AndroidArtCatalog.gd")
const ThemeArt = preload("res://scripts/ui/CorePageLayout.gd")
const AppSettings = preload("res://scripts/app/AppSettings.gd")

var chapter: Dictionary
var session: Node
var persistent := false
var page_index := 0
var revealing := false
var _tween: Tween
var _stage: Control
var _portraits := {}
var _speaker: Label
var _text: Label
var _counter: Label
var _next: Button
var _skip: Button
var _error: Label
var _panel: PanelContainer

static func show_pending(host: Node, app_session: Node) -> void:
	if not is_instance_valid(host) or not host.is_inside_tree() or host.is_queued_for_deletion():
		return
	var state: PreparationState = app_session.call("ensure_preparation_state")
	var id := Rules.pending(state)
	if state.phase == PreparationState.Phase.HOME and not id.is_empty():
		show_chapter(host, app_session, id, true)

static func show_chapter(host: Node, app_session: Node, id: String, saved := false) -> void:
	if not host.get_tree().get_nodes_in_group("story_player").is_empty():
		return
	var state: PreparationState = app_session.call("ensure_preparation_state")
	var hero: String = state.quest_progress.get("story", {}).get("heroes", {}).get(id, state.active_hero_id)
	var entry := Catalog.chapter(id, hero)
	if entry.is_empty():
		return
	var player := load("res://scripts/story/StoryPlayer.gd").new() as CanvasLayer
	player.chapter = entry
	player.session = app_session
	player.persistent = saved
	player.page_index = Rules.page(state, id) if saved else 0
	host.add_child(player)

func _ready() -> void:
	layer = 100
	add_to_group("story_player")
	var root := ColorRect.new()
	root.color = Color.BLACK
	add_child(root)
	root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var layout := VBoxContainer.new()
	root.add_child(layout)
	layout.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layout.offset_left = 24
	layout.offset_right = -24
	layout.offset_top = 24
	layout.offset_bottom = -24
	layout.add_theme_constant_override("separation", 16)
	var title := Label.new()
	title.text = str(chapter.title)
	title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	title.add_theme_color_override("font_color", Color("ffca3f"))
	layout.add_child(title)
	_stage = Control.new()
	_stage.name = "PortraitStage"
	_stage.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_stage.size_flags_stretch_ratio = 1.3
	layout.add_child(_stage)
	var backdrop := TextureRect.new()
	backdrop.texture = ThemeArt.CAMP
	backdrop.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	backdrop.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	_stage.add_child(backdrop)
	backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	for portrait in chapter.portraits:
		var image := TextureRect.new()
		var path := Art.hero_path(str(portrait.hero_id))
		if not path.is_empty():
			image.texture = load(path)
		image.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		image.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		_stage.add_child(image)
		image.anchor_left = 0.0 if portrait.side == "left" else 0.42
		image.anchor_right = 0.58 if portrait.side == "left" else 1.0
		image.anchor_top = 0.02
		image.anchor_bottom = 1.0
		_portraits[portrait.hero_id] = image
	_panel = PanelContainer.new()
	_panel.size_flags_vertical = Control.SIZE_EXPAND_FILL
	layout.add_child(_panel)
	var copy := VBoxContainer.new()
	copy.add_theme_constant_override("separation", 16)
	_panel.add_child(copy)
	_speaker = Label.new()
	_speaker.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(28))
	_speaker.add_theme_color_override("font_color", Color("ffca3f"))
	copy.add_child(_speaker)
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	copy.add_child(scroll)
	_text = Label.new()
	_text.name = "StoryText"
	_text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_text.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_text.add_theme_font_size_override("font_size", AppSettings.scaled_font_size(28))
	_text.add_theme_color_override("font_color", Color("fff4dc"))
	_text.add_theme_constant_override("line_spacing", 10)
	_text.mouse_filter = Control.MOUSE_FILTER_PASS
	scroll.add_child(_text)
	_error = Label.new()
	_error.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_error.add_theme_color_override("font_color", Color("ffb299"))
	layout.add_child(_error)
	_counter = Label.new()
	_counter.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	layout.add_child(_counter)
	_next = Button.new()
	_next.custom_minimum_size.y = 72
	ThemeArt.paint_button(_next, true)
	_next.pressed.connect(advance)
	layout.add_child(_next)
	var skip := Button.new()
	skip.text = "跳过本段"
	skip.custom_minimum_size.y = 64
	ThemeArt.paint_button(skip)
	skip.pressed.connect(func() -> void: _move(true))
	layout.add_child(skip)
	_skip = skip
	_render()

func _render() -> void:
	var line: Dictionary = chapter.lines[page_index]
	var narration: bool = str(line.speaker_id).is_empty()
	for button: Button in [_next, _skip]:
		if narration:
			for style_name in ["normal", "disabled", "hover", "pressed"]:
				button.add_theme_stylebox_override(style_name, StyleBoxEmpty.new())
			for color_name in ["font_color", "font_hover_color", "font_pressed_color"]:
				button.add_theme_color_override(color_name, Color("bbbbbb"))
		else:
			ThemeArt.paint_button(button, button == _next)
	_stage.visible = not narration
	_speaker.visible = not narration
	_speaker.text = str(line.speaker_name)
	_text.text = str(line.text)
	_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER if narration else HORIZONTAL_ALIGNMENT_LEFT
	_text.vertical_alignment = VERTICAL_ALIGNMENT_CENTER if narration else VERTICAL_ALIGNMENT_TOP
	var style: StyleBox = StyleBoxEmpty.new() if narration else ThemeArt.frame(Color("09394c"), Color("ffca3f"))
	_panel.add_theme_stylebox_override("panel", style)
	for id in _portraits:
		(_portraits[id] as TextureRect).modulate = Color.WHITE if id == line.speaker_id else Color(0.35, 0.35, 0.35, 1)
	_counter.text = "%d / %d" % [page_index + 1, chapter.lines.size()]
	_next.text = "结束 · 返回" if page_index == chapter.lines.size() - 1 else "点击继续 ▾"
	_text.get_parent().scroll_vertical = 0
	if _tween != null:
		_tween.kill()
	# 关闭动画时旁白直接整段显示，不做逐字淡入（对齐 Android animations 开关）。
	revealing = narration and AppSettings.get_flag("animations")
	_text.modulate.a = 0.0 if revealing else 1.0
	if revealing:
		_tween = create_tween()
		_tween.tween_property(_text, "modulate:a", 1.0, 0.7)
		_tween.tween_callback(func() -> void: revealing = false)

func advance() -> void:
	if revealing:
		_tween.kill()
		_text.modulate.a = 1
		revealing = false
		return
	_move(false)

func _move(skip: bool) -> void:
	if persistent:
		var result: Dictionary = session.call("advance_story", str(chapter.id), skip)
		if not result.accepted:
			_error.text = str(result.reason) + "；请重试，剧情进度未丢失。"
			return
		if result.finished:
			_finish()
			return
	elif skip or page_index == chapter.lines.size() - 1:
		_finish()
		return
	page_index += 1
	_render()

func _finish() -> void:
	var host := get_parent()
	closed.emit()
	remove_from_group("story_player")
	queue_free()
	if persistent:
		show_pending.call_deferred(host, session)
