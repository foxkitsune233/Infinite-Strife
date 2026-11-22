class_name AndroidArtCatalog
extends RefCounted
## Mirrors Android GameArt + ExpansionArtwork. No save data or gameplay definitions.

const ROOT := "res://assets/android/"
const EQUIPMENT_ALIASES := [
	"iron_branch", "broadsword", "slippers", "chainmail", "gloves_of_haste",
	"ring_of_health", "black_king_bar", "mjollnir", "satanic",
]
const CARD_ALIASES := ["A1", "A2", "A3", "A4", "U1", "U2", "U3", "U4"]
static var _index: Dictionary = {}
static var _initialized := false
const MAX_HERO_EDGE := 512
const MAX_HERO_CACHE_ENTRIES := 16
static var _hero_cache: Dictionary = {}
static var _hero_recency: Array[String] = []

static func _initialize() -> void:
	if _initialized:
		return
	_initialized = true
	if FileAccess.file_exists(ROOT + "catalog.json"):
		var parsed: Variant = JSON.parse_string(FileAccess.get_file_as_string(ROOT + "catalog.json"))
		if parsed is Dictionary:
			_index = parsed

static func _resolve(id: String, group: String, aliases: Array) -> String:
	_initialize()
	var prefix := "card_" if group == "cards" else "equipment_"
	if id.begins_with(prefix):
		var suffix := id.trim_prefix(prefix)
		if suffix.is_valid_int():
			var index := suffix.to_int()
			if index >= 0 and index < aliases.size() and str(index) == suffix:
				id = aliases[index]
	var entries: Dictionary = _index.get(group, {})
	return _existing(str(entries.get(id, entries.get(id.to_upper(), ""))))

static func card_path(id: String) -> String:
	if id in ["FS1", "FS2", "FS3", "FS4"]:
		return _existing(ROOT + "art/cards/v0.1.5/F%s.webp" % id.trim_prefix("FS"))
	return _resolve(id, "cards", CARD_ALIASES)

static func equipment_path(id: String) -> String:
	var path := _resolve(id, "equipment", EQUIPMENT_ALIASES)
	if path.is_empty() and id.begins_with("recipe_"):
		return _existing(ROOT + "art/items/illustrated-v1/SCROLL.png")
	return path

static func hero_path(id: String, pose: String = "idle", portrait: bool = false) -> String:
	if id == "anti_mage":
		id = "antimage"
	if id in ["antimage", "axe", "faceless_void", "legion_commander"]:
		return _existing(ROOT + "art/heroes/v0.1.5/%s-%s.png" % [id, "portrait" if portrait else "idle"])
	if id not in ["juggernaut", "skywrath", "abaddon", "necrophos", "viper", "phantom_assassin"]:
		return ""
	if portrait or pose not in ["idle", "attack", "cast", "charge"]:
		pose = "idle"
	var stem := id.replace("_", "-")
	var path := _existing(ROOT + "art/heroes/%s-%s-v1.png" % [stem, pose])
	return path if not path.is_empty() else _existing(ROOT + "art/heroes/%s-idle-v1.png" % stem)

static func _existing(path: String) -> String:
	if not path.begins_with(ROOT) or ".." in path or not ResourceLoader.exists(path):
		return ""
	return path

static func hero_texture(hero_id: String) -> Texture2D:
	var path := hero_path(hero_id, "idle", true)
	if path.is_empty():
		path = hero_path(hero_id)
	if path.is_empty():
		return null
	if _hero_cache.has(path):
		_hero_recency.erase(path)
		_hero_recency.append(path)
		return _hero_cache[path] as Texture2D
	var source := load(path) as Texture2D
	if source == null:
		return null
	var image := source.get_image()
	if image == null or image.is_empty():
		return null
	var edge := maxi(image.get_width(), image.get_height())
	if edge > MAX_HERO_EDGE:
		var scale := float(MAX_HERO_EDGE) / float(edge)
		image.resize(maxi(1, roundi(image.get_width() * scale)), maxi(1, roundi(image.get_height() * scale)), Image.INTERPOLATE_LANCZOS)
	image.convert(Image.FORMAT_RGBA8)
	image.clear_mipmaps()
	var texture := ImageTexture.create_from_image(image)
	while _hero_cache.size() >= MAX_HERO_CACHE_ENTRIES:
		_hero_cache.erase(_hero_recency.pop_front())
	_hero_cache[path] = texture
	_hero_recency.append(path)
	return texture

static func clear_hero_cache() -> void:
	_hero_cache.clear()
	_hero_recency.clear()
