package com.example.douqiuqiu.ui

import com.example.douqiuqiu.quest.*

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.equipment.*
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.ui.components.GameUi
import com.example.douqiuqiu.ui.components.BattleArenaView
import com.example.douqiuqiu.ui.components.LootPreviewView
import kotlin.math.roundToInt

/** Android single-run client. Every action/tick is persisted before it is displayed. */
class JourneyActivity : AppCompatActivity() {
    private lateinit var repository: PlayerRepository
    private lateinit var state: InventoryState
    private lateinit var content: LinearLayout
    private lateinit var shell: GameUi.Shell
    private var selectedChest: String? = null
    private var showingChestDetail = false
    private var selectedHero = "juggernaut"
    private var restoredHeroSelection = false
    private var carryGold = 0
    private var resumed = false
    private var ready = false
    private var failed = false
    private val handler = Handler(Looper.getMainLooper())
    private var battleText: TextView? = null
    private var debugClockLabel: TextView? = null
    private var arena: BattleArenaView? = null
    private var logText: TextView? = null
    private val battleItemButtons = mutableMapOf<Int, com.example.douqiuqiu.ui.components.BattleItemButton>()
    private var endingTask: Runnable? = null
    private var inspection: AlertDialog? = null
    private var lastLoopAt = 0L
    private var stepping = false
    private fun scheduleBattle() {
        handler.removeCallbacks(loop)
        if (resumed && !failed && !TestProfile.battlePaused(this) && state.progress.run?.stage == JourneyStage.BATTLE)
            handler.postDelayed(loop, 100)
    }
    private val watchdog = object : Runnable {
        override fun run() {
            if (!resumed || failed) return
            if (!stepping && !TestProfile.battlePaused(this@JourneyActivity) && state.progress.run?.stage == JourneyStage.BATTLE && android.os.SystemClock.uptimeMillis() - lastLoopAt > 1800) {
                BattleDiagnostics.record(this@JourneyActivity, "battle callback overdue; rescheduling persisted frame", state)
                lastLoopAt = android.os.SystemClock.uptimeMillis(); scheduleBattle()
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val ink = GameUi.ink
    private val accent = GameUi.gold
    private val loop = Runnable { advanceBattle() }
    private fun advanceBattle(singleStep: Boolean = false) {
            if (singleStep && (!TestProfile.active(this) || !TestProfile.battlePaused(this))) return
            if (!resumed || failed || stepping || state.progress.run?.stage != JourneyStage.BATTLE ||
                TestProfile.battlePaused(this) && !singleStep) return
            stepping = true
            val began = android.os.SystemClock.uptimeMillis()
            try {
            val before = state
            var frame: DuelFrame? = null
            val next = repository.journey.tick(before) { frame = it }
            val persisted = save(next, redraw = false)
            val oldRun = before.progress.run!!
            frame?.let { recorded -> oldRun.fight?.let { BattleRecorder.frame(this@JourneyActivity, oldRun.id, oldRun.round, it, recorded,
                if (singleStep) "debug_clock:step" else "tick", committed = persisted) } }
            if (persisted) {
                frame?.let { audioFrame ->
                    when {
                        audioFrame.state.won != null -> GameAudio.play(this@JourneyActivity, if (audioFrame.state.won == true) GameAudio.Cue.WIN else GameAudio.Cue.LOSE)
                        audioFrame.cues.any { it.kind == DuelCueKind.ULTIMATE_CAST } -> GameAudio.play(this@JourneyActivity, GameAudio.Cue.SKILL)
                        audioFrame.cues.any { it.kind == DuelCueKind.HIT } -> GameAudio.play(this@JourneyActivity, GameAudio.Cue.HIT)
                    }
                }
                if (state.progress.run?.stage == JourneyStage.BATTLE) updateBattle(frame?.cues.orEmpty())
                else if (frame != null && arena != null) {
                    // Settlement is already durable. Only the final visual impact gets a short tail.
                    arena?.bind(frame!!.state, frame!!.cues)
                    shell.accessory.removeAllViews()
                    shell.accessory.addView(GameUi.label(this@JourneyActivity, "战斗结束 · 正在显示结果", 14, GameUi.gold))
                    endingTask = Runnable { endingTask = null; if (resumed && !failed) render() }.also { handler.postDelayed(it, 900) }
                } else render()
                if (resumed && state.progress.run?.stage == JourneyStage.BATTLE) scheduleBattle()
            }
            } catch (e: Exception) {
                BattleDiagnostics.record(this@JourneyActivity, "battle tick failed; previous durable frame preserved", state, e)
                error(e)
            } finally {
                lastLoopAt = android.os.SystemClock.uptimeMillis(); stepping = false
                if (!failed && lastLoopAt - began > 500) BattleDiagnostics.record(this@JourneyActivity, "slow frame ${lastLoopAt - began}ms", state)
            }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ 起 targetSdk 33 以上系统返回不再回退到废弃的 onBackPressed，
        // 必须注册 OnBackInvokedCallback，否则详情页返回键会直接退出 Activity。
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { if (!handleBack()) finish() }
        }
        selectedChest = savedInstanceState?.getString("chest")
        showingChestDetail = savedInstanceState?.getBoolean("chestDetail") ?: false
        selectedHero = savedInstanceState?.getString("hero") ?: "juggernaut"
        restoredHeroSelection = savedInstanceState != null
        carryGold = savedInstanceState?.getInt("carryGold") ?: 0
        try { repository = PlayerRepository(this); ready = true }
        catch (e: Exception) { error(e) }
    }
    override fun onResume() {
        super.onResume(); handler.removeCallbacks(loop); resumed = true
        if (!ready || failed) return
        try {
            val loaded = repository.load()
            state = loaded.state
            if (!restoredHeroSelection && state.progress.run == null) {
                selectedHero = state.progress.activeHeroId
                restoredHeroSelection = true
            }
            com.example.douqiuqiu.ui.components.InspectionDialog.reconcile(this, state, repository.catalog.rules.definitions)?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
            render()
            // A fully collected node may have been saved just before the preparation screen closed.
            val loot = state.progress.run?.nodeLoot
            if (state.progress.run?.stage == JourneyStage.REWARD && loot != null && loot.remaining.isEmpty() &&
                loot.inspectionMs >= NodeLootRules.duration(loot, repository.catalog.rules.definitions)) {
                if (!apply(repository.journey.finishLoot(state))) return
            }
            resumeInspection()
            if (loaded.recoveredFromBackup) Toast.makeText(this, "已恢复上一次有效存档，请检查物资", Toast.LENGTH_LONG).show()
            lastLoopAt = android.os.SystemClock.uptimeMillis()
            handler.removeCallbacks(watchdog); handler.postDelayed(watchdog, 1000)
            if (state.progress.run?.stage == JourneyStage.BATTLE) scheduleBattle()
        } catch (e: Exception) { error(e) }
    }
    override fun onPause() { resumed = false; inspection?.dismiss(); inspection = null; handler.removeCallbacks(loop); handler.removeCallbacks(watchdog); endingTask?.let { handler.removeCallbacks(it) }; endingTask = null; super.onPause() }
    override fun onStop() { resumed = false; handler.removeCallbacks(loop); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("chestDetail", showingChestDetail)
        outState.putString("chest", selectedChest); outState.putString("hero", selectedHero); outState.putInt("carryGold", carryGold); super.onSaveInstanceState(outState)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!handleBack()) super.onBackPressed()
    }
    /** 宝箱详情页的返回逻辑：系统返回与页内返回按钮共用。返回 true 表示已消费。 */
    private fun handleBack(): Boolean {
        if (::state.isInitialized && state.progress.run == null && showingChestDetail) {
            showingChestDetail = false
            render()
            return true
        }
        return false
    }
    private fun save(next: InventoryState, redraw: Boolean = true): Boolean {
        return try {
            repository.store.save(next); state = next
            if (redraw) render()
            true
        } catch (e: Exception) {
            if (::state.isInitialized && state.progress.run?.stage == JourneyStage.BATTLE) BattleDiagnostics.record(this, "profile save failed", state, e)
            error(e); false
        }
    }
    private fun apply(result: InventoryResult, operation: String = "manual_operation"): Boolean {
        if (!result.ok) {
            state.progress.run?.takeIf { it.stage == JourneyStage.BATTLE }?.let { r -> r.fight?.let {
                BattleRecorder.frame(this, r.id, r.round, it, DuelFrame(it), "$operation:rejected:${result.error}", committed = false)
            } }
            Toast.makeText(this, result.error, Toast.LENGTH_LONG).show(); return false
        }
        val previous = state
        val wasBattle = state.progress.run?.stage == JourneyStage.BATTLE
        val completedTarget = state.progress.run?.takeIf { it.round == 5 && it.stage == JourneyStage.REWARD &&
            result.state.progress.run == null && result.state.progress.clears > state.progress.clears }?.target
        val staysInBattle = wasBattle && result.state.progress.run?.stage == JourneyStage.BATTLE
        if (completedTarget != null) try {
            com.example.douqiuqiu.ui.components.InspectionDialog.enqueue(this, "宝箱已开启 · 鉴定战利品", completedTarget.items.map {
                repository.catalog.rules.definitions.getValue(it.definitionId) to 2
            }, completedTarget.quality, result.state)
        } catch (e: Exception) { error(e); return false }
        val saved = save(result.state, redraw = !staysInBattle && completedTarget == null)
        if (saved) result.state.progress.run?.fight?.let { fight ->
            if (result.state.progress.run?.stage == JourneyStage.BATTLE) {
                val r = result.state.progress.run!!
                BattleRecorder.frame(this, r.id, r.round, previous.progress.run?.fight ?: fight, DuelFrame(fight),
                    if (wasBattle) operation else "battle_start")
            }
        }
        if (saved && completedTarget != null) {
            try {
                resumeInspection()
            } catch (e: Exception) { error(e) }
        }
        if (saved && staysInBattle) updateBattle()
        if (saved && !wasBattle && state.progress.run?.stage == JourneyStage.BATTLE) { lastLoopAt = android.os.SystemClock.uptimeMillis(); scheduleBattle() }
        return saved
    }
    private fun resumeInspection() {
        if (inspection != null) return
        if (com.example.douqiuqiu.ui.components.InspectionDialog.hasPending(this)) {
            shell.scroll.visibility = android.view.View.INVISIBLE
            shell.accessory.visibility = android.view.View.INVISIBLE
        }
        inspection = com.example.douqiuqiu.ui.components.InspectionDialog.resume(this, repository.catalog.rules.definitions, { error(it) }) {
            inspection = null; if (resumed && !failed) render()
        }
    }
    private fun appraise(chest: TargetChest) {
        val result = repository.journey.appraise(state, chest.id)
        if (!result.ok) { Toast.makeText(this, result.error, Toast.LENGTH_LONG).show(); return }
        val next = result.state.progress.chests.single { it.id == chest.id }
        val changed = next.items.indices.filter { next.items[it].reveal != chest.items[it].reveal }.map {
            repository.catalog.rules.definitions.getValue(next.items[it].definitionId) to next.items[it].reveal
        }
        try {
            com.example.douqiuqiu.ui.components.InspectionDialog.enqueue(this, "鉴定已开始", changed, chest.quality, result.state, chest.id, next.appraisals)
            if (save(result.state, redraw = false)) resumeInspection()
        } catch (e: Exception) { error(e) }
    }
    private fun error(e: Exception) {
        failed = true; handler.removeCallbacks(loop)
        AlertDialog.Builder(this).setTitle("无法继续本次操作").setMessage("${e.message ?: "存档失败"}\n未重置存档。请返回后重试。")
            .setCancelable(false).setPositiveButton("返回") { _, _ -> finish() }.show()
    }
    private fun render() {
        battleText = null; debugClockLabel = null; arena = null; logText = null; battleItemButtons.clear()
        shell = GameUi.screen(this, if (state.progress.run == null) { if (showingChestDetail) "宝箱详情" else "选择你的远征" } else "挑战进行中", "营地 ${state.gold} 金币  ·  本局 ${state.runGold} 金币", listOf(
            GameUi.Navigation("远征", true) { if (state.progress.run == null && showingChestDetail) { showingChestDetail = false; render() } else shell.scroll.smoothScrollTo(0, 0) },
            GameUi.Navigation("整备") {
                if (state.progress.run?.stage == JourneyStage.BATTLE) Toast.makeText(this, "开战后锁定，战斗结束才能调整", Toast.LENGTH_SHORT).show()
                else startActivity(Intent(this, InventoryActivity::class.java))
            },
            GameUi.Navigation("营地") { finish() }
        ))
        content = shell.body
        if (showEventResult()) return
        val run = state.progress.run
        if (run == null) home() else runScreen(run)
    }
    private fun home() {
        if (showingChestDetail) {
            val chest = state.progress.chests.find { it.id == selectedChest }
            if (chest != null) { chestDetail(chest); return }
            showingChestDetail = false
        }
        content.addView(com.example.douqiuqiu.ui.components.ExpeditionBannerView(this, selectedHero, "选择目标", "先看线索，再配装\n准备好后进入挑战"))
        if (state.progress.lastEnding.isNotBlank()) {
            val result = GameUi.panel(this, true)
            result.addView(GameUi.label(this, "上次远征 · 结算", 12, accent, true))
            result.addView(GameUi.label(this, state.progress.lastEnding, 20, ink, true))
            result.addView(GameUi.label(this, "营地金币 ${state.gold} · 安全箱物资已保留", 13, GameUi.muted))
            result.addView(GameUi.button(this, "查看带回物资") { startActivity(Intent(this, InventoryActivity::class.java).putExtra("preparationTab", 2)) })
            content.addView(result)
        }
        button("补给站与远征指南  ›") { showSupplyGuide() }
        if (state.progress.chests.isEmpty()) {
            button("查看下一批宝箱") { selectedChest = null; save(repository.journey.ensureBatch(state)) }
            inventoryButton(); return
        }
        text("选择目标宝箱", 20)
        text("任务节点 ${QuestProgression.completedNodes(state)}/24 · 已开放至${ChallengeBalance.name(QuestProgression.maxDifficulty(state))}，在整备工具中领取任务奖励", 13, GameUi.gold)
        if (state.progress.chests.none { it.id == selectedChest }) selectedChest = state.progress.chests.minByOrNull { it.ticket }?.id
        val strip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        state.progress.chests.forEach { chest ->
            strip.addView(com.example.douqiuqiu.ui.components.ChestChoiceView(
                this, chest, repository.catalog.rules.definitions, chest.id == selectedChest
            ) { selectedChest = chest.id; showingChestDetail = true; render() }, LinearLayout.LayoutParams(dp(144), -2).apply { rightMargin = dp(9) })
        }
        val carousel = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) }
        content.addView(carousel)
        carousel.post { carousel.scrollTo(state.progress.chests.indexOfFirst { it.id == selectedChest }.coerceAtLeast(0) * dp(122), 0) }
        text("点击宝箱查看线索、鉴定与入场费用", 13, GameUi.muted)
        inventoryButton()
    }
    private fun chestDetail(chest: TargetChest) {
            button("‹ 返回选择宝箱") { showingChestDetail = false; render() }
            text("${chest.theme.title} · ${chest.quality}色宝箱", 20, accent)
            val purpose = when (chest.theme) {
                ChestTheme.ARSENAL -> "偏向装备与合成组件"
                ChestTheme.ARCANE -> "偏向技能卡"
                ChestTheme.BACKPACK -> "偏向背包扩容材料"
                ChestTheme.SKILLS -> "偏向技能构筑与技能栏成长材料"
                ChestTheme.SAFE -> "偏向安全箱成长材料；大件可能无法装入当前安全箱"
                ChestTheme.SUPPLIES -> "偏向恢复道具与通用成长材料"
            }
            text(purpose, 14)
            text("${ChallengeBalance.name(chest.difficulty)} · 第${chest.difficulty + 1}/6档", 14, GameUi.gold)
            text(if (chest.difficulty == 0) "基础组件与成长物资；敌方装备情报完整"
                else "敌方逐战强化，主动装备增多；越高难度侦察越不完整。成装与卷轴按难度分层投放。", 12)
            preview(chest)
            val standardCapacity = ChestLayout.capacity(chest.quality)
            val actualCapacity = ChestLayout.display(chest, repository.catalog.rules.definitions)
            if (actualCapacity.columns != standardCapacity.columns || actualCapacity.rows != standardCapacity.rows)
                text("旧版宝箱布局 · 保留原有物品位置，新生成同档箱为${standardCapacity.columns}×${standardCapacity.rows}", 12)
            button("随机鉴定 · ${EconomyBalance.appraisalFee(chest.appraisals)}金币（同时提高入场价）") { appraise(chest) }
            button("目标管理 · 更换宝箱与补给  ›") { showSupplyGuide(chest) }
            val subsidy = if (state.progress.economy.vouchers > 0 && EconomyBalance.subsidized(chest)) minOf(EconomyBalance.RELIEF_SUBSIDY, chest.ticket) else 0
            text("出战：${FirstRunContent.hero(state.progress.activeHeroId).name} · 出发时确认英雄与配置", 13, GameUi.muted)
            val locked = QuestProgression.lockedReason(state, chest.difficulty)
            if (locked != null) text(locked + "；请先完成整备工具中的委托任务。", 14, GameUi.gold)
            else primaryAction("进入挑战 · 共${chest.ticket.toLong() - subsidy + carryGold}金币") {
                departure(chest, subsidy)
            }
    }
    private fun showSupplyGuide(chest: TargetChest? = null) {
        val sheet = com.example.douqiuqiu.ui.components.GameCatalogSheet(this, if (chest == null) "补给站与远征指南" else "目标管理", "免费次数与救济随时间恢复", "返回选箱", stoneHeader = true)
        fun label(value: String) { sheet.body.addView(GameUi.label(this, value, 14)) }
        val now = System.currentTimeMillis().coerceAtLeast(0)
        val economy = state.progress.economy
        label("免费刷新 ${economy.refreshCharges}/3 · ${countdown(repository.economy.refreshCountdown(state, now))}")
        label("救济 ${economy.reliefCharges}/2 · ${countdown(repository.economy.reliefCountdown(state, now))}\n入场补助 ${economy.vouchers} 张")
        if (chest != null) {
            label("更换保留难度，重新生成内容、品质和票价。鉴定进度清空，费用不退。")
            val fee = EconomyBalance.chestRefresh(chest.quality)
            sheet.body.addView(GameUi.button(this, "更换此箱 · ${if (economy.refreshCharges > 0) "使用1次免费刷新" else "${fee}金币"}") {
                val index = state.progress.chests.indexOfFirst { it.id == chest.id }
                val result = repository.economy.refreshChest(state, chest.id, System.currentTimeMillis().coerceAtLeast(0))
                if (result.ok) selectedChest = result.state.progress.chests.getOrNull(index)?.id
                if (apply(result)) sheet.dialog.dismiss()
            })
        }
        label("破产救济\n金币不足本批最低票价，且全部可售物资回收总值低于120金币时可领。每次获得绑定阔剑×1、药膏×2及低成本白箱入场补助×1。补助最多抵扣${EconomyBalance.RELIEF_SUBSIDY}金币，成功入场才消耗。")
        label(repository.economy.reliefUnavailable(state) ?: "当前符合领取资格")
        sheet.body.addView(GameUi.button(this, "领取救济") {
            if (apply(repository.economy.claimRelief(state, System.currentTimeMillis().coerceAtLeast(0)))) sheet.dialog.dismiss()
        })
        sheet.body.addView(GameUi.button(this, "更新恢复次数") {
            if (save(repository.economy.recover(state, System.currentTimeMillis().coerceAtLeast(0)))) { sheet.dialog.dismiss(); showSupplyGuide(chest) }
        })
        label("远征规则\n第三战后可撤离；通关五战获得目标宝箱。死亡失去安全箱外的携带物品与本局金币。鉴定可增加线索，也会提高门票价格。")
        sheet.show()
    }
    private fun departure(chest: TargetChest, subsidy: Int) {
        var entered = false
        com.example.douqiuqiu.ui.components.DepartureSheet(this, repository.catalog.rules.definitions,
            { state }, "${ChallengeBalance.name(chest.difficulty)} · ${chest.theme.title} · ${chest.quality}色宝箱", chest.ticket, subsidy, carryGold,
            summary = {
                val fighter = repository.journey.fighter(state, state.progress.activeHeroId)
                "生命 ${fighter.maxHp.roundToInt()} · 攻击 ${fighter.attack.roundToInt()}"
            },
            select = { heroId, slot ->
                val loadouts = LoadoutRules(repository.catalog.rules)
                val result = if (slot == null) loadouts.activateHero(state, heroId) else loadouts.apply(state, heroId, slot)
                if (!result.ok) { Toast.makeText(this, result.error, Toast.LENGTH_LONG).show(); false }
                else if (save(result.state, redraw = false)) {
                    result.notice?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                    selectedHero = heroId; true
                } else false
            },
            depart = { gold ->
                if (entered || state.progress.run != null) false else {
                    carryGold = gold
                    val accepted = apply(repository.journey.enter(state, chest.id, state.progress.activeHeroId, carryGold), "departure_confirm")
                    if (accepted) entered = true
                    accepted
                }
            },
            organize = { startActivity(Intent(this, InventoryActivity::class.java)) },
            closed = { if (!failed) render() }
        ).show()
    }

    private fun runScreen(r: JourneyState) {
        // Battle has its own compact composition. Preparation headings must not push half the arena below the fixed action tray.
        if (r.stage == JourneyStage.BATTLE) {
            text("第 ${r.round} / 5 战", 13, GameUi.gold)
            debugClockControls()
            arena = BattleArenaView(this).also { content.addView(it, LinearLayout.LayoutParams(-1, -2)) }
            if (AppSettings.battleLog(this)) logText = text("", 12)
            battleActions(r); updateBattle()
            return
        }
        val route = LinearLayout(this)
        for (round in 1..5) route.addView(GameUi.label(this, if (round < r.round) "✓" else round.toString(), 17,
            if (round <= r.round) GameUi.gold else GameUi.muted, true).apply {
            gravity = Gravity.CENTER; minimumHeight = dp(44)
            background = GameUi.shape(this@JourneyActivity, if (round == r.round) GameUi.raised else GameUi.surface,
                if (round == r.round) GameUi.gold else GameUi.line, 10)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(12), dp(3), dp(6)) })
        content.addView(route)
        text("${FirstRunContent.hero(r.heroId).name} · 第${r.round}/5战", 21, accent)
        text("生命 ${r.hp.roundToInt()} / ${r.maxHp.roundToInt()}　${r.target.theme.title}", 13)
        if (r.stage == JourneyStage.EVENT || r.stage == JourneyStage.REST) {
            val scene = if (r.stage == JourneyStage.REST) com.example.douqiuqiu.ui.components.SceneKind.REST
                else com.example.douqiuqiu.ui.components.SceneArtwork.event(r.nodeEvent?.kind)
            content.addView(com.example.douqiuqiu.ui.components.SceneArtworkView(this, scene), LinearLayout.LayoutParams(-1, dp(174)))
        }
        when (r.stage) {
            JourneyStage.READY -> {
                val build = EnemyBuilds.preview(r, repository.catalog.rules.definitions)
                val enemy = FirstRunContent.hero(build.heroId)
                val school = com.example.douqiuqiu.content.School.byId(build.schoolId).label
                text("下场对手：${enemy.name}\n主要流派：$school", 18)
                GameUi.heroBanner(content, enemy.id, "下一场对手 · $school", "击败对手获得金币，并搜集掉落物")
                com.example.douqiuqiu.ui.components.EnemyIntelPanel.add(this, content, build, r.target.difficulty, repository.catalog.rules.definitions)
                text("生命延续，魔法重置为初始值，英雄技能就绪。开战后不能换装。", 14)
                inventoryButton()
                primaryAction("开始第 ${r.round} 战") { apply(repository.journey.startBattle(state)) }
            }
            JourneyStage.BATTLE -> {
                arena = BattleArenaView(this).also { content.addView(it, LinearLayout.LayoutParams(-1, -2)) }
                if (AppSettings.battleLog(this)) {
                    text("战斗记录 · 最近事件", 16, GameUi.gold)
                    logText = text("", 15)
                }
                battleActions(r)
                updateBattle()
            }
            JourneyStage.REWARD -> {
                val loot = r.nodeLoot
                text(if (loot?.source == NodeLootSource.EVENT) "事件收获" else "第 ${r.round} 战 · 胜利", 23, accent)
                if (loot != null) {
                    if (loot.goldAwarded > 0) text("获得${loot.goldAwarded}本局金币 · 当前${state.runGold}金币", 15, GameUi.gold)
                    text("待拾取 ${loot.remaining.size} / ${loot.items.size} 件 · 未装入物品不属于本局物资", 14)
                    primaryAction("鉴定掉落与整理背包") { openNodeLoot() }
                    button(if (r.round == 5) "完成挑战并领取目标宝箱" else "离开当前节点") { leaveLoot() }
                    if (r.round == 5) text("途中掉落需手动拾取；最终目标宝箱直接入库。", 13)
                } else button("恢复节点掉落") { save(repository.journey.battleLoot(state)) }
            }
            JourneyStage.EVENT -> {
                if (r.nodeEvent != null) {
                    eventChoices(r)
                } else {
                text("废弃补给站", 21)
                text("搜索可能获得物品，也可能受伤。低生命时可能死亡。背包放不下可先整理或直接离开。", 15)
                inventoryButton()
                button("搜索补给") {
                    presentEventResult(state, repository.journey.event(state, true), "废弃补给站", "搜索补给")
                }
                button("放弃搜索，继续前进") { presentEventResult(state, repository.journey.event(state, false), "废弃补给站", "放弃搜索") }
                }
            }
            JourneyStage.REST -> {
                text("第三战后的安全节点", 21)
                text("撤离保住现有物资与金币；继续休息恢复35%最大生命，再挑战后两战。目标宝箱只在全部通关后获得。", 15)
                inventoryButton()
                button("带收益撤离") { apply(repository.journey.extract(state)) }
                button("免费休息并继续") { apply(repository.journey.rest(state)) }
            }
        }
        button("放弃本局（按死亡结算）") {
            AlertDialog.Builder(this).setTitle("放弃挑战？").setMessage("安全箱外的携带物品、本局物品及本局金币全部失去。")
                .setNegativeButton("继续挑战", null).setPositiveButton("确认放弃") { _, _ -> handler.removeCallbacks(loop); apply(repository.journey.abandon(state)) }.show()
        }
    }
    /** 战斗日志可读化：控制类的内部 sourceId 转中文；无法识别的行原样返回。journal 仍保留原始行。 */
    private fun friendlyLog(line: String): String {
        val m = Regex("^控制\\[(.+)]\\s+(\\w+)").find(line) ?: return line
        val parts = m.groupValues[1].split(":")
        val effect = parts.last().substringAfter('.')
        val side = when {
            parts.any { it == "enemy" } -> "对手"
            parts.any { it == "self" || it == "player" || it == "own" } -> "我方"
            else -> ""
        }
        val name = controlNames[effect] ?: effect
        val phase = when (m.groupValues[2]) {
            "EXPIRED" -> "结束"; "IMMUNITY_ENDED" -> "免疫结束"; "APPLIED" -> "生效"; else -> m.groupValues[2]
        }
        return "$side$name$phase"
    }
    private val controlNames = mapOf(
        "silence" to "沉默", "stun" to "眩晕", "disarm" to "缴械", "hex" to "妖术",
        "fear" to "恐惧", "taunt" to "嘲讽", "break" to "破坏", "mute" to "静音",
        "root" to "束缚", "slow" to "减速", "blind" to "致盲", "sleep" to "催眠",
        "suppress" to "压制", "cyclone" to "吹风"
    )
    private fun updateBattle(cues: List<DuelVisualCue> = emptyList()) {
        val fight = state.progress.run?.fight ?: return
        debugClockLabel?.text = "调试时钟：${if (TestProfile.battlePaused(this)) "暂停" else "运行"} · 帧 ${fight.tick} · ${fight.tick * 100}ms"
        arena?.bind(fight, cues)
        fun line(f: Fighter): String = "${FirstRunContent.hero(f.heroId).name}\n生命 ${f.hp.roundToInt()}/${f.maxHp.roundToInt()} · 魔法 ${f.mana.roundToInt()}/${f.maxMana.roundToInt()}\n通用盾 ${f.shield.roundToInt()} · 物理盾 ${f.physicalShield.roundToInt()} · 魔法盾 ${(f.magicShield + f.skyShield).roundToInt()}"
        battleText?.text = "${fight.tick / 10}.${fight.tick % 10} / 60 秒\n\n${line(fight.player)}\n\n${line(fight.enemy)}"
        logText?.text = fight.log.takeLast(4).joinToString("\n") { friendlyLog(it) }
        val effects = EquipmentCombat.effects(fight.player.equipment)
        battleItemButtons.forEach { (slot, button) ->
            val item = state.items.find { it.place.container == Container.EQUIPMENT && it.place.x == slot }
            val consumable = item?.definitionId in setOf("faerie_fire", "cheese")
            val itemEffects = effects.filter { it.item.instanceId == item?.instanceId }
            val effect = itemEffects.firstOrNull {
                it.effect.trigger == com.example.douqiuqiu.content.EffectTrigger.ACTIVE || it.effect.trigger == com.example.douqiuqiu.content.EffectTrigger.LOW_HP
            }
            val remaining = effect?.let { fight.player.equipment.cooldowns.remainingByGroup[it.group] } ?: 0L
            val base = itemEffects.maxOfOrNull { it.effect.internalCooldownMs ?: 0 } ?: 0L
            val total = if (effect == null) 0L else (EquipmentCombat.cooldown(fight.player.equipment,
                base.coerceAtLeast(if (effect.group == "armlet") 60000L else 500L) / 1000.0) * 1000).toLong()
            val hasAction = item != null && (consumable || effect != null)
            val permitted = FighterEffects.permission(fight.player, if (consumable) com.example.douqiuqiu.status.CombatAction.CONSUMABLE
                else com.example.douqiuqiu.status.CombatAction.ACTIVE_ITEM)
            val alreadyActiveArmlet = effect?.group == "armlet" && EquipmentCombat.active(fight.player.equipment, "armlet")
            val manual = item?.instanceId in state.progress.manualItems
            button.bind(item?.let { repository.catalog.rules.definitions[it.definitionId] }, hasAction && permitted && remaining == 0L && !alreadyActiveArmlet,
                hasAction, hasAction && !manual, remaining, total, item?.count ?: 0, manual)
        }
    }

    private fun debugClockControls() {
        if (!TestProfile.active(this)) return
        debugClockLabel = text("", 12, GameUi.gold)
        val row = LinearLayout(this)
        listOf("暂停" to {
            setDebugClockPaused(true)
        }, "单步100ms" to {
            if (TestProfile.battlePaused(this)) {
                handler.removeCallbacks(loop)
                advanceBattle(singleStep = true)
            } else Toast.makeText(this, "请先暂停调试时钟", Toast.LENGTH_SHORT).show()
        }, "继续" to {
            setDebugClockPaused(false)
        }).forEach { (title, action) ->
            row.addView(GameUi.button(this, title, false, action), LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(row)
    }
    private fun setDebugClockPaused(paused: Boolean) {
        if (!TestProfile.active(this) || state.progress.run?.stage != JourneyStage.BATTLE) return
        try {
            TestProfile.setBattlePaused(this, paused)
            handler.removeCallbacks(loop)
            val r = state.progress.run!!
            r.fight?.let { BattleRecorder.frame(this, r.id, r.round, it, DuelFrame(it),
                if (paused) "debug_clock:pause" else "debug_clock:resume") }
            lastLoopAt = android.os.SystemClock.uptimeMillis()
            updateBattle()
            if (!paused) scheduleBattle()
        } catch (e: Exception) { error(e) }
    }
    private fun battleActions(r: JourneyState) {
        shell.accessory.setPadding(dp(8), dp(4), dp(8), dp(4))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        repeat(6) { slot ->
            val action = com.example.douqiuqiu.ui.components.BattleItemButton(this)
            fun item() = state.items.find { it.place.container == Container.EQUIPMENT && it.place.x == slot }
            fun details() {
                val stored = item() ?: return
                val definition = repository.catalog.rules.definitions.getValue(stored.definitionId)
                val description = EquipmentCombat.definitions[definition.id]?.description.orEmpty()
                AlertDialog.Builder(this).setTitle(definition.name).setMessage("${action.contentDescription}\n\n$description")
                    .setPositiveButton("关闭", null).show()
            }
            action.onDetails = { details() }
            action.setOnClickListener {
                if (state.progress.run?.stage != JourneyStage.BATTLE) return@setOnClickListener
                val stored = item() ?: return@setOnClickListener
                if (!action.readyForUse) { details(); return@setOnClickListener }
                AppSettings.feedback(action, android.view.HapticFeedbackConstants.CLOCK_TICK)
                GameAudio.play(this, GameAudio.Cue.CLICK)
                if (stored.definitionId in setOf("faerie_fire", "cheese"))
                    apply(repository.journey.useConsumable(state, stored.instanceId), "use_consumable:${stored.instanceId}")
                else apply(repository.journey.useEquipment(state, stored.instanceId), "use_equipment:${stored.instanceId}")
            }
            action.setOnLongClickListener {
                val stored = item() ?: return@setOnLongClickListener false
                if (state.progress.run?.stage != JourneyStage.BATTLE) return@setOnLongClickListener false
                val usable = stored.definitionId in setOf("faerie_fire", "cheese") ||
                    EquipmentCombat.activeItems(state.progress.run!!.fight!!.player.equipment).any { it.instanceId == stored.instanceId }
                if (!usable) { details(); return@setOnLongClickListener true }
                val old = state.progress.manualItems
                val saved = save(state.copy(progress = state.progress.copy(manualItems =
                    if (stored.instanceId in old) old - stored.instanceId else old + stored.instanceId)), redraw = false)
                if (saved) {
                    state.progress.run?.fight?.let { snap -> BattleRecorder.frame(this, r.id, r.round, snap, DuelFrame(snap),
                        "mode:${stored.instanceId}:${if (stored.instanceId in state.progress.manualItems) "manual" else "automatic"}") }
                    Toast.makeText(this, "${repository.catalog.rules.definitions.getValue(stored.definitionId).name} · ${if (stored.instanceId in state.progress.manualItems) "手动" else "自动"}", Toast.LENGTH_SHORT).show()
                }
                updateBattle(); true
            }
            battleItemButtons[slot] = action
            row.addView(action, LinearLayout.LayoutParams(0, dp(60), 1f))
        }
        shell.accessory.addView(row)
    }

    private fun rewardCard(title: String, detail: String, action: String, color: Int, art: String = "BOX", choose: () -> Unit) {
        val card = GameUi.panel(this)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        com.example.douqiuqiu.ui.components.GameArt.item(this, art)?.let { bitmap ->
            row.addView(com.example.douqiuqiu.ui.components.GameArt.image(this, bitmap, title).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(68), dp(68)))
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(GameUi.label(this@JourneyActivity, title, 19, color, true))
            addView(GameUi.label(this@JourneyActivity, detail, 14, GameUi.muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        card.addView(GameUi.button(this, action, block = choose))
        card.setOnClickListener { choose() }
        content.addView(card)
    }
    private fun eventChoices(r: JourneyState) {
        val node = r.nodeEvent ?: return
        val choicesPanel = GameUi.paper(this)
        choicesPanel.addView(GameUi.label(this, EventCatalog.definition(node.kind).title, 23, GameUi.paperInk, true))
        choicesPanel.addView(GameUi.label(this, "选择你的下一步", 12, GameUi.paperInk))
        content.addView(choicesPanel)
        JourneyEvents.views(state, repository.catalog.rules).forEach { view ->
            val cost = if (view.option.cost is EventCost.Gold) " · ${node.priceGold}本局金币" else ""
            val choosing = view.option.requiresExplicitItemChoice
            val usable = view.enabled || view.blockedReason == EventBlockReason.ITEM_CHOICE_REQUIRED
            val suffix = when {
                usable -> ""
                view.blockedReason == EventBlockReason.INSUFFICIENT_GOLD -> "（本局金币不足）"
                else -> "（未满足条件）"
            }
            val optionButton = GameUi.button(this, view.option.label + cost + suffix) {
                if (!choosing) resolveEvent(view.option.id)
                else chooseEventItem(view.option.id)
            }.apply { isEnabled = usable; alpha = if (usable) 1f else .45f }
            choicesPanel.addView(optionButton)
        }
        inventoryButton()
    }
    private fun eventReceipt() = getSharedPreferences(if (TestProfile.active(this)) "test-event-result-v1" else "event-result-v1", MODE_PRIVATE)
    private fun resolveEvent(optionId: String, selection: EventItemSelection? = null) {
        val before = state
        val run = before.progress.run ?: return
        val node = run.nodeEvent ?: return
        val result = repository.journey.event(before, optionId, selection)
        val option = EventCatalog.definition(node.kind).options.first { it.id == optionId }
        presentEventResult(before, result, EventCatalog.definition(node.kind).title, option.label, selection,
            com.example.douqiuqiu.ui.components.ExpansionArtwork.eventKey(node.kind.name, optionId))
    }
    private fun presentEventResult(before: InventoryState, result: InventoryResult, title: String, choice: String, selection: EventItemSelection? = null, artKey: String = "") {
        if (!result.ok) { apply(result); return }
        val run = before.progress.run ?: return
        val next = result.state
        val hp = next.progress.run?.hp ?: 0.0
        fun signed(value: Int) = if (value > 0) "+$value" else value.toString()
        fun life(value: Double) = if (kotlin.math.abs(value - value.roundToInt()) < .0001) value.roundToInt().toString()
            else String.format(java.util.Locale.ROOT, "%.1f", value)
        val hpDelta = hp - run.hp
        val drops = next.progress.run?.nodeLoot?.items.orEmpty()
        val delivery = selection?.let { chosen -> before.items.find { it.instanceId == chosen.instanceId } }?.let {
            "\n交付：${repository.catalog.rules.definitions.getValue(it.definitionId).name} ×1${if (it.place.container == Container.SAFE) "（安全箱）" else ""}"
        }.orEmpty()
        val lost = if (next.progress.run == null) before.items.mapNotNull { item ->
            val remaining = next.items.find { it.instanceId == item.instanceId }?.count ?: 0
            (item.count - remaining).takeIf { it > 0 }?.let { count -> "${repository.catalog.rules.definitions.getValue(item.definitionId).name} ×$count" }
        } else emptyList()
        val detail = "$choice\n\n生命 ${life(run.hp)} → ${life(hp)} / ${life(run.maxHp)}（${if (hpDelta > 0) "+" else ""}${life(hpDelta)}）" +
            "\n本局金币 ${before.runGold} → ${next.runGold}（${signed(next.runGold - before.runGold)}）" + delivery +
            "\n" + (if (next.progress.run == null) "远征结束：${next.progress.lastEnding}\n失去物资：${lost.joinToString().ifEmpty { "无" }}" else if (drops.isNotEmpty())
                "发现 ${drops.size} 件待鉴定物品\n物品尚未获得，继续后鉴定并拾取。" else "本次没有物品掉落，结果已保存。")
        // Write the presentation receipt first. Only an exact committed state can display it.
        val receipt = org.json.JSONObject().put("owner", com.example.douqiuqiu.ui.components.InspectionOwner.key(next))
            .put("title", title + " · 结果").put("detail", detail).put("runId", next.progress.run?.id ?: "")
            .put("ending", next.progress.lastEnding).put("committed", false).put("artKey", artKey)
            .put("profile", if (TestProfile.active(this)) "test:${TestProfile.attempt(this)}" else "production")
            .put("scene", com.example.douqiuqiu.ui.components.SceneArtwork.event(run.nodeEvent?.kind).name)
        try {
            check(eventReceipt().edit().putString("receipt", receipt.toString()).commit()) { "事件结果保存失败" }
            if (apply(result)) {
                receipt.put("committed", true)
                check(eventReceipt().edit().putString("receipt", receipt.toString()).commit()) { "事件结果确认保存失败" }
            }
        } catch (e: Exception) { error(e) }
    }
    private fun showEventResult(): Boolean {
        val raw = eventReceipt().getString("receipt", null) ?: return false
        val record = try { org.json.JSONObject(raw) } catch (_: Exception) {
            check(eventReceipt().edit().remove("receipt").commit()) { "事件演出清理失败" }; return false
        }
        val profile = if (TestProfile.active(this)) "test:${TestProfile.attempt(this)}" else "production"
        if (record.optString("profile") != profile) {
            check(eventReceipt().edit().remove("receipt").commit()) { "事件演出清理失败" }; return false
        }
        val exact = record.optString("owner") == com.example.douqiuqiu.ui.components.InspectionOwner.key(state)
        val committedRun = record.optBoolean("committed") && record.optString("runId") == (state.progress.run?.id ?: "") &&
            record.optString("ending") == state.progress.lastEnding
        if (!exact && !committedRun) {
            check(eventReceipt().edit().remove("receipt").commit()) { "事件演出清理失败" }; return false
        }
        // Recovery after a crash between the profile commit and the presentation commit.
        // Exact ownership is sufficient evidence; later equipment edits must not hide this result.
        if (exact && !record.optBoolean("committed")) {
            record.put("committed", true)
            check(eventReceipt().edit().putString("receipt", record.toString()).commit()) { "事件结果确认保存失败" }
        }
        val scene = com.example.douqiuqiu.ui.components.SceneKind.entries.firstOrNull { it.name == record.optString("scene") }
            ?: com.example.douqiuqiu.ui.components.SceneKind.REST
        content.addView(com.example.douqiuqiu.ui.components.EventOutcomeArtworkView(this, record.optString("artKey"), scene), LinearLayout.LayoutParams(-1, dp(184)))
        val panel = GameUi.panel(this, true)
        panel.addView(GameUi.label(this, record.optString("title"), 23, GameUi.gold, true))
        panel.addView(GameUi.label(this, record.optString("detail"), 16))
        content.addView(panel)
        primaryAction(if (state.progress.run?.nodeLoot != null) "前往鉴定与拾取" else "确认结果并继续") {
            try {
                check(eventReceipt().edit().remove("receipt").commit()) { "事件结果确认失败" }
                render()
                if (state.progress.run?.nodeLoot != null) openNodeLoot()
            } catch (e: Exception) { error(e) }
        }
        return true
    }
    private fun chooseEventItem(optionId: String) {
        val choices = state.items.filter { item -> item.place.container != Container.WAREHOUSE &&
            item.instanceId !in state.progress.lockedItems &&
            JourneyEvents.views(state, repository.catalog.rules, EventItemSelection(item.instanceId, item.place.container == Container.SAFE))
                .any { it.option.id == optionId && it.enabled } }
        val sheet = com.example.douqiuqiu.ui.components.GameCatalogSheet(this, "选择交付物品", "只消耗明确选中的1份 · 已锁定物品不参与交付", "保留物品", stoneHeader = true)
        var selected: StoredItem? = null
        val rows = mutableMapOf<String, LinearLayout>()
        val confirmation = GameUi.label(this, "先点选一件物品", 14, GameUi.gold, true)
        val confirm = GameUi.button(this, "确认交付选中物品", true) {
            val item = selected ?: return@button
            if (item.instanceId in state.progress.lockedItems) return@button
            sheet.dialog.dismiss()
            resolveEvent(optionId, EventItemSelection(item.instanceId, item.place.container == Container.SAFE))
        }.apply { isEnabled = false }
        choices.forEach { item ->
            val definition = repository.catalog.rules.definitions.getValue(item.definitionId)
            val card = GameUi.panel(this)
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(object : android.view.View(this) {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas: android.graphics.Canvas) {
                    com.example.douqiuqiu.ui.components.ItemArtwork.draw(context, canvas, definition,
                        android.graphics.RectF(4f, 4f, width - 4f, height - 4f), paint)
                }
            }, LinearLayout.LayoutParams(dp(60), dp(60)))
            val container = when (item.place.container) { Container.SAFE -> "安全箱"; Container.EQUIPMENT -> "装备栏"; Container.SKILLS -> "技能栏"; else -> "背包" }
            row.addView(GameUi.label(this, "${definition.name} ×${item.count}\n$container · 位置${item.place.x + 1},${item.place.y + 1}\n实例 …${item.instanceId.takeLast(8)}", 13), LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            card.setOnClickListener {
                selected = item
                rows.forEach { (id, panel) -> panel.background = GameUi.shape(this, GameUi.surface, if (id == item.instanceId) GameUi.gold else GameUi.line, 12) }
                confirmation.text = "已选：${definition.name} ×1 · $container${if (item.place.container == Container.SAFE) "\n确认即授权消耗安全箱中的这一份" else ""}"
                confirm.isEnabled = true
            }
            rows[item.instanceId] = card; sheet.body.addView(card)
        }
        if (choices.isEmpty()) sheet.body.addView(GameUi.label(this, "没有符合要求的未锁定物品，可返回选择其他路径。", 15))
        sheet.actions.addView(confirmation); sheet.actions.addView(confirm); sheet.show()
    }
    private fun preview(chest: TargetChest) {
        val view = LootPreviewView(this, chest, repository.catalog.rules.definitions)
        text("战利品预览 · ${view.columns}×${view.rows}格 · ${chest.items.size}件线索", 14, GameUi.gold)
        content.addView(view, LinearLayout.LayoutParams(-1, -2))
        text("点按轮廓查看已知线索", 12, GameUi.muted)
    }
    private fun category(kind: ItemKind?) = when (kind) { ItemKind.EQUIPMENT -> "装备"; ItemKind.CARD -> "技能卡"; ItemKind.SCROLL -> "卷轴"; ItemKind.CONSUMABLE -> "补给品"; ItemKind.MATERIAL -> "成长材料"; ItemKind.LOOT -> "战利品"; else -> "物品" }
    private fun countdown(ms: Long?): String = if (ms == null) "已满" else "约${(ms + 59_999) / 60_000}分钟恢复1次"
    private fun openNodeLoot() { startActivity(Intent(this, InventoryActivity::class.java).putExtra("hero", state.progress.run?.heroId ?: selectedHero).putExtra("preparationTab", 2)) }
    private fun leaveLoot() {
        val loot = state.progress.run?.nodeLoot ?: return
        if (loot.inspectionMs < NodeLootRules.duration(loot, repository.catalog.rules.definitions)) { openNodeLoot(); return }
        if (loot.remaining.isEmpty()) { apply(repository.journey.finishLoot(state)); return }
        AlertDialog.Builder(this).setTitle("丢弃剩余掉落并离开？")
            .setMessage("还有${loot.remaining.size}件物品未拾取，离开后无法取回。已经装入容器的物品保留。")
            .setNegativeButton("继续整理") { _, _ -> openNodeLoot() }
            .setPositiveButton("确认丢弃并继续") { _, _ -> apply(repository.journey.finishLoot(state, true)) }.show()
    }
    private fun inventoryButton() { button("整理装备、技能卡与背包") { startActivity(Intent(this, InventoryActivity::class.java).putExtra("hero", selectedHero)) } }
    private fun text(value: String, size: Int, color: Int = ink): TextView = TextView(this).apply {
        text = value; textSize = size * AppSettings.textScale(this@JourneyActivity); setTextColor(color); setPadding(0, dp(10), 0, dp(7)); content.addView(this)
    }
    private fun button(label: String, block: () -> Unit): Button = GameUi.button(this, label, block = block).also { content.addView(it) }
    private fun primaryAction(label: String, block: () -> Unit) {
        shell.accessory.addView(GameUi.button(this, label, true, block))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
