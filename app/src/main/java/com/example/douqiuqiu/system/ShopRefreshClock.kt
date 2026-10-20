package com.example.douqiuqiu.system

/**
 * 商店定时刷新计算器（纯 Kotlin，不依赖 Android / ChargeRecovery / 系统时钟 / 后台计时器）。
 *
 * 规则：商店轮换商品和珍品栏每经过一个时间格自动刷新一次；玩家可花金币提前换货，
 * **提前换货不推迟下一次自动刷新**；离线跨过多个时间格只算一次自动刷新，不补发中间批次。
 *
 * 本工具只回答三个问题：现在是否该换货（[ShopRefreshReason]）、为什么、距下次自动刷新还有多久。
 * 金币校验、扣款、商品生成与持久化由上层事务完成——返回的只是计划，
 * 上层需先校验购买资格，再原子地保存商品、扣款和时间状态；失败时不要提交返回状态。
 *
 * 时间格编号 cycle = 有效当前时间 / intervalMs，首格从时间戳 0 开始。
 * 策略间隔必须与创建状态时一致，运行中切换策略或迁移旧状态不在本工具职责内。
 */
object ShopRefreshClock {

    /**
     * 新商店创建首批商品时调用：按 [nowMs] 落在哪个时间格记录初始状态。
     * 只返回时间状态，不额外要求刷新一次。
     */
    fun initial(policy: ShopRefreshPolicy, nowMs: Long): ShopRefreshState {
        require(policy.intervalMs > 0) { "intervalMs 必须大于 0，当前 ${policy.intervalMs}" }
        require(nowMs >= 0) { "nowMs 不能为负，当前 $nowMs" }
        return ShopRefreshState(nowMs / policy.intervalMs, nowMs)
    }

    /**
     * 按 [nowMs] 评估一次刷新。
     *
     * 有效当前时间取 max(nowMs, maxSeenAtMs)：时间回拨不倒退已见时间，也不重新刷新旧格。
     * 跨格 → [ShopRefreshReason.AUTOMATIC]（编号推进到当前格，只要求生成一批）；
     * 未跨格且请求付费 → [ShopRefreshReason.PAID]（不改编号，即不推迟自动刷新）；
     * 两者同时满足自动刷新优先，上层不扣付费金币；其余 → [ShopRefreshReason.NONE]。
     *
     * 连续请求必须串行保存返回状态，防止同一次自动刷新被重复处理。
     */
    fun evaluate(
        state: ShopRefreshState,
        policy: ShopRefreshPolicy,
        nowMs: Long,
        requestPaidRefresh: Boolean = false
    ): ShopRefreshResult {
        require(policy.intervalMs > 0) { "intervalMs 必须大于 0，当前 ${policy.intervalMs}" }
        require(nowMs >= 0) { "nowMs 不能为负，当前 $nowMs" }
        require(state.lastAutomaticCycle >= 0) {
            "lastAutomaticCycle 不能为负，当前 ${state.lastAutomaticCycle}"
        }
        require(state.maxSeenAtMs >= 0) { "maxSeenAtMs 不能为负，当前 ${state.maxSeenAtMs}" }
        require(state.lastAutomaticCycle <= state.maxSeenAtMs / policy.intervalMs) {
            "lastAutomaticCycle(${state.lastAutomaticCycle}) 不能大于 maxSeenAtMs 所在格" +
                "(${state.maxSeenAtMs / policy.intervalMs})"
        }

        val effectiveNow = if (nowMs > state.maxSeenAtMs) nowMs else state.maxSeenAtMs
        val currentCycle = effectiveNow / policy.intervalMs
        // 边界时刻先完成刷新，因此倒计时取一个完整间隔，范围恒为 1..intervalMs
        val nextIn = policy.intervalMs - effectiveNow % policy.intervalMs

        return when {
            // 自动刷新优先于付费刷新：只返回一批，不生成两批再丢弃
            currentCycle > state.lastAutomaticCycle -> ShopRefreshResult(
                ShopRefreshState(currentCycle, effectiveNow),
                ShopRefreshReason.AUTOMATIC,
                nextIn
            )

            requestPaidRefresh -> ShopRefreshResult(
                ShopRefreshState(state.lastAutomaticCycle, effectiveNow),
                ShopRefreshReason.PAID,
                nextIn
            )

            else -> ShopRefreshResult(
                ShopRefreshState(state.lastAutomaticCycle, effectiveNow),
                ShopRefreshReason.NONE,
                nextIn
            )
        }
    }
}

/** 刷新策略：时间格间隔（毫秒）。首版为两小时，调用方可换其他间隔。 */
data class ShopRefreshPolicy(val intervalMs: Long)

/** 商店的时间状态：最后一次自动刷新的格编号 + 见过的最大时间戳 */
data class ShopRefreshState(
    val lastAutomaticCycle: Long,
    val maxSeenAtMs: Long
)

/** 刷新原因：无需刷新 / 跨格自动刷新 / 玩家付费提前换货 */
enum class ShopRefreshReason { NONE, AUTOMATIC, PAID }

/** 一次评估的结果：新时间状态 + 刷新原因 + 距下次自动刷新的毫秒数（1..intervalMs） */
data class ShopRefreshResult(
    val state: ShopRefreshState,
    val reason: ShopRefreshReason,
    val nextAutomaticInMs: Long
)
