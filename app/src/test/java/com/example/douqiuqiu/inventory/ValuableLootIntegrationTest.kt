package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.loot.ValuableLootCatalog
import com.example.douqiuqiu.ui.components.ItemIconCatalog
import org.junit.Assert.*
import org.junit.Test

/** External execution requested by the user; compilation alone is not acceptance. */
class ValuableLootIntegrationTest {
    private val loot = ValuableLootCatalog.definitions
    private val rules = InventoryRules((loot + ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1)).associateBy { it.id })
    private fun item(id: String, container: Container = Container.WAREHOUSE, instance: String = id) =
        StoredItem(instance, id, ItemPlace(container))

    @Test fun v01CatalogHasTwentyNonCombatUnboundNonStackingItemsAndIcons() {
        assertEquals(20, loot.size)
        assertEquals(20, loot.map { it.id }.distinct().size)
        listOf("白", "蓝", "紫", "金").forEach { q -> assertEquals(5, loot.count { it.quality == q }) }
        loot.forEach {
            assertEquals(ItemKind.LOOT, it.kind)
            assertEquals(EquipmentStats(), it.stats)
            assertFalse(it.combatUsable)
            assertFalse(it.boundOnAcquire)
            assertEquals(1, it.maxStack)
            assertEquals(0, it.shopStock)
            assertNotNull(ItemIconCatalog.explicit(it.id))
        }
    }

    @Test fun v02LargeItemsUsuallyCostMoreButCompactTreasuresOutvalueBulkyJunk() {
        val junk = rules.definitions.getValue("loot_old_carpet")
        val diamond = rules.definitions.getValue("loot_star_diamond")
        val screen = rules.definitions.getValue("loot_royal_screen")
        assertTrue(junk.width * junk.height > diamond.width * diamond.height)
        assertTrue(diamond.price > junk.price)
        assertTrue(screen.price > diamond.price)
        assertEquals(1000L, diamond.price.toLong() * 40 / 100)
        assertEquals(2600L, screen.price.toLong() * 40 / 100)
    }

    @Test fun v03QualityDifficultyAndProgressGateRareItems() {
        assertEquals(0, ValuableLootCatalog.weight("loot_star_diamond", "金", 2, 2))
        assertEquals(0, ValuableLootCatalog.weight("loot_ruby", "白", 0, 10))
        assertTrue(ValuableLootCatalog.weight("loot_ruby", "白", 2, 1) > 0)
        assertTrue(ValuableLootCatalog.weight("loot_star_diamond", "金", 2, 3) > 0)
        assertTrue(ValuableLootCatalog.weight("loot_copper_coin", "金", 2, 3) >
            ValuableLootCatalog.weight("loot_star_diamond", "金", 2, 3))
        assertEquals(0, ValuableLootCatalog.weight("unknown", "金", 2, 10))
        assertEquals(20, ValuableLootCatalog.attemptPercent("白", 0))
        assertEquals(45, ValuableLootCatalog.attemptPercent("金", 2))
    }

    @Test fun v04ChestGenerationIsDeterministicFixedAndIncludesCollectibles() {
        val s = InventoryState(progress = CampaignProgress(seed = 1729, clears = 3))
        val factory = ChestFactory(rules)
        val generated = factory.batch(s)
        assertEquals(generated, factory.batch(s))
        assertEquals(generated, factory.batch(generated))
        assertNull(rules.validate(generated))
        assertTrue(generated.progress.chests.flatMap { it.items }.any { rules.definitions.getValue(it.definitionId).kind == ItemKind.LOOT })
        generated.progress.chests.flatMap { it.items }.forEach {
            assertTrue(rules.definitions.getValue(it.definitionId).minimumDropClears <= s.progress.clears)
        }
    }

    @Test fun v05SalePaysExistingFortyPercentOnceAndRejectsBoundOrNonWarehouse() {
        val supplies = SupplyRules(rules)
        val s = InventoryState(gold = 100, items = listOf(item("loot_star_diamond")))
        val sold = supplies.sell(s, "loot_star_diamond")
        assertTrue(sold.ok)
        assertEquals(1100, sold.state.gold)
        assertTrue(sold.state.items.isEmpty())
        assertFalse(supplies.sell(sold.state, "loot_star_diamond").ok)
        assertFalse(supplies.sell(s.copy(items = s.items.map { it.copy(bound = true) }), "loot_star_diamond").ok)
        assertFalse(supplies.sell(s.copy(items = listOf(item("loot_star_diamond", Container.SAFE))), "loot_star_diamond").ok)
        assertFalse(supplies.buy(s, "loot_star_diamond", "purchase").ok)
    }

    @Test fun v06DeathKeepsOnlySafeLootAndSuccessTransfersBothToWarehouse() {
        val s = InventoryState(phase = InventoryPhase.PREPARATION, runGold = 50,
            items = listOf(item("loot_star_diamond", Container.SAFE), item("loot_copper_coin", Container.BACKPACK)))
        val lost = rules.settle(s, false)
        assertTrue(lost.ok)
        assertEquals(listOf("loot_star_diamond"), lost.state.items.map { it.definitionId })
        assertEquals(s.gold, lost.state.gold)
        val survived = rules.settle(s, true)
        assertTrue(survived.ok)
        assertEquals(2, survived.state.items.size)
        assertTrue(survived.state.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals(s.gold + 50, survived.state.gold)
    }

    @Test fun v07LargeLootCannotEquipOrFitStartingBagButFitsExpandedBag() {
        val s = InventoryState(items = listOf(item("loot_royal_screen")))
        assertFalse(rules.move(s, "loot_royal_screen", ItemPlace(Container.BACKPACK)).ok)
        assertFalse(rules.move(s, "loot_royal_screen", ItemPlace(Container.EQUIPMENT)).ok)
        assertFalse(rules.move(s, "loot_royal_screen", ItemPlace(Container.SKILLS)).ok)
        assertTrue(rules.move(s.copy(backpackWidth = 6, backpackHeight = 4), "loot_royal_screen", ItemPlace(Container.BACKPACK)).ok)
    }

    @Test fun v08ClearingTargetChestTransfersCollectibleEvenWhenBiggerThanStartingBag() {
        val journey = JourneyRules(rules)
        val box = TargetChest("test-box", "金", 0, 60, listOf(ChestItem("loot_royal_screen", 0, 0)))
        val entered = journey.enter(InventoryState(progress = CampaignProgress(chests = listOf(box))), box.id, "juggernaut")
        assertTrue(entered.ok)
        val run = entered.state.progress.run!!
        val finish = entered.state.copy(progress = entered.state.progress.copy(run = run.copy(round = 5, stage = JourneyStage.REWARD)))
        val result = LootFlowTestSupport.finishReward(journey, finish)
        assertTrue(result.ok)
        assertTrue(result.state.items.any { it.definitionId == "loot_royal_screen" && it.place.container == Container.WAREHOUSE })
        assertNull(result.state.progress.run)
    }
}
