extends SceneTree

var _failed := false

func _init() -> void:
	var screen := preload("res://scenes/Expedition.tscn").instantiate()
	root.add_child.call_deferred(screen)
	await process_frame
	await process_frame
	_assert_true(screen.target_scroll.visible, "new expedition opens the target chest selector")
	var backdrop := screen.get_node("ExpeditionBackdrop") as TextureRect
	_assert_true(backdrop.is_visible_in_tree() and backdrop.texture != null, "target selector shows the ruins background")
	_assert_true(screen.target_content.get_node_or_null("HeroBanner") != null, "target selector shows the Android hero and chest banner")
	var strip: Node = screen.target_content.get_child(3).get_child(0)
	_assert_equal(strip.get_child_count(), 4, "initial batch shows four goal chests")
	(strip.get_child(0) as Button).pressed.emit()
	_assert_equal((screen.target_content.get_child(1) as Label).text, "补给宝箱 · 白色", "target detail opens for the selected chest")
	screen.preparation.phase = PreparationState.Phase.JOURNEY
	screen.choosing_target = false
	screen._refresh()
	var nodes := screen.get_node("Scroll/Layout/NodeList") as HBoxContainer
	_assert_equal(nodes.get_child_count(), 5, "expedition displays five fight stages")
	_assert_equal(screen.get_node("PageNavigation").get_child_count(), 3, "expedition has the shared navigation")
	_assert_true(screen.scouting_button.visible, "battle node exposes enemy scouting")
	for difficulty in 6:
		_assert_equal(screen._visible_active_count(difficulty, 5), [5, 5, 3, 1, 0, 0][difficulty], "scouting respects difficulty visibility")
	var loadout := [{"id": "heavens_halberd"}, {"id": "nullifier"}]
	_assert_equal(screen._scouting_hint(4, loadout, 0), "仅探明用途：控制／防护\n装备型号与数量未知", "Master reveals roles only")
	_assert_equal(screen._scouting_hint(5, loadout, 0), "侦察受限，装备型号与数量未知", "Nightmare conceals roles")
	var seed: int = screen.journey.seed
	screen.scouting_button.pressed.emit()
	_assert_equal(screen.journey.seed, seed, "scouting leaves the route seed intact")
	_assert_true(not screen.journey.battle_started, "scouting does not start combat")
	_assert_true((screen.get_node("Scroll/Layout/Resolve") as Button).text.length() > 0, "expedition exposes current node action")
	_assert_true(not screen.has_node("Scroll/Layout/LootActions/Identify"), "loot panel removes the inactive identification action")
	_assert_true((screen.get_node("Scroll/Layout/LootActions/CollectAll") as Button).text.contains("放入"), "loot panel keeps direct collection")
	var item_button := Button.new()
	screen.add_child(item_button)
	screen._play_appraisal_reveal(item_button, Color.GOLD, "金")
	_assert_equal(item_button.get_child_count(), 9, "gold appraisal adds a flash and eight sparks")
	for effect in item_button.get_children():
		_assert_equal(effect.mouse_filter, Control.MOUSE_FILTER_IGNORE, "effects do not intercept pickup input")
	await create_timer(0.5).timeout
	_assert_equal(item_button.get_child_count(), 0, "reveal visuals clean up after playing")
	screen.preparation = PreparationState.new("juggernaut")
	screen.journey.current_node = 1
	screen.journey.frozen_event_id = "moonwell_echo"
	screen.journey.frozen_event_options.assign(ContentCatalog.event("moonwell_echo").options)
	screen._refresh()
	_assert_true(screen.event_choices.get_node_or_null("EventSceneArt") != null, "event choices show Android scene art")
	var scene_art := screen.event_choices.get_node("EventSceneArt") as TextureRect
	_assert_true(scene_art.texture.resource_path.ends_with("clinic-v1.jpg"), "moonwell uses Android clinic scene")
	_assert_true(screen.get_node("Scroll") is ScrollContainer, "expedition content can scroll")
	var resonance_button := screen.event_choices.get_child(2) as Button
	_assert_true(resonance_button.disabled, "resonance is visibly locked without a second-tier synergy")
	var blocked: Dictionary = JourneyRules.choose_event(screen.preparation, screen.journey, 0)
	_assert_true(not blocked.accepted and not screen.journey.resolved, "locked resonance cannot settle")
	var chosen: Dictionary = JourneyRules.choose_event(screen.preparation, screen.journey, 1)
	_assert_true(chosen.accepted, "event option settles")
	_assert_true(screen.journey.loot.any(func(drop: ItemInstance) -> bool: return drop.definition_id == "essence"), "draw guarantees essence")
	screen._refresh()
	var event_art := screen.event_choices.get_node_or_null("EventResultArt") as TextureRect
	_assert_true(event_art != null and event_art.texture != null, "resolved event displays Android result art")
	if event_art != null and event_art.texture != null:
		_assert_true(event_art.texture.resource_path.ends_with("moonwell_echo.draw_echo.webp"), "draw displays its matching illustration")
	var restored: JourneyState = SaveCodec.decode(SaveCodec.encode(screen.preparation, screen.journey)).journey
	_assert_equal(restored.event_result, screen.journey.event_result, "event choice and reward summary survive save restore")
	var state := PreparationState.new("juggernaut")
	var journey := JourneyState.new(seed)
	journey.current_node = 1
	journey.frozen_event_id = "moonwell_echo"
	journey.frozen_event_options.assign(ContentCatalog.event("moonwell_echo").options)
	var frost_cards := ["D10", "D12", "FS1"]
	for index in frost_cards.size():
		var card := ItemInstance.new("frost_%d" % index, frost_cards[index], ItemInstance.Kind.CARD)
		state.add_item(card)
		state.card_slots[index] = card.id
	_assert_true(JourneyRules.choose_event(state, journey, 0).accepted, "three distinct same-school cards unlock resonance")
	if journey.loot.size() >= 2:
		_assert_true(journey.loot[0].kind == ItemInstance.Kind.CARD and journey.loot[1].kind == ItemInstance.Kind.CARD and journey.loot[0].definition_id != journey.loot[1].definition_id, "resonance guarantees two distinct cards")
	else:
		_assert_true(false, "resonance produces at least two drops")
	var skipped := JourneyState.new(seed)
	skipped.current_node = 1
	skipped.frozen_event_id = "moonwell_echo"
	skipped.frozen_event_options.assign(ContentCatalog.event("moonwell_echo").options)
	_assert_true(JourneyRules.choose_event(state, skipped, 2).accepted, "leaving settles the event")
	_assert_true(skipped.loot.is_empty() and skipped.run_gold == 0, "leaving grants neither loot nor gold")
	var banner := JourneyState.new(seed)
	banner.current_node = 1
	banner.frozen_event_id = "banner_ruins"
	banner.frozen_event_options.assign(ContentCatalog.event("banner_ruins").options)
	screen.journey = banner
	screen._refresh()
	scene_art = screen.event_choices.get_node("EventSceneArt") as TextureRect
	_assert_true(scene_art.texture.resource_path.ends_with("blocker-v1.jpg"), "banner uses Android blocker scene")
	var purchase := screen.event_choices.get_child(2) as Button
	_assert_true(purchase.disabled and purchase.text.contains("30 本局金币"), "banner displays the locked purchase price")
	banner.run_gold = 30
	screen._refresh()
	purchase = screen.event_choices.get_child(2) as Button
	_assert_true(not purchase.disabled, "banner enables purchase when enough run gold is available")
	JourneyRules.choose_event(screen.preparation, banner, 0)
	screen._refresh()
	var banner_art := screen.event_choices.get_node_or_null("EventResultArt") as TextureRect
	_assert_true(banner_art != null and banner_art.texture != null and banner_art.texture.resource_path.ends_with("war_banner.claim.webp"), "banner displays the Android purchase illustration")
	var packing := JourneyState.new(seed)
	packing.resolved = true
	packing.drop_serial = 1
	var pickup := ItemInstance.new("packing_test", "broadsword", ItemInstance.Kind.EQUIPMENT)
	pickup.footprint = Vector2i(2, 1)
	packing.loot.append(pickup)
	screen.journey = packing
	screen._refresh()
	_assert_true(screen.packing_panel.visible, "reward screen shows the packing grids")
	var bad := JourneyRules.collect_one(screen.preparation, packing, pickup.id, Vector2i(3, 0))
	_assert_true(not bad.accepted and packing.loot.has(pickup) and screen.preparation.item(pickup.id) == null, "out of bounds pickup stays on the ground")
	var placed := JourneyRules.collect_one(screen.preparation, packing, pickup.id, Vector2i(3, 0), true)
	_assert_true(placed.accepted and pickup.rotated and pickup.grid_position == Vector2i(3, 0) and packing.loot.is_empty(), "rotation permits exact placement in the last column")
	var put_back := JourneyRules.put_down(screen.preparation, packing, pickup.id)
	_assert_true(put_back.accepted and packing.loot.has(pickup) and screen.preparation.item(pickup.id) == null, "putting down removes the carried item and restores its ground tile")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])

func _assert_true(value: bool, label: String) -> void:
	if not value:
		_failed = true
		push_error(label)
