package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

/** 51号外部执行；本机仅编译。 */
class SlotReplacementTest {
    private val rules = InventoryRules(listOf(
        ItemDefinition("small", "小装备", ItemKind.EQUIPMENT, 1, 1),
        ItemDefinition("wide", "长装备", ItemKind.EQUIPMENT, 2, 1),
        ItemDefinition("big", "大装备", ItemKind.EQUIPMENT, 2, 2),
        ItemDefinition("A1", "技能一", ItemKind.CARD, 1, 1),
        ItemDefinition("A2", "技能二", ItemKind.CARD, 1, 1)
    ).associateBy { it.id })
    private fun item(id: String, def: String, c: Container, x: Int = 0, y: Int = 0, star: Int = 1, bound: Boolean = false) = StoredItem(id, def, ItemPlace(c, x, y), star, bound)
    @Test fun s01WarehouseEquipmentReplacesAndOldReturnsToWarehouse() {
        val source = InventoryState(items = listOf(item("incoming", "small", Container.WAREHOUSE, bound = true), item("old", "big", Container.EQUIPMENT)))
        val result = rules.move(source, "incoming", ItemPlace(Container.EQUIPMENT))
        assertTrue(result.error, result.ok)
        assertEquals(Container.EQUIPMENT, result.state.items.first { it.instanceId == "incoming" }.place.container)
        assertEquals(Container.WAREHOUSE, result.state.items.first { it.instanceId == "old" }.place.container)
        assertTrue(result.state.items.first { it.instanceId == "incoming" }.bound)
        assertEquals(source.gold, result.state.gold)
    }
    @Test fun s02CardsKeepStarsBindingsAndInstances() {
        val a = item("a", "A1", Container.WAREHOUSE, star = 3, bound = true)
        val b = item("b", "A2", Container.SKILLS, star = 2)
        val result = rules.move(InventoryState(items = listOf(a, b)), "a", ItemPlace(Container.SKILLS))
        assertTrue(result.error, result.ok)
        assertEquals(a.copy(place = ItemPlace(Container.SKILLS)), result.state.items[0])
        assertEquals(b.copy(place = ItemPlace(Container.WAREHOUSE)), result.state.items[1])
    }
    @Test fun s03OccupiedEquipmentSlotsExchangeExactly() {
        val s = InventoryState(items = listOf(item("a", "small", Container.EQUIPMENT), item("b", "big", Container.EQUIPMENT, 7)))
        val result = rules.move(s, "a", ItemPlace(Container.EQUIPMENT, 7))
        assertTrue(result.error, result.ok)
        assertEquals(7, result.state.items[0].place.x); assertEquals(0, result.state.items[1].place.x)
    }
    @Test fun s04OccupiedSkillSlotsExchangeExactly() {
        val s = InventoryState(items = listOf(item("a", "A1", Container.SKILLS), item("b", "A2", Container.SKILLS, 3)))
        val result = rules.move(s, "a", ItemPlace(Container.SKILLS, 3))
        assertTrue(result.error, result.ok)
        assertEquals(3, result.state.items[0].place.x); assertEquals(0, result.state.items[1].place.x)
    }
    @Test fun s05OldItemReturnsToSourceGridWithRotation() {
        val s = InventoryState(items = listOf(item("a", "small", Container.BACKPACK, 3), item("b", "wide", Container.EQUIPMENT)))
        val result = rules.move(s, "a", ItemPlace(Container.EQUIPMENT))
        assertTrue(result.error, result.ok)
        assertEquals(ItemPlace(Container.BACKPACK, 3, 0, true), result.state.items[1].place)
    }
    @Test fun s06FallbackBackpackInsteadOfProtectedStorage() {
        val s = InventoryState(items = listOf(item("a", "small", Container.SAFE, 1, 1), item("b", "big", Container.EQUIPMENT)))
        val result = rules.move(s, "a", ItemPlace(Container.EQUIPMENT))
        assertTrue(result.error, result.ok)
        assertEquals(ItemPlace(Container.BACKPACK), result.state.items[1].place)
    }
    @Test fun s07NoRoomRollsBackBothItems() {
        val blocks = (0..6).map { item("block$it", "small", Container.BACKPACK, it % 4, it / 4) }
        val s = InventoryState(items = blocks + item("a", "small", Container.BACKPACK, 3, 1) + item("b", "big", Container.EQUIPMENT))
        val result = rules.move(s, "a", ItemPlace(Container.EQUIPMENT))
        assertFalse(result.ok); assertEquals(s, result.state)
    }
    @Test fun s08WrongTypeLockedAndBattleStillReject() {
        val s = InventoryState(items = listOf(item("a", "A1", Container.WAREHOUSE), item("b", "big", Container.EQUIPMENT)))
        assertEquals(s, rules.move(s, "a", ItemPlace(Container.EQUIPMENT)).state)
        assertFalse(rules.move(s, "a", ItemPlace(Container.SKILLS, 4)).ok)
        val fight = s.copy(phase = InventoryPhase.BATTLE)
        assertEquals(fight, rules.move(fight, "a", ItemPlace(Container.SKILLS)).state)
    }
    private fun ground(def: String): InventoryState {
        val original = item("run:battle:1:drop:0", def, Container.WAREHOUSE)
        val loot = NodeLoot("run:battle:1", NodeLootSource.BATTLE, listOf(original))
        val ready = loot.copy(inspectionMs = NodeLootRules.duration(loot, rules.definitions))
        return InventoryState(phase = InventoryPhase.PREPARATION, items = listOf(item("old", if (def == "A1") "A2" else "big", if (def == "A1") Container.SKILLS else Container.EQUIPMENT)),
            progress = CampaignProgress(run = JourneyState("run", "juggernaut", TargetChest("target", "白", 0, 60, listOf(ChestItem("A1", 0, 0))), 1729, 500.0, 1000.0,
                stage = JourneyStage.REWARD, nodeLoot = ready)))
    }
    @Test fun s09GroundEquipmentDisplacesOldToGroundAndCanReverse() {
        val s = ground("small"); val id = s.progress.run!!.nodeLoot!!.remaining.first().instanceId
        val result = NodeLootRules.collect(s, rules, id, ItemPlace(Container.EQUIPMENT))
        assertTrue(result.error, result.ok)
        assertEquals(id, result.state.items.single().instanceId)
        assertEquals("old", result.state.progress.run!!.nodeLoot!!.remaining.single().instanceId)
        val back = NodeLootRules.collect(result.state, rules, "old", ItemPlace(Container.EQUIPMENT))
        assertTrue(back.error, back.ok); assertEquals("old", back.state.items.single().instanceId)
        assertEquals(id, back.state.progress.run!!.nodeLoot!!.remaining.single().instanceId)
    }
    @Test fun s10GroundCardReplacesOccupiedSkill() {
        val s = ground("A1"); val id = s.progress.run!!.nodeLoot!!.remaining.first().instanceId
        val result = NodeLootRules.collect(s, rules, id, ItemPlace(Container.SKILLS))
        assertTrue(result.error, result.ok); assertEquals(id, result.state.items.single().instanceId)
        assertEquals("old", result.state.progress.run!!.nodeLoot!!.remaining.single().instanceId)
    }
    @Test fun s11GroundWrongTypeCannotPutDownOldAsPartialCommit() {
        val s = ground("A1").copy(items = listOf(item("old", "big", Container.EQUIPMENT)))
        val id = s.progress.run!!.nodeLoot!!.remaining.first().instanceId
        val result = NodeLootRules.collect(s, rules, id, ItemPlace(Container.EQUIPMENT))
        assertFalse(result.ok); assertEquals(s, result.state)
    }
    @Test fun s12FullPutDownPoolStillAllowsNativeDropSwapWithoutExtraCapacity() {
        val base = ground("small"); val r = base.progress.run!!; val l = r.nodeLoot!!
        val old = item("${l.id}:drop:1", "big", Container.WAREHOUSE)
        var loot = l.copy(items = l.items + old, picked = setOf(old.instanceId),
            putDown = (0..63).map { item("ground$it", "small", Container.WAREHOUSE) })
        loot = loot.copy(inspectionMs = NodeLootRules.duration(loot, rules.definitions))
        val s = base.copy(items = listOf(old.copy(place = ItemPlace(Container.EQUIPMENT))),
            progress = base.progress.copy(run = r.copy(nodeLoot = loot)))
        assertNull(rules.validate(s))
        val result = NodeLootRules.collect(s, rules, l.items.first().instanceId, ItemPlace(Container.EQUIPMENT))
        assertTrue(result.error, result.ok)
        assertEquals(64, result.state.progress.run!!.nodeLoot!!.putDown.size)
        assertTrue(result.state.progress.run!!.nodeLoot!!.remaining.any { it.instanceId == old.instanceId })
    }
    @Test fun s13IncomingPutDownItemFreesItsPlaceBeforeReplacingOld() {
        val base = ground("small"); val r = base.progress.run!!; val l = r.nodeLoot!!
        val incoming = item("returning", "small", Container.WAREHOUSE)
        val pool = listOf(incoming) + (0..62).map { item("ground$it", "small", Container.WAREHOUSE) }
        val s = base.copy(progress = base.progress.copy(run = r.copy(nodeLoot = l.copy(putDown = pool))))
        val result = NodeLootRules.collect(s, rules, incoming.instanceId, ItemPlace(Container.EQUIPMENT))
        assertTrue(result.error, result.ok)
        assertEquals(incoming.instanceId, result.state.items.single().instanceId)
        assertEquals(64, result.state.progress.run!!.nodeLoot!!.putDown.size)
        assertTrue(result.state.progress.run!!.nodeLoot!!.putDown.any { it.instanceId == "old" })
    }
}
