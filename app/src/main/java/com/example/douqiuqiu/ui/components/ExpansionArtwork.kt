package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import org.json.JSONObject

/** Visual-only expansion index. Unknown content IDs keep the existing fallback. */
internal object ExpansionArtwork {
    fun statusIcon(c: Context, key: String): android.graphics.drawable.Drawable? {
        val resource = when (key) {
            "rage" -> com.example.douqiuqiu.R.drawable.ic_v015_rage
            "frost", "freeze" -> com.example.douqiuqiu.R.drawable.ic_v015_frost
            "time_stop" -> com.example.douqiuqiu.R.drawable.ic_v015_time_stop
            "taunt" -> com.example.douqiuqiu.R.drawable.ic_v015_taunt
            else -> return null
        }
        return androidx.appcompat.content.res.AppCompatResources.getDrawable(c, resource)?.mutate()
    }
    private var cards: Map<String, String>? = null
    private var events: Map<String, String>? = null

    private fun index(c: Context, path: String, build: (JSONObject) -> Map<String, String>): Map<String, String> =
        try { c.assets.open(path).bufferedReader().use { build(JSONObject(it.readText())) } }
        catch (_: java.io.IOException) { emptyMap() }
        catch (_: org.json.JSONException) { emptyMap() }

    fun cardFile(c: Context, id: String): String? {
        val entries = cards ?: index(c, "art/cards/v0.1.5/manifest.json") { root ->
            buildMap {
                val art = root.getJSONArray("art")
                for (i in 0 until art.length()) {
                    val entry = art.getJSONObject(i)
                    val file = entry.getString("file")
                    require(file.matches(Regex("v0\\.1\\.5/[A-Z0-9]+\\.webp")))
                    put(entry.getString("id"), file)
                    put(entry.getString("artKey"), file)
                }
            }
        }.also { cards = it }
        return entries[id]
    }

    fun eventFile(c: Context, key: String): String? {
        val entries = events ?: index(c, "art/events/v0.1.5/manifest.json") { root ->
            val assets = root.getJSONObject("assets")
            assets.keys().asSequence().associateWith { id ->
                assets.getJSONObject(id).getString("file").also {
                    require(it.matches(Regex("v0\\.1\\.5/[a-z_]+\\.[a-z_]+\\.webp")))
                }
            }
        }.also { events = it }
        return entries[key]
    }

    /** Persist this once in the result receipt, never infer art from translated labels. */
    fun eventKey(kind: String, optionId: String): String = try {
        com.example.douqiuqiu.event.EventCatalog.artKey(
            com.example.douqiuqiu.event.EventKind.valueOf(kind), optionId)
    } catch (_: IllegalArgumentException) { "" }
}

/** Full-width illustration; native result text stays outside the painted image. */
internal class EventOutcomeArtworkView(c: Context, private val artKey: String, private val fallback: SceneKind) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val image = GameArt.eventOutcome(context, artKey) ?: GameArt.scene(context, fallback)
        if (image != null) GameArt.cover(canvas, image, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        else SceneArtwork.draw(canvas, width.toFloat(), height.toFloat(), fallback)
    }
}
