package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.CampaignProgress
import org.junit.Assert.*
import org.junit.Test

/**
 * 0.1.6 死亡保护槽验收（装备 3 格 / 技能 4 格，永久有效直到手动取消）。
 */
class GuardSlotTest {

    private val definitions = mapOf(
        "blade" to ItemDefinition("blade", "短刀", ItemKind.EQUIPMENT, 1, 1, price = 50),
        "cardA" to ItemDefinition("cardA", "技能卡", ItemKind.CARD, 1, 1, price = 100),
        "potion" to ItemDefinition("potion", "药水", ItemKind.CONSUMABLE, 1, 1, combatUsable = true)
    )
    private val inventory = InventoryRules(definitions)

    private fun equipment(id: String, x: Int = 0) = StoredItem(id, "blade", ItemPlace(Container.EQUIPMENT, x))
    private fun skill(id: String, x: Int = 0) = StoredItem(id, "cardA", ItemPlace(Container.SKILLS, x))

    private fun state(vararg items: StoredItem, guardLevel: Int = 0, skillGuard: Int = 0) =
        InventoryState(items = items.toList(), progress = CampaignProgress(
            equipmentGuardLevel = guardLevel, skillGuardLevel = skillGuard))

    @Test fun guardingAnEquippedItemIsAcceptedWithinLevel() {
        val s = state(equipment("e1"), guardLevel = 1)
        val r = inventory.setGuard(s, "e1", true)
        assertTrue(r.error ?: "ok", r.ok)
        assertTrue("e1" in r.state.progress.guardedEquipment)
    }

    @Test fun guardSlotFullIsRejected() {
        val base = InventoryState(items = listOf(equipment("e1", 0), equipment("e2", 1)),
            progress = CampaignProgress(equipmentGuardLevel = 1, guardedEquipment = setOf("e1")))
        val r = inventory.setGuard(base, "e2", true)
        assertFalse(r.ok)
        assertTrue(r.error!!, r.error!!.contains("已满"))
        assertEquals(base, r.state)
    }

    @Test fun equipmentGuardCapsAtThree() {
        var s = state(guardLevel = 3)
        (1..3).forEach { s = inventory.setGuard(s.copy(items = s.items + equipment("e$it", it - 1)), "e$it", true).state }
        assertEquals(3, s.progress.guardedEquipment.size)
        val extra = s.copy(items = s.items + equipment("e4"))
        assertFalse(inventory.setGuard(extra, "e4", true).ok)
    }

    @Test fun skillGuardCapsAtFour() {
        var s = InventoryState(progress = CampaignProgress(skillGuardLevel = 4))
        (1..4).forEach { s = inventory.setGuard(s.copy(items = s.items + skill("s$it", it - 1)), "s$it", true).state }
        assertEquals(4, s.progress.guardedSkills.size)
    }

    @Test fun nonSlotItemsCannotBeGuarded() {
        val s = InventoryState(items = listOf(StoredItem("p1", "potion", ItemPlace(Container.BACKPACK, 0, 0))))
        assertFalse(inventory.setGuard(s, "p1", true).ok)
    }

    @Test fun unguardingReleasesTheSlot() {
        var s = InventoryState(items = listOf(equipment("e1", 0)),
            progress = CampaignProgress(equipmentGuardLevel = 2, guardedEquipment = setOf("e1")))
        s = inventory.setGuard(s, "e1", false).state
        assertTrue(s.progress.guardedEquipment.isEmpty())
        // 空出的槽位可以保护新装备
        val next = s.copy(items = s.items + equipment("e2", 1))
        assertTrue(inventory.setGuard(next, "e2", true).ok)
    }

    @Test fun guardedItemsSurviveDeathAndMarkStaysPermanent() {
        val battle = InventoryState(
            items = listOf(equipment("e1", 0), equipment("e2", 1), skill("s1", 0), StoredItem("p1", "potion", ItemPlace(Container.BACKPACK, 0, 0))),
            phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(equipmentGuardLevel = 2, skillGuardLevel = 1,
                guardedEquipment = setOf("e1"), guardedSkills = setOf("s1"))
        )
        val r = inventory.settle(battle, survived = false)
        assertTrue(r.error ?: "ok", r.ok)
        val kept = r.state.items.map { it.instanceId }.toSet()
        assertTrue("e1" in kept)   // 受保护装备保留
        assertTrue("s1" in kept)   // 受保护技能保留
        assertTrue("e2" !in kept)  // 未保护装备掉落
        assertTrue("p1" !in kept)  // 背包物品掉落
        // 保护为永久标记：死亡后仍在，下次出发继续生效
        assertTrue("e1" in r.state.progress.guardedEquipment)
        assertTrue("s1" in r.state.progress.guardedSkills)
        assertEquals(InventoryPhase.HOME, r.state.phase)
    }

    @Test fun deathWithoutGuardsKeepsOnlyWarehouseAndSafe() {
        val battle = InventoryState(
            items = listOf(equipment("e1", 0), StoredItem("w1", "potion", ItemPlace(Container.WAREHOUSE)), StoredItem("s1", "potion", ItemPlace(Container.SAFE, 0, 0))),
            phase = InventoryPhase.PREPARATION
        )
        val r = inventory.settle(battle, survived = false)
        val kept = r.state.items.map { it.instanceId }.toSet()
        assertEquals(setOf("w1", "s1"), kept)
    }

    @Test fun surviveKeepsEverythingAsBefore() {
        val battle = InventoryState(
            items = listOf(equipment("e1", 0), StoredItem("p1", "potion", ItemPlace(Container.BACKPACK, 0, 0))),
            phase = InventoryPhase.PREPARATION, runGold = 100
        )
        val r = inventory.settle(battle, survived = true)
        assertEquals(2, r.state.items.size)
        assertEquals(1200 + 100, r.state.gold)
    }

    @Test fun guardedItemMayRestInWarehouseWithoutInvalidatingSave() {
        // 保护标记是死亡白名单，不约束位置：卸回仓库后存档合法、标记保留。
        val s = InventoryState(
            items = listOf(StoredItem("e1", "blade", ItemPlace(Container.WAREHOUSE))),
            progress = CampaignProgress(equipmentGuardLevel = 1, guardedEquipment = setOf("e1"))
        )
        assertNull(inventory.validate(s))
        assertTrue("e1" in s.progress.guardedEquipment)
    }

    @Test fun guardCountAboveLevelInvalidatesSave() {
        val s = InventoryState(
            items = listOf(equipment("e1", 0), equipment("e2", 1)),
            progress = CampaignProgress(equipmentGuardLevel = 1, guardedEquipment = setOf("e1", "e2"))
        )
        assertTrue(inventory.validate(s)!!.contains("超过档位"))
    }

    @Test fun guardLevelOutOfRangeInvalidatesSave() {
        val s = InventoryState(progress = CampaignProgress(equipmentGuardLevel = 4))
        assertTrue(inventory.validate(s)!!.contains("档位无效"))
        val s2 = InventoryState(progress = CampaignProgress(skillGuardLevel = 5))
        assertTrue(inventory.validate(s2)!!.contains("档位无效"))
    }

    @Test fun staleGuardedIdIsTolerated() {
        // 物品已不存在（被出售/合成消耗）的陈旧保护 ID 允许存在，不拦截存档。
        val s = InventoryState(progress = CampaignProgress(equipmentGuardLevel = 2, guardedEquipment = setOf("gone")))
        assertNull(inventory.validate(s))
    }
}
