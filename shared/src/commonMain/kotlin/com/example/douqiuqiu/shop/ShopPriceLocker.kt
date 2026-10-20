package com.example.douqiuqiu.shop

import kotlin.math.floor

/**
 * 商店价格锁定计算（纯 Kotlin common，不读钱包 / 次数 / 交易记录 / 时钟）。
 *
 * 一次货架生成时整批调用，返回后由上层保存；购买时直接读锁定价，不重算。
 * 本工具不设计需求模型：阶段与需求系数由调用方算好后随商品传入。
 */
data class ShopPriceInput(
    val definitionId: String,
    val basePrice: Int,
    val stageFactor: Double = 1.0,
    val demandFactor: Double = 1.0
)

data class LockedShopPrice(
    val definitionId: String,
    val buyPrice: Int,
    val baseResalePrice: Int
)

object ShopPriceLocker {

    /** 总系数下限 / 上限。 */
    const val FACTOR_MIN = 0.8
    const val FACTOR_MAX = 1.2

    private const val RESALE_NUMERATOR = 40L
    private const val RESALE_DENOMINATOR = 100L

    /**
     * 整批锁价，输出按 definitionId 字典序的独立列表。
     *
     * 买价 = 基准价 × 限幅后总系数，四舍五入（正数 .5 向上）；基准价 0 仍为 0，
     * 正价至少 1；结果超过 Int.MAX_VALUE 时抛 IllegalArgumentException（不饱和）。
     * 回收价 = floor(基准价 × 40 / 100)，Long 中间值，不受本轮系数波动影响，
     * 且只给一星基础物品的底价，星级 / 堆叠倍率由上层处理。
     */
    fun lock(inputs: List<ShopPriceInput>): List<LockedShopPrice> {
        validateInputs(inputs)
        return inputs.map { lockOne(it) }.sortedBy { it.definitionId }
    }

    private fun lockOne(input: ShopPriceInput): LockedShopPrice {
        val factor = totalFactor(input.stageFactor, input.demandFactor)
        val raw = input.basePrice.toDouble() * factor
        val rounded = floor(raw + 0.5)
        if (rounded > Int.MAX_VALUE.toDouble()) {
            throw IllegalArgumentException(
                "锁价结果超出 Int 范围：${input.definitionId} base=${input.basePrice} factor=$factor"
            )
        }
        val buyPrice = if (input.basePrice == 0) 0 else maxOf(1, rounded.toInt())
        val resale = input.basePrice.toLong() * RESALE_NUMERATOR / RESALE_DENOMINATOR
        return LockedShopPrice(input.definitionId, buyPrice, resale.toInt())
    }

    /**
     * 总系数 = 阶段系数 × 需求系数，限幅 0.8..1.2。
     *
     * 两个正有限因子的乘积只会下溢成 0 或溢出成 +Infinity（不会是 NaN）。
     * 有限值直接乘完再限幅；溢出时用分支除法安全判定方向——能溢出说明真实乘积
     * 已远超上限，取上限而不是把 Infinity 截断出意外结果。
     */
    internal fun totalFactor(stageFactor: Double, demandFactor: Double): Double {
        val raw = stageFactor * demandFactor
        if (raw.isFinite()) return raw.coerceIn(FACTOR_MIN, FACTOR_MAX)
        return if (stageFactor > FACTOR_MAX / demandFactor) FACTOR_MAX else FACTOR_MIN
    }

    private fun validateInputs(inputs: List<ShopPriceInput>) {
        val seen = HashSet<String>()
        for (input in inputs) {
            require(input.definitionId.isNotBlank()) { "definitionId 不能为空白" }
            require(seen.add(input.definitionId)) { "definitionId 重复：${input.definitionId}" }
            require(input.basePrice >= 0) {
                "basePrice 必须非负：${input.definitionId}=${input.basePrice}"
            }
            require(input.stageFactor.isFinite() && input.stageFactor > 0.0) {
                "stageFactor 必须为有限正数：${input.definitionId}=${input.stageFactor}"
            }
            require(input.demandFactor.isFinite() && input.demandFactor > 0.0) {
                "demandFactor 必须为有限正数：${input.definitionId}=${input.demandFactor}"
            }
        }
    }
}
