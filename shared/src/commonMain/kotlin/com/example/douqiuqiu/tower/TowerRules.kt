package com.example.douqiuqiu.tower

import com.example.douqiuqiu.inventory.InventoryPhase
import com.example.douqiuqiu.inventory.InventoryResult
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.DuelEngine
import com.example.douqiuqiu.journey.DuelState
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.journey.JourneyRules
import com.example.douqiuqiu.quest.QuestContent
import com.example.douqiuqiu.quest.QuestProgression
import com.example.douqiuqiu.quest.QuestTracker
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 0.1.7 转生爬塔规则（shared，纯函数 + 薄流程壳）。
 *
 * 核心口径（设计稿 §2，用户逐条给定）：
 * - 共 **20 层**；**第 2 层起每通一层等级上限 +5，至 100**：
 *   `capAfterFloor(f) = min(100, 10 + max(0, f-1) * 5)`；
 * - **解锁条件**：全部任务节点完成（24/24）；
 * - **塔内死亡**：本轮战利品按 `Σ价值 × min(100%, 已完成层数 × 10%)` 卖出，轮次清除；
 * - **上限/历史最高层/已解锁奖励持久保留**，不因死亡归零；
 * - 楼层奖励：第 5 层魔晶、第 11–20 层每层转生道具、第 15 层神杖、第 20 层通关圣剑；
 * - **通关第 20 层 = 胜利态**：战利品按 100% 结算，发放圣剑，不再转生。
 */
object TowerRules {

    const val TOWER_FLOORS = 20

    /** 每通关一层（从第 2 层起）提升的等级上限。 */
    const val CAP_PER_FLOOR = 5

    /** 初始等级上限（与 HeroLevelRules.LEVEL_CAP 一致，缺省回退值）。 */
    const val BASE_CAP = 10

    /** 设计上限。 */
    const val MAX_CAP = 100

    /** 死亡卖金比例每层 +10%。 */
    const val SELL_RATIO_PER_FLOOR = 0.10

    /** 战斗模拟 tick 上限（≈ 5.5 分钟游戏时间）；超时判负。 */
    const val SIM_TICK_CAP = 20_000

    /** 全部任务节点数（解锁门控）。 */
    fun totalNodes(): Int = QuestContent.all.map { it.node }.distinct().size

    /** 通关 f 层后的等级上限（f = 已通关层数；第 1 层不给加成）。 */
    fun capAfterFloor(floorsCleared: Int): Int =
        min(MAX_CAP, BASE_CAP + maxOf(0, floorsCleared - 1) * CAP_PER_FLOOR)

    /** 是否已解锁爬塔（全部任务节点完成）。 */
    fun isUnlocked(s: InventoryState): Boolean =
        QuestProgression.completedNodes(s) >= totalNodes()

    /** 未解锁时的门控文案；已解锁返回 null。 */
    fun lockedReason(s: InventoryState): String? {
        val total = totalNodes()
        val done = QuestProgression.completedNodes(s)
        return if (done >= total) null else "转生爬塔需完成全部 $total 个任务节点（当前 $done/$total）"
    }

    /** 敌人随层缩放（每层固定%：生命 +12%/层、攻击 +8%/层、攻速 +1/层、护甲 +0.5/层）。 */
    fun enemyScale(floor: Int): TowerScale {
        val f = (floor - 1).coerceAtLeast(0)
        return TowerScale(
            health = 1.0 + 0.12 * f,
            attack = 1.0 + 0.08 * f,
            attackSpeed = 1.0 * f,
            armor = 0.5 * f
        )
    }

    /** 第 floor 层的敌人英雄（确定性挑选，同一层永远同一敌人）。 */
    fun enemyHeroId(floor: Int): String {
        val heroes = FirstRunContent.heroes
        return heroes[(floor * 7) % heroes.size].id
    }

    /** 死亡卖金比例：已完成层数 × 10%，封顶 100%。 */
    fun sellRatio(floorsCleared: Int): Double =
        min(1.0, floorsCleared.coerceAtLeast(0) * SELL_RATIO_PER_FLOOR)

    /** 第 floor 层通关发放的里程碑奖励。 */
    fun rewardsForFloor(floor: Int): List<TowerReward> = buildList {
        if (floor == 5) add(TowerReward.SHARD)
        if (floor in 11..TOWER_FLOORS) add(TowerReward.REBIRTH_ITEM)
        if (floor == 15) add(TowerReward.AGHANIM)
        if (floor == TOWER_FLOORS) add(TowerReward.RAPIER)
    }

    /** 第 floor 层通关战利品的价值（死亡按比例卖、通关全价）。 */
    fun lootValue(floor: Int): Int = 80 + floor * 60

    /** 无头模拟一场战斗：打完为止，超时判负。 */
    fun simulate(fight: DuelState): Boolean {
        var f = fight
        var guard = 0
        while (f.won == null && guard < SIM_TICK_CAP) {
            f = DuelEngine.step(f)
            guard++
        }
        return f.won == true
    }
}

/**
 * 爬塔流程壳：入口门控 / 战斗生成 / 胜负结算。
 *
 * 与远征的关系：塔战斗复用 `DuelEngine` 与 [JourneyRules.fighter] 的玩家初始化
 * （装备/技能卡/英雄等级全部生效），敌人为无装备基础英雄 + 随层缩放。
 * 塔轮次进行中不阻塞营地其它功能（商店/仓库），只阻塞「开始远征」。
 */
class TowerFlow(val journey: JourneyRules) {

    private fun fail(s: InventoryState, e: String) = InventoryResult(s, e)

    /** 进入爬塔（营地 → 第 1 层）。 */
    fun enter(s: InventoryState): InventoryResult {
        if (s.phase != InventoryPhase.HOME) return fail(s, "仅营地可进入转生爬塔")
        if (s.progress.run != null) return fail(s, "远征进行中，先结算当前挑战")
        if (s.progress.towerRun != null) return fail(s, "爬塔轮次已在进行")
        TowerRules.lockedReason(s)?.let { return fail(s, it) }
        val hero = s.progress.activeHeroId
        if (hero !in s.progress.unlockedHeroes) return fail(s, "英雄尚未解锁")
        val run = TowerRunState(floor = 1, heroId = hero, rng = s.progress.seed)
        return InventoryResult(QuestTracker.observe(s.copy(progress = s.progress.copy(towerRun = run))))
    }

    /** 当前层敌人预览（缩放后的完整 Fighter，供 UI 展示属性）。 */
    fun previewEnemy(s: InventoryState): Fighter? {
        val t = s.progress.towerRun ?: return null
        return buildEnemy(t.floor)
    }

    private fun buildEnemy(floor: Int): Fighter {
        val base = journey.fighter(InventoryState(), TowerRules.enemyHeroId(floor))
        val scale = TowerRules.enemyScale(floor)
        return base.copy(
            hp = base.maxHp * scale.health,
            maxHp = base.maxHp * scale.health,
            attack = base.attack * scale.attack,
            speed = base.speed + scale.attackSpeed,
            armor = base.armor + scale.armor
        )
    }

    /**
     * 打当前层：无头模拟并立即结算。
     * 返回结算后的状态；[InventoryResult.error] 非空表示流程被拒（状态不变）。
     */
    fun fight(s: InventoryState): InventoryResult {
        val t = s.progress.towerRun ?: return fail(s, "没有进行中的爬塔轮次")
        if (s.phase != InventoryPhase.HOME) return fail(s, "仅营地可挑战爬塔楼层")
        val player = journey.fighter(s, t.heroId)
        val enemy = buildEnemy(t.floor)
        val rng = FirstRunContent.next(t.rng)
        val hero = FirstRunContent.hero(TowerRules.enemyHeroId(t.floor))
        val fight = DuelState(player, enemy, rng, log = listOf("转生爬塔 · 第${t.floor}层 · ${hero.name}"))
        val won = TowerRules.simulate(fight)
        return resolve(s.copy(progress = s.progress.copy(towerRun = t.copy(rng = rng))), won)
    }

    /** 胜负结算：胜利进层/发奖/更新上限；死亡卖金清轮次；第 20 层通关为胜利态。 */
    fun resolve(s: InventoryState, won: Boolean): InventoryResult {
        val t = s.progress.towerRun ?: return fail(s, "没有进行中的爬塔轮次")
        val cleared = t.clearedFloors + if (won) 1 else 0
        val loot = if (won) t.loot + TowerLoot("tower_f${t.floor}", 1, TowerRules.lootValue(t.floor)) else t.loot
        val highest = maxOf(s.progress.towerHighestFloor, cleared)
        var progress = s.progress.copy(
            towerHighestFloor = highest,
            levelCap = TowerRules.capAfterFloor(highest),
            towerRun = null
        )
        // 里程碑奖励只在**胜利通关该层**时发放（死亡不发）：魔晶/神杖绑定本轮英雄。
        if (won) {
            for (reward in TowerRules.rewardsForFloor(t.floor)) when (reward) {
                TowerReward.SHARD -> progress = progress.copy(towerShard = progress.towerShard + t.heroId)
                TowerReward.AGHANIM -> progress = progress.copy(towerAghs = progress.towerAghs + t.heroId)
                TowerReward.REBIRTH_ITEM -> progress = progress.copy(pendingRebirthItems = progress.pendingRebirthItems + "rebirth_item")
                TowerReward.RAPIER -> progress = progress.copy(towerRapier = true)
            }
        }
        val finalClear = won && t.floor >= TowerRules.TOWER_FLOORS
        val goldGain = when {
            // 通关第 20 层 = 胜利态：战利品 100% 结算，发放圣剑，不再转生。
            finalClear -> loot.sumOf { it.value }
            // 死亡：战利品按 已完成层数×10% 卖出，轮次清除，上限/奖励持久保留。
            !won -> (loot.sumOf { it.value } * TowerRules.sellRatio(cleared)).roundToInt()
            // 普通胜利：战利品留在轮次里，进下一层。
            else -> 0
        }
        if (won && !finalClear) {
            progress = progress.copy(towerRun = t.copy(floor = t.floor + 1, clearedFloors = cleared, loot = loot))
        }
        val next = s.copy(gold = s.gold + goldGain, progress = progress)
        return InventoryResult(QuestTracker.observe(next))
    }
}
