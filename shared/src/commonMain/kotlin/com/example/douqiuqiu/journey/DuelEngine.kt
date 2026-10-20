package com.example.douqiuqiu.journey

import com.example.douqiuqiu.combat.*
import com.example.douqiuqiu.content.AttackClass
import com.example.douqiuqiu.content.DamageType
import com.example.douqiuqiu.content.EffectFlags
import com.example.douqiuqiu.content.EffectKind
import com.example.douqiuqiu.content.EffectTrigger
import com.example.douqiuqiu.content.HeroContent
import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.equipment.*
import com.example.douqiuqiu.status.CombatAction
import kotlin.math.max
import kotlin.math.min

/** 多平台安全的 1 位小数格式化（Kotlin/JS 不支持 JVM 的 String.format）。 */
private fun format1(value: Double): String {
    val scaled = (value * 10.0 + if (value >= 0) 0.5 else -0.5).toLong()
    val whole = scaled / 10L
    val frac = kotlin.math.abs(scaled % 10L)
    return "$whole.$frac"
}

/** One deterministic 100ms step. Every output contains everything needed to resume. */
object DuelEngine {
    fun useItem(state: DuelState, instanceId: String): DuelState {
        if (state.won != null || state.player.hp <= 0) return state
        return Tick(state, manual = true).use(instanceId)
    }
    fun step(state: DuelState): DuelState = stepWithCues(state).state

    fun stepWithCues(state: DuelState): DuelFrame {
        if (state.won != null) return DuelFrame(state)
        if (state.player.hp <= 0 || state.enemy.hp <= 0 || state.tick >= 600) return DuelFrame(finish(state))
        val step = Tick(state)
        val next = step.run()
        return DuelFrame(next, step.cues.toList())
    }
    private fun finish(s: DuelState): DuelState = s.copy(won = when {
        s.player.hp <= 0 -> false
        s.enemy.hp <= 0 -> true
        else -> s.player.hp / s.player.maxHp > s.enemy.hp / s.enemy.maxHp
    })

    private class Tick(val original: DuelState, val manual: Boolean = false) : CardEffectHost {
        val f = mutableListOf(original.player, original.enemy)
        var seed = original.rng
        val messages = original.log.toMutableList()
        val cues = mutableListOf<DuelVisualCue>()
        fun cue(actor: Int, kind: DuelCueKind) { cues += DuelVisualCue(cardTick, actor, 1 - actor, f[actor].heroId, kind) }
        override val cardTick: Int get() = original.tick + if (manual) 0 else 1
        fun use(id: String): DuelState {
            if (!EquipmentEffectRuntime.use(this, 0, id)) return original
            return original.copy(player = f[0], enemy = f[1], rng = seed, log = (messages + "使用装备").takeLast(LOG_CAP))
        }
        override fun fighter(index: Int) = f[index]
        override fun update(index: Int, fighter: Fighter) { f[index] = fighter }
        override fun controlEvent(source: Int, target: Int, sourceId: String, result: String, durationMs: Long) {
            log("控制[$sourceId] $source->$target $result ${durationMs}ms")
        }
        override fun healCard(index: Int, amount: Double) = heal(index, amount)
        override fun damageCard(source: Int, target: Int, amount: Double, type: DamageType, attackClass: AttackClass, flags: EffectFlags, effectId: String) {
            damage(target, amount, type != DamageType.PHYSICAL, source, damageType = type, attackClass = attackClass, flags = flags, effectId = effectId)
        }
        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) {
            val actor = f[source]
            f[target] = PoisonCombat.apply(f[target], source, sourceId, actor.poisonSerial, cardTick, virulent, layers, damage)
            f[source] = actor.copy(poisonSerial = actor.poisonSerial + 1)
        }
        override fun roll(): Double { seed = FirstRunContent.next(seed); return seed / 2147483647.0 }
        fun log(text: String) { messages += text }
        fun run(): DuelState {
            // 开局效果属于战斗本身，而非可被存档／重建过程改变的卡牌运行时标记。
            // 首帧恢复的战斗也必须保留 U4 的免费施放资格。
            val opening = original.tick == 0
            for (i in 0..1) {
                val c = f[i]
                // 时停暂停目标的技能 CD 与装备 CD 倒计时（0.1.5 时间结界语义）。
                val stopped = com.example.douqiuqiu.status.TimedControls.current(c).timeStopped
                f[i] = FighterEffects.withShields(c, ShieldLedger.elapse(FighterEffects.shields(c), 100)).copy(mana = min(c.maxMana, c.mana + c.manaRegen * .1 * if (c.ghost > 0) 1.5 else 1.0),
                    attackIn = if (stopped) c.attackIn else max(0.0, c.attackIn - .1), ultIn = if (stopped) c.ultIn else max(0.0, c.ultIn - .1),
                    invulnerable = max(0.0, c.invulnerable - .1), ghost = max(0.0, c.ghost - .1),
                    slashIn = max(0.0, c.slashIn - .1), criticalReady = max(0.0, c.criticalReady - .1),
                    broken = max(0.0, c.broken - .1), slowTime = max(0.0, c.slowTime - .1),
                    skinIn = max(0.0, c.skinIn - .1), skinSlowTime = max(0.0, c.skinSlowTime - .1), flareIn = if (stopped) c.flareIn else c.flareIn - .1,
                    equipment = if (stopped) c.equipment.copy(started = true) else EquipmentCombat.elapse(c.equipment).copy(started = true),
                    // 时停只暂停行动、技能 CD 和装备 CD；控制本身必须继续随战斗时钟到期。
                    timedControls = com.example.douqiuqiu.status.TimedControls.elapse(c.timedControls),
                    cardState = c.cardState.copy(started = true, buffs = c.cardState.buffs.filter { it.expiresAtTick > cardTick }),
                    amBurnReady = max(0.0, c.amBurnReady - .1),
                    armorBuff = if (max(0.0, c.armorBuffMs - .1) <= 0) 0.0 else c.armorBuff, armorBuffMs = max(0.0, c.armorBuffMs - .1),
                    speedBuff = if (max(0.0, c.speedBuffMs - .1) <= 0) 0.0 else c.speedBuff, speedBuffMs = max(0.0, c.speedBuffMs - .1))
                c.timedControls.filter { it.remainingMs <= 100 }.forEach { log("控制[${it.sourceId}] EXPIRED") }
                if (c.equipment.immunityMs > 0 && f[i].equipment.immunityMs == 0L) f[i].timedControls.forEach {
                    log("控制[${it.sourceId}] IMMUNITY_ENDED ${it.remainingMs}ms")
                }
            }
            // U4 resolves at battle start, before automatic items can self-silence their owner
            // or the opponent can apply a fresh control. Mana and ordinary cooldowns are waived.
            if (opening) for (i in 0..1) {
                if (f[i].hp > 0 && f[1 - i].hp > 0 &&
                    CardCombat.effects(f[i].cardState).any { it.effect.kind == EffectKind.FREE_ULT_CAST }) cast(i, free = true)
            }
            // Advance both clocks before either unit can apply a fresh control this frame.
            for (i in 0..1) {
                val c = if (i == 0) original.player else original.enemy
                heal(i, c.regen * .1, c.ghost > 0)
                if (!com.example.douqiuqiu.status.TimedControls.current(f[i]).timeStopped) EquipmentEffectRuntime.automatic(this, i)
                if (!com.example.douqiuqiu.status.TimedControls.current(f[i]).timeStopped && EquipmentCombat.active(f[i].equipment, "armlet")) {
                    damage(i, 4.0, true, i, damageType = DamageType.PURE, attackClass = AttackClass.SELF_DAMAGE, flags = EffectFlags(isSelfDamage = true))
                }
            }
            val order = if (roll() < .5) listOf(0, 1) else listOf(1, 0)
            for (i in order) {
                CardEffectRuntime.fire(this, i, EffectTrigger.HEAL_ACCUMULATED)
                CardEffectRuntime.fire(this, i, EffectTrigger.INTERVAL)
                CardEffectRuntime.fire(this, i, EffectTrigger.LOW_HP)
            }
            // Both existing damage streams advance before resolving simultaneous deaths.
            for (i in 0..1) {
                // 毒层使用战斗绝对 tick 作为到期基准。时停不暂停它的时钟，避免保存时留下
                // 已经过期的层；这也使持续伤害与其它绝对时钟保持同一语义。
                val (remaining, hits) = FighterEffects.advancePoison(f[i])
                f[i] = f[i].copy(poisons = remaining)
                hits.forEach { damage(i, it.amount, true, 1 - i, isUltimate = it.sourceId == "viper", effectId = "poison:${it.sourceId}") }
                val (advanced, coreTicks) = PoisonCombat.advance(f[i], cardTick)
                f[i] = advanced
                coreTicks.forEach { tick ->
                    val caster = tick.casterUnitId.toInt()
                    val frost = CardCombat.isFrostDotSource(tick.sourceId)
                    val hit = damage(i, tick.amount, true, caster, isUltimate = tick.sourceId == "hero:viper:ultimate",
                        effectId = "${if (frost) "frostcore" else "poisoncore"}:${tick.sourceId}")
                    if (hit != null && !frost) CardEffectRuntime.firePoisonTick(this, caster, hit.lifeDamage, hit.shieldDamage)
                }
            }
            for (i in order) {
                if (f[i].hp <= 0 || f[1 - i].hp <= 0) break
                if (com.example.douqiuqiu.status.TimedControls.current(f[i]).timeStopped) continue
                if (f[i].heroId == "necrophos" && f[i].broken <= 0) damage(1 - i, f[1 - i].maxHp * .008 * .1, true, i, removal = true)
                if (f[i].flareLeft > 0 && f[i].flareIn <= .00001) {
                    cue(i, DuelCueKind.FLARE_TICK)
                    f[i] = f[i].copy(flareLeft = f[i].flareLeft - 1, flareIn = f[i].flareIn + .25)
                    damage(1 - i, HeroContent.legacy("skywrath").damagePerHit, true, i, isUltimate = true)
                }
                if (f[i].hp <= 0 || f[1 - i].hp <= 0) break
                cast(i)
                if (f[i].invulnerable > 0 && f[i].heroId == "juggernaut" && FighterEffects.permission(f[i], CombatAction.BASIC_ATTACK)) {
                    if (f[i].slashIn <= .00001) { attack(i, slash = true); f[i] = f[i].copy(slashIn = interval(i) / 1.4) }
                } else if (FighterEffects.permission(f[i], CombatAction.BASIC_ATTACK) && f[i].attackIn <= .00001) {
                    attack(i); f[i] = f[i].copy(attackIn = interval(i))
                }
            }
            var next = DuelState(f[0], f[1], seed, original.tick + 1, log = messages.takeLast(LOG_CAP))
            if (f[0].hp <= 0 || f[1].hp <= 0 || next.tick >= 600) next = finish(next)
            return next
        }
        fun interval(i: Int): Double {
            val c = f[i]
            val curseBonus = if (c.heroId == "abaddon" && c.broken <= 0 && f[1 - i].poisons.any { it.source == "curse" }) 20 else 0
            val slow = (if (c.slowTime > 0) c.slow else 0.0) + (if (c.skinSlowTime > 0) 15.0 else 0.0)
            val omniBonus = if (c.heroId == "juggernaut" && c.invulnerable > 0)
                c.speed * HeroContent.legacy("juggernaut").attackSpeedPercent / 100.0 else 0.0
            val speed = max(20.0, (c.speed + c.speedBuff + omniBonus) + curseBonus - slow + CardCombat.buffAmount(c.cardState, CardBuffKind.ATTACK_SPEED) + EquipmentCombat.speed(c.equipment))
            // 寒霜减速：按百分比延长攻击间隔（0.1.5）；封顶 80% 防止间隔趋零。
            val slowPct = CardCombat.buffAmount(c.cardState, CardBuffKind.SLOW).coerceIn(0.0, 80.0)
            return if (slowPct > 0) c.bat * 100 / (speed * (1 - slowPct / 100)) else c.bat * 100 / speed
        }
        fun heal(i: Int, amount: Double, ghost: Boolean = f[i].ghost > 0) {
            if (f[i].hp > 0 && amount > 0) {
                val old = f[i]
                val hp = min(old.maxHp, old.hp + amount * EquipmentCombat.healingMultiplier(old, ghost))
                f[i] = CardEffectRuntime.recordHealing(old.copy(hp = hp), hp - old.hp)
                CardEffectRuntime.fire(this, i, EffectTrigger.HEAL_ACCUMULATED)
            }
        }
        fun damage(target: Int, raw: Double, spell: Boolean, source: Int, removal: Boolean = false,
                   damageType: DamageType? = null, attackClass: AttackClass? = null,
                   flags: EffectFlags = EffectFlags(), isUltimate: Boolean = false, effectId: String = "hero:${f[source].heroId}", critical: Boolean = false): DamageResolution? {
            val c = f[target]
            // 仅「英雄基础普攻命中」才计入斧王的反击螺旋触发（技能/装备/自伤/反弹/反射均不算）。
            val isBasic = attackClass == null && !spell && !flags.isSelfDamage && !flags.isReflect && source != target && effectId.startsWith("hero:")
            if (raw <= 0 || c.hp <= 0 || c.invulnerable > 0 || (!spell && c.ghost > 0)) return null
            val ordinary = !removal && !flags.isReflect && !flags.isSelfDamage
            var amp = if (isUltimate && ordinary) CardCombat.effects(f[source].cardState).filter { it.effect.trigger == EffectTrigger.ULT_DAMAGE }.sumOf { it.number("percent") } else 0.0
            if (ordinary && (spell || attackClass == AttackClass.PHYSICAL_SKILL)) amp += EquipmentCombat.passive(f[source].equipment, EffectKind.DAMAGE_AMP, "percent")
            if (ordinary && (effectId.startsWith("poisoncore:") || effectId.startsWith("frostcore:")))
                amp += CardCombat.dotAmplification(f[source].cardState, effectId.startsWith("frostcore:"))
            // 冰霜易伤：目标受到的伤害按百分比放大（0.1.5）；施加时已过 BKB 免疫，多来源相加。
            if (ordinary) amp += CardCombat.buffAmount(c.cardState, CardBuffKind.VULNERABILITY) / 100
            val isAttack = (attackClass ?: if (spell) AttackClass.SPELL else AttackClass.ATTACK) == AttackClass.ATTACK
            val block = if (isAttack) EquipmentCombat.passive(c.equipment, EffectKind.PHYSICAL_BLOCK, "amount") else 0.0
            val bkbResist = if (c.equipment.immunityMs > 0) listOf(.20) else emptyList()
            val gearResist = EquipmentCombat.passiveValues(c.equipment, EffectKind.MAGIC_RESIST, "percent").filter { it > 0 }
            val resistDown = EquipmentCombat.passive(f[source].equipment, EffectKind.MAGIC_RESIST, "percent").coerceAtMost(0.0)
            val hit = DamagePipeline.resolve(DamageEvent(source.toString(), target.toString(), effectId, (raw * (1 + amp) - block).coerceAtLeast(0.0),
                damageType ?: if (removal) DamageType.HP_REMOVAL else if (spell) DamageType.MAGICAL else DamageType.PHYSICAL,
                attackClass ?: if (removal) AttackClass.HP_REMOVAL else if (spell) AttackClass.SPELL else AttackClass.ATTACK, flags),
                DamageDefense(c.hp, c.armor + c.armorBuff - CardCombat.buffAmount(c.cardState, CardBuffKind.ARMOR_REDUCTION) - if (EquipmentCombat.active(c.equipment, "mask_madness")) 3 else 0, c.magicResist + resistDown, FighterEffects.shields(c),
                    gearResist + bkbResist + if (c.heroId == "viper" && c.broken <= 0) listOf(.15) else emptyList(),
                    invulnerable = c.invulnerable > 0, ethereal = c.ghost > 0,
                    attackProtected = c.equipment.protectionMs > 0,
                    magicVulnerability = if (c.ghost > 0) .2 else 0.0))
            val lifeDamage = hit.lifeDamage
            f[target] = FighterEffects.withShields(c, hit.shields).copy(hp = hit.hp)
            if (!removal && !flags.isSelfDamage && source != target && (hit.lifeDamage > 0 || hit.shieldDamage > 0)) {
                val visual = DuelVisualCue(cardTick, source, target, f[source].heroId,
                    when { effectId.startsWith("frostcore:") -> DuelCueKind.FROST_HIT
                        effectId.startsWith("poison") -> DuelCueKind.POISON_HIT
                        else -> DuelCueKind.HIT },
                    hit.lifeDamage, hit.shieldDamage, damageType ?: if (spell) DamageType.MAGICAL else DamageType.PHYSICAL,
                    critical, cues.size)
                val merge = if (visual.kind in setOf(DuelCueKind.POISON_HIT, DuelCueKind.FROST_HIT)) cues.indexOfLast {
                    it.kind == visual.kind && it.tick == visual.tick && it.actor == source && it.target == target
                } else -1
                if (merge >= 0) cues[merge] = cues[merge].copy(lifeDamage = cues[merge].lifeDamage + hit.lifeDamage,
                    shieldDamage = cues[merge].shieldDamage + hit.shieldDamage)
                else cues += visual
            }
            if (hit.canConvertToShield && f[source].heroId == "skywrath" && f[source].broken <= 0) {
                f[source] = FighterEffects.withShields(f[source], ShieldLedger.apply(FighterEffects.shields(f[source]),
                    ShieldEntry("hero:sky", ShieldKind.MAGICAL, lifeDamage * .3), ShieldRefreshMode.ADD_CAPPED, f[source].maxHp))
            }
            if (hit.canTriggerDamageResponse && c.heroId == "viper" && c.broken <= 0 && c.skinIn <= 0) {
                f[target] = f[target].copy(skinIn = .5)
                poisonCard(target, source, "hero:viper:skin", false, 1, 6.0)
                if (f[source].equipment.immunityMs <= 0) f[source] = f[source].copy(skinSlowTime = EquipmentCombat.statusDuration(f[source], 3.0))
            }
            if (hit.canTriggerDamageResponse) EquipmentEffectRuntime.trigger(this, target, EffectTrigger.DAMAGE_TAKEN, ::roll)
            CardEffectRuntime.fire(this, target, EffectTrigger.LOW_HP)
            EquipmentEffectRuntime.automatic(this, target)
            // 斧王：承受的「进入伤害结算」普攻每满 spinEveryHits 次触发一次反击螺旋。
            if (isBasic && (hit.lifeDamage > 0 || hit.shieldDamage > 0) && c.heroId == "axe" && c.broken <= 0) {
                val n = c.axeHitsSinceSpin + 1
                f[target] = f[target].copy(axeHitsSinceSpin = n)
                if (n >= HeroContent.skill("axe").spinEveryHits) {
                    f[target] = f[target].copy(axeHitsSinceSpin = 0)
                    counterHelix(target)
                }
            }
            return hit
        }
        fun attack(i: Int, slash: Boolean = false, extra: Boolean = false) {
            if (f[i].hp <= 0 || f[1 - i].hp <= 0) return
            if (f[1 - i].invulnerable > 0 || f[1 - i].ghost > 0 || f[1 - i].equipment.protectionMs > 0) {
                cue(i, DuelCueKind.ATTACK_BLOCKED); return
            }
            cue(i, if (slash) DuelCueKind.OMNI_SLASH else DuelCueKind.ATTACK)
            val evasion = EquipmentCombat.passiveValues(f[1 - i].equipment, EffectKind.EVASION, "percent").fold(1.0) { remain, v -> remain * (1 - v) }
            if (evasion < 1 && roll() >= evasion) { cue(i, DuelCueKind.EVADED); return }
            val c = f[i]
            val sources = mutableListOf<CriticalStrikeSource>()
            if (c.heroId == "juggernaut" && c.broken <= 0) sources += CriticalStrikeSource("hero:blade-dance", 1.8, .25, 0.0)
            if (c.criticalReady > 0) {
                sources += CriticalStrikeSource("hero:coup-de-grace", 3.0, 1.0, 0.0)
                f[i] = c.copy(criticalReady = 0.0)
            }
            CardCombat.effects(c.cardState).filter { it.effect.kind == EffectKind.CRIT_SOURCE }.forEach {
                sources += CriticalStrikeSource(it.key, it.number("multiplier"), it.number("chance"), 0.0)
            }
            EquipmentCombat.effects(c.equipment).filter { it.effect.kind == EffectKind.CRIT_SOURCE }.forEach {
                sources += CriticalStrikeSource(it.key, it.number("multiplier"), it.number("chance"), 0.0)
            }
            val bonus = (if (c.heroId == "phantom_assassin" && c.broken <= 0) .3 else 0.0) + CardCombat.passive(c.cardState, EffectKind.CRIT_MULTIPLIER, "points")
            var multiplier = 1.0
            for (candidate in sources.sortedWith(compareByDescending<CriticalStrikeSource> { it.multiplier }.thenBy { it.instanceId })) {
                val isGear = candidate.instanceId.startsWith("gear:")
                val failures = (if (isGear) f[i].equipment.criticalFailures else f[i].cardState.criticalFailures).getOrElse(candidate.instanceId) { 0 }
                val threshold = CriticalPrd.threshold(candidate.threshold, failures)
                val resolved = CriticalStrikeResolver.resolve(listOf(candidate.copy(threshold = threshold, roll = if (threshold >= 1) 0.0 else roll())), bonus)
                if (candidate.threshold < 1) {
                    val next = candidate.instanceId to if (resolved.critical) 0 else failures + 1
                    f[i] = if (isGear) f[i].copy(equipment = f[i].equipment.copy(criticalFailures = f[i].equipment.criticalFailures + next))
                    else f[i].copy(cardState = f[i].cardState.copy(criticalFailures = f[i].cardState.criticalFailures + next))
                }
                if (resolved.critical) { multiplier = resolved.multiplier; break }
            }
            val hit = damage(1 - i, (c.attack + EquipmentCombat.armletAttack(c)) * multiplier, false, i,
                flags = EffectFlags(canLifesteal = true, triggersOnHit = true, countsAsAttack = !slash),
                isUltimate = slash || (c.heroId == "phantom_assassin" && c.criticalReady > 0), critical = multiplier > 1) ?: return
            // 0.1.5 新英雄普攻触发机制（命中且真实造成伤害后才结算）
            if (hit.lifeDamage > 0 || hit.shieldDamage > 0) {
                val opp = 1 - i
                if (c.heroId == "anti_mage" && f[i].amBurnReady <= .00001 && f[opp].mana > 0) {
                    val cfg = HeroContent.skill("anti_mage")
                    val burn = f[opp].mana * cfg.manaBurnPercent
                    f[opp] = f[opp].copy(mana = max(0.0, f[opp].mana - burn))
                    f[i] = f[i].copy(amManaBurn = f[i].amManaBurn + burn, amBurnReady = cfg.manaBurnIntervalSec)
                    log("${FirstRunContent.hero(c.heroId).name}削蓝${format1(burn)}")
                }
                if (c.heroId == "faceless_void" && c.broken <= 0 && roll() < HeroContent.skill("faceless_void").timeLockChance) {
                    val cfg = HeroContent.skill("faceless_void")
                    val applied = com.example.douqiuqiu.status.TimedControls.apply(f[opp], "hero:faceless_void:timelock", i,
                        com.example.douqiuqiu.status.ControlKind.STUN, cfg.timeLockStunSec, com.example.douqiuqiu.status.DispelClass.BASIC, original.tick)
                    f[opp] = applied.fighter
                    if (applied.applied) controlEvent(i, opp, "hero:faceless_void:timelock", applied.reason, applied.durationMs)
                    log("${FirstRunContent.hero(c.heroId).name}时间锁定")
                    if (applied.applied && f[opp].ultIn > 0.00001) {
                        f[opp] = f[opp].copy(ultIn = f[opp].ultIn + cfg.timeLockCdAddSec)
                        log("${FirstRunContent.hero(c.heroId).name}时间锁定延长敌方大招冷却")
                    }
                }
                if (c.heroId == "legion_commander" && !slash && !extra && roll() < HeroContent.skill("legion_commander").dualStrikeChance) {
                    val hpBefore = f[i].hp
                    heal(i, HeroContent.skill("legion_commander").dualStrikeHeal)
                    log("${FirstRunContent.hero(c.heroId).name}决斗打击追加攻击并治疗${format1(f[i].hp - hpBefore)}")
                    attack(i, extra = true)
                }
            }
            if (c.heroId == "phantom_assassin" && c.criticalReady > 0 && multiplier > 1 && hit.lifeDamage > 0) cue(i, DuelCueKind.PA_CRITICAL_HIT)
            heal(i, hit.lifestealBase(listOf(CardCombat.passive(f[i].cardState, EffectKind.LIFESTEAL, "percent") + EquipmentCombat.lifesteal(f[i].equipment))))
            // Evasion/protection/ethereal have already rejected the whole attack above.
            // A landed attack whose damage is fully blocked still applies its on-hit effects.
            run {
                CardEffectRuntime.fire(this, i, EffectTrigger.ATTACK_LAND)
                EquipmentEffectRuntime.trigger(this, i, EffectTrigger.ATTACK_LAND, ::roll, slash)
            }
            // 无敌斩的每一斩视为一次完整普通攻击：计数卡、暴击、装备命中和控制均可触发。
            f[i] = f[i].copy(cardState = f[i].cardState.copy(attackCount = f[i].cardState.attackCount + 1))
            CardEffectRuntime.fire(this, i, EffectTrigger.ATTACK_COUNT)
            if (multiplier > 1) CardEffectRuntime.fire(this, i, EffectTrigger.CRIT_SUCCESS, slash)
            if (c.heroId == "abaddon" && c.broken <= 0 && f[1 - i].hp > 0) {
                f[1 - i] = f[1 - i].copy(poisons = f[1 - i].poisons.filterNot { it.source == "curse" } + Poison(12.0, 2.0, "curse"))
            }
            if (multiplier > 1 || slash) log("${FirstRunContent.hero(c.heroId).name}${if (slash) "斩击" else "暴击"}")
        }
            /** 斧王反击螺旋：真实伤害，不暴击／吸血／触发普攻命中／反射／再次累计承受次数。 */
        fun counterHelix(axeIndex: Int) {
            val cfg = HeroContent.skill("axe")
            val axe = f[axeIndex]
            val spin = cfg.spinBaseDamage + (axe.armor + axe.armorBuff) * cfg.spinArmorCoefficient
            val enemy = 1 - axeIndex
            cue(axeIndex, DuelCueKind.COUNTER_HELIX)
            log("${FirstRunContent.hero(axe.heroId).name}反击螺旋造成${format1(spin)}真实伤害")
            damage(enemy, spin, true, axeIndex, damageType = DamageType.PURE, attackClass = AttackClass.SELF_DAMAGE,
                flags = EffectFlags(isSelfDamage = true), isUltimate = false, effectId = "hero:axe:helix")
        }
        fun cast(i: Int, free: Boolean = false) {
            val c = f[i]
            val hero = FirstRunContent.hero(c.heroId)
            if ((!free && (c.ultIn > .00001 || c.mana < hero.manaCost)) || !FighterEffects.permission(c, CombatAction.HERO_SKILL)) return
            val flat = CardCombat.passive(c.cardState, EffectKind.COOLDOWN_REDUCTION, "flatSeconds")
            val sources = CardCombat.effects(c.cardState).filter { it.effect.kind == EffectKind.COOLDOWN_REDUCTION && it.number("percent") > 0 }
                .map { CooldownReductionSource(it.key, it.number("percent"), it.effect.uniqueGroup) } + EquipmentCombat.cooldownSources(c.equipment)
            val cd = CooldownReduction.calculate(max(.000001, hero.cooldown - flat), sources, .000001).effectiveSeconds
            f[i] = c.copy(mana = if (free) c.mana else c.mana - hero.manaCost,
                ultIn = max(if (c.heroId == "necrophos") 10.0 else .5, cd * if (free) 1.5 else 1.0))
            log("${hero.name}施放大招")
            cue(i, DuelCueKind.ULTIMATE_CAST)
            when (hero.id) {
                "juggernaut" -> f[i] = FighterEffects.strongDispel(f[i]).copy(invulnerable = HeroContent.legacy("juggernaut").durationSec, slashIn = 0.0)
                "skywrath" -> f[i] = f[i].copy(flareLeft = HeroContent.legacy("skywrath").hits, flareIn = 0.0)
                "phantom_assassin" -> f[i] = f[i].copy(criticalReady = 4.0)
                "abaddon" -> {
                    val cleansed = FighterEffects.strongDispel(f[i])
                    f[i] = FighterEffects.withShields(cleansed, ShieldLedger.apply(FighterEffects.shields(cleansed),
                        ShieldEntry("hero:aphotic", ShieldKind.UNIVERSAL, HeroContent.legacy("abaddon").shield * CardCombat.shieldMultiplier(f[i].cardState))))
                    damage(1 - i, ShieldLedger.total(FighterEffects.shields(f[i])) * .3, true, i, isUltimate = true)
                }
                "necrophos" -> f[i] = f[i].copy(ghost = 3.0)
                "viper" -> {
                    poisonCard(i, 1 - i, "hero:viper:ultimate", true, 1, 3.0)
                    val target = f[1 - i]
                    if (target.equipment.immunityMs <= 0) {
                        val duration = EquipmentCombat.statusDuration(target, 3.0)
                        f[1 - i] = target.copy(broken = duration, slowTime = duration, slow = 30.0)
                    }
                }
                "anti_mage" -> {
                    val cfg = HeroContent.skill("anti_mage")
                    val dmg = cfg.ultManaVoidBase + f[i].amManaBurn * cfg.ultManaVoidCoefficient
                    damage(1 - i, dmg, true, i, isUltimate = true)
                    val applied = com.example.douqiuqiu.status.TimedControls.apply(f[1 - i], "hero:anti_mage:ult", i,
                        com.example.douqiuqiu.status.ControlKind.STUN, cfg.ultManaVoidStunSec, com.example.douqiuqiu.status.DispelClass.BASIC, original.tick)
                    f[1 - i] = applied.fighter
                    if (applied.applied) controlEvent(i, 1 - i, "hero:anti_mage:ult", applied.reason, applied.durationMs)
                    log("${hero.name}施放法力虚空造成${format1(dmg)}魔法伤害")
                }
                "axe" -> {
                    val cfg = HeroContent.skill("axe")
                    val applied = com.example.douqiuqiu.status.TimedControls.apply(f[1 - i], "hero:axe:ult", i,
                        com.example.douqiuqiu.status.ControlKind.TAUNT, cfg.tauntDurationSec, com.example.douqiuqiu.status.DispelClass.BASIC, original.tick)
                    f[1 - i] = applied.fighter
                    if (applied.applied) controlEvent(i, 1 - i, "hero:axe:ult", applied.reason, applied.durationMs)
                    if (applied.applied) f[i] = f[i].copy(armorBuff = cfg.selfArmor, armorBuffMs = applied.durationMs / 1000.0)
                    log("${hero.name}施放狂战士之吼")
                }
                "faceless_void" -> {
                    val cfg = HeroContent.skill("faceless_void")
                    val applied = com.example.douqiuqiu.status.TimedControls.apply(f[1 - i], "hero:faceless_void:ult", i,
                        com.example.douqiuqiu.status.ControlKind.TIME_STOP, cfg.timeStopDurationSec, com.example.douqiuqiu.status.DispelClass.UNDISPELLABLE, original.tick)
                    f[1 - i] = applied.fighter
                    if (applied.applied) controlEvent(i, 1 - i, "hero:faceless_void:ult", applied.reason, applied.durationMs)
                    log("${hero.name}施放时间结界")
                }
                "legion_commander" -> {
                    val cfg = HeroContent.skill("legion_commander")
                    val applied = com.example.douqiuqiu.status.TimedControls.apply(f[1 - i], "hero:legion_commander:ult", i,
                        com.example.douqiuqiu.status.ControlKind.TAUNT, cfg.duelTauntSec, com.example.douqiuqiu.status.DispelClass.BASIC, original.tick)
                    f[1 - i] = applied.fighter
                    if (applied.applied) controlEvent(i, 1 - i, "hero:legion_commander:ult", applied.reason, applied.durationMs)
                    if (applied.applied) f[i] = f[i].copy(speedBuff = cfg.duelAttackSpeedPct, speedBuffMs = applied.durationMs / 1000.0)
                    log("${hero.name}施放决斗")
                }
            }
            c.timedControls.filter { old -> f[i].timedControls.none { it.sourceId == old.sourceId && it.caster == old.caster && it.kind == old.kind } }
                .forEach { controlEvent(i, i, it.sourceId, "STRONG_DISPELLED", 0) }
            CardEffectRuntime.fire(this, i, EffectTrigger.ULT_START)
        }
    }
}
