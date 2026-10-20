package com.example.douqiuqiu.loot

/**
 * 材料保底状态机（纯 Kotlin common，无随机数 / 时钟 / 存档依赖）。
 *
 * 按材料类别独立累计保底资格，并在生成符合条件的宝箱时消耗。
 * 只处理调用方已经判定好的类别集合：不生成物品、不匹配主题、不判断进度或安全箱尺寸。
 *
 * 两类输入由调用方负责判定：
 *  - eligibleClearMisses：该类别已开放且仍有成长需求、匹配主题通关、且没有带出该类材料
 *    （死亡、普通撤离或不合格主题不能加入此集合）；
 *  - successfullyBroughtOut：本次确实带出的目标材料类别（从目标箱/途中/安全箱获得的分类由上层决定）。
 *
 * 调用方必须原子保存「生成内容 + 资格消耗」；本工具不提供重复结算事件 ID 去重，
 * 不得对同一已结算事件重复调用 recordOutcome。宝箱之后刷新、放弃或死亡不返还资格。
 */
object MaterialPityTracker {

    /**
     * 结算一次通关结局：成功带出的类别清零，合格未获得的类别累计 miss。
     *
     * 同一类别同时出现在两个集合时成功优先（不先 +1 再重置）；
     * miss 累计到阈值时 pending=true，已有 pending 时冻结在阈值，不积累第二份隐形资格；
     * 未在 state 出现但已在 policy 定义的类别按 (0,false) 处理。
     * 返回状态只保留原有类别与本次真正改变的类别，不强制填满所有 0 项。
     */
    fun recordOutcome(
        state: MaterialPityState,
        policy: MaterialPityPolicy,
        eligibleClearMisses: Set<String>,
        successfullyBroughtOut: Set<String>
    ): MaterialPityState {
        validatePolicy(policy)
        validateState(state, policy)
        validateCategories("eligibleClearMisses", eligibleClearMisses, policy)
        validateCategories("successfullyBroughtOut", successfullyBroughtOut, policy)

        val next = LinkedHashMap(state.counters)
        for (cat in successfullyBroughtOut) {
            putIfChanged(next, cat, MaterialPityCounter(0, false))
        }
        for (cat in eligibleClearMisses) {
            if (cat in successfullyBroughtOut) continue // 成功优先
            val threshold = policy.thresholds.getValue(cat)
            val current = next[cat] ?: MaterialPityCounter(0, false)
            if (current.pending) {
                putIfChanged(next, cat, MaterialPityCounter(threshold, true))
                continue
            }
            val misses = current.misses + 1
            putIfChanged(
                next,
                cat,
                if (misses >= threshold) MaterialPityCounter(threshold, true)
                else MaterialPityCounter(misses, false)
            )
        }
        return MaterialPityState(next)
    }

    /**
     * 生成阶段消耗资格：eligibleCategories 中具有 pending 的类别全部消耗，
     * 返回字典序类别列表，对应计数立即变 (0,false)。未匹配的类别不消耗。
     */
    fun consumeForGeneration(
        state: MaterialPityState,
        policy: MaterialPityPolicy,
        eligibleCategories: Set<String>
    ): PityGenerationResult {
        validatePolicy(policy)
        validateState(state, policy)
        validateCategories("eligibleCategories", eligibleCategories, policy)

        val guaranteed = eligibleCategories
            .filter { state.counters[it]?.pending == true }
            .sorted()

        val next = LinkedHashMap(state.counters)
        for (cat in guaranteed) {
            next[cat] = MaterialPityCounter(0, false)
        }
        return PityGenerationResult(MaterialPityState(next), guaranteed)
    }

    // ---------------- 内部工具 ----------------

    /** 只在真正变化时写入：未出现过的类别写入默认值 (0,false) 视为无变化 */
    private fun putIfChanged(target: MutableMap<String, MaterialPityCounter>, cat: String, value: MaterialPityCounter) {
        val existing = target[cat]
        if (existing == null) {
            if (value != MaterialPityCounter(0, false)) target[cat] = value
        } else if (existing != value) {
            target[cat] = value
        }
    }

    private fun validatePolicy(policy: MaterialPityPolicy) {
        require(policy.thresholds.isNotEmpty()) { "policy.thresholds 不能为空" }
        for ((id, threshold) in policy.thresholds) {
            require(id.isNotBlank()) { "类别 ID 不能为空或全空白" }
            require(threshold > 0) { "类别 $id 阈值必须为正，当前 $threshold" }
        }
    }

    private fun validateState(state: MaterialPityState, policy: MaterialPityPolicy) {
        for ((cat, counter) in state.counters) {
            require(cat.isNotBlank()) { "类别 ID 不能为空或全空白" }
            val threshold = policy.thresholds[cat]
                ?: throw IllegalArgumentException("state 引用未知类别：$cat")
            require(counter.misses in 0..threshold) {
                "类别 $cat misses 必须在 0..$threshold，当前 ${counter.misses}"
            }
            require(counter.pending == (counter.misses == threshold)) {
                "类别 $cat 的 pending 必须与 misses==阈值 一致，当前 misses=${counter.misses}"
            }
        }
    }

    private fun validateCategories(name: String, cats: Set<String>, policy: MaterialPityPolicy) {
        for (cat in cats) {
            require(cat.isNotBlank()) { "$name 的类别 ID 不能为空或全空白" }
            require(cat in policy.thresholds) { "$name 引用未知类别：$cat" }
        }
    }
}

/** 保底策略：类别 → 触发保底所需的合格未获得次数 */
data class MaterialPityPolicy(val thresholds: Map<String, Int>)

/** 单类别计数：累计 miss 数 + 是否已获得保底资格 */
data class MaterialPityCounter(val misses: Int = 0, val pending: Boolean = false)

/** 保底状态：类别 → 计数 */
data class MaterialPityState(val counters: Map<String, MaterialPityCounter> = emptyMap())

/** 生成结果：新状态快照 + 本次消耗（字典序）的保底类别 */
data class PityGenerationResult(
    val state: MaterialPityState,
    val guaranteedCategories: List<String>
)
