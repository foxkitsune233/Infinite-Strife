package com.example.douqiuqiu.quest

import com.example.douqiuqiu.hero.HeroLevelRules
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.InventoryResult
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.ItemPlace
import com.example.douqiuqiu.inventory.StoredItem

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
        val active = quests.filter { it.kind != QuestKind.CONVERSION && it.id !in s.progress.claimedQuests && !nodeClaimed(s, it, quests) }
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
                QuestEvent.Entered -> if (o.type == ObjectiveType.ENTER_RUN) 1 else 0
                QuestEvent.LootPicked -> if (o.type == ObjectiveType.PICKUP_LOOT) 1 else 0
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
            next[quest.id] = minOf(before.toLong() + delta, o.target.toLong()).toInt()
            changed = true
        }
        if (!changed) return s
        return s.copy(progress = s.progress.copy(questCounters = next))
    }

    /** 可验证的状态里程碑只增不减；旧档已有成长无需重买，卸下/消耗物品不会撤销。 */
    fun observe(s: InventoryState, quests: List<Quest> = QuestContent.all): InventoryState {
        val counters = s.progress.questCounters.toMutableMap()
        for (q in quests) {
            if (q.id in s.progress.claimedQuests || nodeClaimed(s, q, quests)) continue
            val value = when (q.objective.type) {
                ObjectiveType.EQUIP_ITEM -> if (s.items.any { it.place.container == Container.EQUIPMENT && it.place.x < 6 }) 1 else 0
                ObjectiveType.SLOT_CARD -> if (s.items.any { it.place.container == Container.SKILLS }) 1 else 0
                ObjectiveType.STORE_SAFE -> if (s.items.any { it.place.container == Container.SAFE }) 1 else 0
                ObjectiveType.BACKPACK_LEVEL -> com.example.douqiuqiu.growth.GrowthContent.backpackSizes.indexOf(s.backpackWidth to s.backpackHeight)
                ObjectiveType.SAFE_LEVEL -> com.example.douqiuqiu.growth.GrowthContent.safeSizes.indexOf(s.safeWidth to s.safeHeight)
                ObjectiveType.SKILL_LEVEL -> s.skillSlots - 4
                ObjectiveType.CLEAR_COUNT -> s.progress.clears
                ObjectiveType.HERO_LEVEL -> q.objective.heroId?.let { s.progress.heroLevels[it] ?: 1 }
                    ?: (s.progress.heroLevels.values.maxOrNull() ?: 1)
                else -> 0
            }.coerceAtMost(q.objective.target)
            if (value > (counters[q.id] ?: 0)) counters[q.id] = value
        }
        return if (counters == s.progress.questCounters) s else s.copy(progress = s.progress.copy(questCounters = counters))
    }

    fun nodeClaimed(s: InventoryState, quest: Quest, quests: List<Quest> = QuestContent.all): Boolean =
        quest.node > 0 && quests.any { it.node == quest.node && it.id in s.progress.claimedQuests }

    fun nodeAvailable(s: InventoryState, quest: Quest, quests: List<Quest> = QuestContent.all): Boolean =
        quest.node <= 1 || (1 until quest.node).all { node ->
            quests.any { it.node == node && it.id in s.progress.claimedQuests }
        }

    /** 可领取的任务（已达目标、前置已完成、未领取），按配置顺序。 */
    fun claimable(s: InventoryState, quests: List<Quest> = QuestContent.all): List<Quest> = quests.filter { q ->
        q.kind != QuestKind.CONVERSION &&
            q.id !in s.progress.claimedQuests && !nodeClaimed(s, q, quests) && nodeAvailable(s, q, quests) &&
            (observe(s, quests).progress.questCounters[q.id] ?: 0) >= q.objective.target &&
            s.progress.clears >= q.minClears &&
            q.prerequisiteQuestIds.all { it in s.progress.claimedQuests }
    }

    /** 可兑换的转化任务（仓库材料足够且安全箱已满级），按配置顺序。 */
    fun convertible(s: InventoryState, quests: List<Quest> = QuestContent.all): List<Quest> = quests.filter { q ->
        q.kind == QuestKind.CONVERSION && q.prerequisiteQuestIds.all { it in s.progress.claimedQuests } &&
            q.cost.all { (id, need) -> warehouseCount(s, id) >= need }
    }

    /**
     * 当前可见任务：只暴露「第一个未完成节点」内的全部选项；已领取节点与尚未解锁的
     * 后续节点均不返回（面板据此只渲染一项，完成一件后自动移出、下一件接着出现）。
     * 一个节点被视为「已完成」当且仅当该节点内任一任务已被领取（三选一节点领取任一项即完成）。
     */
    fun visibleQuests(s: InventoryState, quests: List<Quest> = QuestContent.all): List<Quest> {
        val byNode = quests.filter { it.node > 0 }.groupBy { it.node }
        val done = byNode.filter { (_, qs) -> qs.any { it.id in s.progress.claimedQuests } }.keys
        val current = byNode.keys.sorted().firstOrNull { it !in done } ?: return emptyList()
        return byNode[current]!!
    }

    /** 领取一次性任务奖励。前置未满足 / 未达目标 / 重复领取均拒绝。 */
    fun claim(s: InventoryState, questId: String, rules: InventoryRules, quests: List<Quest> = QuestContent.all): InventoryResult {
        rules.validate(s)?.let { return InventoryResult(s, it) }
        if (s.phase != com.example.douqiuqiu.inventory.InventoryPhase.HOME || s.progress.run != null) return InventoryResult(s, "仅营地可领取任务奖励")
        val quest = quests.firstOrNull { it.id == questId } ?: return InventoryResult(s, "任务不存在")
        if (quest.kind == QuestKind.CONVERSION) return InventoryResult(s, "转化任务请用兑换入口")
        if (questId in s.progress.claimedQuests) return InventoryResult(s, "任务奖励已领取")
        if (nodeClaimed(s, quest, quests)) return InventoryResult(s, "本节点已选择其他任务并领取奖励")
        if (!nodeAvailable(s, quest, quests)) return InventoryResult(s, "请先领取前一节点奖励")
        val observed = observe(s, quests)
        if ((observed.progress.questCounters[questId] ?: 0) < quest.objective.target) return InventoryResult(s, "任务尚未完成")
        if (s.progress.clears < quest.minClears) return InventoryResult(s, "还需通关${quest.minClears - s.progress.clears}次")
        if (!quest.prerequisiteQuestIds.all { it in s.progress.claimedQuests }) return InventoryResult(s, "前置任务未完成")
        val granted = grantRewards(observed, quest, rules)
        if (!granted.ok) return InventoryResult(s, granted.error)
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
        "${observe(s).progress.questCounters[quest.id] ?: 0}/${quest.objective.target}"

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
                    } else {
                        if (next.gold.toLong() + reward.amount > Int.MAX_VALUE) return InventoryResult(s, "金币超出上限")
                        next = next.copy(gold = next.gold + reward.amount)
                    }
                }
                RewardType.HERO_LEVEL_UP -> {
                    val heroId = reward.heroId ?: return InventoryResult(s, "任务奖励配置缺失")
                    val result = HeroLevelRules.upgrade(next, heroId, rules)
                    if (!result.ok) return InventoryResult(s, result.error ?: "等级提升失败")
                    next = result.state
                }
                RewardType.ITEM, RewardType.MATERIAL, RewardType.GUARD_CORE -> {
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
