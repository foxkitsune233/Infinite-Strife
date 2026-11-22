extends SceneTree
const Catalog = preload("res://scripts/content/ContentCatalog.gd")
const Session = preload("res://scripts/AppSession.gd")
func _init() -> void:
	var session := Session.new()
	var state := session.ensure_preparation_state()
	for id in ["sun_thread", "rime_shard", "storm_core"]:
		assert(Catalog.item(id) == null)
		assert(not state.items.values().any(func(item: ItemInstance) -> bool: return item.definition_id == id))
	for entry in CraftingRules.entries(state):
		for ingredient in Catalog.item(entry.definition_id).recipe:
			assert(Catalog.item(ingredient) != null)
	var retired := ItemInstance.new("old-material", "sun_thread", ItemInstance.Kind.EQUIPMENT)
	state.add_item(retired)
	var saved := SaveCodec.encode(state, JourneyState.new(42))
	var loaded := SaveCodec.decode(saved)
	assert(loaded.accepted and loaded.preparation.item(retired.id) == null)
	session.free()
	print("Retired materials excluded from starting inventory, recipes and restored saves")
	quit()
