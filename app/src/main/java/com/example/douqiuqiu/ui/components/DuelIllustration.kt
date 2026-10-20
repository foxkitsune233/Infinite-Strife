package com.example.douqiuqiu.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import com.example.douqiuqiu.journey.*
import kotlin.math.min

/** Animates committed semantic cues. Simulation time is the clock; no timers advance game state. */
internal class DuelIllustration(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spells = HeroSpellEffects()
    private val attacks = AttackEffects()
    private var state: DuelState? = null
    private val cues = mutableListOf<DuelVisualCue>()
    private val seen = mutableSetOf<DuelVisualCue>()
    private var boundAt = 0L
    private val hitTicks = intArrayOf(-100, -100)
    private val pushTicks = intArrayOf(-100, -100)
    private val attackDurations = longArrayOf(420, 420)
    private val attackStartedAt = longArrayOf(-10000, -10000)
    private val swingNumbers = intArrayOf(-1, -1)
    private val attackOrigins = arrayOf(PointF(90f, 110f), PointF(270f, 110f))
    private val greenFilter = LightingColorFilter(0x0088FFAA, 0x00003311)
    // Gameplay sprite frames are independent of Android's transition/animator scale.
    private val motion: Boolean get() = com.example.douqiuqiu.ui.AppSettings.animations(context)

    fun bind(next: DuelState, events: List<DuelVisualCue>) {
        val previous = state
        if (previous != null && (next.tick < previous.tick || next.player.heroId != previous.player.heroId || next.enemy.heroId != previous.enemy.heroId)) {
            cues.clear(); seen.clear()
            hitTicks.fill(-100)
            pushTicks.fill(-100)
            attackDurations.fill(420)
            attackStartedAt.fill(-10000)
            swingNumbers.fill(-1)
        }
        if (previous?.tick != next.tick) boundAt = SystemClock.uptimeMillis()
        previous?.let {
            if (it.player.hp > next.player.hp) hitTicks[0] = next.tick
            if (it.enemy.hp > next.enemy.hp) hitTicks[1] = next.tick
            if (next.player.equipment.protectionMs > it.player.equipment.protectionMs) pushTicks[0] = next.tick
            if (next.enemy.equipment.protectionMs > it.enemy.equipment.protectionMs) pushTicks[1] = next.tick
        }
        events.filter { it.tick == next.tick && it.actor in 0..1 && it.target == 1 - it.actor && seen.add(it) }.forEach {
            cues += it
            if (it.kind == DuelCueKind.ATTACK) {
                val fighter = if (it.actor == 0) next.player else next.enemy
                val now = SystemClock.uptimeMillis()
                // Finish the visible swing instead of restarting windup on every high-speed hit.
                // Hit cues remain unmerged and continue to reflect every actual combat event.
                if (now - attackStartedAt[it.actor] >= attackDurations[it.actor]) {
                    swingNumbers[it.actor]++
                    attackStartedAt[it.actor] = now
                    attackDurations[it.actor] = AttackAnimationClock.durationMs(fighter.attackIn).coerceAtLeast(180)
                }
            }
        }
        cues.removeAll { next.tick - it.tick > 15 }
        seen.removeAll { next.tick - it.tick > 15 }
        state = next; invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); boundAt = SystemClock.uptimeMillis() }
    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        val enabled = motion
        val scale = width / 360f
        if (scale <= 0) return
        canvas.save(); canvas.scale(scale, scale)
        val h = height / scale
        val elapsed = (SystemClock.uptimeMillis() - boundAt).coerceAtLeast(0)
        val fractional = if (enabled) min(elapsed, if (s.won != null) 1100L else 100L) else 0L
        val time = s.tick * 100L + fractional
        val scenery = GameArt.scene(context, SceneKind.ARENA)
        if (scenery != null) GameArt.cover(canvas, scenery, RectF(0f, 0f, 360f, h), Paint(Paint.FILTER_BITMAP_FLAG))
        else SceneArtwork.draw(canvas, 360f, h, SceneKind.ARENA)
        spells.behind(canvas, s, h, if (enabled) time else 0L)
        listOf(s.player, s.enemy).forEachIndexed { index, f -> drawHero(canvas, f, index, h, time, enabled) }
        spells.front(canvas, s, h, time, if (enabled) cues else emptyList())
        if (enabled) attacks.draw(canvas, h, time, cues, attackOrigins)
        canvas.restore()
        val partialCanAdvance = elapsed < if (s.won != null) 1100L else 100L
        val hasEffects = pushTicks.any { time - it * 100L in 0..600 } || cues.any { time - it.tick * 100L < 1100 } || s.player.ghost > 0 || s.enemy.ghost > 0 ||
            s.player.invulnerable > 0 || s.enemy.invulnerable > 0 || s.player.flareLeft > 0 || s.enemy.flareLeft > 0
        val swingActive = attackStartedAt.indices.any { SystemClock.uptimeMillis() - attackStartedAt[it] in 0 until attackDurations[it] }
        if (enabled && ((hasEffects && partialCanAdvance) || swingActive) && isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawHero(canvas: Canvas, f: Fighter, index: Int, h: Float, time: Long, enabled: Boolean) {
        val pushAge = time - pushTicks[index] * 100L
        val push = if (enabled && pushAge in 0..600) kotlin.math.sin(pushAge / 600.0 * Math.PI).toFloat() else 0f
        val center = (if (index == 0) 90f else 270f) + (if (index == 0) -1 else 1) * push * 14f
        if (push > 0) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = GameUi.gold; paint.alpha = (180 * push).toInt()
            canvas.drawOval(center - 38f - push * 9f, h * .84f, center + 38f + push * 9f, h * .96f, paint)
            repeat(3) { n ->
                val x = center + (if (index == 0) 1 else -1) * (22 + n * 8)
                canvas.drawLine(x, h * .54f, x + (if (index == 0) 12 else -12), h * .59f, paint)
            }
            paint.alpha = 255
        }
        paint.color = 0x55000000; paint.style = Paint.Style.FILL
        canvas.drawOval(center - 51f, h * .9f, center + 51f, h * .98f, paint)
        val own = cues.filter { it.actor == index }
        val cast = own.lastOrNull { it.kind == DuelCueKind.ULTIMATE_CAST }?.let { time - it.tick * 100L }
        val attack = (SystemClock.uptimeMillis() - attackStartedAt[index]).takeIf { it >= 0 && it < attackDurations[index] }
        val pose = when {
            !enabled || f.ghost > 0 || f.hp <= 0 -> "idle"
            f.heroId == "abaddon" && cast != null && cast in 0..550 -> "cast"
            f.heroId == "skywrath" && cast != null && cast in 0..450 -> "cast"
            f.heroId == "viper" && cast != null && cast in 0..180 -> "charge"
            f.heroId == "viper" && cast != null && cast in 181..420 -> "attack"
            attack != null && attack >= 0 && attack < attackDurations[index] -> "attack"
            else -> "idle"
        }
        val baseFrameIndex = when {
            !enabled || f.ghost > 0 || f.hp <= 0 -> 0
            f.heroId == "viper" && pose == "charge" -> 1
            f.heroId == "viper" && cast != null && cast in 181..420 -> 2
            attack != null && pose == "attack" -> AttackAnimationClock.frameAt(attack, attackDurations[index])
            else -> 0
        }
        // Every second displayed swing uses newly drawn rear-hand windup and strike.
        // Actual attack cues, damage, target and cadence remain the engine's responsibility.
        val alternatingHero = f.heroId == "juggernaut" || f.heroId == "phantom_assassin"
        val frameIndex = if (alternatingHero && swingNumbers[index] % 2 == 1 && pose == "attack") {
            when (baseFrameIndex) { 1 -> 4; 2 -> 5; else -> baseFrameIndex }
        } else baseFrameIndex
        // String is the stable machine cue name, allowing the independent shared update
        // to append the enum without making this visual-only batch change combat rules.
        val helixAge = if (enabled && f.heroId == "axe" && f.hp > 0) own.lastOrNull {
            it.kind.name == "COUNTER_HELIX"
        }?.let { time - it.tick * 100L } else null
        val sprite = helixAge?.let { AttackAtlas.helixFrame(context, it) }
            ?: if (pose != "cast") AttackAtlas.frame(context, f.heroId, frameIndex) else null
        val bitmap = sprite?.bitmap ?: GameArt.pose(context, f.heroId, pose) ?: return
        val anchor = HeroPoseLayout.anchor(f.heroId, pose)
        val spriteScale = if (sprite != null) minOf(175f / sprite.contentWidth, h * .65f / sprite.bodyHeight,
            h * .86f / 512f)
            else min(171f / bitmap.width, h * anchor.heightFraction / (bitmap.height * anchor.bodyFraction))
        val bw = (if (sprite != null) 512 else bitmap.width) * spriteScale
        val bh = (if (sprite != null) 512 else bitmap.height) * spriteScale
        val left = center - if (sprite != null) sprite.rootX * spriteScale else bw * anchor.x
        val ground = h * .88f
        val top = ground - if (sprite != null) sprite.rootY * spriteScale else bh * anchor.y
        val rect = RectF(left, top, left + bw, top + bh)
        val facing = if (index == 0) 1f else -1f
        attackOrigins[index].set(if (sprite != null) center + (sprite.attackOriginX - sprite.rootX) * spriteScale * facing else center,
            if (sprite != null) ground + (sprite.attackOriginY - sprite.rootY) * spriteScale else h * .53f)
        paint.color = Color.WHITE; paint.alpha = when { f.hp <= 0 -> 85; f.invulnerable > 0 && f.heroId == "juggernaut" -> 18; f.ghost > 0 -> 135; else -> 255 }
        paint.colorFilter = if (f.ghost > 0) greenFilter else null
        canvas.save()
        // Mirror the entire actor, never one frame or weapon independently.
        if (index == 1) canvas.scale(-1f, 1f, center, h / 2)
        canvas.drawBitmap(bitmap, sprite?.source, rect, paint); canvas.restore()
        paint.alpha = 255; paint.colorFilter = null
        if (enabled && time - hitTicks[index] * 100L in 0..140) {
            paint.color = GameUi.danger; canvas.drawCircle(center, h * .4f, 3f, paint)
        }
    }
}
