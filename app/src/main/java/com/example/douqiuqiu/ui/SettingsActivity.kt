package com.example.douqiuqiu.ui

import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.douqiuqiu.inventory.PlayerRepository
import com.example.douqiuqiu.ui.components.*
import java.io.ByteArrayOutputStream

class SettingsActivity : AppCompatActivity() {
    private val repository by lazy { PlayerRepository(this) }
    private lateinit var body: LinearLayout
    private var tab = 0
    private val exportBattle = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) attempt {
            val output = contentResolver.openOutputStream(uri, "wt") ?: error("无法写入日志文件")
            BattleRecorder.export(applicationContext, output, { repository.store.exportBackup() }) { problem ->
                runOnUiThread { message(if (problem == null) "战斗记录已导出" else "日志导出失败：${problem.message}") }
            }
        }
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) attempt {
            repository.load()
            val bytes = repository.store.exportBackup()
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: error("无法写入所选文件")
            message("备份已导出")
        }
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attempt {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (result.size() + count > 1024 * 1024 + 20) error("备份文件超过1MiB限制")
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            } ?: error("无法读取所选文件")
            val candidate = repository.store.readBackup(bytes)
            AlertDialog.Builder(this).setTitle("恢复此备份？")
                .setMessage("备份已通过格式、校验和与物品规则检查。\n金币 ${candidate.gold} · 物品 ${candidate.items.size}份\n${if (candidate.progress.run == null) "营地" else "挑战第${candidate.progress.run!!.round}战"}\n\n将替换当前金币、物资和挑战进度；当前有效存档会保留为上一代备份。")
                .setNegativeButton("保留当前存档", null).setPositiveButton("恢复备份") { _, _ -> attempt {
                    repository.store.save(candidate); message("备份已恢复，返回游戏后生效")
                } }.show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tab = savedInstanceState?.getInt("tab") ?: 0; render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab", tab); super.onSaveInstanceState(outState) }

    private fun render() {
        val shell = GameUi.screen(this, "设置", "显示偏好立即保存 · 不影响战斗规则", emptyList())
        body = shell.body
        val tabs = LinearLayout(this)
        listOf("游戏体验", "存档与诊断", "图鉴与来源").forEachIndexed { index, name ->
            tabs.addView(GameUi.button(this, name, tab == index) { tab = index; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        body.addView(tabs)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        body.addView(GameUi.label(this, "测试版 ${packageInfo.versionName} · 构建 ${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)}", 12, GameUi.gold).apply {
            setTextIsSelectable(true)
        })
        if (tab == 0) {
        GameUi.section(body, "战斗与交互")
        toggle("技能与普攻动画", "控制战斗角色动作与特效；不受系统界面动画倍率影响。关闭后保留状态、标记和静态角色。", "animations", AppSettings.animations(this))
        toggle("操作振动", "按钮与长按拖动物品的触感反馈。", "haptics", AppSettings.haptics(this))
        toggle("战斗记录", "显示战场下方最近发生的战斗事件。", "battleLog", AppSettings.battleLog(this))
        toggle("状态图标摘要", "开启：横向图标与层数。关闭：两行文字。两种模式均可查看全部详情。", "statusIcons", AppSettings.statusIcons(this))
        toggle("游戏音效", "点击、命中、技能与鉴定提示；使用系统媒体音量。", "sound", AppSettings.sound(this))
        toggle("背景音乐", "循环播放轻奇幻器乐；切到后台暂停，返回续播。独立于音效开关，使用系统媒体音量。", "music", AppSettings.music(this))
        toggle("胜负短乐", "战斗结束播放简短提示音乐；游戏音效关闭时一并静音。", "jingles", AppSettings.jingles(this))
        GameUi.section(body, "界面文字", "在系统字体大小基础上调整；不会改变物品占格或伤害。")
        val row = LinearLayout(this)
        listOf("标准", "较大", "最大").forEachIndexed { index, name ->
            row.addView(GameUi.button(this, name, AppSettings.textLevel(this) == index) {
                if (AppSettings.setTextLevel(this, index)) render() else message("设置保存失败，请重试")
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(3, 0, 3, 0) })
        }
        body.addView(row)
        }
        if (tab == 1) {
        GameUi.section(body, "存档与备份", "备份包含金币、物品和当前挑战。恢复前会验证文件并要求确认。")
        body.addView(GameUi.button(this, "导出当前存档") { export.launch("douqiuqiu-backup.dqq") })
        body.addView(GameUi.button(this, "选择备份并恢复") { restore.launch(arrayOf("*/*")) })
        GameUi.section(body, "战斗诊断", "自动记录逐帧状态、事件与操作；本地约48MiB滚动保留。导出包包含当前存档。")
        body.addView(GameUi.button(this, "导出完整战斗记录 ZIP") { exportBattle.launch("douqiuqiu-battle-records.zip") })
        body.addView(GameUi.button(this, "查看最近战斗异常") {
            AlertDialog.Builder(this).setTitle("战斗诊断").setMessage(BattleDiagnostics.read(this)).setPositiveButton("关闭", null).show()
        })
        if (com.example.douqiuqiu.inventory.TestProfile.available(this)) {
            body.addView(GameUi.button(this, "通用测试模式 · 独立存档", true) {
                startActivity(android.content.Intent().setClassName(this, "com.example.douqiuqiu.ui.TestModeActivity"))
            })
        }
        }
        if (tab == 2) {
        body.addView(GameUi.label(this, "界面版本：彩绘高饱和 v2 · 2026-10-18", 13, GameUi.gold, true))
        body.addView(GameUi.button(this, "查看版本日志") { VersionLog.show(this) })
        GameUi.section(body, "图鉴与来源")
        body.addView(GameUi.button(this, "查看全部物品图标") { startActivity(android.content.Intent(this, IconGalleryActivity::class.java)) })
        body.addView(GameUi.button(this, "制作与素材来源") {
            AlertDialog.Builder(this).setTitle("制作与素材来源")
                .setMessage("Infinite Strife · Android单人远征课设\n\n角色、卡面、彩色物品和场景：项目内AI辅助美术，保留提示词与源图。\n状态 / Buff / DOT：Game-icons.net，Lorc和Delapouite，CC BY 3.0；已转换格式、移除底色并着色。逐图来源见开源图标许可。\n界面边框：项目内矢量绘制。\n参考：Dota 2英雄、装备与电子斗蛐蛐玩法。\n\n音效和胜负短乐：Kenney — Interface Sounds、Music Jingles（CC0 1.0）。\n背景音乐：Once Upon a Time (loop)，TAD，CC0 1.0；WAV转OGG，使用原循环长度。\nhttps://opengameart.org/content/once-upon-a-time-loop\nhttps://creativecommons.org/publicdomain/zero/1.0/\nhttps://kenney.nl/assets/interface-sounds\nhttps://kenney.nl/assets/music-jingles")
                .setPositiveButton("关闭", null).show()
        })
        body.addView(GameUi.button(this, "开源图标作者与许可") {
            val entries = listOf("作者与逐图来源" to "game-icons-credits.txt", "CC BY 3.0 许可全文" to "game-icons-cc-by-3.0.txt", "上游作者声明" to "game-icons-upstream-license.txt")
            AlertDialog.Builder(this).setTitle("Game-icons.net · 开源许可").setItems(entries.map { it.first }.toTypedArray()) { _, index -> attempt {
                val text = assets.open("licenses/${entries[index].second}").bufferedReader().use { it.readText() }
                AlertDialog.Builder(this).setTitle(entries[index].first).setMessage(text).setPositiveButton("关闭", null).show()
            } }.setNegativeButton("返回", null).show()
        })
        }
        if (tab == 0) body.addView(GameUi.button(this, "恢复默认体验设置") {
            AlertDialog.Builder(this).setTitle("恢复体验设置？").setMessage("只重置显示、音效与振动偏好，不修改游戏存档。")
                .setNegativeButton("取消", null).setPositiveButton("恢复默认") { _, _ ->
                    if (AppSettings.reset(this)) { GameMusic.refresh(); render() } else message("设置保存失败，请重试")
                }.show()
        })
        shell.accessory.addView(GameUi.button(this, "返回游戏", true) { finish() })
    }
    private fun toggle(title: String, detail: String, key: String, checked: Boolean) {
        val panel = GameUi.panel(this)
        val toggle = SwitchCompat(this).apply {
            text = title; textSize = 15f * AppSettings.textScale(this@SettingsActivity); setTextColor(GameUi.ink)
            minHeight = GameUi.dp(this@SettingsActivity, 48); isChecked = checked
            setOnCheckedChangeListener { _, enabled ->
                if (!AppSettings.setFlag(this@SettingsActivity, key, enabled)) {
                    message("设置保存失败，请重试"); render(); return@setOnCheckedChangeListener
                }
                if (key == "music") GameMusic.refresh()
                if (!enabled && (key == "sound" || key == "jingles")) GameAudio.stop()
            }
        }
        panel.addView(toggle); panel.addView(GameUi.label(this, detail, 12, GameUi.muted)); body.addView(panel)
    }
    private fun attempt(block: () -> Unit) { try { block() } catch (e: Exception) {
        AlertDialog.Builder(this).setTitle("操作未完成").setMessage("${e.message ?: "文件不可读写"}\n原存档保留。")
            .setPositiveButton("关闭", null).show()
    } }
    private fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
}
