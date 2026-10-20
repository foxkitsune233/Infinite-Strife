package com.example.douqiuqiu.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.douqiuqiu.journey.DuelCueKind
import com.example.douqiuqiu.journey.DuelVisualCue

/** Brief cast accents from committed cues. Never guesses skill duration or advances state. */
internal class ExpansionSpellEffects {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: Canvas, height: Float, time: Long, cues: List<DuelVisualCue>) {
        cues.filter { it.kind == DuelCueKind.ULTIMATE_CAST }.forEach { cue ->
            val age = time - cue.tick * 100L
            if (age !in 0..850) return@forEach
            val progress = age / 850f
            val source = if (cue.actor == 0) 90f else 270f
            val target = if (cue.target == 0) 90f else 270f
            paint.style = Paint.Style.FILL
            when (cue.heroId) {
                "antimage", "anti_mage" -> if (age < 320) {
                    val t = age / 320f
                    paint.color = Color.rgb(184, 88, 255); paint.alpha = ((1 - t) * 210).toInt()
                    val burst = Path()
                    repeat(16) { n ->
                        val angle = n * Math.PI / 8
                        val radius = (if (n % 2 == 0) 55f else 19f) * (.3f + t)
                        val x = target + kotlin.math.cos(angle).toFloat() * radius
                        val y = height * .5f + kotlin.math.sin(angle).toFloat() * radius * .8f
                        if (n == 0) burst.moveTo(x, y) else burst.lineTo(x, y)
                    }
                    burst.close(); canvas.drawPath(burst, paint)
                    paint.color = Color.WHITE; paint.alpha = ((1 - t) * 220).toInt()
                    canvas.drawCircle(target, height * .5f, 11 * (1 - t), paint)
                }
                "axe" -> {
                    paint.color = Color.rgb(255, 66, 40); paint.alpha = ((1 - progress) * 170).toInt()
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 8 * (1 - progress) + 1
                    val radius = 14 + progress * 85
                    canvas.drawOval(source - radius, height * .6f - radius * .6f,
                        source + radius, height * .6f + radius * .6f, paint)
                }
                "faceless_void" -> {
                    // Cast flash only. Persistent time-stop tint must follow the future status snapshot.
                    paint.color = Color.rgb(112, 48, 210); paint.alpha = ((1 - progress) * 75).toInt()
                    canvas.drawRect(0f, 0f, 360f, height, paint)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                    paint.alpha = ((1 - progress) * 200).toInt()
                    val radius = 32 + 50 * progress
                    canvas.drawCircle(target, height * .53f, radius, paint)
                }
                "legion_commander" -> listOf(source, target).forEach { x ->
                    paint.color = Color.rgb(247, 67, 45); paint.alpha = ((1 - progress) * 210).toInt()
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                    canvas.drawOval(x - 38, height * .82f, x + 38, height * .95f, paint)
                    canvas.drawLine(x - 10, height * .14f, x - 10, height * .31f, paint)
                    paint.style = Paint.Style.FILL
                    val flag = Path().apply {
                        moveTo(x - 9, height * .14f); lineTo(x + 14, height * .16f)
                        lineTo(x + 7, height * .21f); lineTo(x + 14, height * .25f)
                        lineTo(x - 9, height * .24f); close()
                    }
                    canvas.drawPath(flag, paint)
                }
            }
        }
        paint.alpha = 255; paint.style = Paint.Style.FILL
    }
}
