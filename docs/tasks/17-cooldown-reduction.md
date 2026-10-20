# 外围任务17：冷却缩减计算

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/combat/CooldownReduction.kt` 与 `app/src/test/java/com/example/douqiuqiu/combat/CooldownReductionTest.kt`。不改战斗、10冷却跟踪器、模型、UI、配置、存档或Gradle。

```kotlin
data class CooldownReductionSource(val instanceId: String, val fraction: Double, val uniqueGroup: String? = null, val active: Boolean = true)
data class CooldownReductionResult(val reduction: Double, val effectiveSeconds: Double, val appliedSourceIds: List<String>)
object CooldownReduction {
    fun calculate(baseSeconds: Double, sources: List<CooldownReductionSource>, minimumSeconds: Double = 0.5): CooldownReductionResult
}
```

依据v1：英雄/卡/装备都可受不同来源减CD影响。各独立来源乘算剩余比例，`reduction = min(0.6, 1 - product(1-fraction))`。有效CD=`max(minimumSeconds, baseSeconds*(1-reduction))`。普通默认下限0.5秒，幽魂护罩由调用方明确传10秒；本工具不按英雄名字猜规则。10号工具的场间固定减30秒不经过本工具。

uniqueGroup非null且非空白表示同组只取fraction最高的active来源（玲珑心由上层给同组名），相同强度选instanceId字典序最小。无组来源各自保留，不按卡名去重。先确定同组获选者，再按instanceId字典序做乘算，确保输入重排结果稳定。appliedSourceIds包括全部获选active来源（包括0减免），按字典序；inactive不参与，但全量校验。

baseSeconds/minimumSeconds均须finite且>0，fraction须finite且0..1；实例ID全局唯一且非空白。空sources也要应用最短CD。返回Double秒，不在本工具做毫秒取整、时间推进或开战重置。输入不变，结果独立快照。

至少测试：无来源；20%+30%=44%减免，10秒→5.6秒；超过60%封顶；同组只取高而无组都计；同强稳定选择；inactive排除仍校验；0/100%源；0.5秒普通下限；18秒幽魂传10秒后不能短于10秒；输入重排一致；非法Double/范围/重复ID/空组名拒绝；输入隔离。验收 `:app:testDebugUnitTest --tests '*CooldownReductionTest' :shared:compileKotlinJs`。
