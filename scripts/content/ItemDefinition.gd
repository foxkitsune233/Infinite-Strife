class_name ItemDefinition
extends RefCounted

var id: String
var display_name: String
var rarity: String
var footprint: Vector2i
var value: int
var recipe: Array[String]
var active: Dictionary
var source_data: Dictionary = {}

func _init(
	p_id: String,
	p_display_name: String,
	p_rarity: String,
	p_footprint: Vector2i,
	p_value: int,
	p_recipe: Array[String] = [],
	p_active: Dictionary = {}
) -> void:
	id = p_id
	display_name = p_display_name
	rarity = p_rarity
	footprint = p_footprint
	value = p_value
	recipe = p_recipe.duplicate()
	active = p_active.duplicate(true)
