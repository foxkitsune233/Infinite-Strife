extends SceneTree
const Pity = preload("res://scripts/journey/MaterialProgression.gd")
const Chests = preload("res://scripts/journey/ChestRules.gd")
var checks := 0
var failures := 0
func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(label)
func add(s: PreparationState, id: String, definition: String, location: int = ItemInstance.Location.WAREHOUSE) -> void:
	var item := ItemInstance.new(id, definition, ItemInstance.Kind.MATERIAL)
	item.location = location
	s.items[id] = item
func _init() -> void:
	var s := PreparationState.new("juggernaut")
	s.backpack_size = Vector2i(4, 2)
	check(Pity.eligible(s).is_empty(), "fresh state ineligible")
	s.quest_progress.extracted = true
	check(Pity.eligible(s, "backpack", 0) == ["backpack"], "extraction opens fabric")
	add(s, "old1", "backpack_fabric")
	add(s, "old2", "backpack_fabric")
	check(Pity.needed_item(s, "backpack").is_empty(), "warehouse enough closes need")
	s.items.old2.location = ItemInstance.Location.BACKPACK
	check(Pity.needed_item(s, "backpack") == "backpack_fabric", "carried stock excluded")
	s.quest_progress.clears = 3
	s.quest_progress.enabled_card_slots = 6
	check(Pity.eligible(s, "skills", 0) == ["skills"], "skills gate")
	check(Pity.eligible(s, "safe", 0).is_empty(), "safe requires normal")
	check(Pity.eligible(s, "safe", 1) == ["safe"], "safe normal eligible")
	for cat in Pity.THRESHOLDS:
		var target := {"theme": cat, "difficulty": 1}
		for i in int(Pity.THRESHOLDS[cat]):
			var snap := Pity.capture(s, target)
			Pity.record_after(snap, s, true)
			var count := int(s.quest_progress.material_pity[cat].misses)
			Pity.record_after(snap, s, true)
			check(int(s.quest_progress.material_pity[cat].misses) == count, "same snapshot once")
		check(s.quest_progress.material_pity[cat].pending, "threshold " + cat)
		Pity.record_after(Pity.capture(s, target), s, true)
		check(int(s.quest_progress.material_pity[cat].misses) == int(Pity.THRESHOLDS[cat]), "pending frozen")
	var old: Dictionary = s.quest_progress.material_pity.duplicate(true)
	Pity.record_after(Pity.capture(s, {"theme": "backpack"}), s, false)
	check(s.quest_progress.material_pity == old, "death/extraction no miss and old items no reset")
	var snap := Pity.capture(s, {"theme": "backpack"})
	add(s, "new-safe", "backpack_fabric", ItemInstance.Location.SAFE)
	Pity.record_after(snap, s, false)
	check(s.quest_progress.material_pity.backpack.misses == 0, "new safe survivor resets even death")
	check(s.quest_progress.material_pity.skills.pending, "category independent")
	s.items.erase("new-safe")
	for cat in Pity.THRESHOLDS:
		s.quest_progress.material_pity[cat] = {"misses": Pity.THRESHOLDS[cat], "pending": true}
		var box := {"quality": "白", "difficulty": 1}
		check(Chests.generate(s, box, cat), "forced generation")
		var id := Pity.needed_item(s, cat)
		check(id in box.rewards and box.items[0].id == id, "forced first packed and rewarded")
		check(box.guaranteed_categories == [cat] and not s.quest_progress.material_pity[cat].pending, "consumed same generation")
		s.quest_progress.material_pity[cat] = {"misses": Pity.THRESHOLDS[cat], "pending": true}
		var content: Array = box.items.duplicate(true)
		check(Chests.generate(s, box, cat) and box.items == content and s.quest_progress.material_pity[cat].pending, "same box no duplicate consume")
	var invalid := {"quality": "invalid", "difficulty": 1}
	old = s.quest_progress.material_pity.duplicate(true)
	check(not Chests.generate(s, invalid, "safe") and s.quest_progress.material_pity == old, "failure retains pity")
	var run := JourneyState.new(123)
	run.difficulty = 1
	run.target_chest = {"theme": "skills", "run_start_item_ids": s.items.keys()}
	add(s, "run-new", "skill_shard", ItemInstance.Location.SAFE)
	Pity.snapshot(s, run)
	Pity.record(s, run, false)
	check(s.quest_progress.material_pity.skills.misses == 0, "integration snapshot detects pre-settlement new item")
	Pity.record(s, run, true)
	check(s.quest_progress.material_pity.skills.misses == 0, "integration repeated settlement ignored")
	var lost_run := JourneyState.new(124)
	lost_run.target_chest = {"theme": "backpack", "run_start_item_ids": s.items.keys()}
	add(s, "lost", "backpack_fabric", ItemInstance.Location.BACKPACK)
	Pity.snapshot(s, lost_run)
	s.items.erase("lost")
	Pity.record(s, lost_run, false)
	check(s.quest_progress.material_pity.backpack.pending, "lost new material does not reset")
	var encoded := SaveCodec.encode(s, run)
	var decoded := SaveCodec.decode(JSON.parse_string(JSON.stringify(encoded)))
	check(decoded.accepted and int(decoded.preparation.quest_progress.material_pity.safe.misses) == 6 and bool(decoded.preparation.quest_progress.material_pity.safe.pending), "save JSON roundtrip")
	s.safe_size = Vector2i(3, 2)
	s.quest_progress.clears = 5
	check(Pity.needed_item(s, "safe").is_empty(), "frame backpack gate")
	s.backpack_size = Vector2i(4, 3)
	check(Pity.needed_item(s, "safe") == "safe_frame", "frame available")
	s.quest_progress.clears = 0
	check(Pity.needed_item(s, "backpack").is_empty(), "next tier clear gate")
	print("Material pity: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
