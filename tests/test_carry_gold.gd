extends SceneTree

const Session = preload("res://scripts/AppSession.gd")
const Save = preload("res://scripts/save/SaveRepository.gd")
const Rules = preload("res://scripts/journey/JourneyRules.gd")
const PATH := "user://test_carry_gold.json"
const TARGET := {"name": "carry test", "difficulty": 0, "quality": "白", "ticket": 160}

func fresh(gold: int, vouchers: int = 0) -> Node:
	var session := Session.new()
	session.preparation_state = PreparationState.new("juggernaut")
	session.preparation_state.gold = gold
	session.preparation_state.quest_progress["relief"] = {"vouchers": vouchers}
	session.save_path = PATH
	return session

func check(value: bool, message: String) -> void:
	if not value:
		push_error(message)
		quit(1)
		assert(value, message)

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	Save.erase(PATH)
	var session := fresh(260)
	check(session.call("enter_journey", 0, TARGET, 100).accepted, "sufficient funds enter")
	check(session.preparation_state.gold == 0 and session.journey_state.run_gold == 100, "ticket and carry split")
	var before := SaveCodec.encode(session.preparation_state, session.journey_state)
	check(not session.call("enter_journey", 0, TARGET, 1).accepted, "existing run rejects carry")
	check(SaveCodec.encode(session.preparation_state, session.journey_state) == before, "existing run unchanged")
	check(session.enter_journey(0, TARGET).accepted, "legacy resume")
	var restored := Session.new()
	restored.save_path = PATH
	check(restored.restore_progress().accepted, "restore carry save")
	check(restored.preparation_state.gold == 0 and restored.journey_state.run_gold == 100, "restore both wallets")
	Rules.bank_run_gold(restored.preparation_state, restored.journey_state)
	Rules.bank_run_gold(restored.preparation_state, restored.journey_state)
	check(restored.preparation_state.gold == 100 and restored.journey_state.run_gold == 0, "bank exactly once")
	check(Rules.begin_battle(session.journey_state).accepted, "begin death test")
	Rules.complete_battle(session.preparation_state, session.journey_state, false)
	check(session.preparation_state.gold == 0 and session.journey_state.run_gold == 0, "death loses carry")
	session.free()
	restored.free()
	for amount in [-1, 101, 9223372036854775807]:
		session = fresh(260)
		before = SaveCodec.encode(session.preparation_state, session.ensure_journey_state())
		check(not session.call("enter_journey", 0, TARGET, amount).accepted, "reject negative/insufficient/overflow carry %d" % amount)
		check(SaveCodec.encode(session.preparation_state, session.journey_state) == before, "rejected entry unchanged")
		session.free()
	session = fresh(140, 1)
	check(session.call("enter_journey", 0, TARGET, 100).accepted, "coupon discounts before carry check")
	check(session.preparation_state.gold == 0 and session.journey_state.run_gold == 100, "coupon wallet split")
	check(session.preparation_state.quest_progress.relief.vouchers == 0, "consume one coupon")
	session.free()
	session = fresh(139, 1)
	check(not session.call("enter_journey", 0, TARGET, 100).accepted, "coupon still requires combined funds")
	check(session.preparation_state.quest_progress.relief.vouchers == 1, "failed funds preserves coupon")
	session.free()
	session = fresh(140, 1)
	var old = session.ensure_journey_state()
	old.run_gold = 17
	before = SaveCodec.encode(session.preparation_state, old)
	session.save_path = "user://missing-carry-gold-directory/save.json"
	check(not session.call("enter_journey", 0, TARGET, 100).accepted, "save failure rejected")
	check(session.journey_state == old and SaveCodec.encode(session.preparation_state, old) == before, "rollback restores coupon, wallets, phase, progression and old journey")
	session.free()
	session = fresh(160)
	check(session.enter_journey(0, TARGET).accepted and session.journey_state.run_gold == 0, "two argument entry defaults zero")
	session.free()
	for ending in ["extract", "clear"]:
		session = fresh(260)
		check(session.call("enter_journey", 0, TARGET, 100).accepted, "enter settlement test")
		if ending == "extract":
			session.journey_state.current_node = 5
			check(Rules.extract(session.preparation_state, session.journey_state).accepted, "safe extraction")
		else:
			session.journey_state.current_node = 8
			session.journey_state.resolved = true
			check(session.leave_journey_node().accepted, "clear final node")
		check(session.preparation_state.gold == 100 and session.journey_state.run_gold == 0, "settlement returns carry: " + ending)
		check(session.preparation_state.phase == PreparationState.Phase.HOME, "settlement returns home")
		session.free()
	var live := root.get_node("AppSession")
	live.save_path = PATH
	live.preparation_state = PreparationState.new("juggernaut")
	live.preparation_state.gold = 140
	live.preparation_state.quest_progress["relief"] = {"vouchers": 1}
	var screen := preload("res://scenes/Expedition.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	screen._confirm_departure(TARGET)
	var dialog: ConfirmationDialog = screen.get_children().filter(func(node: Node) -> bool: return node is ConfirmationDialog).back()
	var carry := dialog.find_child("CarryGold", true, false) as SpinBox
	check(carry != null and carry.min_value == 0 and carry.max_value == 100 and carry.step == 1, "UI clamps carry to discounted remainder")
	carry.value = 101
	check(carry.value == 100, "UI prevents excess carry")
	carry.get_line_edit().text = "100"
	dialog.confirmed.emit()
	check(live.preparation_state.gold == 0 and live.journey_state.run_gold == 100, "UI passes selected carry into run wallet")
	screen.queue_free()
	await process_frame
	Save.erase(PATH)
	print("Carry gold: entry, validation, coupon, existing run, rollback, restore, banking and death passed")
	quit()
