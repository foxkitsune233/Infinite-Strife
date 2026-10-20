package com.example.douqiuqiu.ui.components

import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.*

/** Square catalogue and secondary recipe preview; the owner remains responsible for durable commits. */
class CraftingSheet(
    private val activity: AppCompatActivity,
    private val definitions: Map<String, ItemDefinition>,
    private val recipes: Collection<CraftingRecipe>,
    private val state: () -> InventoryState,
    private val description: (ItemDefinition) -> String,
    private val craft: (CraftingOption) -> Boolean
) {
    private val sheet = GameCatalogSheet(activity, "装备合成", "仅使用仓库材料 · 点按查看配方", stoneHeader = true)
    private var onlyReady = false
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
    fun show() { render(); sheet.show() }

    private fun render() {
        sheet.body.removeAllViews()
        val all = CraftingPresentation.options(state(), recipes, definitions)
        val tabs = LinearLayout(activity)
        listOf("全部 ${all.size}" to false, "可合成 ${all.count { it.missing == 0 }}" to true).forEach { (label, ready) ->
            tabs.addView(GameUi.button(activity, label, onlyReady == ready) { onlyReady = ready; render() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        sheet.body.addView(tabs)
        listOf(true, false).forEach { ready ->
            val options = all.filter { (it.missing == 0) == ready }
            if ((!ready && onlyReady) || options.isEmpty()) return@forEach
            sheet.body.addView(GameUi.label(activity, if (ready) "可合成 · ${options.size}" else "待收集 · ${options.size}", 16, GameUi.gold, true))
            options.chunked(3).forEach { group ->
                val row = LinearLayout(activity)
                group.forEach { option ->
                    val definition = definitions.getValue(option.recipe.outputId)
                    val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    val square = object : FrameLayout(activity) {
                        override fun onMeasure(w: Int, h: Int) {
                            val side = View.MeasureSpec.getSize(w)
                            super.onMeasure(View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY))
                        }
                    }.apply {
                        background = GameUi.frame(activity, GameUi.surface, if (ready) GameUi.gold else GameUi.line)
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        addView(ItemArtwork.view(activity, definition), FrameLayout.LayoutParams(-1, -1))
                        if (!ready) addView(View(activity).apply { setBackgroundColor(0x99051925.toInt()) }, FrameLayout.LayoutParams(-1, -1))
                        addView(GameUi.label(activity, if (ready) "✓ 可合成" else "缺${option.missing}件", 11, if (ready) GameUi.gold else GameUi.ink, true).apply {
                            gravity = Gravity.CENTER; setBackgroundColor(0xDD051F2F.toInt())
                        }, FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM))
                        isFocusable = true
                        contentDescription = "${definition.name}，${if (ready) "可合成" else "缺${option.missing}件材料"}，查看配方"
                        setOnClickListener { preview(option.recipe.id) }
                    }
                    column.addView(square)
                    column.addView(GameUi.label(activity, definition.name, 12, if (ready) GameUi.gold else GameUi.muted, true).apply {
                        gravity = Gravity.CENTER; maxLines = 2; minHeight = dp(36)
                    })
                    row.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
                }
                repeat(3 - group.size) { row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f)) }
                sheet.body.addView(row)
            }
        }
        if (all.none { it.missing == 0 }) sheet.body.addView(GameUi.label(activity, "暂无可合成装备，点按暗色装备查看缺少的组件。", 13, GameUi.muted))
    }

    private fun preview(recipeId: String) {
        val option = CraftingPresentation.options(state(), recipes, definitions).single { it.recipe.id == recipeId }
        val output = definitions.getValue(option.recipe.outputId)
        val preview = GameCatalogSheet(activity, "配方预览", output.name, "返回图鉴", stoneHeader = true)
        val product = GameUi.panel(activity)
        product.addView(ItemArtwork.view(activity, output), LinearLayout.LayoutParams(-1, dp(112)))
        product.addView(GameUi.label(activity, output.name, 21, GameUi.gold, true).apply { gravity = Gravity.CENTER })
        product.addView(GameUi.label(activity, description(output), 12))
        preview.body.addView(product)
        preview.body.addView(GameUi.label(activity, "合成材料", 17, GameUi.gold, true))
        option.recipe.ingredients.entries.toList().chunked(3).forEach { group ->
            val row = LinearLayout(activity)
            group.forEach { (id, count) ->
                val d = definitions.getValue(id)
                val material = option.materials.single { it.definitionId == id }
                val ready = material.consumableOwned >= count
                val tile = GameUi.panel(activity).apply { setPadding(dp(5), dp(6), dp(5), dp(6)) }
                tile.addView(ItemArtwork.view(activity, d), LinearLayout.LayoutParams(-1, dp(58)))
                tile.addView(GameUi.label(activity, d.name, 11).apply { gravity = Gravity.CENTER })
                tile.addView(GameUi.label(activity, "${if (ready) "✓ 可用" else "缺"} ${material.consumableOwned} / $count", 13, if (ready) GameUi.green else GameUi.danger, true).apply { gravity = Gravity.CENTER })
                if (!ready && material.totalOwned > material.consumableOwned) {
                    tile.addView(GameUi.label(activity, "持有${material.totalOwned} · 锁定${material.lockedOwned} · 其他${material.elsewhereOwned}", 10, GameUi.muted).apply { gravity = Gravity.CENTER })
                }
                tile.isFocusable = true
                tile.contentDescription = "${d.name}，可用${material.consumableOwned}份，需要${count}份；总持有${material.totalOwned}份，锁定${material.lockedOwned}份，其他位置${material.elsewhereOwned}份，查看说明"
                tile.setOnClickListener { AlertDialog.Builder(activity).setTitle(d.name).setMessage(description(d)).setPositiveButton("关闭", null).show() }
                row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(3 - group.size) { row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f)) }
            preview.body.addView(row)
        }
        preview.body.addView(GameUi.label(activity, "仅消耗仓库内未锁定的独立材料 · 产物进入仓库\n背包、安全箱与堆叠材料会显示为“其他位置”，需先整理。", 12, GameUi.muted))
        preview.actions.addView(GameUi.button(activity, if (option.missing == 0) "确认合成" else "材料不足 · 缺${option.missing}件", true) {
            val latest = CraftingPresentation.options(state(), recipes, definitions).single { it.recipe.id == recipeId }
            if (craft(latest)) { preview.dialog.dismiss(); render() }
        }.apply { isEnabled = option.missing == 0; alpha = if (isEnabled) 1f else .45f })
        preview.show()
    }
}
