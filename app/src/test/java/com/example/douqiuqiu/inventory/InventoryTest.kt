package com.example.douqiuqiu.inventory

import org.junit.Assert.*
import org.junit.Test

class InventoryTest {
    private val defs = listOf(
        ItemDefinition("card", "测试卡", ItemKind.CARD, 1, 1),
        ItemDefinition("sword", "武器", ItemKind.EQUIPMENT, 3, 1),
        ItemDefinition("large", "锁芯", ItemKind.MATERIAL, 3, 2)
    ).associateBy { it.id }
    private val rules = InventoryRules(defs)
    private fun state(vararg entries: StoredItem) = InventoryState(items = entries.toList())
    private fun item(id: String, def: String = "card", place: ItemPlace = ItemPlace(Container.WAREHOUSE),
                     star: Int = 1, bound: Boolean = false) = StoredItem(id, def, place, star, bound)

    @Test fun rotationAllowsTallItemButOverlapFailsWithoutMutation() {
        val original = state(item("a", "sword"), item("b", "card"))
        val moved = rules.move(original, "a", ItemPlace(Container.BACKPACK, 0, 0, true))
        // Rotated 1×3 cannot fit the initial 4×2 backpack.
        assertFalse(moved.ok)
        assertEquals(original, moved.state)
        val horizontal = rules.move(original, "a", ItemPlace(Container.BACKPACK, 0, 0))
        assertTrue(horizontal.ok)
        val overlap = rules.move(horizontal.state, "b", ItemPlace(Container.BACKPACK, 2, 0))
        assertFalse(overlap.ok)
        assertEquals(horizontal.state, overlap.state)
        val rotated = rules.move(original.copy(backpackHeight = 3), "a", ItemPlace(Container.BACKPACK, 3, 0, true))
        assertTrue(rotated.ok)
    }

    @Test fun safeDoesNotAcceptMaterialLargerThanItsDimensions() {
        assertFalse(rules.move(state(item("a", "large")), "a", ItemPlace(Container.SAFE)).ok)
    }

    @Test fun equipmentAndSkillSlotsEnforceTypeCapacityAndCombatLock() {
        val original = state(item("a"), item("b", "sword"))
        assertFalse(rules.move(original, "a", ItemPlace(Container.EQUIPMENT)).ok)
        assertFalse(rules.move(original, "b", ItemPlace(Container.SKILLS)).ok)
        assertFalse(rules.move(original, "a", ItemPlace(Container.SKILLS, 4)).ok)
        assertTrue(rules.move(original, "b", ItemPlace(Container.EQUIPMENT, 8)).ok)
        assertFalse(rules.move(original.copy(phase = InventoryPhase.BATTLE), "a", ItemPlace(Container.SAFE)).ok)
        assertFalse(rules.move(original.copy(phase = InventoryPhase.PREPARATION), "a", ItemPlace(Container.BACKPACK)).ok)
    }

    @Test fun mergeConsumesExactlyThreeAndCarriesBindingAtNoGoldCost() {
        val original = state(item("a"), item("b", bound = true), item("c"), item("d"))
        val result = rules.mergeCards(original, listOf("a", "b", "c"))
        assertTrue(result.ok)
        assertEquals(2, result.state.items.size)
        assertEquals(2, result.state.items.first { it.instanceId == "a" }.star)
        assertTrue(result.state.items.first { it.instanceId == "a" }.bound)
        assertEquals(original.gold, result.state.gold)
        assertFalse(rules.mergeCards(original, listOf("a", "a", "b")).ok)
        assertFalse(rules.mergeCards(original.copy(phase = InventoryPhase.PREPARATION), listOf("a", "b", "c")).ok)
    }

    @Test fun mergeRejectsDifferentStarsAndMaxStar() {
        val mixed = state(item("a"), item("b", star = 2), item("c"))
        assertFalse(rules.mergeCards(mixed, listOf("a", "b", "c")).ok)
        val max = state(item("a", star = 3), item("b", star = 3), item("c", star = 3))
        assertFalse(rules.mergeCards(max, listOf("a", "b", "c")).ok)
    }

    @Test fun deathKeepsWarehouseAndSafeEvenWhenOtherItemsAreBound() {
        val original = state(item("home"), item("safe", place = ItemPlace(Container.SAFE)),
            item("lost", place = ItemPlace(Container.BACKPACK), bound = true)).copy(
            phase = InventoryPhase.PREPARATION, runGold = 250)
        val result = rules.settle(original, survived = false)
        assertTrue(result.ok)
        assertEquals(setOf("home", "safe"), result.state.items.map { it.instanceId }.toSet())
        assertTrue(result.state.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals(1200, result.state.gold)
        assertEquals(0, result.state.runGold)
        assertFalse(rules.settle(result.state, survived = true).ok)
    }

    @Test fun extractionBanksGoldAndItemsExactlyOnce() {
        val original = state(item("a", place = ItemPlace(Container.BACKPACK))).copy(
            phase = InventoryPhase.PREPARATION, runGold = 250)
        val result = rules.settle(original, survived = true)
        assertTrue(result.ok)
        assertEquals(1450, result.state.gold)
        assertEquals(Container.WAREHOUSE, result.state.items.single().place.container)
        assertFalse(rules.settle(result.state, survived = true).ok)
    }

    @Test fun malformedOwnershipAndNegativeCoordinatesAreRejected() {
        assertFalse(rules.move(state(item("same"), item("same")), "same", ItemPlace(Container.SAFE)).ok)
        assertFalse(rules.move(state(item("a")), "a", ItemPlace(Container.BACKPACK, -1)).ok)
        assertFalse(rules.move(state(item("a")), "missing", ItemPlace(Container.SAFE)).ok)
    }
}
