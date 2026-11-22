class_name AndroidContent
extends RefCounted

static var _catalog: RefCounted

static func catalog() -> RefCounted:
	if _catalog == null:
		_catalog = preload("res://scripts/content/AndroidEquipmentCatalog.gd").new()
		var error: int = _catalog.load_catalog()
		if error != OK:
			push_error("Android content load failed: %s" % str(_catalog.errors))
	return _catalog

static func item(id: String) -> Dictionary:
	return catalog().item(id)

static func cards() -> Array:
	return catalog().cards()

static func item_kind(record: Dictionary) -> ItemInstance.Kind:
	match str(record.get("kind", "EQUIPMENT")):
		"CARD": return ItemInstance.Kind.CARD
		"CONSUMABLE": return ItemInstance.Kind.CONSUMABLE
		"MATERIAL", "SCROLL", "LOOT": return ItemInstance.Kind.MATERIAL
		_: return ItemInstance.Kind.EQUIPMENT

static func create_instance(id: String, definition_id: String) -> ItemInstance:
	var definition := item(definition_id)
	if definition.is_empty():
		return null
	var result := ItemInstance.new(id, definition_id, item_kind(definition))
	result.footprint = Vector2i(int(definition.width), int(definition.height))
	result.value = int(definition.price)
	result.bound = bool(definition.get("boundOnAcquire", false))
	return result
