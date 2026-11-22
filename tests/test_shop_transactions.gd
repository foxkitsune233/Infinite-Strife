extends SceneTree

const Session = preload("res://scripts/AppSession.gd")
const Rules = preload("res://scripts/prepare/ShopRules.gd")
var failures := 0

func check(ok: bool, label: String) -> void:
	if not ok:
		failures += 1
		push_error(label)

func _init() -> void:
	call_deferred("run")

func run() -> void:
	var session := root.get_node("AppSession")
	session.save_path = "res://tests/.shop-transaction-save.json"
	SaveRepository.erase(session.save_path)
	session.preparation_state = PreparationState.new("juggernaut")
	session.preparation_state.gold = 100000
	session.journey_state = JourneyState.new(7001)
	var screen := preload("res://scenes/Shop.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	var state: PreparationState = session.preparation_state
	check(state.quest_progress.has("shop"), "screen persists initial shelf")
	if not state.quest_progress.has("shop"):
		screen.free()
		quit(1)
		return
	var shop: ShopState = screen.shop
	var before := SaveCodec.encode(state, session.journey_state)
	var shelf := shop.to_data()
	# An existing ordinary file cannot be used as a parent directory.
	session.save_path = "res://tests/.shop-transaction-save.json/impossible.json"
	screen._on_offer_pressed("iron_branch", shop.revision)
	check(SaveCodec.encode(state, session.journey_state) == before, "buy failure rolls back gold/items/progress/shop")
	check(shop.to_data() == shelf, "buy failure restores UI stock/serial")
	check(screen.status.text.contains("保存") and not screen.status.text.contains("购买完成"), "failed save reports failure")
	screen._on_refresh_pressed()
	check(SaveCodec.encode(state, session.journey_state) == before, "paid refresh failure restores wallet/seed/revision/clock")
	check(shop.to_data() == shelf, "paid refresh UI rollback")
	var auto_result: Dictionary = screen._commit(Rules.recover.bind(state, shop, shop.seen_ms + 7200000))
	check(not auto_result.accepted, "automatic refresh save failure is surfaced")
	check(shop.to_data() == shelf and SaveCodec.encode(state, session.journey_state) == before, "automatic refresh rolls back seed/stock/clock")
	session.save_path = "res://tests/.shop-transaction-save.json"
	screen._on_offer_pressed("iron_branch", shop.revision)
	check(state.gold == int(before.preparation.gold) - 18, "retry commits exactly one charge")
	check(state.items.size() == 1 and Rules.remaining(shop, "iron_branch") == 2, "retry creates exactly one instance")
	var loaded := SaveRepository.read(session.save_path)
	check(loaded.accepted and JSON.parse_string(JSON.stringify(loaded.preparation.quest_progress.shop)) == JSON.parse_string(JSON.stringify(state.quest_progress.shop)), "durable stock matches memory")
	var revision := shop.revision
	var seed_value := shop.next_seed
	screen.free()
	check(session.restore_progress().accepted, "restore before reopening")
	screen = preload("res://scenes/Shop.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	check(screen.shop.revision == revision and screen.shop.next_seed == seed_value, "reopening does not reroll")
	check(Rules.remaining(screen.shop, "iron_branch") == 2, "reopening does not replenish")
	check(screen.get_node("Layout/Refresh").text.contains("50"), "refresh cost visible")
	check(screen.get_node("Layout/Hint").text.contains("珍品"), "shelf availability visible")
	check(screen.get_node("Layout/Gold").text.contains("自动"), "countdown visible")
	screen.free()
	SaveRepository.erase(session.save_path)
	print("Shop transaction failures: ", failures)
	quit(1 if failures else 0)
