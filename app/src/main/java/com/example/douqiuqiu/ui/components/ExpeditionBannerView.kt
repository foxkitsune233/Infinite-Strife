package com.example.douqiuqiu.ui.components

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Large character/chest composition; text stays native, never baked into a screenshot. */
class ExpeditionBannerView(c: Context, hero: String, title: String, detail: String) : FrameLayout(c) {
    init {
        val pad = GameUi.dp(c, 6); setPadding(pad, pad, pad, pad)
        background = GameUi.frame(c, GameUi.surface, GameUi.gold)
        GameArt.scene(c, SceneKind.CAMP)?.let { addView(GameArt.image(c, it, ""), LayoutParams(-1, -1)) }
        GameArt.hero(c, hero)?.let {
            addView(GameArt.image(c, it, "出战英雄").apply { scaleType = ImageView.ScaleType.FIT_CENTER },
                LayoutParams(GameUi.dp(c, 138), GameUi.dp(c, 174), Gravity.START or Gravity.BOTTOM))
        }
        val heading = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(GameUi.label(c, title, 23, GameUi.gold, true).apply { setShadowLayer(GameUi.dp(c, 2).toFloat(), 1f, 2f, GameUi.background) })
            addView(GameUi.label(c, detail, 12, GameUi.ink).apply { setShadowLayer(2f, 1f, 1f, GameUi.background) })
        }
        addView(heading, LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(GameUi.dp(c, 120), GameUi.dp(c, 12), GameUi.dp(c, 8), 0)
        })
        GameArt.chest(c, false)?.let {
            addView(GameArt.image(c, it, "目标宝箱").apply { scaleType = ImageView.ScaleType.FIT_CENTER },
                LayoutParams(GameUi.dp(c, 88), GameUi.dp(c, 80), Gravity.END or Gravity.BOTTOM).apply { rightMargin = GameUi.dp(c, 6) })
        }
        layoutParams = LinearLayout.LayoutParams(-1, GameUi.dp(c, 184)).apply { topMargin = GameUi.dp(c, 8) }
    }
}
