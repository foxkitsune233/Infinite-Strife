package com.example.douqiuqiu.ui

import android.content.Context
import android.view.View

/** Presentation preferences are independent of the campaign save. */
object AppSettings {
    private fun prefs(c: Context) = c.getSharedPreferences("presentation-settings-v1", Context.MODE_PRIVATE)
    fun animations(c: Context) = prefs(c).getBoolean("animations", true)
    fun haptics(c: Context) = prefs(c).getBoolean("haptics", true)
    fun battleLog(c: Context) = prefs(c).getBoolean("battleLog", true)
    fun statusIcons(c: Context) = prefs(c).getBoolean("statusIcons", true)
    fun sound(c: Context) = prefs(c).getBoolean("sound", true)
    fun music(c: Context) = prefs(c).getBoolean("music", true)
    fun jingles(c: Context) = prefs(c).getBoolean("jingles", true)
    fun textLevel(c: Context) = prefs(c).getInt("textLevel", 0).coerceIn(0, 2)
    fun lastSeenRelease(c: Context) = prefs(c).getString("lastSeenRelease", "") ?: ""
    fun textScale(c: Context) = listOf(1f, 1.15f, 1.3f)[textLevel(c)]
    fun setFlag(c: Context, key: String, value: Boolean): Boolean {
        require(key in setOf("animations", "haptics", "battleLog", "statusIcons", "sound", "jingles", "music"))
        return prefs(c).edit().putBoolean(key, value).commit()
    }
    fun setTextLevel(c: Context, level: Int): Boolean {
        require(level in 0..2)
        return prefs(c).edit().putInt("textLevel", level).commit()
    }
    fun setLastSeenRelease(c: Context, version: String): Boolean = prefs(c).edit().putString("lastSeenRelease", version).commit()
    /** Debug fixtures use this instead of clearing the whole app preference file. */
    fun clearLastSeenRelease(c: Context): Boolean = prefs(c).edit().remove("lastSeenRelease").commit()
    fun reset(c: Context) = prefs(c).edit().clear().commit()
    fun feedback(view: View, kind: Int) { if (haptics(view.context)) view.performHapticFeedback(kind) }
}
