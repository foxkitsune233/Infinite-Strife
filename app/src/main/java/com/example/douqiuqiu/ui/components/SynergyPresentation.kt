package com.example.douqiuqiu.ui.components

import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.content.*
import kotlin.math.roundToInt

/** Presentation reads live tier parameters; no copied balance numbers or combat formulas. */
object SynergyPresentation {
    data class Tier(val threshold: Int, val active: Boolean, val text: String)
    data class SchoolView(val id: String, val name: String, val count: Int, val tiers: List<Tier>) {
        val next = tiers.firstOrNull { !it.active }
        val summary get() = next?.let { "再添${it.threshold - count}种：${it.text}" } ?: "全部档位已激活"
    }

    fun schools(cards: List<CombatCard>): List<SchoolView> {
        val counts = CardCombat.synergies(CardBattleState(cards)).associateBy { it.schoolId }
        return School.entries.map { school ->
            val count = counts[school.id]?.uniqueCardIds?.size ?: 0
            SchoolView(school.id, school.label, count, CardCatalog.synergyTiers.filter { it.schoolId == school.id }
                .sortedBy { it.tier }.map { tier -> Tier(tier.tier, count >= tier.tier,
                    tier.effects.filterNot { it.requiresReview }.joinToString("；") { describe(it) }) })
        }.sortedWith(compareByDescending<SchoolView> { it.count }.thenBy { it.id })
    }

    fun describe(e: EffectDefinition): String {
        fun n(key: String): Double = when (val value = e.paramsAt(1)[key]) {
            is EffectValue.Num -> value.value
            is EffectValue.Count -> value.value.toDouble()
            else -> 0.0
        }
        fun number(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else v.toString().trimEnd('0').trimEnd('.')
        fun p(key: String) = number(n(key) * 100) + "%"
        val interval = e.intervalMs?.let { "每${number(it / 1000.0)}秒" }.orEmpty()
        val text = when (e.kind) {
            EffectKind.STAT_BONUS -> listOf("attackSpeed" to "攻速", "attack" to "攻击", "armor" to "护甲", "healthRegen" to "每秒回血", "health" to "最大生命")
                .filter { n(it.first) != 0.0 }.joinToString("，") { "${it.second} +${number(n(it.first))}" }
            EffectKind.MANA_REGEN -> "每秒回蓝 +${number(n("amount"))}"
            EffectKind.MANA_RESTORE -> "施放大招恢复${number(n("amount"))}魔法"
            EffectKind.CRIT_SOURCE -> "独立${p("chance")}概率造成${p("multiplier")}暴击（不与其他暴击连乘）"
            EffectKind.CRIT_MULTIPLIER -> "成功暴击倍率 +${number(n("points") * 100)}个百分点"
            EffectKind.DAMAGE_AMP -> when {
                e.id == "synergy.shield.3" -> "获得的护盾量 +${p("percent")}"
                e.id == "synergy.poison.2" -> "中毒持续伤害 +${p("percent")}（不强化冰霜）"
                e.id == "synergy.frost.3" -> "冰霜持续伤害 +${p("percent")}（不强化中毒）"
                e.trigger == EffectTrigger.ULT_DAMAGE -> "大招伤害 +${p("percent")}"
                else -> "中毒／冰霜持续伤害 +${p("percent")}"
            }
            EffectKind.EXTRA_ATTACK -> "每${number(n("everyN"))}次成功普攻追加${p("damagePercent")}攻击伤害（不递归、不暴击、不吸血）"
            EffectKind.ADD_PHYSICAL_DAMAGE -> "暴击命中后追加${number(n("amount"))}物理伤害"
            EffectKind.GRANT_SHIELD -> "开场获得${number(n("amount"))}通用护盾"
            EffectKind.SHIELD_TO_DAMAGE -> "${interval}按当前护盾量${p("percent")}造成魔法伤害（不耗盾）"
            EffectKind.LIFESTEAL -> "普攻吸血 +${p("percent")}（打盾按50%折算）"
            EffectKind.HEAL -> if (n("percent") > 0) "${interval}恢复最大生命${p("percent")}" else "${interval}恢复${number(n("amount"))}生命"
            EffectKind.APPLY_VIRULENT_POISON -> "${interval}施加${number(n("layers"))}层剧毒；每秒产普通毒，每跳${number(n("spawnDamagePerTick"))}，共${number(n("spawnTicks"))}跳；剧毒仅强驱散清除"
            EffectKind.APPLY_NORMAL_POISON -> "${interval}施加${number(n("layers"))}层普通毒，每层每跳${number(n("damagePerTick"))}、共${number(n("ticks"))}跳"
            EffectKind.FROST_DOT -> "${interval}附加冰霜持续伤害，每跳${number(n("damagePerTick"))}、共${number(n("ticks"))}跳"
            else -> "效果说明待补充"
        }
        return text + (e.internalCooldownMs?.let { "；触发间隔${number(it / 1000.0)}秒" } ?: "")
    }
}
