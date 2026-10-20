package com.example.douqiuqiu.journey

import com.example.douqiuqiu.combat.*
import com.example.douqiuqiu.status.*
import kotlin.math.roundToLong

/** Converts legacy aggregate snapshots once; all new shield operations keep explicit sources. */
object FighterEffects {
    fun shields(f: Fighter): ShieldPool = f.shieldSources ?: ShieldPool(listOfNotNull(
        if (f.shield > 0) ShieldEntry("legacy:universal", ShieldKind.UNIVERSAL, f.shield) else null,
        if (f.physicalShield > 0) ShieldEntry("legacy:physical", ShieldKind.PHYSICAL, f.physicalShield) else null,
        if (f.magicShield > 0) ShieldEntry("legacy:magical", ShieldKind.MAGICAL, f.magicShield) else null,
        if (f.skyShield > 0) ShieldEntry("hero:sky", ShieldKind.MAGICAL, f.skyShield) else null))

    fun withShields(f: Fighter, pool: ShieldPool): Fighter = f.copy(shieldSources = pool,
        shield = pool.entries.filter { it.kind == ShieldKind.UNIVERSAL }.sumOf { it.amount },
        physicalShield = pool.entries.filter { it.kind == ShieldKind.PHYSICAL }.sumOf { it.amount },
        magicShield = pool.entries.filter { it.kind == ShieldKind.MAGICAL && it.sourceId != "hero:sky" }.sumOf { it.amount },
        skyShield = pool.entries.filter { it.sourceId == "hero:sky" }.sumOf { it.amount })

    fun permission(f: Fighter, action: CombatAction): Boolean = TimedControls.current(f).let { control -> ActionPermission.check(control.copy(
        dead = f.hp <= 0 || control.dead, disarmed = control.disarmed || f.ghost > 0,
        silenced = control.silenced || com.example.douqiuqiu.equipment.EquipmentCombat.active(f.equipment, "mask_madness")), action,
        allowStunnedHeroSkill = action == CombatAction.HERO_SKILL && f.heroId == "abaddon").allowed }

    fun advancePoison(f: Fighter): Pair<List<Poison>, List<PoisonDamage>> {
        val pool = PoisonPool(f.poisons.mapIndexed { i, poison -> PoisonLayer("poison-$i", poison.source, poison.dps,
            (poison.remaining * 1000).roundToLong().coerceAtLeast(1), if (poison.dispellable) DispelClass.BASIC else DispelClass.UNDISPELLABLE) })
        val advanced = PoisonTimeline.advance(pool, 100)
        return advanced.pool.layers.map { layer ->
            f.poisons[layer.instanceId.removePrefix("poison-").toInt()].copy(remaining = layer.remainingMs / 1000.0)
        } to advanced.damage
    }

    fun weakDispel(f: Fighter): Fighter = f.copy(
        timedControls = TimedControls.dispel(f.timedControls, false),
        poisons = f.poisons.filterNot { it.dispellable },
        poisonPool = f.poisonPool?.let { PoisonEngine.dispel(it, DispelStrength.BASIC, DispelTarget.SELF_OR_ALLY).pool }?.takeIf { !it.isEmpty() },
        skinSlowTime = 0.0, slowTime = 0.0,
        cardState = com.example.douqiuqiu.cards.CardCombat.dispelBuffs(f.cardState, DispelTarget.SELF_OR_ALLY),
        control = f.control.copy(silenced = false, muted = false, disarmed = false))

    fun strongDispel(f: Fighter): Fighter {
        val effects = f.poisons.mapIndexed { i, p -> DispelEffect("poison-$i", p.source, false,
            if (p.dispellable) DispelClass.BASIC else DispelClass.UNDISPELLABLE) }
        val retained = DispelResolver.resolve(effects, DispelStrength.STRONG, DispelTarget.SELF_OR_ALLY).retained.map { it.instanceId }.toSet()
        return f.copy(timedControls = TimedControls.dispel(f.timedControls, true), poisons = f.poisons.filterIndexed { i, _ -> "poison-$i" in retained }, skinSlowTime = 0.0,
            poisonPool = f.poisonPool?.let { PoisonEngine.dispel(it, DispelStrength.STRONG, DispelTarget.SELF_OR_ALLY).pool }?.takeIf { !it.isEmpty() },
            slowTime = 0.0,
            cardState = com.example.douqiuqiu.cards.CardCombat.dispelBuffs(f.cardState, DispelTarget.SELF_OR_ALLY),
            control = f.control.copy(stunned = false, hexed = false, silenced = false, muted = false, disarmed = false))
    }
}
