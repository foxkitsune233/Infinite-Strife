extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const LoadoutRules = preload("res://scripts/prepare/LoadoutRules.gd")
const PackingRules = preload("res://scripts/prepare/PackingRules.gd")

var _failed := false

func _init() -> void:
	var state = PreparationState.new("juggernaut")
	state.backpack_size = Vector2i(4, 3)
	state.add_item(_item("wide", Vector2i(2, 1), 90))
	state.add_item(_item("tall", Vector2i(1, 2), 140))
	state.add_item(_item("blocker", Vector2i(1, 1), 0))
	state.add_item(_item("gem", Vector2i(1, 1), 50))

	_assert_equal(PackingRules.place(state, "wide", ItemInstance.Location.BACKPACK, Vector2i(0, 0), false).accepted, true, "place wide item")
	_assert_equal(PackingRules.place(state, "tall", ItemInstance.Location.BACKPACK, Vector2i(1, 0), false).accepted, false, "overlap rejected")
	_assert_equal(PackingRules.place(state, "tall", ItemInstance.Location.BACKPACK, Vector2i(2, 0), false).accepted, true, "place tall item")
	_assert_equal(PackingRules.place(state, "blocker", ItemInstance.Location.BACKPACK, Vector2i(0, 1), false).accepted, true, "place rotation blocker")
	_assert_equal(PackingRules.rotate(state, "wide").accepted, false, "rotation rejects occupied cells")
	state.add_item(_item("slotted", Vector2i(1, 1), 20))
	_assert_equal(LoadoutRules.place(state, "slotted", ItemInstance.Kind.EQUIPMENT, 2).accepted, true, "place equipment before moving to backpack")
	_assert_equal(PackingRules.place(state, "slotted", ItemInstance.Location.BACKPACK, Vector2i(3, 2), false).accepted, true, "move slotted equipment into backpack")
	_assert_equal(state.equipment_slots[2], "", "moving to backpack clears prior equipment slot")

	_assert_equal(PackingRules.toggle_lock(state, "gem").accepted, true, "lock item")
	_assert_equal(PackingRules.quick_sell(state, "gem").accepted, false, "locked item cannot quick sell")
	_assert_equal(PackingRules.toggle_lock(state, "gem").accepted, true, "unlock item")
	_assert_equal(PackingRules.quick_sell(state, "gem").accepted, true, "quick sell unlocked warehouse item")
	_assert_equal(state.gold, 1220, "quick sell returns 40 percent of base value")
	state.add_item(_item("batch_a", Vector2i(1, 1), 30))
	state.add_item(_item("batch_b", Vector2i(1, 1), 70))
	state.add_item(_item("batch_locked", Vector2i(1, 1), 120))
	PackingRules.toggle_lock(state, "batch_locked")
	var batch_result := PackingRules.batch_sell_warehouse(state)
	_assert_equal(batch_result.accepted, true, "batch sell accepts unlocked warehouse items")
	_assert_equal(int(batch_result.sold), 2, "batch sell keeps locked stock out of the sale")
	_assert_equal(state.gold, 1260, "batch sell returns 40 percent of every unlocked value")
	_assert_equal(state.item("batch_locked") != null, true, "batch sell preserves locked stock")

	state.remove_item("wide")
	state.remove_item("tall")
	state.remove_item("blocker")
	state.remove_item("slotted")
	state.add_item(_item("large", Vector2i(2, 2), 200))
	state.add_item(_item("small", Vector2i(1, 1), 10))
	_assert_equal(PackingRules.place(state, "small", ItemInstance.Location.BACKPACK, Vector2i(0, 0), false).accepted, true, "place small item")
	_assert_equal(PackingRules.place(state, "large", ItemInstance.Location.BACKPACK, Vector2i(2, 1), false).accepted, true, "place large item")
	_assert_equal(PackingRules.organize(state, ItemInstance.Location.BACKPACK).accepted, true, "organize backpack")
	_assert_equal(state.item("large").grid_position, Vector2i(0, 0), "organize places larger item first")

	# Android 出售规则：40% 基础价，升星卡按 3/9 份成本。
	var card1 := _item("card1", Vector2i(1, 2), 100)
	card1.kind = ItemInstance.Kind.CARD
	var card2 := _item("card2", Vector2i(1, 2), 100)
	card2.kind = ItemInstance.Kind.CARD
	card2.star = 2
	var card3 := _item("card3", Vector2i(1, 2), 100)
	card3.kind = ItemInstance.Kind.CARD
	card3.star = 3
	_assert_equal(PackingRules.sale_value(card1), 40, "one-star card sells at 40 percent")
	_assert_equal(PackingRules.sale_value(card2), 120, "two-star card sells at 3x copies")
	_assert_equal(PackingRules.sale_value(card3), 360, "three-star card sells at 9x copies")

	quit(1 if _failed else 0)

func _item(id: String, size: Vector2i, value: int) -> ItemInstance:
	var item := ItemInstance.new(id, id, ItemInstance.Kind.EQUIPMENT)
	item.footprint = size
	item.value = value
	return item

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
