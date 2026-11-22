extends SceneTree

const Stories = preload("res://scripts/story/StoryCatalog.gd")
const Rules = preload("res://scripts/story/StoryRules.gd")

func _init() -> void:
	assert(Stories.chapter("J01").lines.size() == 2)
	for node in range(1, 25):
		assert(not Stories.chapter("J%02d" % node).is_empty())
	for hero in AndroidHeroCatalog.ids():
		assert(Stories.ending(hero).pages.size() == 15)
	var state := PreparationState.new("juggernaut")
	Rules.enqueue(state, "J01")
	Rules.enqueue(state, "J01")
	assert(Rules.pending(state) == "J01")
	assert(Rules.page(state, "J01") == 0)
	assert(Rules.advance(state, "J01").accepted)
	assert(Rules.page(state, "J01") == 1)
	var restored: PreparationState = SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(7001))).preparation
	assert(Rules.page(restored, "J01") == 1 and Rules.pending(restored) == "J01")
	assert(Rules.advance(restored, "J01").finished)
	assert(Rules.pending(restored).is_empty())
	Rules.enqueue(restored, "J01")
	assert(Rules.pending(restored).is_empty())
	assert(not Rules.advance(restored, "missing").accepted)
	assert(not Rules.can_replay(state, "J24"))
	assert(Rules.can_replay(state, "P00"))
	print("Story catalog and persistent playback passed")
	quit()
