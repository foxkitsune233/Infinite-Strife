extends SceneTree

const Rules = preload("res://scripts/journey/JourneyRules.gd")
const Catalog = preload("res://scripts/content/ContentCatalog.gd")
const Codec = preload("res://scripts/save/SaveCodec.gd")

func event(id: String) -> JourneyState:
	var journey := JourneyState.new(7001)
	journey.current_node = 1
	journey.frozen_event_id = id
	journey.frozen_event_options.assign(Catalog.event(id).options.duplicate(true))
	return journey

func check(value: bool, message: String) -> void:
	if not value:
		push_error(message)
		quit(1)
		assert(value, message)

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	state.phase = PreparationState.Phase.JOURNEY
	var medicine := ItemInstance.new("equipped_medicine", "salve", ItemInstance.Kind.CONSUMABLE)
	medicine.location = ItemInstance.Location.EQUIPMENT
	medicine.slot = 0
	state.add_item(medicine)
	state.equipment_slots[0] = medicine.id
	var journey := event("field_clinic")
	journey.player_hp_ratio = 0.2
	check(Rules.choose_event(state, journey, 1, medicine.id).accepted, "Android held equipment can be explicitly delivered")
	check(state.item(medicine.id) == null and state.equipment_slots[0].is_empty(), "delivery clears equipped reference")
	check(is_equal_approx(journey.player_hp_ratio, 0.6), "clinic heals forty percent")
	medicine = ItemInstance.new("safe_medicine", "salve", ItemInstance.Kind.CONSUMABLE)
	medicine.location = ItemInstance.Location.SAFE
	state.add_item(medicine)
	journey = event("field_clinic")
	var before := Codec.encode(state, journey)
	check(not Rules.choose_event(state, journey, 1, medicine.id).accepted, "safe delivery requires explicit authorization")
	check(Codec.encode(state, journey) == before, "unauthorized delivery atomic")
	check(Rules.new().call("choose_event", state, journey, 1, medicine.id, true).accepted, "authorized safe medicine delivery")
	check(state.item(medicine.id) == null, "authorized exact instance consumed")
	var wrong := ItemInstance.new("essence_prefix_salve", "broadsword", ItemInstance.Kind.EQUIPMENT)
	wrong.location = ItemInstance.Location.BACKPACK
	state.add_item(wrong)
	journey = event("tech_terminal")
	check(not Rules.choose_event(state, journey, 0, wrong.id).accepted, "terminal matches definition, never instance prefix")
	wrong.location = ItemInstance.Location.WAREHOUSE
	journey = event("blocker")
	check(not Rules.new().call("choose_event", state, journey, 1, wrong.id, true).accepted, "authorization never grants warehouse access")
	state.quest_progress["clears"] = 0
	journey = event("safe_wreckage")
	check(not Rules.choose_event(state, journey, 0).accepted, "core locked before clear")
	state.quest_progress["clears"] = 5
	state.backpack_size.y = 2
	var pools: Dictionary = Rules.new().call("_event_pools", state, journey)
	check(pools.core == ["safe_lock"], "frame also requires backpack height three")
	state.backpack_size.y = 3
	pools = Rules.new().call("_event_pools", state, journey)
	check(pools.core == ["safe_frame", "safe_lock"], "eligible core pool sorted and progression filtered")
	journey = event("banner_ruins")
	journey.run_gold = 29
	before = Codec.encode(state, journey)
	check(not Rules.choose_event(state, journey, 0).accepted and Codec.encode(state, journey) == before, "priced event uses run wallet only and rejects atomically")
	_test_pools()
	print("Android event delivery, authorization, pool filtering, core progression and frozen rewards passed")
	quit()

func _test_pools() -> void:
	var state := PreparationState.new("juggernaut")
	var journey := event("rusted_workshop")
	journey.difficulty = 0
	var pools: Dictionary = Rules.new().call("_event_pools", state, journey)
	check(pools.medicine == ["cheese", "faerie_fire", "salve", "tango"], "medicine pool uses Android sorted IDs")
	check(not "broadsword" in pools.component, "easy excludes tier-one main equipment")
	journey.difficulty = 1
	pools = Rules.new().call("_event_pools", state, journey)
	check("broadsword" in pools.component, "normal permits tier-one main equipment")
	state.quest_progress["clears"] = 5
	state.backpack_size.y = 3
	journey = JourneyState.new(7001)
	journey.current_node = 1
	Rules.resolve_current(state, journey)
	check(not journey.frozen_event_id.is_empty(), "event generated")
	for option in journey.frozen_event_options:
		check(option.has("grant_ids"), "reward IDs frozen with each option")
	var saved_options := journey.frozen_event_options.duplicate(true)
	Rules.resolve_current(state, journey)
	check(journey.frozen_event_options == saved_options, "repeat display never rerolls rewards")
	var restored: Dictionary = Codec.decode(Codec.encode(state, journey))
	check(restored.accepted and restored.journey.frozen_event_options == saved_options, "frozen choices survive restore")
