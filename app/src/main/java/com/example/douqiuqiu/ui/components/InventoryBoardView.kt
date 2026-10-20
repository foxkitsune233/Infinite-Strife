package com.example.douqiuqiu.ui.components

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.*
import com.example.douqiuqiu.inventory.*

/** Real grid and slot hit targets. A drop is a request; only InventoryRules commits the move. */
class InventoryBoardView(
    context: Context,
    private val container: Container,
    private val snapshot: InventoryState,
    private val rules: InventoryRules,
    private val selected: () -> StoredItem?,
    private val rotation: () -> Boolean,
    private val select: (StoredItem) -> Unit,
    private val place: (String, ItemPlace) -> Unit,
    private val inspect: () -> Unit,
    private val maxCellDp: Int = Int.MAX_VALUE,
    private val preview: (StoredItem) -> Unit = select,
    private val onReject: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() },
    private val findItem: (String) -> StoredItem? = { id -> snapshot.items.find { it.instanceId == id } },
    private val checkMove: (String, ItemPlace) -> InventoryResult = { id, target -> rules.move(snapshot, id, target) }
) : View(context) {
    private val columns = when (container) {
        Container.EQUIPMENT -> 3; Container.SKILLS -> 4
        Container.BACKPACK -> snapshot.backpackWidth; Container.SAFE -> snapshot.safeWidth
        else -> 1
    }
    private val slots = when (container) { Container.EQUIPMENT -> 9; Container.SKILLS -> snapshot.skillSlots; else -> Int.MAX_VALUE }
    private val rows = when (container) {
        Container.EQUIPMENT -> 3; Container.SKILLS -> 2
        Container.BACKPACK -> snapshot.backpackHeight; Container.SAFE -> snapshot.safeHeight; else -> 1
    }
    private val items = snapshot.items.filter { it.place.container == container }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gap = GameUi.dp(context, 4).toFloat()
    private var cell = 1f
    private var origin = 0f
    private var hover: ItemPlace? = null
    private var draggingId: String? = null
    private var dragSucceeded = false
    private val slotBoard = container == Container.EQUIPMENT || container == Container.SKILLS
    private var hoverResult: InventoryResult? = null
    private val locked = snapshot.phase == InventoryPhase.BATTLE
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent) = true
        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
            val position = position(e.x, e.y) ?: return true
            val occupied = itemAt(position)
            if (occupied != null) { select(occupied); preview(occupied) }
            else selected()?.let { if (!locked) place(it.instanceId, targetFor(it, position)) }
            performClick(); return true
        }
        override fun onLongPress(e: android.view.MotionEvent) {
            if (locked) return
            val item = position(e.x, e.y)?.let(::itemAt) ?: return
            select(item)
            com.example.douqiuqiu.ui.AppSettings.feedback(this@InventoryBoardView, HapticFeedbackConstants.LONG_PRESS)
            startDragAndDrop(ClipData.newPlainText("物品", item.instanceId), ItemDragShadow(this@InventoryBoardView, rules.definitions.getValue(item.definitionId), rotation(), (cell / resources.displayMetrics.density).toInt()), item.instanceId, 0)
        }
    })
    init {
        isClickable = true; isFocusable = true
        contentDescription = "${title()}，${columns}列${rows}行，${items.size}件物品。点选物品，再点空格放置；长按拖动。双击查看容器物品操作。"
        setOnClickListener { }
        setOnDragListener { _, e ->
            val id = e.localState as? String
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    draggingId = id; dragSucceeded = false
                    hover = null; hoverResult = null
                    val item = id?.let(findItem)
                    invalidate()
                    !locked && item != null
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val next = dropPosition(e.x, e.y)
                    if (hover != next) {
                        hover = next
                        val item = draggingId?.let(findItem)
                        hoverResult = if (next != null && item != null) checkMove(item.instanceId, targetFor(item, next)) else null
                        if (hoverResult?.ok == true) com.example.douqiuqiu.ui.AppSettings.feedback(this, HapticFeedbackConstants.CLOCK_TICK)
                        invalidate()
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> { hover = null; hoverResult = null; invalidate(); true }
                DragEvent.ACTION_DROP -> {
                    val destination = dropPosition(e.x, e.y)
                    val item = id?.let(findItem)
                    if (destination != null && item != null) {
                        val target = targetFor(item, destination)
                        val result = checkMove(item.instanceId, target)
                        if (result.ok) {
                            com.example.douqiuqiu.ui.AppSettings.feedback(this, HapticFeedbackConstants.CONFIRM)
                            dragSucceeded = true; place(item.instanceId, target)
                        } else rejected(result.error ?: "此位置不可放置")
                    } else rejected("请将指针移到目标栏位，看到绿色提示后再松手")
                    hover = null; invalidate(); dragSucceeded
                }
                DragEvent.ACTION_DRAG_ENDED -> { draggingId = null; hover = null; hoverResult = null; invalidate(); true }
                else -> true
            }
        }
        ViewCompatActions.install(this, inspect)
    }
    private fun title() = when (container) {
        Container.EQUIPMENT -> "装备栏"; Container.SKILLS -> "技能栏"; Container.SAFE -> "安全箱"; else -> "背包"
    }
    private fun rotationFor(item: StoredItem) = if (selected()?.instanceId == item.instanceId) rotation() else item.place.rotated
    private fun targetFor(item: StoredItem, position: ItemPlace): ItemPlace {
        val target = position.copy(rotated = if (slotBoard) false else rotationFor(item))
        if (!slotBoard && !checkMove(item.instanceId, target).ok) {
            val rotated = target.copy(rotated = !target.rotated)
            if (checkMove(item.instanceId, rotated).ok) return rotated
        }
        return target
    }
    private fun rejected(reason: String) {
        onReject(reason)
        announceForAccessibility(reason)
    }
    /** Small horizontal forgiveness only for slots, never snap an occupied slot to a different slot. */
    private fun dropPosition(x: Float, y: Float): ItemPlace? {
        if (!slotBoard) return position(x, y)
        if (x < 0 || x >= width || y < 0 || y >= height || cell <= 0) return null
        val tolerance = GameUi.dp(context, 12)
        if (x < origin - tolerance || x >= origin + columns * cell + tolerance) return null
        val col = ((x - origin).coerceIn(0f, columns * cell - .01f) / cell).toInt()
        val row = (y / cell).toInt()
        // Include locked cells so the rule can supply an explicit rejection reason.
        return if (row in 0 until rows) ItemPlace(container, row * columns + col) else null
    }
    private fun position(x: Float, y: Float): ItemPlace? {
        if (x < 0 || y < 0 || x >= width || y >= height) return null
        if (x < origin || x >= origin + columns * cell) return null
        val col = ((x - origin) / cell).toInt(); val row = (y / cell).toInt()
        if (col !in 0 until columns || row !in 0 until rows) return null
        return if (container == Container.EQUIPMENT || container == Container.SKILLS) {
            val slot = row * columns + col
            if (slot >= slots) null else ItemPlace(container, slot)
        } else ItemPlace(container, col, row)
    }
    private fun itemAt(pos: ItemPlace): StoredItem? = items.find { item ->
        if (container == Container.EQUIPMENT || container == Container.SKILLS) item.place.x == pos.x
        else {
            val def = rules.definitions.getValue(item.definitionId)
            val w = if (item.place.rotated) def.height else def.width
            val h = if (item.place.rotated) def.width else def.height
            pos.x in item.place.x until item.place.x + w && pos.y in item.place.y until item.place.y + h
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        cell = minOf(w.toFloat() / columns, maxCellDp.toFloat() * resources.displayMetrics.density)
        origin = (w - columns * cell) / 2f
        setMeasuredDimension(w, (cell * rows).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (row in 0 until rows) for (col in 0 until columns) {
            val n = row * columns + col
            val rect = rect(col, row)
            val inactive = n >= slots || (container == Container.EQUIPMENT && n >= 6)
            GameUi.frame(context, if (inactive) GameUi.background else GameUi.surface,
                if (inactive) 0xFF667D8C.toInt() else GameUi.gold).apply {
                setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()); draw(canvas)
            }
            val slotLabel = when {
                n >= slots -> if (cell < GameUi.dp(context, 72)) "锁" else "未解锁"
                cell < GameUi.dp(context, 72) -> if (container == Container.EQUIPMENT && n >= 6) "备${n - 5}" else if (container == Container.EQUIPMENT || container == Container.SKILLS) "${n + 1}" else "·"
                container == Container.EQUIPMENT -> if (n >= 6) "备用 ${n - 5}" else "装备 ${n + 1}"
                container == Container.SKILLS -> "技能 ${n + 1}"
                else -> "·"
            }
            drawText(canvas, slotLabel, rect.centerX(), rect.centerY(), GameUi.muted, 11)
        }
        for (item in items) {
            val def = rules.definitions.getValue(item.definitionId)
            val isSlot = container == Container.EQUIPMENT || container == Container.SKILLS
            val x = if (isSlot) item.place.x % columns else item.place.x
            val y = if (isSlot) item.place.x / columns else item.place.y
            val w = if (isSlot) 1 else if (item.place.rotated) def.height else def.width
            val h = if (isSlot) 1 else if (item.place.rotated) def.width else def.height
            val r = rect(x, y, w, h)
            p.style = Paint.Style.FILL; p.color = GameUi.raised; canvas.drawRoundRect(r, gap * 2, gap * 2, p)
            if (def.kind == ItemKind.CARD) GameArt.card(context, def.id)?.let { bitmap ->
                canvas.save(); canvas.clipRect(r); GameArt.cover(canvas, bitmap, r, p)
                if (cell >= GameUi.dp(context, 72)) {
                    p.color = 0xC9102532.toInt()
                    canvas.drawRect(r.left, r.centerY() - GameUi.dp(context, 12), r.right, r.bottom, p)
                }
                canvas.restore()
            }
            if (def.kind != ItemKind.CARD) {
                val footer = if (cell < GameUi.dp(context, 72)) 0 else GameUi.dp(context, 30)
                val iconRect = RectF(r.left + gap, r.top + gap, r.right - gap, (r.bottom - footer).coerceAtLeast(r.top + gap))
                ItemArtwork.draw(context, canvas, def, iconRect, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = GameUi.dp(context, if (selected()?.instanceId == item.instanceId) 3 else 2).toFloat()
            p.color = if (selected()?.instanceId == item.instanceId) GameUi.gold
                else if (container == Container.EQUIPMENT && item.place.x >= 6) 0xFF667D8C.toInt() else GameUi.quality(def.quality)
            canvas.drawRoundRect(r, gap * 2, gap * 2, p)
            val title = if (def.name.length > 4) def.name.take(4) else def.name
            canvas.save(); canvas.clipRect(r)
            if (container == Container.EQUIPMENT && item.place.x >= 6) drawText(canvas, "备", r.right - GameUi.dp(context, 10), r.top + GameUi.dp(context, 10), GameUi.ink, 9)
            if (cell < GameUi.dp(context, 72)) {
                if (def.kind == ItemKind.CARD) drawText(canvas, "★".repeat(item.star), r.centerX(), r.bottom - GameUi.dp(context, 6), GameUi.gold, 9)
                if (item.count > 1) drawText(canvas, "${item.count}", r.right - GameUi.dp(context, 8), r.bottom - GameUi.dp(context, 7), GameUi.ink, 9)
            } else {
                val titleY = if (def.kind == ItemKind.CARD) r.centerY() - GameUi.dp(context, 2) else r.bottom - GameUi.dp(context, 20)
                drawText(canvas, title, r.centerX(), titleY, GameUi.ink, if (def.kind == ItemKind.CARD) 12 else 10)
                drawText(canvas, if (def.kind == ItemKind.CARD) "★".repeat(item.star) else if (item.count > 1) "×${item.count}${if (item.bound) " 绑" else ""}" else if (item.bound) "绑定" else def.quality,
                    r.centerX(), if (def.kind == ItemKind.CARD) r.centerY() + GameUi.dp(context, 16) else r.bottom - GameUi.dp(context, 7), GameUi.quality(def.quality), 10)
            }
            if (item.instanceId in snapshot.progress.lockedItems) drawText(canvas, "锁", r.left + GameUi.dp(context, 10), r.top + GameUi.dp(context, 11), GameUi.gold, 10)
            canvas.restore()
        }
        val destination = hover
        val item = draggingId?.let(findItem)
        if (destination != null && item != null) {
            val ok = hoverResult?.ok == true
            val slot = container == Container.EQUIPMENT || container == Container.SKILLS
            val d = rules.definitions.getValue(item.definitionId)
            val rotated = targetFor(item, destination).rotated
            val w = if (slot) 1 else if (rotated) d.height else d.width
            val h = if (slot) 1 else if (rotated) d.width else d.height
            val r = rect(if (slot) destination.x % columns else destination.x, if (slot) destination.x / columns else destination.y, w, h)
            p.color = if (ok) 0x557ECFB1 else 0x66E78276; p.style = Paint.Style.FILL
            canvas.save(); canvas.clipRect(0, 0, width, height)
            canvas.drawRoundRect(r, gap * 2, gap * 2, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = GameUi.dp(context, 3).toFloat()
            p.color = if (ok) GameUi.green else GameUi.danger
            canvas.drawRoundRect(r, gap * 2, gap * 2, p)
            if (ok && slot && items.any { it.instanceId != item.instanceId && it.place.container == container && it.place.x == destination.x }) {
                drawText(canvas, "替换", r.centerX(), r.centerY(), GameUi.gold, 11)
            }
            canvas.restore()
        }
    }
    private fun rect(x: Int, y: Int, w: Int = 1, h: Int = 1) = RectF(origin + x * cell + gap, y * cell + gap, origin + (x + w) * cell - gap, (y + h) * cell - gap)
    private fun drawText(canvas: Canvas, value: String, x: Float, y: Float, color: Int, sp: Int) {
        p.style = Paint.Style.FILL; p.color = color; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.textSize = sp * resources.displayMetrics.scaledDensity * com.example.douqiuqiu.ui.AppSettings.textScale(context)
        canvas.drawText(value, x, y - (p.ascent() + p.descent()) / 2, p)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)
    override fun performClick(): Boolean { super.performClick(); return true }
}

private object ViewCompatActions {
    fun install(view: View, inspect: () -> Unit) {
        androidx.core.view.ViewCompat.replaceAccessibilityAction(view,
            androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            "查看物品与选择放置位置") { _, _ -> inspect(); true }
    }
}
