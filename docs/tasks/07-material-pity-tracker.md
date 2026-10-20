# 外围任务07：材料保底状态机

## 范围与文件

纯Kotlin、无随机数和存档依赖。按材料类别独立累计保底资格，并在生成符合条件的宝箱时消耗。只处理调用方已经判定好的类别集合，不生成物品、不匹配主题、不判断进度或安全箱尺寸。

仅新增：`shared/src/commonMain/kotlin/com/example/douqiuqiu/loot/MaterialPityTracker.kt`。

测试仅新增：`app/src/test/java/com/example/douqiuqiu/loot/MaterialPityTrackerTest.kt`。

## 接口

```kotlin
data class MaterialPityPolicy(val thresholds: Map<String, Int>)
data class MaterialPityCounter(val misses: Int = 0, val pending: Boolean = false)
data class MaterialPityState(val counters: Map<String, MaterialPityCounter> = emptyMap())
data class PityGenerationResult(val state: MaterialPityState, val guaranteedCategories: List<String>)
object MaterialPityTracker {
    fun recordOutcome(
        state: MaterialPityState,
        policy: MaterialPityPolicy,
        eligibleClearMisses: Set<String>,
        successfullyBroughtOut: Set<String>
    ): MaterialPityState
    fun consumeForGeneration(
        state: MaterialPityState,
        policy: MaterialPityPolicy,
        eligibleCategories: Set<String>
    ): PityGenerationResult
}
```

首轮调用参数示例：`backpack=4, skill=4, safe=6`。这些值不写死在工具内。

## 已定语义

- eligibleClearMisses：调用方确认“该类别已开放且仍有成长需求、匹配主题通关、没有带出该类材料”的类别。死亡、普通撤离或不合格主题不能加入此集合。
- successfullyBroughtOut：调用方确认本次确实带出的目标材料类别。具体从目标箱/途中/安全箱获得的分类交由上层，不在此猜测终局规则。
- 成功带出类别计数清零、pending=false。若同时出现在两个集合，成功带出优先，不先+1再重置导致副作用。
- 合格未获得则misses+1，到阈值时pending=true；已有pending时冻结在阈值，不积累第二份隐形资格。不相关类别不变。
- 未在state出现但已在policy定义的类别按(0,false)处理。返回state只保存原有类别和本次真正改变的类别，不强制填满所有0项；需要重置的已存在类别保留(0,false)。
- 生成阶段对eligibleCategories中具有pending的类别全部消耗：返回类别ID字典序列表，对应计数立即变(0,false)。未匹配的不消耗。
- 单个主题通常只匹配一种材料类别；若上层传多种匹配类别，分别处理，工具不擅自限制一箱最多一种。是否放入物品及空间预算由生成器负责。
- 用返回state连续生成多箱：第一次合格箱用掉资格，后续同类箱不再领取。宝箱之后刷新、放弃或死亡不返还资格。
- 计数从生成后开始新周期；本次箱子之后通关仍可根据真实产出重新累计或清零。
- 只返回新快照，不修改输入或保留可变Map引用。调用方必须将生成内容和资格消耗原子保存；本工具不提供重复结算事件ID去重，调用方不得对同一已结算事件重复调用recordOutcome。

## 校验

policy必须非空；ID非空白、阈值>0；state和操作集合不得引用未知类别。misses必须在0..阈值，pending必须与“misses==阈值”一致。非法输入require抛IllegalArgumentException。

## 必须测试

0到阈值逐次累计；刚好第4/6次生成资格；pending冻结；成功带出清零；成功优先；不相关类别不变；未匹配箱不消耗；首次合格箱消耗；同批第二箱无重复；多类别独立；类别列表顺序稳定；消耗后再累计；相同参数输入不被修改；未知/空白类别、0阈值、负数/超阈值/不一致pending抛错；阈值Int.MAX_VALUE不因+1溢出；用阈值2验证没有硬编码4/6。

验收命令：`:app:testDebugUnitTest --tests '*MaterialPityTrackerTest'`。不修改JourneyRules、CampaignProgress、InventoryStore或宝箱配置；主任务负责集成与防重复事务。
