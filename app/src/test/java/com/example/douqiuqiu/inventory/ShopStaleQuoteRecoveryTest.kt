package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

/**
 * X03：商店陈旧报价恢复验收，对应 docs/tasks/0.1.4-external-handoff.md。
 *
 * 覆盖四条规则：旧 revision 拒绝且不扣钱/库存、失败结果携带可立即重读的新货架、
 * 刷新后新报价可买、同时请求只有一个成功、售罄同周期内不可恢复。
 * 只测业务层，不依赖 Android 与墙上时钟。
 */
class ShopStaleQuoteRecoveryTest {

    private val definitions = listOf(
        ItemDefinition("component", "组件", ItemKind.EQUIPMENT, 1, 1, price = 100, shopStock = 3)
    ) + (0..7).map { ItemDefinition("card$it", "卡$it", ItemKind.CARD, 1, 1, price = 100, shopStock = 3) }
    private val inventory = InventoryRules(definitions.associateBy { it.id })
    private val economy = HomeEconomyRules(inventory)

    private fun home() = economy.recover(InventoryState(), 1000)

    private fun InventoryState.componentStock() =
        progress.shopStock["component"] ?: progress.shop!!.offers.single { it.definitionId == "component" }.stock

    @Test fun `跨格自动刷新后旧报价被拒绝且不扣钱不扣库存`() {
        val s = home()
        val stale = s.progress.shop!!.revision
        val rejected = economy.buy(s, "component", "n", 7_200_000, stale)
        assertFalse(rejected.ok)
        // 原始快照分毫未动：金币、库存、批次号都与操作前一致
        assertEquals(s, rejected.state)
        assertEquals(1200, rejected.state.gold)
        assertEquals(3, rejected.state.componentStock())
        assertEquals(stale, rejected.state.progress.shop!!.revision)
        // 成功路径不得夹带任何待提交快照
        val ok = economy.buy(s, "component", "n", 1000, stale)
        assertTrue(ok.error ?: "ok", ok.ok)
        assertNull(ok.refreshed)
    }

    @Test fun `旧报价失败时refreshed携带可立即显示的新货架与倒计时状态`() {
        val s = home()
        val stale = s.progress.shop!!.revision
        val rejected = economy.buy(s, "component", "n", 7_200_000, stale)
        val fresh = rejected.refreshed!!
        // 新批次：版本号推进、库存重置、时间状态已跨格
        assertTrue("refreshed 必须是新批次", fresh.progress.shop!!.revision > stale)
        assertEquals(3, fresh.componentStock())
        assertEquals(1L, fresh.progress.shopCycle)
        // UI 拿着 refreshed 就能算倒计时，不需要再等一次恢复
        assertEquals(7_200_000L, economy.shopCountdown(fresh, 7_200_000))
        // refreshed 与原始 state 是不同快照，且 gold 未被扣
        assertEquals(1200, fresh.gold)
        assertNotSame(s, fresh)
    }

    @Test fun `刷新后的新报价可以按新批次购买`() {
        val s = home()
        val stale = s.progress.shop!!.revision
        economy.buy(s, "component", "n", 7_200_000, stale)
        val reopened = economy.recover(s, 7_200_000)
        val fresh = reopened.progress.shop!!.revision
        assertTrue(fresh > stale)
        val bought = economy.buy(reopened, "component", "n2", 7_200_000, fresh)
        assertTrue(bought.error ?: "ok", bought.ok)
        assertEquals(1110, bought.state.gold)
        assertEquals(2, bought.state.componentStock())
    }

    @Test fun `同一批次同时发起的两笔购买只有一笔成功`() {
        var s = home()
        repeat(2) { i -> s = economy.buy(s, "component", "a$i", 1000, s.progress.shop!!.revision).state }
        assertEquals(1, s.componentStock())
        val rev = s.progress.shop!!.revision
        val first = economy.buy(s, "component", "sim-1", 1000, rev)
        assertTrue(first.error ?: "ok", first.ok)
        val second = economy.buy(first.state, "component", "sim-2", 1000, rev)
        assertFalse(second.ok)
        assertEquals("本轮已售完", second.error)
        // 第二笔既不扣钱也不产生物品
        assertEquals(first.state, second.state)
        assertEquals(930, second.state.gold)
        assertTrue(second.state.items.none { it.instanceId == "sim-2" })
    }

    @Test fun `售罄项目在同一周期内不可恢复库存且付费刷新生成新批次`() {
        var s = home()
        repeat(3) { i -> s = economy.buy(s, "component", "k$i", 1000, s.progress.shop!!.revision).state }
        val rev = s.progress.shop!!.revision
        val soldOut = economy.buy(s, "component", "k4", 1000, rev)
        assertFalse(soldOut.ok)
        assertEquals("本轮已售完", soldOut.error)
        assertEquals(930, soldOut.state.gold)
        // 同周期重开不补货
        val reopened = economy.recover(s, 2000)
        assertEquals(0, reopened.componentStock())
        // 付费刷新换到新批次，但不推迟原自动刷新边界
        val refreshed = economy.refreshShop(s, 3_600_000)
        assertTrue(refreshed.error ?: "ok", refreshed.ok)
        assertTrue(refreshed.state.progress.shop!!.revision > rev)
        assertEquals(3, refreshed.state.componentStock())
        assertEquals(3_600_000L, economy.shopCountdown(refreshed.state, 3_600_000))
        // 旧报价即使面对新货架也不生效
        val staleBuy = economy.buy(refreshed.state, "component", "k5", 3_600_000, rev)
        assertFalse(staleBuy.ok)
        assertEquals(refreshed.state, staleBuy.state)
        // 新报价可买，且扣款正好是刷新费加标价
        val newRev = refreshed.state.progress.shop!!.revision
        val bought = economy.buy(refreshed.state, "component", "k6", 3_600_000, newRev)
        assertTrue(bought.error ?: "ok", bought.ok)
        val price = bought.state.progress.shop!!.offers.single { it.definitionId == "component" }.price
        assertEquals(refreshed.state.gold - price, bought.state.gold)
    }
}
