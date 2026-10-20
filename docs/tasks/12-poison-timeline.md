# 外围任务12：逐层中毒时间推进

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/status/PoisonTimeline.kt` 与 `app/src/test/java/com/example/douqiuqiu/status/PoisonTimelineTest.kt`。纯Kotlin，不改现有Poison/Fighter/引擎/存档/Gradle。可复用任务09已交付的DispelClass，不修改它。

```kotlin
data class PoisonLayer(val instanceId: String, val sourceId: String, val damagePerSecond: Double, val remainingMs: Long, val dispelClass: DispelClass = DispelClass.BASIC)
data class PoisonPool(val layers: List<PoisonLayer> = emptyList())
data class PoisonDamage(val instanceId: String, val sourceId: String, val amount: Double)
data class PoisonAdvanceResult(val pool: PoisonPool, val damage: List<PoisonDamage>)
object PoisonTimeline {
    fun add(pool: PoisonPool, layer: PoisonLayer): PoisonPool
    fun advance(pool: PoisonPool, elapsedMs: Long): PoisonAdvanceResult
}
```

规则：
- 一次施毒产生独立instanceId，同sourceId可多层，时长独立，绝不刷新已有层或合并同名毒。重复实例ID是非法输入。
- 只推进时间及计算未减伤的基础魔法法术伤害；不读时钟、不随机、不计算法术增伤/魔抗/护盾/吸血，不检查破坏或免疫。驱散由09按实例ID筛选，再由主任务更新pool。
- 本工具按连续DPS积分，单层本次伤害=`damagePerSecond*(min(elapsedMs,remainingMs)/1000.0)`。必须先转小数秒，不丢失不足一秒的伤害。
- 到期移除，未到期只扣本次实际推进量。超大elapsed不多打伤害，不出现负剩余，不刷新别人时长。
- damage仅包含amount>0的层，按原输入顺序，保留sourceId以便上层结算；一层一条，不按来源提前合并。elapsed=0时pool等值、damage空。
- damagePerSecond可0但不得负，必须finite；remainingMs>0，elapsedMs>=0；ID非空白且实例全局唯一。校验所有现有层与新层，非法require抛IllegalArgumentException。
- 输出计算溢出必须抛错，不能返回Infinity/NaN；不修改输入，不保留可变列表引用。返回剩余层保持输入顺序。
- 纯伤害毒层不因状态抗性自动缩短。本工具没有控制组件；需压缩持续伤害时序的特殊状态由主任务同步提供正确的时长与DPS，不在此猜规则。

测试至少：1秒与100毫秒；3DPS持续3500ms总10.5；大步长越过到期；同源多层错峰到期；不同来源不合并；0DPS/0推进；一次推进和多次分段总伤害一致（误差比较）；强/不可驱散标记原样保留；重复/空ID、0或负时长、负DPS、NaN/Infinity和输出溢出报错；Long.MAX_VALUE推进安全；输入隔离。

验收：`:app:testDebugUnitTest --tests '*PoisonTimelineTest'`、`:shared:compileKotlinJs`。
