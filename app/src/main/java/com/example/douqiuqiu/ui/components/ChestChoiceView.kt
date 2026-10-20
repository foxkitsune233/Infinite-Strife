package com.example.douqiuqiu.ui.components

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.journey.ChestLayout
import com.example.douqiuqiu.journey.TargetChest

/** A compact entry into the chest detail page; the real loot layout is only shown after opening it. */
class ChestChoiceView(
    context: Context,
    chest: TargetChest,
    definitions: Map<String, ItemDefinition>,
    selected: Boolean,
    onChoose: () -> Unit
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        isFocusable = true
        val capacity = ChestLayout.display(chest, definitions)
        val quality = GameUi.quality(chest.quality)
        setPadding(GameUi.dp(context, 8), GameUi.dp(context, 8), GameUi.dp(context, 8), GameUi.dp(context, 10))
        background = GameUi.frame(context, if (selected) GameUi.raised else GameUi.surface, if (selected) GameUi.gold else GameUi.line)
        contentDescription = "${com.example.douqiuqiu.journey.ChallengeBalance.name(chest.difficulty)}难度，${chest.theme.title}${chest.quality}色宝箱，${capacity.columns}乘${capacity.rows}格，${chest.ticket}金币${if (selected) "，已选中" else ""}"

        addView(ChestCrestView(context, chest.quality), LayoutParams(-1, GameUi.dp(context, 76)))
        addView(label(context, chest.theme.title, 15, quality, true))
        addView(label(context, "${com.example.douqiuqiu.journey.ChallengeBalance.name(chest.difficulty)} · ${chest.difficulty + 1}/6", 12, GameUi.gold, true))
        addView(label(context, "${capacity.columns}×${capacity.rows} 格", 12, GameUi.gold, true))
        addView(label(context, "${chest.ticket} 金币", 12, GameUi.ink, false))
        addView(label(context, "查看宝箱  ›", 12, GameUi.gold, true))
        setOnClickListener { onChoose() }
    }

    private fun label(context: Context, value: String, size: Int, color: Int, bold: Boolean): TextView =
        GameUi.label(context, value, size, color, bold).apply { gravity = Gravity.CENTER }
}
