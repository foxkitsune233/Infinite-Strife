package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind

/** Concrete, full-color objects. Abstract CC BY icons are reserved for status indicators. */
object PaintedItemIcons {
    private val specific = setOf("loot_ruby", "loot_star_diamond", "loot_gold_seal", "loot_jeweled_egg", "loot_royal_screen", "black_king_bar", "mjollnir", "satanic", "mask_of_madness", "witch_blade", "rod_of_atos", "viper_bite", "force_staff", "hurricane_pike", "loot_silver_coin", "loot_pocket_watch", "loot_silver_cups", "loot_old_carpet", "loot_tapestry", "loot_royal_pendant", "sange_and_yasha", "kaya_and_sange", "yasha_and_kaya")
    fun drawable(c: Context, d: ItemDefinition): Drawable {
        if (d.kind == ItemKind.CARD) GameArt.card(c, d.id)?.let { return BitmapDrawable(c.resources, it) }
        GameArt.item(c, d.id)?.let { return BitmapDrawable(c.resources, it) }
        val spec = ItemIconCatalog.spec(d)
        val key = if (d.id in specific || d.id in components) d.id else if (spec.glyph == IconGlyph.CROSS) "BOW" else spec.glyph.name
        // A new per-item asset must take effect without maintaining a second ID whitelist.
        val image = GameArt.item(c, key) ?: return GameIconDrawable(spec)
        val base = BitmapDrawable(c.resources, image)
        if (d.kind != ItemKind.SCROLL) return base
        val glyph = IconGlyph.entries.getOrNull(spec.variant) ?: IconGlyph.SWORD
        val productId = when (d.id) {
            "recipe_armlet" -> "armlet_of_mordiggian"
            "recipe_heart" -> "heart_of_tarrasque"
            else -> d.id.removePrefix("recipe_")
        }
        val product = GameArt.item(c, productId) ?: GameArt.item(c, if (glyph == IconGlyph.CROSS) "BOW" else glyph.name) ?: return base
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            override fun getIntrinsicWidth() = base.intrinsicWidth
            override fun getIntrinsicHeight() = base.intrinsicHeight
            override fun draw(canvas: Canvas) {
                base.bounds = bounds; base.draw(canvas)
                val r = RectF(bounds)
                r.inset(r.width() * .27f, r.height() * .27f)
                canvas.drawBitmap(product, null, r, paint)
            }
            override fun setAlpha(alpha: Int) { base.alpha = alpha; paint.alpha = alpha }
            override fun setColorFilter(filter: ColorFilter?) { base.colorFilter = filter; paint.colorFilter = filter }
            @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }
    private val components = setOf("iron_branch", "circlet", "crown", "diadem", "belt_of_strength", "band_of_elvenskin", "robe_of_magi", "gauntlets", "slippers", "broadsword", "claymore", "mithril_hammer", "javelin", "gloves_of_haste", "quarterstaff", "blade_of_alacrity", "ogre_axe", "chainmail", "mantle", "sange", "yasha", "kaya", "ring_of_regen", "ring_of_health", "voodoo_mask", "sage_mask", "fluffy_hat")
}
