package com.example.douqiuqiu.content

import com.example.douqiuqiu.inventory.CraftingRecipe
import com.example.douqiuqiu.inventory.EquipmentStats

/**
 * 0.1.5 新增流派装备：怒火（rage）与寒霜（frost）各两件成装 + 对应卷轴。
 *
 * 设计口径（与 `docs/tasks/2026-10-20-v0.1.5-nonvisual-requirements.md` 第五节一致）：
 * - 每件装备具备独立 ID、属性、效果、配方、尺寸、价格、掉落／商店门槛与图鉴描述；
 * - 配方材料全部指向已存在的组件／卷轴，产物唯一，材料消耗沿用 `CraftingRules` 的原子扣除；
 * - 装备不使用卡牌升星逻辑，效果参数按 1 星基准固定；
 * - 新效果遵守既有唯一组、主动 CD、状态抗性、驱散与护盾规则：
 *   - 寒霜主动冻结走 `APPLY_CONTROL` + `ControlKind.FREEZE`，不可被普通／强驱散清除；
 *   - 怒火攻速叠层走 `ATTACK_SPEED_STACK`，每层独立续期，受 `stackDurationMs` 限制。
 *
 * 数值为本游戏首轮配置值，不声称等于原版数值。
 */
object SchoolEquipmentContent {

    private fun scroll(id: String, name: String, price: Int) =
        EquipmentDefinition(
            id = "recipe_$id", name = "${name}卷轴", category = EquipmentCategory.SCROLL,
            quality = QualityTier.BLUE, width = 1, height = 1, price = price, minimumShopClears = 2
        )

    val scrolls: List<EquipmentDefinition> = listOf(
        scroll("bloodrage_gauntlet", "血怒拳套", 650),
        scroll("frenzy_heart", "狂战之心", 800),
        scroll("frost_shard_blade", "霜棱之刃", 650),
        scroll("glacier_plate", "冰川护甲", 850)
    )

    val mains: List<EquipmentDefinition> = listOf(
        // ---------------- 怒火 ----------------
        EquipmentDefinition(
            id = "bloodrage_gauntlet", name = "血怒拳套",
            category = EquipmentCategory.MAIN, quality = QualityTier.PURPLE,
            width = 1, height = 2, price = 2800,
            stats = EquipmentStats(attack = 18.0, attackSpeed = 18.0),
            effects = listOf(
                EffectDefinition(
                    id = "bloodrage.lifesteal", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
                    params = mapOf("percent" to EffectValue.n(0.12)),
                    uniqueGroup = "bloodrage_lifesteal"
                ),
                // 每次命中叠一层攻速，4 秒独立续期，内置 CD 3 秒防止多段攻击瞬间叠满
                EffectDefinition(
                    id = "bloodrage.fury", kind = EffectKind.ATTACK_SPEED_STACK, trigger = EffectTrigger.ATTACK_LAND,
                    params = mapOf("amount" to EffectValue.n(12)),
                    stackDurationMs = 4000L, internalCooldownMs = 3000L,
                    uniqueGroup = "bloodrage_fury"
                )
            ),
            recipe = CraftingRecipe("bloodrage_gauntlet", "bloodrage_gauntlet",
                mapOf("blades_of_attack" to 1, "gloves_of_haste" to 1, "recipe_bloodrage_gauntlet" to 1)),
            minimumShopClears = 2,
            description = "攻击与攻速；命中叠加攻速（4 秒独立续期，内置 CD 3 秒），并提供攻击吸血"
        ),
        EquipmentDefinition(
            id = "frenzy_heart", name = "狂战之心",
            category = EquipmentCategory.MAIN, quality = QualityTier.PURPLE,
            width = 2, height = 2, price = 3600,
            stats = EquipmentStats(strength = 20.0, health = 300.0),
            effects = listOf(
                EffectDefinition(
                    id = "frenzy.regen", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                    params = mapOf("healthRegen" to EffectValue.n(6.0)),
                    uniqueGroup = "frenzy_heart"
                ),
                // 受创越重吸血越高：阈值内额外吸血由运行时按 thresholdPercent 解释
                EffectDefinition(
                    id = "frenzy.bloodthirst", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
                    params = mapOf("percent" to EffectValue.n(0.15), "thresholdPercent" to EffectValue.n(0.5)),
                    uniqueGroup = "frenzy_heart"
                )
            ),
            recipe = CraftingRecipe("frenzy_heart", "frenzy_heart",
                mapOf("ogre_axe" to 1, "vitality_booster" to 1, "recipe_frenzy_heart" to 1)),
            minimumShopClears = 2,
            description = "力量与生命；持续回血，半血以下吸血提升（同一唯一组择强）"
        ),

        // ---------------- 寒霜 ----------------
        EquipmentDefinition(
            id = "frost_shard_blade", name = "霜棱之刃",
            category = EquipmentCategory.MAIN, quality = QualityTier.PURPLE,
            width = 1, height = 2, price = 2700,
            stats = EquipmentStats(attack = 20.0, intelligence = 10.0),
            effects = listOf(
                EffectDefinition(
                    id = "frostshard.burst", kind = EffectKind.ADD_SPELL_DAMAGE, trigger = EffectTrigger.ATTACK_LAND,
                    params = mapOf("amount" to EffectValue.n(45), "chance" to EffectValue.n(0.30), "maxTargets" to EffectValue.c(1)),
                    damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                    flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false),
                    internalCooldownMs = 1500L, uniqueGroup = "frost_shard"
                ),
                EffectDefinition(
                    id = "frostshard.slow", kind = EffectKind.SLOW, trigger = EffectTrigger.ATTACK_LAND,
                    params = mapOf("slowPercent" to EffectValue.n(20.0), "durationMs" to EffectValue.c(1500), "chance" to EffectValue.n(0.30)),
                    dispelClass = DispelClass.BASIC, internalCooldownMs = 1500L, uniqueGroup = "frost_shard"
                )
            ),
            recipe = CraftingRecipe("frost_shard_blade", "frost_shard_blade",
                mapOf("claymore" to 1, "robe_of_magi" to 1, "recipe_frost_shard_blade" to 1)),
            minimumShopClears = 2,
            description = "攻击与智力；命中概率追加法术伤害并减速，减速可被普通驱散"
        ),
        EquipmentDefinition(
            id = "glacier_plate", name = "冰川护甲",
            category = EquipmentCategory.MAIN, quality = QualityTier.PURPLE,
            width = 2, height = 2, price = 3400,
            stats = EquipmentStats(armor = 8.0, health = 250.0),
            effects = listOf(
                // 主动冻结：独立硬控，不可被普通／强驱散清除，但受状态抗性与减益免疫影响
                EffectDefinition(
                    id = "glacier.freeze", kind = EffectKind.APPLY_CONTROL, trigger = EffectTrigger.ACTIVE,
                    params = mapOf(
                        "control" to EffectValue.e("FREEZE"),
                        "durationMs" to EffectValue.c(900),
                        "rangedDurationMs" to EffectValue.c(1200),
                        "chance" to EffectValue.n(1.0),
                        "rangedChance" to EffectValue.n(1.0)
                    ),
                    dispelClass = DispelClass.NOT_DISPELLABLE, uniqueGroup = "glacier_plate",
                    internalCooldownMs = 12000L
                ),
                EffectDefinition(
                    id = "glacier.bulk", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                    params = mapOf("armor" to EffectValue.n(4.0)),
                    uniqueGroup = "glacier_plate"
                )
            ),
            recipe = CraftingRecipe("glacier_plate", "glacier_plate",
                mapOf("chainmail" to 1, "vitality_booster" to 1, "recipe_glacier_plate" to 1)),
            minimumShopClears = 2,
            description = "护甲与生命；主动冻结目标 0.9 秒（远程 1.2 秒），CD 12 秒；冻结不可驱散、不穿减益免疫"
        )
    )
}
