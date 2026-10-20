package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 外围任务21：永久成长业务验收（G01~G16）。
 *
 * 只做断言与报告，不改生产代码。成本期望按已批准表人工写入，
 * 不用 GrowthContent 自身生成预期。
 */
class GrowthIntegrationTest {

    private val definitions = mapOf(
        "essence" to ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        "backpack_fabric" to ItemDefinition("backpack_fabric", "织件", ItemKind.MATERIAL, 1, 2),
        "skill_shard" to ItemDefinition("skill_shard", "刻片", ItemKind.MATERIAL, 1, 1),
        "safe_lock" to ItemDefinition("safe_lock", "锁芯", ItemKind.MATERIAL, 3, 2),
        "safe_frame" to ItemDefinition("safe_frame", "框架", ItemKind.MATERIAL, 3, 3),
        "blade" to ItemDefinition("blade", "短刀", ItemKind.EQUIPMENT, 1, 1, price = 50),
        "cardA" to ItemDefinition("cardA", "技能卡", ItemKind.CARD, 1, 1, price = 100)
    )
    private val inventory = InventoryRules(definitions)
    private val growth = GrowthRules(inventory)
    private val journey = JourneyRules(inventory)

    private fun essence(n: Int, prefix: String = "e") = (1..n).map {
        StoredItem("$prefix$it", "essence", ItemPlace(Container.WAREHOUSE))
    }

    @Test fun g01FirstHeroUnlockConsumesExactlyThreeEssenceAndKeepsOriginalHeroes() {
        val items = essence(3)
        val s = InventoryState(gold = 300, items = items, progress = CampaignProgress(extracted = true))
        val r = growth.purchase(s, "hero:skywrath")
        assertTrue(r.error ?: "ok", r.ok)
        val n = r.state
        assertEquals(0, n.gold)
        assertTrue(n.items.none { it.definitionId == "essence" })
        assertEquals(setOf("juggernaut", "abaddon", "anti_mage", "axe", "faceless_void", "legion_commander", "skywrath"), n.progress.unlockedHeroes)
    }

    @Test fun g02RebuyingSameHeroIsRejectedWithoutSpending() {
        var s = InventoryState(gold = 300, items = essence(3), progress = CampaignProgress(extracted = true))
        s = growth.purchase(s, "hero:skywrath").state
        val r = growth.purchase(s, "hero:skywrath")
        assertFalse(r.ok)
        assertEquals(s, r.state)
        assertEquals(0, s.gold)
        assertEquals(7, s.progress.unlockedHeroes.size)
    }

    @Test fun g03StatusListsEveryApplicableGapNotJustTheFirst() {
        val s = InventoryState(gold = 100, items = essence(2),
            progress = CampaignProgress(extracted = false, clears = 0))
        val st = growth.status(s, "hero:viper")
        assertFalse(st.canPurchase)
        assertTrue(st.message, st.message.contains("金币还差400"))
        assertTrue(st.message, st.message.contains("精华还差3"))
        assertTrue(st.message, st.message.contains("还需通关1次"))
        val r = growth.purchase(s, "hero:viper")
        assertFalse(r.ok)
        assertEquals(s, r.state)
    }

    @Test fun g04OnlyWarehouseMaterialsCountTowardGrowth() {
        val items = listOf(
            StoredItem("w1", "essence", ItemPlace(Container.WAREHOUSE)),
            StoredItem("s1", "essence", ItemPlace(Container.SAFE)),
            StoredItem("b1", "essence", ItemPlace(Container.BACKPACK))
        )
        val s = InventoryState(gold = 300, items = items, progress = CampaignProgress(extracted = true))
        val st = growth.status(s, "hero:skywrath")
        assertFalse(st.canPurchase)
        assertTrue(st.message, st.message.contains("精华还差2"))
        val full = s.copy(items = items.map { it.copy(place = ItemPlace(Container.WAREHOUSE)) })
        val r = growth.purchase(full, "hero:skywrath")
        assertTrue(r.error ?: "ok", r.ok)
        assertTrue(r.state.items.isEmpty())
    }

    @Test fun g05BoundMaterialsAreConsumedFirstThenById() {
        val items = listOf(
            StoredItem("u2", "essence", ItemPlace(Container.WAREHOUSE)),
            StoredItem("b2", "essence", ItemPlace(Container.WAREHOUSE), bound = true),
            StoredItem("u1", "essence", ItemPlace(Container.WAREHOUSE)),
            StoredItem("b1", "essence", ItemPlace(Container.WAREHOUSE), bound = true)
        )
        val s = InventoryState(gold = 300, items = items, progress = CampaignProgress(extracted = true))
        val r = growth.purchase(s, "hero:skywrath")
        assertTrue(r.error ?: "ok", r.ok)
        assertEquals(listOf("u2"), r.state.items.map { it.instanceId })
    }

    @Test fun g06SkippingSkillTierIsRejectedEvenWithAbundantResources() {
        val s = InventoryState(gold = 9000, items = essence(30) + (1..10).map {
            StoredItem("sh$it", "skill_shard", ItemPlace(Container.WAREHOUSE))
        }, progress = CampaignProgress(extracted = true, clears = 6))
        val r = growth.purchase(s, "skills:2")
        assertFalse(r.ok)
        assertEquals(s, r.state)
        assertTrue(growth.status(s, "skills:2").message.contains("顺序"))
    }

    @Test fun g07SkillSlotsUpgradeInOrderWithExactCosts() {
        // 已批准表人工写入：250+3精华 / 500+5 / 900+8+2刻片 / 1500+12+4刻片
        val costs = listOf(
            Triple("skills:1", 250, mapOf("essence" to 3)),
            Triple("skills:2", 500, mapOf("essence" to 5)),
            Triple("skills:3", 900, mapOf("essence" to 8, "skill_shard" to 2)),
            Triple("skills:4", 1500, mapOf("essence" to 12, "skill_shard" to 4))
        )
        var s = InventoryState(gold = 5000, items = essence(40, "e") + (1..12).map {
            StoredItem("sh$it", "skill_shard", ItemPlace(Container.WAREHOUSE))
        }, progress = CampaignProgress(extracted = true, clears = 6))
        var slots = 4
        for ((id, gold, materials) in costs) {
            val before = s.items.size
            val r = growth.purchase(s, id)
            assertTrue("$id: ${r.error}", r.ok)
            slots++
            assertEquals(id, slots, r.state.skillSlots)
            assertEquals(id, s.gold - gold, r.state.gold)
            assertEquals(id, before - materials.values.sum(), r.state.items.size)
            s = r.state
        }
        assertEquals(8, s.skillSlots)
        assertFalse(growth.purchase(s, "skills:4").ok)
        assertFalse(growth.purchase(s, "skills:5").ok)
        assertEquals(s, growth.purchase(s, "skills:4").state)
    }

    @Test fun g08BackpackExpandsInOrderAndKeepsExistingItems() {
        // 已批准表人工写入：200+2织件 / 400+3 / 700+5 / 1000+8
        val costs = listOf(
            Triple("backpack:1", 200, mapOf("backpack_fabric" to 2)),
            Triple("backpack:2", 400, mapOf("backpack_fabric" to 3)),
            Triple("backpack:3", 700, mapOf("backpack_fabric" to 5)),
            Triple("backpack:4", 1000, mapOf("backpack_fabric" to 8))
        )
        val sizes = listOf(4 to 3, 5 to 3, 6 to 3, 6 to 4)
        val probe = StoredItem("probe", "blade", ItemPlace(Container.BACKPACK, 3, 1))
        var s = InventoryState(gold = 5000, items = listOf(probe) + (1..30).map {
            StoredItem("f$it", "backpack_fabric", ItemPlace(Container.WAREHOUSE))
        }, progress = CampaignProgress(extracted = true, clears = 6))
        costs.forEachIndexed { i, (id, gold, materials) ->
            val r = growth.purchase(s, id)
            assertTrue("$id: ${r.error}", r.ok)
            assertEquals(id, sizes[i].first, r.state.backpackWidth)
            assertEquals(id, sizes[i].second, r.state.backpackHeight)
            assertEquals(id, s.gold - gold, r.state.gold)
            assertEquals(id, probe, r.state.items.single { it.instanceId == "probe" })
            s = r.state
        }
        assertEquals(6 to 4, s.backpackWidth to s.backpackHeight)
    }

    @Test fun g09FirstSafeExpansionCostsSixHundredAndOneLock() {
        val items = listOf(
            StoredItem("l1", "safe_lock", ItemPlace(Container.WAREHOUSE)),
            StoredItem("e1", "essence", ItemPlace(Container.WAREHOUSE))
        )
        val s = InventoryState(gold = 600, items = items, progress = CampaignProgress(clears = 1))
        val r = growth.purchase(s, "safe:1")
        assertTrue(r.error ?: "ok", r.ok)
        assertEquals(0, r.state.gold)
        assertEquals(3 to 2, r.state.safeWidth to r.state.safeHeight)
        assertEquals(listOf("e1"), r.state.items.map { it.instanceId })
    }

    @Test fun g10SecondSafeTierRequiresClearsBackpackAndPreviousTier() {
        fun state(clears: Int, backpack: Pair<Int, Int>, safe: Pair<Int, Int>, gold: Int = 1200): InventoryState {
            val base = InventoryState(gold = gold,
                items = listOf(StoredItem("fr1", "safe_frame", ItemPlace(Container.WAREHOUSE))),
                progress = CampaignProgress(clears = clears))
                .copy(backpackWidth = backpack.first, backpackHeight = backpack.second)
            return base.copy(safeWidth = safe.first, safeHeight = safe.second)
        }
        assertFalse(growth.purchase(state(4, 4 to 3, 3 to 2), "safe:2").ok)
        val st = growth.status(state(5, 4 to 2, 3 to 2), "safe:2")
        assertFalse(st.canPurchase)
        assertTrue(st.message, st.message.contains("背包"))
        assertFalse(growth.purchase(state(5, 4 to 3, 2 to 2), "safe:2").ok)
        val r = growth.purchase(state(5, 4 to 3, 3 to 2), "safe:2")
        assertTrue(r.error ?: "ok", r.ok)
        assertEquals(0, r.state.gold)
        assertEquals(3 to 3, r.state.safeWidth to r.state.safeHeight)
        assertTrue(r.state.items.isEmpty())
    }

    @Test fun g11HeroThresholdsRejectNearMisses() {
        data class Case(val id: String, val gold: Int, val essence: Int, val clears: Int, val extraction: Boolean)
        val cases = listOf(
            Case("hero:skywrath", 300, 3, 0, true),
            Case("hero:viper", 500, 5, 1, false),
            Case("hero:phantom_assassin", 700, 7, 3, false),
            Case("hero:necrophos", 900, 9, 5, false)
        )
        for (c in cases) {
            fun state(gold: Int = c.gold, count: Int = c.essence, clears: Int = c.clears, extracted: Boolean = c.extraction) =
                InventoryState(gold = gold, items = essence(count),
                    progress = CampaignProgress(extracted = extracted, clears = clears))
            assertTrue(c.id, growth.purchase(state(), c.id).ok)
            assertFalse("${c.id} 缺1金币", growth.purchase(state(gold = c.gold - 1), c.id).ok)
            assertFalse("${c.id} 缺1精华", growth.purchase(state(count = c.essence - 1), c.id).ok)
            if (c.clears > 0) {
                assertFalse("${c.id} 缺1通关", growth.purchase(state(clears = c.clears - 1), c.id).ok)
            } else {
                assertFalse("${c.id} 缺撤离", growth.purchase(state(extracted = false), c.id).ok)
            }
        }
    }

    @Test fun g12InRunStagesRejectGrowthPurchases() {
        val s = InventoryState(gold = 5000, items = essence(10),
            progress = CampaignProgress(extracted = true, clears = 6))
        val batch = journey.ensureBatch(s)
        val entered = journey.enter(batch, batch.progress.chests.first().id, "juggernaut").state
        fun attempt(label: String, state: InventoryState) {
            val r = growth.purchase(state, "skills:1")
            assertFalse(label, r.ok)
            assertEquals(label, state, r.state)
        }
        attempt("PREPARATION", entered)
        attempt("READY", entered)
        val battling = journey.startBattle(entered).state
        attempt("BATTLE", battling)
        val run = battling.progress.run!!
        attempt("REWARD", battling.copy(progress = battling.progress.copy(run = run.copy(stage = JourneyStage.REWARD))))
        attempt("REST", battling.copy(progress = battling.progress.copy(run = run.copy(stage = JourneyStage.REST))))
    }

    @Test fun g13PermanentGrowthSurvivesDeathAndNewRunUsesNewCapacity() {
        var s = InventoryState(gold = 5000,
            items = essence(10) + (1..5).map { StoredItem("f$it", "backpack_fabric", ItemPlace(Container.WAREHOUSE)) } +
                listOf(StoredItem("lk", "safe_lock", ItemPlace(Container.WAREHOUSE)),
                    StoredItem("blade", "blade", ItemPlace(Container.BACKPACK, 0, 0))),
            progress = CampaignProgress(extracted = true, clears = 6))
        s = growth.purchase(s, "hero:skywrath").state
        s = growth.purchase(s, "skills:1").state
        s = growth.purchase(s, "backpack:1").state
        s = growth.purchase(s, "safe:1").state
        val batch = journey.ensureBatch(s)
        val entered = journey.enter(batch, batch.progress.chests.first().id, "skywrath").state
        val dead = journey.abandon(entered).state
        assertEquals(InventoryPhase.HOME, dead.phase)
        assertEquals(5, dead.skillSlots)
        assertEquals(4 to 3, dead.backpackWidth to dead.backpackHeight)
        assertEquals(3 to 2, dead.safeWidth to dead.safeHeight)
        assertTrue(dead.progress.unlockedHeroes.contains("skywrath"))
        assertTrue(dead.items.none { it.instanceId == "blade" })
        // 新挑战按新容量验证：扩容后的第三行可以放物品，天怒可再次入场
        val withBlade = dead.copy(items = dead.items + StoredItem("blade2", "blade", ItemPlace(Container.WAREHOUSE)))
        assertTrue(inventory.move(withBlade, "blade2", ItemPlace(Container.BACKPACK, 0, 2)).ok)
        val batch2 = journey.ensureBatch(dead)
        assertTrue(journey.enter(batch2, batch2.progress.chests.first().id, "skywrath").ok)
    }

    @Test fun g14UnlockedHeroCanEnterWhileLockedHeroIsRefused() {
        val s = InventoryState(gold = 1000, items = essence(3), progress = CampaignProgress(extracted = true))
        val batch = journey.ensureBatch(s)
        val before = batch.progress.chests
        val refused = journey.enter(batch, before.first().id, "skywrath")
        assertFalse(refused.ok)
        assertEquals(before, refused.state.progress.chests)
        val unlocked = growth.purchase(s, "hero:skywrath").state
        val batch2 = journey.ensureBatch(unlocked)
        val entered = journey.enter(batch2, batch2.progress.chests.first().id, "skywrath")
        assertTrue(entered.error ?: "ok", entered.ok)
        assertEquals("skywrath", entered.state.progress.run!!.heroId)
        val batch3 = journey.ensureBatch(unlocked)
        assertFalse(journey.enter(batch3, batch3.progress.chests.first().id, "viper").ok)
    }

    @Test fun g15SafeCapacityGatesOversizedItemsEvenRotated() {
        // lock/frame 各两份：一份作为成长材料被消耗，一份保留用于摆放验证
        var s = InventoryState(gold = 5000,
            items = listOf(
                StoredItem("lock", "safe_lock", ItemPlace(Container.WAREHOUSE)),
                StoredItem("lock2", "safe_lock", ItemPlace(Container.WAREHOUSE)),
                StoredItem("frame", "safe_frame", ItemPlace(Container.WAREHOUSE)),
                StoredItem("frame2", "safe_frame", ItemPlace(Container.WAREHOUSE)),
                StoredItem("f1", "backpack_fabric", ItemPlace(Container.WAREHOUSE)),
                StoredItem("f2", "backpack_fabric", ItemPlace(Container.WAREHOUSE))),
            progress = CampaignProgress(extracted = true, clears = 6))
        assertFalse(inventory.move(s, "lock2", ItemPlace(Container.SAFE, 0, 0)).ok)
        assertFalse(inventory.move(s, "lock2", ItemPlace(Container.SAFE, 0, 0, rotated = true)).ok)
        s = growth.purchase(s, "safe:1").state
        s = growth.purchase(s, "backpack:1").state
        assertTrue(inventory.move(s, "lock2", ItemPlace(Container.SAFE, 0, 0)).ok)
        assertFalse(inventory.move(s, "frame2", ItemPlace(Container.SAFE, 0, 0)).ok)
        s = growth.purchase(s, "safe:2").state
        assertTrue(inventory.move(s, "frame2", ItemPlace(Container.SAFE, 0, 0)).ok)
        // 绑定不等于死亡保护：背包里的绑定物品死亡照样损失
        val batch = journey.ensureBatch(s)
        val entered = journey.enter(batch, batch.progress.chests.first().id, "juggernaut").state
        val pre = entered.copy(items = entered.items +
            StoredItem("bb", "blade", ItemPlace(Container.BACKPACK, 0, 0), bound = true))
        val dead = journey.abandon(pre).state
        assertTrue(dead.items.none { it.instanceId == "bb" })
        assertTrue(dead.items.any { it.instanceId == "lock2" })
    }

    @Test fun g16InvalidSnapshotIsRejectedBeforeAnyDeduction() {
        val dup = listOf(
            StoredItem("e1", "essence", ItemPlace(Container.WAREHOUSE)),
            StoredItem("e1", "essence", ItemPlace(Container.WAREHOUSE)),
            StoredItem("e2", "essence", ItemPlace(Container.WAREHOUSE))
        )
        val s = InventoryState(gold = 300, items = dup, progress = CampaignProgress(extracted = true))
        val r = growth.purchase(s, "hero:skywrath")
        assertFalse(r.ok)
        assertEquals(s, r.state)
        assertEquals(300, r.state.gold)
        assertEquals(3, r.state.items.size)
    }
}
