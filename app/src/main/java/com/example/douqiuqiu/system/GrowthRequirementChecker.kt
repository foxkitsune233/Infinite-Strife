package com.example.douqiuqiu.system

/**
 * 成长购买条件检查器（纯 Kotlin，不依赖 Android / 系统时钟）。
 *
 * 供英雄解锁、技能栏扩展、背包和安全箱升级共用：传入需求与当前资源，
 * 一次性返回是否满足及**全部**缺口，不只报第一项失败。
 *
 * 本工具不决定价格或进度门槛——数值全部由调用方传入，主任务调整成长表无需改动这里。
 * 它只回答问题「现阶段的资源与资格够不够」，不扣钱、不消耗材料、不执行升级、不选材料实例，
 * 也不判断是否在局外 / 已解锁 / 可重复买 / 是否上限 / 档位是否连续等业务阶段问题。
 *
 * requiredLevels 是其他永久成长的最低档位（不是本次购买数量）：例如安全箱最后一档
 * 要求背包至少 4×3，调用方把背包档位定义为初始 0、4×3 为 1，传 {"backpack": 1}；
 * 工具不理解这些 ID 与档位名称的含义。
 */
object GrowthRequirementChecker {

    /**
     * 评估一次成长购买条件。
     *
     * 资源可以超过要求，无需完全相等；所有缺口为 0/空且不需要撤离时 [GrowthCheckResult.canPurchase] 为 true。
     * 返回结果是本次计算的独立快照：不修改输入 Map，也不把输入 Map 直接作为结果返回。
     */
    fun evaluate(
        requirement: GrowthRequirement,
        resources: GrowthResources
    ): GrowthCheckResult {
        validate(requirement, resources)

        val missingGold = maxOf(0L, requirement.goldCost - resources.gold)

        // 材料逐 ID 检查，库存缺失视为 0，只返回缺口大于 0 的项
        val missingMaterials = LinkedHashMap<String, Int>()
        for ((id, need) in requirement.materials) {
            if (need <= 0) continue
            val gap = need - (resources.warehouseMaterials[id] ?: 0)
            if (gap > 0) missingMaterials[id] = gap
        }

        // 是否完成撤离由调用方提供，不从通关数擅自推导
        val needsExtraction = requirement.requiresExtraction && !resources.hasExtracted

        val missingClears = maxOf(0, requirement.minClears - resources.clearCount)

        // 前置成长同材料规则：缺失档位视为 0，只返回正缺口
        val missingLevels = LinkedHashMap<String, Int>()
        for ((id, need) in requirement.requiredLevels) {
            if (need <= 0) continue
            val gap = need - (resources.levels[id] ?: 0)
            if (gap > 0) missingLevels[id] = gap
        }

        val canPurchase = missingGold == 0L &&
            missingMaterials.isEmpty() &&
            !needsExtraction &&
            missingClears == 0 &&
            missingLevels.isEmpty()

        return GrowthCheckResult(
            canPurchase = canPurchase,
            missingGold = missingGold,
            missingMaterials = missingMaterials,
            needsExtraction = needsExtraction,
            missingClears = missingClears,
            missingLevels = missingLevels
        )
    }

    // ---------------- 输入校验 ----------------

    private fun validate(requirement: GrowthRequirement, resources: GrowthResources) {
        require(requirement.goldCost >= 0) { "goldCost 不能为负，当前 ${requirement.goldCost}" }
        require(resources.gold >= 0) { "gold 不能为负，当前 ${resources.gold}" }
        require(requirement.minClears >= 0) { "minClears 不能为负，当前 ${requirement.minClears}" }
        require(resources.clearCount >= 0) { "clearCount 不能为负，当前 ${resources.clearCount}" }

        // 需求与库存两侧都全量校验：未被需求使用的负库存项同样算非法输入
        requirement.materials.forEach { (id, n) -> validateCount("需求材料", id, n) }
        resources.warehouseMaterials.forEach { (id, n) -> validateCount("库存材料", id, n) }
        requirement.requiredLevels.forEach { (id, lv) -> validateCount("前置成长档位", id, lv) }
        resources.levels.forEach { (id, lv) -> validateCount("当前成长档位", id, lv) }
    }

    private fun validateCount(kind: String, id: String, value: Int) {
        require(id.isNotBlank()) { "$kind 的 ID 不能为空或全空白" }
        require(value >= 0) { "$kind [$id] 的数量不能为负，当前 $value" }
    }
}

/** 一项成长购买的需求：金币、材料、撤离资格、通关数、前置成长档位 */
data class GrowthRequirement(
    val goldCost: Long,
    val materials: Map<String, Int> = emptyMap(),
    val requiresExtraction: Boolean = false,
    val minClears: Int = 0,
    val requiredLevels: Map<String, Int> = emptyMap()
)

/** 当前资源：金币、仓库可消耗材料、是否完成过撤离、通关数、其他成长档位 */
data class GrowthResources(
    val gold: Long,
    val warehouseMaterials: Map<String, Int> = emptyMap(),
    val hasExtracted: Boolean = false,
    val clearCount: Int = 0,
    val levels: Map<String, Int> = emptyMap()
)

/** 检查结果：是否可购买 + 全部缺口（0/空表示无缺口） */
data class GrowthCheckResult(
    val canPurchase: Boolean,
    val missingGold: Long,
    val missingMaterials: Map<String, Int>,
    val needsExtraction: Boolean,
    val missingClears: Int,
    val missingLevels: Map<String, Int>
)
