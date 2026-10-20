package com.example.douqiuqiu.growth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthMaterialPlannerTest {

    private fun item(
        id: String,
        def: String,
        bound: Boolean = false,
        inWarehouse: Boolean = true
    ) = GrowthMaterialInstance(id, def, bound, inWarehouse)

    // ---------- 足额精确消耗 ----------

    @Test
    fun `exact consumption picks needed count in id order`() {
        val items = listOf(
            item("f2", "fabric"),
            item("f1", "fabric"),
            item("f3", "fabric")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 2))
        assertTrue(r.success)
        assertEquals(listOf("f1", "f2"), r.consumedInstanceIds)
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun `unneeded items are retained`() {
        val items = listOf(
            item("f1", "fabric"),
            item("w1", "wood"),
            item("e1", "essence")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 1))
        assertTrue(r.success)
        assertEquals(listOf("f1"), r.consumedInstanceIds)
    }

    @Test
    fun `multiple independent instances of same definition`() {
        val items = (1..5).map { item("inst_$it", "shard") }
        val r = GrowthMaterialPlanner.plan(items, mapOf("shard" to 3))
        assertEquals(listOf("inst_1", "inst_2", "inst_3"), r.consumedInstanceIds)
    }

    // ---------- 绑定优先 ----------

    @Test
    fun `bound materials consumed first`() {
        val items = listOf(
            item("plain_1", "fabric"),
            item("bound_1", "fabric", bound = true),
            item("plain_2", "fabric")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 1))
        assertEquals(listOf("bound_1"), r.consumedInstanceIds)
    }

    @Test
    fun `bound first then plain by instance id`() {
        val items = listOf(
            item("plain_2", "fabric"),
            item("plain_1", "fabric"),
            item("bound_2", "fabric", bound = true),
            item("bound_1", "fabric", bound = true)
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 3))
        assertEquals(listOf("bound_1", "bound_2", "plain_1"), r.consumedInstanceIds)
    }

    // ---------- 选择顺序 ----------

    @Test
    fun `definitions processed in id order`() {
        val items = listOf(
            item("z1", "zeta_mat"),
            item("a1", "alpha_mat"),
            item("m1", "mid_mat")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("zeta_mat" to 1, "alpha_mat" to 1))
        assertEquals(listOf("a1", "z1"), r.consumedInstanceIds)
    }

    @Test
    fun `bound priority applies within each definition`() {
        val items = listOf(
            item("n_a", "a_mat"),
            item("b_a", "a_mat", bound = true),
            item("x_b", "b_mat", bound = true),
            item("n_b", "b_mat")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("a_mat" to 2, "b_mat" to 1))
        assertEquals(listOf("b_a", "n_a", "x_b"), r.consumedInstanceIds)
    }

    @Test
    fun `reordered input gives identical plan`() {
        val items = listOf(
            item("f2", "fabric"),
            item("f1", "fabric", bound = true),
            item("w1", "wood"),
            item("b1", "bone")
        )
        val costs = mapOf("fabric" to 1, "bone" to 1)
        val a = GrowthMaterialPlanner.plan(items, costs)
        val b = GrowthMaterialPlanner.plan(items.shuffled(java.util.Random(3)), costs)
        assertEquals(a, b)
        // definitionId 字典序：bone 先于 fabric
        assertEquals(listOf("b1", "f1"), a.consumedInstanceIds)
    }

    // ---------- 不足 ----------

    @Test
    fun `insufficient returns full missing without partial consumption`() {
        val items = listOf(
            item("f1", "fabric"),
            item("w1", "wood")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 3, "wood" to 2))
        assertEquals(false, r.success)
        assertTrue(r.consumedInstanceIds.isEmpty())
        assertEquals(mapOf("fabric" to 2, "wood" to 1), r.missing)
    }

    @Test
    fun `missing lists only insufficient kinds in id order`() {
        val items = listOf(
            item("a1", "aaa"),
            item("c1", "ccc")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("aaa" to 5, "bbb" to 1, "ccc" to 1))
        assertEquals(false, r.success)
        assertEquals(listOf("aaa", "bbb"), r.missing.keys.toList())
        assertEquals(mapOf("aaa" to 4, "bbb" to 1), r.missing)
    }

    @Test
    fun `exact stock is enough`() {
        val items = (1..2).map { item("i$it", "fabric") }
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 2))
        assertTrue(r.success)
        assertEquals(listOf("i1", "i2"), r.consumedInstanceIds)
    }

    // ---------- 仓库过滤 ----------

    @Test
    fun `out of warehouse instances cannot be consumed`() {
        val items = listOf(
            item("safe_1", "fabric", inWarehouse = false),
            item("wh_1", "fabric")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 1))
        assertEquals(listOf("wh_1"), r.consumedInstanceIds)

        val r2 = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 2))
        assertEquals(false, r2.success)
        assertEquals(mapOf("fabric" to 1), r2.missing)
    }

    @Test
    fun `bound out of warehouse still excluded`() {
        val items = listOf(
            item("safe_bound", "fabric", bound = true, inWarehouse = false),
            item("wh_plain", "fabric")
        )
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 1))
        assertEquals(listOf("wh_plain"), r.consumedInstanceIds)
    }

    // ---------- 空与边界 ----------

    @Test
    fun `empty costs is a successful empty plan`() {
        val r = GrowthMaterialPlanner.plan(listOf(item("f1", "fabric")), emptyMap())
        assertTrue(r.success)
        assertTrue(r.consumedInstanceIds.isEmpty())
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun `empty items with empty costs succeeds`() {
        val r = GrowthMaterialPlanner.plan(emptyList(), emptyMap())
        assertTrue(r.success)
        assertTrue(r.consumedInstanceIds.isEmpty())
    }

    @Test
    fun `cost for absent definition reports full missing`() {
        val r = GrowthMaterialPlanner.plan(listOf(item("f1", "fabric")), mapOf("ghost" to 2))
        assertEquals(false, r.success)
        assertEquals(mapOf("ghost" to 2), r.missing)
        assertTrue(r.consumedInstanceIds.isEmpty())
    }

    // ---------- 校验 ----------

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate instance id rejected`() {
        GrowthMaterialPlanner.plan(
            listOf(item("dup", "fabric"), item("dup", "wood")),
            mapOf("fabric" to 1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank instance id rejected`() {
        GrowthMaterialPlanner.plan(listOf(item("  ", "fabric")), emptyMap())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank definition id rejected`() {
        GrowthMaterialPlanner.plan(listOf(item("i1", " ")), emptyMap())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of warehouse instance is still validated`() {
        GrowthMaterialPlanner.plan(
            listOf(item("i1", "fabric"), item("", "wood", inWarehouse = false)),
            emptyMap()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank cost key rejected`() {
        GrowthMaterialPlanner.plan(emptyList(), mapOf(" " to 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero cost rejected`() {
        GrowthMaterialPlanner.plan(emptyList(), mapOf("fabric" to 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative cost rejected`() {
        GrowthMaterialPlanner.plan(emptyList(), mapOf("fabric" to -2))
    }

    // ---------- 输入隔离 ----------

    @Test
    fun `input mutation does not change plan`() {
        val items = ArrayList(listOf(item("f1", "fabric"), item("f2", "fabric")))
        val costs = LinkedHashMap<String, Int>()
        costs["fabric"] = 1
        val r = GrowthMaterialPlanner.plan(items, costs)
        items.add(item("f3", "fabric"))
        costs["fabric"] = 2
        costs["wood"] = 5
        // 已返回的计划是快照：不受之后对输入集合的修改影响
        assertEquals(listOf("f1"), r.consumedInstanceIds)
        assertTrue(r.success)
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun `returned collections are fresh snapshots`() {
        val items = listOf(item("f1", "fabric"), item("f2", "fabric"))
        val expected = listOf("f1", "f2")
        val r = GrowthMaterialPlanner.plan(items, mapOf("fabric" to 2))
        assertEquals(expected, r.consumedInstanceIds)
        // 清空返回列表不影响后续重新规划
        (r.consumedInstanceIds as MutableList<String>).clear()
        assertEquals(expected, GrowthMaterialPlanner.plan(items, mapOf("fabric" to 2)).consumedInstanceIds)
    }
}
