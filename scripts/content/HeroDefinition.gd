class_name HeroDefinition
extends RefCounted

var id: String
var display_name: String
var max_hp: int
var max_mp: int
var attack_damage: int
var armor: int
var skill: Dictionary

func _init(
	p_id: String,
	p_display_name: String,
	p_max_hp: int,
	p_max_mp: int,
	p_attack_damage: int,
	p_armor: int,
	p_skill: Dictionary
) -> void:
	id = p_id
	display_name = p_display_name
	max_hp = p_max_hp
	max_mp = p_max_mp
	attack_damage = p_attack_damage
	armor = p_armor
	skill = p_skill.duplicate(true)
