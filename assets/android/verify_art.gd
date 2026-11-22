extends SceneTree
## Run headless with --script res://assets/android/verify_art.gd; no editor import needed.
const Catalog = preload("res://scripts/ui/AndroidArtCatalog.gd")
const Factory = preload("res://scripts/ui/ItemIconFactory.gd")
const Item = preload("res://scripts/prepare/ItemInstance.gd")
var failures := 0

func check(ok: bool, message: String) -> void:
	if not ok:
		failures += 1
		push_error(message)

func _init() -> void:
	var index: Dictionary = JSON.parse_string(FileAccess.get_file_as_string("res://assets/android/catalog.json"))
	var mapped := 0
	for group in ["cards", "equipment"]:
		for id in index[group]:
			var path: String = Catalog.card_path(id) if group == "cards" else Catalog.equipment_path(id)
			check(not path.is_empty() and FileAccess.file_exists(path), "Missing mapping: " + id)
			mapped += 1
	for i in 9:
		check(not Catalog.equipment_path("equipment_%d" % i).is_empty(), "Equipment alias %d" % i)
	for i in 8:
		check(not Catalog.card_path("card_%d" % i).is_empty(), "Card alias %d" % i)
	check(Catalog.card_path("D7") == Catalog.card_path("dual_01_blood_combo"), "Expansion art key alias")
	check(Catalog.equipment_path("../../secrets").is_empty(), "Traversal rejected")
	check(Catalog.card_path("card_-1").is_empty(), "Negative alias rejected")
	check(Catalog.card_path("card_999").is_empty(), "Unknown alias rejected")
	var decoded := 0
	for group in ["cards", "equipment"]:
		var kind: Item.Kind = Item.Kind.CARD if group == "cards" else Item.Kind.EQUIPMENT
		for id in index[group]:
			var texture: Texture2D = Factory.icon_for(id, kind)
			check(texture != null and maxi(texture.get_width(), texture.get_height()) <= 160, "Icon bounds: " + id)
			decoded += 1
	check(Factory.icon_for("card_0", Item.Kind.CARD) == Factory.icon_for("A1", Item.Kind.CARD), "Alias texture shared")
	for i in 300:
		var fallback: Texture2D = Factory.icon_for("missing_%d" % i, Item.Kind.EQUIPMENT)
		check(fallback.get_size() == Vector2(72, 72), "Generated fallback preserved")
	check(Factory._cache.size() == 256 and Factory._recency.size() == 256, "LRU bounded")
	for id in ["juggernaut", "phantom_assassin", "skywrath", "abaddon", "necrophos", "viper", "anti_mage", "antimage", "axe", "faceless_void", "legion_commander"]:
		for pose in ["idle", "attack", "cast", "charge", "unknown"]:
			check(not Catalog.hero_path(id, pose).is_empty(), "Hero pose fallback: " + id)
		var hero: Texture2D = Catalog.hero_texture(id)
		check(hero != null and maxi(hero.get_width(), hero.get_height()) <= 512, "Hero texture: " + id)
	check(Catalog.hero_texture("anti_mage") == Catalog.hero_texture("antimage"), "Hero alias shared")
	check(Catalog.hero_texture("unknown") == null, "Unknown hero returns null")
	Factory.clear_cache()
	Catalog.clear_hero_cache()
	check(Factory._cache.is_empty() and Catalog._hero_cache.is_empty(), "Caches clear")
	print("Android art verification: mapped=%d decoded=%d failures=%d" % [mapped, decoded, failures])
	quit(0 if failures == 0 else 1)
