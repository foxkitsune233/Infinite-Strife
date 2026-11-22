extends RefCounted

const Heroes = preload("res://scripts/content/AndroidHeroCatalog.gd")
static var _data: Dictionary = {}

static func data() -> Dictionary:
	if _data.is_empty():
		var parsed: Variant = JSON.parse_string(FileAccess.get_file_as_string("res://data/android/story.json"))
		if parsed is Dictionary:
			_data = parsed
	return _data

static func chapter(id: String, hero_id := "juggernaut") -> Dictionary:
	for entry in data().get("chapters", []):
		if entry.id != id:
			continue
		var result: Dictionary = entry.duplicate(true)
		var hero_name := str(Heroes.hero(hero_id).get("display_name", hero_id))
		for portrait in result.portraits:
			if portrait.hero_id == "current":
				portrait.hero_id = hero_id
				portrait.display_name = hero_name
		for line in result.lines:
			if line.speaker_id == "current":
				line.speaker_id = hero_id
				line.speaker_name = hero_name
		return result
	for ending_entry in data().get("endings", []):
		if ending_entry.id == id:
			var result: Dictionary = ending_entry.duplicate(true)
			result["portraits"] = []
			result["lines"] = []
			for page_text in result.pages:
				result.lines.append({"speaker_id": "", "speaker_name": "", "text": page_text})
			return result
	return {}

static func ending(hero_id: String) -> Dictionary:
	for entry in data().get("endings", []):
		if entry.hero_id == hero_id:
			return entry.duplicate(true)
	return {}
