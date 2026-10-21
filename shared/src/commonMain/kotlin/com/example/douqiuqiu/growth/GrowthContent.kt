package com.example.douqiuqiu.growth

import com.example.douqiuqiu.inventory.InventoryState

enum class GrowthKind { HERO, SKILLS, BACKPACK, SAFE, EQUIPMENT_GUARD, SKILL_GUARD }
data class GrowthUpgrade(val id: String, val name: String, val kind: GrowthKind, val level: Int,
    val gold: Int, val materials: Map<String, Int>, val minClears: Int = 0,
    val extraction: Boolean = false, val heroId: String? = null, val backpackLevel: Int = 0,
    /** 非空时该成长项目的资格 = 对应任务已领取（HERO 解锁改走任务通道）；为空时沿用金币+材料判定。 */
    val questId: String? = null)

/** First-release progression table approved in v1; it grants no permanent combat stats. */
object GrowthContent {
    val backpackSizes = listOf(4 to 2, 4 to 3, 5 to 3, 6 to 3, 6 to 4)
    val safeSizes = listOf(2 to 2, 3 to 2, 3 to 3)
    val upgrades = listOf(
        GrowthUpgrade("hero:skywrath", "解锁天怒法师", GrowthKind.HERO, 1, 300, mapOf("essence" to 3), extraction = true, heroId = "skywrath"),
        GrowthUpgrade("hero:viper", "解锁冥界亚龙", GrowthKind.HERO, 1, 500, mapOf("essence" to 5), 1, heroId = "viper"),
        GrowthUpgrade("hero:phantom_assassin", "解锁幻影刺客", GrowthKind.HERO, 1, 700, mapOf("essence" to 7), 3, heroId = "phantom_assassin"),
        GrowthUpgrade("hero:necrophos", "解锁瘟疫法师", GrowthKind.HERO, 1, 900, mapOf("essence" to 9), 5, heroId = "necrophos"),
        GrowthUpgrade("skills:1", "技能栏4→5", GrowthKind.SKILLS, 1, 250, mapOf("essence" to 3), extraction = true),
        GrowthUpgrade("skills:2", "技能栏5→6", GrowthKind.SKILLS, 2, 500, mapOf("essence" to 5), 1),
        GrowthUpgrade("skills:3", "技能栏6→7", GrowthKind.SKILLS, 3, 900, mapOf("essence" to 8, "skill_shard" to 2), 3),
        GrowthUpgrade("skills:4", "技能栏7→8", GrowthKind.SKILLS, 4, 1500, mapOf("essence" to 12, "skill_shard" to 4), 6),
        GrowthUpgrade("backpack:1", "背包4×2→4×3", GrowthKind.BACKPACK, 1, 200, mapOf("backpack_fabric" to 2), extraction = true),
        GrowthUpgrade("backpack:2", "背包4×3→5×3", GrowthKind.BACKPACK, 2, 400, mapOf("backpack_fabric" to 3), 1),
        GrowthUpgrade("backpack:3", "背包5×3→6×3", GrowthKind.BACKPACK, 3, 700, mapOf("backpack_fabric" to 5), 3),
        GrowthUpgrade("backpack:4", "背包6×3→6×4", GrowthKind.BACKPACK, 4, 1000, mapOf("backpack_fabric" to 8), 6),
        GrowthUpgrade("safe:1", "安全箱2×2→3×2", GrowthKind.SAFE, 1, 600, mapOf("safe_lock" to 1), 1),
        GrowthUpgrade("safe:2", "安全箱3×2→3×3", GrowthKind.SAFE, 2, 1200, mapOf("safe_frame" to 1), 5, backpackLevel = 1),
        // 0.1.6 栏位强化：安全箱满级后，无用的 safe_lock / safe_frame 经转化任务回收为护芯，
        // 在此开启死亡保护槽。门槛（通关数）让保护槽随进度逐步开放。
        GrowthUpgrade("equip_guard:1", "装备保护 1 格", GrowthKind.EQUIPMENT_GUARD, 1, 300, mapOf("equipment_guard_core" to 1), 2),
        GrowthUpgrade("equip_guard:2", "装备保护 2 格", GrowthKind.EQUIPMENT_GUARD, 2, 700, mapOf("equipment_guard_core" to 2), 4),
        GrowthUpgrade("equip_guard:3", "装备保护 3 格", GrowthKind.EQUIPMENT_GUARD, 3, 1200, mapOf("equipment_guard_core" to 3), 6),
        GrowthUpgrade("skill_guard:1", "技能保护 1 格", GrowthKind.SKILL_GUARD, 1, 300, mapOf("skill_guard_core" to 1), 2),
        GrowthUpgrade("skill_guard:2", "技能保护 2 格", GrowthKind.SKILL_GUARD, 2, 700, mapOf("skill_guard_core" to 2), 4),
        GrowthUpgrade("skill_guard:3", "技能保护 3 格", GrowthKind.SKILL_GUARD, 3, 1200, mapOf("skill_guard_core" to 3), 6),
        GrowthUpgrade("skill_guard:4", "技能保护 4 格", GrowthKind.SKILL_GUARD, 4, 1800, mapOf("skill_guard_core" to 4), 8)
    )
    fun level(s: InventoryState, kind: GrowthKind): Int = when (kind) {
        GrowthKind.HERO -> 0
        GrowthKind.SKILLS -> s.skillSlots - 4
        GrowthKind.BACKPACK -> backpackSizes.indexOf(s.backpackWidth to s.backpackHeight)
        GrowthKind.SAFE -> safeSizes.indexOf(s.safeWidth to s.safeHeight)
        GrowthKind.EQUIPMENT_GUARD -> s.progress.equipmentGuardLevel
        GrowthKind.SKILL_GUARD -> s.progress.skillGuardLevel
    }
    fun nextChoices(s: InventoryState): List<GrowthUpgrade> = upgrades.filter {
        if (it.kind == GrowthKind.HERO) it.heroId !in s.progress.unlockedHeroes else it.level == level(s, it.kind) + 1
    }
}
