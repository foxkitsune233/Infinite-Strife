package com.example.douqiuqiu.engine

import android.graphics.Color
import com.example.douqiuqiu.model.BattleConfig
import com.example.douqiuqiu.model.Combatant
import com.example.douqiuqiu.model.HeroTemplate
import com.example.douqiuqiu.model.RunState
import com.example.douqiuqiu.model.SchoolTemplate
import com.example.douqiuqiu.model.SkillCardTemplate
import com.example.douqiuqiu.model.SkillRuntime
import com.example.douqiuqiu.model.Side
import com.example.douqiuqiu.model.WeaponTemplate
import kotlin.math.roundToInt

/**
 * M2：把「英雄 + 携带物 + 事件增益」组装成战斗单元。
 *
 * 这里是纯数值折算，不碰 UI：
 *  - 武器属性按点数值折算成攻/血/甲/攻速/暴击/闪避/吸血
 *  - 携带的技能卡按流派经验折算星级，取最高流派的效果（M3 商店接入后依然有效）
 *  - 随机事件给的临时增益（buffAtkPct / buffArmor）只作用于下一场
 */
object Loadout {

    /** 组装需要的原材料 */
    data class Input(
        val hero: HeroTemplate,
        val weapons: List<WeaponTemplate>,
        val carriedWeaponIds: List<String>,
        val cards: List<SkillCardTemplate>,
        val carriedCardIds: List<String>,
        val schools: List<SchoolTemplate>,
        val expThresholds: List<Int>,
        val buffAtkPct: Int = 0,
        val buffArmor: Int = 0,
        val shieldPerStack: Int = 60,
        val baseCrit: Int = 5,
        val baseCritDmg: Int = 200
    )

    /** 折算后的最终属性 */
    data class Stats(
        val maxHp: Int,
        val attack: Int,
        val attackSpeedPct: Int,
        val armor: Double,
        val magicResist: Double,
        val crit: Int,
        val critDmg: Int,
        val evasion: Int,
        val lifesteal: Int,
        val shield: Int,
        val schoolName: String,
        val schoolStar: Int,
        val ultDmgScale: Double,
        val ultCdScale: Double
    )

    fun compute(inp: Input): Stats {
        var hp = inp.hero.hp.toDouble()
        var attack = inp.hero.attack.toDouble()
        var asPct = 0
        var armor = inp.hero.armor.toDouble()
        var mr = inp.hero.magicResist.toDouble()
        var crit = inp.baseCrit
        var critDmg = inp.baseCritDmg
        var evasion = 0
        var lifesteal = 0
        var shield = 0

        // ---- 武器 ----
        for (id in inp.carriedWeaponIds) {
            val s = inp.weapons.firstOrNull { it.id == id }?.stats ?: continue
            // 全属性：什么都加一点
            attack += s.all * 2 + s.str + s.agi + s.attack
            hp += s.all * 12 + s.str * 20 + s.hp
            armor += s.all * 0.5
            asPct += s.all * 2 + s.agi + s.atkSpeedPct
            mr += s.int * 0.5 + s.all * 0.5
            crit += s.crit
            evasion += s.evasion
            lifesteal += s.lifesteal
        }

        // ---- 流派星级（由携带的技能卡经验推导） ----
        var bestStar = 0
        var bestExp = 0
        var best: SchoolTemplate? = null
        for (sc in inp.schools) {
            val exp = inp.carriedCardIds.sumOf { cid ->
                val c = inp.cards.firstOrNull { it.id == cid }
                if (c != null && c.school == sc.id) c.exp else 0
            }
            val star = inp.expThresholds.count { exp >= it }
            if (star > bestStar || (star == bestStar && exp > bestExp)) {
                bestStar = star
                bestExp = exp
                best = sc
            }
        }
        var ultDmgScale = 1.0
        var ultCdScale = 1.0
        if (best != null && bestStar > 0) {
            val e = best.effects.getOrNull(bestStar - 1)
            if (e != null) {
                attack += e.attack
                asPct += e.atkSpeedPct
                armor += e.physResistPct
                mr += e.magicResistPct
                crit += e.crit
                critDmg += e.critDmgPct
                evasion += e.evasion
                shield += e.shieldStack * inp.shieldPerStack
                hp *= 1.0 + e.hpPct / 100.0
                ultDmgScale = 1.0 + e.ultPct / 100.0
                ultCdScale = (1.0 - e.manaRegen * 0.02).coerceAtLeast(0.4)
            }
        }

        // ---- 随机事件的临时增益（仅下一场） ----
        attack *= 1.0 + inp.buffAtkPct / 100.0
        armor += inp.buffArmor

        return Stats(
            maxHp = hp.roundToInt().coerceAtLeast(1),
            attack = attack.roundToInt().coerceAtLeast(1),
            attackSpeedPct = asPct,
            armor = armor,
            magicResist = mr,
            crit = crit,
            critDmg = critDmg,
            evasion = evasion,
            lifesteal = lifesteal,
            shield = shield,
            schoolName = if (bestStar > 0) best?.name ?: "" else "",
            schoolStar = bestStar,
            ultDmgScale = ultDmgScale,
            ultCdScale = ultCdScale
        )
    }

    /** 玩家战斗单元：以当前剩余生命入场（生命跨局累计，不满血开打） */
    fun playerCombatant(
        hero: HeroTemplate,
        stats: Stats,
        currentHp: Int,
        cfg: BattleConfig
    ): Combatant {
        val aps = hero.attackSpeed * (1.0 + stats.attackSpeedPct / 100.0)
        val interval = (1000.0 / aps / cfg.tickMs).roundToInt().coerceAtLeast(2)
        val skills = cfg.skillsOf(hero.id, "ult").map { SkillRuntime(it, stats.ultCdScale) } +
            cfg.skillsOf(hero.id, "passive").map { SkillRuntime(it) }
        return Combatant(
            side = Side.PLAYER,
            id = hero.id,
            name = hero.name,
            emoji = hero.emoji,
            colorInt = parseColor(hero.color),
            hp = currentHp.coerceIn(1, stats.maxHp),
            maxHp = stats.maxHp,
            shield = stats.shield,
            maxShield = stats.shield,
            attack = stats.attack,
            baseAttack = stats.attack,
            baseIntervalTicks = interval,
            armor = stats.armor,
            magicResist = stats.magicResist,
            crit = stats.crit,
            critDmg = stats.critDmg,
            evasion = stats.evasion,
            lifesteal = stats.lifesteal,
            skills = skills,
            ultDmgScale = stats.ultDmgScale
        )
    }

    /** AI 敌人：从英雄池取一个，按难度系数整体缩放 */
    fun enemyCombatant(hero: HeroTemplate, coef: Double, cfg: BattleConfig): Combatant {
        val es = cfg.enemyScale
        val hp = (hero.hp * coef * es.hp).roundToInt().coerceAtLeast(50)
        val attack = (hero.attack * coef * es.attack).roundToInt().coerceAtLeast(5)
        val armor = hero.armor * coef * es.armor
        val mr = hero.magicResist * coef * es.magicResist
        val aps = hero.attackSpeed * coef * es.attackSpeed
        val interval = (1000.0 / aps / cfg.tickMs).roundToInt().coerceAtLeast(2)
        val skills = cfg.skillsOf(hero.id, "ult").map { SkillRuntime(it) } +
            cfg.skillsOf(hero.id, "passive").map { SkillRuntime(it) }
        return Combatant(
            side = Side.ENEMY,
            id = hero.id,
            name = hero.name,
            emoji = hero.emoji,
            colorInt = parseColor(hero.color),
            hp = hp,
            maxHp = hp,
            shield = 0,
            maxShield = 0,
            attack = attack,
            baseAttack = attack,
            baseIntervalTicks = interval,
            armor = armor,
            magicResist = mr,
            crit = cfg.baseCrit,
            critDmg = cfg.defaultCritDmg,
            evasion = 0,
            lifesteal = 0,
            skills = skills
        )
    }

    /**
     * 把携带物带来的上限变化同步回 run 状态。
     * 中期捡到武器 → 上限提高，当前生命同比补上；武器被偷 → 上限下降，当前生命被压回去。
     */
    fun syncRunState(state: RunState, newMaxHp: Int) {
        if (newMaxHp <= 0 || newMaxHp == state.maxHp) return
        state.hp = (state.hp + (newMaxHp - state.maxHp)).coerceIn(1, newMaxHp)
        state.maxHp = newMaxHp
    }

    private fun parseColor(hex: String): Int =
        try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            Color.parseColor("#FFFFFF")
        }
}
