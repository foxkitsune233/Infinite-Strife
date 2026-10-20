package com.example.douqiuqiu.content

import com.example.douqiuqiu.status.DispelClass

/**
 * 0.1.5 新增四英雄的技能常数集中表。
 *
 * 需求要求所有未定常数统一放在内容配置，不散落在引擎分支。所有数值都从这里读取，
 * 后续 P6 平衡采样后只改本表，不删失败样本。
 */
data class HeroSkillConfig(
    // 敌法师（anti_mage）
    val manaBurnPercent: Double = 0.0,
    val manaBurnIntervalSec: Double = 1.0,
    val ultManaVoidBase: Double = 0.0,
    val ultManaVoidCoefficient: Double = 0.0,
    val ultManaVoidStunSec: Double = 0.0,
    // 斧王（axe）
    val spinEveryHits: Int = 0,
    val spinBaseDamage: Double = 0.0,
    val spinArmorCoefficient: Double = 0.0,
    val tauntDurationSec: Double = 0.0,
    val selfArmor: Double = 0.0,
    // 虚空假面（faceless_void）
    val timeLockChance: Double = 0.0,
    val timeLockStunSec: Double = 0.0,
    val timeLockCdAddSec: Double = 0.0,
    val timeStopDurationSec: Double = 0.0,
    // 军团指挥官（legion_commander）
    val dualStrikeChance: Double = 0.0,
    val dualStrikeHeal: Double = 0.0,
    val duelTauntSec: Double = 0.0,
    val duelAttackSpeedPct: Double = 0.0
)

object HeroContent {
    /** 旧英雄的可调大招常数；引擎与信息页共同读取，避免平衡数值分叉。 */
    data class LegacySkillConfig(
        val durationSec: Double = 0.0,
        val attackSpeedPercent: Double = 0.0,
        val hits: Int = 0,
        val damagePerHit: Double = 0.0,
        val shield: Double = 0.0
    )

    val legacySkills = mapOf(
        "juggernaut" to LegacySkillConfig(durationSec = 2.0, attackSpeedPercent = 200.0),
        "skywrath" to LegacySkillConfig(hits = 8, damagePerHit = 60.0),
        "abaddon" to LegacySkillConfig(shield = 180.0)
    )

    val skills = mapOf(
        "anti_mage" to HeroSkillConfig(
            manaBurnPercent = 0.02,
            manaBurnIntervalSec = 1.0,
            ultManaVoidBase = 100.0,
            ultManaVoidCoefficient = 0.6,
            ultManaVoidStunSec = 0.2
        ),
        "axe" to HeroSkillConfig(
            spinEveryHits = 4,
            spinBaseDamage = 55.0,
            spinArmorCoefficient = 1.2,
            tauntDurationSec = 2.5,
            selfArmor = 12.0
        ),
        "faceless_void" to HeroSkillConfig(
            timeLockChance = 0.18,
            timeLockStunSec = 0.35,
            timeLockCdAddSec = 1.0,
            timeStopDurationSec = 3.0
        ),
        "legion_commander" to HeroSkillConfig(
            dualStrikeChance = 0.25,
            dualStrikeHeal = 22.0,
            duelTauntSec = 4.0,
            duelAttackSpeedPct = 60.0
        )
    )

    fun skill(id: String): HeroSkillConfig = skills.getValue(id)
    fun legacy(id: String): LegacySkillConfig = legacySkills.getValue(id)

    /**
     * 英雄技能施加的控制来源 → 驱散等级。
     *
     * 存档校验（TimedControls.validateSources）用本表确认战斗快照里的控制确实来自
     * 英雄技能而非伪造；键必须与 DuelEngine 里 TimedControls.apply 的 sourceId 一致。
     */
    val controlSources: Map<String, DispelClass> = mapOf(
        "hero:anti_mage:ult" to DispelClass.BASIC,
        "hero:faceless_void:timelock" to DispelClass.BASIC,
        "hero:axe:ult" to DispelClass.BASIC,
        "hero:legion_commander:ult" to DispelClass.BASIC,
        "hero:faceless_void:ult" to DispelClass.UNDISPELLABLE
    )

    /** 0.1.5 新增英雄 id 集合，用于解锁与敌方构建去重。 */
    val newHeroIds = skills.keys
}
