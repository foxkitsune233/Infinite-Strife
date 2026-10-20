package com.example.douqiuqiu.ui.components

import android.graphics.*
import android.graphics.drawable.Drawable

/** Original faceted vector icons. The 64-unit drawing remains sharp in slots and HUD chips. */
class GameIconDrawable(private val spec: IconSpec) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = 0xFF091820.toInt()
    private val light = 0xFFF4E6C6.toInt()
    override fun draw(c: Canvas) {
        c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.scale(bounds.width() / 64f, bounds.height() / 64f)
        drawGlyph(c, spec.glyph, spec.tint, spec.accent, spec.variant)
        c.restore()
    }
    private fun line(c: Canvas, color: Int, width: Float, vararg xy: Float) {
        p.color = color; p.style = Paint.Style.STROKE; p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path().apply { moveTo(xy[0], xy[1]); for (n in 2 until xy.size step 2) lineTo(xy[n], xy[n + 1]) }
        c.drawPath(path, p)
    }
    private fun poly(c: Canvas, color: Int, vararg xy: Float) {
        val path = Path().apply { moveTo(xy[0], xy[1]); for (n in 2 until xy.size step 2) lineTo(xy[n], xy[n + 1]); close() }
        p.style = Paint.Style.FILL; p.color = color; c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = edge; c.drawPath(path, p)
    }
    private fun oval(c: Canvas, color: Int, x: Float, y: Float, rx: Float, ry: Float) {
        p.color = color; p.style = Paint.Style.FILL; c.drawOval(x - rx, y - ry, x + rx, y + ry, p)
        p.color = edge; p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; c.drawOval(x - rx, y - ry, x + rx, y + ry, p)
    }
    private fun sword(c: Canvas, color: Int, v: Int) {
        line(c, edge, 8f, 16f, 52f, 27f, 40f); line(c, 0xFF9C7651.toInt(), 4f, 16f, 52f, 27f, 40f)
        poly(c, color, 26f, 40f, 24f, 31f, 49f, 8f, 55f, 7f, 54f, 16f, 34f, 43f)
        line(c, light, 1.5f, 29f, 35f, 50f, 12f)
        line(c, edge, 7f, 20f, 32f, 36f, 46f); line(c, spec.accent, 3f, 20f, 32f, 36f, 46f)
        if (v >= 2) poly(c, spec.accent, 29f, 28f, 34f, 25f, 37f, 30f, 32f, 34f)
        if (v == 3) line(c, color, 2f, 45f, 29f, 53f, 30f, 48f, 38f)
    }
    private fun gem(c: Canvas, color: Int) {
        poly(c, color, 21f, 12f, 42f, 12f, 53f, 28f, 32f, 56f, 11f, 28f)
        line(c, light, 1.8f, 21f, 12f, 24f, 28f, 32f, 56f)
        line(c, edge, 1.5f, 11f, 28f, 53f, 28f, 42f, 12f)
    }
    private fun drawGlyph(c: Canvas, g: IconGlyph, color: Int, accent: Int, v: Int) {
        when (g) {
            IconGlyph.COIN -> {
                oval(c, color, 32f, 33f, 23f, 24f)
                oval(c, accent, 32f, 32f, 16f, 17f)
                poly(c, color, 32f, 19f, 41f, 32f, 32f, 45f, 23f, 32f)
                line(c, light, 2f, 15f, 25f, 20f, 17f, 28f, 13f)
            }
            IconGlyph.CUP -> {
                if (v == 1) { oval(c, color, 46f, 29f, 12f, 14f); line(c, accent, 4f, 46f, 43f, 46f, 53f, 55f, 53f) }
                oval(c, color, 32f, 25f, 19f, 19f)
                poly(c, color, 13f, 18f, 51f, 18f, 43f, 40f, 35f, 45f, 35f, 53f, 46f, 56f, 18f, 56f, 29f, 53f, 29f, 45f, 21f, 40f)
                oval(c, accent, 32f, 18f, 19f, 5f)
                line(c, light, 2f, 21f, 25f, 26f, 35f)
            }
            IconGlyph.VASE -> {
                poly(c, color, 23f, 8f, 41f, 8f, 38f, 22f, 49f, 36f, 46f, 51f, 39f, 57f, 25f, 57f, 18f, 51f, 15f, 36f, 26f, 22f)
                oval(c, light, 32f, 9f, 10f, 3f)
                line(c, accent, 4f, 19f, 37f, 45f, 37f)
                line(c, light, 2f, 25f, 26f, 21f, 36f, 24f, 48f)
            }
            IconGlyph.PAINTING -> {
                poly(c, accent, 7f, 10f, 57f, 10f, 57f, 55f, 7f, 55f)
                poly(c, color, 13f, 16f, 51f, 16f, 51f, 49f, 13f, 49f)
                oval(c, light, 23f, 25f, 5f, 5f)
                poly(c, 0xFF4E796C.toInt(), 14f, 47f, 29f, 31f, 38f, 40f, 45f, 34f, 51f, 47f)
                if (v == 1) { line(c, accent, 3f, 26f, 11f, 26f, 54f); line(c, accent, 3f, 42f, 11f, 42f, 54f) }
            }
            IconGlyph.CLOCK -> {
                if (v == 1) poly(c, color, 12f, 57f, 16f, 17f, 32f, 6f, 48f, 17f, 52f, 57f)
                else { oval(c, accent, 32f, 9f, 5f, 5f) }
                oval(c, color, 32f, 34f, 22f, 22f)
                oval(c, light, 32f, 34f, 16f, 16f)
                line(c, edge, 3f, 32f, 23f, 32f, 34f, 42f, 39f)
            }
            IconGlyph.STATUE -> {
                oval(c, color, 32f, 16f, 9f, 11f)
                poly(c, color, 24f, 27f, 40f, 27f, 47f, 45f, 38f, 47f, 38f, 51f, 49f, 51f, 51f, 59f, 13f, 59f, 15f, 51f, 26f, 51f, 26f, 47f, 17f, 45f)
                line(c, light, 2f, 27f, 11f, 26f, 19f)
                line(c, accent, 3f, 25f, 32f, 39f, 32f)
            }
            IconGlyph.BOX -> {
                poly(c, color, 9f, 22f, 42f, 15f, 55f, 25f, 55f, 48f, 22f, 57f, 9f, 46f)
                line(c, accent, 3f, 10f, 23f, 23f, 34f, 54f, 26f)
                line(c, edge, 2f, 23f, 34f, 23f, 55f)
                if (v == 1) { oval(c, accent, 32f, 12f, 8f, 7f); line(c, accent, 5f, 32f, 19f, 32f, 24f) }
            }
            IconGlyph.SWORD -> sword(c, color, v)
            IconGlyph.DUAL_SWORDS -> { c.save(); c.scale(-1f, 1f, 32f, 32f); sword(c, accent, 0); c.restore(); sword(c, color, 0) }
            IconGlyph.AXE -> {
                line(c, edge, 8f, 16f, 55f, 44f, 12f); line(c, 0xFF96734C.toInt(), 4f, 16f, 55f, 44f, 12f)
                poly(c, color, 25f, 14f, 44f, 12f, 56f, 25f, 50f, 37f, 32f, 29f)
                line(c, light, 2f, 46f, 17f, 52f, 25f, 48f, 33f)
                if (v > 0) poly(c, accent, 27f, 12f, 14f, 19f, 15f, 29f, 33f, 25f)
            }
            IconGlyph.HAMMER -> {
                line(c, edge, 9f, 20f, 55f, 40f, 20f); line(c, accent, 5f, 20f, 55f, 40f, 20f)
                poly(c, color, 20f, 9f, 53f, 17f, 48f, 35f, 15f, 27f)
                line(c, light, 2f, 22f, 13f, 48f, 20f)
                if (v > 0) poly(c, light, 40f, 5f, 33f, 21f, 41f, 21f, 32f, 43f, 49f, 19f, 41f, 17f)
            }
            IconGlyph.STAFF, IconGlyph.SPEAR -> {
                line(c, edge, 8f, 17f, 56f, 45f, 12f); line(c, accent, 4f, 17f, 56f, 45f, 12f)
                if (g == IconGlyph.SPEAR) poly(c, color, 36f, 14f, 57f, 5f, 50f, 27f, 44f, 22f)
                else {
                    poly(c, color, 35f, 9f, 46f, 4f, 55f, 13f, 50f, 25f, 39f, 23f)
                    line(c, light, 2f, 43f, 9f, 49f, 13f)
                    if (v >= 2) line(c, color, 4f, 31f, 11f, 34f, 24f, 49f, 30f, 57f, 20f)
                }
            }
            IconGlyph.BOW -> {
                line(c, edge, 8f, 16f, 9f, 39f, 15f, 49f, 35f, 47f, 56f)
                line(c, color, 4f, 16f, 9f, 39f, 15f, 49f, 35f, 47f, 56f)
                line(c, light, 1.5f, 16f, 9f, 47f, 56f)
                line(c, accent, 3f, 11f, 49f, 49f, 15f)
                poly(c, accent, 43f, 16f, 55f, 10f, 49f, 22f)
            }
            IconGlyph.SHIELD -> {
                poly(c, color, 10f, 13f, 32f, 6f, 54f, 13f, 49f, 39f, 32f, 57f, 15f, 39f)
                poly(c, accent, 18f, 19f, 32f, 14f, 46f, 19f, 42f, 36f, 32f, 47f, 22f, 36f)
                line(c, light, 2f, 32f, 14f, 32f, 47f)
                if (v == 2) { line(c, edge, 2f, 18f, 25f, 46f, 25f); line(c, edge, 2f, 20f, 34f, 44f, 34f) }
            }
            IconGlyph.CROWN -> { poly(c, color, 10f, 19f, 22f, 27f, 32f, 9f, 42f, 27f, 54f, 19f, 48f, 49f, 16f, 49f); line(c, light, 2f, 18f, 42f, 46f, 42f); oval(c, accent, 32f, 33f, 4f, 5f) }
            IconGlyph.GLOVE -> { poly(c, color, 16f, 52f, 13f, 32f, 9f, 28f, 13f, 21f, 22f, 27f, 24f, 11f, 30f, 10f, 33f, 22f, 36f, 8f, 42f, 10f, 44f, 24f, 50f, 19f, 54f, 24f, 47f, 49f, 38f, 56f); line(c, accent, 4f, 19f, 46f, 43f, 45f) }
            IconGlyph.BOOT -> { poly(c, color, 26f, 10f, 48f, 11f, 43f, 35f, 52f, 45f, 47f, 54f, 11f, 54f, 9f, 45f, 23f, 35f); line(c, accent, 3f, 28f, 17f, 44f, 18f); line(c, light, 2f, 24f, 39f, 36f, 41f) }
            IconGlyph.CLOAK -> { poly(c, color, 23f, 10f, 41f, 10f, 48f, 23f, 57f, 53f, 35f, 46f, 10f, 55f, 18f, 25f); poly(c, edge, 24f, 20f, 32f, 14f, 40f, 20f, 32f, 29f); line(c, accent, 2f, 32f, 30f, 35f, 46f) }
            IconGlyph.HELMET -> { poly(c, color, 12f, 49f, 13f, 25f, 22f, 12f, 40f, 9f, 52f, 24f, 51f, 49f, 39f, 56f, 39f, 34f, 25f, 34f, 25f, 55f); line(c, light, 2f, 31f, 14f, 31f, 30f); line(c, edge, 3f, 18f, 29f, 45f, 29f) }
            IconGlyph.ARMLET -> { poly(c, color, 11f, 21f, 46f, 12f, 54f, 43f, 20f, 54f); line(c, accent, 4f, 17f, 24f, 44f, 18f); line(c, accent, 4f, 22f, 46f, 48f, 39f); poly(c, accent, 26f, 27f, 37f, 23f, 43f, 34f, 32f, 40f) }
            IconGlyph.RING -> { oval(c, accent, 32f, 36f, 20f, 19f); oval(c, edge, 32f, 37f, 12f, 11f); poly(c, color, 22f, 17f, 32f, 9f, 43f, 17f, 36f, 27f, 26f, 26f); if (v == 2) { poly(c, light, 13f, 36f, 22f, 40f, 20f, 54f); poly(c, light, 51f, 36f, 42f, 40f, 44f, 54f) } }
            IconGlyph.AMULET -> { line(c, accent, 3f, 13f, 10f, 32f, 27f, 51f, 10f); poly(c, color, 32f, 22f, 50f, 36f, 32f, 57f, 14f, 36f); poly(c, accent, 32f, 30f, 40f, 37f, 32f, 48f, 24f, 37f) }
            IconGlyph.GEM, IconGlyph.SHARD -> gem(c, color)
            IconGlyph.ORB -> { oval(c, color, 32f, 33f, 23f, 23f); line(c, light, 3f, 18f, 25f, 23f, 19f, 29f, 17f); poly(c, accent, 41f, 10f, 51f, 19f, 45f, 26f, 35f, 18f); if (v == 2) { oval(c, ItemIconCatalog.RED, 21f, 36f, 5f, 5f); oval(c, ItemIconCatalog.BLUE, 39f, 36f, 5f, 5f); oval(c, ItemIconCatalog.GREEN, 31f, 46f, 5f, 5f) } }
            IconGlyph.MASK -> { poly(c, color, 11f, 14f, 32f, 8f, 53f, 14f, 48f, 43f, 32f, 57f, 16f, 43f); poly(c, edge, 17f, 24f, 29f, 29f, 20f, 33f); poly(c, edge, 47f, 24f, 35f, 29f, 44f, 33f); line(c, accent, 3f, 24f, 42f, 32f, if (v > 0) 49f else 45f, 40f, 42f); if (v == 2) { poly(c, accent, 13f, 20f, 7f, 5f, 26f, 15f); poly(c, accent, 51f, 20f, 57f, 5f, 38f, 15f) } }
            IconGlyph.BUTTERFLY -> { poly(c, color, 29f, 28f, 11f, 10f, 8f, 31f, 23f, 40f, 15f, 55f, 31f, 43f); poly(c, color, 35f, 28f, 53f, 10f, 56f, 31f, 41f, 40f, 49f, 55f, 33f, 43f); line(c, accent, 4f, 32f, 21f, 32f, 49f) }
            IconGlyph.HEART -> { poly(c, color, 32f, 20f, 20f, 10f, 9f, 19f, 10f, 33f, 32f, 55f, 54f, 33f, 55f, 19f, 44f, 10f); line(c, light, 2f, 17f, 23f, 21f, 18f) }
            IconGlyph.PIPE -> { poly(c, color, 10f, 34f, 28f, 34f, 29f, 47f, 42f, 46f, 45f, 32f, 54f, 33f, 50f, 53f, 24f, 57f, 14f, 50f); oval(c, accent, 19f, 33f, 11f, 5f); line(c, light, 2f, 20f, 25f, 15f, 17f, 21f, 8f) }
            IconGlyph.LEAF -> { line(c, accent, 4f, 15f, 55f, 43f, 15f); poly(c, color, 22f, 36f, 12f, 31f, 13f, 13f, 27f, 24f); poly(c, color, 30f, 29f, 36f, 10f, 55f, 8f, 48f, 26f); poly(c, color, 22f, 43f, 42f, 35f, 51f, 42f, 32f, 50f) }
            IconGlyph.POTION -> { poly(c, accent, 23f, 6f, 41f, 6f, 41f, 16f, 23f, 16f); poly(c, light, 24f, 17f, 40f, 17f, 40f, 25f, 51f, 36f, 51f, 52f, 44f, 57f, 20f, 57f, 13f, 52f, 13f, 36f, 24f, 25f); poly(c, color, 17f, 37f, 47f, 37f, 47f, 50f, 42f, 53f, 22f, 53f, 17f, 50f); line(c, light, 2f, 21f, 37f, 21f, 46f) }
            IconGlyph.FLAME -> { poly(c, color, 32f, 5f, 39f, 24f, 49f, 18f, 55f, 37f, 47f, 53f, 29f, 58f, 12f, 47f, 10f, 32f, 22f, 16f, 22f, 32f); poly(c, light, 32f, 30f, 42f, 46f, 33f, 53f, 24f, 44f) }
            IconGlyph.CHEESE -> { poly(c, color, 9f, 31f, 48f, 12f, 54f, 48f, 12f, 55f); line(c, light, 2f, 13f, 34f, 48f, 19f); oval(c, edge, 23f, 43f, 3f, 3f); oval(c, edge, 40f, 35f, 4f, 4f) }
            IconGlyph.SCROLL -> { poly(c, light, 14f, 9f, 49f, 9f, 45f, 53f, 15f, 53f); oval(c, accent, 15f, 13f, 7f, 6f); oval(c, accent, 46f, 51f, 7f, 6f)
                val product = IconGlyph.entries.getOrNull(v)?.takeIf { it != IconGlyph.SCROLL } ?: IconGlyph.SWORD
                c.save(); c.translate(18f, 19f); c.scale(.42f, .42f); drawGlyph(c, product, color, accent, 0); c.restore()
            }
            IconGlyph.FABRIC -> { poly(c, color, 14f, 11f, 52f, 19f, 46f, 54f, 9f, 46f); line(c, light, 2f, 18f, 18f, 44f, 24f); line(c, accent, 2f, 18f, 40f, 41f, 45f); line(c, edge, 1.5f, 26f, 16f, 20f, 46f, 33f, 49f, 39f, 19f) }
            IconGlyph.LOCK -> { p.color = accent; p.style = Paint.Style.STROKE; p.strokeWidth = 7f; c.drawArc(19f, 6f, 45f, 39f, 180f, 180f, false, p); poly(c, color, 12f, 26f, 52f, 26f, 52f, 55f, 12f, 55f); oval(c, edge, 32f, 38f, 4f, 4f); line(c, edge, 4f, 32f, 39f, 32f, 47f) }
            IconGlyph.FRAME -> { poly(c, color, 8f, 8f, 56f, 8f, 56f, 56f, 8f, 56f); poly(c, edge, 18f, 18f, 46f, 18f, 46f, 46f, 18f, 46f); line(c, light, 2f, 12f, 12f, 52f, 12f) }
            IconGlyph.CLAW -> { repeat(3) { n -> val k = n * 13f; poly(c, color, 12f + k, 52f, 13f + k, 25f, 24f + k, 9f, 22f + k, 33f) } }
            IconGlyph.SPIRAL -> { line(c, color, 5f, 20f, 48f, 43f, 45f, 51f, 32f, 40f, 16f, 22f, 14f, 12f, 28f, 18f, 37f, 32f, 39f, 38f, 30f, 31f, 24f, 25f, 30f) }
            IconGlyph.STAR -> { poly(c, color, 32f, 5f, 38f, 24f, 59f, 27f, 41f, 38f, 46f, 58f, 32f, 45f, 15f, 56f, 21f, 36f, 5f, 23f, 25f, 23f) }
            IconGlyph.DROP -> { poly(c, color, 32f, 6f, 51f, 32f, 52f, 43f, 42f, 55f, 23f, 56f, 12f, 44f, 13f, 32f); line(c, light, 2f, 21f, 36f, 20f, 43f) }
            IconGlyph.SPORE -> { repeat(6) { n -> val a = n * Math.PI / 3; line(c, color, 3f, 32f, 32f, 32 + kotlin.math.cos(a).toFloat() * 24, 32 + kotlin.math.sin(a).toFloat() * 24) }; oval(c, color, 32f, 32f, 16f, 16f); oval(c, accent, 27f, 27f, 4f, 4f); oval(c, accent, 37f, 36f, 3f, 3f) }
            IconGlyph.CHAIN -> { oval(c, color, 23f, 24f, 13f, 17f); oval(c, edge, 23f, 24f, 6f, 10f); oval(c, color, 41f, 41f, 13f, 17f); oval(c, edge, 41f, 41f, 6f, 10f); line(c, accent, 4f, 25f, 28f, 38f, 38f) }
            IconGlyph.CROSS -> { line(c, color, 6f, 12f, 32f, 52f, 32f); line(c, color, 6f, 32f, 12f, 32f, 52f); oval(c, accent, 32f, 32f, 10f, 10f); line(c, light, 2f, 18f, 18f, 46f, 46f) }
            IconGlyph.EYE -> { poly(c, color, 5f, 32f, 19f, 18f, 44f, 18f, 59f, 32f, 44f, 46f, 19f, 46f); oval(c, edge, 32f, 32f, 10f, 12f); oval(c, accent, 33f, 31f, 3f, 7f) }
            IconGlyph.SNOW -> { repeat(3) { n -> c.save(); c.rotate(n * 60f, 32f, 32f); line(c, color, 3f, 32f, 7f, 32f, 57f); line(c, color, 2f, 24f, 15f, 32f, 23f, 40f, 15f); c.restore() } }
            IconGlyph.BAN -> { oval(c, color, 32f, 32f, 24f, 24f); oval(c, edge, 32f, 32f, 16f, 16f); line(c, color, 6f, 15f, 49f, 49f, 15f) }
        }
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Required by Drawable") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 64
    override fun getIntrinsicHeight() = 64
}
