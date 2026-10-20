package com.example.douqiuqiu.ui.components

import android.content.Context
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.douqiuqiu.ui.AppSettings
import com.example.douqiuqiu.ui.SettingsActivity

/** Native visual primitives. Business state and save transactions stay in the Activities. */
object GameUi {
    val background = Color.rgb(5, 31, 47)
    val surface = Color.rgb(9, 57, 76)
    val raised = Color.rgb(12, 102, 126)
    val line = Color.rgb(48, 167, 199)
    val ink = Color.rgb(255, 244, 204)
    val muted = Color.rgb(180, 219, 225)
    val gold = Color.rgb(255, 202, 63)
    val green = Color.rgb(100, 229, 128)
    val danger = Color.rgb(255, 108, 66)
    val parchment = Color.rgb(255, 226, 151)
    val paperInk = Color.rgb(28, 44, 49)
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun quality(value: String): Int = when (value) {
        "金", "GOLD" -> gold; "紫", "PURPLE" -> Color.rgb(192, 104, 255); "蓝", "BLUE" -> Color.rgb(65, 192, 255); else -> ink
    }
    @Suppress("UNUSED_PARAMETER")
    fun shape(c: Context, fill: Int = surface, stroke: Int = line, radius: Int = 16): android.graphics.drawable.Drawable = frame(c, fill, stroke)
    fun frame(c: Context, fill: Int = surface, stroke: Int = line) = CutPanelDrawable(fill, stroke, c.resources.displayMetrics.density)
    fun label(c: Context, value: String, size: Int = 14, color: Int = ink, bold: Boolean = false): TextView = TextView(c).apply {
        text = value; textSize = size * AppSettings.textScale(c); setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(c, 3).toFloat(), 1f)
    }
    fun button(c: Context, title: String, primary: Boolean = false, block: () -> Unit): Button = Button(c).apply {
        text = title; isAllCaps = false; textSize = 14f * AppSettings.textScale(c)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        minHeight = dp(c, 50); minimumHeight = dp(c, 50)
        setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7))
        setTextColor(if (primary) GameUi.background else ink)
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(0x337ED1B1),
            frame(c, if (primary) gold else raised, if (primary) 0xFFFFEB9A.toInt() else line), null)
        setOnClickListener { AppSettings.feedback(this, HapticFeedbackConstants.CLOCK_TICK); com.example.douqiuqiu.ui.GameAudio.play(c, com.example.douqiuqiu.ui.GameAudio.Cue.CLICK); block() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 8) }
    }
    fun paper(c: Context): LinearLayout = panel(c).apply {
        background = frame(c, parchment, gold)
        setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
    }
    fun panel(c: Context, selected: Boolean = false): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15))
        background = frame(c, if (selected) raised else surface, if (selected) gold else line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12) }
    }
    data class Navigation(val title: String, val active: Boolean = false, val action: () -> Unit)
    data class Shell(val body: LinearLayout, val accessory: LinearLayout, val scroll: ScrollView)

    fun screen(activity: AppCompatActivity, title: String, subtitle: String, navigation: List<Navigation>, compact: Boolean = false): Shell {
        com.example.douqiuqiu.ui.GameAudio.prepare(activity)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; background = IllustratedBackdrop(activity) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 8))
            background = frame(activity, surface, line)
        }
        val top = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(activity, title, if (compact) 18 else 23, gold, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (activity !is SettingsActivity) top.addView(button(activity, "设置") {
            activity.startActivity(android.content.Intent(activity, SettingsActivity::class.java))
        }.apply {
            if (compact) { minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0); textSize = 12f }
        }, LinearLayout.LayoutParams(dp(activity, if (compact) 56 else 76), if (compact) dp(activity, 36) else -2))
        header.addView(top)
        header.addView(label(activity, subtitle, 12, muted))
        if (com.example.douqiuqiu.inventory.TestProfile.active(activity)) header.addView(label(activity,
            "调试存档 · 测试模式", 11, danger, true))
        root.addView(header)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 12), 0, dp(activity, 12), dp(activity, 16))
        }
        val scroll = DragScrollView(activity).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val accessory = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), 0, dp(activity, 16), dp(activity, 6))
            background = frame(activity, surface, gold)
        }
        root.addView(accessory)
        if (navigation.isNotEmpty()) {
            val bar = LinearLayout(activity).apply {
                gravity = Gravity.CENTER; background = frame(activity, surface, line)
                setPadding(dp(activity, 10), dp(activity, 6), dp(activity, 10), dp(activity, 8))
            }
            navigation.forEach { nav ->
                bar.addView(button(activity, nav.title, nav.active, nav.action).apply {
                    if (compact) { minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0) }
                }, LinearLayout.LayoutParams(0, if (compact) dp(activity, 40) else -2, 1f).apply {
                    setMargins(dp(activity, 3), 0, dp(activity, 3), 0)
                })
            }
            root.addView(bar)
        }
        activity.setContentView(root)
        if (InspectionDialog.hasPending(activity)) {
            body.visibility = View.INVISIBLE
            accessory.visibility = View.INVISIBLE
        }
        if (activity !is com.example.douqiuqiu.ui.JourneyActivity && InspectionDialog.hasPending(activity)) {
            root.post {
                activity.startActivity(android.content.Intent(activity, com.example.douqiuqiu.ui.JourneyActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
        ViewCompat.requestApplyInsets(root)
        return Shell(body, accessory, scroll)
    }

    fun section(parent: LinearLayout, title: String, detail: String = "") {
        val c = parent.context
        parent.addView(label(c, title, 17, gold, true).apply {
            setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
            background = frame(c, raised, line)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 14) }
        })
        if (detail.isNotBlank()) parent.addView(label(c, detail, 12, muted))
    }

    fun heroBanner(parent: LinearLayout, heroId: String, title: String, detail: String) {
        val c = parent.context
        val panel = panel(c)
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        GameArt.hero(c, heroId)?.let { bitmap ->
            row.addView(GameArt.image(c, bitmap, com.example.douqiuqiu.journey.FirstRunContent.hero(heroId).name).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(c, 100), dp(c, 116)))
        }
        row.addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(c, title, 12, gold, true))
            addView(label(c, com.example.douqiuqiu.journey.FirstRunContent.hero(heroId).name, 22, ink, true))
            addView(label(c, detail, 12, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(row); parent.addView(panel)
    }

    fun sheet(activity: AppCompatActivity, title: String, detail: String, actions: List<String>, choose: (Int) -> Unit) {
        val dialog = Dialog(activity)
        val panel = panel(activity).apply { setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 24)) }
        panel.addView(label(activity, title, 22, gold, true))
        panel.addView(label(activity, detail, 13, muted))
        actions.forEachIndexed { index, action ->
            panel.addView(button(activity, action) { dialog.dismiss(); choose(index) })
        }
        panel.addView(button(activity, "关闭") { dialog.dismiss() })
        dialog.setContentView(ScrollView(activity).apply { addView(panel) })
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(-1, minOf(dp(activity, 150 + (actions.size + 1) * 60), (activity.resources.displayMetrics.heightPixels * .85).toInt()))
        }
    }
}
