class_name HeroLevelRules
extends RefCounted

const LEVEL_CAP := 10
const MAX_LEVEL_DESIGN := 100
const COST_RATIO := 1.20

# Tower integration: persist Android progress.levelCap as quest_progress.level_cap
# and progress.towerHighestFloor as quest_progress.tower_highest_floor (0..20).
# The tower owner must retain the historical maximum on defeat and write the cap
# after settlement. Missing explicit caps derive from history for older saves.
static func cap_after_floor(floors_cleared: int) -> int:
	return mini(MAX_LEVEL_DESIGN, LEVEL_CAP + maxi(0, clampi(floors_cleared, 0, 20) - 1) * 5)

static func level_cap(state: PreparationState) -> int:
	return clampi(int(state.quest_progress.get("level_cap", cap_after_floor(int(state.quest_progress.get("tower_highest_floor", 0))))), 1, MAX_LEVEL_DESIGN)

static func is_capped(state: PreparationState, current_level: int) -> bool:
	return current_level >= level_cap(state)

static func level(state: PreparationState, hero_id: String) -> int:
	return int(state.hero_levels.get(hero_id, 1))

static func upgrade_cost(current_level: int) -> int:
	# Kotlin Double.roundToInt saturates at Int.MAX_VALUE; roundi alone does not.
	return int(minf(2147483647.0, floorf(100.0 * pow(COST_RATIO, float(maxi(1, current_level)) - 1.0) + 0.5)))

static func bonuses(current_level: int) -> Dictionary:
	var value := current_level
	var milestones := int(float(value) / 5.0)
	return {
		"attack_multiplier": 1.0 + 0.015 * (value - 1) + 0.01 * milestones,
		"health_multiplier": 1.0 + 0.020 * (value - 1) + 0.01 * milestones,
		"armor_bonus": snappedf(0.4 * (value - 1), 0.1),
		"magic_resist_bonus": 0.5 * (value - 1) + milestones,
	}

static func upgrade(state: PreparationState, hero_id: String) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return {"accepted": false, "reason": "仅营地可升级英雄"}
	var catalog := preload("res://scripts/content/AndroidHeroCatalog.gd")
	if hero_id not in catalog.ids():
		return {"accepted": false, "reason": "英雄资料无效"}
	if hero_id not in state.unlocked_heroes:
		return {"accepted": false, "reason": "英雄尚未解锁"}
	var current_level := level(state, hero_id)
	if is_capped(state, current_level):
		return {"accepted": false, "reason": "英雄等级已达当前版本上限（%d 级）" % level_cap(state)}
	var cost := upgrade_cost(current_level)
	if state.gold < cost:
		return {"accepted": false, "reason": "金币不足，升级需要%d金币" % cost}
	state.gold -= cost
	state.hero_levels[hero_id] = current_level + 1
	return {"accepted": true, "reason": "已升至%d级" % (current_level + 1)}
