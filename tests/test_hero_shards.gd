extends SceneTree
const F = preload("res://scripts/battle/FighterState.gd")
const R = preload("res://scripts/battle/AndroidHeroRuntime.gd")
var failures := 0
func check(ok: bool, label: String) -> void:
	if not ok:
		failures += 1
		push_error(label)
func unit(hero: String, shard := true) -> FighterState:
	var f := F.new(hero, 10000, 100, 1000, 0, 1000)
	f.hero_id = hero
	f.hero_state.has_shard = shard
	return f
func _init() -> void:
	var r := R.new(1)
	var a := unit("juggernaut")
	var b := unit("")
	a.hp = 500
	a.hero_timers.critical_ready = 1
	r.attack(a,b,0)
	check(is_equal_approx(a.hp,620), "jug crit heals 40 percent life damage")
	a = unit("juggernaut",false); a.hp=500; a.hero_timers.critical_ready=1
	r.attack(a,unit(""),0)
	check(a.hp==500,"unowned jug off")
	a = unit("juggernaut"); a.hp=500; a.hero_timers.critical_ready=1; a.add_status("break",1000)
	r.attack(a,unit(""),0)
	check(a.hp==500,"break suppresses shard heal")
	a = unit("skywrath"); a.mp=0
	check(r.cast(a,unit(""),{},0).accepted,"sky free cast with zero mana")
	a = unit("skywrath"); b=unit(""); a.mana_regen=10000
	r.cast(a,b,{},0)
	for i in 300:
		a.advance_time(100); r.advance(a,b,100,i*100,false,false)
	check(not bool(a.hero_state.get("shard_flare_active",false)),"sky exponential drain terminates despite regen")
	a = unit("phantom_assassin"); a.hero_state.evasion_chance=0.5
	var misses := 0
	for i in 1000:
		b=unit(""); a.hp=10000; r.attack(b,a,0)
		if a.hp==10000: misses+=1
	check(misses>600 and misses<700,"PA multiplicative gear evasion about 65 percent")
	a=unit("necrophos"); a.hp=500; b=unit("")
	r.advance(a,b,1000,1000,false,false)
	check(a.hp==540,"nec heals half heartstopper life damage")
	a=unit("abaddon"); b=unit(""); r.attack(a,b,0)
	check(b.hp==9888,"aba frost damage on attack")
	var frozen := false
	for i in 100:
		b=unit(""); r.attack(a,b,0); frozen = frozen or b.has_status("freeze")
	check(frozen,"aba frost can freeze")
	a=unit("viper"); b=unit(""); r.cast(a,b,{},0)
	check(b.has_status("shard_poison_armor"),"viper poison reduces armor")
	var before := b.hp
	r.damage(unit(""),b,100,"physical","attack",true)
	check(before-b.hp>100,"poison armor increases physical damage")
	a=unit("anti_mage"); b=unit("")
	r.damage(b,a,100,"magic","gear:proc")
	check(a.hp==9915,"AM independent magic resistance")
	r.external_damage(b,a,100,"magic","card:bolt",0)
	check(bool(a.hero_state.get("shard_reflect_used",false)) and b.hp==9900,"AM first hostile skill reflection")
	r.external_damage(b,a,100,"magic","card:bolt",0)
	check(a.hp==9830,"AM reflection only once")
	var copy := F.from_data(JSON.parse_string(JSON.stringify(a.to_data())))
	check(copy!=null and copy.hero_state.shard_reflect_used,"reflection survives JSON save")
	a=unit("faceless_void"); b=unit(""); b.cooldowns.spell=5000
	r.initialize(a); a.advance_time(6000); r.advance(a,b,100,6000,false,false)
	check(int(b.cooldowns.spell)==6000,"void extends existing cooldowns")
	r.advance(a,b,1000,7000,false,false)
	check(b.hp<10000,"void dilation DOT")
	a=unit("axe"); b=unit(""); r.attack(a,b,0)
	check(b.has_status("shard_hunger"),"axe applies hunger")
	before=b.hp; r.damage(unit(""),b,100,"pure","test")
	check(is_equal_approx(before-b.hp,110),"hunger vulnerability")
	before=b.hp; r.advance(a,b,1000,1000,false,false)
	check(b.hp<before,"hunger DOT")
	a=unit("legion_commander"); b=unit(""); r.initialize(a); a.advance_time(8000); a.apply_control("stun",1000,"test","other","strong_only")
	r.advance(a,b,100,8000,false,false)
	check(not a.has_status("stun"),"legion periodic strong dispel")
	a=unit("phantom_assassin"); b=unit(""); a.critical_chance_percent=100; a.hero_timers.critical_ready=100; a.hero_timers.aghanim_crit_lock=5000
	r.attack(a,b,0)
	check(b.hp==9900,"aghanim lock blocks every crit source")
	a=unit("anti_mage"); a.add_status("break",1000); b=unit("")
	r.external_damage(b,a,100,"magic","card:bolt",0)
	check(a.hp==9900 and not a.hero_state.get("shard_reflect_used",false),"break suppresses AM passive")
	a=unit("anti_mage"); b=unit("faceless_void")
	r.cast(b,a,{},0)
	check(b.has_status("time_stop") and not a.has_status("time_stop"),"non damage control reflects with one mana payment")
	check(b.mp<1000 and a.mp==1000,"control reflection pays original caster once")
	a=unit("anti_mage"); b=unit("anti_mage")
	r.external_damage(b,a,100,"magic","card:bolt",0)
	check(not b.hero_state.get("shard_reflect_used",false),"reflections never recurse")
	a=unit("anti_mage",false); a.hero_timers.aghanim_reflect=500; b=unit("")
	r.external_damage(b,a,100,"magic","card:bolt",0)
	check(a.hp==10000 and b.hp==9900,"aghanim reflection window independent of shard")
	a=unit("phantom_assassin"); b=unit(""); a.critical_chance_percent=100; a.hero_timers.critical_ready=100; a.hero_timers.aghanim_crit_lock=5000
	r.attack(a,b,0)
	check(a.timer("critical_ready")==100,"crit lock preserves ready charge")
	for hero in ["faceless_void","legion_commander"]:
		a=unit(hero); r.initialize(a); a.advance_time(2300)
		var saved_timer := a.timer("shard_period")
		copy=F.from_data(JSON.parse_string(JSON.stringify(a.to_data())))
		r.initialize(copy)
		check(copy.timer("shard_period")==saved_timer,"partial period survives initialize "+hero)
	for hero in ["abaddon","axe","necrophos","faceless_void","legion_commander"]:
		a=unit(hero); a.add_status("break",10000); a.hp=500; b=unit("")
		r.attack(a,b,0);
		r.advance(a,b,1000,1000,false,false)
		check(not b.has_status("shard_hunger") and not b.has_status("freeze") and not b.has_status("shard_dilation"),"break gates shard "+hero)
		if hero=="necrophos": check(a.hp==500,"break gates heartstopper heal")
	a=unit("viper"); b=unit(""); r.cast(a,b,{},0); b.dispel(true)
	check(not b.has_status("shard_poison_armor"),"poison armor dispellable")
	a=unit("skywrath",false); a.mp=0
	check(not r.cast(a,unit(""),{},0).accepted,"unowned sky still requires mana")
	a=unit("anti_mage"); b=unit(""); r.external_damage(b,a,0,"magic","card:zero",0)
	check(not a.hero_state.get("shard_reflect_used",false),"zero damage does not spend reflection")
	for effect in ["item:dagon","gear:proc","dot:burn","hero:viper:skin"]:
		a=unit("anti_mage"); b=unit("")
		r.external_damage(b,a,100,"magic",effect,0)
		check(not a.hero_state.get("shard_reflect_used",false),"non skill excluded "+effect)
	a=unit("anti_mage"); b=unit("")
	r.external_damage(b,a,100,"magic","effect_card_burn",0,false)
	check(not a.hero_state.get("shard_reflect_used",false),"explicit DOT bypasses reflection")
	a=unit("anti_mage"); b=unit("axe")
	r.damage(b,a,400,"pure","hero:axe:aghanim")
	check(a.hp==10000 and b.hp==9600,"damaging aghanim reflected")
	a=unit("phantom_assassin"); a.add_status("break",1000)
	a.hero_state.evasion_chance=1.0; b=unit(""); r.attack(b,a,0)
	check(a.hp==10000,"break preserves gear evasion")
	a=unit("necrophos"); a.hp=500; b=unit(""); b.shield=1000
	r.advance(a,b,1000,1000,false,false)
	check(a.hp==500,"heartstopper shield absorption never heals")
	a=unit("axe"); b=unit(""); b.hero_timers.invulnerable=1000
	r.attack(a,b,0)
	check(not b.has_status("shard_hunger"),"blocked attacks do not apply shard")
	var engine_script = load("res://scripts/battle/BattleEngine.gd")
	for hero in ["juggernaut","skywrath","phantom_assassin","abaddon","necrophos","viper","anti_mage","faceless_void","axe","legion_commander"]:
		a=unit(hero); b=unit(""); a.attack_damage=1; b.attack_damage=1
		var engine: BattleEngine = engine_script.new(711,a,b)
		if hero=="skywrath": r.cast(a,b,{},0)
		engine.step(2300)
		var restored: BattleEngine = engine_script.from_state(JSON.parse_string(JSON.stringify(engine.export_state())))
		check(restored!=null,"shard engine restores "+hero)
		if restored!=null:
			check(restored.step(1600)==engine.step(1600),"shard continuation events "+hero)
			check(restored.export_state()==engine.export_state(),"shard continuation state "+hero)
	print("Shard checks failures: ",failures)
	quit(0 if failures==0 else 1)
