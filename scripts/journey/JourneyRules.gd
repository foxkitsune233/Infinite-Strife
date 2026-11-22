class_name JourneyRules
extends RefCounted

const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")
const AndroidItems = preload("res://scripts/content/AndroidEquipmentCatalog.gd")
const AndroidEffects = preload("res://scripts/battle/AndroidEffectCatalog.gd")
const PackingRules = preload("res://scripts/prepare/PackingRules.gd")
const BATTLE_GOLD := [[20, 30, 45, 60, 80], [30, 45, 65, 90, 120], [45, 65, 90, 125, 165],
	[60, 90, 125, 170, 220], [80, 120, 165, 220, 285], [105, 155, 215, 285, 365]]

static func resolve_current(_state: PreparationState, journey: JourneyState) -> Dictionary:
	if journey.finished():
		return _rejected("远征已经结束")
	if journey.resolved:
		return _rejected("当前节点已经结算")
	var node := journey.current()
	var node_type := int(node.type)
	if node_type == JourneyState.NodeType.EVENT:
		_freeze_event(journey, _state)
		return _accepted("请选择事件处理方式")
	if node_type == JourneyState.NodeType.REST:
		journey.resolved = true
		return _accepted("休息点：继续挑战或安全撤离")
	journey.resolved = true
	_add_loot(journey, _drops_for(node_type, journey.current_node))
	return _accepted("掉落已出现，可直接手动放入背包")

static func begin_battle(journey: JourneyState) -> Dictionary:
	if journey.finished() or int(journey.current().type) not in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
		return _rejected("当前不是普通战斗节点")
	if journey.resolved or journey.battle_started:
		return _rejected("当前战斗已经开始或结算")
	journey.battle_started = true
	return _accepted("战斗开始")

static func complete_battle(_state: PreparationState, journey: JourneyState, victory: bool) -> Dictionary:
	if journey.finished() or int(journey.current().type) not in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
		return _rejected("当前不是普通战斗节点")
	if not journey.battle_started:
		return _rejected("战斗尚未开始")
	journey.battle_started = false
	journey.battle_snapshot.clear()
	if not victory:
		_end_on_death(_state, journey)
		return _rejected("战斗失败，请重新整备后挑战")
	journey.resolved = true
	var round_index := _round_index(journey)
	_add_loot(journey, battle_drop_ids(_state, journey, round_index))
	var reward := _award_run_gold(_state, journey, int(BATTLE_GOLD[clampi(journey.difficulty, 0, 5)][round_index]))
	return _accepted("战斗胜利，获得 %d 本局金币" % reward)

static func _end_on_death(state: PreparationState, journey: JourneyState) -> void:
	preload("res://scripts/journey/MaterialProgression.gd").snapshot(state, journey)
	journey.ending = "death"
	journey.run_gold = 0
	state.phase = PreparationState.Phase.HOME
	var lost: Array[String] = []
	for item in state.items.values():
		var guarded: bool = (item.location == ItemInstance.Location.EQUIPMENT and item.slot >= 0 and item.slot < int(state.quest_progress.get("equipment_guard_level", 0))) or (item.location == ItemInstance.Location.CARD and item.slot >= 0 and item.slot < int(state.quest_progress.get("skill_guard_level", 0)))
		if not guarded and item.location not in [ItemInstance.Location.WAREHOUSE, ItemInstance.Location.SAFE]:
			lost.append(item.id)
	for id in lost:
		state.remove_item(id)
	return_items_to_warehouse(state)
	preload("res://scripts/journey/MaterialProgression.gd").record(state, journey, false)

static func return_items_to_warehouse(state: PreparationState) -> void:
	state.phase = PreparationState.Phase.HOME
	for id in state.items.keys():
		PackingRules.move_to_warehouse(state, id)

static func use_consumable(state: PreparationState, journey: JourneyState, item_id: String) -> Dictionary:
	if journey.finished() or state.phase != PreparationState.Phase.JOURNEY or journey.battle_started:
		return _rejected("当前不能使用局内恢复品")
	var item := state.item(item_id)
	if item == null or item.location == ItemInstance.Location.WAREHOUSE:
		return _rejected("请选择随身恢复品")
	var healing := {"salve": 180.0, "tango": 70.0, "faerie_fire": 100.0, "cheese": -1.0}
	if not healing.has(item.definition_id):
		return _rejected("此物品不能直接使用")
	if journey.player_hp_ratio <= 0.0:
		return _rejected("死亡后不能使用恢复品")
	var max_hp := float((BattleLoadoutBuilder.build(state).fighter as FighterState).max_hp)
	var ratio := 0.4 if item.definition_id == "cheese" else float(healing[item.definition_id]) / maxf(1.0, max_hp)
	journey.player_hp_ratio = minf(1.0, journey.player_hp_ratio + ratio)
	state.remove_item(item.id)
	return _accepted("已使用恢复品")

static func _round_index(journey: JourneyState) -> int:
	var round_index := -1
	for index in range(journey.current_node + 1):
		if int(journey.nodes[index].type) in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
			round_index += 1
	return clampi(round_index, 0, 4)

static func _award_run_gold(state: PreparationState, journey: JourneyState, amount: int) -> int:
	var reward := mini(amount, maxi(0, 2147483647 - state.gold - journey.run_gold))
	journey.run_gold += reward
	return reward

## 事件奖励池（对齐 Android JourneyEvents.pools）。
## 所有池按 ID 排序并按 clears / difficulty 过滤，读档后可稳定重算。
const MEDICINES := ["salve", "tango", "faerie_fire", "cheese"]
const COMPONENT_EXTRAS := ["iron_branch", "circlet", "gauntlets", "slippers", "mantle", "broadsword"]

static func _item_tier(row: Dictionary) -> int:














	## 对齐 Android ChallengeBalance.equipmentTiers：MAIN / SCROLL 按价格分档。
	## 另外把 inventory.json 中的基础主武器（category 为空但价格 >=100，如 broadsword）
	## 视为一级主装备；纯组件（COMPONENT 及低价基础件）保持 tier 0。
	var raw_category: Variant = row.get("category", "")
	var category := "" if raw_category == null else str(raw_category)
	var price := int(row.get("price", 0))
	if category == "MAIN" or category == "SCROLL":
		if price >= 5500: return 5
		if price >= 4500: return 4
		if price >= 3500: return 3
		if price >= 2500: return 2
		return 1
	if category == "COMPONENT":
		return 0
	if category.is_empty() and str(row.get("kind", "")) == "EQUIPMENT" and price >= 100:
		return 1
	return 0

static func _allows_drop(row: Dictionary, difficulty: int) -> bool:
	## 对齐 Android ChallengeBalance.allowsDrop。版本 3 规则：tier>=4 需难度2，tier>0 需难度1。
	var tier := _item_tier(row)
	if tier >= 4:
		return 2 <= difficulty
	if tier > 0:
		return 1 <= difficulty
	return true

static func _event_pools(state: PreparationState, journey: JourneyState) -> Dictionary:
	var clears := int(state.quest_progress.get("clears", 0))
	var difficulty := journey.difficulty
	var pools := {
		"medicine": [] as Array[String], "component": [] as Array[String], "card": [] as Array[String],
		"general": [] as Array[String], "core": [] as Array[String], "shard": [] as Array[String],
		"rage_card": [] as Array[String], "frost_card": [] as Array[String],
	}
	var card_tags := {}
	for card in AndroidItems.cards():
		card_tags[str(card.id)] = card.get("tags", [])
	for row in AndroidItems.items():
		var id := str(row.id)
		if int(row.get("minimumDropClears", 0)) > clears or not _allows_drop(row, difficulty):
			continue
		var kind := str(row.get("kind", ""))
		if kind == "CONSUMABLE" and id in MEDICINES:
			pools.medicine.append(id)
		elif kind == "EQUIPMENT" and (str(row.get("category", "")) == "COMPONENT" or id in COMPONENT_EXTRAS):
			pools.component.append(id)
		elif kind == "CARD":
			pools.card.append(id)
			var tags: Array = card_tags.get(id, [])
			if "rage" in tags: pools.rage_card.append(id)
			if "frost" in tags: pools.frost_card.append(id)
		elif kind == "MATERIAL":
			if id == "essence": pools.general.append(id)
			elif id == "skill_shard": pools.shard.append(id)
			elif id == "safe_lock" and clears >= 1: pools.core.append(id)
			elif id == "safe_frame" and clears >= 5 and state.backpack_size.y >= 3: pools.core.append(id)
	for key in pools.keys():
		(pools[key] as Array[String]).sort()
	return pools

static func _event_available(pools: Dictionary, option_id: String) -> bool:
	## 对齐 Android JourneyEvents.available：奖励池为空时不提供该分支。
	var medicine: Array = pools.get("medicine", [])
	var component: Array = pools.get("component", [])
	var card: Array = pools.get("card", [])
	var general: Array = pools.get("general", [])
	var core: Array = pools.get("core", [])
	var shard: Array = pools.get("shard", [])
	var rage: Array = pools.get("rage_card", [])
	var frost: Array = pools.get("frost_card", [])
	match option_id:
		"depot.search", "pact_altar.offer_medicine", "alchemy_lab.distill", "hunter_camp.share_rations", "clinic.supply":
			return not medicine.is_empty()
		"workshop.pry", "workshop.pay", "alchemy_lab.break_seal", "hunter_camp.barter", "ropeway_bridge.repair":
			return not component.is_empty()
		"library.delve", "library.study":
			return not card.is_empty()
		"wreckage.core":
			return not core.is_empty()
		"wreckage.salvage", "moonwell_echo.draw_echo", "ropeway_bridge.cross":
			return not general.is_empty()
		"terminal.convert", "terminal.gamble":
			return not shard.is_empty() and not general.is_empty()
		"frozen_shrine.attune":
			return not frost.is_empty()
		"war_banner.claim", "war_banner.blood_rite":
			return not rage.is_empty()
		"roving_caravan.buy":
			return not general.is_empty() or not component.is_empty()
		_:
			return true

static func event_option_price(journey: JourneyState, option: Dictionary) -> int:
	if not bool(option.get("priced", false)):
		return 0
	return 20 + (_round_index(journey) + 1) * 10 + journey.difficulty * 10

static func event_option_block(state: PreparationState, journey: JourneyState, option: Dictionary) -> String:
	if event_option_price(journey, option) > journey.run_gold:
		return "本局金币不足"
	if option.get("id") == "moonwell_echo.resonate":
		var schools: Dictionary = AndroidEffects.for_loadout(state).schools
		if not schools.values().any(func(count: int) -> bool: return count >= 3):
			return "需要同一流派的羁绊达到第二档"
	if option.get("id") == "library.study" and int(AndroidEffects.for_loadout(state).schools.get("ultimate", 0)) < 2:
		return "需要激活大招流派羁绊"
	if option.get("id") == "wreckage.core" and int(state.quest_progress.get("clears", 0)) < 1:
		return "通关后解锁保险箱组件"
	return ""

static func event_deliverable(item: ItemInstance, requirement: String, allow_safe_container: bool = false) -> bool:
	if item == null or item.location == ItemInstance.Location.WAREHOUSE:
		return false
	if item.location == ItemInstance.Location.SAFE and not allow_safe_container:
		return false
	if requirement == "medicine":
		return item.definition_id in ["salve", "tango", "faerie_fire", "cheese"]
	if requirement == "essence":
		return item.definition_id == "essence"
	return requirement == "any"

static func choose_event(state: PreparationState, journey: JourneyState, option_index: int, selected_item_id: String = "", allow_safe_container: bool = false) -> Dictionary:
	if journey.finished() or int(journey.current().type) != JourneyState.NodeType.EVENT:
		return _rejected("当前不是事件节点")
	if journey.resolved:
		return _rejected("当前事件已经结算")
	_freeze_event(journey, state)
	if option_index < 0 or option_index >= journey.frozen_event_options.size():
		return _rejected("无效的事件选项")
	var option := journey.frozen_event_options[option_index]
	var block := event_option_block(state, journey, option)
	if not block.is_empty():
		return _rejected(block)
	var option_id := str(option.id)
	var requirement := str(option.get("item_choice", ""))
	var selected := state.item(selected_item_id)
	if not requirement.is_empty():
		if selected == null:
			return _rejected("请明确选择要交付的物品")
		if selected.location == ItemInstance.Location.SAFE and not allow_safe_container:
			return _rejected("交付安全箱物品需要明确授权")
		if not event_deliverable(selected, requirement, allow_safe_container):
			return _rejected("所选物品不符合要求")
	var outcome := str(option.outcome)
	var guaranteed: Array[String] = []
	var hp_delta := 0.0
	var flat_damage := 0.0
	var roll := _event_roll(journey)
	match option_id:
		"depot.search":
			if roll < 70:
				guaranteed.assign(["salve", "tango", "faerie_fire", "cheese"].slice((roll / 10) % 4, (roll / 10) % 4 + 1))
			else:
				hp_delta = -0.10
		"workshop.pry":
			if roll < 60:
				guaranteed = _event_component(state, journey)
			else:
				hp_delta = -0.12
		"workshop.pay":
			guaranteed = _event_component(state, journey)
		"library.delve", "library.study":
			guaranteed = _event_cards(state, journey, "ruined_library", 1)
			if option_id == "library.delve" and roll >= 60:
				hp_delta = -0.15
		"storage.left":
			hp_delta = -0.10
			if roll < 60:
				guaranteed.assign(["essence"])
		"wreckage.core":
			guaranteed.assign(["safe_lock"])
		"wreckage.salvage":
			guaranteed.assign(["essence"])
		"terminal.convert":
			guaranteed.assign(["skill_shard"])
		"terminal.gamble":
			if roll < 50:
				guaranteed.assign(["skill_shard"])
			else:
				hp_delta = -0.15
		"clinic.gold":
			hp_delta = 0.25
		"clinic.supply":
			hp_delta = 0.40
		"blocker.rush":
			hp_delta = -0.10 if roll < 60 else -0.25
		"moonwell_echo.resonate":
			guaranteed = _event_cards(state, journey, "moonwell_echo", 2)
		"moonwell_echo.draw_echo":
			guaranteed.assign(["essence"])
		"frozen_shrine.attune":
			hp_delta = -0.08
			guaranteed = _event_cards(state, journey, "frozen_shrine", 1, "frost")
		"frozen_shrine.pray":
			hp_delta = 0.06
		"war_banner.claim", "war_banner.blood_rite":
			guaranteed = _event_cards(state, journey, "war_banner", 1, "rage")
			if option_id == "war_banner.blood_rite":
				hp_delta = -0.15
		"ropeway_bridge.cross":
			hp_delta = -0.10
			guaranteed.assign(["essence"])
		"ropeway_bridge.repair":
			guaranteed = _event_component(state, journey)
		"roving_caravan.buy":
			guaranteed.assign(["essence"])
		"hunter_camp.barter":
			guaranteed = _event_component(state, journey)
		"hunter_camp.share_rations":
			hp_delta = 0.30
			guaranteed.assign(["skill_shard"])
		"pact_altar.offer_medicine", "pact_altar.offer_blood":
			guaranteed = _event_cards(state, journey, "pact_altar", 1)
			if option_id == "pact_altar.offer_blood":
				hp_delta = -0.18
		"alchemy_lab.distill":
			hp_delta = 0.25
			guaranteed.assign(["essence"])
		"alchemy_lab.break_seal":
			hp_delta = -0.08
			flat_damage = 9.0
			guaranteed = _event_component(state, journey)
		_:
			match outcome:
				"rare_loot":
					guaranteed.assign(["rime_shard", "storm_core"])
				"material":
					guaranteed.assign(["iron_branch", "sun_thread"])
	preload("res://scripts/progression/ProgressionRules.gd").record(state, {"type": "EventChosen"})
	journey.resolved = true
	journey.event_result = {"choice": str(option.label), "outcome": outcome, "gold": 0, "items": 0}
	if not requirement.is_empty():
		journey.event_result["delivered"] = selected.definition_id
		if option_id == "roving_caravan.sell":
			var definition := ContentCatalog.item(selected.definition_id)
			var sale_price := maxi(1, int(definition.source_data.get("price", 1))) if definition != null else 1
			journey.event_result["gold"] = _award_run_gold(state, journey, sale_price)
		state.remove_item(selected.id)
	if "." in option_id:
		journey.event_result["art_key"] = option_id
	var gold_cost := event_option_price(journey, option)
	if gold_cost > 0:
		journey.run_gold -= gold_cost
		journey.event_result["gold_cost"] = gold_cost
	if hp_delta != 0.0:
		var previous_hp := journey.player_hp_ratio
		var maximum_hp := float((BattleLoadoutBuilder.build(state).fighter as FighterState).max_hp) if flat_damage > 0.0 else 1.0
		journey.player_hp_ratio = clampf(previous_hp + hp_delta - flat_damage / maxf(1.0, maximum_hp), 0.0, 1.0)
		if flat_damage > 0.0:
			journey.event_result["poison_damage"] = int(flat_damage)
		journey.event_result["hp_change_percent"] = roundi((journey.player_hp_ratio - previous_hp) * 100)
		if journey.player_hp_ratio <= 0.0:
			_end_on_death(state, journey)
			return _accepted("生命耗尽，远征失败")
		if guaranteed.is_empty() and hp_delta > 0.0:
			return _accepted("祈祷结束")
	if guaranteed.is_empty():
		if outcome == "sell":
			return _accepted("出售成功，获得 %d 本局金币" % int(journey.event_result.gold))
		return _accepted("你离开了事件，没有获得掉落" if outcome == "leave" else "没有获得掉落")
	var round_index := _round_index(journey)
	_add_loot(journey, _drop_ids(state, journey, round_index, guaranteed))
	var gold := _award_run_gold(state, journey, int(BATTLE_GOLD[clampi(journey.difficulty, 0, 5)][round_index]) >> 1)
	journey.event_result["gold"] = gold
	journey.event_result["items"] = journey.loot.size()
	return _accepted("获得 %d 本局金币，战利品正在鉴定" % gold)

static func _event_roll(journey: JourneyState) -> int:
	var random := clampi(journey.seed, 1, 2147483646)
	var node_key := "%s:%d" % [journey.frozen_event_id, journey.current_node]
	for index in node_key.length():
		random = (random * 48271 + node_key.unicode_at(index)) % 2147483647
	return random % 100

static func _event_component(state: PreparationState, journey: JourneyState) -> Array[String]:
	var pool: Array[String] = []
	var extras := ["iron_branch", "circlet", "gauntlets", "slippers", "mantle", "broadsword"]
	for item in AndroidItems.items():
		if item.kind == "EQUIPMENT" and (item.category == "COMPONENT" or item.id in extras) and int(item.minimumDropClears) <= int(state.quest_progress.get("clears", 0)):
			pool.append(str(item.id))
	pool.sort()
	if pool.is_empty():
		return []
	var random := clampi(journey.seed, 1, 2147483646)
	var node_key := "ropeway_bridge:%d" % journey.current_node
	for index in node_key.length():
		random = (random * 48271 + node_key.unicode_at(index)) % 2147483647
	var selected: Array[String] = [pool[(random % 100 / 10) % pool.size()]]
	return selected

static func _event_cards(state: PreparationState, journey: JourneyState, event_key: String, count: int, school: String = "") -> Array[String]:
	var pool: Array[String] = []
	var preferred: Array[String] = []
	var school_ids := {}
	if not school.is_empty():
		for card in AndroidItems.cards():
			if school in card.tags:
				school_ids[card.id] = true
	var clears := int(state.quest_progress.get("clears", 0))
	for row in AndroidItems.items():
		if row.kind == "CARD" and int(row.minimumDropClears) <= clears:
			pool.append(str(row.id))
			if school_ids.has(row.id):
				preferred.append(str(row.id))
	if not preferred.is_empty():
		pool = preferred
	pool.sort()
	var picked: Array[String] = []
	var random := clampi(journey.seed, 1, 2147483646)
	var node_key := "%s:%d" % [event_key, journey.current_node]
	for index in node_key.length():
		random = (random * 48271 + node_key.unicode_at(index)) % 2147483647
	var start := (random % 100) / 10
	for index in mini(count, pool.size()):
		var candidate := (start + index * 7) % pool.size()
		while pool[candidate] in picked:
			candidate = (candidate + 1) % pool.size()
		picked.append(pool[candidate])
	return picked

static func collect_all(state: PreparationState, journey: JourneyState) -> Dictionary:
	var collected := 0
	for item in journey.loot.duplicate():
		if not item.identified: continue
		if _collect_first_fit(state, item):
			journey.loot.erase(item)
			collected += 1
	if collected > 0:
		preload("res://scripts/progression/ProgressionRules.gd").record(state, {"type": "LootPicked"})
	return {"accepted": collected > 0, "reason": "", "collected": collected, "remaining": journey.loot.size()}

static func collect_one(state: PreparationState, journey: JourneyState, item_id: String, position := Vector2i(-1, -1), rotated := false, location := ItemInstance.Location.BACKPACK) -> Dictionary:
	if journey.finished() or not journey.resolved:
		return _rejected("当前无法拾取")
	for item in journey.loot:
		if item.id != item_id:
			continue
		if not item.identified: return _rejected("物品正在鉴定")
		if state.item(item_id) != null:
			return _rejected("物品已存在")
		if position == Vector2i(-1, -1):
			if not _collect_first_fit(state, item):
				return _rejected("背包没有足够的连续空位")
		else:
			var previous_location := item.location
			item.location = ItemInstance.Location.BACKPACK
			state.add_item(item)
			var result := PackingRules.place(state, item_id, location, position, rotated)
			if not result.accepted:
				state.remove_item(item_id)
				item.location = previous_location
				return result
		journey.loot.erase(item)
		preload("res://scripts/progression/ProgressionRules.gd").record(state, {"type": "LootPicked"})
		return _accepted("已放入安全箱" if location == ItemInstance.Location.SAFE else "已放入背包")
	return _rejected("找不到该掉落")

static func put_down(state: PreparationState, journey: JourneyState, item_id: String) -> Dictionary:
	if journey.finished() or journey.battle_started or not journey.resolved or journey.drop_serial <= 0 or int(journey.current().type) == JourneyState.NodeType.REST:
		return _rejected("当前没有掉落区")
	var item := state.item(item_id)
	if item == null or item.location == ItemInstance.Location.WAREHOUSE:
		return _rejected("只能放下随身物品")
	if journey.loot.any(func(drop: ItemInstance) -> bool: return drop.id == item_id):
		return _rejected("该物品已在地面")
	state.remove_item(item_id)
	item.identified = true
	journey.loot.append(item)
	return _accepted("已放回地面")

static func leave_node(journey: JourneyState) -> Dictionary:
	if journey.finished():
		return _rejected("远征已经结束")
	if not journey.resolved:
		return _rejected("请先完成当前节点")
	if journey.loot.any(func(item: ItemInstance) -> bool: return not item.identified):
		return _rejected("请等待掉落鉴定完成")
	if int(journey.current().type) == JourneyState.NodeType.REST:
		journey.player_hp_ratio = minf(1.0, journey.player_hp_ratio + 0.35)
	var discarded := not journey.loot.is_empty()
	journey.loot.clear()
	journey.reveal_at.clear()
	journey.appraisal_ms = 0.0
	journey.current_node += 1
	journey.resolved = false
	journey.battle_started = false
	journey.frozen_event_id = ""
	journey.event_result.clear()
	journey.frozen_event_options.clear()
	return {"accepted": true, "reason": "", "discarded": discarded, "finished": journey.finished()}

static func extract(state: PreparationState, journey: JourneyState) -> Dictionary:
	if journey.finished() or int(journey.current().type) != JourneyState.NodeType.REST:
		return _rejected("仅第三战后的休息点可安全撤离")
	preload("res://scripts/journey/MaterialProgression.gd").snapshot(state, journey)
	journey.ending = "extracted"
	bank_run_gold(state, journey)
	return_items_to_warehouse(state)
	preload("res://scripts/journey/MaterialProgression.gd").record(state, journey, false)
	state.quest_progress["extracted"] = true
	preload("res://scripts/progression/ProgressionRules.gd").record(state, {"type": "Extracted"})
	return _accepted("已安全撤离，携带物资已保留")

static func bank_run_gold(state: PreparationState, journey: JourneyState) -> void:
	state.gold = mini(2147483647, state.gold + journey.run_gold)
	journey.run_gold = 0

static func _next_rng(journey: JourneyState) -> int:
	## 推进确定性序列（对齐 Android FirstRunContent.next），写回游标以便读档续接。
	var value := clampi(journey.event_rng, 1, 2147483646)
	value = value * 48271 % 2147483647
	journey.event_rng = value
	return value

static func _freeze_event(journey: JourneyState, state: PreparationState) -> void:
	if not journey.frozen_event_id.is_empty():
		return
	var implemented := ["moonwell_echo", "broken_span", "frozen_altar", "banner_ruins", "wandering_caravan",
		"hunter_camp", "alchemist_spill", "pact_altar", "abandoned_depot", "rusted_workshop",
		"ruined_library", "collapsed_storage", "tech_terminal", "field_clinic", "blocker"]
	if int(state.quest_progress.get("clears", 0)) >= 1:
		implemented.append("safe_wreckage")
	# 只纳入至少有一个可操作选项的事件，避免奖励池为空时生成必然空手的事件。
	var pools := _event_pools(state, journey)
	var events: Array[EventDefinition] = []
	for definition in ContentCatalog.events():
		if definition.id not in implemented:
			continue
		var actionable := false
		for option in definition.options:
			if str(option.get("outcome", "")) == "leave":
				continue
			if _event_available(pools, str(option.get("id", ""))):
				actionable = true
				break
		if actionable:
			events.append(definition)
	if events.is_empty():
		events.append(ContentCatalog.event("field_clinic"))
	# 最近两次不重复同类；候选不足时只排除最近一次，再不足则取全集。
	var recent := journey.recent_event_kinds
	var recent_two := recent.slice(maxi(0, recent.size() - 2))
	var candidates := events.filter(func(def: EventDefinition) -> bool: return def.id not in recent_two)
	if candidates.is_empty():
		candidates = events.filter(func(def: EventDefinition) -> bool: return def.id != recent.back() if not recent.is_empty() else true)
	if candidates.is_empty():
		candidates = events
	var picked := _next_rng(journey) % candidates.size()
	var event: EventDefinition = candidates[picked]
	journey.frozen_event_id = event.id
	# 记录本次 kind，仅保留最近两次用于去重。
	journey.recent_event_kinds.append(event.id)
	if journey.recent_event_kinds.size() > 2:
		journey.recent_event_kinds = journey.recent_event_kinds.slice(-2)
	# 冻结奖励 ID：每个选项在生成时算好，读档不重抽（对齐 Android node.outcomes）。
	journey.frozen_event_options = []
	for option in event.options:
		var frozen := option.duplicate(true)
		frozen["grant_ids"] = _option_grant_ids(state, journey, str(option.get("id", "")), pools)
		journey.frozen_event_options.append(frozen)

static func _option_grant_ids(state: PreparationState, journey: JourneyState, option_id: String, pools: Dictionary) -> Array[String]:
	## 计算选项可能发放的材料/组件奖励 ID（不依赖玩家选择，供冻结与预览使用）。
	var ids := _drop_ids(state, journey, _round_index(journey), [])
	var base: Array[String] = ids.duplicate()
	match option_id:
		"wreckage.core":
			base.assign(["safe_lock"])
		"wreckage.salvage", "moonwell_echo.draw_echo", "ropeway_bridge.cross", "roving_caravan.buy", "alchemy_lab.distill", "storage.left":
			base.assign(["essence"])
		"terminal.convert", "terminal.gamble", "hunter_camp.share_rations":
			base.assign(["skill_shard"])
		"depot.search":
			base.assign(MEDICINES.duplicate())
		"workshop.pay", "workshop.pry", "ropeway_bridge.repair", "hunter_camp.barter", "alchemy_lab.break_seal":
			var extras: Array[String] = pools.get("component", [])
			base.assign(extras)
	return base

static func _drops_for(node_type: int, node_index: int) -> Array[String]:
	match node_type:
		JourneyState.NodeType.CHEST:
			return ["rime_shard", "storm_core", "ember_ore"]
		JourneyState.NodeType.BOSS:
			return ["pulse_phial", "void_glass", "moon_ink"]
		_:
			if node_index == 0:
				return ["iron_branch", "sun_thread"]
			return ["thorn_resin", "rime_shard"]

static func battle_drop_ids(state: PreparationState, journey: JourneyState, round_index: int) -> Array[String]:
	return _drop_ids(state, journey, round_index, [])

static func _drop_ids(state: PreparationState, journey: JourneyState, round_index: int, guaranteed: Array[String]) -> Array[String]:
	var items := AndroidItems.items()
	var tiers := {}
	for row in items:
		if row.get("category") != "MAIN": continue
		var price := int(row.price)
		var tier := 5 if price >= 5500 else 4 if price >= 4500 else 3 if price >= 3500 else 2 if price >= 2500 else 1
		tiers[row.id] = tier
		if row.recipe is Dictionary:
			for id in row.recipe.ingredients:
				if str(id).begins_with("recipe_"): tiers[id] = mini(tier, int(tiers.get(id, tier)))
	items.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a.id < b.id)
	var pool: Array[Dictionary] = []
	var total := 0
	var clears := int(state.quest_progress.get("clears", 0))
	for row in items:
		if int(row.minimumDropClears) > clears or int(tiers.get(row.id, 0)) > journey.difficulty: continue
		var base: int = {"金": 3, "紫": 12, "蓝": 35}.get(row.quality, 80)
		var divisor := 1 + floori(float(row.price) / (250 + journey.difficulty * 150))
		total += maxi(1, floori(float(base) / divisor))
		pool.append({"id": str(row.id), "limit": total})
	var result := guaranteed.duplicate()
	if pool.is_empty(): return result
	var random := clampi(journey.seed, 1, 2147483646)
	for index in range(journey.current_node + 1):
		random = random * 48271 % 2147483647
	var count := 2 + random % 5 if not guaranteed.is_empty() else (3 if round_index == 4 else 1) + random % 3
	while result.size() < count:
		random = random * 48271 % 2147483647
		var roll := random % total
		for row in pool:
			if roll < int(row.limit):
				result.append(row.id)
				break
	return result

static func _add_loot(journey: JourneyState, definitions: Array) -> void:
	var opening := 800 if int(journey.current().get("type", -1)) == JourneyState.NodeType.EVENT else 0
	var lanes := [opening, opening, opening]
	for definition_id in definitions:
		var definition := ContentCatalog.item(definition_id)
		if definition == null:
			continue
		journey.drop_serial += 1
		var kind: ItemInstance.Kind = {"CARD": ItemInstance.Kind.CARD, "MATERIAL": ItemInstance.Kind.MATERIAL,
			"CONSUMABLE": ItemInstance.Kind.CONSUMABLE}.get(definition.source_data.get("kind"), ItemInstance.Kind.EQUIPMENT)
		var item := ItemInstance.new("drop_%d_%d_%s" % [journey.seed, journey.drop_serial, definition.id], definition.id, kind)
		item.bound = bool(definition.source_data.get("boundOnAcquire", false))
		item.footprint = definition.footprint
		item.value = definition.value
		var rank: int = {"蓝": 1, "紫": 2, "金": 3}.get(definition.source_data.get("quality"), 0)
		var price := maxi(0, int(definition.source_data.get("price", definition.value)))
		var duration := clampi(500 + item.footprint.x * item.footprint.y * 65 + rank * 160 + int(log(1.0 + price) * 45), 600, 2400)
		var lane := lanes.find(lanes.min())
		lanes[lane] += duration
		journey.reveal_at[item.id] = lanes[lane]
		item.identified = false
		journey.loot.append(item)

static func advance_appraisal(journey: JourneyState, delta_ms: float) -> bool:
	if journey.reveal_at.is_empty() or delta_ms <= 0.0: return false
	journey.appraisal_ms += delta_ms
	var revealed := false
	for item in journey.loot:
		if journey.reveal_at.has(item.id) and journey.appraisal_ms >= float(journey.reveal_at[item.id]):
			item.identified = true
			journey.reveal_at.erase(item.id)
			revealed = true
	return revealed

static func _collect_first_fit(state: PreparationState, item: ItemInstance) -> bool:
	for rotated in [false, true]:
		var dimensions := Vector2i(item.footprint.y, item.footprint.x) if rotated else item.footprint
		for y in range(state.backpack_size.y - dimensions.y + 1):
			for x in range(state.backpack_size.x - dimensions.x + 1):
				var previous_location := item.location
				item.location = ItemInstance.Location.BACKPACK
				state.add_item(item)
				var result := PackingRules.place(state, item.id, ItemInstance.Location.BACKPACK, Vector2i(x, y), rotated)
				if result.accepted:
					return true
				state.remove_item(item.id)
				item.location = previous_location
	return false

static func _accepted(reason: String) -> Dictionary:
	return {"accepted": true, "reason": reason}

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
