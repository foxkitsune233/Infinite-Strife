package com.example.douqiuqiu.ui.components

import android.content.Context
import android.widget.LinearLayout
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.journey.FighterEffects
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.status.*
import kotlin.math.ceil

/** Read-only explanation of the same gates used by DuelEngine.cast. */
internal class HeroSkillHud(context: Context) : LinearLayout(context) {
    private val heading = GameUi.label(context, "", 12, GameUi.gold, true)
    private val detail = GameUi.label(context, "", 12)

    init {
        orientation = VERTICAL
        val p = GameUi.dp(context, 5)
        setPadding(0, p, 0, p)
        addView(heading)
        addView(detail)
    }

    fun bind(f: Fighter) {
        val hero = FirstRunContent.hero(f.heroId)
        val name = com.example.douqiuqiu.content.HeroSkillInfo.of(f.heroId).skillName
        val control = TimedControls.current(f)
        val blocked = ActionPermission.check(control.copy(
            dead = f.hp <= 0 || control.dead,
            silenced = control.silenced || EquipmentCombat.active(f.equipment, "mask_madness")),
            CombatAction.HERO_SKILL, allowStunnedHeroSkill = f.heroId == "abaddon").blockedBy
        val reasons = blocked.map { when (it) {
            ActionBlock.DEAD -> "已阵亡"
            ActionBlock.BANISHED -> "放逐中"
            ActionBlock.STUNNED -> "眩晕中"
            ActionBlock.HEXED -> "妖术中"
            ActionBlock.SILENCED -> "沉默中"
            ActionBlock.MUTED -> "物品禁用"
            ActionBlock.DISARMED -> "缴械中"
            ActionBlock.FROZEN -> "冰冻中"
            ActionBlock.TIME_STOPPED -> "时停中"
        } }.toMutableList()
        if (f.mana < hero.manaCost) reasons += "魔法不足（差${ceil(hero.manaCost - f.mana).toInt()}）"
        val cooling = f.ultIn > .00001
        val cooldown = if (cooling) "冷却 ${ceil(f.ultIn * 10) / 10.0}秒" else "冷却已就绪"
        val ready = !cooling && reasons.isEmpty() && FighterEffects.permission(f, CombatAction.HERO_SKILL)
        if (!ready && !cooling && reasons.isEmpty()) reasons += "暂不可释放"
        heading.text = "$name · 耗蓝 ${hero.manaCost} MP · 魔法 ${f.mana.toInt()}/${f.maxMana.toInt()}"
        detail.text = "$cooldown\n${if (ready) "已就绪 · 自动施放" else reasons.joinToString(" · ").ifEmpty { "等待冷却" }}"
        detail.setTextColor(if (ready) GameUi.green else if (reasons.isNotEmpty()) GameUi.danger else GameUi.ink)
        contentDescription = "大招，${heading.text}，${detail.text}"
    }
}
