class_name LoadoutPreset
extends RefCounted

var equipment_ids: Array[String]
var card_ids: Array[String]

func _init(p_equipment_ids: Array[String] = [], p_card_ids: Array[String] = []) -> void:
	equipment_ids = p_equipment_ids.duplicate()
	card_ids = p_card_ids.duplicate()

func copy() -> LoadoutPreset:
	return LoadoutPreset.new(equipment_ids, card_ids)
