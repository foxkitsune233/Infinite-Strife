package com.example.douqiuqiu.journey

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.cards.SynergyCard
import com.example.douqiuqiu.cards.SynergyCounter
import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.inventory.*

/** Eight event templates backed by one persisted roll. Failed choices return the exact input state. */
object JourneyEvents {
    private val medicines = setOf("salve", "tango", "faerie_fire", "cheese")
    private val components = EquipmentCatalog.components.map { it.id }.toSet() +
        setOf("iron_branch", "circlet", "gauntlets", "slippers", "mantle", "broadsword")

    fun generate(s: InventoryState, r: JourneyState, definitions: Map<String, ItemDefinition>): NodeEventInstance {
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
        val kind = candidates[(seed % candidates.size).toInt()]
        val node = EventCatalog.createInstance(
            nodeId = "${r.id}:event:${r.round}", kind = kind, seed = FirstRunContent.next(seed),
            requiredItemDefinitionId = if (kind == EventKind.TECH_TERMINAL) pools.general.first() else "",
            priceGold = 20 + r.round * 10 + r.target.difficulty * 10
        )
        return node.copy(outcomes = EventCatalog.definition(kind).options.associate { option ->
            option.id to outcome(node, option, pools, null)
        })
    }

    fun views(s: InventoryState, inventory: InventoryRules, selection: EventItemSelection? = null): List<EventOptionView> {
        val r = s.progress.run ?: return emptyList()
        val node = r.nodeEvent ?: return emptyList()
        if (r.stage != JourneyStage.EVENT) return emptyList()
        return EventCatalog.evaluate(EventCatalog.definition(node.kind), node, context(s, inventory), selection).map { view ->
            if (!available(view.option.id, pools(s, inventory.definitions), s)) {
                view.copy(enabled = false, blockedReason = EventBlockReason.CONDITION_UNMET)
            } else view
        }
    }

    fun resolve(s: InventoryState, inventory: InventoryRules, optionId: String,
                selection: EventItemSelection? = null): InventoryResult {
        fun fail(message: String) = InventoryResult(s, message)
        inventory.validate(s)?.let { return fail(it) }
        val r = s.progress.run ?: return fail("没有挑战")
        if (r.stage != JourneyStage.EVENT || s.phase != InventoryPhase.PREPARATION) return fail("当前没有事件")
        val node = r.nodeEvent ?: return fail("事件快照缺失")
        val option = EventCatalog.definition(node.kind).options.find { it.id == optionId }
            ?: return fail("事件选项不存在")
        val pools = pools(s, inventory.definitions)
        if (!available(optionId, pools, s)) return fail("当前进度或物品配置不支持此选项")
        val base = node.outcomes[optionId]?.let { frozen ->
            if (selection == null) frozen else frozen.copy(consumeInstanceIds = listOf(selection.instanceId))
        } ?: outcome(node, option, pools, selection)
        // 卖价必须跟随玩家实际选择的实例，因此在结算时按该实例的定义价格补齐。
        val outcome = if (option.id == "roving_caravan.sell" && selection != null)
            base.copy(goldDelta = saleValue(
                s.items.firstOrNull { it.instanceId == selection.instanceId }?.definitionId,
                inventory.definitions))
        else base
        val checked = EventCatalog.resolve(EventCatalog.definition(node.kind), node, optionId,
            context(s, inventory), outcome, selection)
        if (!checked.ok) return fail(reason(checked.blockReason))

        // Work only on immutable candidates: even consuming a chosen item to make space is rolled back on failure.
        val consumed = outcome.consumeInstanceIds.toSet()
        var next = s.copy(runGold = s.runGold + outcome.goldDelta, items = s.items.mapNotNull { item ->
            if (item.instanceId !in consumed) item else if (item.count > 1) item.copy(count = item.count - 1) else null
        })
        // 事件不存在可持久化的场外中毒池：普通毒按规则立即结算三跳，避免把配置字段留成死数据。
        val eventPoisonDamage = outcome.applyNormalPoisonLayers * outcome.poisonDamagePerTick * 3.0
        val hp = (r.hp + r.maxHp * outcome.hpDeltaPercentOfMax / 100.0 - eventPoisonDamage).coerceIn(0.0, r.maxHp)
        val drops = if (hp > 0 && outcome.grantDefinitionIds.isNotEmpty()) NodeLootRules.generate(next, inventory.definitions,
            NodeLootSource.EVENT, r.rng, outcome.grantDefinitionIds) else null
        val foundGold = if (drops == null) 0 else minOf(
            EconomyBalance.goldReward(r.target.difficulty, r.round, r.rewardVersion) / 2,
            (Int.MAX_VALUE.toLong() - next.gold - next.runGold).coerceAtLeast(0).toInt())
        next = next.copy(runGold = next.runGold + foundGold)
        next = next.copy(phase = InventoryPhase.PREPARATION, progress = next.progress.copy(run = r.copy(
            hp = hp, round = if (hp > 0 && drops == null) r.round + 1 else r.round,
            stage = if (hp <= 0) JourneyStage.EVENT else if (drops != null) JourneyStage.REWARD else JourneyStage.READY,
            nodeEvent = if (hp > 0) null else checked.instance, fight = null,
            nodeLoot = drops?.first?.copy(goldAwarded = foundGold), rng = drops?.second ?: r.rng
        )))
        inventory.validate(next)?.let { return fail(it) }
        return InventoryResult(next)
    }

    private data class Pools(val medicine: List<String>, val component: List<String>, val card: List<String>,
                             val general: List<String>, val core: List<String>, val shard: List<String>,
                             val rageCard: List<String> = emptyList(),
                             val frostCard: List<String> = emptyList(),
                             val definitions: Map<String, ItemDefinition> = emptyMap())

    private fun pools(s: InventoryState, defs: Map<String, ItemDefinition>): Pools {
        val run = s.progress.run
        val eligible = defs.values.filter { it.minimumDropClears <= s.progress.clears &&
            (run == null || run.rewardVersion < 3 || ChallengeBalance.allowsDrop(it, run.target.difficulty, run.rewardVersion)) }.sortedBy { it.id }
        fun ids(predicate: (ItemDefinition) -> Boolean) = eligible.filter(predicate).map { it.id }
        val cards = ids { it.kind == ItemKind.CARD }
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
        )
    }

    private fun available(optionId: String, p: Pools, s: InventoryState): Boolean = when (optionId) {
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
    }

    private fun outcome(node: NodeEventInstance, option: EventOption, p: Pools, selection: EventItemSelection?): EventOutcome {
        var hp = 0.0
        var grants = emptyList<String>()
        var poisonLayers = 0
        fun grant(pool: List<String>) { if (pool.isNotEmpty()) grants = listOf(pool[(node.roll / 10) % pool.size]) }
        when (option.id) {
            "depot.search" -> if (node.roll < 70) grant(p.medicine) else hp = -10.0
            "workshop.pry" -> if (node.roll < 60) grant(p.component) else hp = -12.0
            "workshop.pay" -> grant(p.component)
            "library.delve" -> { grant(p.card); if (node.roll >= 60) hp = -15.0 }
            "library.study" -> grant(p.card)
            "storage.left" -> { hp = -10.0; if (node.roll < 60) grant(p.general) }
            "wreckage.core" -> grant(p.core)
            "wreckage.salvage" -> grant(p.general)
            "terminal.convert" -> grant(p.shard)
            "terminal.gamble" -> if (node.roll < 50) grant(p.shard) else hp = -15.0
            "clinic.gold" -> hp = 25.0
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
        )
    }

    /** 从池中确定性地取 [count] 件不同定义（roll 固定，读档不重抽）。 */
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
        return SynergyCounter.count(cards).any { it.activeTiers.size >= 2 }
    }

    /** 卖出所得：按物品定义价格结算，至少 1 金币；未知道具按 1 金币处理。 */
    private fun saleValue(definitionId: String?, defs: Map<String, ItemDefinition>): Int =
        definitionId?.let { defs[it]?.price }?.coerceAtLeast(1) ?: 1


    private fun context(s: InventoryState, inventory: InventoryRules): EventContext {
        val held = s.items.filter { it.place.container != Container.WAREHOUSE }
        val cards = held.filter { it.place.container == Container.SKILLS }.mapNotNull { item ->
            CardCombat.definitions[item.definitionId]?.let { SynergyCard(item.instanceId, item.definitionId, it.tags, item.star) }
        }
        return EventContext(runGold = s.runGold, walletGold = s.gold, clears = s.progress.clears, skillSlots = s.skillSlots,
            synergyRanks = SynergyCounter.count(cards).associate { it.schoolId to it.activeTiers.size },
            itemInstanceIds = held.map { it.instanceId }.toSet(),
            safeInstanceIds = held.filter { it.place.container == Container.SAFE }.map { it.instanceId }.toSet(),
            items = held.associate { item -> item.instanceId to EventItemInfo(item.instanceId, item.definitionId,
                type = when {
                    item.definitionId in medicines -> EventItemType.MEDICINE
                    inventory.definitions[item.definitionId]?.kind == ItemKind.MATERIAL -> EventItemType.MATERIAL
                    inventory.definitions[item.definitionId]?.kind == ItemKind.CARD -> EventItemType.SKILL_CARD
                    inventory.definitions[item.definitionId]?.kind == ItemKind.EQUIPMENT -> EventItemType.EQUIPMENT
                    else -> EventItemType.OTHER
                }, container = if (item.place.container == Container.SAFE) EventItemContainer.SAFE else EventItemContainer.BACKPACK)
            })
    }

    private fun reason(reason: EventBlockReason?): String = when (reason) {
        EventBlockReason.INSUFFICIENT_GOLD -> "本局金币不足"
        EventBlockReason.ITEM_CHOICE_REQUIRED -> "请明确选择要交付的物品"
        EventBlockReason.SAFE_CONTAINER_NOT_AUTHORIZED -> "交付安全箱物品需要明确授权"
        EventBlockReason.ITEM_INSTANCE_MISSING -> "找不到可交付的本局物品"
        EventBlockReason.ITEM_TYPE_MISMATCH -> "所选物品不符合要求"
        EventBlockReason.CONDITION_UNMET -> "未满足事件选项条件"
        EventBlockReason.ALREADY_RESOLVED -> "此事件已结算"
        else -> "当前无法执行此事件选项"
    }
}
