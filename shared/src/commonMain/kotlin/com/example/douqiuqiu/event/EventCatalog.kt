package com.example.douqiuqiu.event

/**
 * 八类选择事件的纯模型（剩余实现交接清单 H08）。
 *
 * 依据 `docs/design/economy-progression.md` 的「事件初稿」一节与 v1 定案。
 * 当前 `JourneyRules.event(s, search)` 只有「搜索 / 放弃」两个分支，不是完整事件系统；
 * 旧 `config/events.json` 的 12 个模板也不等于已定的 8 类。
 *
 * 本文件只提供**结构、条件求值、展示层风险标记、奖励容量判定与结算原子性契约**，并且：
 * - 不解析中文描述字符串；一切分支、条件、代价都是明确的类型与枚举；
 * - 不读存档、不读时钟、不随机、不直接改动金币 / 物品 / 生命；
 * - 具体随机结果（给什么药、掉多少血）属于内容层，由调用方以 [EventOutcome] 传入——
 *   因为经济文档与 poison.md 里的具体数值仍是审阅稿，不在这里悄悄固化。
 *
 * 界面契约：
 * - 选项只显示**风险类别**（[EventRisk]），不得显示概率或精确伤害；
 * - 可能造成死亡的选项（[EventRisk.DEATH] 或 [EventOption.canLethal]）文案必须包含
 *   「可能死亡」——[EventOptionView.showsLethalWarning] 是界面只读的唯一依据；
 * - 节点事件在进入节点时一次性生成 [NodeEventInstance] 并持久化，
 *   离开重进、反复查看选项都不得重抽（roll 已固定在实例里）。
 *
 * 交物契约（R01）：交物选项**只在结算时接受玩家明确选择的实例**
 * （[EventItemSelection]），事件不得自动代选；未选择一律 [EventBlockReason.ITEM_CHOICE_REQUIRED]，
 * 不能只在界面按钮上禁用。所选实例必须出现在 [EventOutcome.consumeInstanceIds] 里，
 * 且确实被玩家持有、未被安全箱锁定（除非玩家显式授权）。
 */
object EventCatalog {

    // ---------------- 八类事件定义（先声明，供 all 汇总） ----------------

    /** 废弃补给站：搜索可能有药品也可能受伤（低血时可能致死）；离开无收益无风险。 */
    val abandonedDepot: EventDefinition = EventDefinition(
        kind = EventKind.ABANDONED_DEPOT,
        title = "废弃补给站",
        options = listOf(
            EventOption(
                id = "depot.search", label = "搜索（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "depot.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 锈蚀工坊：强拆可能得组件也可能受伤（可能致死）；交金币稳妥取件；离开。 */
    val rustedWorkshop: EventDefinition = EventDefinition(
        kind = EventKind.RUSTED_WORKSHOP,
        title = "锈蚀工坊",
        options = listOf(
            EventOption(
                id = "workshop.pry", label = "强拆（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "workshop.pay", label = "交金币稳妥取件", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "workshop.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /**
     * 残破书库：深入可能得技能卡也可能中毒（可能致死）；持有大招羁绊可安全研读；离开。
     *
     * 深入的伤害与附毒按既定事件规则**当场结算并留在本局**，模型没有「把毒带出局外」的字段，
     * 因此不存在留局外毒的路径。
     */
    val ruinedLibrary: EventDefinition = EventDefinition(
        kind = EventKind.RUINED_LIBRARY,
        title = "残破书库",
        options = listOf(
            EventOption(
                id = "library.delve", label = "深入（可能中毒，可能死亡）",
                risk = EventRisk.POISON, canLethal = true
            ),
            EventOption(id = "library.study", label = "安全研读", risk = EventRisk.NONE,
                conditions = listOf(EventCondition(EventConditionKind.SYNERGY_RANK, key = "ultimate", required = 1))),
            EventOption(id = "library.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 坍塌仓室：无无损选项，两条路都要付出代价，且明确显示风险类型。 */
    val collapsedStorage: EventDefinition = EventDefinition(
        kind = EventKind.COLLAPSED_STORAGE,
        title = "坍塌仓室",
        options = listOf(
            EventOption(
                id = "storage.left", label = "左路（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "storage.right", label = "右路（交金币通过）", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE))
        )
    )

    /** 保险库残骸：搬锁芯占大空间；拆解取通用材料；放弃。满足进度才生成锁芯。 */
    val safeWreckage: EventDefinition = EventDefinition(
        kind = EventKind.SAFE_WRECKAGE,
        title = "保险库残骸",
        options = listOf(
            EventOption(id = "wreckage.core", label = "搬走锁芯", risk = EventRisk.NONE),
            EventOption(id = "wreckage.salvage", label = "拆解取通用材料", risk = EventRisk.NONE),
            EventOption(id = "wreckage.abandon", label = "放弃", risk = EventRisk.NONE)
        )
    )

    /**
     * 技术终端：消耗指定材料换技能刻片；冒险操作；离开。
     *
     * 所需材料按**物品定义 ID** 精确匹配（取节点实例上的 `requiredItemDefinitionId`），
     * 不做实例 ID 前缀推断；玩家必须明确选择要交出的实例。
     */
    val techTerminal: EventDefinition = EventDefinition(
        kind = EventKind.TECH_TERMINAL,
        title = "技术终端",
        options = listOf(
            EventOption(id = "terminal.convert", label = "消耗一份指定材料换技能刻片",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.DEFINITION)),
            EventOption(
                id = "terminal.gamble", label = "冒险操作（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "terminal.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 临时医棚：交本局金币治疗；用一份药品强化治疗；离开。不是商店，不出售商品。 */
    val fieldClinic: EventDefinition = EventDefinition(
        kind = EventKind.FIELD_CLINIC,
        title = "临时医棚",
        options = listOf(
            EventOption(id = "clinic.gold", label = "交本局金币治疗", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "clinic.supply", label = "用一份药品强化治疗",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(id = "clinic.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 拦路者：交金币；交一件可交付物品（必须明确选实例，默认不强取安全箱）；强闯可能受伤。 */
    val blocker: EventDefinition = EventDefinition(
        kind = EventKind.BLOCKER,
        title = "拦路者",
        options = listOf(
            EventOption(id = "blocker.gold", label = "交金币", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "blocker.item", label = "交一件可交付物品",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.ANY_DELIVERABLE)),
            EventOption(id = "blocker.rush", label = "强闯（可能死亡）", risk = EventRisk.DEATH)
        )
    )


    // ---------------- 0.1.5 新增八类事件（8 → 16） ----------------
    // 每个事件固定三个稳定结局；artKey 由 JourneyActivity 按 kind + optionId 生成，
    // 节点生成时把结果冻结进 NodeEventInstance.outcomes，读档不重抽。

    /** 冰封祭坛：以生命共鸣换寒霜奖励；安全祈祷只有小收益；离开。 */
    val frozenShrine: EventDefinition = EventDefinition(
        kind = EventKind.FROZEN_SHRINE,
        title = "冰封祭坛",
        options = listOf(
            EventOption(
                id = "frozen_shrine.attune", label = "以生命共鸣寒霜（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "frozen_shrine.pray", label = "安静祈祷", risk = EventRisk.NONE),
            EventOption(id = "frozen_shrine.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 战旗遗址：交金币领走怒火奖励；血祭以生命换更高价值奖励；离开。 */
    val warBannerRuins: EventDefinition = EventDefinition(
        kind = EventKind.WAR_BANNER_RUINS,
        title = "战旗遗址",
        options = listOf(
            EventOption(id = "war_banner.claim", label = "交金币取走战旗", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(
                id = "war_banner.blood_rite", label = "血祭战旗（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "war_banner.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 流动商队：本局金币购买；明确卖出一件物品；离开。 */
    val rovingCaravan: EventDefinition = EventDefinition(
        kind = EventKind.ROVING_CARAVAN,
        title = "流动商队",
        options = listOf(
            EventOption(id = "roving_caravan.buy", label = "用本局金币购买", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "roving_caravan.sell", label = "卖出一件物品",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.ANY_DELIVERABLE)),
            EventOption(id = "roving_caravan.depart", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 断桥索道：冒险过桥可能受伤换多件小掉落；花钱修桥稳妥取件；离开。 */
    val ropewayBridge: EventDefinition = EventDefinition(
        kind = EventKind.ROPEWAY_BRIDGE,
        title = "断桥索道",
        options = listOf(
            EventOption(
                id = "ropeway_bridge.cross", label = "攀索过桥（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "ropeway_bridge.repair", label = "交金币修桥", risk = EventRisk.GOLD,
                cost = EventCost.Gold(currency = EventCurrency.RUN_GOLD, source = EventCostSource.INSTANCE_PRICE)),
            EventOption(id = "ropeway_bridge.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 秘契祭坛：交一份药换卡；以生命换稀有奖励；拒绝。 */
    val pactAltar: EventDefinition = EventDefinition(
        kind = EventKind.PACT_ALTAR,
        title = "秘契祭坛",
        options = listOf(
            EventOption(id = "pact_altar.offer_medicine", label = "献上一份药品",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(
                id = "pact_altar.offer_blood", label = "以血为契（可能受伤，可能死亡）",
                risk = EventRisk.INJURY, canLethal = true
            ),
            EventOption(id = "pact_altar.refuse", label = "拒绝", risk = EventRisk.NONE)
        )
    )

    /** 失控炼金室：交药蒸馏换治疗型掉落；破封可能中毒或受伤换炼金组件；离开。 */
    val alchemyLab: EventDefinition = EventDefinition(
        kind = EventKind.ALCHEMY_LAB,
        title = "失控炼金室",
        options = listOf(
            EventOption(id = "alchemy_lab.distill", label = "投入药品蒸馏",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(
                id = "alchemy_lab.break_seal", label = "强行破封（可能中毒，可能死亡）",
                risk = EventRisk.POISON, canLethal = true
            ),
            EventOption(id = "alchemy_lab.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 猎人营火：明确交物换战利品；分一份口粮换治疗与情报；离开。 */
    val hunterCamp: EventDefinition = EventDefinition(
        kind = EventKind.HUNTER_CAMP,
        title = "猎人营火",
        options = listOf(
            EventOption(id = "hunter_camp.barter", label = "以物易物",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.ANY_DELIVERABLE)),
            EventOption(id = "hunter_camp.share_rations", label = "分一份药品给猎人",
                risk = EventRisk.ITEM, requiresExplicitItemChoice = true,
                itemRequirement = EventItemRequirement(kind = EventItemRequirementKind.MEDICINE)),
            EventOption(id = "hunter_camp.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 月井回响：羁绊达标才能共鸣；抽取随机低档回响；离开。 */
    val moonwellEcho: EventDefinition = EventDefinition(
        kind = EventKind.MOONWELL_ECHO,
        title = "月井回响",
        options = listOf(
            EventOption(id = "moonwell_echo.resonate", label = "共鸣月井（需要两档羁绊）", risk = EventRisk.NONE),
            EventOption(id = "moonwell_echo.draw_echo", label = "抽取低语回响", risk = EventRisk.NONE),
            EventOption(id = "moonwell_echo.leave", label = "离开", risk = EventRisk.NONE)
        )
    )

    /** 全部八类事件定义，顺序即文档表格顺序。 */
    val all: List<EventDefinition> = listOf(
        abandonedDepot,
        rustedWorkshop,
        ruinedLibrary,
        collapsedStorage,
        safeWreckage,
        techTerminal,
        fieldClinic,
        blocker,
        frozenShrine,
        warBannerRuins,
        rovingCaravan,
        ropewayBridge,
        pactAltar,
        alchemyLab,
        hunterCamp,
        moonwellEcho
    )

    /**
     * 事件的稳定插画键：`事件前缀.选项后缀`（如 `frozen_shrine.attune`）。
     *
     * 旧八类事件没有配图，返回空串；新八类必须返回稳定值，视觉层不得按标题反推。
     */
    fun artKey(kind: EventKind, optionId: String): String {
        val prefix = when (kind) {
            EventKind.FROZEN_SHRINE -> "frozen_shrine"
            EventKind.WAR_BANNER_RUINS -> "war_banner"
            EventKind.ROVING_CARAVAN -> "roving_caravan"
            EventKind.ROPEWAY_BRIDGE -> "ropeway_bridge"
            EventKind.PACT_ALTAR -> "pact_altar"
            EventKind.ALCHEMY_LAB -> "alchemy_lab"
            EventKind.HUNTER_CAMP -> "hunter_camp"
            EventKind.MOONWELL_ECHO -> "moonwell_echo"
            else -> return ""
        }
        val option = optionId.substringAfterLast('.')
        return if (option.matches(Regex("[a-z_]+"))) "$prefix.$option" else ""
    }

    fun definition(kind: EventKind): EventDefinition = all.first { it.kind == kind }

    /**
     * 进入节点时一次性生成并固定的事件实例。
     *
     * [NodeEventInstance.roll] 由 seed 与 nodeId 确定性推出，不读时钟也不随机；
     * 同一 seed + 同一节点永远得到同一结果，存档恢复后不会重抽。
     */
    fun createInstance(
        nodeId: String,
        kind: EventKind,
        seed: Long,
        requiredItemDefinitionId: String = "",
        priceGold: Int = 0
    ): NodeEventInstance = NodeEventInstance(
        nodeId = nodeId,
        kind = kind,
        roll = rollFor(seed, nodeId),
        requiredItemDefinitionId = requiredItemDefinitionId,
        priceGold = priceGold
    )

    /** 确定性 roll（0..99），沿用工程内 Lehmer 风格递推，不读随机数与时钟。 */
    fun rollFor(seed: Long, nodeId: String): Int {
        var state = seed.coerceIn(1, 2147483646)
        for (ch in nodeId) {
            state = (state * 48271L + ch.code.toLong()) % 2147483647L
        }
        return (state % 100L).toInt()
    }

    /**
     * 展示层求值：判断每个选项当前是否可选及被禁原因。
     *
     * **此方法不产生任何扣款或物品变更。** 条件不满足时上层必须原样返回，
     * 不能先扣后验或先扣后退。
     *
     * [itemSelection] 是玩家**当前**已选中的待交实例（可能还没选）。交物类选项在
     * 玩家尚未选择时返回 [EventBlockReason.ITEM_CHOICE_REQUIRED]，界面据此提示先选物品。
     */
    fun evaluate(instance: NodeEventInstance, context: EventContext): List<EventOptionView> =
        evaluate(definition(instance.kind), instance, context)

    /**
     * 同上，但显式传入 [definition] 与 [itemSelection]。
     *
     * 供上层把事件定义从配置加载后复用同一套求值逻辑，也让测试能覆盖
     * 目录里没有的组合（例如交物实例、安全箱保护）。
     */
    fun evaluate(
        definition: EventDefinition,
        instance: NodeEventInstance,
        context: EventContext,
        itemSelection: EventItemSelection? = null
    ): List<EventOptionView> = definition.options.map { option ->
        val reason = blockReason(option, instance, context, itemSelection)
        EventOptionView(
            option = option,
            enabled = reason == null,
            blockedReason = reason,
            showsLethalWarning = disclosesLethalRisk(option)
        )
    }

    /**
     * 结算一次选择：校验通过后把 `resolvedOptionId` 写入实例，并返回调用方给出的结果描述。
     *
     * 契约：
     * - 选项不存在 / 条件不满足 / 金币或物品实例不足 / 未选择交物实例 → `ok = false`，
     *   **实例与资源都不变**（失败不扣资源）；
     * - 已结算过的实例再次调用 → `ok = false` 且 `alreadyResolved = true`，
     *   重复点击不会重复发奖、重复扣票或重复消费保底；
     * - 成功时返回的实例已带 `resolvedOptionId`，调用方应把它与 [outcome] 一起原子落盘；
     * - [EventOutcome.consumeInstanceIds] 必须是玩家实际持有且已授权的实例，
     *   并与 [itemSelection] 一致，否则整体拒绝（零消费）。
     */
    fun resolve(
        instance: NodeEventInstance,
        optionId: String,
        context: EventContext,
        outcome: EventOutcome
    ): EventResolveResult = resolve(definition(instance.kind), instance, optionId, context, outcome)

    /** 同上，但显式传入 [definition] 与玩家选中的 [itemSelection]。 */
    fun resolve(
        definition: EventDefinition,
        instance: NodeEventInstance,
        optionId: String,
        context: EventContext,
        outcome: EventOutcome,
        itemSelection: EventItemSelection? = null
    ): EventResolveResult {
        if (instance.resolvedOptionId != null) {
            return EventResolveResult(
                ok = false,
                instance = instance,
                outcome = null,
                blockReason = EventBlockReason.ALREADY_RESOLVED,
                alreadyResolved = true
            )
        }
        val option = definition.options.firstOrNull { it.id == optionId }
            ?: return EventResolveResult(false, instance, null, blockReason = EventBlockReason.OPTION_NOT_FOUND)
        val reason = blockReason(option, instance, context, itemSelection)
        if (reason != null) {
            return EventResolveResult(false, instance, null, blockReason = reason)
        }
        require(outcome.nodeId == instance.nodeId) {
            "结算描述的 nodeId 与实例不一致: ${outcome.nodeId} vs ${instance.nodeId}"
        }
        require(outcome.optionId == option.id) {
            "结算描述的 optionId 与选项不一致: ${outcome.optionId} vs ${option.id}"
        }
        // 致死结果只能出现在已向玩家告知致死风险的选项上；不为省文案把伤害改成不致死。
        if (outcome.possibleDeath && !disclosesLethalRisk(option)) {
            return EventResolveResult(
                false, instance, null, blockReason = EventBlockReason.LETHAL_NOT_DISCLOSED
            )
        }
        val consumeReason = validateConsumption(outcome, context, itemSelection)
        if (consumeReason != null) {
            return EventResolveResult(false, instance, null, blockReason = consumeReason)
        }
        return EventResolveResult(true, instance.copy(resolvedOptionId = option.id), outcome)
    }

    /**
     * 单个选项的禁用原因；null 表示可选。
     *
     * 检查顺序固定：已结算 → 条件 → 代价 → 交物实例。
     */
    internal fun blockReason(
        option: EventOption,
        instance: NodeEventInstance,
        context: EventContext,
        itemSelection: EventItemSelection? = null
    ): EventBlockReason? {
        if (instance.resolvedOptionId != null) return EventBlockReason.ALREADY_RESOLVED
        for (condition in option.conditions) {
            if (!satisfied(condition, context, instance)) return EventBlockReason.CONDITION_UNMET
        }
        when (val cost = option.cost) {
            null -> Unit
            is EventCost.Gold -> {
                val need = when (cost.source) {
                    EventCostSource.FIXED -> cost.amount
                    EventCostSource.INSTANCE_PRICE -> instance.priceGold
                }
                val available = when (cost.currency) {
                    EventCurrency.RUN_GOLD -> context.runGold
                    EventCurrency.WALLET_GOLD -> context.walletGold
                }
                if (available < need) return EventBlockReason.INSUFFICIENT_GOLD
            }
            is EventCost.Item -> {
                if (!cost.allowSafeContainer && cost.instanceId in context.safeInstanceIds) {
                    // 事件不能强取安全箱物品
                    return EventBlockReason.ITEM_INSTANCE_MISSING
                }
                if (cost.instanceId !in context.itemInstanceIds) {
                    return EventBlockReason.ITEM_INSTANCE_MISSING
                }
            }
        }
        if (needsRuntimeItemChoice(option)) {
            val selection = itemSelection ?: return EventBlockReason.ITEM_CHOICE_REQUIRED
            return validateSelection(option, instance, context, selection)
        }
        return null
    }

    /**
     * 交物类选项是否需要在结算时由玩家给出实例。
     *
     * [EventCost.Item] 是「定义里写死某一实例」的旧路径，实例已知，不再要求运行时选择；
     * 目录里的标准交物选项只有 [EventOption.itemRequirement]，实例必须由玩家现场选。
     */
    private fun needsRuntimeItemChoice(option: EventOption): Boolean =
        option.cost !is EventCost.Item &&
            (option.itemRequirement != null || option.requiresExplicitItemChoice)

    /** 选项是否已向玩家披露致死风险。 */
    private fun disclosesLethalRisk(option: EventOption): Boolean =
        option.risk == EventRisk.DEATH || option.canLethal

    /**
     * 校验玩家选中的实例：持有、来源授权、类型/定义匹配，三者都过才算可选。
     *
     * 不做实例 ID 前缀推断（R02）：一切按 [EventContext.items] 里的实例信息精确比对；
     * 上下文没有该实例的信息时，无法证明其类型或定义，一律拒绝而不是放行。
     */
    private fun validateSelection(
        option: EventOption,
        instance: NodeEventInstance,
        context: EventContext,
        selection: EventItemSelection
    ): EventBlockReason? {
        val id = selection.instanceId
        if (id.isBlank()) return EventBlockReason.ITEM_CHOICE_REQUIRED
        val info = context.items[id]
        val held = info != null || id in context.itemInstanceIds
        if (!held) return EventBlockReason.ITEM_INSTANCE_MISSING

        val inSafe = id in context.safeInstanceIds || info?.container == EventItemContainer.SAFE
        if (inSafe && !selection.allowSafeContainer) {
            return EventBlockReason.SAFE_CONTAINER_NOT_AUTHORIZED
        }

        val requirement = option.itemRequirement ?: return null
        if (inSafe && !requirement.allowSafeContainer && !selection.allowSafeContainer) {
            return EventBlockReason.SAFE_CONTAINER_NOT_AUTHORIZED
        }
        return when (requirement.kind) {
            EventItemRequirementKind.ANY_DELIVERABLE -> null
            EventItemRequirementKind.MEDICINE ->
                if (info == null || info.type != EventItemType.MEDICINE) {
                    EventBlockReason.ITEM_TYPE_MISMATCH
                } else null
            EventItemRequirementKind.DEFINITION -> {
                val required = requirement.definitionId.ifBlank { instance.requiredItemDefinitionId }
                if (required.isBlank() || info == null || info.definitionId != required) {
                    EventBlockReason.ITEM_TYPE_MISMATCH
                } else null
            }
        }
    }

    /**
     * 校验结算描述里的消费清单：必须与玩家实际持有、实际授权、实际选择一致。
     *
     * 任何一项不符就整体拒绝——失败时零消费，不会出现「扣了别的实例」的偏斜。
     */
    private fun validateConsumption(
        outcome: EventOutcome,
        context: EventContext,
        selection: EventItemSelection?
    ): EventBlockReason? {
        val ids = outcome.consumeInstanceIds
        if (ids.isEmpty()) return null
        if (ids.toHashSet().size != ids.size) return EventBlockReason.ITEM_INSTANCE_MISSING
        for (id in ids) {
            val info = context.items[id]
            val held = info != null || id in context.itemInstanceIds
            if (!held) return EventBlockReason.ITEM_INSTANCE_MISSING
            val inSafe = id in context.safeInstanceIds || info?.container == EventItemContainer.SAFE
            val authorized = selection != null && selection.instanceId == id && selection.allowSafeContainer
            if (inSafe && !authorized) return EventBlockReason.SAFE_CONTAINER_NOT_AUTHORIZED
        }
        if (selection != null && selection.instanceId !in ids) {
            return EventBlockReason.ITEM_INSTANCE_MISSING
        }
        return null
    }

    private fun satisfied(
        condition: EventCondition,
        context: EventContext,
        instance: NodeEventInstance
    ): Boolean = when (condition.kind) {
        EventConditionKind.SYNERGY_RANK ->
            (context.synergyRanks[condition.key] ?: 0) >= condition.required
        EventConditionKind.MIN_CLEARS -> context.clears >= condition.required
        EventConditionKind.MIN_RUN_GOLD -> context.runGold >= condition.required
        EventConditionKind.BACKPACK_HEIGHT -> context.backpack.height >= condition.required
        EventConditionKind.SAFE_HEIGHT -> context.safe.height >= condition.required
        EventConditionKind.SKILL_SLOTS -> context.skillSlots >= condition.required
        // 实例级：必须持有「这个」实例，按实例 ID 精确比对。
        EventConditionKind.HAS_ITEM_INSTANCE ->
            condition.key.isNotBlank() && condition.key in heldInstanceIds(context)
        // 定义级：持有任意一件该定义的实例，按 definitionId 精确比对，
        // 不通过实例 ID 前缀推断。key 为空时取节点实例上的 requiredItemDefinitionId。
        EventConditionKind.HAS_ITEM_DEFINITION -> {
            val required = condition.key.ifBlank { instance.requiredItemDefinitionId }
            required.isNotBlank() && context.items.values.any { it.definitionId == required }
        }
    }

    private fun heldInstanceIds(context: EventContext): Set<String> =
        context.itemInstanceIds + context.items.keys
}

/** 八类节点事件。 */
enum class EventKind {
    ABANDONED_DEPOT,
    RUSTED_WORKSHOP,
    RUINED_LIBRARY,
    COLLAPSED_STORAGE,
    SAFE_WRECKAGE,
    TECH_TERMINAL,
    FIELD_CLINIC,
    BLOCKER,

    // 0.1.5 新增八类
    FROZEN_SHRINE,
    WAR_BANNER_RUINS,
    ROVING_CARAVAN,
    ROPEWAY_BRIDGE,
    PACT_ALTAR,
    ALCHEMY_LAB,
    HUNTER_CAMP,
    MOONWELL_ECHO
}

/**
 * 选项展示给玩家的风险类别。
 *
 * 只显示类别，不得显示概率与精确伤害；存在致死可能时界面显示「可能死亡」
 * （见 [EventOptionView.showsLethalWarning]）。
 */
enum class EventRisk { NONE, INJURY, POISON, GOLD, ITEM, DEATH }

/** 选项可用条件；由调用方用当前构筑 / 进度 / 实际物品实例求值。 */
enum class EventConditionKind {
    /** 某流派羁绊档位，key 为流派 ID，required 为最低档位。 */
    SYNERGY_RANK,
    /** 累计通关次数。 */
    MIN_CLEARS,
    /** 本局金币，required 为最低金币。 */
    MIN_RUN_GOLD,
    /** 背包高度（格）。 */
    BACKPACK_HEIGHT,
    /** 安全箱高度（格）。 */
    SAFE_HEIGHT,
    /** 技能栏数量。 */
    SKILL_SLOTS,
    /** 实例级：持有 key 指定的**实例 ID**（精确比对，不是前缀）。 */
    HAS_ITEM_INSTANCE,
    /** 定义级：持有 key 指定**物品定义**的任意实例（精确比对 definitionId）。 */
    HAS_ITEM_DEFINITION
}

/** 一个可用条件。 */
data class EventCondition(
    val kind: EventConditionKind,
    val key: String = "",
    val required: Int = 0
)

/** 选项代价。 */
sealed class EventCost {

    /**
     * 金币代价；[currency] 明确取自本局金币还是仓库钱包，不得混用。
     *
     * [source] 决定价格从哪来：[EventCostSource.FIXED] 用 [amount]，
     * [EventCostSource.INSTANCE_PRICE] 用节点实例上已固定的 `priceGold`
     * ——付费医棚、拦路者这类「进入节点时定价」的选项属于后者，
     * 定义里不写死价格，避免刷新/重进时价格漂移。
     */
    data class Gold(
        val currency: EventCurrency,
        val amount: Int = 0,
        val source: EventCostSource = EventCostSource.FIXED
    ) : EventCost() {
        init { require(amount >= 0) { "金币代价不能为负: $amount" } }
    }

    /**
     * 交付一件**定义里已写死**的物品实例。
     *
     * 目录里的标准交物选项不走这条（实例由玩家现场选），见 [EventOption.itemRequirement]；
     * [allowSafeContainer] 默认 false——事件不能强取安全箱物品。
     */
    data class Item(val instanceId: String, val allowSafeContainer: Boolean = false) : EventCost()
}

/** 金币来源。临时医棚等局内付费只能用 [RUN_GOLD]，不碰仓库钱包。 */
enum class EventCurrency { RUN_GOLD, WALLET_GOLD }

/** 价格取自定义里的固定值，还是节点实例上已固定的价格。 */
enum class EventCostSource { FIXED, INSTANCE_PRICE }

/** 交物要求的种类。 */
enum class EventItemRequirementKind {
    /** 任意一件可交付物品（拦路者）。 */
    ANY_DELIVERABLE,
    /** 必须是药品（临时医棚强化治疗）。 */
    MEDICINE,
    /** 必须是指定物品定义的实例（技术终端换刻片）。 */
    DEFINITION
}

/**
 * 交物选项对「玩家交出什么」的要求。
 *
 * [definitionId] 为空且 kind 为 [EventItemRequirementKind.DEFINITION] 时，
 * 取节点实例上的 `requiredItemDefinitionId`。
 */
data class EventItemRequirement(
    val kind: EventItemRequirementKind,
    val definitionId: String = "",
    val allowSafeContainer: Boolean = false
)

/**
 * 玩家明确选中的待交付实例。
 *
 * [allowSafeContainer] 为 true 表示玩家**主动授权**交出安全箱里的这件物品；
 * 事件本身永远不会把安全箱当作默认来源。
 */
data class EventItemSelection(
    val instanceId: String,
    val allowSafeContainer: Boolean = false
)

/** 一个事件选项。 */
data class EventOption(
    val id: String,
    val label: String,
    val risk: EventRisk,
    val conditions: List<EventCondition> = emptyList(),
    val cost: EventCost? = null,
    /** 交物类选项必须由玩家明确选择实例，事件不得自动代选。 */
    val requiresExplicitItemChoice: Boolean = false,
    /** 交物类选项对实例的要求；非 null 时结算必须带 [EventItemSelection]。 */
    val itemRequirement: EventItemRequirement? = null,
    /**
     * 该选项的结果**可能造成死亡**（不只是「可能受伤」）。
     *
     * 为 true 时文案必须含「可能死亡」，界面提前提示，且不公布精确概率；
     * 未告知致死风险的选项不接受 [EventOutcome.possibleDeath] 的结果。
     */
    val canLethal: Boolean = false
)

/** 一类事件的静态定义（不含随机结果）。 */
data class EventDefinition(
    val kind: EventKind,
    val title: String,
    val options: List<EventOption>
) {
    init {
        require(options.isNotEmpty()) { "事件必须至少有一个选项: $kind" }
        val ids = HashSet<String>()
        for (option in options) {
            require(option.id.isNotBlank()) { "选项 id 不能为空白: $kind" }
            require(ids.add(option.id)) { "选项 id 重复: $kind -> ${option.id}" }
            require(option.label.isNotBlank()) { "选项文案不能为空白: ${option.id}" }
            if (option.cost is EventCost.Item) {
                require(option.requiresExplicitItemChoice) {
                    "交付物品的选项必须要求玩家明确选择实例: ${option.id}"
                }
            }
            if (option.itemRequirement != null && option.cost !is EventCost.Item) {
                require(option.requiresExplicitItemChoice) {
                    "带交物要求的选项必须要求玩家明确选择实例: ${option.id}"
                }
            }
            // 致死风险必须在文案里披露；不为省文案把伤害改成不致死。
            if (option.risk == EventRisk.DEATH || option.canLethal) {
                require(DEATH_LABEL in option.label) {
                    "可能造成死亡的选项必须标注「$DEATH_LABEL」: ${option.id}"
                }
            }
        }
    }

    companion object {
        /** 致死选项文案中必须出现的字样。 */
        const val DEATH_LABEL: String = "可能死亡"
    }
}

/** 容器里的一格（row 从 0 起，col 从 0 起）。 */
data class EventCell(val row: Int, val col: Int) {
    init {
        require(row >= 0 && col >= 0) { "格子坐标不能为负: ($row, $col)" }
    }
}

/**
 * 一个容器当前的空间信息，用于奖励容量判定。
 *
 * @property occupied 已占用格；**null 表示上层没有提供摆放快照**，此时只有
 *           「整箱为空（freeCells == width * height）」才能证明连续空间存在，
 *           其余一律 [EventGrantFit.NEEDS_PLACEMENT_CHECK]，不得当作 FITS（R03）。
 */
data class EventContainerSpace(
    val width: Int,
    val height: Int,
    val freeCells: Int,
    val occupied: Set<EventCell>? = null
) {
    init {
        require(width >= 0 && height >= 0) { "容器尺寸不能为负: ${width}x$height" }
        require(freeCells >= 0) { "空闲格数不能为负: $freeCells" }
        require(freeCells <= width * height) {
            "空闲格数超过容器总格数: $freeCells > ${width * height}"
        }
        if (occupied != null) {
            for (cell in occupied) {
                require(cell.row < height && cell.col < width) {
                    "占用格越界: $cell 不在 ${width}x$height 内"
                }
            }
        }
    }
}

/** 物品实例的语义分类，用于交物要求的类型校验。 */
enum class EventItemType { MEDICINE, MATERIAL, EQUIPMENT, SKILL_CARD, OTHER }

/** 物品实例当前所在的容器。 */
enum class EventItemContainer { BACKPACK, SAFE, WAREHOUSE }

/**
 * 一件物品实例的真实信息（R02）。
 *
 * 交物校验一律以 [definitionId] / [type] / [container] 精确比对，
 * 不通过实例 ID 前缀推断定义。
 */
data class EventItemInfo(
    val instanceId: String,
    val definitionId: String,
    val type: EventItemType = EventItemType.OTHER,
    val container: EventItemContainer = EventItemContainer.BACKPACK
) {
    init {
        require(instanceId.isNotBlank()) { "instanceId 不能为空白" }
        require(definitionId.isNotBlank()) { "definitionId 不能为空白: $instanceId" }
    }
}

/** 事件选项求值所需的当前状态快照。全部由上层填充，本模型不读存档。 */
data class EventContext(
    val runGold: Int = 0,
    val walletGold: Int = 0,
    val synergyRanks: Map<String, Int> = emptyMap(),
    val clears: Int = 0,
    val skillSlots: Int = 0,
    val backpack: EventContainerSpace = EventContainerSpace(0, 0, 0),
    val safe: EventContainerSpace = EventContainerSpace(0, 0, 0),
    val itemInstanceIds: Set<String> = emptySet(),
    val safeInstanceIds: Set<String> = emptySet(),
    /** 实例 ID → 真实物品信息；交物校验的唯一依据。 */
    val items: Map<String, EventItemInfo> = emptyMap()
)

/** 待发放奖励的占格（用于容量判定；支持旋转）。 */
data class EventGrant(val definitionId: String, val width: Int, val height: Int,
                      val container: EventGrantContainer) {
    init {
        require(definitionId.isNotBlank()) { "definitionId 不能为空白" }
        require(width > 0 && height > 0) { "占格尺寸必须为正: ${width}x$height" }
    }
}

/** 奖励进入哪个容器。 */
enum class EventGrantContainer { BACKPACK, SAFE }

/**
 * 奖励容量判定结果。
 *
 * - [FITS]：已证明存在可容纳（含旋转）的**连续矩形**空间；
 * - [NEEDS_ORGANIZE]：面积够但没有现成连续空间，上层必须走现有整理 / 取舍界面，
 *   **不得凭空把物品塞进安全箱**——安全箱只在玩家明确放入时持有物品；
 * - [NEEDS_PLACEMENT_CHECK]：上层没给摆放快照，无法证明连续空间存在。
 *   这是「待校验」而不是「可放置」，上层必须补快照或走实际摆放事务后再判定；
 * - [DOES_NOT_FIT]：容器尺寸（含旋转）根本容不下。
 */
enum class EventGrantFit { FITS, NEEDS_ORGANIZE, NEEDS_PLACEMENT_CHECK, DOES_NOT_FIT }

/**
 * 判定一件奖励能否放进目标容器。
 *
 * **空闲面积足够不等于有连续空间**（R03）：给了摆放快照时按实际网格扫描
 * 两种朝向；没给快照时只有整箱为空才敢返回 [EventGrantFit.FITS]。
 * 无论返回什么，最终发物仍走库存事务——FITS 只是初筛，不是跳过摆放校验的授权。
 */
fun fitGrant(grant: EventGrant, context: EventContext): EventGrantFit {
    val space = when (grant.container) {
        EventGrantContainer.BACKPACK -> context.backpack
        EventGrantContainer.SAFE -> context.safe
    }
    val fitsUpright = grant.width <= space.width && grant.height <= space.height
    val fitsRotated = grant.height <= space.width && grant.width <= space.height
    if (!fitsUpright && !fitsRotated) return EventGrantFit.DOES_NOT_FIT

    val area = grant.width * grant.height
    if (space.freeCells < area) return EventGrantFit.NEEDS_ORGANIZE

    val occupied = space.occupied
    if (occupied == null) {
        return if (space.freeCells == space.width * space.height) EventGrantFit.FITS
        else EventGrantFit.NEEDS_PLACEMENT_CHECK
    }
    val upright = hasFreeRectangle(space, occupied, grant.width, grant.height)
    val rotated = hasFreeRectangle(space, occupied, grant.height, grant.width)
    return if (upright || rotated) EventGrantFit.FITS else EventGrantFit.NEEDS_ORGANIZE
}

/** 在 [occupied] 之外能否找到一块 w x h 的连续空矩形。 */
private fun hasFreeRectangle(
    space: EventContainerSpace,
    occupied: Set<EventCell>,
    w: Int,
    h: Int
): Boolean {
    if (w > space.width || h > space.height) return false
    for (row in 0..(space.height - h)) {
        for (col in 0..(space.width - w)) {
            var free = true
            for (dr in 0 until h) {
                for (dc in 0 until w) {
                    if (EventCell(row + dr, col + dc) in occupied) {
                        free = false
                        break
                    }
                }
                if (!free) break
            }
            if (free) return true
        }
    }
    return false
}

/**
 * 节点事件的运行时快照。
 *
 * 进入节点时一次性生成（事件类别、随机结果、所需物品、价格全部固定）并持久化；
 * 离开重进、反复查看选项都读同一份快照，不重抽。
 */
data class NodeEventInstance(
    val nodeId: String,
    val kind: EventKind,
    val roll: Int,
    val requiredItemDefinitionId: String = "",
    val priceGold: Int = 0,
    val resolvedOptionId: String? = null,
    /** Adapter freezes generated outcomes; selected handover instance is filled only on confirmation. */
    val outcomes: Map<String, EventOutcome> = emptyMap()
) {
    init {
        require(nodeId.isNotBlank()) { "nodeId 不能为空白" }
        require(roll in 0..99) { "roll 必须在 0..99: $roll" }
        require(priceGold >= 0) { "priceGold 不能为负: $priceGold" }
    }

    val resolved: Boolean get() = resolvedOptionId != null
}

/**
 * 一次选择的结算描述：由上层据此原子地改动金币 / 生命 / 物品。
 *
 * 这里**不携带概率字段**——隐藏精确概率不向界面泄露。
 * [consumeInstanceIds] 会与玩家选择、实际持有、安全箱授权逐项校验，
 * 任一项不符即整体拒绝（零消费）。
 */
data class EventOutcome(
    val nodeId: String,
    val optionId: String,
    val goldDelta: Int = 0,
    val currency: EventCurrency = EventCurrency.RUN_GOLD,
    /** 生命变化，按最大生命的百分比；负值为受伤。 */
    val hpDeltaPercentOfMax: Double = 0.0,
    val grantDefinitionIds: List<String> = emptyList(),
    val consumeInstanceIds: List<String> = emptyList(),
    val applyNormalPoisonLayers: Int = 0,
    val poisonDamagePerTick: Double = 0.0,
    /** 该结果可能造成死亡；只允许出现在已向玩家披露致死风险的选项上。 */
    val possibleDeath: Boolean = false,
    /** 稳定结局 ID；节点生成时固定，读档不重算。 */
    val outcomeId: String = "",
    /** 结局插画键；视觉层只读取该键，不根据中文标题反推。 */
    val artKey: String = ""
) {
    init {
        require(hpDeltaPercentOfMax.isFinite()) { "hpDeltaPercentOfMax 必须 finite: $hpDeltaPercentOfMax" }
        require(applyNormalPoisonLayers >= 0) { "applyNormalPoisonLayers 不能为负: $applyNormalPoisonLayers" }
        require(poisonDamagePerTick.isFinite() && poisonDamagePerTick >= 0.0) {
            "poisonDamagePerTick 必须 finite 且 >= 0: $poisonDamagePerTick"
        }
    }
}

/** 展示层的一个选项视图。 */
data class EventOptionView(
    val option: EventOption,
    val enabled: Boolean,
    val blockedReason: EventBlockReason?,
    /** 是否向玩家显示「可能死亡」提示；由选项声明决定，不含概率或精确数值。 */
    val showsLethalWarning: Boolean = false
)

/** 选项被禁的原因。 */
enum class EventBlockReason {
    OPTION_NOT_FOUND,
    CONDITION_UNMET,
    INSUFFICIENT_GOLD,
    ITEM_INSTANCE_MISSING,
    ITEM_CHOICE_REQUIRED,
    /** 选中的实例类型 / 定义与要求不符（含「上下文无该实例信息，无法证明匹配」）。 */
    ITEM_TYPE_MISMATCH,
    /** 试图交出安全箱物品而玩家未明确授权。 */
    SAFE_CONTAINER_NOT_AUTHORIZED,
    /** 结算描述可能造成死亡，但该选项并未向玩家披露致死风险。 */
    LETHAL_NOT_DISCLOSED,
    ALREADY_RESOLVED
}

/** 一次结算的返回：成功标志 + 新实例 + 结果描述。 */
data class EventResolveResult(
    val ok: Boolean,
    val instance: NodeEventInstance,
    val outcome: EventOutcome?,
    val blockReason: EventBlockReason? = null,
    val alreadyResolved: Boolean = false
)
