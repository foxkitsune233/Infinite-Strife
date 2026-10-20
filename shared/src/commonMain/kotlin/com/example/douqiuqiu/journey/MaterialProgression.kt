package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.loot.*
import com.example.douqiuqiu.growth.*

/** Maps permanent growth needs to the already delivered category-only pity tracker. */
object MaterialProgression {
    val policy = MaterialPityPolicy(mapOf("backpack" to 4, "skills" to 4, "safe" to 6))
    fun theme(category: String): ChestTheme = when (category) {
        "backpack" -> ChestTheme.BACKPACK; "skills" -> ChestTheme.SKILLS; "safe" -> ChestTheme.SAFE
        else -> error("未知材料类别")
    }
    fun category(definitionId: String): String? = when (definitionId) {
        "backpack_fabric" -> "backpack"; "skill_shard" -> "skills"; "safe_lock", "safe_frame" -> "safe"; else -> null
    }
    fun neededItem(s: InventoryState, category: String): String? {
        val candidate = when (category) {
        "backpack" -> if (s.progress.extracted && (s.backpackWidth != 6 || s.backpackHeight != 4)) "backpack_fabric" else null
        "skills" -> if (s.skillSlots in 6..7 && s.progress.clears >= if (s.skillSlots == 6) 3 else 6) "skill_shard" else null
        "safe" -> when {
            s.safeWidth == 2 && s.progress.clears >= 1 -> "safe_lock"
            s.safeWidth == 3 && s.safeHeight == 2 && s.progress.clears >= 5 && s.backpackHeight >= 3 -> "safe_frame"
            else -> null
        }
        else -> null
        } ?: return null
        val kind = when (category) { "backpack" -> GrowthKind.BACKPACK; "skills" -> GrowthKind.SKILLS; else -> GrowthKind.SAFE }
        val upgrade = GrowthContent.nextChoices(s).firstOrNull { it.kind == kind } ?: return null
        if (s.progress.clears < upgrade.minClears || upgrade.extraction && !s.progress.extracted ||
            GrowthContent.level(s, GrowthKind.BACKPACK) < upgrade.backpackLevel) return null
        val cost = upgrade.materials[candidate] ?: return null
        val stored = s.items.count { it.definitionId == candidate && it.place.container == Container.WAREHOUSE }
        return candidate.takeIf { stored < cost }
    }
    fun eligible(s: InventoryState, definitions: Map<String, ItemDefinition>, theme: ChestTheme? = null, difficulty: Int = 2): Set<String> =
        policy.thresholds.keys.filter { category ->
            val material = neededItem(s, category)
            material != null && definitions[material]?.kind == ItemKind.MATERIAL &&
                definitions.getValue(material).minimumDropClears <= s.progress.clears &&
                (theme == null || MaterialProgression.theme(category) == theme) && difficulty >= if (category == "safe") 1 else 0
        }.toSet()

    fun record(before: InventoryState, settled: InventoryState, definitions: Map<String, ItemDefinition>, cleared: Boolean): MaterialPityState {
        val run = before.progress.run ?: return before.progress.materialPity
        // Only instances actually granted in this run count; old warehouse/carried-in materials cannot reset misses.
        val brought = settled.items.filter { it.instanceId.startsWith("${run.id}:") }
            .mapNotNull { category(it.definitionId) }.toSet()
        val misses = if (cleared) eligible(before, definitions, run.target.theme, run.target.difficulty) else emptySet()
        return MaterialPityTracker.recordOutcome(before.progress.materialPity, policy, misses, brought)
    }
}
