package com.example.douqiuqiu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CriticalStrikeResolverTest {

    private val d = 1e-9

    private fun crit(
        id: String,
        multiplier: Double,
        threshold: Double,
        roll: Double,
        active: Boolean = true
    ) = CriticalStrikeSource(id, multiplier, threshold, roll, active)

    @Test
    fun `highest multiplier succeeds first and stops`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(
                crit("a", 3.0, 1.0, 0.5),
                crit("b", 2.0, 1.0, 0.5)
            )
        )
        assertEquals(true, r.critical)
        assertEquals(3.0, r.multiplier, d)
        assertEquals("a", r.sourceId)
        // b 本也能成功，但首次成功即停止，不再检查
        assertEquals(listOf("a"), r.evaluatedSourceIds)
    }

    @Test
    fun `high fails then low succeeds`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(
                crit("a", 3.0, 0.0, 0.5),
                crit("b", 2.0, 0.5, 0.2)
            )
        )
        assertEquals(true, r.critical)
        assertEquals(2.0, r.multiplier, d)
        assertEquals("b", r.sourceId)
        assertEquals(listOf("a", "b"), r.evaluatedSourceIds)
    }

    @Test
    fun `same multiplier uses stable id order`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(
                crit("z", 2.0, 0.5, 0.4),
                crit("a", 2.0, 0.5, 0.4)
            )
        )
        assertEquals(true, r.critical)
        assertEquals("a", r.sourceId)
        assertEquals(listOf("a"), r.evaluatedSourceIds)

        val reversed = CriticalStrikeResolver.resolve(
            listOf(
                crit("a", 2.0, 0.5, 0.4),
                crit("z", 2.0, 0.5, 0.4)
            )
        )
        assertEquals(r, reversed)
    }

    @Test
    fun `multipliers do not chain`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(
                crit("a", 3.0, 0.2, 0.1),
                crit("b", 2.0, 0.9, 0.1)
            )
        )
        // a 成功即停，倍率就是 3.0，不与 b 相乘
        assertEquals(true, r.critical)
        assertEquals(3.0, r.multiplier, d)
        assertEquals(listOf("a"), r.evaluatedSourceIds)
    }

    @Test
    fun `PA guaranteed source still competes by strength`() {
        val paWins = CriticalStrikeResolver.resolve(
            listOf(
                crit("pa", 3.0, 1.0, 0.999),
                crit("other", 2.5, 1.0, 0.999)
            )
        )
        assertEquals("pa", paWins.sourceId)
        assertEquals(3.0, paWins.multiplier, d)

        val strongerWins = CriticalStrikeResolver.resolve(
            listOf(
                crit("pa", 3.0, 1.0, 0.999),
                crit("stronger", 4.0, 1.0, 0.999)
            )
        )
        // PA 不享特殊优先，倍率更高者赢
        assertEquals("stronger", strongerWins.sourceId)
        assertEquals(4.0, strongerWins.multiplier, d)
    }

    @Test
    fun `all fail returns normal attack`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(
                crit("a", 3.0, 0.0, 0.0),
                crit("b", 2.0, 0.3, 0.3)
            )
        )
        assertEquals(false, r.critical)
        assertEquals(1.0, r.multiplier, d)
        assertNull(r.sourceId)
        assertEquals(listOf("a", "b"), r.evaluatedSourceIds)
    }

    @Test
    fun `empty sources returns normal attack`() {
        val r = CriticalStrikeResolver.resolve(emptyList())
        assertEquals(false, r.critical)
        assertEquals(1.0, r.multiplier, d)
        assertNull(r.sourceId)
        assertEquals(emptyList<String>(), r.evaluatedSourceIds)
    }

    @Test
    fun `threshold and roll boundaries use strict less than`() {
        val alwaysOn = CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 1.0, 0.999999)))
        assertEquals(true, alwaysOn.critical)

        val alwaysOff = CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 0.0, 0.0)))
        assertEquals(false, alwaysOff.critical)

        val equal = CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 0.5, 0.5)))
        assertEquals(false, equal.critical)
    }

    @Test
    fun `bonus multiplier only enhances triggered crit`() {
        val hit = CriticalStrikeResolver.resolve(
            listOf(crit("a", 2.0, 1.0, 0.1)),
            bonusMultiplier = 0.30
        )
        assertEquals(true, hit.critical)
        assertEquals(2.3, hit.multiplier, d)

        val miss = CriticalStrikeResolver.resolve(
            listOf(crit("a", 2.0, 0.0, 0.1)),
            bonusMultiplier = 0.30
        )
        assertEquals(false, miss.critical)
        // 不加到普通 1.0 攻击上
        assertEquals(1.0, miss.multiplier, d)
    }

    @Test
    fun `inactive excluded but validated`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(
                crit("a", 2.0, 1.0, 0.1),
                crit("b", 9.0, 1.0, 0.1, active = false)
            )
        )
        assertEquals("a", r.sourceId)
        assertEquals(listOf("a"), r.evaluatedSourceIds)

        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("b", 0.5, 1.0, 0.1, active = false)))
        }
    }

    @Test
    fun `reordered input is consistent`() {
        val sources = listOf(
            crit("a", 3.0, 0.0, 0.5),
            crit("b", 2.0, 0.5, 0.2),
            crit("c", 2.0, 0.9, 0.2)
        )
        val base = CriticalStrikeResolver.resolve(sources)
        val shuffled = CriticalStrikeResolver.resolve(sources.shuffled(java.util.Random(11)))
        assertEquals(base, shuffled)
    }

    @Test
    fun `rejects illegal parameters`() {
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 1.0, 0.5, 0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 0.5, 0.5, 0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", Double.NaN, 0.5, 0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 1.1, 0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, -0.1, 0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 0.5, 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 0.5, -0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("a", 2.0, 0.5, Double.NaN)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(listOf(crit("", 2.0, 0.5, 0.1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(
                listOf(crit("a", 2.0, 0.5, 0.1), crit("a", 3.0, 0.5, 0.1))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(emptyList(), bonusMultiplier = -0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(emptyList(), bonusMultiplier = Double.NaN)
        }
    }

    @Test
    fun `success multiplier overflow throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CriticalStrikeResolver.resolve(
                listOf(crit("a", Double.MAX_VALUE, 1.0, 0.1)),
                bonusMultiplier = Double.MAX_VALUE
            )
        }
    }

    @Test
    fun `failure does not manufacture overflow`() {
        val r = CriticalStrikeResolver.resolve(
            listOf(crit("a", Double.MAX_VALUE, 0.0, 0.1)),
            bonusMultiplier = Double.MAX_VALUE
        )
        assertEquals(false, r.critical)
        assertEquals(1.0, r.multiplier, d)
        assertNull(r.sourceId)
        assertEquals(listOf("a"), r.evaluatedSourceIds)
    }

    @Test
    fun `input list is not mutated and result is independent`() {
        val sources = mutableListOf(crit("a", 3.0, 1.0, 0.5), crit("b", 2.0, 1.0, 0.5))
        val r = CriticalStrikeResolver.resolve(sources)
        sources.add(crit("c", 4.0, 1.0, 0.5))
        sources.clear()
        assertEquals(true, r.critical)
        assertEquals("a", r.sourceId)
        assertEquals(listOf("a"), r.evaluatedSourceIds)
    }
}
