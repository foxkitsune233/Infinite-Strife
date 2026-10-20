package com.example.douqiuqiu.combat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CombatMath 验收用例，对应 docs/tasks/06-combat-math.md。
 */
class CombatMathTest {

    private val eps = 1e-9

    private fun eq(expected: Double, actual: Double) = assertEquals(expected, actual, eps)

    @Test
    fun `物理伤害倍率`() {
        eq(1.0, CombatMath.physicalDamageMultiplier(0.0))
        eq(0.625, CombatMath.physicalDamageMultiplier(10.0))
        eq(1.375, CombatMath.physicalDamageMultiplier(-10.0))
    }

    @Test
    fun `魔法伤害倍率`() {
        // 基础 25% + 独立 20% → 0.75 × 0.8 = 0.6
        eq(0.6, CombatMath.magicDamageMultiplier(0.25, listOf(0.2)))
        // 空独立抗性 → 只算基础
        eq(0.75, CombatMath.magicDamageMultiplier(0.25, emptyList()))
        // 独立魔抗不先相加：两个 50% 是 0.5×0.5
        eq(0.25, CombatMath.magicDamageMultiplier(0.0, listOf(0.5, 0.5)))
        // 负独立抗性表示魔法易伤，增伤
        eq(1.5, CombatMath.magicDamageMultiplier(0.0, listOf(-0.5)))
    }

    @Test
    fun `恢复增强与抑制各自递减`() {
        // 两个 20% 增幅：A = 1-0.8×0.8 = 0.36
        eq(1.36, CombatMath.restorationMultiplier(listOf(0.2, 0.2), emptyList()))
        // 两个 20% 抑制：R = 0.36
        eq(0.64, CombatMath.restorationMultiplier(emptyList(), listOf(0.2, 0.2)))
        // 两个 20% 增幅 + 30% 抑制 → 1.06
        eq(1.06, CombatMath.restorationMultiplier(listOf(0.2, 0.2), listOf(0.3)))
        // 空列表 → 1
        eq(1.0, CombatMath.restorationMultiplier(emptyList(), emptyList()))
    }

    @Test
    fun `百分之百抑制`() {
        eq(0.0, CombatMath.restorationMultiplier(emptyList(), listOf(1.0)))
    }

    @Test
    fun `状态时长倍率`() {
        eq(1.0, CombatMath.statusDurationMultiplier(emptyList()))
        eq(0.64, CombatMath.statusDurationMultiplier(listOf(0.2, 0.2)))
        // 100% 抗性 → 时长为 0
        eq(0.0, CombatMath.statusDurationMultiplier(listOf(1.0)))
    }

    @Test
    fun `法术输出倍率按毒选择`() {
        eq(1.2, CombatMath.spellOutputMultiplier(listOf(0.2), emptyList(), isPoison = false))
        eq(1.2, CombatMath.spellOutputMultiplier(emptyList(), listOf(0.2), isPoison = true))
        // 毒 + 通用同时计入
        eq(1.5, CombatMath.spellOutputMultiplier(listOf(0.2), listOf(0.3), isPoison = true))
        // 非毒忽略毒池
        eq(1.2, CombatMath.spellOutputMultiplier(listOf(0.2), listOf(0.3), isPoison = false))
        eq(1.0, CombatMath.spellOutputMultiplier(emptyList(), emptyList(), isPoison = false))
    }

    @Test
    fun `攻击吸血基础恢复`() {
        // (100 + 0.5×50) × (0.2+0.1) = 37.5
        eq(37.5, CombatMath.attackLifestealBase(100.0, 50.0, listOf(0.2, 0.1)))
        // 全打盾按 50% 折算
        eq(20.0, CombatMath.attackLifestealBase(0.0, 200.0, listOf(0.2)))
        // 吸血率合计可超过 100%
        eq(150.0, CombatMath.attackLifestealBase(100.0, 0.0, listOf(0.6, 0.9)))
        // 无吸血率 → 0
        eq(0.0, CombatMath.attackLifestealBase(100.0, 50.0, emptyList()))
    }

    @Test
    fun `换装生命按比例换算`() {
        eq(1000.0, CombatMath.rescaleHealth(500.0, 1000.0, 2000.0))
        // 满生命同比例
        eq(500.0, CombatMath.rescaleHealth(1000.0, 1000.0, 500.0))
        // 死亡保持 0，不夹到 1
        eq(0.0, CombatMath.rescaleHealth(0.0, 1000.0, 500.0))
        // 反复换装不刷血：来回换算后回到原值
        var hp = 500.0
        var max = 1000.0
        repeat(4) {
            val next = if (max == 1000.0) 2000.0 else 1000.0
            hp = CombatMath.rescaleHealth(hp, max, next)
            max = next
        }
        eq(500.0, hp)
    }

    @Test
    fun `非法输入抛异常`() {
        fun expectIllegal(name: String, block: () -> Unit) {
            try {
                block()
                throw AssertionError("$name 未抛 IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assert(e.message!!.isNotEmpty())
            }
        }

        expectIllegal("护甲NaN") { CombatMath.physicalDamageMultiplier(Double.NaN) }
        expectIllegal("护甲Infinity") { CombatMath.physicalDamageMultiplier(Double.POSITIVE_INFINITY) }
        expectIllegal("基础抗性越界") { CombatMath.magicDamageMultiplier(1.5, emptyList()) }
        expectIllegal("独立抗性越界") { CombatMath.magicDamageMultiplier(0.0, listOf(-1.5)) }
        expectIllegal("独立抗性NaN") { CombatMath.magicDamageMultiplier(0.0, listOf(Double.NaN)) }
        expectIllegal("增幅越界") { CombatMath.restorationMultiplier(listOf(1.5), emptyList()) }
        expectIllegal("抑制为负") { CombatMath.restorationMultiplier(emptyList(), listOf(-0.1)) }
        expectIllegal("状态抗性越界") { CombatMath.statusDurationMultiplier(listOf(1.2)) }
        expectIllegal("法术增幅为负") {
            CombatMath.spellOutputMultiplier(listOf(-0.1), emptyList(), isPoison = false)
        }
        expectIllegal("毒增幅为负即使非毒") {
            CombatMath.spellOutputMultiplier(emptyList(), listOf(-0.1), isPoison = false)
        }
        expectIllegal("吸血率为负") {
            CombatMath.attackLifestealBase(10.0, 0.0, listOf(-0.2))
        }
        expectIllegal("生命伤害为负") {
            CombatMath.attackLifestealBase(-1.0, 0.0, emptyList())
        }
        expectIllegal("旧上限为0") { CombatMath.rescaleHealth(0.0, 0.0, 100.0) }
        expectIllegal("新上限为负") { CombatMath.rescaleHealth(0.0, 100.0, -1.0) }
        expectIllegal("当前生命超上限") { CombatMath.rescaleHealth(101.0, 100.0, 100.0) }
        expectIllegal("当前生命为负") { CombatMath.rescaleHealth(-1.0, 100.0, 100.0) }
    }

    @Test
    fun `极端有限输入导致溢出时抛错`() {
        // 通用增幅相加溢出
        try {
            CombatMath.spellOutputMultiplier(
                listOf(Double.MAX_VALUE, Double.MAX_VALUE), emptyList(), isPoison = false
            )
            throw AssertionError("未抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("溢出"))
        }
        // 吸血乘积溢出
        try {
            CombatMath.attackLifestealBase(
                Double.MAX_VALUE, Double.MAX_VALUE, listOf(2.0)
            )
            throw AssertionError("未抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("溢出"))
        }
    }
}
