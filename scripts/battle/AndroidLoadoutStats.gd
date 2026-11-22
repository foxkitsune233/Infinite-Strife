class_name AndroidLoadoutStats
extends RefCounted

const Content = preload("res://scripts/content/AndroidContent.gd")
const Heroes = preload("res://scripts/content/AndroidHeroCatalog.gd")
const Levels = preload("res://scripts/progression/HeroLevelRules.gd")
const EffectCatalog = preload("res://scripts/battle/AndroidEffectCatalog.gd")

static func calculate(state: PreparationState, tower_primary_bonus: int = 0) -> Dictionary:
	var hero := Heroes.hero(state.active_hero_id)
	if hero.is_empty():
		hero = Heroes.hero("juggernaut")
	var stats := {"strength": float(hero.strength), "agility": float(hero.agility), "intelligence": float(hero.intelligence), "health": 0.0, "attack": 0.0, "armor": 0.0, "attackSpeed": 0.0, "manaPercent": 0.0, "healthRegen": 0.0, "manaRegen": 0.0}
	var primary := "strength" if hero.id == "abaddon" else "intelligence" if hero.id in ["skywrath", "necrophos"] else "agility"
	stats[primary] += clampi(tower_primary_bonus, 0, 48)
	# The adapter owns card stars, unique groups, gear upgrades and school tiers.
	# Keep base gear stats here because they are separate from combat effects.
	var resolved := EffectCatalog.for_loadout(state)
	var effects: Array[Dictionary] = resolved.effects
	var schools: Dictionary = resolved.schools
	# Android's first six slots are effective; the remaining three are reserves.
	for index in mini(6, state.equipment_slots.size()):
		var instance := state.item(state.equipment_slots[index])
		if instance == null:
			continue
		var record := Content.item(instance.definition_id)
		if record.is_empty():
			continue
		for key in record.stats:
			stats[key] = float(stats.get(key, 0.0)) + float(record.stats[key])
	var passive: Dictionary = resolved.passive
	for key in ["strength", "agility", "intelligence", "health", "attack", "armor", "attackSpeed", "healthRegen"]:
		stats[key] += float(passive.get(key, 0.0))
	var strength := float(stats.strength)
	var agility := float(stats.agility)
	var intelligence := float(stats.intelligence)
	var main := agility
	if hero.id == "abaddon":
		main = (strength + agility + intelligence) * 0.45
	elif hero.id in ["skywrath", "necrophos"]:
		main = intelligence
	var bonuses := Levels.bonuses(Levels.level(state, str(hero.id)))
	var speed := (100.0 + agility + float(stats.attackSpeed)) * (1.0 + float(passive.get("attackSpeedPercent", 0.0)))
	var resistance := 0.25 + intelligence * 0.001 + float(bonuses.magic_resist_bonus) / 100.0
	for value in passive.get("magic_resistance_values", []):
		resistance += float(value)
	return {
		"hp": (500.0 + 22.0 * strength + float(stats.health)) * float(bonuses.health_multiplier),
		"mana": (75.0 + 12.0 * intelligence + float(passive.get("manaFlat", 0.0))) * (1.0 + float(stats.manaPercent) / 100.0),
		"attack": (float(hero.base_attack) + main + float(stats.attack)) * float(bonuses.attack_multiplier),
		"armor": float(hero.base_armor) + agility / 6.0 + float(stats.armor) + float(bonuses.armor_bonus),
		"interval": float(hero.bat) * 100000.0 / maxf(20.0, speed),
		"magic_resistance": resistance,
		"health_regen": float(hero.base_health_regen) + strength * 0.1 + float(stats.healthRegen),
		"mana_regen": float(hero.base_mana_regen) + intelligence * 0.05 + float(stats.manaRegen) + float(passive.get("mana_regen", 0.0)),
		"attack_speed": speed, "effects": effects, "schools": schools,
		"initial_mana": float(resolved.initial_mana), "initial_shields": resolved.initial_shields,
		"passive": passive,
	}
