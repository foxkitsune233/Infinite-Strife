package com.example.douqiuqiu.ui.components

import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/** Game-framed, scrollable catalog. Data and transactions remain in the owning Activity. */
class GameCatalogSheet(private val activity: AppCompatActivity, title: String, detail: String, closeLabel: String = "返回整备", stoneHeader: Boolean = false) {
    val dialog = Dialog(activity)
    val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val root = GameUi.panel(activity, true).apply {
        val heading = if (stoneHeader) GameUi.panel(activity) else GameUi.paper(activity)
        if (stoneHeader) {
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(GameUi.label(activity, title, 23, GameUi.gold, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(GameUi.label(activity, "×", 26, GameUi.gold, true).apply {
                gravity = Gravity.CENTER; isFocusable = true; contentDescription = "关闭$title"
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(GameUi.dp(activity, 44), GameUi.dp(activity, 44)))
            heading.addView(row)
        } else heading.addView(GameUi.label(activity, title, 23, GameUi.paperInk, true))
        heading.addView(GameUi.label(activity, detail, 12, if (stoneHeader) GameUi.muted else GameUi.paperInk))
        addView(heading)
        addView(ScrollView(activity).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(actions)
        addView(GameUi.button(activity, closeLabel) { dialog.dismiss() })
    }
    fun show() {
        dialog.setContentView(root); dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            setLayout((activity.resources.displayMetrics.widthPixels * .96f).toInt(),
                (activity.resources.displayMetrics.heightPixels * .87f).toInt())
        }
    }
}
