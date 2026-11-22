extends SceneTree

# Frozen independently from Android QuestContent.kt, including every localized hint.
const ANDROID_FIXTURE := [
	["journey.1",1,"整装出发","在整备页把一件装备放入前六个装备栏位。","GUIDE",["EQUIP_ITEM",1,0,null,null],[["GOLD",100,null,null]]],
	["journey.2",2,"技能入阵","把一张技能卡放入技能栏，战斗中自动生效。","GUIDE",["SLOT_CARD",1,0,null,null],[["MATERIAL",1,null,"essence"]]],
	["journey.3",3,"留好退路","把一件物品放进安全箱；挑战失败时仍能保留。","GUIDE",["STORE_SAFE",1,0,null,null],[["GOLD",100,null,null]]],
	["journey.4",4,"初次远征","选择简单宝箱并成功入场；先确认门票与携入金币。","GUIDE",["ENTER_RUN",1,0,null,null],[["ITEM",2,null,"salve"]]],
	["journey.5",5,"初试锋芒","累计赢得一场战斗，战后整理掉落。","GUIDE",["WIN_BATTLES",1,0,null,null],[["HERO_UNLOCK",250,"axe",null]]],
	["journey.6",6,"战地拾取","鉴定完成后成功拾取一件新掉落；本节点只需拾取一件。","GUIDE",["PICKUP_LOOT",1,0,null,null],[["GOLD",150,null,null]]],
	["journey.7",7,"稳妥撤离","第三战后的休息节点选择撤离，带回物资。","GUIDE",["EXTRACT_COUNT",1,0,null,null],[["HERO_UNLOCK",300,"skywrath",null],["MATERIAL",2,null,"backpack_fabric"],["GOLD",200,null,null]]],
	["journey.8",8,"扩充行囊","营地成长购买背包4×3（200金币、背包布料×2、撤离资格）。","GUIDE",["BACKPACK_LEVEL",1,0,null,null],[["MATERIAL",3,null,"essence"],["GOLD",250,null,null],["ITEM",1,null,"gauntlets"],["ITEM",1,null,"circlet"],["ITEM",1,null,"recipe_bracer"]]],
	["journey.9",9,"工坊入门","在合成页用仓库中的力量手套、贵族圆环与护腕卷轴合成护腕。","GUIDE",["CRAFT_COUNT",1,0,null,null],[["GOLD",200,null,null],["MATERIAL",1,null,"safe_lock"]]],
	["journey.10",10,"完整归航","累计通关一次五战远征；简单难度也可完成。","GUIDE",["CLEAR_COUNT",1,0,null,null],[["HERO_UNLOCK",500,"viper",null],["GOLD",600,null,null]]],
	["journey.11",11,"加固安全箱","营地成长购买安全箱3×2（600金币、锁芯×1、通关1次）。","GUIDE",["SAFE_LEVEL",1,0,null,null],[["MATERIAL",2,null,"skill_shard"]]],
	["journey.12",12,"英雄成长","英雄等级页将任意英雄升至2级（1→2级花费100金币）。","GUIDE",["HERO_LEVEL",2,0,null,null],[["HERO_UNLOCK",600,"anti_mage",null]]],
	["journey.13.1",13,"困难通关1次","三选一：困难通关1次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",1,2,null,null],[["HERO_UNLOCK",700,"phantom_assassin",null],["MATERIAL",3,null,"backpack_fabric"],["GOLD",400,null,null]]],
	["journey.13.2",13,"累计战斗胜利20场","三选一：累计战斗胜利20场。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["WIN_BATTLES",20,0,null,null],[["HERO_UNLOCK",700,"phantom_assassin",null],["MATERIAL",3,null,"backpack_fabric"],["GOLD",400,null,null]]],
	["journey.13.3",13,"累计选择事件10次","三选一：累计选择事件10次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["EVENT_CHOICES",10,0,null,null],[["HERO_UNLOCK",700,"phantom_assassin",null],["MATERIAL",3,null,"backpack_fabric"],["GOLD",400,null,null]]],
	["journey.14",14,"丰富技能","营地成长将技能栏扩至5格（250金币、精华×3、撤离资格）。","GUIDE",["SKILL_LEVEL",1,0,null,null],[["MATERIAL",2,null,"skill_shard"],["GOLD",300,null,null]]],
	["journey.15.1",15,"困难通关2次","三选一：困难通关2次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",2,2,null,null],[["MATERIAL",1,null,"equipment_guard_core"],["GOLD",300,null,null]]],
	["journey.15.2",15,"累计合成3件装备","三选一：累计合成3件装备。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CRAFT_COUNT",3,0,null,null],[["MATERIAL",1,null,"equipment_guard_core"],["GOLD",300,null,null]]],
	["journey.15.3",15,"累计通关5次","三选一：累计通关5次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_COUNT",5,0,null,null],[["MATERIAL",1,null,"equipment_guard_core"],["GOLD",300,null,null]]],
	["journey.16",16,"远行准备","营地成长将背包扩至5×3（400金币、布料×3、通关1次）。","GUIDE",["BACKPACK_LEVEL",2,0,null,null],[["MATERIAL",5,null,"backpack_fabric"],["GOLD",700,null,null]]],
	["journey.17.1",17,"精英通关1次","三选一：精英通关1次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",1,3,null,null],[["HERO_UNLOCK",900,"necrophos",null],["GOLD",500,null,null]]],
	["journey.17.2",17,"天怒法师战斗胜利15场","三选一：天怒法师战斗胜利15场。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["HERO_WINS",15,0,"skywrath",null],[["HERO_UNLOCK",900,"necrophos",null],["GOLD",500,null,null]]],
	["journey.17.3",17,"累计战斗胜利40场","三选一：累计战斗胜利40场。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["WIN_BATTLES",40,0,null,null],[["HERO_UNLOCK",900,"necrophos",null],["GOLD",500,null,null]]],
	["journey.18",18,"远征常客","累计通关6次；任意难度均计入。","GUIDE",["CLEAR_COUNT",6,0,null,null],[["MATERIAL",8,null,"essence"],["HERO_UNLOCK",1000,"legion_commander",null]]],
	["journey.19.1",19,"精英通关2次","三选一：精英通关2次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",2,3,null,null],[["MATERIAL",1,null,"safe_frame"],["GOLD",1200,null,null],["HERO_UNLOCK",1100,"faceless_void",null]]],
	["journey.19.2",19,"累计合成6件装备","三选一：累计合成6件装备。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CRAFT_COUNT",6,0,null,null],[["MATERIAL",1,null,"safe_frame"],["GOLD",1200,null,null],["HERO_UNLOCK",1100,"faceless_void",null]]],
	["journey.19.3",19,"累计选择事件25次","三选一：累计选择事件25次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["EVENT_CHOICES",25,0,null,null],[["MATERIAL",1,null,"safe_frame"],["GOLD",1200,null,null],["HERO_UNLOCK",1100,"faceless_void",null]]],
	["journey.20",20,"稳固后方","营地成长将安全箱扩至3×3（1200金币、框架×1、通关5次、背包至少4×3）。","GUIDE",["SAFE_LEVEL",2,0,null,null],[["MATERIAL",4,null,"skill_shard"],["GOLD",1500,null,null]]],
	["journey.21.1",21,"大师通关1次","三选一：大师通关1次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",1,4,null,null],[["ITEM",1,null,"broadsword"],["MATERIAL",2,null,"equipment_guard_core"],["GOLD",700,null,null]]],
	["journey.21.2",21,"累计战斗胜利70场","三选一：累计战斗胜利70场。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["WIN_BATTLES",70,0,null,null],[["ITEM",1,null,"broadsword"],["MATERIAL",2,null,"equipment_guard_core"],["GOLD",700,null,null]]],
	["journey.21.3",21,"累计通关12次","三选一：累计通关12次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_COUNT",12,0,null,null],[["ITEM",1,null,"broadsword"],["MATERIAL",2,null,"equipment_guard_core"],["GOLD",700,null,null]]],
	["journey.22.1",22,"大师通关2次","三选一：大师通关2次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",2,4,null,null],[["MATERIAL",2,null,"skill_guard_core"],["MATERIAL",5,null,"essence"],["GOLD",800,null,null]]],
	["journey.22.2",22,"任意英雄达到5级","三选一：任意英雄达到5级。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["HERO_LEVEL",5,0,null,null],[["MATERIAL",2,null,"skill_guard_core"],["MATERIAL",5,null,"essence"],["GOLD",800,null,null]]],
	["journey.22.3",22,"累计商店购买15次","三选一：累计商店购买15次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["SHOP_PURCHASE",15,0,null,null],[["MATERIAL",2,null,"skill_guard_core"],["MATERIAL",5,null,"essence"],["GOLD",800,null,null]]],
	["journey.23.1",23,"大师通关3次","三选一：大师通关3次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",3,4,null,null],[["MATERIAL",3,null,"equipment_guard_core"],["MATERIAL",4,null,"skill_shard"],["GOLD",1000,null,null]]],
	["journey.23.2",23,"累计战斗胜利100场","三选一：累计战斗胜利100场。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["WIN_BATTLES",100,0,null,null],[["MATERIAL",3,null,"equipment_guard_core"],["MATERIAL",4,null,"skill_shard"],["GOLD",1000,null,null]]],
	["journey.23.3",23,"累计通关18次","三选一：累计通关18次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_COUNT",18,0,null,null],[["MATERIAL",3,null,"equipment_guard_core"],["MATERIAL",4,null,"skill_shard"],["GOLD",1000,null,null]]],
	["journey.24.1",24,"噩梦通关1次","三选一：噩梦通关1次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_DIFFICULTY",1,5,null,null],[["MATERIAL",4,null,"skill_guard_core"],["MATERIAL",12,null,"essence"],["ITEM",1,null,"black_king_bar"],["GOLD",1500,null,null]]],
	["journey.24.2",24,"累计战斗胜利150场","三选一：累计战斗胜利150场。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["WIN_BATTLES",150,0,null,null],[["MATERIAL",4,null,"skill_guard_core"],["MATERIAL",12,null,"essence"],["ITEM",1,null,"black_king_bar"],["GOLD",1500,null,null]]],
	["journey.24.3",24,"累计通关25次","三选一：累计通关25次。累计进度；领取后本节点其他选项关闭。","CHALLENGE",["CLEAR_COUNT",25,0,null,null],[["MATERIAL",4,null,"skill_guard_core"],["MATERIAL",12,null,"essence"],["ITEM",1,null,"black_king_bar"],["GOLD",1500,null,null]]],
]

var failed := 0
var checks := 0
var rules: Script
var catalog: Script

func _init() -> void:
	# Missing implementations fail deliberately before loading, rather than parse-erroring.
	if not FileAccess.file_exists("res://scripts/progression/QuestRules.gd"):
		check(false, "pure quest rules must exist")
		quit(1)
		return
	rules = load("res://scripts/progression/QuestRules.gd")
	catalog = load("res://scripts/progression/AndroidQuestCatalog.gd")
	if rules == null or catalog == null or not rules.can_instantiate():
		check(false, "quest scripts must compile")
		quit(1)
		return
	test_catalog()
	test_record()
	test_observe()
	test_claim_and_visibility()
	test_difficulties_and_low_difficulty_path()
	print("Quest rules: %d checks, %d failures" % [checks, failed])
	quit(1 if failed else 0)

func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failed += 1
		push_error(label)

func counter(p: Dictionary, id: String) -> int:
	return int(p.get("quest_counters", {}).get(id, 0))

func through(node: int) -> Dictionary:
	var ids: Array = []
	for n in range(1, node + 1):
		ids.append("journey.%d.1" % n if n in [13, 15, 17, 19, 21, 22, 23, 24] else "journey.%d" % n)
	return {"quest_counters": {}, "claimed_quests": ids}

func test_catalog() -> void:
	var all: Array = catalog.all()
	check(all.size() == 40, "all 40 Android definitions")
	for i in range(mini(all.size(), ANDROID_FIXTURE.size())):
		var q: Dictionary = all[i]
		var o: Dictionary = q.objective
		var reward_rows: Array = []
		for r in q.rewards:
			reward_rows.append([r.type, r.amount, r.hero_id, r.material_id])
		check([q.id, q.node, q.name, q.description, q.kind, [o.type, o.target, o.difficulty, o.hero_id, o.material_id], reward_rows] == ANDROID_FIXTURE[i], "exact Android catalog parity " + q.id)
		check(q.prerequisite_quest_ids == [] and q.min_clears == 0 and q.cost == {}, "Android model defaults " + q.id)
	var ids := {}
	var counts := {}
	for q in all:
		check(not ids.has(q.id), "unique stable ID " + q.id)
		ids[q.id] = true
		counts[q.node] = counts.get(q.node, 0) + 1
		check(q.objective.target > 0 and not q.repeatable, "positive one-shot goal " + q.id)
		check(not q.name.is_empty() and not q.description.is_empty(), "localized text " + q.id)
	check(counts.size() == 24, "24 nodes")
	for n in range(1, 25):
		check(counts[n] == (3 if n in [13, 15, 17, 19, 21, 22, 23, 24] else 1), "node cardinality %d" % n)
	# Literal Android fixtures catch incorrect difficulty, hero and reward translations.
	check(catalog.by_id("journey.8").rewards == [
		{"type": "MATERIAL", "amount": 3, "hero_id": null, "material_id": "essence"},
		{"type": "GOLD", "amount": 250, "hero_id": null, "material_id": null},
		{"type": "ITEM", "amount": 1, "hero_id": null, "material_id": "gauntlets"},
		{"type": "ITEM", "amount": 1, "hero_id": null, "material_id": "circlet"},
		{"type": "ITEM", "amount": 1, "hero_id": null, "material_id": "recipe_bracer"}], "full bracer recipe reward")
	check(catalog.by_id("journey.17.2").objective.hero_id == "skywrath", "hero-specific branch")
	check(catalog.by_id("journey.24.1").objective.difficulty == 5, "nightmare branch")
	check(catalog.by_id("missing").is_empty(), "unknown catalog ID")
	all[0].name = "changed"
	check(catalog.by_id("journey.1").name == "整装出发", "catalog snapshots isolated")

func test_record() -> void:
	var original := {"quest_counters": {"future.mod": 17}, "claimed_quests": [], "extra": {"a": [1]}}
	var p: Dictionary = rules.record(original, {"type": "BattleWon", "hero_id": "juggernaut", "difficulty": 0})
	check(counter(p, "journey.5") == 1 and counter(p, "journey.24.2") == 1, "wins accrue in future nodes")
	check(counter(p, "journey.17.2") == 0, "wrong hero excluded")
	for i in range(160):
		p = rules.record(p, {"type": "BattleWon", "hero_id": "skywrath", "difficulty": 0})
	check(counter(p, "journey.24.2") == 150 and counter(p, "journey.17.2") == 15, "independent target caps")
	check(counter(p, "future.mod") == 17, "unknown future counter preserved")
	check(original.quest_counters == {"future.mod": 17}, "record does not mutate input")
	p.extra.a.append(2)
	check(original.extra.a == [1], "record deep copies unrelated nested state")
	var clear: Dictionary = rules.record({}, {"type": "RunCleared", "hero_id": "juggernaut", "difficulty": 1})
	check(counter(clear, "journey.10") == 1 and counter(clear, "journey.13.1") == 0, "below hard threshold")
	clear = rules.record(clear, {"type": "RunCleared", "difficulty": 5})
	check(counter(clear, "journey.13.1") == 1 and counter(clear, "journey.21.1") == 1 and counter(clear, "journey.24.1") == 1, "higher clears count at every lower threshold")
	check(counter(clear, "journey.7") == 0, "clear is not extraction")
	for pair in [["Entered", "journey.4"], ["LootPicked", "journey.6"], ["Extracted", "journey.7"], ["EventChosen", "journey.13.3"], ["Crafted", "journey.9"], ["ShopPurchase", "journey.22.3"]]:
		check(counter(rules.record({}, {"type": pair[0]}), pair[1]) == 1, "event routing " + pair[0])
	check(counter(rules.record({}, {"type": "HeroLevelChanged", "hero_id": "viper", "level": 4}), "journey.22.2") == 0, "level event below target does not partially count")
	check(counter(rules.record({}, {"type": "HeroLevelChanged", "hero_id": "viper", "level": 5}), "journey.22.2") == 5, "level event reaches whole target")
	check(rules.record(original, {"type": "unknown"}) == original, "unknown event no-op")
	for event in ["ConsumableUsed", "GuardedDeath", "MaterialGained"]:
		check(rules.record(original, {"type": event, "count": 100, "material_id": "essence"}) == original, "unused Android event does not invent goals")

func test_observe() -> void:
	var milestones := {"equip_item": true, "slot_card": true, "store_safe": true, "backpack_level": 3, "safe_level": 2, "skill_slots": 8, "clears": 10, "hero_levels": {"juggernaut": 5}}
	var original := {"quest_counters": {"future": 12}, "claimed_quests": []}
	var p: Dictionary = rules.observe(original, milestones)
	for pair in [["journey.1", 1], ["journey.2", 1], ["journey.3", 1], ["journey.8", 1], ["journey.16", 2], ["journey.11", 1], ["journey.20", 2], ["journey.14", 1], ["journey.10", 1], ["journey.18", 6], ["journey.24.3", 10], ["journey.12", 2], ["journey.22.2", 5]]:
		check(counter(p, pair[0]) == pair[1], "observed milestone " + pair[0])
	for id in ["journey.5", "journey.7", "journey.9", "journey.13.1", "journey.13.3", "journey.22.3"]:
		check(counter(p, id) == 0, "observation cannot fabricate history " + id)
	check(rules.observe(p, {}) == p, "milestones survive loss")
	check(rules.observe(p, milestones) == p, "observation idempotent")
	check(original.quest_counters == {"future": 12}, "observe input unchanged")
	check(milestones.hero_levels == {"juggernaut": 5}, "milestones input unchanged")

func test_claim_and_visibility() -> void:
	var p: Dictionary = through(0)
	for q in catalog.all():
		p.quest_counters[q.id] = q.objective.target
	p["gold"] = 600
	p["unlocked_heroes"] = ["skywrath"]
	var before := p.duplicate(true)
	check(rules.visible(p)[0].id == "journey.1", "only first node visible")
	for test in [["journey.1", {}, "仅营地可领取任务奖励"], ["missing", {"at_home": true}, "任务不存在"], ["journey.13.1", {"at_home": true}, "请先领取前一节点奖励"]]:
		var rejected: Dictionary = rules.claim(p, test[0], test[1])
		check(not rejected.accepted and rejected.reason == test[2] and rejected.progress == p and rejected.rewards.is_empty(), "claim rejection " + test[0] + str(test[1]))
	var incomplete: Dictionary = rules.claim({}, "journey.1", {"at_home": true})
	check(not incomplete.accepted and incomplete.reason == "任务尚未完成", "incomplete claim rejected")
	for n in range(1, 25):
		var vis: Array = rules.visible(p)
		check(vis[0].node == n, "visibility advances in node order %d" % n)
		var selected: Dictionary = vis[-1]
		var result: Dictionary = rules.claim(p, selected.id, {"at_home": true})
		check(result.keys().size() == 4 and result.accepted and result.reason == "", "claim envelope %d" % n)
		check(result.rewards == selected.rewards, "reward descriptors %d" % n)
		check(result.progress.gold == 600 and result.progress.unlocked_heroes == ["skywrath"], "claim validates without applying rewards %d" % n)
		check(p.claimed_quests.size() == n - 1, "claim input unchanged %d" % n)
		p = result.progress
		check(rules.completed_nodes(p) == n, "completed distinct nodes %d" % n)
		check(not rules.claim(p, selected.id, {"at_home": true}).accepted, "repeat rejected %d" % n)
		if vis.size() == 3:
			check(not rules.claim(p, vis[0].id, {"at_home": true}).accepted, "mutex sibling rejected %d" % n)
			var sparse := p.duplicate(true)
			sparse.quest_counters.erase(vis[0].id)
			var event: Dictionary = {13: {"type": "RunCleared", "difficulty": 5}, 15: {"type": "RunCleared", "difficulty": 5}, 17: {"type": "RunCleared", "difficulty": 5}, 19: {"type": "RunCleared", "difficulty": 5}, 21: {"type": "RunCleared", "difficulty": 5}, 22: {"type": "RunCleared", "difficulty": 5}, 23: {"type": "RunCleared", "difficulty": 5}, 24: {"type": "RunCleared", "difficulty": 5}}[n]
			check(counter(rules.record(sparse, event), vis[0].id) == 0, "closed sibling stops recording %d" % n)
	check(rules.visible(p).is_empty(), "no visible nodes after final claim")
	check(before.claimed_quests.is_empty(), "initial snapshot untouched")
	var duplicate := through(0)
	duplicate.claimed_quests = ["journey.13.1", "journey.13.2", "journey.13.1", "future"]
	check(rules.completed_nodes(duplicate) == 1, "count known distinct nodes only")
	check(rules.visible(duplicate)[0].node == 1, "holes cannot be skipped")
	var detached: Array = rules.visible({})
	detached[0].objective.target = 999
	check(rules.visible({})[0].objective.target == 1, "visible metadata isolated")

func test_difficulties_and_low_difficulty_path() -> void:
	for pair in [[0, 0], [5, 0], [6, 1], [11, 1], [12, 2], [15, 2], [16, 3], [19, 3], [20, 4], [22, 4], [23, 5], [24, 5]]:
		check(rules.max_difficulty(through(pair[0])) == pair[1], "difficulty boundary %d" % pair[0])
	for difficulty in range(6):
		var need: int = [0, 6, 12, 16, 20, 23][difficulty]
		check(rules.locked_reason(through(need), difficulty) == "", "difficulty accepted at threshold")
		if need > 0:
			check(not rules.locked_reason(through(need - 1), difficulty).is_empty(), "difficulty locked below threshold")
	check(rules.locked_reason({}, -1) == "副本难度无效" and rules.locked_reason({}, 6) == "副本难度无效", "invalid difficulties")
	# Reach the finale entirely with simple runs and legitimate non-difficulty alternatives.
	var p: Dictionary = rules.observe({}, {"equip_item": true, "slot_card": true, "store_safe": true, "backpack_level": 2, "safe_level": 2, "skill_slots": 5, "hero_levels": {"juggernaut": 5}})
	for event in [{"type": "Entered"}, {"type": "LootPicked"}, {"type": "Extracted"}, {"type": "Crafted"}]:
		p = rules.record(p, event)
	for i in range(150):
		p = rules.record(p, {"type": "BattleWon", "hero_id": "juggernaut", "difficulty": 0})
	for i in range(25):
		p = rules.record(p, {"type": "RunCleared", "difficulty": 0})
	for n in range(1, 25):
		var id := "journey.%d" % n
		if n in [13, 21, 22, 23, 24]:
			id += ".2"
		elif n == 17:
			id += ".3"
		elif n in [15, 19]:
			if n == 19:
				for i in range(25):
					p = rules.record(p, {"type": "EventChosen"})
			id += ".3"
		var result: Dictionary = rules.claim(p, id, {"at_home": true})
		check(result.accepted, "simple-only route node %d: %s" % [n, result.reason])
		p = result.progress
	check(rules.completed_nodes(p) == 24, "all nodes reachable without locked difficulty")
