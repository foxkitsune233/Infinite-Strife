package com.example.douqiuqiu.inventory

/** Locks protect owned instances from sale and crafting consumption; placement and death rules are unchanged. */
class ItemLockRules(private val inventory: InventoryRules) {
    fun toggle(state: InventoryState, id: String): InventoryResult {
        inventory.validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase == InventoryPhase.BATTLE) return InventoryResult(state, "战斗中不能调整锁定")
        if (state.items.none { it.instanceId == id }) return InventoryResult(state, "找不到物品")
        val locks = state.progress.lockedItems
        val next = state.copy(progress = state.progress.copy(lockedItems = if (id in locks) locks - id else locks + id))
        return inventory.validate(next)?.let { InventoryResult(state, it) } ?: InventoryResult(next)
    }
}
