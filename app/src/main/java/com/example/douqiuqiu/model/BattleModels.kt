package com.example.douqiuqiu.model

import java.io.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** 战斗阵营 */
enum class Side {
    PLAYER, ENEMY;

    fun opposite(): Side = if (this == PLAYER) ENEMY else PLAYER
}

/**
 * 战斗事件类型。
 * 引擎只负责产出事件序列，BattleView 按类型播动画，两边彻底解耦。
 */
enum class BattleEventType {
    BATTLE_START,   // 开场
    HIT,            // 普攻 / 技能命中（value = 扣掉的血）
    CRIT,           // 暴击命中
    MISS,           // 被闪避
    SKILL,         // 技能释放横幅（不一定造成伤害）
    SHIELD,         // 获得护盾（value = 护盾量）
    SHIELD_BREAK,   // 护盾被击碎
    HEAL,           // 回血
    LIFESTEAL,      // 吸血
    STUN,           // 被眩晕（value = tick 数）
    DOT,            // 持续伤害跳字
    DEATH,          // 一方阵亡
    BATTLE_END      // 战斗结束
}

/**
 * 一条战斗事件。
 *
 * 说明：value 对伤害类事件表示「本次总伤害」；shieldPart 是其中被护盾吸收的部分，
 * 剩余部分才扣血，这样 UI 的血条 / 护盾条能和引擎完全一致。
 */
data class BattleEvent(
    val tick: Int,
    val type: BattleEventType,
    val source: Side,
    val target: Side,
    val value: Int = 0,
    val shieldPart: Int = 0,
    val skillName: String = "",
    val text: String = ""
) : Serializable

/** 技能模板（配置表 battle.json） */
data class SkillTemplate(
    val id: String,
    val hero: String,
    val slot: String,        // ult = 大招（按 CD 放） / passive = 被动（按概率触发）
    val name: String,
    val kind: String,
    val chance: Int = 0,
    val hits: Int = 1,
    val dmgPct: Int = 0,
    val healPct: Int = 0,
    val shieldPct: Int = 0,
    val buffValue: Int = 0,
    val buffTicks: Int = 0,
    val thresholdPct: Int = 0,
    val selfDamagePct: Int = 0,
    val stunTicks: Int = 0,
    val dotPct: Double = 0.0,
    val critDmgPct: Int = 0,
    val maxBonusPct: Int = 0,
    val cooldownTicks: Int = 0,
    val text: String = ""
) : Serializable

/** 技能运行时：模板 + 当前冷却 */
class SkillRuntime(val tpl: SkillTemplate, cooldownScale: Double = 1.0) : Serializable {
    val cooldownTicks: Int =
        (tpl.cooldownTicks * cooldownScale).roundToInt().coerceAtLeast(10)

    /** 初始给一半冷却，避免双方大招都在第 1  tick 砸出来 */
    var cdLeft: Int = (cooldownTicks * 0.5).roundToInt()
}

/** 持续伤害（挂在受害者身上） */
class Dot(val perTick: Int, var ticksLeft: Int, val name: String) : Serializable

/**
 * 战斗单元：引擎内部的可变状态。
 *
 * 属性分「基础值」和「临时值」：
 *  - baseAttack / baseIntervalTicks：构建时定死（英雄 + 武器 + 流派）
 *  - asBonusPct / tempAtkPct / tempEvasion：战斗中动态变化（被动、buff）
 */
class Combatant(
    val side: Side,
    val id: String,
    val name: String,
    val emoji: String,
    val colorInt: Int,
    var hp: Int,
    val maxHp: Int,
    var shield: Int,
    val maxShield: Int,
    var attack: Int,
    val baseAttack: Int,
    val baseIntervalTicks: Int,
    val armor: Double,
    val magicResist: Double,
    var crit: Int,
    var critDmg: Int,
    var evasion: Int,
    val lifesteal: Int,
    val skills: List<SkillRuntime>,
    val ultDmgScale: Double = 1.0
) : Serializable {

    var stunTicks: Int = 0
    var nextAttackIn: Int = baseIntervalTicks

    /** 动态攻速加成（哈斯卡被动等），不影响基础值 */
    var asBonusPct: Int = 0

    /** 临时闪避 / 临时攻击，到期自动清 */
    var tempEvasion: Int = 0
    var tempEvasionTicks: Int = 0
    var tempAtkPct: Int = 0
    var tempAtkTicks: Int = 0

    val dots: MutableList<Dot> = mutableListOf()

    val alive: Boolean get() = hp > 0

    fun hpRatio(): Double = if (maxHp <= 0) 0.0 else hp.toDouble() / maxHp.toDouble()

    /** 当前攻速对应的攻击间隔（tick），至少 2 tick */
    fun intervalTicks(): Int =
        (baseIntervalTicks * 100.0 / (100.0 + asBonusPct)).roundToInt().coerceAtLeast(2)

    /** 每次 tick 刷新有效攻击力 */
    fun refreshAttack() {
        attack = if (tempAtkTicks > 0) {
            (baseAttack * (1.0 + tempAtkPct / 100.0)).roundToInt()
        } else {
            baseAttack
        }
    }

    /** 每秒攻击次数，仅用于「战力」展示 */
    fun attacksPerSecond(tickMs: Int): Double = 1000.0 / (baseIntervalTicks * tickMs)
}

/** AI 敌人在 roundCoefs 之上再乘的属性系数（配置表 battle.json） */
data class EnemyScale(
    val hp: Double = 1.0,
    val attack: Double = 1.0,
    val armor: Double = 0.6,
    val magicResist: Double = 1.0,
    val attackSpeed: Double = 1.0
) : Serializable

/** 战斗全局参数（配置表 battle.json） */
data class BattleConfig(
    val tickMs: Int = 100,
    val maxTicks: Int = 900,
    val armorK: Double = 0.06,
    val damageVariance: Double = 0.1,
    val baseCrit: Int = 5,
    val defaultCritDmg: Int = 200,
    val shieldPerStack: Int = 60,
    val enemyScale: EnemyScale = EnemyScale(),
    val skills: List<SkillTemplate> = emptyList()
) : Serializable {

    fun skillsOf(heroId: String, slot: String): List<SkillTemplate> =
        skills.filter { it.hero == heroId && it.slot == slot }

    /** 抗性减伤：k × 抗性 / (1 + k × |抗性|)，护甲和魔抗同一套公式 */
    fun resistanceReduction(resist: Double): Double {
        val r = abs(resist)
        return armorK * r / (1.0 + armorK * r)
    }
}

/** 引擎输出：一场战斗的完整结果 */
data class BattleSimResult(
    val win: Boolean,
    val timeout: Boolean,
    val ticks: Int,
    val events: List<BattleEvent>,
    val playerHpLeft: Int,
    val enemyHpLeft: Int,
    val damageDealt: Int,
    val damageTaken: Int,
    val playerPower: Int,
    val enemyPower: Int
) : Serializable
