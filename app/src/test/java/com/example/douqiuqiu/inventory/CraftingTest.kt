package com.example.douqiuqiu.inventory

import org.junit.Assert.*
import org.junit.Test

class CraftingTest {
    private val defs = listOf("circlet", "gauntlets", "bracer").map { ItemDefinition(it, it, ItemKind.EQUIPMENT, 1, 1) } +
        ItemDefinition("recipe_bracer", "卷轴", ItemKind.SCROLL, 1, 1)
    private val rules = InventoryRules(defs.associateBy { it.id })
    private val recipe = CraftingRecipe("bracer", "bracer", mapOf("circlet" to 1, "gauntlets" to 1, "recipe_bracer" to 1))
    private val crafting = CraftingRules(rules, listOf(recipe))
    private fun input() = InventoryState(items = listOf(
        StoredItem("c", "circlet", ItemPlace(Container.WAREHOUSE)),
        StoredItem("g", "gauntlets", ItemPlace(Container.WAREHOUSE), bound = true),
        StoredItem("r", "recipe_bracer", ItemPlace(Container.WAREHOUSE))))

    @Test fun consumesComponentsAndScrollAndInheritsBindingWithoutChargingGold() {
        val before = input()
        val result = crafting.craft(before, "bracer", listOf("c", "g", "r"), "out")
        assertTrue(result.ok)
        assertEquals(before.gold, result.state.gold)
        assertEquals(StoredItem("out", "bracer", ItemPlace(Container.WAREHOUSE), bound = true), result.state.items.single())
        assertFalse(crafting.craft(result.state, "bracer", listOf("c", "g", "r"), "out2").ok)
    }
    @Test fun failedCraftPreservesAllItemsAndOnlyWarehouseIsAccessible() {
        val before = input()
        for (ids in listOf(listOf("c", "g"), listOf("c", "g", "g"), listOf("c", "g", "missing"))) {
            assertEquals(before, crafting.craft(before, "bracer", ids, "out").state)
            assertFalse(crafting.craft(before, "bracer", ids, "out").ok)
        }
        assertFalse(crafting.craft(before.copy(phase = InventoryPhase.PREPARATION), "bracer", listOf("c", "g", "r"), "out").ok)
        assertFalse(crafting.craft(before, "bracer", listOf("c", "g", "r"), "c").ok)
        val withSafe = rules.move(before, "r", ItemPlace(Container.SAFE)).state
        assertFalse(crafting.craft(withSafe, "bracer", listOf("c", "g", "r"), "out").ok)
    }
}
