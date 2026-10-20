package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*

/** Basic supplies use the same wallet and ownership snapshot as the run. */
class SupplyRules(private val inventory: InventoryRules) {
    fun buy(s: InventoryState, definitionId: String, instanceId: String): InventoryResult {
        val def = inventory.definitions[definitionId] ?: return InventoryResult(s, "物品不存在")
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return InventoryResult(s, "挑战中不能购买")
        if (s.progress.clears < def.minimumShopClears) return InventoryResult(s, "此商品尚未开放")
        val shelf = s.progress.shop
        val offer = shelf?.offers?.find { it.definitionId == definitionId }
        if (shelf != null && offer == null) return InventoryResult(s, "此商品本轮未上架")
        val remaining = s.progress.shopStock[definitionId] ?: offer?.stock ?: def.shopStock
        val price = offer?.price ?: def.price
        if (def.shopStock <= 0 || remaining <= 0) return InventoryResult(s, "本轮已售完")
        if (s.gold < price) return InventoryResult(s, "金币不足")
        val next = s.copy(gold = s.gold - price, items = s.items + StoredItem(instanceId, definitionId, ItemPlace(Container.WAREHOUSE), bound = def.boundOnAcquire),
            progress = s.progress.copy(shopStock = s.progress.shopStock + (definitionId to remaining - 1),
                shop = shelf?.copy(purchases = shelf.purchases + (definitionId to minOf(100, (shelf.purchases[definitionId] ?: 0) + 1)))))
        return inventory.validate(next)?.let { InventoryResult(s, it) } ?: InventoryResult(next)
    }
    fun sell(s: InventoryState, instanceId: String): InventoryResult {
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return InventoryResult(s, "挑战中不能出售")
        val item = s.items.singleOrNull { it.instanceId == instanceId } ?: return InventoryResult(s, "物品不存在")
        if (instanceId in s.progress.lockedItems) return InventoryResult(s, "锁定物品不可出售")
        if (item.bound) return InventoryResult(s, "绑定物品不可出售")
        if (item.place.container != Container.WAREHOUSE) return InventoryResult(s, "请先放回仓库再出售")
        val value = saleValue(item)
        if (s.gold.toLong() + value > Int.MAX_VALUE) return InventoryResult(s, "金币超出上限")
        return InventoryResult(s.copy(gold = s.gold + value.toInt(), items = s.items.filterNot { it.instanceId == instanceId }))
    }

    /** Per-instance quote: stars and stacks use the same 40% rounding as a single sale. */
    fun saleValue(item: StoredItem): Long {
        val def = inventory.definitions.getValue(item.definitionId)
        val copies = if (def.kind == ItemKind.CARD) when (item.star) { 2 -> 3; 3 -> 9; else -> 1 } else 1
        return def.price.toLong() * copies * item.count * 40 / 100
    }

    /** All selections must be eligible; a rejected batch preserves the complete original snapshot. */
    fun sellMany(s: InventoryState, instanceIds: List<String>): InventoryResult {
        fun fail(reason: String) = InventoryResult(s, reason)
        inventory.validate(s)?.let { return fail(it) }
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return fail("挑战中不能出售")
        if (instanceIds.isEmpty()) return fail("请先选择出售物品")
        val ids = instanceIds.toSet()
        if (ids.size != instanceIds.size) return fail("出售物品实例重复")
        val owned = s.items.associateBy { it.instanceId }
        val selected = instanceIds.map { owned[it] ?: return fail("物品不存在") }
        if (ids.any { it in s.progress.lockedItems }) return fail("锁定物品不可出售")
        if (selected.any { it.bound }) return fail("绑定物品不可出售")
        if (selected.any { it.place.container != Container.WAREHOUSE }) return fail("请先放回仓库再出售")
        val value = selected.sumOf(::saleValue)
        if (s.gold.toLong() + value > Int.MAX_VALUE) return fail("金币超出上限")
        return InventoryResult(s.copy(gold = s.gold + value.toInt(), items = s.items.filterNot { it.instanceId in ids }))
    }
}
