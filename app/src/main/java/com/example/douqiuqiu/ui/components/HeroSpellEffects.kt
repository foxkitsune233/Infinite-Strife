package com.example.douqiuqiu.ui.components

import android.graphics.*
import com.example.douqiuqiu.journey.*
import kotlin.math.*

/** Lightweight geometric FX in a 360-unit scene, with no random numbers or rule changes. */
internal class HeroSpellEffects {
    private val expansion = ExpansionSpellEffects()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mint = Color.rgb(116, 244, 192)
    private val cyan = Color.rgb(120, 204, 255)
    private val venom = Color.rgb(163, 230, 52)
    private fun x(index: Int) = if (index == 0) 90f else 270f
    private fun fill(color: Int, alpha: Int = 255) { p.style = Paint.Style.FILL; p.color = color; p.alpha = alpha; p.shader = null }
    private fun stroke(color: Int, width: Float, alpha: Int = 255) { fill(color, alpha); p.style = Paint.Style.STROKE; p.strokeWidth = width }

    fun behind(c: Canvas, state: DuelState, h: Float, time: Long) {
        val units = listOf(state.player, state.enemy)
        // Persisted status drives the lasting tint; a cast flash alone cannot claim a 3s stop.
        if (units.any { f -> f.timedControls.any { it.kind.name == "TIME_STOP" && it.remainingMs > 0 } }) {
            fill(Color.rgb(102, 42, 184), 65); c.drawRect(0f, 0f, 360f, h, p)
        }
        units.forEachIndexed { index, f ->
            if (f.ghost > 0) {
                val phase = time / 700.0
                fill(mint, 28); c.drawOval(x(index) - 65, h * .37f, x(index) + 65, h * .98f, p)
                stroke(mint, 2f, 170)
                c.drawOval(x(index) - 54, h * .84f, x(index) + 54, h * .98f, p)
                repeat(5) { n ->
                    val a = phase + n * Math.PI * .4
                    val px = x(index) + cos(a).toFloat() * 48
                    val py = h * (.82f - ((n * .17f + time % 1600 / 1600f) % 1f) * .42f)
                    stroke(mint, 2f, 100)
                    c.drawArc(px - 8, py - 17, px + 8, py + 17, -90f, 150f, false, p)
                }
            }
            if (f.flareLeft > 0) {
                val targetX = x(1 - index)
                fill(cyan, 35); c.drawOval(targetX - 58, h * .80f, targetX + 58, h * .99f, p)
                stroke(cyan, 2f, 210); c.drawOval(targetX - 56, h * .82f, targetX + 56, h * .98f, p)
                c.drawOval(targetX - 43, h * .85f, targetX + 43, h * .95f, p)
                repeat(6) { n ->
                    val a = n * Math.PI / 3 + time / 2300.0
                    val px = targetX + cos(a).toFloat() * 48
                    val py = h * .90f + sin(a).toFloat() * 13
                    c.drawLine(px - 3, py - 4, px + 3, py + 4, p)
                }
            }
        }
    }

    fun front(c: Canvas, state: DuelState, h: Float, time: Long, cues: List<DuelVisualCue>) {
        expansion.draw(c, h, time, cues)
        listOf(state.player, state.enemy).forEachIndexed { index, f ->
            if (f.heroId == "abaddon" && FighterEffects.shields(f).entries.any { it.sourceId == "hero:aphotic" && it.amount > 0 }) {
                val r = RectF(x(index) - 61, h * .26f, x(index) + 61, h * .96f)
                fill(mint, 22); c.drawOval(r, p)
                stroke(mint, 2f, 200); c.drawOval(r, p)
                stroke(Color.WHITE, 1f, 110); c.drawArc(r, 200f, 85f, false, p)
            }
            if (f.heroId == "phantom_assassin" && f.criticalReady > 0 && f.hp > 0) {
                mark(c, x(1 - index), h * .20f, GameUi.danger, false)
            }
            if (f.broken > 0) mark(c, x(index), h * .32f, venom, true)
        }
        cues.forEach { cue ->
            val age = time - cue.tick * 100L
            if (age < 0) return@forEach
            val sourceX = x(cue.actor); val targetX = x(cue.target)
            when (cue.kind) {
                DuelCueKind.OMNI_SLASH -> if (age < 260) slash(c, targetX, h * .57f, age, cue.tick)
                DuelCueKind.FLARE_TICK -> if (age < 220) {
                    val alpha = ((1 - age / 220f) * 220).toInt()
                    val beamX = targetX + ((cue.tick % 3) - 1) * 17f
                    fill(cyan, alpha / 3); c.drawRect(beamX - 18, h * .06f, beamX + 18, h * .91f, p)
                    fill(cyan, alpha); c.drawRect(beamX - 6, h * .02f, beamX + 6, h * .91f, p)
                    fill(Color.WHITE, alpha); c.drawRect(beamX - 1.5f, 0f, beamX + 1.5f, h * .90f, p)
                    ring(c, targetX, h * .89f, age / 220f, cyan, 45f)
                }
                DuelCueKind.PA_CRITICAL_HIT -> if (age < 420) blood(c, targetX, h * .50f, age / 420f, if (cue.actor == 0) 1 else -1)
                DuelCueKind.ULTIMATE_CAST -> when (cue.heroId) {
                    "abaddon" -> if (age < 650) {
                        ring(c, sourceX, h * .59f, age / 650f, mint, 68f)
                        if (age < 350) {
                            stroke(Color.WHITE, 2f, ((1 - age / 350f) * 220).toInt())
                            c.drawLine(sourceX, h * .13f, sourceX, h * .72f, p)
                            c.drawLine(sourceX - 27, h * .34f, sourceX + 27, h * .34f, p)
                        }
                        ring(c, targetX, h * .58f, age / 650f, mint, 48f)
                    }
                    "viper" -> venomShot(c, sourceX, targetX, h, age)
                    else -> Unit
                }
                else -> Unit
            }
        }
    }

    private fun mark(c: Canvas, cx: Float, cy: Float, color: Int, broken: Boolean) {
        stroke(color, 2f)
        val diamond = Path().apply { moveTo(cx, cy - 10); lineTo(cx + 9, cy); lineTo(cx, cy + 10); lineTo(cx - 9, cy); close() }
        c.drawPath(diamond, p)
        if (broken) {
            c.drawLine(cx - 3, cy - 7, cx + 2, cy, p); c.drawLine(cx + 2, cy, cx - 2, cy + 7, p)
        } else {
            c.drawLine(cx - 5, cy - 4, cx + 5, cy + 4, p); c.drawLine(cx + 5, cy - 4, cx - 5, cy + 4, p)
        }
    }
    private fun ring(c: Canvas, cx: Float, cy: Float, progress: Float, color: Int, radius: Float) {
        val r = 12 + radius * progress
        stroke(color, 3f * (1 - progress) + 1, ((1 - progress) * 220).toInt().coerceIn(0, 255))
        c.drawOval(cx - r, cy - r * .65f, cx + r, cy + r * .65f, p)
    }
    private fun slash(c: Canvas, cx: Float, cy: Float, age: Long, tick: Int) {
        val fade = (1 - age / 260f).coerceIn(0f, 1f)
        c.save(); c.rotate(if (tick % 2 == 0) -36f else 38f, cx, cy)
        fill(GameUi.gold, (90 * fade).toInt()); c.drawOval(cx - 67, cy - 11, cx + 67, cy + 11, p)
        val blade = Path().apply { moveTo(cx - 72, cy + 5); quadTo(cx + 5, cy - 19, cx + 74, cy - 6); quadTo(cx - 5, cy - 3, cx - 72, cy + 5); close() }
        fill(Color.WHITE, (255 * fade).toInt()); c.drawPath(blade, p)
        stroke(GameUi.gold, 2f, (200 * fade).toInt()); c.drawLine(cx - 49, cy + 13, cx + 56, cy + 6, p)
        c.restore()
    }
    private fun blood(c: Canvas, cx: Float, cy: Float, progress: Float, direction: Int) {
        // Stylized red fragments, no anatomical gore or persistent stains.
        repeat(9) { n ->
            val angle = -1.15 + n * .29
            val distance = 10 + progress * (32 + n % 3 * 9)
            val px = cx + cos(angle).toFloat() * distance * direction
            val py = cy + sin(angle).toFloat() * distance + progress * progress * 22
            val fragment = Path().apply { moveTo(px, py - 3); lineTo(px + 7 * direction, py); lineTo(px - 2 * direction, py + 3); close() }
            fill(if (n % 2 == 0) Color.rgb(213, 55, 65) else Color.rgb(248, 103, 80), ((1 - progress) * 240).toInt())
            c.drawPath(fragment, p)
        }
    }
    private fun venomShot(c: Canvas, from: Float, to: Float, h: Float, age: Long) {
        val mouth = from + if (to > from) 25 else -25
        if (age in 0..180) {
            fill(venom, 80); c.drawCircle(mouth, h * .45f, 5 + age / 30f, p)
        } else if (age in 181..460) {
            val t = (age - 180) / 280f
            val px = mouth + (to - mouth) * t
            val py = h * .46f - sin(t * Math.PI).toFloat() * 13
            stroke(venom, 6f, 160); c.drawLine(px - (to - mouth) * .15f, py + 4, px, py, p)
            fill(venom); c.drawOval(px - 9, py - 5, px + 9, py + 5, p)
            fill(Color.WHITE, 190); c.drawCircle(px - 2, py - 2, 2f, p)
        } else if (age in 461..850) {
            val t = (age - 460) / 390f
            ring(c, to, h * .46f, t, venom, 34f)
            repeat(7) { n ->
                val a = n * Math.PI * 2 / 7
                fill(venom, ((1 - t) * 210).toInt())
                c.drawCircle(to + cos(a).toFloat() * 36 * t, h * .46f + sin(a).toFloat() * 24 * t, 3.5f, p)
            }
        }
    }
}
