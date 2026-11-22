extends SceneTree

const State = preload("res://scripts/prepare/PreparationState.gd")
const Item = preload("res://scripts/prepare/ItemInstance.gd")
var _failed := false
var _checks := 0
var adapter: Script

func _init() -> void:
	if not FileAccess.file_exists("res://scripts/battle/AndroidEffectCatalog.gd"):
		_check(false, "Android effect adapter exists")
		quit(1)
		return
	adapter = load("res://scripts/battle/AndroidEffectCatalog.gd")
	_test_cards()
	_test_start_inputs()
	_test_gear()
	_test_selection_math()
	print("Android effect catalog: %d checks, failed=%s" % [_checks, _failed])
	quit(1 if _failed else 0)

func _test_cards() -> void:
	var state = State.new("juggernaut")
	_card(state, "a", "A1", 0, 2)
	_card(state, "b", "A1", 1, 3)
	_card(state, "dual", "D2", 2, 1)
	var result: Dictionary = adapter.for_loadout(state)
	_equal(result.schools, {"basic": 2, "crit": 1}, "copies count once; dual contributes to both schools")
	_near(result.passive.attackSpeed, 82.0, "24 + 48 + basic tier-2 10")
	_equal(_effect(result, "A1.attackSpeed", "a").source_key, "a:A1.attackSpeed", "Android card source key")
	_equal(_effect(result, "A1.attackSpeed", "b").params.attackSpeed.type, "Num", "typed resolved params")
	state.card_slots[3] = "a"
	_equal(adapter.for_loadout(state), result, "duplicate slot reference cannot grant another copy")
	_card(state, "a2", "A2", 3, 1)
	_card(state, "a3", "A3", 4, 1)
	result = adapter.for_loadout(state)
	_equal(result.schools.basic, 4, "four distinct basic card IDs")
	_near(result.passive.attack, 22.0, "card attack plus cumulative tier-3")
	_check(not _effect(result, "synergy.basic.2").is_empty(), "tier 2 retained at count four")
	_check(not _effect(result, "synergy.basic.3").is_empty(), "tier 3 retained at count four")
	_check(not _effect(result, "synergy.basic.4").is_empty(), "tier 4 active")
	for star in range(1, 4):
		var poison = State.new("viper")
		_card(poison, "p", "P2", 0, star)
		var effect: Dictionary = _effect(adapter.for_loadout(poison), "P2.virulent")
		_equal(effect.intervalMs, [8000, 6000, 4000][star - 1], "P2 runtime interval star %d" % star)
		_near(effect.params.spawnDamagePerTick.value, 3.0, "P2 explicit per-star poison damage stays three")
		_equal(effect.params.layers.value, 1, "Count layer does not scale")
	var crit = State.new("juggernaut")
	_card(crit, "crit", "C1", 0, 3)
	var critical: Dictionary = adapter.for_loadout(crit)
	_near(critical.passive.critical_sources[0].chance, 0.24, "explicit PER_STAR probability")
	_near(critical.passive.critical_sources[0].multiplier, 1.6, "crit multiplier does not scale")
	_equal(critical.passive.critical_sources[0].source_key, "crit:C1.critSource", "PRD failure key input")
	var detached: Dictionary = adapter.for_loadout(state)
	detached.effects[0].params.clear()
	_check(not adapter.for_loadout(state).effects[0].params.is_empty(), "results cannot mutate source snapshot")
	_equal(state.item("a").star, 2, "input stars unchanged")

func _test_start_inputs() -> void:
	var state = State.new("skywrath")
	_card(state, "u1", "U1", 0, 2)
	_card(state, "u2", "U2", 1, 3)
	_card(state, "u3", "U3", 2, 2)
	_card(state, "u4", "U4", 3, 1)
	var result: Dictionary = adapter.for_loadout(state)
	_near(result.initial_mana, 90.0, "30 + U2 star3 60; ultimate tier4 restores on cast")
	_near(result.passive.manaFlat, 240.0, "U2 raw passive mana pool")
	_near(result.passive.mana_regen, 4.5, "U1 amount plus ultimate tier2")
	_near(result.passive.cooldown_flat_seconds, 1.0, "U3 star2 flat seconds kept separate from fractions")
	_near(result.passive.cooldown_reduction, 0.0, "flat CD reduction is not a fraction")
	_check(_effect(result, "U2.initialMana").is_empty(), "preapplied start mana removed from runtime list")
	_equal(_effect(result, "synergy.ultimate.4").trigger, "ULT_START", "ultimate tier4 restores on cast, not on battle start")
	_check(not _effect(result, "U4.freeCast").is_empty(), "unconsumed free cast stays in runtime list")
	var shields = State.new("abaddon")
	_card(shields, "s1", "S1", 0, 1)
	_card(shields, "s1b", "S1", 1, 2)
	_card(shields, "s2", "S2", 2, 1)
	_card(shields, "s3", "S3", 3, 1)
	result = adapter.for_loadout(shields)
	_equal(result.initial_shields.size(), 2, "same card shields form one source pool plus synergy pool")
	_near(_shield(result, "card:S1:S1.startShield").amount, 207.0, "(60+120)*1.15")
	_near(_shield(result, "synergy.shield.2").amount, 92.0, "80*1.15")
	_equal(_shield(result, "card:S1:S1.startShield").kind, "UNIVERSAL", "shield kind")
	_equal(_shield(result, "card:S1:S1.startShield").duration_ms, null, "permanent shield expiry")
	_check(_effect(result, "S1.startShield").is_empty(), "start shield cannot fire twice")
	_check(not _effect(result, "S2.periodicShield").is_empty(), "future shields remain selectable")

func _test_gear() -> void:
	var state = State.new("juggernaut")
	_gear(state, "low", "maelstrom", 0)
	_gear(state, "z-upgrade", "mjollnir", 1)
	_gear(state, "a-upgrade", "mjollnir", 2)
	var result: Dictionary = adapter.for_loadout(state)
	_check(_effect(result, "maelstrom.chain").is_empty(), "upgrade suppresses base proc")
	_equal(_effect(result, "mjollnir.chain").instance_id, "a-upgrade", "equal upgrade priority chooses smallest instance ID")
	_equal(_effect(result, "mjollnir.chain").source_key, "gear:a-upgrade:mjollnir.chain", "Android gear source key")
	_equal(_count_effect(result, "mjollnir.static"), 2, "different family retains both secondary effects")
	for pair in [["witch_blade", "rod_of_atos", "witch.poison"], ["force_staff", "hurricane_pike", "force.protection"], ["expert_array", "viper_bite", "array.extraHit"]]:
		var priority = State.new("juggernaut")
		_gear(priority, "a-base", pair[0], 0)
		_gear(priority, "z-upgrade", pair[1], 1)
		_check(_effect(adapter.for_loadout(priority), pair[2]).is_empty(), "priority family %s" % pair[0])
	var stats = State.new("abaddon")
	_gear(stats, "s", "sange", 0)
	_gear(stats, "sy", "sange_and_yasha", 1)
	_gear(stats, "y", "yasha", 2)
	_gear(stats, "orb1", "octarine_core", 3)
	_gear(stats, "orb2", "octarine_core", 4)
	_gear(stats, "cloak", "cloak", 5)
	_gear(stats, "reserve", "pipe_of_insight", 6)
	result = adapter.for_loadout(stats)
	_near(result.passive.attackSpeed, 12.0, "yasha family strongest per-parameter stat")
	_near(result.passive.health, 500.0, "ungrouped gear stat effects stack")
	_near(result.passive.manaFlat, 500.0, "distinct STAT_BONUS params both included")
	_near(result.passive.mana_regen, 4.0, "ungrouped gear mana amounts stack")
	_near(result.passive.cooldown_reduction, 0.25, "same CD group chooses only one octarine")
	_equal(result.passive.cooldown_sources.size(), 2, "raw cooldown candidates preserved for consumer")
	_equal(result.passive.cooldown_applied_source_ids, ["gear:orb1:octarine.cdr"], "CD group tie-break source")
	_equal(result.passive.magic_resistance_values, [0.15], "component resistance; reserve slot excluded")
	_equal(result.passive.status_resistance_values, [0.2], "sange family strongest resistance")
	_equal(result.passive.healing_amplification_values, [0.24], "sange family strongest healing")
	_check(_effect(result, "pipe.magicResist").is_empty(), "reserve equipment inactive")
	_near(result.passive.strength, 0.0, "base item stats excluded from adapter")
	var negative = State.new("viper")
	_gear(negative, "atos1", "rod_of_atos", 0)
	_gear(negative, "atos2", "rod_of_atos", 1)
	_equal(adapter.for_loadout(negative).passive.magic_resistance_values, [-0.15], "negative grouped resistance remains negative")
	var basher = State.new("juggernaut")
	_gear(basher, "z", "skull_basher", 0)
	_gear(basher, "a", "skull_basher", 1)
	_equal(_effect(adapter.for_loadout(basher), "basher.stun").instance_id, "a", "basher priority also deduplicates copies")
	var ungrouped = State.new("juggernaut")
	_gear(ungrouped, "c1", "cloak", 0)
	_gear(ungrouped, "c2", "cloak", 1)
	_equal(adapter.for_loadout(ungrouped).passive.magic_resistance_values, [0.15, 0.15], "independent resists stay independent")
	var critical = State.new("juggernaut")
	_card(critical, "cardcrit", "C1", 0, 1)
	_gear(critical, "gearcrit", "daedalus", 0)
	var crit_result: Dictionary = adapter.for_loadout(critical)
	_equal(crit_result.passive.critical_sources[0].source_key, "gear:gearcrit:daedalus.crit", "stronger gear crit ordered before card PRD input")
	var passive_card = State.new("juggernaut")
	_card(passive_card, "regen", "R1", 0, 3)
	_near(adapter.for_loadout(passive_card).passive.healthRegen, 12.0, "STAT_BONUS regen is separate from effect mana amount")

func _test_selection_math() -> void:
	# Explicit domain candidates catch accidental grouping of cards with gear,
	# and independent positive/negative winners within one parameter family.
	var effects: Array[Dictionary] = []
	for row in [["card", "a", 0.1], ["card", "b", 0.1], ["gear", "c", 0.2], ["gear", "d", 0.3], ["gear", "e", -0.1], ["gear", "f", -0.2]]:
		effects.append({"source_type": row[0], "instance_id": row[1], "source_key": row[1], "kind": "MAGIC_RESIST", "trigger": "PASSIVE", "uniqueGroup": "same", "params": {"percent": {"type": "Num", "value": row[2]}}})
	_equal(adapter.passive_values(effects, "MAGIC_RESIST", "percent"), [0.1, 0.1, 0.3, -0.2], "cards additive; gear positive max plus negative min")
	effects[0]["requiresReview"] = true
	_equal(adapter.passive_values(effects, "MAGIC_RESIST", "percent"), [0.1, 0.3, -0.2], "pending-review excluded")
	var sources: Array[Dictionary] = [{"source_key": "b", "fraction": 0.2, "unique_group": null}, {"source_key": "a", "fraction": 0.25, "unique_group": null}]
	_near(adapter.cooldown_reduction(sources).fraction, 0.4, "independent CD remaining proportions multiply")
	sources.append({"source_key": "c", "fraction": 0.9, "unique_group": null})
	_near(adapter.cooldown_reduction(sources).fraction, 0.6, "CD cap is sixty percent")
	sources.append({"source_key": "ignored", "fraction": 1.0, "unique_group": null, "active": false})
	_check("ignored" not in adapter.cooldown_reduction(sources).applied_source_ids, "inactive CD source ignored")
	var empty: Dictionary = adapter.for_loadout(State.new("juggernaut"))
	_equal(empty.effects, [], "empty loadout has no effects")
	_near(empty.initial_mana, 30.0, "Android baseline mana")
	_equal(empty.initial_shields, [], "empty initial shield pool")

func _card(state: PreparationState, id: String, definition: String, slot: int, star: int) -> void:
	var item = Item.new(id, definition, Item.Kind.CARD)
	item.star = star
	state.add_item(item)
	state.card_slots[slot] = id

func _gear(state: PreparationState, id: String, definition: String, slot: int) -> void:
	state.add_item(Item.new(id, definition, Item.Kind.EQUIPMENT))
	state.equipment_slots[slot] = id

func _effect(result: Dictionary, id: String, instance: String = "") -> Dictionary:
	for effect in result.effects:
		if effect.id == id and (instance.is_empty() or effect.instance_id == instance):
			return effect
	return {}

func _count_effect(result: Dictionary, id: String) -> int:
	var count := 0
	for effect in result.effects:
		if effect.id == id:
			count += 1
	return count

func _shield(result: Dictionary, source: String) -> Dictionary:
	for shield in result.initial_shields:
		if shield.source_key == source:
			return shield
	return {}

func _near(actual: float, expected: float, label: String) -> void:
	_check(absf(actual - expected) < 0.0000001, "%s: expected %s got %s" % [label, expected, actual])

func _equal(actual: Variant, expected: Variant, label: String) -> void:
	_check(actual == expected, "%s: expected %s got %s" % [label, expected, actual])

func _check(condition: bool, label: String) -> void:
	_checks += 1
	if not condition:
		_failed = true
		push_error(label)
