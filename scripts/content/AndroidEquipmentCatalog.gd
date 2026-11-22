extends RefCounted
## Standalone source-data catalog. Effects are metadata, not combat implementations.

const DEFAULT_PATH := "res://data/android/equipment.json"
const STAT_KEYS := ["strength", "agility", "intelligence", "health", "healthRegen", "armor", "attackSpeed", "manaPercent", "manaRegen", "attack"]

## Dictionary lookup: catalog.definitions.get(android_id, {}). Treat as read-only.
static var definitions: Dictionary = {}
static var errors := PackedStringArray()
static var _recipes: Dictionary = {}
static var _document: Dictionary = {}
static var _attempted := false

static func load_catalog(path: String = DEFAULT_PATH) -> Error:
	_attempted = true
	definitions.clear()
	_recipes.clear()
	_document.clear()
	errors.clear()
	if not FileAccess.file_exists(path):
		errors.append("Catalog file does not exist: %s" % path)
		return ERR_FILE_NOT_FOUND
	var parser := JSON.new()
	if parser.parse(FileAccess.get_file_as_string(path)) != OK:
		errors.append("Invalid catalog JSON at line %s: %s" % [parser.get_error_line(), parser.get_error_message()])
		return ERR_PARSE_ERROR
	errors = validate_document(parser.data)
	if not errors.is_empty():
		return ERR_INVALID_DATA
	_document = parser.data
	for definition in _document.items:
		var row: Dictionary = definition.duplicate(true)
		for source_key in ["boundOnAcquire", "minimumDropClears", "minimumShopClears", "maxStack", "combatUsable", "shopStock", "shopShelf", "uniqueGroup"]:
			row[source_key.to_snake_case()] = row.get(source_key)
		definitions[definition.id] = row
	for entry in _document.recipes:
		_recipes[entry.id] = entry.duplicate(true)
	return OK

static func _ensure_loaded() -> void:
	if not _attempted:
		load_catalog()

static func items() -> Array:
	_ensure_loaded()
	return definitions.values().duplicate(true)

static func item(id: String) -> Dictionary:
	_ensure_loaded()
	return definitions.get(id, {}).duplicate(true)

static func recipes() -> Array:
	_ensure_loaded()
	return _recipes.values().duplicate(true)

static func recipe(id: String) -> Dictionary:
	_ensure_loaded()
	return _recipes.get(id, {}).duplicate(true)

static func cards() -> Array:
	_ensure_loaded()
	return _document.get("cards", []).duplicate(true)

static func metadata() -> Dictionary:
	_ensure_loaded()
	var result := _document.duplicate(true)
	for key in ["items", "recipes", "cards", "synergy_tiers"]:
		result.erase(key)
	return result

static func _number(value: Variant) -> bool:
	return (value is int or value is float) and is_finite(float(value))

static func _integer(value: Variant, minimum: int, maximum: int) -> bool:
	return _number(value) and value >= minimum and value <= maximum and floor(float(value)) == float(value)

static func validate_document(document: Variant) -> PackedStringArray:
	var issues := PackedStringArray()
	if not document is Dictionary:
		return PackedStringArray(["Catalog root must be an object"])
	if document.get("schema_version") != 1 or not document.get("items") is Array or not document.get("recipes") is Array:
		return PackedStringArray(["Unsupported schema or missing items/recipes arrays"])
	if document.get("stats_keys") != STAT_KEYS:
		issues.append("Unexpected stats_keys contract")
	var ids := {}
	for d in document.items:
		if not d is Dictionary or not d.get("id") is String or d.id.is_empty():
			issues.append("Item requires a nonempty string ID")
			continue
		if ids.has(d.id):
			issues.append("Duplicate item ID: %s" % d.id)
		ids[d.id] = d
		if not d.get("name") is String or d.name.is_empty() or d.get("kind") not in ["EQUIPMENT", "CARD", "MATERIAL", "SCROLL", "CONSUMABLE", "LOOT"]:
			issues.append("Invalid item name/kind: %s" % d.id)
		if not _integer(d.get("width"), 1, 6) or not _integer(d.get("height"), 1, 6):
			issues.append("Invalid dimensions: %s" % d.id)
		if not d.get("stats") is Dictionary or d.stats.size() != STAT_KEYS.size():
			issues.append("Incomplete stats: %s" % d.id)
		else:
			for key in STAT_KEYS:
				if not _number(d.stats.get(key)):
					issues.append("Invalid stat %s/%s" % [d.id, key])
		if not d.get("effects") is Array or not d.get("active_effects") is Array:
			issues.append("Missing effect arrays: %s" % d.id)
		else:
			var effect_ids := {}
			var active: Array = []
			for effect in d.effects:
				if not effect is Dictionary or not effect.get("id") is String:
					issues.append("Invalid effect: %s" % d.id)
					continue
				if effect_ids.has(effect.id):
					issues.append("Duplicate effect ID: %s/%s" % [d.id, effect.id])
				effect_ids[effect.id] = true
				if effect.get("trigger") == "ACTIVE":
					active.append(effect)
			if active != d.active_effects:
				issues.append("Active metadata differs from effects: %s" % d.id)
	var recipe_ids := {}
	var outputs := {}
	var graph := {}
	for r in document.recipes:
		if not r is Dictionary or not r.get("id") is String or r.id.is_empty() or not r.get("outputId") is String or not r.get("ingredients") is Dictionary:
			issues.append("Malformed recipe")
			continue
		if recipe_ids.has(r.id) or outputs.has(r.outputId):
			issues.append("Duplicate recipe ID/output: %s" % r.id)
		recipe_ids[r.id] = r
		outputs[r.outputId] = true
		if not ids.has(r.outputId) or ids[r.outputId].get("kind") != "EQUIPMENT":
			issues.append("Unknown/non-equipment recipe output: %s" % r.outputId)
		if r.ingredients.is_empty():
			issues.append("Empty recipe: %s" % r.id)
		graph[r.outputId] = r.ingredients.keys()
		for ingredient in r.ingredients:
			if not ids.has(ingredient) or not _integer(r.ingredients[ingredient], 1, 20):
				issues.append("Invalid ingredient: %s/%s" % [r.id, ingredient])
	for id in ids:
		var embedded: Variant = ids[id].get("recipe")
		if embedded != null and (not embedded is Dictionary or recipe_ids.get(embedded.get("id")) != embedded or embedded.get("outputId") != id):
			issues.append("Embedded recipe mismatch: %s" % id)
	for output in outputs:
		if ids.has(output) and ids[output].get("recipe") == null:
			issues.append("Missing embedded recipe: %s" % output)
	# Remove leaves; any remaining output depends on a recipe cycle.
	while not graph.is_empty():
		var leaves: Array = []
		for output in graph:
			var blocked := false
			for ingredient in graph[output]:
				if graph.has(ingredient):
					blocked = true
			if not blocked:
				leaves.append(output)
		if leaves.is_empty():
			issues.append("Recipe dependency cycle")
			break
		for output in leaves:
			graph.erase(output)
	return issues
