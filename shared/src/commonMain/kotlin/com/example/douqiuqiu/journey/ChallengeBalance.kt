package com.example.douqiuqiu.journey

import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.content.EquipmentCategory
import com.example.douqiuqiu.inventory.ItemDefinition

/** Versioned fixed progression. Never scales enemies to player equipment or HP. */
object ChallengeBalance {
    const val COUNT = 6
    val difficulties = 0 until COUNT
    val names = listOf("简单", "普通", "困难", "精英", "大师", "噩梦")
    fun name(difficulty: Int): String = names[difficulty]
    data class EnemyScale(val health: Double, val attack: Double, val attackSpeed: Double = 0.0, val armor: Double = 0.0)
    data class HardBudget(val cards: Int, val twoStars: Int, val goldCards: Int, val gear: Int, val gearValue: Int)
    private val hardScales = listOf(
        EnemyScale(1.10, 1.05), EnemyScale(1.35, 1.18, 8.0, 1.0),
        EnemyScale(1.65, 1.35, 16.0, 2.0), EnemyScale(2.00, 1.55, 24.0, 3.0),
        EnemyScale(2.40, 1.80, 32.0, 4.0))
    private val hardBudgets = listOf(
        HardBudget(3, 1, 0, 2, 900), HardBudget(4, 2, 1, 3, 2200),
        HardBudget(6, 3, 1, 4, 5500), HardBudget(7, 4, 2, 5, 9000),
        HardBudget(8, 6, 3, 6, 16000))
    fun enhanced(run: JourneyState) = run.target.difficulty > 0 && run.rewardVersion >= 4 || run.target.difficulty == 2 && run.rewardVersion == 3
    /** Independent budgets: no reading player equipment, wallet or health. */
    fun budget(run: JourneyState): HardBudget {
        require(run.round in 1..5 && run.target.difficulty in difficulties)
        val base = hardBudget(run.round)
        if (run.rewardVersion < 4) return base
        val d = run.target.difficulty
        val cards = listOf(0, 2, 3, 4, 5, 6)[d] + run.round - 1
        val values = listOf(
            listOf(60, 100, 230, 360, 540),
            listOf(800, 1900, 3500, 5500, 8000),
            listOf(3000, 3800, 6500, 10000, 16000),
            listOf(4000, 6500, 10000, 15000, 21000),
            listOf(6500, 10000, 14500, 20500, 28000),
            listOf(10000, 14500, 20000, 27500, 36000))
        return HardBudget(cards.coerceIn(1, 8), (run.round + d - 3).coerceIn(0, 8),
            (run.round + d - 4).coerceIn(0, 4), (1 + d + (run.round - 1) / 2).coerceAtMost(6), values[d][run.round - 1])
    }
    fun activeQuota(run: JourneyState): Int = if (run.rewardVersion < 4 || run.target.difficulty == 0) 0
        else (listOf(0, 0, 1, 1, 2, 3)[run.target.difficulty] + (run.round - 1) / 2).coerceAtMost(4)
    fun hardBudget(round: Int) = hardBudgets[round.also { require(it in 1..5) } - 1]
    fun scale(run: JourneyState): EnemyScale {
        require(run.round in 1..5 && run.target.difficulty in difficulties)
        if (run.rewardVersion >= 4 && run.target.difficulty > 0) {
            val d = run.target.difficulty
            val r = run.round - 1
            return EnemyScale(listOf(0.0, 1.00, 1.20, 1.45, 1.75, 2.10)[d] + r * listOf(0.0, .18, .30, .40, .52, .65)[d],
                listOf(0.0, .95, 1.10, 1.25, 1.45, 1.65)[d] + r * listOf(0.0, .10, .16, .21, .27, .34)[d],
                (d - 1) * 8.0 + r * (d + 1) * 2.0, (d - 1) * 1.5 + r * (d + 1) * .5)
        }
        if (enhanced(run)) return hardScales[run.round - 1]
        val original = .62 + run.round * .04 + run.target.difficulty * .16
        return EnemyScale(original, original)
    }

    private val equipmentTiers: Map<String, Int> by lazy {
        val mains = EquipmentCatalog.all.filter { it.category == EquipmentCategory.MAIN }
        buildMap {
            fun tier(price: Int) = when { price >= 5500 -> 5; price >= 4500 -> 4; price >= 3500 -> 3; price >= 2500 -> 2; else -> 1 }
            mains.forEach { put(it.id, tier(it.price)) }
            EquipmentCatalog.all.filter { it.category == EquipmentCategory.SCROLL }.forEach { scroll ->
                val owners = mains.filter { it.recipe?.ingredients?.containsKey(scroll.id) == true }
                if (owners.isNotEmpty()) put(scroll.id, owners.minOf { tier(it.price) })
            }
        }
    }
    fun minimumDropDifficulty(id: String): Int = equipmentTiers[id] ?: 0
    fun allowsDrop(d: ItemDefinition, difficulty: Int, version: Int = EconomyBalance.VERSION): Boolean {
        require(difficulty in difficulties)
        if (version == 3) {
            val tier = minimumDropDifficulty(d.id)
            return (if (tier >= 4) 2 else if (tier > 0) 1 else 0) <= difficulty
        }
        return minimumDropDifficulty(d.id) <= difficulty
    }
}
