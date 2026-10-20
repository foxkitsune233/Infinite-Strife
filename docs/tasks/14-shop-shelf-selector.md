# 外围任务14：商店货架选择器

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/shop/ShopShelfSelector.kt` 与 `app/src/test/java/com/example/douqiuqiu/shop/ShopShelfSelectorTest.kt`。不要接UI、时间、库存扣减、存档、定价，不修改其他文件。

## 接口

```kotlin
enum class ShopShelf { STAPLE, ROTATING, RARE }
data class ShopCandidate(val definitionId: String, val shelf: ShopShelf, val minimumClears: Int = 0, val stock: Int = 1)
data class SelectedShopOffer(val definitionId: String, val shelf: ShopShelf, val stock: Int)
data class ShopShelfSelection(val offers: List<SelectedShopOffer>, val nextSeed: Long)
object ShopShelfSelector {
    fun select(candidates: List<ShopCandidate>, clears: Int, seed: Long): ShopShelfSelection
}
```

规则：按达成的累计通关数筛选。所有合格STAPLE常备品都出现；ROTATING最多6件不重复；珍品栏30%概率有货，有货时合格RARE中最多1件。最终常备、轮换、珍品分组，每组definitionId字典序，输出独立快照。

确定性算法写死在本工具内部，禁止系统随机数与时钟：seed必须1..2147483646；每次抽取先 `seed = seed * 48271 % 2147483647`（Long），再取 `seed % n`。先对三类候选分别按ID排序。常备不消耗随机数；轮换从剩余列表反复抽一个并移除，直到6件或池空，每抽一件消费一次；随后无论RARE是否为空均消费一次随机数，`seed % 100 < 30`视为珍品有货，有货且RARE非空再抽一次决定物品。返回最后seed。相同输入重排仍完全相同。

ID全输入范围唯一、非空白；minimumClears和clears非负；stock在1..100。未开放候选也全量校验，不要悄悄忽略坏配置。卡牌/卷轴/成装的库存差异由上层传stock，此处不猜物品类型、不额外解锁或制造商品。

至少覆盖：固定输入种子有精确可核对结果；重排输入一致；常备全保留；轮换不超6无重复；不足6全选；进度筛选；珍品命中/未命中/空池；空输入仍消费珍品检查一次；nextSeed正确；重复ID/非法stock/负门槛/非法seed拒绝；输入隔离。验收 `:app:testDebugUnitTest --tests '*ShopShelfSelectorTest' :shared:compileKotlinJs`。

依据：v1-decisions商店6格+珍品1格与F3进度。现有基础商店继续由主任务维护，本工具只做未来完整货架选择。
