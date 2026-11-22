extends RefCounted
## Android TowerRules/TowerFlow and InventoryRules pending grants.
## Mutates only gold and quest_progress. Caller owns combat, story enqueue and
## save/rollback transactions; inventory contents and phase remain untouched.
## Persistent keys: tower_run, tower_highest_floor, level_cap, tower_shard,
## tower_aghs, tower_rapier, pending_shard_heroes, pending_aghanim_heroes,
## pending_rebirth_items. Pending arrays retain duplicates; owned arrays are sets.

const Quests = preload("res://scripts/progression/QuestRules.gd")
const Heroes = preload("res://scripts/content/AndroidHeroCatalog.gd")
const Levels = preload("res://scripts/progression/HeroLevelRules.gd")
const Stats = preload("res://scripts/battle/AndroidLoadoutStats.gd")
const Stories = preload("res://scripts/story/StoryCatalog.gd")
const TOWER_FLOORS := 20
const SIM_TICK_CAP := 20000
const WAYPOINT_FLOORS := [2, 5, 8, 11, 14, 17]

static func cap_after_floor(cleared: int) -> int:
	return Levels.cap_after_floor(cleared)

static func has_waypoint_after_floor(floor: int) -> bool:
	return floor in WAYPOINT_FLOORS

static func enemy_hero_id(floor: int) -> String:
	var ids := Heroes.ids()
	return ids[posmod(floor * 7, ids.size())]

static func enemy_scale(floor: int) -> Dictionary:
	var f := maxi(0, floor - 1)
	return {"health": 1.0 + 0.12 * f, "attack": 1.0 + 0.08 * f,
		"attack_speed": float(f), "armor": 0.5 * f}

static func loot_value(floor: int) -> int:
	return 80 + floor * 60

static func sell_ratio(cleared: int) -> float:
	return minf(1.0, maxi(0, cleared) * 0.10)

static func supply_regen_at_tick(base_regen: float, max_hp: float, tick: int, active: bool) -> float:
	# Android ticks are 100 ms; caller converts its engine clock to this unit.
	return base_regen + (max_hp * 0.01 if active and tick >= 0 and tick < 600 else 0.0)

static func rewards_for_floor(floor: int) -> Array:
	var rewards: Array = []
	if floor == 5: rewards.append("SHARD")
	if floor >= 11 and floor <= 20: rewards.append("REBIRTH_ITEM")
	if floor == 15: rewards.append("AGHANIM")
	if floor == 20: rewards.append("RAPIER")
	return rewards

static func locked_reason(state: PreparationState) -> String:
	var done := Quests.completed_nodes(state.quest_progress)
	return "" if done >= 24 else "转生爬塔需完成全部 24 个任务节点（当前 %d/24）" % done

static func is_unlocked(state: PreparationState) -> bool:
	return locked_reason(state).is_empty()

static func enter(state: PreparationState) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _fail("仅营地可进入转生爬塔")
	# JourneyState is owned outside this API; HOME is its authoritative phase.
	if state.quest_progress.get("tower_run") != null:
		return _fail("爬塔轮次已在进行")
	var locked := locked_reason(state)
	if not locked.is_empty(): return _fail(locked)
	if state.active_hero_id not in state.unlocked_heroes:
		return _fail("英雄尚未解锁")
	var progress := state.quest_progress.duplicate(true)
	progress["tower_run"] = {"floor": 1, "hero_id": state.active_hero_id,
		"rng": int(progress.get("seed", 1729)), "cleared_floors": 0, "loot": [],
		"current_hp": null, "pending_supply_regen": false, "tower_primary_bonus": 0,
		"pending_waypoint": false, "battle_started": false, "battle_snapshot": {},
		"battle_max_hp": 0.0, "supply_active": false}
	state.quest_progress = progress
	return _ok({"hero_id": state.active_hero_id, "story_ids": ["T00"]})

static func begin(state: PreparationState, max_hp: float = -1.0, battle_snapshot: Dictionary = {}) -> Dictionary:
	return prepare(state, max_hp, battle_snapshot)

static func prepare(state: PreparationState, max_hp: float = -1.0, battle_snapshot: Dictionary = {}) -> Dictionary:
	var run: Dictionary = state.quest_progress.get("tower_run", {})
	if run.is_empty(): return _fail("没有进行中的爬塔轮次")
	if state.phase != PreparationState.Phase.HOME: return _fail("仅营地可挑战爬塔楼层")
	if bool(run.get("pending_waypoint", false)): return _fail("请先选择道中节点")
	if bool(run.get("battle_started", false)): return _fail("当前爬塔战斗已开始")
	var full := _max_hp(state, run, max_hp)
	if not is_finite(full) or full <= 0: return _fail("最大生命值无效")
	var hp: float = full if run.get("current_hp") == null else clampf(float(run.current_hp), 0.0, full)
	if not is_finite(hp): return _fail("生命值无效")
	var next := run.duplicate(true)
	next.rng = (clampi(int(run.rng), 1, 2147483646) * 48271) % 2147483647
	next.current_hp = hp
	next.battle_started = true
	next.battle_snapshot = battle_snapshot.duplicate(true)
	next.battle_max_hp = full
	next.supply_active = bool(run.get("pending_supply_regen", false))
	next.pending_supply_regen = false
	_put_run(state, next)
	return _ok({"floor": int(next.floor), "hero_id": str(next.hero_id), "rng": int(next.rng),
		"current_hp": hp, "max_hp": full, "hp_ratio": hp / full,
		"tower_primary_bonus": int(next.tower_primary_bonus), "supply_active": bool(next.supply_active),
		"enemy_hero_id": enemy_hero_id(int(next.floor)), "enemy_scale": enemy_scale(int(next.floor))})

static func settle(state: PreparationState, victory: bool, surviving_hp: float = -1.0, max_hp: float = -1.0) -> Dictionary:
	var run: Dictionary = state.quest_progress.get("tower_run", {})
	if run.is_empty(): return _fail("没有进行中的爬塔轮次")
	if not bool(run.get("battle_started", false)): return _fail("当前没有待结算的爬塔战斗")
	var full := max_hp if max_hp != -1.0 else float(run.get("battle_max_hp", 0.0))
	if full <= 0: full = _max_hp(state, run, -1.0)
	var hp: float = (float(run.current_hp) if run.get("current_hp") != null else full) if surviving_hp == -1.0 else surviving_hp
	if not is_finite(full) or full <= 0 or not is_finite(hp): return _fail("生命值无效")
	var floor := int(run.floor)
	var cleared := int(run.cleared_floors) + (1 if victory else 0)
	var loot: Array = run.loot.duplicate(true)
	if victory: loot.append({"definition_id": "tower_f%d" % floor, "star": 1, "value": loot_value(floor)})
	var progress := state.quest_progress.duplicate(true)
	var highest := maxi(int(progress.get("tower_highest_floor", 0)), cleared)
	progress["tower_highest_floor"] = highest
	progress["level_cap"] = cap_after_floor(highest)
	progress.erase("tower_run")
	var rewards := rewards_for_floor(floor) if victory else []
	for reward in rewards:
		match reward:
			"SHARD": _append(progress, "pending_shard_heroes", str(run.hero_id))
			"AGHANIM": _append(progress, "pending_aghanim_heroes", str(run.hero_id))
			"REBIRTH_ITEM": _append(progress, "pending_rebirth_items", "rebirth_item")
			"RAPIER": progress["tower_rapier"] = true
	var final_clear := victory and floor >= TOWER_FLOORS
	var value := 0
	for item in loot: value += int(item.value)
	var gain := value if final_clear else int(floorf(value * sell_ratio(cleared) + 0.5)) if not victory else 0
	if victory and not final_clear:
		var next := run.duplicate(true)
		next.floor = floor + 1
		next.cleared_floors = cleared
		next.loot = loot
		next.current_hp = clampf(hp + full * 0.04, 0.0, full)
		next.pending_waypoint = has_waypoint_after_floor(floor)
		next.battle_started = false
		next.battle_snapshot = {}
		next.battle_max_hp = 0.0
		next.supply_active = false
		progress["tower_run"] = next
	state.quest_progress = progress
	state.gold += gain
	var story_ids: Array = []
	if victory:
		if floor in [5, 10, 11, 15, 20]: story_ids.append("T%02d" % floor)
		if final_clear:
			var ending := Stories.ending(str(run.hero_id))
			if not ending.is_empty(): story_ids.append(str(ending.id))
	return _ok({"hero_id": str(run.hero_id), "gold_gain": gain, "finished": not victory or final_clear,
		"victory": victory, "cleared_floor": cleared, "rewards": rewards, "story_ids": story_ids})

static func waypoint(state: PreparationState, choice: String, max_hp: float = -1.0, next_max_hp: float = -1.0) -> Dictionary:
	var run: Dictionary = state.quest_progress.get("tower_run", {})
	if run.is_empty(): return _fail("没有进行中的爬塔轮次")
	if not bool(run.get("pending_waypoint", false)) or bool(run.get("battle_started", false)):
		return _fail("当前没有待选择的道中节点")
	choice = choice.to_upper()
	if choice not in ["REST", "SUPPLY", "FORGE"]: return _fail("道中选择无效")
	var full := _max_hp(state, run, max_hp)
	if not is_finite(full) or full <= 0: return _fail("最大生命值无效")
	var hp: float = full if run.get("current_hp") == null else float(run.current_hp)
	if not is_finite(hp): return _fail("生命值无效")
	var next := run.duplicate(true)
	match choice:
		"REST": next.current_hp = minf(full, hp + full * 0.30)
		"SUPPLY": next.pending_supply_regen = true
		"FORGE":
			next.tower_primary_bonus = mini(48, int(run.tower_primary_bonus) + 8)
			var next_full := _max_hp(state, next, next_max_hp)
			if not is_finite(next_full) or next_full <= 0: return _fail("锻造后最大生命值无效")
			next.current_hp = hp / full * next_full
	next.pending_waypoint = false
	_put_run(state, next)
	return _ok({"hero_id": str(next.hero_id), "choice": choice, "current_hp": next.current_hp,
		"tower_primary_bonus": int(next.tower_primary_bonus), "pending_supply_regen": bool(next.pending_supply_regen)})

static func grant(state: PreparationState, kind: String, hero_id: String) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME: return _fail("仅营地可授予强化")
	kind = kind.to_upper()
	if kind not in ["SHARD", "AGHANIM"]: return _fail("授予类型无效")
	var pending_key := "pending_shard_heroes" if kind == "SHARD" else "pending_aghanim_heroes"
	var owned_key := "tower_shard" if kind == "SHARD" else "tower_aghs"
	var pending: Array = state.quest_progress.get(pending_key, []).duplicate()
	if hero_id not in pending: return _fail("没有该英雄待授予的强化")
	pending.remove_at(pending.find(hero_id))
	var progress := state.quest_progress.duplicate(true)
	var owned: Array = progress.get(owned_key, [])
	var already_owned := hero_id in owned
	if not already_owned: owned.append(hero_id)
	progress[pending_key] = pending
	progress[owned_key] = owned
	state.quest_progress = progress
	return _ok({"hero_id": hero_id, "kind": kind, "already_owned": already_owned})

static func _max_hp(state: PreparationState, run: Dictionary, supplied: float) -> float:
	if supplied != -1.0: return supplied
	# A read-only loadout view binds the original run hero even after home switching.
	var view := PreparationState.new(str(run.hero_id))
	view.items = state.items
	view.equipment_slots = state.equipment_slots
	view.card_slots = state.card_slots
	view.hero_levels = state.hero_levels
	view.quest_progress = state.quest_progress
	return float(Stats.calculate(view, int(run.tower_primary_bonus)).hp)

static func _append(progress: Dictionary, key: String, value: String) -> void:
	var entries: Array = progress.get(key, [])
	entries.append(value)
	progress[key] = entries

static func _put_run(state: PreparationState, run: Dictionary) -> void:
	var progress := state.quest_progress.duplicate(true)
	progress["tower_run"] = run
	state.quest_progress = progress

static func _ok(metadata: Dictionary = {}) -> Dictionary:
	var result := {"accepted": true, "reason": "", "story_ids": []}
	result.merge(metadata, true)
	return result

static func _fail(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason, "story_ids": []}
