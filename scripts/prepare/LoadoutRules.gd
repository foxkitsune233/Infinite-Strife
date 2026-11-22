class_name LoadoutRules
extends RefCounted

static func place(state: PreparationState, instance_id: String, kind: ItemInstance.Kind, slot: int) -> Dictionary:
	if state.phase == PreparationState.Phase.BATTLE:
		return _rejected("战斗中不能调整物品")
	var instance := state.item(instance_id)
	if instance == null:
		return _rejected("物品已不在仓库")
	if state.phase != PreparationState.Phase.HOME and instance.location == ItemInstance.Location.WAREHOUSE:
		return _rejected("挑战中不能访问局外仓库")
	var combat_consumable := kind == ItemInstance.Kind.EQUIPMENT and instance.kind == ItemInstance.Kind.CONSUMABLE and instance.definition_id in ["faerie_fire", "cheese"]
	if instance.kind != kind and not combat_consumable:
		return _rejected("物品类型不匹配")
	var slots := _slots_for(state, kind)
	if slot < 0 or slot >= slots.size():
		return _rejected("目标栏位无效")
	if kind == ItemInstance.Kind.CARD and slot >= preload("res://scripts/prepare/CardPreparationRules.gd").enabled_slots(state):
		return _rejected("技能栏位尚未解锁")

	var replaced_id: String = slots[slot]
	var origin := instance.location
	var origin_slot := instance.slot
	var position := instance.grid_position
	var rotation := instance.rotated
	_clear_instance_from_active_slot(state, instance)
	instance.location = _location_for(kind)
	instance.slot = slot
	slots[slot] = instance_id
	if not replaced_id.is_empty() and replaced_id != instance_id:
		var replaced := state.item(replaced_id)
		if replaced != null:
			if origin in [ItemInstance.Location.BACKPACK, ItemInstance.Location.SAFE]:
				var packing = preload("res://scripts/prepare/PackingRules.gd")
				var fit := packing.place(state, replaced.id, origin, position, rotation)
				if not fit.accepted:
					fit = packing.place(state, replaced.id, origin, position, not rotation)
				if not fit.accepted:
					for y in state.backpack_size.y:
						for x in state.backpack_size.x:
							for turned in [false, true]:
								if not fit.accepted:
									fit = packing.place(state, replaced.id, ItemInstance.Location.BACKPACK, Vector2i(x, y), turned)
				if not fit.accepted:
					slots[slot] = replaced_id
					instance.location = origin
					instance.slot = origin_slot
					return _rejected("旧装备无法放回原位置，背包也没有空间")
				slots[slot] = instance_id
			else:
				replaced.location = origin
				replaced.slot = origin_slot
				if origin in [ItemInstance.Location.EQUIPMENT, ItemInstance.Location.CARD]:
					slots[origin_slot] = replaced.id
	return _accepted()

static func save_preset(state: PreparationState, preset_index: int) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("仅营地可保存方案")
	if not _valid_preset_index(preset_index):
		return _rejected("方案编号无效")
	var preset := state.preset(state.active_hero_id, preset_index)
	preset.equipment_ids = state.equipment_slots.duplicate()
	preset.card_ids = state.card_slots.duplicate()
	for index in range(preload("res://scripts/prepare/CardPreparationRules.gd").enabled_slots(state), preset.card_ids.size()):
		preset.card_ids[index] = ""
	state.set_selected_preset(state.active_hero_id, preset_index)
	return _accepted()

static func select_preset(state: PreparationState, preset_index: int) -> Dictionary:
	return apply_preset(state, preset_index)

static func activate_hero(state: PreparationState, hero_id: String) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("挑战中不能更换英雄")
	if hero_id not in state.unlocked_heroes:
		return _rejected("英雄尚未解锁")
	state.active_hero_id = hero_id
	return apply_preset(state, state.selected_preset(hero_id))

static func apply_preset(state: PreparationState, preset_index: int) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("挑战中不能套用局外方案")
	if not _valid_preset_index(preset_index):
		return _rejected("方案编号无效")
	_move_active_items_to_warehouse(state)
	var preset := state.preset(state.active_hero_id, preset_index)
	_apply_group(state, preset.equipment_ids, ItemInstance.Kind.EQUIPMENT)
	_apply_group(state, preset.card_ids, ItemInstance.Kind.CARD)
	state.set_selected_preset(state.active_hero_id, preset_index)
	return _accepted()

static func _apply_group(state: PreparationState, saved_ids: Array[String], kind: ItemInstance.Kind) -> void:
	var slots := _slots_for(state, kind)
	for index in slots.size():
		if kind == ItemInstance.Kind.CARD and index >= preload("res://scripts/prepare/CardPreparationRules.gd").enabled_slots(state):
			slots[index] = ""
			continue
		var instance_id := saved_ids[index] if index < saved_ids.size() else ""
		var instance := state.item(instance_id)
		if instance == null or instance.kind != kind:
			slots[index] = ""
			if index < saved_ids.size():
				saved_ids[index] = ""
			continue
		slots[index] = instance_id
		instance.location = _location_for(kind)
		instance.slot = index

static func _move_active_items_to_warehouse(state: PreparationState) -> void:
	for instance_id in state.equipment_slots + state.card_slots:
		var instance := state.item(instance_id)
		if instance != null:
			instance.location = ItemInstance.Location.WAREHOUSE
			instance.slot = -1
	for index in state.equipment_slots.size():
		state.equipment_slots[index] = ""
	for index in state.card_slots.size():
		state.card_slots[index] = ""

static func _clear_instance_from_active_slot(state: PreparationState, instance: ItemInstance) -> void:
	if instance.location == ItemInstance.Location.EQUIPMENT and instance.slot >= 0 and instance.slot < state.equipment_slots.size():
		state.equipment_slots[instance.slot] = ""
	elif instance.location == ItemInstance.Location.CARD and instance.slot >= 0 and instance.slot < state.card_slots.size():
		state.card_slots[instance.slot] = ""

static func _slots_for(state: PreparationState, kind: ItemInstance.Kind) -> Array[String]:
	return state.equipment_slots if kind == ItemInstance.Kind.EQUIPMENT else state.card_slots

static func _location_for(kind: ItemInstance.Kind) -> ItemInstance.Location:
	return ItemInstance.Location.EQUIPMENT if kind == ItemInstance.Kind.EQUIPMENT else ItemInstance.Location.CARD

static func _valid_preset_index(preset_index: int) -> bool:
	return preset_index >= 0 and preset_index < PreparationState.PRESET_COUNT

static func _accepted() -> Dictionary:
	return {"accepted": true, "reason": ""}

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
