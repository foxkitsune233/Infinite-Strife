class_name AndroidEffectCatalog
extends RefCounted
## Pure loadout-to-effect inputs. No RNG, timers, state writes or base item stats.

const SNAPSHOT_PATH := "res://data/android/equipment.json"
const UPGRADE_GROUPS := {
	"poison_blade": ["rod_of_atos", "witch_blade"],
	"force_staff": ["hurricane_pike", "force_staff"],
	"maelstrom": ["mjollnir", "maelstrom"],
	"expert_array": ["viper_bite", "expert_array"],
	"basher": ["abyssal_blade", "skull_basher"],
}
const SCALABLE_PARAMS := ["amount", "damagePerTick", "spawnDamagePerTick", "points", "percent", "slowPercent", "strength", "agility", "intelligence", "attack", "armor", "attackSpeed", "health", "healthRegen", "manaFlat", "manaPercent", "manaRegen"]
const STAT_PARAMS := ["strength", "agility", "intelligence", "health", "healthRegen", "armor", "attackSpeed", "manaPercent", "manaRegen", "attack", "manaFlat", "attackSpeedPercent"]

static var _snapshot: Dictionary = {}
static var _cards: Dictionary = {}
static var _items: Dictionary = {}

static func _load() -> void:
	if not _snapshot.is_empty():
		return
	var parsed: Variant = JSON.parse_string(FileAccess.get_file_as_string(SNAPSHOT_PATH))
	if not parsed is Dictionary:
		push_error("Android effects snapshot is missing or invalid: %s" % SNAPSHOT_PATH)
		return
	_snapshot = parsed
	for card in _snapshot.get("cards", []):
		if card.get("review") == "CONFIRMED" and not bool(card.get("requiresReview", false)):
			_cards[card.id] = card
	for item in _snapshot.get("items", []):
		_items[item.id] = item

static func for_loadout(state: PreparationState) -> Dictionary:
	_load()
	var cards: Array[Dictionary] = []
	var gear: Array[Dictionary] = []
	var seen := {}
	var school_ids := {}
	for index in mini(8, state.card_slots.size()):
		var instance := state.item(state.card_slots[index])
		if instance == null or seen.has(instance.id) or not _cards.has(instance.definition_id) or instance.star not in [1, 2, 3]:
			continue
		seen[instance.id] = true
		var card: Dictionary = _cards[instance.definition_id]
		for school in card.tags:
			if not school_ids.has(school):
				school_ids[school] = {}
			school_ids[school][card.id] = true
		for effect in card.effects:
			if not bool(effect.get("requiresReview", false)):
				var bound := _bind(effect, "card", instance.id, card.id, instance.star)
				if effect.id == "P2.virulent":
					bound.intervalMs = [8000, 6000, 4000][instance.star - 1]
				cards.append(bound)
	var schools := {}
	var names: Array = school_ids.keys()
	names.sort()
	for school in names:
		schools[school] = school_ids[school].size()
	for tier in _snapshot.get("synergy_tiers", []):
		if int(schools.get(tier.schoolId, 0)) < int(tier.tier):
			continue
		for effect in tier.effects:
			if not bool(effect.get("requiresReview", false)):
				cards.append(_bind(effect, "synergy", effect.id, effect.id, 1))
	cards.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		return a.source_key < b.source_key if a.id == b.id else a.id < b.id)
	var equipped: Array[Dictionary] = []
	for index in mini(6, state.equipment_slots.size()):
		var instance := state.item(state.equipment_slots[index])
		if instance == null or seen.has(instance.id) or not _items.has(instance.definition_id):
			continue
		seen[instance.id] = true
		var definition: Dictionary = _items[instance.definition_id]
		if definition.get("source") != "EquipmentCatalog" or definition.get("kind") != "EQUIPMENT":
			continue
		equipped.append({"instance_id": instance.id, "definition_id": instance.definition_id, "definition": definition})
	for item in equipped:
		if bool(item.definition.get("requiresReview", false)):
			continue
		var effect_ids := {}
		for effect in item.definition.effects:
			if effect_ids.has(effect.id):
				continue
			effect_ids[effect.id] = true
			if bool(effect.get("requiresReview", false)):
				continue
			var priority: Array = UPGRADE_GROUPS.get(effect.get("uniqueGroup"), [])
			if not priority.is_empty() and _upgrade_winner(equipped, priority) != item.instance_id:
				continue
			gear.append(_bind(effect, "gear", item.instance_id, item.definition_id, 1))
	gear.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.source_key < b.source_key)
	var all_effects: Array[Dictionary] = []
	all_effects.append_array(cards)
	all_effects.append_array(gear)
	var initial_mana := 30.0
	var shield_groups := {}
	var shield_multiplier := 1.0
	for effect in cards:
		if effect.id == "synergy.shield.3":
			shield_multiplier += number(effect, "percent")
	var effects: Array[Dictionary] = []
	for effect in all_effects:
		var starting_card: bool = effect.source_type != "gear" and effect.trigger == "BATTLE_START"
		if starting_card and effect.kind == "MANA_RESTORE":
			initial_mana += number(effect, "amount")
		elif starting_card and effect.kind == "GRANT_SHIELD":
			var key: String = effect.shield_source
			if not shield_groups.has(key):
				var duration := number(effect, "durationMs")
				var kind: Variant = effect.get("shieldKind")
				shield_groups[key] = {"source_key": key, "kind": kind if kind in ["PHYSICAL", "MAGICAL"] else "UNIVERSAL", "amount": 0.0, "duration_ms": int(duration) if duration > 0 else null}
			shield_groups[key].amount += number(effect, "amount")
		else:
			effects.append(effect)
	var initial_shields: Array[Dictionary] = []
	var sources: Array = shield_groups.keys()
	sources.sort()
	for key in sources:
		shield_groups[key].amount *= shield_multiplier
		initial_shields.append(shield_groups[key])
	return {"effects": effects, "schools": schools, "initial_mana": initial_mana, "initial_shields": initial_shields, "passive": _passive(all_effects)}

static func _upgrade_winner(equipped: Array[Dictionary], priority: Array) -> String:
	var winner := ""
	var rank := priority.size()
	for item in equipped:
		var candidate := priority.find(item.definition_id)
		if candidate >= 0 and (candidate < rank or (candidate == rank and str(item.instance_id) < winner)):
			rank = candidate
			winner = item.instance_id
	return winner

static func _bind(effect: Dictionary, type: String, instance: String, definition: String, star: int) -> Dictionary:
	var result := effect.duplicate(true)
	var overrides: Dictionary = effect.get("perStar", {})
	result.params = overrides.get(str(star), effect.params).duplicate(true)
	if effect.get("scaling") == "LINEAR_1_2_4" and star != 1:
		for key in result.params:
			if key in SCALABLE_PARAMS and result.params[key].get("type") == "Num":
				result.params[key].value = float(result.params[key].value) * [1.0, 2.0, 4.0][star - 1]
	result.source_type = type
	result.instance_id = instance
	result.definition_id = definition
	result.star = star
	result.source_key = "gear:%s:%s" % [instance, effect.id] if type == "gear" else (str(effect.id) if type == "synergy" else "%s:%s" % [instance, effect.id])
	result.shield_source = result.source_key if type == "gear" else (str(effect.id) if type == "synergy" else "card:%s:%s" % [definition, effect.id])
	return result

static func number(effect: Dictionary, parameter: String, fallback: float = 0.0) -> float:
	var value: Variant = effect.get("params", {}).get(parameter)
	if value is Dictionary and value.get("type") in ["Num", "Count"]:
		return float(value.get("value", fallback))
	return fallback

## Cards/synergies add independently; only gear uses per-kind/parameter groups.
static func passive_values(effects: Array[Dictionary], kind: String, parameter: String) -> Array[float]:
	var plain: Array[float] = []
	var positive := {}
	var negative := {}
	for effect in effects:
		if effect.get("trigger") != "PASSIVE" or effect.get("kind") != kind or bool(effect.get("requiresReview", false)) or not effect.get("params", {}).has(parameter):
			continue
		var value := number(effect, parameter)
		var group: Variant = effect.get("uniqueGroup")
		if effect.get("source_type") != "gear" or group == null:
			plain.append(value)
		elif value >= 0:
			if not positive.has(group) or value > float(positive[group].value) or (value == float(positive[group].value) and str(effect.instance_id) < str(positive[group].instance_id)):
				positive[group] = {"value": value, "instance_id": effect.instance_id}
		else:
			negative[group] = minf(value, float(negative.get(group, 0.0)))
	var groups: Array = positive.keys()
	groups.sort()
	for group in groups:
		plain.append(positive[group].value)
	# Android negative groups preserve first appearance in source-key-sorted gear.
	for group in negative:
		plain.append(negative[group])
	return plain

static func _sum(values: Array[float]) -> float:
	var result := 0.0
	for value in values:
		result += value
	return result

static func _passive(effects: Array[Dictionary]) -> Dictionary:
	var result := {}
	var stat_params: Array = STAT_PARAMS.duplicate()
	for effect in effects:
		if effect.trigger == "PASSIVE" and effect.kind == "STAT_BONUS":
			for key in effect.params:
				if key not in stat_params:
					stat_params.append(key)
	for key in stat_params:
		result[key] = _sum(passive_values(effects, "STAT_BONUS", key))
	result.mana_regen = _sum(passive_values(effects, "MANA_REGEN", "amount"))
	result.magic_resistance_values = passive_values(effects, "MAGIC_RESIST", "percent")
	result.status_resistance_values = passive_values(effects, "STATUS_RESIST", "percent")
	result.healing_amplification_values = passive_values(effects, "HEAL_AMP", "percent")
	result.evasion_values = passive_values(effects, "EVASION", "percent")
	result.lifesteal = _sum(passive_values(effects, "LIFESTEAL", "percent"))
	result.critical_multiplier_bonus = _sum(passive_values(effects, "CRIT_MULTIPLIER", "points"))
	# DuelEngine subtracts card flatSeconds before applying fraction sources. Gear
	# flatSeconds is not used by the Android ultimate cooldown path.
	result.cooldown_flat_seconds = 0.0
	var cooldown_sources: Array[Dictionary] = []
	var critical_sources: Array[Dictionary] = []
	for effect in effects:
		if effect.kind == "COOLDOWN_REDUCTION":
			if effect.source_type != "gear" and effect.trigger == "PASSIVE":
				result.cooldown_flat_seconds += number(effect, "flatSeconds")
			if (effect.source_type == "gear" and effect.trigger == "PASSIVE") or (effect.source_type != "gear" and number(effect, "percent") > 0):
				cooldown_sources.append({"source_key": effect.source_key, "fraction": number(effect, "percent"), "unique_group": effect.get("uniqueGroup"), "active": true})
		if effect.kind == "CRIT_SOURCE":
			critical_sources.append({"source_key": effect.source_key, "instance_id": effect.instance_id, "chance": number(effect, "chance"), "multiplier": number(effect, "multiplier"), "source_type": effect.source_type})
	critical_sources.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		return a.source_key < b.source_key if a.multiplier == b.multiplier else a.multiplier > b.multiplier)
	cooldown_sources.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.source_key < b.source_key)
	var reduction := cooldown_reduction(cooldown_sources)
	result.cooldown_sources = cooldown_sources
	result.cooldown_reduction = reduction.fraction
	result.cooldown_applied_source_ids = reduction.applied_source_ids
	result.critical_sources = critical_sources
	return result

## Matches CooldownReduction's group choice, stable multiplication and 60% cap.
static func cooldown_reduction(sources: Array[Dictionary]) -> Dictionary:
	var selected: Array[Dictionary] = []
	var groups := {}
	for source in sources:
		if not bool(source.get("active", true)):
			continue
		var group: Variant = source.get("unique_group")
		if group == null:
			selected.append(source)
		elif not groups.has(group) or float(source.fraction) > float(groups[group].fraction) or (source.fraction == groups[group].fraction and str(source.source_key) < str(groups[group].source_key)):
			groups[group] = source
	for group in groups:
		selected.append(groups[group])
	selected.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.source_key < b.source_key)
	var remaining := 1.0
	var ids: Array[String] = []
	for source in selected:
		remaining *= 1.0 - float(source.fraction)
		ids.append(source.source_key)
	return {"fraction": minf(0.6, 1.0 - remaining), "applied_source_ids": ids}
