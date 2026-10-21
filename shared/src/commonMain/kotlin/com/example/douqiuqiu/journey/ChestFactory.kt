package com.example.douqiuqiu.journey

import com.example.douqiuqiu.quest.*

import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.grid.*
import com.example.douqiuqiu.loot.*

/** Generates complete fixed-content boxes and consumes only guarantees assigned to displayed boxes. */
class ChestFactory(private val inventory: InventoryRules) {
    private val definitions get() = inventory.definitions
    /** Headless balance sampling: returns a branch, never commits or touches the player's save. */
    fun sample(s: InventoryState, theme: ChestTheme, difficulty: Int, quality: String): Pair<InventoryState, TargetChest> {
        require(s.phase == InventoryPhase.HOME && s.progress.run == null && s.progress.seed in 1..2147483646)
        return generate(s, theme, difficulty, quality, EconomyBalance.ticket(quality, difficulty))
    }
    fun batch(s: InventoryState): InventoryState {
        if (s.progress.run != null || s.progress.chests.isNotEmpty()) return s
        require(s.phase == InventoryPhase.HOME) { "仅局外生成宝箱" }
        // 初始 4 箱，累计 3 次通关扩至 6 箱，10 次扩至 8 箱（design/economy-progression.md）。
        val count = maxOf(QuestProgression.maxDifficulty(s) + 1, when { s.progress.clears >= 10 -> 8; s.progress.clears >= 3 -> 6; else -> 4 })
        val needed = MaterialProgression.eligible(s, definitions).sorted().map(MaterialProgression::theme)
        val themes = (listOf(ChestTheme.SUPPLIES) + needed + ChestTheme.entries).distinct()
        var next = s
        val boxes = mutableListOf<TargetChest>()
        repeat(count) { index ->
            val theme = themes[index % themes.size]
            // 首箱始终简单；所有可进入难度均可在新批次出现，包括少通关但多任务的玩家。
            val unlocked = QuestProgression.maxDifficulty(s)
            val cycledDifficulty = index % (unlocked + 1)
            // 安全箱材料保底要求普通或以上；不能因循环到简单档而失去旧档保底路径。
            val difficulty = if (theme == ChestTheme.SAFE && unlocked >= 1) maxOf(1, cycledDifficulty) else cycledDifficulty
            val quality = listOf("白", "蓝", "紫", "紫", "金", "金")[difficulty]
            val ticket = EconomyBalance.ticket(quality, difficulty)
            val generated = generate(next, theme, difficulty, quality, ticket)
            next = generated.first; boxes += generated.second
        }
        return next.copy(progress = next.progress.copy(chests = boxes))
    }

    fun replace(s: InventoryState, id: String, rescue: Boolean): InventoryResult {
        if (s.phase != InventoryPhase.HOME || s.progress.run != null) return InventoryResult(s, "挑战中不能刷新")
        val old = s.progress.chests.find { it.id == id } ?: return InventoryResult(s, "宝箱不存在")
        if (s.progress.batch == Int.MAX_VALUE) return InventoryResult(s, "宝箱批次已达上限")
        val theme = if (rescue) ChestTheme.SUPPLIES else old.theme
        val difficulty = if (rescue) 0 else old.difficulty
        val nextSeed = FirstRunContent.next(s.progress.seed)
        val qualityIndex = if (rescue) 0 else (nextSeed % 4).toInt()
        val quality = listOf("白", "蓝", "紫", "金")[qualityIndex]
        val ticket = EconomyBalance.ticket(quality, difficulty)
        val generated = generate(s.copy(progress = s.progress.copy(seed = nextSeed)), theme, difficulty, quality, ticket)
        return InventoryResult(generated.first.copy(progress = generated.first.progress.copy(
            chests = s.progress.chests.map { if (it.id == id) generated.second else it })))
    }

    private fun generate(s: InventoryState, theme: ChestTheme, difficulty: Int, quality: String, ticket: Int): Pair<InventoryState, TargetChest> {
        require(s.progress.batch < Int.MAX_VALUE) { "宝箱批次已达上限" }
        val capacity = ChestLayout.capacity(quality)
        val choices = definitions.values.filter { it.kind != ItemKind.LOOT && it.minimumDropClears <= s.progress.clears &&
            ChallengeBalance.allowsDrop(it, difficulty) &&
            it.width <= capacity.columns && it.height <= capacity.rows }
        require(choices.isNotEmpty()) { "没有可用宝箱物品" }
        val eligible = MaterialProgression.eligible(s, definitions, theme, difficulty)
        val guarantee = MaterialPityTracker.consumeForGeneration(s.progress.materialPity, MaterialProgression.policy, eligible)
        // A theme has one material category, so a single box can consume at most one pending guarantee.
        val forced = guarantee.guaranteedCategories.singleOrNull()?.let { MaterialProgression.neededItem(s, it) }
        var seed = s.progress.seed
        fun draw(n: Int): Int { seed = FirstRunContent.next(seed); return (seed % n).toInt() }
        fun pool(surprise: Boolean = false, main: Boolean = false) = choices.filter {
            EconomyBalance.allowed(it, quality, surprise, main)
        }.flatMap { item -> List(weight(item, theme)) { item } }
        val weighted = pool()
        val mainPool = pool(main = true)
        val surprisePool = pool(surprise = true)
        require(weighted.isNotEmpty()) { "当前箱档缺少基础物品配置" }
        val valuables = definitions.values.filter { it.kind == ItemKind.LOOT && it.width <= capacity.columns && it.height <= capacity.rows }.sortedBy { it.id }.flatMap { item ->
            List(ValuableLootCatalog.weight(item.id, quality, difficulty, s.progress.clears)) { item }
        }
        val entries = mutableListOf<ChestItem>()
        repeat(capacity.attempts) { index ->
            val selector = draw(100)
            val selected = when {
                selector < EconomyBalance.mainChance(quality) -> mainPool.ifEmpty { weighted }
                selector < EconomyBalance.mainChance(quality) + EconomyBalance.surpriseChance(quality) -> surprisePool.ifEmpty { weighted }
                else -> weighted
            }
            val random = selected[draw(selected.size)]
            val item = if (index == 0 && forced != null) definitions.getValue(forced)
                else if (valuables.isNotEmpty() && draw(100) < ValuableLootCatalog.attemptPercent(quality, difficulty))
                    valuables[draw(valuables.size)] else random
            val occupied = entries.map { entry -> definitions.getValue(entry.definitionId).let { GridRect(entry.x, entry.y, it.width, it.height) } }
            val packed = FixedGridPacker.pack(GridSize(capacity.columns, capacity.rows), listOf(PackItem("item-$index", item.width, item.height, false)), occupied)
            if (packed.success) {
                val p = packed.placements.single()
                entries += ChestItem(item.id, p.x, p.y, if (index == 0) 2 else if (index % 3 == 0) 1 else 0)
            }
        }
        require(forced == null || entries.any { it.definitionId == forced }) { "保底物品未能放入宝箱" }
        val serial = s.progress.batch + 1
        val box = TargetChest("box-$serial", quality, difficulty, ticket, entries, theme = theme,
            guaranteedCategories = guarantee.guaranteedCategories.toSet())
        return s.copy(progress = s.progress.copy(seed = seed, batch = serial, materialPity = guarantee.state)) to box
    }

    private fun weight(item: ItemDefinition, theme: ChestTheme): Int = when (theme) {
        ChestTheme.ARSENAL -> if (item.kind in setOf(ItemKind.EQUIPMENT, ItemKind.SCROLL)) 4 else 1
        ChestTheme.ARCANE -> if (item.kind == ItemKind.CARD) 4 else 1
        ChestTheme.SUPPLIES -> when { item.kind == ItemKind.CONSUMABLE -> 4; item.id == "essence" -> 3; else -> 1 }
        ChestTheme.BACKPACK -> if (item.id == "backpack_fabric") 6 else if (item.id == "essence") 3 else 1
        ChestTheme.SKILLS -> if (item.id == "skill_shard") 6 else if (item.kind == ItemKind.CARD) 3 else 1
        ChestTheme.SAFE -> if (item.id in setOf("safe_lock", "safe_frame")) 6 else if (item.id == "essence") 3 else 1
    }
}
