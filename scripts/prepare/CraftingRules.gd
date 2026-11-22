class_name CraftingRules
extends RefCounted

const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")

static func entries(state: PreparationState) -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	for definition in ContentCatalog.items():
		if definition.recipe.is_empty():
			continue
		if definition.recipe.any(func(id: String) -> bool: return ContentCatalog.item(id) == null):
			continue
		var material_result := _materials_for(state, definition.recipe)
		result.append({
			"definition_id": definition.id,
			"display_name": definition.display_name,
			"craftable": material_result.ok,
			"missing": material_result.missing,
			"locked": material_result.locked,
		})
	result.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		if bool(a.craftable) != bool(b.craftable):
			return bool(a.craftable)
		return str(a.display_name) < str(b.display_name)
	)
	return result

static func craft(state: PreparationState, definition_id: String) -> Dictionary:
	if state.phase != PreparationState.Phase.HOME:
		return _rejected("仅营地可合成")
	var definition := ContentCatalog.item(definition_id)
	if definition == null or definition.recipe.is_empty():
		return _rejected("该物品不可合成")
	if definition.recipe.any(func(id: String) -> bool: return ContentCatalog.item(id) == null):
		return _rejected("该原型配方已停用")
	var material_result := _materials_for(state, definition.recipe)
	if not material_result.locked.is_empty():
		return _rejected("材料已锁定")
	if not material_result.missing.is_empty():
		return _rejected("材料不足")
	for instance_id in material_result.ids:
		state.remove_item(str(instance_id))
	state.craft_serial += 1
	var output := ItemInstance.new("craft_%s_%d" % [definition_id, state.craft_serial], definition.id, ItemInstance.Kind.EQUIPMENT)
	output.footprint = definition.footprint
	output.value = definition.value
	state.add_item(output)
	preload("res://scripts/progression/ProgressionRules.gd").record(state, {"type": "Crafted"})
	return _accepted()

static func _materials_for(state: PreparationState, recipe: Array[String]) -> Dictionary:
	var selected: Array[String] = []
	var missing: Array[String] = []
	var locked: Array[String] = []
	var used := {}
	for ingredient_id in recipe:
		var available := ""
		var has_locked := false
		for candidate in state.items.values():
			if candidate.definition_id != ingredient_id or candidate.location != ItemInstance.Location.WAREHOUSE or used.has(candidate.id):
				continue
			if candidate.locked:
				has_locked = true
				continue
			available = candidate.id
			break
		if available.is_empty():
			if has_locked:
				locked.append(ingredient_id)
			else:
				missing.append(ingredient_id)
			continue
		used[available] = true
		selected.append(available)
	return {"ok": missing.is_empty() and locked.is_empty(), "ids": selected, "missing": missing, "locked": locked}

static func _accepted() -> Dictionary:
	return {"accepted": true, "reason": ""}

static func _rejected(reason: String) -> Dictionary:
	return {"accepted": false, "reason": reason}
