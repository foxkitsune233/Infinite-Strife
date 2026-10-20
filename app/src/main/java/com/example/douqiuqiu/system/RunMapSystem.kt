package com.example.douqiuqiu.system

import com.example.douqiuqiu.model.EventOutcome
import com.example.douqiuqiu.model.GameEventTemplate
import com.example.douqiuqiu.model.NodeKind
import com.example.douqiuqiu.model.RunEnding
import com.example.douqiuqiu.model.RunMapConfig
import com.example.douqiuqiu.model.RunNode
import com.example.douqiuqiu.model.RunState
import com.example.douqiuqiu.model.SchoolTemplate
import com.example.douqiuqiu.model.SkillCardTemplate
import com.example.douqiuqiu.model.WeaponTemplate
import java.util.Random

/**
 * v0.3：5 局固定地图的流程推进。
 *
 * 地图：B1 → ①事件 → B2 → ②事件 → B3 → ③返回/休息 → B4 → ④事件 → B5
 * 规则：任一一局战败 → 立即结束（携带物全掉）；打完 B5 → 通关；在 ③ 选返回 → 撤离。
 *
 * M2 起真战斗由 engine/BattleEngine 在 ui/BattleActivity 中跑（tick 循环 + 事件序列），
 * 本类只保留节点推进、事件结算和经济/掉落结算。
 */
object RunMapSystem {

    private val rng = Random()

    // ---------------- 节点查询 ----------------

    fun currentNode(map: RunMapConfig, state: RunState): RunNode? =
        map.nodes.getOrNull(state.currentNode)

    fun isLastNode(map: RunMapConfig, state: RunState): Boolean =
        state.currentNode >= map.nodes.size - 1

    // ---------------- 随机事件 ----------------

    /**
     * 按权重从事件池抽一条（纯随机触发，玩家不选择）。
     * 只从允许出现在该节点序号的事件中抽。
     */
    fun rollEvent(events: List<GameEventTemplate>, nodeOrder: Int): GameEventTemplate {
        val pool = events.filter { it.nodes.isEmpty() || nodeOrder in it.nodes }
        val usable = if (pool.isEmpty()) events else pool
        val total = usable.sumOf { it.weight }
        if (total <= 0) return usable.first()
        var r = rng.nextInt(total)
        for (e in usable) {
            r -= e.weight
            if (r < 0) return e
        }
        return usable.last()
    }

    /**
     * 结算一条事件，直接改动 run 状态，并返回给 UI 展示的结果文案。
     */
    fun applyEvent(
        event: GameEventTemplate,
        state: RunState,
        weapons: List<WeaponTemplate>,
        cards: List<SkillCardTemplate>,
        schools: List<SchoolTemplate>
    ): EventOutcome {
        var v = if (event.valueMax > event.value) {
            event.value + rng.nextInt(event.valueMax - event.value + 1)
        } else event.value

        val text = when (event.kind) {
            "gold_add" -> {
                state.gold += v
                event.text.replace("{v}", v.toString())
            }
            "gold_lose" -> {
                v = minOf(v, state.gold)
                state.gold -= v
                event.text.replace("{v}", v.toString())
            }
            "hp_heal" -> {
                val before = state.hp
                state.hp = minOf(state.maxHp, state.hp + v)
                val real = state.hp - before
                event.text.replace("{v}", real.toString())
            }
            "hp_lose" -> {
                state.hp -= v
                if (state.hp <= 0) {
                    state.hp = 0
                    state.finished = true
                    state.ending = RunEnding.DEAD
                }
                event.text.replace("{v}", v.toString())
            }
            "weapon_gain" -> {
                if (weapons.isEmpty()) {
                    "宝箱是空的。"
                } else {
                    val w = weapons[rng.nextInt(weapons.size)]
                    state.carriedWeapons.add(w.id)
                    event.text.replace("{v}", "${w.emoji} ${w.name}")
                }
            }
            "card_gain" -> {
                if (cards.isEmpty()) {
                    "前辈欲言又止，什么也没给你。"
                } else {
                    val c = cards[rng.nextInt(cards.size)]
                    state.carriedCards.add(c.id)
                    event.text.replace("{v}", c.name)
                }
            }
            "weapon_lose" -> {
                if (state.carriedWeapons.isNotEmpty()) {
                    val id = state.carriedWeapons.removeAt(rng.nextInt(state.carriedWeapons.size))
                    val name = weapons.firstOrNull { it.id == id }?.name ?: id
                    event.text.replace("{v}", name)
                } else {
                    val lose = minOf(60, state.gold)
                    state.gold -= lose
                    "扒手翻遍了你的口袋，没找到武器，顺走金币 -$lose"
                }
            }
            "school_exp" -> {
                val name = if (schools.isEmpty()) "未知" else schools[rng.nextInt(schools.size)].name
                event.text.replace("{v}", name)
            }
            "buff_atk" -> {
                state.buffAtkPct += v
                event.text.replace("{v}", v.toString())
            }
            "buff_armor" -> {
                state.buffArmor += v
                event.text.replace("{v}", v.toString())
            }
            "gamble_gold" -> {
                if (rng.nextBoolean()) {
                    val gain = state.gold
                    state.gold += gain
                    "赌神眷顾：金币翻倍，+$gain"
                } else {
                    val loss = state.gold / 2
                    state.gold -= loss
                    "赌运不佳：金币减半，-$loss"
                }
            }
            "reveal_next" -> "神谕揭示了下一场仓库中的 1 件物品"
            else -> event.text.replace("{v}", v.toString())
        }
        return EventOutcome(event, text)
    }

    // ---------------- 战斗（M2：真引擎在 BattleActivity 里跑，这里只做结算） ----------------

    /**
     * run 层面的战斗结算结果。
     * 真战斗由 BattleEngine 在 BattleActivity 中跑完，这里只负责把战斗结果
     * 翻译成「掉多少血 / 给多少金币 / 掉什么战利品」。
     */
    data class BattleResult(
        val win: Boolean,
        val hpAfter: Int,
        val goldGain: Int,
        val lootText: String,
        val playerPower: Int,
        val enemyPower: Int,
        val playerMaxHp: Int = 0,
        val damageDealt: Int = 0,
        val damageTaken: Int = 0,
        val ticks: Int = 0,
        val timeout: Boolean = false,
        val enemyName: String = ""
    )

    /**
     * 把一场战斗的结果结算成 run 状态变化。
     *
     * 血量：以战斗结束时的剩余生命为准（开局就是当前剩余生命，跨局累计不回满）。
     * 金币：随回合数上涨，越往后打赢越赚——这是撑到 B5 的动力。
     */
    fun settleBattle(
        win: Boolean,
        hpAfter: Int,
        round: Int,
        playerPower: Int,
        enemyPower: Int,
        damageDealt: Int,
        damageTaken: Int,
        ticks: Int,
        timeout: Boolean,
        enemyName: String,
        playerMaxHp: Int
    ): BattleResult {
        if (!win) {
            return BattleResult(
                win = false, hpAfter = 0, goldGain = 0, lootText = "",
                playerPower = playerPower, enemyPower = enemyPower,
                playerMaxHp = playerMaxHp, damageDealt = damageDealt,
                damageTaken = damageTaken, ticks = ticks, timeout = timeout,
                enemyName = enemyName
            )
        }
        val gold = 100 + round * 40 + rng.nextInt(60)
        return BattleResult(
            win = true,
            hpAfter = hpAfter.coerceAtLeast(1),
            goldGain = gold,
            lootText = "战利品仓库 ×1 入袋",
            playerPower = playerPower,
            enemyPower = enemyPower,
            playerMaxHp = playerMaxHp,
            damageDealt = damageDealt,
            damageTaken = damageTaken,
            ticks = ticks,
            timeout = timeout,
            enemyName = enemyName
        )
    }

    /** 战斗胜利：回血 + 金币 + 仓库入袋 + 推进节点 */
    fun winBattle(map: RunMapConfig, state: RunState, result: BattleResult) {
        state.hp = minOf(state.maxHp, result.hpAfter + (state.maxHp * map.battleVictoryHealPct / 100))
        state.gold += result.goldGain
        state.securedLoot.add(result.lootText)
        state.battlesWon += 1
        // 临时增益只作用于下一场，用完清空
        state.buffAtkPct = 0
        state.buffArmor = 0
        advance(map, state)
    }

    /** 战斗失败：携带物全部掉落，run 立即结束 */
    fun loseBattle(state: RunState): List<String> {
        val dropped = mutableListOf<String>()
        dropped.addAll(state.carriedCards)
        dropped.addAll(state.carriedWeapons)
        state.carriedCards.clear()
        state.carriedWeapons.clear()
        state.hp = 0
        state.finished = true
        state.ending = RunEnding.DEAD
        return dropped
    }

    // ---------------- 节点 ③：返回 / 休息 ----------------

    /** 选择「休息（休整）」：回血 + 金币，继续打 B4、B5 */
    fun restAtNode(map: RunMapConfig, state: RunState) {
        val cfg = map.restNode
        state.hp = minOf(state.maxHp, state.hp + state.maxHp * cfg.healPct / 100)
        state.gold += cfg.bonusGold
        state.rested = true
        advance(map, state)
    }

    /** 选择「返回（撤离）」：安全结束，战利品全部落袋 */
    fun extractAtNode(state: RunState) {
        state.finished = true
        state.ending = RunEnding.EXTRACT
    }

    // ---------------- 推进 ----------------

    /** 推进到下一个节点；走完最后一个节点即通关 */
    fun advance(map: RunMapConfig, state: RunState) {
        if (state.currentNode >= map.nodes.size - 1) {
            state.finished = true
            state.ending = RunEnding.CLEAR
            return
        }
        state.currentNode += 1
    }

    /** 当前节点是否为「返回 / 休息」 */
    fun isRestNode(map: RunMapConfig, state: RunState): Boolean =
        currentNode(map, state)?.kind == NodeKind.REST_EXIT

    /** 通关奖励倍率：走过休整则更高 */
    fun clearMultiplier(map: RunMapConfig, state: RunState): Double =
        if (state.rested) map.restNode.clearBonusMultiplier else 1.0
}
