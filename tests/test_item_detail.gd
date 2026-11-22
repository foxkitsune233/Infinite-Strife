extends SceneTree

const Detail = preload("res://scripts/ui/ItemDetailSheet.gd")
const PackingRules = preload("res://scripts/prepare/PackingRules.gd")

var _failed := false

func _assert(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)

func _collect_text(node: Node, buffer: Array[String]) -> void:
	if node is Label:
		buffer.append((node as Label).text)
	for child in node.get_children():
		_collect_text(child, buffer)

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var state := PreparationState.new("juggernaut")
	# 矩形物品（2×1）：旋转有视觉意义，预览按钮应出现。放在背包，验证预览不改动物品。
	var item := ItemInstance.new("blade", "blade_mail", ItemInstance.Kind.EQUIPMENT)
	item.value = 2600
	item.footprint = Vector2i(2, 1)
	state.add_item(item)
	PackingRules.place(state, item.id, ItemInstance.Location.BACKPACK, Vector2i(0, 0), false)
	var dialog := Detail.show(root, state, item.id)
	await process_frame
	_assert(dialog != null, "detail dialog created")
	_assert(dialog.title == "物品详情", "detail dialog titled")
	var lines: Array[String] = []
	_collect_text(dialog, lines)
	var text := "\n".join(lines)
	_assert(text.contains("刃甲") or text.contains("blade_mail"), "detail shows item name")
	_assert(text.contains("2×1格"), "detail shows footprint")
	_assert(text.contains("1040"), "detail shows 40 percent sale value (2600 * 40%)")
	_assert(text.contains("护甲"), "detail shows stats")
	var rotate_button: Button = dialog.find_child("RotatePreview", true, false) as Button
	_assert(rotate_button != null, "rectangular item exposes rotation preview")
	var before_rotated: bool = state.item(item.id).rotated
	var before_footprint: Vector2i = state.item(item.id).footprint
	if rotate_button != null:
		rotate_button.pressed.emit()
		_assert(state.item(item.id).rotated == before_rotated, "preview does not flip item.rotated")
		_assert(state.item(item.id).footprint == before_footprint, "preview does not change footprint")
		var after: Array[String] = []
		_collect_text(dialog, after)
		_assert("\n".join(after).contains("1×2格"), "preview shows swapped dimensions")
		_assert("\n".join(after).contains("预览旋转"), "preview is labelled as preview")
		rotate_button.pressed.emit()
		var restored: Array[String] = []
		_collect_text(dialog, restored)
		_assert("\n".join(restored).contains("2×1格"), "toggling preview restores original dimensions")
	dialog.hide()
	dialog.queue_free()
	await process_frame
	# 正方形物品旋转无视觉意义，不应显示预览按钮。
	var square := ItemInstance.new("square", "chainmail", ItemInstance.Kind.EQUIPMENT)
	square.footprint = Vector2i(2, 2)
	state.add_item(square)
	var square_dialog := Detail.show(root, state, square.id)
	await process_frame
	_assert(square_dialog.find_child("RotatePreview", true, false) == null, "square item has no preview button")
	square_dialog.queue_free()
	await process_frame
	# 仓库物品也能打开详情（不因旋转规则受限）。
	var warehouse_item := ItemInstance.new("wh", "broadsword", ItemInstance.Kind.EQUIPMENT)
	warehouse_item.footprint = Vector2i(2, 1)
	state.add_item(warehouse_item)
	PackingRules.place(state, warehouse_item.id, ItemInstance.Location.WAREHOUSE, Vector2i.ZERO, false)
	var wh_dialog := Detail.show(root, state, warehouse_item.id)
	await process_frame
	_assert(wh_dialog.find_child("RotatePreview", true, false) != null, "warehouse item can preview rotation")
	wh_dialog.queue_free()
	await process_frame
	var missing := Detail.show(root, state, "nonexistent")
	_assert(missing != null, "missing item opens a safe dialog")
	missing.queue_free()
	quit(1 if _failed else 0)

func _all_buttons(node: Node) -> Array[Button]:
	var result: Array[Button] = []
	if node is Button:
		result.append(node)
	for child in node.get_children():
		result.append_array(_all_buttons(child))
	return result
