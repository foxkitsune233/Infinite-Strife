package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.shop.*
import org.junit.Assert.*
import org.junit.Test

class ShopIntegrationTest {
    private val definitions = listOf(
        ItemDefinition("component", "组件", ItemKind.EQUIPMENT, 1, 1, price = 100, shopStock = 3),
        ItemDefinition("rare", "珍品", ItemKind.EQUIPMENT, 1, 1, price = 1000, shopStock = 1, shopShelf = ShopShelf.RARE, minimumShopClears = 3)
    ) + (0..7).map { ItemDefinition("card$it", "卡$it", ItemKind.CARD, 1, 1, price = 100, shopStock = 3, shopShelf = ShopShelf.ROTATING) }
    private val inventory = InventoryRules(definitions.associateBy { it.id })
    private val shop = ShopRules(inventory)
    private val economy = HomeEconomyRules(inventory)
    private fun home() = economy.recover(InventoryState(), 1000)

    @Test fun firstShelfHasLockedPricesAndOnlySelectedRotationCanBePurchased() {
        val s = home()
        val shelf = s.progress.shop!!
        assertEquals(6, shelf.offers.count { it.shelf == ShopShelf.ROTATING })
        assertFalse(shelf.offers.any { it.definitionId == "rare" })
        assertEquals(90, shelf.offers.single { it.definitionId == "component" }.price)
        val absent = (0..7).map { "card$it" }.first { id -> shelf.offers.none { it.definitionId == id } }
        val rejected = SupplyRules(inventory).buy(s, absent, "absent")
        assertFalse(rejected.ok)
        assertEquals(s, rejected.state)
        val bought = SupplyRules(inventory).buy(s, "component", "new")
        assertTrue(bought.ok)
        assertEquals(1110, bought.state.gold)
        assertEquals(90, bought.state.progress.shop!!.offers.single { it.definitionId == "component" }.price)
        assertEquals(1, bought.state.progress.shop!!.purchases["component"])
    }

    @Test fun initialMigrationRetainsSoldOutItemsAndReopeningCannotReroll() {
        val old = InventoryState(progress = CampaignProgress(shopStock = mapOf("component" to 0)))
        val migrated = economy.recover(old, 1000)
        assertEquals(0, migrated.progress.shopStock["component"])
        assertFalse(SupplyRules(inventory).buy(migrated, "component", "new").ok)
        val reopened = economy.recover(migrated, 2000)
        assertEquals(migrated.progress.shop, reopened.progress.shop)
        assertEquals(migrated.progress.shopStock, reopened.progress.shopStock)
    }

    @Test fun paidRefreshLocksNewDemandPriceAndPreservesAutomaticBoundary() {
        var s = home()
        s = SupplyRules(inventory).buy(s, "component", "buy").state
        val revision = s.progress.shop!!.revision
        val refreshed = economy.refreshShop(s, 3_600_000)
        assertTrue(refreshed.ok)
        assertEquals(1060, refreshed.state.gold)
        assertEquals(revision + 1, refreshed.state.progress.shop!!.revision)
        assertEquals(93, refreshed.state.progress.shop!!.offers.single { it.definitionId == "component" }.price)
        val automatic = economy.recover(refreshed.state, 7_200_000)
        assertEquals(1060, automatic.gold)
        assertEquals(1L, automatic.progress.shopCycle)
        assertEquals(revision + 2, automatic.progress.shop!!.revision)
    }

    @Test fun purchaseAtRefreshBoundaryRejectsOldQuoteWithoutSpending() {
        val s = home()
        val result = economy.buy(s, "component", "new", 7_200_000, s.progress.shop!!.revision)
        assertFalse(result.ok)
        assertEquals(s, result.state)
    }

    @Test fun shopGenerationDoesNotTouchFixedChestContentsOrChallengeSeed() {
        val s = home()
        val changed = shop.generate(s)
        assertEquals(s.progress.chests, changed.progress.chests)
        assertEquals(s.progress.seed, changed.progress.seed)
    }

    // ---------------- S01~S16 业务验收（外部执行批次） ----------------

    @Test fun s01FirstShelfHasStapleSixDistinctRotationNoRareAndLockedPrice() {
        val s = home()
        val shelf = s.progress.shop!!
        val staples = shelf.offers.filter { it.shelf == ShopShelf.STAPLE }
        assertEquals(1, staples.size)
        assertEquals("component", staples.single().definitionId)
        val rotating = shelf.offers.filter { it.shelf == ShopShelf.ROTATING }
        assertEquals(6, rotating.size)
        assertEquals(6, rotating.map { it.definitionId }.toSet().size)
        assertTrue(shelf.offers.none { it.shelf == ShopShelf.RARE })
        assertEquals(90, shelf.offers.single { it.definitionId == "component" }.price)
        assertEquals(1, shelf.revision)
    }

    @Test fun s02StaplePurchaseDeductsLockedPriceAndGrantsUniqueWarehouseInstance() {
        val s = home()
        val r = economy.buy(s, "component", "inst-1", 1000, s.progress.shop!!.revision)
        assertTrue(r.error ?: "ok", r.ok)
        val n = r.state
        assertEquals(1110, n.gold)
        assertEquals(2, n.progress.shopStock["component"])
        assertEquals(1, n.progress.shop!!.purchases["component"])
        assertEquals(90, n.progress.shop!!.offers.single { it.definitionId == "component" }.price)
        val bought = n.items.single { it.instanceId == "inst-1" }
        assertEquals("component", bought.definitionId)
        assertEquals(Container.WAREHOUSE, bought.place.container)
        val dup = economy.buy(n, "component", "inst-1", 1000, n.progress.shop!!.revision)
        assertFalse(dup.ok)
        assertEquals(n, dup.state)
    }

    @Test fun s03UnlistedDuplicateIdInsufficientGoldAndSoldOutAllFailWithoutPartialChange() {
        val s = home()
        val shelf = s.progress.shop!!
        val absent = (0..7).map { "card$it" }.first { id -> shelf.offers.none { it.definitionId == id } }
        val r1 = SupplyRules(inventory).buy(s, absent, "x1")
        assertFalse(r1.ok)
        assertEquals(s, r1.state)
        val bought = SupplyRules(inventory).buy(s, "component", "dup").state
        val r2 = SupplyRules(inventory).buy(bought, "component", "dup")
        assertFalse(r2.ok)
        assertEquals(bought, r2.state)
        val poor = s.copy(gold = 89)
        val r3 = SupplyRules(inventory).buy(poor, "component", "x3")
        assertFalse(r3.ok)
        assertEquals(poor, r3.state)
        var soldOut = s
        repeat(3) { soldOut = SupplyRules(inventory).buy(soldOut, "component", "s$it").state }
        assertEquals(0, soldOut.progress.shopStock["component"])
        val r4 = SupplyRules(inventory).buy(soldOut, "component", "x4")
        assertFalse(r4.ok)
        assertEquals(soldOut, r4.state)
    }

    @Test fun s04ThreeStaplePurchasesExhaustStockAndFourthFailsWithoutCharge() {
        var s = home()
        repeat(3) { i ->
            val r = economy.buy(s, "component", "c$i", 1000, s.progress.shop!!.revision)
            assertTrue(r.error ?: "ok", r.ok)
            assertEquals(1200 - 90 * (i + 1), r.state.gold)
            s = r.state
        }
        assertEquals(0, s.progress.shopStock["component"])
        assertEquals(930, s.gold)
        val fourth = economy.buy(s, "component", "c3", 1000, s.progress.shop!!.revision)
        assertFalse(fourth.ok)
        assertEquals(s, fourth.state)
    }

    @Test fun s05ReopenSameTimeSameCycleAndRollbackNeverReroll() {
        val s = home()
        val again = economy.recover(s, 1000)
        assertEquals(s.progress.shop, again.progress.shop)
        assertEquals(s.progress.shopStock, again.progress.shopStock)
        val back = economy.recover(s, 500)
        assertEquals(s.progress.shop, back.progress.shop)
        assertEquals(s.progress.shopStock, back.progress.shopStock)
        assertEquals(s.progress.shop!!.nextSeed, back.progress.shop!!.nextSeed)
        assertEquals(s.progress.shop!!.revision, back.progress.shop!!.revision)
    }

    @Test fun s06LegacySoldOutSnapshotMigratesInSameCycleWithoutRestocking() {
        val old = InventoryState(progress = CampaignProgress(shopStock = mapOf("component" to 0)))
        val migrated = economy.recover(old, 1000)
        assertEquals(0, migrated.progress.shopStock["component"])
        assertFalse(SupplyRules(inventory).buy(migrated, "component", "n").ok)
        assertEquals(3, migrated.progress.shop!!.offers.single { it.definitionId == "component" }.stock)
    }

    @Test fun s07LegacySnapshotCrossingAutomaticBoundaryGetsFreshBatchAndStock() {
        val old = InventoryState(progress = CampaignProgress(shopStock = mapOf("component" to 0)))
        val migrated = economy.recover(old, 7_200_000)
        assertEquals(1, migrated.progress.shop!!.revision)
        assertEquals(1L, migrated.progress.shopCycle)
        val r = economy.buy(migrated, "component", "n", 7_200_000, migrated.progress.shop!!.revision)
        assertTrue(r.error ?: "ok", r.ok)
        assertEquals(1110, r.state.gold)
    }

    @Test fun s08PaidRefreshChargesFiftyAndDemandRaisesNextLockPrice() {
        var s = home()
        s = economy.buy(s, "component", "b1", 1000, s.progress.shop!!.revision).state
        val revision = s.progress.shop!!.revision
        val refreshed = economy.refreshShop(s, 3_600_000)
        assertTrue(refreshed.error ?: "ok", refreshed.ok)
        assertEquals(1060, refreshed.state.gold)
        assertEquals(revision + 1, refreshed.state.progress.shop!!.revision)
        assertEquals(93, refreshed.state.progress.shop!!.offers.single { it.definitionId == "component" }.price)
    }

    @Test fun s09AutomaticBoundaryRefreshesFreeAndKeepsOriginalBoundary() {
        var s = home()
        s = economy.buy(s, "component", "b1", 1000, s.progress.shop!!.revision).state
        s = economy.refreshShop(s, 3_600_000).state
        val revision = s.progress.shop!!.revision
        val automatic = economy.recover(s, 7_200_000)
        assertEquals(1060, automatic.gold)
        assertEquals(1L, automatic.progress.shopCycle)
        assertEquals(revision + 1, automatic.progress.shop!!.revision)
        assertEquals(7_200_000L, economy.shopCountdown(automatic, 7_200_000))
    }

    @Test fun s10AutomaticBoundaryWinsOverPaidRefreshWhenWalletIsEmpty() {
        val s = home().copy(gold = 0)
        val r = economy.refreshShop(s, 7_200_000)
        assertTrue(r.error ?: "ok", r.ok)
        assertEquals(0, r.state.gold)
        assertEquals(s.progress.shop!!.revision + 1, r.state.progress.shop!!.revision)
        assertEquals(1L, r.state.progress.shopCycle)
    }

    @Test fun s11PaidRefreshFailsWithInsufficientGoldAndLeavesSnapshotUntouched() {
        val s = home().copy(gold = 49)
        val r = economy.refreshShop(s, 3_600_000)
        assertFalse(r.ok)
        assertEquals(s, r.state)
    }

    @Test fun s12StaleQuoteRejectedThenNewShelfAllowsPurchaseAtNewPrice() {
        val s = home()
        val stale = s.progress.shop!!.revision
        val rejected = economy.buy(s, "component", "n", 7_200_000, stale)
        assertFalse(rejected.ok)
        assertEquals(s, rejected.state)
        val reopened = economy.recover(s, 7_200_000)
        val fresh = reopened.progress.shop!!.revision
        assertTrue(fresh > stale)
        val bought = economy.buy(reopened, "component", "n2", 7_200_000, fresh)
        assertTrue(bought.error ?: "ok", bought.ok)
        assertEquals(1110, bought.state.gold)
        assertEquals(90, bought.state.progress.shop!!.offers.single { it.definitionId == "component" }.price)
    }

    @Test fun s13ChallengePhaseRejectsShopOperationsAndRecoverKeepsRun() {
        val s = home()
        val journey = JourneyRules(inventory)
        val entered = journey.enter(s, s.progress.chests.first().id, "juggernaut").state
        assertEquals(InventoryPhase.PREPARATION, entered.phase)
        assertFalse(economy.buy(entered, "component", "n", 2000, entered.progress.shop!!.revision).ok)
        assertFalse(economy.refreshShop(entered, 2000).ok)
        val recovered = economy.recover(entered, 2000)
        assertEquals(entered, recovered)
        assertEquals(entered.items, recovered.items)
        assertEquals(entered.progress.shop, recovered.progress.shop)
        assertEquals(entered.progress.run, recovered.progress.run)
    }

    @Test fun s14ShelfGenerationKeepsChestsCoordinatesAppraisalsAndSeed() {
        val s = home()
        val before = s.progress.chests
        val seedBefore = s.progress.seed
        val changed = shop.generate(s)
        assertEquals(before, changed.progress.chests)
        assertEquals(seedBefore, changed.progress.seed)
        before.zip(changed.progress.chests).forEach { (a, b) ->
            assertEquals(a.items.map { Triple(it.definitionId, it.x, it.y) }, b.items.map { Triple(it.definitionId, it.x, it.y) })
            assertEquals(a.items.map { it.reveal }, b.items.map { it.reveal })
            assertEquals(a.appraisals, b.appraisals)
        }
        assertTrue(changed.progress.shop!!.nextSeed in 1..2147483646)
    }

    @Test fun s15WithThreeClearsOnlyQualifiedRareAppearsAtMostOnce() {
        val seedsWithRare = listOf(1234L, 31337L, 8080L, 100L)
        val seedsWithoutRare = listOf(1729L, 42L, 777L, 12345L, 99999L, 555L, 2024L, 7L, 256L, 4096L, 65537L)
        fun shelfWithSeed(seed: Long): ShopSnapshot {
            val preset = ShopSnapshot(1, seed, listOf(StoredShopOffer("component", ShopShelf.STAPLE, 90, 3)), emptyMap())
            val base = InventoryState(progress = CampaignProgress(clears = 3, shop = preset))
            return shop.generate(base).progress.shop!!
        }
        for (seed in seedsWithRare) {
            val rares = shelfWithSeed(seed).offers.filter { it.shelf == ShopShelf.RARE }
            assertEquals("seed=$seed", 1, rares.size)
            assertEquals("rare", rares.single().definitionId)
            assertEquals(1, rares.single().stock)
            assertEquals(1020, rares.single().price)
        }
        for (seed in seedsWithoutRare) {
            val shelf = shelfWithSeed(seed)
            assertTrue("seed=$seed", shelf.offers.none { it.shelf == ShopShelf.RARE })
            assertTrue(shelf.offers.all { it.definitionId in inventory.definitions.keys })
            assertTrue(shelf.offers.count { it.shelf == ShopShelf.ROTATING } <= 6)
            assertEquals(6, shelf.offers.count { it.shelf == ShopShelf.ROTATING })
        }
    }

    @Test fun s16SellUsesBasePriceTimesStarCopiesAndBoundItemsAreLocked() {
        val supply = SupplyRules(inventory)
        val withEquip = home().copy(items = listOf(StoredItem("e1", "component", ItemPlace(Container.WAREHOUSE))))
        val soldEquip = supply.sell(withEquip, "e1")
        assertTrue(soldEquip.error ?: "ok", soldEquip.ok)
        assertEquals(1240, soldEquip.state.gold)
        assertTrue(soldEquip.state.items.isEmpty())
        for ((star, copies) in listOf(1 to 1, 2 to 3, 3 to 9)) {
            val s = home().copy(items = listOf(StoredItem("c$star", "card0", ItemPlace(Container.WAREHOUSE), star = star)))
            val r = supply.sell(s, "c$star")
            assertTrue(r.error ?: "ok", r.ok)
            assertEquals(1200 + 40 * copies, r.state.gold)
        }
        val base = home()
        val bought = economy.buy(base, "component", "b1", 1000, base.progress.shop!!.revision).state
        assertEquals(1110, bought.gold)
        val sold = supply.sell(bought, "b1")
        assertTrue(sold.error ?: "ok", sold.ok)
        assertEquals(1150, sold.state.gold)
        val bound = home().copy(items = listOf(StoredItem("r1", "rare", ItemPlace(Container.WAREHOUSE), bound = true)))
        val locked = supply.sell(bound, "r1")
        assertFalse(locked.ok)
        assertEquals(bound, locked.state)
    }
}
