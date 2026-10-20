package com.example.douqiuqiu.loot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MaterialPityTracker 验收用例，对应 docs/tasks/07-material-pity-tracker.md。
 */
class MaterialPityTrackerTest {

    private fun policy(vararg pairs: Pair<String, Int>) =
        MaterialPityPolicy(linkedMapOf(*pairs))

    @Test
    fun `0到阈值逐次累计`() {
        val p = policy("a" to 4)
        var s = MaterialPityState()
        val expected = listOf(1, 2, 3)
        for (m in expected) {
            s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
            assertEquals(m, s.counters.getValue("a").misses)
            assertFalse(s.counters.getValue("a").pending)
        }
        // 第 4 次触发资格
        s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertEquals(4, s.counters.getValue("a").misses)
        assertTrue(s.counters.getValue("a").pending)
    }

    @Test
    fun `刚好第4和第6次生成资格`() {
        val p = policy("backpack" to 4, "safe" to 6)
        var s = MaterialPityState()
        repeat(3) { s = MaterialPityTracker.recordOutcome(s, p, setOf("backpack", "safe"), emptySet()) }
        assertFalse(s.counters.getValue("backpack").pending)
        s = MaterialPityTracker.recordOutcome(s, p, setOf("backpack", "safe"), emptySet())
        assertTrue(s.counters.getValue("backpack").pending)
        assertFalse(s.counters.getValue("safe").pending)
        repeat(2) { s = MaterialPityTracker.recordOutcome(s, p, setOf("safe"), emptySet()) }
        assertTrue(s.counters.getValue("safe").pending)
    }

    @Test
    fun `pending冻结不积累第二份资格`() {
        val p = policy("a" to 4)
        var s = MaterialPityState()
        repeat(4) { s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet()) }
        // 已 pending 再合格未获得：冻结在阈值
        s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertEquals(4, s.counters.getValue("a").misses)
        assertTrue(s.counters.getValue("a").pending)
        // 只应拿到一份资格
        val gen = MaterialPityTracker.consumeForGeneration(s, p, setOf("a"))
        assertEquals(listOf("a"), gen.guaranteedCategories)
    }

    @Test
    fun `成功带出清零`() {
        val p = policy("a" to 4)
        val s = MaterialPityState(mapOf("a" to MaterialPityCounter(3, false)))
        val next = MaterialPityTracker.recordOutcome(s, p, emptySet(), setOf("a"))
        assertEquals(MaterialPityCounter(0, false), next.counters.getValue("a"))
    }

    @Test
    fun `成功优先不先加一再重置`() {
        val p = policy("a" to 4)
        val s = MaterialPityState(mapOf("a" to MaterialPityCounter(2, false)))
        val next = MaterialPityTracker.recordOutcome(s, p, setOf("a"), setOf("a"))
        assertEquals(MaterialPityCounter(0, false), next.counters.getValue("a"))
    }

    @Test
    fun `不相关类别不变且不填满0项`() {
        val p = policy("a" to 4, "b" to 4)
        val s = MaterialPityState(mapOf("a" to MaterialPityCounter(2, false)))
        val next = MaterialPityTracker.recordOutcome(s, p, setOf("b"), emptySet())
        assertEquals(MaterialPityCounter(2, false), next.counters.getValue("a"))
        assertEquals(MaterialPityCounter(1, false), next.counters.getValue("b"))
        assertEquals(setOf("a", "b"), next.counters.keys)

        // 成功一个从未出现的类别：默认值不算改变，不写入
        val s2 = MaterialPityState(mapOf("a" to MaterialPityCounter(2, false)))
        val next2 = MaterialPityTracker.recordOutcome(s2, p, emptySet(), setOf("b"))
        assertEquals(setOf("a"), next2.counters.keys)
    }

    @Test
    fun `未匹配箱不消耗`() {
        val p = policy("a" to 2, "b" to 2)
        val s = MaterialPityState(mapOf("a" to MaterialPityCounter(2, true)))
        val gen = MaterialPityTracker.consumeForGeneration(s, p, setOf("b"))
        assertTrue(gen.guaranteedCategories.isEmpty())
        assertTrue(gen.state.counters.getValue("a").pending)
    }

    @Test
    fun `首次合格箱消耗且同批第二箱不重复`() {
        val p = policy("a" to 2)
        val s = MaterialPityState(mapOf("a" to MaterialPityCounter(2, true)))
        val first = MaterialPityTracker.consumeForGeneration(s, p, setOf("a"))
        assertEquals(listOf("a"), first.guaranteedCategories)
        assertEquals(MaterialPityCounter(0, false), first.state.counters.getValue("a"))
        // 用返回状态再生成一箱：不再领取
        val second = MaterialPityTracker.consumeForGeneration(first.state, p, setOf("a"))
        assertTrue(second.guaranteedCategories.isEmpty())
    }

    @Test
    fun `多类别独立累计`() {
        val p = policy("a" to 2, "b" to 3)
        var s = MaterialPityState()
        repeat(2) { s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet()) }
        assertTrue(s.counters.getValue("a").pending)
        assertFalse(s.counters.containsKey("b"))
    }

    @Test
    fun `类别列表按字典序稳定`() {
        val p = policy("z" to 1, "a" to 1, "m" to 1)
        val s = MaterialPityState(
            mapOf(
                "z" to MaterialPityCounter(1, true),
                "a" to MaterialPityCounter(1, true),
                "m" to MaterialPityCounter(1, true)
            )
        )
        val gen = MaterialPityTracker.consumeForGeneration(s, p, setOf("z", "a", "m"))
        assertEquals(listOf("a", "m", "z"), gen.guaranteedCategories)
        assertTrue(gen.state.counters.values.none { it.pending })
    }

    @Test
    fun `消耗后再累计重新获得资格`() {
        val p = policy("a" to 2)
        var s = MaterialPityState(mapOf("a" to MaterialPityCounter(2, true)))
        s = MaterialPityTracker.consumeForGeneration(s, p, setOf("a")).state
        s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertFalse(s.counters.getValue("a").pending)
        s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertTrue(s.counters.getValue("a").pending)
    }

    @Test
    fun `相同参数输入不被修改`() {
        val p = policy("a" to 4)
        val counters = linkedMapOf("a" to MaterialPityCounter(3, false))
        val s = MaterialPityState(counters)
        MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        MaterialPityTracker.consumeForGeneration(s, p, setOf("a"))
        assertEquals(MaterialPityCounter(3, false), counters["a"])
        assertEquals(1, counters.size)
    }

    @Test
    fun `阈值2验证没有硬编码4或6`() {
        val p = policy("a" to 2)
        var s = MaterialPityState()
        s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertFalse(s.counters.getValue("a").pending)
        s = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertTrue(s.counters.getValue("a").pending)
        assertEquals(listOf("a"), MaterialPityTracker.consumeForGeneration(s, p, setOf("a")).guaranteedCategories)
    }

    @Test
    fun `Int最大值阈值不因加一溢出`() {
        val p = policy("a" to Int.MAX_VALUE)
        // 已满且 pending：再合格未获得应冻结，不 +1
        val s = MaterialPityState(mapOf("a" to MaterialPityCounter(Int.MAX_VALUE, true)))
        val next = MaterialPityTracker.recordOutcome(s, p, setOf("a"), emptySet())
        assertEquals(Int.MAX_VALUE, next.counters.getValue("a").misses)
        assertTrue(next.counters.getValue("a").pending)
        assertEquals(
            listOf("a"),
            MaterialPityTracker.consumeForGeneration(next, p, setOf("a")).guaranteedCategories
        )
    }

    @Test
    fun `非法输入抛异常`() {
        fun expectIllegal(name: String, block: () -> Unit) {
            try {
                block()
                throw AssertionError("$name 未抛 IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assert(e.message!!.isNotEmpty())
            }
        }

        val ok = policy("a" to 4)
        expectIllegal("空policy") {
            MaterialPityTracker.recordOutcome(MaterialPityState(), MaterialPityPolicy(emptyMap()), emptySet(), emptySet())
        }
        expectIllegal("空白类别ID") {
            MaterialPityTracker.recordOutcome(MaterialPityState(), policy(" " to 4), emptySet(), emptySet())
        }
        expectIllegal("0阈值") {
            MaterialPityTracker.recordOutcome(MaterialPityState(), policy("a" to 0), emptySet(), emptySet())
        }
        expectIllegal("负阈值") {
            MaterialPityTracker.recordOutcome(MaterialPityState(), policy("a" to -1), emptySet(), emptySet())
        }
        expectIllegal("state未知类别") {
            MaterialPityTracker.recordOutcome(
                MaterialPityState(mapOf("x" to MaterialPityCounter(0, false))), ok, emptySet(), emptySet()
            )
        }
        expectIllegal("misses为负") {
            MaterialPityTracker.recordOutcome(
                MaterialPityState(mapOf("a" to MaterialPityCounter(-1, false))), ok, emptySet(), emptySet()
            )
        }
        expectIllegal("misses超阈值") {
            MaterialPityTracker.recordOutcome(
                MaterialPityState(mapOf("a" to MaterialPityCounter(5, false))), ok, emptySet(), emptySet()
            )
        }
        expectIllegal("pending不一致") {
            MaterialPityTracker.recordOutcome(
                MaterialPityState(mapOf("a" to MaterialPityCounter(2, true))), ok, emptySet(), emptySet()
            )
        }
        expectIllegal("eligibleClearMisses未知类别") {
            MaterialPityTracker.recordOutcome(MaterialPityState(), ok, setOf("x"), emptySet())
        }
        expectIllegal("successfullyBroughtOut未知类别") {
            MaterialPityTracker.recordOutcome(MaterialPityState(), ok, emptySet(), setOf("x"))
        }
        expectIllegal("eligibleCategories未知类别") {
            MaterialPityTracker.consumeForGeneration(MaterialPityState(), ok, setOf("x"))
        }
        expectIllegal("eligibleCategories空白类别") {
            MaterialPityTracker.consumeForGeneration(MaterialPityState(), ok, setOf(" "))
        }
    }
}
