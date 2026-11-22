extends Control

const CraftingRules = preload("res://scripts/prepare/CraftingRules.gd")
const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")
const ItemIconFactory = preload("res://scripts/ui/ItemIconFactory.gd")
const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")

@onready var recipes: GridContainer = $Layout/Scroll/Recipes
@onready var status: Label = %Status

var state: PreparationState

func _ready() -> void:
	state = _session_state()
	_refresh()

func _session_state() -> PreparationState:
	var session: Node = get_tree().root.get_node_or_null("AppSession")
	if session == null:
		session = preload("res://scripts/AppSession.gd").new()
		session.name = "AppSession"
		get_tree().root.add_child(session)
	return session.call("ensure_preparation_state") as PreparationState

func _refresh() -> void:
	for child in recipes.get_children():
		child.queue_free()
	for entry in CraftingRules.entries(state):
		var definition := ContentCatalog.item(str(entry.definition_id))
		var button := Button.new()
		button.custom_minimum_size = Vector2(310, 126)
		button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		button.icon = ItemIconFactory.icon_for(definition.id, ItemInstance.Kind.EQUIPMENT)
		button.text = _entry_text(entry)
		button.disabled = not bool(entry.craftable)
		button.pressed.connect(_on_recipe_pressed.bind(str(entry.definition_id)))
		recipes.add_child(button)
	status.text = "优先处理可合成项目；锁定材料需要回整备页解锁。"

func _entry_text(entry: Dictionary) -> String:
	if bool(entry.craftable):
		return "%s\n可合成" % str(entry.display_name)
	if not (entry.locked as Array).is_empty():
		return "%s\n材料已锁定" % str(entry.display_name)
	return "%s\n缺少材料" % str(entry.display_name)

func _on_recipe_pressed(definition_id: String) -> void:
	var result := CraftingRules.craft(state, definition_id)
	if result.accepted:
		get_tree().root.get_node("AppSession").call("save_progress")
	status.text = "合成完成：产物已放入局外仓库。" if result.accepted else str(result.reason)
	_refresh()

func _on_back_pressed() -> void:
	ScreenRouter.open(get_tree(), "preparation")
