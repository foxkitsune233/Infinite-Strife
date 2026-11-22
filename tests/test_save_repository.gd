extends SceneTree

const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const JourneyState = preload("res://scripts/journey/JourneyState.gd")
const SaveRepository = preload("res://scripts/save/SaveRepository.gd")

var _failed := false

func _init() -> void:
	var path := "user://test_infinite_strife_save.json"
	SaveRepository.erase(path)
	var preparation := PreparationState.new("phantom_assassin")
	preparation.gold = 456
	var journey := JourneyState.new(9201)
	_assert_equal(SaveRepository.write(path, preparation, journey).accepted, true, "save file writes")
	var loaded := SaveRepository.read(path)
	_assert_equal(loaded.accepted, true, "save file reads")
	_assert_equal((loaded.preparation as PreparationState).active_hero_id, "phantom_assassin", "hero resumes")
	_assert_equal((loaded.journey as JourneyState).seed, 9201, "journey seed resumes")
	SaveRepository.erase(path)
	_assert_equal(SaveRepository.read(path).accepted, false, "missing save is reported")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
