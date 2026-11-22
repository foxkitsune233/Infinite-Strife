extends SceneTree

const Relief = preload("res://scripts/journey/ReliefRules.gd")
const Session = preload("res://scripts/AppSession.gd")
const Save = preload("res://scripts/save/SaveRepository.gd")
const PATH := "user://test_relief.json"

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	state.gold = 0
	state.quest_progress["chest_offers"] = {"0:0": {"name": "test", "hint": "test", "difficulty": 1, "quality": "蓝", "ticket": 220}}
	assert(Relief.claim(state, 1000).accepted)
	assert(state.items.size() == 3)
	assert(state.items.values().all(func(item: ItemInstance) -> bool: return item.bound and item.location == ItemInstance.Location.WAREHOUSE))
	assert(state.quest_progress.chest_offers["0:0"].ticket == 120)
	assert(Relief.entry_price(state, state.quest_progress.chest_offers["0:0"]) == 0)
	assert(Relief.entry_price(state, {"difficulty": 1, "quality": "白", "ticket": 180}) == 180)
	assert(Relief.entry_price(state, {"difficulty": 0, "quality": "白", "ticket": 160}) == 40)
	assert(Relief.claim(state, 1000).accepted)
	assert(not Relief.claim(state, 1000).accepted)
	assert(int(Relief.status(state, 14401000).charges) == 2)
	assert(int(Relief.status(state, 7201000).charges) == 1)
	assert(int(Relief.status(state, 0).charges) == 0)
	state.phase = PreparationState.Phase.JOURNEY
	assert(not Relief.claim(state, 7201000).accepted)
	state.phase = PreparationState.Phase.HOME
	state.gold = 120
	assert(not Relief.claim(state, 7201000).accepted)
	state.gold = 0
	var sword := ItemInstance.new("sellable", "broadsword", ItemInstance.Kind.EQUIPMENT)
	state.add_item(sword)
	state.add_item(ItemInstance.new("sellable2", "broadsword", ItemInstance.Kind.EQUIPMENT))
	assert(not Relief.claim(state, 7201000).accepted)
	state.remove_item("sellable")
	state.remove_item("sellable2")
	state.quest_progress["runs_started"] = 1
	assert(not Relief.claim(state, 7201000).accepted) # Old offers cannot unlock relief.
	state.quest_progress["runs_started"] = 0
	var card_definition: ItemDefinition = ContentCatalog.items().filter(func(definition: ItemDefinition) -> bool:
		return definition.source_data.get("kind") == "CARD" and definition.value * 9 * 40 / 100 >= 120)[0]
	var card := ItemInstance.new("sellable-card", card_definition.id, ItemInstance.Kind.CARD)
	card.star = 3
	state.add_item(card)
	assert(not Relief.claim(state, 7201000).accepted)
	state.remove_item(card.id)
	Save.erase(PATH)
	var session := Session.new()
	session.preparation_state = state
	session.save_path = "user://missing-relief-directory/save.json"
	var before := SaveCodec.encode(state, JourneyState.new(7001))
	assert(not session.claim_relief(7201000).accepted)
	assert(SaveCodec.encode(state, JourneyState.new(7001)) == before)
	session.save_path = PATH
	assert(session.claim_relief(7201000).accepted)
	var vouchers := int(state.quest_progress.relief.vouchers)
	var target: Dictionary = state.quest_progress.chest_offers["0:0"]
	assert(not session.enter_journey(1, target).accepted)
	session.save_path = "user://missing-relief-directory/save.json"
	assert(not session.enter_journey(0, target).accepted)
	assert(int(state.quest_progress.relief.vouchers) == vouchers and state.gold == 0)
	session.save_path = PATH
	assert(session.enter_journey(0, target).accepted)
	assert(state.gold == 0 and int(state.quest_progress.relief.vouchers) == vouchers - 1)
	var restored := Session.new()
	restored.save_path = PATH
	assert(restored.restore_progress().accepted)
	assert(int(restored.preparation_state.quest_progress.relief.vouchers) == vouchers - 1)
	assert(restored.preparation_state.items.size() == 9)
	Save.erase(PATH)
	session.free()
	restored.free()
	print("Relief conditions, recovery, subsidy and rollback passed")
	quit()
