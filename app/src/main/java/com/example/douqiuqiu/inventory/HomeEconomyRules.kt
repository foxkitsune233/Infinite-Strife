package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.quest.*

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.system.*
import com.example.douqiuqiu.shop.ShopRules

/** Immutable transactions; the caller commits the complete result once before showing success. */
class HomeEconomyRules(private val inventory: InventoryRules) {
    private val journey = JourneyRules(inventory)

    fun recover(s: InventoryState, nowMs: Long): InventoryState {
        require(nowMs >= 0)
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return s
        val p = s.progress
        val now = maxOf(nowMs, p.shopSeen, p.economy.refreshAnchorMs, p.economy.reliefAnchorMs)
        val e = if (p.economy.initialized) p.economy else p.economy.copy(initialized = true, refreshAnchorMs = now, reliefAnchorMs = now)
        val refresh = ChargeRecovery.recover(RecoveryState(e.refreshCharges, e.refreshAnchorMs), REFRESH, now).state
        val relief = ChargeRecovery.recover(RecoveryState(e.reliefCharges, e.reliefAnchorMs), RELIEF, now).state
        val clock = ShopRefreshClock.evaluate(ShopRefreshState(p.shopCycle, p.shopSeen), SHOP, now)
        var next = s.copy(progress = p.copy(shopCycle = clock.state.lastAutomaticCycle, shopSeen = clock.state.maxSeenAtMs,
            economy = e.copy(refreshCharges = refresh.charges, refreshAnchorMs = refresh.anchorMs,
                reliefCharges = relief.charges, reliefAnchorMs = relief.anchorMs)))
        if (p.shop == null || clock.reason == ShopRefreshReason.AUTOMATIC) {
            next = ShopRules(inventory).generate(next, preserveStock = p.shop == null && clock.reason != ShopRefreshReason.AUTOMATIC)
        }
        return journey.ensureBatch(next)
    }

    fun refreshChest(s: InventoryState, id: String, nowMs: Long): InventoryResult {
        if (!atHome(s)) return InventoryResult(s, "挑战中不能刷新")
        if (s.progress.chests.none { it.id == id }) return InventoryResult(s, "宝箱不存在")
        val current = recover(s, nowMs)
        val e = current.progress.economy
        val consumed = ChargeRecovery.tryConsume(RecoveryState(e.refreshCharges, e.refreshAnchorMs), REFRESH, current.progress.shopSeen)
        val old = current.progress.chests.single { it.id == id }
        val cost = if (consumed.consumed) 0 else EconomyBalance.chestRefresh(old.quality)
        if (current.gold < cost) return InventoryResult(s, "免费次数已用完，付费刷新需要${cost}金币")
        val changed = journey.replaceChest(current, id)
        if (!changed.ok) return InventoryResult(s, changed.error)
        val counter = consumed.recovery.state
        return checked(s, changed.state.copy(gold = current.gold - cost, progress = changed.state.progress.copy(
            economy = e.copy(refreshCharges = counter.charges, refreshAnchorMs = counter.anchorMs))))
    }

    fun reliefUnavailable(s: InventoryState): String? {
        if (!atHome(s)) return "挑战中不能领取救济"
        val minimum = s.progress.chests.minOfOrNull { it.ticket } ?: return "请先生成宝箱"
        if (s.gold >= minimum) return "当前金币足够进入本批最低票价挑战"
        val assets = s.items.filterNot { it.bound }.sumOf { item ->
            val def = inventory.definitions.getValue(item.definitionId)
            val copies = if (def.kind == ItemKind.CARD) when (item.star) { 2 -> 3; 3 -> 9; else -> 1 } else 1
            def.price.toLong() * copies * item.count * 40 / 100
        }
        return if (assets >= 120) "可售物资回收总值达到120金币，请先整理出售" else null
    }

    fun claimRelief(s: InventoryState, nowMs: Long): InventoryResult {
        if (!atHome(s)) return InventoryResult(s, "挑战中不能领取救济")
        val current = recover(s, nowMs)
        reliefUnavailable(current)?.let { return InventoryResult(s, it) }
        val e = current.progress.economy
        if (e.reliefClaims == Int.MAX_VALUE) return InventoryResult(s, "救济记录已达上限")
        val consumed = ChargeRecovery.tryConsume(RecoveryState(e.reliefCharges, e.reliefAnchorMs), RELIEF, current.progress.shopSeen)
        if (!consumed.consumed) return InventoryResult(s, "救济次数已用完，需等待恢复")
        if ("broadsword" !in inventory.definitions || "salve" !in inventory.definitions) return InventoryResult(s, "救济物资配置缺失")
        val claim = e.reliefClaims + 1
        var next = current.copy(items = current.items + listOf(
            StoredItem("relief-$claim:weapon", "broadsword", ItemPlace(Container.WAREHOUSE), bound = true),
            StoredItem("relief-$claim:salve", "salve", ItemPlace(Container.WAREHOUSE), bound = true, count = 2)))
        if (next.progress.chests.none { EconomyBalance.subsidized(it) && it.ticket <= EconomyBalance.RELIEF_SUBSIDY }) {
            val replaced = journey.replaceChest(next, next.progress.chests.last().id, rescue = true)
            if (!replaced.ok) return InventoryResult(s, replaced.error)
            next = replaced.state
        }
        val counter = consumed.recovery.state
        return checked(s, next.copy(progress = next.progress.copy(economy = e.copy(reliefCharges = counter.charges,
            reliefAnchorMs = counter.anchorMs, reliefClaims = claim, vouchers = e.vouchers + 1))))
    }

    fun refreshShop(s: InventoryState, nowMs: Long): InventoryResult {
        if (!atHome(s)) return InventoryResult(s, "挑战中不能刷新商店")
        val clock = ShopRefreshClock.evaluate(ShopRefreshState(s.progress.shopCycle, s.progress.shopSeen), SHOP, nowMs, true)
        val cost = if (clock.reason == ShopRefreshReason.PAID) EconomyBalance.SHOP_REFRESH else 0
        if (s.gold < cost) return InventoryResult(s, "刷新商店需要${cost}金币")
        var next = recover(s, nowMs)
        if (clock.reason == ShopRefreshReason.PAID && s.progress.shop != null) next = ShopRules(inventory).generate(next)
        return checked(s, next.copy(gold = next.gold - cost))
    }

    fun buy(s: InventoryState, id: String, instanceId: String, nowMs: Long, expectedRevision: Int): InventoryResult {
        if (!atHome(s)) return InventoryResult(s, "挑战中不能购买")
        val current = recover(s, nowMs)
        // 跨格自动刷新后旧报价直接拒绝：不扣钱、不扣库存；refreshed 让 UI 能立即显示新货架。
        if (current.progress.shop?.revision != expectedRevision) {
            return InventoryResult(s, "商店已经刷新，请重新打开后选择", refreshed = current)
        }
        val result = SupplyRules(inventory).buy(current, id, instanceId)
        // 同批次内的失败（售罄/未上架/金币不足）也带上恢复后的快照，避免界面停留在过期数字上。
        return if (result.ok) checked(s, QuestTracker.record(result.state, QuestEvent.ShopPurchase)) else InventoryResult(s, result.error, refreshed = current)
    }

    fun refreshCountdown(s: InventoryState, nowMs: Long): Long? = ChargeRecovery.recover(
        RecoveryState(s.progress.economy.refreshCharges, s.progress.economy.refreshAnchorMs), REFRESH, maxOf(nowMs, s.progress.shopSeen)).nextChargeInMs
    fun reliefCountdown(s: InventoryState, nowMs: Long): Long? = ChargeRecovery.recover(
        RecoveryState(s.progress.economy.reliefCharges, s.progress.economy.reliefAnchorMs), RELIEF, maxOf(nowMs, s.progress.shopSeen)).nextChargeInMs
    fun shopCountdown(s: InventoryState, nowMs: Long): Long = ShopRefreshClock.evaluate(
        ShopRefreshState(s.progress.shopCycle, s.progress.shopSeen), SHOP, nowMs).nextAutomaticInMs

    private fun atHome(s: InventoryState) = s.phase == InventoryPhase.HOME && s.progress.run == null
    private fun checked(before: InventoryState, after: InventoryState): InventoryResult =
        inventory.validate(after)?.let { InventoryResult(before, it) } ?: InventoryResult(after)

    companion object {
        private val REFRESH = RecoveryPolicy(3, 1_800_000)
        private val RELIEF = RecoveryPolicy(2, 7_200_000)
        private val SHOP = ShopRefreshPolicy(7_200_000)
    }
}
