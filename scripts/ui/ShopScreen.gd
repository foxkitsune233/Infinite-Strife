extends Control
## Shop operations stage in memory and commit through existing AppSession.save_progress.
## Failed commits restore the same state object, including quest counters and shop RNG.

const ShopState = preload("res://scripts/prepare/ShopState.gd")
const ShopRules = preload("res://scripts/prepare/ShopRules.gd")
const Content = preload("res://scripts/content/AndroidContent.gd")
const ItemIconFactory = preload("res://scripts/ui/ItemIconFactory.gd")
const ThemeArt = preload("res://scripts/ui/CorePageLayout.gd")
const SHELVES := {"STAPLE": "常备", "ROTATING": "轮换", "RARE": "珍品"}
const TYPES := {"EQUIPMENT": "装备", "CARD": "卡牌", "SCROLL": "卷轴", "CONSUMABLE": "消耗品", "MATERIAL": "材料", "LOOT": "战利品"}

@onready var gold: Label = %Gold
@onready var offers: GridContainer = $Layout/Scroll/Offers
@onready var status: Label = %Status
var state: PreparationState
var shop: ShopState
var _session: Node
var _shelf_filter := ""
var _type_filter := ""
var _timer: Timer

func _ready() -> void:
	state = _session_state()
	shop = ShopState.new()
	if state.quest_progress.has("shop"): shop.load_data(state.quest_progress.shop)
	ThemeArt.install(self, "shop", "营地补给", "常备 · 轮换 · 珍品")
	var background := TextureRect.new()
	background.texture = ThemeArt.CAMP
	background.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	background.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	background.mouse_filter = Control.MOUSE_FILTER_IGNORE
	background.modulate = Color(0.32, 0.32, 0.32)
	add_child(background)
	move_child(background, 1)
	background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	$Layout.offset_top = 104
	$Layout.offset_bottom = -96
	$Layout/Header/Title.text = "物资货架"
	$Layout/Hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	ThemeArt.paint_button($Layout/Header/Back)
	ThemeArt.paint_button($Layout/Refresh, true)
	var filters := HBoxContainer.new()
	filters.name = "ShopFilters"
	$Layout.add_child(filters)
	$Layout.move_child(filters, $Layout/Scroll.get_index())
	var shelf_picker := OptionButton.new()
	shelf_picker.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	shelf_picker.custom_minimum_size.y = 52
	shelf_picker.add_item("全部货架")
	for label in SHELVES.values(): shelf_picker.add_item(label)
	shelf_picker.item_selected.connect(func(index: int) -> void:
		_shelf_filter = "" if index == 0 else str(SHELVES.keys()[index - 1])
		_refresh()
	)
	filters.add_child(shelf_picker)
	var type_picker := OptionButton.new()
	type_picker.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	type_picker.add_item("全部类型")
	for label in TYPES.values(): type_picker.add_item(label)
	type_picker.item_selected.connect(func(index: int) -> void:
		_type_filter = "" if index == 0 else str(TYPES.keys()[index - 1])
		_refresh()
	)
	filters.add_child(type_picker)
	_apply_layout()
	resized.connect(_apply_layout)
	var result := _commit(ShopRules.recover.bind(state, shop, _now_ms()))
	status.text = "购买后进入局外仓库；每次购买一件，消耗品分别存放。" if result.accepted else str(result.reason)
	_refresh()
	_timer = Timer.new()
	_timer.wait_time = 1.0
	_timer.timeout.connect(_tick)
	add_child(_timer)
	_timer.start()

func _now_ms() -> int:
	return int(Time.get_unix_time_from_system() * 1000)

func _apply_layout() -> void:
	var compact := size.x < 600
	# Compact pages already have the shared title and bottom 整备 navigation.
	$Layout/Header.visible = not compact
	offers.columns = 1 if compact else 2
	$Layout.offset_top = 100 if compact else 104
	$Layout.offset_bottom = -78 if compact else -96
	$Layout.offset_left = 12 if compact else 26
	$Layout.offset_right = -12 if compact else -26
	$Layout.add_theme_constant_override("separation", 8 if compact else 14)
	$Layout/Header/Title.add_theme_font_size_override("font_size", 22 if compact else 32)
	$Layout/Header/Back.custom_minimum_size = Vector2(120 if compact else 170, 44 if compact else 54)
	$Layout/Refresh.custom_minimum_size.y = 44 if compact else 50
	$Layout/Status.custom_minimum_size.y = 30 if compact else 52
	$Layout/Hint.add_theme_font_size_override("font_size", 16 if compact else 18)
	$Layout/Gold.add_theme_font_size_override("font_size", 18 if compact else 21)
	$Layout/Status.add_theme_font_size_override("font_size", 16 if compact else 17)
	$PageHeader.add_theme_font_size_override("font_size", 18 if compact else 24)
	$PageHeader.offset_bottom = 66 if compact else 86
	$PageNavigation.offset_top = -66 if compact else -78
	for button in $PageNavigation.get_children(): button.custom_minimum_size.y = 52 if compact else 64
	for picker in $Layout/ShopFilters.get_children():
		picker.custom_minimum_size.y = 44 if compact else 52
		picker.add_theme_font_size_override("font_size", 18 if compact else 20)

func _session_state() -> PreparationState:
	_session = get_tree().root.get_node_or_null("AppSession")
	if _session == null:
		_session = preload("res://scripts/AppSession.gd").new()
		_session.name = "AppSession"
		get_tree().root.add_child(_session)
	return _session.call("ensure_preparation_state") as PreparationState

func _commit(action: Callable) -> Dictionary:
	var previous_gold := state.gold
	var previous_items := state.items.duplicate()
	var previous_progress := state.quest_progress.duplicate(true)
	var previous_serial := state.progression_serial
	var previous_shop := shop.to_data()
	var result: Dictionary = action.call()
	# Failed purchases may still recover an automatically refreshed shelf, as on Android.
	var changed := previous_progress != state.quest_progress or previous_gold != state.gold or previous_items != state.items
	if not changed: return result
	var saved: Dictionary = _session.call("save_progress")
	if not saved.accepted:
		state.gold = previous_gold
		state.items = previous_items
		state.quest_progress = previous_progress
		state.progression_serial = previous_serial
		shop.load_data(previous_shop)
		return {"accepted": false, "reason": "保存失败：%s；本次变更已撤回，请重试。" % str(saved.reason), "revision": shop.revision}
	return result

func _clock_text() -> void:
	var now := _now_ms()
	var seconds := ceili(float(ShopRules.countdown(shop, now)) / 1000.0)
	@warning_ignore("integer_division")
	var hours: int = seconds / 3600
	@warning_ignore("integer_division")
	var minutes: int = (seconds % 3600) / 60
	gold.text = "金币 %d · 货架 #%d\n自动刷新 %02d:%02d:%02d" % [state.gold, shop.revision, hours, minutes, seconds % 60]
	var cost := ShopRules.refresh_cost(shop, now)
	$Layout/Refresh.text = "刷新货架 · %d 金币" % cost if cost > 0 else "自动换货 · 免费"
	$Layout/Refresh.disabled = state.phase != PreparationState.Phase.HOME or state.gold < cost

func _tick() -> void:
	if state.phase == PreparationState.Phase.HOME and (shop.revision == 0 or ShopRules.refresh_cost(shop, _now_ms()) == 0):
		var result := _commit(ShopRules.recover.bind(state, shop, _now_ms()))
		status.text = "货架已自动刷新。" if result.accepted else str(result.reason)
		_refresh()
	else:
		_clock_text()

func _refresh() -> void:
	_clock_text()
	# Minimum width follows the real viewport instead of forcing a horizontal scrollbar.
	offers.columns = 1 if size.x < 600 else 2
	for child in offers.get_children(): child.free()
	var counts := {"STAPLE": 0, "ROTATING": 0, "RARE": 0}
	for offer in shop.offers:
		counts[str(offer.shelf)] += 1
		var definition := Content.item(str(offer.definition_id))
		if not _shelf_filter.is_empty() and offer.shelf != _shelf_filter: continue
		if not _type_filter.is_empty() and definition.kind != _type_filter: continue
		var button := Button.new()
		button.custom_minimum_size = Vector2(0, 150)
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		button.add_theme_font_size_override("font_size", 22)
		button.icon = ItemIconFactory.icon_for(str(offer.definition_id), Content.item_kind(definition))
		button.expand_icon = true
		button.add_theme_constant_override("icon_max_width", 64)
		var remaining := ShopRules.remaining(shop, str(offer.id))
		button.text = "【%s · %s】%s\n%d 金币 · 库存 %d/%d\n%s" % [SHELVES[str(offer.shelf)], TYPES[str(definition.kind)], str(offer.display_name), int(offer.price), remaining, int(offer.stock), "购买" if remaining > 0 else "已售完"]
		button.tooltip_text = str(definition.get("description", ""))
		button.disabled = state.phase != PreparationState.Phase.HOME or state.gold < int(offer.price) or remaining <= 0
		ThemeArt.paint_button(button)
		button.pressed.connect(_on_offer_pressed.bind(str(offer.id), shop.revision))
		offers.add_child(button)
	var locked := 0
	for definition in ShopRules.candidates():
		if int(definition.minimumShopClears) > int(state.quest_progress.get("clears", 0)): locked += 1
	$Layout/Hint.text = "常备 %d · 轮换 %d/6 · 珍品 %s\n%d 件待通关开放；刷新补货。" % [counts.STAPLE, counts.ROTATING, "本轮无货" if counts.RARE == 0 else "有货", locked]

func _on_offer_pressed(offer_id: String, quoted_revision: int) -> void:
	var result := _commit(ShopRules.buy.bind(state, shop, offer_id, quoted_revision, _now_ms()))
	status.text = "购买完成，物品已放入局外仓库。" if result.accepted else str(result.reason)
	_refresh()

func _on_refresh_pressed() -> void:
	var result := _commit(ShopRules.refresh_shop.bind(state, shop, _now_ms()))
	status.text = "货架已刷新；此前报价已失效。" if result.accepted else str(result.reason)
	_refresh()

func _on_back_pressed() -> void:
	ScreenRouter.open(get_tree(), "preparation")
