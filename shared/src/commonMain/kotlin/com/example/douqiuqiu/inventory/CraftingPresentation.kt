package com.example.douqiuqiu.inventory

/**
 * 单种材料在某个配方里的可用性分层（外围任务 X04）。
 *
 * 三个计数是完备且互斥的划分：[consumableOwned] + [lockedOwned] + [elsewhereOwned] == [totalOwned]。
 * 可合成判断只认 [consumableOwned]——仓库内、独立成格、未锁定的单位，
 * 与 [CraftingRules.craft] 的实际校验完全同一口径；绑定材料照常可合成（产出随输入绑定），
 * 因此绑定不出现在任何分层里，只有锁定才剥夺可消耗性。
 */
data class CraftingMaterial(
    val definitionId: String,
    val required: Int,
    /** 总持有单位数：任何容器、任何堆叠状态都计入。 */
    val totalOwned: Int,
    /** 其中被锁定的单位数：持有但不可消耗，UI 必须明示而不能猜。 */
    val lockedOwned: Int,
    /** 真正可消耗的单位数：仓库内、独立成格、未锁定。可合成判断只以它为准。 */
    val consumableOwned: Int,
    /** 按可消耗量计算的缺口（下限 0）。 */
    val missing: Int,
    /** 持有但不在可选池的单位数：在背包/安全箱/装备栏/技能栏，或仓库内处于堆叠状态。 */
    val elsewhereOwned: Int
)

data class CraftingOption(
    val recipe: CraftingRecipe,
    val missing: Int,
    val ingredientIds: List<String>,
    /** 按配方声明顺序给出的逐材料分层；汇总 [missing] 等于各项之和。 */
    val materials: List<CraftingMaterial> = emptyList()
)

/** Display availability and material selection share exactly the same warehouse eligibility. */
object CraftingPresentation {
    fun options(state: InventoryState, recipes: Collection<CraftingRecipe>, definitions: Map<String, ItemDefinition>): List<CraftingOption> {
        val locked = state.progress.lockedItems
        // 可选池与 craft() 的校验保持同一口径：仓库内、独立成格、未锁定。
        val selectable = state.items
            .filter { it.place.container == Container.WAREHOUSE && it.count == 1 && it.instanceId !in locked }
            .groupBy { it.definitionId }
        fun unitsOf(predicate: (StoredItem) -> Boolean) = state.items.filter(predicate)
            .groupBy { it.definitionId }.mapValues { (_, v) -> v.sumOf { it.count } }
        val total = unitsOf { true }
        val lockedUnits = unitsOf { it.instanceId in locked }
        val consumable = selectable.mapValues { (_, v) -> v.sumOf { it.count } }
        return recipes.map { recipe ->
            val materials = recipe.ingredients.map { (id, count) ->
                val c = consumable[id] ?: 0
                val l = lockedUnits[id] ?: 0
                val t = total[id] ?: 0
                CraftingMaterial(id, count, t, l, c, (count - c).coerceAtLeast(0), (t - c - l).coerceAtLeast(0))
            }
            // 选择规则沿用既有实现：同定义先未绑定、再按实例号，取前 count 件。
            val ids = recipe.ingredients.flatMap { (id, count) ->
                selectable[id].orEmpty()
                    .sortedWith(compareBy<StoredItem> { it.bound }.thenBy { it.instanceId })
                    .take(count).map { it.instanceId }
            }
            CraftingOption(recipe, materials.sumOf { it.missing }, ids, materials)
        }.sortedWith(compareBy<CraftingOption> { it.missing != 0 }.thenBy { definitions.getValue(it.recipe.outputId).name }.thenBy { it.recipe.id })
    }
}

object WarehouseOrder {
    fun organize(state: InventoryState, definitions: Map<String, ItemDefinition>): InventoryResult {
        if (state.phase != InventoryPhase.HOME) return InventoryResult(state, "挑战中不能整理局外仓库")
        InventoryRules(definitions).validate(state)?.let { return InventoryResult(state, it) }
        val order = listOf(ItemKind.EQUIPMENT, ItemKind.CARD, ItemKind.MATERIAL, ItemKind.SCROLL, ItemKind.CONSUMABLE, ItemKind.LOOT)
        val quality = listOf("白", "绿", "蓝", "紫", "金", "红")
        val sorted = state.items.filter { it.place.container == Container.WAREHOUSE }.sortedWith(
            compareBy<StoredItem> { order.indexOf(definitions.getValue(it.definitionId).kind) }
                .thenByDescending { quality.indexOf(definitions.getValue(it.definitionId).quality) }
                .thenBy { definitions.getValue(it.definitionId).name }.thenByDescending { it.star }.thenBy { it.instanceId })
        val iterator = sorted.iterator()
        return InventoryResult(state.copy(items = state.items.map { if (it.place.container == Container.WAREHOUSE) iterator.next() else it }))
    }
}
