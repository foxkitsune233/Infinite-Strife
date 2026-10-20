package com.example.douqiuqiu.cards

import com.example.douqiuqiu.combat.ShieldPool
import com.example.douqiuqiu.combat.ShieldLedger
import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.journey.FighterEffects
import com.example.douqiuqiu.status.ControlKind
import com.example.douqiuqiu.status.DispelClass
import com.example.douqiuqiu.status.TimedControls

/** Host owns simultaneous combat and deterministic RNG; card effects never read a clock. */
interface CardEffectHost {
    val cardTick: Int
    fun fighter(index: Int): Fighter
    fun update(index: Int, fighter: Fighter)
    fun healCard(index: Int, amount: Double)
    fun damageCard(source: Int, target: Int, amount: Double, type: DamageType, attackClass: AttackClass, flags: EffectFlags, effectId: String)
    fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double)
    fun controlEvent(source: Int, target: Int, sourceId: String, result: String, durationMs: Long) = Unit

    /** 确定性随机源（返回 0..1）；带 chance 参数的效果经此掷点，保证存档可复现。 */
    fun roll(): Double
    fun enemyTargets(actor: Int): List<com.example.douqiuqiu.equipment.EquipmentTarget> = listOf(
        com.example.douqiuqiu.equipment.EquipmentTarget(1 - actor, fighter(1 - actor).hp > 0))
}

object CardEffectRuntime {
    private fun ticks(ms: Long) = ((ms + 99) / 100).toInt().coerceAtLeast(1)

    /** chance 参数缺席视为必定生效；存在时按宿主随机源掷点（与既有 PRD 阈值语义一致）。 */
    private fun chanceRolls(host: CardEffectHost, bound: BoundCardEffect): Boolean {
        if (!bound.params.containsKey("chance")) return true
        val chance = bound.number("chance")
        return chance >= 1.0 || host.roll() < chance
    }

    /**
     * 对敌方施加一个 timed CardBuff 减益（减速 / 冰霜易伤，0.1.5）。
     *
     * 语义：BKB 类减益免疫直接阻止；持续时间经状态抗性压缩；同一来源单层，
     * 重复施加刷新到期时刻与数值（不叠层）；多来源相加，叠加上限由来源数天然界定。
     */
    private fun applyTimedDebuff(host: CardEffectHost, actor: Int, bound: BoundCardEffect, kind: CardBuffKind, amount: Double) {
        val target = host.fighter(1 - actor)
        if (target.hp <= 0 || target.equipment.immunityMs > 0) return
        if (!chanceRolls(host, bound)) return
        val key = "$actor:${bound.key}"
        val kept = target.cardState.buffs.filterNot { it.sourceKey == key && it.kind == kind }
        host.update(1 - actor, target.copy(cardState = target.cardState.copy(buffs = kept +
            CardBuff(key, kind, amount, host.cardTick + ticks((EquipmentCombat.statusDuration(target, bound.number("durationMs") / 1000.0) * 1000).toLong())))))
    }

    fun recordHealing(fighter: Fighter, actual: Double): Fighter {
        if (actual <= 0) return fighter
        val healed = fighter.cardState.healed.toMutableMap()
        CardCombat.effects(fighter.cardState).filter { it.effect.trigger == EffectTrigger.HEAL_ACCUMULATED }.forEach {
            healed[it.key] = healed.getOrElse(it.key) { 0.0 } + actual
        }
        return fighter.copy(cardState = fighter.cardState.copy(healed = healed))
    }

    fun fire(host: CardEffectHost, actor: Int, trigger: EffectTrigger, slash: Boolean = false) {        if (host.fighter(actor).hp <= 0) return
        val shields = mutableListOf<BoundCardEffect>()
        val effects = CardCombat.effects(host.fighter(actor).cardState).filter { it.effect.trigger == trigger }
        for (bound in effects) {
            val f = host.fighter(actor)
            val effect = bound.effect
            if (f.hp <= 0) break
            if (host.cardTick < f.cardState.readyAt.getOrElse(bound.key) { 0 }) continue
            if (trigger == EffectTrigger.ATTACK_COUNT && f.cardState.attackCount % bound.number("everyN").toInt().coerceAtLeast(1) != 0) continue
            val periodMs = effect.intervalMs?.let { (com.example.douqiuqiu.equipment.EquipmentCombat.cooldown(f.equipment, it / 1000.0) * 1000).toLong() }
            if (trigger == EffectTrigger.INTERVAL && host.cardTick % ticks(periodMs!!) != 0) continue
            if (trigger == EffectTrigger.LOW_HP && f.hp / f.maxHp >= bound.number("thresholdPercent")) continue
            if (trigger == EffectTrigger.HEAL_ACCUMULATED && f.cardState.healed.getOrElse(bound.key) { 0.0 } < bound.number("requiredHeal")) continue
            var runtime = f.cardState
            effect.internalCooldownMs?.takeIf { it > 0 }?.let {
                val reduced = (com.example.douqiuqiu.equipment.EquipmentCombat.cooldown(f.equipment, it / 1000.0) * 1000).toLong()
                runtime = runtime.copy(readyAt = runtime.readyAt + (bound.key to host.cardTick + ticks(reduced)))
            }
            if (trigger == EffectTrigger.HEAL_ACCUMULATED) runtime = runtime.copy(healed = runtime.healed +
                (bound.key to (runtime.healed.getOrElse(bound.key) { 0.0 } - bound.number("requiredHeal")).coerceAtLeast(0.0)))
            host.update(actor, f.copy(cardState = runtime))
            when (effect.kind) {
                EffectKind.APPLY_NORMAL_POISON, EffectKind.APPLY_VIRULENT_POISON -> host.poisonCard(actor, 1 - actor, bound.key,
                    effect.kind == EffectKind.APPLY_VIRULENT_POISON, bound.number("layers").toInt(),
                    bound.number(if (effect.kind == EffectKind.APPLY_VIRULENT_POISON) "spawnDamagePerTick" else "damagePerTick"))
                EffectKind.GRANT_SHIELD -> shields += bound
                EffectKind.HEAL -> host.healCard(actor, bound.number("amount") + host.fighter(actor).maxHp * bound.number("percent"))
                EffectKind.MANA_RESTORE -> host.fighter(actor).let {
                    host.update(actor, it.copy(mana = minOf(it.maxMana, it.mana + bound.number("amount"))))
                }
                EffectKind.ADD_PHYSICAL_DAMAGE, EffectKind.ADD_SPELL_DAMAGE, EffectKind.ADD_PURE_DAMAGE ->
                    host.damageCard(actor, 1 - actor, bound.number("amount"), effect.damageType!!, effect.attackClass!!, effect.flags, effect.id)
                EffectKind.EXTRA_ATTACK -> host.damageCard(actor, 1 - actor, host.fighter(actor).attack * bound.number("damagePercent"),
                    DamageType.PHYSICAL, AttackClass.ATTACK, effect.flags, effect.id)
                EffectKind.SHIELD_TO_DAMAGE -> host.damageCard(actor, 1 - actor,
                    ShieldLedger.total(FighterEffects.shields(host.fighter(actor))) * bound.number("percent"),
                    effect.damageType!!, effect.attackClass!!, effect.flags, effect.id)
                EffectKind.CONSUME_SHIELD_DAMAGE -> {
                    val current = host.fighter(actor)
                    val pool = FighterEffects.shields(current)
                    val amount = ShieldLedger.total(pool) * bound.number("consumePercent")
                    val after = ShieldPool(pool.entries.map { it.copy(amount = it.amount * (1 - bound.number("consumePercent"))) }.filter { it.amount > 0 })
                    host.update(actor, FighterEffects.withShields(current, after))
                    host.damageCard(actor, 1 - actor, amount * bound.number("damageMultiplier"), effect.damageType!!, effect.attackClass!!, effect.flags, effect.id)
                }
                EffectKind.ATTACK_SPEED_STACK -> {
                    val current = host.fighter(actor)
                    val old = current.cardState.buffs.filter { it.sourceKey == bound.key && it.kind == CardBuffKind.ATTACK_SPEED }
                    val kept = if (old.size >= effect.maxStacks!!) old.sortedBy { it.expiresAtTick }.drop(1) else old
                    val other = current.cardState.buffs.filterNot { it.sourceKey == bound.key && it.kind == CardBuffKind.ATTACK_SPEED }
                    host.update(actor, current.copy(cardState = current.cardState.copy(buffs = other + kept +
                        CardBuff(bound.key, CardBuffKind.ATTACK_SPEED, bound.number("amount"), host.cardTick + ticks(effect.stackDurationMs!!)))))
                }
                EffectKind.ARMOR_REDUCTION -> {
                    val target = host.fighter(1 - actor)
                    val key = "$actor:${bound.key}"
                    if (target.hp > 0 && target.cardState.buffs.count { it.sourceKey == key } < effect.maxStacks!!) {
                        host.update(1 - actor, target.copy(cardState = target.cardState.copy(buffs = target.cardState.buffs +
                            CardBuff(key, CardBuffKind.ARMOR_REDUCTION, bound.number("amount"), Int.MAX_VALUE))))
                    }
                }
                EffectKind.SLOW -> applyTimedDebuff(host, actor, bound, CardBuffKind.SLOW, bound.number("slowPercent"))
                EffectKind.VULNERABILITY -> applyTimedDebuff(host, actor, bound, CardBuffKind.VULNERABILITY, bound.number("percent"))
                EffectKind.FREEZE -> {
                    // 冻结：独立于眩晕的硬控；受状态抗性，但不可被普通／强驱散清除。
                    val target = host.fighter(1 - actor)
                    if (chanceRolls(host, bound)) {
                        val applied = TimedControls.apply(target, bound.key, actor, ControlKind.FREEZE, bound.number("durationMs") / 1000.0,
                            DispelClass.UNDISPELLABLE, host.cardTick)
                        host.update(1 - actor, applied.fighter)
                        host.controlEvent(actor, 1 - actor, bound.key, applied.reason, applied.durationMs)
                    }
                }
                EffectKind.FROST_DOT -> host.poisonCard(actor, 1 - actor, bound.key, false, 1, bound.number("damagePerTick"))
                else -> error("未接入卡牌触发效果: ${effect.id}")
            }
        }
        if (shields.isNotEmpty()) {
            val current = host.fighter(actor)
            host.update(actor, FighterEffects.withShields(current,
                CardCombat.grantShields(FighterEffects.shields(current), current.cardState, shields)))
        }
    }

    /**
     * 新毒核心的一次普通毒跳结算后触发（H03）。
     *
     * 只对**施毒者本人**触发：D5「毒血回流」的语义是自身普通毒伤害按比例转治疗，
     * 含剧毒生成的普通毒（生成层继承施毒者与来源），剧毒本体产毒不是伤害、不触发。
     * [lifeDamage] / [shieldDamage] 是该跳经过护盾与减伤后的实际数值，比例参数
     * （如打盾部分折算比）由效果自行解释，引擎不写死。
     */
    fun firePoisonTick(host: CardEffectHost, actor: Int, lifeDamage: Double, shieldDamage: Double) {
        val f = host.fighter(actor)
        if (f.hp <= 0) return
        for (bound in CardCombat.effects(f.cardState).filter { it.effect.trigger == EffectTrigger.POISON_TICK }) {
            if (host.cardTick < f.cardState.readyAt.getOrElse(bound.key) { 0 }) continue
            when (bound.effect.kind) {
                EffectKind.HEAL -> host.healCard(actor,
                    (lifeDamage + shieldDamage * bound.number("shieldAbsorbedRatio")) * bound.number("percent"))
                else -> error("未接入的毒跳效果: ${bound.effect.id}")
            }
        }
    }
}
