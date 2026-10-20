package com.example.douqiuqiu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownReductionTest {

    private val d = 1e-9

    private fun src(
        id: String,
        fraction: Double,
        group: String? = null,
        active: Boolean = true
    ) = CooldownReductionSource(id, fraction, group, active)

    @Test
    fun `no sources applies nothing but still respects floor`() {
        val r = CooldownReduction.calculate(10.0, emptyList())
        assertEquals(0.0, r.reduction, d)
        assertEquals(10.0, r.effectiveSeconds, d)
        assertTrue(r.appliedSourceIds.isEmpty())
    }

    @Test
    fun `twenty and thirty percent multiply to forty four`() {
        val r = CooldownReduction.calculate(10.0, listOf(src("a", 0.20), src("b", 0.30)))
        assertEquals(0.44, r.reduction, d)
        assertEquals(5.6, r.effectiveSeconds, d)
        assertEquals(listOf("a", "b"), r.appliedSourceIds)
    }

    @Test
    fun `reduction capped at sixty percent`() {
        val r = CooldownReduction.calculate(
            10.0,
            listOf(src("a", 0.50), src("b", 0.40), src("c", 0.30))
        )
        assertEquals(0.60, r.reduction, d)
        assertEquals(4.0, r.effectiveSeconds, d)
        assertEquals(listOf("a", "b", "c"), r.appliedSourceIds)
    }

    @Test
    fun `unique group keeps only highest fraction`() {
        val r = CooldownReduction.calculate(
            10.0,
            listOf(
                src("g1", 0.15, "octarine"),
                src("g2", 0.25, "octarine"),
                src("u1", 0.10)
            )
        )
        // 只有 g2(25%) 与 u1(10%) 参与：1 - 0.75*0.9 = 0.325
        assertEquals(0.325, r.reduction, d)
        assertEquals(6.75, r.effectiveSeconds, d)
        assertEquals(listOf("g2", "u1"), r.appliedSourceIds)
    }

    @Test
    fun `tie inside unique group picks smallest instance id`() {
        val r = CooldownReduction.calculate(
            10.0,
            listOf(src("z", 0.20, "octarine"), src("a", 0.20, "octarine"))
        )
        assertEquals(0.20, r.reduction, d)
        assertEquals(8.0, r.effectiveSeconds, d)
        assertEquals(listOf("a"), r.appliedSourceIds)
    }

    @Test
    fun `ungrouped sources are not deduplicated`() {
        val r = CooldownReduction.calculate(10.0, listOf(src("a", 0.20), src("b", 0.20)))
        // 1 - 0.8*0.8 = 0.36
        assertEquals(0.36, r.reduction, d)
        assertEquals(6.4, r.effectiveSeconds, d)
        assertEquals(listOf("a", "b"), r.appliedSourceIds)
    }

    @Test
    fun `zero fraction winners are still applied`() {
        val r = CooldownReduction.calculate(
            10.0,
            listOf(src("z", 0.0, "g"), src("a", 0.0, "g"), src("u", 0.0))
        )
        assertEquals(0.0, r.reduction, d)
        assertEquals(10.0, r.effectiveSeconds, d)
        assertEquals(listOf("a", "u"), r.appliedSourceIds)
    }

    @Test
    fun `zero and full fraction sources`() {
        val r = CooldownReduction.calculate(10.0, listOf(src("a", 0.0), src("b", 1.0)))
        // 1 - 1*0 = 1.0 -> 封顶 0.6
        assertEquals(0.60, r.reduction, d)
        assertEquals(4.0, r.effectiveSeconds, d)
        assertEquals(listOf("a", "b"), r.appliedSourceIds)
    }

    @Test
    fun `inactive excluded from result but still validated`() {
        val r = CooldownReduction.calculate(
            10.0,
            listOf(src("a", 0.20), src("b", 0.50, active = false))
        )
        assertEquals(0.20, r.reduction, d)
        assertEquals(8.0, r.effectiveSeconds, d)
        assertEquals(listOf("a"), r.appliedSourceIds)

        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("b", 1.5, active = false)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(
                10.0,
                listOf(src("a", 0.20), src("a", 0.30, active = false))
            )
        }
    }

    @Test
    fun `default minimum floor is half second`() {
        val r = CooldownReduction.calculate(
            1.0,
            listOf(src("a", 0.50), src("b", 0.40), src("c", 0.30))
        )
        // 60% 封顶 -> 1*0.4 = 0.4 -> 抬到 0.5
        assertEquals(0.60, r.reduction, d)
        assertEquals(0.5, r.effectiveSeconds, d)
    }

    @Test
    fun `ghost shroud minimum ten seconds`() {
        val half = CooldownReduction.calculate(
            18.0,
            listOf(src("a", 0.50)),
            minimumSeconds = 10.0
        )
        // 18*0.5 = 9 -> 不能短于 10
        assertEquals(0.50, half.reduction, d)
        assertEquals(10.0, half.effectiveSeconds, d)

        val full = CooldownReduction.calculate(
            18.0,
            listOf(src("a", 0.50), src("b", 0.40), src("c", 0.30)),
            minimumSeconds = 10.0
        )
        // 60% 封顶 -> 18*0.4 = 7.2 -> 仍为 10
        assertEquals(10.0, full.effectiveSeconds, d)
    }

    @Test
    fun `reordered input is stable`() {
        val sources = listOf(
            src("g1", 0.15, "octarine"),
            src("g2", 0.25, "octarine"),
            src("u1", 0.10),
            src("u2", 0.05)
        )
        val a = CooldownReduction.calculate(10.0, sources)
        val b = CooldownReduction.calculate(10.0, sources.reversed())
        val c = CooldownReduction.calculate(10.0, sources.shuffled(java.util.Random(7)))
        assertEquals(a, b)
        assertEquals(a, c)
        assertEquals(listOf("g2", "u1", "u2"), a.appliedSourceIds)
    }

    @Test
    fun `rejects illegal base and minimum seconds`() {
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(Double.NaN, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(Double.POSITIVE_INFINITY, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(0.0, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(-1.0, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, emptyList(), minimumSeconds = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, emptyList(), minimumSeconds = Double.NaN)
        }
    }

    @Test
    fun `rejects illegal fraction and ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", -0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", 1.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", Double.NaN)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", Double.POSITIVE_INFINITY)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("", 0.2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("   ", 0.2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", 0.2), src("a", 0.3)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", 0.2, group = "")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CooldownReduction.calculate(10.0, listOf(src("a", 0.2, group = "   ")))
        }
    }

    @Test
    fun `input list is not mutated and result is independent`() {
        val sources = mutableListOf(src("a", 0.20), src("b", 0.30))
        val r = CooldownReduction.calculate(10.0, sources)
        sources.add(src("c", 0.50))
        sources.clear()
        assertEquals(0.44, r.reduction, d)
        assertEquals(listOf("a", "b"), r.appliedSourceIds)

        val again = CooldownReduction.calculate(
            10.0,
            listOf(src("a", 0.20), src("b", 0.30))
        )
        assertEquals(again, r)
    }
}
