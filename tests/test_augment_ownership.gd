extends SceneTree
func _init() -> void:
	var state := PreparationState.new("juggernaut")
	state.quest_progress["pending_shard_heroes"] = ["juggernaut"]
	var fighter: FighterState = BattleLoadoutBuilder.build(state).fighter
	assert(not fighter.hero_state.get("has_shard", false))
	state.quest_progress["tower_shard"] = ["juggernaut"]
	state.quest_progress["tower_aghs"] = ["juggernaut"]
	fighter = BattleLoadoutBuilder.build(state).fighter
	assert(fighter.hero_state.has_shard and fighter.hero_state.has_aghanim)
	fighter.hero_state["aghanim_used"] = true
	var engine := BattleEngine.new(42, fighter, FighterState.new("enemy", 1000, 0, 1000, 0))
	var restored := BattleEngine.from_state(engine.export_state())
	assert(restored._left.hero_state.aghanim_used)
	assert(not BattleLoadoutBuilder.build(state).fighter.hero_state.get("aghanim_used", false))
	state.active_hero_id = "axe"
	assert(not BattleLoadoutBuilder.build(state).fighter.hero_state.get("has_shard", false))
	print("Granted augments only, hero binding, per-battle reset and save persistence passed")
	quit()
