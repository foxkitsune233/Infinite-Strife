package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.journey.*

/** An information-safe preview: names and quality are taken only from revealed fields. */
class LootPreviewView(context: Context, private val chest: TargetChest, private val definitions: Map<String, ItemDefinition>) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = mutableListOf<RectF>()
    private var downX = 0f
    private var downY = 0f
    private val capacity = ChestLayout.display(chest, definitions)
    // Old saves contain coordinates on a 6x6 board. Never clip or repack a fixed reward.
    val columns = capacity.columns
    val rows = capacity.rows
    private fun publicLabel(index: Int): String {
        val item = chest.items[index]; val d = definitions.getValue(item.definitionId)
        return when (item.reveal) { 2 -> "${d.name} · ${d.quality}色"; 1 -> "${d.quality}色未知物品"; else -> "未鉴定物品" }
    }
    init {
        isClickable = true; isFocusable = true
        contentDescription = "${columns}乘${rows}宝箱预览。" + chest.items.indices.joinToString("；") { publicLabel(it) }
        setOnClickListener {
            AlertDialog.Builder(context).setTitle("已知线索").setItems(chest.items.indices.map(::publicLabel).toTypedArray(), null).setPositiveButton("关闭", null).show()
        }
    }
    override fun onMeasure(w: Int, h: Int) { val side = MeasureSpec.getSize(w); setMeasuredDimension(side, side * rows / columns) }
    override fun onDraw(canvas: Canvas) {
        val unit = width / columns.toFloat()
        p.style = Paint.Style.FILL; p.color = GameUi.parchment; canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 16f, 16f, p)
        p.color = 0xFFB59B62.toInt(); p.strokeWidth = 1f
        for (i in 1 until columns) canvas.drawLine(i * unit, 0f, i * unit, height.toFloat(), p)
        for (i in 1 until rows) canvas.drawLine(0f, i * unit, width.toFloat(), i * unit, p)
        bounds.clear()
        chest.items.forEachIndexed { index, item ->
            val def = definitions.getValue(item.definitionId)
            val r = RectF(item.x * unit + 5, item.y * unit + 5, (item.x + def.width) * unit - 5, (item.y + def.height) * unit - 5)
            bounds += r
            val color = if (item.reveal > 0) GameUi.quality(def.quality) else GameUi.muted
            p.style = Paint.Style.FILL; p.color = GameUi.raised; canvas.drawRoundRect(r, 9f, 9f, p)
            if (item.reveal == 2) {
                canvas.save(); canvas.clipRect(r); ItemArtwork.draw(context, canvas, def, r, p)
                p.color = 0xC9102532.toInt(); canvas.drawRect(r.left, r.bottom - unit * .32f, r.right, r.bottom, p); canvas.restore()
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 2 * resources.displayMetrics.density; p.color = color
            canvas.drawRoundRect(r, 9f, 9f, p)
            p.style = Paint.Style.FILL; p.color = GameUi.ink; p.textAlign = Paint.Align.CENTER
            p.textSize = (if (item.reveal == 2) 11 else 24) * resources.displayMetrics.scaledDensity
            canvas.save(); canvas.clipRect(r)
            canvas.drawText(if (item.reveal == 2) def.name.take(4) else "?", r.centerX(),
                (if (item.reveal == 2) r.bottom - unit * .16f else r.centerY()) - (p.ascent() + p.descent()) / 2, p)
            canvas.restore()
        }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
            MotionEvent.ACTION_UP -> {
                if (kotlin.math.abs(e.x - downX) + kotlin.math.abs(e.y - downY) < android.view.ViewConfiguration.get(context).scaledTouchSlop * 2) {
                    val index = bounds.indexOfFirst { it.contains(e.x, e.y) }
                    if (index >= 0) AlertDialog.Builder(context).setTitle("线索 ${index + 1}").setMessage(publicLabel(index)).setPositiveButton("知道了", null).show()
                    else performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

