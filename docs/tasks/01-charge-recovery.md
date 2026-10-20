# 外围开发任务：次数恢复计算器

这是交给用户自行实现的小任务，不需要先完成其他游戏模块。本任务仅写需求，当前没有生成业务实现。

## 目标

实现一个纯 Kotlin 工具，供两套系统共用：

| 用途 | 最大储存次数 | 恢复1次的时间 |
| --- | --- | --- |
| 免费单箱刷新 | 3 | 30分钟 |
| 破产救济 | 2 | 2小时 |

规则：次数逐个恢复、达到上限停止积累；离线时间也计入。两个用途各自拥有独立状态，不能互相消耗。

上表使用首版定稿的初始数值，写成调用参数，工具本身不要写死这两组数字。

## 文件和边界

新增：

`app/src/main/java/com/example/douqiuqiu/system/ChargeRecovery.kt`

可选测试：

`app/src/test/java/com/example/douqiuqiu/system/ChargeRecoveryTest.kt`

只实现时间与次数计算，不修改现有Activity、RunState、配置JSON或Gradle文件。无需Android类、网络、数据库、后台计时器或协程；由调用者传入当前时间、保存返回状态。

不做：付费刷新扣钱、发放救济、判断破产、生成宝箱、商店刷新。这些由以后上层调用处理。

## 建议接口

以下代码是接口约定，不是实现。可根据习惯调整命名，但保持规则和返回信息一致。

```kotlin
data class RecoveryPolicy(
    val capacity: Int,
    val intervalMs: Long
)

data class RecoveryState(
    val charges: Int,
    val anchorMs: Long
)

data class RecoveryResult(
    val state: RecoveryState,
    val nextChargeInMs: Long? // 满次数为null；否则距恢复下次还需多少毫秒
)

data class ConsumeResult(
    val consumed: Boolean,
    val recovery: RecoveryResult
)

object ChargeRecovery {
    fun recover(
        state: RecoveryState,
        policy: RecoveryPolicy,
        nowMs: Long
    ): RecoveryResult

    fun tryConsume(
        state: RecoveryState,
        policy: RecoveryPolicy,
        nowMs: Long
    ): ConsumeResult
}
```

`anchorMs`表示当前这一轮恢复开始计时的位置。所有时间都使用同一种毫秒时间戳；工具内部不要调用系统时钟，以便测试。

## 具体规则

### 1. 补充次数

- 未满时，每完整经过一个`intervalMs`恢复1次，最多恢复到`capacity`。
- 不足一个间隔的时间保留。例如已有1次、已经计时20分钟，30分钟间隔还需10分钟。
- 若这次计算后达到上限，将`anchorMs`更新为本次有效当前时间，丢弃多余累计时间。
- 原状态已经满次数时，也将`anchorMs`更新为本次有效当前时间；满次数期间不储存隐形恢复额度。
- 未达到上限时，`anchorMs`只向前推进已用于恢复的完整间隔数，保留余数。

### 2. 消耗次数

- `tryConsume`先按当前时间恢复，再尝试消耗1次。
- 恢复后次数为0：返回`consumed=false`，不扣成负数，但仍返回最新恢复状态及倒计时。
- 恢复后次数大于0：扣1次，返回`consumed=true`。
- 若消耗前为满次数，从本次有效当前时间开始计算下次恢复。
- 若消耗前未满，继续原恢复进度，不因本次消耗重新计时。
- 连续点击只能消耗现有次数，不能重复使用同一份旧状态。调用者必须串行保存返回状态后再下一次调用；工具本身不管理并发和存档。

### 3. 时间与无效参数

- 参数要求：`capacity>0`，`intervalMs>0`，`charges`在`0..capacity`范围内，时间戳均非负；不满足时使用`require`抛出`IllegalArgumentException`，不要偷偷重置次数。
- 如果`nowMs<anchorMs`，本次有效当前时间取`anchorMs`，不能减少次数，也不能给出负倒计时。
- 满次数：`nextChargeInMs=null`。
- 未满：倒计时应在`1..intervalMs`范围内；恰好到恢复点时，先恢复次数再计算下一次倒计时。
- 这是本地恢复计算，不宣称防止修改系统时间。全局时钟与存档策略由后续任务负责。

## 验收例子

下表统一使用：容量3、每30分钟恢复1次；起点时间为0。每行独立，不接续上一行。

| 操作 | 输入次数/anchor | 当前时间 | 预期次数 | 预期anchor | 距下次恢复 |
| --- | --- | --- | --- | --- | --- |
| recover | 0 / 0 | 29分钟 | 0 | 0 | 1分钟 |
| recover | 0 / 0 | 30分钟 | 1 | 30分钟 | 30分钟 |
| recover | 0 / 0 | 65分钟 | 2 | 60分钟 | 25分钟 |
| recover | 0 / 0 | 100分钟 | 3 | 100分钟 | null |
| recover | 3 / 0 | 100分钟 | 3 | 100分钟 | null |
| tryConsume | 3 / 0 | 100分钟 | 2 | 100分钟 | 30分钟 |
| tryConsume | 1 / 0 | 20分钟 | 0 | 0 | 10分钟 |
| tryConsume | 0 / 0 | 20分钟 | 0，consumed=false | 0 | 10分钟 |
| tryConsume | 0 / 0 | 30分钟 | 0，consumed=true | 30分钟 | 30分钟 |
| recover | 1 / 60分钟 | 50分钟（回拨） | 1 | 60分钟 | 30分钟 |

其他需覆盖：

- 对同一个状态、相同时间重复`recover`，结果一致，不重复加次数。
- 使用救济参数容量2/间隔2小时，离线5小时最多恢复2次。
- 非法次数、0容量、0间隔、负时间抛出参数错误。
- 连续消耗满3次后的第四次失败；时间不变不会额外恢复。

## 交付标准

- 一个不依赖Android的Kotlin实现文件。
- 上述行为可通过本地单元测试或独立Kotlin用例验证；若添加测试依赖需要改Gradle，请先把依赖改动单独说明。
- 简短注明实际验证方式和结果，不把“没有测试源码”当成测试通过。
- 不接入现有UI或存档，不改其他业务逻辑。

实现完成后，把文件交回当前项目即可；后续由主任务把它接入宝箱刷新和救济领取事务。
