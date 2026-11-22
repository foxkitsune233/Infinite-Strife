extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const LoadoutRules = preload("res://scripts/prepare/LoadoutRules.gd")
const BattleLoadoutBuilder = preload("res://scripts/battle/BattleLoadoutBuilder.gd")

var _failed := false

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	var item := ItemInstance.new("lens", "storm_lens", ItemInstance.Kind.EQUIPMENT)
	state.add_item(item)
	LoadoutRules.place(state, item.id, ItemInstance.Kind.EQUIPMENT, 0)
	var card := ItemInstance.new("assault", "assault_pulse_3", ItemInstance.Kind.CARD)
	state.add_item(card)
	LoadoutRules.place(state, card.id, ItemInstance.Kind.CARD, 0)
	var built := BattleLoadoutBuilder.build(state)
	_assert_equal(built.fighter.id, "juggernaut", "active hero drives battle fighter")
	_assert_true(built.fighter.attack_damage > 78, "equipped item and card raise attack")
	_assert_equal(str(built.skill.id), "omnislash", "juggernaut exposes the intended skill")
	_assert_true((built.active_items as Array).is_empty(), "passive crafted gear does not create an active button")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])

func _assert_true(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)
