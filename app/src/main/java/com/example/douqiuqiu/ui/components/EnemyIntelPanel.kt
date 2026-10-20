package com.example.douqiuqiu.ui.components

import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.journey.*

object EnemyIntelPanel {
    fun add(activity: AppCompatActivity, parent: LinearLayout, build: EnemyBuild, difficulty: Int,
        definitions: Map<String, ItemDefinition>) {
        val intel = EnemyScouting.inspect(build, difficulty)
        val panel = GameUi.panel(activity)
        panel.addView(GameUi.label(activity, "${ChallengeBalance.name(difficulty)} · 敌方主动装备侦察", 15, GameUi.gold, true))
        intel.revealedIds.forEach { id ->
            val definition = definitions.getValue(id)
            val row = LinearLayout(activity).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(ItemArtwork.view(activity, definition), LinearLayout.LayoutParams(GameUi.dp(activity, 48), GameUi.dp(activity, 48)))
            row.addView(GameUi.label(activity, definition.name + "  ›", 14), LinearLayout.LayoutParams(0, -2, 1f))
            row.isClickable = true; row.isFocusable = true
            row.contentDescription = "敌方主动装备：${definition.name}，查看效果"
            row.setOnClickListener {
                val sheet = GameCatalogSheet(activity, "敌方主动装备", definition.name, "返回侦察", stoneHeader = true)
                sheet.body.addView(ItemArtwork.view(activity, definition), LinearLayout.LayoutParams(-1, GameUi.dp(activity, 112)))
                sheet.body.addView(GameUi.label(activity, EquipmentCombat.definitions.getValue(id).description, 14))
                sheet.body.addView(GameUi.label(activity, "敌方使用同一套冷却、使用条件与控制限制。", 12, GameUi.muted))
                sheet.show()
            }
            panel.addView(row)
            if (intel.inlineDetails) panel.addView(GameUi.label(activity, EquipmentCombat.definitions.getValue(id).description, 12, GameUi.muted))
        }
        val hint = when {
            intel.complete && intel.revealedIds.isEmpty() -> "未携带主动装备"
            intel.complete -> "装备已全部识别 · 点击查看效果"
            intel.concealedCount != null -> "另有 ${intel.concealedCount} 件主动装备未识别"
            difficulty == 4 -> if (intel.roles.isEmpty()) "侦察结果不完整，无法确认主动装备" else "仅探明用途：${intel.roles.joinToString("／")}；型号与数量未知"
            else -> "侦察受阻 · 主动装备型号、用途与数量未知"
        }
        panel.addView(GameUi.label(activity, hint, 12, if (intel.complete) GameUi.muted else GameUi.gold))
        parent.addView(panel)
    }
}
