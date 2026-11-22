class_name ItemDetailSheet
extends RefCounted
## 独立物品详情弹层 + 旋转预览。对齐 Android InventoryActivity.description / 点按预览。

const PackingRules = preload("res://scripts/prepare/PackingRules.gd")
const CorePageLayout = preload("res://scripts/ui/CorePageLayout.gd")
const ItemIconFactory = preload("res://scripts/ui/ItemIconFactory.gd")

const STAT_LABELS := {
	"strength": "力量", "agility": "敏捷", "intelligence": "智力", "health": "生命",
	"healthRegen": "每秒回血", "armor": "护甲", "attackSpeed": "攻速",
	"manaPercent": "魔法上限%", "manaRegen": "每秒回蓝", "attack": "攻击",
}

## rotate_handler 仅为兼容旧调用方保留，已不再调用：预览纯视觉，绝不改动物品数据。
static func show(parent: Node, state: PreparationState, instance_id: String, rotate_handler: Callable = Callable()) -> AcceptDialog:
	var instance := state.item(instance_id)
	var dialog := AcceptDialog.new()
	dialog.title = "物品详情"
	dialog.ok_button_text = "关闭"
	dialog.add_theme_stylebox_override("panel", CorePageLayout.frame(Color("09394c"), Color("30a7c7")))
	CorePageLayout.paint_button(dialog.get_ok_button(), true)
	dialog.get_ok_button().custom_minimum_size.y = 56
	dialog.add_theme_color_override("title_color", Color("ffe297"))
	var body := VBoxContainer.new()
	body.add_theme_constant_override("separation", 10)
	dialog.add_child(body)
	if instance == null:
		var missing := Label.new()
		missing.text = "物品不存在"
		body.add_child(missing)
		parent.add_child(dialog)
		dialog.popup_centered(Vector2i(440, 360))
		return dialog
	var definition := preload("res://scripts/content/AndroidEquipmentCatalog.gd").item(instance.definition_id)
	var name_label := Label.new()
	name_label.text = str(definition.get("name", instance.definition_id))
	name_label.add_theme_font_size_override("font_size", 26)
	name_label.add_theme_color_override("font_color", Color("ffca3f"))
	body.add_child(name_label)
	# 图标 + 尺寸 + 旋转预览
	var preview := HBoxContainer.new()
	preview.alignment = BoxContainer.ALIGNMENT_CENTER
	body.add_child(preview)
	var icon := TextureRect.new()
	icon.texture = ItemIconFactory.icon_for(instance.definition_id, instance.kind)
	icon.custom_minimum_size = Vector2(96, 96)
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	preview.add_child(icon)
	var info := VBoxContainer.new()
	preview.add_child(info)
	var quality := str(definition.get("quality", ""))
	var dims := _dimensions(instance)
	# 预览只在弹层内翻转显示，不写回 state：任何位置的物品（含仓库）都能安全预览。
	# 用数组持有状态：GDScript lambda 对局部 bool 的捕获不稳定，容器引用可靠。
	var preview_state := [false]
	var base_info := Label.new()
	base_info.text = _size_text(quality, dims, instance.rotated, preview_state[0])
	base_info.add_theme_color_override("font_color", Color("fff4cc"))
	info.add_child(base_info)
	var sell := Label.new()
	sell.text = "出售回收：%d 金币%s" % [PackingRules.sale_value(instance), "（绑定，不可出售）" if instance.bound else ""]
	sell.add_theme_font_size_override("font_size", 14)
	info.add_child(sell)
	if instance.kind == ItemInstance.Kind.CARD:
		var star := Label.new()
		star.text = "%d 星 · 三张同名同星可升星" % instance.star
		star.add_theme_font_size_override("font_size", 14)
		info.add_child(star)
	# 旋转预览：纯视觉翻转尺寸与图标，不改动物品数据，也不受"仅背包/安全箱可旋转"限制。
	# 正方形物品旋转后尺寸不变，没有预览意义，故不显示按钮。
	if dims.x != dims.y:
		var rotate_button := Button.new()
		rotate_button.name = "RotatePreview"
		rotate_button.text = "旋转预览（%d×%d → %d×%d）" % [dims.x, dims.y, dims.y, dims.x]
		CorePageLayout.paint_button(rotate_button)
		rotate_button.pressed.connect(func() -> void:
			preview_state[0] = not preview_state[0]
			var shown := Vector2i(dims.y, dims.x) if preview_state[0] else dims
			base_info.text = _size_text(quality, dims, instance.rotated, preview_state[0])
			icon.rotation = PI / 2.0 if preview_state[0] else 0.0
			rotate_button.text = "旋转预览（当前 %d×%d → %d×%d）" % [shown.x, shown.y, shown.y, shown.x]
		)
		body.add_child(rotate_button)
	# 属性
	var stats: Dictionary = definition.get("stats", {})
	for key in STAT_LABELS.keys():
		var value: float = float(stats.get(key, 0.0))
		if is_zero_approx(value):
			continue
		var stat_label := Label.new()
		stat_label.text = "%s +%s" % [STAT_LABELS[key], _format_number(value)]
		body.add_child(stat_label)
	var description := str(definition.get("description", ""))
	if not description.is_empty():
		var desc := Label.new()
		desc.text = description
		desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		desc.add_theme_color_override("font_color", Color("b4dbe1"))
		body.add_child(desc)
	parent.add_child(dialog)
	dialog.popup_centered(Vector2i(460, 480))
	return dialog

static func _dimensions(instance: ItemInstance) -> Vector2i:
	return Vector2i(instance.footprint.y, instance.footprint.x) if instance.rotated else instance.footprint

## 显示用尺寸文本：实际旋转与预览翻转做异或，标注是否处于预览态。
static func _size_text(quality: String, actual_dims: Vector2i, actually_rotated: bool, preview_rotated: bool) -> String:
	var shown := Vector2i(actual_dims.y, actual_dims.x) if preview_rotated else actual_dims
	var rotated := actually_rotated != preview_rotated
	var suffix := ""
	if preview_rotated:
		suffix = "（预览旋转）"
	elif actually_rotated:
		suffix = "（已旋转）"
	return "%s · %d×%d格%s" % [quality, shown.x, shown.y, suffix]

static func _format_number(value: float) -> String:
	if is_equal_approx(value, floorf(value)):
		return str(int(value))
	return "%.1f" % value
