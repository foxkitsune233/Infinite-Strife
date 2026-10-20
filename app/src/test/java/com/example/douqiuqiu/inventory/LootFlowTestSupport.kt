package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.JourneyRules
import com.example.douqiuqiu.journey.NodeLootRules

/**
 * 0.1.4 之后的统一节点收尾工具。
 *
 * 战后「回血 / 金币 / 物品」三选一已永久取消，正确链路是：
 * `battleLoot()` 落地掉落与战斗金币 → 完成鉴定 → `finishLoot()` 进入下一节点或通关。
 * 测试里凡是要「结算当前奖励节点」的地方都走这里，避免再依赖已废弃的 `reward()`。
 */
object LootFlowTestSupport {
    fun finishReward(journey: JourneyRules, state: InventoryState, discard: Boolean = true): InventoryResult {
        var next = journey.battleLoot(state)
        val loot = next.progress.run?.nodeLoot
            ?: return InventoryResult(next, "节点掉落尚未生成")
        val duration = NodeLootRules.duration(loot, journey.inventory.definitions)
        while ((next.progress.run?.nodeLoot?.inspectionMs ?: duration) < duration) {
            val stepped = NodeLootRules.inspect(next, journey.inventory, 500)
            if (!stepped.ok) return stepped
            next = stepped.state
        }
        return journey.finishLoot(next, discard)
    }
}
