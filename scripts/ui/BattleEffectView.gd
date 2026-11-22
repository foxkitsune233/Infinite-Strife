class_name BattleEffectView
extends Control

var _left_anchor := Vector2.ZERO
var _right_anchor := Vector2.ZERO
var _right_actor_id := "right"

func set_stage_anchors(left: Vector2, right: Vector2, right_actor_id: String = "right") -> void:
	_left_anchor = left
	_right_anchor = right
	_right_actor_id = right_actor_id

static func style_for(event: Dictionary) -> Dictionary:
	var kind := str(event.get("kind", ""))
	if kind == "skill_reflected":
		return {"text": "技能反射", "color": Color("b2b0ff"), "duration_ms": 620}
	if event.get("runtime_mode") == "hero_aghanim":
		var title: String = {"hero:juggernaut:aghanim": "小无敌斩", "hero:skywrath:aghanim": "沉默",
			"hero:abaddon:aghanim": "回光待发", "hero:phantom_assassin:aghanim": "致命连击",
			"hero:viper:aghanim": "毒龙护体", "hero:necrophos:aghanim": "死亡脉冲",
			"hero:anti_mage:aghanim": "反射窗口", "hero:faceless_void:aghanim": "时间回溯",
			"hero:axe:aghanim": "淘汰", "hero:legion_commander:aghanim": "强攻"}.get(event.get("effect_id"), "神杖")
		return {"text": title, "color": Color("7eddf0"), "duration_ms": 760}
	if event.get("effect_id") == "gear:blade_mail:reflect":
		return {"text": "反伤 %d" % int(event.get("amount", 0)), "color": Color(1.0, 0.6, 0.42), "duration_ms": 520}
	if event.get("effect_id") == "gear:monkey_king_bar:proc":
		return {"text": "金箍棒 %d" % int(event.get("amount", 0)), "color": Color(1.0, 0.82, 0.32), "duration_ms": 520}
	if kind == "physical_attack":
		if event.get("critical", false):
			return {"text": "暴击 %d" % int(event.get("amount", 0)), "color": Color(1.0, 0.72, 0.18), "duration_ms": 520}
		return {"text": "%d" % int(event.get("amount", 0)), "color": Color(0.9, 0.96, 1.0), "duration_ms": 360}
	if kind == "skill_cast":
		return {"text": "技能 %d" % int(event.get("amount", 0)), "color": Color(0.52, 0.86, 1.0), "duration_ms": 620}
	if kind == "dot_tick":
		return {"text": "持续伤害 %d" % int(event.get("amount", 0)), "color": Color(0.58, 0.94, 0.44), "duration_ms": 420}
	if kind == "active_used":
		match str(event.get("school_id", "")):
			"magic_damage", "ethereal":
				return {"text": "装备 %d" % int(event.get("amount", 0)), "color": Color(1.0, 0.48, 0.36), "duration_ms": 520}
			"break":
				return {"text": "破被动", "color": Color(0.81, 0.65, 1.0), "duration_ms": 520}
			"purge_positive":
				return {"text": "驱散 · 禁增益", "color": Color(0.84, 0.66, 1.0), "duration_ms": 520}
			"reflect":
				return {"text": "反伤开启", "color": Color(1.0, 0.6, 0.42), "duration_ms": 520}
			"control":
				var text: String = {"silence": "沉默", "stun": "眩晕", "hex": "妖术", "banish": "放逐", "disarm": "缴械"}.get(event.get("control"), "控制")
				return {"text": text, "color": Color(0.81, 0.65, 1.0), "duration_ms": 520}
			"shield", "aeon_disk":
				return {"text": "护盾", "color": Color(0.42, 0.82, 1.0), "duration_ms": 520}
			"heal":
				return {"text": "治疗", "color": Color(0.4, 1.0, 0.66), "duration_ms": 520}
			"poison":
				return {"text": "毒蚀", "color": Color(0.58, 0.94, 0.44), "duration_ms": 520}
			_:
				return {"text": "激活", "color": Color(1.0, 0.82, 0.32), "duration_ms": 460}
	return {"text": "", "color": Color.WHITE, "duration_ms": 0}

func show_event(event: Dictionary) -> void:
	# 关闭动画时不生成飘字与粒子特效；战斗数值与状态仍照常结算（对齐 Android animations 开关）。
	if not preload("res://scripts/app/AppSettings.gd").get_flag("animations"):
		return
	var style := style_for(event)
	if not str(style["text"]).is_empty():
		_spawn_number(event, style)
	var kind := str(event.get("kind", ""))
	if event.get("runtime_mode") == "hero_aghanim":
		if event.get("effect_id") in ["hero:juggernaut:aghanim", "hero:abaddon:aghanim", "hero:phantom_assassin:aghanim", "hero:viper:aghanim", "hero:anti_mage:aghanim", "hero:faceless_void:aghanim"]:
			var self_event := event.duplicate()
			self_event.target_id = event.source_id
			_spawn_aura(self_event, style["color"], "✦")
		else: _spawn_projectile(event, style["color"])
	elif kind == "skill_reflected": _spawn_aura(event, style["color"], "◌")
	elif kind == "skill_cast": _spawn_projectile(event, Color(0.5, 0.74, 1.0))
	elif kind == "dot_tick": _spawn_aura(event, Color(0.36, 0.92, 0.38, 0.36), "☠")
	elif kind == "active_used":
		if event.get("school_id") in ["magic_damage", "ethereal"]: _spawn_projectile(event, style["color"])
		else: _spawn_aura(event, style["color"], "◌")

func _spawn_number(event: Dictionary, style: Dictionary) -> void:
	var label := Label.new()
	label.text = style["text"]
	label.add_theme_font_size_override("font_size", 30 if event.get("critical", false) else 20)
	label.add_theme_color_override("font_color", style["color"])
	label.add_theme_color_override("font_shadow_color", Color(0.01, 0.03, 0.06, 0.94))
	label.add_theme_constant_override("shadow_offset_x", 2)
	label.add_theme_constant_override("shadow_offset_y", 2)
	label.position = event_position(event)
	add_child(label)
	var duration := float(style["duration_ms"]) / 1000.0
	var tween := create_tween().set_parallel(true)
	tween.tween_property(label, "position:y", label.position.y - 46.0, duration)
	tween.tween_property(label, "scale", Vector2.ONE * (1.25 if event.get("critical", false) else 1.0), duration * 0.3)
	tween.tween_property(label, "modulate:a", 0.0, duration * 0.62).set_delay(duration * 0.32)
	tween.chain().tween_callback(label.queue_free)

func _spawn_projectile(event: Dictionary, color: Color) -> void:
	var spark := Label.new()
	spark.text = "✦"
	spark.add_theme_font_size_override("font_size", 32)
	spark.add_theme_color_override("font_color", color)
	spark.position = _source_position(event)
	add_child(spark)
	var tween := create_tween().set_parallel(true)
	tween.tween_property(spark, "position", event_position(event), 0.26)
	tween.tween_property(spark, "rotation", TAU, 0.26)
	tween.tween_property(spark, "scale", Vector2.ONE * 1.45, 0.26)
	tween.chain().tween_callback(spark.queue_free)

func _spawn_aura(event: Dictionary, color: Color, glyph: String) -> void:
	var aura := Label.new()
	aura.text = glyph
	aura.add_theme_font_size_override("font_size", 48)
	aura.add_theme_color_override("font_color", color)
	aura.position = event_position(event) - Vector2(18, 24)
	add_child(aura)
	var tween := create_tween().set_parallel(true)
	tween.tween_property(aura, "scale", Vector2.ONE * 1.65, 0.42)
	tween.tween_property(aura, "modulate:a", 0.0, 0.42)
	tween.chain().tween_callback(aura.queue_free)

func event_position(event: Dictionary) -> Vector2:
	var target := str(event.get("target_id", ""))
	if target == _right_actor_id or target in ["right", "gale", "enemy"]: return _right_anchor
	return _left_anchor

func _source_position(event: Dictionary) -> Vector2:
	var source := str(event.get("source_id", ""))
	return _right_anchor if source == _right_actor_id or source in ["right", "gale", "enemy"] else _left_anchor
