class_name BattleActorView
extends TextureRect

const Art = preload("res://scripts/ui/AndroidArtCatalog.gd")
const HERO_ROOT := "res://assets/android/art/heroes/"
const FRAME_EDGE := 256
const MAX_CACHE_ENTRIES := 64
const ATTACK_SECONDS := 0.42
const CAST_SECONDS := 0.55
static var _metadata: Dictionary = {}
static var _metadata_loaded := false
static var _textures: Dictionary = {}
static var _lru: Array[String] = []
static var _invalid_frames: Dictionary = {}

var actor_id := "":
	set(value):
		if actor_id == value:
			return
		actor_id = value
		_motion = "idle"
		_age = 0.0
		_swing = 0
		_alternate = false
		if _tint_tween != null:
			_tint_tween.kill()
		modulate = Color.WHITE
		scale = Vector2.ONE
		if is_inside_tree():
			_show_frame()

var _motion := "idle"
var _age := 0.0
var _swing := 0
var _alternate := false
var _tint_tween: Tween
var _owned_texture: Texture2D
var _presentation_offset := 0.0
var attack_interval_ms := 1000
var _attack_seconds := ATTACK_SECONDS

static func attack_duration_seconds(interval_ms: int) -> float:
	return clampf(interval_ms * 0.0009, 0.18, ATTACK_SECONDS)

static func _json(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return {}
	var parsed: Variant = JSON.parse_string(FileAccess.get_file_as_string(path))
	return parsed if parsed is Dictionary else {}

static func _entries() -> Dictionary:
	if not _metadata_loaded:
		_metadata_loaded = true
		var base := _json(HERO_ROOT + "attack-atlas-v3.json")
		if int(base.get("version", 0)) == 3 and base.get("heroes") is Dictionary:
			_metadata = base.heroes.duplicate(true)
		var expansion := _json(HERO_ROOT + "v0.1.5/attack-manifest.json")
		if int(expansion.get("version", 0)) == 3 and expansion.get("heroes") is Dictionary:
			for id in expansion.heroes:
				var entry: Variant = expansion.heroes[id]
				if entry is Dictionary and entry.get("runtimeReady", false) == true:
					_metadata[id] = entry
	return _metadata

static func _safe_file(file: String, suffix: String) -> bool:
	if not file.ends_with(suffix):
		return false
	var stem := file.trim_suffix(suffix)
	if stem.is_empty():
		return false
	for character in stem:
		if character not in "abcdefghijklmnopqrstuvwxyz-":
			return false
	return true

static func valid_entry(entry: Dictionary) -> bool:
	for key in ["frameWidth", "frameHeight", "rootX", "rootY", "bodyHeight", "contentWidth", "attackOriginX", "attackOriginY"]:
		if not (entry.get(key) is float or entry.get(key) is int):
			return false
		if not is_finite(float(entry[key])):
			return false
	return float(entry.frameWidth) == 512.0 and float(entry.frameHeight) == 512.0 \
		and float(entry.rootX) >= 0 and float(entry.rootX) <= 512 \
		and float(entry.rootY) > 0 and float(entry.rootY) <= 512 \
		and float(entry.bodyHeight) > 0 and float(entry.bodyHeight) <= 512 \
		and float(entry.contentWidth) > 0 and float(entry.contentWidth) <= 512 \
		and float(entry.attackOriginX) >= 0 and float(entry.attackOriginX) <= 512 \
		and float(entry.attackOriginY) >= 0 and float(entry.attackOriginY) <= 512 \
		and _safe_file(str(entry.get("file", "")), "-attack-atlas-v3.png") \
		and (not entry.has("alternateFile") or _safe_file(str(entry.alternateFile), "-offhand-atlas-v4.png"))

## Validated source selection; atlas dimensions are checked again against decoded pixels.
static func frame_spec(hero_id: String, pose: String = "idle", frame: int = 0, alternate: bool = false) -> Dictionary:
	if frame < 0 or frame > 3 or pose not in ["idle", "attack", "cast"]:
		return {}
	var id := "antimage" if hero_id == "anti_mage" else hero_id
	var value: Variant = _entries().get(id, {})
	if not value is Dictionary or not valid_entry(value):
		return {}
	var entry: Dictionary = value
	var path := HERO_ROOT + "atlases/" + str(entry.file)
	var cell := 0 if pose == "idle" else frame
	var rows := 2
	var standalone := false
	if pose == "cast":
		if id in ["abaddon", "skywrath"]:
			path = Art.hero_path(id, "cast")
			standalone = true
		elif id == "viper" and frame < 2:
			path = Art.hero_path(id, "charge")
			standalone = true
		elif id == "viper":
			cell = 2
	elif pose == "attack" and alternate and cell in [1, 2] and entry.has("alternateFile"):
		path = HERO_ROOT + "atlases/" + str(entry.alternateFile)
		cell -= 1
		rows = 1
	if path.is_empty() or not ResourceLoader.exists(path):
		return {}
	return {"path": path, "region": Rect2i((cell % 2) * 512, (cell / 2) * 512, 512, 512),
		"root": Vector2(float(entry.rootX), float(entry.rootY)), "body_height": float(entry.bodyHeight),
		"standalone": standalone, "rows": rows,
		"body_fraction": 0.69 if id == "abaddon" else (0.86 if id == "skywrath" else 0.94)}

static func texture_for_frame(hero_id: String, pose: String = "idle", frame: int = 0, alternate: bool = false) -> Texture2D:
	var spec := frame_spec(hero_id, pose, frame, alternate)
	if spec.is_empty():
		return Art.hero_texture(hero_id)
	var key := "%s:%s" % [spec.path, str(spec.region) if not spec.standalone else "pose"]
	if _invalid_frames.has(key):
		return Art.hero_texture(hero_id)
	if _textures.has(key):
		_lru.erase(key)
		_lru.append(key)
		return _textures[key] as Texture2D
	var texture := load(spec.path) as Texture2D
	var source := texture.get_image() if texture != null else null
	if source == null or source.is_empty():
		_reject_frame(key)
		return Art.hero_texture(hero_id)
	source.convert(Image.FORMAT_RGBA8)
	source.clear_mipmaps()
	var image: Image
	if spec.standalone:
		image = Image.create(FRAME_EDGE, FRAME_EDGE, false, Image.FORMAT_RGBA8)
		image.fill(Color.TRANSPARENT)
		var root: Vector2 = spec.root * 0.5
		var fit := minf(float(FRAME_EDGE) / source.get_width(), root.y / source.get_height())
		fit = minf(fit, float(spec.body_height) * 0.5 / (source.get_height() * float(spec.body_fraction)))
		source.resize(maxi(1, roundi(source.get_width() * fit)), maxi(1, roundi(source.get_height() * fit)), Image.INTERPOLATE_LANCZOS)
		image.blit_rect(source, Rect2i(Vector2i.ZERO, source.get_size()), Vector2i(roundi(root.x - source.get_width() * 0.5), roundi(root.y - source.get_height())))
	else:
		if source.get_size() != Vector2i(1024, 512 * int(spec.rows)):
			_reject_frame(key)
			return Art.hero_texture(hero_id)
		image = source.get_region(spec.region)
		image.resize(FRAME_EDGE, FRAME_EDGE, Image.INTERPOLATE_LANCZOS)
	return _remember(key, ImageTexture.create_from_image(image))

static func _reject_frame(key: String) -> void:
	if _invalid_frames.size() >= MAX_CACHE_ENTRIES:
		_invalid_frames.erase(_invalid_frames.keys()[0])
	_invalid_frames[key] = true

static func _remember(key: String, image: Texture2D) -> Texture2D:
	_lru.erase(key)
	while _textures.size() >= MAX_CACHE_ENTRIES and not _textures.has(key):
		_textures.erase(_lru.pop_front())
	_textures[key] = image
	_lru.append(key)
	return image

func _ready() -> void:
	expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_show_frame()

func _process(delta: float) -> void:
	if _motion != "idle":
		_age += maxf(0, delta)
		if _age >= (CAST_SECONDS if _motion == "cast" else _attack_seconds):
			_motion = "idle"
	_show_frame()

func _show_frame() -> void:
	var duration := CAST_SECONDS if _motion == "cast" else _attack_seconds
	var fraction := _age / duration
	var frame := 0 if fraction < 0.14 else (1 if fraction < 0.34 else (2 if fraction < 0.68 else 3))
	var spec := frame_spec(actor_id, _motion, frame, _alternate)
	var selected: Texture2D = texture_for_frame(actor_id, _motion, frame, _alternate)
	if selected != null:
		texture = selected
		_owned_texture = selected
	elif texture == _owned_texture:
		texture = null
		_owned_texture = null
	# Unknown demo actors keep their caller-supplied texture.
	var root := Vector2(0.5, 1.0) if spec.is_empty() else Vector2(spec.root) / 512.0
	var edge := minf(size.x, size.y)
	pivot_offset = (size - Vector2.ONE * edge) * 0.5 + root * edge

static func motion_for(event: Dictionary, id: String) -> String:
	if str(event.get("source_id", "")) == id:
		match str(event.get("kind", "")):
			"skill_cast":
				return "cast"
			"physical_attack", "attack_evaded", "attack_blocked":
				return "attack"
	if str(event.get("target_id", "")) == id and int(event.get("amount", 0)) > 0:
		return "hit"
	return "none"

func play_event(event: Dictionary) -> void:
	# 关闭动画时保留静态角色与状态，不播放姿态/受击染色（对齐 Android animations 开关）。
	if not preload("res://scripts/app/AppSettings.gd").get_flag("animations"):
		return
	var motion := motion_for(event, actor_id)
	if motion == "none":
		return
	if motion in ["attack", "cast"]:
		if motion == "attack" and _motion != "idle":
			return
		_motion = motion
		_age = 0.0
		if motion == "attack":
			_attack_seconds = attack_duration_seconds(attack_interval_ms)
			_alternate = _swing % 2 == 1
			_swing += 1
		_show_frame()
	# Damage feedback overlays the current pose rather than restarting its animation.
	if is_inside_tree() and motion in ["hit", "cast"]:
		if _tint_tween != null:
			_tint_tween.kill()
		modulate = Color.WHITE
		_tint_tween = create_tween()
		var tint := Color(0.58, 0.9, 1.0, 1.0) if motion == "cast" else Color(1.0, 0.74, 0.74, 1.0)
		_tint_tween.tween_property(self, "modulate", tint, 0.06)
		_tint_tween.tween_property(self, "modulate", Color.WHITE, 0.22)

func apply_presentation(state: Dictionary) -> void:
	var flash := float(state.get("flash", 0.0))
	var knockback := float(state.get("knockback", 0.0))
	position.x -= _presentation_offset
	_presentation_offset = knockback
	position.x += _presentation_offset
	if flash > 0.0:
		modulate = Color(1.0, 0.72 + flash, 0.72 + flash, 1.0)
	elif _tint_tween == null or not _tint_tween.is_running():
		modulate = Color.WHITE
