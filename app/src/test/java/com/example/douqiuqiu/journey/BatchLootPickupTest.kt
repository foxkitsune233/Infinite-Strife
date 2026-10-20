package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*
import org.junit.Assert.*
import org.junit.Test

/**
 * X01：搜刮一键放入的原子规则验收，对应 docs/tasks/0.1.4-external-handoff.md。
 * 只验证纯规则层；存档由调用方负责，因此这里不依赖 Android 或墙上时钟。
 */
class BatchLootPickupTest {

    private val inventory = InventoryRules(listOf(
        ItemDefinition("blade", "刀", ItemKind.EQUIPMENT, 1, 1, price = 100),
        ItemDefinition("staff", "杖", ItemKind.EQUIPMENT, 1, 2, price = 120),
        ItemDefinition("gauntlets", "手套", ItemKind.EQUIPMENT, 2, 1, price = 90),
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1, boundOnAcquire = true),
        ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3)
    ).associateBy { it.id })
    private val journey = JourneyRules(inventory)
    private val box = TargetChest("box", "白", 0, 60, listOf(ChestItem("blade", 0, 0)))

    private fun entered(carry: Int = 0): InventoryState {
        val s = InventoryState(gold = 500, progress = CampaignProgress(chests = listOf(box)))
        val result = journey.enter(s, box.id, "juggernaut", carry)
        assertTrue(result.error, result.ok)
        return result.state
    }

    /** 生成掉落。inspectionRatio 决定鉴定进度：1.0 全部完成，0.0 尚未开始。 */
    private fun withGround(s: InventoryState, definitionIds: List<String>, inspectionRatio: Double = 1.0): InventoryState {
        val run = s.progress.run!!
        val previous = run.nodeLoot!!
        val items = definitionIds.mapIndexed { index, definitionId ->
            StoredItem("${previous.id}:drop:$index", definitionId, ItemPlace(Container.WAREHOUSE),
                bound = inventory.definitions.getValue(definitionId).boundOnAcquire)
        }
        val base = previous.copy(items = items, picked = emptySet(), putDown = emptyList(), inspectionMs = 0L)
        val total = NodeLootRules.duration(base, inventory.definitions)
        val loot = base.copy(inspectionMs = (total * inspectionRatio).toLong().coerceIn(0, total))
        return s.copy(progress = s.progress.copy(run = run.copy(nodeLoot = loot)))
    }

    /** 带掉落的 REWARD 准备态。 */
    private fun reward(vararg definitionIds: String, inspectionRatio: Double = 1.0): InventoryState {
        val base = entered(100)
        val staged = base.copy(progress = base.progress.copy(run = base.progress.run!!.copy(stage = JourneyStage.REWARD)))
        return withGround(journey.battleLoot(staged), definitionIds.toList(), inspectionRatio)
    }

    private fun inspected(): InventoryState = reward("blade", inspectionRatio = 1.0)

    /** 批量拾取适用时的统一前置断言，失败时给出具体原因。 */
    private fun expectApplicable(s: InventoryState): BatchLootResult {
        val invalid = inventory.validate(s)
        assertNull("前置状态应合法：$invalid", invalid)
        val run = s.progress.run
        assertNotNull("应存在挑战", run)
        assertEquals("阶段应为 REWARD", JourneyStage.REWARD, run!!.stage)
        assertEquals("相位应为 PREPARATION", InventoryPhase.PREPARATION, s.phase)
        val result = NodeLootRules.collectRevealedIntoBackpack(s, inventory)
        assertNotNull("REWARD 准备阶段应适用批量拾取", result)
        return result!!
    }

    private fun fill(vararg cells: Pair<Int, Int>) = cells.mapIndexed { index, (x, y) ->
        StoredItem("own$index", "blade", ItemPlace(Container.BACKPACK, x, y))
    }

    /** 掉落实例按约定命名，索引即它在掉落列表中的位置。 */
    private fun groundId(s: InventoryState, index: Int) = "${s.progress.run!!.nodeLoot!!.id}:drop:$index"

    @Test fun `掉落可全部放入时逐个进入背包`() {
        val s = reward("blade", "essence")
        val result = expectApplicable(s)
        assertEquals(listOf(groundId(s, 0), groundId(s, 1)), result.pickedIds)
        assertTrue(result.remainingIds.isEmpty())
        assertTrue(result.pendingInspectionIds.isEmpty())
        assertNull(inventory.validate(result.state))
        val owned = result.state.items.associateBy { it.instanceId }
        // 顺序扫描：第一件落在左上角，第二件紧随其后
        assertEquals(ItemPlace(Container.BACKPACK, 0, 0), owned.getValue(groundId(s, 0)).place)
        assertEquals(ItemPlace(Container.BACKPACK, 1, 0), owned.getValue(groundId(s, 1)).place)
        // 绑定状态跟随定义保留
        assertTrue(owned.getValue(groundId(s, 1)).bound)
        assertTrue(result.state.progress.run!!.nodeLoot!!.remaining.isEmpty())
    }

    @Test fun `只有第二件放不下时留在地面并继续处理后面物品`() {
        // 4×2 背包占掉 7 格，只剩 (3,1)
        val carried = fill(0 to 0, 1 to 0, 2 to 0, 3 to 0, 0 to 1, 1 to 1, 2 to 1)
        val base = inspected()
        val s = withGround(base.copy(items = base.items + carried), listOf("blade", "gauntlets", "salve"))
        val result = expectApplicable(s)
        // 第一件进唯一的空格；两格宽的第二件与随后的第三件都放不下，但循环没有中断
        assertEquals(listOf(groundId(s, 0)), result.pickedIds)
        assertEquals(setOf(groundId(s, 1), groundId(s, 2)), result.remainingIds.toSet())
        // 已有背包物品一件未动
        assertEquals(7, result.state.items.count { it.instanceId.startsWith("own") })
        assertEquals(ItemPlace(Container.BACKPACK, 0, 0), result.state.items.single { it.instanceId == "own0" }.place)
        assertNull(inventory.validate(result.state))
    }

    @Test fun `普通方向放不下时改用旋转后放置`() {
        // y=1 整行占满、y=0 只留 (0,0)(1,0)：竖放 1×2 需要同列两行，只能旋转成 2×1
        val carried = fill(2 to 0, 3 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1)
        val base = inspected()
        val s = withGround(base.copy(items = base.items + carried), listOf("staff"))
        val result = expectApplicable(s)
        assertEquals(listOf(groundId(s, 0)), result.pickedIds)
        assertTrue(result.remainingIds.isEmpty())
        val placed = result.state.items.single { it.instanceId == groundId(s, 0) }
        assertEquals(ItemPlace(Container.BACKPACK, 0, 0, rotated = true), placed.place)
        assertNull(inventory.validate(result.state))
    }

    @Test fun `背包已满时全部留在地面且状态不变`() {
        val carried = fill(0 to 0, 1 to 0, 2 to 0, 3 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1)
        val base = inspected()
        val s = withGround(base.copy(items = base.items + carried), listOf("blade", "essence"))
        val result = expectApplicable(s)
        assertEquals(0, result.pickedCount)
        assertEquals(2, result.remainingCount)
        // 一件都没进来，地面也一件没少
        assertEquals(s.items.size, result.state.items.size)
        assertEquals(2, result.state.progress.run!!.nodeLoot!!.remaining.size)
    }

    @Test fun `未鉴定物品只等待不跳过也不拾取`() {
        val s = reward("blade", "staff", inspectionRatio = 0.0)
        // 前提：此刻尚未揭示任何一件
        val loot = s.progress.run!!.nodeLoot!!
        assertTrue(loot.remaining.none { NodeLootRules.revealed(loot, it.instanceId, inventory.definitions) })
        val result = expectApplicable(s)
        assertEquals(0, result.pickedCount)
        assertEquals(2, result.pendingCount)
        // 待鉴定与放不下是两种原因，互不混淆
        assertTrue(result.remainingIds.isEmpty())
        assertEquals(2, result.state.progress.run!!.nodeLoot!!.remaining.size)
    }

    @Test fun `部分鉴定时只拾取已揭示物品并保持三类结果互斥`() {
        val s = reward("blade", "staff", "gauntlets", inspectionRatio = 0.5)
        val loot = s.progress.run!!.nodeLoot!!
        val result = expectApplicable(s)
        // 三类结果互斥且覆盖全部地面实例
        val all = setOf(result.pickedIds, result.remainingIds, result.pendingInspectionIds)
            .sumOf { it.size }
        assertEquals(loot.remaining.size, all)
        assertEquals(loot.remaining.size, (result.pickedIds + result.remainingIds + result.pendingInspectionIds).toSet().size)
        // 已拾取的一定是当时已揭示的
        assertTrue(result.pickedIds.all { id -> NodeLootRules.revealed(loot, id, inventory.definitions) })
        // 等待中的一定仍未揭示，没有被偷偷拾取
        assertTrue(result.pendingInspectionIds.all { id -> !NodeLootRules.revealed(loot, id, inventory.definitions) })
        assertTrue(result.state.items.none { it.instanceId in result.pendingInspectionIds })
        assertNull(inventory.validate(result.state))
    }

    @Test fun `重复调用不会重复拾取也不丢失实例`() {
        val s = reward("blade")
        val first = expectApplicable(s)
        assertEquals(listOf(groundId(s, 0)), first.pickedIds)
        val second = expectApplicable(first.state)
        assertEquals(0, second.pickedCount)
        assertEquals(0, second.remainingCount)
        assertEquals(first.state.items.size, second.state.items.size)
        assertEquals(1, second.state.items.count { it.instanceId == groundId(s, 0) })
        assertNull(inventory.validate(second.state))
    }

    @Test fun `取走最后一件后仍由现有流程结束掉落节点`() {
        val s = reward("blade")
        val result = expectApplicable(s)
        assertTrue("取完应无剩余", result.state.progress.run!!.nodeLoot!!.remaining.isEmpty())
        val finished = journey.finishLoot(result.state, true)
        assertTrue(finished.error, finished.ok)
        assertNull(inventory.validate(finished.state))
    }

    @Test fun `非掉落阶段批量拾取不适用`() {
        val home = InventoryState(gold = 500, progress = CampaignProgress(chests = listOf(box)))
        assertNull("局外不应适用", NodeLootRules.collectRevealedIntoBackpack(home, inventory))
        val ready = entered(100)
        assertNull("READY 阶段不应适用", NodeLootRules.collectRevealedIntoBackpack(ready, inventory))
    }

    @Test fun `拾取保留星级与堆叠数量`() {
        val base = inspected()
        val run = base.progress.run!!
        val previous = run.nodeLoot!!
        val items = listOf(
            StoredItem("${previous.id}:drop:0", "salve", ItemPlace(Container.WAREHOUSE), count = 3)
        )
        val staged = previous.copy(items = items, picked = emptySet(), putDown = emptyList(), inspectionMs = 0L)
        val total = NodeLootRules.duration(staged, inventory.definitions)
        val s = base.copy(progress = base.progress.copy(run = run.copy(nodeLoot = staged.copy(inspectionMs = total))))
        val result = expectApplicable(s)
        val placed = result.state.items.single { it.instanceId == groundId(s, 0) }
        assertEquals(3, placed.count)
        assertEquals(1, placed.star)
        assertNull(inventory.validate(result.state))
    }
}
