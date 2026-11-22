class_name InventoryDragButton
extends Button

var instance_id := ""
var drop_target: Dictionary = {}
var drop_handler: Callable
var drop_cell_size := 0.0

func _get_drag_data(_at_position: Vector2) -> Variant:
	if instance_id.is_empty():
		return null
	var preview := Label.new()
	preview.text = tooltip_text if text.is_empty() else text
	preview.modulate = Color(1.0, 0.86, 0.45, 0.88)
	set_drag_preview(preview)
	return {"infinite_strife_instance_id": instance_id}

func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return drop_handler.is_valid() and data is Dictionary and data.has("infinite_strife_instance_id")

func _drop_data(_at_position: Vector2, data: Variant) -> void:
	if _can_drop_data(_at_position, data):
		var target := drop_target.duplicate()
		if drop_cell_size > 0.0:
			target.position += Vector2i((_at_position / drop_cell_size).floor())
		drop_handler.call(str(data["infinite_strife_instance_id"]), target)
