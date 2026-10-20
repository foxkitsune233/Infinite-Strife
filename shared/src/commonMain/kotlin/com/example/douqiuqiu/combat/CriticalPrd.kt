package com.example.douqiuqiu.combat

/** Linear rising chance, calibrated so the long-run mean equals the configured probability. */
object CriticalPrd {
    private val common = setOf(.10, .12, .18, .24, .25).associateWith { calibrate(it) }

    fun threshold(probability: Double, failures: Int): Double {
        require(probability.isFinite() && probability in 0.0..1.0 && failures in 0..10000)
        if (probability == 0.0 || probability == 1.0) return probability
        return minOf(1.0, (common[probability] ?: calibrate(probability)) * (failures + 1))
    }

    private fun calibrate(probability: Double): Double {
        // Content currently uses >=10%; explicitly reject pathological input instead of a long loop.
        require(probability in .01..1.0)
        var low = 0.0
        var high = probability
        repeat(52) {
            val c = (low + high) / 2
            var survival = 1.0
            var expected = 0.0
            var trial = 1
            while (survival > 1e-14) {
                expected += survival
                survival *= 1 - minOf(1.0, c * trial)
                trial++
                require(trial <= 100000) { "伪随机参数无法校准" }
            }
            if (1 / expected > probability) high = c else low = c
        }
        return (low + high) / 2
    }
}
