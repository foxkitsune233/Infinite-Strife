package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.json.JSONObject

/** Exported pixel coordinates are independent of Android bitmap sampling. */
internal data class AttackSprite(val bitmap: Bitmap, val source: Rect, val rootX: Float, val rootY: Float,
                                 val bodyHeight: Float, val contentWidth: Float, val attackOriginX: Float, val attackOriginY: Float)

internal object AttackAtlas {
    private var helix: Entry? = null
    private data class Entry(val file: String, val alternateFile: String?, val width: Int, val height: Int,
                             val rootX: Float, val rootY: Float, val bodyHeight: Float, val contentWidth: Float,
                             val attackOriginX: Float, val attackOriginY: Float)
    private var entries: Map<String, Entry>? = null
    private fun entries(c: Context): Map<String, Entry> {
        entries?.let { return it }
        val root = c.assets.open("art/heroes/attack-atlas-v3.json").bufferedReader().use { JSONObject(it.readText()) }
        require(root.getInt("version") == 3)
        val heroes = root.getJSONObject("heroes")
        // Expansion entries are separate so exporting them cannot overwrite calibrated old heroes.
        try {
            val expansion = c.assets.open("art/heroes/v0.1.5/attack-manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("heroes") }
            expansion.keys().forEach { key ->
                val value = expansion.getJSONObject(key)
                if (value.optBoolean("runtimeReady", false)) heroes.put(key, value)
            }
        } catch (_: java.io.IOException) { /* Older APK assets have no expansion. */ }
        return heroes.keys().asSequence().associateWith { id ->
            val d = heroes.getJSONObject(id)
            Entry(d.getString("file"), d.optString("alternateFile").takeIf { it.isNotBlank() }, d.getInt("frameWidth"), d.getInt("frameHeight"),
                d.getDouble("rootX").toFloat(), d.getDouble("rootY").toFloat(),
                d.getDouble("bodyHeight").toFloat(), d.getDouble("contentWidth").toFloat(),
                d.getDouble("attackOriginX").toFloat(), d.getDouble("attackOriginY").toFloat()).also {
                require(it.width == 512 && it.height == 512 && it.rootX in 0f..512f && it.rootY in 0f..512f &&
                    it.bodyHeight > 0 && it.contentWidth in 1f..512f && it.attackOriginX in 0f..512f && it.attackOriginY in 0f..512f &&
                    it.file.matches(Regex("[a-z-]+-attack-atlas-v3.png")))
                require(it.alternateFile == null || it.alternateFile.matches(Regex("[a-z-]+-offhand-atlas-v4.png")))
            }
        }.also { entries = it }
    }
    fun frame(c: Context, hero: String, frame: Int): AttackSprite? {
        require(frame in 0..5)
        val entry = try { entries(c)[if (hero == "anti_mage") "antimage" else hero] } catch (_: java.io.IOException) { return null } ?: return null
        return sprite(c, entry, frame)
    }
    /** COUNTER_HELIX visual cue only; four source-facing poses form one loop. */
    fun helixFrame(c: Context, ageMs: Long): AttackSprite? {
        if (ageMs !in 0 until 480) return null
        val entry = helix ?: try {
            val d = c.assets.open("art/heroes/v0.1.5/axe-spin.json").bufferedReader().use { JSONObject(it.readText()) }
            Entry(d.getString("file"), null, 512, 512, 256f, 464f,
                d.getDouble("bodyHeight").toFloat(), d.getDouble("contentWidth").toFloat(), 360f, 270f).also { helix = it }
        } catch (_: java.io.IOException) { return null }
        return sprite(c, entry, (ageMs / 120).toInt())
    }
    private fun sprite(c: Context, entry: Entry, frame: Int): AttackSprite? {
        val alternate = frame >= 4 && entry.alternateFile != null
        val bitmap = GameArt.atlas(c, if (alternate) entry.alternateFile!! else entry.file) ?: return null
        val cell = if (alternate) frame - 4 else if (frame >= 4) frame - 3 else frame
        val sx = bitmap.width / (entry.width * 2f)
        val sy = bitmap.height / (entry.height * if (alternate) 1f else 2f)
        val left = (cell % 2 * entry.width * sx).toInt(); val top = (cell / 2 * entry.height * sy).toInt()
        return AttackSprite(bitmap, Rect(left, top, left + (entry.width * sx).toInt(), top + (entry.height * sy).toInt()),
            entry.rootX, entry.rootY, entry.bodyHeight, entry.contentWidth, entry.attackOriginX, entry.attackOriginY)
    }
}
