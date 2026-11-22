class_name BattleLog
extends RefCounted

var events: Array[Dictionary] = []

func record_attack(tick_ms: int, source: FighterState, target: FighterState, amount: int, critical: bool = false, healed: int = 0) -> Dictionary:
	var event := {
		"tick_ms": tick_ms,
		"source_id": source.id,
		"target_id": target.id,
		"kind": "physical_attack",
		"amount": amount,
		"school_id": "physical",
		"critical": critical,
		"healed": healed,
		"effect_id": "effect_critical_strike" if critical else "effect_physical_strike",
		"remaining_hp": target.hp,
	}
	events.append(event)
	return event

func record_event(event: Dictionary) -> Dictionary:
	events.append(event)
	return event
