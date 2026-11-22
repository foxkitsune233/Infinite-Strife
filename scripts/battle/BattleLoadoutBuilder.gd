class_name BattleLoadoutBuilder
extends RefCounted

const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")
const AndroidEquipmentCatalog = preload("res://scripts/content/AndroidEquipmentCatalog.gd")
const AndroidHeroCatalog = preload("res://scripts/content/AndroidHeroCatalog.gd")
const HeroLevelRules = preload("res://scripts/progression/HeroLevelRules.gd")
const STANDARD_ENEMY_BUDGETS := [[1, 0, 0, 1, 60], [2, 0, 0, 1, 100],
	[3, 0, 0, 2, 230], [4, 0, 1, 2, 360], [4, 1, 1, 3, 540]]
const ENEMY_GEAR_BUDGETS := [[60, 100, 230, 360, 540], [800, 1900, 3500, 5500, 8000],
	[3000, 3800, 6500, 10000, 16000], [4000, 6500, 10000, 15000, 21000],
	[6500, 10000, 14500, 20500, 28000], [10000, 14500, 20000, 27500, 36000],
	[13000, 19000, 27000, 36000, 46000]]
const ENEMY_SCHOOLS := {
	"juggernaut": ["basic", "crit", "ultimate"], "phantom_assassin": ["crit", "basic"],
	"skywrath": ["ultimate", "shield"], "abaddon": ["shield", "restore", "basic"],
	"necrophos": ["restore", "ultimate", "poison"], "viper": ["poison", "basic"],
	"anti_mage": ["basic", "crit", "ultimate"], "axe": ["basic", "shield", "ultimate"],
	"faceless_void": ["basic", "ultimate", "crit"], "legion_commander": ["basic", "crit", "shield"]
}
const ENEMY_BASE_GEAR := ["iron_branch", "gauntlets", "slippers", "mantle", "circlet", "broadsword",
	"bracer", "wraith_band", "null_talisman"]

static func build(state: PreparationState, tower_primary_bonus: int = 0) -> Dictionary:
	var profile := _hero_profile(state.active_hero_id)
	var hp := float(profile.hp)
	var mana := float(profile.mana)
	var attack := float(profile.attack)
	var armor := float(profile.armor)
	var active_items: Array[Dictionary] = []
	var equipment_slots := {}
	var mkb := {}
	for slot in mini(PreparationState.ACTIVE_EQUIPMENT_SLOT_COUNT, state.equipment_slots.size()):
		var instance_id := state.equipment_slots[slot]
		var instance := state.item(instance_id)
		if instance == null:
			continue
		var definition := ContentCatalog.item(instance.definition_id)
		if definition == null:
			continue
		if definition.id == "monkey_king_bar":
			for effect in definition.source_data.get("effects", []):
				if effect.get("id") == "mkb.proc":
					mkb = {"chance": AndroidEffectCatalog.number(effect, "chance"), "damage": AndroidEffectCatalog.number(effect, "amount")}
		var active := _active_item(definition)
		if slot < 6 and instance.definition_id in ["faerie_fire", "cheese"] and instance.kind == ItemInstance.Kind.CONSUMABLE:
			active = {"id": instance.definition_id, "kind": "consumable", "instance_id": instance.id, "display_name": definition.display_name}
		equipment_slots[slot] = {"id": definition.id, "display_name": definition.display_name, "active": active}
		if not active.is_empty():
			active_items.append(active)
		if not definition.source_data.is_empty():
			continue
		match definition.rarity:
			"common":
				attack += 4
				mana += 6
			"rare":
				attack += 14
				hp += 80
				armor += 2
			"epic":
				attack += 18
				hp += 110
				armor += 3
	for instance_id in state.card_slots:
		var instance := state.item(instance_id)
		if instance == null:
			continue
		var card := _card_for(instance.definition_id)
		if card == null:
			continue
		var value := int(card.effect.get("value", 1))
		for school_id in card.school_ids:
			match school_id:
				"assault", "rage": attack += 4 * value
				"ward": armor += value
				"grace": hp += 24 * value
				"surge": mana += 12 * value
				"rime", "venom", "shadow": attack += 2 * value
	var hero_level := HeroLevelRules.level(state, state.active_hero_id)
	var bonuses := HeroLevelRules.bonuses(hero_level)
	var resistance := float(profile.get("magic_resistance", 0.0))
	# Original ContentCatalog profiles retain their pre-migration behavior.
	if profile.get("source", "") == "android_shared":
		var actual := preload("res://scripts/battle/AndroidLoadoutStats.gd").calculate(state, tower_primary_bonus)
		hp = float(actual.hp) + (hp - float(profile.hp)) * float(bonuses.health_multiplier)
		attack = float(actual.attack) + (attack - float(profile.attack)) * float(bonuses.attack_multiplier)
		armor = float(actual.armor) + (armor - float(profile.armor))
		mana = float(actual.mana) + (mana - float(profile.mana))
		resistance = float(actual.magic_resistance)
		profile["interval"] = actual.interval
		profile["android_stats"] = actual
	var fighter := FighterState.new(state.active_hero_id, roundi(hp), roundi(attack), roundi(float(profile.interval)), armor, roundi(mana), 0, resistance)
	if profile.get("source", "") == "android_shared":
		fighter.hero_id = str(profile.hero_metadata.id)
		fighter.health_regen = float(profile.android_stats.health_regen)
		fighter.mana_regen = float(profile.android_stats.mana_regen)
		fighter.hero_state["base_speed"] = float(profile.android_stats.attack_speed)
		var hit_chance := 1.0
		var bloodthorn_crit: Array[Dictionary] = []
		for effect in profile.android_stats.effects:
			if effect.get("id") == "bloodthorn.crit":
				bloodthorn_crit.append({"key": str(effect.source_key), "multiplier": AndroidEffectCatalog.number(effect, "multiplier")})
			elif effect.get("id") == "abyssal.stun":
				fighter.hero_state["abyssal_bash"] = {
					"chance": AndroidEffectCatalog.number(effect, "chance"),
					"ranged_chance": AndroidEffectCatalog.number(effect, "rangedChance"),
					"duration_ms": int(AndroidEffectCatalog.number(effect, "durationMs")),
					"ranged_duration_ms": int(AndroidEffectCatalog.number(effect, "rangedDurationMs")),
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
		if not bloodthorn_crit.is_empty(): fighter.hero_state["bloodthorn_crit"] = bloodthorn_crit
		for value in profile.android_stats.passive.get("evasion_values", []):
			hit_chance *= 1.0 - float(value)
		if hit_chance < 1.0: fighter.hero_state["evasion_chance"] = 1.0 - hit_chance
		for shield in profile.android_stats.initial_shields:
			var duration: Variant = shield.duration_ms
			fighter.grant_shield(str(shield.source_key), str(shield.kind), float(shield.amount), int(duration) if duration != null else 0)
		fighter.mp = minf(float(fighter.max_mp), float(profile.android_stats.initial_mana))
	elif profile.has("initial_mana"):
		fighter.mp = mini(fighter.max_mp, roundi(float(profile.initial_mana)))
	if not mkb.is_empty(): fighter.hero_state["monkey_king_bar"] = mkb
	var augments := preload("res://scripts/content/HeroAugmentCatalog.gd")
	fighter.hero_state["has_shard"] = augments.owned(state, fighter.hero_id, "shard")
	fighter.hero_state["has_aghanim"] = augments.owned(state, fighter.hero_id, "aghanim")
	for active in active_items:
		if active.get("kind") == "aeon_disk": fighter.hero_state["aeon_disk"] = active.duplicate(true)
	var skill: Dictionary = profile.skill.duplicate(true)
	if fighter.hero_id == "skywrath" and bool(fighter.hero_state.has_shard):
		skill["mana_cost"] = 0
	return {
		"fighter": fighter, "skill": skill, "active_items": active_items,
		"equipment_slots": equipment_slots,
		"display_name": profile.display_name,
		"base_stats": profile.get("base_stats", {}).duplicate(true),
		"hero_metadata": profile.get("hero_metadata", {}).duplicate(true),
		"hero_level": hero_level,
	}

static func enemy_for_node(journey_seed: int, node_index: int, player_hero_id: String, difficulty: int = 0) -> Dictionary:
	var heroes := AndroidHeroCatalog.ids()
	heroes.erase(player_hero_id)
	heroes.sort()
	# Derive a separate draw from the route seed; previews never advance battle or loot RNG.
	var node := maxi(0, node_index)
	var random := clampi(journey_seed, 1, 2147483646)
	for index in range(node + 1):
		random = (random * 48271) % 2147483647
	var hero_id := heroes[random % heroes.size()]
	var round_index := mini(4, node / 2)
	var scouting := {}
	var enemy := build(_standard_enemy_loadout(hero_id, round_index, random, clampi(difficulty, 0, 6), scouting))
	enemy["school_id"] = scouting.get("school_id", "")
	var fighter: FighterState = enemy.fighter
	var scale := _enemy_scale(clampi(difficulty, 0, 6), round_index)
	fighter.max_hp = roundi(fighter.max_hp * scale.health)
	fighter.hp = fighter.max_hp
	fighter.attack_damage = roundi(float(fighter.attack_damage) * scale.attack)
	fighter.armor += float(scale.armor)
	var speed := float(fighter.hero_state.get("base_speed", 100.0))
	var scaled_speed := speed + float(scale.speed)
	fighter.attack_interval_ms = maxi(1, roundi(fighter.attack_interval_ms * maxf(20.0, speed) / maxf(20.0, scaled_speed)))
	fighter.hero_state["base_speed"] = scaled_speed
	return enemy

static func _enemy_scale(difficulty: int, round_index: int) -> Dictionary:
	if difficulty == 0:
		var base := 0.62 + (round_index + 1) * 0.04
		return {"health": base, "attack": base, "speed": 0.0, "armor": 0.0}
	var health: float = [0.0, 1.0, 1.2, 1.45, 1.75, 2.10, 2.42][difficulty]
	var attack: float = [0.0, 0.95, 1.10, 1.25, 1.45, 1.65, 1.85][difficulty]
	health += round_index * [0.0, 0.18, 0.30, 0.40, 0.52, 0.65, 0.78][difficulty]
	attack += round_index * [0.0, 0.10, 0.16, 0.21, 0.27, 0.34, 0.40][difficulty]
	var speed := 40.0 + round_index * 14.0 if difficulty == 6 else (difficulty - 1) * 8.0 + round_index * (difficulty + 1) * 2.0
	var armor := 8.0 + round_index * 3.5 if difficulty == 6 else (difficulty - 1) * 1.5 + round_index * (difficulty + 1) * 0.5
	return {"health": health, "attack": attack, "speed": speed, "armor": armor}

static func _standard_enemy_loadout(hero_id: String, round_index: int, seed: int, difficulty: int = 0, scouting: Dictionary = {}) -> PreparationState:
	var prep := PreparationState.new(hero_id)
	var budget: Array = STANDARD_ENEMY_BUDGETS[round_index]
	var active_quota := 0
	var control_limit := 0
	if difficulty > 0:
		budget = [mini(8, difficulty + 1 + round_index), clampi(round_index + difficulty - 2, 0, 8),
			clampi(round_index + difficulty - 3, 0, 4), mini(6, 1 + difficulty + (round_index >> 1)),
			ENEMY_GEAR_BUDGETS[difficulty][round_index]]
		active_quota = mini(4, int([0, 0, 1, 1, 2, 3, 4][difficulty]) + (round_index >> 1))
		control_limit = (1 if round_index >= 3 else 0) if difficulty == 1 else mini(3, difficulty - 1 + (round_index >> 1))
	var random := seed
	for index in range(round_index + 1):
		random = random * 48271 % 2147483647
	var cards := AndroidEquipmentCatalog.cards().filter(func(card: Dictionary) -> bool:
		return (round_index > 0 or card.quality == "蓝") and (budget[2] > 0 or card.quality != "金")
	)
	cards.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.id < b.id)
	var schools: Array = ENEMY_SCHOOLS[hero_id].filter(func(school: String) -> bool:
		return cards.any(func(card: Dictionary) -> bool: return school in card.schools)
	)
	var school := ""
	if not schools.is_empty():
		random = random * 48271 % 2147483647
		school = str(schools[random % schools.size()])
	scouting["school_id"] = school
	var gold_count := 0
	for slot in int(budget[0]):
		var eligible := cards.filter(func(card: Dictionary) -> bool: return card.quality != "金" or gold_count < int(budget[2]))
		if eligible.is_empty(): break
		var matching := eligible.filter(func(card: Dictionary) -> bool: return school in card.schools)
		var pool := matching if not matching.is_empty() else eligible
		random = random * 48271 % 2147483647
		var card: Dictionary = pool[random % pool.size()]
		cards.erase(card)
		if card.quality == "金": gold_count += 1
		_place_enemy_item(prep, str(card.id), ItemInstance.Kind.CARD, slot, 2 if slot < int(budget[1]) else 1)
	var minimum_clears := 0 if round_index < 2 else 1 if round_index < 4 else 2
	if difficulty > 0: minimum_clears = maxi(difficulty * 3, int([0, 1, 3, 6, 10][round_index]))
	var gear := AndroidEquipmentCatalog.items().filter(func(item: Dictionary) -> bool:
		if item.kind != "EQUIPMENT" or int(item.price) <= 0 or (difficulty == 0 and item.quality == "金"): return false
		if item.id not in ENEMY_BASE_GEAR and item.source != "EquipmentCatalog": return false
		if int(item.minimumShopClears) > minimum_clears or (difficulty == 0 and round_index == 0 and item.quality != "白"): return false
		return control_limit > 0 or not _enemy_control_gear(item)
	)
	gear.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.id < b.id)
	var remaining := int(budget[4])
	var actives := 0
	var controls := 0
	for slot in int(budget[3]):
		var cap := maxi(1, floori(remaining * 0.55)) if difficulty > 0 and slot < int(budget[3]) - 1 else remaining
		var available := gear.filter(func(item: Dictionary) -> bool: return controls < control_limit or not _enemy_control_gear(item))
		var affordable := available.filter(func(item: Dictionary) -> bool: return int(item.price) <= cap)
		if affordable.is_empty(): break
		var best := 0
		for item in affordable: best = maxi(best, _enemy_gear_affinity(str(item.id), school))
		var candidates := affordable.filter(func(item: Dictionary) -> bool: return _enemy_gear_affinity(str(item.id), school) == best)
		if actives < active_quota:
			var reserve := floori(float(remaining) / (active_quota - actives))
			var usable := available.filter(func(item: Dictionary) -> bool:
				return int(item.price) <= reserve and _enemy_usable_active(str(item.id))
			)
			if not usable.is_empty(): candidates = usable
		random = random * 48271 % 2147483647
		var item: Dictionary = candidates[random % candidates.size()]
		gear.erase(item)
		remaining -= int(item.price)
		if _enemy_usable_active(str(item.id)): actives += 1
		if _enemy_control_gear(item): controls += 1
		_place_enemy_item(prep, str(item.id), ItemInstance.Kind.EQUIPMENT, slot)
	return prep

static func _enemy_control_gear(item: Dictionary) -> bool:
	return item.effects.any(func(effect: Dictionary) -> bool: return effect.kind == "APPLY_CONTROL")

static func _enemy_usable_active(id: String) -> bool:
	var definition := ContentCatalog.item(id)
	return definition != null and not _active_item(definition).is_empty()

static func _enemy_gear_affinity(id: String, school: String) -> int:
	if school in ["basic", "crit", "poison"]:
		return 3 if id in ["wraith_band", "broadsword"] else 2 if id in ["slippers", "circlet"] else 1
	if school == "ultimate":
		return 3 if id == "null_talisman" else 2 if id in ["mantle", "circlet"] else 1
	return 3 if id == "bracer" else 2 if id in ["gauntlets", "circlet"] else 1

static func _place_enemy_item(prep: PreparationState, id: String, kind: ItemInstance.Kind, slot: int, star: int = 1) -> void:
	var item := ItemInstance.new("enemy_%d_%d" % [kind, slot], id, kind)
	item.star = star
	prep.add_item(item)
	LoadoutRules.place(prep, item.id, kind, slot)

static func _hero_profile(hero_id: String) -> Dictionary:
	var hero := AndroidHeroCatalog.hero(hero_id)
	if not hero.is_empty():
		var stats := AndroidHeroCatalog.base_stats(hero_id)
		return {
			"display_name": hero.display_name, "source": hero.source,
			"hp": stats.hp, "mana": stats.mana, "attack": stats.attack, "armor": stats.armor,
			"interval": stats.attack_interval_ms, "initial_mana": stats.initial_mana,
			"magic_resistance": stats.magic_resistance, "base_stats": stats, "hero_metadata": hero,
			"skill": _android_placeholder_skill(hero),
		}
	for original_hero in ContentCatalog.heroes():
		if original_hero.id == hero_id:
			return {"display_name": original_hero.display_name, "hp": original_hero.max_hp, "mana": original_hero.max_mp, "attack": original_hero.attack_damage, "armor": original_hero.armor, "interval": 1000, "skill": _normalize_skill(original_hero.skill)}
	return _hero_profile("juggernaut")

static func _android_placeholder_skill(hero: Dictionary) -> Dictionary:
	# Compatibility projection only. These damage values are NOT Android abilities.
	var damage := 110
	var effect := "effect_arcane_cast"
	if hero.id == "juggernaut":
		damage = 180
		effect = "effect_ember_burst"
	elif hero.id == "phantom_assassin":
		damage = 155
		effect = "effect_shadow_cut"
	return {
		"id": hero.skill_id, "display_name": hero.skill_name,
		"mana_cost": int(hero.mana_cost), "cooldown_ms": roundi(float(hero.cooldown_sec) * 1000.0),
		"magic_damage": damage, "stun_ms": 0, "effect_id": effect,
		"runtime_mode": "generic_placeholder", "ability_parity": false, "ability_gap": hero.ability_gap,
	}

static func _normalize_skill(skill: Dictionary) -> Dictionary:
	return {"id": str(skill.get("id", "basic_pulse")), "mana_cost": int(skill.get("mana", 0)), "cooldown_ms": int(skill.get("cooldown_ms", 0)), "magic_damage": 110, "stun_ms": 0, "effect_id": "effect_arcane_cast"}

static func _card_for(definition_id: String) -> CardDefinition:
	for card in ContentCatalog.cards():
		if card.id == definition_id:
			return card
	return null

static func _active_item(definition: ItemDefinition) -> Dictionary:
	var active := definition.active.duplicate(true)
	if definition.id in ["dagon", "ethereal_blade"]:
		for effect in definition.source_data.get("active_effects", []):
			if effect.get("id") in ["dagon.blast", "ethereal.blast"]:
				active = {"kind": "ethereal" if definition.id == "ethereal_blade" else "magic_damage",
					"damage": AndroidEffectCatalog.number(effect, "amount"),
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
	elif definition.id == "shivas_guard":
		for effect in definition.source_data.get("active_effects", []):
			if effect.get("id") == "shiva.slow":
				active = {"kind": "slow", "slow_percent": AndroidEffectCatalog.number(effect, "slowPercent"),
					"duration_ms": int(AndroidEffectCatalog.number(effect, "durationMs")),
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
	elif definition.id == "aeon_disk":
		for effect in definition.source_data.get("effects", []):
			if effect.get("id") == "aeon.save":
				active = {"kind": "aeon_disk", "shield": AndroidEffectCatalog.number(effect, "amount"),
					"threshold": AndroidEffectCatalog.number(effect, "thresholdPercent"),
					"duration_ms": int(AndroidEffectCatalog.number(effect, "durationMs")),
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
	elif definition.id == "silver_edge":
		for effect in definition.source_data.get("active_effects", []):
			if effect.get("kind") == "BREAK":
				active = {"kind": "break", "duration_ms": int(AndroidEffectCatalog.number(effect, "durationMs")),
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
	elif definition.id == "nullifier":
		for effect in definition.source_data.get("active_effects", []):
			if effect.get("id") == "nullifier.dispel":
				active = {"kind": "purge_positive", "duration_ms": 4000,
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
	elif definition.id == "blade_mail":
		for effect in definition.source_data.get("active_effects", []):
			if effect.get("kind") == "REFLECT_DAMAGE":
				active = {"kind": "reflect", "duration_ms": 4000,
					"reflect_ratio": AndroidEffectCatalog.number(effect, "percent"),
					"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
	elif definition.id in ["bloodthorn", "abyssal_blade", "scythe_of_vyse", "euls_scepter", "orchid", "heavens_halberd"]:
		for effect in definition.source_data.get("active_effects", []):
			if effect.get("kind") != "APPLY_CONTROL": continue
			var dispel_class: String = {"BASIC": "basic", "STRONG_ONLY": "strong_only"}.get(
				str(effect.get("dispelClass")), "undispellable")
			active = {"kind": "control", "control": str(effect.params.control.value).to_lower(),
				"dispel_class": dispel_class, "duration_ms": int(AndroidEffectCatalog.number(effect, "durationMs")),
				"ranged_duration_ms": int(AndroidEffectCatalog.number(effect, "rangedDurationMs",
					AndroidEffectCatalog.number(effect, "durationMs"))),
				"cooldown_ms": int(effect.get("internalCooldownMs", 0))}
			break
	if active.is_empty(): return active
	active["id"] = definition.id
	active["display_name"] = definition.display_name
	return active
