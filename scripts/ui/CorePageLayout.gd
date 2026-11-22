extends RefCounted

const RUINS = preload("res://assets/android/art/scenes/ruins-menu-v2.jpg")
const CAMP = preload("res://assets/android/art/scenes/camp-v2.jpg")
const CHEST = preload("res://assets/android/art/chests/closed-v1.png")
static var _frames := {}
const TOUCH_HEIGHT := 108.0
const NAV_RESERVE := 132.0

static func frame(fill: Color, rim: Color) -> StyleBoxTexture:
	var key := [fill, rim]
	if _frames.has(key):
		return _frames[key]
	var art := Image.create(64, 64, false, Image.FORMAT_RGBA8)
	for y in 64:
		for x in 64:
			var dx := mini(x, 63 - x)
			var dy := mini(y, 63 - y)
			var inset := minf(mini(dx, dy), (dx + dy - 14) * 0.707)
			if inset < 0:
				continue
			var color := fill
			if inset < 3 or (inset >= 7 and inset < 10):
				color = Color("041824")
			elif inset < 7:
				color = rim
			elif inset < 12:
				color = fill.lightened(0.20) if y < 32 else fill.darkened(0.22)
			art.set_pixel(x, y, color)
	var style := StyleBoxTexture.new()
	style.texture = ImageTexture.create_from_image(art)
	for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]:
		style.set_texture_margin(side, 25)
		style.set_content_margin(side, 14)
	_frames[key] = style
	return style

static func paint_button(button: Button, primary := false) -> void:
	var fill := Color("ffca3f") if primary else Color("0c667e")
	var rim := Color("ffe99a") if primary else Color("30a7c7")
	var ink := Color("051f2f") if primary else Color("fff4cc")
	button.add_theme_stylebox_override("normal", frame(fill, rim))
	button.add_theme_stylebox_override("hover", frame(fill.lightened(0.12), rim))
	button.add_theme_stylebox_override("pressed", frame(fill.darkened(0.22), rim))
	button.add_theme_stylebox_override("disabled", frame(fill.darkened(0.25), rim.darkened(0.25)))
	button.add_theme_stylebox_override("focus", frame(Color.TRANSPARENT, Color("ffe99a")))
	for state in ["font_color", "font_disabled_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
		button.add_theme_color_override(state, ink)
	button.add_theme_color_override("font_disabled_color", ink.darkened(0.18))

static func banner(hero_id: String, title: String, detail: String) -> Control:
	var panel := PanelContainer.new()
	panel.name = "HeroBanner"
	panel.custom_minimum_size.y = 320
	panel.add_theme_stylebox_override("panel", frame(Color("09394c"), Color("ffca3f")))
	var stage := Control.new()
	panel.add_child(stage)
	_banner_art(stage, CAMP, Vector4(0, 0, 1, 1), TextureRect.STRETCH_KEEP_ASPECT_COVERED)
	var hero_path := AndroidArtCatalog.hero_path(hero_id)
	if not hero_path.is_empty():
		_banner_art(stage, load(hero_path), Vector4(0, 0.04, 0.40, 1))
	_banner_art(stage, CHEST, Vector4(0.73, 0.55, 0.99, 0.99))
	var copy := VBoxContainer.new()
	stage.add_child(copy)
	copy.anchor_left = 0.36
	copy.anchor_top = 0.10
	copy.anchor_right = 0.98
	var heading := Label.new()
	heading.text = title
	heading.add_theme_font_size_override("font_size", 40)
	heading.add_theme_color_override("font_color", Color("ffca3f"))
	copy.add_child(heading)
	var description := Label.new()
	description.text = detail
	description.add_theme_font_size_override("font_size", 23)
	description.add_theme_color_override("font_color", Color("fff4cc"))
	copy.add_child(description)
	return panel

static func _banner_art(parent: Control, texture: Texture2D, bounds: Vector4, stretch := TextureRect.STRETCH_KEEP_ASPECT_CENTERED) -> void:
	var art := TextureRect.new()
	art.texture = texture
	art.mouse_filter = Control.MOUSE_FILTER_IGNORE
	art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	art.stretch_mode = stretch
	parent.add_child(art)
	art.anchor_left = bounds.x
	art.anchor_top = bounds.y
	art.anchor_right = bounds.z
	art.anchor_bottom = bounds.w

static func install(page: Control, active_page: String, title: String, subtitle: String) -> void:
	var skin := Theme.new()
	var sample := Button.new()
	paint_button(sample)
	for state in ["normal", "hover", "pressed", "focus", "disabled"]:
		skin.set_stylebox(state, "Button", sample.get_theme_stylebox(state))
	sample.free()
	skin.set_color("font_color", "Button", Color("fff4cc"))
	skin.set_color("font_hover_color", "Button", Color("fff4cc"))
	skin.set_color("font_disabled_color", "Button", Color("b4dbe1"))
	page.theme = skin
	var header := Label.new()
	header.name = "PageHeader"
	header.text = title + "\n" + subtitle
	header.add_theme_font_size_override("font_size", 24)
	header.add_theme_color_override("font_color", Color("ffe297"))
	header.add_theme_stylebox_override("normal", frame(Color("09394c"), Color("30a7c7")))
	header.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
	header.offset_left = 24
	header.offset_top = 12
	header.offset_bottom = 86
	page.add_child(header)
	var footer := HBoxContainer.new()
	footer.name = "PageNavigation"
	footer.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE)
	footer.offset_left = 20
	footer.offset_right = -20
	footer.offset_top = -NAV_RESERVE + 12
	footer.offset_bottom = -12
	footer.add_theme_constant_override("separation", 8)
	page.add_child(footer)
	for destination in ["expedition", "preparation", "home"]:
		var button := Button.new()
		button.text = {"expedition": "远征", "preparation": "整备", "home": "营地"}[destination]
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.custom_minimum_size.y = TOUCH_HEIGHT
		button.add_theme_font_size_override("font_size", preload("res://scripts/app/AppSettings.gd").scaled_font_size(28))
		button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		button.disabled = destination == active_page
		paint_button(button, button.disabled)
		button.pressed.connect(_navigate.bind(page, destination))
		footer.add_child(button)

static func _navigate(page: Control, destination: String) -> void:
	var session := page.get_tree().root.get_node("AppSession")
	if not page.has_method("_on_expedition_pressed"):
		session.call("save_progress")
	ScreenRouter.open(page.get_tree(), destination)
