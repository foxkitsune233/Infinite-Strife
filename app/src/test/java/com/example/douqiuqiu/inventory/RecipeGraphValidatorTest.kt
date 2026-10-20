package com.example.douqiuqiu.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecipeGraphValidator 验收用例，对应 docs/tasks/05-recipe-graph-validator.md。
 */
class RecipeGraphValidatorTest {

    private fun recipe(id: String, output: String, vararg ingredients: Pair<String, Int>) =
        CraftingRecipe(id, output, linkedMapOf(*ingredients))

    private fun kinds(r: RecipeGraphResult, kind: RecipeIssueKind) =
        r.issues.filter { it.kind == kind }

    @Test
    fun `三条基础配方合法`() {
        val recipes = listOf(
            recipe("r_bracer", "bracer", "gloves" to 2),
            recipe("r_sash", "sash", "leather" to 1),
            recipe("r_circlet", "circlet", "gem" to 1)
        )
        val r = RecipeGraphValidator.validate(
            setOf("bracer", "sash", "circlet", "gloves", "leather", "gem"), recipes
        )
        assertTrue(r.valid)
        assertTrue(r.issues.isEmpty())
        // 独立分支按配方 ID 字典序
        assertEquals(listOf("r_bracer", "r_circlet", "r_sash"), r.buildOrder)
    }

    @Test
    fun `合法两级链`() {
        val recipes = listOf(
            recipe("r_base", "base", "m" to 1),
            recipe("r_top", "top", "base" to 2)
        )
        val r = RecipeGraphValidator.validate(setOf("base", "top", "m"), recipes)
        assertTrue(r.valid)
        assertEquals(listOf("r_base", "r_top"), r.buildOrder)
    }

    @Test
    fun `菱形依赖不是循环`() {
        val recipes = listOf(
            recipe("r_d", "d", "m" to 1),
            recipe("r_l", "l", "d" to 1),
            recipe("r_r", "r", "d" to 1),
            recipe("r_t", "t", "l" to 1, "r" to 1)
        )
        val res = RecipeGraphValidator.validate(setOf("d", "l", "r", "t", "m"), recipes)
        assertTrue(res.valid)
        assertTrue(kinds(res, RecipeIssueKind.CYCLE).isEmpty())
        assertEquals(listOf("r_d", "r_l", "r_r", "r_t"), res.buildOrder)
    }

    @Test
    fun `缺组件`() {
        val res = RecipeGraphValidator.validate(
            setOf("sword"), listOf(recipe("r1", "sword", "iron" to 2))
        )
        assertFalse(res.valid)
        assertEquals(1, kinds(res, RecipeIssueKind.UNKNOWN_INGREDIENT).size)
        assertEquals("iron", kinds(res, RecipeIssueKind.UNKNOWN_INGREDIENT).single().itemId)
        assertTrue(res.buildOrder.isEmpty())
    }

    @Test
    fun `缺产物`() {
        val res = RecipeGraphValidator.validate(
            setOf("iron"), listOf(recipe("r1", "ghost", "iron" to 1))
        )
        assertFalse(res.valid)
        assertEquals("ghost", kinds(res, RecipeIssueKind.UNKNOWN_OUTPUT).single().itemId)
    }

    @Test
    fun `零及负数量`() {
        val res = RecipeGraphValidator.validate(
            setOf("sword", "iron"),
            listOf(recipe("r1", "sword", "iron" to 0), recipe("r2", "sword", "iron" to -2))
        )
        assertFalse(res.valid)
        assertEquals(2, kinds(res, RecipeIssueKind.INVALID_COUNT).size)
    }

    @Test
    fun `空配方`() {
        val res = RecipeGraphValidator.validate(
            setOf("sword"), listOf(CraftingRecipe("r1", "sword", emptyMap()))
        )
        assertFalse(res.valid)
        assertEquals(1, kinds(res, RecipeIssueKind.EMPTY_INGREDIENTS).size)
    }

    @Test
    fun `空白与重复配方ID`() {
        val res = RecipeGraphValidator.validate(
            setOf("a", "m"),
            listOf(
                CraftingRecipe(" ", "a", mapOf("m" to 1)),
                recipe("r1", "a", "m" to 1),
                recipe("r1", "a", "m" to 2)
            )
        )
        assertFalse(res.valid)
        assertEquals(1, kinds(res, RecipeIssueKind.BLANK_RECIPE_ID).size)
        assertEquals(1, kinds(res, RecipeIssueKind.DUPLICATE_RECIPE_ID).size)
    }

    @Test
    fun `重复产物`() {
        val res = RecipeGraphValidator.validate(
            setOf("a", "m"),
            listOf(recipe("r1", "a", "m" to 1), recipe("r2", "a", "m" to 2))
        )
        assertFalse(res.valid)
        // 多出的那条配方报一次 DUPLICATE_OUTPUT（不自行选择其一或合并）
        assertEquals(1, kinds(res, RecipeIssueKind.DUPLICATE_OUTPUT).size)
        assertEquals("r2", kinds(res, RecipeIssueKind.DUPLICATE_OUTPUT).single().recipeId)
        assertTrue(res.buildOrder.isEmpty())
    }

    @Test
    fun `自循环`() {
        val res = RecipeGraphValidator.validate(
            setOf("a"), listOf(recipe("r1", "a", "a" to 1))
        )
        assertFalse(res.valid)
        val cycles = kinds(res, RecipeIssueKind.CYCLE)
        assertEquals(1, cycles.size)
        assertEquals(listOf("a", "a"), cycles.single().path)
    }

    @Test
    fun `三节点循环只报一次`() {
        val res = RecipeGraphValidator.validate(
            setOf("a", "b", "c"),
            listOf(
                recipe("r1", "a", "b" to 1),
                recipe("r2", "b", "c" to 1),
                recipe("r3", "c", "a" to 1)
            )
        )
        assertFalse(res.valid)
        val cycles = kinds(res, RecipeIssueKind.CYCLE)
        assertEquals(1, cycles.size)
        val path = cycles.single().path
        assertEquals(path.first(), path.last())
        assertEquals(4, path.size)
        assertEquals(setOf("a", "b", "c"), path.toSet())
    }

    @Test
    fun `两个独立循环各给证据`() {
        val res = RecipeGraphValidator.validate(
            setOf("a", "b", "c", "d"),
            listOf(
                recipe("r1", "a", "b" to 1),
                recipe("r2", "b", "a" to 1),
                recipe("r3", "c", "d" to 1),
                recipe("r4", "d", "c" to 1)
            )
        )
        assertFalse(res.valid)
        assertEquals(2, kinds(res, RecipeIssueKind.CYCLE).size)
    }

    @Test
    fun `重复产物歧义时不崩且跳过歧义节点循环检查`() {
        // x 有两条配方（歧义），其中一条构成自循环；工具必须不崩溃、不无限递归
        val res = RecipeGraphValidator.validate(
            setOf("x", "m"),
            listOf(
                recipe("r1", "x", "m" to 1),
                recipe("r2", "x", "x" to 1)
            )
        )
        assertFalse(res.valid)
        assertTrue(kinds(res, RecipeIssueKind.DUPLICATE_OUTPUT).isNotEmpty())
        // 歧义节点被跳过，不应再产出 CYCLE
        assertTrue(kinds(res, RecipeIssueKind.CYCLE).isEmpty())
    }

    @Test
    fun `空配方列表合法`() {
        val res = RecipeGraphValidator.validate(setOf("a", "m"), emptyList())
        assertTrue(res.valid)
        assertTrue(res.buildOrder.isEmpty())
    }

    @Test
    fun `叶子组件不必有配方`() {
        val res = RecipeGraphValidator.validate(
            setOf("sword", "iron"), listOf(recipe("r1", "sword", "iron" to 2))
        )
        assertTrue(res.valid)
        assertEquals(listOf("r1"), res.buildOrder)
    }

    @Test
    fun `输入不变`() {
        val recipes = listOf(
            recipe("r1", "a", "b" to 1),
            recipe("r2", "b", "a" to 1)
        )
        val before = recipes.map { it.copy(ingredients = LinkedHashMap(it.ingredients)) }
        RecipeGraphValidator.validate(setOf("a", "b"), recipes)
        assertEquals(before, recipes)
    }

    @Test
    fun `itemIds空白ID抛异常`() {
        try {
            RecipeGraphValidator.validate(setOf("a", " "), emptyList())
            throw AssertionError("未抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }
}
