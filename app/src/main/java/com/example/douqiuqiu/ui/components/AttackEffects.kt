package com.example.douqiuqiu.ui.components

import android.graphics.*
import com.example.douqiuqiu.content.DamageType
import com.example.douqiuqiu.journey.*
import kotlin.math.*

/** All motion is derived from committed cues; no speculative hit or combat RNG. */
internal class AttackEffects {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun x(i: Int) = if (i == 0) 90f else 270f
    private fun color(hero: String) = when (hero) {
        "juggernaut" -> 0xFFFFD4A0.toInt(); "phantom_assassin" -> 0xFF73F0E4.toInt()
        "abaddon" -> 0xFFA9EBDC.toInt(); "skywrath" -> 0xFF8AD5FF.toInt()
        "necrophos" -> 0xFF7AEFA0.toInt(); else -> 0xFFB5DC54.toInt()
    }
    private fun stroke(color: Int, alpha: Int, width: Float) {
        p.color = color; p.alpha = alpha.coerceIn(0, 255); p.style = Paint.Style.STROKE
        p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND
    }
    fun draw(c: Canvas, h: Float, time: Long, cues: List<DuelVisualCue>, origins: Array<PointF>) {
        cues.filter { it.kind == DuelCueKind.ATTACK }.forEach { cue ->
            val age = time - cue.tick * 100L
            if (age !in 0..300) return@forEach
            val to = x(cue.target); val tint = color(cue.heroId)
            val ranged = cue.heroId in setOf("skywrath", "necrophos", "viper")
            if (ranged) {
                val origin = origins[cue.actor]
                val t = (age / 210f).coerceIn(0f, 1f)
                val px = origin.x + (to - origin.x) * t
                val py = origin.y + (h * .53f - origin.y) * t - sin(t * PI).toFloat() * 12
                if (age <= 210) {
                    stroke(tint, 110, 8f); c.drawLine(px - (to - origin.x) * .12f, py + 3, px, py, p)
                    p.style = Paint.Style.FILL; p.color = tint; p.alpha = 240
                    c.drawOval(px - 7, py - 4, px + 7, py + 4, p)
                    p.color = Color.WHITE; c.drawCircle(px, py, 2f, p)
                }
            } else {
                val t = age / 300f
                val bounds = RectF(to - 42, h * .50f - 37, to + 42, h * .50f + 37)
                val start = if (cue.actor == 0) 210f else -30f
                stroke(tint, ((1 - t) * 220).toInt(), if (cue.heroId == "abaddon") 5f else 3f)
                c.drawArc(bounds, start, 150 * t, false, p)
                if (cue.heroId == "phantom_assassin") {
                    bounds.offset(0f, 16f); c.drawArc(bounds, start + 30, 125 * t, false, p)
                }
            }
        }
        cues.filter { it.kind == DuelCueKind.EVADED || it.kind == DuelCueKind.ATTACK_BLOCKED }.forEach { cue ->
            val age = time - cue.tick * 100L
            if (age !in 0..650) return@forEach
            val tint = if (cue.kind == DuelCueKind.EVADED) GameUi.muted else GameUi.gold
            stroke(tint, ((1 - age / 650f) * 200).toInt(), 2f)
            c.drawArc(x(cue.target) - 33, h * .34f, x(cue.target) + 33, h * .73f, 190f, 160f, false, p)
            text(c, if (cue.kind == DuelCueKind.EVADED) "闪避" else "普攻无效", x(cue.target), h * .28f - age / 60f, tint, 12f, p.alpha)
        }
        // Merge bursts and many poison layers into bounded readable numbers, preserving actual totals.
        data class Key(val actor: Int, val target: Int, val kind: DuelCueKind, val type: DamageType?, val critical: Boolean, val bucket: Int)
        val groups = cues.filter { it.kind == DuelCueKind.HIT || it.kind == DuelCueKind.POISON_HIT || it.kind == DuelCueKind.FROST_HIT }
            .groupBy { Key(it.actor, it.target, it.kind, it.damageType, it.critical, it.tick / 5) }
            .values.sortedBy { group -> group.maxOf { it.tick } }.takeLast(6)
        groups.forEachIndexed { lane, group ->
            val cue = group.last(); val age = time - cue.tick * 100L
            if (age !in 0..850) return@forEachIndexed
            val hp = group.sumOf { it.lifeDamage }; val shield = group.sumOf { it.shieldDamage }
            val poison = cue.kind == DuelCueKind.POISON_HIT
            val frost = cue.kind == DuelCueKind.FROST_HIT
            val tint = when { frost -> 0xFF9EDBFF.toInt(); poison -> 0xFFB5DC54.toInt(); cue.critical -> 0xFFFFB65D.toInt()
                cue.damageType == DamageType.MAGICAL -> 0xFF9EDBFF.toInt(); else -> Color.WHITE }
            val alpha = ((1 - age / 850f) * 255).toInt()
            val cx = x(cue.target) + (lane % 2 * 2 - 1) * 15
            val cy = h * .40f - age / 35f - lane % 3 * 15
            if (age < 220 && !poison && !frost) {
                stroke(if (shield > 0) GameUi.gold else tint, alpha, if (cue.critical) 3f else 2f)
                repeat(6) { n ->
                    val a = n * PI / 3 + cue.tick
                    val r = 6 + age / 12f
                    c.drawLine(x(cue.target) + cos(a).toFloat() * r, h * .54f + sin(a).toFloat() * r,
                        x(cue.target) + cos(a).toFloat() * (r + 8), h * .54f + sin(a).toFloat() * (r + 8), p)
                }
            }
            val hpLabel = if (hp > 0) "${if (frost) "霜 " else if (poison) "毒 " else if (cue.critical) "暴击 " else ""}${ceil(hp).toInt()}" else ""
            val shieldLabel = if (shield > 0) "盾 ${ceil(shield).toInt()}" else ""
            text(c, listOf(hpLabel, shieldLabel).filter { it.isNotEmpty() }.joinToString(" / "), cx, cy,
                if (hp <= 0) GameUi.gold else tint, if (cue.critical) 16f else 12f, alpha)
        }
    }
    private fun text(c: Canvas, label: String, x: Float, y: Float, tint: Int, size: Float, alpha: Int) {
        p.textAlign = Paint.Align.CENTER; p.textSize = size; p.typeface = Typeface.DEFAULT_BOLD
        stroke(GameUi.background, alpha, 3f); c.drawText(label, x, y, p)
        p.style = Paint.Style.FILL; p.color = tint; p.alpha = alpha.coerceIn(0, 255); c.drawText(label, x, y, p)
    }
}
