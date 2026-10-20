package com.example.douqiuqiu.event

import org.junit.Assert.*
import org.junit.Test

/**
 * 八类选择事件模型行为测试（H08）。
 *
 * 只测模型契约：结构完整性、条件求值、代价校验、容量判定、结算原子性与
 * 「不重抽 / 不重复结算」。具体随机掉落数值属于内容层，不在这里断言。
 */
class EventCatalogTest {

    private fun ctx(
        runGold: Int = 0,
        walletGold: Int = 0,
        synergies: Map<String, Int> = emptyMap(),
        clears: Int = 0,
        backpack: EventContainerSpace = EventContainerSpace(4, 2, 8),
        safe: EventContainerSpace = EventContainerSpace(2, 2, 4),
        items: Set<String> = emptySet(),
        safeItems: Set<String> = emptySet()
    ) = EventContext(
        runGold = runGold, walletGold = walletGold, synergyRanks = synergies,
        clears = clears, skillSlots = 4, backpack = backpack, safe = safe,
        itemInstanceIds = items, safeInstanceIds = safeItems
    )

    // ---------------- 目录结构 ----------------

    @Test fun e01_catalogHasExactlySixteenKinds() {
        assertEquals(16, EventCatalog.all.size)
        assertEquals(EventKind.entries.toSet(), EventCatalog.all.map { it.kind }.toSet())
    }

    @Test fun e02_everyEventOffersAtLeastTwoOptions() {
        for (definition in EventCatalog.all) {
            assertTrue("${definition.title} 至少两个选项", definition.options.size >= 2)
        }
    }

    @Test fun e03_collapsedStorageHasNoLosslessOption() {
        val storage = EventCatalog.collapsedStorage
        assertTrue("坍塌仓室无无损选项", storage.options.none { it.risk == EventRisk.NONE })
    }

    @Test fun e04_deathRiskOptionMustBeLabelled() {
        // 拦路者强闯是唯一的致死选项，文案必须带「可能死亡」
        val deathOptions = EventCatalog.all.flatMap { it.options }.filter { it.risk == EventRisk.DEATH }
        assertEquals(1, deathOptions.size)
        assertTrue(EventDefinition.DEATH_LABEL in deathOptions.single().label)

        // 构造期就拒绝未标注的致死选项，避免界面漏标
        assertThrows(IllegalArgumentException::class.java) {
            EventDefinition(
                kind = EventKind.BLOCKER, title = "拦路者",
                options = listOf(EventOption(id = "x", label = "强闯", risk = EventRisk.DEATH))
            )
        }
    }

    @Test fun e05_itemCostMustRequireExplicitChoice() {
        assertThrows(IllegalArgumentException::class.java) {
            EventDefinition(
                kind = EventKind.BLOCKER, title = "测试",
                options = listOf(
                    EventOption(id = "pay", label = "交物品", risk = EventRisk.ITEM,
                        cost = EventCost.Item(instanceId = "it-1"))
                )
            )
        }
    }

    // ---------------- 进入节点即固定 ----------------

    @Test fun e06_rollForIsDeterministic() {
        val a = EventCatalog.rollFor(1729L, "node-3")
        val b = EventCatalog.rollFor(1729L, "node-3")
        assertEquals(a, b)
        assertTrue(a in 0..99)
    }

    @Test fun e07_rollDiffersAcrossNodes() {
        val values = (1..8).map { EventCatalog.rollFor(1729L, "node-$it") }.toSet()
        assertTrue("不同节点应得到不同结果（防回归）", values.size >= 5)
    }

    @Test fun e08_createInstanceFreezesTheRoll() {
        val first = EventCatalog.createInstance("node-3", EventKind.TECH_TERMINAL, 1729L, "essence", 1)
        val second = EventCatalog.createInstance("node-3", EventKind.TECH_TERMINAL, 1729L, "essence", 1)
        assertEquals(first, second)
        assertEquals("essence", first.requiredItemDefinitionId)
        assertEquals(1, first.priceGold)
        assertFalse(first.resolved)
    }

    @Test fun e09_instanceRejectsOutOfRangeRoll() {
        assertThrows(IllegalArgumentException::class.java) {
            NodeEventInstance(nodeId = "n", kind = EventKind.BLOCKER, roll = 100)
        }
    }

    // ---------------- 条件求值 ----------------

    @Test fun e10_optionsWithoutConditionsAreEnabled() {
        val instance = EventCatalog.createInstance("node-1", EventKind.ABANDONED_DEPOT, 1729L)
        val views = EventCatalog.evaluate(instance, ctx())
        assertEquals(2, views.size)
        assertTrue(views.all { it.enabled })
        assertTrue(views.all { it.blockedReason == null })
    }

    @Test fun e11_unmetSynergyBlocksSafeStudy() {
        val instance = EventCatalog.createInstance("node-2", EventKind.RUINED_LIBRARY, 1729L)
        val views = EventCatalog.evaluate(instance, ctx())
        val study = views.single { it.option.id == "library.study" }
        assertFalse(study.enabled)
        assertEquals(EventBlockReason.CONDITION_UNMET, study.blockedReason)
    }

    @Test fun e12_metSynergyEnablesSafeStudy() {
        val instance = EventCatalog.createInstance("node-2", EventKind.RUINED_LIBRARY, 1729L)
        val views = EventCatalog.evaluate(instance, ctx(synergies = mapOf("ultimate" to 1)))
        assertTrue(views.single { it.option.id == "library.study" }.enabled)
    }

    // ---------------- 代价校验：失败不扣资源 ----------------

    @Test fun e13_insufficientRunGoldBlocksPaidOption() {
        val instance = EventCatalog.createInstance("node-3", EventKind.FIELD_CLINIC, 1729L, priceGold = 80)
        val views = EventCatalog.evaluate(instance, ctx(runGold = 30))
        val paid = views.single { it.option.id == "clinic.gold" }
        assertFalse(paid.enabled)
        assertEquals(EventBlockReason.INSUFFICIENT_GOLD, paid.blockedReason)
    }

    @Test fun e14_walletGoldNeverPaysForInRunEventCosts() {
        val instance = EventCatalog.createInstance("node-3", EventKind.FIELD_CLINIC, 1729L, priceGold = 80)
        // 仓库很富、本局没钱：仍需禁用，且不得动钱包
        val views = EventCatalog.evaluate(instance, ctx(runGold = 0, walletGold = 99_999))
        val paid = views.single { it.option.id == "clinic.gold" }
        assertFalse(paid.enabled)
        assertEquals(EventBlockReason.INSUFFICIENT_GOLD, paid.blockedReason)
        assertEquals(EventCurrency.RUN_GOLD, (paid.option.cost as EventCost.Gold).currency)
    }

    @Test fun e15_sufficientRunGoldEnablesPaidOption() {
        val instance = EventCatalog.createInstance("node-3", EventKind.FIELD_CLINIC, 1729L, priceGold = 80)
        val views = EventCatalog.evaluate(instance, ctx(runGold = 80))
        assertTrue(views.single { it.option.id == "clinic.gold" }.enabled)
    }

    @Test fun e16_itemCostIsCheckedAgainstRealInstances() {
        val definition = eventWithItemCost(instanceId = "it-1", allowSafe = false)
        val instance = NodeEventInstance("node-4", EventKind.BLOCKER, 42)

        // 玩家没有这个实例 → 禁用且不动任何资源
        val missing = EventCatalog.evaluate(definition, instance, ctx(items = setOf("it-7")))
            .single { it.option.id == "blocker.item" }
        assertFalse(missing.enabled)
        assertEquals(EventBlockReason.ITEM_INSTANCE_MISSING, missing.blockedReason)

        // 玩家确实持有 → 可选
        val held = EventCatalog.evaluate(definition, instance, ctx(items = setOf("it-1")))
            .single { it.option.id == "blocker.item" }
        assertTrue(held.enabled)
    }

    @Test fun e17_eventsCannotTakeSafeContainerItems() {
        val instance = NodeEventInstance("node-4", EventKind.BLOCKER, 42)
        val context = ctx(items = setOf("it-1"), safeItems = setOf("it-1"))

        // 默认不允许：安全箱物品不能被事件取走
        val blocked = EventCatalog.evaluate(
            eventWithItemCost(instanceId = "it-1", allowSafe = false), instance, context
        ).single { it.option.id == "blocker.item" }
        assertFalse(blocked.enabled)
        assertEquals(EventBlockReason.ITEM_INSTANCE_MISSING, blocked.blockedReason)

        // 玩家明确选择交出，且声明允许安全箱来源 → 可选
        val allowed = EventCatalog.evaluate(
            eventWithItemCost(instanceId = "it-1", allowSafe = true), instance, context
        ).single { it.option.id == "blocker.item" }
        assertTrue(allowed.enabled)
    }

    @Test fun e18_itemCostWithoutExplicitChoiceIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            EventDefinition(
                kind = EventKind.BLOCKER, title = "拦路者",
                options = listOf(
                    EventOption(id = "blocker.item", label = "交一件可交付物品",
                        risk = EventRisk.ITEM, cost = EventCost.Item(instanceId = "it-1"))
                )
            )
        }
    }

    private fun eventWithItemCost(instanceId: String, allowSafe: Boolean): EventDefinition =
        EventDefinition(
            kind = EventKind.BLOCKER, title = "拦路者",
            options = listOf(
                EventOption(
                    id = "blocker.item", label = "交一件可交付物品", risk = EventRisk.ITEM,
                    cost = EventCost.Item(instanceId = instanceId, allowSafeContainer = allowSafe),
                    requiresExplicitItemChoice = true
                )
            )
        )

    @Test fun e19_resolveSucceedsAndMarksTheInstance() {
        val instance = EventCatalog.createInstance("node-1", EventKind.ABANDONED_DEPOT, 1729L)
        val outcome = EventOutcome(nodeId = "node-1", optionId = "depot.search", grantDefinitionIds = listOf("potion"))
        val result = EventCatalog.resolve(instance, "depot.search", ctx(), outcome)

        assertTrue(result.ok)
        assertNull(result.blockReason)
        assertEquals("depot.search", result.instance.resolvedOptionId)
        assertTrue(result.instance.resolved)
        assertEquals(outcome, result.outcome)
    }

    @Test fun e20_repeatClickDoesNotSettleTwice() {
        var instance = EventCatalog.createInstance("node-1", EventKind.ABANDONED_DEPOT, 1729L)
        val outcome = EventOutcome(nodeId = "node-1", optionId = "depot.search", grantDefinitionIds = listOf("potion"))
        instance = EventCatalog.resolve(instance, "depot.search", ctx(), outcome).instance

        val second = EventCatalog.resolve(
            instance, "depot.search", ctx(),
            EventOutcome(nodeId = "node-1", optionId = "depot.search", grantDefinitionIds = listOf("potion"))
        )
        assertFalse(second.ok)
        assertTrue(second.alreadyResolved)
        assertNull(second.outcome)
        assertEquals(EventBlockReason.ALREADY_RESOLVED, second.blockReason)
        assertEquals("depot.search", second.instance.resolvedOptionId)
    }

    @Test fun e21_resolveRejectsUnknownOption() {
        val instance = EventCatalog.createInstance("node-1", EventKind.ABANDONED_DEPOT, 1729L)
        val result = EventCatalog.resolve(
            instance, "nope", ctx(),
            EventOutcome(nodeId = "node-1", optionId = "nope")
        )
        assertFalse(result.ok)
        assertEquals(EventBlockReason.OPTION_NOT_FOUND, result.blockReason)
        assertNull(result.outcome)
        assertFalse(result.instance.resolved)
    }

    @Test fun e22_blockedResolveChangesNothing() {
        val instance = EventCatalog.createInstance("node-3", EventKind.FIELD_CLINIC, 1729L, priceGold = 80)
        val result = EventCatalog.resolve(
            instance, "clinic.gold", ctx(runGold = 10),
            EventOutcome(nodeId = "node-3", optionId = "clinic.gold", hpDeltaPercentOfMax = 30.0)
        )
        assertFalse(result.ok)
        assertEquals(EventBlockReason.INSUFFICIENT_GOLD, result.blockReason)
        assertNull(result.outcome)
        assertFalse(result.instance.resolved)
    }

    @Test fun e23_mismatchedOutcomeIsRejected() {
        val instance = EventCatalog.createInstance("node-1", EventKind.ABANDONED_DEPOT, 1729L)
        assertThrows(IllegalArgumentException::class.java) {
            EventCatalog.resolve(
                instance, "depot.search", ctx(),
                EventOutcome(nodeId = "other-node", optionId = "depot.search")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventCatalog.resolve(
                instance, "depot.search", ctx(),
                EventOutcome(nodeId = "node-1", optionId = "depot.leave")
            )
        }
    }

    // ---------------- 奖励容量判定 ----------------

    @Test fun e24_lockCoreDoesNotFitInitialSafeBox() {
        // 安全箱 2x2 装不下 3x2 锁芯
        val grant = EventGrant("safe_lock", 3, 2, EventGrantContainer.SAFE)
        assertEquals(EventGrantFit.DOES_NOT_FIT, fitGrant(grant, ctx()))
    }

    @Test fun e25_lockCoreFitsInitialBackpack() {
        // 初始 4x2 背包可以直接放入 3x2 锁芯
        val grant = EventGrant("safe_lock", 3, 2, EventGrantContainer.BACKPACK)
        assertEquals(EventGrantFit.FITS, fitGrant(grant, ctx()))
    }

    @Test fun e26_frameNeedsUpgradedBackpack() {
        // 3x2 安全箱装不下 3x3 框架；升级到 4x3 背包后才可携带
        val grant = EventGrant("safe_frame", 3, 3, EventGrantContainer.BACKPACK)
        assertEquals(
            EventGrantFit.DOES_NOT_FIT,
            fitGrant(grant, ctx(backpack = EventContainerSpace(4, 2, 8)))
        )
        assertEquals(
            EventGrantFit.FITS,
            fitGrant(grant, ctx(backpack = EventContainerSpace(4, 3, 12)))
        )
    }

    @Test fun e27_freeSpaceShortageTriggersOrganizeNotSafeBoxFallback() {
        // 尺寸够但空闲格不足：走整理 / 取舍，不得凭空塞进安全箱
        val grant = EventGrant("backpack_fabric", 1, 2, EventGrantContainer.BACKPACK)
        assertEquals(
            EventGrantFit.NEEDS_ORGANIZE,
            fitGrant(grant, ctx(backpack = EventContainerSpace(4, 2, 1)))
        )
    }

    @Test fun e28_rotationIsAllowed() {
        // 1x3 竖放不下，旋转成 3x1 可以
        val grant = EventGrant("skill_shard", 1, 3, EventGrantContainer.SAFE)
        assertEquals(EventGrantFit.DOES_NOT_FIT, fitGrant(grant, ctx()))
        assertEquals(
            EventGrantFit.FITS,
            fitGrant(grant, ctx(safe = EventContainerSpace(3, 2, 6)))
        )
    }
}
