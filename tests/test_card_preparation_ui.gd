extends SceneTree
const Rules = preload("res://scripts/prepare/CardPreparationRules.gd")
var checks := 0
func check(ok: bool, message: String) -> void:
	checks += 1
	assert(ok, message)
func _init() -> void:
	call_deferred("run")
func run() -> void:
	var session := root.get_node("AppSession")
	var state := PreparationState.new("juggernaut")
	state.quest_progress.enabled_card_slots = 4
	var count := 0
	Rules.Catalog._load()
	for card in Rules.Catalog._cards.values():
		var item := ItemInstance.new("card%d" % count, card.id, ItemInstance.Kind.CARD)
		state.add_item(item)
		count += 1
	for id in ["merge1", "merge2", "merge3"]:
		state.add_item(ItemInstance.new(id, "A1", ItemInstance.Kind.CARD))
	state.items.merge2.bound = true
	state.preset(state.active_hero_id, 0).card_ids[0] = "merge2"
	session.preparation_state = state
	session.journey_state = JourneyState.new(1)
	session.save_path = "user://missing_card_preparation_directory/save.json"
	var screen := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	screen._on_mode_pressed("skills")
	await process_frame
	await process_frame
	for mode in ["skills", "loadout", "skills"]:
		screen._on_mode_pressed(mode)
		await process_frame
		await process_frame
		var layout: Control = screen.get_node("Layout")
		check(layout.get_global_rect().position.y >= 90 and layout.get_global_rect().end.y <= 1192, "layout stays between header and navigation in " + mode)
		check(screen.get_node("Layout/LoadoutRow").visible == (mode == "loadout"), "equipment row only occupies equipment page")
		check(screen.status.get_global_rect().end.y <= 1192, "status stays above navigation")
		check(screen.get_node("Layout/ModeBar").get_global_rect().position.y >= screen.get_node("PageHeader").get_global_rect().end.y + 12, "mode buttons clear actual header height")
		check(screen.supplies_panel.visible == (mode != "skills"), "skills has no duplicate warehouse; equipment retains warehouse")
		if mode != "skills":
			check(screen.get_node("Layout/SuppliesPanel/Scroll").get_global_rect().end.y <= screen.status.get_global_rect().position.y, "warehouse and status do not overlap")
	var pool: ScrollContainer = screen.card_panel.get_node("CardPreparationPanel/SchoolPool")
	check(pool.size.y > 400, "dedicated school pool expands into freed warehouse space")
	check(not screen.warehouse_items.is_visible_in_tree() and not screen.filter_cycle.is_visible_in_tree(), "skills only exposes one visible pool")
	var header: Label = screen.get_node("PageHeader")
	header.text += "\n额外页头说明"
	await process_frame
	await process_frame
	check(screen.get_node("Layout/ModeBar").get_global_rect().position.y >= header.get_global_rect().end.y + 12, "layout follows taller header")
	check(screen.status.get_global_rect().end.y <= 1192, "taller header keeps status above navigation")
	pool.scroll_vertical = 400
	await process_frame
	var scrolled := pool.scroll_vertical
	check(scrolled > 0, "school pool actually scrolls")
	var scrolled_card: Button = pool.get_child(0).get_node("frost").get_child(1).get_child(0)
	scrolled_card.pressed.emit()
	await process_frame
	await process_frame
	pool = screen.card_panel.get_node("CardPreparationPanel/SchoolPool")
	check(pool.scroll_vertical == scrolled, "selecting a scrolled card preserves scroll offset")
	screen._refresh()
	screen._refresh()
	await process_frame
	await process_frame
	check(screen.card_panel.get_node("CardPreparationPanel/SchoolPool").scroll_vertical == scrolled, "consecutive refreshes preserve pending scroll restoration")
	check(screen.card_slots.columns == 8 and screen.card_slots.get_child_count() == 8, "eight visual slots")
	for index in 8:
		var tile: Button = screen.card_slots.get_child(index)
		check(tile.custom_minimum_size == Vector2(72, 144), "equipped display one by two")
		check(tile.disabled == (index >= 4), "unopened slots disabled")
	var groups: Node = screen.card_panel.get_node("CardPreparationPanel/SchoolPool").get_child(0)
	check(groups.get_child_count() == 8, "all school layers")
	var displayed := {}
	for group in groups.get_children():
		for tile in group.get_child(1).get_children():
			check(tile.custom_minimum_size == Vector2(72, 144) and tile.icon != null, "pool portrait shape and actual icon")
			check(not displayed.has(tile.instance_id), "each card only once")
			displayed[tile.instance_id] = true
	check(displayed.size() == state.items.size(), "all cards represented")
	for item in state.items.values():
		check(item.footprint == Vector2i.ONE, "inventory footprint unchanged")
	screen.selected_instance_id = "merge1"
	var before := SaveCodec.encode(state, session.journey_state)
	screen.card_panel.get_node("CardPreparationPanel").get_child(0).get_node("MergeCards").pressed.emit()
	check(SaveCodec.encode(state, session.journey_state) == before, "merge save failure restores items stars bound presets progress")
	var good_path := "user://test_card_preparation_ui.json"
	SaveRepository.erase(good_path)
	session.save_path = good_path
	screen.card_panel.get_node("CardPreparationPanel").get_child(0).get_node("MergeCards").pressed.emit()
	check(state.items.merge1.star == 2 and state.items.merge1.bound and not state.items.has("merge2"), "merge UI commits")
	check(SaveRepository.read(good_path).preparation.items.merge1.star == 2, "merge persistence")
	screen.selected_instance_id = "merge1"
	screen._on_slot_pressed(ItemInstance.Kind.CARD, 3)
	check(state.card_slots[3] == "merge1", "select then equip arbitrary slot")
	screen.selected_instance_id = "merge1"
	var panel: Node = screen.card_panel.get_node("CardPreparationPanel")
	panel.get_child(0).get_child(2).pressed.emit()
	check(state.items.merge1.location == ItemInstance.Location.WAREHOUSE and state.card_slots[3] == "", "return action restores school pool")
	check(screen.has_node("Layout/SuppliesPanel/Scroll/WarehouseContent/BackpackGrid") and screen.warehouse_items != null, "original warehouse and backpack preserved")
	screen._on_mode_pressed("supplies")
	check(screen.backpack_grid.visible, "original supplies grid usable")
	check(screen.supplies_panel.visible and screen.warehouse_items.is_visible_in_tree(), "supplies restores real warehouse")
	SaveRepository.erase(good_path)
	screen.queue_free()
	await process_frame
	print("Card preparation UI: %d checks passed" % checks)
	quit()
