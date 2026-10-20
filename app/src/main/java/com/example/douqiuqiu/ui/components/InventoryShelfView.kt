package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup

/** Unlimited warehouse/ground presentation. One visual grid unit has the same size as a carry-grid cell. */
class InventoryShelfView(context: Context, private val unitDp: Int) : ViewGroup(context) {
    private data class Entry(val view: View, val width: Int, val height: Int, var x: Int = 0, var y: Int = 0)
    private val entries = mutableListOf<Entry>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var unit = GameUi.dp(context, unitDp)
    private var rows = 0
    private var left = 0
    init { setWillNotDraw(false); clipChildren = false }

    fun addItem(view: View, width: Int, height: Int) {
        require(width in 1..6 && height in 1..6)
        entries += Entry(view, width, height)
        addView(view)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        unit = minOf(GameUi.dp(context, unitDp), width / 6).coerceAtLeast(1)
        left = (width - unit * 6) / 2
        var x = 0; var y = 0; var lineHeight = 0
        entries.forEach { entry ->
            if (x + entry.width > 6) { y += lineHeight; x = 0; lineHeight = 0 }
            entry.x = x; entry.y = y
            entry.view.measure(MeasureSpec.makeMeasureSpec(entry.width * unit - GameUi.dp(context, 4), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(entry.height * unit - GameUi.dp(context, 4), MeasureSpec.EXACTLY))
            x += entry.width; lineHeight = maxOf(lineHeight, entry.height)
        }
        rows = y + lineHeight
        setMeasuredDimension(width, maxOf(unit, rows * unit))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val inset = GameUi.dp(context, 2)
        entries.forEach { e ->
            val x = left + e.x * unit + inset; val y = e.y * unit + inset
            e.view.layout(x, y, x + e.view.measuredWidth, y + e.view.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        paint.color = 0x4030A7C7; paint.strokeWidth = resources.displayMetrics.density
        for (x in 0..6) canvas.drawLine((left + x * unit).toFloat(), 0f, (left + x * unit).toFloat(), height.toFloat(), paint)
        for (y in 0..maxOf(1, rows)) canvas.drawLine(left.toFloat(), (y * unit).toFloat(), (left + 6 * unit).toFloat(), (y * unit).toFloat(), paint)
    }
}
