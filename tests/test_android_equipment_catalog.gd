extends SceneTree

var _failed := false

func _init() -> void:
	# Missing export, dropped fields, bad references, and changed source values must fail.
	var path := "res://scripts/content/AndroidEquipmentCatalog.gd"
	if not FileAccess.file_exists(path):
		_check(false, "standalone Android catalog exists")
		quit(1)
		return
	var catalog = load(path)
	_check(catalog.item("essence").kind == "MATERIAL", "static item lazily loads reward catalog")
	_check(catalog.load_catalog() == OK, "export loads: %s" % catalog.errors)
	if not catalog.errors.is_empty():
		quit(1)
		return
	_check(catalog.items().size() > 100, "full catalog, including legacy inventory equipment")
	_check(catalog.recipes().size() == 53, "Android 1.0: 50 Kotlin plus 3 inventory recipes")
	_check(catalog.recipe("silver_edge").ingredients.get("phantom_cloak", 0) == 1, "Silver Edge uses the evasion component")
	_check(catalog.item("crown").name == "王冠", "source Chinese name")
	_check(catalog.item("crown").stats.strength == 4.0, "crown raw strength")
	_check(catalog.item("broadsword").stats.attack == 15.0, "legacy inventory attack")
	_check(catalog.item("broadsword").width == 2, "legacy inventory dimensions")
	_check(catalog.item("bloodrage_gauntlet").stats.attackSpeed == 18.0, "school attack speed")
	_check(catalog.item("frenzy_heart").stats.health == 300.0, "school raw health")
	_check(catalog.item("orchid").stats.manaRegen == 1.0, "control mana regen")
	_check(catalog.item("orchid").active_effects[0].internalCooldownMs == 12000, "active cooldown metadata")
	_check(catalog.item("glacier_plate").active_effects[0].params.control.value == "FREEZE", "typed active control")
	_check(catalog.item("glacier_plate").active_effects[0].params.rangedDurationMs.value == 1200, "ranged duration")
	_check(catalog.item("cloak").effects[0].params.percent.value == 0.15, "separate component effects retained")
	_check(catalog.recipe("bracer").ingredients.recipe_bracer == 1, "legacy recipe scroll")
	_check(catalog.recipe("glacier_plate").ingredients.vitality_booster == 1, "school recipe reference")
	for id in ["essence", "safe_lock", "safe_frame", "backpack_fabric", "skill_shard", "equipment_guard_core", "skill_guard_core", "salve", "recipe_bracer"]:
		_check(not catalog.definitions.get(id, {}).is_empty(), "reward inventory lookup: %s" % id)
	_check(catalog.item("salve").maxStack == 3, "consumable stack limit")
	_check(catalog.item("safe_frame").minimumDropClears == 3, "material drop gate")
	_check(catalog.item("backpack_fabric").boundOnAcquire, "material binding flag")
	_check(catalog.item("backpack_fabric").bound_on_acquire, "Godot reward binding alias")
	_check(catalog.item("loot_royal_screen").width == 4, "valuable loot inventory definition")
	_check(catalog.cards().size() == 45, "enabled source cards preserved")
	var card_by_id := {}
	for card in catalog.cards():
		_check(not card_by_id.has(card.id), "unique card ID")
		card_by_id[card.id] = card
		_check(card.stars.size() == 3, "all stars exported: %s" % card.id)
		_check(not card.description.is_empty(), "card description: %s" % card.id)
	_check(card_by_id.A1.schools == ["basic"], "source school ID")
	_check(card_by_id.A1.stars[2].effects[0].params.attackSpeed.value == 48.0, "three-star resolved attack speed")
	_check(card_by_id.A1.stars[2].effects[0].params.attackSpeed.type == "Num", "typed star value")
	_check(catalog.item("missing").is_empty(), "unknown item")
	var copy: Dictionary = catalog.item("crown")
	copy.stats.strength = -99
	_check(catalog.item("crown").stats.strength == 4.0, "returned definitions are isolated")
	var document: Dictionary = JSON.parse_string(FileAccess.get_file_as_string(catalog.DEFAULT_PATH))
	_check(catalog.validate_document(document).is_empty(), "all IDs, stats, effects and recipe references valid")
	var duplicate := document.duplicate(true)
	duplicate.items.append(duplicate.items[0].duplicate(true))
	_check(not catalog.validate_document(duplicate).is_empty(), "duplicate item IDs rejected")
	var bad_ref := document.duplicate(true)
	bad_ref.recipes[0].ingredients["unknown_component"] = 1
	_check(not catalog.validate_document(bad_ref).is_empty(), "unknown ingredient rejected")
	var bad_output := document.duplicate(true)
	bad_output.recipes[0].outputId = "unknown_output"
	_check(not catalog.validate_document(bad_output).is_empty(), "unknown output rejected")
	var bad_count := document.duplicate(true)
	bad_count.recipes[0].ingredients[bad_count.recipes[0].ingredients.keys()[0]] = 0
	_check(not catalog.validate_document(bad_count).is_empty(), "zero ingredient count rejected")
	var duplicate_recipe := document.duplicate(true)
	duplicate_recipe.recipes.append(duplicate_recipe.recipes[0].duplicate(true))
	_check(not catalog.validate_document(duplicate_recipe).is_empty(), "duplicate recipes rejected")
	var bad_stats := document.duplicate(true)
	bad_stats.items[0].stats.erase("attackSpeed")
	_check(not catalog.validate_document(bad_stats).is_empty(), "incomplete stats rejected")
	var cycle := document.duplicate(true)
	cycle.recipes[0].ingredients[cycle.recipes[0].outputId] = 1
	_check(not catalog.validate_document(cycle).is_empty(), "cyclic recipe rejected")
	_check(catalog.metadata().source_fingerprints.size() >= 8, "source fingerprints provided")
	_check(catalog.load_catalog("res://data/android/missing.json") != OK, "missing file rejected")
	_check(catalog.items().is_empty(), "failed reload clears previous definitions")
	if not _failed:
		print("PASS Android equipment catalog: source values, typed actives, recipe graph and invalid inputs")
	quit(1 if _failed else 0)

func _check(condition: bool, label: String) -> void:
	if not condition:
		_failed = true
		push_error(label)
