package com.example.douqiuqiu.quest

import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind
import com.example.douqiuqiu.inventory.ItemPlace
import com.example.douqiuqiu.inventory.StoredItem
import com.example.douqiuqiu.journey.CampaignProgress
import org.junit.Assert.*
import org.junit.Test

/**
 * 0.1.6 任务系统框架验收。任务列表由测试注入（独立于 QuestContent 生产表），验证记录、领取、兑换三类行为与原子性。
 */
class QuestTrackerTest {

    private val definitions = mapOf(
        "skill_shard" to ItemDefinition("skill_shard", "技能刻片", ItemKind.MATERIAL, 1, 1),
        "safe_lock" to ItemDefinition("safe_lock", "安全箱锁芯", ItemKind.MATERIAL, 1, 1),
        "equipment_guard_core" to ItemDefinition("equipment_guard_core", "装备护芯", ItemKind.MATERIAL, 1, 1)
    )
    private val inventory = InventoryRules(definitions)

    private val winQuest = Quest(
        "guide.test", "胜利三场", "赢三场", QuestKind.GUIDE,
        QuestObjective(ObjectiveType.WIN_BATTLES, 3),
        listOf(QuestReward(RewardType.GOLD, 200))
    )
    private val heroQuest = Quest(
        "challenge.hero", "主宰专精", "主宰赢两场", QuestKind.CHALLENGE,
        QuestObjective(ObjectiveType.HERO_WINS, 2, heroId = "juggernaut"),
        listOf(QuestReward(RewardType.MATERIAL, 1, materialId = "skill_shard"))
    )
    private val chained = Quest(
        "guide.chained", "链式", "前置完成后可领", QuestKind.GUIDE,
        QuestObjective(ObjectiveType.EXTRACT_COUNT, 1),
        listOf(QuestReward(RewardType.HERO_UNLOCK, heroId = "skywrath")),
        prerequisiteQuestIds = listOf("guide.test")
    )
    private val conversion = Quest(
        "convert.lock", "箱锁回收", "锁芯换护芯", QuestKind.CONVERSION,
        QuestObjective(ObjectiveType.WIN_BATTLES, 0),
        listOf(QuestReward(RewardType.GUARD_CORE, 1, materialId = "equipment_guard_core")),
        repeatable = true, cost = mapOf("safe_lock" to 1)
    )
    private val quests = listOf(winQuest, heroQuest, chained, conversion)

    private fun shard(n: Int) = (1..n).map { StoredItem("sh$it", "skill_shard", ItemPlace(Container.WAREHOUSE)) }
    private fun lock(n: Int) = (1..n).map { StoredItem("lk$it", "safe_lock", ItemPlace(Container.WAREHOUSE)) }

    @Test fun battleWinsIncrementOnlyMatchingCounter() {
        var s = InventoryState(progress = CampaignProgress(unlockedHeroes = setOf("juggernaut")))
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        assertEquals(1, s.progress.questCounters["guide.test"])
        assertEquals(1, s.progress.questCounters["challenge.hero"])   // 主宰赢也推进专精
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        assertEquals(2, s.progress.questCounters["guide.test"])
        // 换英雄只推进胜利场次，专精计数不动
        s = QuestTracker.record(s, QuestEvent.BattleWon("abaddon", 1), quests)
        assertEquals(3, s.progress.questCounters["guide.test"])
        assertEquals(2, s.progress.questCounters["challenge.hero"])
    }

    @Test fun counterStopsAtTarget() {
        var s = InventoryState()
        repeat(5) { s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests) }
        assertEquals(3, s.progress.questCounters["guide.test"])
    }

    @Test fun claimedQuestStopsCounting() {
        var s = InventoryState(gold = 0)
        repeat(3) { s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests) }
        val r = QuestTracker.claim(s, "guide.test", inventory, quests)
        assertTrue(r.error ?: "ok", r.ok)
        s = r.state
        assertEquals(200, s.gold)
        assertTrue("guide.test" in s.progress.claimedQuests)
        // 已领取后同事件不再计数
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        assertEquals(3, s.progress.questCounters["guide.test"])
    }

    @Test fun claimBeforeCompletionIsRejected() {
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        val r = QuestTracker.claim(s, "guide.test", inventory, quests)
        assertFalse(r.ok)
        assertEquals(s, r.state)
    }

    @Test fun claimIsNotRepeatable() {
        var s = InventoryState()
        repeat(3) { s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests) }
        s = QuestTracker.claim(s, "guide.test", inventory, quests).state
        val again = QuestTracker.claim(s, "guide.test", inventory, quests)
        assertFalse(again.ok)
        assertEquals(1200 + 200, s.gold)
    }

    @Test fun prerequisiteBlocksClaimUntilParentClaimed() {
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.Extracted, quests)
        assertTrue(QuestTracker.claim(s, "guide.chained", inventory, quests).let { !it.ok })
        repeat(3) { s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests) }
        s = QuestTracker.claim(s, "guide.test", inventory, quests).state
        val r = QuestTracker.claim(s, "guide.chained", inventory, quests)
        assertTrue(r.error ?: "ok", r.ok)
        assertTrue("skywrath" in r.state.progress.unlockedHeroes)
    }

    @Test fun materialRewardLandsInWarehouse() {
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        val r = QuestTracker.claim(s, "challenge.hero", inventory, quests)
        assertTrue(r.error ?: "ok", r.ok)
        val granted = r.state.items.filter { it.definitionId == "skill_shard" }
        assertEquals(1, granted.size)
        assertEquals(Container.WAREHOUSE, granted.first().place.container)
    }

    @Test fun minClearsGatesClaim() {
        val gated = Quest("g", "门控", "", QuestKind.CHALLENGE,
            QuestObjective(ObjectiveType.WIN_BATTLES, 1),
            listOf(QuestReward(RewardType.GOLD, 10)), minClears = 2)
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), listOf(gated))
        assertFalse(QuestTracker.claim(s, "g", inventory, listOf(gated)).ok)
        s = s.copy(progress = s.progress.copy(clears = 2))
        assertTrue(QuestTracker.claim(s, "g", inventory, listOf(gated)).ok)
    }

    @Test fun convertConsumesMaterialAndGrantsRewardAtomically() {
        var s = InventoryState(items = lock(1))
        assertTrue(QuestTracker.convertible(s, quests).any { it.id == "convert.lock" })
        val r = QuestTracker.convert(s, "convert.lock", inventory, quests)
        assertTrue(r.error ?: "ok", r.ok)
        assertTrue(r.state.items.none { it.definitionId == "safe_lock" })
        assertEquals(1, r.state.items.count { it.definitionId == "equipment_guard_core" })
    }

    @Test fun convertWithoutMaterialChangesNothing() {
        val s = InventoryState()
        assertTrue(QuestTracker.convertible(s, quests).none { it.id == "convert.lock" })
        val r = QuestTracker.convert(s, "convert.lock", inventory, quests)
        assertFalse(r.ok)
        assertEquals(s, r.state)
        assertTrue(s.items.isEmpty())
    }

    @Test fun convertIsRepeatableWhileMaterialsLast() {
        var s = InventoryState(items = lock(2))
        repeat(2) { s = QuestTracker.convert(s, "convert.lock", inventory, quests).state }
        assertEquals(0, s.items.count { it.definitionId == "safe_lock" })
        assertEquals(2, s.items.count { it.definitionId == "equipment_guard_core" })
        assertFalse(QuestTracker.convert(s, "convert.lock", inventory, quests).ok)
    }

    @Test fun emptyContentTableRecordsNothing() {
        val s = InventoryState()
        val after = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), emptyList())
        assertEquals(s, after)
    }

    @Test fun heroLevelObjectiveSatisfiedByLevelEvent() {
        val levelQuest = Quest("lv", "等级", "", QuestKind.CHALLENGE,
            QuestObjective(ObjectiveType.HERO_LEVEL, 5),
            listOf(QuestReward(RewardType.GOLD, 50)))
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.HeroLevelChanged("juggernaut", 4), listOf(levelQuest))
        assertNull(s.progress.questCounters["lv"])
        s = QuestTracker.record(s, QuestEvent.HeroLevelChanged("juggernaut", 5), listOf(levelQuest))
        assertEquals(5, s.progress.questCounters["lv"])   // 达到即满：直接推到目标值
        assertTrue(QuestTracker.claim(s, "lv", inventory, listOf(levelQuest)).ok)
    }

    @Test fun progressTextShowsCounterOverTarget() {
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.BattleWon("juggernaut", 1), quests)
        assertEquals("1/3", QuestTracker.progressText(s, winQuest))
    }

    @Test fun clearDifficultyCountsOnlyAtOrAboveThreshold() {
        val hard = Quest("hard", "困难", "", QuestKind.CHALLENGE,
            QuestObjective(ObjectiveType.CLEAR_DIFFICULTY, 1, difficulty = 2),
            listOf(QuestReward(RewardType.GOLD, 10)))
        var s = InventoryState()
        s = QuestTracker.record(s, QuestEvent.RunCleared("juggernaut", 1), listOf(hard))
        assertEquals(null, s.progress.questCounters["hard"])
        s = QuestTracker.record(s, QuestEvent.RunCleared("juggernaut", 2), listOf(hard))
        assertEquals(1, s.progress.questCounters["hard"])
        s = QuestTracker.record(s, QuestEvent.RunCleared("juggernaut", 5), listOf(hard))
        assertEquals(1, s.progress.questCounters["hard"])
    }
}
