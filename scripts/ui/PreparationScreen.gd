extends Control

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const LoadoutRules = preload("res://scripts/prepare/LoadoutRules.gd")
const PackingRules = preload("res://scripts/prepare/PackingRules.gd")
const ItemIconFactory = preload("res://scripts/ui/ItemIconFactory.gd")
const InventoryDragButton = preload("res://scripts/ui/InventoryDragButton.gd")
const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")
const AndroidHeroCatalog = preload("res://scripts/content/AndroidHeroCatalog.gd")
const CorePageLayout = preload("res://scripts/ui/CorePageLayout.gd")

@onready var hero_button: Button = %HeroButton
@onready var status: Label = %Status
@onready var preset_row: HBoxContainer = $Layout/PresetRow
@onready var equipment_panel: VBoxContainer = $Layout/LoadoutRow/EquipmentPanel
@onready var card_panel: VBoxContainer = $Layout/CardPanel
@onready var supplies_panel: VBoxContainer = $Layout/SuppliesPanel
@onready var camp_panel: HBoxContainer = $Layout/CampPanel
@onready var inventory_more_panel: HBoxContainer = $Layout/SuppliesPanel/InventoryMorePanel
@onready var filter_cycle: Button = $Layout/SuppliesPanel/FilterRow/FilterCycle
@onready var equipment_slots: GridContainer = $Layout/LoadoutRow/EquipmentPanel/EquipmentSlots
@onready var card_slots: GridContainer = $Layout/CardPanel/CardSlots
const InventoryShelf = preload("res://scripts/ui/InventoryShelf.gd")
@onready var warehouse_items: InventoryShelf = $Layout/SuppliesPanel/Scroll/WarehouseContent/WarehouseItems
@onready var backpack_grid: Control = $Layout/SuppliesPanel/Scroll/WarehouseContent/BackpackGrid
@onready var safe_grid: Control = $Layout/SuppliesPanel/Scroll/WarehouseContent/SafeGrid

var state: PreparationState
var selected_instance_id := ""
var active_mode := "loadout"
var inventory_filter := "all"
var hero_portrait: Button

const EQUIPMENT_NAMES := ["先锋长剑", "守卫圆盾", "迅捷短靴", "日耀护符", "潮汐胸甲", "雷鸣手套", "猎手弩", "月影披风", "焰核指环"]
const CARD_NAMES := ["迅击训练", "精准打击", "备战护盾", "生机", "怒火回响", "寒霜箭矢", "毒蚀印记", "灵光祝福"]

func _ready() -> void:
	state = _session_state()
	CorePageLayout.install(self, "preparation", "仓库与整备", "%d 营地金币 · 装备与技能方案" % state.gold)
	equipment_panel.get_node("Caption").text = "装备 · 上6生效 / 下3备用"
	$Layout.offset_top = 90
	$Layout.offset_bottom = -CorePageLayout.NAV_RESERVE
	get_node("PageHeader").resized.connect(_fit_page_header)
	resized.connect(_fit_page_header)
	_fit_page_header.call_deferred()
	$Layout/Back.hide()
	$Layout/Header.hide()
	var storage := backpack_grid.get_parent()
	for section in ["SafeGrid", "SafeCaption", "BackpackGrid", "BackpackCaption"]:
		storage.move_child(storage.get_node(section), 0)
	hero_portrait = Button.new()
	hero_portrait.name = "HeroPortrait"
	hero_portrait.custom_minimum_size = Vector2(150, 0)
	hero_portrait.expand_icon = true
	hero_portrait.add_theme_constant_override("icon_max_width", 150)
	hero_portrait.pressed.connect(_on_hero_pressed)
	var loadout_row := $Layout/LoadoutRow
	loadout_row.custom_minimum_size.y = 270
	loadout_row.add_theme_constant_override("separation", 18)
	$Layout.add_theme_constant_override("separation", 18)
	loadout_row.add_child(hero_portrait)
	loadout_row.move_child(hero_portrait, 0)
	var spacer := Control.new()
	spacer.custom_minimum_size.y = 24
	$Layout.add_child(spacer)
	$Layout.move_child(spacer, supplies_panel.get_index())
	# 物品详情入口（含旋转预览）：对齐 Android 点按预览。
	var detail := Button.new()
	detail.name = "ItemDetail"
	detail.text = "物品详情"
	CorePageLayout.paint_button(detail)
	detail.pressed.connect(_on_item_detail_pressed)
	inventory_more_panel.add_child(detail)
	_set_mode("loadout")
	_refresh()

func _on_item_detail_pressed() -> void:
	if selected_instance_id.is_empty():
		status.text = "请先选择要查看的物品。"
		return
	# 详情弹层内的旋转为纯视觉预览，不改动物品；真实旋转仍由工具栏"旋转"按钮触发。
	preload("res://scripts/ui/ItemDetailSheet.gd").show(self, state, selected_instance_id)

func _fit_page_header() -> void:
	var header: Control = get_node("PageHeader")
	$Layout.offset_top = maxf(90.0, header.position.y + header.size.y + 12.0)
	$Layout.add_theme_constant_override("separation", 8 if size.y < 1100 else 18)
	var warehouse_scroll: ScrollContainer = $Layout/SuppliesPanel/Scroll
	var available_height := maxf(0.0, size.y - CorePageLayout.NAV_RESERVE - $Layout.offset_top)
	var fixed_height: float = $Layout.get_combined_minimum_size().y - warehouse_scroll.custom_minimum_size.y
	warehouse_scroll.custom_minimum_size.y = clampf(available_height - fixed_height, 96.0, 220.0)
	var pool: ScrollContainer = card_panel.get_node_or_null("CardPreparationPanel/SchoolPool")
	if pool != null and active_mode == "skills":
		var card_fixed_height: float = $Layout.get_combined_minimum_size().y - pool.custom_minimum_size.y
		var available: float = size.y - CorePageLayout.NAV_RESERVE - $Layout.offset_top
		pool.custom_minimum_size.y = clampf(available - card_fixed_height, 120.0, 250.0)

func _session_state() -> PreparationState:
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if session == null:
		session = preload("res://scripts/AppSession.gd").new()
		session.name = "AppSession"
		get_tree().root.add_child(session)
	return session.call("ensure_preparation_state") as PreparationState

func _refresh() -> void:
	hero_button.text = "英雄：%s（切换）" % _hero_name(state.active_hero_id)
	hero_portrait.text = "切换英雄"
	hero_portrait.tooltip_text = _hero_name(state.active_hero_id)
	var augments := preload("res://scripts/content/HeroAugmentCatalog.gd")
	var owned_badges: Array[String] = []
	if augments.owned(state, state.active_hero_id, "shard"): owned_badges.append("魔晶")
	if augments.owned(state, state.active_hero_id, "aghanim"): owned_badges.append("神杖")
	if not owned_badges.is_empty(): hero_portrait.text = " · ".join(owned_badges)
	hero_portrait.tooltip_text += "\n" + augments.summary(state, state.active_hero_id)
	hero_portrait.icon = load("res://assets/android/art/branding/juggernaut-launcher-portrait-v1.png") if state.active_hero_id == "juggernaut" else AndroidArtCatalog.hero_texture(state.active_hero_id)
	_rebuild_presets()
	_rebuild_slots(equipment_slots, ItemInstance.Kind.EQUIPMENT, state.equipment_slots)
	_rebuild_slots(card_slots, ItemInstance.Kind.CARD, state.card_slots)
	preload("res://scripts/ui/CardPreparationPanel.gd").rebuild(self)
	_rebuild_warehouse()
	_rebuild_storage(backpack_grid, ItemInstance.Location.BACKPACK, state.backpack_size)
	_rebuild_storage(safe_grid, ItemInstance.Location.SAFE, state.safe_size)
	filter_cycle.text = _filter_label()
	_fit_page_header.call_deferred()
	if selected_instance_id.is_empty() and status.text.is_empty():
		status.text = "配装与技能可直接替换；物资页支持拖放和一键整理。"

func _set_mode(mode: String) -> void:
	active_mode = mode
	$Layout/LoadoutRow.visible = mode == "loadout"
	equipment_panel.visible = mode == "loadout"
	card_panel.visible = mode == "skills"
	card_panel.size_flags_vertical = Control.SIZE_EXPAND_FILL if mode == "skills" else Control.SIZE_FILL
	supplies_panel.visible = mode != "skills"
	var show_storage := mode == "supplies"
	var storage := backpack_grid.get_parent()
	for section in ["BackpackCaption", "BackpackGrid", "SafeCaption", "SafeGrid"]:
		storage.get_node(section).visible = show_storage
	$Layout/SuppliesPanel/InventoryActions.visible = show_storage
	if not show_storage:
		inventory_more_panel.hide()
		camp_panel.hide()
	inventory_filter = {"loadout": "equipment", "skills": "cards"}.get(mode, "all")
	preset_row.visible = mode != "supplies"
	hero_portrait.visible = mode == "loadout"
	($Layout/ModeBar/Loadout as Button).button_pressed = mode == "loadout"
	($Layout/ModeBar/Skills as Button).button_pressed = mode == "skills"
	($Layout/ModeBar/Supplies as Button).button_pressed = mode == "supplies"
	for tab in [$Layout/ModeBar/Loadout, $Layout/ModeBar/Skills, $Layout/ModeBar/Supplies]:
		CorePageLayout.paint_button(tab, tab.button_pressed)

func _on_mode_pressed(mode: String) -> void:
	_set_mode(mode)
	status.text = {"loadout": "选择装备后点击格位即可替换。", "skills": "选择技能卡后点击格位即可替换。", "supplies": "拖动物资整理背包；批量出售会跳过锁定物品。"}.get(mode, "")
	_refresh()

func _on_camp_pressed() -> void:
	camp_panel.visible = not camp_panel.visible
	_fit_page_header.call_deferred()
	status.text = "营地功能已展开。" if camp_panel.visible else "营地功能已收起。"

func _on_inventory_more_pressed() -> void:
	inventory_more_panel.visible = not inventory_more_panel.visible
	_fit_page_header.call_deferred()

func _on_filter_cycle_pressed() -> void:
	var filters := ["all", "equipment", "cards"]
	inventory_filter = str(filters[(filters.find(inventory_filter) + 1) % filters.size()])
	_rebuild_warehouse()
	filter_cycle.text = _filter_label()

func _filter_label() -> String:
	return {"all": "全部物资 · 点击切换", "equipment": "装备 · 点击切换", "cards": "技能卡 · 点击切换"}.get(inventory_filter, "全部物资 · 点击切换")

func _rebuild_presets() -> void:
	_clear_children(preset_row)
	for index in PreparationState.PRESET_COUNT:
		var button := _button("方案 %d" % (index + 1), Vector2(0, 48))
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		if index == state.selected_preset(state.active_hero_id):
			button.text += " · 已选"
		button.pressed.connect(_on_preset_pressed.bind(index))
		preset_row.add_child(button)

func _rebuild_slots(container: GridContainer, kind: ItemInstance.Kind, slots: Array[String]) -> void:
	_clear_children(container)
	if kind == ItemInstance.Kind.CARD:
		container.columns = 8
	for index in slots.size():
		var instance_id := slots[index]
		var label := "%d\n空位" % (index + 1) if instance_id.is_empty() else _instance_label(instance_id)
		var instance := state.item(instance_id)
		var icon: Texture2D = ItemIconFactory.icon_for(instance.definition_id, instance.kind) if instance != null else null
		var minimum_width := 144.0 if kind == ItemInstance.Kind.EQUIPMENT else 155.0
		var button := _item_button(label, Vector2(minimum_width, 70), icon, instance_id, {"area": "slot", "kind": kind, "slot": index})
		if kind == ItemInstance.Kind.EQUIPMENT:
			var reserve := index >= PreparationState.ACTIVE_EQUIPMENT_SLOT_COUNT
			button.tooltip_text = ("备用位：仅携带，不提供属性、被动和主动技能。移至上方6格后生效。" if reserve else "生效位：装备属性、被动和主动技能在战斗中生效。")
			if reserve:
				button.text = "备用%d\n%s" % [index - PreparationState.ACTIVE_EQUIPMENT_SLOT_COUNT + 1, "空位" if instance == null else _instance_label(instance_id)]
				var selected := instance_id != "" and instance_id == selected_instance_id
				button.add_theme_stylebox_override("normal", CorePageLayout.frame(Color("142b37"), Color("ffca3f") if selected else Color("718694")))
				button.add_theme_stylebox_override("hover", CorePageLayout.frame(Color("233e4b"), Color("91a6b4")))
				button.add_theme_color_override("font_color", Color("b8cbd2"))
		if kind == ItemInstance.Kind.CARD:
			button.custom_minimum_size = Vector2(72, 144)
			if instance != null:
				button.text += "\n%d星" % instance.star
				button.tooltip_text = preload("res://scripts/prepare/CardPreparationRules.gd").details(instance)
			button.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
			button.vertical_icon_alignment = VERTICAL_ALIGNMENT_TOP
			button.disabled = index >= preload("res://scripts/prepare/CardPreparationRules.gd").enabled_slots(state)
			if button.disabled:
				button.text = "%d\n未解锁" % (index + 1)
		button.pressed.connect(_on_slot_pressed.bind(kind, index))
		container.add_child(button)

func _rebuild_warehouse() -> void:
	_clear_children(warehouse_items)
	warehouse_items.reset()
	$Layout/SuppliesPanel/Scroll/WarehouseContent/WarehouseCaption.text = "局外仓库" if state.phase == PreparationState.Phase.HOME else "随身可用物品 · 选中后点上方栏位装备"
	var return_target := _item_button("", Vector2.ZERO, null, "", {"area": "warehouse"})
	return_target.flat = true
	return_target.tooltip_text = "移回仓库"
	return_target.visible = state.phase == PreparationState.Phase.HOME
	return_target.pressed.connect(_on_return_to_warehouse)
	warehouse_items.add_child(return_target)
	return_target.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	for instance in state.items.values():
		var available: bool = instance.location == ItemInstance.Location.WAREHOUSE if state.phase == PreparationState.Phase.HOME else instance.location in [ItemInstance.Location.BACKPACK, ItemInstance.Location.SAFE]
		if not available:
			continue
		if inventory_filter == "equipment" and instance.kind != ItemInstance.Kind.EQUIPMENT:
			continue
		if inventory_filter == "cards" and instance.kind != ItemInstance.Kind.CARD:
			continue
		var span: Vector2i = Vector2i(instance.footprint.y, instance.footprint.x) if instance.rotated else instance.footprint
		var button := _item_button("", Vector2.ZERO, ItemIconFactory.icon_for(instance.definition_id, instance.kind), instance.id, {"area": "warehouse"})
		button.tooltip_text = _instance_label(instance.id)
		button.pressed.connect(_on_warehouse_pressed.bind(instance.id))
		warehouse_items.append_tile(button, span)

func _rebuild_storage(grid: Control, location: ItemInstance.Location, dimensions: Vector2i) -> void:
	_clear_children(grid)
	var is_safe := location == ItemInstance.Location.SAFE
	var area := "safe" if is_safe else "backpack"
	var caption := grid.get_parent().get_node("SafeCaption" if is_safe else "BackpackCaption") as Label
	caption.text = "%s · %d×%d" % ["安全箱" if is_safe else "随身背包", dimensions.x, dimensions.y]
	var edge := minf(104.0, floorf(600.0 / maxi(1, dimensions.x)))
	grid.custom_minimum_size.y = dimensions.y * edge
	for y in dimensions.y:
		for x in dimensions.x:
			_add_storage_tile(grid, location, area, Vector2i(x, y), Vector2i.ONE, edge)
	for item in state.items.values():
		if item.location == location:
			var span: Vector2i = Vector2i(item.footprint.y, item.footprint.x) if item.rotated else item.footprint
			_add_storage_tile(grid, location, area, item.grid_position, span, edge, item)

func _add_storage_tile(grid: Control, location: ItemInstance.Location, area: String, cell: Vector2i, span: Vector2i, edge: float, item: ItemInstance = null) -> void:
	var icon: Texture2D = ItemIconFactory.icon_for(item.definition_id, item.kind) if item != null else null
	var button := _item_button("", Vector2.ZERO, icon, item.id if item != null else "", {"area": area, "position": cell})
	button.drop_cell_size = edge
	if item != null:
		button.tooltip_text = _instance_label(item.id)
		button.add_theme_constant_override("icon_max_width", int(edge * span.x) - 12)
	button.pressed.connect(func() -> void:
		var pointer := button.get_local_mouse_position()
		var position := cell
		if Rect2(Vector2.ZERO, button.size).has_point(pointer):
			position += Vector2i((pointer / edge).floor())
		_on_grid_cell_pressed(location, position)
	)
	grid.add_child(button)
	button.position = Vector2(cell) * edge
	button.size = Vector2(span) * edge - Vector2(4, 4)

func _on_warehouse_pressed(instance_id: String) -> void:
	selected_instance_id = instance_id
	status.text = "已选择 %s：点击上方对应栏位放入。" % _instance_label(instance_id)
	_rebuild_warehouse()

func _on_slot_pressed(kind: ItemInstance.Kind, slot: int) -> void:
	if selected_instance_id.is_empty():
		var slots := state.equipment_slots if kind == ItemInstance.Kind.EQUIPMENT else state.card_slots
		if slot >= 0 and slot < slots.size() and not slots[slot].is_empty():
			selected_instance_id = slots[slot]
			status.text = "已选择 %s，可切换到物资页移回仓库或放入容器。" % _instance_label(selected_instance_id)
			_refresh()
		else:
			status.text = "请先选择物品。"
		return
	var result: Dictionary
	if kind == ItemInstance.Kind.CARD:
		result = preload("res://scripts/ui/CardPreparationPanel.gd").transact(self, func() -> Dictionary: return LoadoutRules.place(state, selected_instance_id, kind, slot))
	else:
		result = LoadoutRules.place(state, selected_instance_id, kind, slot)
	if not result.accepted:
		status.text = result.reason
		return
	selected_instance_id = ""
	status.text = "已替换栏位；被替换的物品已回到局外仓库。"
	_refresh()

func _on_backpack_cell_pressed(position: Vector2i) -> void:
	_on_grid_cell_pressed(ItemInstance.Location.BACKPACK, position)

func _on_safe_cell_pressed(position: Vector2i) -> void:
	_on_grid_cell_pressed(ItemInstance.Location.SAFE, position)

func _on_grid_cell_pressed(container: ItemInstance.Location, position: Vector2i) -> void:
	if selected_instance_id.is_empty():
		var hit := _grid_item_at(container, position)
		if hit == null:
			status.text = "请选择仓库物品，或点已有物品后使用整理、旋转、锁定、出售。"
			return
		selected_instance_id = hit.id
		status.text = "已选择 %s。" % _instance_label(hit.id)
		_refresh()
		return
	var item := state.item(selected_instance_id)
	if item == null:
		selected_instance_id = ""
		_refresh()
		return
	var result := PackingRules.place(state, selected_instance_id, container, position, item.rotated)
	status.text = ("已放入安全箱。" if container == ItemInstance.Location.SAFE else "已放入随身背包。") if result.accepted else result.reason
	if result.accepted:
		selected_instance_id = ""
	_refresh()

func _on_return_to_warehouse() -> void:
	if selected_instance_id.is_empty():
		status.text = "请先选择要移回仓库的物品。"
		return
	_on_drag_drop(selected_instance_id, {"area": "warehouse"})

func _on_drag_drop(instance_id: String, target: Dictionary) -> void:
	var dragged := state.item(instance_id)
	if str(target.get("area", "")) == "warehouse" and dragged != null and dragged.kind == ItemInstance.Kind.CARD:
		preload("res://scripts/ui/CardPreparationPanel.gd").transact(self, func() -> Dictionary: return PackingRules.move_to_warehouse(state, instance_id))
		return
	if str(target.get("area", "")) == "slot" and int(target.get("kind", -1)) == ItemInstance.Kind.CARD:
		preload("res://scripts/ui/CardPreparationPanel.gd").transact(self, func() -> Dictionary: return LoadoutRules.place(state, instance_id, ItemInstance.Kind.CARD, int(target.slot)))
		return
	var result := {"accepted": false, "reason": "未知目标"}
	match str(target.get("area", "")):
		"slot":
			result = LoadoutRules.place(state, instance_id, target.kind as ItemInstance.Kind, int(target.slot))
		"backpack":
			var backpack_item := state.item(instance_id)
			result = PackingRules.place(state, instance_id, ItemInstance.Location.BACKPACK, target.position as Vector2i, backpack_item.rotated if backpack_item != null else false)
		"safe":
			var safe_item := state.item(instance_id)
			result = PackingRules.place(state, instance_id, ItemInstance.Location.SAFE, target.position as Vector2i, safe_item.rotated if safe_item != null else false)
		"warehouse":
			result = PackingRules.move_to_warehouse(state, instance_id)
	status.text = "已拖放物品。" if result.accepted else str(result.reason)
	if result.accepted:
		selected_instance_id = ""
	_refresh()

func _on_preset_pressed(preset_index: int) -> void:
	if active_mode == "skills":
		preload("res://scripts/ui/CardPreparationPanel.gd").transact(self, func() -> Dictionary: return LoadoutRules.select_preset(state, preset_index))
		return
	var result := LoadoutRules.select_preset(state, preset_index)
	status.text = "已切换方案 %d。" % (preset_index + 1) if result.accepted else result.reason
	_refresh()

func _on_hero_pressed() -> void:
	if has_node("HeroPicker"):
		return
	var dialog := AcceptDialog.new()
	dialog.name = "HeroPicker"
	dialog.title = "切换英雄"
	dialog.ok_button_text = "返回"
	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(480, 600)
	var list := VBoxContainer.new()
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(list)
	for hero_id in AndroidHeroCatalog.ids():
		var button := Button.new()
		button.text = _hero_name(hero_id)
		button.custom_minimum_size.y = 95
		button.expand_icon = true
		button.add_theme_constant_override("icon_max_width", 80)
		button.icon = load("res://assets/android/art/branding/juggernaut-launcher-portrait-v1.png") if hero_id == "juggernaut" else AndroidArtCatalog.hero_texture(hero_id)
		button.disabled = hero_id not in state.unlocked_heroes
		if button.disabled:
			button.text += " · 未解锁"
		button.pressed.connect(_select_hero.bind(hero_id, dialog))
		list.add_child(button)
	dialog.add_child(scroll)
	add_child(dialog)
	dialog.confirmed.connect(dialog.queue_free)
	dialog.close_requested.connect(dialog.queue_free)
	dialog.popup_centered()

func _select_hero(next_hero: String, dialog: AcceptDialog) -> void:
	dialog.hide()
	dialog.queue_free()
	var result := LoadoutRules.activate_hero(state, next_hero)
	if not result.accepted:
		status.text = str(result.reason)
		return
	selected_instance_id = ""
	status.text = "已切换为%s，并自动套用该英雄最近方案。" % _hero_name(next_hero)
	_refresh()
	for chapter in preload("res://scripts/story/StoryCatalog.gd").data().get("chapters", []):
		if str(chapter.id).begins_with("H") and not chapter.portraits.is_empty() and chapter.portraits[0].hero_id == next_hero:
			var saved: Dictionary = get_tree().root.get_node("AppSession").call("queue_story", str(chapter.id))
			if saved.accepted:
				preload("res://scripts/story/StoryPlayer.gd").show_pending(self, get_tree().root.get_node("AppSession"))
			else:
				status.text = str(saved.reason)
			break

func _on_save_pressed() -> void:
	var preset_index := state.selected_preset(state.active_hero_id)
	if active_mode == "skills":
		preload("res://scripts/ui/CardPreparationPanel.gd").transact(self, func() -> Dictionary: return LoadoutRules.save_preset(state, preset_index))
		return
	var result := LoadoutRules.save_preset(state, preset_index)
	status.text = "已保存方案 %d。" % (preset_index + 1) if result.accepted else result.reason
	_refresh()

func _on_organize_pressed() -> void:
	var result := PackingRules.organize(state, ItemInstance.Location.BACKPACK)
	status.text = "随身背包已按物品面积整理。" if result.accepted else result.reason
	_refresh()

func _on_rotate_pressed() -> void:
	if selected_instance_id.is_empty():
		status.text = "先点选背包内的物品。"
		return
	var result := PackingRules.rotate(state, selected_instance_id)
	status.text = "物品已旋转。" if result.accepted else result.reason
	_refresh()

func _on_lock_pressed() -> void:
	if selected_instance_id.is_empty():
		status.text = "先选择需要锁定的物品。"
		return
	var result := PackingRules.toggle_lock(state, selected_instance_id)
	var item := state.item(selected_instance_id)
	status.text = ("物品已锁定。" if item != null and item.locked else "物品已解锁。") if result.accepted else result.reason
	_refresh()

func _on_quick_sell_pressed() -> void:
	if selected_instance_id.is_empty():
		status.text = "先选择要出售的仓库或背包物品。"
		return
	var result := PackingRules.quick_sell(state, selected_instance_id)
	status.text = "已出售，获得金币。" if result.accepted else result.reason
	if result.accepted:
		selected_instance_id = ""
	_refresh()

func _on_batch_sell_pressed() -> void:
	var result := PackingRules.batch_sell_warehouse(state)
	if result.accepted:
		selected_instance_id = ""
		status.text = "批量出售 %d 件，获得 %d 金币；锁定物品已保留。" % [int(result.sold), int(result.value)]
	else:
		status.text = str(result.reason)
	_refresh()

func _on_back_pressed() -> void:
	get_tree().root.get_node("AppSession").call("save_progress")
	if state.phase == PreparationState.Phase.JOURNEY:
		get_tree().root.get_node("AppSession").set_meta("journey_route_overview", true)
	ScreenRouter.open(get_tree(), "expedition" if state.phase == PreparationState.Phase.JOURNEY else "home")

func _on_crafting_pressed() -> void:
	ScreenRouter.open(get_tree(), "crafting")

func _on_shop_pressed() -> void:
	ScreenRouter.open(get_tree(), "shop")

func _button(label: String, minimum_size: Vector2, icon: Texture2D = null) -> Button:
	var button := Button.new()
	CorePageLayout.paint_button(button)
	button.custom_minimum_size = minimum_size
	button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	button.text = label
	button.icon = icon
	return button

func _item_button(label: String, minimum_size: Vector2, icon: Texture2D, instance_id: String, target: Dictionary) -> InventoryDragButton:
	var button := InventoryDragButton.new()
	button.custom_minimum_size = minimum_size
	button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	button.text = label
	button.icon = icon
	button.instance_id = instance_id
	button.expand_icon = true
	button.add_theme_constant_override("icon_max_width", 72)
	var rim := Color("ffca3f") if instance_id != "" and instance_id == selected_instance_id else Color("30a7c7")
	button.add_theme_stylebox_override("normal", CorePageLayout.frame(Color("09394c"), rim))
	button.add_theme_stylebox_override("hover", CorePageLayout.frame(Color("0c667e"), rim))
	button.drop_target = target
	button.drop_handler = _on_drag_drop
	return button

func _instance_label(instance_id: String) -> String:
	var instance := state.item(instance_id)
	if instance == null:
		return "缺失物品"
	var lock_mark := " 🔒" if instance.locked else ""
	var display_name := ""
	if instance.definition_id.begins_with("equipment_") or instance.definition_id.begins_with("card_"):
		var numeric_id := instance.definition_id.get_slice("_", 1).to_int()
		var names: Array = EQUIPMENT_NAMES if instance.kind == ItemInstance.Kind.EQUIPMENT else CARD_NAMES
		display_name = str(names[numeric_id])
	else:
		var definition := ContentCatalog.item(instance.definition_id)
		display_name = definition.display_name if definition != null else instance.definition_id
	return "%s%s" % [display_name, lock_mark]

func _grid_item_at(container: ItemInstance.Location, position: Vector2i) -> ItemInstance:
	for candidate in state.items.values():
		if candidate.location != container:
			continue
		var dimensions: Vector2i = Vector2i(candidate.footprint.y, candidate.footprint.x) if candidate.rotated else candidate.footprint
		if position.x >= candidate.grid_position.x and position.x < candidate.grid_position.x + dimensions.x and position.y >= candidate.grid_position.y and position.y < candidate.grid_position.y + dimensions.y:
			return candidate
	return null

func _hero_name(hero_id: String) -> String:
	var hero := AndroidHeroCatalog.hero(hero_id)
	if not hero.is_empty():
		return str(hero.display_name)
	for definition in ContentCatalog.heroes():
		if definition.id == hero_id:
			return definition.display_name
	return hero_id

func _clear_children(container: Node) -> void:
	for child in container.get_children():
		container.remove_child(child)
		child.queue_free()
