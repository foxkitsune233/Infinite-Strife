extends SceneTree
const Content = preload("res://scripts/content/AndroidContent.gd")
func _init() -> void:
	call_deferred("_run")
func _run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "user://test_quick_equip.json"
	SaveRepository.erase(session.save_path)
	var state := PreparationState.new("juggernaut")
	state.phase = PreparationState.Phase.JOURNEY
	state.backpack_size = Vector2i(4, 2)
	session.preparation_state = state
	var journey := JourneyState.new(7001)
	journey.resolved = true
	journey.drop_serial = 1
	session.journey_state = journey
	for y in 2:
		for x in 4:
			var filler := Content.create_instance("fill_%d_%d" % [x, y], "iron_branch")
			filler.location = ItemInstance.Location.BACKPACK
			filler.grid_position = Vector2i(x, y)
			state.add_item(filler)
	var sword := Content.create_instance("sword", "broadsword")
	sword.identified = true
	journey.loot.append(sword)
	assert(session.has_method("equip_journey_item"), "pickup must expose immediate equipment")
	assert(session.equip_journey_item(sword.id, 0).accepted, "empty gear slot works even when bag is full")
	assert(state.equipment_slots[0] == sword.id and journey.loot.is_empty())
	var second := Content.create_instance("second", "dagon")
	second.identified = true
	journey.loot.append(second)
	var before := SaveCodec.encode(state, journey)
	assert(not session.equip_journey_item(second.id, 0).accepted, "full bag cannot silently discard replaced gear")
	assert(SaveCodec.encode(state, journey) == before)
	session.save_path = "user://missing-quick-equip-directory/save.json"
	assert(not session.equip_journey_item(second.id, 1).accepted)
	assert(SaveCodec.encode(state, journey) == before, "disk failure restores ground and gear")
	session.save_path = "user://test_quick_equip.json"
	state.phase = PreparationState.Phase.BATTLE
	assert(not session.equip_journey_item(second.id, 1).accepted)
	state.phase = PreparationState.Phase.JOURNEY
	var screen := preload("res://scenes/Expedition.tscn").instantiate()
	root.add_child(screen)
	screen.selected_loot = second.id
	screen._refresh()
	await process_frame
	assert(screen.packing_panel.find_child("JourneyEquipment", true, false) != null, "loot screen exposes equipment slots")
	var route_before := SaveCodec.encode(state, journey)
	screen.show_route_overview()
	assert(not screen.loot_items.visible and not screen.packing_panel.get_parent().visible)
	assert(SaveCodec.encode(state, journey) == route_before, "return to route never advances or discards loot")
	screen.show_route_overview(false)
	assert(screen.loot_items.visible and screen.packing_panel.get_parent().visible)
	screen.queue_free()
	await process_frame
	var prep := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(prep)
	await process_frame
	assert(prep.warehouse_items.get_children().any(func(child: Node) -> bool: return child is Button and child.get("instance_id") == "fill_0_0"), "journey loadout offers carried gear")
	assert(prep.get_node("PageNavigation").get_child(0).text.contains("路线"))
	prep.queue_free()
	await process_frame
	SaveRepository.erase(session.save_path)
	print("Quick equip: ground pickup, full bag, rollback, battle lock, carried inventory and route entry passed")
	quit()
