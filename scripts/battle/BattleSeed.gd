class_name BattleSeed
extends RefCounted

var _random := RandomNumberGenerator.new()

func _init(seed: int) -> void:
	_random.seed = seed

func next_bool() -> bool:
	return (_random.randi() & 1) == 0

func next_percent() -> int:
	return _random.randi_range(1, 100)
