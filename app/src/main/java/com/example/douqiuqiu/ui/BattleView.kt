package com.example.douqiuqiu.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.example.douqiuqiu.model.BattleConfig
import com.example.douqiuqiu.model.BattleEvent
import com.example.douqiuqiu.model.BattleEventType
import com.example.douqiuqiu.model.Combatant
import com.example.douqiuqiu.model.Side
import kotlin.math.min
import kotlin.math.sin

/**
 * M2：自绘战斗画面。
 *
 * 只做「播放器」：拿 BattleEngine 产出的事件序列，按 tickMs 步长回放。
 * 逻辑全在引擎里，所以这里可以随便加速、跳过、甚至重播，不影响战斗结果。
 *
 * 画面分区：
 *   顶部    对手面板（名字 / 血条 / 护盾条 / 大招 CD）
 *   中部    竞技场（双方蛐蛐、伤害飘字、技能横幅）
 *   中下    战斗日志（最近 4 条）
 *   底部    玩家面板
 */
class BattleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ---------------- 对外接口 ----------------

    private var cfg: BattleConfig = BattleConfig()
    private var events: List<BattleEvent> = emptyList()
    private var eventIndex = 0
    private var virtualTick = 0
    private var speed = 1
    private var running = false
    private var ended = false
    var onBattleEnd: (() -> Unit)? = null

    private var player: Combatant? = null
    private var enemy: Combatant? = null

    /** UI 侧镜像状态：引擎算终态，UI 按事件逐条推进，两边数值始终一致 */
    private class Mirror(val c: Combatant) {
        var hp: Int = c.hp
        var shield: Int = c.shield
        var stun: Int = 0
        val cds: MutableMap<String, Int> = mutableMapOf()
        var deadAt: Long = 0L
    }

    private var pMirror: Mirror? = null
    private var eMirror: Mirror? = null

    fun setup(
        player: Combatant,
        enemy: Combatant,
        cfg: BattleConfig,
        events: List<BattleEvent>,
        speed: Int,
        onEnd: () -> Unit
    ) {
        this.player = player
        this.enemy = enemy
        this.cfg = cfg
        this.events = events
        this.speed = speed
        this.onBattleEnd = onEnd
        this.eventIndex = 0
        this.virtualTick = 0
        this.ended = false

        pMirror = Mirror(player).also { m ->
            player.skills.filter { it.tpl.slot == "ult" }.forEach { m.cds[it.tpl.name] = it.cdLeft }
        }
        eMirror = Mirror(enemy).also { m ->
            enemy.skills.filter { it.tpl.slot == "ult" }.forEach { m.cds[it.tpl.name] = it.cdLeft }
        }
        floats.clear()
        logs.clear()
        bannerText = ""
        bannerAt = 0L
        invalidate()
    }

    fun start() {
        if (running || ended) return
        running = true
        lastFrame = 0L
        acc = 0.0
        handler.post(frame)
    }

    /** 跳过动画：直接把剩余事件全部应用 */
    fun skip() {
        if (ended) return
        while (eventIndex < events.size) {
            applyEvent(events[eventIndex])
            eventIndex++
        }
        running = false
        finishIfNeeded()
        invalidate()
    }

    fun currentSpeed(): Int = speed

    fun toggleSpeed(): Int {
        speed = when (speed) {
            1 -> 2
            2 -> 4
            else -> 1
        }
        return speed
    }

    // ---------------- 播放循环 ----------------

    private val handler = Handler(Looper.getMainLooper())
    private var lastFrame = 0L
    private var acc = 0.0

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            if (lastFrame == 0L) lastFrame = now
            val dt = now - lastFrame
            lastFrame = now

            acc += dt * speed
            while (acc >= cfg.tickMs) {
                acc -= cfg.tickMs
                virtualTick++
                advanceClock()
            }
            while (eventIndex < events.size && events[eventIndex].tick <= virtualTick) {
                applyEvent(events[eventIndex])
                eventIndex++
            }
            if (eventIndex >= events.size) {
                running = false
                finishIfNeeded()
            } else {
                handler.postDelayed(this, 16)
            }
            invalidate()
        }
    }

    private fun finishIfNeeded() {
        if (ended) return
        ended = true
        onBattleEnd?.invoke()
    }

    private fun advanceClock() {
        for (m in listOfNotNull(pMirror, eMirror)) {
            if (m.stun > 0) m.stun--
            for (k in m.cds.keys.toList()) {
                val v = m.cds[k] ?: 0
                if (v > 0) m.cds[k] = v - 1
            }
        }
    }

    private fun mirrorOf(side: Side): Mirror? = if (side == Side.PLAYER) pMirror else eMirror

    private fun applyEvent(e: BattleEvent) {
        val now = System.currentTimeMillis()
        when (e.type) {
            BattleEventType.HIT, BattleEventType.CRIT, BattleEventType.DOT -> {
                val m = mirrorOf(e.target) ?: return
                m.shield = (m.shield - e.shieldPart).coerceAtLeast(0)
                m.hp = (m.hp - (e.value - e.shieldPart)).coerceAtLeast(0)
                val big = e.type == BattleEventType.CRIT
                addFloat(
                    "-${e.value}",
                    spriteX(e.target),
                    spriteY(e.target),
                    if (big) COLOR_CRIT else COLOR_DMG,
                    if (big) 34f else 26f
                )
                flash(e.target, now)
                shake(e.target, now)
                lunge(e.source, now)
                pushLog(e)
            }

            BattleEventType.MISS -> {
                addFloat("闪避", spriteX(e.target), spriteY(e.target), COLOR_MISS, 24f)
                lunge(e.source, now)
                pushLog(e)
            }

            BattleEventType.HEAL, BattleEventType.LIFESTEAL -> {
                val m = mirrorOf(e.target) ?: return
                m.hp = min(m.c.maxHp, m.hp + e.value)
                addFloat("+${e.value}", spriteX(e.target), spriteY(e.target), COLOR_HEAL, 24f)
                pushLog(e)
            }

            BattleEventType.SHIELD -> {
                val m = mirrorOf(e.target) ?: return
                m.shield = min(m.c.maxShield, m.shield + e.value)
                addFloat("护盾+${e.value}", spriteX(e.target), spriteY(e.target), COLOR_SHIELD, 22f)
                pushLog(e)
            }

            BattleEventType.SHIELD_BREAK -> {
                addFloat("护盾碎裂", spriteX(e.target), spriteY(e.target), COLOR_SHIELD, 22f)
                pushLog(e)
            }

            BattleEventType.STUN -> {
                val m = mirrorOf(e.target) ?: return
                m.stun = e.value
                addFloat("眩晕", spriteX(e.target), spriteY(e.target) - 40f, COLOR_STUN, 22f)
                pushLog(e)
            }

            BattleEventType.SKILL -> {
                bannerText = e.skillName.ifEmpty { e.text }
                bannerAt = now
                val m = mirrorOf(e.source)
                if (m != null && m.cds.containsKey(e.skillName)) {
                    val sk = (if (e.source == Side.PLAYER) player else enemy)
                        ?.skills?.firstOrNull { it.tpl.name == e.skillName }
                    m.cds[e.skillName] = sk?.cooldownTicks ?: 0
                }
                pushLog(e)
            }

            BattleEventType.DEATH -> {
                val m = mirrorOf(e.target) ?: return
                m.deadAt = now
                addFloat("💀", spriteX(e.target), spriteY(e.target), COLOR_DMG, 40f)
                pushLog(e)
            }

            BattleEventType.BATTLE_START, BattleEventType.BATTLE_END -> pushLog(e)
        }
    }

    private fun pushLog(e: BattleEvent) {
        val who = when (e.type) {
            // 治疗 / 护盾是施加在自己身上的，按目标算阵营
            BattleEventType.HEAL, BattleEventType.LIFESTEAL, BattleEventType.SHIELD ->
                if (e.target == Side.PLAYER) "我方" else "敌方"
            else -> if (e.source == Side.PLAYER) "我方" else "敌方"
        }
        val line = if (e.text.isNotEmpty()) {
            "${who} ${e.text}"
        } else {
            val name = if (e.skillName.isNotEmpty()) e.skillName else "攻击"
            val tgt = if (e.target == Side.PLAYER) "我方" else "敌方"
            "${who} ${name} → ${tgt} -${e.value}"
        }
        logs.add(0, line)
        while (logs.size > 4) logs.removeAt(logs.size)
    }

    override fun onDetachedFromWindow() {
        running = false
        handler.removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    // ---------------- 动画状态 ----------------

    private class FloatText(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Int,
        val size: Float,
        val born: Long
    )

    private val floats = mutableListOf<FloatText>()
    private val logs = mutableListOf<String>()
    private var bannerText = ""
    private var bannerAt = 0L

    private val flashAt = mutableMapOf<Side, Long>()
    private val shakeAt = mutableMapOf<Side, Long>()
    private val lungeAt = mutableMapOf<Side, Long>()

    private fun addFloat(text: String, x: Float, y: Float, color: Int, size: Float) {
        floats.add(FloatText(text, x, y, color, size, System.currentTimeMillis()))
        if (floats.size > 12) floats.removeAt(0)
    }

    private fun flash(side: Side, now: Long) {
        flashAt[side] = now
    }

    private fun shake(side: Side, now: Long) {
        shakeAt[side] = now
    }

    private fun lunge(side: Side, now: Long) {
        lungeAt[side] = now
    }

    // ---------------- 坐标 ----------------

    private fun spriteX(side: Side): Float {
        val w = width.toFloat()
        return if (side == Side.PLAYER) w * 0.28f else w * 0.72f
    }

    private fun spriteY(side: Side): Float = height * 0.44f

    // ---------------- 绘制 ----------------

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, 2000f,
            intArrayOf(Color.parseColor("#23232B"), Color.parseColor("#14141A")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        panelPaint.color = Color.parseColor("#2A2A31")
        barBgPaint.color = Color.parseColor("#3A3A44")
        hpPaint.color = COLOR_HP
        shieldPaint.color = COLOR_SHIELD
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        smallPaint.color = Color.parseColor("#BBBBBB")
        floatPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        spritePaint.textAlign = Paint.Align.CENTER
        spritePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        circlePaint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val now = System.currentTimeMillis()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        drawArenaFloor(canvas, w, h)
        drawSidePanel(canvas, w, h, enemy, eMirror, top = true)
        drawSidePanel(canvas, w, h, player, pMirror, top = false)
        drawSprite(canvas, Side.ENEMY, now)
        drawSprite(canvas, Side.PLAYER, now)
        drawFloats(canvas, now)
        drawBanner(canvas, w, h, now)
        drawLogs(canvas, w, h)
    }

    private fun drawArenaFloor(canvas: Canvas, w: Float, h: Float) {
        val y = h * 0.56f
        rect.set(0f, y, w, h * 0.62f)
        circlePaint.color = Color.parseColor("#1E1E26")
        canvas.drawRoundRect(rect, 12f, 12f, circlePaint)
        circlePaint.color = Color.parseColor("#FF6D00")
        circlePaint.alpha = 60
        canvas.drawRoundRect(rect, 12f, 12f, circlePaint)
        circlePaint.alpha = 255
    }

    /** 顶 / 底信息面板：名字、血条、护盾条、大招 CD */
    private fun drawSidePanel(
        canvas: Canvas,
        w: Float,
        h: Float,
        c: Combatant?,
        m: Mirror?,
        top: Boolean
    ) {
        if (c == null || m == null) return
        val pad = 14f
        val panelH = h * 0.155f
        val yTop = if (top) pad else h - pad - panelH
        rect.set(pad, yTop, w - pad, yTop + panelH)
        panelPaint.color = Color.parseColor("#2A2A31")
        canvas.drawRoundRect(rect, 16f, 16f, panelPaint)

        // 名字 + emoji
        textPaint.textSize = 17f
        canvas.drawText("${c.emoji} ${c.name}", pad + 14f, yTop + 26f, textPaint)

        val tag = if (c.side == Side.PLAYER) "我方" else "敌方"
        smallPaint.textSize = 12f
        smallPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(tag, w - pad - 14f, yTop + 26f, smallPaint)
        smallPaint.textAlign = Paint.Align.LEFT

        // 血条
        val barX = pad + 14f
        val barW = w - pad * 2 - 28f
        val hpY = yTop + 38f
        rect.set(barX, hpY, barX + barW, hpY + 18f)
        canvas.drawRoundRect(rect, 9f, 9f, barBgPaint)
        val ratio = if (c.maxHp <= 0) 0f else m.hp.toFloat() / c.maxHp
        hpPaint.color = if (ratio > 0.3f) COLOR_HP else COLOR_HP_LOW
        if (ratio > 0f) {
            rect.set(barX, hpY, barX + barW * ratio, hpY + 18f)
            canvas.drawRoundRect(rect, 9f, 9f, hpPaint)
        }
        smallPaint.textSize = 12f
        smallPaint.color = Color.WHITE
        canvas.drawText("${m.hp} / ${c.maxHp}", barX + 6f, hpY + 14f, smallPaint)

        // 护盾条（贴在血条上方）
        if (c.maxShield > 0) {
            val shY = yTop + 30f
            rect.set(barX, shY, barX + barW, shY + 5f)
            canvas.drawRoundRect(rect, 2.5f, 2.5f, barBgPaint)
            val sr = m.shield.toFloat() / c.maxShield
            if (sr > 0f) {
                rect.set(barX, shY, barX + barW * sr, shY + 5f)
                canvas.drawRoundRect(rect, 2.5f, 2.5f, shieldPaint)
            }
        }

        // 大招 CD
        if (m.cds.isNotEmpty()) {
            var cx = barX
            val cdY = yTop + 62f
            for ((name, left) in m.cds) {
                val pw = 62f
                rect.set(cx, cdY, cx + pw, cdY + 20f)
                circlePaint.color = Color.parseColor("#3A3A44")
                canvas.drawRoundRect(rect, 6f, 6f, circlePaint)
                if (left > 0) {
                    // 冷却遮罩：按剩余比例从右往左压暗
                    val total = c.skills.firstOrNull { it.tpl.name == name }?.cooldownTicks ?: 1
                    val frac = (left.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
                    rect.set(cx + pw * (1f - frac), cdY, cx + pw, cdY + 20f)
                    circlePaint.color = Color.parseColor("#B3000000")
                    circlePaint.alpha = 150
                    canvas.drawRoundRect(rect, 6f, 6f, circlePaint)
                    circlePaint.alpha = 255
                    smallPaint.color = COLOR_GOLD
                    smallPaint.textSize = 11f
                    smallPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("${left / 10}.${left % 10}s", cx + pw / 2f, cdY + 14f, smallPaint)
                    smallPaint.textAlign = Paint.Align.LEFT
                } else {
                    smallPaint.color = COLOR_GOLD
                    smallPaint.textSize = 11f
                    smallPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("就绪", cx + pw / 2f, cdY + 14f, smallPaint)
                    smallPaint.textAlign = Paint.Align.LEFT
                }
                cx += pw + 8f
            }
        }
    }

    private fun drawSprite(canvas: Canvas, side: Side, now: Long) {
        val c = if (side == Side.PLAYER) player else enemy
        val m = if (side == Side.PLAYER) pMirror else eMirror
        if (c == null || m == null) return

        var x = spriteX(side)
        val y = spriteY(side)

        // 受击抖动
        val sh = shakeAt[side] ?: 0L
        if (sh > 0 && now - sh < 160) {
            x += (sin((now - sh) / 9.0) * 9).toFloat()
        }
        // 攻击前扑
        val lg = lungeAt[side] ?: 0L
        if (lg > 0 && now - lg < 180) {
            val p = ((now - lg) / 180.0).coerceIn(0.0, 1.0)
            val dir = if (side == Side.PLAYER) 1f else -1f
            x += (sin(p * Math.PI) * 46).toFloat() * dir
        }

        // 脚下光圈
        circlePaint.color = c.colorInt
        circlePaint.alpha = 55
        canvas.drawCircle(x, y + 52f, 46f, circlePaint)
        circlePaint.alpha = 255

        // 受击白闪
        val fl = flashAt[side] ?: 0L
        if (fl > 0 && now - fl < 150) {
            circlePaint.color = Color.WHITE
            circlePaint.alpha = (110 * (1f - (now - fl) / 150f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y + 6f, 56f, circlePaint)
            circlePaint.alpha = 255
        }

        // 本体（emoji）
        val dead = m.deadAt > 0
        spritePaint.textSize = 66f
        spritePaint.alpha = if (dead) 70 else 255
        canvas.save()
        if (dead) {
            canvas.rotate(-14f, x, y)
            canvas.translate(0f, 26f)
        }
        canvas.drawText(c.emoji, x, y, spritePaint)
        canvas.restore()
        spritePaint.alpha = 255

        // 眩晕标记
        if (m.stun > 0) {
            spritePaint.textSize = 24f
            canvas.drawText("💫", x, y - 58f, spritePaint)
            spritePaint.textSize = 66f
        }
    }

    private fun drawFloats(canvas: Canvas, now: Long) {
        val it = floats.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val life = now - f.born
            if (life > 950) {
                it.remove()
                continue
            }
            val p = life / 950f
            floatPaint.textSize = f.size
            floatPaint.color = f.color
            floatPaint.alpha = ((1f - p) * 255).toInt().coerceIn(0, 255)
            canvas.drawText(f.text, f.x, f.y - p * 78f, floatPaint)
        }
        floatPaint.alpha = 255
    }

    private fun drawBanner(canvas: Canvas, w: Float, h: Float, now: Long) {
        if (bannerText.isEmpty()) return
        val life = now - bannerAt
        if (life > 1100) return
        val p = (life / 1100f).coerceIn(0f, 1f)
        val alpha = if (p < 0.15f) (p / 0.15f * 255) else ((1f - p) / 0.85f * 255)
        textPaint.textSize = 26f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_GOLD
        textPaint.alpha = alpha.toInt().coerceIn(0, 255)
        canvas.drawText(bannerText, w / 2f, h * 0.30f, textPaint)
        textPaint.alpha = 255
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawLogs(canvas: Canvas, w: Float, h: Float) {
        smallPaint.textSize = 12f
        smallPaint.color = Color.parseColor("#9E9E9E")
        var y = h * 0.665f
        for (line in logs) {
            canvas.drawText(line, 18f, y, smallPaint)
            y += 17f
        }
    }

    companion object {
        private val COLOR_HP = Color.parseColor("#E53935")
        private val COLOR_HP_LOW = Color.parseColor("#FF8A65")
        private val COLOR_SHIELD = Color.parseColor("#29B6F6")
        private val COLOR_DMG = Color.parseColor("#FFFFFF")
        private val COLOR_CRIT = Color.parseColor("#FFD54F")
        private val COLOR_HEAL = Color.parseColor("#66BB6A")
        private val COLOR_MISS = Color.parseColor("#B0BEC5")
        private val COLOR_STUN = Color.parseColor("#CE93D8")
        private val COLOR_GOLD = Color.parseColor("#FFD54F")
    }
}
