package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.view.Gravity
import android.text.TextUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.equipment.EquipmentCombat
import kotlin.math.roundToInt

/** Native text can wrap independently of the illustrated battlefield. No simulation runs in Views. */
class BattleArenaView(context: Context) : LinearLayout(context) {
    private val enemy = FighterHud(context, false)
    private val player = FighterHud(context, true)
    private val scene = DuelIllustration(context)
    private val timer = GameUi.label(context, "", 13, GameUi.gold, true).apply { gravity = Gravity.CENTER }
    init {
        orientation = VERTICAL
        val screenDp = context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density
        val arenaHeight = (screenDp * .26f).toInt().coerceIn(150, 228)
        scene.background = GameUi.frame(context, GameUi.background, GameUi.gold)
        addView(scene, LayoutParams(-1, GameUi.dp(context, arenaHeight)))
        val hud = LinearLayout(context)
        hud.addView(player, LayoutParams(0, -2, 1f).apply { rightMargin = GameUi.dp(context, 3) })
        hud.addView(enemy, LayoutParams(0, -2, 1f).apply { leftMargin = GameUi.dp(context, 3) })
        addView(hud); addView(timer)
    }
    fun bind(next: DuelState, cues: List<DuelVisualCue> = emptyList()) {
        enemy.bind(next.enemy, next.tick); player.bind(next.player, next.tick); scene.bind(next, cues)
        timer.text = "${next.tick / 10}.${next.tick % 10} / 60 秒"
    }
}

private class FighterHud(context: Context, private val friendly: Boolean) : LinearLayout(context) {
    private val title = GameUi.label(context, "", 13, GameUi.gold, true)
    private val health = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(if (friendly) GameUi.green else GameUi.danger)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(GameUi.line)
    }
    private val vitals = GameUi.label(context, "", 12)
    private val mana = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(0xFF46B9E8.toInt())
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(GameUi.background)
    }
    private val shields = GameUi.label(context, "", 11, GameUi.gold)
    private val skill = HeroSkillHud(context)
    private val statuses = GameUi.label(context, "", 12, GameUi.green).apply {
        maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
    }
    private var allStatuses = emptyList<String>()
    private val strip = StatusStripView(context) { showStatuses() }
    init {
        orientation = VERTICAL; background = GameUi.frame(context)
        val pad = GameUi.dp(context, 8); setPadding(pad, pad, pad, pad)
        addView(title); addView(health, LayoutParams(-1, GameUi.dp(context, 10)))
        addView(mana, LayoutParams(-1, GameUi.dp(context, 8)))
        addView(vitals); addView(shields); addView(skill); addView(strip); addView(statuses)
        statuses.isFocusable = true
        statuses.setOnClickListener { showStatuses() }
    }
    private fun showStatuses() {
        AlertDialog.Builder(context).setTitle("${title.text} · 状态详情")
            .setMessage(allStatuses.joinToString("\n").ifEmpty { "当前没有持续状态" }).setPositiveButton("关闭", null).show()
    }
    fun bind(f: Fighter, tick: Int) {
        title.text = "${if (friendly) "我方" else "对手"} · ${FirstRunContent.hero(f.heroId).name}"
        health.progress = (f.hp / f.maxHp * 1000).toInt().coerceIn(0, 1000)
        mana.progress = if (f.maxMana > 0) (f.mana / f.maxMana * 1000).toInt().coerceIn(0, 1000) else 0
        vitals.text = "${f.hp.roundToInt()}/${f.maxHp.roundToInt()} HP\n${f.mana.roundToInt()}/${f.maxMana.roundToInt()} MP"
        shields.text = "护盾 通${f.shield.roundToInt()} · 物${f.physicalShield.roundToInt()} · 法${(f.magicShield + f.skyShield).roundToInt()}"
        shields.contentDescription = "通用护盾${f.shield.roundToInt()}，物理护盾${f.physicalShield.roundToInt()}，魔法护盾${(f.magicShield + f.skyShield).roundToInt()}"
        skill.bind(f)
        val entries = BattleStatusCatalog.entries(f, tick)
        allStatuses = entries.map { it.detail }
        val icons = com.example.douqiuqiu.ui.AppSettings.statusIcons(context)
        strip.visibility = if (icons && entries.isNotEmpty()) VISIBLE else GONE
        statuses.visibility = if (entries.isEmpty()) GONE else VISIBLE
        if (icons) strip.bind(entries)
        statuses.text = if (allStatuses.isEmpty()) "" else if (icons) "${entries.size}项状态 · 查看全部 ›" else "状态 ${allStatuses.size}  ›  ${allStatuses.joinToString("  ·  ")}"
        statuses.contentDescription = "${allStatuses.size}项状态，点按展开：${allStatuses.joinToString("；")}"
        contentDescription = "${title.text}，${vitals.text}，${shields.text}，${skill.contentDescription}"
    }
    private fun time(seconds: Double) = "${kotlin.math.ceil(seconds * 10).toInt() / 10.0}秒"
}

