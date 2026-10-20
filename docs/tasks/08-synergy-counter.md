# 外围任务08：技能卡羁绊计数

仅新增主体：`shared/src/commonMain/kotlin/com/example/douqiuqiu/cards/SynergyCounter.kt`。
仅新增测试：`app/src/test/java/com/example/douqiuqiu/cards/SynergyCounterTest.kt`。
只用Kotlin通用标准库，不改模型/配置/Gradle/UI/引擎；不应用属性或触发技能。

```kotlin
data class SynergyCard(val instanceId: String, val cardId: String, val tags: Set<String>, val star: Int = 1, val active: Boolean = true)
data class SchoolSynergy(val schoolId: String, val uniqueCardIds: Set<String>, val activeTiers: List<Int>)
object SynergyCounter {
    fun count(cards: List<SynergyCard>, thresholds: List<Int> = listOf(2, 3, 4)): List<SchoolSynergy>
}
```

规则：
- active=true才计数，由调用方根据技能栏提供；背包/安全箱/仓库为false。
- cardId是卡种，同名不同星和多实例在每流派只算一种。instanceId只标唯一实例。
- 每卡1～2个非空白标签，双标签分别贡献两个流派；同cardId所有实例标签必须一致。
- 达到4种同时返回[2,3,4]；5种保留5个不同卡ID，仍只激活[2,3,4]。不在此实现重复卡单卡效果。
- 输出包含至少1种有效卡的流派，即使未达档也返回空activeTiers；未出现流派不输出。流派按ID字典序；uniqueCardIds按字典序形成稳定顺序集合。
- 空卡列表返回空。thresholds必须非空、正数且严格递增；star为1..3；instanceId/cardId/标签均非空白，instanceId全局唯一。同名标签冲突非法。
- inactive输入也校验；非法参数require抛IllegalArgumentException。不修改输入，结果不引用输入可变集合。

必须测试：0/1/2/3/4/5卡种；同名重复/不同星；双标签；多流派同时激活；inactive不计；输入顺序不影响输出；无效标签数量/空ID/重复实例/冲突标签/非法星级和门槛抛错；输入与结果快照隔离。

验收：`:app:testDebugUnitTest --tests '*SynergyCounterTest'`，可加`:shared:compileKotlinJs`。只交这两个文件，由主任务接入。
