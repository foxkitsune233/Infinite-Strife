package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.widget.ImageView

/** Sampled UI artwork; the cache is bounded by decoded bytes, not source file count. */
object GameArt {
    // 64MB：整仓物品 + 卡牌 + 场景的工作集需全部驻留，否则 LRU 挤爆会引发主线程反复解码（曾致 ANR）。
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val missing = mutableSetOf<String>()
    private fun load(c: Context, path: String, maxEdge: Int = 600): Bitmap? {
        cache.get(path)?.let { return it }
        if (path in missing) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > maxEdge) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            c.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.also { cache.put(path, it) }
        } catch (_: java.io.IOException) { missing += path; null }
    }
    fun card(c: Context, id: String): Bitmap? = ExpansionArtwork.cardFile(c, id)?.let { load(c, "art/cards/$it", 768) }
        ?: if (id.matches(Regex("[AUCSRPD][1-6]"))) load(c, "art/cards/$id-v1.png") else null
    fun eventOutcome(c: Context, key: String): Bitmap? = ExpansionArtwork.eventFile(c, key)?.let { load(c, "art/events/$it", 1024) }
    fun hero(c: Context, id: String): Bitmap? = pose(c, id, "idle")
    fun portrait(c: Context, id: String): Bitmap? = if (id in setOf("antimage", "anti_mage", "axe", "faceless_void", "legion_commander"))
        load(c, "art/heroes/v0.1.5/${if (id == "anti_mage") "antimage" else id}-portrait.png", 256) else hero(c, id)
    // 160 ≥ 最大显示尺寸（格位约 147px@420dpi），避免按 256 解码浪费一半主线程时间。
    fun item(c: Context, id: String) = load(c, "art/items/illustrated-v1/$id.png", 160)
    fun backdrop(c: Context) = load(c, "art/scenes/ruins-menu-v2.jpg", 1024)
    fun scene(c: Context, kind: SceneKind) = load(c, if (kind == SceneKind.CAMP || kind == SceneKind.ARENA)
        "art/scenes/camp-v2.jpg" else "art/scenes/${kind.name.lowercase()}-v1.jpg", 768)
    fun chest(c: Context, opened: Boolean) = load(c, "art/chests/${if (opened) "open" else "closed"}-v1.png", 384)
    internal fun atlas(c: Context, file: String): Bitmap? = load(c, "art/heroes/atlases/$file", 1024)
    fun pose(c: Context, id: String, pose: String): Bitmap? {
        if (id in setOf("antimage", "anti_mage", "axe", "faceless_void", "legion_commander"))
            return load(c, "art/heroes/v0.1.5/${if (id == "anti_mage") "antimage" else id}-idle.png")
        val file = when (id) {
            "juggernaut", "skywrath", "abaddon", "necrophos", "viper" -> id
            "phantom_assassin" -> "phantom-assassin"
            else -> return null
        }
        val suffix = if (pose in setOf("idle", "attack", "cast", "charge")) pose else "idle"
        return load(c, "art/heroes/$file-$suffix-v1.png") ?: if (suffix != "idle") load(c, "art/heroes/$file-idle-v1.png") else null
    }
    fun image(c: Context, bitmap: Bitmap, description: String) = ImageView(c).apply {
        setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = description
    }
    fun cover(canvas: Canvas, bitmap: Bitmap, rect: RectF, paint: Paint) {
        val scale = maxOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val w = rect.width() / scale; val h = rect.height() / scale
        val src = Rect(((bitmap.width - w) / 2).toInt(), ((bitmap.height - h) / 2).toInt(),
            ((bitmap.width + w) / 2).toInt(), ((bitmap.height + h) / 2).toInt())
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.alpha = 255
        canvas.drawBitmap(bitmap, src, rect, paint)
    }
}
