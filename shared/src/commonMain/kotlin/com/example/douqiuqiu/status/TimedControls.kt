package com.example.douqiuqiu.status

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.EffectValue
import com.example.douqiuqiu.content.HeroContent
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.journey.Fighter
import kotlin.math.ceil

enum class ControlKind { STUN, HEX, SILENCE, DISARM, TAUNT, TIME_STOP, FREEZE }
data class TimedControl(val sourceId: String, val caster: Int, val kind: ControlKind,
                        val remainingMs: Long, val dispelClass: DispelClass, val appliedAtTick: Int)
data class ControlApplication(val fighter: Fighter, val reason: String, val durationMs: Long = 0) {
    val applied get() = reason == "APPLIED"
}

/** Game time only. Legacy bool controls stay separate and never acquire invented timers. */
object TimedControls {
    fun current(f: Fighter): ActionControlState {
        val kinds = if (f.equipment.immunityMs > 0) emptySet() else f.timedControls.map { it.kind }.toSet()
        return f.control.copy(stunned = f.control.stunned || ControlKind.STUN in kinds,
            hexed = f.control.hexed || ControlKind.HEX in kinds,
            silenced = f.control.silenced || ControlKind.SILENCE in kinds,
            disarmed = f.control.disarmed || ControlKind.DISARM in kinds,
            taunted = f.control.taunted || ControlKind.TAUNT in kinds,
            timeStopped = f.control.timeStopped || ControlKind.TIME_STOP in kinds,
            frozen = f.control.frozen || ControlKind.FREEZE in kinds)
    }
    fun elapse(entries: List<TimedControl>) = entries.mapNotNull {
        if (it.remainingMs <= 100) null else it.copy(remainingMs = it.remainingMs - 100)
    }
    fun dispel(entries: List<TimedControl>, strong: Boolean) = entries.filterNot {
        it.dispelClass == DispelClass.BASIC || strong && it.dispelClass == DispelClass.STRONG_ONLY
    }
    fun apply(f: Fighter, sourceId: String, caster: Int, kind: ControlKind, seconds: Double,
              dispelClass: DispelClass, tick: Int): ControlApplication {
        require(sourceId.isNotBlank() && sourceId.length <= 256 && caster in 0..1 && tick in 0..600)
        require(seconds.isFinite() && seconds > 0 && seconds <= 60)
        val reason = when {
            f.hp <= 0 || f.control.dead -> "DEAD"
            f.control.banished -> "BANISHED"
            f.invulnerable > 0 -> "INVULNERABLE"
            f.equipment.immunityMs > 0 -> "DEBUFF_IMMUNE"
            else -> null
        }
        if (reason != null) return ControlApplication(f, reason)
        val ms = (ceil(EquipmentCombat.statusDuration(f, seconds) * 10).toLong() * 100).coerceIn(100, 60000)
        val old = f.timedControls.firstOrNull { it.sourceId == sourceId && it.caster == caster && it.kind == kind }
        val entry = TimedControl(sourceId, caster, kind, maxOf(ms, old?.remainingMs ?: 0), dispelClass, tick)
        val pool = f.timedControls.filterNot { it.sourceId == sourceId && it.caster == caster && it.kind == kind } + entry
        if (pool.size > 24) return ControlApplication(f, "POOL_LIMIT")
        return ControlApplication(f.copy(timedControls = pool), "APPLIED", entry.remainingMs)
    }
    fun validate(entries: List<TimedControl>, tick: Int) {
        require(entries.size <= 24 && entries.map { Triple(it.sourceId, it.caster, it.kind) }.distinct().size == entries.size)
        require(entries.all { it.sourceId.isNotBlank() && it.sourceId.length <= 256 && it.caster in 0..1 &&
            it.remainingMs in 1..60000 && it.remainingMs % 100 == 0L && it.appliedAtTick in 0..tick })
    }
    /**
     * 校验控制来源合法性：装备主动效果、卡牌效果与英雄技能均可施加控制。
     *
     * 0.1.5 起英雄技能（敌法师眩晕、斧王/军团嘲讽、虚空时停）与寒霜卡牌（冻结）
     * 也会写入 [Fighter.timedControls]，存档校验必须承认这三类来源，否则带控制中的
     * 战斗快照无法通过 [com.example.douqiuqiu.journey.JourneyValidation]。
     */
    fun validateSources(f: Fighter, opponent: Fighter, targetActor: Int) {
        val gearSources = EquipmentCombat.effects(opponent.equipment).filter { it.effect.kind == com.example.douqiuqiu.content.EffectKind.APPLY_CONTROL }
        // 卡牌来源：APPLY_CONTROL 参数驱动与寒霜 FREEZE 硬控两类（0.1.5）。
        val cardSources = CardCombat.effects(opponent.cardState).filter {
            it.effect.kind == com.example.douqiuqiu.content.EffectKind.APPLY_CONTROL || it.effect.kind == com.example.douqiuqiu.content.EffectKind.FREEZE
        }
        f.timedControls.forEach { entry ->
            require(entry.caster == 1 - targetActor) { "Forged control caster" }
            val gear = gearSources.singleOrNull { it.key == entry.sourceId }
            if (gear != null) {
                require((gear.effect.params.getValue("control") as EffectValue.Choice).value == entry.kind.name)
                val dispel = when (gear.effect.dispelClass) {
                    com.example.douqiuqiu.content.DispelClass.BASIC -> DispelClass.BASIC
                    com.example.douqiuqiu.content.DispelClass.STRONG_ONLY -> DispelClass.STRONG_ONLY
                    else -> DispelClass.UNDISPELLABLE
                }
                require(entry.dispelClass == dispel) { "Forged control dispel class" }
                return@forEach
            }
            val card = cardSources.singleOrNull { it.key == entry.sourceId }
            if (card != null) {
                val expectedKind = if (card.effect.kind == com.example.douqiuqiu.content.EffectKind.FREEZE) ControlKind.FREEZE.name
                    else (card.effect.params.getValue("control") as EffectValue.Choice).value
                require(expectedKind == entry.kind.name)
                val dispel = if (card.effect.kind == com.example.douqiuqiu.content.EffectKind.FREEZE) DispelClass.UNDISPELLABLE
                    else when (card.effect.dispelClass) {
                        com.example.douqiuqiu.content.DispelClass.BASIC -> DispelClass.BASIC
                        com.example.douqiuqiu.content.DispelClass.STRONG_ONLY -> DispelClass.STRONG_ONLY
                        else -> DispelClass.UNDISPELLABLE
                    }
                require(entry.dispelClass == dispel) { "Forged control dispel class" }
                return@forEach
            }
            val heroDispel = HeroContent.controlSources[entry.sourceId]
            if (heroDispel != null) {
                val heroId = entry.sourceId.removePrefix("hero:").substringBefore(":")
                require(opponent.heroId == heroId && entry.dispelClass == heroDispel) { "Unknown control source" }
                return@forEach
            }
            error("Unknown control source")
        }
    }
}
