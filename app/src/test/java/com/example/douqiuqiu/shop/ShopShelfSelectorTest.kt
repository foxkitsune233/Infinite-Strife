package com.example.douqiuqiu.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务 14 验收：固定种子的精确结果由文档算法的独立推演（Lehmer 发生器逐步手算）得出，
 * 不是从实现反推的「 whatever it prints 」。
 */
class ShopShelfSelectorTest {

    private fun cand(id: String, shelf: ShopShelf, minClears: Int = 0, stock: Int = 1) =
        ShopCandidate(id, shelf, minClears, stock)

    /** 固定场景：常备 3（1 件被进度挡）、轮换 10、珍品 3；输入故意打乱顺序。 */
    private fun scenario(): List<ShopCandidate> = listOf(
        cand("card_e", ShopShelf.ROTATING),
        cand("gem_staple", ShopShelf.STAPLE),
        cand("card_j", ShopShelf.ROTATING),
        cand("relic_y", ShopShelf.RARE),
        cand("potion_staple", ShopShelf.STAPLE, minClears = 3),
        cand("card_a", ShopShelf.ROTATING),
        cand("blade_staple", ShopShelf.STAPLE),
        cand("relic_x", ShopShelf.RARE),
        cand("card_c", ShopShelf.ROTATING),
        cand("card_h", ShopShelf.ROTATING),
        cand("card_b", ShopShelf.ROTATING),
        cand("card_i", ShopShelf.ROTATING),
        cand("card_d", ShopShelf.ROTATING),
        cand("card_f", ShopShelf.ROTATING),
        cand("card_g", ShopShelf.ROTATING),
        cand("relic_z", ShopShelf.RARE)
    )

    private fun ids(offers: List<SelectedShopOffer>) = offers.map { it.definitionId }

    // ---------- 精确轨迹 ----------

    @Test
    fun `fixed seed yields exact trace`() {
        val r = ShopShelfSelector.select(scenario(), clears = 1, seed = 777)
        // 常备全上（potion_staple 需 3 次通关被挡）；轮换 10 选 6；珍品判定 82 无货
        assertEquals(
            listOf(
                SelectedShopOffer("blade_staple", ShopShelf.STAPLE, 1),
                SelectedShopOffer("gem_staple", ShopShelf.STAPLE, 1),
                SelectedShopOffer("card_b", ShopShelf.ROTATING, 1),
                SelectedShopOffer("card_c", ShopShelf.ROTATING, 1),
                SelectedShopOffer("card_d", ShopShelf.ROTATING, 1),
                SelectedShopOffer("card_f", ShopShelf.ROTATING, 1),
                SelectedShopOffer("card_g", ShopShelf.ROTATING, 1),
                SelectedShopOffer("card_h", ShopShelf.ROTATING, 1)
            ),
            r.offers
        )
        assertEquals(292520297L, r.nextSeed)
    }

    @Test
    fun `rare in stock picks one of eligible`() {
        val r = ShopShelfSelector.select(scenario(), clears = 1, seed = 31337)
        // 珍品判定 < 30 有货，relic_x 是字典序第一个合格珍品
        assertEquals(
            listOf("card_c", "card_d", "card_f", "card_g", "card_h", "card_i"),
            ids(r.offers.filter { it.shelf == ShopShelf.ROTATING })
        )
        assertEquals(listOf("relic_x"), ids(r.offers.filter { it.shelf == ShopShelf.RARE }))
        assertEquals(835071801L, r.nextSeed)
    }

    @Test
    fun `stock passes through and grouping order holds`() {
        val candidates = listOf(
            cand("z_staple", ShopShelf.STAPLE, stock = 5),
            cand("a_staple", ShopShelf.STAPLE, stock = 1),
            cand("m_rot", ShopShelf.ROTATING, stock = 9),
            cand("b_rot", ShopShelf.ROTATING, stock = 2),
            cand("rare_1", ShopShelf.RARE, stock = 3)
        )
        val r = ShopShelfSelector.select(candidates, clears = 0, seed = 42)
        // 分组顺序：常备 -> 轮换 -> 珍品；组内字典序
        assertEquals(
            listOf(
                SelectedShopOffer("a_staple", ShopShelf.STAPLE, 1),
                SelectedShopOffer("z_staple", ShopShelf.STAPLE, 5),
                SelectedShopOffer("b_rot", ShopShelf.ROTATING, 2),
                SelectedShopOffer("m_rot", ShopShelf.ROTATING, 9)
            ),
            r.offers
        )
    }

    // ---------- 结构性质 ----------

    @Test
    fun `reordered input gives identical result`() {
        val a = ShopShelfSelector.select(scenario(), clears = 1, seed = 777)
        val b = ShopShelfSelector.select(scenario().shuffled(java.util.Random(7)), clears = 1, seed = 777)
        assertEquals(a, b)
    }

    @Test
    fun `same input twice is identical`() {
        val a = ShopShelfSelector.select(scenario(), clears = 1, seed = 777)
        val b = ShopShelfSelector.select(scenario(), clears = 1, seed = 777)
        assertEquals(a, b)
        assertNotSame(a, b)
        assertNotSame(a.offers, b.offers)
    }

    @Test
    fun `rotating capped at six without duplicates`() {
        val many = (1..20).map { cand("card_$it", ShopShelf.ROTATING) }
        val r = ShopShelfSelector.select(many, clears = 0, seed = 2024)
        val rot = r.offers.filter { it.shelf == ShopShelf.ROTATING }
        assertEquals(6, rot.size)
        assertEquals(6, ids(rot).toSet().size)
    }

    @Test
    fun `rotating fewer than six selects all`() {
        val few = listOf(
            cand("card_a", ShopShelf.ROTATING),
            cand("card_b", ShopShelf.ROTATING),
            cand("card_c", ShopShelf.ROTATING)
        )
        val r = ShopShelfSelector.select(few, clears = 0, seed = 2024)
        assertEquals(listOf("card_a", "card_b", "card_c"), ids(r.offers.filter { it.shelf == ShopShelf.ROTATING }))
    }

    @Test
    fun `rotating exactly six selects all six`() {
        val six = (1..6).map { cand("card_$it", ShopShelf.ROTATING) }
        val r = ShopShelfSelector.select(six, clears = 0, seed = 2024)
        assertEquals(6, r.offers.size)
    }

    @Test
    fun `progress gates every shelf`() {
        val candidates = listOf(
            cand("open_staple", ShopShelf.STAPLE),
            cand("locked_staple", ShopShelf.STAPLE, minClears = 2),
            cand("open_rot", ShopShelf.ROTATING),
            cand("locked_rot", ShopShelf.ROTATING, minClears = 2),
            cand("open_rare", ShopShelf.RARE),
            cand("locked_rare", ShopShelf.RARE, minClears = 2)
        )
        val r = ShopShelfSelector.select(candidates, clears = 1, seed = 777)
        assertEquals(listOf("open_staple"), ids(r.offers.filter { it.shelf == ShopShelf.STAPLE }))
        assertEquals(listOf("open_rot"), ids(r.offers.filter { it.shelf == ShopShelf.ROTATING }))
        assertTrue(r.offers.none { it.definitionId.startsWith("locked") })

        // 通关数达到后全部开放
        val r2 = ShopShelfSelector.select(candidates, clears = 2, seed = 777)
        assertEquals(2, r2.offers.filter { it.shelf == ShopShelf.STAPLE }.size)
        assertEquals(2, r2.offers.filter { it.shelf == ShopShelf.ROTATING }.size)
    }

    @Test
    fun `empty rare pool still consumes the rare check`() {
        val noRare = (1..5).map { cand("card_$it", ShopShelf.ROTATING) }
        val r = ShopShelfSelector.select(noRare, clears = 0, seed = 777)
        assertEquals(5, r.offers.size)
        assertEquals(835269582L, r.nextSeed) // 5 次轮换 + 1 次珍品判定
    }

    @Test
    fun `empty input still consumes the rare check once`() {
        val r = ShopShelfSelector.select(emptyList(), clears = 0, seed = 777)
        assertTrue(r.offers.isEmpty())
        assertEquals(37506567L, r.nextSeed) // 777 * 48271 % 2147483647
    }

    @Test
    fun `staple only consumes no randomness`() {
        val staples = listOf(cand("s1", ShopShelf.STAPLE), cand("s2", ShopShelf.STAPLE))
        val r = ShopShelfSelector.select(staples, clears = 0, seed = 42)
        assertEquals(2, r.offers.size)
        assertEquals(2027382L, r.nextSeed) // 与空输入一致：只走珍品判定
    }

    @Test
    fun `next seed chains forward`() {
        val first = ShopShelfSelector.select(scenario(), clears = 1, seed = 777)
        val second = ShopShelfSelector.select(scenario(), clears = 1, seed = first.nextSeed)
        assertEquals(292520297L, first.nextSeed)
        assertTrue(second.nextSeed in 1L..2147483646L)
    }

    // ---------- 校验 ----------

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate definition id rejected`() {
        ShopShelfSelector.select(
            listOf(
                cand("dup", ShopShelf.STAPLE),
                cand("dup", ShopShelf.ROTATING)
            ),
            clears = 0,
            seed = 1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank definition id rejected`() {
        ShopShelfSelector.select(listOf(cand("   ", ShopShelf.STAPLE)), clears = 0, seed = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative minimum clears rejected`() {
        ShopShelfSelector.select(listOf(cand("x", ShopShelf.STAPLE, minClears = -1)), clears = 0, seed = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stock out of range rejected`() {
        ShopShelfSelector.select(listOf(cand("x", ShopShelf.STAPLE, stock = 0)), clears = 0, seed = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stock above range rejected`() {
        ShopShelfSelector.select(listOf(cand("x", ShopShelf.STAPLE, stock = 101)), clears = 0, seed = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ineligible candidate is still validated`() {
        // 未开放候选也不能悄悄放过坏配置
        ShopShelfSelector.select(listOf(cand("x", ShopShelf.STAPLE, minClears = 99, stock = 0)), clears = 0, seed = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seed zero rejected`() {
        ShopShelfSelector.select(emptyList(), clears = 0, seed = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `seed above range rejected`() {
        ShopShelfSelector.select(emptyList(), clears = 0, seed = 2147483647L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative clears rejected`() {
        ShopShelfSelector.select(emptyList(), clears = -1, seed = 1)
    }

    // ---------- 输入隔离 ----------

    @Test
    fun `input mutation does not change returned snapshot`() {
        val input = ArrayList(scenario())
        val r = ShopShelfSelector.select(input, clears = 1, seed = 777)
        val before = ArrayList(r.offers)
        input.add(cand("card_zzz", ShopShelf.ROTATING))
        input.removeAt(0)
        assertEquals(before, r.offers)
        // 返回列表是新集合，改动它不影响下一次调用
        (r.offers as MutableList<SelectedShopOffer>).clear()
        val again = ShopShelfSelector.select(scenario(), clears = 1, seed = 777)
        assertEquals(before, again.offers)
    }
}
