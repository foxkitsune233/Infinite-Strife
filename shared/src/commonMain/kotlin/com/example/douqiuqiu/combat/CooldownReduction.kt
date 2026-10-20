package com.example.douqiuqiu.combat

/**
 * 外围任务17：冷却缩减计算（纯 Kotlin common，无 Android / java.* / 随机数 / 时钟依赖）。
 *
 * 多来源减CD按「各独立来源乘算剩余比例」结算：
 * ```
 * reduction       = min(0.6, 1 - Π(1 - fraction))
 * effectiveSeconds = max(minimumSeconds, baseSeconds * (1 - reduction))
 * ```
 *
 * uniqueGroup 非 null 且非空白表示同组只取 fraction 最高的 active 来源
 * （玲珑心等唯一组由上层给同组名），相同强度取 instanceId 字典序最小；
 * 无组来源各自保留，不按卡名去重。先确定同组获选者，再按 instanceId
 * 字典序乘算，保证输入重排结果稳定。
 *
 * appliedSourceIds 包含全部获选 active 来源（包括 0 减免），按字典序；
 * inactive 不参与计算，但仍走全量校验。空 sources 也要应用最短 CD。
 *
 * 本工具只返回 Double 秒：不做毫秒取整、时间推进或开战重置。
 * 普通默认下限 0.5 秒，幽魂护罩由调用方明确传 10 秒，不按英雄名字猜规则。
 * 10 号工具的场间固定减 30 秒不经过本工具。
 */
object CooldownReduction {

    /** 减免硬上限：最多 60% */
    const val MAX_REDUCTION: Double = 0.6

    fun calculate(
        baseSeconds: Double,
        sources: List<CooldownReductionSource>,
        minimumSeconds: Double = 0.5
    ): CooldownReductionResult {
        require(baseSeconds.isFinite() && baseSeconds > 0.0) {
            "baseSeconds 必须 finite 且 > 0，当前 $baseSeconds"
        }
        require(minimumSeconds.isFinite() && minimumSeconds > 0.0) {
            "minimumSeconds 必须 finite 且 > 0，当前 $minimumSeconds"
        }

        val seenIds = HashSet<String>()
        for (s in sources) {
            require(s.instanceId.isNotBlank()) { "instanceId 不能为空白" }
            require(seenIds.add(s.instanceId)) { "instanceId 重复：${s.instanceId}" }
            require(s.fraction.isFinite() && s.fraction in 0.0..1.0) {
                "fraction 必须 finite 且在 [0,1]，当前 ${s.fraction}（${s.instanceId}）"
            }
            if (s.uniqueGroup != null) {
                require(s.uniqueGroup.isNotBlank()) { "uniqueGroup 不能为空白（${s.instanceId}）" }
            }
        }

        // 1) 唯一组择强：每组只留 fraction 最高的 active 来源，同强取 instanceId 字典序最小
        val groupWinners = HashMap<String, CooldownReductionSource>()
        val ungrouped = ArrayList<CooldownReductionSource>()
        for (s in sources) {
            if (!s.active) continue
            val group = s.uniqueGroup
            if (group == null) {
                ungrouped.add(s)
                continue
            }
            val current = groupWinners[group]
            if (current == null ||
                s.fraction > current.fraction ||
                (s.fraction == current.fraction && s.instanceId < current.instanceId)
            ) {
                groupWinners[group] = s
            }
        }

        // 2) 获选来源（含 0 减免）按 instanceId 字典序乘算，浮点顺序因此稳定
        val applied = ArrayList<CooldownReductionSource>(groupWinners.size + ungrouped.size)
        applied.addAll(groupWinners.values)
        applied.addAll(ungrouped)
        applied.sortBy { it.instanceId }

        var remaining = 1.0
        for (s in applied) {
            remaining *= 1.0 - s.fraction
        }
        val reduction = minOf(MAX_REDUCTION, 1.0 - remaining)
        val effectiveSeconds = maxOf(minimumSeconds, baseSeconds * (1.0 - reduction))

        return CooldownReductionResult(
            reduction = reduction,
            effectiveSeconds = effectiveSeconds,
            appliedSourceIds = applied.map { it.instanceId }
        )
    }
}

data class CooldownReductionSource(
    val instanceId: String,
    val fraction: Double,
    val uniqueGroup: String? = null,
    val active: Boolean = true
)

data class CooldownReductionResult(
    val reduction: Double,
    val effectiveSeconds: Double,
    val appliedSourceIds: List<String>
)
