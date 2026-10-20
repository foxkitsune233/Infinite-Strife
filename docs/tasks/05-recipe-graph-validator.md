# 外围任务05：装备配方图校验

## 目标与文件

在配置加载时检查配方是否引用缺失物品、产生循环或存在歧义，并给出可读的错误列表。它只检查定义，不合成物品、不扣材料、不核验Dota来源、不决定游戏价格。

仅新增：`shared/src/commonMain/kotlin/com/example/douqiuqiu/inventory/RecipeGraphValidator.kt`。

测试仅新增：`app/src/test/java/com/example/douqiuqiu/inventory/RecipeGraphValidatorTest.kt`。

复用现有共享类型，不修改它：`CraftingRecipe(id: String, outputId: String, ingredients: Map<String,Int>)`，定义在shared的inventory/Crafting.kt。其他现有文件不得修改，不增加依赖。

## 接口

```kotlin
enum class RecipeIssueKind {
    BLANK_RECIPE_ID, DUPLICATE_RECIPE_ID, UNKNOWN_OUTPUT, UNKNOWN_INGREDIENT,
    INVALID_COUNT, EMPTY_INGREDIENTS, DUPLICATE_OUTPUT, CYCLE
}
data class RecipeIssue(val kind: RecipeIssueKind, val recipeId: String, val itemId: String = "", val path: List<String> = emptyList())
data class RecipeGraphResult(val valid: Boolean, val issues: List<RecipeIssue>, val buildOrder: List<String>)
object RecipeGraphValidator {
    fun validate(itemIds: Set<String>, recipes: List<CraftingRecipe>): RecipeGraphResult
}
```

## 规则

- itemIds只提供合法物品ID集合，不要求所有物品都有配方。材料/卷轴直接存在于集合中即可当作叶子节点。
- 检查空白配方ID、重复配方ID、未知产物、未知组件、组件数量<=0、空配方。未知ID包括空白outputId/ingredientId。
- 首版一件产物只允许一条配方，重复outputId报告DUPLICATE_OUTPUT，不自行选择其一或合并。
- 依赖边为“某配方需要另一配方的产物”，不是所有组件都必须能合成。检查直接自循环及任意长度间接循环；发现CYCLE时path给出闭环的物品ID序列，例如[A,B,C,A]。
- 同一循环不因从每个节点出发而重复报错；多个独立循环至少各给一个证据。实现可用DFS颜色/栈；不能只检查深度2，也不能把合法菱形依赖误判为循环。
- 同一次调用汇总能安全检查到的全部配置问题。存在重复ID/产物等歧义时可跳过这些歧义节点的循环检查，但不能崩溃或无限递归。
- 无问题才valid=true；buildOrder按“先组件配方，后上级配方”返回配方ID，不是物品ID。独立分支以配方ID字典序稳定排序；有任何错误时buildOrder为空。
- 空配方列表合法；itemIds有空白ID属于调用参数错误，require抛IllegalArgumentException。配方内容问题通过issues返回，不直接抛异常。
- 不修改输入，结果为独立快照；不要为检测循环消耗/更改配方中的数量。

## 必须测试

三条基础护腕/系带/挂件配方；合法两级链；菱形依赖；缺组件；缺产物；0及负数量；空配方；空/重复配方ID；重复产物；自循环；三节点循环；两个独立循环；独立分支顺序稳定；所有错误时无buildOrder；叶子组件不必有配方；输入不变。

验收命令：`:app:testDebugUnitTest --tests '*RecipeGraphValidatorTest'`。不用改ConfigLoader/InventoryCatalog；主任务负责最后接入。配方源数据仍由主任务核对，不把本任务扩成联网搜集装备资料。
