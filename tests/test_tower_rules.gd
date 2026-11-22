extends SceneTree

const Tower = preload("res://scripts/tower/TowerRules.gd")
const Catalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")
const Codec = preload("res://scripts/save/SaveCodec.gd")

func check(value: bool, message: String) -> void:
	if not value:
		push_error(message)
		quit(1)
		assert(value, message)

func unlocked() -> PreparationState:
	var state := PreparationState.new("juggernaut")
	var nodes := {}
	var claimed: Array = []
	for quest in Catalog.all():
		if not nodes.has(quest.node):
			nodes[quest.node] = true
			claimed.append(quest.id)
	state.quest_progress["claimed_quests"] = claimed
	state.gold = 0
	return state

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	check(not Tower.enter(state).accepted, "fresh tower locked")
	state = unlocked()
	state.quest_progress.claimed_quests.pop_back()
	check(not Tower.enter(state).accepted, "23 claimed nodes insufficient")
	state = unlocked()
	state.phase = PreparationState.Phase.JOURNEY
	check(not Tower.enter(state).accepted, "journey blocks tower")
	state.phase = PreparationState.Phase.HOME
	state.unlocked_heroes.erase("juggernaut")
	check(not Tower.enter(state).accepted, "locked active hero rejected")
	state.unlocked_heroes.append("juggernaut")
	check(Tower.enter(state).accepted, "24 claimed nodes unlock tower")
	check(state.phase == PreparationState.Phase.HOME, "tower retains home access")
	check(state.quest_progress.tower_run.current_hp == null, "first battle full health sentinel")
	check(not Tower.enter(state).accepted, "duplicate entry rejected")
	check(not Tower.settle(state, true, 400, 1000).accepted, "cannot settle before battle")
	var prepared: Dictionary = Tower.prepare(state, 1000)
	check(prepared.accepted and prepared.current_hp == 1000 and prepared.rng == 83460559, "prepare full health and Android RNG")
	check(not Tower.prepare(state, 1000).accepted, "cannot start twice")
	check(Tower.settle(state, true, 400, 1000).accepted, "floor one victory")
	check(is_equal_approx(state.quest_progress.tower_run.current_hp, 440.0), "four percent march recovery")
	check(not Tower.settle(state, true, 400, 1000).accepted, "cannot settle twice")
	check(Tower.prepare(state, 1200).current_hp == 440, "health absolute across changed maximum")
	check(Tower.settle(state, true, 500, 1200).accepted, "floor two victory")
	check(state.quest_progress.tower_run.pending_waypoint, "floor two waypoint mandatory")
	check(not Tower.prepare(state, 1200).accepted, "waypoint blocks next battle")
	var before: Dictionary = state.quest_progress.duplicate(true)
	check(not Tower.waypoint(state, "UNKNOWN", 1200).accepted and state.quest_progress == before, "invalid choice leaves state unchanged")
	check(Tower.waypoint(state, "REST", 1200).accepted, "rest choice")
	check(is_equal_approx(state.quest_progress.tower_run.current_hp, 908.0), "rest heals thirty percent")
	check(not Tower.waypoint(state, "REST", 1200).accepted, "waypoint once only")
	state.quest_progress = before.duplicate(true)
	check(Tower.waypoint(state, "FORGE", 1200, 1440).accepted, "forge choice")
	check(state.quest_progress.tower_run.tower_primary_bonus == 8, "forge adds eight")
	check(is_equal_approx(state.quest_progress.tower_run.current_hp, 657.6), "forge preserves health ratio")
	state.quest_progress = before.duplicate(true)
	check(Tower.waypoint(state, "SUPPLY", 1200).accepted, "supply choice")
	prepared = Tower.prepare(state, 1200, {"tick": 42})
	check(prepared.supply_active and not state.quest_progress.tower_run.pending_supply_regen, "supply consumed for next battle")
	var decoded: Dictionary = Codec.decode(Codec.encode(state, JourneyState.new(7001)))
	check(decoded.accepted and decoded.preparation.quest_progress.tower_run.battle_started, "battle flag restores")
	check(decoded.preparation.quest_progress.tower_run.battle_snapshot.tick == 42, "snapshot restores")
	check(decoded.preparation.quest_progress.tower_run.supply_active, "active supply restores")
	check(Tower.settle(state, true, 500, 1200).accepted, "supplied battle settles")
	check(not Tower.prepare(state, 1200).supply_active, "supply not repeated")
	check(Tower.supply_regen_at_tick(3, 1000, 0, true) == 13, "supply starts tick zero")
	check(Tower.supply_regen_at_tick(3, 1000, 599, true) == 13, "supply last tick")
	check(Tower.supply_regen_at_tick(3, 1000, 600, true) == 3, "supply ends tick six hundred")
	check(Tower.supply_regen_at_tick(3, 1000, 0, false) == 3, "inactive supply no effect")
	check(Tower.enemy_hero_id(1) == "axe" and Tower.enemy_hero_id(2) == "necrophos", "deterministic Android enemy order")
	var scale: Dictionary = Tower.enemy_scale(20)
	check(is_equal_approx(scale.health, 3.28) and is_equal_approx(scale.attack, 2.52), "enemy health attack scale")
	check(scale.attack_speed == 19 and scale.armor == 9.5, "enemy speed armor additive")
	_test_full_run()
	_test_death_and_grants()
	_test_boundaries_and_disk_resume()
	print("Tower rules: gates, RNG, HP, waypoints, supply, 20 floors, rewards, grants, death, snapshots and restore passed")
	quit()

func _test_full_run() -> void:
	var state := unlocked()
	check(Tower.enter(state).story_ids == ["T00"], "entry story metadata")
	for floor in range(1, 21):
		check(Tower.begin(state, 1000).accepted, "begin floor %d" % floor)
		var result: Dictionary = Tower.settle(state, true, 500, 1000)
		check(result.accepted, "win floor %d" % floor)
		if floor in [5, 10, 11, 15]:
			check(result.story_ids == ["T%02d" % floor], "victory milestone story metadata")
		elif floor != 20:
			check(result.story_ids.is_empty(), "ordinary floor has no milestone story")
		check(state.quest_progress.tower_highest_floor == floor, "history matches clears")
		if floor < 20:
			var run: Dictionary = state.quest_progress.tower_run
			check(run.floor == floor + 1 and run.cleared_floors == floor, "advance one floor")
			check(run.pending_waypoint == (floor in [2, 5, 8, 11, 14, 17]), "exact waypoint schedule")
			if run.pending_waypoint:
				check(Tower.waypoint(state, "FORGE", 1000, 1000).accepted, "choose forge")
		else:
			check(result.finished and result.gold_gain == 14200, "full loot payout")
			check(result.story_ids == ["T20", "E01"], "final hero ending metadata")
	check(not state.quest_progress.has("tower_run") and state.gold == 14200, "clear ends tower and banks loot")
	check(state.quest_progress.level_cap == 100 and state.quest_progress.tower_rapier, "cap and rapier permanent")
	check(state.quest_progress.pending_rebirth_items.size() == 10, "rebirth items floors eleven through twenty")
	check(state.quest_progress.pending_shard_heroes == ["juggernaut"], "shard pending once")
	check(state.quest_progress.pending_aghanim_heroes == ["juggernaut"], "aghanim pending once")
	check(state.quest_progress.get("tower_shard", []).is_empty(), "no automatic shard grant")
	check(not Tower.settle(state, true, 500, 1000).accepted, "no duplicate final reward")
	var restored: Dictionary = Codec.decode(Codec.encode(state, JourneyState.new(7001)))
	check(restored.accepted and restored.preparation.quest_progress == state.quest_progress, "all permanent tower data restores")
	check(Tower.enter(state).accepted and state.quest_progress.tower_rapier, "reentry preserves grants")
	check(state.quest_progress.tower_run.tower_primary_bonus == 0, "forge bonus resets on new run")

func _test_death_and_grants() -> void:
	var state := unlocked()
	var sword := ItemInstance.new("original", "broadsword", ItemInstance.Kind.EQUIPMENT)
	sword.location = ItemInstance.Location.EQUIPMENT
	sword.slot = 0
	state.add_item(sword)
	state.equipment_slots[0] = sword.id
	check(Tower.enter(state).accepted, "death fixture enter")
	state.quest_progress.tower_run.floor = 8
	state.quest_progress.tower_run.cleared_floors = 7
	state.quest_progress.tower_run.loot = [{"definition_id": "a", "star": 1, "value": 1000}, {"definition_id": "b", "star": 1, "value": 500}]
	state.quest_progress["tower_highest_floor"] = 12
	state.quest_progress["level_cap"] = 65
	state.quest_progress["pending_shard_heroes"] = ["viper", "viper"]
	state.quest_progress["pending_aghanim_heroes"] = ["abaddon"]
	check(Tower.prepare(state, 1000).accepted, "prepare death")
	var result: Dictionary = Tower.settle(state, false, 0, 1000)
	check(result.accepted and result.gold_gain == 1050 and state.gold == 1050, "death sells seventy percent")
	check(state.items.original == sword and sword.location == ItemInstance.Location.EQUIPMENT and state.equipment_slots[0] == "original", "death preserves original equipment and placement")
	check(state.quest_progress.tower_highest_floor == 12 and state.quest_progress.level_cap == 65, "death retains historical maximum")
	check(state.quest_progress.pending_shard_heroes == ["viper", "viper"], "death retains pending duplicates")
	check(Tower.grant(state, "SHARD", "viper").accepted, "grant matching hero")
	check(state.quest_progress.tower_shard == ["viper"] and state.quest_progress.pending_shard_heroes == ["viper"], "consume one pending entry")
	check(Tower.grant(state, "SHARD", "viper").accepted and state.quest_progress.tower_shard == ["viper"], "duplicate consumed without stacking")
	check(not Tower.grant(state, "SHARD", "viper").accepted, "empty pool rejected")
	check(not Tower.grant(state, "AGHANIM", "viper").accepted, "wrong hero rejected")
	state.phase = PreparationState.Phase.BATTLE
	check(not Tower.grant(state, "AGHANIM", "abaddon").accepted, "grant only home")
	state.phase = PreparationState.Phase.HOME
	check(Tower.grant(state, "AGHANIM", "abaddon").accepted and state.quest_progress.tower_aghs == ["abaddon"], "permanent aghanim flag")
	check(Tower.enter(state).accepted, "reentry after death")
	check(state.quest_progress.tower_shard == ["viper"] and state.quest_progress.tower_aghs == ["abaddon"], "owned grants survive reentry")
	check(Tower.prepare(state, 1000).accepted, "prepare second death")
	check(Tower.settle(state, false, 0, 1000).gold_gain == 0, "first floor death has no sale")
	check(state.quest_progress.tower_shard == ["viper"] and state.quest_progress.tower_aghs == ["abaddon"], "owned grants survive death")

func _test_boundaries_and_disk_resume() -> void:
	for pair in [[0, 10], [1, 10], [2, 15], [18, 95], [19, 100], [20, 100]]:
		check(Tower.cap_after_floor(pair[0]) == pair[1], "cap boundary %d" % pair[0])
	check(Tower.sell_ratio(0) == 0 and Tower.sell_ratio(10) == 1 and Tower.sell_ratio(19) == 1, "sale fraction capped at full value")
	check(Tower.loot_value(1) == 140 and Tower.loot_value(20) == 1280, "loot value endpoints")
	var state := unlocked()
	state.active_hero_id = "abaddon"
	check(Tower.enter(state).accepted, "abaddon starts bound run")
	state.active_hero_id = "skywrath"
	var prepared: Dictionary = Tower.prepare(state)
	check(prepared.hero_id == "abaddon" and prepared.max_hp == 1116, "fallback maximum binds original hero")
	var before: Dictionary = state.quest_progress.duplicate(true)
	check(not Tower.settle(state, true, NAN, 1116).accepted, "invalid HP rejects settlement")
	check(state.quest_progress == before, "invalid HP rejection atomic")
	check(Tower.settle(state, true, 500).accepted, "fallback uses recorded combat maximum")
	state.quest_progress.tower_run.pending_waypoint = true
	var hp: float = state.quest_progress.tower_run.current_hp
	check(Tower.waypoint(state, "FORGE").accepted, "forge computes next maximum when omitted")
	check(is_equal_approx(state.quest_progress.tower_run.current_hp, hp / 1116.0 * 1292.0), "forge fallback respects STR health increase")
	state.quest_progress.tower_run.pending_waypoint = true
	state.quest_progress.tower_run.tower_primary_bonus = 48
	check(Tower.waypoint(state, "FORGE", 1000, 1000).accepted, "forge at cap allowed")
	check(state.quest_progress.tower_run.tower_primary_bonus == 48, "forge cannot exceed forty eight")
	state.quest_progress.tower_run.pending_waypoint = true
	state.quest_progress.tower_run.current_hp = 950.0
	check(Tower.waypoint(state, "REST", 1000).accepted, "rest near maximum")
	check(state.quest_progress.tower_run.current_hp == 1000, "rest cannot overheal")
	state.quest_progress.tower_run.pending_waypoint = true
	check(Tower.waypoint(state, "SUPPLY", 1000).accepted, "supply before disk save")
	check(Tower.prepare(state, 1000, {"player": {"hp": 700}, "tick": 91}).accepted, "prepare saved battle")
	var save = preload("res://scripts/save/SaveRepository.gd")
	var path := "user://test_tower_rules.json"
	save.erase(path)
	check(save.write(path, state, JourneyState.new(7001)).accepted, "write tower battle to disk")
	var loaded: Dictionary = save.read(path)
	check(loaded.accepted, "read tower battle from disk")
	var restored: PreparationState = loaded.preparation
	check(restored.quest_progress.tower_run.battle_snapshot.player.hp == 700, "nested battle snapshot restored")
	check(restored.quest_progress.tower_run.battle_started and restored.quest_progress.tower_run.supply_active, "restore in-flight supply battle")
	check(not Tower.prepare(restored, 1000).accepted, "restore does not allow battle restart")
	check(Tower.settle(restored, true, 700, 1000).accepted, "restored battle can settle")
	check(not restored.quest_progress.tower_run.battle_started and restored.quest_progress.tower_run.battle_snapshot.is_empty(), "settlement clears combat snapshot")
	check(restored.quest_progress.tower_run.pending_waypoint, "restored floor two requires waypoint")
	check(restored.quest_progress.tower_run.hero_id == "abaddon", "restore retains bound hero")
	check(restored.quest_progress.tower_run.current_hp == 740, "restore settlement carries absolute health")
	save.erase(path)
