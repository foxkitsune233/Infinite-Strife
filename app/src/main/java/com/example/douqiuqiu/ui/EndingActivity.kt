package com.example.douqiuqiu.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.douqiuqiu.quest.EndingPlayback
import com.example.douqiuqiu.quest.HeroEndingCatalog

/** Black-screen subtitle player. Opening it never writes to the campaign save. */
class EndingActivity : AppCompatActivity() {
    private lateinit var playback: EndingPlayback
    private lateinit var subtitle: TextView
    private lateinit var progress: TextView
    private lateinit var next: TextView
    private lateinit var scroll: ScrollView
    private var revealing = false
    private var heroId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        heroId = intent.getStringExtra(EXTRA_HERO_ID).orEmpty()
        val ending = HeroEndingCatalog.forHero(heroId)
        if (ending == null) { finish(); return }
        playback = EndingPlayback(ending.pages, savedInstanceState?.getInt(STATE_PAGE) ?: 0)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(Color.BLACK)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(24), bars.top + dp(8), bars.right + dp(24), bars.bottom + dp(12))
            insets
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("${ending.heroName} · ${ending.title}", 13f, 0xFF999999.toInt()),
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("跳过", 14f, 0xFFBBBBBB.toInt()).apply {
            gravity = Gravity.CENTER
            contentDescription = "跳过结局并返回"
            isFocusable = true
            setOnClickListener { closeEnding(skipped = true) }
        }, LinearLayout.LayoutParams(dp(64), dp(48)))
        root.addView(header)

        subtitle = label("", 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
            setLineSpacing(dp(8).toFloat(), 1.08f)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isFocusable = true
            setOnClickListener { continueEnding() }
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(subtitle, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        progress = label("", 12f, 0xFF777777.toInt()).apply { gravity = Gravity.CENTER }
        root.addView(progress)
        next = label("", 14f, 0xFFBBBBBB.toInt()).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { continueEnding() }
        }
        root.addView(next, LinearLayout.LayoutParams(-1, dp(56)))
        root.setOnClickListener { continueEnding() }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { closeEnding(skipped = true) }
        })
        renderPage(animate = savedInstanceState == null)
    }

    private fun label(value: String, sp: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = sp * AppSettings.textScale(this@EndingActivity)
        setTextColor(color)
        includeFontPadding = true
    }

    private fun renderPage(animate: Boolean) {
        subtitle.animate().cancel()
        subtitle.text = playback.text
        scroll.scrollTo(0, 0)
        progress.text = "${playback.page + 1} / ${playback.pages.size}"
        next.text = if (playback.page == playback.pages.lastIndex) "结束 · 返回" else "点击继续  ▾"
        val systemAnimations = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        revealing = animate && AppSettings.animations(this) && systemAnimations
        subtitle.alpha = if (revealing) 0f else 1f
        if (revealing) subtitle.animate().alpha(1f).setDuration(700L).withEndAction { revealing = false }.start()
    }

    private fun continueEnding() {
        if (revealing) { revealImmediately(); return }
        if (playback.advance()) renderPage(animate = true) else closeEnding(skipped = false)
    }

    private fun revealImmediately() {
        if (!::subtitle.isInitialized) return
        subtitle.animate().cancel()
        subtitle.alpha = 1f
        revealing = false
    }

    private fun closeEnding(skipped: Boolean) {
        if (skipped) playback.skip()
        revealImmediately()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_HERO_ID, heroId).putExtra("skipped", skipped))
        finish()
    }

    override fun onPause() {
        revealImmediately()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::playback.isInitialized) outState.putInt(STATE_PAGE, playback.page)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HERO_ID = "endingHeroId"
        private const val STATE_PAGE = "endingPage"
        fun intent(context: Context, heroId: String): Intent =
            Intent(context, EndingActivity::class.java).putExtra(EXTRA_HERO_ID, heroId)
    }
}
