package com.example.douqiuqiu.hero

import kotlin.math.pow
import kotlin.math.roundToInt

import com.example.douqiuqiu.quest.*

import com.example.douqiuqiu.inventory.InventoryResult
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState

/**
 * 英雄等级规则（0.1.6；0.1.7 重构）。
 *
 * 设计要点：
 * - 每英雄独立等级，**用营地金币升级**，不引入新货币、不加经验条；
 * - 设计上限 100 级（[MAX_LEVEL_DESIGN]），[LEVEL_CAP] 作为**初始基准/默认值（10）**；
 *   实际等级上限是**存档进度值**（`progress.levelCap`，0.1.7 起由「转生爬塔」模式通关累加提升），
 *   不再仅由本常量决定（详见 0.1.7 设计稿）；
 * - 升级金币 0.1.7 起由线性改为**指数增长**（[upgradeCost]），抑制后期堆级；
 * - 等级只提供**永久战斗属性**，与 [com.example.douqiuqiu.growth.GrowthContent] 的
 *   「不加永久战斗属性」承诺互补，两者不重叠；
 * - 属性在战斗初始化处一次性写入 [com.example.douqiuqiu.combat.Fighter]，
 *   战斗内不读等级，保证 DuelState 回放可复现。
 */
object HeroLevelRules {

    /** 设计上限；本版本不开放到此。 */
    const val MAX_LEVEL_DESIGN = 100

    /**
     * 初始等级上限基准/默认值（10）。
     * 实际上限由存档字段 `progress.levelCap` 决定（0.1.7 起经「转生爬塔」模式通关提升）；
     * 旧档缺省回退本值。
     */
    const val LEVEL_CAP = 10

    /** 升级费用指数基数 R（0.1.7 设计稿 §6 #1 定稿：1.20 温和）。 */
    const val COST_RATIO = 1.20

    /** 升级费用基数：Lv1→2 保持 100，不破坏新手体验。 */
    private const val COST_BASE = 100.0

    /**
     * 升到下一级所需营地金币。
     * 0.1.6：线性 `100 × 当前等级`。
     * 0.1.7 起改为**指数增长** `round(100 × 1.2^(level-1))`（0.1.7 设计稿 §2.4，R=1.20 定稿）：
     * Lv1→2=100，Lv9→10=430，到 10 级共 2080；后期费用指数抬头抑制堆级。
     */
    fun upgradeCost(level: Int): Int =
        (COST_BASE * COST_RATIO.pow((level.coerceAtLeast(1) - 1).toDouble())).roundToInt()

    /** 当前英雄等级（缺省视为 1 级）。 */
    fun level(state: InventoryState, heroId: String): Int = state.progress.heroLevels[heroId] ?: 1

    /**
     * 当前存档的动态等级上限（0.1.7）：`progress.levelCap`（缺省回退 [LEVEL_CAP]），
     * 由「转生爬塔」历史最高通关层驱动；旧档无该字段时取默认值 10。
     */
    fun levelCap(state: InventoryState): Int = state.progress.levelCap.coerceIn(1, MAX_LEVEL_DESIGN)

    /** 是否已达当前存档的等级上限（0.1.7 起读动态上限，不再读常量锁）。 */
    fun isCapped(state: InventoryState, level: Int): Boolean = level >= levelCap(state)

    /**
     * 攻击力系数：每级 +1.5%，每 5 级里程碑额外 +1%。
     * Lv1 为 1.0（无增益），Lv10 为 1.17。
     */
    fun attackMultiplier(level: Int): Double = 1.0 + 0.015 * (level - 1) + 0.01 * milestone(level)

    /** 生命系数：每级 +2.0%，每 5 级里程碑额外 +1%。Lv10 为 1.22。 */
    fun healthMultiplier(level: Int): Double = 1.0 + 0.020 * (level - 1) + 0.01 * milestone(level)

    /** 护甲加成：每级 +0.4，保留一位小数。 */
    fun armorBonus(level: Int): Double = round1(0.4 * (level - 1))

    /** 魔法抗性加成（百分点）：每级 +0.5，每 5 级里程碑额外 +1。 */
    fun magicResistBonus(level: Int): Double = 0.5 * (level - 1) + 1.0 * milestone(level)

    private fun milestone(level: Int): Int = level / 5

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    /**
     * 用营地金币为指定英雄升一级。
     *
     * 纯函数事务：被拒时返回操作前的原始快照。金币不足、已达版本锁、
     * 存档校验失败均不产生任何变更。
     */
    fun upgrade(s: InventoryState, heroId: String, rules: InventoryRules): InventoryResult {
        rules.validate(s)?.let { return InventoryResult(s, it) }
        if (s.phase != com.example.douqiuqiu.inventory.InventoryPhase.HOME || s.progress.run != null) return InventoryResult(s, "仅营地可升级英雄")
        if (heroId !in s.progress.unlockedHeroes) return InventoryResult(s, "英雄尚未解锁")
        if (heroId.isBlank()) return InventoryResult(s, "英雄资料无效")
        val current = level(s, heroId)
        if (isCapped(s, current)) return InventoryResult(s, "英雄等级已达当前版本上限（${levelCap(s)} 级）")
        val cost = upgradeCost(current)
        if (s.gold < cost) return InventoryResult(s, "金币不足，升级需要${cost}金币")
        val next = s.copy(
            gold = s.gold - cost,
            progress = s.progress.copy(heroLevels = s.progress.heroLevels + (heroId to current + 1))
        )
        return rules.validate(next)?.let { InventoryResult(s, it) } ?: InventoryResult(QuestTracker.record(next, QuestEvent.HeroLevelChanged(heroId, current + 1)))
    }
}
