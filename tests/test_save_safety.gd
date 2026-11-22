extends SceneTree

const Repository = preload("res://scripts/save/SaveRepository.gd")
const Codec = preload("res://scripts/save/SaveCodec.gd")
const PATH := "user://migration_save_safety_test.json"

func _init() -> void:
	Repository.erase(PATH)
	var state := PreparationState.new("juggernaut")
	var journey := JourneyState.new(71)
	state.gold = 100
	assert(Repository.write(PATH, state, journey).accepted)
	state.gold = 200
	assert(Repository.write(PATH, state, journey).accepted)
	assert(Repository.read(PATH).preparation.gold == 200)
	_write("broken JSON")
	var recovered := Repository.read(PATH)
	assert(recovered.accepted and recovered.recovered_backup)
	assert(recovered.preparation.gold == 100)
	var future := Codec.encode(state, journey)
	future.schema_version = Codec.SCHEMA_VERSION + 1
	var future_text := JSON.stringify(future)
	_write(future_text)
	assert(not Repository.read(PATH).accepted)
	assert(not Repository.write(PATH, state, journey).accepted)
	assert(FileAccess.get_file_as_string(PATH) == future_text)
	Repository.erase(PATH)
	quit(0)

func _write(value: String) -> void:
	var file := FileAccess.open(PATH, FileAccess.WRITE)
	file.store_string(value)
	file.close()
