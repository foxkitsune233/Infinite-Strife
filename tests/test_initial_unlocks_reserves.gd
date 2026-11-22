extends SceneTree

const Content = preload("res://scripts/content/AndroidContent.gd")
var failures := 0
var checks := 0
func check(ok: bool, message: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(message)
func _init() -> void:
	call_deferred("_run")
func _run() -> void:
	var state := PreparationState.new("juggernaut")
	check(state.unlocked_heroes == ["juggernaut", "abaddon"], "new game starts with two heroes")
	check(not LoadoutRules.activate_hero(state, "skywrath").accepted, "locked hero cannot bypass selection UI")
	check(state.active_hero_id == "juggernaut", "rejected selection preserves active hero")
	var bare := BattleLoadoutBuilder.build(state)
	for pair in [["dagon", 6], ["monkey_king_bar", 7], ["aeon_disk", 8]]:
		var item := Content.create_instance("reserve_" + str(pair[0]), str(pair[0]))
		state.add_item(item)
		check(LoadoutRules.place(state, item.id, item.kind, int(pair[1])).accepted, "reserve placement stays available")
	var reserved := BattleLoadoutBuilder.build(state)
	check(reserved.active_items.is_empty(), "reserves grant no active items")
	check(reserved.equipment_slots.is_empty(), "reserves do not populate effective battle slots")
	check(not reserved.fighter.hero_state.has("monkey_king_bar"), "reserve MKB proc disabled")
	check(not reserved.fighter.hero_state.has("aeon_disk"), "reserve Aeon proc disabled")
	check(reserved.fighter.to_data() == bare.fighter.to_data(), "all reserve stats and effects are inert")
	var reserved_save := SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(9)))
	check(reserved_save.accepted and reserved_save.preparation.equipment_slots == state.equipment_slots, "save preserves nine slot placement")
	check(BattleLoadoutBuilder.build(reserved_save.preparation).fighter.to_data() == bare.fighter.to_data(), "reserves stay inert after loading")
	for definition in preload("res://scripts/content/AndroidEquipmentCatalog.gd").items():
		for slot in [6, 7, 8]:
			var isolated := PreparationState.new("juggernaut")
			var candidate := Content.create_instance("candidate", str(definition.id))
			if candidate.kind != ItemInstance.Kind.EQUIPMENT and candidate.definition_id not in ["faerie_fire", "cheese"]: continue
			isolated.add_item(candidate)
			check(LoadoutRules.place(isolated, candidate.id, ItemInstance.Kind.EQUIPMENT, slot).accepted, "reserve accepts " + candidate.definition_id)
			var built := BattleLoadoutBuilder.build(isolated)
			check(built.fighter.to_data() == bare.fighter.to_data() and built.active_items.is_empty(), "catalog reserve inert: %s/%d" % [candidate.definition_id, slot])
	LoadoutRules.place(state, "reserve_dagon", ItemInstance.Kind.EQUIPMENT, 0)
	check(BattleLoadoutBuilder.build(state).active_items.size() == 1, "moving to active slot enables Dagon")
	state.unlocked_heroes.append("skywrath")
	check(LoadoutRules.activate_hero(state, "skywrath").accepted, "earned unlock remains selectable")
	var restored := SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(9)))
	check(restored.accepted and "skywrath" in restored.preparation.unlocked_heroes, "save preserves earned unlock")
	var session := root.get_node("AppSession")
	session.preparation_state = state
	var screen := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	for index in 9:
		var button: Button = screen.equipment_slots.get_child(index)
		check(button.text.contains("备用") if index >= 6 else button.tooltip_text.contains("生效"), "slot role visible: " + str(index))
	check(screen.equipment_slots.get_child(0).get_theme_stylebox("normal") != screen.equipment_slots.get_child(6).get_theme_stylebox("normal"), "active and reserve frames differ")
	screen.queue_free()
	await process_frame
	print("Initial unlocks and reserves: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
