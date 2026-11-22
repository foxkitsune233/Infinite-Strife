extends SceneTree

const Controller = preload("res://scripts/ui/BattlePresentationController.gd")

func _init() -> void:
	var controller = Controller.new(true)
	var normal: Dictionary = controller.push_event({"kind": "physical_attack", "source_id": "left", "target_id": "right", "amount": 40})
	assert(normal.target == "right")
	assert(normal.target_flash > 0.0)
	assert(normal.target_knockback > 0.0)
	assert(is_zero_approx(normal.slow_motion_ms))

	var critical: Dictionary = controller.push_event({"kind": "physical_attack", "source_id": "left", "target_id": "right", "amount": 80, "critical": true})
	assert(is_zero_approx(critical.camera_shake))
	assert(critical.slow_motion_ms == 90.0)
	assert(critical.damage_scale > normal.damage_scale)

	var skill: Dictionary = controller.push_event({"kind": "skill_cast", "source_id": "right", "target_id": "left", "amount": 120})
	assert(skill.source_pose == "cast")
	assert(skill.projectile == "arcane")
	assert(skill.slow_motion_ms == 140.0)

	var burst = Controller.new(true)
	assert(_hit(burst, 0, 25, "right") == 0.0)
	assert(_hit(burst, 100, 25, "left") == 0.0)
	assert(_hit(burst, 300, 25, "right") > 0.0)
	assert(_hit(burst, 400, 250, "right") == 0.0)
	assert(_hit(burst, 799, 50, "right") == 0.0)
	assert(_hit(burst, 800, 50, "right") > 0.0)
	assert(_hit(burst, 1500, 25, "right") == 0.0)
	assert(_hit(burst, 2001, 25, "right") == 0.0)
	assert(_hit(burst, 2100, 0, "right") == 0.0)
	assert(_hit(burst, 2200, 25, "right") > 0.0)
	assert(_hit(Controller.new(true), 0, 49, "right") == 0.0)
	assert(is_equal_approx(_hit(Controller.new(true), 0, 50, "right"), 4.0))
	assert(is_equal_approx(_hit(Controller.new(true), 0, 225, "right"), 8.0))
	assert(is_equal_approx(_hit(Controller.new(true), 0, 400, "right"), 12.0))
	assert(is_equal_approx(_hit(Controller.new(true), 0, 600, "right"), 12.0))

	controller.advance(0.7)
	assert(is_zero_approx(float(controller.side_state("left").flash)))
	assert(is_zero_approx(controller.camera_offset().length()))

	var reduced = Controller.new(false)
	var disabled: Dictionary = reduced.push_event({"kind": "skill_cast", "source_id": "left", "target_id": "right", "amount": 1})
	assert(disabled.keep_number)
	assert(is_zero_approx(disabled.camera_shake))
	assert(disabled.projectile.is_empty())
	quit(0)

func _hit(controller: Controller, tick: int, amount: int, target: String) -> float:
	return controller.push_event({"kind": "physical_attack", "target_id": target, "tick_ms": tick, "amount": amount}, 1000.0).camera_shake
