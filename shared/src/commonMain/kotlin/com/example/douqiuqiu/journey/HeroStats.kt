package com.example.douqiuqiu.journey

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.combat.CooldownReduction
import com.example.douqiuqiu.combat.CooldownReductionSource
import com.example.douqiuqiu.content.EffectKind
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import kotlin.math.abs
import kotlin.math.round

/**
 * 单个数值的只读展示模型（外围任务 X02）。
 *
 * [base] / [equipment] / [cards] / [synergy] 是各来源的**边际**贡献：做法是「拿掉该来源重新算一遍再相减」，
 * 因此天然包含属性之间的耦合（例如装备加力量、力量再加生命），不需要、也不应该在 UI 层另写公式。
 *
 * 卡牌自身效果与羁绊档位效果在共享层是合并下发的，无法可靠分离，
 * 所以 [synergy] 恒为 null、[cards] 是「卡牌 + 羁绊」的合计值。
 * UI 遇到 null 来源时必须标「总值」，不得伪造精确贡献。
 */
data class StatValue(
    val total: Double,
    val base: Double? = null,
    val equipment: Double? = null,
    val cards: Double? = null,
    val synergy: Double? = null
) {
    /** 是否至少有一路来源可拆分显示。false 时 UI 只能显示总值。 */
    val hasBreakdown: Boolean get() = base != null || equipment != null || cards != null || synergy != null
}

/** 英雄数值快照。局外为当前配装结果，战斗中为实时有效状态。 */
data class HeroStatSnapshot(
    val heroId: String,
    val inBattle: Boolean,
    val hp: StatValue,
    val mana: StatValue,
    val attack: StatValue,
    val armor: StatValue,
    val magicResistance: StatValue,
    val attackSpeed: StatValue,
    val healthRegen: StatValue,
    val manaRegen: StatValue,
    val lifesteal: StatValue,
    val cooldownReduction: StatValue,
    val statusResistance: StatValue
)

/**
 * 只读数值快照（外围任务 X02）。
 *
 * 所有取值都来自共享层 [JourneyRules.fighter]；规则层只做读取和相减，不复制任何战斗公式。
 */
object HeroStats {

    /** 拿掉指定容器里的装备/卡牌后重算，用于求某一来源的边际贡献。随身的背包与安全箱不受影响。 */
    private fun strip(state: InventoryState, vararg drop: Container) =
    state.copy(items = state.items.filterNot { it.place.container in drop })

    private fun lifeStealOf(f: Fighter) = CardCombat.passive(f.cardState, EffectKind.LIFESTEAL, "percent") +
        EquipmentCombat.passive(f.equipment, EffectKind.LIFESTEAL, "percent")

    private fun statusResistOf(f: Fighter) = CardCombat.passive(f.cardState, EffectKind.STATUS_RESIST, "percent") +
        EquipmentCombat.passive(f.equipment, EffectKind.STATUS_RESIST, "percent")

    /** 冷却缩减是按来源合并后统一计算的，这里给的是减免比例本身（0..0.6）。 */
    private fun cdrOf(f: Fighter): Double {
        val sources = CardCombat.effects(f.cardState)
            .filter { it.effect.kind == EffectKind.COOLDOWN_REDUCTION && it.number("percent") > 0 }
            .map { CooldownReductionSource(it.key, it.number("percent"), it.effect.uniqueGroup) } +
            EquipmentCombat.cooldownSources(f.equipment)
        return CooldownReduction.calculate(1.0, sources, 0.000001).reduction
    }

    private fun safe(value: Double) = if (value.isFinite()) value else 0.0

    /** 抹掉浮点噪声，避免显示 2.999999 或 12.700000000000001 之类的脏数。取到 1e-9 并四舍五入。 */
    private fun clean(value: Double) = if (abs(value) < 1e-9) 0.0 else round(value * 1e9) / 1e9

    fun snapshot(state: InventoryState, rules: InventoryRules, heroId: String, battle: Fighter? = null): HeroStatSnapshot {
        // 战斗中：数值取自正在推进的战斗快照，无法构造消融对照，只报实时总值。
        if (battle != null) {
            fun only(value: Double) = StatValue(total = safe(value))
            return HeroStatSnapshot(heroId, true,
                hp = only(battle.maxHp), mana = only(battle.maxMana), attack = only(battle.attack),
                armor = only(battle.armor), magicResistance = only(battle.magicResist),
                attackSpeed = only(battle.speed), healthRegen = only(battle.regen),
                manaRegen = only(battle.manaRegen), lifesteal = only(lifeStealOf(battle)),
                cooldownReduction = only(cdrOf(battle)), statusResistance = only(statusResistOf(battle)))
        }
        val journey = JourneyRules(rules)
        val full = journey.fighter(state, heroId)
        val bare = journey.fighter(strip(state, Container.EQUIPMENT, Container.SKILLS), heroId)
        val noEquip = journey.fighter(strip(state, Container.EQUIPMENT), heroId)
        val noCards = journey.fighter(strip(state, Container.SKILLS), heroId)
        /**
         * 完整值减去「拿掉该来源后」的值，即为该来源的边际贡献。
         * 因为属性互相依赖，各来源之和不保证正好等于总值，这是预期的。
         */
        fun value(pick: (Fighter) -> Double, withoutEquipment: Fighter, withoutCards: Fighter): StatValue {
            val total = pick(full)
            return StatValue(
                total = safe(total),
                base = safe(pick(bare)),
                equipment = clean(total - pick(withoutEquipment)),
                cards = clean(total - pick(withoutCards)),
                synergy = null
            )
        }
        return HeroStatSnapshot(heroId, false,
            hp = value({ it.maxHp }, noEquip, noCards),
            mana = value({ it.maxMana }, noEquip, noCards),
            attack = value({ it.attack }, noEquip, noCards),
            armor = value({ it.armor }, noEquip, noCards),
            magicResistance = value({ it.magicResist }, noEquip, noCards),
            attackSpeed = value({ it.speed }, noEquip, noCards),
            healthRegen = value({ it.regen }, noEquip, noCards),
            manaRegen = value({ it.manaRegen }, noEquip, noCards),
            lifesteal = value({ lifeStealOf(it) }, noEquip, noCards),
            cooldownReduction = value({ cdrOf(it) }, noEquip, noCards),
            statusResistance = value({ statusResistOf(it) }, noEquip, noCards)
        )
    }
}
