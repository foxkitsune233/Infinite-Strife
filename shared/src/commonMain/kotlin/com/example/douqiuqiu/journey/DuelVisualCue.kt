package com.example.douqiuqiu.journey

/** Presentation-only output: never serialized, never consulted by combat rules or RNG. */
enum class DuelCueKind { ULTIMATE_CAST, ATTACK, OMNI_SLASH, COUNTER_HELIX, FLARE_TICK, PA_CRITICAL_HIT, HIT, POISON_HIT, EVADED, ATTACK_BLOCKED, FROST_HIT }

data class DuelVisualCue(val tick: Int, val actor: Int, val target: Int, val heroId: String, val kind: DuelCueKind,
    val lifeDamage: Double = 0.0, val shieldDamage: Double = 0.0,
    val damageType: com.example.douqiuqiu.content.DamageType? = null, val critical: Boolean = false, val sequence: Int = 0)
data class DuelFrame(val state: DuelState, val cues: List<DuelVisualCue> = emptyList())
