package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.SupplyRules
import org.junit.Assert.*
import org.junit.Test

class ItemProtectionSalesTest {
    private val definitions = listOf(
        ItemDefinition("gear", "装备", ItemKind.EQUIPMENT, 1, 1, price = 101),
        ItemDefinition("card", "卡", ItemKind.CARD, 1, 1, price = 101),
        ItemDefinition("potion", "药", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, price = 101)
    ).associateBy { it.id }
    private val inventory = InventoryRules(definitions)
    private val supply = SupplyRules(inventory)
    private fun item(id: String, definition: String = "gear") = StoredItem(id, definition, ItemPlace(Container.WAREHOUSE))
    private fun locked(state: InventoryState, vararg ids: String) = state.copy(progress = state.progress.copy(lockedItems = ids.toSet()))

    @Test fun toggleRequiresOwnedItemAndAllowsPreparationButRejectsBattle() {
        val rules = ItemLockRules(inventory)
        val state = InventoryState(items = listOf(item("a")))
        val locked = rules.toggle(state, "a")
        assertTrue(locked.ok)
        assertEquals(setOf("a"), locked.state.progress.lockedItems)
        assertEquals(state, rules.toggle(locked.state, "a").state)
        assertFalse(rules.toggle(state, "missing").ok)
        assertTrue(rules.toggle(state.copy(phase = InventoryPhase.PREPARATION), "a").ok)
        val battle = state.copy(phase = InventoryPhase.BATTLE)
        assertEquals(battle, rules.toggle(battle, "a").state)
        assertFalse(rules.toggle(battle, "a").ok)
    }

    @Test fun lockBlocksSellingCraftingAndCardMergingWithoutPartialMutation() {
        val state = locked(InventoryState(items = listOf(item("a"), item("b"))), "a")
        assertEquals(state, supply.sell(state, "a").state)
        assertFalse(supply.sell(state, "a").ok)
        val recipe = CraftingRecipe("combine", "gear", mapOf("gear" to 2))
        val crafting = CraftingRules(inventory, listOf(recipe))
        assertFalse(crafting.craft(state, recipe.id, listOf("a", "b"), "output").ok)
        val option = CraftingPresentation.options(state, listOf(recipe), definitions).single()
        assertEquals(1, option.missing)
        assertEquals(listOf("b"), option.ingredientIds)
        val cards = locked(InventoryState(items = listOf(item("c1", "card"), item("c2", "card"), item("c3", "card"))), "c2")
        assertEquals(cards, inventory.mergeCards(cards, listOf("c1", "c2", "c3")).state)
        assertFalse(inventory.mergeCards(cards, listOf("c1", "c2", "c3")).ok)
    }

    @Test fun batchSaleIsAtomicForEveryInvalidSelectionAndOverflow() {
        val state = locked(InventoryState(items = listOf(item("free"), item("locked"), item("bound").copy(bound = true),
            item("equipped").copy(place = ItemPlace(Container.EQUIPMENT)))), "locked")
        for (ids in listOf(emptyList(), listOf("free", "free"), listOf("free", "missing"),
            listOf("free", "locked"), listOf("free", "bound"), listOf("free", "equipped"))) {
            val result = supply.sellMany(state, ids)
            assertFalse("$ids", result.ok)
            assertEquals(state, result.state)
        }
        val rich = state.copy(gold = Int.MAX_VALUE)
        assertFalse(supply.sellMany(rich, listOf("free")).ok)
        assertEquals(rich, supply.sellMany(rich, listOf("free")).state)
        assertFalse(supply.sellMany(state.copy(phase = InventoryPhase.PREPARATION), listOf("free")).ok)
    }

    @Test fun batchQuoteMatchesActualPerInstanceRoundingIncludingStarsAndStacks() {
        val items = listOf(item("gear"), item("card2", "card").copy(star = 2),
            item("card3", "card").copy(star = 3), item("stack", "potion").copy(count = 3))
        assertEquals(listOf(40L, 121L, 363L, 121L), items.map(supply::saleValue))
        val state = InventoryState(items = items + item("keep"))
        val result = supply.sellMany(state, items.map { it.instanceId })
        assertTrue(result.ok)
        assertEquals(state.gold + 645, result.state.gold)
        assertEquals(listOf("keep"), result.state.items.map { it.instanceId })
    }

    @Test fun lockAllowsMovementAndDoesNotProtectAgainstDeathAndStaleIdsRemainValid() {
        val state = locked(InventoryState(items = listOf(item("a"))), "a", "old-consumed-id")
        val moved = inventory.move(state, "a", ItemPlace(Container.EQUIPMENT))
        assertTrue(moved.ok)
        val settled = inventory.settle(moved.state.copy(phase = InventoryPhase.PREPARATION), survived = false)
        assertTrue(settled.ok)
        assertTrue(settled.state.items.isEmpty())
        assertEquals(state.progress.lockedItems, settled.state.progress.lockedItems)
        assertNull(inventory.validate(settled.state))
        assertNotNull(inventory.validate(locked(state, " ")))
        assertNotNull(inventory.validate(locked(state, "x".repeat(129))))
        assertNotNull(inventory.validate(state.copy(progress = state.progress.copy(lockedItems = (0..10000).map { "i$it" }.toSet()))))
    }
}
