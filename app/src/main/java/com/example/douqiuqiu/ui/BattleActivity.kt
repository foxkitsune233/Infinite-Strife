package com.example.douqiuqiu.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.R
import com.example.douqiuqiu.config.ConfigLoader
import com.example.douqiuqiu.engine.BattleEngine
import com.example.douqiuqiu.engine.Loadout
import com.example.douqiuqiu.model.BattleSimResult
import com.example.douqiuqiu.model.HeroTemplate
import com.example.douqiuqiu.model.RunState
import java.util.Random

/**
 * M2：战斗界面。
 *
 * 职责只有三件事：
 *  1. 用 Loadout 把「英雄 + 携带物 + 事件增益」组装成双方战斗单元
 *  2. 跑一遍 BattleEngine 拿到事件序列，交给 BattleView 回放
 *  3. 播完把结果（胜负 / 剩余生命 / 伤害统计）回传给 MapActivity 结算
 *
 * 自己不做任何胜负判定，也不改 run 状态——那是 RunMapSystem 的事。
 */
class BattleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HERO_ID = "hero_id"
        const val EXTRA_ROUND = "battle_round"
        const val EXTRA_STATE = "run_state"

        const val RESULT_WIN = "win"
        const val RESULT_HP_AFTER = "hp_after"
        const val RESULT_PLAYER_MAX_HP = "player_max_hp"
        const val RESULT_DAMAGE_DEALT = "damage_dealt"
        const val RESULT_DAMAGE_TAKEN = "damage_taken"
        const val RESULT_PLAYER_POWER = "player_power"
        const val RESULT_ENEMY_POWER = "enemy_power"
        const val RESULT_TICKS = "ticks"
        const val RESULT_TIMEOUT = "timeout"
        const val RESULT_ENEMY_NAME = "enemy_name"
    }

    private lateinit var hero: HeroTemplate
    private lateinit var enemyHero: HeroTemplate
    private lateinit var state: RunState
    private lateinit var result: BattleSimResult
    private var playerMaxHp: Int = 0
    private var round: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle)

        val heroId = intent.getStringExtra(EXTRA_HERO_ID) ?: ""
        round = intent.getIntExtra(EXTRA_ROUND, 1)
        state = intent.getSerializableExtra(EXTRA_STATE) as? RunState ?: RunState(heroId)

        val heroes = ConfigLoader.loadHeroes(this)
        hero = heroes.firstOrNull { it.id == heroId } ?: heroes.first()

        val battleCfg = ConfigLoader.loadBattle(this)
        val weapons = ConfigLoader.loadWeapons(this)
        val cards = ConfigLoader.loadSkillCards(this)
        val schools = ConfigLoader.loadSchools(this)
        val balance = ConfigLoader.loadBalance(this)
        val difficulty = ConfigLoader.loadDifficulty(this)
        val coef = difficulty.roundCoefs.getOrElse(round - 1) { 1.0 }

        // 对手：从英雄池抽一个，尽量不镜像玩家
        val pool = heroes.filter { it.id != hero.id }.ifEmpty { heroes }
        enemyHero = pool[Random().nextInt(pool.size)]

        // 组装双方战斗单元
        val stats = Loadout.compute(
            Loadout.Input(
                hero = hero,
                weapons = weapons,
                carriedWeaponIds = state.carriedWeapons,
                cards = cards,
                carriedCardIds = state.carriedCards,
                schools = schools,
                expThresholds = balance.schoolExpThresholds,
                buffAtkPct = state.buffAtkPct,
                buffArmor = state.buffArmor,
                shieldPerStack = battleCfg.shieldPerStack,
                baseCrit = battleCfg.baseCrit,
                baseCritDmg = battleCfg.defaultCritDmg
            )
        )
        playerMaxHp = stats.maxHp
        val playerC = Loadout.playerCombatant(hero, stats, state.hp, battleCfg)
        val enemyC = Loadout.enemyCombatant(enemyHero, coef, battleCfg)

        // 引擎一次算完整场，产出事件序列
        result = BattleEngine(battleCfg).simulate(playerC, enemyC)

        // 标题栏
        findViewById<TextView>(R.id.tvBattleRound).text = "⚔️ 第 $round 场"
        findViewById<TextView>(R.id.tvEnemy).text =
            "对手：${enemyHero.emoji} ${enemyHero.name}"
        findViewById<TextView>(R.id.tvLoadout).text = buildString {
            append("携带：")
            val wNames = state.carriedWeapons.mapNotNull { id -> weapons.firstOrNull { it.id == id }?.name }
            val cNames = state.carriedCards.mapNotNull { id -> cards.firstOrNull { it.id == id }?.name }
            if (wNames.isEmpty() && cNames.isEmpty()) {
                append("无（输了也不掉东西）")
            } else {
                if (wNames.isNotEmpty()) append("武器 ${wNames.joinToString("、")}")
                if (cNames.isNotEmpty()) {
                    if (wNames.isNotEmpty()) append("　")
                    append("卡 ${cNames.joinToString("、")}")
                }
            }
            if (stats.schoolStar > 0) append("　【${stats.schoolName} ${stats.schoolStar}星】")
            if (state.buffAtkPct > 0) append("　攻+${state.buffAtkPct}%")
            if (state.buffArmor > 0) append("　甲+${state.buffArmor}")
        }

        // 回放
        val view = findViewById<BattleView>(R.id.battleView)
        view.setup(playerC, enemyC, battleCfg, result.events, 1) { showResult() }
        view.start()

        findViewById<Button>(R.id.btnSpeed).setOnClickListener {
            val s = view.toggleSpeed()
            it as Button
            it.text = "×$s"
        }
        findViewById<Button>(R.id.btnSkip).setOnClickListener { view.skip() }
    }

    private fun showResult() {
        if (isFinishing) return
        val hpAfter = if (result.win) result.playerHpLeft.coerceAtLeast(1) else 0
        val title = if (result.win) "⚔️ 第 $round 场 · 胜利" else "💀 第 $round 场 · 战败"
        val msg = buildString {
            append("对手：${enemyHero.emoji} ${enemyHero.name}\n")
            append("战力 ${result.playerPower} vs 敌 ${result.enemyPower}\n")
            append("造成伤害 ${result.damageDealt}　承受伤害 ${result.damageTaken}\n")
            append("剩余生命 $hpAfter / $playerMaxHp\n")
            append("战斗时长 ${result.ticks / 10}.${result.ticks % 10} 秒")
            if (result.timeout) append("\n（超时，按剩余血量百分比判定）")
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("继续") { _, _ -> deliver(result, hpAfter) }
            .show()
    }

    private fun deliver(res: BattleSimResult, hpAfter: Int) {
        val data = Intent().apply {
            putExtra(RESULT_WIN, res.win)
            putExtra(RESULT_HP_AFTER, hpAfter)
            putExtra(RESULT_PLAYER_MAX_HP, playerMaxHp)
            putExtra(RESULT_DAMAGE_DEALT, res.damageDealt)
            putExtra(RESULT_DAMAGE_TAKEN, res.damageTaken)
            putExtra(RESULT_PLAYER_POWER, res.playerPower)
            putExtra(RESULT_ENEMY_POWER, res.enemyPower)
            putExtra(RESULT_TICKS, res.ticks)
            putExtra(RESULT_TIMEOUT, res.timeout)
            putExtra(RESULT_ENEMY_NAME, "${enemyHero.emoji} ${enemyHero.name}")
        }
        setResult(RESULT_OK, data)
        finish()
    }

    /** 中途退出（按返回键）不算打完，MapActivity 保持原节点 */
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
