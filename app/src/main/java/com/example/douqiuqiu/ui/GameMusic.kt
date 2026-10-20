package com.example.douqiuqiu.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Collections
import java.util.WeakHashMap

/** One foreground-only music stream, separate from SoundPool cues and the campaign save. */
object GameMusic {
    private val handler = Handler(Looper.getMainLooper())
    private val resumed = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private var application: Application? = null
    private var player: MediaPlayer? = null
    private var prepared = false
    private var hasFocus = false
    private var focusHeld = false
    private var ducked = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var focusRequest: AudioFocusRequest? = null
    private val background = Runnable { if (resumed.isEmpty()) pauseAndAbandonFocus() }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { hasFocus = true; ducked = false; startIfAllowed() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; setVolume() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { hasFocus = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; focusHeld = false; pause() }
        }
    }

    fun install(app: Application) {
        if (application != null) return
        application = app
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed.add(activity); handler.removeCallbacks(background); refresh()
            }
            override fun onActivityPaused(activity: Activity) {
                resumed.remove(activity)
                if (resumed.isEmpty()) {
                    handler.removeCallbacks(background)
                    // Let another Activity resume without a stop/start at every in-app navigation.
                    handler.postDelayed(background, 180)
                }
            }
            override fun onActivityDestroyed(activity: Activity) { resumed.remove(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }

    /** Called on the main thread after a setting is durably saved. */
    fun refresh() {
        val app = application ?: return
        if (resumed.isEmpty() || !AppSettings.music(app)) { pauseAndAbandonFocus(); return }
        if (!ensurePlayer(app)) return
        if (!focusHeld) {
            val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val result = if (Build.VERSION.SDK_INT >= 26) {
                val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener, handler)
                    .build().also { focusRequest = it }
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusHeld = hasFocus
            ducked = false
        }
        startIfAllowed()
    }

    private fun ensurePlayer(app: Application): Boolean {
        if (player != null) return true
        return try {
            val next = MediaPlayer()
            player = next; prepared = false
            next.setAudioAttributes(attributes)
            app.assets.openFd("audio/bgm.ogg").use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            next.isLooping = true
            next.setOnPreparedListener {
                if (player === it) { prepared = true; startIfAllowed() }
            }
            next.setOnErrorListener { failed, what, extra ->
                if (player === failed) {
                    prepared = false; player = null
                    failed.release(); pauseAndAbandonFocus()
                    Log.w("GameMusic", "BGM decoder failed: $what/$extra")
                }
                true
            }
            next.prepareAsync()
            true
        } catch (e: Exception) {
            val failed = player
            player = null; prepared = false; failed?.release(); pauseAndAbandonFocus()
            Log.w("GameMusic", "BGM unavailable; gameplay continues", e)
            false
        }
    }

    private fun setVolume() {
        if (!prepared) return
        try { player?.setVolume(if (ducked) .05f else .18f, if (ducked) .05f else .18f) }
        catch (e: IllegalStateException) { resetFailedPlayer(e) }
    }
    private fun startIfAllowed() {
        val app = application ?: return
        if (!prepared || !hasFocus || resumed.isEmpty() || !AppSettings.music(app)) return
        try { setVolume(); player?.let { if (!it.isPlaying) it.start() } }
        catch (e: IllegalStateException) { resetFailedPlayer(e) }
    }
    private fun pause() {
        if (!prepared) return
        try { player?.let { if (it.isPlaying) it.pause() } }
        catch (e: IllegalStateException) { resetFailedPlayer(e) }
    }
    private fun resetFailedPlayer(e: Exception) {
        Log.w("GameMusic", "BGM state unavailable", e)
        player?.release(); player = null; prepared = false
        pauseAndAbandonFocus()
    }
    private fun pauseAndAbandonFocus() {
        pause()
        val app = application ?: return
        if (focusHeld) {
            val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusListener)
            }
        }
        focusHeld = false; hasFocus = false; ducked = false
    }
}
