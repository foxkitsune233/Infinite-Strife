import io

p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyEvents.kt"
s = io.open(p, encoding="utf-8").read()

# ---------- generate: pool filters + 最近两次不重复 ----------
old = """    fun generate(s: InventoryState, r: JourneyState, definitions: Map<String, ItemDefinition>): NodeEventInstance {
        val pools = pools(s, definitions)
        val kinds = EventKind.entries.filter { kind -> when (kind) {
            EventKind.ABANDONED_DEPOT -> pools.medicine.isNotEmpty()
            EventKind.RUSTED_WORKSHOP -> pools.component.isNotEmpty()
            EventKind.RUINED_LIBRARY -> pools.card.isNotEmpty()
            EventKind.SAFE_WRECKAGE -> pools.core.isNotEmpty() && pools.general.isNotEmpty()
            EventKind.TECH_TERMINAL -> pools.shard.isNotEmpty() && pools.general.isNotEmpty()
            else -> true
        } }
        val seed = FirstRunContent.next(r.rng)
        val kind = kinds[(seed % kinds.size).toInt()]"""
new = """    fun generate(s: InventoryState, r: JourneyState, definitions: Map<String, ItemDefinition>): NodeEventInstance {
        val pools = pools(s, definitions)
        val kinds = EventKind.entries.filter { kind -> when (kind) {
            EventKind.ABANDONED_DEPOT -> pools.medicine.isNotEmpty()
            EventKind.RUSTED_WORKSHOP -> pools.component.isNotEmpty()
            EventKind.RUINED_LIBRARY -> pools.card.isNotEmpty()
            EventKind.SAFE_WRECKAGE -> pools.core.isNotEmpty() && pools.general.isNotEmpty()
            EventKind.TECH_TERMINAL -> pools.shard.isNotEmpty() && pools.general.isNotEmpty()
            EventKind.FROZEN_SHRINE -> pools.frostCard.isNotEmpty()
            EventKind.WAR_BANNER_RUINS -> pools.rageCard.isNotEmpty()
            EventKind.ROVING_CARAVAN -> pools.general.isNotEmpty() || pools.component.isNotEmpty()
            EventKind.ROPEWAY_BRIDGE -> pools.general.isNotEmpty() || pools.component.isNotEmpty()
            EventKind.PACT_ALTAR -> pools.medicine.isNotEmpty()
            EventKind.ALCHEMY_LAB -> pools.medicine.isNotEmpty() || pools.component.isNotEmpty()
            EventKind.HUNTER_CAMP -> pools.component.isNotEmpty() || pools.medicine.isNotEmpty()
            // 月井只在有羁绊可共鸣或至少有低档奖励池时出现
            EventKind.MOONWELL_ECHO -> hasSynergyTierTwo(s) || pools.general.isNotEmpty()
            else -> true
        } }
        // 最近两次不重复同类；候选不足时只放宽为排除最近一次，再不足则取全集。
        val recent = r.recentEventKinds
        val fresh = kinds.filter { it !in recent.takeLast(2) }
        val relaxed = fresh.ifEmpty { kinds.filter { it != recent.lastOrNull() } }
        val candidates = relaxed.ifEmpty { kinds }
        val seed = FirstRunContent.next(r.rng)
        val kind = candidates[(seed % candidates.size).toInt()]"""
assert old in s
s = s.replace(old, new, 1)

# ---------- views / resolve 调用 available ----------
old = """        return EventCatalog.evaluate(EventCatalog.definition(node.kind), node, context(s, inventory), selection).map { view ->
            if (!available(view.option.id, pools(s, inventory.definitions))) {"""
new = """        return EventCatalog.evaluate(EventCatalog.definition(node.kind), node, context(s, inventory), selection).map { view ->
            if (!available(view.option.id, pools(s, inventory.definitions), s)) {"""
assert old in s
s = s.replace(old, new, 1)

old = """        if (!available(optionId, pools)) return fail("当前进度或物品配置不支持此选项")"""
new = """        if (!available(optionId, pools, s)) return fail("当前进度或物品配置不支持此选项")"""
assert old in s
s = s.replace(old, new, 1)

# ---------- resolve: 卖出价格跟随所选实例 ----------
old = """        val outcome = node.outcomes[optionId]?.copy(consumeInstanceIds =
            if (option.requiresExplicitItemChoice && selection != null) listOf(selection.instanceId) else emptyList())
            ?: outcome(node, option, pools, selection)"""
new = """        val outcome = node.outcomes[optionId]?.let { frozen ->
            if (selection == null) frozen
            else frozen.copy(consumeInstanceIds = listOf(selection.instanceId),
                goldDelta = if (option.id == "roving_caravan.sell")
                    saleValue(selection.instanceId, inventory.definitions) else frozen.goldDelta)
        } ?: outcome(node, option, pools, selection)"""
assert old in s
s = s.replace(old, new, 1)

# ---------- Pools ----------
old = """    private data class Pools(val medicine: List<String>, val component: List<String>, val card: List<String>,
                             val general: List<String>, val core: List<String>, val shard: List<String>)"""
new = """    private data class Pools(val medicine: List<String>, val component: List<String>, val card: List<String>,
                             val general: List<String>, val core: List<String>, val shard: List<String>,
                             val rageCard: List<String> = emptyList(),
                             val frostCard: List<String> = emptyList(),
                             val definitions: Map<String, ItemDefinition> = emptyMap())"""
assert old in s
s = s.replace(old, new, 1)

old = """        return Pools(
            ids { it.kind == ItemKind.CONSUMABLE && it.id in medicines },
            ids { it.kind == ItemKind.EQUIPMENT && it.id in components },
            ids { it.kind == ItemKind.CARD },
            ids { it.kind == ItemKind.MATERIAL && it.id == "essence" },
            ids { it.kind == ItemKind.MATERIAL && (it.id == "safe_lock" && s.progress.clears >= 1 ||
                it.id == "safe_frame" && s.progress.clears >= 5 && s.backpackHeight >= 3) },
            ids { it.kind == ItemKind.MATERIAL && it.id == "skill_shard" }
        )"""
new = """        val cards = ids { it.kind == ItemKind.CARD }
        return Pools(
            ids { it.kind == ItemKind.CONSUMABLE && it.id in medicines },
            ids { it.kind == ItemKind.EQUIPMENT && it.id in components },
            cards,
            ids { it.kind == ItemKind.MATERIAL && it.id == "essence" },
            ids { it.kind == ItemKind.MATERIAL && (it.id == "safe_lock" && s.progress.clears >= 1 ||
                it.id == "safe_frame" && s.progress.clears >= 5 && s.backpackHeight >= 3) },
            ids { it.kind == ItemKind.MATERIAL && it.id == "skill_shard" },
            cards.filter { id -> CardCombat.definitions[id]?.tags?.contains("rage") == true },
            cards.filter { id -> CardCombat.definitions[id]?.tags?.contains("frost") == true },
            defs
        )"""
assert old in s
s = s.replace(old, new, 1)

# ---------- available ----------
old = """    private fun available(optionId: String, p: Pools): Boolean = when (optionId) {
        "depot.search" -> p.medicine.isNotEmpty()
        "workshop.pry", "workshop.pay" -> p.component.isNotEmpty()
        "library.delve", "library.study" -> p.card.isNotEmpty()
        "wreckage.core" -> p.core.isNotEmpty()
        "wreckage.salvage" -> p.general.isNotEmpty()
        "terminal.convert", "terminal.gamble" -> p.shard.isNotEmpty() && p.general.isNotEmpty()
        else -> true
    }"""
new = """    private fun available(optionId: String, p: Pools, s: InventoryState): Boolean = when (optionId) {
        "depot.search" -> p.medicine.isNotEmpty()
        "workshop.pry", "workshop.pay" -> p.component.isNotEmpty()
        "library.delve", "library.study" -> p.card.isNotEmpty()
        "wreckage.core" -> p.core.isNotEmpty()
        "wreckage.salvage" -> p.general.isNotEmpty()
        "terminal.convert", "terminal.gamble" -> p.shard.isNotEmpty() && p.general.isNotEmpty()
        // 0.1.5 新事件：奖励池为空时不提供该分支，避免生成必然空手的结果。
        "frozen_shrine.attune" -> p.frostCard.isNotEmpty()
        "war_banner.claim", "war_banner.blood_rite" -> p.rageCard.isNotEmpty()
        "roving_caravan.buy" -> p.general.isNotEmpty() || p.component.isNotEmpty()
        "ropeway_bridge.cross", "ropeway_bridge.repair" ->
            p.general.isNotEmpty() || p.component.isNotEmpty()
        "pact_altar.offer_medicine", "alchemy_lab.distill", "hunter_camp.share_rations" ->
            p.medicine.isNotEmpty()
        "alchemy_lab.break_seal" -> p.component.isNotEmpty()
        "hunter_camp.barter" -> p.component.isNotEmpty()
        "moonwell_echo.resonate" -> hasSynergyTierTwo(s)
        "moonwell_echo.draw_echo" -> p.general.isNotEmpty()
        else -> true
    }"""
assert old in s
s = s.replace(old, new, 1)

# ---------- outcome 新分支 ----------
old = """        when (option.id) {
            "depot.search" -> if (node.roll < 70) grant(p.medicine) else hp = -10.0"""
new = """        when (option.id) {
            "depot.search" -> if (node.roll < 70) grant(p.medicine) else hp = -10.0"""
assert old in s

old = """            "clinic.gold" -> hp = 25.0
            "clinic.supply" -> hp = 40.0
            "blocker.rush" -> hp = if (node.roll < 60) -10.0 else -25.0
        }
        return EventOutcome(node.nodeId, option.id,
            goldDelta = if (option.cost is EventCost.Gold) -node.priceGold else 0,
            hpDeltaPercentOfMax = hp, grantDefinitionIds = grants,
            consumeInstanceIds = if (option.requiresExplicitItemChoice && selection != null) listOf(selection.instanceId) else emptyList(),
            possibleDeath = hp < 0
        )"""
new = """            "clinic.gold" -> hp = 25.0
            "clinic.supply" -> hp = 40.0
            "blocker.rush" -> hp = if (node.roll < 60) -10.0 else -25.0

            // ---- 0.1.5 新增事件 ----
            "frozen_shrine.attune" -> { hp = -8.0; grant(p.frostCard.ifEmpty { p.card }) }
            "frozen_shrine.pray" -> hp = 6.0
            "war_banner.claim" -> grant(p.rageCard.ifEmpty { p.card })
            "war_banner.blood_rite" -> { hp = -15.0; grant(p.rageCard.ifEmpty { p.card }) }
            "roving_caravan.buy" -> grant(p.general.ifEmpty { p.component })
            // 卖出价格在结算时按所选实例计算，见 resolve()
            "roving_caravan.sell" -> Unit
            "ropeway_bridge.cross" -> { hp = -10.0; grants = pickMany(p.general.ifEmpty { p.component }, node.roll, 2) }
            "ropeway_bridge.repair" -> grant(p.component.ifEmpty { p.general })
            "pact_altar.offer_medicine" -> grant(p.card)
            "pact_altar.offer_blood" -> { hp = -18.0; grant(p.card) }
            "alchemy_lab.distill" -> { hp = 25.0; grant(p.general.ifEmpty { p.component }) }
            "alchemy_lab.break_seal" -> {
                hp = -8.0; poisonLayers = 1; grant(p.component)
            }
            "hunter_camp.barter" -> grant(p.component.ifEmpty { p.general })
            "hunter_camp.share_rations" -> { hp = 30.0; grant(p.shard.ifEmpty { p.general }) }
            "moonwell_echo.resonate" -> grants = pickMany(p.card, node.roll, 2)
            "moonwell_echo.draw_echo" -> grant(p.general.ifEmpty { p.component })
        }
        return EventOutcome(node.nodeId, option.id,
            goldDelta = if (option.cost is EventCost.Gold) -node.priceGold else 0,
            hpDeltaPercentOfMax = hp, grantDefinitionIds = grants,
            consumeInstanceIds = if (option.requiresExplicitItemChoice && selection != null) listOf(selection.instanceId) else emptyList(),
            applyNormalPoisonLayers = poisonLayers,
            poisonDamagePerTick = if (poisonLayers > 0) 3.0 else 0.0,
            possibleDeath = hp < 0,
            outcomeId = option.id,
            artKey = EventCatalog.artKey(node.kind, option.id)
        )"""
assert old in s
s = s.replace(old, new, 1)

old = """        var hp = 0.0
        var grants = emptyList<String>()
        fun grant(pool: List<String>) { if (pool.isNotEmpty()) grants = listOf(pool[(node.roll / 10) % pool.size]) }"""
new = """        var hp = 0.0
        var grants = emptyList<String>()
        var poisonLayers = 0
        fun grant(pool: List<String>) { if (pool.isNotEmpty()) grants = listOf(pool[(node.roll / 10) % pool.size]) }"""
assert old in s
s = s.replace(old, new, 1)

# ---------- helpers ----------
old = """    private fun context(s: InventoryState, inventory: InventoryRules): EventContext {"""
new = """    /** 从池中确定性地取 [count] 件不同定义（roll 固定，读档不重抽）。 */
    private fun pickMany(pool: List<String>, roll: Int, count: Int): List<String> {
        if (pool.isEmpty()) return emptyList()
        val picked = LinkedHashSet<String>()
        for (i in 0 until count) {
            var index = (roll / 10 + i * 7) % pool.size
            while (!picked.add(pool[index]) && picked.size < pool.size) index = (index + 1) % pool.size
            if (picked.size >= pool.size) break
        }
        return picked.toList()
    }

    /** 是否已有一项羁绊达到两档（月井共鸣门槛）。 */
    private fun hasSynergyTierTwo(s: InventoryState): Boolean {
        val cards = s.items.filter { it.place.container == Container.SKILLS }.mapNotNull { item ->
            CardCombat.definitions[item.definitionId]?.let { SynergyCard(item.instanceId, item.definitionId, it.tags, item.star) }
        }
        return SynergyCounter.count(cards).values.any { it.activeTiers.size >= 2 }
    }

    /** 卖出所得：按物品定义价格结算，至少 1 金币；未知道具按 1 金币处理。 */
    private fun saleValue(instanceId: String, defs: Map<String, ItemDefinition>): Int {
        val item = defs.values.firstOrNull { it.id == instanceId }
        return (item?.price ?: 1).coerceAtLeast(1)
    }

    private fun context(s: InventoryState, inventory: InventoryRules): EventContext {"""
assert old in s
s = s.replace(old, new, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("journey events patched")
