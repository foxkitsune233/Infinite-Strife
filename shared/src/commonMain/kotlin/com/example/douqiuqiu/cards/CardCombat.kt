package com.example.douqiuqiu.cards

import com.example.douqiuqiu.combat.ShieldEntry
import com.example.douqiuqiu.combat.ShieldKind
import com.example.douqiuqiu.combat.ShieldLedger
import com.example.douqiuqiu.combat.ShieldPool
import com.example.douqiuqiu.content.*

data class CombatCard(val instanceId: String, val cardId: String, val star: Int)
enum class CardBuffKind { ATTACK_SPEED, ARMOR_REDUCTION, SLOW, VULNERABILITY }
data class CardBuff(val sourceKey: String, val kind: CardBuffKind, val amount: Double, val expiresAtTick: Int)

/** Absolute tick deadlines survive restarts. A fresh battle rebuilds this state. */
data class CardBattleState(
    val cards: List<CombatCard> = emptyList(),
    val started: Boolean = true,
    val attackCount: Int = 0,
    val readyAt: Map<String, Int> = emptyMap(),
    val healed: Map<String, Double> = emptyMap(),
    val buffs: List<CardBuff> = emptyList(),
    val criticalFailures: Map<String, Int> = emptyMap()
)

data class BoundCardEffect(
    val key: String, val cardId: String, val effect: EffectDefinition, val params: EffectParams
) {
    fun number(name: String, default: Double = 0.0): Double = when (val v = params[name]) {
        is EffectValue.Num -> v.value
        is EffectValue.Count -> v.value.toDouble()
        null -> default
        else -> error("效果参数不是数值: $key/$name")
    }
    val shieldSource: String get() = if (cardId.startsWith("synergy.")) cardId else "card:$cardId:${effect.id}"
}

/** The live catalog is deliberately narrower than the unimplemented/review content catalog. */
object CardCombat {
    val enabledCards: List<CardDefinition> = CardCatalog.confirmedOnly
    val definitions: Map<String, CardDefinition> = enabledCards.associateBy { it.id }

    // DOTs share the persisted discrete clock, not damage-school bonuses or poison healing.
    // Deriving the family from stable effect IDs also recognises already-saved frost layers.
    private val frostDotEffectIds = (CardCatalog.all.flatMap { it.effects } + CardCatalog.synergyTiers.flatMap { it.effects })
        .filter { it.kind == EffectKind.FROST_DOT }.map { it.id }.toSet()
    fun isFrostDotSource(sourceId: String): Boolean = sourceId.substringAfterLast(':') in frostDotEffectIds
    fun dotAmplification(state: CardBattleState, frost: Boolean): Double = effects(state)
        .filter { it.effect.id == if (frost) "synergy.frost.3" else "synergy.poison.2" }.sumOf { it.number("percent") }

    private fun validStackAmount(source: BoundCardEffect, amount: Double): Boolean =
        amount == source.number("amount") || (amount == 8.0 && source.effect.id in
            setOf("RG2.stack", "D9.stack", "D13.stack") && source.number("amount") in setOf(16.0, 32.0))

    fun synergies(state: CardBattleState): List<SchoolSynergy> = SynergyCounter.count(state.cards.map { card ->
        SynergyCard(card.instanceId, card.cardId, definitions.getValue(card.cardId).tags, card.star)
    })

    fun effects(state: CardBattleState): List<BoundCardEffect> {
        val own = state.cards.flatMap { card -> definitions.getValue(card.cardId).effects.filterNot { it.requiresReview }.map {
            val effect = if (it.id == "P2.virulent") it.copy(intervalMs = listOf(8000L, 6000L, 4000L)[card.star - 1]) else it
            BoundCardEffect("${card.instanceId}:${it.id}", card.cardId, effect, effect.paramsAt(card.star))
        } }
        val tiers = synergies(state).flatMap { school -> CardCatalog.synergyTiers.filter {
            it.schoolId == school.schoolId && it.tier in school.activeTiers
        } }.flatMap { tier -> tier.effects.filterNot { it.requiresReview }.map {
            BoundCardEffect(it.id, it.id, it, it.paramsAt(1))
        } }
        return (own + tiers).sortedWith(compareBy<BoundCardEffect> { it.effect.id }.thenBy { it.key })
    }

    fun passive(state: CardBattleState, kind: EffectKind, name: String): Double = effects(state)
        .filter { it.effect.trigger == EffectTrigger.PASSIVE && it.effect.kind == kind }.sumOf { it.number(name) }

    fun shieldMultiplier(state: CardBattleState): Double = 1.0 + effects(state)
        .filter { it.effect.id == "synergy.shield.3" }.sumOf { it.number("percent") }

    fun initialShields(state: CardBattleState): ShieldPool = grantShields(ShieldPool(), state,
        effects(state).filter { it.effect.trigger == EffectTrigger.BATTLE_START && it.effect.kind == EffectKind.GRANT_SHIELD })

    /** Duplicate copies sum one acquisition before same-card source refresh, never gain separate pools. */
    fun grantShields(pool: ShieldPool, state: CardBattleState, effects: List<BoundCardEffect>): ShieldPool {
        var result = pool
        val grouped = effects.groupBy { it.shieldSource }
        grouped.keys.sorted().forEach { source ->
            val copies = grouped.getValue(source)
            val first = copies.first()
            val kind = when (first.effect.shieldKind) {
                com.example.douqiuqiu.content.ShieldKind.PHYSICAL -> ShieldKind.PHYSICAL
                com.example.douqiuqiu.content.ShieldKind.MAGICAL -> ShieldKind.MAGICAL
                else -> ShieldKind.UNIVERSAL
            }
            result = ShieldLedger.apply(result, ShieldEntry(source, kind,
                copies.sumOf { it.number("amount") } * shieldMultiplier(state),
                first.number("durationMs").toLong().takeIf { it > 0 }))
        }
        return result
    }

    fun initialManaBonus(state: CardBattleState): Double = effects(state).filter {
        it.effect.trigger == EffectTrigger.BATTLE_START && it.effect.kind == EffectKind.MANA_RESTORE
    }.sumOf { it.number("amount") }

    fun buffAmount(state: CardBattleState, kind: CardBuffKind): Double = state.buffs.filter { it.kind == kind }.sumOf { it.amount }

    fun validate(state: CardBattleState) {
        require(state.cards.size <= 8 && state.cards.map { it.instanceId }.distinct().size == state.cards.size)
        require(state.cards.all { it.instanceId.isNotBlank() && it.instanceId.length <= 128 && it.cardId in definitions && it.star in 1..3 })
        require(state.attackCount in 0..100000)
        val bound = effects(state).associateBy { it.key }
        val keys = bound.keys
        require(state.readyAt.size <= 128 && state.readyAt.all { (key, tick) -> key in keys && tick in 0..1000000 })
        require(state.healed.size <= 128 && state.healed.all { (key, amount) -> key in keys && amount.isFinite() && amount >= 0 })
        require(state.buffs.size <= 64 && state.buffs.all {
            it.sourceKey.isNotBlank() && it.sourceKey.length <= 256 && it.amount.isFinite() && it.amount >= 0 && it.expiresAtTick >= 0
        })
        require(state.healed.keys.all { bound.getValue(it).effect.trigger == EffectTrigger.HEAL_ACCUMULATED })
        require(state.readyAt.keys.all { (bound.getValue(it).effect.internalCooldownMs ?: 0) > 0 })
        state.buffs.groupBy { it.sourceKey }.forEach { (key, buffs) ->
            require(buffs.map { it.kind }.distinct().size == 1)
            if (buffs.first().kind == CardBuffKind.ATTACK_SPEED) {
                val source = requireNotNull(bound[key]) { "攻速层来源不存在" }
                require(source.effect.kind == EffectKind.ATTACK_SPEED_STACK && buffs.size <= source.effect.maxStacks!!)
                require(buffs.all { validStackAmount(source, it.amount) && it.expiresAtTick in 1..1000000 })
            } else if (buffs.first().kind == CardBuffKind.ARMOR_REDUCTION) {
                require(key.endsWith(":C4.armorShred") && buffs.size <= 3 && buffs.all { it.expiresAtTick == Int.MAX_VALUE })
            } else {
                // Timed hostile debuffs are checked against the opponent's real effect in validateAt.
                require(buffs.size == 1 && buffs.single().expiresAtTick in 1..1000000)
            }
        }
        require(state.criticalFailures.size <= 16 && state.criticalFailures.all { (key, failures) ->
            failures in 0..10000 && (key == "hero:blade-dance" || bound[key]?.effect?.kind == EffectKind.CRIT_SOURCE)
        })
    }

    fun validateAt(state: CardBattleState, opponent: CardBattleState, tick: Int, opponentIndex: Int) {
        require(state.started || tick == 0)
        // Legion can land a normal and one non-recursive extra attack in the same 100ms frame.
        require(state.attackCount.toLong() <= tick.toLong() * 2)
        val own = effects(state).associateBy { it.key }
        val incoming = effects(opponent).filter {
            it.effect.kind in setOf(EffectKind.ARMOR_REDUCTION, EffectKind.SLOW, EffectKind.VULNERABILITY)
        }.associateBy { "$opponentIndex:${it.key}" }
        state.buffs.forEach { buff ->
            if (buff.kind == CardBuffKind.ATTACK_SPEED) {
                val source = own.getValue(buff.sourceKey)
                require(buff.expiresAtTick > tick && buff.expiresAtTick <= tick + ((source.effect.stackDurationMs!! + 99) / 100).toInt())
            } else if (buff.kind == CardBuffKind.ARMOR_REDUCTION) {
                val source = requireNotNull(incoming[buff.sourceKey]) { "减甲层来源不存在" }
                require(buff.amount == source.number("amount"))
            } else {
                val source = requireNotNull(incoming[buff.sourceKey]) { "减益来源不存在" }
                val expectedKind = if (buff.kind == CardBuffKind.SLOW) EffectKind.SLOW else EffectKind.VULNERABILITY
                require(source.effect.kind == expectedKind)
                val value = if (buff.kind == CardBuffKind.SLOW) source.number("slowPercent") else source.number("percent")
                require(buff.amount == value)
                val baseTicks = ((source.number("durationMs").toLong() + 99L) / 100L).toInt()
                require(buff.expiresAtTick > tick && buff.expiresAtTick <= tick + baseTicks)
            }
        }
    }

    /** These two buff kinds are BASIC dispellable; enemy dispels remove positives only. */
    fun dispelBuffs(state: CardBattleState, target: com.example.douqiuqiu.status.DispelTarget): CardBattleState = state.copy(
        buffs = state.buffs.filterNot {
            if (target == com.example.douqiuqiu.status.DispelTarget.SELF_OR_ALLY)
                it.kind in setOf(CardBuffKind.ARMOR_REDUCTION, CardBuffKind.SLOW, CardBuffKind.VULNERABILITY)
            else it.kind == CardBuffKind.ATTACK_SPEED
        }
    )
}
