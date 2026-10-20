package com.example.douqiuqiu.inventory

/**
 * 装备配方图校验（纯 Kotlin common，无 Android / java.* / 随机数 / 时钟依赖）。
 *
 * 在配置加载时检查配方是否引用缺失物品、产生循环或存在歧义，并给出可读的问题列表。
 * 只检查定义：不合成物品、不扣材料、不核验 Dota 来源、不决定价格。
 *
 * 复用共享类型 [CraftingRecipe]（shared 的 inventory/Crafting.kt），不修改它。
 * itemIds 只提供合法物品 ID 集合，不要求所有物品都有配方——材料/卷轴直接存在于集合中即视为叶子节点。
 */
object RecipeGraphValidator {

    /**
     * 校验配方表，返回全部可安全检查的问题、拓扑构建顺序（配方 ID，组件在前）。
     *
     * - 无任何问题时 valid=true，buildOrder 按「先组件配方后上级配方」排列，
     *   独立分支以配方 ID 字典序稳定排序；存在任何问题时 buildOrder 为空。
     * - 循环以 CYCLE 问题返回，path 为闭环的物品 ID 序列（如 [A,B,C,A]）；
     *   同一循环只报一次，多个独立循环各给至少一个证据。
     * - 存在重复配方 ID / 重复产物等歧义时，跳过这些歧义节点的循环检查（不崩溃、不无限递归）。
     * - itemIds 含空白 ID 属于调用参数错误，抛 IllegalArgumentException；
     *   配方内容问题一律通过 issues 返回，不直接抛异常。
     * - 不修改输入，结果为独立快照。
     */
    fun validate(itemIds: Set<String>, recipes: List<CraftingRecipe>): RecipeGraphResult {
        require(itemIds.none { it.isBlank() }) { "itemIds 包含空白 ID" }

        val issues = ArrayList<RecipeIssue>()

        // ---- 逐配方内容检查（按输入顺序，汇总全部可发现问题） ----
        val idCount = HashMap<String, Int>()
        val outputCount = HashMap<String, Int>()
        for (r in recipes) {
            if (r.id.isNotBlank()) idCount[r.id] = (idCount[r.id] ?: 0) + 1
            if (r.outputId.isNotBlank()) outputCount[r.outputId] = (outputCount[r.outputId] ?: 0) + 1
        }
        val seenIds = HashSet<String>()
        for (r in recipes) {
            if (r.id.isBlank()) {
                issues += RecipeIssue(RecipeIssueKind.BLANK_RECIPE_ID, r.id, r.outputId)
            } else if (!seenIds.add(r.id)) {
                issues += RecipeIssue(RecipeIssueKind.DUPLICATE_RECIPE_ID, r.id, r.outputId)
            }
            if (r.outputId.isBlank() || r.outputId !in itemIds) {
                issues += RecipeIssue(RecipeIssueKind.UNKNOWN_OUTPUT, r.id, r.outputId)
            }
            if (r.ingredients.isEmpty()) {
                issues += RecipeIssue(RecipeIssueKind.EMPTY_INGREDIENTS, r.id, r.outputId)
            }
            for ((ingredientId, count) in r.ingredients) {
                if (ingredientId.isBlank() || ingredientId !in itemIds) {
                    issues += RecipeIssue(RecipeIssueKind.UNKNOWN_INGREDIENT, r.id, ingredientId)
                }
                if (count <= 0) {
                    issues += RecipeIssue(RecipeIssueKind.INVALID_COUNT, r.id, ingredientId)
                }
            }
        }

        // ---- 一件产物只允许一条配方：重复产物报歧义，不自行选择或合并 ----
        val seenOutputs = HashSet<String>()
        for (r in recipes) {
            if (r.outputId.isBlank()) continue
            if (!seenOutputs.add(r.outputId)) {
                issues += RecipeIssue(RecipeIssueKind.DUPLICATE_OUTPUT, r.id, r.outputId)
            }
        }

        // ---- 循环检测：只走无歧义节点（配方 ID 唯一且产物唯一） ----
        val nodeRecipe = HashMap<String, CraftingRecipe>()
        for (r in recipes) {
            if (r.id.isBlank() || idCount[r.id] != 1) continue
            if (r.outputId.isBlank() || outputCount[r.outputId] != 1) continue
            if (r.outputId in itemIds) nodeRecipe[r.outputId] = r
        }
        val edges = HashMap<String, List<String>>()
        for ((outputId, r) in nodeRecipe) {
            edges[outputId] = r.ingredients.keys.filter { it in nodeRecipe }
        }
        issues += findCycles(nodeRecipe, edges)

        if (issues.isNotEmpty()) {
            return RecipeGraphResult(valid = false, issues = issues, buildOrder = emptyList())
        }
        return RecipeGraphResult(valid = true, issues = emptyList(), buildOrder = buildOrder(recipes))
    }

    // ---------------- 循环检测（DFS 三色 + 栈） ----------------

    private const val WHITE = 0
    private const val GRAY = 1
    private const val BLACK = 2

    private fun findCycles(
        nodeRecipe: Map<String, CraftingRecipe>,
        edges: Map<String, List<String>>
    ): List<RecipeIssue> {
        val color = HashMap<String, Int>()
        val stack = ArrayList<String>()
        val found = ArrayList<RecipeIssue>()

        fun dfs(node: String) {
            color[node] = GRAY
            stack.add(node)
            for (next in edges[node] ?: emptyList()) {
                when (color[next] ?: WHITE) {
                    GRAY -> {
                        // 回边：闭环证据，同一循环只在此处报一次
                        val from = stack.indexOf(next)
                        val path = stack.subList(from, stack.size).toMutableList()
                        path.add(next)
                        found += RecipeIssue(
                            RecipeIssueKind.CYCLE,
                            nodeRecipe.getValue(node).id,
                            next,
                            path
                        )
                    }
                    WHITE -> dfs(next)
                }
            }
            stack.removeAt(stack.size - 1)
            color[node] = BLACK
        }

        // 排序后遍历，保证问题顺序稳定
        for (node in nodeRecipe.keys.sorted()) {
            if ((color[node] ?: WHITE) == WHITE) dfs(node)
        }
        return found
    }

    // ---------------- 拓扑构建顺序（Kahn + 字典序） ----------------

    private fun buildOrder(recipes: List<CraftingRecipe>): List<String> {
        val recipeByOutput = HashMap<String, String>()
        for (r in recipes) recipeByOutput[r.outputId] = r.id

        // recipeId -> 依赖的配方 ID 集合（组件配方在前）
        val deps = HashMap<String, MutableSet<String>>()
        val dependents = HashMap<String, MutableSet<String>>()
        for (r in recipes) {
            for (ingredientId in r.ingredients.keys) {
                val dep = recipeByOutput[ingredientId] ?: continue
                if (dep == r.id) continue
                deps.getOrPut(r.id) { LinkedHashSet() }.add(dep)
                dependents.getOrPut(dep) { LinkedHashSet() }.add(r.id)
            }
        }

        // 通用集合实现字典序最小优先（common 不可用 java.util.TreeSet）
        val ready = ArrayList<String>()
        for (r in recipes) {
            if (deps[r.id].isNullOrEmpty()) ready.add(r.id)
        }
        val order = ArrayList<String>(recipes.size)
        while (ready.isNotEmpty()) {
            val id = ready.minOrNull()!!
            ready.remove(id)
            order.add(id)
            for (dependent in dependents[id] ?: emptySet()) {
                val remaining = deps.getValue(dependent)
                remaining.remove(id)
                if (remaining.isEmpty()) ready.add(dependent)
            }
        }
        return order
    }
}

/** 配方问题类型 */
enum class RecipeIssueKind {
    BLANK_RECIPE_ID, DUPLICATE_RECIPE_ID, UNKNOWN_OUTPUT, UNKNOWN_INGREDIENT,
    INVALID_COUNT, EMPTY_INGREDIENTS, DUPLICATE_OUTPUT, CYCLE
}

/** 一条配方问题；CYCLE 时 path 为闭环物品 ID 序列，recipeId 为形成环的配方，itemId 为闭合点的物品 */
data class RecipeIssue(
    val kind: RecipeIssueKind,
    val recipeId: String,
    val itemId: String = "",
    val path: List<String> = emptyList()
)

/** 校验结果：无问题时 buildOrder 为配方 ID 拓扑序，有任何问题时为空 */
data class RecipeGraphResult(
    val valid: Boolean,
    val issues: List<RecipeIssue>,
    val buildOrder: List<String>
)
