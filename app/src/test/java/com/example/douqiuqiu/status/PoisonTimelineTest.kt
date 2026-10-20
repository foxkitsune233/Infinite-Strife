package com.example.douqiuqiu.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PoisonTimelineTest {

    private fun layer(
        id: String,
        source: String,
        dps: Double,
        remainingMs: Long,
        cls: DispelClass = DispelClass.BASIC
    ) = PoisonLayer(id, source, dps, remainingMs, cls)

    private fun pool(vararg layers: PoisonLayer) = PoisonPool(layers.toList())

    @Test
    fun `整秒推进按DPS积分`() {
        val p = pool(layer("i1", "venom", 10.0, 1_000))
        val r = PoisonTimeline.advance(p, 1_000)
        assertEquals(10.0, r.damage.single().amount, 1e-9)
        assertTrue(r.pool.layers.isEmpty())
    }

    @Test
    fun `不足一秒不丢失伤害`() {
        val p = pool(layer("i1", "venom", 10.0, 1_000))
        val r = PoisonTimeline.advance(p, 100)
        assertEquals(1.0, r.damage.single().amount, 1e-9)
        assertEquals(900L, r.pool.layers.single().remainingMs)
    }

    @Test
    fun `3DPS持续3500ms总伤害10点5`() {
        val p = pool(layer("i1", "venom", 3.0, 3_500))
        val r = PoisonTimeline.advance(p, 3_500)
        assertEquals(10.5, r.damage.single().amount, 1e-9)
        assertTrue(r.pool.layers.isEmpty())
    }

    @Test
    fun `大步长越过到期不多打伤害`() {
        val p = pool(layer("i1", "venom", 5.0, 1_000))
        val r = PoisonTimeline.advance(p, 100_000)
        assertEquals(5.0, r.damage.single().amount, 1e-9)
        assertTrue(r.pool.layers.isEmpty())
    }

    @Test
    fun `同源多层错峰到期且不合并`() {
        val p = pool(
            layer("i1", "venom", 5.0, 1_000),
            layer("i2", "venom", 5.0, 3_000)
        )
        val r = PoisonTimeline.advance(p, 2_000)
        // 一层一条，按输入顺序，不按来源合并
        assertEquals(listOf("i1", "i2"), r.damage.map { it.instanceId })
        assertEquals(5.0, r.damage[0].amount, 1e-9)
        assertEquals(10.0, r.damage[1].amount, 1e-9)
        assertEquals(listOf("i2"), r.pool.layers.map { it.instanceId })
        assertEquals(1_000L, r.pool.layers.single().remainingMs)
    }

    @Test
    fun `不同来源各自结算`() {
        val p = pool(
            layer("i1", "venom", 4.0, 1_000),
            layer("i2", "p4", 6.0, 1_000)
        )
        val r = PoisonTimeline.advance(p, 500)
        assertEquals(listOf("venom", "p4"), r.damage.map { it.sourceId })
        assertEquals(2.0, r.damage[0].amount, 1e-9)
        assertEquals(3.0, r.damage[1].amount, 1e-9)
    }

    @Test
    fun `零DPS不出伤害但保留层`() {
        val p = pool(layer("i1", "venom", 0.0, 1_000))
        val r = PoisonTimeline.advance(p, 1_000)
        assertTrue(r.damage.isEmpty())
        assertTrue(r.pool.layers.isEmpty()) // 时长照常到期
    }

    @Test
    fun `零推进池等值伤害为空`() {
        val p = pool(layer("i1", "venom", 5.0, 1_000))
        val r = PoisonTimeline.advance(p, 0)
        assertEquals(p, r.pool)
        assertTrue(r.damage.isEmpty())
    }

    @Test
    fun `一次推进与多次分段总伤害一致`() {
        val once = PoisonTimeline.advance(pool(layer("i1", "venom", 3.0, 3_500)), 3_500)
        var acc = 0.0
        var cur = pool(layer("i1", "venom", 3.0, 3_500))
        for (step in listOf(1_000L, 1_000L, 1_000L, 500L)) {
            val r = PoisonTimeline.advance(cur, step)
            acc += r.damage.sumOf { it.amount }
            cur = r.pool
        }
        assertEquals(once.damage.sumOf { it.amount }, acc, 1e-9)
        assertTrue(cur.layers.isEmpty())
    }

    @Test
    fun `驱散标记原样保留`() {
        val p = pool(
            layer("i1", "venom", 5.0, 3_000, DispelClass.STRONG_ONLY),
            layer("i2", "viper", 5.0, 3_000, DispelClass.UNDISPELLABLE)
        )
        val r = PoisonTimeline.advance(p, 1_000)
        assertEquals(DispelClass.STRONG_ONLY, r.pool.layers[0].dispelClass)
        assertEquals(DispelClass.UNDISPELLABLE, r.pool.layers[1].dispelClass)
    }

    @Test
    fun `add追加独立层且同源不合并`() {
        var p = PoisonTimeline.add(PoisonPool(), layer("i1", "venom", 5.0, 1_000))
        p = PoisonTimeline.add(p, layer("i2", "venom", 5.0, 2_000))
        assertEquals(listOf("i1", "i2"), p.layers.map { it.instanceId })
        assertEquals(1_000L, p.layers[0].remainingMs)
        assertEquals(2_000L, p.layers[1].remainingMs)
    }

    @Test
    fun `重复与空白ID抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            PoisonTimeline.add(PoisonPool(), layer("  ", "venom", 1.0, 1_000))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoisonTimeline.add(PoisonPool(), layer("i1", " ", 1.0, 1_000))
        }
        // 池内重复
        assertThrows(IllegalArgumentException::class.java) {
            PoisonTimeline.advance(pool(layer("i1", "a", 1.0, 1_000), layer("i1", "b", 1.0, 1_000)), 100)
        }
        // add 与池中重复
        assertThrows(IllegalArgumentException::class.java) {
            PoisonTimeline.add(pool(layer("i1", "a", 1.0, 1_000)), layer("i1", "b", 1.0, 1_000))
        }
    }

    @Test
    fun `时长与DPS非法抛错`() {
        assertThrows(IllegalArgumentException::class.java) { PoisonTimeline.add(PoisonPool(), layer("i1", "a", 1.0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { PoisonTimeline.add(PoisonPool(), layer("i1", "a", 1.0, -5)) }
        assertThrows(IllegalArgumentException::class.java) { PoisonTimeline.add(PoisonPool(), layer("i1", "a", -1.0, 1_000)) }
        assertThrows(IllegalArgumentException::class.java) { PoisonTimeline.add(PoisonPool(), layer("i1", "a", Double.NaN, 1_000)) }
        assertThrows(IllegalArgumentException::class.java) { PoisonTimeline.add(PoisonPool(), layer("i1", "a", Double.POSITIVE_INFINITY, 1_000)) }
        assertThrows(IllegalArgumentException::class.java) { PoisonTimeline.advance(pool(layer("i1", "a", 1.0, 1_000)), -1) }
        // 池中已有非法层也会在 advance 时被校验
        assertThrows(IllegalArgumentException::class.java) {
            PoisonTimeline.advance(PoisonPool(listOf(PoisonLayer("i1", "a", 1.0, -1))), 100)
        }
    }

    @Test
    fun `输出溢出抛错`() {
        // 1e308 DPS × 2 秒 = Infinity
        assertThrows(IllegalArgumentException::class.java) {
            PoisonTimeline.advance(pool(layer("i1", "a", 1e308, 2_000)), 2_000)
        }
    }

    @Test
    fun `Long最大值推进安全`() {
        val p = pool(layer("i1", "a", 5.0, 1_000))
        val r = PoisonTimeline.advance(p, Long.MAX_VALUE)
        assertEquals(5.0, r.damage.single().amount, 1e-9)
        assertTrue(r.pool.layers.isEmpty())
    }

    @Test
    fun `输入隔离`() {
        val inputLayers = arrayListOf(layer("i1", "a", 5.0, 1_000))
        val input = PoisonPool(inputLayers)
        val r = PoisonTimeline.advance(input, 500)
        // 输入层未被修改
        assertEquals(1_000L, inputLayers[0].remainingMs)
        assertNotSame(inputLayers, r.pool.layers)
        // 后续再推进原池不受上次结果影响：仍是 500ms × 5DPS = 2.5
        val r2 = PoisonTimeline.advance(input, 500)
        assertEquals(2.5, r2.damage.single().amount, 1e-9)
        assertEquals(500L, r2.pool.layers.single().remainingMs)
    }
}
