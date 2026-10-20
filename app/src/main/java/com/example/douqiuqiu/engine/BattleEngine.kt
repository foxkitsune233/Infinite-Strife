package com.example.douqiuqiu.engine

import com.example.douqiuqiu.model.BattleConfig
import com.example.douqiuqiu.model.BattleEvent
import com.example.douqiuqiu.model.BattleEventType
import com.example.douqiuqiu.model.BattleSimResult
import com.example.douqiuqiu.model.Combatant
import com.example.douqiuqiu.model.Dot
import com.example.douqiuqiu.model.Side
import com.example.douqiuqiu.model.SkillRuntime
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * M2：真战斗引擎。
 *
 * 设计要点（和设计文档 §3.7 / §5.2 对齐）：
 *  1. **一次算完整场**：simulate 一次性跑完整个 tick 循环，产出事件序列。
 *     UI 拿序列回放，所以「跳过 / 倍速 / 断线重播」都只是播放器的事，与逻辑无关。
 *  2. **引擎不碰 Android**：没有 Context / View，可以随时搬到单元测试里。
 *  3. **伤害公式**：减伤 = armorK × 抗性 / (1 + armorK × |抗性|)，
 *     最终伤害 = 基础伤害 × (1 - 减伤) × 暴击倍率 × ±damageVariance 浮动。
 *  4. **结束判定**：一方生命归零；超时（maxTicks）按剩余血量百分比判胜负。
 */
class BattleEngine(
    private val cfg: BattleConfig,
    private val rng: Random = Random()
) {

    private var dealt = 0
    private var taken = 0

    fun simulate(player: Combatant, enemy: Combatant): BattleSimResult {
        dealt = 0
        taken = 0

        val events = mutableListOf<BattleEvent>()
        val playerPower = powerOf(player)
        val enemyPower = powerOf(enemy)
        var tick = 0
        var timeout = false

        events += BattleEvent(
            tick = 0, type = BattleEventType.BATTLE_START,
            source = Side.PLAYER, target = Side.ENEMY,
            text = "${player.name}  VS  ${enemy.name}"
        )
        // 开场被动：持续伤害类直接挂到对手身上
        applyStartPassives(player, enemy, events)
        applyStartPassives(enemy, player, events)

        while (true) {
            if (!player.alive || !enemy.alive) break
            if (tick >= cfg.maxTicks) {
                timeout = true
                break
            }
            tick++

            tickDecay(player)
            tickDecay(enemy)

            stepDots(player, tick, events)
            if (!player.alive || !enemy.alive) break
            stepDots(enemy, tick, events)
            if (!player.alive || !enemy.alive) break

            castUltimates(player, enemy, tick, events)
            if (!player.alive || !enemy.alive) break
            castUltimates(enemy, player, tick, events)
            if (!player.alive || !enemy.alive) break

            doAttack(player, enemy, tick, events)
            if (!player.alive || !enemy.alive) break
            doAttack(enemy, player, tick, events)
        }

        val win = when {
            !enemy.alive && !player.alive -> player.hpRatio() >= enemy.hpRatio()
            !enemy.alive -> true
            !player.alive -> false
            timeout -> player.hpRatio() >= enemy.hpRatio()
            else -> false
        }

        events += BattleEvent(
            tick = tick,
            type = BattleEventType.BATTLE_END,
            source = if (win) Side.PLAYER else Side.ENEMY,
            target = if (win) Side.ENEMY else Side.PLAYER,
            text = if (win) "胜利" else "战败"
        )

        return BattleSimResult(
            win = win,
            timeout = timeout,
            ticks = tick,
            events = events,
            playerHpLeft = player.hp.coerceAtLeast(0),
            enemyHpLeft = enemy.hp.coerceAtLeast(0),
            damageDealt = dealt,
            damageTaken = taken,
            playerPower = playerPower,
            enemyPower = enemyPower
        )
    }

    // ---------------- 每 tick 的状态衰减 ----------------

    private fun tickDecay(c: Combatant) {
        if (c.stunTicks > 0) c.stunTicks--
        if (c.tempEvasionTicks > 0) {
            c.tempEvasionTicks--
            if (c.tempEvasionTicks == 0) c.tempEvasion = 0
        }
        if (c.tempAtkTicks > 0) {
            c.tempAtkTicks--
            if (c.tempAtkTicks == 0) c.tempAtkPct = 0
        }
        c.refreshAttack()
    }

    // ---------------- 持续伤害 ----------------

    private fun stepDots(victim: Combatant, tick: Int, events: MutableList<BattleEvent>) {
        if (victim.dots.isEmpty()) return
        val it = victim.dots.iterator()
        while (it.hasNext()) {
            val d: Dot = it.next()
            if (!victim.alive) break
            val dmg = reducedDamage(d.perTick.toDouble(), victim.magicResist)
            applyDamage(victim, dmg, tick, BattleEventType.DOT, victim.side.opposite(), d.name, events)
            d.ticksLeft--
            if (d.ticksLeft <= 0) it.remove()
        }
    }

    // ---------------- 大招（按 CD） ----------------

    private fun castUltimates(
        src: Combatant,
        dst: Combatant,
        tick: Int,
        events: MutableList<BattleEvent>
    ) {
        if (!src.alive || !dst.alive) return
        for (sk in src.skills) {
            if (sk.tpl.slot != "ult") continue
            if (sk.cdLeft > 0) {
                sk.cdLeft--
                continue
            }
            sk.cdLeft = sk.cooldownTicks
            castSkill(src, dst, sk, tick, events)
            if (!dst.alive) return
        }
    }

    private fun castSkill(
        src: Combatant,
        dst: Combatant,
        sk: SkillRuntime,
        tick: Int,
        events: MutableList<BattleEvent>
    ) {
        val t = sk.tpl
        val scale = if (src.side == Side.PLAYER) src.ultDmgScale else 1.0

        events += BattleEvent(
            tick = tick, type = BattleEventType.SKILL,
            source = src.side, target = dst.side,
            skillName = t.name,
            text = t.text.replace("{src}", src.name)
        )

        when (t.kind) {
            "multi_hit" -> {
                // 多段物理：总倍率分摊到每一段
                val per = src.baseAttack * t.dmgPct / 100.0 / t.hits * scale
                repeat(t.hits) {
                    if (!dst.alive) return
                    val dmg = reducedDamage(per, dst.armor)
                    applyDamage(dst, dmg, tick, BattleEventType.HIT, src.side, t.name, events)
                }
            }

            "damage" -> {
                val dmg = reducedDamage(src.baseAttack * t.dmgPct / 100.0 * scale, dst.magicResist)
                applyDamage(dst, dmg, tick, BattleEventType.HIT, src.side, t.name, events)
                if (t.selfDamagePct > 0) {
                    val self = (src.maxHp * t.selfDamagePct / 100.0).roundToInt()
                    src.hp = (src.hp - self).coerceAtLeast(0)
                    events += BattleEvent(
                        tick = tick, type = BattleEventType.HIT,
                        source = src.side, target = src.side, value = self,
                        skillName = t.name, text = "${src.name} 自损 $self"
                    )
                    if (!src.alive) {
                        events += deathEvent(src, tick, src.side, t.name)
                    }
                }
            }

            "execute" -> {
                if (dst.hp <= dst.maxHp * t.thresholdPct / 100.0) {
                    events += BattleEvent(
                        tick = tick, type = BattleEventType.SKILL,
                        source = src.side, target = dst.side, value = dst.hp,
                        skillName = t.name, text = "${dst.name} 被直接斩杀！"
                    )
                    dst.hp = 0
                    events += deathEvent(dst, tick, src.side, t.name)
                } else {
                    val dmg = reducedDamage(src.baseAttack * t.dmgPct / 100.0 * scale, dst.magicResist)
                    applyDamage(dst, dmg, tick, BattleEventType.HIT, src.side, t.name, events)
                }
            }

            "heal" -> {
                val amount = (src.maxHp * t.healPct / 100.0).roundToInt()
                heal(src, amount, tick, BattleEventType.HEAL, src.side, t.name, events)
            }

            "shield" -> {
                val amount = (src.maxHp * t.shieldPct / 100.0).roundToInt()
                gainShield(src, amount, tick, src.side, t.name, events)
            }

            "buff_evasion" -> {
                src.tempEvasion += t.buffValue
                src.tempEvasionTicks = t.buffTicks
                events += BattleEvent(
                    tick = tick, type = BattleEventType.SKILL,
                    source = src.side, target = src.side, value = t.buffValue,
                    skillName = t.name, text = "${src.name} 闪避 +${t.buffValue}"
                )
            }

            "buff_atk" -> {
                src.tempAtkPct += t.buffValue
                src.tempAtkTicks = t.buffTicks
                src.refreshAttack()
                events += BattleEvent(
                    tick = tick, type = BattleEventType.SKILL,
                    source = src.side, target = src.side, value = t.buffValue,
                    skillName = t.name, text = "${src.name} 攻击 +${t.buffValue}%"
                )
            }

            "stun_damage" -> {
                val dmg = reducedDamage(src.baseAttack * t.dmgPct / 100.0 * scale, dst.magicResist)
                applyDamage(dst, dmg, tick, BattleEventType.HIT, src.side, t.name, events)
                if (dst.alive && t.stunTicks > 0) {
                    dst.stunTicks = maxOf(dst.stunTicks, t.stunTicks)
                    events += BattleEvent(
                        tick = tick, type = BattleEventType.STUN,
                        source = src.side, target = dst.side, value = t.stunTicks,
                        skillName = t.name, text = "${dst.name} 被眩晕 ${t.stunTicks} 拍"
                    )
                }
            }

            else -> Unit
        }
    }

    // ---------------- 普攻 ----------------

    private fun doAttack(
        src: Combatant,
        dst: Combatant,
        tick: Int,
        events: MutableList<BattleEvent>
    ) {
        if (!src.alive || !dst.alive) return
        if (src.stunTicks > 0) return          // 眩晕中无法出手
        src.nextAttackIn--
        if (src.nextAttackIn > 0) return
        src.nextAttackIn = src.intervalTicks()

        val eva = dst.evasion + dst.tempEvasion
        if (eva > 0 && rng.nextInt(100) < eva) {
            events += BattleEvent(
                tick = tick, type = BattleEventType.MISS,
                source = src.side, target = dst.side,
                text = "${dst.name} 闪避了攻击"
            )
            return
        }

        val isCrit = rng.nextInt(100) < src.crit
        var dmg = reducedDamage(src.attack.toDouble(), dst.armor)
        if (isCrit) dmg = (dmg * src.critDmg / 100.0).roundToInt()

        applyDamage(
            dst, dmg, tick,
            if (isCrit) BattleEventType.CRIT else BattleEventType.HIT,
            src.side, "", events
        )
        if (!dst.alive) return

        // 吸血
        if (src.lifesteal > 0) {
            val amount = (dmg * src.lifesteal / 100.0).roundToInt()
            if (amount > 0 && src.hp < src.maxHp) {
                heal(src, amount, tick, BattleEventType.LIFESTEAL, src.side, "", events)
            }
        }

        triggerPassives(src, dst, tick, events, isCrit)
    }

    // ---------------- 被动（受击 / 命中时触发） ----------------

    private fun triggerPassives(
        src: Combatant,
        dst: Combatant,
        tick: Int,
        events: MutableList<BattleEvent>,
        isCrit: Boolean
    ) {
        for (sk in src.skills) {
            if (sk.tpl.slot != "passive") continue
            val t = sk.tpl
            when (t.kind) {
                "onhit_extra", "onhit_counter", "onhit_splash" -> {
                    if (rng.nextInt(100) < t.chance) {
                        val dmg = reducedDamage(src.attack * t.dmgPct / 100.0, dst.armor)
                        applyDamage(dst, dmg, tick, BattleEventType.HIT, src.side, t.name, events)
                    }
                }

                "onhit_crit" -> {
                    if (!isCrit && rng.nextInt(100) < t.chance) {
                        val base = reducedDamage(src.attack.toDouble(), dst.armor)
                        val dmg = (base * t.critDmgPct / 100.0).roundToInt()
                        applyDamage(dst, dmg, tick, BattleEventType.CRIT, src.side, t.name, events)
                    }
                }

                "onhit_heal" -> {
                    if (rng.nextInt(100) < t.chance) {
                        val amount = (src.maxHp * t.healPct / 100.0).roundToInt()
                        heal(src, amount, tick, BattleEventType.HEAL, src.side, t.name, events)
                    }
                }

                "onhit_stun" -> {
                    if (dst.alive && rng.nextInt(100) < t.chance) {
                        dst.stunTicks = maxOf(dst.stunTicks, t.stunTicks)
                        events += BattleEvent(
                            tick = tick, type = BattleEventType.STUN,
                            source = src.side, target = dst.side, value = t.stunTicks,
                            skillName = t.name, text = "${dst.name} 被${t.name}定住"
                        )
                    }
                }

                "aspd_lowhp" -> {
                    // 血越少攻速越快：按已损失生命比例线性加成
                    src.asBonusPct = (t.maxBonusPct * (1.0 - src.hpRatio())).roundToInt()
                }

                else -> Unit
            }
            if (!dst.alive) return
        }
    }

    // ---------------- 开场被动 ----------------

    private fun applyStartPassives(
        src: Combatant,
        dst: Combatant,
        events: MutableList<BattleEvent>
    ) {
        for (sk in src.skills) {
            if (sk.tpl.slot != "passive") continue
            if (sk.tpl.kind == "dot" && sk.tpl.dotPct > 0) {
                val perTick = (src.maxHp * sk.tpl.dotPct / 100.0).roundToInt().coerceAtLeast(1)
                dst.dots += Dot(perTick, cfg.maxTicks, sk.tpl.name)
                events += BattleEvent(
                    tick = 0, type = BattleEventType.SKILL,
                    source = src.side, target = dst.side, value = perTick,
                    skillName = sk.tpl.name,
                    text = "${src.name} 的${sk.tpl.name}开始灼烧对手"
                )
            }
        }
    }

    // ---------------- 伤害 / 治疗 / 护盾 ----------------

    /** 护盾先吸收，剩余才扣血；事件里带上护盾吸收了多少，UI 才能同步 */
    private fun applyDamage(
        dst: Combatant,
        dmg: Int,
        tick: Int,
        type: BattleEventType,
        src: Side,
        skillName: String,
        events: MutableList<BattleEvent>
    ) {
        val total = dmg.coerceAtLeast(0)
        if (total == 0) return
        var remain = total
        var shieldPart = 0

        if (dst.shield > 0) {
            shieldPart = minOf(dst.shield, remain)
            dst.shield -= shieldPart
            remain -= shieldPart
            if (dst.shield <= 0) {
                dst.shield = 0
                events += BattleEvent(
                    tick = tick, type = BattleEventType.SHIELD_BREAK,
                    source = src, target = dst.side, value = shieldPart,
                    skillName = skillName, text = "${dst.name} 的护盾碎裂"
                )
            }
        }

        dst.hp = (dst.hp - remain).coerceAtLeast(0)
        if (remain > 0) {
            events += BattleEvent(
                tick = tick, type = type, source = src, target = dst.side,
                value = remain, shieldPart = shieldPart,
                skillName = skillName
            )
            if (dst.side == Side.PLAYER) taken += remain else dealt += remain
        }
        if (dst.hp <= 0) {
            events += deathEvent(dst, tick, src, skillName)
        }
    }

    private fun heal(
        c: Combatant,
        amount: Int,
        tick: Int,
        type: BattleEventType,
        src: Side,
        skillName: String,
        events: MutableList<BattleEvent>
    ) {
        if (amount <= 0 || c.hp >= c.maxHp) return
        val real = minOf(amount, c.maxHp - c.hp)
        c.hp += real
        events += BattleEvent(
            tick = tick, type = type, source = src, target = c.side,
            value = real, skillName = skillName
        )
    }

    private fun gainShield(
        c: Combatant,
        amount: Int,
        tick: Int,
        src: Side,
        skillName: String,
        events: MutableList<BattleEvent>
    ) {
        if (amount <= 0) return
        val real = minOf(amount, c.maxShield - c.shield)
        if (real <= 0) return
        c.shield += real
        events += BattleEvent(
            tick = tick, type = BattleEventType.SHIELD, source = src, target = c.side,
            value = real, skillName = skillName
        )
    }

    private fun deathEvent(c: Combatant, tick: Int, src: Side, skillName: String) =
        BattleEvent(
            tick = tick, type = BattleEventType.DEATH,
            source = src, target = c.side,
            skillName = skillName, text = "${c.name} 倒下了"
        )

    // ---------------- 公式 ----------------

    /** 基础伤害 × (1 - 减伤) × ±浮动，至少 1 点 */
    private fun reducedDamage(base: Double, resist: Double): Int {
        val v = 1.0 + (rng.nextDouble() * 2 - 1) * cfg.damageVariance
        return (base * (1.0 - cfg.resistanceReduction(resist)) * v)
            .roundToInt().coerceAtLeast(1)
    }

    /** 「战力」只是一个展示用的综合分，不参与胜负判定 */
    private fun powerOf(c: Combatant): Int {
        val aps = c.attacksPerSecond(cfg.tickMs)
        return (c.maxHp * 0.5 +
            c.baseAttack * 10.0 * aps +
            abs(c.armor) * 5.0 +
            c.crit * 3.0 +
            c.maxShield * 0.3).roundToInt()
    }
}
