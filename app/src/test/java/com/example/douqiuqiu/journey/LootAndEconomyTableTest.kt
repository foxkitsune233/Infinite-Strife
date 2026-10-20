package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*
import org.junit.Assert.*
import org.junit.Test

/**
 * X05：掉落与事件内容表的数值锁定，对应 docs/design/2026-10-20-loot-and-event-table.md。
 * 表改数必须同步改这里；这里红了说明文档与实现已经脱节。
 */
class LootAndEconomyTableTest {
    private val inventory = InventoryRules(listOf(
        ItemDefinition("blade", "刀", ItemKind.EQUIPMENT, 1, 1, price = 100),
        ItemDefinition("A1", "卡", ItemKind.CARD, 1, 1, price = 80),
        ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3)
    ).associateBy { it.id })
    private val journey = JourneyRules(inventory)
    private val box = TargetChest("box", "白", 0, 60, listOf(ChestItem("A1", 0, 0)))

    @Test fun `战斗金币表与文档一致`() {
        val expected = listOf(
            listOf(20, 30, 45, 60, 80),
            listOf(30, 45, 65, 90, 120),
            listOf(45, 65, 90, 125, 165))
        for (d in 0..2) for (r in 1..5) {
            assertEquals("难度$d 第$r 回合", expected[d][r - 1], EconomyBalance.goldReward(d, r))
        }
        // v1 旧值仍可按版本取到，保证旧档迁移不跳变
        assertEquals(35, EconomyBalance.goldReward(1, 1, 1))
        assertEquals(120, EconomyBalance.goldReward(1, 5, 1))
    }

    @Test fun `宝箱票价表与文档一致`() {
        // ticket = round10(基础价 × 难度倍率 ÷ 2)，基础价 白120/蓝220/紫420/金700，倍率 2/3/4
        assertEquals(120, EconomyBalance.ticket("白", 0)); assertEquals(180, EconomyBalance.ticket("白", 1)); assertEquals(240, EconomyBalance.ticket("白", 2))
        assertEquals(220, EconomyBalance.ticket("蓝", 0)); assertEquals(330, EconomyBalance.ticket("蓝", 1)); assertEquals(440, EconomyBalance.ticket("蓝", 2))
        assertEquals(420, EconomyBalance.ticket("紫", 0)); assertEquals(630, EconomyBalance.ticket("紫", 1)); assertEquals(840, EconomyBalance.ticket("紫", 2))
        assertEquals(700, EconomyBalance.ticket("金", 0)); assertEquals(1050, EconomyBalance.ticket("金", 1)); assertEquals(1400, EconomyBalance.ticket("金", 2))
    }

    @Test fun `战斗胜利当刻按表发放金币并生成地面掉落`() {
        val s = InventoryState(gold = 500, progress = CampaignProgress(chests = listOf(box)))
        val entered = journey.enter(s, box.id, "juggernaut").state
        val looted = journey.battleLoot(entered.copy(progress = entered.progress.copy(run = entered.progress.run!!.copy(stage = JourneyStage.REWARD))))
        assertEquals("低档第一战 20 金币", 20, looted.runGold)
        assertEquals(20, looted.progress.run!!.nodeLoot!!.goldAwarded)
        assertTrue("地面 1–3 件", looted.progress.run!!.nodeLoot!!.items.size in 1..3)
        assertTrue("掉落物不属玩家", looted.items.isEmpty())
        // 重复调用不重复发钱
        assertEquals(looted, journey.battleLoot(looted))
    }

    @Test fun `事件入场费按回合与难度线性增长且走本局金币`() {
        fun eventNode(round: Int, difficulty: Int): com.example.douqiuqiu.event.NodeEventInstance {
            val chest = box.copy(difficulty = difficulty)
            val run = JourneyState("run-box", "juggernaut", chest, 1729, 100.0, 100.0,
                round = round, stage = JourneyStage.EVENT)
            val state = InventoryState(progress = CampaignProgress(run = run))
            return JourneyEvents.generate(state, run, inventory.definitions)
        }
        assertEquals(30, eventNode(1, 0).priceGold)
        assertEquals(40, eventNode(2, 0).priceGold)
        assertEquals(50, eventNode(3, 0).priceGold)
        assertEquals(50, eventNode(1, 2).priceGold)
        assertEquals(80, eventNode(4, 2).priceGold)
    }

    @Test fun `第五战目标箱直接入库且不受背包容量限制`() {
        val s = InventoryState(gold = 500, progress = CampaignProgress(chests = listOf(box)))
        var state = journey.enter(s, box.id, "juggernaut").state
        state = state.copy(progress = state.progress.copy(run = state.progress.run!!.copy(round = 5, stage = JourneyStage.REWARD)))
        state = journey.battleLoot(state)
        // 先完成鉴定才允许离开节点
        while (state.progress.run!!.nodeLoot!!.let { it.inspectionMs < NodeLootRules.duration(it, inventory.definitions) }) {
            val stepped = NodeLootRules.inspect(state, inventory, 500)
            assertTrue(stepped.error ?: "ok", stepped.ok); state = stepped.state
        }
        // 背包只有 4×2 且塞满占用，也不该影响目标箱入库
        val packed = state.copy(items = (0 until 8).map { StoredItem("bp$it", "salve", ItemPlace(Container.BACKPACK, it % 4, it / 4)) })
        val finished = journey.finishLoot(packed, true)
        assertTrue(finished.error ?: "ok", finished.ok)
        assertTrue("目标箱物品直接入库", finished.state.items.any { it.instanceId == "run-box:chest:0" && it.definitionId == "A1" })
        assertTrue(finished.state.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals(1, finished.state.progress.clears)
        assertNull(finished.state.progress.run)
    }

    @Test fun `死亡仅带回仓库与安全箱物品且本局金币清零`() {
        val state = InventoryState(gold = 100, runGold = 250, phase = InventoryPhase.PREPARATION, items = listOf(
            StoredItem("bp", "blade", ItemPlace(Container.BACKPACK, 0, 0)),
            StoredItem("sf", "blade", ItemPlace(Container.SAFE)),
            StoredItem("wh", "blade", ItemPlace(Container.WAREHOUSE))
        ))
        val settled = inventory.settle(state, survived = false)
        assertTrue(settled.error ?: "ok", settled.ok)
        assertEquals(listOf("sf", "wh"), settled.state.items.map { it.instanceId }.sorted())
        assertEquals("死亡不发本局金币", 100, settled.state.gold)
        assertEquals(0, settled.state.runGold)
    }

    @Test fun `撤离带回全部随身物品并结算本局金币`() {
        val state = InventoryState(gold = 100, runGold = 250, phase = InventoryPhase.PREPARATION, items = listOf(
            StoredItem("bp", "blade", ItemPlace(Container.BACKPACK, 0, 0)),
            StoredItem("sf", "blade", ItemPlace(Container.SAFE))
        ))
        val settled = inventory.settle(state, survived = true)
        assertTrue(settled.error ?: "ok", settled.ok)
        assertEquals(2, settled.state.items.size)
        assertTrue(settled.state.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals(350, settled.state.gold)
        assertEquals(0, settled.state.runGold)
    }
}
