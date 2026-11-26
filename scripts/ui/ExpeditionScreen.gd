extends Control

const JourneyState = preload("res://scripts/journey/JourneyState.gd")
const JourneyRules = preload("res://scripts/journey/JourneyRules.gd")
const ChestRules = preload("res://scripts/journey/ChestRules.gd")
const ReliefRules = preload("res://scripts/journey/ReliefRules.gd")
const CorePageLayout = preload("res://scripts/ui/CorePageLayout.gd")
const ItemIconFactory = preload("res://scripts/ui/ItemIconFactory.gd")
const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")
const AndroidItems = preload("res://scripts/content/AndroidEquipmentCatalog.gd")
const CLOSED_CHEST = preload("res://assets/android/art/chests/closed-v1.png")
const OPEN_CHEST = preload("res://assets/android/art/chests/open-v1.png")
const EVENT_ART := {
	"moonwell_echo": ["moonwell_echo.resonate", "moonwell_echo.draw_echo", "moonwell_echo.leave"],
	"broken_span": ["ropeway_bridge.repair", "ropeway_bridge.cross", "ropeway_bridge.leave"],
	"frozen_altar": ["frozen_shrine.pray", "frozen_shrine.attune", "frozen_shrine.leave"],
	"banner_ruins": ["war_banner.claim", "war_banner.blood_rite", "war_banner.leave"],
	"wandering_caravan": ["roving_caravan.buy", "roving_caravan.sell", "roving_caravan.depart"],
	"hunter_camp": ["hunter_camp.share_rations", "hunter_camp.barter", "hunter_camp.leave"],
	"alchemist_spill": ["alchemy_lab.distill", "alchemy_lab.break_seal", "alchemy_lab.leave"],
	"pact_altar": ["pact_altar.offer_medicine", "pact_altar.offer_blood", "pact_altar.refuse"],
}
const EVENT_BACKDROPS := {"mystic": "library", "travel": "blocker", "war": "arena", "trade": "depot",
	"ruin": "vault", "camp": "camp", "risk": "clinic", "nature": "rest"}
const EVENT_SCENES := {
	"abandoned_depot": "depot", "rusted_workshop": "workshop", "ruined_library": "library",
	"collapsed_storage": "storage", "safe_wreckage": "vault", "tech_terminal": "terminal",
	"field_clinic": "clinic", "blocker": "blocker", "frozen_altar": "vault",
	"banner_ruins": "blocker", "wandering_caravan": "depot", "broken_span": "storage",
	"pact_altar": "library", "alchemist_spill": "workshop", "hunter_camp": "camp",
	"moonwell_echo": "clinic",
}

@onready var node_list: HBoxContainer = $Scroll/Layout/NodeList
@onready var current: Label = %Current
@onready var resolve_button: Button = $Scroll/Layout/Resolve
@onready var event_choices: VBoxContainer = $Scroll/Layout/EventChoices
const InventoryShelf = preload("res://scripts/ui/InventoryShelf.gd")
@onready var loot_items: InventoryShelf = $Scroll/Layout/LootItems
@onready var collect_all_button: Button = $Scroll/Layout/LootActions/CollectAll
@onready var leave_button: Button = $Scroll/Layout/Leave
@onready var status: Label = %Status

var preparation: PreparationState
var journey: JourneyState
var session: Node
var extract_button: Button
var scouting_button: Button
var appraisal_art: TextureRect
var _just_revealed := {}
var packing_panel: VBoxContainer
var selected_loot := ""
var selected_rotation := false
var _packing_mode := "equipment"
var _route_overview := false
var _route_button: Button
var target_scroll: ScrollContainer
var target_content: VBoxContainer
var choosing_target := false
const TARGETS := [
	["补给宝箱", "恢复道具与成长材料", ["salve", "essence"]],
	["军械宝箱", "装备与合成组件", ["broadsword", "chainmail"]],
	["奥术宝箱", "技能卡", ["A1", "skill_shard"]],
	["背包宝箱", "随身物资与成长材料", ["essence", "iron_branch"]],
	["技能传承", "技能构筑与技能栏成长材料", []],
	["安全箱工程", "安全箱成长材料", []],
]

func _ready() -> void:
	session = _session()
	preparation = session.call("ensure_preparation_state") as PreparationState
	journey = session.call("ensure_journey_state") as JourneyState
	_route_overview = bool(session.get_meta("journey_route_overview", false))
	session.remove_meta("journey_route_overview")
	_route_button = Button.new()
	_route_button.custom_minimum_size.y = CorePageLayout.TOUCH_HEIGHT
	_route_button.add_theme_font_size_override("font_size", 26)
	CorePageLayout.paint_button(_route_button)
	_route_button.pressed.connect(func() -> void: show_route_overview(not _route_overview))
	$Scroll/Layout.add_child(_route_button)
	$Scroll/Layout.move_child(_route_button, 0)
	CorePageLayout.install(self, "expedition", "远征", "挑战进度与本局物资")
	$Scroll.offset_top = 90
	$Scroll.offset_bottom = -CorePageLayout.NAV_RESERVE
	$Scroll/Layout/Header.hide()
	$Scroll/Layout/RouteHint.hide()
	target_scroll = ScrollContainer.new()
	target_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(target_scroll)
	target_scroll.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	target_scroll.offset_left = 24
	target_scroll.offset_top = 96
	target_scroll.offset_right = -24
	target_scroll.offset_bottom = -CorePageLayout.NAV_RESERVE
	target_content = VBoxContainer.new()
	target_content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	target_content.add_theme_constant_override("separation", 16)
	target_scroll.add_child(target_content)
	choosing_target = preparation.phase == PreparationState.Phase.HOME
	_show_target_list()
	appraisal_art = TextureRect.new()
	appraisal_art.custom_minimum_size.y = 92
	appraisal_art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	appraisal_art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	$Scroll/Layout.add_child(appraisal_art)
	$Scroll/Layout.move_child(appraisal_art, loot_items.get_index())
	packing_panel = VBoxContainer.new()
	var packing_surface := PanelContainer.new()
	packing_surface.add_theme_stylebox_override("panel", CorePageLayout.frame(Color("09394c"), Color("30a7c7")))
	packing_surface.add_child(packing_panel)
	$Scroll/Layout.add_child(packing_surface)
	$Scroll/Layout.move_child(packing_surface, appraisal_art.get_index())
	scouting_button = Button.new()
	scouting_button.text = "查看敌情"
	scouting_button.custom_minimum_size.y = 54
	scouting_button.pressed.connect(_on_scout_pressed)
	$Scroll/Layout.add_child(scouting_button)
	$Scroll/Layout.move_child(scouting_button, resolve_button.get_index())
	extract_button = Button.new()
	extract_button.text = "安全撤离，带回物资"
	extract_button.custom_minimum_size.y = 56
	extract_button.pressed.connect(_on_extract_pressed)
	$Scroll/Layout.add_child(extract_button)
	_refresh()

func _process(delta: float) -> void:
	if journey == null or journey.reveal_at.is_empty(): return
	var pending := journey.reveal_at.keys()
	if JourneyRules.advance_appraisal(journey, delta * 1000.0):
		for id in pending:
			if not journey.reveal_at.has(id): _just_revealed[id] = true
		session.call("save_progress")
		_refresh()

func _session() -> Node:
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if session == null:
		session = preload("res://scripts/AppSession.gd").new()
		session.name = "AppSession"
		get_tree().root.add_child(session)
	return session

func _refresh() -> void:
	target_scroll.visible = choosing_target
	$Scroll.visible = not choosing_target
	_rebuild_nodes()
	_rebuild_event_choices()
	_rebuild_loot()
	$Scroll/Layout/LootCaption.visible = not journey.loot.is_empty()
	$Scroll/Layout/LootActions.visible = not journey.loot.is_empty()
	extract_button.visible = not journey.finished() and int(journey.current().type) == JourneyState.NodeType.REST
	scouting_button.visible = _can_scout()
	_route_button.visible = not choosing_target and not journey.finished()
	if journey.finished():
		current.text = {"death": "挑战失败，返回营地重新整备", "extracted": "已安全撤离"}.get(journey.ending, "远征完成")
		resolve_button.disabled = true
		leave_button.disabled = true
		return
	var node := journey.current()
	current.text = "%s · 第 %d / %d 节点 · %s" % [JourneyState.DIFFICULTY_NAMES[journey.difficulty], journey.current_node + 1, journey.nodes.size(), str(node.label)]
	current.text += "\n本局金币 %d" % journey.run_gold
	resolve_button.text = "选择事件" if int(node.type) == JourneyState.NodeType.EVENT else ("进入休息点" if int(node.type) == JourneyState.NodeType.REST else "进入战斗")
	resolve_button.disabled = journey.resolved
	leave_button.text = "免费休息并继续 · 恢复35%最大生命" if int(node.type) == JourneyState.NodeType.REST else "继续前进（未拾取掉落将丢弃）"
	collect_all_button.disabled = not journey.loot.any(func(item: ItemInstance) -> bool: return item.identified)
	leave_button.disabled = not journey.resolved or not journey.reveal_at.is_empty()
	if status.text.is_empty():
		status.text = "进入节点后先完成结算；背包放不下的物品会保留在地面。"
	_route_button.visible = not choosing_target
	_route_button.text = "查看当前节点物品" if _route_overview else "返回路线总览 · 保留地面物品"
	for control in [loot_items, $Scroll/Layout/LootCaption, $Scroll/Layout/LootActions, appraisal_art, packing_panel.get_parent()]:
		if _route_overview: control.hide()
	if int(node.type) != JourneyState.NodeType.REST:
		leave_button.text = "前往下一节点" if journey.loot.is_empty() else "前往下一节点 · 丢弃未拾取物品"

func show_route_overview(value: bool = true) -> void:
	_route_overview = value
	_refresh()
	($Scroll as ScrollContainer).scroll_vertical = 0

func _show_target_list() -> void:
	var previous_progress := preparation.quest_progress.duplicate(true)
	var previous_serial := preparation.progression_serial
	_clear(target_content)
	target_content.add_child(CorePageLayout.banner(preparation.active_hero_id, "选择目标", "先看线索，再配装\n准备好后进入挑战"))
	_target_label("选择目标宝箱", 30)
	_target_label("营地金币 %d" % preparation.gold, 21)
	var max_difficulty := 0
	for difficulty in 6:
		if QuestRules.locked_reason(preparation.quest_progress, difficulty).is_empty():
			max_difficulty = difficulty
	var carousel := ScrollContainer.new()
	carousel.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	carousel.custom_minimum_size.y = 250
	target_content.add_child(carousel)
	var strip := HBoxContainer.new()
	strip.add_theme_constant_override("separation", 12)
	carousel.add_child(strip)
	var clears := int(preparation.quest_progress.get("clears", 0))
	var count := maxi(max_difficulty + 1, 8 if clears >= 10 else 6 if clears >= 3 else 4)
	var generated := false
	var base_themes := ["supplies", "arsenal", "arcane", "backpack", "skills", "safe"]
	var themes: Array[String] = ["supplies"]
	for theme in preload("res://scripts/journey/MaterialProgression.gd").eligible(preparation, "", max_difficulty):
		if theme not in themes:
			themes.append(theme)
	for theme in base_themes:
		if theme not in themes:
			themes.append(theme)
	for index in count:
		var theme: String = themes[index % themes.size()]
		var template: Array = TARGETS[base_themes.find(theme)]
		var difficulty := index % (max_difficulty + 1)
		if theme == "safe" and max_difficulty >= 1:
			difficulty = maxi(1, difficulty)
		var quality: String = ["白", "蓝", "紫", "紫", "金", "金"][difficulty]
		var base: int = {"白": 120, "蓝": 220, "紫": 420, "金": 700}[quality]
		var multiplier: int = [2, 3, 4, 5, 6, 8][difficulty]
		var target := {"name": template[0], "hint": template[1], "rewards": template[2],
			"difficulty": difficulty, "quality": quality, "ticket": ceili(base * multiplier / 20.0) * 10}
		var batch := int(preparation.quest_progress.get("runs_started", 0))
		var offers: Dictionary = preparation.quest_progress.get("chest_offers", {})
		var key := "%d:%d" % [batch, index]
		if offers.has(key):
			target = offers[key]
		else:
			if not ChestRules.generate(preparation, target, theme):
				_target_label("当前宝箱缺少可用物品配置", 20)
				continue
			generated = true
			offers[key] = target
			preparation.quest_progress["chest_offers"] = offers
		var card := Button.new()
		card.custom_minimum_size = Vector2(244, 220)
		card.text = "%s\n%s · %s色\n%d 金币" % [target.name, JourneyState.DIFFICULTY_NAMES[int(target.difficulty)], target.quality, ReliefRules.entry_price(preparation, target)]
		card.icon = CLOSED_CHEST
		card.expand_icon = true
		card.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
		card.vertical_icon_alignment = VERTICAL_ALIGNMENT_TOP
		card.add_theme_constant_override("icon_max_width", 128)
		CorePageLayout.paint_button(card)
		card.pressed.connect(_show_target_detail.bind(target))
		strip.add_child(card)
	_target_label("点击宝箱查看线索与入场费用", 19)
	var now := int(Time.get_unix_time_from_system() * 1000)
	var relief := ReliefRules.status(preparation, now)
	_target_label("入场补助券 %d 张\n简单白箱自动抵扣至多120金币" % int(relief.vouchers), 19)
	var rescue := _target_button("领取破产救济 · 剩余 %d/2" % int(relief.charges), _claim_relief)
	rescue.name = "ClaimRelief"
	var unavailable := ReliefRules.unavailable(preparation)
	rescue.disabled = not unavailable.is_empty() or int(relief.charges) == 0
	_target_label(unavailable if not unavailable.is_empty() else "绑定阔剑、药膏×2、入场补助券×1", 19)
	if int(relief.next_ms) > 0:
		_target_label("下次救济约 %d 分钟后恢复" % ceili(float(relief.next_ms) / 60000.0), 19)
	if generated:
		var saved: Dictionary = session.call("save_progress")
		if not saved.accepted:
			preparation.quest_progress = previous_progress
			preparation.progression_serial = previous_serial
			for card in strip.get_children():
				(card as Button).disabled = true
			rescue.disabled = true
			_target_label(str(saved.reason), 20)
			var retry := _target_button("重试生成宝箱", _show_target_list)
			retry.name = "RetryChestBatch"

func _show_target_detail(target: Dictionary) -> void:
	_clear(target_content)
	_target_button("‹ 返回选择宝箱", _show_target_list)
	_target_label("%s · %s色" % [target.name, target.quality], 30)
	var art := TextureRect.new()
	art.texture = CLOSED_CHEST
	art.custom_minimum_size.y = 200
	art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	var chest_panel := PanelContainer.new()
	chest_panel.add_theme_stylebox_override("panel", CorePageLayout.frame(Color("09394c"), Color("ffe297")))
	target_content.add_child(chest_panel)
	chest_panel.add_child(art)
	_render_chest_clues(target)
	_target_label("%s · %s" % [JourneyState.DIFFICULTY_NAMES[int(target.difficulty)], target.hint], 21)
	var appraise := _target_button("随机鉴定 · %d 金币（同时提高入场价）" % ChestRules.appraisal_fee(target), _appraise_target.bind(target))
	appraise.disabled = preparation.gold < ChestRules.appraisal_fee(target) or target.items.all(func(entry: Dictionary) -> bool: return int(entry.reveal) == 2)
	var now := int(Time.get_unix_time_from_system() * 1000)
	var refresh := ChestRules.refresh_status(preparation, now)
	var cost := ChestRules.refresh_cost(preparation, target, now)
	var refresh_text := "更换宝箱 · 免费次数 %d/3" % int(refresh.charges) if cost == 0 else "更换宝箱 · %d 金币" % cost
	_target_button(refresh_text, _confirm_replace_target.bind(target))
	if int(refresh.next_ms) > 0:
		_target_label("下次免费刷新约 %d 分钟后恢复" % ceili(float(refresh.next_ms) / 60000.0), 19)
	var payable := ReliefRules.entry_price(preparation, target)
	_target_label("通关后奖励入库 · 入场 %d 金币" % payable, 21)
	if ReliefRules.subsidized(preparation, target):
		_target_label("使用入场补助券 · 抵扣 %d 金币" % (int(target.ticket) - payable), 19)
	var depart := _target_button("确认出战配置", _confirm_departure.bind(target))
	CorePageLayout.paint_button(depart, true)
	depart.disabled = preparation.gold < payable
	if depart.disabled:
		_target_label("营地金币不足", 20)

func _render_chest_clues(target: Dictionary) -> void:
	ChestRules.initialize(target)
	var grid := Control.new()
	grid.name = "ChestClues"
	var edge := minf(120.0, 600.0 / int(target.columns))
	grid.custom_minimum_size.y = int(target.rows) * edge
	target_content.add_child(grid)
	for entry in target.items:
		var definition := ContentCatalog.item(str(entry.id))
		if definition == null:
			continue
		var tile := Button.new()
		CorePageLayout.paint_button(tile)
		tile.tooltip_text = definition.display_name if int(entry.reveal) == 2 else "尚未完全鉴定"
		if int(entry.reveal) == 2:
			tile.icon = ItemIconFactory.icon_for(definition.id, ItemInstance.Kind.CARD if definition.source_data.get("kind") == "CARD" else ItemInstance.Kind.EQUIPMENT)
			tile.expand_icon = true
			tile.add_theme_constant_override("icon_max_width", 130)
		else:
			tile.text = str(definition.source_data.get("quality", "未知")) + "色线索" if int(entry.reveal) == 1 else "?"
		grid.add_child(tile)
		tile.position = Vector2(float(entry.x), float(entry.y)) * edge
		tile.size = Vector2(definition.footprint) * edge - Vector2(4, 4)

func _appraise_target(target: Dictionary) -> void:
	var original := target.duplicate(true)
	var previous_gold := preparation.gold
	var previous_seed: Variant = preparation.quest_progress.get("chest_seed")
	var result := ChestRules.appraise(preparation, target)
	if result.accepted:
		var saved: Dictionary = session.call("save_progress")
		if not saved.accepted:
			target.clear()
			target.merge(original, true)
			preparation.gold = previous_gold
			if previous_seed == null:
				preparation.quest_progress.erase("chest_seed")
			else:
				preparation.quest_progress["chest_seed"] = previous_seed
			result = saved
	_show_target_detail(target)
	_target_label(str(result.reason), 20)

func _confirm_replace_target(target: Dictionary) -> void:
	var dialog := ConfirmationDialog.new()
	dialog.title = "更换目标宝箱"
	dialog.dialog_text = "保留难度，重新生成品质、物品和门票。鉴定进度清空，已支付的鉴定费用不退。"
	dialog.ok_button_text = "确认更换"
	dialog.confirmed.connect(func() -> void:
		dialog.queue_free()
		var gold := preparation.gold
		var progress := preparation.quest_progress.duplicate(true)
		var result := ChestRules.replace(preparation, target, int(Time.get_unix_time_from_system() * 1000))
		if result.accepted:
			var saved: Dictionary = session.call("save_progress")
			if not saved.accepted:
				preparation.gold = gold
				preparation.quest_progress = progress
				_show_target_list()
				_target_label(str(saved.reason), 20)
				return
		_show_target_detail(target)
		_target_label(str(result.reason), 20)
	)
	dialog.canceled.connect(dialog.queue_free)
	add_child(dialog)
	dialog.popup_centered()

func _confirm_departure(target: Dictionary) -> void:
	var dialog := ConfirmationDialog.new()
	dialog.title = "确认出发"
	dialog.ok_button_text = "进入挑战"
	var hero := AndroidHeroCatalog.hero(preparation.active_hero_id)
	var effective_gear := preparation.equipment_slots.slice(0, PreparationState.ACTIVE_EQUIPMENT_SLOT_COUNT)
	var reserve_gear := preparation.equipment_slots.slice(PreparationState.ACTIVE_EQUIPMENT_SLOT_COUNT)
	dialog.dialog_text = "%s\n出战英雄  %s\n入场金币  %d\n生效装备 %d 件 · 备用 %d 件\n技能卡 %d 张" % [target.name,
		hero.get("display_name", preparation.active_hero_id), ReliefRules.entry_price(preparation, target),
		effective_gear.size() - effective_gear.count(""), reserve_gear.size() - reserve_gear.count(""),
		preparation.card_slots.size() - preparation.card_slots.count("")]
	if ReliefRules.subsidized(preparation, target):
		dialog.dialog_text += "\n本次使用入场补助券一张"
	var content := VBoxContainer.new()
	dialog.add_child(content)
	var summary := Label.new()
	summary.text = dialog.dialog_text
	content.add_child(summary)
	dialog.get_label().hide()
	var carry_label := Label.new()
	carry_label.text = "携入金币（从营地扣除，存入本局钱包）\n战败将丢失；安全撤离或通关后带回营地。"
	content.add_child(carry_label)
	var carry := SpinBox.new()
	carry.name = "CarryGold"
	carry.min_value = 0
	carry.max_value = maxi(0, preparation.gold - ReliefRules.entry_price(preparation, target))
	carry.step = 1
	carry.rounded = true
	content.add_child(carry)
	dialog.get_ok_button().disabled = preparation.gold < ReliefRules.entry_price(preparation, target)
	dialog.confirmed.connect(func() -> void:
		carry.apply()
		var result: Dictionary = session.call("enter_journey", int(target.difficulty), target, int(carry.value))
		dialog.queue_free()
		if result.accepted:
			journey = session.call("ensure_journey_state") as JourneyState
			choosing_target = false
			_refresh()
		else:
			_target_label(str(result.reason), 20)
	)
	dialog.canceled.connect(dialog.queue_free)
	add_child(dialog)
	dialog.popup_centered()

func _claim_relief() -> void:
	var result: Dictionary = session.call("claim_relief", int(Time.get_unix_time_from_system() * 1000))
	_show_target_list()
	_target_label(str(result.reason), 20)

func _target_label(value: String, size: int) -> void:
	var label := Label.new()
	label.text = value
	label.add_theme_font_size_override("font_size", size)
	target_content.add_child(label)

func _target_button(value: String, action: Callable) -> Button:
	var button := Button.new()
	CorePageLayout.paint_button(button)
	button.text = value
	button.custom_minimum_size.y = 64
	button.pressed.connect(action)
	target_content.add_child(button)
	return button

func _can_scout() -> bool:
	return not journey.finished() and not journey.resolved and not journey.battle_started \
		and int(journey.current().type) in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]

static func _visible_active_count(difficulty: int, count: int) -> int:
	match difficulty:
		0, 1: return count
		2: return (count + 1) >> 1
		3: return mini(1, count)
		_: return 0

static func _scouting_hint(difficulty: int, actives: Array, visible_count: int) -> String:
	if difficulty == 4:
		var roles: Array[String] = []
		for item in actives:
			for effect in AndroidItems.item(str(item.id)).get("effects", []):
				if bool(effect.get("requiresReview", false)) or effect.trigger not in ["ACTIVE", "LOW_HP"]:
					continue
				var role: String = {"APPLY_CONTROL": "控制", "IMMUNITY": "防护", "DISPEL": "防护",
					"GRANT_SHIELD": "防护", "ATTACK_PROTECTION": "防护", "LIFESTEAL": "续航"}.get(effect.kind, "强化")
				if role not in roles: roles.append(role)
		roles.sort()
		if not roles.is_empty(): return "仅探明用途：%s\n装备型号与数量未知" % "／".join(roles)
	if difficulty >= 4: return "侦察受限，装备型号与数量未知"
	if visible_count < actives.size(): return "另有 %d 件主动装备未识别" % (actives.size() - visible_count)
	return "未携带主动装备" if actives.is_empty() else "主动装备已识别"

func _on_scout_pressed() -> void:
	if not _can_scout(): return
	var enemy := BattleLoadoutBuilder.enemy_for_node(journey.seed, journey.current_node, preparation.active_hero_id, journey.difficulty)
	var dialog := AcceptDialog.new()
	dialog.title = "战前侦察"
	dialog.ok_button_text = "返回"
	var panel := VBoxContainer.new()
	dialog.add_child(panel)
	var portrait := TextureRect.new()
	portrait.texture = AndroidArtCatalog.hero_texture(str(enemy.fighter.hero_id))
	portrait.custom_minimum_size.y = 100
	portrait.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	portrait.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	panel.add_child(portrait)
	var identity := Label.new()
	identity.text = "%s · %s" % [enemy.display_name, enemy.skill.get("display_name", "")]
	for school in AndroidItems.metadata().get("schools", []):
		if school.id == enemy.school_id:
			identity.text += "\n流派 · " + str(school.name)
			break
	panel.add_child(identity)
	var actives: Array = enemy.active_items.duplicate()
	actives.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return str(a.id) < str(b.id))
	var visible_count := _visible_active_count(journey.difficulty, actives.size())
	for index in visible_count:
		var item: Dictionary = actives[index]
		var row := Button.new()
		row.text = str(item.display_name)
		row.icon = ItemIconFactory.icon_for(str(item.id), ItemInstance.Kind.EQUIPMENT)
		row.add_theme_constant_override("icon_max_width", 44)
		row.custom_minimum_size.y = 50
		panel.add_child(row)
		var definition := ContentCatalog.item(str(item.id))
		var detail := Label.new()
		detail.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		detail.text = str(definition.source_data.get("description", "")) if definition != null else ""
		detail.visible = journey.difficulty == 0 and not detail.text.is_empty()
		row.pressed.connect(func() -> void: detail.visible = not detail.visible)
		panel.add_child(detail)
	var hint := Label.new()
	hint.text = _scouting_hint(journey.difficulty, actives, visible_count)
	panel.add_child(hint)
	add_child(dialog)
	dialog.confirmed.connect(dialog.queue_free)
	dialog.close_requested.connect(dialog.queue_free)
	dialog.popup_centered(Vector2i(490, 250))

func _rebuild_nodes() -> void:
	_clear(node_list)
	var round_number := 0
	for index in journey.nodes.size():
		var node: Dictionary = journey.nodes[index]
		if int(node.type) not in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
			continue
		round_number += 1
		var next_fight := index + 1
		while next_fight < journey.nodes.size() and int(journey.nodes[next_fight].type) not in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
			next_fight += 1
		var active := journey.current_node >= index and journey.current_node < next_fight
		var chip := Label.new()
		chip.custom_minimum_size = Vector2(0, 60)
		chip.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		chip.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		chip.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		chip.text = "第 %d 战" % round_number
		chip.add_theme_font_size_override("font_size", 18)
		chip.add_theme_color_override("font_color", Color(1, 0.8, 0.3) if active else Color(0.55, 0.72, 0.78))
		node_list.add_child(chip)

func _rebuild_event_choices() -> void:
	_clear(event_choices)
	if int(journey.current().get("type", -1)) != JourneyState.NodeType.EVENT:
		return
	if journey.frozen_event_id.is_empty():
		return
	var definition := ContentCatalog.event(journey.frozen_event_id)
	var title := Label.new()
	title.text = definition.display_name if definition != null else "沿途事件"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	title.add_theme_font_size_override("font_size", 25)
	title.add_theme_color_override("font_color", Color("e9bc66"))
	event_choices.add_child(title)
	if journey.resolved:
		_render_event_result(definition.category if definition != null else "")
		return
	var scene_path := "res://assets/android/art/scenes/%s-v1.jpg" % EVENT_SCENES.get(journey.frozen_event_id, EVENT_BACKDROPS.get(definition.category if definition != null else "", "camp"))
	if ResourceLoader.exists(scene_path):
		var scene := TextureRect.new()
		scene.name = "EventSceneArt"
		scene.texture = load(scene_path) as Texture2D
		scene.custom_minimum_size.y = 174
		scene.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		scene.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		event_choices.add_child(scene)
	for index in journey.frozen_event_options.size():
		var option := journey.frozen_event_options[index]
		var button := Button.new()
		button.custom_minimum_size = Vector2(0, 72)
		button.text = str(option.label)
		button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		button.alignment = HORIZONTAL_ALIGNMENT_LEFT
		var frame := StyleBoxFlat.new()
		frame.bg_color = Color("0c667e")
		frame.border_color = Color("30a7c7")
		frame.set_border_width_all(2)
		frame.set_corner_radius_all(8)
		frame.content_margin_left = 20
		frame.content_margin_right = 20
		button.add_theme_stylebox_override("normal", frame)
		button.add_theme_stylebox_override("hover", frame)
		button.add_theme_stylebox_override("disabled", frame)
		var price := JourneyRules.event_option_price(journey, option)
		if price > 0:
			button.text += " · %d 本局金币" % price
		button.tooltip_text = JourneyRules.event_option_block(preparation, journey, option)
		button.disabled = not button.tooltip_text.is_empty()
		if button.disabled:
			button.text += "（%s）" % button.tooltip_text
		button.pressed.connect(_on_event_choice_pressed.bind(index))
		event_choices.add_child(button)

func _render_event_result(category: String) -> void:
	var result := journey.event_result
	if result.is_empty(): return
	var paths: Array = EVENT_ART.get(journey.frozen_event_id, [])
	var index: int = {"material": 0, "rare_loot": 1, "leave": 2}.get(result.get("outcome"), 2)
	var art_key := str(result.get("art_key", paths[index] if not paths.is_empty() else ""))
	var path := "res://assets/android/art/events/v0.1.5/%s.webp" % art_key
	if not ResourceLoader.exists(path):
		path = "res://assets/android/art/scenes/%s-v1.jpg" % EVENT_SCENES.get(journey.frozen_event_id, EVENT_BACKDROPS.get(category, "camp"))
	var art := TextureRect.new()
	art.name = "EventResultArt"
	art.texture = load(path) as Texture2D
	art.custom_minimum_size.y = 160
	art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	event_choices.add_child(art)
	var summary := Label.new()
	summary.name = "EventResultSummary"
	summary.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	summary.text = "%s · 战利品 %d 件 · 本局金币 +%d" % [result.get("choice", ""), int(result.get("items", 0)), int(result.get("gold", 0))]
	if result.has("gold_cost"):
		summary.text += " / −%d" % int(result.gold_cost)
	if result.has("hp_change_percent"):
		summary.text += "\n生命值 %+d%% 最大生命值" % int(result.hp_change_percent)
	if result.has("poison_damage"):
		summary.text += "（含中毒 %d 点）" % int(result.poison_damage)
	summary.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	event_choices.add_child(summary)

func _rebuild_loot() -> void:
	_rebuild_packing()
	_clear(loot_items)
	loot_items.reset()
	loot_items.visible = packing_panel.visible
	var floor_target := InventoryDragButton.new()
	floor_target.flat = true
	floor_target.tooltip_text = "放回地面"
	floor_target.drop_handler = _put_down_item
	floor_target.pressed.connect(func() -> void: _put_down_item(selected_loot, {}))
	loot_items.add_child(floor_target)
	floor_target.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	appraisal_art.visible = not journey.loot.is_empty()
	appraisal_art.texture = CLOSED_CHEST if not journey.reveal_at.is_empty() else OPEN_CHEST
	for item in journey.loot:
		var button := InventoryDragButton.new()
		var span: Vector2i = item.footprint
		if item.id == selected_loot and selected_rotation:
			span = Vector2i(span.y, span.x)
		if not item.identified:
			button.text = "鉴定中…"
			button.disabled = true
			loot_items.append_tile(button, span)
			continue
		button.icon = ItemIconFactory.icon_for(item.definition_id, item.kind)
		button.expand_icon = true
		button.add_theme_constant_override("icon_max_width", 56)
		var definition := ContentCatalog.item(item.definition_id)
		var display_name := definition.display_name if definition != null else "未知掉落"
		var quality := str(definition.source_data.get("quality", "")) if definition != null else ""
		var accent: Color = {"蓝": Color("70c8fb"), "紫": Color("c2a1f7"), "金": Color("f4ca79")}.get(quality, Color("b2bdc9"))
		button.add_theme_color_override("font_color", accent)
		var frame := StyleBoxFlat.new()
		frame.bg_color = Color("142635")
		frame.border_color = accent
		frame.set_border_width_all(2)
		frame.set_corner_radius_all(10)
		frame.content_margin_left = 10
		frame.content_margin_right = 10
		button.add_theme_stylebox_override("normal", frame)
		button.text = ""
		button.instance_id = item.id
		button.tooltip_text = display_name
		button.pressed.connect(_on_loot_pressed.bind(item.id))
		loot_items.append_tile(button, span)
		if _just_revealed.erase(item.id):
			_play_appraisal_reveal(button, accent, quality)

func _put_down_item(item_id: String, _target: Dictionary) -> void:
	if item_id.is_empty():
		return
	var result := JourneyRules.put_down(preparation, journey, item_id)
	status.text = str(result.reason)
	if result.accepted:
		selected_loot = ""
		session.call("save_progress")
		_refresh()

func _play_appraisal_reveal(button: Button, accent: Color, quality: String) -> void:
	var glow := ColorRect.new()
	glow.mouse_filter = Control.MOUSE_FILTER_IGNORE
	glow.color = Color(accent, 0.52 if quality in ["紫", "金"] else 0.30)
	button.add_child(glow)
	glow.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var tween := glow.create_tween()
	tween.tween_property(glow, "modulate:a", 0.0, 0.34).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
	tween.tween_callback(glow.queue_free)
	if quality not in ["紫", "金"]: return
	for index in (8 if quality == "金" else 5):
		var spark := ColorRect.new()
		spark.mouse_filter = Control.MOUSE_FILTER_IGNORE
		spark.color = accent
		spark.rotation = PI * 0.25
		button.add_child(spark)
		spark.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
		var travel := Vector2.from_angle(TAU * index / (8.0 if quality == "金" else 5.0)) * Vector2(68, 29)
		var burst := spark.create_tween().set_parallel()
		burst.tween_method(func(progress: float) -> void:
			var offset := travel * progress
			spark.offset_left = offset.x - 2.5
			spark.offset_right = offset.x + 2.5
			spark.offset_top = offset.y - 2.5
			spark.offset_bottom = offset.y + 2.5
		, 0.0, 1.0, 0.42).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
		burst.tween_property(spark, "modulate:a", 0.0, 0.42)
		burst.chain().tween_callback(spark.queue_free)

func _on_resolve_pressed() -> void:
	if not journey.finished() and int(journey.current().type) in [JourneyState.NodeType.BATTLE, JourneyState.NodeType.BOSS]:
		var battle_start := session.call("begin_journey_battle") as Dictionary
		if bool(battle_start.accepted):
			ScreenRouter.open(get_tree(), "battle")
			return
		status.text = str(battle_start.reason)
		_refresh()
		return
	var result := JourneyRules.resolve_current(preparation, journey)
	status.text = str(result.reason)
	session.call("save_progress")
	_refresh()

func _on_event_choice_pressed(index: int) -> void:
	var option := journey.frozen_event_options[index]
	var requirement := str(option.get("item_choice", ""))
	if not requirement.is_empty():
		var dialog := AcceptDialog.new()
		dialog.title = "选择交付物品"
		dialog.ok_button_text = "取消"
		var list := VBoxContainer.new()
		dialog.add_child(list)
		for item in preparation.items.values():
			if not JourneyRules.event_deliverable(item, requirement):
				continue
			var button := Button.new()
			var definition := ContentCatalog.item(item.definition_id)
			button.text = definition.display_name if definition != null else item.definition_id
			button.icon = ItemIconFactory.icon_for(item.definition_id, item.kind)
			button.add_theme_constant_override("icon_max_width", 40)
			button.pressed.connect(_settle_event_choice.bind(index, item.id, dialog))
			list.add_child(button)
		if list.get_child_count() == 0:
			var empty := Label.new()
			empty.text = "背包中没有符合条件的物品"
			list.add_child(empty)
		add_child(dialog)
		dialog.confirmed.connect(dialog.queue_free)
		dialog.close_requested.connect(dialog.queue_free)
		dialog.popup_centered(Vector2i(420, 280))
		return
	_settle_event_choice(index)

func _settle_event_choice(index: int, item_id: String = "", dialog: AcceptDialog = null) -> void:
	if dialog != null:
		dialog.queue_free()
	var result := JourneyRules.choose_event(preparation, journey, index, item_id)
	status.text = str(result.reason)
	session.call("save_progress")
	_refresh()

func _on_loot_pressed(item_id: String) -> void:
	selected_loot = item_id
	selected_rotation = false
	status.text = "已选中物品，点击格子放置。"
	_rebuild_packing()
	_focus_packing.call_deferred()

func _focus_packing() -> void:
	await get_tree().process_frame
	var selection := packing_panel.get_node_or_null("SelectedItem") as Control
	if selection != null: ($Scroll as ScrollContainer).ensure_control_visible(selection)

func _rebuild_packing() -> void:
	_clear(packing_panel)
	packing_panel.visible = journey.resolved and not journey.finished() and (not journey.loot.is_empty() or journey.drop_serial > 0 and int(journey.current().type) != JourneyState.NodeType.REST)
	packing_panel.get_parent().visible = packing_panel.visible
	if not packing_panel.visible:
		return
	var tabs := HBoxContainer.new()
	packing_panel.add_child(tabs)
	for mode in ["equipment", "bags"]:
		var tab := Button.new()
		tab.text = "即时换装" if mode == "equipment" else "背包与安全箱"
		tab.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		tab.custom_minimum_size.y = CorePageLayout.TOUCH_HEIGHT
		tab.add_theme_font_size_override("font_size", 26)
		CorePageLayout.paint_button(tab, mode == _packing_mode)
		tab.pressed.connect(func() -> void:
			_packing_mode = mode
			_rebuild_packing()
		)
		tabs.add_child(tab)
	var tools := HBoxContainer.new()
	packing_panel.add_child(tools)
	var selected := preparation.item(selected_loot)
	if selected == null:
		for item in journey.loot:
			if item.id == selected_loot:
				selected = item
				break
	if selected == null:
		selected_loot = ""
	var selection := Label.new()
	selection.name = "SelectedItem"
	selection.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	selection.add_theme_font_size_override("font_size", 26)
	selection.text = "先点选地面或背包中的物品" if selected == null else "已选择：" + ContentCatalog.item(selected.definition_id).display_name
	packing_panel.add_child(selection)
	var rotate := Button.new()
	var dimensions := Vector2i.ZERO if selected == null else selected.footprint
	if selected_rotation:
		dimensions = Vector2i(dimensions.y, dimensions.x)
	rotate.text = "旋转 90°"
	rotate.disabled = selected == null or dimensions.x == dimensions.y
	rotate.custom_minimum_size.y = 80
	rotate.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	rotate.add_theme_font_size_override("font_size", 24)
	rotate.pressed.connect(func() -> void:
		selected_rotation = not selected_rotation
		_rebuild_packing()
	)
	tools.add_child(rotate)
	var cancel := Button.new()
	cancel.text = "取消选择"
	cancel.disabled = selected == null
	cancel.custom_minimum_size.y = 80
	cancel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cancel.add_theme_font_size_override("font_size", 24)
	cancel.pressed.connect(func() -> void:
		selected_loot = ""
		_rebuild_packing()
	)
	tools.add_child(cancel)
	var equipment_title := Label.new()
	equipment_title.text = "选择物品后点装备槽：上6格生效，下3格备用"
	equipment_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	equipment_title.add_theme_font_size_override("font_size", 24)
	packing_panel.add_child(equipment_title)
	equipment_title.visible = _packing_mode == "equipment"
	var equipment := GridContainer.new()
	equipment.name = "JourneyEquipment"
	equipment.columns = 3
	equipment.add_theme_constant_override("h_separation", 8)
	equipment.add_theme_constant_override("v_separation", 8)
	packing_panel.add_child(equipment)
	equipment.visible = _packing_mode == "equipment"
	for slot in preparation.equipment_slots.size():
		var equipped := preparation.item(preparation.equipment_slots[slot])
		var button := Button.new()
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.custom_minimum_size.y = CorePageLayout.TOUCH_HEIGHT
		button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		button.add_theme_font_size_override("font_size", 22)
		button.text = ("生效%d" % (slot + 1) if slot < 6 else "备用%d" % (slot - 5)) + (" · 空位" if equipped == null else " · " + ContentCatalog.item(equipped.definition_id).display_name)
		if equipped != null:
			button.icon = ItemIconFactory.icon_for(equipped.definition_id, equipped.kind)
			button.expand_icon = true
			button.add_theme_constant_override("icon_max_width", 40)
		CorePageLayout.paint_button(button)
		button.disabled = selected == null or (selected.kind != ItemInstance.Kind.EQUIPMENT and selected.definition_id not in ["faerie_fire", "cheese"])
		button.pressed.connect(func() -> void:
			var result: Dictionary = session.equip_journey_item(selected_loot, slot)
			status.text = str(result.reason)
			if result.accepted: selected_loot = ""
			_refresh()
		)
		equipment.add_child(button)
	if selected != null and selected.definition_id in ["salve", "tango", "faerie_fire", "cheese"] and preparation.item(selected.id) != null:
		var use := Button.new()
		use.text = "使用恢复品"
		use.pressed.connect(func() -> void:
			var result := JourneyRules.use_consumable(preparation, journey, selected_loot)
			status.text = str(result.reason)
			if result.accepted:
				selected_loot = ""
				session.call("save_progress")
				_refresh()
		)
		tools.add_child(use)
	if _packing_mode == "bags":
		_build_packing_grid("随身背包", ItemInstance.Location.BACKPACK, preparation.backpack_size)
		_build_packing_grid("安全箱", ItemInstance.Location.SAFE, preparation.safe_size)

func _build_packing_grid(title: String, location: ItemInstance.Location, dimensions: Vector2i) -> void:
	var caption := Label.new()
	caption.text = "%s · %d × %d" % [title, dimensions.x, dimensions.y]
	packing_panel.add_child(caption)
	var grid := Control.new()
	var edge := minf(104.0, floorf(620.0 / maxi(dimensions.x, 1)))
	grid.custom_minimum_size = Vector2(0, dimensions.y * edge)
	packing_panel.add_child(grid)
	for y in dimensions.y:
		for x in dimensions.x:
			_add_packing_tile(grid, location, Vector2i(x, y), Vector2i.ONE, edge)
	for item in preparation.items.values():
		if item.location != location:
			continue
		var size: Vector2i = Vector2i(item.footprint.y, item.footprint.x) if item.rotated else item.footprint
		_add_packing_tile(grid, location, item.grid_position, size, edge, item)

func _add_packing_tile(grid: Control, location: ItemInstance.Location, cell: Vector2i, span: Vector2i, edge: float, item: ItemInstance = null) -> void:
	var tile := InventoryDragButton.new()
	tile.drop_target = {"location": location, "position": cell}
	tile.drop_handler = _place_reward_item
	tile.drop_cell_size = edge
	if item != null:
		tile.instance_id = item.id
		tile.icon = ItemIconFactory.icon_for(item.definition_id, item.kind)
		tile.expand_icon = true
		tile.tooltip_text = ContentCatalog.item(item.definition_id).display_name if ContentCatalog.item(item.definition_id) != null else item.definition_id
		if item.id == selected_loot:
			tile.modulate = Color("ffca3f")
	tile.pressed.connect(_on_packing_tile_pressed.bind(tile))
	grid.add_child(tile)
	tile.position = Vector2(cell) * edge
	tile.size = Vector2(span) * edge - Vector2(4, 4)

func _on_packing_tile_pressed(tile: InventoryDragButton) -> void:
	var target := tile.drop_target
	var cell: Vector2i = target.position
	var pointer := tile.get_local_mouse_position()
	if Rect2(Vector2.ZERO, tile.size).has_point(pointer):
		cell += Vector2i((pointer / tile.drop_cell_size).floor())
	if not selected_loot.is_empty():
		_place_reward_item(selected_loot, {"location": target.location, "position": cell})
	elif not tile.instance_id.is_empty():
		selected_loot = tile.instance_id
		selected_rotation = preparation.item(selected_loot).rotated
		_rebuild_packing()

func _place_reward_item(item_id: String, target: Dictionary) -> void:
	var item := preparation.item(item_id)
	var rotated := selected_rotation if item_id == selected_loot else item != null and item.rotated
	var location := int(target.location) as ItemInstance.Location
	var result := JourneyRules.collect_one(preparation, journey, item_id, target.position, rotated, location) if item == null else PackingRules.place(preparation, item_id, location, target.position, rotated)
	status.text = str(result.reason)
	if result.accepted:
		status.text = "物品已放置。"
		selected_loot = ""
		session.call("save_progress")
	_refresh()

func _on_collect_all_pressed() -> void:
	var result := JourneyRules.collect_all(preparation, journey)
	status.text = "已放入 %d 件；剩余 %d 件。" % [int(result.collected), int(result.remaining)]
	session.call("save_progress")
	_refresh()

func _on_leave_pressed() -> void:
	if not journey.loot.is_empty():
		var dialog := ConfirmationDialog.new()
		dialog.title = "确认前往下一节点"
		dialog.dialog_text = "还有%d件地面物品未拾取。继续前进会丢弃它们；仅返回路线总览不会丢弃。" % journey.loot.size()
		dialog.ok_button_text = "丢弃并继续"
		dialog.confirmed.connect(func() -> void:
			dialog.queue_free()
			_advance_from_node()
		)
		dialog.canceled.connect(dialog.queue_free)
		add_child(dialog)
		dialog.popup_centered()
		return
	_advance_from_node()

func _advance_from_node() -> void:
	var result: Dictionary = session.call("leave_journey_node")
	if not result.accepted:
		status.text = str(result.reason)
		return
	selected_loot = ""
	status.text = "未拾取掉落已丢弃。" if bool(result.discarded) else "已返回远征路线。"
	session.call("save_progress")
	_refresh()

func _on_extract_pressed() -> void:
	var result: Dictionary = session.call("extract_journey")
	status.text = str(result.reason)
	_refresh()

func _on_back_pressed() -> void:
	session.call("save_progress")
	ScreenRouter.open(get_tree(), "home")

func _node_icon(type: int) -> String:
	match type:
		JourneyState.NodeType.BATTLE: return "战斗"
		JourneyState.NodeType.EVENT: return "事件"
		JourneyState.NodeType.CHEST: return "宝箱"
		JourneyState.NodeType.REST: return "休息"
		_: return "首领"

func _clear(container: Control) -> void:
	for child in container.get_children():
		container.remove_child(child)
		child.queue_free()
