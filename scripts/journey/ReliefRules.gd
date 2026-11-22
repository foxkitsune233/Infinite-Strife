extends RefCounted

const Catalog = preload("res://scripts/content/ContentCatalog.gd")
const Chests = preload("res://scripts/journey/ChestRules.gd")
const INTERVAL_MS := 7200000

static func status(state: PreparationState, now_ms: int) -> Dictionary:
	var old: Dictionary = state.quest_progress.get("relief", {})
	var now := maxi(maxi(0, now_ms), int(old.get("seen", 0)))
	# Android uses one maximum observed clock for the home economy.
	now = maxi(now, int(state.quest_progress.get("chest_refresh", {}).get("seen", 0)))
	now = maxi(now, int(state.quest_progress.get("shop", {}).get("seen_ms", 0)))
	var charges := clampi(int(old.get("charges", 2)), 0, 2)
	var anchor := int(old.get("anchor", now))
	now = maxi(now, anchor)
	if charges == 2:
		anchor = now
	else:
		var steps := int((now - anchor) / INTERVAL_MS)
		charges = mini(2, charges + steps)
		anchor = now if charges == 2 else anchor + steps * INTERVAL_MS
	return {"charges": charges, "anchor": anchor, "seen": now,
		"claims": int(old.get("claims", 0)), "vouchers": int(old.get("vouchers", 0)),
		"next_ms": 0 if charges == 2 else INTERVAL_MS - (now - anchor)}

static func current_offers(state: PreparationState) -> Dictionary:
	var result := {}
	var prefix := "%d:" % int(state.quest_progress.get("runs_started", 0))
	var offers: Dictionary = state.quest_progress.get("chest_offers", {})
	for key in offers:
		if str(key).begins_with(prefix):
			result[key] = offers[key]
	return result

static func unavailable(state: PreparationState) -> String:
	if state.phase != PreparationState.Phase.HOME:
		return "挑战中不能领取救济"
	var offers := current_offers(state)
	if offers.is_empty():
		return "请先生成宝箱"
	var minimum := 2147483647
	for target in offers.values():
		minimum = mini(minimum, int(target.ticket))
	if state.gold >= minimum:
		return "当前金币足够进入本批最低票价挑战"
	var assets := 0
	for item: ItemInstance in state.items.values():
		if item.bound:
			continue
		var definition := Catalog.item(item.definition_id)
		if definition == null:
			continue
		var copies := 1
		if item.kind == ItemInstance.Kind.CARD:
			copies = 9 if item.star == 3 else 3 if item.star == 2 else 1
		assets += int(definition.value * copies * 40 / 100)
	return "可售物资回收总值达到120金币，请先整理出售" if assets >= 120 else ""

static func subsidized(state: PreparationState, target: Dictionary) -> bool:
	return int(state.quest_progress.get("relief", {}).get("vouchers", 0)) > 0 and int(target.get("difficulty", -1)) == 0 and target.get("quality") == "白"

static func entry_price(state: PreparationState, target: Dictionary) -> int:
	var ticket := maxi(0, int(target.get("ticket", 0)))
	return maxi(0, ticket - 120) if subsidized(state, target) else ticket

static func claim(state: PreparationState, now_ms: int) -> Dictionary:
	var reason := unavailable(state)
	if not reason.is_empty():
		return {"accepted": false, "reason": reason}
	var counter := status(state, now_ms)
	if int(counter.charges) <= 0:
		return {"accepted": false, "reason": "救济次数已用完，需等待恢复"}
	if int(counter.claims) >= 2147483647 or int(counter.vouchers) >= 2147483647:
		return {"accepted": false, "reason": "救济记录已达上限"}
	for id in ["broadsword", "salve"]:
		if Catalog.item(id) == null:
			return {"accepted": false, "reason": "救济物资配置缺失"}
	var offers := current_offers(state)
	var has_rescue := offers.values().any(func(target: Dictionary) -> bool:
		return int(target.difficulty) == 0 and target.quality == "白" and int(target.ticket) <= 120)
	if not has_rescue:
		var replacement := {"name": "救济补给宝箱", "hint": "简单挑战 · 入场补助可抵扣", "difficulty": 0, "quality": "白", "ticket": 120}
		if not Chests.generate(state, replacement, "supplies"):
			return {"accepted": false, "reason": "救济宝箱配置缺失"}
		var target: Dictionary = offers.values().back()
		target.clear()
		target.merge(replacement, true)
	var claim_id := int(counter.claims) + 1
	for index in 3:
		var id := "broadsword" if index == 0 else "salve"
		var definition := Catalog.item(id)
		var item := ItemInstance.new("relief-%d:%d" % [claim_id, index], id,
			ItemInstance.Kind.EQUIPMENT if index == 0 else ItemInstance.Kind.CONSUMABLE)
		item.bound = true
		item.footprint = definition.footprint
		item.value = int(definition.source_data.get("price", 0))
		state.add_item(item)
	counter.claims = claim_id
	counter.charges = int(counter.charges) - 1
	counter.vouchers = int(counter.vouchers) + 1
	state.quest_progress["relief"] = counter
	return {"accepted": true, "reason": "已领取：绑定阔剑、药膏×2、入场补助券×1"}
