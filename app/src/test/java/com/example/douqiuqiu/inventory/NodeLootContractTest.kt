package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.event.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream

/** 50号：仅编译，交外部执行。No Android or wall clock needed. */
class NodeLootContractTest {
    @get:Rule val temp = TemporaryFolder()
    private val inventory = InventoryRules(listOf(
        ItemDefinition("blade", "刀", ItemKind.EQUIPMENT, 1, 1, price = 100),
        ItemDefinition("A1", "卡", ItemKind.CARD, 1, 1, price = 80),
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1, boundOnAcquire = true),
        ItemDefinition("gauntlets", "手套", ItemKind.EQUIPMENT, 1, 1),
        ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3)
    ).associateBy { it.id })
    private val journey = JourneyRules(inventory)
    private val box = TargetChest("box", "白", 0, 60, listOf(ChestItem("A1", 0, 0)))
    private fun entered(carry: Int = 0): InventoryState {
        val s = InventoryState(gold = 500, progress = CampaignProgress(chests = listOf(box)))
        val result = journey.enter(s, box.id, "juggernaut", carry)
        assertTrue(result.error, result.ok)
        return result.state
    }
    private fun node(round: Int = 1): InventoryState {
        val s = entered(100)
        return journey.battleLoot(s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REWARD, round = round))))
    }
    private fun inspect(s0: InventoryState): InventoryState {
        var s = s0
        while (s.progress.run!!.nodeLoot!!.let { it.inspectionMs < NodeLootRules.duration(it, inventory.definitions) }) {
            val result = NodeLootRules.inspect(s, inventory, 500)
            assertTrue(result.error, result.ok); s = result.state
        }
        return s
    }
    @Test fun g01CarryDebitsWalletAndCannotBorrowTicketMoney() {
        val s = entered(100)
        assertEquals(340, s.gold); assertEquals(100, s.runGold)
        val home = InventoryState(gold = 500, progress = CampaignProgress(chests = listOf(box)))
        assertEquals(home, journey.enter(home, box.id, "juggernaut", 441).state)
        assertFalse(journey.enter(home, box.id, "juggernaut", -1).ok)
    }
    @Test fun g02GroundIsNotOwnedAndBattleMoneyIsOnlyGrantedOnce() {
        val s = node()
        assertTrue(s.items.isEmpty()); assertTrue(s.runGold > 100)
        assertTrue(s.progress.run!!.nodeLoot!!.items.size in 1..3)
        assertEquals(s, journey.battleLoot(s))
        assertFalse(journey.reward(s, RewardChoice.HEAL).ok)
    }
    @Test fun g03UnidentifiedCannotPickUpOrSkipInspection() {
        val s = node(); val id = s.progress.run!!.nodeLoot!!.remaining.first().instanceId
        assertEquals(s, NodeLootRules.collect(s, inventory, id, ItemPlace(Container.BACKPACK)).state)
        assertFalse(NodeLootRules.inspect(s, inventory, 501).ok)
        assertEquals(s, journey.finishLoot(s, true).state)
    }
    @Test fun g04PickIsAtomicAndCannotDuplicateOrBankDirectly() {
        val s = inspect(node()); val item = s.progress.run!!.nodeLoot!!.remaining.first()
        assertFalse(NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.WAREHOUSE)).ok)
        assertEquals(s, NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.BACKPACK, 99)).state)
        val picked = NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.BACKPACK)).state
        assertEquals(1, picked.items.size)
        assertFalse(NodeLootRules.collect(picked, inventory, item.instanceId, ItemPlace(Container.SAFE)).ok)
        assertNull(inventory.validate(picked))
    }
    @Test fun g05FullBagDoesNotPreventEventLootAndEventCannotRepeat() {
        val e = entered(100); val run = e.progress.run!!
        val event = NodeEventInstance("${run.id}:event:1", EventKind.RUSTED_WORKSHOP, 10, priceGold = 30)
        val s = e.copy(items = (0..7).map { StoredItem("full$it", "blade", ItemPlace(Container.BACKPACK, it % 4, it / 4)) },
            progress = e.progress.copy(run = run.copy(stage = JourneyStage.EVENT, nodeEvent = event)))
        val result = journey.event(s, "workshop.pay")
        assertTrue(result.error, result.ok)
        assertEquals(s.items, result.state.items)
        assertTrue(result.state.progress.run!!.nodeLoot!!.items.size in 2..6)
        assertEquals(NodeLootSource.EVENT, result.state.progress.run!!.nodeLoot!!.source)
        assertFalse(journey.event(result.state, "workshop.pay").ok)
    }
    @Test fun g06LeavingNeedsExplicitDiscardAndAdvancesOnlyOnce() {
        val s = inspect(node())
        assertFalse(journey.finishLoot(s).ok)
        val done = journey.finishLoot(s, true)
        assertTrue(done.error, done.ok); assertNull(done.state.progress.run!!.nodeLoot)
        assertFalse(journey.finishLoot(done.state, true).ok)
        assertEquals(s.runGold, done.state.runGold)
    }
    @Test fun g07PutPickedItemBackAndRecollectWithoutDuplication() {
        val s = inspect(node()); val item = s.progress.run!!.nodeLoot!!.remaining.first()
        val picked = NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.BACKPACK)).state
        val back = NodeLootRules.putDown(picked, inventory, item.instanceId)
        assertTrue(back.error, back.ok); assertTrue(back.state.items.isEmpty())
        assertTrue(back.state.progress.run!!.nodeLoot!!.remaining.any { it.instanceId == item.instanceId })
        val again = NodeLootRules.collect(back.state, inventory, item.instanceId, ItemPlace(Container.SAFE))
        assertTrue(again.error, again.ok); assertEquals(1, again.state.items.size)
    }
    @Test fun g08BroughtBoundItemMayBePutDownAndKeepsIdentity() {
        val s = inspect(node()).copy(items = listOf(StoredItem("brought", "blade", ItemPlace(Container.EQUIPMENT), bound = true)))
        val down = NodeLootRules.putDown(s, inventory, "brought")
        assertTrue(down.error, down.ok); assertTrue(down.state.items.isEmpty())
        val back = NodeLootRules.collect(down.state, inventory, "brought", ItemPlace(Container.BACKPACK))
        assertTrue(back.error, back.ok); assertTrue(back.state.items.single().bound)
        assertEquals("brought", back.state.items.single().instanceId)
    }
    @Test fun g09FifthBattleBanksOnlyPickedLootAndFullTargetChest() {
        val s = inspect(node(5)); val item = s.progress.run!!.nodeLoot!!.remaining.first()
        val picked = NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.BACKPACK)).state
        val done = journey.finishLoot(picked, true)
        assertTrue(done.error, done.ok); assertNull(done.state.progress.run)
        assertEquals(1, done.state.progress.clears)
        assertEquals(1 + box.items.size, done.state.items.size)
        assertTrue(done.state.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals(picked.gold + picked.runGold, done.state.gold)
    }
    @Test fun g10DeathLosesCarriedAndEarnedMoneyAndUnpickedItems() {
        val s = inspect(node()); val item = s.progress.run!!.nodeLoot!!.remaining.first()
        val safe = NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.SAFE)).state
        val dead = journey.abandon(safe)
        assertTrue(dead.error, dead.ok); assertEquals(s.gold, dead.state.gold); assertEquals(0, dead.state.runGold)
        assertEquals(listOf(item.instanceId), dead.state.items.map { it.instanceId })
    }
    @Test fun g11V11RetainsPartialInspectionPickupAndPutDown() {
        var s = inspect(node()).copy(items = listOf(StoredItem("brought", "blade", ItemPlace(Container.EQUIPMENT), bound = true)))
        s = NodeLootRules.putDown(s, inventory, "brought").state
        val item = s.progress.run!!.nodeLoot!!.items.first()
        s = NodeLootRules.collect(s, inventory, item.instanceId, ItemPlace(Container.BACKPACK)).state
        val file = temp.root.resolve("node.dat"); val storage = InventoryStore(file, inventory) { InventoryState() }
        storage.save(s); val restored = storage.load().state
        assertEquals(s, restored)
        assertEquals(restored, journey.battleLoot(restored))
        DataInputStream(file.inputStream()).use { it.readInt(); assertEquals(InventoryStore.VERSION, it.readInt()) }
    }
    @Test fun g12MalformedDropCannotReplaceValidSave() {
        val s = node(); val loot = s.progress.run!!.nodeLoot!!
        val bad = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(nodeLoot = loot.copy(items = loot.items + loot.items.first()))))
        assertNotNull(inventory.validate(bad))
        val file = temp.root.resolve("valid.dat"); val storage = InventoryStore(file, inventory) { InventoryState() }
        storage.save(s)
        assertThrows(IllegalArgumentException::class.java) { storage.save(bad) }
        assertEquals(s, storage.load().state)
    }
    @Test fun g13PuttingBackPartlyUsedStackCannotRestoreConsumedQuantity() {
        val base = node(); val run = base.progress.run!!; val loot = run.nodeLoot!!
        val first = loot.items.first().copy(definitionId = "salve", bound = false, count = 2)
        val s = inspect(base.copy(progress = base.progress.copy(run = run.copy(hp = run.maxHp / 2,
            nodeLoot = loot.copy(items = listOf(first) + loot.items.drop(1))))))
        val picked = NodeLootRules.collect(s, inventory, first.instanceId, ItemPlace(Container.BACKPACK)).state
        val used = journey.useConsumable(picked, first.instanceId)
        assertTrue(used.error, used.ok); assertEquals(1, used.state.items.single().count)
        val down = NodeLootRules.putDown(used.state, inventory, first.instanceId)
        assertTrue(down.error, down.ok)
        val back = NodeLootRules.collect(down.state, inventory, first.instanceId, ItemPlace(Container.BACKPACK))
        assertTrue(back.error, back.ok); assertEquals(1, back.state.items.single().count)
    }
}
