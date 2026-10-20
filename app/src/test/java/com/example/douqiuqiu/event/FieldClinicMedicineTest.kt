package com.example.douqiuqiu.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 复验：28 号把「临时医棚 · 用一份药品强化治疗 接受奶酪」记为 bug。
 * 源码确认 cheese 在 JourneyEvents.medicines 集合内、被归类为 EventItemType.MEDICINE，
 * 因此它是合法药品，接受奶酪是预期行为；真正要守的边界是「非药品（如 iron_branch）必须被拒」。
 *
 * 这里直接走公开 API EventCatalog.evaluate，不依赖完整 JourneyState。
 */
class FieldClinicMedicineTest {

    private val clinic = EventCatalog.fieldClinic
    private val instance = EventCatalog.createInstance(
        nodeId = "run-p0:event:3", kind = EventKind.FIELD_CLINIC, seed = 12345, priceGold = 20
    )

    private fun ctxWith(vararg infos: EventItemInfo) = EventContext(
        runGold = 999, walletGold = 339, clears = 2, skillSlots = 8,
        itemInstanceIds = infos.map { it.instanceId }.toSet(),
        items = infos.associateBy { it.instanceId }
    )

    @Test
    fun cheeseIsAValidMedicineForClinicSupply() {
        val cheese = EventItemInfo("i-cheese", "cheese", EventItemType.MEDICINE)
        val view = EventCatalog.evaluate(clinic, instance, ctxWith(cheese),
            EventItemSelection("i-cheese", false)).first { it.option.id == "clinic.supply" }
        assertTrue("奶酪属药品，clinic.supply 应判为可用", view.enabled)
        assertEquals(null, view.blockedReason)
    }

    @Test
    fun nonMedicineItemIsRejectedByClinicSupply() {
        val branch = EventItemInfo("i-branch", "iron_branch", EventItemType.MATERIAL)
        val view = EventCatalog.evaluate(clinic, instance, ctxWith(branch),
            EventItemSelection("i-branch", false)).first { it.option.id == "clinic.supply" }
        assertFalse("iron_branch 非药品，clinic.supply 必须被拦截", view.enabled)
        assertEquals(EventBlockReason.ITEM_TYPE_MISMATCH, view.blockedReason)
    }
}
