extends Node

const ItemInstance = preload("res://scripts/prepare/ItemInstance.gd")
const PreparationState = preload("res://scripts/prepare/PreparationState.gd")
const LoadoutRules = preload("res://scripts/prepare/LoadoutRules.gd")
const PackingRules = preload("res://scripts/prepare/PackingRules.gd")
const ContentCatalog = preload("res://scripts/content/ContentCatalog.gd")
const JourneyState = preload("res://scripts/journey/JourneyState.gd")
const JourneyRules = preload("res://scripts/journey/JourneyRules.gd")
const SaveRepository = preload("res://scripts/save/SaveRepository.gd")
const Progression = preload("res://scripts/progression/ProgressionRules.gd")
const QuestRules = preload("res://scripts/progression/QuestRules.gd")
const ReliefRules = preload("res://scripts/journey/ReliefRules.gd")
const StoryRules = preload("res://scripts/story/StoryRules.gd")
const TowerRules = preload("res://scripts/tower/TowerRules.gd")
const TowerCombat = preload("res://scripts/tower/TowerCombat.gd")

var preparation_state: PreparationState
var journey_state: JourneyState
var journey_battle_pending := false
var save_path := SaveRepository.DEFAULT_PATH
var blocked_save_path := ""
var save_block_reason := ""

func ensure_preparation_state() -> PreparationState:
	if preparation_state == null:
		preparation_state = PreparationState.new("juggernaut")
		preparation_state.gold = 600
		preparation_state.backpack_size = Vector2i(4, 2)
		_seed_demo_inventory(preparation_state)
	return preparation_state

func ensure_journey_state() -> JourneyState:
	if journey_state == null:
		journey_state = JourneyState.new(7001)
	return journey_state

func begin_journey_battle() -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	var result := JourneyRules.begin_battle(ensure_journey_state())
	if not result.accepted:
		return result
	journey_battle_pending = bool(result.accepted)
	if journey_battle_pending:
		ensure_preparation_state().phase = PreparationState.Phase.BATTLE
		var saved := save_progress()
		if not saved.accepted:
			_restore_campaign(checkpoint)
			return saved
	return result

func enter_journey(difficulty: int = 0, target: Dictionary = {}, carry_gold: int = 0) -> Dictionary:
	var state := ensure_preparation_state()
	if state.quest_progress.get("tower_run") != null:
		return {"accepted": false, "reason": "爬塔轮次进行中，请先结算转生爬塔"}
	if state.phase != PreparationState.Phase.HOME:
		if carry_gold != 0:
			return {"accepted": false, "reason": "已有挑战，不能追加携入资金"}
		return {"accepted": true, "reason": "继续当前远征"}
	if target.has("difficulty") and int(target.difficulty) != difficulty:
		return {"accepted": false, "reason": "目标宝箱难度与出发配置不一致"}
	var locked := QuestRules.locked_reason(state.quest_progress, difficulty)
	if not locked.is_empty():
		return {"accepted": false, "reason": locked}
	var ticket := ReliefRules.entry_price(state, target)
	# Subtraction avoids overflowing the combined ticket + carry amount.
	if carry_gold < 0 or ticket > state.gold or carry_gold > state.gold - ticket:
		return {"accepted": false, "reason": "金币不足以支付门票和携入资金"}
	var previous_journey := journey_state
	var previous_gold := state.gold
	var previous_progress := state.quest_progress.duplicate(true)
	var previous_serial := state.progression_serial
	journey_state = JourneyState.new(7001 + int(state.quest_progress.get("runs_started", 0)))
	journey_state.difficulty = difficulty
	journey_state.target_chest = target.duplicate(true)
	journey_state.target_chest["run_start_item_ids"] = state.items.keys()
	journey_state.run_gold = carry_gold
	state.gold -= ticket + carry_gold
	if ReliefRules.subsidized(state, target):
		state.quest_progress.relief.vouchers = int(state.quest_progress.relief.vouchers) - 1
	state.phase = PreparationState.Phase.JOURNEY
	state.quest_progress["runs_started"] = int(state.quest_progress.get("runs_started", 0)) + 1
	Progression.record(state, {"type": "Entered"})
	var saved := save_progress()
	if not saved.accepted:
		state.phase = PreparationState.Phase.HOME
		state.gold = previous_gold
		state.quest_progress = previous_progress
		state.progression_serial = previous_serial
		journey_state = previous_journey
	return saved

func claim_relief(now_ms: int) -> Dictionary:
	var state := ensure_preparation_state()
	var previous_items := state.items.duplicate()
	var previous_progress := state.quest_progress.duplicate(true)
	var previous_serial := state.progression_serial
	var result := ReliefRules.claim(state, now_ms)
	if not result.accepted:
		return result
	var saved := save_progress()
	if not saved.accepted:
		state.items = previous_items
		state.quest_progress = previous_progress
		state.progression_serial = previous_serial
		return saved
	return result

func complete_journey_battle(victory: bool) -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	if not ensure_journey_state().battle_started or ensure_journey_state().finished():
		return {"accepted": false, "reason": "当前没有可结算的战斗"}
	journey_battle_pending = false
	ensure_preparation_state().phase = PreparationState.Phase.JOURNEY
	var result := JourneyRules.complete_battle(ensure_preparation_state(), ensure_journey_state(), victory)
	if result.accepted and victory:
		Progression.record(ensure_preparation_state(), {"type": "BattleWon", "hero_id": ensure_preparation_state().active_hero_id, "difficulty": ensure_journey_state().difficulty})
	var saved := save_progress()
	if not saved.accepted:
		_restore_campaign(checkpoint)
		return saved
	return result

func save_progress() -> Dictionary:
	if blocked_save_path == save_path:
		return {"accepted": false, "reason": save_block_reason}
	Progression.observe(ensure_preparation_state())
	return SaveRepository.write(save_path, ensure_preparation_state(), ensure_journey_state())

func restore_progress() -> Dictionary:
	var result := SaveRepository.read(save_path)
	if bool(result.get("accepted", false)):
		blocked_save_path = ""
		save_block_reason = ""
		preparation_state = result.preparation as PreparationState
		journey_state = result.journey as JourneyState
		journey_battle_pending = journey_state.battle_started and not journey_state.finished()
	elif has_saved_progress():
		blocked_save_path = save_path
		save_block_reason = "现有存档无法恢复，已阻止覆盖原文件。请先恢复备份。"
	return result

## 恢复玩家备份：先落盘，再把新存档同步进当前会话内存。
## 只恢复磁盘会让旧的内存状态在下次保存时覆盖回来（777 金币恢复后仍是 1 金币）。
func restore_from_backup(text: String) -> Dictionary:
	var result := preload("res://scripts/app/BackupRules.gd").restore(text, save_path)
	if not bool(result.get("accepted", false)):
		return result
	# 重新读取刚写入的存档，确保内存与磁盘完全一致。
	var reloaded := SaveRepository.read(save_path)
	if not bool(reloaded.get("accepted", false)):
		return {"accepted": false, "reason": "备份已写入，但重新载入失败；请重启游戏后再试"}
	_adopt_decoded(reloaded)
	# 原存档已被有效备份替换，解除此前的覆盖阻断。
	blocked_save_path = ""
	save_block_reason = ""
	return {"accepted": true, "reason": "备份已恢复并立即生效"}

func leave_journey_node() -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	var journey := ensure_journey_state()
	var result := JourneyRules.leave_node(journey)
	if result.accepted and journey.finished():
		var state := ensure_preparation_state()
		preload("res://scripts/journey/MaterialProgression.gd").snapshot(state, journey)
		state.phase = PreparationState.Phase.HOME
		journey.ending = "cleared"
		for definition_id in journey.target_chest.get("rewards", []):
			var definition := ContentCatalog.item(str(definition_id))
			if definition == null:
				continue
			journey.drop_serial += 1
			var kind: ItemInstance.Kind = {"CARD": ItemInstance.Kind.CARD, "CONSUMABLE": ItemInstance.Kind.CONSUMABLE, "MATERIAL": ItemInstance.Kind.MATERIAL}.get(definition.source_data.get("kind"), ItemInstance.Kind.EQUIPMENT)
			var item := ItemInstance.new("chest_%d_%d" % [journey.seed, journey.drop_serial], definition.id, kind)
			item.footprint = definition.footprint
			item.value = definition.value
			item.bound = bool(definition.source_data.get("boundOnAcquire", false))
			state.add_item(item)
		JourneyRules.bank_run_gold(state, journey)
		JourneyRules.return_items_to_warehouse(state)
		preload("res://scripts/journey/MaterialProgression.gd").record(state, journey, true)
		state.quest_progress["extracted"] = true
		Progression.record(state, {"type": "RunCleared", "hero_id": state.active_hero_id, "difficulty": ensure_journey_state().difficulty})
	if result.accepted:
		var saved := save_progress()
		if not saved.accepted:
			_restore_campaign(checkpoint)
			return saved
	return result

func extract_journey() -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	var result := JourneyRules.extract(ensure_preparation_state(), ensure_journey_state())
	if result.accepted:
		var saved := save_progress()
		if not saved.accepted:
			_restore_campaign(checkpoint)
			return saved
	return result

func _campaign_checkpoint() -> Dictionary:
	return preload("res://scripts/save/SaveCodec.gd").encode(ensure_preparation_state(), ensure_journey_state()).duplicate(true)

func _restore_campaign(checkpoint: Dictionary) -> void:
	var decoded := preload("res://scripts/save/SaveCodec.gd").decode(checkpoint, false)
	_adopt_decoded(decoded)

## 把解码后的存档状态写回现有状态对象，保持资源身份：活动页面持有的引用继续有效。
func _adopt_decoded(decoded: Dictionary) -> void:
	var target_prep := ensure_preparation_state()
	var target_journey := ensure_journey_state()
	for pair in [[target_prep, decoded.preparation], [target_journey, decoded.journey]]:
		for property in pair[0].get_property_list():
			if int(property.usage) & PROPERTY_USAGE_SCRIPT_VARIABLE:
				pair[0].set(property.name, pair[1].get(property.name))
	journey_battle_pending = journey_state.battle_started and not journey_state.finished()

func is_tower_battle_pending() -> bool:
	return bool(ensure_preparation_state().quest_progress.get("tower_run", {}).get("battle_started", false))

func enter_tower() -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	return _commit_tower(TowerRules.enter(ensure_preparation_state()), checkpoint)

func begin_tower_battle() -> Dictionary:
	var state := ensure_preparation_state()
	var run: Dictionary = state.quest_progress.get("tower_run", {})
	if run.is_empty():
		return {"accepted": false, "reason": "没有进行中的爬塔轮次"}
	var checkpoint := _campaign_checkpoint()
	var builds := TowerCombat.build(state, run)
	var result := TowerRules.prepare(state, float(builds.left.fighter.max_hp))
	if result.accepted:
		state.phase = PreparationState.Phase.BATTLE
	return _commit_tower(result, checkpoint)

func complete_tower_battle(victory: bool, hp: float, max_hp: float) -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	var state := ensure_preparation_state()
	var result := TowerRules.settle(state, victory, hp, max_hp)
	if result.accepted:
		state.phase = PreparationState.Phase.HOME
	return _commit_tower(result, checkpoint)

func choose_tower_waypoint(choice: String) -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	return _commit_tower(TowerRules.waypoint(ensure_preparation_state(), choice), checkpoint)

func grant_tower_reward(kind: String, hero_id: String) -> Dictionary:
	var checkpoint := _campaign_checkpoint()
	return _commit_tower(TowerRules.grant(ensure_preparation_state(), kind, hero_id), checkpoint)

func _commit_tower(result: Dictionary, checkpoint: Dictionary) -> Dictionary:
	if not result.accepted:
		_restore_campaign(checkpoint)
		return result
	for id in result.get("story_ids", []):
		StoryRules.enqueue(ensure_preparation_state(), str(id), str(result.get("hero_id", "")))
	var saved := save_progress()
	if not saved.accepted:
		_restore_campaign(checkpoint)
		return saved
	return result

func has_saved_progress() -> bool:
	return FileAccess.file_exists(save_path) or FileAccess.file_exists(save_path + ".bak")

func upgrade_hero(hero_id: String) -> Dictionary:
	var state := ensure_preparation_state()
	var before_gold := state.gold
	var before_levels := state.hero_levels.duplicate(true)
	var before_quests := state.quest_progress.duplicate(true)
	var result := preload("res://scripts/progression/HeroLevelRules.gd").upgrade(state, hero_id)
	if result.accepted:
		var saved := save_progress()
		if not saved.accepted:
			state.gold = before_gold
			state.hero_levels = before_levels
			state.quest_progress = before_quests
			return saved
	return result

func claim_quest(quest_id: String) -> Dictionary:
	var snapshot := preload("res://scripts/save/SaveCodec.gd").encode(ensure_preparation_state(), ensure_journey_state())
	var result := Progression.claim(ensure_preparation_state(), quest_id)
	if result.accepted:
		var quest := preload("res://scripts/progression/AndroidQuestCatalog.gd").by_id(quest_id)
		StoryRules.enqueue(ensure_preparation_state(), "J%02d" % int(quest.node))
		var saved := save_progress()
		if not saved.accepted:
			preparation_state = preload("res://scripts/save/SaveCodec.gd").decode(snapshot, false).preparation
			return saved
	return result

func advance_story(id: String, skip := false) -> Dictionary:
	var state := ensure_preparation_state()
	var previous := state.quest_progress.duplicate(true)
	var serial := state.progression_serial
	var result := StoryRules.advance(state, id, skip)
	if result.accepted:
		var saved := save_progress()
		if not saved.accepted:
			state.quest_progress = previous
			state.progression_serial = serial
			return saved
	return result

func queue_story(id: String) -> Dictionary:
	var state := ensure_preparation_state()
	var previous := state.quest_progress.duplicate(true)
	var serial := state.progression_serial
	StoryRules.enqueue(state, id)
	var saved := save_progress()
	if not saved.accepted:
		state.quest_progress = previous
		state.progression_serial = serial
	return saved

func upgrade_growth(kind: String) -> Dictionary:
	var codec := preload("res://scripts/save/SaveCodec.gd")
	var snapshot := codec.encode(ensure_preparation_state(), ensure_journey_state())
	var result := preload("res://scripts/progression/AndroidGrowthRules.gd").upgrade(ensure_preparation_state(), kind)
	if result.accepted:
		var saved := save_progress()
		if not saved.accepted:
			preparation_state = codec.decode(snapshot, false).preparation
			return saved
	return result

func _seed_demo_inventory(state: PreparationState) -> void:
	for index in 9:
		var ids := ["broadsword", "chainmail", "slippers", "circlet", "gauntlets", "gloves_of_haste", "iron_branch", "ring_of_regen", "iron_branch"]
		var equipment := preload("res://scripts/content/AndroidContent.gd").create_instance("equip_%d" % index, str(ids[index]))
		state.add_item(equipment)
	for index in 8:
		var ids := ["A1", "A2", "A3", "A4", "U1", "U2", "U3", "U4"]
		var card := preload("res://scripts/content/AndroidContent.gd").create_instance("card_%d" % index, str(ids[index]))
		state.add_item(card)
	for definition_id in ["iron_branch"]:
		var definition := ContentCatalog.item(definition_id)
		var component := ItemInstance.new("material_%s" % definition_id, definition_id, ItemInstance.Kind.EQUIPMENT)
		component.footprint = definition.footprint
		component.value = definition.value
		state.add_item(component)
	for index in 6:
		LoadoutRules.place(state, "equip_%d" % index, ItemInstance.Kind.EQUIPMENT, index)
	for index in 4:
		LoadoutRules.place(state, "card_%d" % index, ItemInstance.Kind.CARD, index)
	PackingRules.place(state, "equip_6", ItemInstance.Location.BACKPACK, Vector2i(0, 0), false)
	PackingRules.place(state, "card_4", ItemInstance.Location.BACKPACK, Vector2i(2, 0), false)
	PackingRules.place(state, "equip_7", ItemInstance.Location.SAFE, Vector2i(0, 0), false)
