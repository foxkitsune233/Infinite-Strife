package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind

/** Game-icons.net / Lorc & Delapouite, CC BY 3.0. See assets/licenses for per-icon attribution. */
object OpenItemIcons {
    fun resourceId(id: String, spec: IconSpec): Int? = OpenIconResources.items[id] ?: OpenIconResources.glyphs[spec.glyph]
    fun drawable(c: Context, d: ItemDefinition): Drawable {
        val spec = ItemIconCatalog.spec(d)
        if (d.kind == ItemKind.CARD) return GameIconDrawable(spec)
        val id = resourceId(d.id, spec) ?: return GameIconDrawable(spec)
        fun load(resource: Int, tint: Int): Drawable = checkNotNull(AppCompatResources.getDrawable(c, resource)).mutate().apply { setTint(tint) }
        val base = load(id, spec.tint)
        if (d.kind != ItemKind.SCROLL) return base
        val productId = when (d.id) { "recipe_armlet" -> "armlet_of_mordiggian"; "recipe_heart" -> "heart_of_tarrasque"; else -> d.id.removePrefix("recipe_") }
        val productGlyph = IconGlyph.entries.getOrNull(spec.variant) ?: IconGlyph.SWORD
        val product = OpenIconResources.items[productId] ?: OpenIconResources.glyphs[productGlyph] ?: return base
        val badge = load(product, 0xFF102532.toInt())
        return RecipeIcon(base, badge)
    }
    private class RecipeIcon(private val scroll: Drawable, private val product: Drawable) : Drawable() {
        override fun getIntrinsicWidth() = scroll.intrinsicWidth
        override fun getIntrinsicHeight() = scroll.intrinsicHeight
        override fun draw(canvas: Canvas) {
            scroll.bounds = bounds; scroll.draw(canvas)
            val w = bounds.width(); val h = bounds.height()
            product.setBounds(bounds.left + w * 31 / 100, bounds.top + h * 25 / 100,
                bounds.left + w * 73 / 100, bounds.top + h * 69 / 100)
            product.draw(canvas)
        }
        override fun setAlpha(alpha: Int) { scroll.alpha = alpha; product.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { scroll.colorFilter = filter; product.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
