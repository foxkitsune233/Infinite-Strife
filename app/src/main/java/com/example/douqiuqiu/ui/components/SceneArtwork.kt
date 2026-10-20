package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.view.View
import com.example.douqiuqiu.event.EventKind

enum class SceneKind { CAMP, ARENA, REST, DEPOT, WORKSHOP, LIBRARY, STORAGE, VAULT, TERMINAL, CLINIC, BLOCKER }

/** Original layered paper-cut scenery. Text and interactive controls remain native Views. */
object SceneArtwork {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun polygon(c: Canvas, color: Int, vararg xy: Float) {
        p.color = color; p.style = Paint.Style.FILL
        val path = Path().apply { moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]); close() }
        c.drawPath(path, p)
    }
    private fun rect(c: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float) {
        p.color = color; p.style = Paint.Style.FILL; c.drawRect(l, t, r, b, p)
    }
    fun event(kind: EventKind?) = when (kind) {
        EventKind.ABANDONED_DEPOT -> SceneKind.DEPOT; EventKind.RUSTED_WORKSHOP -> SceneKind.WORKSHOP
        EventKind.RUINED_LIBRARY -> SceneKind.LIBRARY; EventKind.COLLAPSED_STORAGE -> SceneKind.STORAGE
        EventKind.SAFE_WRECKAGE -> SceneKind.VAULT; EventKind.TECH_TERMINAL -> SceneKind.TERMINAL
        EventKind.FIELD_CLINIC -> SceneKind.CLINIC; EventKind.BLOCKER -> SceneKind.BLOCKER
        // 0.1.5 新事件暂无专属插画，复用同氛围的既有场景，不新增美术资源。
        EventKind.FROZEN_SHRINE -> SceneKind.VAULT; EventKind.WAR_BANNER_RUINS -> SceneKind.BLOCKER
        EventKind.ROVING_CARAVAN -> SceneKind.DEPOT; EventKind.ROPEWAY_BRIDGE -> SceneKind.STORAGE
        EventKind.PACT_ALTAR -> SceneKind.LIBRARY; EventKind.ALCHEMY_LAB -> SceneKind.WORKSHOP
        EventKind.HUNTER_CAMP -> SceneKind.CAMP; EventKind.MOONWELL_ECHO -> SceneKind.CLINIC
        null -> SceneKind.DEPOT
    }
    fun draw(c: Canvas, width: Float, height: Float, kind: SceneKind) {
        c.save(); c.scale(width / 360f, height / 160f)
        rect(c, 0xFF142A38.toInt(), 0f, 0f, 360f, 160f)
        p.color = 0xFF8DA6A3.toInt(); c.drawCircle(285f, 32f, 17f, p)
        polygon(c, 0xFF223D49.toInt(), 0f, 90f, 58f, 23f, 112f, 81f, 184f, 30f, 268f, 85f, 333f, 40f, 360f, 75f, 360f, 160f, 0f, 160f)
        polygon(c, 0xFF1B303A.toInt(), 0f, 115f, 78f, 69f, 141f, 110f, 223f, 61f, 310f, 102f, 360f, 87f, 360f, 160f, 0f, 160f)
        rect(c, 0xFF263D40.toInt(), 0f, 132f, 360f, 160f)
        polygon(c, 0xFF3D4C48.toInt(), 116f, 160f, 169f, 121f, 206f, 121f, 267f, 160f)
        val stone = 0xFF526260.toInt(); val dark = 0xFF101F28.toInt(); val gold = 0xFFCCAE70.toInt()
        when (kind) {
            SceneKind.CAMP, SceneKind.REST, SceneKind.CLINIC -> {
                polygon(c, if (kind == SceneKind.CLINIC) 0xFFBFC7A5.toInt() else 0xFF8F704D.toInt(), 24f, 128f, 89f, 49f, 153f, 128f)
                polygon(c, 0xFF473F35.toInt(), 68f, 128f, 89f, 57f, 108f, 128f)
                rect(c, gold, 89f, 43f, 92f, 132f)
                if (kind == SceneKind.CLINIC) {
                    rect(c, 0xFF799F83.toInt(), 81f, 80f, 98f, 86f); rect(c, 0xFF799F83.toInt(), 86f, 75f, 92f, 92f)
                }
                polygon(c, 0xFF927249.toInt(), 241f, 142f, 273f, 129f, 278f, 135f, 245f, 148f)
                polygon(c, 0xFFFFC46F.toInt(), 244f, 135f, 250f, 111f, 259f, 121f, 267f, 104f, 275f, 131f, 265f, 142f)
                polygon(c, 0xFFEC8455.toInt(), 253f, 137f, 261f, 119f, 267f, 135f, 261f, 143f)
            }
            SceneKind.ARENA, SceneKind.BLOCKER -> {
                listOf(23f, 306f).forEach { x ->
                    polygon(c, stone, x, 135f, x + 4, 43f, x + 14, 27f, x + 31, 41f, x + 35, 135f)
                    polygon(c, dark, x + 14, 47f, x + 31, 41f, x + 35, 135f, x + 19, 135f)
                    rect(c, gold, x - 4, 43f, x + 37, 48f)
                }
                if (kind == SceneKind.BLOCKER) repeat(5) { i -> polygon(c, 0xFF715B45.toInt(), 112f + i * 28, 141f, 122f + i * 28, 91f, 130f + i * 28, 141f) }
                rect(c, 0xFF61716B.toInt(), 68f, 143f, 292f, 147f)
            }
            SceneKind.LIBRARY -> {
                rect(c, dark, 77f, 37f, 282f, 140f)
                repeat(3) { row ->
                    repeat(12) { i -> rect(c, listOf(gold, stone, 0xFF698D87.toInt(), 0xFF895E69.toInt())[i % 4], 88f + i * 15, 50f + row * 29, 97f + i * 15, 69f + row * 29) }
                    rect(c, 0xFF796248.toInt(), 80f, 71f + row * 29, 280f, 76f + row * 29)
                }
                polygon(c, gold, 178f, 141f, 146f, 123f, 176f, 119f, 184f, 123f, 212f, 118f, 223f, 135f)
            }
            SceneKind.VAULT, SceneKind.TERMINAL -> {
                rect(c, stone, 104f, 36f, 260f, 145f); rect(c, dark, 115f, 46f, 248f, 136f)
                p.color = if (kind == SceneKind.TERMINAL) 0xFF7BCAC4.toInt() else gold
                p.style = Paint.Style.STROKE; p.strokeWidth = 5f; c.drawCircle(183f, 91f, 31f, p)
                c.drawLine(163f, 91f, 203f, 91f, p); c.drawLine(183f, 71f, 183f, 111f, p); p.style = Paint.Style.FILL
            }
            SceneKind.WORKSHOP -> {
                rect(c, dark, 78f, 43f, 285f, 137f); rect(c, stone, 90f, 110f, 274f, 120f)
                polygon(c, gold, 156f, 81f, 193f, 81f, 219f, 65f, 210f, 93f, 186f, 101f, 178f, 111f, 154f, 111f)
                rect(c, 0xFF84654D.toInt(), 101f, 120f, 111f, 145f); rect(c, 0xFF84654D.toInt(), 254f, 120f, 264f, 145f)
            }
            SceneKind.DEPOT, SceneKind.STORAGE -> repeat(5) { i ->
                val x = 58f + i * 47; val y = if (i % 2 == 0) 81f else 103f
                rect(c, 0xFF6F634D.toInt(), x, y, x + 40, 141f)
                polygon(c, 0xFF9B8058.toInt(), x, y, x + 8, y - 9, x + 47, y - 9, x + 40, y)
                rect(c, gold, x + 7, y, x + 11, 141f); rect(c, dark, x + 30, y, x + 34, 141f)
            }
        }
        // Small fixed facets provide paper texture without per-frame random numbers.
        repeat(24) { i ->
            val x = (i * 73 % 360).toFloat(); val y = (i * 37 % 160).toFloat()
            polygon(c, 0x0CFFEBC2, x, y, x + 11, y + 2, x + 3, y + 7)
        }
        c.restore()
    }
}

class SceneArtworkView(c: Context, private val kind: SceneKind) : View(c) {
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val image = GameArt.scene(context, kind)
        if (image != null) GameArt.cover(canvas, image, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        else SceneArtwork.draw(canvas, width.toFloat(), height.toFloat(), kind)
    }
}
