class_name ScreenRouter
extends RefCounted

const PATHS := {
	"home": "res://scenes/MainMenu.tscn",
	"preparation": "res://scenes/Preparation.tscn",
	"crafting": "res://scenes/Crafting.tscn",
	"shop": "res://scenes/Shop.tscn",
	"expedition": "res://scenes/Expedition.tscn",
	"battle": "res://scenes/Battle.tscn",
	"progression": "res://scenes/Progression.tscn",
	"tower": "res://scenes/Tower.tscn",
	"settings": "res://scenes/Settings.tscn",
}

static func path_for(screen_id: String) -> String:
	return PATHS.get(screen_id, "")

static func open(tree: SceneTree, screen_id: String) -> Error:
	var path := path_for(screen_id)
	if path.is_empty():
		return ERR_INVALID_PARAMETER
	return tree.change_scene_to_file(path)
