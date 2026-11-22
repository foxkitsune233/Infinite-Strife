extends SceneTree

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const JourneyState = preload("res://scripts/journey/JourneyState.gd")
const JourneyRules = preload("res://scripts/journey/JourneyRules.gd")
const AndroidItems = preload("res://scripts/content/AndroidEquipmentCatalog.gd")

var _failed := false

func _init() -> void:
	var preparation := PreparationState.new("juggernaut")
	var journey := JourneyState.new(7001)
	_assert_equal(journey.nodes.size(), 9, "Android journey has five battles, three events and a rest node")
	_assert_equal(JourneyRules.resolve_current(preparation, journey).accepted, true, "first battle resolves")
	_assert_equal(journey.loot.size() > 0, true, "battle creates ground loot")
	var ground_count := journey.loot.size()
	_assert_equal((journey.loot[0] as ItemInstance).identified, false, "loot starts concealed")
	_assert_equal(JourneyRules.collect_all(preparation, journey).collected, 0, "unidentified loot cannot be collected")
	_assert_equal(JourneyRules.leave_node(journey).accepted, false, "pending appraisal prevents leaving")
	JourneyRules.advance_appraisal(journey, 100)
	var restored: JourneyState = SaveCodec.decode(SaveCodec.encode(preparation, journey)).journey
	_assert_equal(restored.appraisal_ms, 100.0, "elapsed appraisal survives saving")
	_assert_equal(restored.loot[0].identified, false, "restoring does not reveal pending loot")
	_assert_equal(JourneyRules.advance_appraisal(restored, 10000), true, "restored deadlines still reveal loot")
	JourneyRules.advance_appraisal(journey, 10000)
	_assert_equal(JourneyRules.collect_all(preparation, journey).collected, ground_count, "collect all picks every fitting drop")
	_assert_equal(JourneyRules.leave_node(journey).accepted, true, "leave advances after collection")
	_assert_equal(journey.current_node, 1, "journey advances to frozen event")
	journey.frozen_event_id = "moonwell_echo"
	journey.frozen_event_options.assign(ContentCatalog.event("moonwell_echo").options)
	_assert_equal(JourneyRules.choose_event(preparation, journey, 1).accepted, true, "event choice resolves")
	_assert_equal(journey.loot.size() >= 2 and journey.loot.size() <= 6, true, "reward event has two to six items")
	_assert_equal(journey.run_gold, 10, "first event grants half of first-round battle gold")
	_assert_equal(JourneyRules.choose_event(preparation, journey, 0).accepted, false, "event rewards cannot be collected twice")
	_assert_equal(journey.run_gold, 10, "rejected repeat does not change gold")
	JourneyRules.advance_appraisal(journey, 800)
	_assert_equal(journey.loot[0].identified, false, "event chest opening precedes item appraisal")
	var frozen_event := journey.frozen_event_id
	_assert_equal(frozen_event.is_empty(), false, "event identity freezes before choice")
	JourneyRules.advance_appraisal(journey, 10000)
	_assert_equal(JourneyRules.leave_node(journey).discarded, true, "leaving discards remaining event loot")
	_test_frozen_shrine()
	_test_war_banner()
	_test_bridge()
	_test_event_item_choice()
	_test_alchemy()
	_test_legacy_events()
	var rest := JourneyState.new(7001)
	rest.current_node = 5
	rest.player_hp_ratio = 0.5
	_assert_equal(JourneyRules.leave_node(rest).accepted, false, "rest must be entered first")
	JourneyRules.resolve_current(preparation, rest)
	_assert_equal(JourneyRules.leave_node(rest).accepted, true, "rest continues to the fourth fight")
	_assert_equal(is_equal_approx(rest.player_hp_ratio, 0.85), true, "rest heals 35 percent maximum HP")
	_assert_equal(JourneyRules.leave_node(rest).accepted, false, "rest healing cannot repeat at the next node")
	quit(1 if _failed else 0)

func _test_legacy_events() -> void:
	var state := PreparationState.new("juggernaut")
	var clinic := _event_journey("field_clinic", 0.5)
	clinic.run_gold = 30
	_assert_equal(JourneyRules.choose_event(state, clinic, 0).accepted, true, "clinic accepts run gold")
	_assert_equal(is_equal_approx(clinic.player_hp_ratio, 0.75), true, "clinic restores health")
	_assert_equal(clinic.run_gold, 0, "clinic deducts run gold without loot reward")
	var terminal := _event_journey("tech_terminal", 1.0)
	var wrong := ItemInstance.new("wrong", "iron_branch", ItemInstance.Kind.EQUIPMENT)
	wrong.location = ItemInstance.Location.BACKPACK
	state.add_item(wrong)
	_assert_equal(JourneyRules.choose_event(state, terminal, 0, wrong.id).accepted, false, "terminal rejects other materials")
	var essence := ItemInstance.new("essence_instance", "essence", ItemInstance.Kind.MATERIAL)
	essence.location = ItemInstance.Location.BACKPACK
	state.add_item(essence)
	_assert_equal(JourneyRules.choose_event(state, terminal, 0, essence.id).accepted, true, "terminal consumes selected essence")
	_assert_equal(terminal.loot[0].definition_id, "skill_shard", "terminal grants skill shard")
	var library := _event_journey("ruined_library", 1.0)
	_assert_equal(JourneyRules.choose_event(state, library, 1).accepted, false, "safe study requires ultimate synergy")
	var storage := _event_journey("collapsed_storage", 1.0)
	storage.run_gold = 30
	_assert_equal(JourneyRules.choose_event(state, storage, 1).accepted, true, "storage allows paid exit")
	_assert_equal(storage.run_gold, 0, "paid exit does not generate rewards")
	var wreckage := _event_journey("safe_wreckage", 1.0)
	_assert_equal(JourneyRules.choose_event(state, wreckage, 0).accepted, false, "safe core requires progression")
	state.quest_progress["clears"] = 1
	_assert_equal(JourneyRules.choose_event(state, wreckage, 0).accepted, true, "safe core unlocks after clear")
	_assert_equal(wreckage.loot[0].definition_id, "safe_lock", "wreckage grants safe lock")
	var blocker := _event_journey("blocker", 1.0)
	_assert_equal(JourneyRules.choose_event(state, blocker, 1, wrong.id).accepted, true, "blocker consumes selected item")
	_assert_equal(state.item(wrong.id) == null, true, "delivered item is removed")

func _test_alchemy() -> void:
	var state := PreparationState.new("juggernaut")
	var medicine := ItemInstance.new("salve_instance", "salve", ItemInstance.Kind.CONSUMABLE)
	medicine.location = ItemInstance.Location.BACKPACK
	state.add_item(medicine)
	var distilled := _event_journey("alchemist_spill", 0.5)
	_assert_equal(JourneyRules.choose_event(state, distilled, 0, medicine.id).accepted, true, "distilling consumes selected medicine")
	_assert_equal(is_equal_approx(distilled.player_hp_ratio, 0.75), true, "distilling restores 25 percent health")
	_assert_equal(distilled.loot[0].definition_id, "essence", "distilling grants essence")
	var sealed := _event_journey("alchemist_spill", 0.5)
	var max_hp := float((BattleLoadoutBuilder.build(state).fighter as FighterState).max_hp)
	JourneyRules.choose_event(state, sealed, 1)
	_assert_equal(is_equal_approx(sealed.player_hp_ratio, 0.42 - 9.0 / max_hp), true, "seal damage includes three poison ticks")
	_assert_equal(sealed.event_result.get("poison_damage"), 9, "poison damage is reported")
	var fatal := _event_journey("alchemist_spill", 0.08)
	JourneyRules.choose_event(state, fatal, 1)
	_assert_equal(fatal.ending, "death", "seal damage may be fatal")

func _test_event_item_choice() -> void:
	var state := PreparationState.new("juggernaut")
	var item := ItemInstance.new("sale", "iron_branch", ItemInstance.Kind.EQUIPMENT)
	item.location = ItemInstance.Location.BACKPACK
	state.add_item(item)
	var event := _event_journey("wandering_caravan", 1.0)
	_assert_equal(JourneyRules.choose_event(state, event, 1).accepted, false, "sale requires an explicit item")
	_assert_equal(state.item("sale") != null and not event.resolved, true, "rejected sale changes nothing")
	_assert_equal(JourneyRules.choose_event(state, event, 1, "sale").accepted, true, "sale accepts chosen item")
	_assert_equal(state.item("sale") == null and event.run_gold == 20, true, "sale consumes item and pays its price")
	var safe := ItemInstance.new("safe_item", "iron_branch", ItemInstance.Kind.EQUIPMENT)
	safe.location = ItemInstance.Location.SAFE
	state.add_item(safe)
	var barter := _event_journey("hunter_camp", 0.5)
	_assert_equal(JourneyRules.choose_event(state, barter, 0, safe.id).accepted, false, "barter rejects unselected safe contents")
	var medicine := ItemInstance.new("medicine", "salve", ItemInstance.Kind.CONSUMABLE)
	medicine.location = ItemInstance.Location.BACKPACK
	state.add_item(medicine)
	_assert_equal(JourneyRules.choose_event(state, barter, 1, medicine.id).accepted, true, "rations consume selected medicine")
	_assert_equal(is_equal_approx(barter.player_hp_ratio, 0.8), true, "rations heal thirty percent")
	_assert_equal(barter.loot[0].definition_id, "skill_shard", "rations grant a skill shard")

func _test_bridge() -> void:
	var state := PreparationState.new("juggernaut")
	var cross := _event_journey("broken_span", 0.5)
	_assert_equal(JourneyRules.choose_event(state, cross, 0).accepted, true, "bridge crossing succeeds")
	_assert_equal(is_equal_approx(cross.player_hp_ratio, 0.4), true, "crossing costs ten percent health")
	_assert_equal(cross.loot[0].definition_id, "essence", "crossing grants essence")
	var fatal := _event_journey("broken_span", 0.1)
	JourneyRules.choose_event(state, fatal, 0)
	_assert_equal(fatal.ending, "death", "bridge crossing can be fatal")
	var repair := _event_journey("broken_span", 1.0)
	_assert_equal(JourneyRules.choose_event(state, repair, 1).accepted, false, "repair requires run gold")
	repair.run_gold = 30
	_assert_equal(JourneyRules.choose_event(state, repair, 1).accepted, true, "repair accepts exact price")
	_assert_equal(repair.event_result.get("gold_cost"), 30, "repair records cost")
	_assert_equal(repair.loot[0].kind, ItemInstance.Kind.EQUIPMENT, "repair grants an equipment component")
	var restored: JourneyState = SaveCodec.decode(SaveCodec.encode(state, repair)).journey
	_assert_equal(restored.event_result, repair.event_result, "bridge result survives save restore")

func _test_war_banner() -> void:
	var state := PreparationState.new("juggernaut")
	var purchase := _event_journey("banner_ruins", 0.5)
	purchase.run_gold = 29
	_assert_equal(JourneyRules.choose_event(state, purchase, 0).accepted, false, "purchase requires 30 run gold after the first fight")
	_assert_equal(purchase.run_gold == 29 and not purchase.resolved and purchase.loot.is_empty(), true, "failed purchase leaves the event open")
	purchase.run_gold = 30
	var wallet := state.gold
	_assert_equal(JourneyRules.choose_event(state, purchase, 0).accepted, true, "purchase accepts exact price")
	_assert_equal(state.gold, wallet, "purchase only charges run gold")
	_assert_equal(purchase.run_gold, 10, "purchase deducts 30 before crediting the 10 gold reward")
	_assert_equal(purchase.event_result.get("gold_cost"), 30, "result records gold spent")
	_assert_equal(purchase.event_result.get("art_key"), "war_banner.claim", "purchase uses the corresponding art")
	var rite := _event_journey("banner_ruins", 0.5)
	_assert_equal(JourneyRules.choose_event(state, rite, 1).accepted, true, "blood rite settles")
	_assert_equal(is_equal_approx(rite.player_hp_ratio, 0.35), true, "blood rite costs 15% max HP")
	for result in [purchase, rite]:
		var first_id: String = result.loot[0].definition_id if not result.loot.is_empty() else ""
		_assert_equal(AndroidItems.cards().any(func(card: Dictionary) -> bool: return card.id == first_id and "rage" in card.tags), true, "banner rewards prefer rage cards")
	var fatal := _event_journey("banner_ruins", 0.15)
	JourneyRules.choose_event(state, fatal, 1)
	_assert_equal(fatal.ending, "death", "blood rite can end the journey")
	_assert_equal(fatal.loot.is_empty(), true, "lethal blood rite generates no loot")

func _test_frozen_shrine() -> void:
	var state := PreparationState.new("juggernaut")
	var prayer := _event_journey("frozen_altar", 0.97)
	_assert_equal(JourneyRules.choose_event(state, prayer, 1).accepted, true, "prayer settles")
	_assert_equal(prayer.player_hp_ratio, 1.0, "prayer restores up to maximum HP")
	_assert_equal(prayer.loot.is_empty() and prayer.run_gold == 0, true, "prayer only restores HP")
	_assert_equal(prayer.event_result.get("art_key"), "frozen_shrine.pray", "prayer uses the corresponding art")
	var restored: JourneyState = SaveCodec.decode(SaveCodec.encode(state, prayer)).journey
	_assert_equal(restored.event_result, prayer.event_result, "prayer result survives save restore")
	_assert_equal(restored.player_hp_ratio, prayer.player_hp_ratio, "prayer health survives save restore")
	var attunement := _event_journey("frozen_altar", 0.5)
	JourneyRules.choose_event(state, attunement, 0)
	_assert_equal(is_equal_approx(attunement.player_hp_ratio, 0.42), true, "attunement costs 8% max HP")
	if not attunement.loot.is_empty():
		var guaranteed := AndroidItems.item(attunement.loot[0].definition_id)
		_assert_equal(guaranteed.get("kind"), "CARD", "attunement guarantees a skill card")
		_assert_equal(AndroidItems.cards().any(func(card: Dictionary) -> bool: return card.id == guaranteed.get("id") and "frost" in card.tags), true, "attunement prefers a frost card")
	else:
		_assert_equal(false, true, "attunement generates loot")
	var fatal := _event_journey("frozen_altar", 0.08)
	state.phase = PreparationState.Phase.JOURNEY
	fatal.run_gold = 10
	JourneyRules.choose_event(state, fatal, 0)
	_assert_equal(fatal.ending, "death", "lethal attunement ends the journey")
	_assert_equal(fatal.loot.is_empty() and fatal.run_gold == 0, true, "lethal attunement loses run gold and generates no loot")
	_assert_equal(state.phase, PreparationState.Phase.HOME, "lethal attunement returns player to preparation")

func _event_journey(event_id: String, hp_ratio: float) -> JourneyState:
	var journey := JourneyState.new(7001)
	journey.current_node = 1
	journey.player_hp_ratio = hp_ratio
	journey.frozen_event_id = event_id
	journey.frozen_event_options.assign(ContentCatalog.event(journey.frozen_event_id).options)
	return journey

func _assert_equal(actual: Variant, expected: Variant, label: String) -> void:
	if actual != expected:
		_failed = true
		push_error("%s; expected=%s actual=%s" % [label, str(expected), str(actual)])
