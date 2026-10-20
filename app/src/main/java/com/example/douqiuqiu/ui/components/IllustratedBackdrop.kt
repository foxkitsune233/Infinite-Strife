package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable

/** Decoration only; all controls and text remain real Android views. */
class IllustratedBackdrop(context: Context) : Drawable() {
    private val bitmap = GameArt.backdrop(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas) {
        canvas.drawColor(GameUi.background)
        bitmap?.let { GameArt.cover(canvas, it, RectF(bounds), paint) }
        paint.color = 0x14001824
        canvas.drawRect(bounds, paint)
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
}
