package com.example.douqiuqiu.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GrowthRequirementChecker 验收用例，对应 docs/tasks/03-growth-requirement-checker.md。
 */
class GrowthRequirementCheckerTest {

    // ---- 验收表 ----

    @Test
    fun `零需求可购买`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0),
            GrowthResources(gold = 0)
        )
        assertTrue(r.canPurchase)
        assertEquals(0L, r.missingGold)
        assertTrue(r.missingMaterials.isEmpty())
        assertFalse(r.needsExtraction)
        assertEquals(0, r.missingClears)
        assertTrue(r.missingLevels.isEmpty())
    }

    @Test
    fun `首次扩背包恰好满足`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(
                goldCost = 200,
                materials = mapOf("fabric" to 2),
                requiresExtraction = true
            ),
            GrowthResources(
                gold = 200,
                warehouseMaterials = mapOf("fabric" to 2),
                hasExtracted = true
            )
        )
        assertTrue(r.canPurchase)
        assertEquals(0L, r.missingGold)
        assertTrue(r.missingMaterials.isEmpty())
        assertFalse(r.needsExtraction)
    }

    @Test
    fun `金币不足`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 200),
            GrowthResources(gold = 150)
        )
        assertFalse(r.canPurchase)
        assertEquals(50L, r.missingGold)
    }

    @Test
    fun `没有材料ID时按0库存`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to 2)),
            GrowthResources(gold = 0, warehouseMaterials = emptyMap())
        )
        assertFalse(r.canPurchase)
        assertEquals(mapOf("fabric" to 2), r.missingMaterials)
    }

    @Test
    fun `多种材料只返回正缺口`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, materials = mapOf("essence" to 3, "shard" to 2)),
            GrowthResources(
                gold = 0,
                warehouseMaterials = mapOf("essence" to 1, "shard" to 5)
            )
        )
        assertFalse(r.canPurchase)
        assertEquals(mapOf("essence" to 2), r.missingMaterials)
    }

    @Test
    fun `尚未撤离不可购买`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, requiresExtraction = true),
            GrowthResources(gold = 0, hasExtracted = false)
        )
        assertFalse(r.canPurchase)
        assertTrue(r.needsExtraction)
    }

    @Test
    fun `通关数不能替代显式撤离资格`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, requiresExtraction = true),
            GrowthResources(gold = 0, hasExtracted = false, clearCount = 10)
        )
        assertFalse(r.canPurchase)
        assertTrue(r.needsExtraction)
        assertEquals(0, r.missingClears)
    }

    @Test
    fun `通关数量不足`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, minClears = 6),
            GrowthResources(gold = 0, clearCount = 4)
        )
        assertFalse(r.canPurchase)
        assertEquals(2, r.missingClears)
    }

    @Test
    fun `缺前置成长返回不足档数`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, requiredLevels = mapOf("backpack" to 1)),
            GrowthResources(gold = 0, levels = emptyMap())
        )
        assertFalse(r.canPurchase)
        assertEquals(mapOf("backpack" to 1), r.missingLevels)
    }

    @Test
    fun `前置成长超过要求不产生缺口`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, requiredLevels = mapOf("backpack" to 1)),
            GrowthResources(gold = 0, levels = mapOf("backpack" to 3))
        )
        assertTrue(r.canPurchase)
        assertTrue(r.missingLevels.isEmpty())
    }

    @Test
    fun `多类不足一次全部返回`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(
                goldCost = 200,
                materials = mapOf("fabric" to 2, "essence" to 1),
                requiresExtraction = true,
                minClears = 6,
                requiredLevels = mapOf("backpack" to 1)
            ),
            GrowthResources(
                gold = 150,
                warehouseMaterials = mapOf("fabric" to 1, "essence" to 0),
                hasExtracted = false,
                clearCount = 4,
                levels = emptyMap()
            )
        )
        assertFalse(r.canPurchase)
        assertEquals(50L, r.missingGold)
        assertEquals(mapOf("fabric" to 1, "essence" to 1), r.missingMaterials)
        assertTrue(r.needsExtraction)
        assertEquals(2, r.missingClears)
        assertEquals(mapOf("backpack" to 1), r.missingLevels)
    }

    @Test
    fun `需求外材料不产生额外要求`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to 1)),
            GrowthResources(
                gold = 0,
                warehouseMaterials = mapOf("fabric" to 1, "essence" to 100)
            )
        )
        assertTrue(r.canPurchase)
        assertTrue(r.missingMaterials.isEmpty())
    }

    @Test
    fun `Long边界不溢出`() {
        val poor = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = Long.MAX_VALUE),
            GrowthResources(gold = 0)
        )
        assertEquals(Long.MAX_VALUE, poor.missingGold)
        assertFalse(poor.canPurchase)

        val rich = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = Long.MAX_VALUE),
            GrowthResources(gold = Long.MAX_VALUE)
        )
        assertEquals(0L, rich.missingGold)
        assertTrue(rich.canPurchase)
    }

    @Test
    fun `需求数为0代表无该项要求`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(
                goldCost = 0,
                materials = mapOf("fabric" to 0),
                minClears = 0,
                requiredLevels = mapOf("backpack" to 0)
            ),
            GrowthResources(
                gold = 0,
                warehouseMaterials = mapOf("fabric" to 0),
                clearCount = 0,
                levels = mapOf("backpack" to 0)
            )
        )
        assertTrue(r.canPurchase)
        assertTrue(r.missingMaterials.isEmpty())
        assertTrue(r.missingLevels.isEmpty())
        assertEquals(0, r.missingClears)
    }

    @Test
    fun `库存恰好为0时缺口等于需求量`() {
        val r = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to 2)),
            GrowthResources(gold = 0, warehouseMaterials = mapOf("fabric" to 0))
        )
        assertEquals(mapOf("fabric" to 2), r.missingMaterials)
        assertFalse(r.canPurchase)
    }

    @Test
    fun `Int最大值材料边界`() {
        val almost = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to Int.MAX_VALUE)),
            GrowthResources(
                gold = 0,
                warehouseMaterials = mapOf("fabric" to Int.MAX_VALUE - 1)
            )
        )
        assertEquals(mapOf("fabric" to 1), almost.missingMaterials)

        val exact = GrowthRequirementChecker.evaluate(
            GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to Int.MAX_VALUE)),
            GrowthResources(gold = 0, warehouseMaterials = mapOf("fabric" to Int.MAX_VALUE))
        )
        assertTrue(exact.canPurchase)
    }

    @Test
    fun `非法输入抛异常`() {
        fun expectIllegal(name: String, block: () -> Unit) {
            try {
                block()
                throw AssertionError("$name 未抛 IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotEmpty())
            }
        }

        expectIllegal("负金币需求") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = -1), GrowthResources(gold = 0)
            )
        }
        expectIllegal("负金币持有") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0), GrowthResources(gold = -1)
            )
        }
        expectIllegal("负材料需求") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to -1)), GrowthResources(gold = 0)
            )
        }
        expectIllegal("负通关需求") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0, minClears = -1), GrowthResources(gold = 0)
            )
        }
        expectIllegal("负通关持有") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0), GrowthResources(gold = 0, clearCount = -2)
            )
        }
        expectIllegal("负前置档位需求") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0, requiredLevels = mapOf("backpack" to -1)),
                GrowthResources(gold = 0)
            )
        }
        expectIllegal("负当前档位") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0), GrowthResources(gold = 0, levels = mapOf("backpack" to -3))
            )
        }
        // 未被需求使用的负库存项同样非法
        expectIllegal("需求外负库存") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0, materials = mapOf("fabric" to 1)),
                GrowthResources(gold = 0, warehouseMaterials = mapOf("essence" to -5))
            )
        }
        expectIllegal("空白材料ID") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0, materials = mapOf("  " to 1)), GrowthResources(gold = 0)
            )
        }
        expectIllegal("空成长ID") {
            GrowthRequirementChecker.evaluate(
                GrowthRequirement(goldCost = 0, requiredLevels = mapOf("" to 1)), GrowthResources(gold = 0)
            )
        }
    }

    @Test
    fun `输入隔离返回独立快照`() {
        val needMaterials = linkedMapOf("fabric" to 3)
        val stockMaterials = linkedMapOf("fabric" to 1)
        val needLevels = linkedMapOf("backpack" to 2)
        val haveLevels = linkedMapOf("backpack" to 1)

        val requirement = GrowthRequirement(
            goldCost = 100,
            materials = needMaterials,
            requiredLevels = needLevels
        )
        val resources = GrowthResources(
            gold = 50,
            warehouseMaterials = stockMaterials,
            levels = haveLevels
        )

        val r = GrowthRequirementChecker.evaluate(requirement, resources)
        assertEquals(mapOf("fabric" to 2), r.missingMaterials)
        assertEquals(mapOf("backpack" to 1), r.missingLevels)
        assertEquals(50L, r.missingGold)

        // 计算后修改原始可变 Map：结果快照不变，输入也未被计算过程改动
        needMaterials["fabric"] = 99
        stockMaterials["fabric"] = 99
        needLevels["backpack"] = 99
        haveLevels["backpack"] = 99
        stockMaterials["essence"] = 7

        assertEquals(mapOf("fabric" to 2), r.missingMaterials)
        assertEquals(mapOf("backpack" to 1), r.missingLevels)
        assertEquals(50L, r.missingGold)
        assertFalse(r.canPurchase)
    }
}
