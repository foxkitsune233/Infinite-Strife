package com.example.douqiuqiu.tower

/**
 * 0.1.7 转生爬塔数据模型（shared，Godot 可直接继承）。
 *
 * 塔轮次是**营地之外的独立进度**：`CampaignProgress.towerRun != null` 即「在塔中」。
 * 设计稿：`docs/superpowers/plans/2026-10-22-017-open-hero-level-cap.md` §2。
 */

/** 爬塔轮次状态（随存档持久化，重进应用可继续）。 */
data class TowerRunState(
    /** 当前要挑战的层（1..20）。 */
    val floor: Int = 1,
    /** 本轮使用的英雄（魔晶/神杖绑定该英雄）。 */
    val heroId: String = "juggernaut",
    /** 战斗 RNG 种子（由 progress.seed 起步，逐场推进，保证可复现）。 */
    val rng: Long = 1729,
    /** 本轮已通关层数（死亡卖金比例与历史最高层都取此值）。 */
    val clearedFloors: Int = 0,
    /** 本轮拾取的战利品（死亡按比例卖出、通关按 100% 结算）。 */
    val loot: List<TowerLoot> = emptyList(),
    /** 连战生命；null 表示尚未进行第一场，下一战按常规满血开局。 */
    val currentHp: Double? = null,
    /** 应急补给只在下一场生效，战斗结束后清除。 */
    val pendingSupplyRegen: Boolean = false,
    /** 锻造遗迹的本轮主属性加成；死亡或通关后随 towerRun 清除。 */
    val towerPrimaryBonus: Int = 0
)

/** 塔内战利品：结算为金币的携带物（死亡卖 ratio、通关全价）。 */
data class TowerLoot(
    val definitionId: String,
    val star: Int = 1,
    val value: Int
)

/** 敌人随层缩放（health/attack 为乘数，attackSpeed/armor 为加数，与远征 ChallengeBalance 同构）。 */
data class TowerScale(
    val health: Double,
    val attack: Double,
    val attackSpeed: Double,
    val armor: Double
)

/** 楼层里程碑奖励类型（设计稿 §2.3）。 */
enum class TowerReward {
    /** 第 5 层：魔晶（绑定英雄的被动/技能强化）。 */
    SHARD,
    /** 第 11–20 层每层：转生道具（使用后一张技能卡星级直满）。 */
    REBIRTH_ITEM,
    /** 第 15 层：阿哈利姆神杖（绑定英雄的主动技能）。 */
    AGHANIM,
    /** 第 20 层通关：圣剑（纪念品装备，不回收转生）。 */
    RAPIER
}
