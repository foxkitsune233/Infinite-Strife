extends SceneTree

const Progression = preload("res://scripts/progression/ProgressionRules.gd")
const Quests = preload("res://scripts/progression/QuestRules.gd")
const Catalog = preload("res://scripts/progression/AndroidQuestCatalog.gd")
const Content = preload("res://scripts/content/AndroidContent.gd")

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	state.gold = 600
	state.add_item(Content.create_instance("sword", "broadsword"))
	LoadoutRules.place(state, "sword", ItemInstance.Kind.EQUIPMENT, 0)
	assert(Progression.claim(state, "journey.1").accepted)
	assert(state.gold == 700)
	assert(not Progression.claim(state, "journey.1").accepted)
	state.add_item(Content.create_instance("skill", "A1"))
	LoadoutRules.place(state, "skill", ItemInstance.Kind.CARD, 0)
	assert(Progression.claim(state, "journey.2").accepted)
	var materials := 0
	for item in state.items.values():
		if item.definition_id == "essence":
			materials += 1
			assert(item.kind == ItemInstance.Kind.MATERIAL)
	assert(materials == 1)
	var before := SaveCodec.encode(state, JourneyState.new(9))
	state.gold = 2147483647
	var safe := Content.create_instance("safe", "iron_branch")
	state.add_item(safe)
	assert(PackingRules.place(state, "safe", ItemInstance.Location.SAFE, Vector2i.ZERO, false).accepted)
	var before_progress := state.quest_progress.duplicate(true)
	assert(not Progression.claim(state, "journey.3").accepted)
	assert(state.gold == 2147483647 and state.quest_progress == before_progress)
	state.gold = 1000
	assert(Progression.claim(state, "journey.3").accepted)
	Progression.record(state, {"type": "Entered"})
	assert(Progression.claim(state, "journey.4").accepted)
	var salves := 0
	for item in state.items.values():
		if item.definition_id == "salve":
			salves += 1
			assert(item.kind == ItemInstance.Kind.CONSUMABLE)
	assert(salves == 2)
	# Every shipped reward reference must resolve before a player can reach its node.
	for quest in Catalog.all():
		for reward in quest.rewards:
			if reward.type in ["ITEM", "MATERIAL", "GUARD_CORE"]:
				assert(not Content.item(str(reward.material_id)).is_empty())
	var restored := SaveCodec.decode(before)
	assert(restored.accepted and Quests.completed_nodes(restored.preparation.quest_progress) == 2)
	quit(0)
