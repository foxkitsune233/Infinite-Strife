# 外围开发任务 02：商店定时刷新计算器

前一任务 `ChargeRecovery.kt` 已交付。这次仍是纯 Kotlin 小模块，不需要接入 Android、界面、随机商品或存档，和主任务正在编写的仓库代码没有文件交叉。

## 目标与已定规则

商店轮换商品和珍品栏每两小时按固定时间格自动刷新；玩家可以花金币提前换货，**提前换货不推迟下一次自动刷新**。离线跨过多个时间格只生成当前一批商品，不补发中间商品。

本工具只负责判断“现在是否该换货、为什么换货、下次自动刷新还剩多久”。金币检查、扣款、商品生成和持久化由主任务上层事务完成。

首版按 Unix 时间戳的两小时整数格计算，策略提供间隔参数，调用方可换成其他间隔；不读系统时钟、时区或 `ChargeRecovery`，无需后台计时器。

## 文件和边界

只新增：

`app/src/main/java/com/example/douqiuqiu/system/ShopRefreshClock.kt`

可选测试：`app/src/test/java/com/example/douqiuqiu/system/ShopRefreshClockTest.kt`。

不修改 `ChargeRecovery.kt`、Activity、库存、存档、配置或 Gradle。本轮主任务已添加 JUnit 4.13.2，测试可以直接使用现有依赖，也可沿用工程外临时验证。

## 建议接口

```kotlin
data class ShopRefreshPolicy(val intervalMs: Long)
data class ShopRefreshState(
    val lastAutomaticCycle: Long,
    val maxSeenAtMs: Long
)
enum class ShopRefreshReason { NONE, AUTOMATIC, PAID }
data class ShopRefreshResult(
    val state: ShopRefreshState,
    val reason: ShopRefreshReason,
    val nextAutomaticInMs: Long
)
object ShopRefreshClock {
    fun initial(policy: ShopRefreshPolicy, nowMs: Long): ShopRefreshState
    fun evaluate(
        state: ShopRefreshState,
        policy: ShopRefreshPolicy,
        nowMs: Long,
        requestPaidRefresh: Boolean = false
    ): ShopRefreshResult
}
```

`initial` 在新商店创建首批商品时调用，只返回时间状态，不额外要求刷新一次。

## 精确计算约定

1. 时间格编号为 `cycle = effectiveNow / intervalMs`，首个时间格从时间戳 0 开始。
2. `effectiveNow = max(nowMs, state.maxSeenAtMs)`，返回状态的 `maxSeenAtMs` 更新到它。时间回拨不倒退已见时间，也不重新刷新旧格。
3. 当前编号大于 `lastAutomaticCycle` 时返回 `AUTOMATIC`，将编号推进到当前格；一次调用只要求生成一批，不返回错过的批次数。
4. 尚未跨格且 `requestPaidRefresh=true` 时返回 `PAID`，不改变 `lastAutomaticCycle`。
5. 两者同时满足时自动刷新优先，返回 `AUTOMATIC`，上层不扣付费刷新金币。不要生成两批再丢弃其中一批。
6. 其余返回 `NONE`。调用者必须保存返回状态后再处理下一次请求，防止重复自动刷新。
7. 倒计时统一为 `intervalMs - effectiveNow % intervalMs`，范围 `1..intervalMs`。边界时刻先完成刷新，因此返回一个完整间隔，不返回 0。不计算可能溢出的“下次绝对时间戳”。
8. `intervalMs>0`，所有时间戳和格编号非负；`lastAutomaticCycle` 不得大于 `maxSeenAtMs / intervalMs`。非法输入用 `require` 抛 `IllegalArgumentException`。
9. 策略间隔必须与创建该状态时一致，本工具不负责运行中切换策略或迁移旧状态。
10. 返回只是计划；**不读取钱包、不实际扣费、不生成商品、不修改传入对象**。上层需要先校验购买资格，再原子保存商品、扣款和时间状态。失败时不提交返回状态。

## 验收例子

以下用 `intervalMs=1000` 简化，真实两小时为 `7_200_000`。

| 场景 | 输入或步骤 | 期望 |
| --- | --- | --- |
| 新建 | `initial(1000, 1500)` | cycle=1，maxSeen=1500 |
| 尚未到期 | state=(1,1500)，now=1800 | NONE，余200，state=(1,1800) |
| 恰好边界 | state=(1,1500)，now=2000 | AUTOMATIC，cycle=2，余1000 |
| 重复检查 | 上条返回状态，now=2000 | NONE，余1000 |
| 离线多格 | state=(1,1500)，now=6200 | AUTOMATIC 一次，cycle=6，余800 |
| 提前付费 | state=(1,1500)，now=1800，paid=true | PAID，cycle仍1，余200 |
| 付费后到期 | 保存上条状态，now=2000 | AUTOMATIC，cycle=2，余1000 |
| 到期同时付费 | state=(1,1500)，now=2000，paid=true | 仅 AUTOMATIC，不要求扣费 |
| 时间回拨 | state=(2,2500)，now=1800 | NONE，maxSeen仍2500，余500 |
| 回拨中付费 | state=(2,2500)，now=1800，paid=true | PAID，cycle仍2，余500 |
| 大数 | interval=1000，initial(now=Long.MAX_VALUE)，同时间 evaluate | NONE，余193，不溢出 |
| 非法输入 | interval=0、负时间、负cycle、state=(3,2500)配1000间隔 | 抛 IllegalArgumentException |

同时补一个真实两小时参数例子，确认没有把 1000 或两小时写死在工具内部。

## 交付

提交一个实现文件，说明独立验证结果；若添加测试，可跑现有 Gradle 测试。无需接 UI、扣款或存档。主任务随后把该工具用于商店货架刷新事务。
