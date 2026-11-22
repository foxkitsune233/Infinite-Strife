extends SceneTree

const Rules = preload("res://scripts/journey/JourneyRules.gd")
const Codec = preload("res://scripts/save/SaveCodec.gd")

func _init() -> void:
	call_deferred("_run")

func _check(value: bool, message: String) -> void:
	if not value:
		push_error(message)
		quit(1)
		assert(value, message)

func _pick(seed: int, forced_recent: Array[String]) -> Dictionary:
	var state := PreparationState.new("juggernaut")
	state.quest_progress["clears"] = 5
	state.backpack_size = Vector2i(4, 3)
	var journey := JourneyState.new(seed)
	journey.current_node = 1
	journey.recent_event_kinds.assign(forced_recent)
	Rules.resolve_current(state, journey)
	return {"id": journey.frozen_event_id, "rng": journey.event_rng, "recent": journey.recent_event_kinds.duplicate(), "journey": journey}

func _run() -> void:
	# 1. 确定性：同种子 + 同去重历史 -> 同事件、同 RNG 游标。
	var a := _pick(99001, [])
	var b := _pick(99001, [])
	_check(a.id == b.id, "same seed and history pick the same event")
	_check(a.rng == b.rng, "RNG cursor advances deterministically")
	_check(a.rng != 99001, "RNG sequence consumed exactly once")
	# 2. 最近两次不重复：强制历史为最近两个候选事件时不得再次出现。
	var excluded: Array[String] = [a.id]
	var c := _pick(99001, excluded)
	_check(c.id != a.id, "most recent event excluded from candidates")
	var excluded2: Array[String] = [a.id, c.id]
	var d := _pick(99001, excluded2)
	_check(d.id != a.id and d.id != c.id, "last two events both excluded when candidates allow")
	# 3. 事件历史只保留最近两次。
	_check(d.recent.size() <= 2, "recent event history capped at two")
	_check(d.recent.has(d.id), "picked event recorded in history")
	# 4. RNG 游标与历史跨存档保持。
	var saved := Codec.decode(Codec.encode(PreparationState.new("juggernaut"), d.journey))
	_check(saved.accepted, "save roundtrip accepted")
	_check(saved.journey.event_rng == d.rng, "RNG cursor survives save")
	_check(saved.journey.recent_event_kinds == d.recent, "recent event history survives save")
	# 5. 每个冻结选项都带 grant_ids。
	for option in d.journey.frozen_event_options:
		_check(option.has("grant_ids"), "frozen option carries grant_ids")
	print("Event de-dup, deterministic RNG, history persistence and frozen grants passed")
	quit()
