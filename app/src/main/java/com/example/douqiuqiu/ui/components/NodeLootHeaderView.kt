package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.journey.*

/** A visible, focused node advances persisted inspection time. No background/offline fast-forward. */
class NodeLootHeaderView(c: Context, private val current: () -> NodeLoot?,
                         private val definitions: Map<String, ItemDefinition>,
                         private val advance: (Long) -> Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var previous = 0L
    private val timer = object : Runnable {
        override fun run() {
            val loot = current() ?: return
            if (!isAttachedToWindow || !hasWindowFocus() || loot.inspectionMs >= NodeLootRules.duration(loot, definitions)) return
            val now = SystemClock.uptimeMillis()
            val delta = (now - previous).coerceIn(0, 250); previous = now
            if (!advance(delta)) return
            invalidate()
            if (isAttachedToWindow && hasWindowFocus()) postDelayed(this, 200)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); resume() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) resume() else removeCallbacks(timer) }
    private fun resume() { removeCallbacks(timer); previous = SystemClock.uptimeMillis(); if (hasWindowFocus()) postDelayed(timer, 200) }
    override fun onDetachedFromWindow() { removeCallbacks(timer); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        val loot = current() ?: return
        val total = NodeLootRules.duration(loot, definitions)
        val opening = NodeLootRules.openingMs(loot)
        val open = opening == 0L || loot.inspectionMs >= opening
        val fraction = if (opening == 0L) 1f else (loot.inspectionMs.toFloat() / opening).coerceIn(0f, 1f)
        val side = height.toFloat()
        GameArt.chest(context, open)?.let {
            canvas.save()
            if (com.example.douqiuqiu.ui.AppSettings.animations(context) && !open) canvas.rotate(kotlin.math.sin(fraction * 24) * 4, side / 2, side / 2)
            canvas.drawBitmap(it, null, RectF(0f, 0f, side, side), p)
            canvas.restore()
        }
        p.color = GameUi.gold; p.textSize = GameUi.dp(context, 12).toFloat(); p.textAlign = Paint.Align.LEFT
        val count = loot.items.count { NodeLootRules.revealed(loot, it.instanceId, definitions) }
        val label = if (!open) "宝箱开启中…" else if (loot.inspectionMs < total) "鉴定 $count / ${loot.items.size} · 不可跳过" else "鉴定完成 · 拖入后获得"
        contentDescription = label
        canvas.drawText(label, side + 6, height * .42f, p)
        p.color = GameUi.line; canvas.drawRect(side + 6, height * .62f, width - 6f, height * .69f, p)
        p.color = GameUi.gold
        canvas.drawRect(side + 6, height * .62f, side + 6 + (width - side - 12) * if (total == 0L) 1f else loot.inspectionMs.toFloat() / total, height * .69f, p)
    }
}
