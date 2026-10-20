# 外围任务09：按来源标记筛选驱散状态

仅新增主体：`shared/src/commonMain/kotlin/com/example/douqiuqiu/status/DispelResolver.kt`。
仅新增测试：`app/src/test/java/com/example/douqiuqiu/status/DispelResolverTest.kt`。
纯Kotlin，只筛选现有状态实例，不施放技能、不判断施放权限、不赋予免疫、不推进计时或重挂光环，不改任何已有文件。

```kotlin
enum class DispelClass { BASIC, STRONG_ONLY, UNDISPELLABLE }
enum class DispelStrength { BASIC, STRONG }
enum class DispelTarget { SELF_OR_ALLY, ENEMY }
data class DispelEffect(val instanceId: String, val sourceId: String, val positive: Boolean, val dispelClass: DispelClass)
data class DispelResult(val retained: List<DispelEffect>, val removed: List<DispelEffect>)
object DispelResolver {
    fun resolve(effects: List<DispelEffect>, strength: DispelStrength, target: DispelTarget): DispelResult
}
```

规则：
- 自己/友方只清负面；弱清BASIC，强清BASIC和STRONG_ONLY；UNDISPELLABLE及全部正面保留。
- 敌方只清BASIC正面，负面保留；传STRONG也不扩大到STRONG_ONLY增益，强驱散额外能力用于解友方负面。
- 不根据名称猜驱散性质。同sourceId可有多个实例乃至不同驱散标记；符合条件的层全清，不限定层数。
- retained/removed各自保留输入相对顺序，两者互斥、合起来恰好等于输入实例，不复制不丢失。
- 空列表合法；instanceId/sourceId非空白、instanceId全局唯一，sourceId重复合法。非法输入require抛IllegalArgumentException。
- 不改输入，输出独立快照；相同输入确定同结果。
- 无光之盾/无敌斩成功施放后传STRONG+SELF_OR_ALLY；撒旦/BKB传BASIC+SELF_OR_ALLY。普通毒BASIC，P4毒STRONG_ONLY，蝮蛇突袭相关特殊状态UNDISPELLABLE。标记由调用方提供。
- 不能由强驱散推导受控施放，也不把不可驱散等同穿透减益免疫。

必须测试：弱清多层普通毒但保留强/不可驱散；强清弱/强负面；友方增益保留；敌方只清BASIC增益；强度不扩大敌方驱散；同来源不同标记；空输入；实例守恒及顺序；空白ID/重复实例报错；结果快照隔离。

参考项目内`docs/rules/dispel.md`和`docs/design/v1-decisions.md`（后者覆盖历史待审），不联网改版本或新增玩法。
验收：`:app:testDebugUnitTest --tests '*DispelResolverTest'`。
