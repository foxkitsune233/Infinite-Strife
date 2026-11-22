extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const JourneyState = preload("res://scripts/journey/JourneyState.gd")
const JourneyRules = preload("res://scripts/journey/JourneyRules.gd")
const SaveCodec = preload("res://scripts/save/SaveCodec.gd")

var _failed := false

func _init() -> void:
	var preparation := PreparationState.new("juggernaut")
	preparation.gold = 735
	var item := ItemInstance.new("saved_item", "storm_lens", ItemInstance.Kind.EQUIPMENT)
	item.locked = true
	item.location = ItemInstance.Location.BACKPACK
	item.grid_position = Vector2i(1, 2)
	preparation.add_item(item)
	var journey := JourneyState.new(7351)
	JourneyRules.resolve_current(preparation, journey)
	var encoded := SaveCodec.encode(preparation, journey)
	var decoded := SaveCodec.decode(encoded)
	_assert_equal(decoded.accepted, true, "current schema decodes")
	var restored: PreparationState = decoded.preparation
	_assert_equal(restored.gold, 735, "gold survives round trip")
	_assert_equal(restored.item("saved_item").locked, true, "lock survives round trip")
	_assert_equal(restored.item("saved_item").grid_position, Vector2i(1, 2), "packing position survives round trip")
	_assert_equal((decoded.journey as JourneyState).loot.size() > 0, true, "ground loot survives round trip")
	var future := encoded.duplicate(true)
	future.schema_version = 99
	_assert_equal(SaveCodec.decode(future).accepted, false, "future schema is rejected")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
