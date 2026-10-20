package com.example.douqiuqiu.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import com.example.douqiuqiu.inventory.ItemDefinition

/** The shadow represents one item, never the whole source grid. */
class ItemDragShadow(source: View, private val definition: ItemDefinition, rotated: Boolean = false, cellDp: Int = 48) : View.DragShadowBuilder(source) {
    private val context = source.context
    private val cell = GameUi.dp(context, cellDp)
    private val w = cell * (if (rotated) definition.height else definition.width)
    private val h = cell * (if (rotated) definition.width else definition.height)
    private val lift = GameUi.dp(context, 30)
    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        // Keep the icon above the finger. The cursor, not the lifted icon, is the drop point.
        size.set(w, h + lift + GameUi.dp(context, 6)); touch.set(cell / 2, h + lift)
    }
    override fun onDrawShadow(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(2f, 2f, w - 2f, h - 2f)
        p.color = GameUi.raised; canvas.drawRoundRect(r, 6f, 6f, p)
        ItemArtwork.draw(context, canvas, definition, r, p)
        p.color = GameUi.gold; p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        canvas.drawRoundRect(r, 6f, 6f, p)
        p.strokeWidth = GameUi.dp(context, 1).toFloat()
        canvas.drawLine(cell / 2f, h.toFloat(), cell / 2f, (h + lift).toFloat(), p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(cell / 2f, (h + lift).toFloat(), GameUi.dp(context, 3).toFloat(), p)
    }
}
