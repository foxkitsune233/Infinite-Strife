package com.example.douqiuqiu.journey

import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.content.EquipmentCategory
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind

/** v4 adds six difficulty tiers; existing v1..v3 gold tables remain unchanged. */
object EconomyBalance {
    const val VERSION = 4
    const val INITIAL_GOLD = 600
    const val SHOP_REFRESH = 50
    const val RELIEF_SUBSIDY = 120
    private val qualities = listOf("白", "蓝", "紫", "金")
    private val mainIds by lazy { EquipmentCatalog.all.filter { it.category == EquipmentCategory.MAIN }.map { it.id }.toSet() }
    fun rank(quality: String) = qualities.indexOf(quality).also { require(it >= 0) }
    fun ticket(quality: String, difficulty: Int): Int {
        require(difficulty in ChallengeBalance.difficulties)
        val doubled = listOf(120, 220, 420, 700)[rank(quality)] * listOf(2, 3, 4, 5, 6, 8)[difficulty]
        return (doubled + 19) / 20 * 10
    }
    fun appraisalFee(count: Int): Int { require(count >= 0); return 10 + minOf(count, 4) * 10 }
    fun chestRefresh(quality: String) = listOf(20, 30, 50, 80)[rank(quality)]
    fun subsidized(c: TargetChest) = c.difficulty == 0 && c.quality == "白"
    fun goldReward(difficulty: Int, round: Int, version: Int = VERSION): Int {
        require(difficulty in ChallengeBalance.difficulties && round in 1..5 && version in 1..VERSION)
        if (version == 1) return (if (difficulty == 0) listOf(20, 30, 40, 55, 70) else listOf(35, 50, 70, 90, 120))[round - 1]
        return listOf(listOf(20, 30, 45, 60, 80), listOf(30, 45, 65, 90, 120), listOf(45, 65, 90, 125, 165),
            listOf(60, 90, 125, 170, 220), listOf(80, 120, 165, 220, 285), listOf(105, 155, 215, 285, 365))[difficulty][round - 1]
    }
    fun mainChance(quality: String) = listOf(0, 0, 5, 10)[rank(quality)]
    fun surpriseChance(quality: String) = listOf(2, 3, 3, 0)[rank(quality)]
    fun allowed(d: ItemDefinition, quality: String, surprise: Boolean = false, main: Boolean = false): Boolean {
        if (d.kind == ItemKind.LOOT) return false
        // Progression materials, including the assigned pity item, are never price-gated.
        if (d.kind == ItemKind.MATERIAL) return !main
        val rank = rank(quality)
        if (d.id in mainIds && (rank < 2 || !main && !(surprise && rank == 2))) return false
        if (main && d.id !in mainIds) return false
        val cap = (if (surprise) listOf(700, 2000, 5000, 8000) else listOf(300, 1200, 3500, 8000))[rank]
        return d.price <= cap
    }
}
