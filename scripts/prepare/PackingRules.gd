class_name PackingRules
extends RefCounted

static func place(state: PreparationState, instance_id: String, container: ItemInstance.Location, position: Vector2i, rotated: bool) -> Dictionary:
	if not _is_grid(container):
		return _rejected("目标不是可整理容器")
	var instance := state.item(instance_id)
	if instance == null:
		return _rejected("找不到物品")
	if state.phase == PreparationState.Phase.BATTLE:
		return _rejected("战斗中不能调整物品")
	if state.phase != PreparationState.Phase.HOME and instance.location == ItemInstance.Location.WAREHOUSE:
		return _rejected("挑战中不能访问局外仓库")
	if not _fits(state, instance, container, position, rotated, instance_id):
		return _rejected("物品无法放入该格位")
	_clear_active_slot(state, instance)
	instance.location = container
	instance.slot = -1
	instance.grid_position = position
	instance.rotated = rotated
	return _accepted()

static func rotate(state: PreparationState, instance_id: String) -> Dictionary:
	if state.phase == PreparationState.Phase.BATTLE:
		return _rejected("战斗中不能调整物品")
	var instance := state.item(instance_id)
	if instance == null:
		return _rejected("找不到物品")
	if not _is_grid(instance.location):
		return _rejected("只有背包或安全箱物品可以旋转")
	var next_rotation := not instance.rotated
	if not _fits(state, instance, instance.location, instance.grid_position, next_rotation, instance_id):
		return _rejected("旋转后没有连续空位")
	instance.rotated = next_rotation
	return _accepted()

static func toggle_lock(state: PreparationState, instance_id: String) -> Dictionary:
	var instance := state.item(instance_id)
	if instance == null:
		return _rejected("找不到物品")
	instance.locked = not instance.locked
	return _accepted()

## 对齐 Android SupplyRules.saleValue：基础价 40% 回收，升星卡按 3/9 份成本计。
static func sale_value(instance: ItemInstance) -> int:
	var copies := 1
	if instance.kind == ItemInstance.Kind.CARD:
		copies = 9 if instance.star == 3 else 3 if instance.star == 2 else 1
	return int(float(instance.value) * float(copies) * 40.0 / 100.0)

static func quick_sell(state: PreparationState, instance_id: String) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("仅营地可出售")
	var instance := state.item(instance_id)
	if instance == null:
		return _rejected("找不到物品")
	if instance.locked:
		return _rejected("锁定物品不可快速出售")
	if instance.bound:
		return _rejected("绑定物品不可出售")
	if instance.location == ItemInstance.Location.EQUIPMENT or instance.location == ItemInstance.Location.CARD:
		return _rejected("请先卸下已配装物品")
	state.gold += sale_value(instance)
	state.remove_item(instance_id)
	return _accepted()

## Sells only unlocked items still in the external warehouse.  Equipped, carried,
## secured and locked items are deliberately excluded from the bulk action.
static func batch_sell_warehouse(state: PreparationState) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return {"accepted": false, "reason": "仅营地可出售", "sold": 0, "value": 0}
	var sold := 0
	var value := 0
	var ids_to_sell: Array[String] = []
	for instance in state.items.values():
		if instance.location != ItemInstance.Location.WAREHOUSE or instance.locked or instance.bound:
			continue
		ids_to_sell.append(instance.id)
	for instance_id in ids_to_sell:
		var instance := state.item(instance_id)
		if instance == null:
			continue
		sold += 1
		value += sale_value(instance)
		state.remove_item(instance_id)
	if sold == 0:
		return {"accepted": false, "reason": "仓库中没有可出售的未锁定物品", "sold": 0, "value": 0}
	state.gold += value
	return {"accepted": true, "reason": "", "sold": sold, "value": value}

static func move_to_warehouse(state: PreparationState, instance_id: String) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("挑战中不能访问局外仓库")
	var instance := state.item(instance_id)
	if instance == null:
		return _rejected("找不到物品")
	_clear_active_slot(state, instance)
	instance.location = ItemInstance.Location.WAREHOUSE
	instance.slot = -1
	instance.grid_position = Vector2i.ZERO
	instance.rotated = false
	return _accepted()

static func organize(state: PreparationState, container: ItemInstance.Location) -> Dictionary:
	if state.phase == PreparationState.Phase.BATTLE:
		return _rejected("战斗中不能整理物品")
	if not _is_grid(container):
		return _rejected("只能整理背包或安全箱")
	var size := _grid_size(state, container)
	var items: Array[ItemInstance] = []
	for candidate in state.items.values():
		if candidate.location == container:
			items.append(candidate)
	items.sort_custom(func(a: ItemInstance, b: ItemInstance) -> bool:
		var area_a := a.footprint.x * a.footprint.y
		var area_b := b.footprint.x * b.footprint.y
		return area_a > area_b if area_a != area_b else a.id < b.id
	)
	var used := {}
	var placements := {}
	for instance in items:
		var placed := false
		for rotated in [instance.rotated, not instance.rotated]:
			var dimensions := _dimensions(instance, rotated)
			for y in range(size.y - dimensions.y + 1):
				for x in range(size.x - dimensions.x + 1):
					var candidate := Vector2i(x, y)
					if _fits_used(dimensions, candidate, size, used):
						placements[instance.id] = {"position": candidate, "rotated": rotated}
						_mark_used(dimensions, candidate, used)
						placed = true
						break
				if placed:
					break
			if placed:
				break
		if not placed:
			return _rejected("无法整理：没有连续空位")
	for instance in items:
		var placement: Dictionary = placements[instance.id]
		instance.grid_position = placement.position
		instance.rotated = placement.rotated
	return _accepted()

static func _fits(state: PreparationState, instance: ItemInstance, container: ItemInstance.Location, position: Vector2i, rotated: bool, ignored_id: String) -> bool:
	var size := _grid_size(state, container)
	var dimensions := _dimensions(instance, rotated)
	if position.x < 0 or position.y < 0 or position.x + dimensions.x > size.x or position.y + dimensions.y > size.y:
		return false
	for candidate in state.items.values():
		if candidate.id == ignored_id or candidate.location != container:
			continue
		if _overlaps(position, dimensions, candidate.grid_position, _dimensions(candidate, candidate.rotated)):
			return false
	return true

static func _grid_size(state: PreparationState, container: ItemInstance.Location) -> Vector2i:
	return state.backpack_size if container == ItemInstance.Location.BACKPACK else state.safe_size

static func _clear_active_slot(state: PreparationState, instance: ItemInstance) -> void:
	if instance.location == ItemInstance.Location.EQUIPMENT and instance.slot >= 0 and instance.slot < state.equipment_slots.size():
		state.equipment_slots[instance.slot] = ""
	elif instance.location == ItemInstance.Location.CARD and instance.slot >= 0 and instance.slot < state.card_slots.size():
		state.card_slots[instance.slot] = ""

static func _dimensions(instance: ItemInstance, rotated: bool) -> Vector2i:
	return Vector2i(instance.footprint.y, instance.footprint.x) if rotated else instance.footprint

static func _is_grid(location: ItemInstance.Location) -> bool:
	return location == ItemInstance.Location.BACKPACK or location == ItemInstance.Location.SAFE

static func _overlaps(position_a: Vector2i, size_a: Vector2i, position_b: Vector2i, size_b: Vector2i) -> bool:
	return position_a.x < position_b.x + size_b.x and position_a.x + size_a.x > position_b.x and position_a.y < position_b.y + size_b.y and position_a.y + size_a.y > position_b.y

static func _fits_used(dimensions: Vector2i, position: Vector2i, size: Vector2i, used: Dictionary) -> bool:
	if position.x + dimensions.x > size.x or position.y + dimensions.y > size.y:
		return false
	for x in range(position.x, position.x + dimensions.x):
		for y in range(position.y, position.y + dimensions.y):
			if used.has(Vector2i(x, y)):
				return false
	return true

static func _mark_used(dimensions: Vector2i, position: Vector2i, used: Dictionary) -> void:
	for x in range(position.x, position.x + dimensions.x):
		for y in range(position.y, position.y + dimensions.y):
			used[Vector2i(x, y)] = true

static func _accepted() -> Dictionary:
	return {"accepted": true, "reason": ""}

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
