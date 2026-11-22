extends SceneTree

func _init() -> void:
	call_deferred("_run")

func _run() -> void:
	# Explicit recipe ingredients; production starter inventory no longer includes
	# retired prototype materials and need not guarantee a craftable recipe.
	var state := PreparationState.new("juggernaut")
	state.add_item(ItemInstance.new("ore", "ember_ore", ItemInstance.Kind.MATERIAL))
	state.add_item(ItemInstance.new("branch", "iron_branch", ItemInstance.Kind.EQUIPMENT))
	root.get_node("AppSession").preparation_state = state
	var screen := preload("res://scenes/Crafting.tscn").instantiate()
	root.add_child(screen)
	await process_frame
	var recipes := screen.get_node("Layout/Scroll/Recipes") as GridContainer
	if recipes.get_child_count() == 0:
		push_error("crafting screen must render recipe entries")
		quit(1)
		return
	var first := recipes.get_child(0) as Button
	if not first.text.contains("可合成"):
		push_error("craftable recipes must render before unavailable recipes")
		quit(1)
		return
	quit(0)
