package com.example.douqiuqiu.quest

import com.example.douqiuqiu.hero.HeroLevelRules
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.InventoryResult
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.ItemPlace
import com.example.douqiuqiu.inventory.StoredItem

/**
 * 任务内容表（0.1.6）。
 *
 * **当前为空表**：框架与全部机制已就绪，具体任务条目由维护者审阅设计稿后
 * 补充（初稿 40 条见 `docs/superpowers/plans/016-quest-list-issue.md`）。
 * 空表时任务面板显示"暂无任务"，其余系统不受影响。
 *
 * 配置约束（补充时遵循）：
 * - GUIDE / CHALLENGE 用 [QuestObjective] 描述目标，由 [QuestTracker.record] 推进；
 * - CONVERSION 用 [Quest.cost] 描述上缴材料，由 [QuestTracker.convert] 手动兑换；
 * - 奖励只允许 [QuestReward] 已声明的类型，不新增类型；
 * - ID 命名：`guide.<英雄>` / `challenge.<序号>` / `convert.<序号>`。
 */
object QuestContent {
    val all: List<Quest> = emptyList()

    fun byId(id: String): Quest? = all.firstOrNull { it.id == id }
}

/**
 * 任务推进器（0.1.6）。纯函数快照事务，风格对齐
 * [com.example.douqiuqiu.inventory.GrowthRules]：被拒时返回操作前快照。
 *
 * 三个入口：
 * - [record]：事件计数，挂在全部现有结算点上（不新增流程）；
 * - [claim]：领取一次性任务奖励；
 * - [convert]：可重复的转化任务，上缴材料换奖励。
 */
object QuestTracker {

    /** 推进所有未领取任务中匹配该事件的计数器。 */
    fun record(s: InventoryState, event: QuestEvent, quests: List<Quest> = QuestContent.all): InventoryState {
        val active = quests.filter { it.kind != QuestKind.CONVERSION && it.id !in s.progress.claimedQuests }
        if (active.isEmpty()) return s
        val next = s.progress.questCounters.toMutableMap()
        var changed = false
        for (quest in active) {
            val o = quest.objective
            val before = next[quest.id] ?: 0
            if (before >= o.target) continue
            // HERO_LEVEL 是「达到即满」型目标：满足条件时直接把计数器推到目标值，
            // 其余目标按事件次数累加。
            val delta = when (event) {
                is QuestEvent.BattleWon -> when (o.type) {
                    ObjectiveType.WIN_BATTLES -> 1
                    ObjectiveType.HERO_WINS -> if (o.heroId == event.heroId) 1 else 0
                    else -> 0
                }
                is QuestEvent.RunCleared -> when (o.type) {
                    ObjectiveType.CLEAR_COUNT -> 1
                    ObjectiveType.CLEAR_DIFFICULTY -> if (event.difficulty >= o.difficulty) 1 else 0
                    else -> 0
                }
                QuestEvent.Extracted -> if (o.type == ObjectiveType.EXTRACT_COUNT) 1 else 0
                QuestEvent.EventChosen -> if (o.type == ObjectiveType.EVENT_CHOICES) 1 else 0
                QuestEvent.ShopPurchase -> if (o.type == ObjectiveType.SHOP_PURCHASE) 1 else 0
                QuestEvent.Crafted -> if (o.type == ObjectiveType.CRAFT_COUNT) 1 else 0
                QuestEvent.ConsumableUsed -> if (o.type == ObjectiveType.CONSUMABLE_USED) 1 else 0
                is QuestEvent.MaterialGained ->
                    if (o.type == ObjectiveType.MATERIAL_GAINED && o.materialId == event.materialId) event.count else 0
                is QuestEvent.HeroLevelChanged -> when (o.type) {
                    ObjectiveType.HERO_LEVEL ->
                        if ((o.heroId == null || o.heroId == event.heroId) && event.level >= o.target) o.target - before else 0
                    else -> 0
                }
                QuestEvent.GuardedDeath -> if (o.type == ObjectiveType.GUARDED_DEATH) 1 else 0
            }
            if (delta <= 0) continue
            next[quest.id] = minOf(before + delta, o.target)
            changed = true
        }
        if (!changed) return s
        return s.copy(progress = s.progress.copy(questCounters = next))
    }

    /** 可领取的任务（已达目标、前置已完成、未领取），按配置顺序。 */
    fun claimable(s: InventoryState, quests: List<Quest> = QuestContent.all): List<Quest> = quests.filter { q ->
        q.kind != QuestKind.CONVERSION &&
            q.id !in s.progress.claimedQuests &&
            (s.progress.questCounters[q.id] ?: 0) >= q.objective.target &&
            s.progress.clears >= q.minClears &&
            q.prerequisiteQuestIds.all { it in s.progress.claimedQuests }
    }

    /** 可兑换的转化任务（仓库材料足够且安全箱已满级），按配置顺序。 */
    fun convertible(s: InventoryState, quests: List<Quest> = QuestContent.all): List<Quest> = quests.filter { q ->
        q.kind == QuestKind.CONVERSION && q.prerequisiteQuestIds.all { it in s.progress.claimedQuests } &&
            q.cost.all { (id, need) -> warehouseCount(s, id) >= need }
    }

    /** 领取一次性任务奖励。前置未满足 / 未达目标 / 重复领取均拒绝。 */
    fun claim(s: InventoryState, questId: String, rules: InventoryRules, quests: List<Quest> = QuestContent.all): InventoryResult {
        val quest = quests.firstOrNull { it.id == questId } ?: return InventoryResult(s, "任务不存在")
        if (quest.kind == QuestKind.CONVERSION) return InventoryResult(s, "转化任务请用兑换入口")
        if (questId in s.progress.claimedQuests) return InventoryResult(s, "任务奖励已领取")
        if ((s.progress.questCounters[questId] ?: 0) < quest.objective.target) return InventoryResult(s, "任务尚未完成")
        if (s.progress.clears < quest.minClears) return InventoryResult(s, "还需通关${quest.minClears - s.progress.clears}次")
        if (!quest.prerequisiteQuestIds.all { it in s.progress.claimedQuests }) return InventoryResult(s, "前置任务未完成")
        val granted = grantRewards(s, quest, rules)
        if (!granted.ok) return granted
        return InventoryResult(
            granted.state.copy(progress = granted.state.progress.copy(claimedQuests = granted.state.progress.claimedQuests + questId))
        )
    }

    /** 兑换可重复转化任务：原子扣除上缴材料后发放奖励；材料不足不产生任何变更。 */
    fun convert(s: InventoryState, questId: String, rules: InventoryRules, quests: List<Quest> = QuestContent.all): InventoryResult {
        val quest = quests.firstOrNull { it.id == questId } ?: return InventoryResult(s, "任务不存在")
        if (quest.kind != QuestKind.CONVERSION) return InventoryResult(s, "该任务不是转化任务")
        if (!quest.prerequisiteQuestIds.all { it in s.progress.claimedQuests }) return InventoryResult(s, "前置任务未完成")
        if (s.phase != com.example.douqiuqiu.inventory.InventoryPhase.HOME) return InventoryResult(s, "仅局外可兑换")
        val missing = quest.cost.filter { (id, need) -> warehouseCount(s, id) < need }
        if (missing.isNotEmpty()) return InventoryResult(s, "上缴材料不足")
        val consumed = mutableSetOf<String>()
        for ((id, need) in quest.cost) {
            var remain = need
            for (item in s.items.filter { it.definitionId == id && it.place.container == Container.WAREHOUSE && it.instanceId !in consumed }) {
                if (remain <= 0) break
                consumed += item.instanceId
                remain -= item.count
            }
        }
        val stripped = s.copy(items = s.items.filterNot { it.instanceId in consumed })
        val granted = grantRewards(stripped, quest, rules)
        return if (granted.ok) granted else InventoryResult(s, granted.error ?: "兑换失败")
    }

    /** 任务面板进度文本，如 "3/5"。 */
    fun progressText(s: InventoryState, quest: Quest): String =
        "${s.progress.questCounters[quest.id] ?: 0}/${quest.objective.target}"

    private fun warehouseCount(s: InventoryState, definitionId: String): Int =
        s.items.filter { it.definitionId == definitionId && it.place.container == Container.WAREHOUSE }.sumOf { it.count }

    private fun grantRewards(s: InventoryState, quest: Quest, rules: InventoryRules): InventoryResult {
        var next = s
        for (reward in quest.rewards) {
            when (reward.type) {
                RewardType.GOLD -> {
                    if (next.gold.toLong() + reward.amount > Int.MAX_VALUE) return InventoryResult(s, "金币超出上限")
                    next = next.copy(gold = next.gold + reward.amount)
                }
                RewardType.HERO_UNLOCK -> {
                    val heroId = reward.heroId ?: return InventoryResult(s, "任务奖励配置缺失")
                    if (heroId !in next.progress.unlockedHeroes) {
                        next = next.copy(progress = next.progress.copy(unlockedHeroes = next.progress.unlockedHeroes + heroId))
                    }
                }
                RewardType.HERO_LEVEL_UP -> {
                    val heroId = reward.heroId ?: return InventoryResult(s, "任务奖励配置缺失")
                    val result = HeroLevelRules.upgrade(next, heroId, rules)
                    if (!result.ok) return InventoryResult(s, result.error ?: "等级提升失败")
                    next = result.state
                }
                RewardType.MATERIAL, RewardType.GUARD_CORE -> {
                    val id = reward.materialId ?: return InventoryResult(s, "任务奖励配置缺失")
                    val definition = rules.definitions[id] ?: return InventoryResult(s, "奖励物品配置缺失")
                    val created = (0 until reward.amount).map { index ->
                        StoredItem(newInstanceId(next, "quest:${quest.id}:$index"), id,
                            ItemPlace(Container.WAREHOUSE), bound = definition.boundOnAcquire)
                    }
                    next = next.copy(items = next.items + created)
                }
            }
        }
        return rules.validate(next)?.let { InventoryResult(s, it) } ?: InventoryResult(next)
    }

    private fun newInstanceId(s: InventoryState, prefix: String): String {
        var candidate = prefix
        var index = 0
        while (s.items.any { it.instanceId == candidate }) {
            index++
            candidate = "$prefix-$index"
        }
        return candidate
    }
}
