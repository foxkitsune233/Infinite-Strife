package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

class HomeEconomyTest {
    private val rules = InventoryRules(listOf(
        ItemDefinition("broadsword", "阔剑", ItemKind.EQUIPMENT, 2, 1, price = 180, stats = EquipmentStats(attack = 15.0)),
        ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, price = 30, shopStock = 3),
        ItemDefinition("A1", "迅击", ItemKind.CARD, 1, 1, price = 100, shopStock = 3)
    ).associateBy { it.id })
    private val journey = JourneyRules(rules)
    private val economy = HomeEconomyRules(rules)
    private fun home(gold: Int = 1200): InventoryState = economy.recover(journey.ensureBatch(InventoryState(gold = gold)), 1_000)

    @Test fun refreshReplacesOnlySelectedChestAndResetsAppraisalWithoutRefund() {
        val initial = home()
        val box = initial.progress.chests.first()
        val appraised = journey.appraise(initial, box.id).state
        val result = economy.refreshChest(appraised, box.id, 1_000)
        assertTrue(result.ok)
        assertEquals(1190, result.state.gold) // v2首次鉴定10，免费刷新不退鉴定费
        assertEquals(2, result.state.progress.economy.refreshCharges)
        val next = result.state.progress.chests.first()
        assertNotEquals(box.id, next.id)
        assertEquals(0, next.appraisals)
        assertEquals(box.difficulty, next.difficulty)
        assertEquals(initial.progress.chests.drop(1), result.state.progress.chests.drop(1))
        assertFalse(economy.refreshChest(result.state, box.id, 1_000).ok)
    }

    @Test fun fourthRefreshCostsThirtyAndFifthInsufficientWalletIsAtomic() {
        var state = home(30)
        repeat(4) { state = economy.refreshChest(state, state.progress.chests.first().id, 1_000).also { assertTrue(it.ok) }.state }
        assertEquals(0, state.gold)
        assertEquals(0, state.progress.economy.refreshCharges)
        val rejected = economy.refreshChest(state, state.progress.chests.first().id, 1_000)
        assertFalse(rejected.ok)
        assertEquals(state, rejected.state)
    }

    @Test fun offlineRecoveryPreservesRemainderAndRollbackCannotRefill() {
        var state = home()
        repeat(3) { state = economy.refreshChest(state, state.progress.chests.first().id, 1_000).state }
        val restored = economy.recover(state, 2_701_000)
        assertEquals(1, restored.progress.economy.refreshCharges)
        assertEquals(1_801_000L, restored.progress.economy.refreshAnchorMs)
        assertEquals(restored, economy.recover(restored, 500))
        assertEquals(3, economy.recover(restored, 10_801_000).progress.economy.refreshCharges)
    }

    @Test fun reliefGrantsBoundGoodsAndVoucherWithoutSpendableGold() {
        val result = economy.claimRelief(home(0), 1_000)
        assertTrue(result.ok)
        assertEquals(0, result.state.gold)
        assertEquals(1, result.state.progress.economy.reliefCharges)
        assertEquals(1, result.state.progress.economy.vouchers)
        assertEquals(2, result.state.items.size)
        assertTrue(result.state.items.all { it.bound && it.place.container == Container.WAREHOUSE })
        assertEquals(2, result.state.items.single { it.definitionId == "salve" }.count)
        assertFalse(SupplyRules(rules).sell(result.state, result.state.items.first().instanceId).ok)
    }

    @Test fun reliefChecksSaleValueAcrossContainersAndCardStars() {
        val original = home(0)
        val valuable = original.copy(items = listOf(StoredItem("valuable", "A1", ItemPlace(Container.SAFE), star = 2)))
        assertFalse(economy.claimRelief(valuable, 1_000).ok) // 3 * 100 * 40% = 120, not below threshold
        assertTrue(economy.claimRelief(valuable.copy(items = valuable.items.map { it.copy(bound = true) }), 1_000).ok)
        assertFalse(economy.claimRelief(home(120), 1_000).ok)
    }

    @Test fun reliefAddsLowCostTargetOnlyWhenNoApplicableBoxExists() {
        val state = home(0)
        val expensive = state.copy(progress = state.progress.copy(chests = state.progress.chests.map { it.copy(ticket = 250) }))
        val supplied = economy.claimRelief(expensive, 1_000).state
        assertEquals(expensive.progress.chests.dropLast(1), supplied.progress.chests.dropLast(1))
        assertEquals(120, supplied.progress.chests.last().ticket)
        assertEquals(0, supplied.progress.chests.last().difficulty)
        assertEquals(0, supplied.progress.chests.last().appraisals)
        assertEquals(3, supplied.progress.economy.refreshCharges)
        assertEquals(state.progress.chests, economy.claimRelief(state, 1_000).state.progress.chests)
    }

    @Test fun reliefChargesExhaustThenRecoverIndividuallyAndGoodsStayUnique() {
        var state = home(0)
        repeat(2) { state = economy.claimRelief(state, 1_000).also { assertTrue(it.ok) }.state }
        assertFalse(economy.claimRelief(state, 1_000).ok)
        val third = economy.claimRelief(state, 7_201_000)
        assertTrue(third.ok)
        assertEquals(0, third.state.progress.economy.reliefCharges)
        assertEquals(6, third.state.items.map { it.instanceId }.toSet().size)
        assertEquals(3, third.state.progress.economy.vouchers)
    }

    @Test fun voucherPaysOnlySuccessfulLowCostEntryAndCannotReplay() {
        val state = economy.claimRelief(home(0), 1_000).state
        val expensive = state.progress.chests.last()
        val failed = journey.enter(state, expensive.id, "abaddon")
        assertFalse(failed.ok)
        assertEquals(state, failed.state)
        val entered = journey.enter(state, state.progress.chests.first().id, "abaddon")
        assertTrue(entered.ok)
        assertEquals(0, entered.state.gold)
        assertEquals(0, entered.state.progress.economy.vouchers)
        assertFalse(journey.enter(entered.state, state.progress.chests.first().id, "abaddon").ok)
        assertEquals(0, journey.abandon(entered.state).state.progress.economy.vouchers)
    }

    @Test fun voucherCoversOnlyOneHundredTwentyAndFailedTopUpDoesNotConsume() {
        val state = economy.claimRelief(home(0), 1_000).state
        val box = state.progress.chests.first().copy(ticket = 150)
        val raised = state.copy(progress = state.progress.copy(chests = listOf(box)))
        val insufficient = raised.copy(gold = 29)
        val failed = journey.enter(insufficient, box.id, "abaddon")
        assertFalse(failed.ok)
        assertEquals(insufficient, failed.state)
        val success = journey.enter(raised.copy(gold = 30), box.id, "abaddon")
        assertTrue(success.ok)
        assertEquals(0, success.state.gold)
        assertEquals(0, success.state.progress.economy.vouchers)
    }

    @Test fun paidShopRefreshKeepsOriginalAutomaticBoundaryAndAutoTakesPriority() {
        val state = home(99).copy(progress = home(99).progress.copy(shopStock = mapOf("salve" to 0)))
        val paid = economy.refreshShop(state, 3_600_000)
        assertTrue(paid.ok)
        assertEquals(49, paid.state.gold)
        assertTrue(paid.state.progress.shopStock.isEmpty())
        assertEquals(0L, paid.state.progress.shopCycle)
        val soldAgain = paid.state.copy(progress = paid.state.progress.copy(shopStock = mapOf("salve" to 0)))
        val auto = economy.refreshShop(soldAgain, 7_200_000)
        assertTrue(auto.ok)
        assertEquals(49, auto.state.gold)
        assertEquals(1L, auto.state.progress.shopCycle)
        assertTrue(auto.state.progress.shopStock.isEmpty())
        val failed = economy.refreshShop(auto.state, 7_200_000)
        assertFalse(failed.ok)
        assertEquals(auto.state, failed.state)
    }

    @Test fun homeActionsRejectDuringRunWithoutSpendingAnything() {
        val state = home()
        val entered = journey.enter(state, state.progress.chests.first().id, "abaddon").state
        assertFalse(economy.claimRelief(entered, 1_000).ok)
        assertFalse(economy.refreshShop(entered, 1_000).ok)
        assertFalse(economy.refreshChest(entered, state.progress.chests.first().id, 1_000).ok)
        assertEquals(entered, economy.recover(entered, 9_999_999))
    }
}
