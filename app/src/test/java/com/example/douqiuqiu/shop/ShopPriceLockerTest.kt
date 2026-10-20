package com.example.douqiuqiu.shop

import org.junit.Assert.assertEquals
import org.junit.Test

class ShopPriceLockerTest {

    private fun input(
        id: String,
        base: Int,
        stage: Double = 1.0,
        demand: Double = 1.0
    ) = ShopPriceInput(id, base, stage, demand)

    private fun lockOne(id: String, base: Int, stage: Double = 1.0, demand: Double = 1.0) =
        ShopPriceLocker.lock(listOf(input(id, base, stage, demand))).single()

    // ---------- 基础定价 ----------

    @Test
    fun `default factors keep base price`() {
        val r = lockOne("sword", 100)
        assertEquals(100, r.buyPrice)
        assertEquals(40, r.baseResalePrice)
    }

    @Test
    fun `stage and demand factors multiply`() {
        val r = lockOne("bow", 100, stage = 1.1, demand = 1.05)
        assertEquals(116, r.buyPrice) // 100 * 1.155 = 115.5 -> 116
    }

    @Test
    fun `product above cap clamps instead of pricing`() {
        // 1.1 * 1.1 = 1.21 已超上限，限幅后按 1.2 计价
        val r = lockOne("cap", 100, stage = 1.1, demand = 1.1)
        assertEquals(120, r.buyPrice)
    }

    @Test
    fun `total factor clamped to upper bound`() {
        val r = lockOne("armor", 100, stage = 2.0, demand = 3.0)
        assertEquals(120, r.buyPrice) // 6.0 -> 1.2
    }

    @Test
    fun `total factor clamped to lower bound`() {
        val r = lockOne("boots", 100, stage = 0.5, demand = 0.5)
        assertEquals(80, r.buyPrice) // 0.25 -> 0.8
    }

    @Test
    fun `half rounds up`() {
        assertEquals(6, lockOne("a", 5, demand = 1.1).buyPrice)   // 5.5 -> 6
        assertEquals(14, lockOne("b", 15, stage = 0.9).buyPrice)  // 13.5 -> 14
        assertEquals(23, lockOne("c", 25, stage = 0.9).buyPrice)  // 22.5 -> 23
    }

    @Test
    fun `below half rounds down`() {
        assertEquals(4, lockOne("d", 5, demand = 0.85).buyPrice) // 4.25 -> 4
        assertEquals(9, lockOne("f", 10, stage = 0.9).buyPrice)  // 9.0 -> 9
    }

    @Test
    fun `factor below floor clamps before pricing`() {
        val r = lockOne("g", 5, demand = 0.65) // 0.65 -> 0.8
        assertEquals(4, r.buyPrice)
    }

    @Test
    fun `zero base price stays zero`() {
        val r = lockOne("free", 0, stage = 1.2, demand = 1.2)
        assertEquals(0, r.buyPrice)
        assertEquals(0, r.baseResalePrice)
    }

    @Test
    fun `positive base price never drops below one`() {
        val r = lockOne("cheap", 1, stage = 0.5, demand = 0.5) // 0.25 -> 0.8 -> 0.8
        assertEquals(1, r.buyPrice)
        assertEquals(0, r.baseResalePrice)
    }

    // ---------- 锁价语义 ----------

    @Test
    fun `locked price does not change when input set changes later`() {
        val first = ShopPriceLocker.lock(listOf(input("a", 100), input("b", 200)))
        val before = first.map { it.buyPrice }
        // 上层之后重新整批锁价（加入新商品）不影响已锁批次
        val second = ShopPriceLocker.lock(listOf(input("a", 100), input("b", 200), input("c", 300)))
        assertEquals(before, second.take(2).map { it.buyPrice })
        assertEquals(first, second.subList(0, 2))
    }

    @Test
    fun `resale price ignores factor swings`() {
        val high = lockOne("gem", 1000, stage = 1.2, demand = 1.2)
        val low = lockOne("gem", 1000, stage = 0.5, demand = 0.5)
        assertEquals(1200, high.buyPrice)
        assertEquals(800, low.buyPrice)
        assertEquals(400, high.baseResalePrice)
        assertEquals(400, low.baseResalePrice)
    }

    @Test
    fun `int max value uses long for resale`() {
        val r = lockOne("priceless", Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, r.buyPrice)
        assertEquals(858993458, r.baseResalePrice) // floor(2147483647 * 40 / 100)
    }

    @Test
    fun `resale floors fractional part`() {
        assertEquals(4, lockOne("a", 10).baseResalePrice)  // 4.0
        assertEquals(4, lockOne("b", 12).baseResalePrice)  // 4.8 -> 4
        assertEquals(0, lockOne("c", 2).baseResalePrice)   // 0.8 -> 0
    }

    // ---------- 极端因子 ----------

    @Test
    fun `overflowing factor product clamps to upper bound`() {
        val r = lockOne("huge", 100, stage = Double.MAX_VALUE, demand = Double.MAX_VALUE)
        assertEquals(120, r.buyPrice)
    }

    @Test
    fun `underflowing factor product clamps to lower bound`() {
        val r = lockOne("tiny", 100, stage = Double.MIN_VALUE, demand = Double.MIN_VALUE)
        assertEquals(80, r.buyPrice)
    }

    @Test
    fun `finite huge factor alone still clamps`() {
        assertEquals(120, lockOne("a", 100, stage = Double.MAX_VALUE, demand = 1.0).buyPrice)
        assertEquals(80, lockOne("b", 100, stage = Double.MIN_VALUE, demand = 1.0).buyPrice)
    }

    // ---------- 溢出拒绝 ----------

    @Test(expected = IllegalArgumentException::class)
    fun `buy price overflow is rejected`() {
        lockOne("overflow", Int.MAX_VALUE, stage = 1.2, demand = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buy price overflow with both factors rejected`() {
        lockOne("overflow", 2_000_000_000, stage = 1.2, demand = 1.2)
    }

    // ---------- 校验 ----------

    @Test(expected = IllegalArgumentException::class)
    fun `nan factor rejected`() {
        lockOne("a", 100, stage = Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `infinite factor rejected`() {
        lockOne("a", 100, demand = Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero factor rejected`() {
        lockOne("a", 100, stage = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative factor rejected`() {
        lockOne("a", 100, demand = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative base price rejected`() {
        lockOne("a", -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate id rejected`() {
        ShopPriceLocker.lock(listOf(input("dup", 100), input("dup", 200)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank id rejected`() {
        lockOne("  ", 100)
    }

    // ---------- 输出形态 ----------

    @Test
    fun `output sorted by definition id`() {
        val r = ShopPriceLocker.lock(
            listOf(input("zeta", 10), input("alpha", 20), input("mid", 30))
        )
        assertEquals(listOf("alpha", "mid", "zeta"), r.map { it.definitionId })
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<LockedShopPrice>(), ShopPriceLocker.lock(emptyList()))
    }

    @Test
    fun `input mutation does not change result`() {
        val inputs = ArrayList(listOf(input("a", 100), input("b", 200)))
        val r = ShopPriceLocker.lock(inputs)
        val before = r.map { it.buyPrice }
        inputs.add(input("c", 300))
        inputs.clear()
        assertEquals(before, r.map { it.buyPrice })
    }
}
