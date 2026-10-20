# 外围任务16：成长材料消耗规划

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/growth/GrowthMaterialPlanner.kt` 与 `app/src/test/java/com/example/douqiuqiu/growth/GrowthMaterialPlannerTest.kt`。不改库存模型、03资格检查器、UI、成长表或存档；本工具不扣金币、扣物品或升级。

```kotlin
data class GrowthMaterialInstance(val instanceId: String, val definitionId: String, val bound: Boolean = false, val inWarehouse: Boolean = true)
data class GrowthMaterialPlan(val success: Boolean, val consumedInstanceIds: List<String>, val missing: Map<String, Int>)
object GrowthMaterialPlanner {
    fun plan(items: List<GrowthMaterialInstance>, costs: Map<String, Int>): GrowthMaterialPlan
}
```

成长仅在局外使用仓库材料。输入是上层已筛为成长材料的唯一实例，不是所有库存物品；材料不可堆叠，每条=1份。costs按definitionId要求精确份数。安全箱/背包/装备等位置由上层传inWarehouse=false，不能消费；绑定材料可以用于成长。

确定性选择：按definitionId字典序逐种，优先消耗绑定材料（不可出售，保留普通材料的变现选择），同绑定状态按instanceId字典序。每种恰选所需份数，不能多扣。所有材料都够时success=true、missing为空、consumed为按上述选择顺序的完整清单；任一不足则success=false、consumed为空，missing仅列不足种类的缺额，按ID字典序。不得返回可被上层误当成功的半份消耗计划。空costs是合法成功空计划。

全量验证：输入实例ID唯一且非空白，definitionId非空白；cost键非空白、数量须>0。仓库外实例也检查ID，非法数据require抛IllegalArgumentException。不猜别名、不把不同材料互换、不读资源金币。所有输出与输入集合独立。

至少覆盖：足额精确消耗；绑定优先；ID排序稳定/输入重排同结果；不足返回完整缺额但不部分消费；仓库外排除；空成本；不需要的物品保留；多份独立实例；重复ID/空ID/非法cost拒绝；输入隔离。验收 `:app:testDebugUnitTest --tests '*GrowthMaterialPlannerTest' :shared:compileKotlinJs`。

依据：金币+通用材料+专用材料成长、材料不堆叠、关键材料绑定。03继续负责资格与资源缺口检查，本工具只提供具体材料实例计划，主任务将两者与升级作为一个原子事务接入。
