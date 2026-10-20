package com.example.douqiuqiu.ui.components

/** Explicit layout for the existing full-body poses; review sheets are not runtime sprite atlases. */
internal object HeroPoseLayout {
    data class Anchor(val x: Float = .5f, val y: Float = 1f, val bodyFraction: Float = .94f, val heightFraction: Float = .65f)
    fun anchor(hero: String, pose: String): Anchor = when (hero) {
        "abaddon" -> Anchor(bodyFraction = if (pose == "cast") .69f else .98f, heightFraction = .57f)
        "skywrath" -> Anchor(bodyFraction = .86f)
        else -> Anchor()
    }
}
