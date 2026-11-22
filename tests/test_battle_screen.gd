extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var battle := preload("res://scenes/Battle.tscn").instantiate()
	root.add_child(battle)
	await process_frame
	assert(battle.get_node_or_null("BattleStage") != null, "battle must use one shared duel stage")
	assert(battle.get_node_or_null("%ManualSkillButton") != null, "battle must expose a manual skill button")
	assert(battle.has_method("cast_player_skill"), "battle must expose manual player skill dispatch")
	assert(battle.has_method("stage_anchor"), "battle must expose effect anchors")
	assert(battle.stage_anchor("left").x < battle.stage_anchor("right").x, "left and right fighters need stable stage anchors")
	var arena := battle.get_node("BattleStage") as Control
	var vitals := battle.get_node("TopHud") as Control
	var equipment := battle.get_node("%ManualItems") as Control
	assert(arena.get_global_rect().end.y <= vitals.global_position.y, "Android-style vitals sit below the arena")
	assert(vitals.get_global_rect().end.y < equipment.global_position.y, "equipment tray sits below vitals")
	assert(equipment.get_child_count() == 6, "default drill shows all six equipment slots")
	assert((equipment.get_child(0) as Button).tooltip_text == "达贡之神力", "default drill has a usable demo item")
	assert((equipment.get_child(5) as Button).disabled, "empty equipment slots stay visible")
	assert(battle.get_node_or_null("%EventLogLabel") == null, "logs do not occupy the main HUD")
	assert(battle.get_node("%LeftStatusLabel").text.contains("BUFF"), "empty Buff bar is visible")
	var state := PreparationState.new("juggernaut")
	var dagon := ItemInstance.new("dagon_fixture", "dagon", ItemInstance.Kind.EQUIPMENT)
	state.add_item(dagon)
	LoadoutRules.place(state, dagon.id, ItemInstance.Kind.EQUIPMENT, 0)
	var build := BattleLoadoutBuilder.build(state)
	assert(build.active_items.size() == 1, "equipped Dagon supplies one actionable item")
	var target := FighterState.new("enemy", 500, 0, 1000, 0, 0, 0, 0.25)
	target.add_status("slow", 1500)
	target.hero_timers["armor_buff"] = 2000
	battle.start_match_fighters(7, build, {"fighter": target, "display_name": "测试目标", "skill": {}})
	assert(battle.get_node("%RightStatusLabel").tooltip_text.contains("减速 1.5s"))
	assert(battle.get_node("%RightStatusLabel").tooltip_text.contains("护甲 2.0s"))
	var button := battle.get_node("%ManualItems").get_child(0) as Button
	assert(button.icon != null and button.tooltip_text == "达贡之神力")
	assert(not AndroidArtCatalog.equipment_path("dagon").is_empty())
	button.pressed.emit()
	assert(is_equal_approx(target.hp, 260.0), "320 magical damage respects 25% resistance")
	assert(button.disabled and button.text == "10.0", "cooldown is shown immediately on the same button")
	assert(not battle.use_player_active(build.active_items[0]).accepted, "cooldown rejects repeated use")
	var enemy_prep := PreparationState.new("axe")
	var halberd := ItemInstance.new("enemy_halberd", "heavens_halberd", ItemInstance.Kind.EQUIPMENT)
	enemy_prep.add_item(halberd)
	LoadoutRules.place(enemy_prep, halberd.id, ItemInstance.Kind.EQUIPMENT, 0)
	var enemy_build := BattleLoadoutBuilder.build(enemy_prep)
	var ranged_build := BattleLoadoutBuilder.build(PreparationState.new("skywrath"))
	battle.start_match_fighters(7, ranged_build, enemy_build)
	battle._process(0.11)
	assert(ranged_build.fighter.has_status("disarm"), "Enemy equipment auto-casts on a live battle tick")
	assert(int(ranged_build.fighter.status_snapshot().get("disarm", 0)) == 2900, "Ranged disarm lasts 3 seconds minus one simulation tick")
	assert(enemy_build.fighter.cooldowns.get("active:heavens_halberd") == 11900, "Enemy auto-use starts the Android cooldown")
	battle.queue_free()
	var menu := preload("res://scenes/MainMenu.tscn").instantiate()
	root.add_child(menu)
	set_current_scene(menu)
	menu.start_default_match()
	await process_frame
	await process_frame
	var scene := current_scene
	if scene == null or scene.scene_file_path != "res://scenes/Battle.tscn":
		push_error("menu must open the Battle scene")
		quit(1)
		return
	quit(0)
