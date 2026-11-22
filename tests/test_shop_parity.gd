extends SceneTree

const RulesScript = preload("res://scripts/prepare/ShopRules.gd")
const State = preload("res://scripts/prepare/ShopState.gd")
const Content = preload("res://scripts/content/AndroidContent.gd")
var Rules: RefCounted = RulesScript.new()
var failures := 0

func check(ok: bool, message: String) -> void:
	if not ok:
		failures += 1
		push_error(message)

func _init() -> void:
	# Source oracle: Android ShopShelfSelector's Lehmer trace, seed=1729.
	# Behavioral tests intentionally use call() so absent APIs fail as assertions.
	if not Rules.has_method("recover"):
		check(false, "Android persistent shop recovery is missing")
		quit(1)
		return
	var state := PreparationState.new("juggernaut")
	state.gold = 100000
	var shop := State.new()
	check(Rules.call("recover", state, shop, 1000).accepted, "initial recovery")
	var rotating := []
	var staples := 0
	for offer in shop.offers:
		var definition := Content.item(str(offer.definition_id))
		check(int(definition.minimumShopClears) == 0, "locked offer excluded")
		check(int(offer.stock) == int(definition.shopStock), "definition stock retained")
		check(int(offer.price) == int(floor(float(definition.price) * 0.9 + 0.5)), "stage zero locked price")
		if offer.shelf == "ROTATING": rotating.append(offer.id)
		if offer.shelf == "STAPLE": staples += 1
	check(staples == 51, "all 51 starter staples, not legacy six")
	check(rotating == ["C1", "C2", "D7", "R1", "U1", "bracer"], "exact seeded rotating trace")
	var original: Dictionary = state.quest_progress.shop.duplicate(true)
	check(Rules.call("recover", state, shop, 2000).accepted and shop.revision == 1, "reopening keeps batch")
	var quote: int = shop.revision
	check(Rules.call("buy", state, shop, "iron_branch", quote, 2000).accepted, "buy current quote")
	check(state.gold == 99982, "locked price 18 deducted")
	check(Rules.call("remaining", shop, "iron_branch") == 2, "stock decremented")
	check(Rules.call("buy", state, shop, "iron_branch", quote, 2000).accepted, "second separate instance")
	check(state.items.size() == 2, "no stack overwrite")
	check(Rules.call("buy", state, shop, "iron_branch", quote, 2000).accepted, "third purchase")
	check(not Rules.call("buy", state, shop, "iron_branch", quote, 2000).accepted, "sold out")
	var gold_before: int = state.gold
	check(not Rules.call("buy", state, shop, "iron_branch", quote, 7200000).accepted, "automatic boundary rejects stale quote")
	check(state.gold == gold_before and shop.revision == 2, "stale purchase recovers without spending")
	check(Rules.call("remaining", shop, "iron_branch") == 3, "auto replenishes")
	check(Rules.call("refresh_shop", state, shop, 7300000).accepted, "paid refresh")
	check(state.gold == gold_before - 50 and shop.revision == 3, "50 gold source cost")
	check(int(state.quest_progress.shop.cycle) == 1, "paid does not move automatic boundary")
	check(Rules.call("countdown", shop, 7200000) == 7100000, "clock rollback clamped")
	var restored: PreparationState = SaveCodec.decode(SaveCodec.encode(state, JourneyState.new(7001))).preparation
	var reopened := State.new()
	Rules.call("recover", restored, reopened, 7300000)
	check(reopened.to_data() == shop.to_data(), "save codec roundtrip preserves entire shop")
	state.phase = PreparationState.Phase.JOURNEY
	var before: Dictionary = shop.to_data()
	check(not Rules.call("refresh_shop", state, shop, 99999999).accepted, "refresh home guard")
	check(not Rules.call("buy", state, shop, "salve", shop.revision, 99999999).accepted, "buy home guard")
	check(shop.to_data() == before, "away actions preserve shop")
	state.phase = PreparationState.Phase.HOME
	state.gold = 0
	check(not Rules.call("refresh_shop", state, shop, 7300000).accepted, "insufficient refresh funds")
	check(shop.to_data() == before, "failed refresh leaves snapshot unchanged")
	# A free automatic refresh takes precedence, even with an empty wallet.
	check(Rules.call("refresh_shop", state, shop, 14400000).accepted, "auto wins over paid")
	check(state.gold == 0 and shop.revision == 4, "auto exactly one batch")
	# High progress exposes all shelves and source item types over deterministic batches.
	state.quest_progress.clears = 3
	state.gold = 1000000
	var kinds := {}
	for batch in range(32):
		Rules.call("refresh_shop", state, shop, 14400000)
		for offer in shop.offers:
			var definition := Content.item(str(offer.definition_id))
			var kind := str(definition.kind)
			if kinds.has(kind): continue
			var result: Dictionary = Rules.call("buy", state, shop, str(offer.id), shop.revision, 14400000)
			check(result.accepted, "purchase source type " + kind)
			if result.accepted:
				check(state.item(result.item_id).kind == Content.item_kind(definition), "correct instance type " + kind)
				check(state.item(result.item_id).bound == bool(definition.boundOnAcquire), "binding retained")
				kinds[kind] = true
	check(kinds.has("CARD") and kinds.has("SCROLL") and kinds.has("CONSUMABLE") and kinds.has("EQUIPMENT"), "all buyable types exercised")
	check(Rules.call("candidates").size() == 190, "full merged Android pool: 190 buyable definitions")
	var prices := PreparationState.new("juggernaut")
	prices.gold = 100000
	var demand_shop := State.new()
	Rules.call("recover", prices, demand_shop, 0)
	for index in 3: Rules.call("buy", prices, demand_shop, "iron_branch", demand_shop.revision, 0)
	Rules.call("refresh_shop", prices, demand_shop, 0)
	var branch: Dictionary = Rules.call("_offer_by_id", demand_shop, "iron_branch")
	check(int(branch.price) == 20 and demand_shop.purchases.is_empty(), "previous batch demand locks next price then resets")
	Rules.call("refresh_shop", prices, demand_shop, 0)
	branch = Rules.call("_offer_by_id", demand_shop, "iron_branch")
	check(int(branch.price) == 18, "demand is not cumulative across empty batches")
	var copies := prices.items.size()
	Rules.call("buy", prices, demand_shop, "salve", demand_shop.revision, 0)
	Rules.call("buy", prices, demand_shop, "salve", demand_shop.revision, 0)
	check(prices.items.size() == copies + 2, "stackable salves are separate owned instances")
	check(Rules.call("remaining", demand_shop, "salve") == 1, "separate consumables consume two stock")
	var snapshot := demand_shop.to_data()
	var wallet := prices.gold
	check(not Rules.call("buy", prices, demand_shop, "sun_thread", demand_shop.revision, 0).accepted, "zero-stock progression material cannot be bought")
	check(demand_shop.to_data() == snapshot and prices.gold == wallet, "unlisted purchase is inert")
	prices.gold = 0
	check(not Rules.call("buy", prices, demand_shop, "salve", demand_shop.revision, 0).accepted, "insufficient purchase funds")
	check(demand_shop.to_data() == snapshot, "insufficient purchase preserves stock")
	var rev := demand_shop.revision
	Rules.call("recover", prices, demand_shop, 100 * 7200000)
	check(demand_shop.revision == rev + 1 and demand_shop.cycle == 100, "offline skips generate exactly one batch")
	check(Rules.call("countdown", demand_shop, 100 * 7200000) == 7200000, "exact boundary countdown is full interval")
	# Independent instances with identical progress/time/seed must match byte-for-byte.
	var twin := PreparationState.new("juggernaut")
	var twin_shop := State.new()
	var first := State.new()
	var another := PreparationState.new("juggernaut")
	Rules.call("recover", twin, twin_shop, 1000)
	Rules.call("recover", another, first, 1000)
	check(JSON.stringify(twin_shop.to_data()) == JSON.stringify(first.to_data()), "deterministic full snapshot")
	check(not original.is_empty(), "shop stored in quest_progress")
	print("Shop parity failures: ", failures)
	quit(1 if failures else 0)
