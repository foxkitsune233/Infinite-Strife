class_name ItemIconFactory
extends RefCounted

const AndroidArt = preload("res://scripts/ui/AndroidArtCatalog.gd")
const MAX_ICON_EDGE := 160
const MAX_CACHE_ENTRIES := 256
static var _cache: Dictionary = {}
static var _recency: Array[String] = []

static func icon_for(definition_id: String, kind: ItemInstance.Kind) -> Texture2D:
	var path: String = AndroidArt.card_path(definition_id) if kind == ItemInstance.Kind.CARD else AndroidArt.equipment_path(definition_id)
	var cache_key := path if not path.is_empty() else "%s:%d" % [definition_id, kind]
	if path.ends_with("/SCROLL.png"):
		cache_key = definition_id
	if _cache.has(cache_key):
		_recency.erase(cache_key)
		_recency.append(cache_key)
		return _cache[cache_key] as Texture2D
	if not path.is_empty():
		var source := load(path) as Texture2D
		var art := source.get_image() if source != null else null
		if art != null and not art.is_empty():
			var edge := maxi(art.get_width(), art.get_height())
			if edge > MAX_ICON_EDGE:
				var scale := float(MAX_ICON_EDGE) / float(edge)
				art.resize(maxi(1, roundi(art.get_width() * scale)), maxi(1, roundi(art.get_height() * scale)), Image.INTERPOLATE_LANCZOS)
			art.convert(Image.FORMAT_RGBA8)
			art.clear_mipmaps()
			if path.ends_with("/SCROLL.png"):
				var product_path := AndroidArt.equipment_path(definition_id.trim_prefix("recipe_"))
				if not product_path.is_empty():
					var product := (load(product_path) as Texture2D).get_image()
					product.convert(Image.FORMAT_RGBA8)
					var badge := Vector2i(maxi(1, roundi(art.get_width() * 0.46)), maxi(1, roundi(art.get_height() * 0.46)))
					product.resize(badge.x, badge.y, Image.INTERPOLATE_LANCZOS)
					art.blend_rect(product, Rect2i(Vector2i.ZERO, badge), (art.get_size() - badge) / 2)
			return _remember(cache_key, ImageTexture.create_from_image(art))
	var image := Image.create(72, 72, false, Image.FORMAT_RGBA8)
	var background := Color("173b4c") if kind == ItemInstance.Kind.EQUIPMENT else Color("36235b")
	var accent := _accent(definition_id, kind)
	image.fill(background)
	image.fill_rect(Rect2i(3, 3, 66, 66), Color("0b1723"))
	image.fill_rect(Rect2i(6, 6, 60, 60), background)
	_draw_border(image, accent)
	if kind == ItemInstance.Kind.CARD:
		_draw_card(image, accent)
	else:
		_draw_equipment(image, definition_id, accent)
	var texture := ImageTexture.create_from_image(image)
	return _remember(cache_key, texture)

static func _remember(key: String, texture: Texture2D) -> Texture2D:
	while _cache.size() >= MAX_CACHE_ENTRIES:
		_cache.erase(_recency.pop_front())
	_cache[key] = texture
	_recency.append(key)
	return texture

static func clear_cache() -> void:
	_cache.clear()
	_recency.clear()

static func _accent(definition_id: String, kind: ItemInstance.Kind) -> Color:
	if kind == ItemInstance.Kind.CARD:
		return Color("a975ff") if definition_id.get_slice("_", 1).to_int() % 2 == 0 else Color("4dd8ff")
	match definition_id:
		"equipment_0", "equipment_3", "equipment_6":
			return Color("f6c951")
		"equipment_1", "equipment_4", "equipment_7":
			return Color("55bff4")
		_:
			return Color("75de8b")

static func _draw_border(image: Image, accent: Color) -> void:
	image.fill_rect(Rect2i(6, 6, 60, 3), accent)
	image.fill_rect(Rect2i(6, 63, 60, 3), accent)
	image.fill_rect(Rect2i(6, 6, 3, 60), accent)
	image.fill_rect(Rect2i(63, 6, 3, 60), accent)

static func _draw_equipment(image: Image, definition_id: String, accent: Color) -> void:
	var variant := definition_id.to_int() % 3
	for y in range(18, 55):
		for x in range(18, 55):
			var draw := false
			if variant == 0:
				draw = abs(x - y) <= 4 or (x in range(20, 35) and y in range(42, 50))
			elif variant == 1:
				draw = abs(x - 36) + abs(y - 36) < 20
			else:
				draw = (x - 36) * (x - 36) + (y - 36) * (y - 36) < 250
			if draw:
				image.set_pixel(x, y, accent)

static func _draw_card(image: Image, accent: Color) -> void:
	for y in range(18, 54):
		for x in range(18, 54):
			if abs(x - 36) + abs(y - 36) < 16:
				image.set_pixel(x, y, accent)
