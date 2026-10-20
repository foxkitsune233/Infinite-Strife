package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.douqiuqiu.inventory.*

object ItemArtwork {
    fun view(c: Context, definition: ItemDefinition): ImageView = ImageView(c).apply {
        contentDescription = "${definition.name}图标"
        val card = if (definition.kind == ItemKind.CARD) GameArt.card(c, definition.id) else null
        if (card != null) { setImageBitmap(card); scaleType = ImageView.ScaleType.CENTER_CROP }
        else { setImageDrawable(PaintedItemIcons.drawable(c, definition)); scaleType = ImageView.ScaleType.FIT_CENTER }
    }
    fun draw(c: Context, canvas: Canvas, d: ItemDefinition, rect: RectF, paint: Paint) {
        val card = if (d.kind == ItemKind.CARD) GameArt.card(c, d.id) else null
        if (card != null) { GameArt.cover(canvas, card, rect, paint); return }
        val side = minOf(rect.width(), rect.height()).coerceAtLeast(0f)
        PaintedItemIcons.drawable(c, d).apply {
            setBounds((rect.centerX() - side / 2).toInt(), (rect.centerY() - side / 2).toInt(),
                (rect.centerX() + side / 2).toInt(), (rect.centerY() + side / 2).toInt())
            draw(canvas)
        }
    }
}

class IconListAdapter(c: Context, private val rows: List<Pair<ItemDefinition?, String>>) : ArrayAdapter<Pair<ItemDefinition?, String>>(c, 0, rows) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (d, title) = rows[position]
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; val padding = GameUi.dp(context, 12); setPadding(padding, padding, padding, padding)
            if (d != null) addView(ItemArtwork.view(context, d), LinearLayout.LayoutParams(GameUi.dp(context, 44), GameUi.dp(context, 44)))
            addView(GameUi.label(context, title, 14).apply { setPadding(padding, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            minimumHeight = GameUi.dp(context, 64)
        }
    }
}
