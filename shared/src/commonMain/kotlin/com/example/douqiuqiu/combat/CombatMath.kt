package com.example.douqiuqiu.combat

import kotlin.math.abs

/**
 * 战斗基础公式工具（纯 Kotlin common，无 Android / java.* / 随机数 / 时钟依赖）。
 *
 * 只计算数字：不操作战斗单位、状态计时、事件、配置或存档。
 * 百分比一律用小数（20% 传 0.2）；不四舍五入；空列表乘积为 1、和为 0；
 * 相同数值可来自不同有效来源，保留重复项。
 *
 * 所有 Double 输入必须 finite；计算结果若溢出为 Infinity/NaN 会明确抛
 * IllegalArgumentException，不静默返回。规则依据 docs/rules/restoration-and-status.md。
 */
object CombatMath {

    /** 物理伤害倍率：1 - 0.06A/(1+0.06|A|)。护甲可为负，负护甲增伤。 */
    fun physicalDamageMultiplier(armor: Double): Double {
        requireFinite(armor, "armor")
        return checked(1.0 - 0.06 * armor / (1.0 + 0.06 * abs(armor)), "physicalDamageMultiplier")
    }

    /** 魔法伤害倍率：(1-基础抗性) × Π(1-独立魔抗)。独立魔抗不先相加。 */
    fun magicDamageMultiplier(baseResistance: Double, independentResistances: List<Double>): Double {
        requireFinite(baseResistance, "baseResistance")
        require(baseResistance in -1.0..1.0) { "baseResistance 必须在 [-1,1]，当前 $baseResistance" }
        var multiplier = 1.0 - baseResistance
        for (r in independentResistances) {
            requireFinite(r, "independentResistance")
            require(r in -1.0..1.0) { "independentResistance 必须在 [-1,1]，当前 $r" }
            multiplier *= 1.0 - r
        }
        return checked(multiplier, "magicDamageMultiplier")
    }

    /**
     * 恢复倍率：1 + clamp(A-R, -1, 1)，其中 A=1-Π(1-a)、R=1-Π(1-r)。
     * 增幅与抑制各自递减（不是 (1+A)*(1-R)）；a/r 各限制 [0,1]。
     */
    fun restorationMultiplier(amplifications: List<Double>, reductions: List<Double>): Double {
        var ampProduct = 1.0
        for (a in amplifications) {
            requireFinite(a, "amplification")
            require(a in 0.0..1.0) { "amplification 必须在 [0,1]，当前 $a" }
            ampProduct *= 1.0 - a
        }
        var redProduct = 1.0
        for (r in reductions) {
            requireFinite(r, "reduction")
            require(r in 0.0..1.0) { "reduction 必须在 [0,1]，当前 $r" }
            redProduct *= 1.0 - r
        }
        val amplification = 1.0 - ampProduct
        val reduction = 1.0 - redProduct
        val delta = (amplification - reduction).coerceIn(-1.0, 1.0)
        return checked(1.0 + delta, "restorationMultiplier")
    }

    /** 状态时长倍率：Π(1-抗性)，每项 [0,1]；不设 50% 抗性上限。 */
    fun statusDurationMultiplier(resistances: List<Double>): Double {
        var multiplier = 1.0
        for (r in resistances) {
            requireFinite(r, "resistance")
            require(r in 0.0..1.0) { "resistance 必须在 [0,1]，当前 $r" }
            multiplier *= 1.0 - r
        }
        return checked(multiplier, "statusDurationMultiplier")
    }

    /** 法术输出倍率：1 + Σ通用增幅 +（isPoison 时 Σ毒增幅，否则 0）。加成非负、可超过 1。 */
    fun spellOutputMultiplier(
        genericAmplifications: List<Double>,
        poisonAmplifications: List<Double>,
        isPoison: Boolean
    ): Double {
        var sum = 1.0
        for (g in genericAmplifications) {
            requireFinite(g, "genericAmplification")
            require(g >= 0.0) { "genericAmplification 不能为负，当前 $g" }
            sum += g
        }
        // 两个池都校验，避免未使用的池里藏非法值
        for (p in poisonAmplifications) {
            requireFinite(p, "poisonAmplification")
            require(p >= 0.0) { "poisonAmplification 不能为负，当前 $p" }
            if (isPoison) sum += p
        }
        return checked(sum, "spellOutputMultiplier")
    }

    /** 攻击吸血基础恢复：(生命伤害 + 0.5×护盾伤害) × Σ吸血率。吸血率合计可大于 100%。 */
    fun attackLifestealBase(
        lifeDamage: Double,
        shieldDamage: Double,
        lifestealRates: List<Double>
    ): Double {
        requireFinite(lifeDamage, "lifeDamage")
        require(lifeDamage >= 0.0) { "lifeDamage 不能为负，当前 $lifeDamage" }
        requireFinite(shieldDamage, "shieldDamage")
        require(shieldDamage >= 0.0) { "shieldDamage 不能为负，当前 $shieldDamage" }
        var rate = 0.0
        for (r in lifestealRates) {
            requireFinite(r, "lifestealRate")
            require(r >= 0.0) { "lifestealRate 不能为负，当前 $r" }
            rate += r
        }
        return checked((lifeDamage + 0.5 * shieldDamage) * rate, "attackLifestealBase")
    }

    /**
     * 换装生命换算：(当前生命/旧上限)×新上限。先除后乘避免中间溢出；
     * 死亡保持 0，不夹到 1；不触发治疗。
     */
    fun rescaleHealth(currentHealth: Double, oldMaxHealth: Double, newMaxHealth: Double): Double {
        requireFinite(currentHealth, "currentHealth")
        requireFinite(oldMaxHealth, "oldMaxHealth")
        requireFinite(newMaxHealth, "newMaxHealth")
        require(oldMaxHealth > 0.0) { "oldMaxHealth 必须为正，当前 $oldMaxHealth" }
        require(newMaxHealth > 0.0) { "newMaxHealth 必须为正，当前 $newMaxHealth" }
        require(currentHealth in 0.0..oldMaxHealth) {
            "currentHealth 必须在 [0, oldMaxHealth]=[0, $oldMaxHealth]，当前 $currentHealth"
        }
        return checked(currentHealth / oldMaxHealth * newMaxHealth, "rescaleHealth")
    }

    // ---------------- 内部工具 ----------------

    private fun requireFinite(value: Double, name: String) {
        require(value.isFinite()) { "$name 必须为有限值，当前 $value" }
    }

    private fun checked(value: Double, name: String): Double {
        require(value.isFinite()) { "$name 计算结果溢出或非法：$value" }
        return value
    }
}
