class_name ProgressionRules
extends RefCounted

const Quests = preload("res://scripts/progression/QuestRules.gd")
const Levels = preload("res://scripts/progression/HeroLevelRules.gd")

static func observe(state: PreparationState) -> void:
	var equipped := false
	for index in mini(6, state.equipment_slots.size()):
		equipped = equipped or not state.equipment_slots[index].is_empty()
	var safe := false
	for instance in state.items.values():
		safe = safe or instance.location == ItemInstance.Location.SAFE
	var backpack_sizes := [Vector2i(4, 2), Vector2i(4, 3), Vector2i(5, 3), Vector2i(6, 3), Vector2i(6, 4)]
	var safe_sizes := [Vector2i(2, 2), Vector2i(3, 2), Vector2i(3, 3)]
	state.quest_progress = Quests.observe(state.quest_progress, {
		"equip_item": equipped, "slot_card": state.card_slots.any(func(id: String) -> bool: return not id.is_empty()),
		"store_safe": safe, "backpack_level": backpack_sizes.find(state.backpack_size),
		"safe_level": safe_sizes.find(state.safe_size), "hero_levels": state.hero_levels,
		"skill_slots": int(state.quest_progress.get("enabled_card_slots", 4)),
		"clears": int(state.quest_progress.get("clears", 0)),
	})

static func record(state: PreparationState, event: Dictionary) -> void:
	state.quest_progress = Quests.record(state.quest_progress, event)
	if str(event.get("type", "")) == "RunCleared":
		state.quest_progress["clears"] = int(state.quest_progress.get("clears", 0)) + 1

static func claim(state: PreparationState, quest_id: String) -> Dictionary:
	var previous_progress := state.quest_progress.duplicate(true)
	observe(state)
	var result := Quests.claim(state.quest_progress, quest_id, {"at_home": state.phase == PreparationState.Phase.HOME})
	if not result.accepted:
		state.quest_progress = previous_progress
		return result
	# Stage every reward before mutating the state or publishing the claim.
	var gold := state.gold
	var heroes := state.unlocked_heroes.duplicate()
	var pending: Array[ItemInstance] = []
	var serial := state.progression_serial
	for reward in result.rewards:
		match str(reward.type):
			"GOLD":
				gold += int(reward.amount)
			"HERO_UNLOCK":
				if str(reward.hero_id) in heroes:
					gold += int(reward.amount)
				else:
					heroes.append(str(reward.hero_id))
			"ITEM", "MATERIAL", "GUARD_CORE":
				var catalog := preload("res://scripts/content/AndroidContent.gd")
				var definition: Dictionary = catalog.item(str(reward.material_id))
				if definition.is_empty():
					state.quest_progress = previous_progress
					return {"accepted": false, "reason": "奖励配置缺失：%s" % str(reward.material_id)}
				for ignored in int(reward.amount):
					serial += 1
					var instance_id := "quest_reward_%d" % serial
					while state.items.has(instance_id):
						serial += 1
						instance_id = "quest_reward_%d" % serial
					var instance := catalog.create_instance(instance_id, str(reward.material_id))
					pending.append(instance)
			_:
				state.quest_progress = previous_progress
				return {"accepted": false, "reason": "尚不支持该奖励类型"}
	if gold > 2147483647 or gold < 0 or state.items.size() + pending.size() > 10000:
		state.quest_progress = previous_progress
		return {"accepted": false, "reason": "金币或仓库容量达到上限"}
	state.gold = gold
	state.unlocked_heroes.assign(heroes)
	for instance in pending:
		state.add_item(instance)
	state.progression_serial = serial
	state.quest_progress = result.progress
	return {"accepted": true, "reason": "任务奖励已领取"}
