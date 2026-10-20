package com.example.douqiuqiu.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.ui.components.*

class IconGalleryActivity : AppCompatActivity() {
    private val catalog by lazy { InventoryCatalog(this) }
    private var category = 0
    private val categories = listOf("装备", "消耗品", "卷轴", "材料", "状态", "卡牌", "战利品")
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); category = (savedInstanceState?.getInt("category") ?: 0).coerceIn(0, categories.lastIndex) }
    override fun onResume() { super.onResume(); render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("category", category); super.onSaveInstanceState(outState) }
    private fun render() {
        val shell = GameUi.screen(this, "图标图鉴", "点按查看名称与品质 · 矢量图标支持任意分辨率", emptyList())
        val tabs = LinearLayout(this)
        categories.forEachIndexed { index, name -> tabs.addView(GameUi.button(this, name, category == index) { category = index; render() },
            LinearLayout.LayoutParams(GameUi.dp(this, 88), -2).apply { rightMargin = GameUi.dp(this@IconGalleryActivity, 4) }) }
        shell.body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs); post { scrollTo(category * GameUi.dp(this@IconGalleryActivity, 92), 0) } })
        if (category == 4) {
            StatusKind.entries.chunked(3).forEach { kinds ->
                val row = LinearLayout(this)
                kinds.forEach { kind ->
                    val name = statusName(kind)
                    val tile = GameUi.panel(this)
                    tile.addView(ImageView(this).apply { setImageDrawable(StatusIconArtwork.drawable(this@IconGalleryActivity, BattleStatusCatalog.icon(kind))); contentDescription = name },
                        LinearLayout.LayoutParams(-1, GameUi.dp(this, 64)))
                    tile.addView(GameUi.label(this, name, 12).apply { gravity = Gravity.CENTER })
                    tile.isFocusable = true; tile.setOnClickListener { AlertDialog.Builder(this).setTitle(name).setMessage("战斗中仅在此状态实际存在时显示；徽标表示层数、数值或剩余秒数。")
                        .setPositiveButton("关闭", null).show() }
                    row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(3, 8, 3, 0) })
                }
                shell.body.addView(row)
            }
        } else {
            val kind = when (category) { 0 -> ItemKind.EQUIPMENT; 1 -> ItemKind.CONSUMABLE; 2 -> ItemKind.SCROLL; 3 -> ItemKind.MATERIAL; 6 -> ItemKind.LOOT; else -> ItemKind.CARD }
            val entries = catalog.rules.definitions.values.filter { it.kind == kind }.sortedWith(compareBy<ItemDefinition> { it.price }.thenBy { it.id })
            shell.body.addView(GameUi.label(this, "${entries.size}件${categories[category]}", 13, GameUi.muted))
            entries.chunked(2).forEach { items ->
                val row = LinearLayout(this)
                items.forEach { d ->
                    val tile = GameUi.panel(this)
                    tile.addView(ItemArtwork.view(this, d), LinearLayout.LayoutParams(-1, GameUi.dp(this, if (kind == ItemKind.CARD) 112 else 76)))
                    tile.addView(GameUi.label(this, d.name, 14, GameUi.quality(d.quality), true))
                    tile.addView(GameUi.label(this, "${d.quality}色 · ${d.width}×${d.height}格", 12, GameUi.muted))
                    if (kind == ItemKind.LOOT) tile.addView(GameUi.label(this, "出售 ${d.price.toLong() * 40 / 100}金币", 12, GameUi.gold))
                    tile.isFocusable = true; tile.setOnClickListener {
                        AlertDialog.Builder(this).setTitle(d.name).setIcon(PaintedItemIcons.drawable(this, d))
                            .setMessage("${d.quality}色 · ${d.width}×${d.height}格\n基础价格 ${d.price}金币\n" +
                                (com.example.douqiuqiu.loot.ValuableLootCatalog.find(d.id)?.let { "${it.description}\n无战斗效果，出售可得${d.price.toLong() * 40 / 100}金币。" }
                                    ?: com.example.douqiuqiu.content.EquipmentCatalog.all.find { it.id == d.id }?.description
                                    ?: com.example.douqiuqiu.content.CardCatalog.all.find { it.id == d.id }?.description ?: "物品效果以游戏内详情为准。"))
                            .setPositiveButton("关闭", null).show()
                    }
                    row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 8, 4, 0) })
                }
                if (items.size == 1) row.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f))
                shell.body.addView(row)
            }
        }
        shell.accessory.addView(GameUi.button(this, "返回", true) { finish() })
    }
    private fun statusName(kind: StatusKind) = when (kind) {
        StatusKind.INVULNERABLE -> "无敌"; StatusKind.GHOST -> "虚无"; StatusKind.BREAK -> "破坏"
        StatusKind.STUN -> "眩晕"; StatusKind.HEX -> "妖术"; StatusKind.BANISHED -> "放逐"
        StatusKind.MUTED -> "物品禁用"; StatusKind.DISARMED -> "缴械"; StatusKind.SILENCE -> "沉默"
        StatusKind.IMMUNITY -> "减益免疫"; StatusKind.PROTECTED -> "普攻保护"; StatusKind.HASTE -> "攻速提升"
        StatusKind.ARMOR_DOWN -> "护甲降低"; StatusKind.NORMAL_POISON -> "普通毒"; StatusKind.VIRULENT_POISON -> "剧毒"
        StatusKind.LEGACY_POISON -> "旧档中毒"; StatusKind.SLOW -> "减速"; StatusKind.CRITICAL_READY -> "确保暴击"
        StatusKind.EQUIPMENT -> "装备增益"
        StatusKind.FROST_DOT -> "冰霜持续伤害"
    }
}
