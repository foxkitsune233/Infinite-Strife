package com.example.douqiuqiu.ui.components

import android.content.Context
import android.view.Gravity
import android.widget.*

class StatusStripView(context: Context, private val inspect: () -> Unit) : HorizontalScrollView(context) {
    private val row = LinearLayout(context)
    private val badges = linkedMapOf<String, TextView>()
    init {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(GameUi.dp(context, 14))
        addView(row)
    }
    fun bind(entries: List<BattleStatusEntry>) {
        if (badges.keys.toList() != entries.map { it.key }) {
            row.removeAllViews(); badges.clear()
            entries.forEach { entry ->
                val chip = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true
                    background = GameUi.frame(context)
                    setOnClickListener { inspect() }
                }
                chip.addView(ImageView(context).apply {
                    setImageDrawable(StatusIconArtwork.drawable(context, BattleStatusCatalog.icon(entry.kind)))
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(GameUi.dp(context, 30), GameUi.dp(context, 30)))
                val badge = GameUi.label(context, "", 10, GameUi.ink, true).apply { gravity = Gravity.CENTER }
                chip.addView(badge); badges[entry.key] = badge
                row.addView(chip, LinearLayout.LayoutParams(GameUi.dp(context, 44), GameUi.dp(context, 52)).apply { rightMargin = GameUi.dp(context, 4) })
            }
        }
        entries.forEach { entry ->
            badges[entry.key]?.let { badge ->
                if (badge.text.toString() != entry.badge) badge.text = entry.badge
                (badge.parent as LinearLayout).contentDescription = "${entry.detail}，点按查看全部状态"
            }
        }
        contentDescription = "${entries.size}项状态，可横向滚动查看"
    }
}
