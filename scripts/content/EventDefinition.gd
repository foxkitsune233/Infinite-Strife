class_name EventDefinition
extends RefCounted

var id: String
var display_name: String
var category: String
var options: Array[Dictionary]

func _init(p_id: String, p_display_name: String, p_category: String, p_options: Array[Dictionary]) -> void:
	id = p_id
	display_name = p_display_name
	category = p_category
	options = p_options.duplicate(true)
