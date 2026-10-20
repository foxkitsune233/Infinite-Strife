package com.example.douqiuqiu.equipment

import com.example.douqiuqiu.cards.CardEffectHost
import com.example.douqiuqiu.combat.ShieldEntry
import com.example.douqiuqiu.combat.ShieldLedger
import com.example.douqiuqiu.combat.ShieldKind
import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.status.CombatAction
import com.example.douqiuqiu.status.TimedControls
import com.example.douqiuqiu.status.ControlKind

/** Gear callbacks never roll a second ordinary attack and cannot recurse on their own damage. */
object EquipmentEffectRuntime {
    private fun ranged(f: Fighter) = f.heroId in setOf("skywrath", "necrophos", "viper")
    private fun control(host: CardEffectHost, actor: Int, effect: GearEffect): com.example.douqiuqiu.status.ControlApplication {
        val target = host.fighter(1 - actor)
        val kind = ControlKind.valueOf((effect.effect.params.getValue("control") as EffectValue.Choice).value)
        val duration = effect.number(if (ranged(target)) "rangedDurationMs" else "durationMs") / 1000.0
        return TimedControls.apply(target, effect.key, actor, kind, duration,
            when (effect.effect.dispelClass) {
                DispelClass.BASIC -> com.example.douqiuqiu.status.DispelClass.BASIC
                DispelClass.STRONG_ONLY -> com.example.douqiuqiu.status.DispelClass.STRONG_ONLY
                else -> com.example.douqiuqiu.status.DispelClass.UNDISPELLABLE
            }, host.cardTick)
    }
    private fun start(host: CardEffectHost, actor: Int, group: String, ms: Long): Boolean {
        val f = host.fighter(actor)
        val duration = (EquipmentCombat.cooldown(f.equipment, ms / 1000.0) * 1000).toLong().coerceAtLeast(1)
        val started = EquipmentCooldownTracker.tryStart(f.equipment.cooldowns, group, duration)
        if (started.started) host.update(actor, f.copy(equipment = f.equipment.copy(cooldowns = started.state)))
        return started.started
    }
    fun use(host: CardEffectHost, actor: Int, instanceId: String): Boolean {
        val f = host.fighter(actor)
        if (!FighterEffects.permission(f, CombatAction.ACTIVE_ITEM)) return false
        val item = EquipmentCombat.activeItems(f.equipment).find { it.instanceId == instanceId } ?: return false
        val effects = EquipmentCombat.effects(f.equipment).filter { it.item == item }
        val main = effects.firstOrNull { it.effect.trigger == EffectTrigger.ACTIVE || it.effect.trigger == EffectTrigger.LOW_HP } ?: return false
        val group = main.group
        if (group == "armlet" && EquipmentCombat.active(f.equipment, group)) return false
        val applied = if (main.effect.kind == EffectKind.APPLY_CONTROL) control(host, actor, main) else null
        if (applied != null && !applied.applied) {
            host.controlEvent(actor, 1 - actor, main.key, applied.reason, 0)
            return false
        }
        val cooldown = effects.maxOf { it.effect.internalCooldownMs ?: 0 }.coerceAtLeast(60000L.takeIf { group == "armlet" } ?: 500L)
        if (!start(host, actor, group, cooldown)) return false
        if (applied != null) {
            host.update(1 - actor, applied.fighter)
            host.controlEvent(actor, 1 - actor, main.key, applied.reason, applied.durationMs)
            protectOnControl(host, 1 - actor)
        }
        var current = host.fighter(actor)
        var duration = 0L
        when (group) {
            "mask_madness" -> duration = 4000
            "satanic" -> { current = FighterEffects.weakDispel(current); duration = 3000 }
            "bkb" -> {
                current = FighterEffects.weakDispel(current)
                current = current.copy(equipment = current.equipment.copy(immunityMs = 3000))
                duration = 3000
            }
            "armlet" -> {
                val max = current.maxHp + 550.0
                current = current.copy(maxHp = max, hp = current.hp / current.maxHp * max, regen = current.regen + 2.5)
                duration = 60000
            }
        }
        if (duration > 0) current = current.copy(equipment = current.equipment.copy(activeGroups = current.equipment.activeGroups + (group to duration)))
        for (effect in effects.filter { it.effect.trigger == EffectTrigger.ACTIVE && it.effect.kind == EffectKind.GRANT_SHIELD }) {
            val kind = if (effect.effect.shieldKind == com.example.douqiuqiu.content.ShieldKind.PHYSICAL) ShieldKind.PHYSICAL else ShieldKind.MAGICAL
            val shield = ShieldEntry("gear:$group", kind, effect.number("amount") * com.example.douqiuqiu.cards.CardCombat.shieldMultiplier(current.cardState), effect.number("durationMs").toLong())
            current = FighterEffects.withShields(current, ShieldLedger.apply(FighterEffects.shields(current), shield))
        }
        f.timedControls.filter { old -> current.timedControls.none { it.sourceId == old.sourceId && it.caster == old.caster && it.kind == old.kind } }
            .forEach { host.controlEvent(actor, actor, it.sourceId, "DISPELLED", 0) }
        if (f.equipment.immunityMs == 0L && current.equipment.immunityMs > 0) current.timedControls.forEach {
            host.controlEvent(actor, actor, it.sourceId, "IMMUNITY_SUPPRESSED", it.remainingMs)
        }
        host.update(actor, current)
        return true
    }
    private fun protectOnControl(host: CardEffectHost, actor: Int) {
        var f = host.fighter(actor)
        if (f.hp <= 0) return
        val currentControl = TimedControls.current(f)
        val controlled = currentControl.stunned || currentControl.hexed || currentControl.silenced || currentControl.disarmed
        val force = EquipmentCombat.effects(f.equipment).firstOrNull { it.effect.kind == EffectKind.ATTACK_PROTECTION }
        if (controlled && force != null) {
            val ranged = f.heroId in setOf("skywrath", "necrophos", "viper")
            val baseCd = force.effect.internalCooldownMs ?: 12000
            val cd = if (ranged) baseCd * 2 / 3 else baseCd
            if (start(host, actor, force.group, cd)) {
                f = host.fighter(actor)
                host.update(actor, f.copy(equipment = f.equipment.copy(protectionMs = force.number("durationMs").toLong(),
                    activeGroups = if (force.item.definitionId == "hurricane_pike") f.equipment.activeGroups + ("force_staff" to 3000L) else f.equipment.activeGroups)))
            }
        }
    }
    fun automatic(host: CardEffectHost, actor: Int) {
        if (host.fighter(actor).hp <= 0) return
        protectOnControl(host, actor)
        var f: Fighter
        for (item in EquipmentCombat.activeItems(host.fighter(actor).equipment)) {
            f = host.fighter(actor)
            if (item.instanceId in f.equipment.manual) continue
            val control = TimedControls.current(f)
            val negative = control.stunned || control.hexed || control.silenced || control.disarmed || control.muted ||
                f.broken > 0 || f.slowTime > 0 || f.skinSlowTime > 0 || f.poisonPool?.isEmpty() == false
            val target = host.fighter(1 - actor)
            val targetControl = TimedControls.current(target)
            val shouldUse = when (item.definitionId) {
                "black_king_bar" -> negative
                "satanic" -> f.hp / f.maxHp < .4
                "orchid" -> !targetControl.silenced && !targetControl.stunned && !targetControl.hexed && !targetControl.banished && target.hp > 0 && target.invulnerable <= 0 && target.equipment.immunityMs <= 0
                "heavens_halberd" -> !targetControl.disarmed && FighterEffects.permission(target, CombatAction.BASIC_ATTACK) && target.hp > 0 && target.invulnerable <= 0 && target.equipment.immunityMs <= 0
                "glacier_plate" -> !targetControl.frozen && target.hp > 0 && target.invulnerable <= 0 && target.equipment.immunityMs <= 0
                else -> true
            }
            if (shouldUse) use(host, actor, item.instanceId)
        }
    }
    fun trigger(host: CardEffectHost, actor: Int, trigger: EffectTrigger, roll: () -> Double, slash: Boolean = false) {
        val triggered = EquipmentCombat.effects(host.fighter(actor).equipment).filter { it.effect.trigger == trigger }
        // 霜棱之刃是一条 "命中时 30%：伤害并减速" 的同一次 proc，不是两条互相抢 CD 的独立掷点。
        // 该组先完成一次概率与冷却判定，再原子应用组内两个效果。
        val frostShard = triggered.filter { it.group == "frost_shard" }
        if (frostShard.isNotEmpty() && host.fighter(actor).hp > 0 && host.fighter(1 - actor).hp > 0) {
            val lead = frostShard.first()
            val cd = frostShard.maxOf { it.effect.internalCooldownMs ?: 0 }
            val chance = lead.number("chance", 1.0)
            if ((host.fighter(actor).equipment.cooldowns.remainingByGroup[lead.group] ?: 0) <= 0 &&
                (chance >= 1.0 || roll() < chance) && start(host, actor, lead.group, cd)) {
                for (bound in frostShard) when (bound.effect.kind) {
                    EffectKind.ADD_SPELL_DAMAGE -> EquipmentTargeting.select(1 - actor, host.enemyTargets(actor),
                        bound.number("maxTargets", 1.0).toInt()).forEach { target ->
                        host.damageCard(actor, target, bound.number("amount"), bound.effect.damageType!!,
                            bound.effect.attackClass!!, bound.effect.flags, bound.key)
                    }
                    EffectKind.SLOW -> {
                        val target = host.fighter(1 - actor)
                        val seconds = EquipmentCombat.statusDuration(target, bound.number("durationMs") / 1000.0)
                        host.update(1 - actor, target.copy(slow = maxOf(target.slow, bound.number("slowPercent")),
                            slowTime = maxOf(target.slowTime, seconds)))
                    }
                    else -> error("霜棱之刃包含了不支持的命中效果: ${bound.effect.id}")
                }
            }
        }
        for (bound in triggered.filterNot { it.group == "frost_shard" }) {
            if (host.fighter(actor).hp <= 0 || host.fighter(1 - actor).hp <= 0) break
            val cd = bound.effect.internalCooldownMs ?: 0
            if (cd > 0 && (host.fighter(actor).equipment.cooldowns.remainingByGroup[bound.group] ?: 0) > 0) continue
            val applied = if (bound.effect.kind == EffectKind.APPLY_CONTROL) control(host, actor, bound) else null
            if (applied != null && !applied.applied) {
                host.controlEvent(actor, 1 - actor, bound.key, applied.reason, 0)
                continue
            }
            val chance = bound.number(if (bound.effect.kind == EffectKind.APPLY_CONTROL && ranged(host.fighter(actor))) "rangedChance" else "chance", 1.0)
            if (chance < 1 && roll() >= chance) continue
            if (cd > 0 && !start(host, actor, bound.group, cd)) continue
            when (bound.effect.kind) {
                EffectKind.APPLY_CONTROL -> {
                    host.update(1 - actor, applied!!.fighter)
                    host.controlEvent(actor, 1 - actor, bound.key, applied.reason, applied.durationMs)
                    protectOnControl(host, 1 - actor)
                }
                EffectKind.ADD_PHYSICAL_DAMAGE, EffectKind.ADD_SPELL_DAMAGE -> EquipmentTargeting.select(1 - actor,
                    host.enemyTargets(actor), bound.number("maxTargets", 1.0).toInt()).forEach { target ->
                    host.damageCard(actor, target, bound.number("amount"), bound.effect.damageType!!, bound.effect.attackClass!!, bound.effect.flags, bound.key)
                }
                EffectKind.APPLY_NORMAL_POISON, EffectKind.APPLY_VIRULENT_POISON -> host.poisonCard(actor, 1 - actor, bound.key,
                    bound.effect.kind == EffectKind.APPLY_VIRULENT_POISON, bound.number("layers").toInt(),
                    bound.number(if (bound.effect.kind == EffectKind.APPLY_VIRULENT_POISON) "spawnDamagePerTick" else "damagePerTick"))
                EffectKind.SLOW -> {
                    // 寒霜减速：受状态抗性压缩时长，取更高减速值与更长时间，普通驱散可清。
                    val target = host.fighter(1 - actor)
                    val seconds = EquipmentCombat.statusDuration(target, bound.number("durationMs") / 1000.0)
                    host.update(1 - actor, target.copy(
                        slow = maxOf(target.slow, bound.number("slowPercent")),
                        slowTime = maxOf(target.slowTime, seconds)))
                }
                EffectKind.ATTACK_SPEED_STACK -> {
                    // 怒火叠层：同源取更高值并续期，层时长由 stackDurationMs 控制。
                    val f = host.fighter(actor)
                    val seconds = (bound.effect.stackDurationMs ?: 4000L) / 1000.0
                    host.update(actor, f.copy(
                        speedBuff = maxOf(f.speedBuff, bound.number("amount")),
                        speedBuffMs = maxOf(f.speedBuffMs, seconds)))
                }
                else -> Unit
            }
        }
    }
}
