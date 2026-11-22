extends RefCounted

const Catalog = preload("res://scripts/story/StoryCatalog.gd")
const Rules = preload("res://scripts/story/StoryRules.gd")
const Player = preload("res://scripts/story/StoryPlayer.gd")
const ThemeArt = preload("res://scripts/ui/CorePageLayout.gd")

static func open(host: Control, session: Node) -> void:
	var state: PreparationState = session.call("ensure_preparation_state")
	var dialog := AcceptDialog.new()
	dialog.title = "无尽纷争 · 剧情回顾"
	dialog.ok_button_text = "返回"
	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(560, 680)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	dialog.add_child(scroll)
	var rows := VBoxContainer.new()
	rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(rows)
	for chapter in Catalog.data().get("chapters", []):
		if str(chapter.id).begins_with("R"):
			continue
		_add(rows, host, session, dialog, chapter, Rules.can_replay(state, str(chapter.id)))
	for ending in Catalog.data().get("endings", []):
		_add(rows, host, session, dialog, ending, Rules.can_replay(state, str(ending.id)))
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	host.add_child(dialog)
	dialog.popup_centered(Vector2i(mini(640, int(host.size.x) - 24), mini(880, int(host.size.y) - 48)))

static func _add(rows: VBoxContainer, host: Control, session: Node, dialog: AcceptDialog, entry: Dictionary, unlocked: bool) -> void:
	var button := Button.new()
	button.text = "%s · %s" % [entry.id, entry.title] if unlocked else "%s · 尚未解锁" % entry.id
	button.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	button.custom_minimum_size.y = 68
	button.disabled = not unlocked
	ThemeArt.paint_button(button)
	rows.add_child(button)
	button.pressed.connect(func() -> void:
		dialog.queue_free()
		Player.show_chapter(host, session, str(entry.id))
	)
