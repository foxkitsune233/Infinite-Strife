package com.example.douqiuqiu.shop

import com.example.douqiuqiu.inventory.*

data class StoredShopOffer(val definitionId: String, val shelf: ShopShelf, val price: Int, val stock: Int)
data class ShopSnapshot(val revision: Int, val nextSeed: Long, val offers: List<StoredShopOffer>, val purchases: Map<String, Int> = emptyMap())

/** Generates a shelf once; reopening and purchasing never recalculate its locked prices. */
class ShopRules(private val inventory: InventoryRules) {
    fun generate(s: InventoryState, preserveStock: Boolean = false): InventoryState {
        require(s.phase == InventoryPhase.HOME && s.progress.run == null)
        val old = s.progress.shop
        require(old == null || old.revision < Int.MAX_VALUE) { "商店批次已达上限" }
        val candidates = inventory.definitions.values.filter { it.shopStock > 0 }.map {
            ShopCandidate(it.id, it.shopShelf, it.minimumShopClears, it.shopStock)
        }
        val selection = ShopShelfSelector.select(candidates, s.progress.clears, old?.nextSeed ?: 1729L)
        val stage = (.9 + minOf(s.progress.clears, 5) * .04).coerceAtMost(1.1)
        val prices = ShopPriceLocker.lock(selection.offers.map {
            ShopPriceInput(it.definitionId, inventory.definitions.getValue(it.definitionId).price,
                stage, 1.0 + minOf(old?.purchases?.get(it.definitionId) ?: 0, 3) * .03)
        }).associateBy { it.definitionId }
        val offers = selection.offers.map { StoredShopOffer(it.definitionId, it.shelf, prices.getValue(it.definitionId).buyPrice, it.stock) }
        val stock = if (preserveStock) s.progress.shopStock.filterKeys { id -> offers.any { it.definitionId == id } }
            .mapValues { (id, n) -> minOf(n, offers.first { it.definitionId == id }.stock) } else emptyMap()
        return s.copy(progress = s.progress.copy(shop = ShopSnapshot((old?.revision ?: 0) + 1, selection.nextSeed, offers), shopStock = stock))
    }
}
