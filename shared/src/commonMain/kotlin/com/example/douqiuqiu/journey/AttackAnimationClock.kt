package com.example.douqiuqiu.journey

/** Presentation timing only. Frame 0 doubles as the stable idle pose. */
object AttackAnimationClock {
    fun durationMs(nextAttackSeconds: Double): Long {
        require(nextAttackSeconds.isFinite() && nextAttackSeconds >= 0)
        return (nextAttackSeconds * 900).coerceIn(60.0, 420.0).toLong()
    }
    fun frameAt(ageMs: Long, durationMs: Long): Int {
        require(durationMs in 60..420)
        if (ageMs < 0 || ageMs >= durationMs) return 0
        val phase = ageMs.toDouble() / durationMs
        return when { phase < .14 -> 0; phase < .34 -> 1; phase < .68 -> 2; else -> 3 }
    }
}
