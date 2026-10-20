package com.example.douqiuqiu.growth

/**
 * 成长材料消耗规划（纯 Kotlin common，不扣金币 / 不扣物品 / 不升级 / 不读存档）。
 *
 * 只回答「这次成长要拿仓库里哪些具体材料实例」：材料不可堆叠，每条实例 = 1 份。
 * 局外成长限定使用仓库材料，绑定材料可以消耗（不可出售，优先用掉）。
 *
 * 03 号检查器继续负责资格与资源缺口检查；本工具给出实例级计划，由主任务把
 * 「资格 + 计划 + 扣金币 + 升级」作为一个原子事务提交，失败时不要部分扣减。
 */
data class GrowthMaterialInstance(
    val instanceId: String,
    val definitionId: String,
    val bound: Boolean = false,
    val inWarehouse: Boolean = true
)

data class GrowthMaterialPlan(
    val success: Boolean,
    val consumedInstanceIds: List<String>,
    val missing: Map<String, Int>
)

object GrowthMaterialPlanner {

    /**
     * 规划一次成长的材料消耗。
     *
     * 确定性选择：按 definitionId 字典序逐种处理；每种内优先消耗绑定材料
     * （保留普通材料的变现选择），同绑定状态按 instanceId 字典序，恰取所需份数。
     * 所有种类都够 → success=true，consumed 为按上述顺序的完整清单；
     * 任一不足 → success=false，consumed 为空，missing 只列不足种类的缺额（字典序）。
     * 空 costs 是合法的成功空计划。
     */
    fun plan(items: List<GrowthMaterialInstance>, costs: Map<String, Int>): GrowthMaterialPlan {
        validateItems(items)
        validateCosts(costs)

        // 仓库内可用实例：definitionId → 绑定优先 → instanceId 字典序
        val usable = items
            .filter { it.inWarehouse }
            .sortedWith(compareBy({ it.definitionId }, { !it.bound }, { it.instanceId }))

        val byDefinition = LinkedHashMap<String, MutableList<GrowthMaterialInstance>>()
        for (item in usable) {
            byDefinition.getOrPut(item.definitionId) { ArrayList() }.add(item)
        }

        val consumed = ArrayList<String>()
        val missing = LinkedHashMap<String, Int>()
        for (definitionId in costs.keys.sorted()) {
            val needed = costs.getValue(definitionId)
            val available = byDefinition[definitionId]
            if (available == null || available.size < needed) {
                missing[definitionId] = needed - (available?.size ?: 0)
                continue
            }
            for (i in 0 until needed) consumed.add(available[i].instanceId)
        }

        return if (missing.isEmpty()) {
            GrowthMaterialPlan(success = true, consumedInstanceIds = consumed, missing = emptyMap())
        } else {
            // 任一不足即整体失败：不返回可被误当成功的半份计划
            GrowthMaterialPlan(success = false, consumedInstanceIds = emptyList(), missing = missing)
        }
    }

    // ---------------- 内部 ----------------

    private fun validateItems(items: List<GrowthMaterialInstance>) {
        val seen = HashSet<String>()
        for (item in items) {
            require(item.instanceId.isNotBlank()) { "instanceId 不能为空白" }
            require(seen.add(item.instanceId)) { "instanceId 重复：${item.instanceId}" }
            require(item.definitionId.isNotBlank()) {
                "definitionId 不能为空白：instanceId=${item.instanceId}"
            }
        }
    }

    private fun validateCosts(costs: Map<String, Int>) {
        for ((definitionId, amount) in costs) {
            require(definitionId.isNotBlank()) { "costs 键不能为空白" }
            require(amount > 0) { "costs 数量必须 > 0：$definitionId=$amount" }
        }
    }
}
