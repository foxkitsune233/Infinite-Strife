package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.view.View

/** Resolution-independent chest emblem; quality tint is public information. */
class ChestCrestView(context: Context, private val quality: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var openingFraction: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    override fun onDraw(canvas: Canvas) {
        canvas.save(); canvas.scale(width / 160f, height / 96f)
        val tint = GameUi.quality(quality)
        val closed = GameArt.chest(context, false)
        val opened = GameArt.chest(context, true)
        if (closed != null && opened != null) {
            val r = RectF(28f, -4f, 132f, 100f)
            val phase = openingFraction
            paint.style = Paint.Style.FILL; paint.color = tint; paint.alpha = (phase * 70).toInt()
            canvas.drawOval(38f - phase * 10, 5f, 122f + phase * 10, 91f, paint)
            paint.color = Color.WHITE; paint.isFilterBitmap = true
            paint.alpha = ((1f - phase) * 255).toInt(); canvas.drawBitmap(closed, null, r, paint)
            paint.alpha = (phase * 255).toInt(); canvas.drawBitmap(opened, null, r, paint)
            paint.color = tint; paint.alpha = 255
            canvas.drawRoundRect(48f, 91f, 112f, 94f, 2f, 2f, paint)
            if (phase > 0 && phase < 1) repeat(7) { i ->
                val x = 48f + i * 10f
                val y = 72f - ((phase * 64 + i * 9) % 58)
                paint.alpha = ((1 - phase) * 230).toInt()
                canvas.drawCircle(x, y, 1.5f, paint)
            }
            paint.alpha = 255; canvas.restore(); return
        }
        val lid = Path().apply { moveTo(28f, 43f); lineTo(40f, 17f); lineTo(119f, 17f); lineTo(133f, 43f); close() }
        val base = Path().apply { moveTo(28f, 48f); lineTo(133f, 48f); lineTo(126f, 83f); lineTo(35f, 83f); close() }
        if (openingFraction > 0) {
            paint.color = tint; paint.alpha = (openingFraction * 80).toInt(); paint.style = Paint.Style.FILL
            val light = Path().apply { moveTo(46f, 51f); lineTo(30f, 2f); lineTo(129f, 2f); lineTo(116f, 51f); close() }
            canvas.drawPath(light, paint); paint.alpha = 255
        }
        paint.color = GameUi.raised; paint.style = Paint.Style.FILL; canvas.drawPath(base, paint)
        paint.color = tint; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        canvas.drawPath(base, paint)
        canvas.save(); canvas.translate(0f, -openingFraction * 14f)
        paint.color = GameUi.raised; paint.style = Paint.Style.FILL; canvas.drawPath(lid, paint)
        paint.color = tint; paint.style = Paint.Style.STROKE; canvas.drawPath(lid, paint); canvas.restore()
        canvas.drawLine(49f, 19f, 44f, 81f, paint); canvas.drawLine(110f, 19f, 115f, 81f, paint)
        paint.style = Paint.Style.FILL
        val lock = Path().apply { moveTo(80f, 34f); lineTo(93f, 49f); lineTo(80f, 65f); lineTo(67f, 49f); close() }
        canvas.drawPath(lock, paint)
        canvas.restore()
    }
}
