class_name ContentCatalog
extends RefCounted

const RETIRED_MATERIALS := ["sun_thread", "rime_shard", "storm_core"]

static var _item_cache: Array[ItemDefinition] = []
static var _item_by_id: Dictionary = {}

static func schools() -> Array[String]:
	return ["assault", "ward", "rime", "rage", "venom", "surge", "grace", "shadow"]

static func heroes() -> Array[HeroDefinition]:
	return [
		HeroDefinition.new("cinder", "余烬旅者", 980, 180, 74, 5, {"id": "meteor_step", "mana": 70, "cooldown_ms": 9000}),
		HeroDefinition.new("gale", "岚歌", 900, 260, 58, 3, {"id": "sky_lance", "mana": 85, "cooldown_ms": 10000}),
		HeroDefinition.new("bastion", "壁垒守卫", 1200, 150, 62, 13, {"id": "aegis_wave", "mana": 90, "cooldown_ms": 11000}),
		HeroDefinition.new("vex", "裂隙行者", 930, 220, 68, 4, {"id": "null_burst", "mana": 75, "cooldown_ms": 9500}),
		HeroDefinition.new("rime", "霜痕", 1030, 190, 64, 8, {"id": "winter_mark", "mana": 80, "cooldown_ms": 10000}),
		HeroDefinition.new("thorn", "荆冠", 1120, 160, 66, 10, {"id": "briar_guard", "mana": 85, "cooldown_ms": 10500}),
		HeroDefinition.new("lumen", "晨辉", 860, 300, 50, 2, {"id": "dawnfall", "mana": 95, "cooldown_ms": 12000}),
		HeroDefinition.new("shade", "暮影", 910, 210, 76, 3, {"id": "veil_cut", "mana": 65, "cooldown_ms": 8500}),
		HeroDefinition.new("emberwing", "焰翼", 950, 230, 70, 6, {"id": "flare_ring", "mana": 88, "cooldown_ms": 10000}),
		HeroDefinition.new("ironveil", "铁幕", 1180, 170, 60, 12, {"id": "iron_oath", "mana": 82, "cooldown_ms": 11000}),
	]

static func cards() -> Array[CardDefinition]:
	var result: Array[CardDefinition] = []
	for school_id in schools():
		for rank in range(1, 6):
			result.append(CardDefinition.new(
				"%s_pulse_%d" % [school_id, rank],
				"%s回响 %d" % [_school_title(school_id), rank],
				[school_id],
				"获得一层%s构筑增幅。" % _school_title(school_id),
				{"kind": "school_stack", "school": school_id, "value": rank}
			))
	var duals := [
		["rime", "ward", "frostward_conduit"],
		["rage", "assault", "cinder_charge"],
		["venom", "shadow", "nightbloom"],
		["surge", "grace", "dawn_current"],
		["ward", "assault", "iron_advance"],
	]
	for dual in duals:
		result.append(CardDefinition.new(
			dual[2], "双流派共鸣", [dual[0], dual[1]], "同时强化两种构筑。",
			{"kind": "dual_school_stack", "schools": [dual[0], dual[1]], "value": 1}
		))
	return result

static func cards_for_school(school_id: String) -> Array[CardDefinition]:
	var result: Array[CardDefinition] = []
	for card in cards():
		if school_id in card.school_ids:
			result.append(card)
	return result

static func items() -> Array[ItemDefinition]:
	if not _item_cache.is_empty():
		return _item_cache
	var result: Array[ItemDefinition] = [
		ItemDefinition.new("iron_branch", "铁枝", "common", Vector2i(1, 1), 35),
		ItemDefinition.new("thorn_resin", "荆脂", "common", Vector2i(1, 1), 38),
		ItemDefinition.new("void_glass", "空镜", "common", Vector2i(1, 1), 48),
		ItemDefinition.new("ember_ore", "余烬矿", "common", Vector2i(1, 1), 50),
		ItemDefinition.new("moon_ink", "月墨", "common", Vector2i(1, 1), 44),
	]
	var crafted := [
		["storm_lens", "风暴透镜", "storm_core", "void_glass"],
		["ward_charm", "守望护符", "iron_branch", "sun_thread"],
		["rime_edge", "霜棱", "rime_shard", "iron_branch"],
		["ember_maul", "余烬重锤", "ember_ore", "iron_branch"],
		["briar_mail", "荆甲", "thorn_resin", "iron_branch"],
		["moon_cloak", "月幕披风", "moon_ink", "sun_thread"],
		["void_compass", "虚空罗盘", "void_glass", "storm_core"],
		["sunward_band", "向阳环", "sun_thread", "ember_ore"],
		["rime_anchor", "冻锚", "rime_shard", "thorn_resin"],
		["gale_boots", "逐风靴", "storm_core", "sun_thread"],
		["thorn_idol", "荆灵像", "thorn_resin", "moon_ink"],
		["cinder_plate", "炽板甲", "ember_ore", "rime_shard"],
		["lumen_staff", "辉杖", "sun_thread", "void_glass"],
		["shade_dagger", "暮刃", "moon_ink", "ember_ore"],
		["veil_shield", "幕盾", "void_glass", "thorn_resin"],
		["dawn_relay", "晨光接力器", "sun_thread", "storm_core"],
	]
	for entry in crafted:
		result.append(ItemDefinition.new(entry[0], entry[1], "rare", Vector2i(2, 1), 160, [entry[2], entry[3]]))
	var active := [
		["pulse_phial", "脉冲瓶", "heal"], ["ward_totem", "守卫图腾", "shield"],
		["frost_bell", "霜铃", "slow"], ["rage_drum", "怒鼓", "haste"],
		["venom_vial", "毒雾瓶", "poison"], ["surge_coil", "潮涌线圈", "mana"],
		["grace_lantern", "恩辉灯", "cleanse"], ["shadow_key", "暮影钥", "evasion"],
	]
	for entry in active:
		result.append(ItemDefinition.new(entry[0], entry[1], "epic", Vector2i(1, 2), 220, [], {"kind": entry[2], "cooldown_ms": 12000}))
	var android := preload("res://scripts/content/AndroidContent.gd")
	var merged := {}
	for definition in result:
		merged[definition.id] = definition
	for record in android.catalog().items():
		var recipe: Array[String] = []
		if record.get("recipe") is Dictionary:
			for ingredient in record.recipe.ingredients:
				for ignored in int(record.recipe.ingredients[ingredient]):
					recipe.append(str(ingredient))
		var rarity: String = {"白": "common", "蓝": "rare", "紫": "epic", "金": "legendary"}.get(str(record.quality), "rare")
		var definition := ItemDefinition.new(str(record.id), str(record.name), str(rarity), Vector2i(int(record.width), int(record.height)), int(record.price), recipe)
		definition.source_data = record.duplicate(true)
		merged[definition.id] = definition
	result.clear()
	for definition in merged.values():
		result.append(definition)
	_item_cache = result
	_item_by_id = merged
	return result

static func item(id: String) -> ItemDefinition:
	if _item_cache.is_empty():
		items()
	return _item_by_id.get(id) as ItemDefinition

static func events(include_placeholders := false) -> Array[EventDefinition]:
	var records := [
		["moonwell_echo", "月井回响", "mystic"], ["broken_span", "断桥索道", "travel"],
		["frozen_altar", "冰封祭坛", "mystic"], ["banner_ruins", "战旗遗址", "war"],
		["wandering_caravan", "流动商队", "trade"], ["sealed_vault", "封锁遗库", "ruin"],
		["hunter_camp", "猎人营火", "camp"], ["alchemist_spill", "炼金泄漏", "risk"],
		["glass_grove", "琉璃林", "nature"], ["sunken_archive", "沉没档案", "ruin"],
		["storm_gate", "风暴门", "travel"], ["quiet_market", "静市", "trade"],
		["thorn_court", "荆棘庭", "nature"], ["lost_signal", "失落信标", "war"],
		["mirror_drift", "镜面漂流", "mystic"], ["iron_feast", "铁宴", "camp"],
		["pact_altar", "秘契祭坛", "mystic"],
	]
	var result: Array[EventDefinition] = []
	for record in records:
		if record[0] == "alchemist_spill":
			result.append(EventDefinition.new(record[0], "失控炼金室", record[2], [
				{"id": "alchemy_lab.distill", "label": "投入药品蒸馏", "outcome": "material", "item_choice": "medicine"},
				{"id": "alchemy_lab.break_seal", "label": "强行破封（可能中毒，可能死亡）", "outcome": "material"},
				{"id": "alchemy_lab.leave", "label": "离开", "outcome": "leave"},
			]))
			continue
		if record[0] == "pact_altar":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "pact_altar.offer_medicine", "label": "献上一份药品", "outcome": "material", "item_choice": "medicine"},
				{"id": "pact_altar.offer_blood", "label": "以血为契（可能受伤，可能死亡）", "outcome": "rare_loot"},
				{"id": "pact_altar.refuse", "label": "拒绝", "outcome": "leave"},
			]))
			continue
		if record[0] == "wandering_caravan":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "roving_caravan.buy", "label": "用本局金币购买", "outcome": "material", "priced": true},
				{"id": "roving_caravan.sell", "label": "卖出一件物品", "outcome": "sell", "item_choice": "any"},
				{"id": "roving_caravan.depart", "label": "离开", "outcome": "leave"},
			]))
			continue
		if record[0] == "hunter_camp":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "hunter_camp.barter", "label": "以物易物", "outcome": "material", "item_choice": "any"},
				{"id": "hunter_camp.share_rations", "label": "分一份药品给猎人", "outcome": "material", "item_choice": "medicine"},
				{"id": "hunter_camp.leave", "label": "离开", "outcome": "leave"},
			]))
			continue
		if record[0] == "broken_span":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "ropeway_bridge.cross", "label": "攀索过桥（可能受伤，可能死亡）", "outcome": "rare_loot"},
				{"id": "ropeway_bridge.repair", "label": "交金币修桥", "outcome": "material", "priced": true},
				{"id": "ropeway_bridge.leave", "label": "离开", "outcome": "leave"},
			]))
			continue
		if record[0] == "banner_ruins":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "war_banner.claim", "label": "交金币取走战旗", "outcome": "rare_loot", "priced": true},
				{"id": "war_banner.blood_rite", "label": "血祭战旗（可能受伤，可能死亡）", "outcome": "rare_loot"},
				{"id": "war_banner.leave", "label": "离开", "outcome": "leave"},
			]))
			continue
		if record[0] == "frozen_altar":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "frozen_shrine.attune", "label": "以生命共鸣寒霜（可能受伤，可能死亡）", "outcome": "rare_loot"},
				{"id": "frozen_shrine.pray", "label": "安静祈祷", "outcome": "heal"},
				{"id": "frozen_shrine.leave", "label": "离开", "outcome": "leave"},
			]))
			continue
		if record[0] == "moonwell_echo":
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "moonwell_echo.resonate", "label": "共鸣月井（需要两档羁绊）", "outcome": "rare_loot"},
				{"id": "moonwell_echo.draw_echo", "label": "抽取低语回响", "outcome": "material"},
				{"id": "moonwell_echo.leave", "label": "离开", "outcome": "leave"},
			]))
			continue
		if include_placeholders:
			result.append(EventDefinition.new(record[0], record[1], record[2], [
				{"id": "%s_cautious" % record[0], "label": "谨慎处理", "outcome": "material"},
				{"id": "%s_bold" % record[0], "label": "冒险尝试", "outcome": "rare_loot"},
				{"id": "%s_leave" % record[0], "label": "离开", "outcome": "leave"},
			]))
	result.append_array(_android_legacy_events())
	return result

static func _android_legacy_events() -> Array[EventDefinition]:
	var definitions: Array[EventDefinition] = []
	var records := [
		["abandoned_depot", "废弃补给站", "trade", [
			{"id": "depot.search", "label": "搜索（可能受伤，可能死亡）", "outcome": "risk"},
			{"id": "depot.leave", "label": "离开", "outcome": "leave"}]],
		["rusted_workshop", "锈蚀工坊", "ruin", [
			{"id": "workshop.pry", "label": "强拆（可能受伤，可能死亡）", "outcome": "risk"},
			{"id": "workshop.pay", "label": "交金币稳妥取件", "outcome": "material", "priced": true},
			{"id": "workshop.leave", "label": "离开", "outcome": "leave"}]],
		["ruined_library", "残破书库", "mystic", [
			{"id": "library.delve", "label": "深入（可能受伤，可能死亡）", "outcome": "risk"},
			{"id": "library.study", "label": "安全研读", "outcome": "material"},
			{"id": "library.leave", "label": "离开", "outcome": "leave"}]],
		["collapsed_storage", "坍塌仓室", "ruin", [
			{"id": "storage.left", "label": "左路（可能受伤，可能死亡）", "outcome": "risk"},
			{"id": "storage.right", "label": "右路（交金币通过）", "outcome": "leave", "priced": true}]],
		["safe_wreckage", "保险库残骸", "ruin", [
			{"id": "wreckage.core", "label": "搬走锁芯", "outcome": "material"},
			{"id": "wreckage.salvage", "label": "拆解取通用材料", "outcome": "material"},
			{"id": "wreckage.abandon", "label": "放弃", "outcome": "leave"}]],
		["tech_terminal", "技术终端", "ruin", [
			{"id": "terminal.convert", "label": "消耗一份精华换技能刻片", "outcome": "material", "item_choice": "essence"},
			{"id": "terminal.gamble", "label": "冒险操作（可能受伤，可能死亡）", "outcome": "risk"},
			{"id": "terminal.leave", "label": "离开", "outcome": "leave"}]],
		["field_clinic", "临时医棚", "camp", [
			{"id": "clinic.gold", "label": "交本局金币治疗", "outcome": "heal", "priced": true},
			{"id": "clinic.supply", "label": "用一份药品强化治疗", "outcome": "heal", "item_choice": "medicine"},
			{"id": "clinic.leave", "label": "离开", "outcome": "leave"}]],
		["blocker", "拦路者", "travel", [
			{"id": "blocker.gold", "label": "交金币", "outcome": "leave", "priced": true},
			{"id": "blocker.item", "label": "交一件物品", "outcome": "leave", "item_choice": "any"},
			{"id": "blocker.rush", "label": "强闯（可能死亡）", "outcome": "risk"}]],
	]
	for row in records:
		var options: Array[Dictionary] = []
		options.assign(row[3])
		definitions.append(EventDefinition.new(row[0], row[1], row[2], options))
	return definitions

static func event(id: String) -> EventDefinition:
	for definition in events(true):
		if definition.id == id:
			return definition
	return null

static func _school_title(id: String) -> String:
	var titles := {
		"assault": "突击", "ward": "守卫", "rime": "寒霜", "rage": "怒火",
		"venom": "毒蚀", "surge": "潮涌", "grace": "恩辉", "shadow": "暮影",
	}
	return titles.get(id, id)
