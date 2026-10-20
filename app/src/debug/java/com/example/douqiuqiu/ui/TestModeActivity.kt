package com.example.douqiuqiu.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.MainActivity
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.ui.components.GameUi

/** Included only in the debug source set and debug manifest. */
class TestModeActivity : AppCompatActivity() {
    private var readForm: (() -> ScenarioSpec)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TestProfile.available(this)) { finish(); return }
        val previous = runCatching { ScenarioSpec.parse(savedInstanceState?.getString("config") ?: TestProfile.lastConfig(this)) }.getOrDefault(ScenarioSpec())
        val shell = GameUi.screen(this, "通用测试模式", "仅调试包 · 独立测试存档 · 后续操作走真实规则", emptyList())
        val body = shell.body
        body.addView(GameUi.label(this, "载入会重置测试存档，不改正式金币、物资或挑战。退出测试模式将恢复正式存档。", 13, GameUi.gold))
        GameUi.section(body, "64号版本日志夹具 · 不改战役存档")
        body.addView(GameUi.label(this, "用于复现首次弹窗、已读状态和版本排序；只写“体验设置”里的已读标记。", 12, GameUi.muted))
        body.addView(GameUi.button(this, "清除版本日志已读标记") {
            attempt {
                check(AppSettings.clearLastSeenRelease(this)) { "已读标记重置失败" }
                Toast.makeText(this, "已清除：返回游戏后会按当前版本再次检查", Toast.LENGTH_SHORT).show()
            }
        })
        listOf("0.1.3", "0.1.4", "0.1.5", "").forEach { version ->
            val label = if (version.isEmpty()) "模拟空版本日志" else "模拟 $version 首次进入"
            body.addView(GameUi.button(this, label) {
                attempt {
                    val shown = VersionLog.showIfUnread(this, version)
                    Toast.makeText(this, if (shown) "已写入 $version 的已读标记" else "未显示：此版本已读或没有日志", Toast.LENGTH_SHORT).show()
                }
            })
        }
        body.addView(GameUi.button(this, "预览当前版本日志（不写已读）") { VersionLog.show(this) })
        body.addView(GameUi.button(this, "打开设置中的版本日志入口") { startActivity(Intent(this, SettingsActivity::class.java)) })
        GameUi.section(body, "41号专项夹具 · 一键载入")
        body.addView(GameUi.button(this, "T06 · 装备与卷轴彩绘／回退对照") { attempt { AcceptanceFixtures.showIcons(this) } })
        AcceptanceFixtures.presets.forEach { preset ->
            body.addView(GameUi.button(this, preset.title) {
                AlertDialog.Builder(this).setTitle(preset.title).setMessage(preset.instruction)
                    .setNegativeButton("取消", null).setPositiveButton("载入独立测试存档") { _, _ ->
                        attempt { load(AcceptanceFixtures.spec(preset.id)) }
                    }.show()
            })
        }
        fun selector(title: String, labels: List<String>, index: Int): Spinner {
            GameUi.section(body, title)
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@TestModeActivity, android.R.layout.simple_spinner_dropdown_item, labels)
                setSelection(index.coerceIn(labels.indices)); minimumHeight = GameUi.dp(this@TestModeActivity, 48)
                body.addView(this)
            }
        }
        fun number(title: String, value: Long): EditText {
            body.addView(GameUi.label(this, title, 13, GameUi.gold))
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER; setText(value.toString()); setTextColor(GameUi.ink)
                minimumHeight = GameUi.dp(this@TestModeActivity, 48); body.addView(this)
            }
        }
        val scene = selector("起始场景", TestScene.entries.map { it.title }, previous.scene.ordinal)
        val round = selector("场号：事件1–4；休息固定3；奖励／战斗1–5", (1..5).map { "第 $it 战" }, previous.round - 1)
        val heroes = FirstRunContent.heroes
        val hero = selector("我方英雄", heroes.map { it.name }, heroes.indexOfFirst { it.id == previous.hero })
        val enemy = selector("敌方英雄（仅直接战斗覆盖）", heroes.map { it.name }, heroes.indexOfFirst { it.id == previous.enemy })
        val event = selector("事件类型（仅事件场景）", EventKind.entries.map { EventCatalog.definition(it).title }, previous.event.ordinal)
        val loadout = selector("配装预设", TestLoadout.entries.map { it.title }, previous.loadout.ordinal)
        val controls = listOf("none", "stun", "silence", "hex", "disarm")
        val control = selector("我方控制夹具（仅直接战斗，无自然持续时长）", listOf("无", "固定眩晕", "固定沉默", "固定妖术", "固定缴械"), controls.indexOf(previous.control))
        val health = selector("我方起始生命", listOf("100%", "50%", "10%"), listOf(100, 50, 10).indexOf(previous.healthPercent))
        val seed = number("固定种子：1–2147483646", previous.seed)
        val roll = number("事件roll：0–99，可比较0和99覆盖不同结果", previous.eventRoll.toLong())
        val gold = number("本局金币：0–100000（用于付费事件与带出）", previous.runGold.toLong())
        val weak = CheckBox(this).apply { text = "弱敌夹具：1生命，用于快速走胜利流程"; setTextColor(GameUi.ink); isChecked = previous.weakEnemy }
        val expanded = CheckBox(this).apply { text = "八技能位／最大背包与安全箱"; setTextColor(GameUi.ink); isChecked = previous.expanded }
        val paused = CheckBox(this).apply { text = "载入后暂停战斗时钟（可单步，不影响手动物品）"; setTextColor(GameUi.ink); isChecked = previous.startPaused }
        body.addView(weak); body.addView(expanded); body.addView(paused)
        readForm = {
            ScenarioSpec(TestScene.entries[scene.selectedItemPosition], round.selectedItemPosition + 1,
                heroes[hero.selectedItemPosition].id, heroes[enemy.selectedItemPosition].id,
                EventKind.entries[event.selectedItemPosition], TestLoadout.entries[loadout.selectedItemPosition],
                seed.text.toString().toLong(), roll.text.toString().toInt(), gold.text.toString().toInt(),
                listOf(100, 50, 10)[health.selectedItemPosition], controls[control.selectedItemPosition], weak.isChecked, expanded.isChecked,
                startPaused = paused.isChecked)
        }
        body.addView(GameUi.label(this, "夹具不代表真实难度或平衡。固定控制不会自动到期，可被现有驱散移除；不用于验证真实控制持续时间。营地商店仍按现实时间刷新。", 12, GameUi.muted))
        body.addView(GameUi.button(this, "重复上次场景（同配置重建）") {
            attempt { load(ScenarioSpec.parse(TestProfile.lastConfig(this))) }
        })
        body.addView(GameUi.button(this, "复制当前场景配置 JSON") {
            attempt {
                val manager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                manager.setPrimaryClip(android.content.ClipData.newPlainText("battle-scenario", readForm!!.invoke().json()))
                Toast.makeText(this, "场景配置已复制", Toast.LENGTH_SHORT).show()
            }
        })
        body.addView(GameUi.button(this, "粘贴配置 JSON 并载入") {
            val input = EditText(this).apply {
                hint = "粘贴其他测试者提供的场景JSON"; minLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            AlertDialog.Builder(this).setTitle("复现场景").setView(input).setNegativeButton("取消", null)
                .setPositiveButton("载入") { _, _ -> attempt { load(ScenarioSpec.parse(input.text.toString())) } }.show()
        })
        if (TestProfile.active(this)) body.addView(GameUi.button(this, "退出测试模式 · 恢复正式存档") {
            attempt { TestProfile.exit(this); restart(MainActivity::class.java) }
        })
        shell.accessory.addView(GameUi.button(this, "载入所选测试场景", true) { attempt { load(readForm!!.invoke()) } })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        runCatching { readForm?.invoke()?.json() }.getOrNull()?.let { outState.putString("config", it) }
        super.onSaveInstanceState(outState)
    }
    private fun load(spec: ScenarioSpec) {
        check(TestProfile.available(this))
        val catalog = InventoryCatalog(this)
        val fixture = ScenarioFixtures.create(catalog, spec)
        // Commit only the sandbox, then switch profile and clear the old Activity task.
        InventoryStore(TestProfile.file(this, true), catalog.rules, catalog::initial).save(fixture)
        TestProfile.resetSandboxPresentation(this)
        TestProfile.activate(this, "${spec.fixture.takeUnless { it == "none" } ?: spec.scene.title} · 种子${spec.seed}", spec.json(), spec.startPaused)
        fixture.progress.run?.let { r -> r.fight?.let { fight ->
            BattleRecorder.frame(this, r.id, r.round, fight, com.example.douqiuqiu.journey.DuelFrame(fight), "scenario_loaded")
        } }
        restart(JourneyActivity::class.java)
    }
    private fun restart(target: Class<out AppCompatActivity>) {
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
    }
    private fun attempt(action: () -> Unit) { try { action() } catch (e: Exception) {
        AlertDialog.Builder(this).setTitle("测试场景未载入").setMessage(e.message ?: "请检查参数")
            .setPositiveButton("返回修改", null).show()
    } }
}
