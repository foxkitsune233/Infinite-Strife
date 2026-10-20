package com.example.douqiuqiu.system

/**
 * 次数恢复计算器（纯 Kotlin，不依赖 Android / 网络 / 数据库 / 协程）。
 *
 * 供两套系统共用的外围工具：
 *  - 免费单箱刷新：容量 3，每 30 分钟恢复 1 次
 *  - 破产救济：容量 2，每 2 小时恢复 1 次
 *
 * 规则：
 *  - 次数逐个恢复，每完整经过一个 intervalMs 恢复 1 次，达到上限停止积累；
 *  - 离线时间同样计入（调用方只需传入当前时间戳）；
 *  - 不足一个间隔的时间保留为余数，不清零；
 *  - 达到上限 / 原本已满时，锚点推到本次有效当前时间，不储存隐形恢复额度；
 *  - 时间回拨（nowMs 早于 anchorMs）时以 anchorMs 为有效当前时间，不减少次数、不给负倒计时。
 *
 * 两组数值由调用方通过 [RecoveryPolicy] 传入，本工具不写死任何配置。
 * 工具内部不读取系统时钟；并发与存档由调用方负责——连续消耗时必须串行保存返回状态。
 * 这是本地恢复计算，不宣称能防止修改系统时间。
 */
object ChargeRecovery {

    /**
     * 按 [nowMs] 结算恢复，返回最新状态与距下次恢复的毫秒数（满次数为 null）。
     *
     * 同一个状态、同一个时间重复调用结果一致，不会重复加次数。
     */
    fun recover(
        state: RecoveryState,
        policy: RecoveryPolicy,
        nowMs: Long
    ): RecoveryResult {
        validate(state, policy, nowMs)
        val now = effectiveNow(state, nowMs)

        // 原本已满：锚点推到当前时间，满额期间不积累隐形恢复额度
        if (state.charges >= policy.capacity) {
            return RecoveryResult(RecoveryState(policy.capacity, now), null)
        }

        val elapsed = now - state.anchorMs
        val steps = elapsed / policy.intervalMs
        val gained = minOf(steps, (policy.capacity - state.charges).toLong()).toInt()
        val charges = state.charges + gained

        // 本次达到上限：锚点取有效当前时间，丢弃多余的累计时间
        if (charges >= policy.capacity) {
            return RecoveryResult(RecoveryState(policy.capacity, now), null)
        }

        // 未满：锚点只前进已消耗的完整间隔数，保留余数，倒计时落在 1..intervalMs
        val anchor = state.anchorMs + steps * policy.intervalMs
        val next = policy.intervalMs - (now - anchor)
        return RecoveryResult(RecoveryState(charges, anchor), next)
    }

    /**
     * 先按 [nowMs] 恢复，再尝试消耗 1 次。
     *
     *  - 恢复后为 0 次：consumed=false，不扣成负数，但仍返回最新状态与倒计时；
     *  - 恢复后大于 0 次：扣 1 次，consumed=true；
     *  - 消耗前是满次数：从本次有效当前时间重新计时；
     *  - 消耗前未满：保留原恢复进度，不因消耗重新计时。
     */
    fun tryConsume(
        state: RecoveryState,
        policy: RecoveryPolicy,
        nowMs: Long
    ): ConsumeResult {
        validate(state, policy, nowMs)
        val now = effectiveNow(state, nowMs)

        val recovered = recover(state, policy, nowMs)
        val after = recovered.state

        if (after.charges <= 0) {
            return ConsumeResult(false, recovered)
        }

        return if (after.charges >= policy.capacity) {
            // 消耗前满次数：从有效当前时间重新开始计
            ConsumeResult(
                true,
                RecoveryResult(RecoveryState(policy.capacity - 1, now), policy.intervalMs)
            )
        } else {
            // 未满：沿用恢复后的锚点与倒计时
            ConsumeResult(
                true,
                RecoveryResult(
                    RecoveryState(after.charges - 1, after.anchorMs),
                    recovered.nextChargeInMs
                )
            )
        }
    }

    /** 时间回拨时有效当前时间不早于锚点 */
    private fun effectiveNow(state: RecoveryState, nowMs: Long): Long =
        if (nowMs < state.anchorMs) state.anchorMs else nowMs

    private fun validate(state: RecoveryState, policy: RecoveryPolicy, nowMs: Long) {
        require(policy.capacity > 0) { "capacity 必须大于 0，当前 ${policy.capacity}" }
        require(policy.intervalMs > 0) { "intervalMs 必须大于 0，当前 ${policy.intervalMs}" }
        require(state.charges in 0..policy.capacity) {
            "charges 必须在 0..${policy.capacity} 范围内，当前 ${state.charges}"
        }
        require(nowMs >= 0) { "nowMs 不能为负，当前 $nowMs" }
        require(state.anchorMs >= 0) { "anchorMs 不能为负，当前 ${state.anchorMs}" }
    }
}

/** 恢复策略：最大储存次数 + 恢复 1 次所需毫秒数 */
data class RecoveryPolicy(
    val capacity: Int,
    val intervalMs: Long
)

/** 恢复状态：当前次数 + 本轮恢复计时锚点（毫秒时间戳） */
data class RecoveryState(
    val charges: Int,
    val anchorMs: Long
)

/** 一次恢复计算的结果 */
data class RecoveryResult(
    val state: RecoveryState,
    /** 满次数为 null；否则距恢复下次还需 1..intervalMs 毫秒 */
    val nextChargeInMs: Long?
)

/** 一次消耗尝试的结果 */
data class ConsumeResult(
    val consumed: Boolean,
    val recovery: RecoveryResult
)
