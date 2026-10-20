package com.example.douqiuqiu.content

import com.example.douqiuqiu.journey.FirstRunContent

/** 技能信息页的一行主要数值（标签 + 已格式化文本）。 */
data class HeroSkillValue(val label: String, val value: String)

/**
 * 英雄技能的信息视图（0.1.5 第五节「技能与装备信息」）。
 *
 * 所有数值都从共享内容表读取：旧英雄取 [com.example.douqiuqiu.journey.HeroSpec]，
 * 新英雄取 [HeroContent]。UI 只负责排版，不在界面层复制引擎公式。
 * 「当前值」由调用方传入当前战斗快照（剩余冷却、当前魔法），本表只给基础值。
 */
data class HeroSkillView(
    val heroId: String,
    val skillName: String,
    val manaCost: Int,
    val baseCooldownSec: Double,
    val trigger: String,
    val values: List<HeroSkillValue>,
    val note: String
)

object HeroSkillInfo {

    private fun n(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    fun of(heroId: String): HeroSkillView {
        val spec = FirstRunContent.hero(heroId)
        val cfg = HeroContent.skills[heroId]
        val (name, values, note) = when (heroId) {
            "juggernaut" -> HeroContent.legacy(heroId).let { c -> Triple("无敌斩",
                listOf(HeroSkillValue("无敌时长", "${n(c.durationSec)} 秒"), HeroSkillValue("攻速", "+${n(c.attackSpeedPercent)}%"), HeroSkillValue("形态", "完整普通攻击斩击")),
                "施放瞬间强驱散自身并获得无敌；每一斩可触发暴击、碎颅锤等普通攻击命中特效。")
            }
            "skywrath" -> HeroContent.legacy(heroId).let { c -> Triple("神秘之耀",
                listOf(HeroSkillValue("打击次数", "${c.hits} 次"), HeroSkillValue("单次伤害", "${n(c.damagePerHit)} 魔法"), HeroSkillValue("伤害类型", "魔法")),
                "每 0.25 秒落下一道强化光柱，命中当前目标。")
            }
            "phantom_assassin" -> Triple("恩赐解脱",
                listOf(HeroSkillValue("暴击窗口", "4 秒"), HeroSkillValue("触发方式", "窗口内普攻")),
                "窗口期内普攻按暴击结算，结束后重新进入冷却。")
            "abaddon" -> HeroContent.legacy(heroId).let { c -> Triple("无光之盾",
                listOf(HeroSkillValue("护盾", n(c.shield)), HeroSkillValue("反伤", "盾量的 30%")),
                "耗蓝 ${spec.manaCost}；强驱散自身后获得护盾，护盾由护盾账本统一结算。")
            }
            "necrophos" -> Triple("幽魂护罩",
                listOf(HeroSkillValue("持续", "3 秒"), HeroSkillValue("减伤", "物理免疫、法伤减半")),
                "持续期间免疫物理伤害。")
            "viper" -> Triple("蝮蛇突袭",
                listOf(HeroSkillValue("剧毒层数", "1 层"), HeroSkillValue("破坏/减速", "3 秒")),
                "施加一层剧毒，并使目标被破坏与减速。")
            "anti_mage" -> cfg?.let { c ->
                Triple("法力虚空",
                    listOf(
                        HeroSkillValue("基础伤害", n(c.ultManaVoidBase)),
                        HeroSkillValue("削蓝系数", n(c.ultManaVoidCoefficient)),
                        HeroSkillValue("眩晕", "${n(c.ultManaVoidStunSec)} 秒"),
                        HeroSkillValue("被动削蓝", "每秒 ${n(c.manaBurnPercent * 100)}% 目标最大魔法")
                    ),
                    "伤害 = 基础值 + 本场已累计削蓝 × 系数；被动削蓝每 ${n(c.manaBurnIntervalSec)} 秒至多一次。")
            } ?: Triple("法力虚空", emptyList(), "")
            "axe" -> cfg?.let { c ->
                Triple("狂战士之吼",
                    listOf(
                        HeroSkillValue("嘲讽", "${n(c.tauntDurationSec)} 秒"),
                        HeroSkillValue("自身护甲", "+${n(c.selfArmor)}"),
                        HeroSkillValue("反击螺旋", "每 ${c.spinEveryHits} 次受击"),
                        HeroSkillValue("螺旋伤害", "${n(c.spinBaseDamage)} + 护甲 × ${n(c.spinArmorCoefficient)}")
                    ),
                    "嘲讽为普通驱散可清的控制；反击螺旋伤害为纯粹伤害。")
            } ?: Triple("狂战士之吼", emptyList(), "")
            "faceless_void" -> cfg?.let { c ->
                Triple("时间结界",
                    listOf(
                        HeroSkillValue("时停", "${n(c.timeStopDurationSec)} 秒"),
                        HeroSkillValue("时间锁定概率", "${n(c.timeLockChance * 100)}%"),
                        HeroSkillValue("锁定眩晕", "${n(c.timeLockStunSec)} 秒"),
                        HeroSkillValue("延长敌方冷却", "${n(c.timeLockCdAddSec)} 秒")
                    ),
                    "时停目标无法普攻、施法或使用物品；锁定延长敌方大招冷却只在敌方大招冷却中生效。")
            } ?: Triple("时间结界", emptyList(), "")
            "legion_commander" -> cfg?.let { c ->
                Triple("决斗",
                    listOf(
                        HeroSkillValue("决斗嘲讽", "${n(c.duelTauntSec)} 秒"),
                        HeroSkillValue("决斗攻速", "+${n(c.duelAttackSpeedPct)}"),
                        HeroSkillValue("追加攻击概率", "${n(c.dualStrikeChance * 100)}%"),
                        HeroSkillValue("追加治疗", n(c.dualStrikeHeal))
                    ),
                    "追加攻击不递归触发额外攻击；决斗期间双方强制互相攻击。")
            } ?: Triple("决斗", emptyList(), "")
            else -> Triple("大招", emptyList(), "")
        }
        return HeroSkillView(
            heroId = heroId,
            skillName = name,
            manaCost = spec.manaCost,
            baseCooldownSec = spec.cooldown,
            trigger = "冷却结束且魔法足够时自动施放",
            values = values,
            note = note
        )
    }

    /** 当前状态的一行摘要（战斗快照存在时使用，否则显示基础值）。 */
    fun currentLine(heroId: String, mana: Double?, maxMana: Double?, cooldownRemaining: Double?): String {
        val view = of(heroId)
        val manaText = if (mana != null && maxMana != null) "魔法 ${mana.toInt()}/${maxMana.toInt()}" else "耗蓝 ${view.manaCost}"
        val cdText = when {
            cooldownRemaining == null -> "冷却 ${n(view.baseCooldownSec)} 秒"
            cooldownRemaining > 0.00001 -> "冷却剩余 ${((cooldownRemaining * 10).toInt() / 10.0)} 秒"
            else -> "冷却已就绪"
        }
        return "$manaText · $cdText"
    }
}
