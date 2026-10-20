package com.example.douqiuqiu.ui.components

import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.cards.CombatCard
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.InventoryState

object SynergySheet {
    private fun cards(s: InventoryState) = s.items.filter { it.place.container == Container.SKILLS && it.definitionId in CardCombat.definitions }
        .map { CombatCard(it.instanceId, it.definitionId, it.star) }

    fun compactSummary(s: InventoryState): String {
        val views = SynergyPresentation.schools(cards(s)).filter { it.count > 0 }
        return views.firstOrNull { it.next != null }?.let { "${it.name} ${it.count}种 · ${it.summary}" }
            ?: views.firstOrNull()?.let { "${it.name} ${it.count}种 · 全档激活" } ?: "放入技能卡，激活2／3／4种羁绊"
    }

    fun show(a: AppCompatActivity, s: InventoryState) {
        val sheet = GameCatalogSheet(a, "羁绊与构筑", "只计算技能栏 · 同卡种去重 · 双流派各计一种 · 2／3／4档收益累加", "返回", stoneHeader = true)
        SynergyPresentation.schools(cards(s)).forEach { school ->
            val panel = GameUi.panel(a)
            panel.addView(GameUi.label(a, "${school.name}  ${school.count}种", 18, if (school.count >= 2) GameUi.gold else GameUi.ink, true))
            panel.addView(GameUi.label(a, school.summary, 12, GameUi.gold))
            school.tiers.forEach { tier ->
                val row = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, GameUi.dp(a, 6), 0, GameUi.dp(a, 6)) }
                row.addView(GameUi.label(a, "${if (tier.active) "✓ 已激活" else "○ 未激活"} · ${tier.threshold}种", 13,
                    if (tier.active) GameUi.green else GameUi.muted, true))
                row.addView(GameUi.label(a, tier.text, 14, if (tier.active) GameUi.ink else GameUi.muted))
                panel.addView(row)
            }
            sheet.body.addView(panel)
        }
        sheet.show()
    }
}
