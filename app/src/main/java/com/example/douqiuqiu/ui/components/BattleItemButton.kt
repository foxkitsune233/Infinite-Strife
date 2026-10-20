package com.example.douqiuqiu.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import com.example.douqiuqiu.R
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.ui.AppSettings

/** Visual game-time values are supplied by the saved battle; only the automatic rim uses wall time. */
class BattleItemButton(c: Context) : View(c) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Path()
    private val rim = Path()
    private val measure = PathMeasure()
    private val rect = RectF()
    private val arcRect = RectF()
    private var art: Drawable? = null
    private var definitionId: String? = null
    private var usable = false
    private var present = false
    private var automatic = false
    private var remainingFraction = 0f
    private var phase = 0f
    private var count = 0
    var readyForUse = false
        private set
    var onDetails: (() -> Unit)? = null
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init {
        isClickable = true; isFocusable = true
        minimumWidth = (48 * density).toInt(); minimumHeight = (48 * density).toInt()
    }

    fun bind(definition: ItemDefinition?, canUse: Boolean, hasAction: Boolean, isAutomatic: Boolean,
             remainingMs: Long, totalMs: Long, quantity: Int, manual: Boolean) {
        if (definitionId != definition?.id) {
            definitionId = definition?.id
            art = definition?.let { PaintedItemIcons.drawable(context, it) }
        }
        present = definition != null; usable = hasAction; automatic = hasAction && isAutomatic
        isHapticFeedbackEnabled = AppSettings.haptics(context)
        readyForUse = canUse; count = quantity
        remainingFraction = if (remainingMs > 0) (remainingMs.toDouble() / maxOf(totalMs, remainingMs, 1L)).toFloat().coerceIn(0f, 1f) else 0f
        val state = when {
            !present -> "空槽或物品已消耗"
            !usable -> "被动装备"
            remainingMs > 0 -> "冷却${(remainingMs + 99) / 100 / 10.0}秒"
            canUse -> "可使用"
            else -> "当前状态不可使用"
        }
        contentDescription = "${definition?.name ?: "装备位"}，$state" +
            if (hasAction) "，${if (manual) "手动" else "自动"}模式，长按切换，详情可通过辅助操作打开" else "，点按查看详情"
        syncAnimation(); invalidate()
    }

    private fun syncAnimation() {
        val animate = isAttachedToWindow && windowVisibility == VISIBLE && isShown && automatic && AppSettings.animations(context)
        if (animate && !animator.isStarted) animator.start()
        if (!animate && animator.isStarted) animator.cancel()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncAnimation() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); syncAnimation() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = minOf(w, h).toFloat() - 8 * density
        rect.set((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
        val cut = 6 * density
        frame.reset()
        // Clockwise perimeter, starting at twelve o'clock.
        frame.moveTo(rect.centerX(), rect.top)
        frame.lineTo(rect.right - cut, rect.top); frame.lineTo(rect.right, rect.top + cut)
        frame.lineTo(rect.right, rect.bottom - cut); frame.lineTo(rect.right - cut, rect.bottom)
        frame.lineTo(rect.left + cut, rect.bottom); frame.lineTo(rect.left, rect.bottom - cut)
        frame.lineTo(rect.left, rect.top + cut); frame.lineTo(rect.left + cut, rect.top); frame.close()
        measure.setPath(frame, true)
        val radius = rect.width() // larger than diagonal half-width, so the sector covers the complete square
        arcRect.set(rect.centerX() - radius, rect.centerY() - radius, rect.centerX() + radius, rect.centerY() + radius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL; paint.color = 0xFF062838.toInt(); canvas.drawPath(frame, paint)
        canvas.save(); canvas.clipPath(frame)
        art?.let {
            val inner = RectF(rect).apply { inset(4 * density, 4 * density) }
            it.alpha = if (present) 255 else 80
            it.setBounds(inner.left.toInt(), inner.top.toInt(), inner.right.toInt(), inner.bottom.toInt()); it.draw(canvas)
        }
        if (!readyForUse && remainingFraction == 0f) {
            paint.color = if (usable) 0x75041926 else 0x29041926; canvas.drawPath(frame, paint)
        }
        if (remainingFraction > 0f) {
            paint.color = 0xCC03121D.toInt()
            canvas.drawArc(arcRect, -90f + 360f * (1f - remainingFraction), 360f * remainingFraction, true, paint)
        }
        canvas.restore()
        paint.style = Paint.Style.STROKE
        if (readyForUse) {
            paint.strokeWidth = 7 * density; paint.color = 0x48FFD24D; canvas.drawPath(frame, paint)
        }
        paint.strokeWidth = 2 * density
        paint.color = if (readyForUse) 0xFFFFCF47.toInt() else 0xFF388A9D.toInt()
        canvas.drawPath(frame, paint)
        if (automatic) {
            val length = measure.length
            val start = phase * length
            val end = start + length * .23f
            rim.reset(); measure.getSegment(start, minOf(end, length), rim, true)
            if (end > length) measure.getSegment(0f, end - length, rim, true)
            paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 3 * density
            paint.color = 0xFFFFF2A5.toInt(); canvas.drawPath(rim, paint)
            paint.strokeCap = Paint.Cap.BUTT
            // Static amber notch distinguishes automatic mode when motion is disabled.
            paint.style = Paint.Style.FILL; paint.color = 0xFFFFCF47.toInt()
            canvas.drawCircle(rect.centerX(), rect.top, 2.5f * density, paint)
        }
        if (count > 1) {
            paint.style = Paint.Style.FILL; paint.color = 0xDF03121D.toInt()
            canvas.drawCircle(rect.right - 6 * density, rect.bottom - 6 * density, 8 * density, paint)
            paint.color = GameUi.ink; paint.textSize = 10 * density; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(count.toString(), rect.right - 6 * density, rect.bottom - 2.5f * density, paint)
        }
        if (isPressed || isFocused) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density; paint.color = Color.WHITE
            canvas.drawPath(frame, paint)
        }
    }

    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.battle_item_details, "查看物品详情"))
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == R.id.battle_item_details) { onDetails?.invoke(); return true }
        return super.performAccessibilityAction(action, arguments)
    }
}
