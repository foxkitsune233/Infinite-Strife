extends SceneTree

const Shop = preload("res://scripts/prepare/ShopRules.gd")
const Chests = preload("res://scripts/journey/ChestRules.gd")
const Relief = preload("res://scripts/journey/ReliefRules.gd")
const INTERVAL := 7200000
var failures := 0

func check(ok: bool, label: String) -> void:
	if not ok:
		failures += 1
		push_error(label)

func _init() -> void:
	# Android HomeEconomyRules.recover: max(now, shopSeen, refreshAnchor, reliefAnchor).
	# Each anchor must work independently, even without a corresponding seen field.
	for source in ["chest_refresh", "relief"]:
		var state := PreparationState.new("juggernaut")
		state.gold = 1000
		var shop := ShopState.new()
		Shop.recover(state, shop, 1000)
		var quote := shop.revision
		state.quest_progress[source] = {"anchor": INTERVAL}
		var result := Shop.buy(state, shop, "iron_branch", quote, 1000)
		check(not result.accepted, source + " future anchor rejects old quote after wall-clock rollback")
		check(shop.revision == quote + 1 and shop.cycle == 1, source + " advances exactly one batch")
		check(shop.seen_ms == INTERVAL, source + " persists the common maximum time")
		check(state.gold == 1000 and state.items.is_empty(), source + " stale quote does not charge or grant items")
		check(Shop.remaining(shop, "iron_branch") == 3, source + " rejected purchase retains full new stock")
		check(Shop.countdown(shop, 1000) == INTERVAL, source + " countdown stays at shared observed time")
		# Repeated recovery at the rolled-back wall time cannot refresh the same grid twice.
		Shop.recover(state, shop, 1000)
		check(shop.revision == quote + 1, source + " repeated recovery is idempotent")
		var restored: PreparationState = SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(1))).preparation
		var reopened := ShopState.new()
		Shop.recover(restored, reopened, 1000)
		check(reopened.seen_ms == INTERVAL and reopened.revision == shop.revision, source + " shared clock survives save roundtrip")
	# Godot's existing counters also persist max-seen values between charge recoveries.
	for source in ["chest_refresh", "relief"]:
		var state := PreparationState.new("juggernaut")
		state.quest_progress[source] = {"seen": INTERVAL + 1234, "anchor": 1000}
		var shop := ShopState.new()
		Shop.recover(state, shop, 1000)
		check(shop.seen_ms == INTERVAL + 1234, source + " observed time cannot move backward")
	# Reverse direction: previously observed shop time must recover both charge pools.
	var state := PreparationState.new("juggernaut")
	state.quest_progress["shop"] = {"seen_ms": INTERVAL}
	state.quest_progress["chest_refresh"] = {"charges": 0, "anchor": 0, "seen": 0}
	state.quest_progress["relief"] = {"charges": 0, "anchor": 0, "seen": 0, "claims": 4, "vouchers": 1}
	var before := state.quest_progress.duplicate(true)
	var chest_status := Chests.refresh_status(state, 1000)
	var relief_status := Relief.status(state, 1000)
	check(int(chest_status.seen) == INTERVAL and int(chest_status.charges) == 3, "shop time recovers all three chest charges")
	check(int(relief_status.seen) == INTERVAL and int(relief_status.charges) == 1, "shop time recovers one relief charge")
	check(int(relief_status.next_ms) == INTERVAL and int(chest_status.next_ms) == 0, "shared charge countdowns")
	check(int(relief_status.claims) == 4 and int(relief_status.vouchers) == 1, "recovery preserves claims and vouchers")
	check(state.quest_progress == before, "status queries remain pure")
	state.phase = PreparationState.Phase.JOURNEY
	check(not Shop.recover(state, ShopState.new(), 2 * INTERVAL).accepted, "home guard preserved")
	check(state.quest_progress == before, "away recovery cannot advance clocks")
	print("Shared shop clock failures: ", failures)
	quit(1 if failures else 0)
