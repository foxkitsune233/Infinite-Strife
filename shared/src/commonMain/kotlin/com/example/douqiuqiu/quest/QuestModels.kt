package com.example.douqiuqiu.quest

/**
 * 任务系统数据模型（0.1.6）。
 *
 * 定位：新的挑战节点与引导层。英雄解锁经任务发放；技能碎片与两种护芯
 * 也由任务产出。任务内容表在 [QuestContent] 中配置，本文件只定义结构。
 */

/** 任务分类。引导=一次性解锁链；挑战=一次性目标；转化=可重复的材料兑换。 */
enum class QuestKind { GUIDE, CHALLENGE, CONVERSION }

/** 目标类型。全部挂在现有结算函数上，由 [QuestTracker] 推进。 */
enum class ObjectiveType {
    WIN_BATTLES,        // 战斗胜利累计场次
    CLEAR_COUNT,        // 通关次数
    CLEAR_DIFFICULTY,   // 指定及以上难度通关次数
    EXTRACT_COUNT,      // 安全撤离次数
    HERO_WINS,          // 指定英雄胜利场次
    HERO_LEVEL,         // 任意（或指定）英雄达到等级
    MATERIAL_GAINED,    // 指定材料累计获得数
    EVENT_CHOICES,      // 事件选项选择次数
    SHOP_PURCHASE,      // 商店购买次数
    CRAFT_COUNT,        // 装备合成次数
    CONSUMABLE_USED,    // 战斗内使用恢复品次数
    GUARDED_DEATH       // 携带受保护物品死亡并保留的次数
}

/** 单个任务的完成目标。 */
data class QuestObjective(
    val type: ObjectiveType,
    val target: Int,
    val heroId: String? = null,     // HERO_WINS / HERO_LEVEL 专用
    val difficulty: Int = 0,        // CLEAR_DIFFICULTY 专用，0=简单起
    val materialId: String? = null  // MATERIAL_GAINED 专用
)

/** 奖励类型。 */
enum class RewardType {
    GOLD,           // 营地金币
    HERO_UNLOCK,    // 解锁英雄（替代原金币购买通道）
    MATERIAL,       // 指定材料（如 skill_shard）
    GUARD_CORE,     // 装备护芯 / 技能护芯（由 materialId 区分）
    HERO_LEVEL_UP   // 直接提升一级指定英雄等级（消耗金币走 HeroLevelRules，此处仅作目标奖励）
}

data class QuestReward(
    val type: RewardType,
    val amount: Int = 0,
    val heroId: String? = null,
    val materialId: String? = null
)

/** 一条任务。内容全部配置化，逻辑零硬编码。 */
data class Quest(
    val id: String,
    val name: String,
    val description: String,
    val kind: QuestKind,
    val objective: QuestObjective,
    val rewards: List<QuestReward>,
    val prerequisiteQuestIds: List<String> = emptyList(),
    val minClears: Int = 0,
    val repeatable: Boolean = false,
    /** CONVERSION 专用：上缴材料 definitionId → 数量。发放前从仓库原子扣除。 */
    val cost: Map<String, Int> = emptyMap()
)

/** 推进事件。每个事件对应现有代码里一个已存在的结算点。 */
sealed class QuestEvent {
    /** 战斗胜利。 [heroId] 出战英雄，[difficulty] 本场难度。 */
    data class BattleWon(val heroId: String, val difficulty: Int) : QuestEvent()

    /** 远征通关（5 战全胜）。 */
    data class RunCleared(val heroId: String, val difficulty: Int) : QuestEvent()

    /** 安全撤离。 */
    data object Extracted : QuestEvent()

    /** 事件选项已选择。 */
    data object EventChosen : QuestEvent()

    /** 商店购买成功。 */
    data object ShopPurchase : QuestEvent()

    /** 装备合成成功。 */
    data object Crafted : QuestEvent()

    /** 战斗内使用恢复品。 */
    data object ConsumableUsed : QuestEvent()

    /** 材料入库。 [materialId] 定义 ID，[count] 本次数量。 */
    data class MaterialGained(val materialId: String, val count: Int) : QuestEvent()

    /** 英雄等级发生变化（升级成功后由调用方发出，供 HERO_LEVEL 目标检查）。 */
    data class HeroLevelChanged(val heroId: String, val level: Int) : QuestEvent()

    /** 携带受保护物品死亡并成功保留。 */
    data object GuardedDeath : QuestEvent()
}
