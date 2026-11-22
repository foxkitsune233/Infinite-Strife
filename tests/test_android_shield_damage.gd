extends SceneTree

func _init() -> void:
	var fighter := FighterState.new("target", 200, 10, 1000, 0, 0, 40)
	fighter.magic_resistance = 0.0
	fighter.magic_shield = 30.0
	fighter.hero_state["aphotic_remaining"] = 40.0
	var magic := fighter.receive_android_damage(50.0, "magic")
	var magic_ok: bool = magic.shield_damage == 50.0 and magic.life_damage == 0.0 \
		and fighter.magic_shield == 0.0 and fighter.shield == 20.0 \
		and fighter.hero_state.aphotic_remaining == 20.0
	var pure_target := FighterState.new("pure_target", 200, 10, 1000, 0, 0, 40)
	var pure := pure_target.receive_android_damage(35.0, "pure")
	var pure_ok: bool = pure.shield_damage == 0.0 and pure.life_damage == 35.0 and pure_target.shield == 40.0
	if not magic_ok: push_error("Magic damage must consume the magic shield before Aphotic Shield")
	if not pure_ok: push_error("Pure damage must bypass shields and reduce HP")
	var tracked := FighterState.new("tracked", 200, 10, 1000, 0)
	tracked.grant_shield("hero:aphotic", "UNIVERSAL", 40.0)
	tracked.grant_shield("card:timed", "UNIVERSAL", 25.0, 300)
	tracked.receive_android_damage(15.0, "physical")
	var resumed := FighterState.from_data(tracked.to_data())
	resumed.grant_shield("hero:aphotic", "UNIVERSAL", 40.0)
	var refreshed_ok := resumed.shield == 50.0
	resumed.advance_time(300)
	var expired_ok := resumed.shield == 40.0
	if not refreshed_ok or not expired_ok:
		push_error("Timed shield absorbs first and expires after resume; Aphotic refresh preserves other sources")
	quit(0 if magic_ok and pure_ok and refreshed_ok and expired_ok else 1)
