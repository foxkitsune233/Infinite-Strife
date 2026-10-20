# 外围任务03：成长购买条件检查器

## 目标

实现纯 Kotlin 检查器，供英雄解锁、技能栏扩展、背包和安全箱升级共用。传入需求与当前资源，返回是否满足及所有缺口；一次显示全部缺口，不只返回第一项失败。

本模块不决定价格或进度门槛，数值全部由调用方传入。因此即使主任务调整成长表，也不用改工具实现。

## 文件边界

新增主体：`app/src/main/java/com/example/douqiuqiu/system/GrowthRequirementChecker.kt`

新增测试：`app/src/test/java/com/example/douqiuqiu/system/GrowthRequirementCheckerTest.kt`

只操作这两个文件；不修改 Gradle、其他 system 工具、库存模型、配置、Activity 或存档。不扣钱、不消耗材料、不升级、不选材料实例，不依赖 Android 或系统时钟。

## 建议接口

```kotlin
data class GrowthRequirement(
    val goldCost: Long,
    val materials: Map<String, Int> = emptyMap(),
    val requiresExtraction: Boolean = false,
    val minClears: Int = 0,
    val requiredLevels: Map<String, Int> = emptyMap()
)
data class GrowthResources(
    val gold: Long,
    val warehouseMaterials: Map<String, Int> = emptyMap(),
    val hasExtracted: Boolean = false,
    val clearCount: Int = 0,
    val levels: Map<String, Int> = emptyMap()
)
data class GrowthCheckResult(
    val canPurchase: Boolean,
    val missingGold: Long,
    val missingMaterials: Map<String, Int>,
    val needsExtraction: Boolean,
    val missingClears: Int,
    val missingLevels: Map<String, Int>
)
object GrowthRequirementChecker {
    fun evaluate(requirement: GrowthRequirement, resources: GrowthResources): GrowthCheckResult
}
```

`requiredLevels` 是其他永久成长的最低档位，不是本次购买数量。例如安全箱最后一档要求背包至少4×3，调用方将背包档位定义为初始0、4×3为1，然后传 `{"backpack":1}`。工具不理解这些ID或档位名称。

## 规则

1. `missingGold=max(0, goldCost-gold)`；金币均非负，用Long，不转Int。
2. 材料逐ID检查，库存缺失视为0。返回缺少的数量，只含缺口大于0的项。
3. `needsExtraction = requiresExtraction && !hasExtracted`。是否完成撤离由调用方提供，不从通关数擅自推导。
4. `missingClears=max(0,minClears-clearCount)`。
5. 前置成长同材料规则，缺失档位视为0；返回不足的档数，只含正缺口。
6. 所有缺口都为0/空且不需要撤离时，`canPurchase=true`。资源可以超过要求，无需完全相等。
7. 需求数量和档位允许0，代表无该项要求。金币、数量、计数、档位不得为负；材料/成长ID不得为空或全空白。所有非法输入，包括未被需求使用的负库存项，也必须 `require` 抛 `IllegalArgumentException`。
8. 绑定材料允许用于成长。调用者只传仓库内可消耗材料数量，局内物品和待领取奖励不能混入；工具不判断物品位置与绑定。
9. 不修改输入Map，也不把输入Map直接作为结果Map返回；返回结果是本次计算的独立快照，调用方之后修改输入不改变既有结果。
10. 不检查“当前是否在局外”“已经解锁”“能否重复买”“是否达到上限”“下一档是否连续”等业务阶段问题，这些由上层处理。`canPurchase`仅表示传入的资源和资格条件满足，不等于已经执行购买。

## 验收

| 场景 | 条件 | 期望 |
| --- | --- | --- |
| 零需求 | 需求gold=0，其余默认；资源gold=0 | 可购买，所有缺口为空/0/false |
| 首次扩背包满足 | 需求200金币、2织件、已撤离；资源恰好满足 | 可购买 |
| 金币不足 | 需求200，持有150 | missingGold=50 |
| 没有材料ID | 需求2织件，库存空 | 缺2织件 |
| 多种材料 | 需求3精华+2刻片，库存1精华+5刻片 | 只缺2精华 |
| 尚未撤离 | 需求requiresExtraction=true，hasExtracted=false | needsExtraction=true，不可购买 |
| 通关不能替代显式资格 | 同上，clearCount=10 | 仍needsExtraction=true，由上层正确填写成就 |
| 通关数量不足 | 需求6次，当前4次 | missingClears=2 |
| 缺前置成长 | 需求backpack=1，当前levels空 | missingLevels={backpack:1} |
| 前置成长超过 | 需求backpack=1，当前backpack=3 | 不缺前置成长 |
| 同时不足 | 上述金币/材料/撤离/通关/前置都不足 | 一次返回全部缺口 |
| 多余资源 | 只需织件，另有100精华 | 精华不产生额外要求或缺口 |
| Long边界 | 费用Long.MAX_VALUE，余额0或同值 | 缺口分别Long.MAX_VALUE或0，不溢出 |
| 空白ID/负数 | 任意非法需求或资源，包括多余ID负库存 | 抛IllegalArgumentException |
| 输入隔离 | 计算后修改原始可变Map | 原结果缺口保持不变，输入计算时未被改动 |

另外补充需求数为0、库存恰好为0及Int.MAX_VALUE材料数量边界。

## 交付

运行现有 `:app:testDebugUnitTest :app:assembleDebug`，报告新增用例数、失败数，保留原有测试。工具接口不依赖尚在审阅的成长价格和商品开放表，可以先独立实现。
