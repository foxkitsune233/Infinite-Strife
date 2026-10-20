package com.example.douqiuqiu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.config.ConfigLoader
import com.example.douqiuqiu.engine.Loadout
import com.example.douqiuqiu.model.BalanceConfig
import com.example.douqiuqiu.model.BattleConfig
import com.example.douqiuqiu.model.GameEventTemplate
import com.example.douqiuqiu.model.HeroTemplate
import com.example.douqiuqiu.model.NodeKind
import com.example.douqiuqiu.model.RunEnding
import com.example.douqiuqiu.model.RunMapConfig
import com.example.douqiuqiu.model.RunNode
import com.example.douqiuqiu.model.RunState
import com.example.douqiuqiu.model.SchoolTemplate
import com.example.douqiuqiu.model.SkillCardTemplate
import com.example.douqiuqiu.model.WeaponTemplate
import com.example.douqiuqiu.system.RunMapSystem
import com.example.douqiuqiu.ui.BattleActivity

/**
 * v0.3：闯关地图。
 *
 * 固定 9 节点：B1 → ①事件 → B2 → ②事件 → B3 → ③返回/休息 → B4 → ④事件 → B5
 * 英雄生命跨局累计、不自动回满，所以 5 局是一场消耗战，
 * 节点 ③ 的「返回 / 休息」因此成为真正的抉择点。
 *
 * M2：战斗节点改为拉起 BattleActivity，由 BattleEngine 真打一场，
 * 打完把结果回传到这里做经济与掉落结算。
 */
class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HERO_ID = "hero_id"
    }

    private lateinit var hero: HeroTemplate
    private lateinit var runMap: RunMapConfig
    private lateinit var state: RunState
    private lateinit var balance: BalanceConfig
    private lateinit var battleCfg: BattleConfig
    private var events: List<GameEventTemplate> = emptyList()
    private var weapons: List<WeaponTemplate> = emptyList()
    private var cards: List<SkillCardTemplate> = emptyList()
    private var schools: List<SchoolTemplate> = emptyList()
    private var roundCoefs: List<Double> = listOf(1.0, 1.25, 1.55, 1.90, 2.35)

    private lateinit var mapList: LinearLayout
    private lateinit var tvHeroEmoji: TextView
    private lateinit var tvHeroName: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvHp: TextView
    private lateinit var tvGold: TextView
    private lateinit var tvLoadout: TextView

    /** M2：战斗页结果回传 */
    private val battleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r -> onBattleFinished(r) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val heroId = intent.getStringExtra(EXTRA_HERO_ID) ?: ""
        val heroes = ConfigLoader.loadHeroes(this)
        hero = heroes.firstOrNull { it.id == heroId } ?: heroes.first()

        runMap = ConfigLoader.loadRunMap(this)
        events = ConfigLoader.loadEvents(this)
        weapons = ConfigLoader.loadWeapons(this)
        cards = ConfigLoader.loadSkillCards(this)
        schools = ConfigLoader.loadSchools(this)
        roundCoefs = ConfigLoader.loadDifficulty(this).roundCoefs
        balance = ConfigLoader.loadBalance(this)
        battleCfg = ConfigLoader.loadBattle(this)

        state = RunState(
            heroId = hero.id,
            gold = balance.initialGold,
            hp = hero.hp,
            maxHp = hero.hp
        )

        tvHeroEmoji = findViewById(R.id.tvHeroEmoji)
        tvHeroName = findViewById(R.id.tvHeroName)
        tvProgress = findViewById(R.id.tvProgress)
        tvHp = findViewById(R.id.tvHp)
        tvGold = findViewById(R.id.tvGold)
        tvLoadout = findViewById(R.id.tvLoadout)
        mapList = findViewById(R.id.mapList)

        render()
    }

    // ---------------- 渲染 ----------------

    private fun render() {
        // 携带物会改变生命上限，先同步再画头部
        Loadout.syncRunState(state, loadoutStats().maxHp)
        updateHeader()
        mapList.removeAllViews()

        for (node in runMap.nodes) {
            val v = LayoutInflater.from(this).inflate(R.layout.item_map_node, mapList, false)
            bindNode(v, node)
            mapList.addView(v)
        }

        if (state.finished) showEnding()
    }

    /** 当前携带物折算出的属性（生命上限 / 流派星级 / 临时增益） */
    private fun loadoutStats(): Loadout.Stats = Loadout.compute(
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

    private fun updateHeader() {
        tvHeroEmoji.text = hero.emoji
        tvHeroName.text = hero.name
        val next = (state.battlesWon + 1).coerceAtMost(runMap.totalBattles)
        val coef = roundCoefs.getOrElse(next - 1) { 1.0 }
        tvProgress.text = "B$next/${runMap.totalBattles} · 强度×$coef"
        tvHp.text = "❤\ufe0f ${state.hp} / ${state.maxHp}"
        tvGold.text = "\uD83D\uDCB0 ${state.gold}"
        val st = loadoutStats()
        tvLoadout.text = buildString {
            append("携带：卡 ×${state.carriedCards.size}　武器 ×${state.carriedWeapons.size}　已入袋战利品 ×${state.securedLoot.size}")
            if (st.schoolStar > 0) append("　【${st.schoolName} ${st.schoolStar}星】")
            if (state.buffAtkPct > 0) append("　攻+${state.buffAtkPct}%")
            if (state.buffArmor > 0) append("　甲+${state.buffArmor}")
        }
    }

    private fun bindNode(v: View, node: RunNode) {
        v.findViewById<TextView>(R.id.tvNodeEmoji).text = node.emoji
        v.findViewById<TextView>(R.id.tvNodeLabel).text = node.label
        val status = v.findViewById<TextView>(R.id.tvNodeStatus)
        val btnAction = v.findViewById<Button>(R.id.btnAction)
        val rowRest = v.findViewById<LinearLayout>(R.id.rowRest)

        when {
            node.index < state.currentNode -> {
                v.setBackgroundResource(R.drawable.bg_node_done)
                status.text = "✅ 已通过"
            }
            node.index == state.currentNode -> {
                v.setBackgroundResource(R.drawable.bg_node_current)
                status.text = "⬅ 当前"
                when (node.kind) {
                    NodeKind.BATTLE -> {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "入场 · ${node.label}"
                        btnAction.setOnClickListener { doBattle(node) }
                    }
                    NodeKind.EVENT -> {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "触发事件"
                        btnAction.setOnClickListener { doEvent(node) }
                    }
                    NodeKind.REST_EXIT -> {
                        rowRest.visibility = View.VISIBLE
                        v.findViewById<Button>(R.id.btnExtract).setOnClickListener {
                            RunMapSystem.extractAtNode(state)
                            render()
                        }
                        v.findViewById<Button>(R.id.btnRest).setOnClickListener {
                            RunMapSystem.restAtNode(runMap, state)
                            render()
                        }
                    }
                }
            }
            else -> {
                v.setBackgroundResource(R.drawable.bg_node_locked)
                status.text = "未解锁"
            }
        }
    }

    // ---------------- 战斗 / 事件 / 结局 ----------------

    /** 战斗节点：M2 起拉起战斗页，由 BattleEngine 真打一场 */
    private fun doBattle(node: RunNode) {
        val intent = Intent(this, BattleActivity::class.java).apply {
            putExtra(BattleActivity.EXTRA_HERO_ID, hero.id)
            putExtra(BattleActivity.EXTRA_ROUND, node.battleRound)
            putExtra(BattleActivity.EXTRA_STATE, state)
        }
        battleLauncher.launch(intent)
    }

    /** 战斗页回传：把引擎结果翻译成经济 / 掉落结算 */
    private fun onBattleFinished(r: ActivityResult) {
        val d = r.data
        if (r.resultCode != RESULT_OK || d == null) {
            render()   // 中途退出，保持原节点重来
            return
        }
        val round = state.battlesWon + 1
        val result = RunMapSystem.settleBattle(
            win = d.getBooleanExtra(BattleActivity.RESULT_WIN, false),
            hpAfter = d.getIntExtra(BattleActivity.RESULT_HP_AFTER, 0),
            round = round,
            playerPower = d.getIntExtra(BattleActivity.RESULT_PLAYER_POWER, 0),
            enemyPower = d.getIntExtra(BattleActivity.RESULT_ENEMY_POWER, 0),
            damageDealt = d.getIntExtra(BattleActivity.RESULT_DAMAGE_DEALT, 0),
            damageTaken = d.getIntExtra(BattleActivity.RESULT_DAMAGE_TAKEN, 0),
            ticks = d.getIntExtra(BattleActivity.RESULT_TICKS, 0),
            timeout = d.getBooleanExtra(BattleActivity.RESULT_TIMEOUT, false),
            enemyName = d.getStringExtra(BattleActivity.RESULT_ENEMY_NAME) ?: "",
            playerMaxHp = d.getIntExtra(BattleActivity.RESULT_PLAYER_MAX_HP, state.maxHp)
        )

        if (result.win) {
            val heal = state.maxHp * runMap.battleVictoryHealPct / 100
            RunMapSystem.winBattle(runMap, state, result)
            val summary = "对手 ${result.enemyName}\n" +
                "战力 ${result.playerPower} vs 敌 ${result.enemyPower}\n" +
                "造成 ${result.damageDealt} / 承受 ${result.damageTaken} 伤害\n" +
                "生命 ${result.hpAfter} → ${state.hp}（胜利回复 +$heal）\n" +
                "金币 +${result.goldGain}\n" +
                result.lootText
            if (state.finished) {
                render()
            } else {
                showSimple("⚔️ 第 $round 场 · 胜利", summary) { render() }
            }
        } else {
            val dropped = RunMapSystem.loseBattle(state)
            val dropText = if (dropped.isEmpty()) {
                "所幸没有携带物品，没有损失。"
            } else {
                "掉落的携带物：\n" + dropped.joinToString("\n") { "· $it" }
            }
            showSimple(
                "💀 第 $round 场 · 战败",
                "对手 ${result.enemyName}\n战力 ${result.playerPower} vs 敌 ${result.enemyPower}\n\n$dropText"
            ) { render() }
        }
    }

    private fun doEvent(node: RunNode) {
        val e = RunMapSystem.rollEvent(events, node.nodeOrder)
        val outcome = RunMapSystem.applyEvent(e, state, weapons, cards, schools)

        if (state.finished) {
            render()
            return
        }
        RunMapSystem.advance(runMap, state)
        showSimple("${e.emoji} ${e.name}", outcome.text) { render() }
    }

    private fun showEnding() {
        val (title, desc) = when (state.ending) {
            RunEnding.CLEAR -> {
                val mul = RunMapSystem.clearMultiplier(runMap, state)
                runMap.endings.clearTitle to
                    "${runMap.endings.clearDesc}\n\n" +
                    "胜场：${state.battlesWon} / ${runMap.totalBattles}\n" +
                    "金币：${state.gold}\n" +
                    "入袋战利品：${state.securedLoot.size} 件\n" +
                    "通关奖励倍率：×$mul"
            }
            RunEnding.DEAD -> runMap.endings.deadTitle to
                "${runMap.endings.deadDesc}\n\n" +
                "止步于：第 ${state.battlesWon + 1} 场\n" +
                "保留的入袋战利品：${state.securedLoot.size} 件"
            RunEnding.EXTRACT -> runMap.endings.extractTitle to
                "${runMap.endings.extractDesc}\n\n" +
                "已通过 ${state.battlesWon} 场\n" +
                "战利品：${state.securedLoot.size} 件\n" +
                "金币：${state.gold}"
            RunEnding.NONE -> "继续" to ""
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(desc)
            .setCancelable(false)
            .setPositiveButton("返回主菜单") { _, _ -> finish() }
            .setNegativeButton("查看地图") { _, _ -> updateHeader() }
            .show()
    }

    private fun showSimple(title: String, message: String, onDismiss: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("继续") { _, _ -> onDismiss() }
            .show()
    }
}
