extends SceneTree

const Chests = preload("res://scripts/journey/ChestRules.gd")

func _init() -> void:
	_test_generation()
	var state := PreparationState.new("juggernaut")
	var target := {"quality": "白", "ticket": 120, "rewards": ["broadsword", "chainmail"]}
	Chests.initialize(target)
	assert(target.items.size() == 2)
	assert(target.items[0].x + 2 <= target.columns)
	state.gold = 9
	var before := target.duplicate(true)
	assert(not Chests.appraise(state, target).accepted and state.gold == 9 and target == before)
	state.gold = 500
	state.quest_progress["chest_offers"] = {"test": target}
	assert(Chests.appraise(state, target).accepted)
	assert(state.gold == 490 and target.ticket > 120)
	assert(Chests.appraisal_fee(target) == 20)
	var saved := SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(7001)))
	var restored: Dictionary = saved.preparation.quest_progress.chest_offers.test
	assert(restored == target)
	var same_save := SaveCodec.decode(SaveCodec.encode(saved.preparation, saved.journey))
	var second: Dictionary = same_save.preparation.quest_progress.chest_offers.test
	assert(Chests.appraise(saved.preparation, restored).accepted)
	assert(Chests.appraise(same_save.preparation, second).accepted)
	assert(restored == second)
	while target.items.any(func(entry: Dictionary) -> bool: return int(entry.reveal) < 2):
		assert(Chests.appraise(state, target).accepted)
	var gold := state.gold
	assert(not Chests.appraise(state, target).accepted and state.gold == gold)
	quit()

func _test_generation() -> void:
	for quality in ["白", "蓝", "紫", "金"]:
		var a := PreparationState.new("juggernaut")
		var b := PreparationState.new("juggernaut")
		a.quest_progress["clears"] = 6
		b.quest_progress["clears"] = 6
		var first := {"quality": quality, "difficulty": 5, "ticket": 120}
		var second := first.duplicate(true)
		assert(Chests.generate(a, first, "arsenal"))
		assert(Chests.generate(b, second, "arsenal"))
		assert(first == second and not first.items.is_empty())
		assert(first.items.size() == first.rewards.size())
		var occupied := {}
		for entry in first.items:
			var item: Dictionary = Chests.Items.item(str(entry.id))
			assert(int(entry.x) + int(item.width) <= int(first.columns))
			assert(int(entry.y) + int(item.height) <= int(first.rows))
			for y in int(item.height):
				for x in int(item.width):
					var cell := Vector2i(int(entry.x) + x, int(entry.y) + y)
					assert(not occupied.has(cell))
					occupied[cell] = true
		a.quest_progress["chest_offers"] = {"generated": first}
		var saved := SaveCodec.decode(SaveCodec.encode(a, JourneyState.new(7001)))
		assert(saved.preparation.quest_progress.chest_offers.generated == first)
