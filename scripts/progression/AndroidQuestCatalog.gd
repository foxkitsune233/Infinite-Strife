class_name AndroidQuestCatalog
extends RefCounted
## Android QuestContent/QuestModels, including hero rewards from commit 255a1b2.
## Returns owned dictionaries; callers may safely retain or modify their snapshots.

const REQUIRED_NODES := [0, 6, 12, 16, 20, 23]
const DIFFICULTY_NAMES := ["简单", "普通", "困难", "精英", "大师", "噩梦"]

static func all() -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	result.append(_single(1, "整装出发", "在整备页把一件装备放入前六个装备栏位。", _goal("EQUIP_ITEM", 1), [_gold(100)]))
	result.append(_single(2, "技能入阵", "把一张技能卡放入技能栏，战斗中自动生效。", _goal("SLOT_CARD", 1), [_material("essence")]))
	result.append(_single(3, "留好退路", "把一件物品放进安全箱；挑战失败时仍能保留。", _goal("STORE_SAFE", 1), [_gold(100)]))
	result.append(_single(4, "初次远征", "选择简单宝箱并成功入场；先确认门票与携入金币。", _goal("ENTER_RUN", 1), [_item("salve", 2)]))
	result.append(_single(5, "初试锋芒", "累计赢得一场战斗，战后整理掉落。", _goal("WIN_BATTLES", 1), [_hero("axe", 250)]))
	result.append(_single(6, "战地拾取", "鉴定完成后成功拾取一件新掉落；本节点只需拾取一件。", _goal("PICKUP_LOOT", 1), [_gold(150)]))
	result.append(_single(7, "稳妥撤离", "第三战后的休息节点选择撤离，带回物资。", _goal("EXTRACT_COUNT", 1), [_hero("skywrath", 300), _material("backpack_fabric", 2), _gold(200)]))
	result.append(_single(8, "扩充行囊", "营地成长购买背包4×3（200金币、背包布料×2、撤离资格）。", _goal("BACKPACK_LEVEL", 1), [_material("essence", 3), _gold(250), _item("gauntlets"), _item("circlet"), _item("recipe_bracer")]))
	result.append(_single(9, "工坊入门", "在合成页用仓库中的力量手套、贵族圆环与护腕卷轴合成护腕。", _goal("CRAFT_COUNT", 1), [_gold(200), _material("safe_lock")]))
	result.append(_single(10, "完整归航", "累计通关一次五战远征；简单难度也可完成。", _goal("CLEAR_COUNT", 1), [_hero("viper", 500), _gold(600)]))
	result.append(_single(11, "加固安全箱", "营地成长购买安全箱3×2（600金币、锁芯×1、通关1次）。", _goal("SAFE_LEVEL", 1), [_material("skill_shard", 2)]))
	result.append(_single(12, "英雄成长", "英雄等级页将任意英雄升至2级（1→2级花费100金币）。", _goal("HERO_LEVEL", 2), [_hero("anti_mage", 600)]))
	result.append_array(_choice(13, [
		["困难通关1次", _goal("CLEAR_DIFFICULTY", 1, 2)],
		["累计战斗胜利20场", _goal("WIN_BATTLES", 20)],
		["累计选择事件10次", _goal("EVENT_CHOICES", 10)]],
		[_hero("phantom_assassin", 700), _material("backpack_fabric", 3), _gold(400)]))
	result.append(_single(14, "丰富技能", "营地成长将技能栏扩至5格（250金币、精华×3、撤离资格）。", _goal("SKILL_LEVEL", 1), [_material("skill_shard", 2), _gold(300)]))
	result.append_array(_choice(15, [
		["困难通关2次", _goal("CLEAR_DIFFICULTY", 2, 2)],
		["累计合成3件装备", _goal("CRAFT_COUNT", 3)],
		["累计通关5次", _goal("CLEAR_COUNT", 5)]],
		[_material("equipment_guard_core"), _gold(300)]))
	result.append(_single(16, "远行准备", "营地成长将背包扩至5×3（400金币、布料×3、通关1次）。", _goal("BACKPACK_LEVEL", 2), [_material("backpack_fabric", 5), _gold(700)]))
	result.append_array(_choice(17, [
		["精英通关1次", _goal("CLEAR_DIFFICULTY", 1, 3)],
		["天怒法师战斗胜利15场", _goal("HERO_WINS", 15, 0, "skywrath")],
		["累计战斗胜利40场", _goal("WIN_BATTLES", 40)]],
		[_hero("necrophos", 900), _gold(500)]))
	result.append(_single(18, "远征常客", "累计通关6次；任意难度均计入。", _goal("CLEAR_COUNT", 6), [_material("essence", 8), _hero("legion_commander", 1000)]))
	result.append_array(_choice(19, [
		["精英通关2次", _goal("CLEAR_DIFFICULTY", 2, 3)],
		["累计合成6件装备", _goal("CRAFT_COUNT", 6)],
		["累计选择事件25次", _goal("EVENT_CHOICES", 25)]],
		[_material("safe_frame"), _gold(1200), _hero("faceless_void", 1100)]))
	result.append(_single(20, "稳固后方", "营地成长将安全箱扩至3×3（1200金币、框架×1、通关5次、背包至少4×3）。", _goal("SAFE_LEVEL", 2), [_material("skill_shard", 4), _gold(1500)]))
	result.append_array(_choice(21, [
		["大师通关1次", _goal("CLEAR_DIFFICULTY", 1, 4)],
		["累计战斗胜利70场", _goal("WIN_BATTLES", 70)],
		["累计通关12次", _goal("CLEAR_COUNT", 12)]],
		[_item("broadsword"), _material("equipment_guard_core", 2), _gold(700)]))
	result.append_array(_choice(22, [
		["大师通关2次", _goal("CLEAR_DIFFICULTY", 2, 4)],
		["任意英雄达到5级", _goal("HERO_LEVEL", 5)],
		["累计商店购买15次", _goal("SHOP_PURCHASE", 15)]],
		[_material("skill_guard_core", 2), _material("essence", 5), _gold(800)]))
	result.append_array(_choice(23, [
		["大师通关3次", _goal("CLEAR_DIFFICULTY", 3, 4)],
		["累计战斗胜利100场", _goal("WIN_BATTLES", 100)],
		["累计通关18次", _goal("CLEAR_COUNT", 18)]],
		[_material("equipment_guard_core", 3), _material("skill_shard", 4), _gold(1000)]))
	result.append_array(_choice(24, [
		["噩梦通关1次", _goal("CLEAR_DIFFICULTY", 1, 5)],
		["累计战斗胜利150场", _goal("WIN_BATTLES", 150)],
		["累计通关25次", _goal("CLEAR_COUNT", 25)]],
		[_material("skill_guard_core", 4), _material("essence", 12), _item("black_king_bar"), _gold(1500)]))
	return result

static func by_id(quest_id: String) -> Dictionary:
	for quest in all():
		if quest.id == quest_id:
			return quest
	return {}

static func _goal(type: String, target: int, difficulty: int = 0, hero_id: Variant = null) -> Dictionary:
	return {"type": type, "target": target, "difficulty": difficulty, "hero_id": hero_id, "material_id": null}

static func _reward(type: String, amount: int, hero_id: Variant = null, material_id: Variant = null) -> Dictionary:
	return {"type": type, "amount": amount, "hero_id": hero_id, "material_id": material_id}

static func _gold(amount: int) -> Dictionary:
	return _reward("GOLD", amount)

static func _item(id: String, amount: int = 1) -> Dictionary:
	return _reward("ITEM", amount, null, id)

static func _material(id: String, amount: int = 1) -> Dictionary:
	return _reward("MATERIAL", amount, null, id)

static func _hero(id: String, compensation: int) -> Dictionary:
	return _reward("HERO_UNLOCK", compensation, id)

static func _quest(id: String, node: int, name: String, description: String, kind: String, objective: Dictionary, rewards: Array) -> Dictionary:
	return {"id": id, "node": node, "name": name, "description": description, "kind": kind,
		"objective": objective.duplicate(true), "rewards": rewards.duplicate(true),
		"prerequisite_quest_ids": [], "min_clears": 0, "repeatable": false, "cost": {}}

static func _single(node: int, name: String, hint: String, objective: Dictionary, rewards: Array) -> Dictionary:
	return _quest("journey.%d" % node, node, name, hint, "GUIDE", objective, rewards)

static func _choice(node: int, goals: Array, rewards: Array) -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	for i in range(goals.size()):
		var name: String = goals[i][0]
		result.append(_quest("journey.%d.%d" % [node, i + 1], node, name,
			"三选一：%s。累计进度；领取后本节点其他选项关闭。" % name, "CHALLENGE", goals[i][1], rewards))
	return result
