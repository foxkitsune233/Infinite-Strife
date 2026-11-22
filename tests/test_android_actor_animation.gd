extends SceneTree

const Actor = preload("res://scripts/ui/BattleActorView.gd")
const Art = preload("res://scripts/ui/AndroidArtCatalog.gd")
const HERO_FILES := {
	"juggernaut": "juggernaut", "phantom_assassin": "phantom-assassin",
	"skywrath": "skywrath", "abaddon": "abaddon", "necrophos": "necrophos",
	"viper": "viper", "anti_mage": "antimage", "axe": "axe",
	"faceless_void": "faceless-void", "legion_commander": "legion-commander",
}
var failures := 0

func check(ok: bool, message: String) -> void:
	if not ok:
		failures += 1
		push_error(message)

func _init() -> void:
	call_deferred("run")

func run() -> void:
	var actor = Actor.new()
	root.add_child(actor)
	if not actor.has_method("frame_spec"):
		check(false, "Actor must expose validated Android frame selection")
		actor.free()
		quit(1)
		return
	for hero in HERO_FILES:
		var idle: Dictionary = Actor.frame_spec(hero, "idle", 0)
		check(idle.get("path", "").ends_with(HERO_FILES[hero] + "-attack-atlas-v3.png"), "Correct actual atlas: " + hero)
		check(idle.get("region") == Rect2i(0, 0, 512, 512), "Ready cell: " + hero)
		check(idle.get("root") == Vector2(256, 464), "Calibrated foot root: " + hero)
		var sheet := (load(idle.path) as Texture2D).get_image()
		check(sheet != null and sheet.get_size() == Vector2i(1024, 1024), "Actual source atlas dimensions: " + hero)
		var colors: Array[int] = []
		for frame in 4:
			var spec: Dictionary = Actor.frame_spec(hero, "attack", frame)
			check(spec.get("region") == Rect2i((frame % 2) * 512, (frame / 2) * 512, 512, 512), "2x2 atlas cell: %s/%d" % [hero, frame])
			var texture: Texture2D = Actor.texture_for_frame(hero, "attack", frame)
			check(texture != null and texture.get_size() == Vector2(256, 256), "Bounded frame dimensions: " + hero)
			if texture != null:
				var image := texture.get_image()
				check(image.get_used_rect().has_area(), "Frame has visible pixels: " + hero)
				colors.append(hash(image.get_data()))
		check(colors.size() == 4 and colors[0] != colors[1] and colors[1] != colors[2], "Actual distinct attack pixels: " + hero)
		actor.actor_id = hero
		actor.size = Vector2(180, 110)
		actor._process(0.0)
		var foot: Vector2 = actor.pivot_offset
		actor.play_event({"kind": "physical_attack", "source_id": hero, "target_id": "other"})
		actor._process(0.12)
		check(actor.texture == Actor.texture_for_frame(hero, "attack", 1), "Attack advances to windup: " + hero)
		check(actor.pivot_offset.is_equal_approx(foot), "Attack foot stays fixed: " + hero)
		actor._process(1.0)
		check(actor.texture == Actor.texture_for_frame(hero, "idle", 0), "Attack returns to ready: " + hero)
		actor.play_event({"kind": "skill_cast", "source_id": hero, "target_id": hero})
		check(actor._motion == "cast", "Same source/target retains cast precedence: " + hero)
		actor._process(0.1)
		check(actor.texture != null and actor.pivot_offset.is_equal_approx(foot), "Cast preserves foot: " + hero)
		actor._process(1.0)
	# A hit overlays an existing attack without resetting its frame clock.
	actor.actor_id = "juggernaut"
	actor.play_event({"kind": "physical_attack", "source_id": "juggernaut"})
	actor._process(0.12)
	var age_before_hit: float = actor._age
	actor.play_event({"kind": "damage", "source_id": "other", "target_id": "juggernaut", "amount": 10})
	check(actor._motion == "attack" and actor._age == age_before_hit, "Hit does not cancel attack")
	actor.play_event({"kind": "physical_attack", "source_id": "juggernaut"})
	check(actor._age == age_before_hit, "Rapid attack does not restart a visible swing")
	actor._process(1.0)
	actor.play_event({"kind": "physical_attack", "source_id": "juggernaut"})
	actor._process(0.12)
	check(actor.texture == Actor.texture_for_frame("juggernaut", "attack", 1, true), "Second swing uses actual offhand windup")
	actor.flip_h = true
	actor._process(0.1)
	check(actor.flip_h, "Playback preserves caller's facing")
	actor.actor_id = "axe"
	check(actor._motion == "idle" and actor.scale == Vector2.ONE and actor.modulate == Color.WHITE, "Rebinding resets animation state")
	actor.actor_id = "necrophos"
	actor.play_event({"kind": "hero_damage", "source_id": "necrophos", "target_id": "other", "amount": 1})
	check(actor._motion == "idle", "Heartstopper damage does not start an attack")
	actor.play_event({"kind": "physical_attack", "source_id": "necrophos"})
	actor._process(0.2)
	var nec_age: float = actor._age
	actor.play_event({"kind": "hero_damage", "source_id": "necrophos", "amount": 1})
	check(actor._age == nec_age, "Heartstopper does not reset a visible attack")
	check(is_equal_approx(Actor.attack_duration_seconds(100), 0.18), "High attack speed keeps Android minimum duration")
	check(is_equal_approx(Actor.attack_duration_seconds(1000), 0.42), "Normal attacks keep Android maximum duration")
	actor.actor_id = "unknown"
	check(actor.texture == null, "Unknown hero does not inherit previous hero's art")
	var caller_texture: Texture2D = Art.hero_texture("axe")
	actor.texture = caller_texture
	actor._process(0.1)
	check(actor.texture == caller_texture, "Unknown actor keeps caller's fallback")
	for hero in ["juggernaut", "phantom_assassin"]:
		for frame in [1, 2]:
			var alternate: Dictionary = Actor.frame_spec(hero, "attack", frame, true)
			check(alternate.get("path", "").ends_with("-offhand-atlas-v4.png"), "Offhand sheet: " + hero)
			check(alternate.get("region") == Rect2i((frame - 1) * 512, 0, 512, 512), "Offhand cell")
			check((load(alternate.path) as Texture2D).get_size() == Vector2(1024, 512), "Actual offhand sheet dimensions")
	for hero in ["abaddon", "skywrath"]:
		check(Actor.frame_spec(hero, "cast", 0).get("path") == Art.hero_path(hero, "cast"), "Dedicated cast pose: " + hero)
	check(Actor.frame_spec("viper", "cast", 0).get("path") == Art.hero_path("viper", "charge"), "Viper charge")
	check(Actor.frame_spec("viper", "cast", 2).get("region") == Rect2i(0, 512, 512, 512), "Viper release")
	check(Actor.frame_spec("anti_mage", "attack", 2) == Actor.frame_spec("antimage", "attack", 2), "Anti-mage alias")
	check(Actor.frame_spec("unknown", "attack", 0).is_empty(), "Unknown hero has no guessed sheet")
	check(Actor.frame_spec("axe", "attack", -1).is_empty(), "Negative cell rejected")
	check(Actor.frame_spec("axe", "attack", 4).is_empty(), "Out-of-range cell rejected")
	var valid: Dictionary = Actor._entries()["axe"].duplicate(true)
	for broken in [{"frameWidth": 0}, {"rootY": 900}, {"file": "../outside.png"}, {"bodyHeight": -1}, {"contentWidth": 800}]:
		var entry := valid.duplicate(true)
		entry.merge(broken, true)
		check(not Actor.valid_entry(entry), "Malformed atlas metadata rejected: " + str(broken))
	var original: Dictionary = Actor._entries()["axe"]
	Actor._entries()["axe"] = {"frameWidth": 0}
	actor.actor_id = "axe"
	actor.play_event({"kind": "physical_attack", "source_id": "axe"})
	check(actor.texture == Art.hero_texture("axe"), "Invalid atlas falls back to genuine static hero")
	Actor._entries()["axe"] = original
	check(Actor._textures.size() <= Actor.MAX_CACHE_ENTRIES, "Texture cache bound")
	# Evict real entries and prove that recently used frames survive under pressure.
	var keep: Texture2D = Actor.texture_for_frame("axe", "attack", 0)
	for i in Actor.MAX_CACHE_ENTRIES + 2:
		Actor._remember("test_%d" % i, keep)
		Actor.texture_for_frame("axe", "attack", 0)
	check(Actor._textures.size() == Actor.MAX_CACHE_ENTRIES, "Cache evicts at capacity")
	check(Actor.texture_for_frame("axe", "attack", 0) == keep, "LRU refresh retains hot frame")
	actor.free()
	print("Android actor animation: heroes=10 failures=%d" % failures)
	quit(0 if failures == 0 else 1)
