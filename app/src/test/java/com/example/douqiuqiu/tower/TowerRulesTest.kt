package com.example.douqiuqiu.tower

import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.CampaignProgress
import com.example.douqiuqiu.journey.JourneyRules
import com.example.douqiuqiu.quest.QuestContent
import org.junit.Assert.*
import org.junit.Test

/**
 * 0.1.7 转生爬塔验收（设计稿 §2）。
 * 数值期望按设计稿人工写入，不用被测函数自身生成预期。
 */
class TowerRulesTest {
    private val journey = JourneyRules(InventoryRules(emptyMap()))
    private val flow = TowerFlow(journey)

    /** 全部 24 节点已领取的营地状态（解锁爬塔）。 */
    private fun allQuestsDone(): InventoryState {
        val claimed = QuestContent.all.groupBy { it.node }.values.map { it.first().id }.toSet()
        return InventoryState(progress = CampaignProgress(
            claimedQuests = claimed,
            questCounters = QuestContent.all.associate { it.id to it.objective.target }))
    }

    private fun inTower(
        floor: Int, cleared: Int,
        loot: List<TowerLoot> = emptyList(),
        cap: Int = 10, highest: Int = 0
    ): InventoryState {
        val base = allQuestsDone()
        return base.copy(progress = base.progress.copy(
            towerRun = TowerRunState(floor = floor, heroId = "juggernaut", rng = 1729, clearedFloors = cleared, loot = loot),
            levelCap = cap, towerHighestFloor = highest))
    }

    private fun ok(result: com.example.douqiuqiu.inventory.InventoryResult): InventoryState {
        assertTrue(result.error, result.ok); return result.state
    }

    @Test fun capGrowsByFiveFromSecondFloorAndCapsAtHundred() {
        assertEquals(10, TowerRules.capAfterFloor(0))
        assertEquals(10, TowerRules.capAfterFloor(1))
        assertEquals(15, TowerRules.capAfterFloor(2))
        assertEquals(20, TowerRules.capAfterFloor(3))
        assertEquals(95, TowerRules.capAfterFloor(18))
        assertEquals(100, TowerRules.capAfterFloor(19))
        assertEquals(100, TowerRules.capAfterFloor(20))
    }

    @Test fun towerStaysLockedUntilEveryQuestNodeIsClaimed() {
        val fresh = InventoryState()
        assertFalse(TowerRules.isUnlocked(fresh))
        assertNotNull(TowerRules.lockedReason(fresh))
        assertFalse(flow.enter(fresh).ok)
        val done = allQuestsDone()
        assertTrue(TowerRules.isUnlocked(done))
        assertNull(TowerRules.lockedReason(done))
    }

    @Test fun enterStartsAtFloorOneWithActiveHero() {
        val s = ok(flow.enter(allQuestsDone()))
        val run = s.progress.towerRun!!
        assertEquals(1, run.floor)
        assertEquals(0, run.clearedFloors)
        assertEquals("juggernaut", run.heroId)
        assertTrue(run.loot.isEmpty())
    }

    @Test fun secondEnterRejectedWhileTowerRunActive() {
        val s = ok(flow.enter(allQuestsDone()))
        val again = flow.enter(s)
        assertFalse(again.ok)
        assertEquals(s, again.state)
    }

    @Test fun victoryAdvancesFloorAddsLootAndRaisesCap() {
        val s = inTower(floor = 2, cleared = 1)
        val r = flow.resolve(s, won = true)
        val run = r.state.progress.towerRun!!
        assertEquals(3, run.floor)
        assertEquals(2, run.clearedFloors)
        assertEquals(1, run.loot.size)
        assertEquals(200, run.loot.single().value) // lootValue(2) = 80 + 2×60
        assertEquals(15, r.state.progress.levelCap) // capAfterFloor(2)
        assertEquals(2, r.state.progress.towerHighestFloor)
        assertEquals(0, r.state.gold - s.gold) // 普通胜利不当场结算金币
    }

    @Test fun deathSellsLootAtFloorRatioAndKeepsCapAndHighest() {
        val loot = listOf(TowerLoot("a", 1, 1000), TowerLoot("b", 1, 500))
        val s = inTower(floor = 8, cleared = 7, loot = loot, cap = 40, highest = 7)
        val r = flow.resolve(s, won = false)
        assertNull(r.state.progress.towerRun)
        assertEquals(1050, r.state.gold - s.gold) // 1500 × min(100%, 7×10%)
        assertEquals(40, r.state.progress.levelCap) // 上限持久保留
        assertEquals(7, r.state.progress.towerHighestFloor)
    }

    @Test fun deathBelowTenFloorsSellsLessThanFullValue() {
        val loot = listOf(TowerLoot("a", 1, 1000))
        val s = inTower(floor = 4, cleared = 3, loot = loot)
        val r = flow.resolve(s, won = false)
        assertEquals(300, r.state.gold - s.gold) // 1000 × min(100%, 3×10%)
    }

    @Test fun floorFiveGrantsShardBoundToRunHero() {
        val r = flow.resolve(inTower(floor = 5, cleared = 4), won = true)
        assertEquals(setOf("juggernaut"), r.state.progress.towerShard)
        assertTrue(r.state.progress.towerAghs.isEmpty())
        assertTrue(r.state.progress.pendingRebirthItems.isEmpty())
    }

    @Test fun floorsElevenToTwentyEachGrantARebirthItem() {
        for (floor in 11..20) {
            val r = flow.resolve(inTower(floor = floor, cleared = floor - 1), won = true)
            assertEquals("floor=$floor", 1, r.state.progress.pendingRebirthItems.size)
        }
        // 第 10 层本身不给。
        val r10 = flow.resolve(inTower(floor = 10, cleared = 9), won = true)
        assertTrue(r10.state.progress.pendingRebirthItems.isEmpty())
    }

    @Test fun floorFifteenGrantsAghanimWithoutShard() {
        val r = flow.resolve(inTower(floor = 15, cleared = 14), won = true)
        assertEquals(setOf("juggernaut"), r.state.progress.towerAghs)
        assertTrue(r.state.progress.towerShard.isEmpty())
    }

    @Test fun clearingTwentiethFloorIsVictoryWithRapierAndFullPayout() {
        val loot = listOf(TowerLoot("a", 1, 1000))
        val s = inTower(floor = 20, cleared = 19, loot = loot, cap = 100, highest = 19)
        val r = flow.resolve(s, won = true)
        assertNull(r.state.progress.towerRun)
        assertTrue(r.state.progress.towerRapier)
        assertEquals(1, r.state.progress.pendingRebirthItems.size) // 第 20 层也发转生道具
        assertEquals(2280, r.state.gold - s.gold) // 携带 1000 + 第20层掉落 1280，通关 100% 结算
        assertEquals(100, r.state.progress.levelCap)
        assertEquals(20, r.state.progress.towerHighestFloor)
    }

    @Test fun enemyScalesUpWithFloorAndPreviewMatchesFormula() {
        val s = ok(flow.enter(allQuestsDone()))
        val enemy = flow.previewEnemy(s)!!
        val base = journey.fighter(InventoryState(), TowerRules.enemyHeroId(1))
        val scale = TowerRules.enemyScale(1)
        assertEquals(base.maxHp * scale.health, enemy.maxHp, 1e-9)
        assertEquals(base.attack * scale.attack, enemy.attack, 1e-9)
        assertTrue(TowerRules.enemyScale(10).health > TowerRules.enemyScale(1).health)
        assertTrue(TowerRules.enemyScale(20).attack > TowerRules.enemyScale(10).attack)
    }

    @Test fun enemyHeroIsDeterministicPerFloor() {
        assertEquals(TowerRules.enemyHeroId(3), TowerRules.enemyHeroId(3))
        assertTrue(TowerRules.enemyHeroId(1) in com.example.douqiuqiu.journey.FirstRunContent.heroes.map { it.id })
    }

    @Test fun fightRunsHeadlessAndLeavesConsistentState() {
        val s = ok(flow.enter(allQuestsDone()))
        val r = flow.fight(s)
        assertTrue(r.error ?: "ok", r.ok)
        val p = r.state.progress
        val run = p.towerRun
        if (run != null) {
            // 胜利进层：轮次推进、历史最高层与上限至少维持基准。
            assertEquals(2, run.floor)
            assertTrue(p.towerHighestFloor >= 1)
            assertTrue(p.levelCap >= 10)
        } else {
            // 死亡清算：轮次清除、上限回退基准。
            assertEquals(0, p.towerHighestFloor)
            assertEquals(10, p.levelCap)
        }
    }
}
