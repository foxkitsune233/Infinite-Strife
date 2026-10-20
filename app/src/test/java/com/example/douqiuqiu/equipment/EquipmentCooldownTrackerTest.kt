package com.example.douqiuqiu.equipment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentCooldownTrackerTest {

    private fun state(vararg pairs: Pair<String, Long>, last: Int = 0) =
        EquipmentCooldownState(pairs.toMap(), last)

    @Test
    fun `就绪时启动并设置正冷却`() {
        val r = EquipmentCooldownTracker.tryStart(state(), "blink", 45_000)
        assertTrue(r.started)
        assertEquals(45_000L, r.state.remainingByGroup["blink"])
        assertEquals(0, r.state.lastPreparedBattle)
    }

    @Test
    fun `冷却中启动失败且不覆盖剩余`() {
        val s = state("blink" to 20_000L)
        val r = EquipmentCooldownTracker.tryStart(s, "blink", 45_000)
        assertFalse(r.started)
        assertEquals(20_000L, r.state.remainingByGroup["blink"])
    }

    @Test
    fun `相同groupId共享冷却异组独立`() {
        val started = EquipmentCooldownTracker.tryStart(state(), "blink", 30_000)
        assertTrue(started.started)
        // 同组：仍在冷却，第二次启动失败
        val again = EquipmentCooldownTracker.tryStart(started.state, "blink", 30_000)
        assertFalse(again.started)
        // 异组：独立就绪
        val other = EquipmentCooldownTracker.tryStart(started.state, "force", 30_000)
        assertTrue(other.started)
        assertEquals(2, other.state.remainingByGroup.size)
    }

    @Test
    fun `elapse部分扣减并保留归零项`() {
        val s = EquipmentCooldownTracker.elapse(state("a" to 45_000L, "b" to 10_000L), 15_000)
        assertEquals(30_000L, s.remainingByGroup["a"])
        assertEquals(0L, s.remainingByGroup["b"])
        assertEquals(2, s.remainingByGroup.size)
    }

    @Test
    fun `elapse超过则归零`() {
        val s = EquipmentCooldownTracker.elapse(state("a" to 10_000L), 30_000)
        assertEquals(0L, s.remainingByGroup["a"])
    }

    @Test
    fun `elapse为0不变且不新增缺失组`() {
        val s = EquipmentCooldownTracker.elapse(state("a" to 10_000L), 0)
        assertEquals(10_000L, s.remainingByGroup["a"])
        assertEquals(1, s.remainingByGroup.size)
    }

    @Test
    fun `跨战抵扣45秒到15秒`() {
        val s = EquipmentCooldownTracker.prepareBattle(state("a" to 45_000L), battleIndex = 1)
        assertEquals(15_000L, s.remainingByGroup["a"])
        assertEquals(1, s.lastPreparedBattle)
    }

    @Test
    fun `重复prepare同场号不重复抵扣`() {
        val s1 = EquipmentCooldownTracker.prepareBattle(state("a" to 45_000L), 1)
        val s2 = EquipmentCooldownTracker.prepareBattle(s1, 1)
        val s3 = EquipmentCooldownTracker.prepareBattle(s2, 1)
        assertEquals(15_000L, s3.remainingByGroup["a"])
        assertEquals(1, s3.lastPreparedBattle)
    }

    @Test
    fun `下一场再抵扣一次`() {
        val s1 = EquipmentCooldownTracker.prepareBattle(state("a" to 45_000L), 1)
        val s2 = EquipmentCooldownTracker.prepareBattle(s1, 2)
        assertEquals(0L, s2.remainingByGroup["a"])
        assertEquals(2, s2.lastPreparedBattle)
    }

    @Test
    fun `recovery为0不抵扣`() {
        val s = EquipmentCooldownTracker.prepareBattle(state("a" to 45_000L), 1, recoveryMs = 0)
        assertEquals(45_000L, s.remainingByGroup["a"])
        assertEquals(1, s.lastPreparedBattle)
    }

    @Test
    fun `新挑战用空状态从头开始`() {
        val fresh = EquipmentCooldownTracker.prepareBattle(EquipmentCooldownState(), 1)
        assertTrue(fresh.remainingByGroup.isEmpty())
        assertEquals(1, fresh.lastPreparedBattle)
    }

    @Test
    fun `Long最大值边界不溢出`() {
        val started = EquipmentCooldownTracker.tryStart(state(), "a", Long.MAX_VALUE)
        assertTrue(started.started)
        assertFalse(EquipmentCooldownTracker.tryStart(started.state, "a", 1).started)
        // 扣到 0
        val zeroed = EquipmentCooldownTracker.elapse(started.state, Long.MAX_VALUE)
        assertEquals(0L, zeroed.remainingByGroup["a"])
        // 只扣 1ms 也不能溢出成负数
        val partial = EquipmentCooldownTracker.elapse(state("a" to Long.MAX_VALUE), 1L)
        assertEquals(Long.MAX_VALUE - 1, partial.remainingByGroup["a"])
    }

    @Test
    fun `Int最大值场号边界`() {
        val nearTop = state("a" to 45_000L, last = Int.MAX_VALUE - 1)
        val topped = EquipmentCooldownTracker.prepareBattle(nearTop, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, topped.lastPreparedBattle)
        assertEquals(15_000L, topped.remainingByGroup["a"])
        // 到顶后同场号仍合法（不溢出成负数放行倒退）
        val same = EquipmentCooldownTracker.prepareBattle(topped, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, same.lastPreparedBattle)
        assertEquals(15_000L, same.remainingByGroup["a"])
        // 到顶后传更大值已不可能（Int 上限），倒退必须拒绝
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentCooldownTracker.prepareBattle(topped, Int.MAX_VALUE - 1)
        }
    }

    @Test
    fun `倒退与跳号均拒绝`() {
        val s = state("a" to 45_000L, last = 3)
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.prepareBattle(s, 2) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.prepareBattle(s, 5) }
        // 新状态（last=0）必须从 1 开始，直接传 2 视为跳号
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.prepareBattle(state(), 2) }
    }

    @Test
    fun `负值与非正参数抛错`() {
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.elapse(state(), -1) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.tryStart(state(), "a", 0) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.tryStart(state(), "a", -5) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.tryStart(state(), "  ", 5) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.prepareBattle(state(), 0) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.prepareBattle(state(), -1) }
        assertThrows(IllegalArgumentException::class.java) {
            EquipmentCooldownTracker.prepareBattle(state(), 1, recoveryMs = -1)
        }
        // 无关组的负数剩余同样非法
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.elapse(state("x" to -1L), 5) }
        // 空白组名 / 负场号记录
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.tryStart(state(" " to 5L), "a", 5) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentCooldownTracker.elapse(state(last = -1), 5) }
    }

    @Test
    fun `输入与结果隔离`() {
        val input = EquipmentCooldownState(mapOf("a" to 45_000L), 1)
        val prepared = EquipmentCooldownTracker.prepareBattle(input, 2)
        val elapsed = EquipmentCooldownTracker.elapse(input, 10_000)
        val started = EquipmentCooldownTracker.tryStart(input, "a", 30_000)

        // 输入未被修改
        assertEquals(45_000L, input.remainingByGroup["a"])
        assertEquals(1, input.lastPreparedBattle)
        // 各返回都是独立快照
        assertNotSame(input.remainingByGroup, prepared.remainingByGroup)
        assertNotSame(input.remainingByGroup, elapsed.remainingByGroup)
        assertNotSame(input.remainingByGroup, started.state.remainingByGroup)
        assertEquals(15_000L, prepared.remainingByGroup["a"])
        assertEquals(35_000L, elapsed.remainingByGroup["a"])
        assertFalse(started.started)
    }
}
