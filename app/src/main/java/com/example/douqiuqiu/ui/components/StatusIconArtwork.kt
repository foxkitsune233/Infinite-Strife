package com.example.douqiuqiu.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources

/** Open-source silhouettes are status marks, not inventory illustrations. */
object StatusIconArtwork {
    fun drawable(c: Context, spec: IconSpec): Drawable {
        val id = OpenIconResources.glyphs[spec.glyph] ?: return GameIconDrawable(spec)
        return checkNotNull(AppCompatResources.getDrawable(c, id)).mutate().apply { setTint(spec.tint) }
    }
}
