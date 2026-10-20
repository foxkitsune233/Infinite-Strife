package com.example.douqiuqiu.content

import com.example.douqiuqiu.inventory.CraftingRecipe
import com.example.douqiuqiu.inventory.EquipmentStats

/** Game-specific first-pass recipes and values, not a claim about Valve's current recipes. */
object ControlEquipmentContent {
    private fun scroll(id: String, name: String, price: Int) = EquipmentDefinition("recipe_$id", "${name}卷轴",
        EquipmentCategory.SCROLL, QualityTier.BLUE, 1, 1, price, minimumShopClears = 2)
    private fun effect(id: String, kind: String, duration: Int, group: String, active: Boolean,
                       dispel: DispelClass, rangedDuration: Int = duration) = EffectDefinition(
        id, EffectKind.APPLY_CONTROL, if (active) EffectTrigger.ACTIVE else EffectTrigger.ATTACK_LAND,
        params = mapOf("control" to EffectValue.e(kind), "durationMs" to EffectValue.c(duration),
            "rangedDurationMs" to EffectValue.c(rangedDuration), "chance" to EffectValue.n(if (active) 1.0 else .25),
            "rangedChance" to EffectValue.n(if (active) 1.0 else .10)),
        dispelClass = dispel, uniqueGroup = group, internalCooldownMs = if (active) 12000 else 3000)
    val mains = listOf(
        EquipmentDefinition("skull_basher", "碎颅锤", EquipmentCategory.MAIN, QualityTier.PURPLE, 1, 2, 2400,
            stats = EquipmentStats(attack = 20.0, strength = 8.0),
            effects = listOf(effect("basher.stun", "STUN", 800, "basher", false, DispelClass.STRONG_ONLY)),
            recipe = CraftingRecipe("skull_basher", "skull_basher", mapOf("broadsword" to 1, "belt_of_strength" to 1, "recipe_skull_basher" to 1)),
            minimumShopClears = 2, description = "普攻近战25%/远程10%眩晕0.8秒，内置CD3秒；无敌斩不触发，同组唯一"),
        EquipmentDefinition("orchid", "紫怨", EquipmentCategory.MAIN, QualityTier.PURPLE, 1, 2, 2800,
            stats = EquipmentStats(intelligence = 15.0, attackSpeed = 20.0, manaRegen = 1.0),
            effects = listOf(effect("orchid.silence", "SILENCE", 3000, "orchid", true, DispelClass.BASIC)),
            recipe = CraftingRecipe("orchid", "orchid", mapOf("oblivion_staff" to 1, "robe_of_magi" to 1, "recipe_orchid" to 1)),
            minimumShopClears = 2, description = "主动沉默3秒，CD12秒；可弱驱散，自动时不重复覆盖当前沉默"),
        EquipmentDefinition("heavens_halberd", "天堂之戟", EquipmentCategory.MAIN, QualityTier.PURPLE, 2, 2, 3700,
            stats = EquipmentStats(strength = 16.0),
            effects = listOf(effect("halberd.disarm", "DISARM", 2000, "halberd", true, DispelClass.NOT_DISPELLABLE, 3000),
                EffectDefinition("halberd.evasion", EffectKind.EVASION, EffectTrigger.PASSIVE, mapOf("percent" to EffectValue.n(.15)), uniqueGroup = "halberd_evasion")),
            recipe = CraftingRecipe("heavens_halberd", "heavens_halberd", mapOf("sange" to 1, "talisman_of_evasion" to 1, "recipe_heavens_halberd" to 1)),
            minimumShopClears = 2, description = "主动缴械近战2秒/远程3秒，CD12秒；不可驱散，不穿减益免疫")
    )
    val scrolls = listOf(scroll("skull_basher", "碎颅锤", 1770), scroll("orchid", "紫怨", 750), scroll("heavens_halberd", "天堂之戟", 200))
}
