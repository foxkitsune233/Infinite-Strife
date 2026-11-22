class_name QuestRules
extends RefCounted
## Pure Android quest progression. Reward application belongs to the caller transaction.

const Catalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")

static func record(progress: Dictionary, event: Dictionary) -> Dictionary:
	var next := progress.duplicate(true)
	var counters: Dictionary = next.get("quest_counters", {})
	var done := _completed_node_set(progress)
	for quest in Catalog.all():
		if _closed(progress, quest, done):
			continue
		var objective: Dictionary = quest.objective
		var before := int(counters.get(quest.id, 0))
		var target := int(objective.target)
		if before >= target:
			continue
		var delta := _event_delta(objective, event, before)
		if delta > 0:
			# Clamp before adding, including untrusted large imported/custom counter values.
			counters[quest.id] = mini(maxi(before, 0), target) + mini(delta, target - maxi(before, 0))
	if not counters.is_empty() or next.has("quest_counters"):
		next["quest_counters"] = counters
	return next

static func observe(progress: Dictionary, milestones: Dictionary) -> Dictionary:
	var next := progress.duplicate(true)
	var counters: Dictionary = next.get("quest_counters", {})
	var done := _completed_node_set(progress)
	for quest in Catalog.all():
		if _closed(progress, quest, done):
			continue
		var value := mini(_milestone_value(quest.objective, milestones), int(quest.objective.target))
		if value > int(counters.get(quest.id, 0)):
			counters[quest.id] = value
	if not counters.is_empty() or next.has("quest_counters"):
		next["quest_counters"] = counters
	return next

static func visible(progress: Dictionary) -> Array[Dictionary]:
	var done := _completed_node_set(progress)
	var current := 0
	var result: Array[Dictionary] = []
	for quest in Catalog.all():
		if done.has(quest.node):
			continue
		if current == 0:
			current = quest.node
		if quest.node != current:
			break
		result.append(quest)
	return result

static func claim(progress: Dictionary, quest_id: String, context: Dictionary) -> Dictionary:
	if context.get("at_home", false) != true:
		return _rejected(progress, "仅营地可领取任务奖励")
	var quest := Catalog.by_id(quest_id)
	if quest.is_empty():
		return _rejected(progress, "任务不存在")
	var claimed: Array = progress.get("claimed_quests", [])
	if quest_id in claimed:
		return _rejected(progress, "任务奖励已领取")
	var done := _completed_node_set(progress)
	if done.has(quest.node):
		return _rejected(progress, "本节点已选择其他任务并领取奖励")
	for node in range(1, int(quest.node)):
		if not done.has(node):
			return _rejected(progress, "请先领取前一节点奖励")
	var counters: Dictionary = progress.get("quest_counters", {})
	if int(counters.get(quest_id, 0)) < int(quest.objective.target):
		return _rejected(progress, "任务尚未完成")
	# All 40 Android definitions have min_clears=0 and no explicit prerequisites.
	# Node order above is their prerequisite policy. Observe milestones before claim.
	var next := progress.duplicate(true)
	var next_claimed: Array = next.get("claimed_quests", [])
	next_claimed.append(quest_id)
	next["claimed_quests"] = next_claimed
	return {"accepted": true, "reason": "", "progress": next, "rewards": quest.rewards.duplicate(true)}

static func completed_nodes(progress: Dictionary) -> int:
	return _completed_node_set(progress).size()

static func max_difficulty(progress: Dictionary) -> int:
	var count := completed_nodes(progress)
	var result := 0
	for difficulty in range(Catalog.REQUIRED_NODES.size()):
		if count >= Catalog.REQUIRED_NODES[difficulty]:
			result = difficulty
	return result

static func locked_reason(progress: Dictionary, difficulty: int) -> String:
	if difficulty < 0 or difficulty >= Catalog.REQUIRED_NODES.size():
		return "副本难度无效"
	var count := completed_nodes(progress)
	var required: int = Catalog.REQUIRED_NODES[difficulty]
	if count >= required:
		return ""
	return "%s需领取%d个任务节点奖励（当前%d/24）" % [Catalog.DIFFICULTY_NAMES[difficulty], required, count]

static func _completed_node_set(progress: Dictionary) -> Dictionary:
	var result := {}
	var claimed: Array = progress.get("claimed_quests", [])
	for quest in Catalog.all():
		if quest.id in claimed:
			result[quest.node] = true
	return result

static func _closed(progress: Dictionary, quest: Dictionary, done: Dictionary) -> bool:
	return quest.id in progress.get("claimed_quests", []) or done.has(quest.node)

static func _rejected(progress: Dictionary, reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason, "progress": progress.duplicate(true), "rewards": []}

static func _event_delta(objective: Dictionary, event: Dictionary, before: int) -> int:
	var type: String = objective.type
	match str(event.get("type", "")):
		"Entered":
			return 1 if type == "ENTER_RUN" else 0
		"LootPicked":
			return 1 if type == "PICKUP_LOOT" else 0
		"BattleWon":
			return 1 if type == "WIN_BATTLES" or (type == "HERO_WINS" and objective.hero_id == event.get("hero_id")) else 0
		"RunCleared":
			return 1 if type == "CLEAR_COUNT" or (type == "CLEAR_DIFFICULTY" and int(event.get("difficulty", -1)) >= int(objective.difficulty)) else 0
		"Extracted":
			return 1 if type == "EXTRACT_COUNT" else 0
		"EventChosen":
			return 1 if type == "EVENT_CHOICES" else 0
		"ShopPurchase":
			return 1 if type == "SHOP_PURCHASE" else 0
		"Crafted":
			return 1 if type == "CRAFT_COUNT" else 0
		"ConsumableUsed":
			return 1 if type == "CONSUMABLE_USED" else 0
		"GuardedDeath":
			return 1 if type == "GUARDED_DEATH" else 0
		"MaterialGained":
			return maxi(0, int(event.get("count", 0))) if type == "MATERIAL_GAINED" and objective.material_id == event.get("material_id") else 0
		"HeroLevelChanged":
			if type == "HERO_LEVEL" and (objective.hero_id == null or objective.hero_id == event.get("hero_id")) and int(event.get("level", 0)) >= int(objective.target):
				return int(objective.target) - before
	return 0

static func _milestone_value(objective: Dictionary, milestones: Dictionary) -> int:
	match objective.type:
		"EQUIP_ITEM":
			return 1 if milestones.get("equip_item", false) else 0
		"SLOT_CARD":
			return 1 if milestones.get("slot_card", false) else 0
		"STORE_SAFE":
			return 1 if milestones.get("store_safe", false) else 0
		"BACKPACK_LEVEL":
			return int(milestones.get("backpack_level", 0))
		"SAFE_LEVEL":
			return int(milestones.get("safe_level", 0))
		"SKILL_LEVEL":
			return maxi(0, int(milestones.get("skill_slots", 4)) - 4)
		"CLEAR_COUNT":
			return int(milestones.get("clears", 0))
		"HERO_LEVEL":
			var levels: Dictionary = milestones.get("hero_levels", {})
			if objective.hero_id != null:
				return int(levels.get(objective.hero_id, 1))
			var best := 1
			for level in levels.values():
				best = maxi(best, int(level))
			return best
	return 0
