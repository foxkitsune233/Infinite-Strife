package com.example.douqiuqiu.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock

/** Short CC0 cues only. System media volume applies; audio never touches simulation. */
object GameAudio {
    enum class Cue(val file: String) { CLICK("click"), HIT("hit"), SKILL("skill"), REVEAL("reveal"), WIN("win"), LOSE("lose") }
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Cue, Int>()
    private val ready = mutableSetOf<Int>()
    private val last = mutableMapOf<Cue, Long>()
    private val streams = mutableSetOf<Int>()
    @Synchronized fun prepare(c: Context) {
        if (pool != null) return
        val sound = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        pool = sound
        (c.applicationContext as? android.app.Application)?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(a: android.app.Activity) { stop() }
            override fun onActivityCreated(a: android.app.Activity, s: android.os.Bundle?) = Unit
            override fun onActivityStarted(a: android.app.Activity) = Unit
            override fun onActivityResumed(a: android.app.Activity) = Unit
            override fun onActivityStopped(a: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(a: android.app.Activity, s: android.os.Bundle) = Unit
            override fun onActivityDestroyed(a: android.app.Activity) = Unit
        })
        sound.setOnLoadCompleteListener { _, sample, status -> synchronized(this) { if (status == 0) ready += sample } }
        Cue.entries.forEach { cue ->
            try { c.assets.openFd("audio/${cue.file}.ogg").use { ids[cue] = sound.load(it, 1) } }
            catch (_: Exception) { /* Absent sounds never block gameplay. */ }
        }
    }
    @Synchronized fun play(c: Context, cue: Cue) {
        prepare(c)
        if (!AppSettings.sound(c) || ((cue == Cue.WIN || cue == Cue.LOSE) && !AppSettings.jingles(c))) return
        val sample = ids[cue] ?: return
        if (sample !in ready) return
        val now = SystemClock.uptimeMillis()
        if (now - (last[cue] ?: -10000) < if (cue == Cue.HIT) 180 else 80) return
        last[cue] = now
        if (streams.size > 16) streams.clear()
        val volume = if (cue == Cue.HIT) .22f else .45f
        val stream = pool?.play(sample, volume, volume, 1, 0, 1f) ?: 0
        if (stream != 0) streams += stream
    }
    @Synchronized fun stop() { streams.forEach { pool?.stop(it) }; streams.clear() }
}
