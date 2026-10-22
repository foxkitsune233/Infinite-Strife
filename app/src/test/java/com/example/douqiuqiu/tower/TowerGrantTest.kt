package com.example.douqiuqiu.tower

import com.example.douqiuqiu.inventory.InventoryPhase
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.CampaignProgress
import com.example.douqiuqiu.journey.JourneyRules
import com.example.douqiuqiu.quest.QuestContent
import org.junit.Assert.*
import org.junit.Test

/**
 * 0.1.7 转生授予验收（设计稿 §2.3/§2.5）：魔晶/神杖通关后进待授予池，
 * 由营地「转生授予」面板手动授予，授予一次性且绑定英雄。
 */
class TowerGrantTest {
    private val rules = InventoryRules(emptyMap())
    private val flow = TowerFlow(JourneyRules(rules))

    private fun pending(shard: List<String> = emptyList(), aghs: List<String> = emptyList()): InventoryState =
        InventoryState(progress = CampaignProgress(pendingShardHeroes = shard, pendingAghanimHeroes = aghs))

    /** 全部 24 节点已领取的营地状态（解锁爬塔）。 */
    private fun allQuestsDone(): InventoryState {
        val claimed = QuestContent.all.groupBy { it.node }.values.map { it.first().id }.toSet()
        return InventoryState(progress = CampaignProgress(
            claimedQuests = claimed,
            questCounters = QuestContent.all.associate { it.id to it.objective.target }))
    }

    @Test fun floorFivePutsShardIntoPendingPoolInsteadOfApplyingIt() {
        val base = allQuestsDone()
        val s = base.copy(progress = base.progress.copy(
            towerRun = TowerRunState(floor = 5, heroId = "viper", rng = 1729, clearedFloors = 4)))
        val r = flow.resolve(s, won = true)
        assertEquals(listOf("viper"), r.state.progress.pendingShardHeroes)
        assertTrue(r.state.progress.towerShard.isEmpty())
    }

    @Test fun floorFifteenPutsAghanimIntoPendingPoolInsteadOfApplyingIt() {
        val base = allQuestsDone()
        val s = base.copy(progress = base.progress.copy(
            towerRun = TowerRunState(floor = 15, heroId = "viper", rng = 1729, clearedFloors = 14)))
        val r = flow.resolve(s, won = true)
        assertEquals(listOf("viper"), r.state.progress.pendingAghanimHeroes)
        assertTrue(r.state.progress.towerAghs.isEmpty())
    }

    @Test fun grantingShardMovesItToOwnedAndIsOneTime() {
        val s = pending(shard = listOf("viper"))
        val r = rules.grantPendingShard(s, "viper")
        assertTrue(r.error, r.ok)
        assertTrue(r.state.progress.towerShard.contains("viper"))
        assertTrue(r.state.progress.pendingShardHeroes.isEmpty())
        // 一次性：授予后不能再授予第二次。
        val again = rules.grantPendingShard(r.state, "viper")
        assertFalse(again.ok)
        assertEquals(setOf("viper"), again.state.progress.towerShard)
    }

    @Test fun grantingAghanimMovesItToOwnedAndIsOneTime() {
        val s = pending(aghs = listOf("abaddon"))
        val r = rules.grantPendingAghanim(s, "abaddon")
        assertTrue(r.error, r.ok)
        assertTrue(r.state.progress.towerAghs.contains("abaddon"))
        assertTrue(r.state.progress.pendingAghanimHeroes.isEmpty())
        assertFalse(rules.grantPendingAghanim(r.state, "abaddon").ok)
    }

    @Test fun duplicatePendingRewardIsConsumedWithoutStacking() {
        val s = pending(shard = listOf("viper", "viper"))
        val first = rules.grantPendingShard(s, "viper")
        assertTrue(first.error, first.ok)
        assertEquals(setOf("viper"), first.state.progress.towerShard)
        assertEquals(listOf("viper"), first.state.progress.pendingShardHeroes)
        // 第二条重复奖励授予时只消耗，不叠加。
        val second = rules.grantPendingShard(first.state, "viper")
        assertTrue(second.error, second.ok)
        assertEquals(setOf("viper"), second.state.progress.towerShard)
        assertTrue(second.state.progress.pendingShardHeroes.isEmpty())
    }

    @Test fun grantRejectsEmptyPoolUnknownHeroAndNonHomePhase() {
        assertFalse(rules.grantPendingShard(pending(), "viper").ok)
        assertFalse(rules.grantPendingShard(pending(shard = listOf("viper")), "axe").ok)
        assertFalse(rules.grantPendingAghanim(pending(), "viper").ok)
        val inBattle = pending(shard = listOf("viper")).copy(phase = InventoryPhase.BATTLE)
        assertFalse(rules.grantPendingShard(inBattle, "viper").ok)
        val inBattleAghs = pending(aghs = listOf("viper")).copy(phase = InventoryPhase.BATTLE)
        assertFalse(rules.grantPendingAghanim(inBattleAghs, "viper").ok)
    }

    @Test fun shardAghanimAndPendingPoolSurviveDeathAndReentry() {
        val base = allQuestsDone()
        val s = base.copy(progress = base.progress.copy(
            towerShard = setOf("viper"), towerAghs = setOf("abaddon"),
            pendingShardHeroes = listOf("axe"), pendingAghanimHeroes = listOf("legion_commander"),
            towerRapier = true, towerHighestFloor = 12, levelCap = 65,
            towerRun = TowerRunState(floor = 13, heroId = "viper", rng = 1729, clearedFloors = 12,
                loot = listOf(TowerLoot("tower_f12", 1, 800)))))
        // 塔内死亡：只清轮次与临时战利品，魔晶/神杖/待授予池/圣剑/上限完全保留。
        val dead = flow.resolve(s, won = false)
        assertNull(dead.state.progress.towerRun)
        assertEquals(setOf("viper"), dead.state.progress.towerShard)
        assertEquals(setOf("abaddon"), dead.state.progress.towerAghs)
        assertEquals(listOf("axe"), dead.state.progress.pendingShardHeroes)
        assertEquals(listOf("legion_commander"), dead.state.progress.pendingAghanimHeroes)
        assertTrue(dead.state.progress.towerRapier)
        assertEquals(65, dead.state.progress.levelCap)
        assertEquals(12, dead.state.progress.towerHighestFloor)
        // 重新进入爬塔同样不清除任何已获强化与待授予奖励。
        val again = flow.enter(dead.state)
        assertTrue(again.error, again.ok)
        assertEquals(setOf("viper"), again.state.progress.towerShard)
        assertEquals(setOf("abaddon"), again.state.progress.towerAghs)
        assertEquals(listOf("axe"), again.state.progress.pendingShardHeroes)
        assertEquals(listOf("legion_commander"), again.state.progress.pendingAghanimHeroes)
    }
}
