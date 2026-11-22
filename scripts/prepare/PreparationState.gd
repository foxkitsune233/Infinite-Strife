class_name PreparationState
extends RefCounted

const EQUIPMENT_SLOT_COUNT := 9
const ACTIVE_EQUIPMENT_SLOT_COUNT := 6
const CARD_SLOT_COUNT := 8
const PRESET_COUNT := 3

enum Phase {
	HOME,
	JOURNEY,
	BATTLE,
}

var phase: Phase = Phase.HOME
var active_hero_id: String
var gold := 1200
var backpack_size := Vector2i(4, 3)
var safe_size := Vector2i(2, 2)
var craft_serial := 0
var equipment_slots: Array[String] = []
var card_slots: Array[String] = []
var items: Dictionary = {}
var hero_presets: Dictionary = {}
var selected_preset_by_hero: Dictionary = {}
var hero_levels: Dictionary = {}
var quest_progress: Dictionary = {"quest_counters": {}, "claimed_quests": []}
var progression_serial := 0
var unlocked_heroes: Array[String] = ["juggernaut", "abaddon"]

func _init(p_active_hero_id: String) -> void:
	active_hero_id = p_active_hero_id
	_reset_slots()
	_ensure_hero(active_hero_id)

func add_item(instance: ItemInstance) -> void:
	items[instance.id] = instance

func item(instance_id: String) -> ItemInstance:
	return items.get(instance_id, null) as ItemInstance

func remove_item(instance_id: String) -> void:
	var instance := item(instance_id)
	if instance == null:
		return
	_clear_slot_reference(instance)
	items.erase(instance_id)

func preset_count() -> int:
	return PRESET_COUNT

func preset(hero_id: String, preset_index: int) -> LoadoutPreset:
	_ensure_hero(hero_id)
	return (hero_presets[hero_id] as Array)[preset_index] as LoadoutPreset

func selected_preset(hero_id: String) -> int:
	_ensure_hero(hero_id)
	return int(selected_preset_by_hero[hero_id])

func set_selected_preset(hero_id: String, preset_index: int) -> void:
	_ensure_hero(hero_id)
	selected_preset_by_hero[hero_id] = preset_index

func _ensure_hero(hero_id: String) -> void:
	if hero_presets.has(hero_id):
		return
	var presets: Array[LoadoutPreset] = []
	for ignored in PRESET_COUNT:
		presets.append(LoadoutPreset.new(_empty_slots(EQUIPMENT_SLOT_COUNT), _empty_slots(CARD_SLOT_COUNT)))
	hero_presets[hero_id] = presets
	selected_preset_by_hero[hero_id] = 0

func _reset_slots() -> void:
	equipment_slots = _empty_slots(EQUIPMENT_SLOT_COUNT)
	card_slots = _empty_slots(CARD_SLOT_COUNT)

func _empty_slots(size: int) -> Array[String]:
	var result: Array[String] = []
	for ignored in size:
		result.append("")
	return result

func _clear_slot_reference(instance: ItemInstance) -> void:
	if instance.location == ItemInstance.Location.EQUIPMENT and instance.slot >= 0 and instance.slot < equipment_slots.size():
		equipment_slots[instance.slot] = ""
	elif instance.location == ItemInstance.Location.CARD and instance.slot >= 0 and instance.slot < card_slots.size():
		card_slots[instance.slot] = ""
	instance.location = ItemInstance.Location.WAREHOUSE
	instance.slot = -1
	instance.grid_position = Vector2i.ZERO
	instance.rotated = false
