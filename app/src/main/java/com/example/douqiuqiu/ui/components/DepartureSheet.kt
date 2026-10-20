package com.example.douqiuqiu.ui.components

import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.FirstRunContent

/** Final preparation checkpoint. Hero/preset changes persist; only confirm invokes the entry transaction. */
class DepartureSheet(
    private val activity: AppCompatActivity,
    private val definitions: Map<String, ItemDefinition>,
    private val state: () -> InventoryState,
    private val destination: String,
    private val ticket: Int,
    private val subsidy: Int,
    private val initialGold: Int,
    private val summary: () -> String,
    private val select: (heroId: String, slot: Int?) -> Boolean,
    private val depart: (gold: Int) -> Boolean,
    private val organize: () -> Unit,
    private val closed: () -> Unit
) {
    private val sheet = GameCatalogSheet(activity, "出发检查", destination, "继续整备", stoneHeader = true)
    private val configuration = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var goldInput: EditText
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    fun show() {
        sheet.body.addView(configuration)
        renderConfiguration()
        val cost = GameUi.panel(activity, true)
        cost.addView(GameUi.label(activity, "门票 ${ticket - subsidy} 金币${if (subsidy > 0) " · 补助抵扣 $subsidy" else ""}", 16, GameUi.gold, true))
        cost.addView(GameUi.label(activity, "携入金币 · 可用于途中事件", 13))
        goldInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialGold.toString()); setTextColor(GameUi.ink)
            setSingleLine(); contentDescription = "携入本局金币"
            background = GameUi.shape(activity, GameUi.surface, GameUi.line)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        cost.addView(goldInput, LinearLayout.LayoutParams(-1, dp(48)))
        cost.addView(GameUi.label(activity, "最多可携入 ${(state().gold.toLong() - ticket + subsidy).coerceAtLeast(0)} 金币", 12, GameUi.muted))
        sheet.body.addView(cost)
        val protection = GameUi.panel(activity)
        protection.addView(GameUi.label(activity, "安全箱内物品 · 死亡保留", 14, GameUi.green, true))
        protection.addView(GameUi.label(activity, "其他携带物品与本局金币 · 死亡全部失去", 13, GameUi.danger))
        sheet.body.addView(protection)
        sheet.actions.addView(GameUi.button(activity, "确认购票并出发", true) {
            val amount = goldInput.text.toString().toLongOrNull()
            val available = state().gold.toLong() - ticket + subsidy
            if (amount == null || amount !in 0..available.coerceAtMost(Int.MAX_VALUE.toLong())) {
                goldInput.error = if (available < 0) "金币不足以支付门票" else "请输入0至${available}之间的金币"
                goldInput.requestFocus()
            } else if (depart(amount.toInt())) sheet.dialog.dismiss()
        })
        sheet.dialog.setOnDismissListener { closed() }
        sheet.show()
    }

    private fun renderConfiguration() {
        configuration.removeAllViews()
        val current = state()
        val heroId = current.progress.activeHeroId
        val heroes = LinearLayout(activity)
        FirstRunContent.heroes.filter { it.id in current.progress.unlockedHeroes }.forEach { hero ->
            val tile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GameUi.frame(activity, GameUi.surface, if (hero.id == heroId) GameUi.gold else GameUi.line)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                GameArt.portrait(activity, hero.id)?.let { addView(GameArt.image(activity, it, hero.name).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(60), dp(60))) }
                addView(GameUi.label(activity, hero.name, 11, if (hero.id == heroId) GameUi.gold else GameUi.ink, true))
                isFocusable = true
                contentDescription = "${hero.name}${if (hero.id == heroId) "，当前英雄" else "，切换并套用最近方案"}"
                setOnClickListener { if (hero.id != heroId && select(hero.id, null)) renderConfiguration() }
            }
            heroes.addView(tile, LinearLayout.LayoutParams(dp(82), dp(94)).apply { rightMargin = dp(5) })
        }
        configuration.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(heroes) })
        configuration.addView(GameUi.label(activity, "${FirstRunContent.hero(heroId).name} · ${summary()}", 14, GameUi.gold, true))
        val presets = LinearLayout(activity)
        repeat(LoadoutRules.PRESET_COUNT) { slot ->
            val exists = current.progress.loadouts[heroId]?.getOrNull(slot) != null
            val selected = exists && current.progress.loadoutSelections[heroId] == slot
            presets.addView(GameUi.button(activity, "方案${slot + 1}${if (!exists) " · 未保存" else ""}", selected) {
                if (select(heroId, slot)) renderConfiguration()
            }.apply { isEnabled = exists; alpha = if (exists) 1f else .4f }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        configuration.addView(presets)
        slots(Container.EQUIPMENT, 9, 3, "装备 · 前六格生效，后三格备用")
        slots(Container.SKILLS, 8, 4, "技能卡 · ${current.skillSlots}/8格开放")
        configuration.addView(GameUi.button(activity, "羁绊效果 · ${SynergySheet.compactSummary(current)}") { SynergySheet.show(activity, state()) })
        configuration.addView(GameUi.button(activity, "前往整理装备与背包") { sheet.dialog.dismiss(); organize() })
    }

    private fun slots(container: Container, count: Int, columns: Int, title: String) {
        configuration.addView(GameUi.label(activity, title, 12, GameUi.muted))
        val current = state()
        (0 until count).toList().chunked(columns).forEach { indices ->
            val row = LinearLayout(activity)
            indices.forEach { index ->
                val item = current.items.find { it.place.container == container && it.place.x == index }
                val tile = FrameLayout(activity).apply {
                    val active = if (container == Container.EQUIPMENT) index < 6 else index < current.skillSlots
                    background = GameUi.frame(activity, GameUi.surface, if (active) GameUi.gold else GameUi.line)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    if (item != null) {
                        val definition = definitions.getValue(item.definitionId)
                        addView(ItemArtwork.view(activity, definition), FrameLayout.LayoutParams(-1, -1))
                        contentDescription = "${if (container == Container.EQUIPMENT) "装备" else "技能"}${index + 1}，${definition.name}"
                        setOnClickListener { Toast.makeText(activity, definition.name, Toast.LENGTH_SHORT).show() }
                    } else addView(GameUi.label(activity, if (!active && container == Container.SKILLS) "锁定" else "空", 11, GameUi.muted).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(45), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            configuration.addView(row)
        }
    }
}
