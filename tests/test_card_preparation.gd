extends SceneTree
const Rules = preload("res://scripts/prepare/CardPreparationRules.gd")
var checks := 0
func check(ok: bool, message: String) -> void:
	checks += 1
	assert(ok, message)
func add(s: PreparationState, id: String, def: String = "A1") -> ItemInstance:
	var item := ItemInstance.new(id, def, ItemInstance.Kind.CARD)
	s.add_item(item)
	return item
func _init() -> void:
	var s := PreparationState.new("juggernaut")
	for id in ["a", "b", "c"]:
		add(s, id)
	s.items.b.bound = true
	s.preset(s.active_hero_id, 0).card_ids[0] = "b"
	s.preset(s.active_hero_id, 0).card_ids[1] = "a"
	var gold := s.gold
	check(Rules.merge_cards(s, ["a", "b", "c"]).accepted, "merge")
	check(s.items.size() == 1 and s.items.a.star == 2 and s.items.a.bound, "first id and any bound")
	check(s.gold == gold and s.items.a.footprint == Vector2i.ONE, "free and footprint unchanged")
	check(s.preset(s.active_hero_id, 0).card_ids[0] == "" and s.preset(s.active_hero_id, 0).card_ids[1] == "a", "presets cleaned")
	add(s, "b").star = 2
	add(s, "c").star = 2
	check(Rules.merge_cards(s, ["a", "b", "c"]).accepted and s.items.a.star == 3, "three star")
	add(s, "b").star = 3
	add(s, "c").star = 3
	check(not Rules.merge_cards(s, ["a", "b", "c"]).accepted, "max star")
	for id in ["a", "b", "c"]:
		s.items[id].star = 1
	check(not Rules.merge_cards(s, ["a", "a", "b"]).accepted, "duplicate inputs")
	s.items.b.locked = true
	check(not Rules.merge_cards(s, ["a", "b", "c"]).accepted, "lock")
	s.items.b.locked = false
	s.items.b.star = 2
	check(not Rules.merge_cards(s, ["a", "b", "c"]).accepted, "same star required")
	s.items.b.star = 1
	s.items.b.definition_id = "A2"
	check(not Rules.merge_cards(s, ["a", "b", "c"]).accepted, "same id required")
	s.items.b.definition_id = "A1"
	for location in [ItemInstance.Location.CARD, ItemInstance.Location.BACKPACK, ItemInstance.Location.SAFE]:
		s.items.b.location = location
		check(not Rules.merge_cards(s, ["a", "b", "c"]).accepted, "warehouse only")
	s.items.b.location = ItemInstance.Location.WAREHOUSE
	for phase in [PreparationState.Phase.JOURNEY, PreparationState.Phase.BATTLE]:
		s.phase = phase
		check(not Rules.merge_cards(s, ["a", "b", "c"]).accepted, "home only")
	s.phase = PreparationState.Phase.HOME
	check(not LoadoutRules.place(s, "a", ItemInstance.Kind.CARD, 4).accepted, "disabled slot")
	check(LoadoutRules.place(s, "a", ItemInstance.Kind.CARD, 3).accepted, "any slot")
	add(s, "p", "P1")
	check(LoadoutRules.place(s, "p", ItemInstance.Kind.CARD, 0).accepted and s.card_slots[3] == "a", "mixed equip no reorder")
	LoadoutRules.save_preset(s, 0)
	s.preset(s.active_hero_id, 0).card_ids[7] = "b"
	LoadoutRules.apply_preset(s, 0)
	check(s.card_slots[7] == "" and s.items.b.location == ItemInstance.Location.WAREHOUSE, "preset slot limit")
	PackingRules.move_to_warehouse(s, "a")
	check("a" in Rules.pool(s).basic and s.card_slots[0] == "p", "return grouped")
	check(Rules.pool(s).size() == 8, "eight school layers")
	check(Rules.details(s.items.a).contains("48"), "all star metadata")
	LoadoutRules.place(s, "a", ItemInstance.Kind.CARD, 1)
	LoadoutRules.place(s, "b", ItemInstance.Kind.CARD, 2)
	for tier in Rules.synergy(s):
		if tier.schoolId == "basic":
			check(tier.count == 1 and not tier.active, "distinct definition synergy")
	print("Card preparation: %d checks passed" % checks)
	quit()
