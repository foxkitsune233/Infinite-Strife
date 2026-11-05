package com.example.douqiuqiu.ui.components

import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.quest.QuestStoryChapter

/** Reusable portrait-and-text presentation for an approved quest story chapter. */
class QuestStorySheet(
    private val activity: AppCompatActivity,
    private val chapter: QuestStoryChapter,
    private val onClosed: () -> Unit
) {
    private val dialog = Dialog(activity)
    private var page = 0
    private val root = GameUi.panel(activity, true).apply { orientation = LinearLayout.VERTICAL }

    fun show() {
        renderPage()
        dialog.setContentView(root)
        dialog.setOnDismissListener { onClosed() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            setLayout((activity.resources.displayMetrics.widthPixels * .96f).toInt(),
                (activity.resources.displayMetrics.heightPixels * .87f).toInt())
        }
    }

    private fun renderPage() {
        val line = chapter.lines[page]
        val speaker = chapter.speakerFor(page)
        root.removeAllViews()
        val stage = FrameLayout(activity)
        GameArt.scene(activity, SceneKind.CAMP)?.let { art ->
            stage.addView(GameArt.image(activity, art, "营地背景").apply { scaleType = ImageView.ScaleType.CENTER_CROP },
                FrameLayout.LayoutParams(-1, -1))
        }
        chapter.portraits.forEach { portrait -> addPortrait(stage, portrait, portrait.heroId == speaker.heroId) }
        root.addView(stage, LinearLayout.LayoutParams(-1, GameUi.dp(activity, 270)))
        root.addView(GameUi.label(activity, speaker.displayName, 18, GameUi.gold, true))
        root.addView(GameUi.label(activity, line.text, 16, GameUi.ink))
        root.addView(GameUi.label(activity, "${page + 1} / ${chapter.lines.size}", 11, GameUi.muted))
        root.addView(GameUi.button(activity, if (page + 1 == chapter.lines.size) "结束" else "继续") {
            if (page + 1 == chapter.lines.size) dialog.dismiss() else { page += 1; renderPage() }
        })
        root.addView(GameUi.button(activity, "跳过本段") { dialog.dismiss() })
    }

    private fun addPortrait(stage: FrameLayout, portrait: com.example.douqiuqiu.quest.QuestStoryPortrait, active: Boolean) {
        val gravity = if (portrait.side == com.example.douqiuqiu.quest.QuestStorySide.LEFT) Gravity.START else Gravity.END
        val width = (activity.resources.displayMetrics.widthPixels * .58f).toInt()
        val params = FrameLayout.LayoutParams(width, -1, gravity)
        GameArt.portrait(activity, portrait.heroId)?.let { art ->
            stage.addView(GameArt.image(activity, art, "${portrait.displayName}立绘").apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                alpha = if (active) 1f else .55f
            }, params)
        }
        if (!active) stage.addView(View(activity).apply { setBackgroundColor(Color.argb(102, 0, 0, 0)) }, params)
    }
}
