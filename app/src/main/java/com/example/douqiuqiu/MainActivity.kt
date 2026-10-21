package com.example.douqiuqiu

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.PlayerRepository
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.ui.InventoryActivity
import com.example.douqiuqiu.ui.JourneyActivity
import com.example.douqiuqiu.ui.components.GameUi

/** Camp and preparation read the same persisted profile. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        try { render() } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("暂时无法打开营地").setMessage("${e.message ?: "读取失败"}\n原存档保留，不会重置。")
                .setPositiveButton("重试") { _, _ -> recreate() }.setNegativeButton("关闭") { _, _ -> finish() }.show()
        }
    }
    private fun render() {
        val repo = PlayerRepository(this)
        val loaded = repo.load()
        val state = loaded.state
        val run = state.progress.run
        val shell = GameUi.screen(this, "远征营地", "Infinite Strife  ·  单人远征", listOf(
            GameUi.Navigation("远征") { open(JourneyActivity::class.java) },
            GameUi.Navigation("整备") { open(InventoryActivity::class.java) },
            GameUi.Navigation("营地", true) { }
        ))
        val body = shell.body
        body.addView(com.example.douqiuqiu.ui.components.ExpeditionBannerView(this, run?.heroId ?: state.progress.activeHeroId,
            if (run == null) "远征寻宝" else "继续远征", if (run == null) "五场挑战\n通关带回目标宝箱" else "第${run.round}/5战\n${FirstRunContent.hero(run.heroId).name}"))
        val stats = LinearLayout(this)
        listOf("%,d".format(state.gold) to "营地金币", "${state.progress.clears}" to "成功通关", "${state.progress.unlockedHeroes.size}" to "已解锁英雄").forEach { (value, title) ->
            stats.addView(GameUi.panel(this).apply {
                setPadding(GameUi.dp(this@MainActivity, 8), GameUi.dp(this@MainActivity, 7), GameUi.dp(this@MainActivity, 8), GameUi.dp(this@MainActivity, 7))
                addView(GameUi.label(this@MainActivity, value, 18, GameUi.gold, true))
                addView(GameUi.label(this@MainActivity, title, 10, GameUi.muted))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(GameUi.dp(this@MainActivity, 2), GameUi.dp(this@MainActivity, 8), GameUi.dp(this@MainActivity, 2), 0) })
        }
        body.addView(stats)
        val next = GameUi.panel(this, true)
        next.addView(GameUi.label(this, if (run == null) "下一次远征" else "远征尚未结束", 12, GameUi.gold, true))
        next.addView(GameUi.label(this, if (run == null) "挑选目标宝箱，再确认英雄和配装。" else "第${run.round}/5战 · 本局金币 ${state.runGold} · ${run.target.theme.title}", 15, GameUi.ink, true))
        next.addView(GameUi.button(this, if (run == null) "挑选目标宝箱" else "继续远征", true) { open(JourneyActivity::class.java) })
        body.addView(next)
        val shortcuts = LinearLayout(this)
        shortcuts.addView(GameUi.button(this, "整备 ${state.skillSlots}/8") { open(InventoryActivity::class.java) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, GameUi.dp(this@MainActivity, 3), 0) })
        shortcuts.addView(GameUi.button(this, "更多") {
            val guide = com.example.douqiuqiu.ui.components.GameCatalogSheet(this, "营地事务", "探索与鉴定", "返回营地", stoneHeader = true)
            val explore = GameUi.panel(this, true)
            explore.addView(GameUi.label(this, "探索", 21, GameUi.gold, true))
            explore.addView(GameUi.label(this, "查看远征规则、撤离和安全箱说明。", 14, GameUi.muted))
            explore.addView(GameUi.button(this, "远征规则") {
                GameUi.sheet(this, "远征规则", "第三战后可以撤离，带回已有收益；全部通关才能获得目标宝箱。\n\n死亡会失去安全箱外的携带物品与本局金币。安全箱内装备不生效，绑定也不等于死亡保护。", listOf("知道了")) { }
            })
            explore.addView(GameUi.button(this, "物品图鉴") { guide.dialog.dismiss(); open(com.example.douqiuqiu.ui.IconGalleryActivity::class.java) })
            guide.body.addView(explore)
            val appraisal = GameUi.panel(this)
            appraisal.addView(GameUi.label(this, "鉴定", 21, GameUi.gold, true))
            appraisal.addView(GameUi.label(this, "宝箱和掉落会按物品品质、价值与体积完成鉴定；鉴定不能跳过。", 14, GameUi.muted))
            appraisal.addView(GameUi.button(this, "前往宝箱鉴定") { guide.dialog.dismiss(); open(JourneyActivity::class.java) })
            guide.body.addView(appraisal)
            guide.show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(GameUi.dp(this@MainActivity, 3), 0, 0, 0) })
        body.addView(shortcuts)
        // 成长入口提级：任务与英雄等级原先藏在 整备→更多 第三层，营地位置充裕故直接前置。
        val growth = LinearLayout(this)
        growth.addView(GameUi.button(this, "委托任务") {
            startActivity(Intent(this, InventoryActivity::class.java).putExtra("openSheet", 3))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, GameUi.dp(this@MainActivity, 3), 0) })
        growth.addView(GameUi.button(this, "英雄等级") {
            startActivity(Intent(this, InventoryActivity::class.java).putExtra("openSheet", 4))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(GameUi.dp(this@MainActivity, 3), 0, 0, 0) })
        body.addView(growth)
        if (loaded.recoveredFromBackup) AlertDialog.Builder(this).setTitle("已恢复有效备份").setMessage("主存档读取失败，现已恢复备份。请核对物品与挑战进度，原文件保留。")
            .setPositiveButton("知道了", null).show()
        window.decorView.post { com.example.douqiuqiu.ui.VersionLog.showIfUnread(this) }
    }
    private fun open(activity: Class<out AppCompatActivity>) { startActivity(Intent(this, activity)) }
}
