extends RefCounted
# Explicit modest defaults for effects whose original Android design left numbers open.
const Shard := {
	"juggernaut": {"critical_lifesteal": 0.40},
	"skywrath": {"drain_base": 10.0, "drain_growth": 2.0, "drain_interval_ms": 1000, "max_channel_ms": 20000},
	"phantom_assassin": {"evasion": 0.30},
	"necrophos": {"heartstopper_heal": 0.50},
	"abaddon": {"frost_damage": 12.0, "freeze_chance": 0.15, "freeze_ms": 500},
	"viper": {"armor_loss": 3.0, "armor_ms": 3000},
	"anti_mage": {"magic_resist": 0.15},
	"faceless_void": {"period_ms": 6000, "cooldown_add_ms": 1000, "duration_ms": 3000, "damage_per_sec": 8.0},
	"axe": {"duration_ms": 3000, "vulnerability": 0.10, "damage_per_sec": 8.0},
	"legion_commander": {"period_ms": 8000},
}
static func get_value(hero: String, key: String) -> float:
	return float(Shard.get(hero, {}).get(key, 0.0))
