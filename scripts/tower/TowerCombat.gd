extends RefCounted

const Builder = preload("res://scripts/battle/BattleLoadoutBuilder.gd")
const Heroes = preload("res://scripts/content/AndroidHeroCatalog.gd")

static func build(state: PreparationState, run: Dictionary) -> Dictionary:
	var prepared := PreparationState.new(str(run.hero_id))
	prepared.items = state.items
	prepared.equipment_slots = state.equipment_slots
	prepared.card_slots = state.card_slots
	prepared.hero_levels = state.hero_levels
	prepared.quest_progress = state.quest_progress
	var player := Builder.build(prepared, int(run.get("tower_primary_bonus", 0)))
	var fighter: FighterState = player.fighter
	if run.get("current_hp") != null:
		fighter.hp = clampf(float(run.current_hp), 0.0, float(fighter.max_hp))
	var floor_index := clampi(int(run.floor), 1, 20)
	var hero: String = Heroes.ids()[(floor_index * 7) % Heroes.ids().size()]
	var enemy := Builder.build(PreparationState.new(hero))
	var raw := preload("res://scripts/battle/AndroidLoadoutStats.gd").calculate(PreparationState.new(hero))
	var opponent: FighterState = enemy.fighter
	# A tower can face the same hero. Combat identities must still be distinct.
	opponent.id = "tower_enemy_%s" % hero
	var offset := float(floor_index - 1)
	opponent.max_hp = roundi(float(raw.hp) * (1.0 + 0.12 * offset))
	opponent.hp = opponent.max_hp
	opponent.attack_damage = roundi(float(raw.attack) * (1.0 + 0.08 * offset))
	opponent.armor += offset * 0.5
	var speed := float(opponent.hero_state.get("base_speed", 100.0))
	opponent.attack_interval_ms = maxi(1, roundi(opponent.attack_interval_ms * maxf(20.0, speed) / maxf(20.0, speed + offset)))
	opponent.hero_state["base_speed"] = speed + offset
	return {"left": player, "right": enemy}

static func configure(engine: BattleEngine, supply: bool) -> void:
	engine.time_limit_ms = 2000000 # Android SIM_TICK_CAP=20,000 ticks at 100ms.
	engine.timeout_left_loses = true
	if supply:
		engine._left.hero_state["tower_supply_base_regen"] = engine._left.health_regen
		engine._left.hero_state["tower_supply_until_ms"] = 60000
