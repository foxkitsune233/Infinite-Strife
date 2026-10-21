package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.quest.*

data class CraftingRecipe(val id: String, val outputId: String, val ingredients: Map<String, Int>)

class CraftingRules(private val inventory: InventoryRules, recipes: List<CraftingRecipe>) {
    val recipes = recipes.associateBy { it.id }
    init {
        require(this.recipes.size == recipes.size)
        recipes.forEach { recipe ->
            require(recipe.id.isNotBlank() && recipe.ingredients.isNotEmpty())
            require(inventory.definitions[recipe.outputId]?.kind == ItemKind.EQUIPMENT)
            require(recipe.ingredients.all { (id, count) -> id in inventory.definitions && count in 1..20 })
        }
    }

    fun craft(state: InventoryState, recipeId: String, ingredientInstanceIds: List<String>, outputInstanceId: String): InventoryResult {
        fun fail(reason: String) = InventoryResult(state, reason)
        inventory.validate(state)?.let { return fail(it) }
        if (state.phase != InventoryPhase.HOME) return fail("仅局外可合成")
        val recipe = recipes[recipeId] ?: return fail("配方不存在")
        if (ingredientInstanceIds.size != ingredientInstanceIds.toSet().size) return fail("材料实例重复")
        if (outputInstanceId.isBlank() || state.items.any { it.instanceId == outputInstanceId }) return fail("产物实例无效或重复")
        val selected = ingredientInstanceIds.map { id -> state.items.singleOrNull { it.instanceId == id } ?: return fail("找不到材料") }
        if (ingredientInstanceIds.any { it in state.progress.lockedItems }) return fail("锁定物品不可用于合成")
        if (selected.any { it.place.container != Container.WAREHOUSE || it.count != 1 }) return fail("请使用仓库内独立材料")
        if (selected.groupingBy { it.definitionId }.eachCount() != recipe.ingredients) return fail("组件或卷轴不匹配")
        val output = StoredItem(outputInstanceId, recipe.outputId, ItemPlace(Container.WAREHOUSE), bound = selected.any { it.bound })
        val next = state.copy(items = state.items.filterNot { it.instanceId in ingredientInstanceIds } + output)
        return inventory.validate(next)?.let { fail(it) } ?: InventoryResult(QuestTracker.record(next, QuestEvent.Crafted))
    }
}

/** Raw item bonuses only. Attribute-derived HP/mana/armor are calculated once in loadout. */
data class EquipmentStats(
    val strength: Double = 0.0, val agility: Double = 0.0, val intelligence: Double = 0.0,
    val health: Double = 0.0, val healthRegen: Double = 0.0,
    val armor: Double = 0.0, val attackSpeed: Double = 0.0,
    val manaPercent: Double = 0.0, val manaRegen: Double = 0.0, val attack: Double = 0.0
)
