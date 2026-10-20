package com.example.douqiuqiu.ui.components

import android.graphics.*
import android.graphics.drawable.Drawable

/** Geometric frame remains crisp at any screen density and has no baked-in text. */
class CutPanelDrawable(private val fill: Int, private val edge: Int, private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val r = RectF(bounds).apply { inset(density, density) }
        val cut = minOf(11 * density, r.height() / 5)
        val path = Path().apply {
            moveTo(r.left + cut, r.top); lineTo(r.right - cut, r.top); lineTo(r.right, r.top + cut)
            lineTo(r.right, r.bottom - cut); lineTo(r.right - cut, r.bottom); lineTo(r.left + cut, r.bottom)
            lineTo(r.left, r.bottom - cut); lineTo(r.left, r.top + cut); close()
        }
        paint.style = Paint.Style.FILL; paint.color = fill; canvas.drawPath(path, paint)
        canvas.save(); canvas.clipPath(path)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 121, 241, 255)
        val facet = Path().apply {
            moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.right - cut * 2, r.top + 8 * density)
            lineTo(r.left + cut, r.top + 8 * density); close()
        }
        canvas.drawPath(facet, paint)
        paint.color = 0x3500172A
        canvas.drawRect(r.left, r.bottom - 7 * density, r.right, r.bottom, paint)
        paint.color = Color.argb(17, 255, 245, 185)
        repeat(28) { i ->
            val x = r.left + ((i * 73) % 101) / 101f * r.width()
            val y = r.top + ((i * 41) % 97) / 97f * r.height()
            canvas.drawRect(x, y, x + 12 * density, y + density, paint)
        }
        repeat(8) { i ->
            val x = r.left + (i * 29 % 89) / 89f * r.width()
            val y = r.top + (i * 37 % 83) / 83f * r.height()
            val shard = Path().apply { moveTo(x, y); lineTo(x + 19 * density, y + 3 * density); lineTo(x + 6 * density, y + 14 * density); close() }
            canvas.drawPath(shard, paint)
        }
        canvas.restore()
        paint.style = Paint.Style.STROKE; paint.color = 0xFF03131F.toInt(); paint.strokeWidth = 8f * density; canvas.drawPath(path, paint)
        paint.color = edge; paint.strokeWidth = 3.5f * density; canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFD464.toInt()
        val tab = Path().apply {
            moveTo(r.left + cut, r.top + density); lineTo(r.left + cut + 13 * density, r.top + density)
            lineTo(r.left + cut, r.top + 5 * density); close()
        }
        canvas.drawPath(tab, paint)
        canvas.save(); canvas.rotate(180f, r.centerX(), r.centerY()); canvas.drawPath(tab, paint); canvas.restore()
        paint.style = Paint.Style.STROKE; paint.color = Color.argb(90, 255, 248, 196); paint.strokeWidth = density
        canvas.drawLine(r.left + cut + 5 * density, r.top + 4 * density, r.right - cut - 5 * density, r.top + 4 * density, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Required by Drawable on older Android versions") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
