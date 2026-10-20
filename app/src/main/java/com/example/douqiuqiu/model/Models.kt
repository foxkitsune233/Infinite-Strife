package com.example.douqiuqiu.model

/** 英雄模板（配置表 heroes.json） */
data class HeroTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val trait: String,      // 力 / 敏 / 智
    val color: String,      // 主题色，用于卡片
    val hp: Int,
    val attack: Int,
    val attackSpeed: Double,
    val armor: Int,
    val magicResist: Int,
    val ult: String,        // 大招描述
    val passive: String     // 被动描述
)

/** 流派效果（某一星级下的数值加成，缺省为 0） */
data class SchoolEffect(
    val atkSpeedPct: Int = 0,
    val attack: Int = 0,
    val ultPct: Int = 0,
    val manaRegen: Int = 0,
    val magicResistPct: Int = 0,
    val hpPct: Int = 0,
    val shieldStack: Int = 0,
    val critDmgPct: Int = 0,
    val crit: Int = 0,
    val physResistPct: Int = 0,
    val evasion: Int = 0
)

/** 流派模板（配置表 schools.json），effects 下标 0..3 对应 1..4 星 */
data class SchoolTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val effects: List<SchoolEffect>
)

/** 武器模板（配置表 weapons.json） */
data class WeaponStat(
    val all: Int = 0,
    val str: Int = 0,
    val agi: Int = 0,
    val int: Int = 0,
    val attack: Int = 0,
    val hp: Int = 0,
    val crit: Int = 0,
    val evasion: Int = 0,
    val lifesteal: Int = 0,
    val atkSpeedPct: Int = 0
)

data class WeaponTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val quality: String,    // 白 / 蓝 / 紫 / 金
    val value: Int,         // 价值分（计算携带赌注）
    val price: Int,
    val stats: WeaponStat
)

/** 技能卡模板（配置表 skill_cards.json） */
data class SkillCardTemplate(
    val id: String,
    val school: String,
    val quality: String,    // 蓝 / 紫 / 金
    val exp: Int,           // 提供的流派经验
    val price: Int,
    val name: String,
    val desc: String,
    val requireStar: Int = 0
)

/** 全局平衡参数（配置表 balance.json） */
data class BalanceConfig(
    val initialGold: Int = 300,
    val incomePerRound: Int = 250,
    val interestPer100: Int = 10,
    val interestCap: Int = 100,
    val winBase: Int = 50,
    val winStreakBonus: Int = 20,
    val winStreakCap: Int = 100,
    val losePerHp: Int = 15,
    val loseStreakBonus: Int = 15,
    val loseStreakCap: Int = 60,
    val intelCost: Int = 80,
    val maxIntelPerRound: Int = 3,
    val cheerPerBattle: Int = 3,
    val schoolExpThresholds: List<Int> = listOf(4, 10, 20, 40),
    val starHpBonus: List<Int> = listOf(80, 120, 200, 400),
    val initialPlayerHp: Int = 100,
    val courierDamageBase: Int = 6
)

/** AI 难度曲线（配置表 difficulty.json） */
data class DifficultyConfig(
    val baseCoef: Double = 1.0,
    val growthPerRound: Double = 0.16,
    val hpCoef: Double = 1.0,
    val attackCoef: Double = 1.0,
    val armorCoef: Double = 0.6,
    val maxRound: Int = 5,
    /** v0.3：固定 5 局，每局一个属性系数（B1~B5） */
    val roundCoefs: List<Double> = listOf(1.0, 1.25, 1.55, 1.90, 2.35)
)

// ==================== v0.3：5 局固定地图 ====================

/** 节点类型：战斗 / 随机事件 / 返回或休息 */
enum class NodeKind { BATTLE, EVENT, REST_EXIT }

/** run 结局 */
enum class RunEnding { NONE, CLEAR, DEAD, EXTRACT }

/** 地图节点（配置表 run_map.json） */
data class RunNode(
    val index: Int,
    val kind: NodeKind,
    val label: String,
    val emoji: String,
    /** 战斗节点才有：第几场（1..5） */
    val battleRound: Int = 0,
    /** 中间节点才有：第几个节点（1..4） */
    val nodeOrder: Int = 0
)

/** 返回 / 休息节点的参数（配置表 run_map.json 的 restNode） */
data class RestNodeConfig(
    val healPct: Int = 40,
    val bonusGold: Int = 100,
    val clearDebuff: Boolean = true,
    val clearBonusMultiplier: Double = 1.5
)

/** 三种结局的文案（配置表 run_map.json 的 endings） */
data class EndingTexts(
    val clearTitle: String = "通关",
    val clearDesc: String = "",
    val deadTitle: String = "阵亡",
    val deadDesc: String = "",
    val extractTitle: String = "安全撤离",
    val extractDesc: String = ""
)

/** 地图配置（配置表 run_map.json） */
data class RunMapConfig(
    val totalBattles: Int = 5,
    val nodes: List<RunNode> = emptyList(),
    /** 每场战斗胜利后回复的最大生命百分比 */
    val battleVictoryHealPct: Int = 15,
    val restNode: RestNodeConfig = RestNodeConfig(),
    val endings: EndingTexts = EndingTexts()
)

/** 随机事件模板（配置表 events.json） */
data class GameEventTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    /** 效果类型：gold_add / gold_lose / hp_heal / hp_lose / weapon_gain /
     *  card_gain / weapon_lose / school_exp / buff_atk / buff_armor /
     *  gamble_gold / reveal_next */
    val kind: String,
    val value: Int = 0,
    /** 有值时表示在 value..valueMax 间随机 */
    val valueMax: Int = 0,
    val weight: Int = 1,
    /** 允许出现的节点序号（1..4） */
    val nodes: List<Int> = emptyList(),
    /** 结果文案模板，{v} 替换为实际数值 */
    val text: String = ""
)

/** 事件结算结果（供 UI 展示） */
data class EventOutcome(
    val event: GameEventTemplate,
    val text: String
)

/** 一轮 run 的完整状态（M2 起通过 Intent 传给战斗页，需要可序列化） */
data class RunState(
    val heroId: String,
    var gold: Int = 300,
    var hp: Int = 1000,
    var maxHp: Int = 1000,
    /** 当前所处节点下标（0..8） */
    var currentNode: Int = 0,
    var battlesWon: Int = 0,
    /** 携带进场的卡 / 武器（死亡会掉落） */
    val carriedCards: MutableList<String> = mutableListOf(),
    val carriedWeapons: MutableList<String> = mutableListOf(),
    /** 已入袋的战利品（撤离 / 通关时结算，不会掉落） */
    val securedLoot: MutableList<String> = mutableListOf(),
    /** 下一场战斗的临时增益（事件给予） */
    var buffAtkPct: Int = 0,
    var buffArmor: Int = 0,
    /** 是否在节点 ③ 选择了「休息」 */
    var rested: Boolean = false,
    var finished: Boolean = false,
    var ending: RunEnding = RunEnding.NONE
) : java.io.Serializable
