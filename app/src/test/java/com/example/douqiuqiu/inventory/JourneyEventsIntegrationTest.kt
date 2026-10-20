package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

class JourneyEventsIntegrationTest {
    private val inventory = InventoryRules(listOf(
        ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3),
        ItemDefinition("gauntlets", "力量手套", ItemKind.EQUIPMENT, 1, 1),
        ItemDefinition("A1", "卡", ItemKind.CARD, 1, 1),
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("skill_shard", "刻片", ItemKind.MATERIAL, 1, 1, minimumDropClears = 1),
        ItemDefinition("safe_lock", "锁芯", ItemKind.MATERIAL, 3, 2, boundOnAcquire = true)
    ).associateBy { it.id })

    private fun state(kind: EventKind, roll: Int = 0, hp: Double = 50.0): InventoryState {
        val chest = TargetChest("target", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0)))
        val node = NodeEventInstance("run:event:1", kind, roll, "essence", 30)
        return InventoryState(runGold = 50, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(clears = 1, run = JourneyState("run", "juggernaut", chest,
                1729L, hp, 100.0, stage = JourneyStage.EVENT, nodeEvent = node)))
    }

    @Test fun clinicUsesRunGoldAndCanOnlyResolveOnce() {
        val before = state(EventKind.FIELD_CLINIC)
        val result = JourneyEvents.resolve(before, inventory, "clinic.gold")
        assertTrue(result.error, result.ok)
        assertEquals(before.gold, result.state.gold)
        assertEquals(20, result.state.runGold)
        assertEquals(75.0, result.state.progress.run!!.hp, 0.0)
        assertEquals(JourneyStage.READY, result.state.progress.run!!.stage)
        assertNull(result.state.progress.run!!.nodeEvent)
        assertFalse(JourneyEvents.resolve(result.state, inventory, "clinic.gold").ok)
        val poor = before.copy(runGold = 0)
        assertEquals(poor, JourneyEvents.resolve(poor, inventory, "clinic.gold").state)
    }

    @Test fun supplyRequiresExplicitChoiceAndSafeAuthorizationAndConsumesOneUnit() {
        val before = state(EventKind.FIELD_CLINIC).copy(items = listOf(
            StoredItem("chosen", "salve", ItemPlace(Container.SAFE), count = 3)))
        assertEquals(before, JourneyEvents.resolve(before, inventory, "clinic.supply").state)
        assertEquals(before, JourneyEvents.resolve(before, inventory, "clinic.supply", EventItemSelection("chosen")).state)
        val result = JourneyEvents.resolve(before, inventory, "clinic.supply", EventItemSelection("chosen", true))
        assertTrue(result.error, result.ok)
        assertEquals(2, result.state.items.single().count)
        assertEquals(90.0, result.state.progress.run!!.hp, 0.0)
    }

    @Test fun warehouseItemsCannotBeHandedOver() {
        val before = state(EventKind.BLOCKER).copy(items = listOf(StoredItem("home", "salve", ItemPlace(Container.WAREHOUSE))))
        val result = JourneyEvents.resolve(before, inventory, "blocker.item", EventItemSelection("home", true))
        assertFalse(result.ok)
        assertEquals(before, result.state)
    }

    @Test fun fullBackpackKeepsEventRewardOnGroundAndChargesOnce() {
        val before = state(EventKind.RUSTED_WORKSHOP).copy(items = (0..7).map {
            StoredItem("filler$it", "A1", ItemPlace(Container.BACKPACK, it % 4, it / 4))
        })
        // 0.1.4 起奖励先落地：背包满不阻止结算，扣费只发生一次，物品零改动。
        val result = JourneyEvents.resolve(before, inventory, "workshop.pay")
        assertTrue(result.error, result.ok)
        assertEquals(before.items, result.state.items)
        val loot = result.state.progress.run!!.nodeLoot!!
        assertTrue(loot.items.any { it.definitionId == "gauntlets" })
        assertEquals(before.runGold - 30 + loot.goldAwarded, result.state.runGold)
        assertEquals(JourneyStage.REWARD, result.state.progress.run!!.stage)
        assertFalse(JourneyEvents.resolve(result.state, inventory, "workshop.pay").ok)
    }

    @Test fun lethalChoiceKeepsZeroHealthForCallerToSettleAndDisclosesRisk() {
        val before = state(EventKind.ABANDONED_DEPOT, roll = 99, hp = 5.0)
        assertTrue(JourneyEvents.views(before, inventory).first().showsLethalWarning)
        val result = JourneyEvents.resolve(before, inventory, "depot.search")
        assertTrue(result.error, result.ok)
        assertEquals(0.0, result.state.progress.run!!.hp, 0.0)
        assertTrue(result.state.progress.run!!.nodeEvent!!.resolved)
        assertEquals(InventoryPhase.PREPARATION, result.state.phase)
    }

    @Test fun generationIsDeterministicAndToleratesLimitedDefinitions() {
        val before = state(EventKind.BLOCKER)
        val run = before.progress.run!!
        assertEquals(JourneyEvents.generate(before, run, inventory.definitions),
            JourneyEvents.generate(before, run, inventory.definitions.toList().reversed().toMap()))
        repeat(50) { seed ->
            val limited = JourneyEvents.generate(before.copy(progress = before.progress.copy(clears = 0)),
                run.copy(rng = seed.toLong() + 1), mapOf("A1" to inventory.definitions.getValue("A1")))
            assertTrue(limited.kind in setOf(EventKind.RUINED_LIBRARY, EventKind.COLLAPSED_STORAGE,
                EventKind.FIELD_CLINIC, EventKind.BLOCKER))
        }
    }

    @Test fun terminalChecksDefinitionAndConsumesOnlyChosenMaterial() {
        val before = state(EventKind.TECH_TERMINAL).copy(items = listOf(
            StoredItem("material", "essence", ItemPlace(Container.BACKPACK)),
            StoredItem("other", "A1", ItemPlace(Container.BACKPACK, 1, 0))))
        assertEquals(before, JourneyEvents.resolve(before, inventory, "terminal.convert", EventItemSelection("other")).state)
        val result = JourneyEvents.resolve(before, inventory, "terminal.convert", EventItemSelection("material"))
        assertTrue(result.error, result.ok)
        // 交付的材料被消耗，奖励刻片先落地待拾取。
        assertEquals(setOf("A1"), result.state.items.map { it.definitionId }.toSet())
        assertTrue(result.state.progress.run!!.nodeLoot!!.items.any { it.definitionId == "skill_shard" })
    }

    @Test fun allEightTemplatesHaveWorkingChoicesAndCorePreservesBinding() {
        val choices = mapOf(EventKind.ABANDONED_DEPOT to "depot.search", EventKind.RUSTED_WORKSHOP to "workshop.pry",
            EventKind.RUINED_LIBRARY to "library.delve", EventKind.COLLAPSED_STORAGE to "storage.left",
            EventKind.SAFE_WRECKAGE to "wreckage.core", EventKind.TECH_TERMINAL to "terminal.gamble",
            EventKind.FIELD_CLINIC to "clinic.gold", EventKind.BLOCKER to "blocker.gold")
        for ((kind, option) in choices) {
            val result = JourneyEvents.resolve(state(kind), inventory, option)
            assertTrue("$kind: ${result.error}", result.ok)
            val run = result.state.progress.run!!
            if (run.nodeLoot != null) {
                // 有奖励的选项进入掉落整理，回合在离开节点时才推进。
                assertEquals("$kind", JourneyStage.REWARD, run.stage)
                assertEquals("$kind", 1, run.round)
            } else {
                assertEquals("$kind", 2, run.round)
            }
            if (kind == EventKind.SAFE_WRECKAGE) {
                assertTrue(run.nodeLoot!!.items.any { it.definitionId == "safe_lock" && it.bound })
            }
        }
    }

    @Test fun lockedMaterialAndMissingSynergyAreRejectedWithoutCost() {
        val wreckage = state(EventKind.SAFE_WRECKAGE)
        val locked = wreckage.copy(progress = wreckage.progress.copy(clears = 0))
        assertEquals(locked, JourneyEvents.resolve(locked, inventory, "wreckage.core").state)
        val library = state(EventKind.RUINED_LIBRARY)
        assertEquals(library, JourneyEvents.resolve(library, inventory, "library.study").state)
    }
}
