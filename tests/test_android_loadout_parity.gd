extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const LoadoutRules = preload("res://scripts/prepare/LoadoutRules.gd")

var _failed := false

func _init() -> void:
	var state = PreparationState.new("juggernaut")
	state.add_item(ItemInstance.new("blade", "phase_blade", ItemInstance.Kind.EQUIPMENT))
	state.add_item(ItemInstance.new("shield", "guardian_plate", ItemInstance.Kind.EQUIPMENT))
	state.add_item(ItemInstance.new("card_a", "opening_slash", ItemInstance.Kind.CARD))
	state.add_item(ItemInstance.new("card_b", "critical_rhythm", ItemInstance.Kind.CARD))

	_assert_equal(state.equipment_slots.size(), 9, "Android equipment slot count")
	_assert_equal(state.card_slots.size(), 8, "Android card slot count")
	_assert_equal(state.preset_count(), 3, "Android preset count")

	_assert_equal(LoadoutRules.place(state, "blade", ItemInstance.Kind.EQUIPMENT, 0).accepted, true, "place equipment")
	_assert_equal(LoadoutRules.place(state, "card_a", ItemInstance.Kind.CARD, 0).accepted, true, "place card")
	_assert_equal(LoadoutRules.save_preset(state, 0).accepted, true, "save at camp")

	_assert_equal(LoadoutRules.place(state, "shield", ItemInstance.Kind.EQUIPMENT, 0).accepted, true, "replace occupied equipment")
	_assert_equal(state.equipment_slots[0], "shield", "replacement takes target slot")
	_assert_equal(state.item("blade").location, ItemInstance.Location.WAREHOUSE, "replaced item returns warehouse")

	_assert_equal(LoadoutRules.apply_preset(state, 0).accepted, true, "apply preset")
	_assert_equal(state.equipment_slots[0], "blade", "preset restores saved instance")
	_assert_equal(state.card_slots[0], "card_a", "preset restores saved card")

	state.phase = PreparationState.Phase.JOURNEY
	_assert_equal(LoadoutRules.save_preset(state, 1).accepted, false, "save rejected outside camp")
	state.phase = PreparationState.Phase.HOME

	state.add_item(ItemInstance.new("axe", "battle_axe", ItemInstance.Kind.EQUIPMENT))
	_assert_equal(LoadoutRules.place(state, "axe", ItemInstance.Kind.EQUIPMENT, 1).accepted, true, "prepare second hero gear")
	_assert_equal(LoadoutRules.save_preset(state, 1).accepted, true, "save second juggernaut preset")
	_assert_equal(LoadoutRules.select_preset(state, 1).accepted, true, "select recent preset")
	state.unlocked_heroes.append("phantom_assassin")
	_assert_equal(LoadoutRules.activate_hero(state, "phantom_assassin").accepted, true, "activate unlocked other hero")
	_assert_equal(LoadoutRules.activate_hero(state, "juggernaut").accepted, true, "return to hero")
	_assert_equal(state.equipment_slots[1], "axe", "hero uses last selected preset")

	state.remove_item("axe")
	_assert_equal(LoadoutRules.apply_preset(state, 1).accepted, true, "missing instance does not block preset")
	_assert_equal(state.equipment_slots[1], "", "missing instance is pruned")

	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
