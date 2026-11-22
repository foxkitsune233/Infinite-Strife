extends RefCounted

const Items = preload("res://scripts/content/AndroidEquipmentCatalog.gd")
const MaterialProgression = preload("res://scripts/journey/MaterialProgression.gd")
const QUALITIES := ["白", "蓝", "紫", "金"]
const REFRESH_INTERVAL_MS := 1800000

static func refresh_status(state: PreparationState, now_ms: int) -> Dictionary:
	var old: Dictionary = state.quest_progress.get("chest_refresh", {})
	var now := maxi(maxi(0, now_ms), int(old.get("seen", 0)))
	now = maxi(now, int(state.quest_progress.get("relief", {}).get("seen", 0)))
	now = maxi(now, int(state.quest_progress.get("shop", {}).get("seen_ms", 0)))
	var charges := clampi(int(old.get("charges", 3)), 0, 3)
	var anchor := int(old.get("anchor", now))
	now = maxi(now, anchor)
	if charges == 3:
		anchor = now
	else:
		var steps := int((now - anchor) / REFRESH_INTERVAL_MS)
		charges = mini(3, charges + steps)
		anchor = now if charges == 3 else anchor + steps * REFRESH_INTERVAL_MS
	return {"charges": charges, "anchor": anchor, "seen": now,
		"next_ms": 0 if charges == 3 else REFRESH_INTERVAL_MS - (now - anchor)}

static func refresh_cost(state: PreparationState, target: Dictionary, now_ms: int) -> int:
	if int(refresh_status(state, now_ms).charges) > 0:
		return 0
	return int({"白": 20, "蓝": 30, "紫": 50, "金": 80}.get(target.get("quality"), 20))

static func replace(state: PreparationState, target: Dictionary, now_ms: int) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return {"accepted": false, "reason": "挑战中不能刷新"}
	var cost := refresh_cost(state, target, now_ms)
	if state.gold < cost:
		return {"accepted": false, "reason": "免费次数已用完，金币不足"}
	var previous_seed: Variant = state.quest_progress.get("chest_seed")
	var random := {"seed": clampi(int(state.quest_progress.get("chest_seed", 7001)), 1, 2147483646)}
	var quality: String = QUALITIES[_draw(random, 4)]
	var difficulty := int(target.difficulty)
	var replacement := {"name": target.name, "hint": target.hint, "quality": quality, "difficulty": difficulty}
	var base: int = [120, 220, 420, 700][QUALITIES.find(quality)]
	replacement["ticket"] = ceili(base * int([2, 3, 4, 5, 6, 8][difficulty]) / 20.0) * 10
	state.quest_progress["chest_seed"] = random.seed
	if not generate(state, replacement, str(target.get("theme", "supplies"))):
		if previous_seed == null:
			state.quest_progress.erase("chest_seed")
		else:
			state.quest_progress["chest_seed"] = previous_seed
		return {"accepted": false, "reason": "缺少可用宝箱物品"}
	var status := refresh_status(state, now_ms)
	if int(status.charges) > 0:
		status.charges = int(status.charges) - 1
	state.gold -= cost
	state.quest_progress["chest_refresh"] = status
	target.clear()
	target.merge(replacement, true)
	return {"accepted": true, "reason": "已更换宝箱，鉴定线索已重置"}

static func _draw(random: Dictionary, count: int) -> int:
	random.seed = int(random.seed) * 48271 % 2147483647
	return int(random.seed) % count

static func generate(state: PreparationState, target: Dictionary, theme: String) -> bool:
	if target.has("items"):
		return true # Fixed content must not consume another pending guarantee.
	var rank := QUALITIES.find(str(target.quality))
	if rank < 0:
		return false
	var difficulty := int(target.difficulty)
	var clears := int(state.quest_progress.get("clears", 0))
	var capacity: Array = [[4, 3], [5, 4], [6, 5], [6, 6]][rank]
	var definitions := Items.items()
	var tiers := {}
	for item in definitions:
		if int(item.width) > int(capacity[0]) or int(item.height) > int(capacity[1]):
			continue
		if item.get("category") != "MAIN":
			continue
		var price := int(item.price)
		var tier := 5 if price >= 5500 else 4 if price >= 4500 else 3 if price >= 3500 else 2 if price >= 2500 else 1
		tiers[item.id] = tier
		if item.recipe is Dictionary:
			for id in item.recipe.ingredients:
				if str(id).begins_with("recipe_"):
					tiers[id] = mini(tier, int(tiers.get(id, tier)))
	var pools: Array = [[], [], []]
	var valuables: Array[String] = []
	for item in definitions:
		if int(item.minimumDropClears) > clears or int(tiers.get(item.id, 0)) > difficulty:
			continue
		if item.kind == "LOOT":
			var loot_rank := QUALITIES.find(str(item.quality))
			if loot_rank >= 0 and loot_rank <= mini(3, rank + difficulty):
				for _index in int([12, 6, 2, 1][loot_rank]) * (difficulty + 1 if loot_rank >= 2 else 1):
					valuables.append(str(item.id))
			continue
		var weight := 1
		match theme:
			"arsenal":
				weight = 4 if item.kind in ["EQUIPMENT", "SCROLL"] else 1
			"arcane":
				weight = 4 if item.kind == "CARD" else 1
			"supplies":
				weight = 4 if item.kind == "CONSUMABLE" else 3 if item.id == "essence" else 1
			"backpack", "skills", "safe":
				var material_ids: Array = {"backpack": ["backpack_fabric"], "skills": ["skill_shard"], "safe": ["safe_lock", "safe_frame"]}[theme]
				weight = 6 if item.id in material_ids else 3 if item.id == "essence" or theme == "skills" and item.kind == "CARD" else 1
		for pool_index in 3:
			var is_main: bool = item.get("category") == "MAIN"
			var allowed := true
			if item.kind == "MATERIAL":
				allowed = pool_index != 1
			else:
				if is_main and (rank < 2 or pool_index != 1 and not (pool_index == 2 and rank == 2)):
					allowed = false
				if pool_index == 1 and not is_main:
					allowed = false
				var cap := int(([700, 2000, 5000, 8000] if pool_index == 2 else [300, 1200, 3500, 8000])[rank])
				if int(item.price) > cap:
					allowed = false
			if allowed:
				for _index in weight:
					pools[pool_index].append(str(item.id))
	if pools[0].is_empty():
		return false
	var guaranteed: Array[String] = []
	var forced := ""
	for cat in MaterialProgression.eligible(state, theme, difficulty):
		if bool(state.quest_progress.get("material_pity", {}).get(cat, {}).get("pending", false)):
			forced = MaterialProgression.needed_item(state, cat)
			var definition := Items.item(forced)
			if definition.is_empty() or int(definition.width) > int(capacity[0]) or int(definition.height) > int(capacity[1]):
				return false
			guaranteed.append(cat)
	var random := {"seed": clampi(int(state.quest_progress.get("chest_seed", 7001)), 1, 2147483646)}
	var rewards: Array[String] = []
	for _index in int([4, 7, 10, 14][rank]):
		var selector := _draw(random, 100)
		var main_chance := int([0, 0, 5, 10][rank])
		var pool_index := 1 if selector < main_chance else 2 if selector < main_chance + int([2, 3, 3, 0][rank]) else 0
		var pool: Array = pools[pool_index] if not pools[pool_index].is_empty() else pools[0]
		var id := str(pool[_draw(random, pool.size())])
		if not valuables.is_empty() and _draw(random, 100) < 20 + rank * 5 + difficulty * 5:
			id = valuables[_draw(random, valuables.size())]
		rewards.append(forced if _index == 0 and not forced.is_empty() else id)
	target["rewards"] = rewards
	target["theme"] = theme
	initialize(target)
	var packed: Array[String] = []
	for index in target.items.size():
		packed.append(str(target.items[index].id))
		target.items[index].reveal = 2 if index == 0 else 1 if index % 3 == 0 else 0
	target.rewards = packed
	var counters: Dictionary = state.quest_progress.get("material_pity", {}).duplicate(true)
	for cat in guaranteed:
		counters[cat] = {"misses": 0, "pending": false}
	target["guaranteed_categories"] = guaranteed
	state.quest_progress["material_pity"] = counters
	state.quest_progress["chest_seed"] = random.seed
	return true

static func appraisal_fee(target: Dictionary) -> int:
	return 10 + mini(4, int(target.get("appraisals", 0))) * 10

static func initialize(target: Dictionary) -> void:
	if target.has("items"):
		return
	var capacity: Array = {"白": [4, 3], "蓝": [5, 4], "紫": [6, 5], "金": [6, 6]}.get(target.get("quality", "白"), [4, 3])
	target["columns"] = capacity[0]
	target["rows"] = capacity[1]
	var entries: Array[Dictionary] = []
	var occupied := {}
	for id in target.get("rewards", []):
		var definition := Items.item(str(id))
		if definition.is_empty():
			continue
		var placed := false
		for y in int(capacity[1]):
			for x in int(capacity[0]):
				if placed or x + int(definition.width) > int(capacity[0]) or y + int(definition.height) > int(capacity[1]):
					continue
				var free := true
				for dy in int(definition.height):
					for dx in int(definition.width):
						if occupied.has(Vector2i(x + dx, y + dy)):
							free = false
				if not free:
					continue
				for dy in int(definition.height):
					for dx in int(definition.width):
						occupied[Vector2i(x + dx, y + dy)] = true
				entries.append({"id": id, "x": x, "y": y, "reveal": 0})
				placed = true
	target["items"] = entries
	target["appraisals"] = 0

static func appraise(state: PreparationState, target: Dictionary) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return {"accepted": false, "reason": "挑战中不能鉴定"}
	initialize(target)
	var fee := appraisal_fee(target)
	if state.gold < fee:
		return {"accepted": false, "reason": "金币不足"}
	var unknown: Array[int] = []
	for index in target.items.size():
		if int(target.items[index].reveal) < 2:
			unknown.append(index)
	if unknown.is_empty():
		return {"accepted": false, "reason": "已全部公开"}
	var seed := clampi(int(state.quest_progress.get("chest_seed", 7001)), 1, 2147483646)
	seed = seed * 48271 % 2147483647
	var count := mini(unknown.size(), 1 + seed % 3)
	var extra := 0
	for _index in count:
		seed = seed * 48271 % 2147483647
		var position := seed % unknown.size()
		var entry: Dictionary = target.items[unknown[position]]
		unknown.remove_at(position)
		var definition := Items.item(str(entry.id))
		seed = seed * 48271 % 2147483647
		var reveal := 2 if int(entry.reveal) == 1 or seed % 100 < 30 else 1
		if int(entry.reveal) == 0:
			extra += int({"金": 120, "紫": 60, "蓝": 25}.get(definition.get("quality"), 10))
		if reveal == 2:
			extra += int(float(definition.get("price", 0)) * 0.4 * 1.1)
		entry.reveal = reveal
	target.ticket = mini(2147483647, int(target.ticket) + extra)
	target.appraisals = int(target.appraisals) + 1
	state.gold -= fee
	state.quest_progress["chest_seed"] = seed
	return {"accepted": true, "reason": "鉴定完成，入场价增加 %d 金币" % extra}
