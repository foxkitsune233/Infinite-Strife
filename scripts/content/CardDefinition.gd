class_name CardDefinition
extends RefCounted

var id: String
var display_name: String
var school_ids: Array[String]
var description: String
var effect: Dictionary

func _init(p_id: String, p_display_name: String, p_school_ids: Array[String], p_description: String, p_effect: Dictionary) -> void:
	id = p_id
	display_name = p_display_name
	school_ids = p_school_ids.duplicate()
	description = p_description
	effect = p_effect.duplicate(true)
