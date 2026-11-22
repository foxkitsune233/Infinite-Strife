extends SceneTree

const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const ShopState = preload("res://scripts/prepare/ShopState.gd")
const ShopRules = preload("res://scripts/prepare/ShopRules.gd")

var _failed := false

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	state.gold = 200
	var shop := ShopState.new()
	ShopRules.recover(state, shop, 1000)
	var old_revision := shop.revision
	_assert_equal(ShopRules.buy(state, shop, "iron_branch", old_revision, 1000).accepted, true, "buy with current quote")
	_assert_equal(state.item("shop_iron_branch_1").definition_id, "iron_branch", "purchase enters warehouse")
	ShopRules.refresh_shop(state, shop, 1000)
	_assert_equal(ShopRules.buy(state, shop, str(shop.offers[0].id), old_revision).accepted, false, "stale quote rejected")
	_assert_equal(ShopRules.buy(state, shop, str(shop.offers[0].id), old_revision).revision, shop.revision, "rejection exposes current revision")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
