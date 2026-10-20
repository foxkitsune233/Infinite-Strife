package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.AppraisalTiming
import org.json.JSONArray
import org.json.JSONObject

/** Durable presentation receipt. It never generates items, rolls values or charges money. */
object InspectionDialog {
    private fun prefs(c: Context) = c.getSharedPreferences(com.example.douqiuqiu.inventory.TestProfile.inspectionPreferences(c), Context.MODE_PRIVATE)
    fun hasPending(c: Context) = prefs(c).contains("receipt")
    fun diagnostic(c: Context, sandbox: Boolean = com.example.douqiuqiu.inventory.TestProfile.active(c)): String {
        val settings = c.getSharedPreferences(if (sandbox) "test-inspection-presentation-v1" else "inspection-presentation-v1", Context.MODE_PRIVATE)
        return JSONObject().put("active", settings.getString("receipt", "")).put("elapsedMs", settings.getLong("elapsed", 0))
            .put("quarantined", settings.getString("quarantinedReceipt", "")).put("quarantinedElapsedMs", settings.getLong("quarantinedElapsed", 0)).toString(2)
    }
    fun enqueue(c: Context, title: String, items: List<Pair<ItemDefinition, Int>>, quality: String,
                owner: InventoryState, sourceChest: String = "", appraisal: Int = 0) {
        require(items.isNotEmpty() && items.size <= 36 && items.all { it.second in 0..2 })
        val json = JSONObject().put("owner", InspectionOwner.key(owner)).put("sourceChest", sourceChest)
            .put("sourceToken", if (sourceChest.isEmpty()) "" else InspectionOwner.chestKey(owner.progress.chests.single { it.id == sourceChest }))
            .put("appraisal", appraisal).put("title", title).put("quality", quality).put("items", JSONArray().apply {
            items.forEach { (d, reveal) -> put(JSONObject().put("id", d.id).put("reveal", reveal)) }
        })
        check(prefs(c).edit().putString("receipt", json.toString()).putLong("elapsed", 0).commit()) { "鉴定进度保存失败" }
    }
    /** Call after loading a validated profile, before constructing gated screens. Never charges or grants. */
    fun reconcile(c: Context, state: InventoryState, definitions: Map<String, ItemDefinition>): String? {
        val settings = prefs(c)
        val raw = settings.getString("receipt", null) ?: return null
        val json = try {
            JSONObject(raw).also { record ->
                require(record.optString("title").isNotBlank() && record.optString("quality").isNotBlank())
                val items = record.getJSONArray("items")
                require(items.length() in 1..36 && settings.getLong("elapsed", 0) >= 0)
                repeat(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    require(item.getString("id") in definitions && item.getInt("reveal") in 0..2)
                }
            }
        } catch (_: Exception) { null }
        val valid = json?.let {
            if (it.has("owner")) {
                val chest = it.optString("sourceChest")
                it.getString("owner") == InspectionOwner.key(state) && (chest.isEmpty() ||
                    state.progress.chests.any { box -> box.id == chest && box.appraisals == it.optInt("appraisal") &&
                        it.optString("sourceToken") == InspectionOwner.chestKey(box) })
            } else {
                // Legacy receipts lack an owner. Only migrate if their revealed clues belong to one current chest.
                val items = it.optJSONArray("items")
                val matching = if (items == null || items.length() !in 1..36) emptyList() else state.progress.chests.filter { box ->
                    box.quality == it.optString("quality") && box.appraisals > 0 && (0 until items.length()).all { i ->
                        val item = items.optJSONObject(i)
                        item != null && box.items.any { clue -> clue.definitionId == item.optString("id") && clue.reveal == item.optInt("reveal", -1) }
                    }
                }
                val completionMatches = if (items == null || state.progress.run != null ||
                    state.progress.lastEnding != "全部通关，目标宝箱和本局物资已入库" || it.optString("title") != "宝箱已开启 · 鉴定战利品") 0
                else state.items.filter { item -> ":chest:" in item.instanceId }
                    .groupBy { item -> item.instanceId.substringBeforeLast(":chest:") }.values.count { group ->
                        group.size == items.length() && (0 until items.length()).all { i ->
                            group.any { item -> item.instanceId.endsWith(":chest:$i") &&
                                item.definitionId == items.optJSONObject(i)?.optString("id") }
                        }
                    }
                if (matching.size == 1 || completionMatches == 1) {
                    it.put("owner", InspectionOwner.key(state)).put("sourceChest", matching.singleOrNull()?.id ?: "")
                        .put("sourceToken", matching.singleOrNull()?.let { box -> InspectionOwner.chestKey(box) } ?: "")
                        .put("appraisal", matching.singleOrNull()?.appraisals ?: 0)
                    check(settings.edit().putString("receipt", it.toString()).commit()) { "旧鉴定记录迁移失败" }
                    true
                } else false
            }
        } ?: false
        if (valid) return null
        check(settings.edit().putString("quarantinedReceipt", raw)
            .putLong("quarantinedElapsed", settings.getLong("elapsed", 0))
            .remove("receipt").remove("elapsed").commit()) { "无法隔离不匹配的鉴定记录" }
        return "已隔离无法匹配当前存档的旧鉴定演出，原记录保留；金币和物品未改动。"
    }
    fun resume(c: Context, definitions: Map<String, ItemDefinition>, onError: (Exception) -> Unit, onClosed: () -> Unit): AlertDialog? {
        val raw = prefs(c).getString("receipt", null) ?: return null
        val json = JSONObject(raw)
        val array = json.getJSONArray("items")
        require(array.length() in 1..36)
        val items = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            definitions.getValue(item.getString("id")) to item.getInt("reveal").also { require(it in 0..2) }
        }
        val panel = GameUi.panel(c, true)
        panel.addView(GameUi.label(c, json.getString("title"), 22, GameUi.gold, true))
        val crest = InspectionHeaderView(c)
        panel.addView(crest, LinearLayout.LayoutParams(-1, GameUi.dp(c, 76)))
        panel.addView(GameUi.label(c, "大件与珍品鉴定更久 · 最多三件同时鉴定\n不可跳过 · 离开应用暂停，回来继续", 12, GameUi.muted))
        val view = InspectionView(c, items, prefs(c).getLong("elapsed", 0))
        view.progress = { elapsed -> crest.bind(elapsed, view.totalMs, view.revealedCount, items.size) }
        crest.bind(prefs(c).getLong("elapsed", 0), view.totalMs, view.revealedCount, items.size)
        panel.addView(ScrollView(c).apply { addView(view) }, LinearLayout.LayoutParams(-1, (c.resources.displayMetrics.heightPixels * .43f).toInt()))
        val button = GameUi.button(c, "鉴定中…", true) { }
        panel.addView(button)
        val dialog = AlertDialog.Builder(c).setView(panel).setCancelable(false).create()
        view.checkpoint = { elapsed ->
            if (!prefs(c).edit().putLong("elapsed", elapsed).commit()) {
                view.halt(); view.checkpoint = null; dialog.dismiss()
                onError(IllegalStateException("鉴定进度保存失败"))
            }
        }
        dialog.setOnShowListener {
            button.isEnabled = false
            view.finished = { button.isEnabled = true; button.text = "查看结果并继续" }
            if (view.complete) view.finished?.invoke()
            button.setOnClickListener {
                if (view.complete) {
                    if (!prefs(c).edit().clear().commit()) { onError(IllegalStateException("鉴定进度保存失败")); return@setOnClickListener }
                    view.checkpoint = null
                    dialog.dismiss(); onClosed()
                }
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((c.resources.displayMetrics.widthPixels * .96f).toInt(), -2)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }
}

/** Matches the node-loot chest, opening motion and gold progress track. Presentation timing stays unchanged. */
private class InspectionHeaderView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var elapsed = 0L
    private var total = 1L
    private var revealed = 0
    private var count = 0
    fun bind(ms: Long, duration: Long, known: Int, size: Int) {
        elapsed = ms; total = duration.coerceAtLeast(1); revealed = known; count = size; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        val side = height.toFloat()
        val fraction = (elapsed / 600f).coerceIn(0f, 1f)
        GameArt.chest(context, fraction >= 1)?.let { chest ->
            canvas.save()
            if (com.example.douqiuqiu.ui.AppSettings.animations(context) && fraction < 1)
                canvas.rotate(kotlin.math.sin(fraction * 24) * 4, side / 2, side / 2)
            canvas.drawBitmap(chest, null, RectF(0f, 0f, side, side), paint); canvas.restore()
        }
        paint.textSize = GameUi.dp(context, 12).toFloat(); paint.color = GameUi.gold
        val label = if (elapsed >= total) "鉴定完成 · 查看线索" else if (fraction < 1) "宝箱开启中…" else "鉴定 $revealed / $count · 不可跳过"
        canvas.drawText(label, side + 6, height * .42f, paint)
        contentDescription = label
        paint.color = GameUi.line; canvas.drawRect(side + 6, height * .62f, width - 6f, height * .69f, paint)
        paint.color = GameUi.gold; canvas.drawRect(side + 6, height * .62f,
            side + 6 + (width - side - 12) * (elapsed.toFloat() / total).coerceIn(0f, 1f), height * .69f, paint)
    }
}

private class InspectionView(c: Context, private val items: List<Pair<ItemDefinition, Int>>, checkpointMs: Long) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starts = AppraisalTiming.startsMs(items.map { it.first })
    private val durations = items.map { AppraisalTiming.durationMs(it.first) }
    private val total = items.indices.maxOf { starts[it] + durations[it] }
    private var elapsed = checkpointMs.coerceIn(0, total)
    private var attachedAt = 0L
    private var base = elapsed
    private var notified = false
    private var halted = false
    private var lastCheckpoint = elapsed
    private var soundedItems = items.indices.count { elapsed >= starts[it] + durations[it] }
    var checkpoint: ((Long) -> Unit)? = null
    var progress: ((Long) -> Unit)? = null
    var finished: (() -> Unit)? = null
    val complete get() = elapsed >= total
    val totalMs get() = total
    val revealedCount get() = items.indices.count { elapsed >= starts[it] + durations[it] }
    fun halt() { halted = true; removeCallbacks(ticker) }
    private val ticker = object : Runnable {
        override fun run() {
            elapsed = (base + SystemClock.uptimeMillis() - attachedAt).coerceAtMost(total)
            progress?.invoke(elapsed)
            val revealed = items.indices.count { elapsed >= starts[it] + durations[it] }
            if (revealed > soundedItems) {
                com.example.douqiuqiu.ui.GameAudio.play(context, com.example.douqiuqiu.ui.GameAudio.Cue.REVEAL)
                soundedItems = revealed
            }
            if (elapsed - lastCheckpoint >= 250 || complete) { checkpoint?.invoke(elapsed); lastCheckpoint = elapsed }
            if (halted) return
            invalidate()
            if (complete) { if (!notified) { notified = true; finished?.invoke() } }
            else postDelayed(this, 40)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); base = elapsed; attachedAt = SystemClock.uptimeMillis(); post(ticker) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); checkpoint?.invoke(elapsed); super.onDetachedFromWindow() }
    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(MeasureSpec.getSize(w), GameUi.dp(context, ((items.size + 2) / 3) * 112)) }
    override fun onDraw(c: Canvas) {
        val unit = width / 3f
        items.forEachIndexed { index, (d, reveal) ->
            val r = RectF(index % 3 * unit + 4, GameUi.dp(context, index / 3 * 112).toFloat() + 4,
                (index % 3 + 1) * unit - 4, GameUi.dp(context, index / 3 * 112 + 106).toFloat())
            val progress = ((elapsed - starts[index]).toFloat() / durations[index]).coerceIn(0f, 1f)
            val done = progress >= 1
            p.style = Paint.Style.FILL; p.color = GameUi.surface; c.drawRoundRect(r, 8f, 8f, p)
            if (done && reveal == 2) {
                val icon = RectF(r.left + 15, r.top + 7, r.right - 15, r.bottom - GameUi.dp(context, 42))
                ItemArtwork.draw(context, c, d, icon, p)
            }
            p.style = Paint.Style.FILL; p.color = if (done && reveal > 0) GameUi.quality(d.quality) else GameUi.muted
            p.textAlign = Paint.Align.CENTER; p.textSize = GameUi.dp(context, 12).toFloat()
            val label = if (done) when (reveal) { 2 -> d.name; 1 -> "${d.quality}色未知物品"; else -> "未鉴定物品" }
                else if (elapsed < starts[index]) "等待鉴定" else "鉴定中 ${(progress * 100).toInt()}%"
            c.save(); c.clipRect(r)
            c.drawText(label, r.centerX(), r.bottom - GameUi.dp(context, 24), p)
            c.drawText("${d.width}×${d.height}格", r.centerX(), r.bottom - GameUi.dp(context, 9), p)
            c.restore()
            p.color = GameUi.line; c.drawRect(r.left + 5, r.bottom - 5, r.right - 5, r.bottom - 2, p)
            p.color = GameUi.gold; c.drawRect(r.left + 5, r.bottom - 5, r.left + 5 + (r.width() - 10) * progress, r.bottom - 2, p)
        }
    }
}
