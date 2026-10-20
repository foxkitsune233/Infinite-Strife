package com.example.douqiuqiu.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShopRefreshClock 验收用例，对应 docs/tasks/02-shop-refresh-clock.md。
 * 前 12 条用 intervalMs=1000 简化，最后补真实两小时参数例子。
 */
class ShopRefreshClockTest {

    private val p = ShopRefreshPolicy(1000L)
    private val twoHours = ShopRefreshPolicy(7_200_000L)

    // ---- 验收表（interval=1000） ----

    @Test
    fun `新建商店记录当前格`() {
        val s = ShopRefreshClock.initial(p, 1500)
        assertEquals(1L, s.lastAutomaticCycle)
        assertEquals(1500L, s.maxSeenAtMs)
    }

    @Test
    fun `尚未跨格返回NONE并刷新已见时间`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(1, 1500), p, 1800)
        assertEquals(ShopRefreshReason.NONE, r.reason)
        assertEquals(200L, r.nextAutomaticInMs)
        assertEquals(1L, r.state.lastAutomaticCycle)
        assertEquals(1800L, r.state.maxSeenAtMs)
    }

    @Test
    fun `恰好边界返回AUTOMATIC并推进编号`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(1, 1500), p, 2000)
        assertEquals(ShopRefreshReason.AUTOMATIC, r.reason)
        assertEquals(2L, r.state.lastAutomaticCycle)
        assertEquals(2000L, r.state.maxSeenAtMs)
        assertEquals(1000L, r.nextAutomaticInMs)
    }

    @Test
    fun `重复检查同一格不再刷新`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(2, 2000), p, 2000)
        assertEquals(ShopRefreshReason.NONE, r.reason)
        assertEquals(1000L, r.nextAutomaticInMs)
        assertEquals(2L, r.state.lastAutomaticCycle)
    }

    @Test
    fun `离线跨多格只刷新一次`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(1, 1500), p, 6200)
        assertEquals(ShopRefreshReason.AUTOMATIC, r.reason)
        assertEquals(6L, r.state.lastAutomaticCycle)
        assertEquals(800L, r.nextAutomaticInMs)
    }

    @Test
    fun `提前付费不改编号不推迟刷新`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(1, 1500), p, 1800, requestPaidRefresh = true)
        assertEquals(ShopRefreshReason.PAID, r.reason)
        assertEquals(1L, r.state.lastAutomaticCycle)
        assertEquals(200L, r.nextAutomaticInMs)
    }

    @Test
    fun `付费后到期仍按原格自动刷新`() {
        val paid = ShopRefreshClock.evaluate(ShopRefreshState(1, 1500), p, 1800, requestPaidRefresh = true)
        val r = ShopRefreshClock.evaluate(paid.state, p, 2000)
        assertEquals(ShopRefreshReason.AUTOMATIC, r.reason)
        assertEquals(2L, r.state.lastAutomaticCycle)
        assertEquals(1000L, r.nextAutomaticInMs)
    }

    @Test
    fun `到期同时请求付费时自动刷新优先`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(1, 1500), p, 2000, requestPaidRefresh = true)
        assertEquals(ShopRefreshReason.AUTOMATIC, r.reason)
        assertEquals(2L, r.state.lastAutomaticCycle)
        assertEquals(1000L, r.nextAutomaticInMs)
    }

    @Test
    fun `时间回拨不倒退已见时间不刷新旧格`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(2, 2500), p, 1800)
        assertEquals(ShopRefreshReason.NONE, r.reason)
        assertEquals(2500L, r.state.maxSeenAtMs)
        assertEquals(2L, r.state.lastAutomaticCycle)
        assertEquals(500L, r.nextAutomaticInMs)
    }

    @Test
    fun `回拨中付费仍按当前格`() {
        val r = ShopRefreshClock.evaluate(ShopRefreshState(2, 2500), p, 1800, requestPaidRefresh = true)
        assertEquals(ShopRefreshReason.PAID, r.reason)
        assertEquals(2L, r.state.lastAutomaticCycle)
        assertEquals(2500L, r.state.maxSeenAtMs)
        assertEquals(500L, r.nextAutomaticInMs)
    }

    @Test
    fun `大时间戳不溢出`() {
        val s = ShopRefreshClock.initial(p, Long.MAX_VALUE)
        val r = ShopRefreshClock.evaluate(s, p, Long.MAX_VALUE)
        assertEquals(ShopRefreshReason.NONE, r.reason)
        assertEquals(193L, r.nextAutomaticInMs)
    }

    @Test
    fun `非法输入抛异常`() {
        // intervalMs = 0
        try {
            ShopRefreshClock.initial(ShopRefreshPolicy(0), 1500)
            throw AssertionError("intervalMs=0 未抛异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("intervalMs"))
        }
        // 负时间戳
        try {
            ShopRefreshClock.initial(p, -1)
            throw AssertionError("负 nowMs 未抛异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("nowMs"))
        }
        // 负格编号
        try {
            ShopRefreshClock.evaluate(ShopRefreshState(-1, 1500), p, 1800)
            throw AssertionError("负 lastAutomaticCycle 未抛异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("lastAutomaticCycle"))
        }
        // 编号超过已见时间所在格：2500/1000=2，却记了 3
        try {
            ShopRefreshClock.evaluate(ShopRefreshState(3, 2500), p, 1800)
            throw AssertionError("编号越界未抛异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("lastAutomaticCycle"))
        }
    }

    // ---- 真实两小时参数：确认工具内部没有写死 1000 或两小时 ----

    @Test
    fun `真实两小时参数全流程`() {
        // now = 10_000_000ms，落在第 1 格（0 ~ 7_200_000 为第 0 格）
        val s = ShopRefreshClock.initial(twoHours, 10_000_000)
        assertEquals(1L, s.lastAutomaticCycle)

        // 第 1 格内请求付费：编号不动，倒计时不变
        val paid = ShopRefreshClock.evaluate(s, twoHours, 13_000_000, requestPaidRefresh = true)
        assertEquals(ShopRefreshReason.PAID, paid.reason)
        assertEquals(1L, paid.state.lastAutomaticCycle)
        assertEquals(1_400_000L, paid.nextAutomaticInMs)

        // 到点 14_400_000（= 2 × 7_200_000）：自动刷新，倒计时一个完整间隔
        val auto = ShopRefreshClock.evaluate(paid.state, twoHours, 14_400_000)
        assertEquals(ShopRefreshReason.AUTOMATIC, auto.reason)
        assertEquals(2L, auto.state.lastAutomaticCycle)
        assertEquals(7_200_000L, auto.nextAutomaticInMs)

        // 再离线跨过 5 个格：仍只刷新一次
        val offline = ShopRefreshClock.evaluate(auto.state, twoHours, 50_400_000)
        assertEquals(ShopRefreshReason.AUTOMATIC, offline.reason)
        assertEquals(7L, offline.state.lastAutomaticCycle)
        assertEquals(7_200_000L, offline.nextAutomaticInMs)
    }

    @Test
    fun `不修改传入状态对象`() {
        val input = ShopRefreshState(1, 1500)
        val r = ShopRefreshClock.evaluate(input, p, 6200)
        assertEquals(1L, input.lastAutomaticCycle)
        assertEquals(1500L, input.maxSeenAtMs)
        assertNotSame(input, r.state)
    }
}
