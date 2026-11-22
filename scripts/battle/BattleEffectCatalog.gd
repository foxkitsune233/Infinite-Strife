class_name BattleEffectCatalog
extends RefCounted

static func effect_for(event: Dictionary) -> String:
	var authored := str(event.get("effect_id", ""))
	if not authored.is_empty():
		return authored
	match str(event.get("kind", "")):
		"physical_attack":
			return "effect_critical_strike" if event.get("critical", false) else "effect_physical_strike"
		"dot_tick":
			return "effect_dot_tick"
		"active_used":
			return "effect_" + str(event.get("school_id", "active"))
		_:
			return "effect_none"
