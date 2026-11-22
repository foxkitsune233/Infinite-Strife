extends SceneTree

const Stats = preload("res://scripts/battle/AndroidLoadoutStats.gd")
const Content = preload("res://scripts/content/AndroidContent.gd")

func _init() -> void:
	var state := PreparationState.new("juggernaut")
	var bare := Stats.calculate(state)
	assert(is_equal_approx(bare.hp, 1028.0))
	assert(is_equal_approx(bare.attack, 61.0))
	state.add_item(Content.create_instance("blade", "broadsword"))
	LoadoutRules.place(state, "blade", ItemInstance.Kind.EQUIPMENT, 0)
	var sword := Stats.calculate(state)
	assert(is_equal_approx(sword.attack, bare.attack + 15.0))
	state.add_item(Content.create_instance("branch", "iron_branch"))
	LoadoutRules.place(state, "branch", ItemInstance.Kind.EQUIPMENT, 6)
	assert(is_equal_approx(Stats.calculate(state).hp, bare.hp))
	LoadoutRules.place(state, "branch", ItemInstance.Kind.EQUIPMENT, 1)
	assert(is_equal_approx(Stats.calculate(state).hp, bare.hp + 22.0))
	var card := Content.create_instance("fast", "A1")
	card.star = 3
	state.add_item(card)
	LoadoutRules.place(state, card.id, card.kind, 0)
	assert(Stats.calculate(state).interval < sword.interval)
	state.hero_levels["juggernaut"] = 10
	assert(is_equal_approx(Stats.calculate(state).hp, 1050.0 * 1.20))
	var snapshot := SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(81)))
	assert(snapshot.accepted and snapshot.preparation.item("fast").star == 3)
	quit(0)
