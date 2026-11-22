extends SceneTree

const Catalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")
const Progression = preload("res://scripts/progression/ProgressionRules.gd")
const Growth = preload("res://scripts/progression/AndroidGrowthRules.gd")
# Android commit 255a1b2: node -> hero, duplicate compensation.
const REWARDS := {5: ["axe", 250], 7: ["skywrath", 300], 10: ["viper", 500],
	12: ["anti_mage", 600], 13: ["phantom_assassin", 700], 17: ["necrophos", 900],
	18: ["legion_commander", 1000], 19: ["faceless_void", 1100]}
var checks := 0
var failures := 0

func check(ok: bool, why: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(why)

func ready_state(quest: Dictionary) -> PreparationState:
	var state := PreparationState.new("juggernaut")
	var claimed: Array = []
	var nodes := {}
	for previous in Catalog.all():
		if previous.node < quest.node and not nodes.has(previous.node):
			claimed.append(previous.id)
			nodes[previous.node] = true
	state.quest_progress.claimed_quests = claimed
	state.quest_progress.quest_counters = {quest.id: quest.objective.target}
	return state

func _init() -> void:
	for quest in Catalog.all():
		if not REWARDS.has(quest.node): continue
		var expected: Array = REWARDS[quest.node]
		var heroes: Array = quest.rewards.filter(func(reward: Dictionary) -> bool: return reward.type == "HERO_UNLOCK")
		check(heroes.size() == 1 and heroes[0].hero_id == expected[0] and heroes[0].amount == expected[1], "source reward " + quest.id)
		var state := ready_state(quest)
		check(expected[0] not in state.unlocked_heroes, "hero not granted early")
		check(Progression.claim(state, quest.id).accepted, "claim " + quest.id)
		check(expected[0] in state.unlocked_heroes, "hero granted " + quest.id)
		check(LoadoutRules.activate_hero(state, expected[0]).accepted, "granted hero selectable")
		var gold := state.gold
		check(not Progression.claim(state, quest.id).accepted and state.gold == gold, "duplicate claim rejected")
		var loaded := SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(1)))
		check(loaded.accepted and expected[0] in loaded.preparation.unlocked_heroes, "unlock saved")
		var owned := ready_state(quest)
		owned.unlocked_heroes.append(expected[0])
		var extra_gold := 0
		for reward in quest.rewards:
			if reward.type == "GOLD": extra_gold += int(reward.amount)
		var before := owned.gold
		check(Progression.claim(owned, quest.id).accepted and owned.gold == before + extra_gold + int(expected[1]), "owned hero compensation " + quest.id)
	for entry in Growth.entries():
		check(entry.get("hero_id") not in ["axe", "anti_mage", "legion_commander", "faceless_void"], "no invented purchase route " + entry.id)
	var old := PreparationState.new("juggernaut")
	old.quest_progress.claimed_quests = ["journey.5", "journey.12", "journey.18", "journey.19.2"]
	old.quest_progress.quest_counters = {"journey.17.1": 1}
	var original := SaveCodec.encode(old, JourneyState.new(1))
	var migrated := SaveCodec.decode(original)
	var rollback := SaveCodec.decode(original, false)
	check(rollback.accepted and rollback.preparation.unlocked_heroes == old.unlocked_heroes, "internal rollback skips reward migration")
	check(migrated.accepted, "legacy save loads")
	for hero in ["axe", "anti_mage", "legion_commander", "faceless_void"]:
		check(hero in migrated.preparation.unlocked_heroes, "claimed legacy reward repaired: " + hero)
	check("necrophos" not in migrated.preparation.unlocked_heroes, "unclaimed progress never grants hero")
	check(migrated.preparation.gold == old.gold and migrated.preparation.items.is_empty(), "repair grants no repeated gold/items")
	check(SaveCodec.encode(old, JourneyState.new(1)) == original, "decode does not mutate input state")
	var twice := SaveCodec.decode(SaveCodec.encode(migrated.preparation, JourneyState.new(1)))
	check(twice.preparation.unlocked_heroes == migrated.preparation.unlocked_heroes, "legacy repair is idempotent")
	var malformed := original.duplicate(true)
	malformed.preparation.quest_progress.claimed_quests = "journey.5"
	check(not SaveCodec.decode(malformed).accepted, "invalid receipt type is rejected")
	var session := preload("res://scripts/AppSession.gd").new()
	session.preparation_state = ready_state(Catalog.by_id("journey.5"))
	session.save_path = "user://missing-task-hero-directory/save.json"
	check(not session.claim_quest("journey.5").accepted, "save failure rejects hero reward")
	check("axe" not in session.preparation_state.unlocked_heroes and "journey.5" not in session.preparation_state.quest_progress.claimed_quests, "failure rolls back hero and receipt")
	session.save_path = "user://test_task_hero_transaction.json"
	SaveRepository.erase(session.save_path)
	check(session.claim_quest("journey.5").accepted, "hero reward committed")
	check(session.restore_progress().accepted and "axe" in session.preparation_state.unlocked_heroes, "committed hero survives disk restore")
	check(session.preparation_state.quest_progress.get("story", {}).get("pending", []).has("J05"), "claim preserves task story linkage")
	SaveRepository.erase(session.save_path)
	session.free()
	print("Task hero unlocks: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
