package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.CampaignProgress
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class LoadoutAndPackingTest {
    @get:Rule val temporary = TemporaryFolder()
    private val rules = InventoryRules(listOf(
        ItemDefinition("sword", "长剑", ItemKind.EQUIPMENT, 2, 1),
        ItemDefinition("shield", "圆盾", ItemKind.EQUIPMENT, 1, 1),
        ItemDefinition("A1", "迅击训练", ItemKind.CARD, 1, 1),
        ItemDefinition("A2", "精准打击", ItemKind.CARD, 1, 1),
        ItemDefinition("fairy", "仙灵之火", ItemKind.CONSUMABLE, 1, 1, combatUsable = true),
        ItemDefinition("wide", "长材料", ItemKind.MATERIAL, 2, 1),
        ItemDefinition("tall", "高材料", ItemKind.MATERIAL, 1, 2)
    ).associateBy { it.id })
    private val loadouts = LoadoutRules(rules)
    private fun item(id: String, definition: String, place: ItemPlace = ItemPlace(Container.WAREHOUSE)) =
        StoredItem(id, definition, place)

    @Test fun saveThenApplyReturnsOldSlotsToWarehouseAndRestoresManualChoices() {
        val saved = InventoryState(items = listOf(
            item("sword", "sword", ItemPlace(Container.EQUIPMENT, 0)),
            item("a1", "A1", ItemPlace(Container.SKILLS, 0)),
            item("shield", "shield"), item("a2", "A2")
        ), progress = CampaignProgress(manualItems = setOf("sword")))
        val stored = loadouts.save(saved, "juggernaut", 0)
        assertTrue(stored.ok)
        val changed = stored.state.copy(items = listOf(
            item("sword", "sword"), item("a1", "A1"),
            item("shield", "shield", ItemPlace(Container.EQUIPMENT, 0)),
            item("a2", "A2", ItemPlace(Container.SKILLS, 0))
        ), progress = stored.state.progress.copy(manualItems = setOf("shield")))

        val applied = loadouts.apply(changed, "juggernaut", 0)

        assertTrue(applied.error, applied.ok)
        assertEquals("sword", applied.state.items.single { it.place == ItemPlace(Container.EQUIPMENT, 0) }.instanceId)
        assertEquals("a1", applied.state.items.single { it.place == ItemPlace(Container.SKILLS, 0) }.instanceId)
        assertEquals(setOf("sword"), applied.state.progress.manualItems)
        assertEquals("juggernaut", applied.state.progress.activeHeroId)
        assertEquals(Container.WAREHOUSE, applied.state.items.single { it.instanceId == "shield" }.place.container)
    }

    @Test fun whollyMissingPresetIsCleanedWithoutMovingCurrentEquipment() {
        val preset = LoadoutPreset(equipment = listOf("gone") + List(8) { null }, skills = List(8) { null })
        val state = InventoryState(items = listOf(item("held", "shield", ItemPlace(Container.EQUIPMENT, 0))),
            progress = CampaignProgress(loadouts = mapOf("juggernaut" to listOf(preset))))

        val result = loadouts.apply(state, "juggernaut", 0)

        assertTrue(result.error, result.ok)
        assertEquals(state.items, result.state.items)
        assertTrue(result.notice.orEmpty().contains("1"))
        assertTrue(result.state.progress.loadouts.getValue("juggernaut")[0].equipment.all { it == null })
        assertNull(rules.validate(result.state))
    }

    @Test fun saveAndApplyRetainsCombatConsumableInEquipmentSlot() {
        val source = InventoryState(items = listOf(item("fairy", "fairy", ItemPlace(Container.EQUIPMENT, 2))))
        val saved = loadouts.save(source, "juggernaut", 1)
        assertTrue(saved.error, saved.ok)
        val removed = saved.state.copy(items = listOf(item("fairy", "fairy")))

        val applied = loadouts.apply(removed, "juggernaut", 1)

        assertTrue(applied.error, applied.ok)
        assertEquals(ItemPlace(Container.EQUIPMENT, 2), applied.state.items.single().place)
    }

    @Test fun organizeBackpackUsesStableFirstFitAndKeepsAllItems() {
        val source = InventoryState(backpackWidth = 4, backpackHeight = 2, items = listOf(
            item("b", "tall", ItemPlace(Container.BACKPACK, 3, 0)),
            item("a", "wide", ItemPlace(Container.BACKPACK, 0, 1)),
            item("c", "shield", ItemPlace(Container.BACKPACK, 2, 0))
        ))

        val result = rules.organize(source, Container.BACKPACK)

        assertTrue(result.error, result.ok)
        // 现行策略：按面积从大到小、同面积按 instanceId 稳定摆放；只验证不丢件、合法且可重复执行。
        assertEquals(setOf("a", "b", "c"), result.state.items.map { it.instanceId }.toSet())
        assertEquals(result.state, rules.organize(result.state, Container.BACKPACK).state)
        assertNull(rules.validate(result.state))
    }

    @Test fun organizeRejectsBattleAndLeavesStateUntouched() {
        val state = InventoryState(phase = InventoryPhase.BATTLE, items = listOf(item("a", "shield", ItemPlace(Container.BACKPACK))))
        val result = rules.organize(state, Container.BACKPACK)
        assertFalse(result.ok)
        assertEquals(state, result.state)
    }

    @Test fun storeRoundTripRetainsActiveHeroAndAllPresetReferences() {
        val preset = LoadoutPreset(listOf("sword") + List(8) { null }, listOf("a1") + List(7) { null }, setOf("sword"))
        val source = InventoryState(items = listOf(item("sword", "sword", ItemPlace(Container.EQUIPMENT)), item("a1", "A1", ItemPlace(Container.SKILLS))),
            progress = CampaignProgress(activeHeroId = "juggernaut", loadouts = mapOf("juggernaut" to listOf(preset)), loadoutSelections = mapOf("juggernaut" to 0)))
        val store = InventoryStore(temporary.root.resolve("profile.dat"), rules) { InventoryState() }

        store.save(source)

        assertEquals(source, store.load().state)
    }
}
