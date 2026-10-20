package com.example.douqiuqiu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldLedgerTest {

    private fun entry(
        id: String,
        kind: ShieldKind,
        amount: Double,
        remainingMs: Long? = null
    ) = ShieldEntry(id, kind, amount, remainingMs)

    private fun pool(vararg entries: ShieldEntry) = ShieldPool(entries.toList())

    // ---------- apply ----------

    @Test
    fun `异源直接相加`() {
        var p = pool(entry("s1", ShieldKind.UNIVERSAL, 30.0))
        p = ShieldLedger.apply(p, entry("s2", ShieldKind.UNIVERSAL, 40.0))
        assertEquals(70.0, ShieldLedger.total(p), 1e-9)
        assertEquals(2, p.entries.size)
    }

    @Test
    fun `同源取较高值且刷新时长不相加`() {
        var p = pool(entry("s1", ShieldKind.PHYSICAL, 50.0, 1_000))
        // 新值更低：取高 50，时长刷新为 5000
        p = ShieldLedger.apply(p, entry("s1", ShieldKind.PHYSICAL, 30.0, 5_000))
        assertEquals(50.0, p.entries[0].amount, 1e-9)
        assertEquals(5_000L, p.entries[0].remainingMs)
        // 新值更高：取高 80
        p = ShieldLedger.apply(p, entry("s1", ShieldKind.PHYSICAL, 80.0, 2_000))
        assertEquals(80.0, p.entries[0].amount, 1e-9)
        assertEquals(2_000L, p.entries[0].remainingMs)
    }

    @Test
    fun `同源刷新为永久时长`() {
        var p = pool(entry("s1", ShieldKind.UNIVERSAL, 10.0, 1_000))
        p = ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 60.0, null))
        assertEquals(60.0, p.entries[0].amount, 1e-9)
        assertEquals(null, p.entries[0].remainingMs)
    }

    @Test
    fun `ADD_CAPPED累计且只封顶本来源`() {
        var p = pool(
            entry("s1", ShieldKind.UNIVERSAL, 30.0),
            entry("s2", ShieldKind.UNIVERSAL, 20.0)
        )
        p = ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 40.0), ShieldRefreshMode.ADD_CAPPED, 50.0)
        assertEquals(50.0, p.entries.first { it.sourceId == "s1" }.amount, 1e-9)
        // 别的来源不受影响
        assertEquals(20.0, p.entries.first { it.sourceId == "s2" }.amount, 1e-9)
        assertEquals(70.0, ShieldLedger.total(p), 1e-9)
        // 未到 cap 时正常累计
        p = ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 10.0), ShieldRefreshMode.ADD_CAPPED, 100.0)
        assertEquals(60.0, p.entries.first { it.sourceId == "s1" }.amount, 1e-9)
    }

    @Test
    fun `amount为0是无操作`() {
        val p = pool(entry("s1", ShieldKind.UNIVERSAL, 30.0, 1_000))
        val same = ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 0.0, 9_999))
        assertEquals(30.0, same.entries[0].amount, 1e-9)
        assertEquals(1_000L, same.entries[0].remainingMs)
        val fresh = ShieldLedger.apply(p, entry("s9", ShieldKind.UNIVERSAL, 0.0))
        assertEquals(1, fresh.entries.size)
    }

    // ---------- absorb ----------

    @Test
    fun `物理先专用后通用`() {
        val p = pool(
            entry("phys", ShieldKind.PHYSICAL, 50.0),
            entry("univ", ShieldKind.UNIVERSAL, 30.0),
            entry("magic", ShieldKind.MAGICAL, 80.0)
        )
        val r = ShieldLedger.absorb(p, 60.0, ShieldDamageKind.PHYSICAL)
        assertEquals(60.0, r.absorbed, 1e-9)
        assertEquals(0.0, r.remainingDamage, 1e-9)
        // 魔法盾不参与物理吸收：80 原样 + 通用盾剩 20
        assertEquals(100.0, ShieldLedger.total(r.pool), 1e-9)
    }

    @Test
    fun `魔法先专用后通用`() {
        val p = pool(
            entry("magic", ShieldKind.MAGICAL, 20.0),
            entry("univ", ShieldKind.UNIVERSAL, 100.0),
            entry("phys", ShieldKind.PHYSICAL, 60.0)
        )
        val r = ShieldLedger.absorb(p, 50.0, ShieldDamageKind.MAGICAL)
        assertEquals(50.0, r.absorbed, 1e-9)
        // 专用 20 + 通用 30，物理盾不动
        assertEquals(130.0, ShieldLedger.total(r.pool), 1e-9)
    }

    @Test
    fun `纯粹伤害全穿`() {
        val p = pool(entry("univ", ShieldKind.UNIVERSAL, 100.0))
        val r = ShieldLedger.absorb(p, 50.0, ShieldDamageKind.PURE)
        assertEquals(0.0, r.absorbed, 1e-9)
        assertEquals(50.0, r.remainingDamage, 1e-9)
        assertEquals(100.0, ShieldLedger.total(r.pool), 1e-9)
    }

    @Test
    fun `专用打空后溢出到通用`() {
        val p = pool(
            entry("phys", ShieldKind.PHYSICAL, 10.0),
            entry("univ", ShieldKind.UNIVERSAL, 5.0)
        )
        val r = ShieldLedger.absorb(p, 20.0, ShieldDamageKind.PHYSICAL)
        assertEquals(15.0, r.absorbed, 1e-9)
        assertEquals(5.0, r.remainingDamage, 1e-9)
        assertTrue(r.pool.entries.isEmpty())
    }

    @Test
    fun `同类按最早到期优先null最后`() {
        val p = pool(
            entry("perm", ShieldKind.UNIVERSAL, 100.0),          // null 永久
            entry("late", ShieldKind.UNIVERSAL, 100.0, 5_000),
            entry("early", ShieldKind.UNIVERSAL, 100.0, 1_000)
        )
        val r = ShieldLedger.absorb(p, 150.0, ShieldDamageKind.PHYSICAL)
        // 先扣 early(100) 再扣 late(50)，永久盾不动
        val ids = r.pool.entries.map { it.sourceId }
        assertEquals(listOf("perm", "late"), ids)
        assertEquals(100.0, r.pool.entries.first { it.sourceId == "perm" }.amount, 1e-9)
        assertEquals(50.0, r.pool.entries.first { it.sourceId == "late" }.amount, 1e-9)
    }

    @Test
    fun `同到期按sourceId字典序且输出保持原池顺序`() {
        val p = pool(
            entry("b", ShieldKind.UNIVERSAL, 40.0, 1_000),
            entry("a", ShieldKind.UNIVERSAL, 40.0, 1_000),
            entry("c", ShieldKind.UNIVERSAL, 40.0, 1_000)
        )
        val r = ShieldLedger.absorb(p, 60.0, ShieldDamageKind.PHYSICAL)
        // 先扣 a(40) 再扣 b(20)；输出仍按原池相对顺序 b, c
        assertEquals(listOf("b", "c"), r.pool.entries.map { it.sourceId })
        assertEquals(20.0, r.pool.entries.first { it.sourceId == "b" }.amount, 1e-9)
        assertEquals(40.0, r.pool.entries.first { it.sourceId == "c" }.amount, 1e-9)
    }

    @Test
    fun `盾清零删除条目`() {
        val p = pool(
            entry("s1", ShieldKind.UNIVERSAL, 30.0),
            entry("s2", ShieldKind.UNIVERSAL, 20.0)
        )
        val r = ShieldLedger.absorb(p, 30.0, ShieldDamageKind.PHYSICAL)
        assertEquals(listOf("s2"), r.pool.entries.map { it.sourceId })
        // 全清空
        val r2 = ShieldLedger.absorb(r.pool, 20.0, ShieldDamageKind.PHYSICAL)
        assertTrue(r2.pool.entries.isEmpty())
    }

    @Test
    fun `零伤害不消耗`() {
        val p = pool(entry("s1", ShieldKind.UNIVERSAL, 30.0))
        val r = ShieldLedger.absorb(p, 0.0, ShieldDamageKind.PHYSICAL)
        assertEquals(0.0, r.absorbed, 1e-9)
        assertEquals(0.0, r.remainingDamage, 1e-9)
        assertEquals(30.0, ShieldLedger.total(r.pool), 1e-9)
    }

    @Test
    fun `吸收守恒且池减少量等于absorbed`() {
        val p = pool(
            entry("phys", ShieldKind.PHYSICAL, 45.5),
            entry("univ", ShieldKind.UNIVERSAL, 12.25)
        )
        val before = ShieldLedger.total(p)
        val r = ShieldLedger.absorb(p, 50.0, ShieldDamageKind.PHYSICAL)
        assertEquals(50.0, r.absorbed + r.remainingDamage, 1e-9)
        val after = ShieldLedger.total(r.pool)
        assertEquals(r.absorbed, before - after, 1e-9)
    }

    // ---------- elapse ----------

    @Test
    fun `elapse扣剩余并移除到期永久不变`() {
        val p = pool(
            entry("perm", ShieldKind.UNIVERSAL, 10.0, null),
            entry("a", ShieldKind.UNIVERSAL, 10.0, 1_000),
            entry("b", ShieldKind.UNIVERSAL, 10.0, 500)
        )
        val r = ShieldLedger.elapse(p, 600)
        val ids = r.entries.map { it.sourceId }
        assertEquals(listOf("perm", "a"), ids)
        assertEquals(400L, r.entries.first { it.sourceId == "a" }.remainingMs)
        // 永久条不变
        assertEquals(null, r.entries.first { it.sourceId == "perm" }.remainingMs)
    }

    @Test
    fun `elapse为0不变`() {
        val p = pool(entry("a", ShieldKind.UNIVERSAL, 10.0, 1_000))
        val r = ShieldLedger.elapse(p, 0)
        assertEquals(1_000L, r.entries[0].remainingMs)
        assertEquals(1, r.entries.size)
    }

    // ---------- 校验 ----------

    @Test
    fun `同源类型冲突抛错`() {
        val p = pool(entry("s1", ShieldKind.PHYSICAL, 10.0))
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.MAGICAL, 20.0))
        }
    }

    @Test
    fun `池内重复ID与非法条目抛错`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.absorb(pool(entry("s1", ShieldKind.UNIVERSAL, 10.0), entry("s1", ShieldKind.UNIVERSAL, 5.0)), 1.0, ShieldDamageKind.PHYSICAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.absorb(pool(entry(" ", ShieldKind.UNIVERSAL, 10.0)), 1.0, ShieldDamageKind.PHYSICAL)
        }
        // 已有条目 amount 必须 > 0
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.absorb(pool(entry("s1", ShieldKind.UNIVERSAL, 0.0)), 1.0, ShieldDamageKind.PHYSICAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.absorb(pool(entry("s1", ShieldKind.UNIVERSAL, -1.0)), 1.0, ShieldDamageKind.PHYSICAL)
        }
        // remainingMs 必须 null 或 > 0
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.absorb(pool(entry("s1", ShieldKind.UNIVERSAL, 10.0, 0)), 1.0, ShieldDamageKind.PHYSICAL)
        }
    }

    @Test
    fun `ADD_CAPPED缺cap或非法cap抛错`() {
        val p = pool(entry("s1", ShieldKind.UNIVERSAL, 10.0))
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 5.0), ShieldRefreshMode.ADD_CAPPED, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 5.0), ShieldRefreshMode.ADD_CAPPED, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 5.0), ShieldRefreshMode.ADD_CAPPED, Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 5.0), ShieldRefreshMode.ADD_CAPPED, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `MAX模式传cap抛错`() {
        val p = pool(entry("s1", ShieldKind.UNIVERSAL, 10.0))
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, 5.0), ShieldRefreshMode.MAX_REMAINING, 100.0)
        }
    }

    @Test
    fun `NaN与Infinity伤害抛错`() {
        val p = pool(entry("s1", ShieldKind.UNIVERSAL, 10.0))
        assertThrows(IllegalArgumentException::class.java) { ShieldLedger.absorb(p, Double.NaN, ShieldDamageKind.PHYSICAL) }
        assertThrows(IllegalArgumentException::class.java) { ShieldLedger.absorb(p, Double.POSITIVE_INFINITY, ShieldDamageKind.PHYSICAL) }
        assertThrows(IllegalArgumentException::class.java) { ShieldLedger.absorb(p, -1.0, ShieldDamageKind.PHYSICAL) }
        assertThrows(IllegalArgumentException::class.java) { ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { ShieldLedger.elapse(p, -1) }
    }

    @Test
    fun `ADD_CAPPED累计溢出抛错`() {
        val p = pool(entry("s1", ShieldKind.UNIVERSAL, Double.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) {
            ShieldLedger.apply(p, entry("s1", ShieldKind.UNIVERSAL, Double.MAX_VALUE), ShieldRefreshMode.ADD_CAPPED, Double.MAX_VALUE)
        }
    }

    @Test
    fun `输入隔离`() {
        val input = ShieldPool(
            arrayListOf(entry("s1", ShieldKind.UNIVERSAL, 30.0, 1_000), entry("s2", ShieldKind.PHYSICAL, 20.0))
        )
        val absorbed = ShieldLedger.absorb(input, 10.0, ShieldDamageKind.PHYSICAL)
        val elapsed = ShieldLedger.elapse(input, 500)
        val applied = ShieldLedger.apply(input, entry("s1", ShieldKind.UNIVERSAL, 40.0))

        // 输入未被修改
        assertEquals(30.0, input.entries[0].amount, 1e-9)
        assertEquals(1_000L, input.entries[0].remainingMs)
        // 返回均为独立快照
        assertNotSame(input.entries, absorbed.pool.entries)
        assertNotSame(input.entries, elapsed.entries)
        assertNotSame(input.entries, applied.entries)
        // 各自的数值符合预期：物理 10 只从 PHYSICAL 的 s2 扣，UNIVERSAL 的 s1 不动
        assertEquals(30.0, absorbed.pool.entries.first { it.sourceId == "s1" }.amount, 1e-9)
        assertEquals(10.0, absorbed.pool.entries.first { it.sourceId == "s2" }.amount, 1e-9)
        assertEquals(500L, elapsed.entries.first { it.sourceId == "s1" }.remainingMs)
        assertEquals(40.0, applied.entries.first { it.sourceId == "s1" }.amount, 1e-9)
    }
}
