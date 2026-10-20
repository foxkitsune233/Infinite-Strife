package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.growth.*
import com.example.douqiuqiu.system.*

data class GrowthStatus(val canPurchase: Boolean, val message: String)

/** Qualification, exact instance selection, payment and permanent upgrade form one snapshot. */
class GrowthRules(private val inventory: InventoryRules) {
    fun status(s: InventoryState, id: String): GrowthStatus {
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return GrowthStatus(false, "仅局外可购买成长")
        val u = GrowthContent.upgrades.find { it.id == id } ?: return GrowthStatus(false, "成长项目不存在")
        if (u.kind == GrowthKind.HERO && u.heroId in s.progress.unlockedHeroes) return GrowthStatus(false, "英雄已解锁")
        if (u.kind != GrowthKind.HERO && GrowthContent.level(s, u.kind) != u.level - 1) return GrowthStatus(false, "需按顺序升级，不能重复购买或跳档")
        if (u.materials.any { (material, _) -> inventory.definitions[material]?.kind != ItemKind.MATERIAL }) return GrowthStatus(false, "成长材料配置缺失")
        val available = s.items.filter { it.place.container == Container.WAREHOUSE && inventory.definitions.getValue(it.definitionId).kind == ItemKind.MATERIAL }
            .groupingBy { it.definitionId }.eachCount()
        val checked = GrowthRequirementChecker.evaluate(GrowthRequirement(u.gold.toLong(), u.materials, u.extraction, u.minClears,
            if (u.backpackLevel > 0) mapOf("backpack" to u.backpackLevel) else emptyMap()),
            GrowthResources(s.gold.toLong(), available, s.progress.extracted, s.progress.clears,
                mapOf("backpack" to GrowthContent.level(s, GrowthKind.BACKPACK))))
        val missing = mutableListOf<String>()
        if (checked.missingGold > 0) missing += "金币还差${checked.missingGold}"
        checked.missingMaterials.forEach { (material, n) -> missing += "${inventory.definitions.getValue(material).name}还差${n}份（仅计算仓库）" }
        if (checked.needsExtraction) missing += "需要完成一次安全撤离或通关"
        if (checked.missingClears > 0) missing += "还需通关${checked.missingClears}次"
        if (checked.missingLevels.isNotEmpty()) missing += "背包至少扩至4×3"
        return GrowthStatus(checked.canPurchase, if (missing.isEmpty()) "资格与资源充足" else missing.joinToString("\n"))
    }

    fun purchase(s: InventoryState, id: String): InventoryResult {
        inventory.validate(s)?.let { return InventoryResult(s, it) }
        val checked = status(s, id)
        if (!checked.canPurchase) return InventoryResult(s, checked.message)
        val u = GrowthContent.upgrades.first { it.id == id }
        val plan = GrowthMaterialPlanner.plan(s.items.filter { inventory.definitions.getValue(it.definitionId).kind == ItemKind.MATERIAL }.map {
            GrowthMaterialInstance(it.instanceId, it.definitionId, it.bound, it.place.container == Container.WAREHOUSE)
        }, u.materials)
        if (!plan.success) return InventoryResult(s, "材料不足，未扣除任何资源")
        val consumed = plan.consumedInstanceIds.toSet()
        var next = s.copy(gold = s.gold - u.gold, items = s.items.filterNot { it.instanceId in consumed })
        next = when (u.kind) {
            GrowthKind.HERO -> next.copy(progress = next.progress.copy(unlockedHeroes = next.progress.unlockedHeroes + u.heroId!!))
            GrowthKind.SKILLS -> next.copy(skillSlots = 4 + u.level)
            GrowthKind.BACKPACK -> GrowthContent.backpackSizes[u.level].let { next.copy(backpackWidth = it.first, backpackHeight = it.second) }
            GrowthKind.SAFE -> GrowthContent.safeSizes[u.level].let { next.copy(safeWidth = it.first, safeHeight = it.second) }
        }
        return inventory.validate(next)?.let { InventoryResult(s, it) } ?: InventoryResult(next)
    }
}
