package com.example.douqiuqiu.inventory

import org.junit.Assert.*
import org.junit.Test

class CraftingPresentationTest {
    private val definitions = listOf(
        ItemDefinition("a", "甲", ItemKind.EQUIPMENT, 1, 1, quality = "白"),
        ItemDefinition("b", "乙", ItemKind.EQUIPMENT, 1, 1, quality = "金"),
        ItemDefinition("card", "卡", ItemKind.CARD, 1, 1),
        ItemDefinition("scroll", "卷轴", ItemKind.SCROLL, 1, 1)
    ).associateBy { it.id }

    @Test fun readinessCountsOnlyWarehouseAndReordersAfterCraft() {
        val a = CraftingRecipe("a", "a", mapOf("scroll" to 1))
        val b = CraftingRecipe("b", "b", mapOf("a" to 1))
        val state = InventoryState(items = listOf(
            StoredItem("scroll", "scroll", ItemPlace(Container.SAFE)),
            StoredItem("a", "a", ItemPlace(Container.WAREHOUSE))
        ))
        val options = CraftingPresentation.options(state, listOf(a, b), definitions)
        assertEquals(listOf("b", "a"), options.map { it.recipe.id })
        assertEquals(listOf(0, 1), options.map { it.missing })
        assertEquals(listOf("a"), options.first().ingredientIds)
        assertEquals(1, CraftingPresentation.options(state.copy(items = emptyList()), listOf(b), definitions).single().missing)
    }

    @Test fun materialChoiceRetainsUnboundFirstPolicyAndCountsMultiplicity() {
        val recipe = CraftingRecipe("b", "b", mapOf("a" to 2))
        val items = listOf(
            StoredItem("bound", "a", ItemPlace(Container.WAREHOUSE), bound = true),
            StoredItem("free", "a", ItemPlace(Container.WAREHOUSE))
        )
        assertEquals(listOf("free", "bound"), CraftingPresentation.options(InventoryState(items = items), listOf(recipe), definitions).single().ingredientIds)
    }

    @Test fun warehouseSortPreservesInstancesAndOtherPlacesAndIsIdempotent() {
        val equipped = StoredItem("eq", "a", ItemPlace(Container.EQUIPMENT))
        val items = listOf(StoredItem("card", "card", ItemPlace(Container.WAREHOUSE)), equipped,
            StoredItem("white", "a", ItemPlace(Container.WAREHOUSE)), StoredItem("gold", "b", ItemPlace(Container.WAREHOUSE)))
        val state = InventoryState(items = items)
        val result = WarehouseOrder.organize(state, definitions)
        assertTrue(result.ok)
        assertEquals(items.toSet(), result.state.items.toSet())
        assertEquals(equipped, result.state.items[1])
        assertEquals(listOf("gold", "white", "card"), result.state.items.filter { it.place.container == Container.WAREHOUSE }.map { it.instanceId })
        assertEquals(result.state, WarehouseOrder.organize(result.state, definitions).state)
        assertFalse(WarehouseOrder.organize(state.copy(phase = InventoryPhase.PREPARATION), definitions).ok)
    }
}
