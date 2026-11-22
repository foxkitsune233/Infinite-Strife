class_name ShopRules
extends RefCounted
## Android source root: D:/android_project/DouQiuQiu.
## shared/src/commonMain/kotlin/com/example/douqiuqiu/shop/
##   ShopRules.kt, ShopShelfSelector.kt, ShopPriceLocker.kt
## shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/
##   SupplyRules.kt, EconomyBalance.kt (SHOP_REFRESH=50)
## app/src/main/java/com/example/douqiuqiu/inventory/HomeEconomyRules.kt
## app/src/main/java/com/example/douqiuqiu/system/ShopRefreshClock.kt
## Full staple pool, 6 rotating draws, 30% rare draw; Lehmer 48271 mod 2147483647.
## Lock round-half-up(base * clamp(stage * demand, .8, 1.2)); demand resets per batch.
## Two-hour Unix time grids, monotone max-seen clock; paid refresh keeps the grid.
## Full definitions come from AndroidContent (InventoryCatalog merged export).
## One purchase is one instance; consumable counts remain separate instances.
## State keys: version,revision,next_seed,offers,purchases,stock,purchase_serial,
## cycle,seen_ms, all inside quest_progress.shop. UI saves the whole transaction.

const Content = preload("res://scripts/content/AndroidContent.gd")
const Progression = preload("res://scripts/progression/ProgressionRules.gd")
const REFRESH_COST := 50
const INTERVAL_MS := 7200000
const MAX_INT := 2147483647

static func candidates() -> Array:
	var result := []
	for definition in Content.catalog().items():
		if int(definition.get("shopStock", 0)) > 0:
			result.append(definition)
	result.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return str(a.id) < str(b.id))
	return result

static func _draw(seed_value: int, count: int) -> Array:
	var next := (seed_value * 48271) % MAX_INT
	return [next, next % count]

static func _generate(shop: ShopState, clears: int, preserve_stock := false) -> Dictionary:
	if shop.revision >= MAX_INT or shop.next_seed < 1 or shop.next_seed >= MAX_INT:
		return _rejected("商店批次或种子无效", shop.revision)
	var groups := {"STAPLE": [], "ROTATING": [], "RARE": []}
	for definition in candidates():
		if int(definition.minimumShopClears) <= clears:
			groups[str(definition.shopShelf)].append(definition)
	var selected: Array = groups.STAPLE.duplicate()
	var pool: Array = groups.ROTATING.duplicate()
	var rotating := []
	var seed_value := shop.next_seed
	while rotating.size() < 6 and not pool.is_empty():
		var draw := _draw(seed_value, pool.size())
		seed_value = draw[0]
		rotating.append(pool.pop_at(int(draw[1])))
	rotating.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return str(a.id) < str(b.id))
	selected.append_array(rotating)
	var rare_draw := _draw(seed_value, 100)
	seed_value = rare_draw[0]
	if int(rare_draw[1]) < 30 and not groups.RARE.is_empty():
		var draw := _draw(seed_value, groups.RARE.size())
		seed_value = draw[0]
		selected.append(groups.RARE[int(draw[1])])
	var offers: Array[Dictionary] = []
	var stock := {}
	var stage := minf(1.1, 0.9 + mini(clears, 5) * 0.04)
	for definition: Dictionary in selected:
		var demand := 1.0 + mini(int(shop.purchases.get(definition.id, 0)), 3) * 0.03
		var price := int(floor(float(definition.price) * clampf(stage * demand, 0.8, 1.2) + 0.5))
		if int(definition.price) > 0: price = maxi(1, price)
		if price > MAX_INT: return _rejected("锁价超出上限", shop.revision)
		offers.append({"id": definition.id, "definition_id": definition.id,
			"display_name": definition.name, "price": price, "shelf": definition.shopShelf,
			"stock": int(definition.shopStock)})
		if preserve_stock and shop.stock.has(definition.id):
			stock[definition.id] = mini(int(shop.stock[definition.id]), int(definition.shopStock))
	shop.revision += 1
	shop.next_seed = seed_value
	shop.offers = offers
	shop.stock = stock
	shop.purchases = {}
	return _accepted(shop)

## Compatibility generation-only API; gameplay must use recover/refresh_shop.
## Generates the full Android pool, never the removed fixed legacy six.
static func refresh(shop: ShopState) -> void:
	_generate(shop, 0)

static func _persist(state: PreparationState, shop: ShopState) -> void:
	state.quest_progress["shop"] = shop.to_data()

static func recover(state: PreparationState, shop: ShopState, now_ms: int) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("挑战中不能刷新商店", shop.revision)
	if now_ms < 0: return _rejected("时间无效", shop.revision)
	if state.quest_progress.has("shop"):
		shop.load_data(state.quest_progress.shop)
	var now := maxi(now_ms, shop.seen_ms)
	# HomeEconomyRules.recover shares the shop clock and both charge anchors.
	# Godot also stores per-counter max-seen timestamps; never move behind those.
	for key in ["chest_refresh", "relief"]:
		var counter: Dictionary = state.quest_progress.get(key, {})
		now = maxi(now, maxi(int(counter.get("anchor", 0)), int(counter.get("seen", 0))))
	@warning_ignore("integer_division")
	var current_cycle: int = now / INTERVAL_MS
	var automatic := current_cycle > shop.cycle
	if shop.revision == 0 or automatic:
		var generated := _generate(shop, maxi(0, int(state.quest_progress.get("clears", 0))), shop.revision == 0 and not automatic)
		if not generated.accepted: return generated
	shop.cycle = current_cycle
	shop.seen_ms = now
	_persist(state, shop)
	return _accepted(shop)

static func countdown(shop: ShopState, now_ms: int) -> int:
	return INTERVAL_MS - maxi(now_ms, shop.seen_ms) % INTERVAL_MS

static func refresh_cost(shop: ShopState, now_ms: int) -> int:
	@warning_ignore("integer_division")
	var current_cycle: int = maxi(now_ms, shop.seen_ms) / INTERVAL_MS
	return 0 if current_cycle > shop.cycle else REFRESH_COST

static func refresh_shop(state: PreparationState, shop: ShopState, now_ms: int) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("挑战中不能刷新商店", shop.revision)
	if now_ms < 0: return _rejected("时间无效", shop.revision)
	if state.quest_progress.has("shop"): shop.load_data(state.quest_progress.shop)
	var cost := refresh_cost(shop, now_ms)
	if state.gold < cost: return _rejected("刷新商店需要%d金币" % cost, shop.revision)
	var old_revision := shop.revision
	var previous := shop.to_data()
	var progress := state.quest_progress.duplicate(true)
	var result := recover(state, shop, now_ms)
	if not result.accepted: return result
	# HomeEconomyRules: absent shelf is generated once by recover.
	if cost > 0 and old_revision > 0:
		result = _generate(shop, maxi(0, int(state.quest_progress.get("clears", 0))))
		if not result.accepted:
			shop.load_data(previous)
			state.quest_progress = progress
			return result
	state.gold -= cost
	_persist(state, shop)
	return _accepted(shop)

static func remaining(shop: ShopState, offer_id: String) -> int:
	var offer := _offer_by_id(shop, offer_id)
	return int(shop.stock.get(offer_id, offer.get("stock", 0)))

static func buy(state: PreparationState, shop: ShopState, offer_id: String, quoted_revision: int, now_ms: int = -1) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("挑战中不能购买", shop.revision)
	# Legacy callers use the stored clock, not wall time.
	var result := recover(state, shop, shop.seen_ms if now_ms == -1 else now_ms)
	if not result.accepted: return result
	if quoted_revision != shop.revision:
		return _rejected("货架已刷新，请按新价格重试", shop.revision)
	var offer := _offer_by_id(shop, offer_id)
	if offer.is_empty(): return _rejected("此商品本轮未上架", shop.revision)
	var definition := Content.item(str(offer.definition_id))
	if definition.is_empty() or int(definition.shopStock) <= 0:
		return _rejected("商品配置无效", shop.revision)
	if int(state.quest_progress.get("clears", 0)) < int(definition.minimumShopClears):
		return _rejected("此商品尚未开放", shop.revision)
	if remaining(shop, offer_id) <= 0: return _rejected("本轮已售完", shop.revision)
	var price := int(offer.price)
	if price < 0 or state.gold < price: return _rejected("金币不足", shop.revision)
	if state.items.size() >= 10000: return _rejected("仓库容量达到上限", shop.revision)
	var serial := shop.purchase_serial + 1
	var item_id := "shop_%s_%d" % [definition.id, serial]
	while state.items.has(item_id):
		serial += 1
		item_id = "shop_%s_%d" % [definition.id, serial]
	var item := Content.create_instance(item_id, str(definition.id))
	state.gold -= price
	state.add_item(item)
	shop.purchase_serial = serial
	shop.stock[offer_id] = remaining(shop, offer_id) - 1
	shop.purchases[offer_id] = mini(100, int(shop.purchases.get(offer_id, 0)) + 1)
	_persist(state, shop)
	Progression.record(state, {"type": "ShopPurchase"})
	result = _accepted(shop)
	result["item_id"] = item.id
	return result

static func _offer_by_id(shop: ShopState, offer_id: String) -> Dictionary:
	for offer in shop.offers:
		if str(offer.id) == offer_id: return offer
	return {}

static func _accepted(shop: ShopState) -> Dictionary:
	return {"accepted": true, "reason": "", "revision": shop.revision}

static func _rejected(reason: String, revision: int) -> Dictionary:
	return {"accepted": false, "reason": reason, "revision": revision}
