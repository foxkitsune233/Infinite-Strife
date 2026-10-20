package com.example.douqiuqiu.event

import org.junit.Assert.*
import org.junit.Test

/**
 * H08 静态复核补测（R01 / R02 / R03 / R06）。
 *
 * 覆盖复核文档 `docs/testing/2026-10-17-H03-H08-review.md` 的 HR01～HR08、HR13～HR15。
 * 断言只针对模型契约，不假设具体掉落数值。
 */
class EventCatalogReviewTest {

    // ---------------- 夹具 ----------------

    private fun ctxOf(
        runGold: Int = 0,
        walletGold: Int = 0,
        backpack: EventContainerSpace = EventContainerSpace(4, 2, 8),
        safe: EventContainerSpace = EventContainerSpace(2, 2, 4),
        items: Map<String, EventItemInfo> = emptyMap(),
        safeIds: Set<String> = items.filter { it.value.container == EventItemContainer.SAFE }.keys
    ) = EventContext(
        runGold = runGold, walletGold = walletGold, clears = 3, skillSlots = 4,
        backpack = backpack, safe = safe,
        itemInstanceIds = items.keys, safeInstanceIds = safeIds, items = items
    )

    private fun info(
        instanceId: String,
        definitionId: String,
        type: EventItemType = EventItemType.OTHER,
        container: EventItemContainer = EventItemContainer.BACKPACK
    ) = EventItemInfo(instanceId, definitionId, type, container)

    private fun view(
        definition: EventDefinition,
        instance: NodeEventInstance,
        context: EventContext,
        optionId: String,
        selection: EventItemSelection? = null
    ): EventOptionView = EventCatalog.evaluate(definition, instance, context, selection)
        .single { it.option.id == optionId }

    private fun outcome(
        nodeId: String,
        optionId: String,
        consume: List<String> = emptyList(),
        possibleDeath: Boolean = false
    ) = EventOutcome(
        nodeId = nodeId, optionId = optionId,
        consumeInstanceIds = consume, possibleDeath = possibleDeath
    )

    // ---------------- HR01 交物必须明确选择实例 ----------------

    @Test fun hr01_standardItemOptionRequiresAnExplicitSelection() {
        val instance = EventCatalog.createInstance(
            "node-9", EventKind.TECH_TERMINAL, 1729L, requiredItemDefinitionId = "essence"
        )
        val empty = ctxOf()

        // 求值阶段：未选择 → 明确报 ITEM_CHOICE_REQUIRED
        val v = view(EventCatalog.techTerminal, instance, empty, "terminal.convert")
        assertFalse(v.enabled)
        assertEquals(EventBlockReason.ITEM_CHOICE_REQUIRED, v.blockedReason)

        // 结算阶段同样拒绝：实例未结算、无结果、零消费
        val r = EventCatalog.resolve(
            EventCatalog.techTerminal, instance, "terminal.convert", empty,
            outcome("node-9", "terminal.convert")
        )
        assertFalse(r.ok)
        assertEquals(EventBlockReason.ITEM_CHOICE_REQUIRED, r.blockReason)
        assertNull(r.outcome)
        assertFalse(r.instance.resolved)
    }

    // ---------------- HR02 药品类型校验 ----------------

    @Test fun hr02_clinicSupplyRejectsMissingAndNonMedicineInstances() {
        val instance = EventCatalog.createInstance("node-8", EventKind.FIELD_CLINIC, 1729L)
        val context = ctxOf(items = mapOf("it-1" to info("it-1", "component", EventItemType.MATERIAL)))

        // 实例不存在
        assertEquals(
            EventBlockReason.ITEM_INSTANCE_MISSING,
            view(EventCatalog.fieldClinic, instance, context, "clinic.supply",
                EventItemSelection("it-9")).blockedReason
        )
        // 存在但不是药品
        assertEquals(
            EventBlockReason.ITEM_TYPE_MISMATCH,
            view(EventCatalog.fieldClinic, instance, context, "clinic.supply",
                EventItemSelection("it-1")).blockedReason
        )

        // 结算同样拒绝：零消费 / 零治疗
        val r = EventCatalog.resolve(
            EventCatalog.fieldClinic, instance, "clinic.supply", context,
            outcome("node-8", "clinic.supply", consume = listOf("it-1")),
            EventItemSelection("it-1")
        )
        assertFalse(r.ok)
        assertEquals(EventBlockReason.ITEM_TYPE_MISMATCH, r.blockReason)
        assertNull(r.outcome)
        assertFalse(r.instance.resolved)
    }

    @Test fun hr02b_clinicSupplyAcceptsARealMedicine() {
        val instance = EventCatalog.createInstance("node-8", EventKind.FIELD_CLINIC, 1729L)
        val context = ctxOf(items = mapOf("it-5" to info("it-5", "potion", EventItemType.MEDICINE)))
        val selection = EventItemSelection("it-5")

        assertTrue(
            view(EventCatalog.fieldClinic, instance, context, "clinic.supply", selection).enabled
        )
        val r = EventCatalog.resolve(
            EventCatalog.fieldClinic, instance, "clinic.supply", context,
            outcome("node-8", "clinic.supply", consume = listOf("it-5")), selection
        )
        assertTrue(r.ok)
        assertEquals(listOf("it-5"), r.outcome!!.consumeInstanceIds)
    }

    // ---------------- HR03 安全箱授权 ----------------

    @Test fun hr03_blockerCannotTakeSafeItemWithoutExplicitConsent() {
        val instance = EventCatalog.createInstance("node-4", EventKind.BLOCKER, 1729L)
        val context = ctxOf(items = mapOf("safe-1" to info("safe-1", "blade", EventItemType.EQUIPMENT, EventItemContainer.SAFE)))

        // 未授权：不得消费
        assertEquals(
            EventBlockReason.SAFE_CONTAINER_NOT_AUTHORIZED,
            view(EventCatalog.blocker, instance, context, "blocker.item",
                EventItemSelection("safe-1")).blockedReason
        )
        val denied = EventCatalog.resolve(
            EventCatalog.blocker, instance, "blocker.item", context,
            outcome("node-4", "blocker.item", consume = listOf("safe-1")),
            EventItemSelection("safe-1")
        )
        assertFalse(denied.ok)
        assertEquals(EventBlockReason.SAFE_CONTAINER_NOT_AUTHORIZED, denied.blockReason)
        assertNull(denied.outcome)
        assertFalse(denied.instance.resolved)

        // 显式授权 + 条件满足：才可交付
        val allowed = EventCatalog.resolve(
            EventCatalog.blocker, instance, "blocker.item", context,
            outcome("node-4", "blocker.item", consume = listOf("safe-1")),
            EventItemSelection("safe-1", allowSafeContainer = true)
        )
        assertTrue(allowed.ok)
        assertEquals("blocker.item", allowed.instance.resolvedOptionId)
    }

    // ---------------- HR04 / HR05 定义精确匹配，不做前缀推断 ----------------

    @Test fun hr04_terminalMatchesByRealDefinitionId() {
        val instance = EventCatalog.createInstance(
            "node-9", EventKind.TECH_TERMINAL, 1729L, requiredItemDefinitionId = "essence"
        )
        // 真实精华实例 ID 并不以 "essence" 开头
        val context = ctxOf(items = mapOf("run-1:event:2" to info("run-1:event:2", "essence", EventItemType.MATERIAL)))
        val selection = EventItemSelection("run-1:event:2")

        assertTrue(
            view(EventCatalog.techTerminal, instance, context, "terminal.convert", selection).enabled
        )
        val r = EventCatalog.resolve(
            EventCatalog.techTerminal, instance, "terminal.convert", context,
            outcome("node-9", "terminal.convert", consume = listOf("run-1:event:2")), selection
        )
        assertTrue(r.ok)
        assertEquals(listOf("run-1:event:2"), r.outcome!!.consumeInstanceIds)
    }

    @Test fun hr05_instanceNamedLikeTheDefinitionIsStillRejected() {
        val instance = EventCatalog.createInstance(
            "node-9", EventKind.TECH_TERMINAL, 1729L, requiredItemDefinitionId = "essence"
        )
        // 名字像 essence，真实定义却是 junk → 前缀推断会误判，精确匹配必须拒绝
        val context = ctxOf(items = mapOf("essence-fake" to info("essence-fake", "junk")))
        val selection = EventItemSelection("essence-fake")

        assertEquals(
            EventBlockReason.ITEM_TYPE_MISMATCH,
            view(EventCatalog.techTerminal, instance, context, "terminal.convert", selection).blockedReason
        )
        val r = EventCatalog.resolve(
            EventCatalog.techTerminal, instance, "terminal.convert", context,
            outcome("node-9", "terminal.convert", consume = listOf("essence-fake")), selection
        )
        assertFalse(r.ok)
        assertFalse(r.instance.resolved)
    }

    @Test fun hr05b_definitionConditionIsNotPrefixMatched() {
        val instance = EventCatalog.createInstance(
            "node-9", EventKind.TECH_TERMINAL, 1729L, requiredItemDefinitionId = "essence"
        )
        val condition = EventCondition(EventConditionKind.HAS_ITEM_DEFINITION)

        // 真精华：通过
        val real = ctxOf(items = mapOf("run-1:event:2" to info("run-1:event:2", "essence")))
        assertTrue(view(EventCatalog.techTerminal, instance, real, "terminal.leave").enabled)
        assertTrue(
            EventCatalog.evaluate(
                EventDefinition(
                    EventKind.TECH_TERMINAL, "终端",
                    listOf(EventOption("x", "换", EventRisk.NONE, conditions = listOf(condition)))
                ), instance, real
            ).single().enabled
        )

        // 名叫 essence-fake 但定义是 junk：不通过
        val fake = ctxOf(items = mapOf("essence-fake" to info("essence-fake", "junk")))
        assertFalse(
            EventCatalog.evaluate(
                EventDefinition(
                    EventKind.TECH_TERMINAL, "终端",
                    listOf(EventOption("x", "换", EventRisk.NONE, conditions = listOf(condition)))
                ), instance, fake
            ).single().enabled
        )

        // 实例级条件按实例 ID 精确比对，也不能靠前缀蒙混
        val byInstance = EventCondition(EventConditionKind.HAS_ITEM_INSTANCE, key = "run-1:event:2")
        assertTrue(
            EventCatalog.evaluate(
                EventDefinition(
                    EventKind.TECH_TERMINAL, "终端",
                    listOf(EventOption("x", "换", EventRisk.NONE, conditions = listOf(byInstance)))
                ), instance, real
            ).single().enabled
        )
        assertFalse(
            EventCatalog.evaluate(
                EventDefinition(
                    EventKind.TECH_TERMINAL, "终端",
                    listOf(EventOption("x", "换", EventRisk.NONE, conditions = listOf(byInstance)))
                ), instance, fake
            ).single().enabled
        )
    }

    // ---------------- HR06 重复结算 ----------------

    @Test fun hr06_repeatResolveAfterItemDeliveryChangesNothing() {
        val instance = EventCatalog.createInstance("node-4", EventKind.BLOCKER, 1729L)
        val context = ctxOf(items = mapOf("safe-1" to info("safe-1", "blade", EventItemType.EQUIPMENT, EventItemContainer.SAFE)))
        val selection = EventItemSelection("safe-1", allowSafeContainer = true)

        val first = EventCatalog.resolve(
            EventCatalog.blocker, instance, "blocker.item", context,
            outcome("node-4", "blocker.item", consume = listOf("safe-1")), selection
        )
        assertTrue(first.ok)

        val second = EventCatalog.resolve(
            EventCatalog.blocker, first.instance, "blocker.item", context,
            outcome("node-4", "blocker.item", consume = listOf("safe-1")), selection
        )
        assertFalse(second.ok)
        assertTrue(second.alreadyResolved)
        assertEquals(EventBlockReason.ALREADY_RESOLVED, second.blockReason)
        assertNull("重复点击不得二次消费 / 二次发奖", second.outcome)
        assertEquals("blocker.item", second.instance.resolvedOptionId)
    }

    // ---------------- HR07 / HR08 连续空间判定 ----------------

    @Test fun hr07_freeCellsAreNotEnoughWithoutContiguousSpace() {
        // 4x2 背包，只有 (0,0) 与 (2,0) 两格空：面积够放 2x1，但任一朝向都没有连续空间
        val occupied = setOf(
            EventCell(0, 1), EventCell(0, 3),
            EventCell(1, 0), EventCell(1, 1), EventCell(1, 2), EventCell(1, 3)
        )
        val context = ctxOf(backpack = EventContainerSpace(4, 2, 2, occupied))
        val grant = EventGrant("blade", 2, 1, EventGrantContainer.BACKPACK)

        val fit = fitGrant(grant, context)
        assertNotEquals("面积足够不等于能放下", EventGrantFit.FITS, fit)
        assertEquals(EventGrantFit.NEEDS_ORGANIZE, fit)
    }

    @Test fun hr07b_noPlacementSnapshotIsNotAPlacementGuarantee() {
        // 上层没给摆放快照：4 格空但整箱不全空，只能报「待实际摆放校验」
        val context = ctxOf(backpack = EventContainerSpace(4, 2, 4))
        assertEquals(
            EventGrantFit.NEEDS_PLACEMENT_CHECK,
            fitGrant(EventGrant("blade", 2, 1, EventGrantContainer.BACKPACK), context)
        )
        // 整箱为空才敢说放得下
        assertEquals(
            EventGrantFit.FITS,
            fitGrant(EventGrant("blade", 2, 1, EventGrantContainer.BACKPACK),
                ctxOf(backpack = EventContainerSpace(4, 2, 8, emptySet())))
        )
    }

    @Test fun hr08_realGridCheckAcceptsContiguousAndRotatedPlacements() {
        // 空网格：3x2 锁芯在 4x2 背包里有连续空间
        val emptyGrid = ctxOf(backpack = EventContainerSpace(4, 2, 8, emptySet()))
        assertEquals(
            EventGrantFit.FITS,
            fitGrant(EventGrant("safe_lock", 3, 2, EventGrantContainer.BACKPACK), emptyGrid)
        )

        // 只允许旋转放下：4x3 背包第一行全占，1x3 竖放不行、转成 3x1 可以
        val blockedTopRow = setOf(EventCell(0, 0), EventCell(0, 1), EventCell(0, 2), EventCell(0, 3))
        val context = ctxOf(backpack = EventContainerSpace(4, 3, 8, blockedTopRow))
        assertEquals(
            EventGrantFit.FITS,
            fitGrant(EventGrant("skill_shard", 1, 3, EventGrantContainer.BACKPACK), context)
        )
    }

    @Test fun hr08b_noAutomaticSafeBoxFallback() {
        // 背包只剩 1 格、放不下 2x1；安全箱有 4 格空，但模型不得偷偷改放安全箱
        val occupied = (0..1).flatMap { row -> (0..3).map { col -> EventCell(row, col) } }
            .toSet() - EventCell(0, 0)
        val context = ctxOf(
            backpack = EventContainerSpace(4, 2, 1, occupied),
            safe = EventContainerSpace(2, 2, 4, emptySet())
        )
        val grant = EventGrant("blade", 2, 1, EventGrantContainer.BACKPACK)

        assertNotEquals(EventGrantFit.FITS, fitGrant(grant, context))
        assertEquals("容器归属不得被模型改写", EventGrantContainer.BACKPACK, grant.container)
        // 只有显式按安全箱判定才会看安全箱
        assertEquals(
            EventGrantFit.FITS,
            fitGrant(EventGrant("blade", 2, 1, EventGrantContainer.SAFE), context)
        )
    }

    // ---------------- HR13 致死风险提示 ----------------

    @Test fun hr13_lethalOptionsDiscloseTheRiskBeforeChoosing() {
        val lethal = listOf(
            EventCatalog.abandonedDepot to "depot.search",
            EventCatalog.rustedWorkshop to "workshop.pry",
            EventCatalog.ruinedLibrary to "library.delve",
            EventCatalog.collapsedStorage to "storage.left",
            EventCatalog.techTerminal to "terminal.gamble",
            EventCatalog.blocker to "blocker.rush"
        )
        for ((definition, optionId) in lethal) {
            val instance = EventCatalog.createInstance("node-1", definition.kind, 1729L)
            val v = view(definition, instance, ctxOf(), optionId)
            assertTrue("${definition.title}/$optionId 必须提示可能死亡", v.showsLethalWarning)
            assertTrue(EventDefinition.DEATH_LABEL in v.option.label)
            assertFalse(
                "不得向界面泄漏精确概率: ${v.option.label}",
                v.option.label.any { it.isDigit() }
            )
        }
    }

    // ---------------- HR14 致死结果与披露一致 ----------------

    @Test fun hr14_lethalOutcomeIsRejectedOnUndisclosedOption() {
        val instance = EventCatalog.createInstance("node-1", EventKind.ABANDONED_DEPOT, 1729L)
        val context = ctxOf()

        // 未披露致死风险的「离开」不接受致死结果
        val r = EventCatalog.resolve(
            EventCatalog.abandonedDepot, instance, "depot.leave", context,
            outcome("node-1", "depot.leave", possibleDeath = true)
        )
        assertFalse(r.ok)
        assertEquals(EventBlockReason.LETHAL_NOT_DISCLOSED, r.blockReason)
        assertNull(r.outcome)
        assertFalse(r.instance.resolved)

        // 已披露的「搜索」可以接受
        val ok = EventCatalog.resolve(
            EventCatalog.abandonedDepot, instance, "depot.search", context,
            outcome("node-1", "depot.search", possibleDeath = true)
        )
        assertTrue(ok.ok)
        assertEquals("depot.search", ok.instance.resolvedOptionId)
    }

    @Test fun hr14b_canLethalForcesTheDeathLabel() {
        assertThrows(IllegalArgumentException::class.java) {
            EventDefinition(
                EventKind.ABANDONED_DEPOT, "废弃补给站",
                listOf(EventOption(id = "s", label = "搜索（可能受伤）", risk = EventRisk.INJURY, canLethal = true))
            )
        }
    }

    // ---------------- HR15 医棚只花本局金币 ----------------

    @Test fun hr15_clinicNeverFallsBackToTheWallet() {
        val instance = EventCatalog.createInstance("node-8", EventKind.FIELD_CLINIC, 1729L, priceGold = 80)
        val context = ctxOf(runGold = 0, walletGold = 99_999)

        val v = view(EventCatalog.fieldClinic, instance, context, "clinic.gold")
        assertFalse(v.enabled)
        assertEquals(EventBlockReason.INSUFFICIENT_GOLD, v.blockedReason)
        assertEquals(EventCurrency.RUN_GOLD, (v.option.cost as EventCost.Gold).currency)

        val r = EventCatalog.resolve(
            EventCatalog.fieldClinic, instance, "clinic.gold", context,
            EventOutcome(nodeId = "node-8", optionId = "clinic.gold", goldDelta = -80,
                currency = EventCurrency.RUN_GOLD, hpDeltaPercentOfMax = 40.0)
        )
        assertFalse(r.ok)
        assertEquals(EventBlockReason.INSUFFICIENT_GOLD, r.blockReason)
        assertNull("失败时不得动钱包也不得回传结果", r.outcome)
        assertFalse(r.instance.resolved)
    }
}
