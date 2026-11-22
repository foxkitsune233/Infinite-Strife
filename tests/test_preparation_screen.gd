extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")

var _failed := false

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	var screen := preload("res://scenes/Preparation.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	for definition in preload("res://scripts/content/AndroidEquipmentCatalog.gd").items():
		var path: String = AndroidArtCatalog.card_path(definition.id) if definition.kind == "CARD" else AndroidArtCatalog.equipment_path(definition.id)
		_assert_equal(not path.is_empty(), true, "Android image resolves for " + str(definition.id))
	_assert_equal(AndroidArtCatalog.card_path("FS1").ends_with("/F1.webp"), true, "frost card uses its actual Android art ID")
	var layout_rect: Rect2 = screen.get_node("Layout").get_global_rect()
	var header_rect: Rect2 = screen.get_node("Layout/Header").get_global_rect()
	_assert_equal(layout_rect.position.y >= 0.0, true, "layout begins in viewport")
	_assert_equal(header_rect.position.y >= 0.0, true, "header remains visible")
	_assert_equal(screen.equipment_slots.get_child_count(), 9, "preparation screen has nine equipment slots")
	_assert_equal(screen.get_node("Layout/CardPanel/CardSlots").get_child_count(), 8, "preparation screen has eight card slots")
	_assert_equal(screen.get_node("Layout/PresetRow").get_child_count(), 3, "preparation screen has three saved presets")
	_assert_equal(screen.get_node("Layout/SuppliesPanel/Scroll/WarehouseContent/WarehouseItems").get_child_count() > 0, true, "warehouse exposes usable instances")
	_assert_equal(screen.safe_grid.get_children().filter(func(tile: InventoryDragButton) -> bool: return tile.instance_id.is_empty()).size(), 4, "safe box has a two by two grid")
	_assert_equal((screen.get_node("Layout/ModeBar/Loadout") as Button).button_pressed, true, "loadout is the focused preparation mode")
	_assert_equal(screen.equipment_panel.visible, true, "loadout shows equipment slots")
	_assert_equal(screen.hero_portrait.icon != null and screen.hero_portrait.visible, true, "loadout shows the selected hero portrait")
	_assert_equal(screen.hero_portrait.icon.resource_path.ends_with("juggernaut-launcher-portrait-v1.png"), true, "juggernaut uses Android launcher portrait")
	_assert_equal(screen.get_node("Layout/CardPanel").visible, false, "loadout keeps skill cards out of the first view")
	_assert_equal(screen.warehouse_items.is_visible_in_tree(), true, "loadout keeps the warehouse accessible")
	_assert_equal(screen.backpack_grid.is_visible_in_tree(), false, "loadout keeps the backpack in supplies")
	_assert_equal(screen.warehouse_items.custom_minimum_size.x, 600.0, "warehouse shelf is six grid units wide")
	screen._on_mode_pressed("skills")
	# 技能页已改为专用 CardPreparationPanel（八流派技能池），旧的仓库物资视图不再常驻。
	var school_pool: ScrollContainer = screen.card_panel.get_node_or_null("CardPreparationPanel/SchoolPool")
	_assert_equal(school_pool != null and school_pool.is_visible_in_tree(), true, "skills mode exposes the dedicated school card pool")
	_assert_equal(screen.card_panel.find_child("MergeCards", true, false) != null, true, "skills mode exposes three-card star merge")
	_assert_equal(screen.get_node("Layout/CardPanel").visible, true, "skills mode shows card slots")
	_assert_equal(screen.equipment_panel.visible, false, "skills mode hides equipment slots")
	screen._on_mode_pressed("supplies")
	_assert_equal(screen.get_node("Layout/SuppliesPanel").visible, true, "supplies mode shows warehouse controls")
	var warehouse_rect: Rect2 = screen.get_node("Layout/SuppliesPanel/Scroll/WarehouseContent/WarehouseItems").get_global_rect()
	_assert_equal(warehouse_rect.size.x >= 600.0, true, "warehouse keeps usable grid width")
	_assert_equal((screen.get_node("Layout/SuppliesPanel/InventoryActions/BatchSell") as Button).text, "批量出售", "supplies keeps batch selling in the primary action row")
	_assert_equal(screen.get_node("Layout/CampPanel").visible, false, "camp tools begin collapsed")
	screen._on_camp_pressed()
	_assert_equal(screen.get_node("Layout/CampPanel").visible, true, "camp tools open from the compact secondary entry")
	var equipment_button := screen.equipment_slots.get_child(0) as Button
	var card_button := screen.get_node("Layout/CardPanel/CardSlots").get_child(0) as Button
	_assert_equal(equipment_button.custom_minimum_size.x >= 140.0, true, "equipment cells fit beside the portrait")
	_assert_equal(card_button.custom_minimum_size, Vector2(72, 144), "dedicated skill cells use one by two display aspect")
	_assert_equal(equipment_button.icon != null, true, "equipped test item has visible art")
	_assert_equal(equipment_button.has_method("_get_drag_data"), true, "items provide drag data")
	screen._on_drag_drop("equip_8", {"area": "safe", "position": Vector2i(1, 0)})
	_assert_equal(screen.state.item("equip_8").location, ItemInstance.Location.SAFE, "drag drop moves item into safe box")
	screen._on_drag_drop("equip_8", {"area": "warehouse"})
	_assert_equal(screen.state.item("equip_8").location, ItemInstance.Location.WAREHOUSE, "drag drop returns item to warehouse")
	screen._on_warehouse_pressed("equip_8")
	screen._on_mode_pressed("loadout")
	_assert_equal(screen.selected_instance_id, "equip_8", "warehouse selection survives switching to loadout")
	screen._on_slot_pressed(ItemInstance.Kind.EQUIPMENT, 8)
	_assert_equal(screen.state.item("equip_8").location, ItemInstance.Location.EQUIPMENT, "selected warehouse equipment reaches its slot")
	screen._on_mode_pressed("supplies")
	screen.selected_instance_id = "equip_8"
	screen._on_safe_cell_pressed(Vector2i(1, 0))
	_assert_equal(screen.state.item("equip_8").location, ItemInstance.Location.SAFE, "selected item can be placed in the safe by tapping")
	screen._on_safe_cell_pressed(Vector2i(1, 0))
	screen._on_return_to_warehouse()
	_assert_equal(screen.state.item("equip_8").location, ItemInstance.Location.WAREHOUSE, "selected safe item can return to warehouse by tapping")
	var item: ItemInstance = screen.state.item("equip_8")
	item.footprint = Vector2i(2, 1)
	screen._rebuild_warehouse()
	var shelf_tiles: Array = screen.warehouse_items.get_children().filter(func(tile: InventoryDragButton) -> bool: return tile.instance_id == item.id)
	_assert_equal(shelf_tiles.size(), 1, "warehouse renders the item only once")
	if shelf_tiles.size() == 1:
		_assert_equal(shelf_tiles[0].size.x, 194.0, "warehouse item spans two cell widths")
	var empty_row: int = screen.state.backpack_size.y
	screen.state.backpack_size.y += 1
	screen._on_drag_drop(item.id, {"area": "backpack", "position": Vector2i(0, empty_row)})
	var tiles: Array = screen.backpack_grid.get_children().filter(func(tile: InventoryDragButton) -> bool: return tile.instance_id == item.id)
	_assert_equal(tiles.size(), 1, "a two-cell item displays one tile")
	if tiles.size() == 1:
		_assert_equal(tiles[0].size.x > tiles[0].drop_cell_size, true, "one tile spans both occupied cells")
	quit(1 if _failed else 0)

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
