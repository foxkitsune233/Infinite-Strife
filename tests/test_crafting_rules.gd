extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const CraftingRules = preload("res://scripts/prepare/CraftingRules.gd")

var _failed := false

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	state.add_item(_component("branch_a", "iron_branch"))
	state.add_item(_component("ore_a", "ember_ore"))
	var entries := CraftingRules.entries(state)
	_assert_equal(entries[0].craftable, true, "craftable recipes sort first")
	_assert_equal(_entry(entries, "ember_maul").craftable, true, "recipe craftable")
	_assert_equal(CraftingRules.craft(state, "ember_maul").accepted, true, "craft consumes warehouse components")
	_assert_equal(state.item("branch_a"), null, "first component consumed")
	_assert_equal(state.item("ore_a"), null, "second component consumed")
	_assert_equal(state.item("craft_ember_maul_1").definition_id, "ember_maul", "crafted item created")

	var locked := _component("branch_locked", "iron_branch")
	locked.locked = true
	state.add_item(locked)
	state.add_item(_component("resin_a", "thorn_resin"))
	_assert_equal(CraftingRules.craft(state, "briar_mail").accepted, false, "locked material blocks craft")
	_assert_equal(CraftingRules.craft(state, "briar_mail").reason, "材料已锁定", "locked material reason")
	quit(1 if _failed else 0)

func _component(id: String, definition_id: String) -> ItemInstance:
	return ItemInstance.new(id, definition_id, ItemInstance.Kind.EQUIPMENT)

func _entry(entries: Array[Dictionary], definition_id: String) -> Dictionary:
	for entry in entries:
		if entry.definition_id == definition_id:
			return entry
	return {}

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
