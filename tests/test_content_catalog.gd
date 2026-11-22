extends SceneTree

const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")

var _failed := false

func _init() -> void:
	_assert_equal(ContentCatalog.heroes().size(), 10, "original hero count")
	_assert_equal(ContentCatalog.schools().size(), 8, "build school count")
	_assert_equal(ContentCatalog.cards().size(), 45, "card pool count")
	_assert_equal(ContentCatalog.items().size() > 150, true, "Android inventory and legacy recipe catalog is available")
	_assert_equal(ContentCatalog.item("broadsword").display_name, "阔剑", "Android equipment names are used")
	_assert_equal(ContentCatalog.item("black_king_bar").recipe.size(), 3, "Android recipe is connected to crafting")
	_assert_equal(ContentCatalog.events().size(), 16, "event catalog count")
	_assert_unique_ids(ContentCatalog.heroes(), "heroes")
	_assert_unique_ids(ContentCatalog.cards(), "cards")
	_assert_unique_ids(ContentCatalog.items(), "items")
	_assert_unique_ids(ContentCatalog.events(), "events")
	_assert_equal(ContentCatalog.cards_for_school("rime").size() > 0, true, "rime school has cards")
	_assert_equal(ContentCatalog.event("moonwell_echo").options.size(), 3, "event has three choices")
	_assert_equal(ContentCatalog.item("storm_lens").recipe.size() > 0, true, "crafted item has recipe")
	quit(1 if _failed else 0)

func _assert_unique_ids(definitions: Array, label: String) -> void:
	var seen := {}
	for definition in definitions:
		if seen.has(definition.id):
			_failed = true
			push_error("duplicate %s id: %s" % [label, definition.id])
		seen[definition.id] = true

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
