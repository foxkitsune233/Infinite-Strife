extends SceneTree

const AudioBus = preload("res://scripts/ui/BattleAudioBus.gd")

func _init() -> void:
	assert(AudioBus.cue_for({"kind": "physical_attack"}) == "hit")
	assert(AudioBus.cue_for({"kind": "physical_attack", "critical": true}) == "critical")
	assert(AudioBus.cue_for({"kind": "skill_cast"}) == "skill")
	assert(AudioBus.cue_for({"kind": "active_used"}) == "active")
	assert(AudioBus.cue_for({"kind": "unknown"}).is_empty())
	quit(0)
