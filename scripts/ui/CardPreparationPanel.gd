extends RefCounted
const Rules = preload("res://scripts/prepare/CardPreparationRules.gd")
const Codec = preload("res://scripts/save/SaveCodec.gd")

static func transact(host: Control, action: Callable) -> Dictionary:
	var session := host.get_tree().root.get_node("AppSession")
	var before := Codec.encode(host.state, session.ensure_journey_state())
	var result: Dictionary = action.call()
	if result.accepted:
		var saved: Dictionary = session.save_progress()
		if not saved.accepted:
			var restored: PreparationState = Codec.decode(before, false).preparation
			for property in restored.get_property_list():
				if int(property.usage) & PROPERTY_USAGE_SCRIPT_VARIABLE:
					host.state.set(property.name, restored.get(property.name))
			result = saved
	host.status.text = str(result.get("reason", "已更新技能卡"))
	host._refresh()
	return result

static func rebuild(host: Control) -> void:
	var old: Node = host.card_panel.get_node_or_null("CardPreparationPanel")
	var scroll_position := 0
	if old != null:
		var old_pool: ScrollContainer = old.get_node("SchoolPool")
		scroll_position = int(old_pool.get_meta("restore_scroll", old_pool.scroll_vertical))
		host.card_panel.remove_child(old)
		old.queue_free()
	var panel := VBoxContainer.new()
	panel.name = "CardPreparationPanel"
	panel.size_flags_vertical = Control.SIZE_EXPAND_FILL
	host.card_panel.add_child(panel)
	var tools := HBoxContainer.new()
	panel.add_child(tools)
	var info := Button.new()
	info.text = "卡牌详情与羁绊"
	info.pressed.connect(show_details.bind(host))
	tools.add_child(info)
	var merge := Button.new()
	merge.name = "MergeCards"
	merge.text = "三张同名同星 → 升星（免费）"
	merge.disabled = host.state.phase != PreparationState.Phase.HOME
	merge.pressed.connect(func() -> void:
		var ids := Rules.merge_candidates(host.state, host.selected_instance_id)
		transact(host, func() -> Dictionary: return Rules.merge_cards(host.state, ids))
	)
	tools.add_child(merge)
	var back = host._item_button("移回技能池", Vector2(120, 48), null, "", {"area": "warehouse"})
	back.drop_handler = func(id: String, _target: Dictionary) -> void:
		transact(host, func() -> Dictionary: return PackingRules.move_to_warehouse(host.state, id))
	back.pressed.connect(func() -> void:
		transact(host, func() -> Dictionary: return PackingRules.move_to_warehouse(host.state, host.selected_instance_id))
	)
	tools.add_child(back)
	var scroll := ScrollContainer.new()
	scroll.name = "SchoolPool"
	scroll.custom_minimum_size.y = 250
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	panel.add_child(scroll)
	var groups := VBoxContainer.new()
	groups.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(groups)
	var pool := Rules.pool(host.state)
	for school in Rules.SCHOOLS:
		var layer := VBoxContainer.new()
		layer.name = school
		groups.add_child(layer)
		var title := Label.new()
		title.text = Rules.SCHOOLS[school]
		layer.add_child(title)
		var row := HFlowContainer.new()
		layer.add_child(row)
		for id in pool[school]:
			var item: ItemInstance = host.state.item(id)
			var card := Rules.definition(item.definition_id)
			var tile = host._item_button("%s\n%d星" % [card.name, item.star], Vector2(72, 144), ItemIconFactory.icon_for(item.definition_id, item.kind), id, {"area": "warehouse"})
			tile.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
			tile.vertical_icon_alignment = VERTICAL_ALIGNMENT_TOP
			tile.tooltip_text = Rules.details(item)
			tile.disabled = host.state.phase != PreparationState.Phase.HOME
			tile.pressed.connect(func() -> void:
				host.selected_instance_id = id
				host.status.text = "已选择%s · %d星，可放入任意已开放技能栏" % [card.name, item.star]
				host._refresh()
			)
			row.add_child(tile)
	scroll.set_meta("restore_scroll", scroll_position)
	_restore_scroll(scroll, scroll_position)

static func _restore_scroll(scroll: ScrollContainer, position: int) -> void:
	# New scroll bars receive their range during container layout.
	await scroll.get_tree().process_frame
	if is_instance_valid(scroll) and scroll.is_inside_tree():
		scroll.scroll_vertical = position
		scroll.remove_meta("restore_scroll")

static func show_details(host: Control) -> void:
	var dialog := AcceptDialog.new()
	dialog.title = "技能卡与流派羁绊"
	dialog.min_size = Vector2i(520, 480)
	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(500, 450)
	var text := Label.new()
	text.custom_minimum_size.x = 470
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	var item: ItemInstance = host.state.item(host.selected_instance_id)
	text.text = Rules.details(item) + "\n\n" if item != null and item.kind == ItemInstance.Kind.CARD else ""
	text.text += "羁绊按不同卡名计数，双流派分别计入；各档累加。\n"
	for tier in Rules.synergy(host.state):
		text.text += "\n%s %d/%d · %s\n%s\n" % [Rules.SCHOOLS.get(tier.schoolId, tier.schoolId), int(tier.count), int(tier.tier), "已激活" if tier.active else "未激活", Rules.effect_text(tier.effects)]
	scroll.add_child(text)
	dialog.add_child(scroll)
	host.add_child(dialog)
	dialog.confirmed.connect(dialog.queue_free)
	dialog.close_requested.connect(dialog.queue_free)
	dialog.popup_centered()
