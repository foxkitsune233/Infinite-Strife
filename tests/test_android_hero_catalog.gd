extends SceneTree

const Builder = preload("res://scripts/battle/BattleLoadoutBuilder.gd")
const PrepState = preload("res://scripts/prepare/PreparationState.gd")
const Rules = preload("res://scripts/prepare/LoadoutRules.gd")
const Instance = preload("res://scripts/prepare/ItemInstance.gd")

# Independent, hand-calculated naked level-one JourneyRules.fighter fixtures.
# id, name, hp, mana, attack, armor, speed, interval_ms (rounded), regen, mana_regen, MR, cost, cooldown_ms
const CASES := [
	["juggernaut", "主宰", 1028, 267, 61.0, 5.333333333333333, 126, 1190, 2.9, 5.8, 0.266, 80, 16000],
	["skywrath", "天怒法师", 896, 435, 60.0, 3.0, 118, 1441, 2.3, 10.5, 0.280, 90, 8000],
	["phantom_assassin", "幻影刺客", 984, 255, 68.0, 5.666666666666667, 128, 1328, 2.7, 5.75, 0.265, 70, 10000],
	["abaddon", "亚巴顿", 1116, 339, 52.6, 5.0, 118, 1441, 3.8, 8.1, 0.272, 140, 9000],
	["necrophos", "瘟疫法师", 1028, 399, 57.0, 3.666666666666667, 116, 1466, 3.4, 8.35, 0.277, 85, 18000],
	["viper", "冥界亚龙", 1028, 315, 58.0, 5.0, 124, 1371, 2.9, 7.0, 0.270, 75, 9000],
	["anti_mage", "敌法师", 940, 291, 66.0, 6.0, 130, 1115, 2.5, 5.9, 0.268, 75, 12000],
	["axe", "斧王", 1204, 267, 50.0, 6.333333333333333, 120, 1417, 4.7, 4.8, 0.266, 90, 14000],
	["faceless_void", "虚空假面", 1072, 291, 62.0, 6.666666666666667, 128, 1250, 3.35, 5.9, 0.268, 85, 16000],
	["legion_commander", "军团指挥官", 1160, 291, 57.0, 6.0, 124, 1250, 4.0, 5.4, 0.268, 80, 15000],
]

var _failed := false

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	# This also fails meaningfully before the new catalog is present.
	_equal(Builder.build(PrepState.new("juggernaut")).fighter.max_hp, 1028, "Android base HP replaces prototype HP")
	if not ResourceLoader.exists("res://scripts/content/AndroidHeroCatalog.gd"):
		_check(false, "Android hero catalog exists")
		quit(1)
		return
	var catalog = load("res://scripts/content/AndroidHeroCatalog.gd")
	_equal(catalog.ids().size(), 10, "all actual Android heroes available")
	for index in CASES.size():
		var row: Array = CASES[index]
		var id: String = row[0]
		_equal(catalog.ids()[index], id, "source roster order")
		var hero: Dictionary = catalog.hero(id)
		_equal(hero.display_name, row[1], id + " name")
		var stats: Dictionary = catalog.base_stats(id)
		var fields := ["hp", "mana", "attack", "armor", "attack_speed"]
		for column in fields.size():
			_near(float(stats[fields[column]]), float(row[column + 2]), id + " " + fields[column])
		_equal(roundi(stats.attack_interval_ms), row[7], id + " interval")
		_near(stats.health_regen, row[8], id + " health regen")
		_near(stats.mana_regen, row[9], id + " mana regen")
		_near(stats.magic_resistance, row[10], id + " resistance")
		_equal(stats.initial_mana, 30.0, id + " Android initial mana")
		var built: Dictionary = Builder.build(PrepState.new(id))
		_equal(built.display_name, row[1], id + " battle name")
		_equal(built.fighter.id, id, id + " identity")
		_equal(built.fighter.max_hp, row[2], id + " battle HP")
		_equal(built.fighter.max_mp, row[3], id + " battle mana")
		_equal(built.fighter.mp, 30, id + " battle initial mana")
		_equal(built.fighter.attack_damage, roundi(row[4]), id + " battle attack")
		_near(built.fighter.armor, float(row[5]), id + " fractional Android armor is preserved")
		_equal(built.fighter.attack_interval_ms, row[7], id + " battle interval")
		_near(built.fighter.magic_resistance, row[10], id + " battle resistance")
		_equal(built.skill.mana_cost, row[11], id + " mana cost")
		_equal(built.skill.cooldown_ms, row[12], id + " cooldown")
		_equal(built.hero_metadata.ability_parity, false, id + " no ability parity claim")
		_equal(built.skill.runtime_mode, "generic_placeholder", id + " explicit placeholder")
		_check(not str(built.hero_metadata.ability_gap).is_empty(), id + " documented ability gap")
		_near(built.base_stats.attack, row[4], id + " precise stats retained")
	var copy: Dictionary = catalog.hero("axe")
	copy.skill_config.self_armor = 999
	_equal(catalog.hero("axe").skill_config.self_armor, 12.0, "catalog results cannot mutate content")
	_near(catalog.base_stats("juggernaut").attack_interval_ms, 1190.4761904761904, "catalog retains submillisecond interval")
	_equal(catalog.hero("skywrath").skill_config.hits, 8, "HeroContent timed hit metadata")
	_near(catalog.hero("anti_mage").skill_config.ult_mana_void_coefficient, 0.6, "HeroContent cumulative mana burn coefficient")
	_near(catalog.hero("faceless_void").skill_config.time_lock_chance, 0.18, "HeroContent proc probability")
	_near(catalog.hero("legion_commander").skill_config.dual_strike_heal, 22.0, "HeroContent extra attack healing")
	_check(catalog.hero("missing").is_empty(), "unknown catalog ID is explicit")
	_check(catalog.base_stats("missing").is_empty(), "unknown stats are explicit")
	var legacy: Dictionary = Builder.build(PrepState.new("cinder"))
	_equal(legacy.fighter.max_hp, 980, "original ContentCatalog fallback HP")
	_equal(legacy.fighter.mp, 180, "original fallback initial mana")
	_equal(legacy.skill.id, "meteor_step", "original fallback skill")
	for original in preload("res://scripts/content/ContentCatalog.gd").heroes():
		var fallback: Dictionary = Builder.build(PrepState.new(original.id))
		_equal(fallback.display_name, original.display_name, "all original heroes retain names")
		_equal(fallback.fighter.max_hp, original.max_hp, "all original heroes retain HP")
		_equal(fallback.fighter.attack_damage, original.attack_damage, "all original heroes retain attack")
		_equal(fallback.fighter.armor, original.armor, "all original heroes retain armor")
		_equal(fallback.fighter.max_mp, original.max_mp, "all original heroes retain mana")
		_equal(fallback.skill.id, original.skill.id, "all original heroes retain skills")
	_equal(Builder.build(PrepState.new("missing")).fighter.max_hp, 1028, "unknown profile falls back to Android juggernaut")
	_test_equipment()
	_test_levels()
	await _test_cycle(catalog)
	if not _failed:
		print("PASS: Android hero catalog, stats, loadout, fallback, and full preparation cycle")
	quit(1 if _failed else 0)

func _test_equipment() -> void:
	var state := PrepState.new("abaddon")
	state.add_item(Instance.new("lens", "storm_lens", Instance.Kind.EQUIPMENT))
	Rules.place(state, "lens", Instance.Kind.EQUIPMENT, 0)
	var built: Dictionary = Builder.build(state)
	_equal(built.fighter.max_hp, 1196, "existing gear HP bonus survives migration")
	_equal(built.fighter.attack_damage, 67, "fractional base attack rounded at fighter boundary")
	_equal(built.fighter.armor, 7, "existing gear armor bonus survives migration")

func _test_levels() -> void:
	var state := PrepState.new("juggernaut")
	state.hero_levels["juggernaut"] = 10
	var built: Dictionary = Builder.build(state)
	_equal(built.hero_level, 10, "builder reads per-hero level")
	_equal(built.fighter.max_hp, 1234, "level ten uses 1.20 health multiplier")
	_equal(built.fighter.attack_damage, 70, "level ten uses 1.155 attack multiplier")
	_near(built.fighter.armor, 1.0 + 26.0 / 6.0 + 3.6, "level ten preserves fractional armor")
	_near(built.fighter.magic_resistance, 0.331, "6.5 resistance percentage points converted once")
	_near(built.base_stats.attack, 61.0, "level scaling leaves exact naked stats intact")
	state.active_hero_id = "abaddon"
	state.hero_levels["abaddon"] = 10
	state.add_item(Instance.new("lens", "storm_lens", Instance.Kind.EQUIPMENT))
	Rules.place(state, "lens", Instance.Kind.EQUIPMENT, 0)
	built = Builder.build(state)
	_equal(built.fighter.max_hp, 1435, "health multiplier includes loadout bonus")
	_equal(built.fighter.attack_damage, 77, "attack multiplier precedes integer projection")
	_near(built.fighter.armor, 10.6, "armor bonus includes loadout armor")
	# Selecting another hero must not inherit Abaddon's level.
	state.active_hero_id = "skywrath"
	_equal(Builder.build(state).fighter.max_hp, 976, "hero without a saved level defaults to one with existing gear")

func _test_cycle(catalog: Script) -> void:
	var screen = load("res://scenes/Preparation.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	screen.state = PrepState.new("juggernaut")
	screen.state.set_selected_preset("axe", 2)
	for index in range(1, 11):
		screen._on_hero_pressed()
		var expected: String = catalog.ids()[index % 10]
		var choice := screen.get_node("HeroPicker").get_child(0).get_child(0).get_child(index % 10) as Button
		_equal(choice.disabled, expected not in screen.state.unlocked_heroes, "locked heroes cannot be selected")
		if choice.disabled:
			screen.get_node("HeroPicker").queue_free()
			await process_frame
			continue
		choice.pressed.emit()
		await process_frame
		_equal(screen.state.active_hero_id, expected, "preparation activates the chosen unlocked hero")
		_check(screen.hero_button.text.contains(str(catalog.hero(expected).display_name)), "button uses catalog name")
		if expected == "axe":
			_equal(screen.state.selected_preset("axe"), 2, "hero-specific preset retained")
	_equal(screen._hero_name("cinder"), "余烬旅者", "original hero name fallback")
	_equal(screen._hero_name("missing"), "missing", "unknown hero label is honest")
	screen.state.active_hero_id = "cinder"
	screen._on_hero_pressed()
	(screen.get_node("HeroPicker").get_child(0).get_child(0).get_child(0) as Button).pressed.emit()
	_equal(screen.state.active_hero_id, "juggernaut", "legacy selection enters Android roster")
	screen.queue_free()
	await process_frame

func _near(actual: float, expected: float, label: String) -> void:
	_check(absf(actual - expected) < 0.000000001, "%s expected=%s actual=%s" % [label, expected, actual])

func _equal(actual: Variant, expected: Variant, label: String) -> void:
	_check(actual == expected, "%s expected=%s actual=%s" % [label, expected, actual])

func _check(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)
