package com.example.douqiuqiu.equipment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UniqueBonusSelectorTest {

    private val d = 1e-9

    private fun cand(
        id: String,
        group: String,
        effect: String,
        value: Double,
        active: Boolean = true
    ) = UniqueBonusCandidate(id, group, effect, value, active)

    @Test
    fun `single blades same family pick highest`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("a", "satian", "regen", 5.0),
                cand("b", "satian", "regen", 8.0)
            )
        )
        assertEquals(1, r.size)
        assertEquals(SelectedUniqueBonus("satian", "regen", "b", 8.0), r[0])
    }

    @Test
    fun `double blade contributes to two families independently`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("dual", "satian", "regen", 6.0),
                cand("dual", "yasha", "attack_speed", 12.0)
            )
        )
        assertEquals(2, r.size)
        // 按 groupId 字典序：satian < yasha
        assertEquals(SelectedUniqueBonus("satian", "regen", "dual", 6.0), r[0])
        assertEquals(SelectedUniqueBonus("yasha", "attack_speed", "dual", 12.0), r[1])
    }

    @Test
    fun `one equipment multiple effects same family`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("blade", "satian", "regen", 5.0),
                cand("blade", "satian", "damage", 7.0)
            )
        )
        assertEquals(2, r.size)
        // 同组内按 effectId 字典序：damage < regen
        assertEquals("damage", r[0].effectId)
        assertEquals("regen", r[1].effectId)
    }

    @Test
    fun `same effect in different groups is not merged`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("a", "g1", "resist", 3.0),
                cand("b", "g2", "resist", 4.0)
            )
        )
        assertEquals(2, r.size)
        assertEquals(SelectedUniqueBonus("g1", "resist", "a", 3.0), r[0])
        assertEquals(SelectedUniqueBonus("g2", "resist", "b", 4.0), r[1])
    }

    @Test
    fun `same value picks smallest instance id`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("z1", "g", "e", 5.0),
                cand("a1", "g", "e", 5.0)
            )
        )
        assertEquals(1, r.size)
        assertEquals("a1", r[0].instanceId)
        assertEquals(5.0, r[0].value, d)
    }

    @Test
    fun `inactive excluded but validated`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("hi", "g", "e", 9.0, active = false),
                cand("lo", "g", "e", 3.0)
            )
        )
        assertEquals(1, r.size)
        assertEquals("lo", r[0].instanceId)
        assertEquals(3.0, r[0].value, d)

        // 全 inactive 的组不出现在结果里
        val none = UniqueBonusSelector.select(
            listOf(cand("hi", "g", "e", 9.0, active = false))
        )
        assertTrue(none.isEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("hi", "g", "e", -1.0, active = false)))
        }
    }

    @Test
    fun `reordered input is consistent`() {
        val candidates = listOf(
            cand("a", "satian", "regen", 5.0),
            cand("b", "satian", "regen", 8.0),
            cand("dual", "yasha", "attack_speed", 12.0),
            cand("dual", "satian", "damage", 7.0)
        )
        val base = UniqueBonusSelector.select(candidates)
        val reversed = UniqueBonusSelector.select(candidates.reversed())
        val shuffled = UniqueBonusSelector.select(candidates.shuffled(java.util.Random(5)))
        assertEquals(base, reversed)
        assertEquals(base, shuffled)
        assertEquals(
            listOf(
                SelectedUniqueBonus("satian", "damage", "dual", 7.0),
                SelectedUniqueBonus("satian", "regen", "b", 8.0),
                SelectedUniqueBonus("yasha", "attack_speed", "dual", 12.0)
            ),
            base
        )
    }

    @Test
    fun `zero value is legal and can win its group`() {
        val only = UniqueBonusSelector.select(listOf(cand("a", "g", "e", 0.0)))
        assertEquals(1, only.size)
        assertEquals(0.0, only[0].value, d)

        val higher = UniqueBonusSelector.select(
            listOf(cand("a", "g", "e", 0.0), cand("b", "g", "e", 3.0))
        )
        assertEquals(1, higher.size)
        assertEquals("b", higher[0].instanceId)
    }

    @Test
    fun `empty input returns empty`() {
        assertTrue(UniqueBonusSelector.select(emptyList()).isEmpty())
    }

    @Test
    fun `rejects illegal values and ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("a", "g", "e", Double.NaN)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("a", "g", "e", Double.POSITIVE_INFINITY)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("a", "g", "e", -1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("", "g", "e", 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("a", "", "e", 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("a", "g", "", 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(listOf(cand("   ", "g", "e", 1.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UniqueBonusSelector.select(
                listOf(
                    cand("a", "g", "e", 1.0),
                    cand("a", "g", "e", 2.0)
                )
            )
        }
    }

    @Test
    fun `same instance different keys is legal`() {
        val r = UniqueBonusSelector.select(
            listOf(
                cand("dual", "satian", "regen", 6.0),
                cand("dual", "yasha", "attack_speed", 12.0),
                cand("dual", "satian", "damage", 7.0)
            )
        )
        assertEquals(3, r.size)
    }

    @Test
    fun `input list is not mutated and output is independent`() {
        val candidates = mutableListOf(
            cand("a", "satian", "regen", 5.0),
            cand("b", "satian", "regen", 8.0)
        )
        val r = UniqueBonusSelector.select(candidates)
        candidates.add(cand("c", "satian", "regen", 99.0))
        candidates.clear()
        assertEquals(1, r.size)
        assertEquals("b", r[0].instanceId)
        assertEquals(8.0, r[0].value, d)
    }
}
