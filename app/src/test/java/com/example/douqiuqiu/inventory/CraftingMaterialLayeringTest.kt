package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.CampaignProgress
import org.junit.Assert.*
import org.junit.Test

/**
 * X04：合成材料可用性分层验收，对应 docs/tasks/0.1.4-external-handoff.md。
 *
 * 覆盖：仅锁定材料、锁定加可用、绑定材料、重复配方材料、材料跨仓库位置。
 * 只测只读展示模型；合成执行仍由 CraftingRules.craft 负责。
 */
class CraftingMaterialLayeringTest {
    private val definitions = listOf(
        ItemDefinition("out", "产物", ItemKind.EQUIPMENT, 1, 1, quality = "白"),
        ItemDefinition("mat", "材料", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("other", "别的材料", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("salve", "药剂", ItemKind.CONSUMABLE, 1, 1, maxStack = 3)
    ).associateBy { it.id }
    private val inventory = InventoryRules(definitions)
    private val recipes = listOf(CraftingRecipe("r", "out", mapOf("mat" to 2)))

    private fun options(state: InventoryState, recipeList: List<CraftingRecipe> = recipes) =
        CraftingPresentation.options(state, recipeList, definitions).single { it.recipe.id == "r" }

    private fun material(state: InventoryState, id: String = "mat") =
        options(state).materials.single { it.definitionId == id }

    private fun stateWith(items: List<StoredItem>, locked: Set<String> = emptySet()) =
        InventoryState(items = items, progress = CampaignProgress(lockedItems = locked))

    @Test fun `仅锁定材料时标记持有但已锁定且不可合成`() {
        val s = stateWith(listOf(
            StoredItem("l1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("l2", "mat", ItemPlace(Container.WAREHOUSE))
        ), locked = setOf("l1", "l2"))
        val m = material(s)
        assertEquals(2, m.required)
        assertEquals(2, m.totalOwned)
        assertEquals(2, m.lockedOwned)
        assertEquals(0, m.consumableOwned)
        assertEquals(2, m.missing)
        assertEquals(0, m.elsewhereOwned)
        assertFalse("缺件时整体不可合成", options(s).missing == 0)
    }

    @Test fun `锁定加可用时只按可用量判定且分层互斥完备`() {
        val s = stateWith(listOf(
            StoredItem("l1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("f1", "mat", ItemPlace(Container.WAREHOUSE))
        ), locked = setOf("l1"))
        val m = material(s)
        assertEquals(2, m.totalOwned)
        assertEquals(1, m.lockedOwned)
        assertEquals(1, m.consumableOwned)
        assertEquals(1, m.missing)
        assertEquals(0, m.elsewhereOwned)
        assertEquals("三个分层必须互斥完备", m.totalOwned, m.consumableOwned + m.lockedOwned + m.elsewhereOwned)
    }

    @Test fun `绑定材料照常可合成且不进入任何不可用分层`() {
        val s = stateWith(listOf(
            StoredItem("b1", "mat", ItemPlace(Container.WAREHOUSE), bound = true),
            StoredItem("b2", "mat", ItemPlace(Container.WAREHOUSE), bound = true)
        ))
        val m = material(s)
        assertEquals(2, m.consumableOwned)
        assertEquals(0, m.missing)
        assertEquals(0, m.lockedOwned)
        assertEquals(0, m.elsewhereOwned)
        assertEquals(0, options(s).missing)
        // 选择规则仍优先未绑定：这里两件都绑定，按实例号顺序取前两件
        assertEquals(listOf("b1", "b2"), options(s).ingredientIds)
    }

    @Test fun `重复配方材料按需求数量计算缺失`() {
        val doubled = listOf(CraftingRecipe("r", "out", mapOf("mat" to 4)))
        val s = stateWith(listOf(
            StoredItem("f1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("f2", "mat", ItemPlace(Container.WAREHOUSE))
        ))
        val option = options(s, doubled)
        val m = option.materials.single()
        assertEquals(4, m.required)
        assertEquals(2, m.consumableOwned)
        assertEquals(2, m.missing)
        assertEquals(2, option.missing)
        assertEquals(listOf("f1", "f2"), option.ingredientIds)
    }

    @Test fun `材料跨仓库位置时只算可选池并提示另有的持有`() {
        val s = stateWith(listOf(
            StoredItem("w1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("bp", "mat", ItemPlace(Container.BACKPACK, 1, 0)),
            StoredItem("sf", "mat", ItemPlace(Container.SAFE))
        ))
        val m = material(s)
        assertEquals(3, m.totalOwned)
        assertEquals(1, m.consumableOwned)
        assertEquals(1, m.missing)
        assertEquals(2, m.elsewhereOwned)
        assertEquals(0, m.lockedOwned)
        // 汇总缺口与逐材料缺口一致
        assertEquals(1, options(s).missing)
    }

    @Test fun `仓库内堆叠材料不进入可选池但计入总持有`() {
        val stackable = listOf(CraftingRecipe("r", "out", mapOf("salve" to 2)))
        val s = stateWith(listOf(StoredItem("stack", "salve", ItemPlace(Container.WAREHOUSE), count = 3)))
        val option = options(s, stackable)
        val m = option.materials.single()
        assertEquals(3, m.totalOwned)
        assertEquals(0, m.consumableOwned)
        assertEquals(2, m.missing)
        assertEquals(3, m.elsewhereOwned)
    }

    @Test fun `混合来源时分层之和恒等于总持有`() {
        val s = stateWith(listOf(
            StoredItem("w1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("w2", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("l1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("bp", "mat", ItemPlace(Container.BACKPACK, 0, 1)),
            StoredItem("l2", "mat", ItemPlace(Container.SAFE))
        ), locked = setOf("l1", "l2"))
        val m = material(s)
        assertEquals(5, m.totalOwned)
        assertEquals(2, m.consumableOwned)
        assertEquals(2, m.lockedOwned)
        assertEquals(1, m.elsewhereOwned)
        assertEquals(0, m.missing)
        assertEquals(m.totalOwned, m.consumableOwned + m.lockedOwned + m.elsewhereOwned)
    }

    @Test fun `多材料配方逐项给出分层且汇总一致`() {
        val two = listOf(CraftingRecipe("r", "out", mapOf("mat" to 1, "other" to 2)))
        val s = stateWith(listOf(
            StoredItem("m1", "mat", ItemPlace(Container.WAREHOUSE)),
            StoredItem("o1", "other", ItemPlace(Container.WAREHOUSE))
        ))
        val option = options(s, two)
        val mats = option.materials.associateBy { it.definitionId }
        assertEquals(0, mats.getValue("mat").missing)
        assertEquals(1, mats.getValue("other").missing)
        assertEquals(1, option.missing)
        assertEquals(listOf("m1", "o1"), option.ingredientIds)
    }

    @Test fun `展示选择与craft校验同口径未锁定优先`() {
        val s = stateWith(listOf(
            StoredItem("bound", "mat", ItemPlace(Container.WAREHOUSE), bound = true),
            StoredItem("free", "mat", ItemPlace(Container.WAREHOUSE))
        ))
        val option = options(s)
        assertEquals(listOf("free", "bound"), option.ingredientIds)
        // 选中集合必须全部通过 craft 的材料校验
        val crafted = CraftingRules(inventory, recipes).craft(s, "r", option.ingredientIds, "out-1")
        assertTrue(crafted.error ?: "ok", crafted.ok)
    }
}
