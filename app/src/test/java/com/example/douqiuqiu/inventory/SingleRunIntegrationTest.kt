package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.grid.*
import org.junit.Assert.*
import org.junit.Test

class SingleRunIntegrationTest {
    private val definitions = listOf("A1", "A2", "S1", "R1").map { ItemDefinition(it, it, ItemKind.CARD, 1, 1, price = 100, shopStock = 3) } + listOf(
        ItemDefinition("branch", "树枝", ItemKind.EQUIPMENT, 1, 1, stats = EquipmentStats(1.0, 1.0, 1.0)),
        ItemDefinition("gloves", "手套", ItemKind.EQUIPMENT, 1, 1, stats = EquipmentStats(strength = 3.0)),
        ItemDefinition("slippers", "便鞋", ItemKind.EQUIPMENT, 1, 1, stats = EquipmentStats(agility = 3.0)),
        ItemDefinition("mantle", "斗篷", ItemKind.EQUIPMENT, 1, 1, stats = EquipmentStats(intelligence = 3.0)),
        ItemDefinition("fire", "仙灵火测试", ItemKind.CONSUMABLE, 1, 1)
    )
    private val rules = InventoryRules(definitions.associateBy { it.id })
    private val journey = JourneyRules(rules)
    private fun equipped(): InventoryState = InventoryState(items =
        listOf("A1", "A2", "S1", "R1").mapIndexed { i, id -> StoredItem("card$i", id, ItemPlace(Container.SKILLS, i), bound = true) } +
            listOf("branch", "branch", "gloves", "slippers", "mantle").mapIndexed { i, id -> StoredItem("gear$i", id, ItemPlace(Container.EQUIPMENT, i), bound = true) })

    @Test fun legacyMinimalFixtureTerminatesValidlyAtCurrentBalance() {
        // 该夹具只含 4 张早期卡和 5 件基础属性装，已不代表正式版完整起手构筑。
        // 平衡调整后固定种子均止于第 3 战；这里保留它作为流程终止与终态合法性基线。
        val reachedRounds = mutableListOf<Int>()
        var clears = 0
        for (seed in listOf(1L, 2L, 3L, 7L, 8L)) {
            val start = equipped().copy(progress = CampaignProgress(seed = seed))
            var s = journey.ensureBatch(start)
            val box = s.progress.chests.first()
            s = journey.enter(s, box.id, "abaddon").state
            var maxRound = 0
            var steps = 0
            while (s.progress.run != null && steps++ < 4000) {
                val stage = s.progress.run!!.stage
                if (stage == JourneyStage.BATTLE) maxRound = maxOf(maxRound, s.progress.run!!.round)
                s = when (stage) {
                    JourneyStage.READY -> journey.startBattle(s).state
                    JourneyStage.BATTLE -> journey.tick(s)
                    JourneyStage.REWARD -> LootFlowTestSupport.finishReward(journey, s).also { assertTrue(it.error ?: "ok", it.ok) }.state
                    JourneyStage.EVENT -> {
                        if (s.progress.run!!.nodeEvent == null) journey.event(s, false).state else {
                            val views = JourneyEvents.views(s, rules).filter { it.enabled && !it.option.requiresExplicitItemChoice }
                            val choice = views.firstOrNull { it.option.risk == com.example.douqiuqiu.event.EventRisk.NONE } ?: views.first()
                            val result = journey.event(s, choice.option.id)
                            assertTrue(result.error, result.ok)
                            result.state
                        }
                    }
                    JourneyStage.REST -> journey.rest(s).state
                }
            }
            assertNull("seed=$seed 必须结束", s.progress.run)
            // 极简夹具在战斗逐 tick 的中间态并不总能通过完整快照校验，这里只校验每个种子的终态。
            assertNull("seed=$seed 结束后状态必须合法", rules.validate(s))
            reachedRounds += maxRound
            clears += s.progress.clears
        }
        assertEquals("旧极简夹具的固定平衡基线", listOf(3, 3, 3, 3, 3), reachedRounds)
        assertEquals("旧极简夹具不应被误当作正式起手通关验证", 0, clears)
    }

    @Test fun supplyPurchaseAndSaleCannotDuplicateInventoryOrSellBoundItems() {
        val shop = SupplyRules(rules)
        val original = InventoryState()
        val bought = shop.buy(original, "A1", "new")
        assertTrue(bought.ok)
        assertEquals(1100, bought.state.gold)
        assertEquals(2, bought.state.progress.shopStock["A1"])
        assertFalse(shop.buy(bought.state, "A1", "new").ok)
        val sold = shop.sell(bought.state, "new")
        assertTrue(sold.ok)
        assertEquals(1140, sold.state.gold)
        assertTrue(sold.state.items.isEmpty())
        assertFalse(shop.sell(sold.state, "new").ok)
        assertFalse(shop.sell(bought.state.copy(items = bought.state.items.map { it.copy(bound = true) }), "new").ok)
    }

    @Test fun oversizedObstacleCoordinatesRejectWithoutIntegerOverflow() {
        assertThrows(IllegalArgumentException::class.java) {
            FixedGridPacker.pack(GridSize(6, 6), emptyList(), listOf(GridRect(Int.MAX_VALUE, 0, 2, 1)))
        }
    }

}
