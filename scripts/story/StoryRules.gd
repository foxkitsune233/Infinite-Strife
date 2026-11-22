extends RefCounted

const Catalog = preload("res://scripts/story/StoryCatalog.gd")
const Quests = preload("res://scripts/progression/AndroidQuestCatalog.gd")

static func enqueue(state: PreparationState, id: String, hero_id: String = "") -> void:
	if Catalog.chapter(id).is_empty():
		return
	var story: Dictionary = state.quest_progress.get("story", {}).duplicate(true)
	var queue: Array = story.get("pending", [])
	if id in story.get("seen", []) or id in queue:
		return
	queue.append(id)
	story["pending"] = queue
	var heroes: Dictionary = story.get("heroes", {})
	heroes[id] = state.active_hero_id if hero_id.is_empty() else hero_id
	story["heroes"] = heroes
	state.quest_progress["story"] = story

static func pending(state: PreparationState) -> String:
	var queue: Array = state.quest_progress.get("story", {}).get("pending", [])
	return "" if queue.is_empty() else str(queue[0])

static func page(state: PreparationState, id: String) -> int:
	var count: int = Catalog.chapter(id).get("lines", []).size()
	return clampi(int(state.quest_progress.get("story", {}).get("pages", {}).get(id, 0)), 0, maxi(0, count - 1))

static func advance(state: PreparationState, id: String, skip := false) -> Dictionary:
	var chapter := Catalog.chapter(id)
	if chapter.is_empty() or pending(state) != id:
		return {"accepted": false, "reason": "当前没有这段待播剧情", "finished": false}
	var story: Dictionary = state.quest_progress.get("story", {}).duplicate(true)
	var next_page := page(state, id) + 1
	var finished: bool = skip or next_page >= chapter.lines.size()
	var pages: Dictionary = story.get("pages", {})
	if finished:
		var queue: Array = story.get("pending", [])
		queue.erase(id)
		story["pending"] = queue
		var seen: Array = story.get("seen", [])
		if id not in seen:
			seen.append(id)
		story["seen"] = seen
		pages.erase(id)
	else:
		pages[id] = next_page
	story["pages"] = pages
	state.quest_progress["story"] = story
	return {"accepted": true, "reason": "", "finished": finished}

static func can_replay(state: PreparationState, id: String) -> bool:
	if Catalog.chapter(id).is_empty():
		return false
	if id == "P00" or id in state.quest_progress.get("story", {}).get("seen", []):
		return true
	if id.begins_with("J"):
		for quest in Quests.all():
			if int(quest.node) == id.trim_prefix("J").to_int() and quest.id in state.quest_progress.get("claimed_quests", []):
				return true
	if id.begins_with("H"):
		var portraits: Array = Catalog.chapter(id).portraits
		return not portraits.is_empty() and portraits[0].hero_id in state.unlocked_heroes
	return id in state.quest_progress.get("story", {}).get("unlocked", [])
