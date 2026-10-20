package com.example.douqiuqiu.content

import com.example.douqiuqiu.inventory.CraftingRecipe
import com.example.douqiuqiu.inventory.EquipmentStats

/**
 * 装备、组件与配方的内容定义（H01 非毒部分）。
 *
 * 数据来源：`docs/design/equipment.md`（v1 范围表）。
 *
 * **重要声明（不得省略）：**
 * - 29个配方已按2026-10-17获取的Valve itemlist组件数据核验，卷轴存在性辅以固定版本脚本。
 *   保留旧内部ID的显式兼容别名，详见 `docs/design/equipment-recipes-verified.md`。
 * - 所有属性与效果参数是**本游戏首轮配置值**，不声称等于原版数值。
 * - 附毒类效果（巫师之刃/圣斧/怪蛇）首轮参数已于2026-10-17获用户明确采用。
 * - 已存在的 bracer / wraith_band / null_talisman 及其组件**不在本文件重复建立别名**，
 *   由集成人合入 `inventory.json` 时对齐现有 ID。
 */

/** 装备类别。 */
enum class EquipmentCategory {
    /** 基础组件（含卷轴之外的原材料） */
    COMPONENT,

    /** 卷轴（合成消耗品） */
    SCROLL,

    /** 中间装备 */
    INTERMEDIATE,

    /** 主要成装 */
    MAIN
}

/**
 * 一件装备/组件的定义。
 *
 * @property stats 基础属性，复用 shared 的 [EquipmentStats]（只含原始加成，不含属性派生）
 * @property effects 特殊效果；唯一组择强由 [uniqueGroup] 声明，择强规则交 `UniqueBonusSelector`
 * @property uniqueGroup 唯一组：同组内特殊效果择强，基础属性仍相加
 * @property recipe 合成配方；组件/卷轴为 null
 * @property requiresReview true 表示效果数值待审（目前只有附毒类）
 */
data class EquipmentDefinition(
    val id: String,
    val name: String,
    val category: EquipmentCategory,
    val quality: QualityTier,
    val width: Int,
    val height: Int,
    val price: Int,
    val stats: EquipmentStats? = null,
    val effects: List<EffectDefinition> = emptyList(),
    val uniqueGroup: String? = null,
    val recipe: CraftingRecipe? = null,
    val shopShelf: String = "ROTATING",
    val shopStock: Int = 1,
    val minimumShopClears: Int = 0,
    val requiresReview: Boolean = false,
    val description: String = ""
) {
    init {
        require(id.isNotBlank()) { "装备 ID 不能为空白" }
        require(width in 1..6 && height in 1..4) { "装备尺寸超出上限: $id -> ${width}x$height" }
        require(price > 0) { "价格必须为正: $id -> $price" }
        val effectIds = effects.map { it.id }
        require(effectIds.toSet().size == effectIds.size) { "装备内效果 ID 重复: $id" }
        if (category == EquipmentCategory.COMPONENT || category == EquipmentCategory.SCROLL) {
            require(recipe == null) { "组件/卷轴不应带配方: $id" }
        } else {
            require(recipe != null) { "中间/主要装备必须带配方: $id" }
        }
    }

    /** 是否为主要成装或中间装备（可合成产物）。 */
    val craftable: Boolean get() = recipe != null
}

object EquipmentCatalog {

    // ================================================================
    // 一、基础组件
    // 说明：下列属性为本游戏首轮配置值，未与原版逐项核验；ID 沿用稳定英文命名。
    // 已存在于 inventory.json 的 iron_branch / circlet / gauntlets / slippers /
    // mantle / broadsword 不在本表重复定义。
    // ================================================================

    private fun component(
        id: String, name: String, price: Int, stats: EquipmentStats? = null,
        w: Int = 1, h: Int = 1, quality: QualityTier = QualityTier.WHITE,
        desc: String = ""
    ) = EquipmentDefinition(
        id = id, name = name, category = EquipmentCategory.COMPONENT, quality = quality,
        width = w, height = h, price = price, stats = stats,
        shopShelf = "STAPLE", shopStock = 3, description = desc
    )

    // ---- 已指定十件中缺失的五件 ----
    private val crown = component("crown", "王冠", 450, EquipmentStats(strength = 4.0, agility = 4.0, intelligence = 4.0), desc = "全属性组件（首轮配置值）")
    private val diadem = component("diadem", "冠冕", 1000, EquipmentStats(strength = 6.0, agility = 6.0, intelligence = 6.0), h = 2, quality = QualityTier.BLUE, desc = "高阶全属性组件（首轮配置值）")
    private val beltOfStrength = component("belt_of_strength", "力量腰带", 450, EquipmentStats(strength = 6.0), desc = "力量组件（首轮配置值）")
    private val bandOfElvenskin = component("band_of_elvenskin", "精灵布带", 450, EquipmentStats(agility = 6.0), desc = "敏捷组件（首轮配置值）")
    private val robeOfMagi = component("robe_of_magi", "法师长袍", 450, EquipmentStats(intelligence = 6.0), desc = "智力组件（首轮配置值）")

    // ---- 属性合成组件 ----
    private val ogreAxe = component("ogre_axe", "食人魔之斧", 1000, EquipmentStats(strength = 10.0), h = 2, quality = QualityTier.BLUE)
    private val bladeOfAlacrity = component("blade_of_alacrity", "欢欣之刃", 1000, EquipmentStats(agility = 10.0), h = 2, quality = QualityTier.BLUE)
    private val staffOfWizardry = component("staff_of_wizardry", "魔力法杖", 1000, EquipmentStats(intelligence = 10.0), h = 2, quality = QualityTier.BLUE)
    private val reaver = component("reaver", "掠夺者之斧", 3000, EquipmentStats(strength = 25.0, health = 250.0), w = 2, h = 2, quality = QualityTier.PURPLE)
    private val eaglesong = component("eaglesong", "鹰歌弓", 3200, EquipmentStats(agility = 25.0), w = 2, h = 2, quality = QualityTier.PURPLE)
    private val mysticStaff = component("mystic_staff", "神秘法杖", 3200, EquipmentStats(intelligence = 25.0), w = 2, h = 2, quality = QualityTier.PURPLE)

    // ---- 输出组件 ----
    private val bladesOfAttack = component("blades_of_attack", "攻击之爪", 450, EquipmentStats(attack = 9.0))
    private val claymore = component("claymore", "大剑", 1400, EquipmentStats(attack = 21.0), h = 2, quality = QualityTier.BLUE)
    private val mithrilHammer = component("mithril_hammer", "秘银锤", 1600, EquipmentStats(attack = 24.0), h = 2, quality = QualityTier.BLUE)
    private val demonEdge = component("demon_edge", "恶魔刀锋", 2400, EquipmentStats(attack = 40.0), w = 2, h = 2, quality = QualityTier.PURPLE)
    private val javelin = component("javelin", "标枪", 1100, EquipmentStats(attack = 14.0), h = 2, quality = QualityTier.BLUE)
    private val glovesOfHaste = component("gloves_of_haste", "加速手套", 500, EquipmentStats(attackSpeed = 15.0))
    private val quarterstaff = component("quarterstaff", "闪电指套", 900, EquipmentStats(attack = 10.0, attackSpeed = 10.0), h = 2, quality = QualityTier.BLUE)
    private val hyperstone = component("hyperstone", "振奋宝石", 2000, EquipmentStats(attackSpeed = 55.0), w = 2, h = 2, quality = QualityTier.PURPLE)
    private val orbOfVenom = component("orb_of_venom", "淬毒之珠", 350, EquipmentStats(attack = 3.0), desc = "附毒组件（附毒组件首轮值）")

    // ---- 防守 / 恢复组件 ----
    private val chainmail = component("chainmail", "锁子甲", 550, EquipmentStats(armor = 5.0))
    private val helmOfIronWill = component("helm_of_iron_will", "铁意头盔", 950, EquipmentStats(armor = 5.0, healthRegen = 3.0), h = 2, quality = QualityTier.BLUE)
    private val cloak = component("cloak", "抗魔斗篷", 550, null, desc = "提供独立魔抗（经 effect 表达）")
    private val shawl = component("shawl", "披巾", 800, null, desc = "魔抗/恢复向组件（首轮配置值）")
    private val fluffyHat = component("fluffy_hat", "毛毛帽", 250, EquipmentStats(health = 100.0))
    private val talismanOfEvasion = component("talisman_of_evasion", "闪避护符", 1400, null, h = 2, quality = QualityTier.BLUE, desc = "提供闪避（经 effect 表达）")
    private val ringOfHealth = component("ring_of_health", "治疗指环", 850, EquipmentStats(healthRegen = 5.0))
    private val ringOfRegen = component("ring_of_regen", "回复戒指", 350, EquipmentStats(healthRegen = 2.0))
    private val vitalityBooster = component("vitality_booster", "活力之球", 1100, EquipmentStats(health = 250.0), h = 2, quality = QualityTier.BLUE)
    private val energyBooster = component("energy_booster", "能量之球", 900, EquipmentStats(manaPercent = 0.0, manaRegen = 0.0), h = 2, quality = QualityTier.BLUE, desc = "魔法上限组件（manaFlat 经 effect 表达）")
    private val pointBooster = component("point_booster", "精气之球", 1200, EquipmentStats(strength = 0.0), h = 2, quality = QualityTier.BLUE, desc = "生命与魔法上限（经 effect 表达）")
    private val voodooMask = component("voodoo_mask", "恐鳌之戒", 900, EquipmentStats(healthRegen = 2.0), desc = "恢复向组件")
    private val morbidMask = component("morbid_mask", "吸血面具", 900, null, desc = "提供攻击吸血（经 effect 表达）")
    private val sageMask = component("sage_mask", "贤者面罩", 700, EquipmentStats(manaRegen = 1.0))
    private val voidStone = component("void_stone", "虚无宝石", 850, EquipmentStats(manaRegen = 2.0))
    private val perseverance = component("perseverance", "赛莉蒙妮之冠", 1700, EquipmentStats(healthRegen = 5.0, manaRegen = 2.0), w = 2, h = 2, quality = QualityTier.PURPLE)

    // ---- 提供「非纯属性」能力的组件效果（与定义分开登记，供集成人合并） ----
    private val cloakEffect = EffectDefinition(
        id = "cloak.magicResist", kind = EffectKind.MAGIC_RESIST, trigger = EffectTrigger.PASSIVE,
        params = mapOf("percent" to EffectValue.n(0.15))
    )
    private val talismanEffect = EffectDefinition(
        id = "talisman.evasion", kind = EffectKind.EVASION, trigger = EffectTrigger.PASSIVE,
        params = mapOf("percent" to EffectValue.n(0.15))
    )
    private val morbidEffect = EffectDefinition(
        id = "morbid.lifesteal", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
        params = mapOf("percent" to EffectValue.n(0.10))
    )
    private val pointBoosterEffect = EffectDefinition(
        id = "point.boost", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
        params = mapOf("health" to EffectValue.n(200), "manaFlat" to EffectValue.n(150))
    )
    private val energyBoosterEffect = EffectDefinition(
        id = "energy.boost", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
        params = mapOf("manaFlat" to EffectValue.n(250))
    )

    // ================================================================
    // 二、卷轴（每件成品一件；品质跟随成品档次）
    // ================================================================

    private fun scroll(id: String, name: String, price: Int, quality: QualityTier) =
        EquipmentDefinition(
            id = id, name = name, category = EquipmentCategory.SCROLL, quality = quality,
            width = 1, height = 1, price = price, shopShelf = "STAPLE", shopStock = 3
        )

    val scrolls: List<EquipmentDefinition> = listOf(
        // 卷轴范围严格按 equipment.md 草案：文档配方里写了「+卷轴」的才有卷轴，
        // 未写卷轴的（先锋盾/空明杖/振魂石/疯狂面具/漩涡/撒旦/蝴蝶/三把对剑）不加。
        scroll("recipe_dragon_lance", "魔龙枪卷轴", 400, QualityTier.WHITE),
        scroll("recipe_expert_array", "专家阵列卷轴", 500, QualityTier.WHITE),
        // 主要装备卷轴
        scroll("recipe_mjollnir", "雷神之锤卷轴", 900, QualityTier.BLUE),
        scroll("recipe_kaya", "慧光卷轴", 600, QualityTier.WHITE),
        scroll("recipe_sange", "散华卷轴", 600, QualityTier.WHITE),
        scroll("recipe_yasha", "夜叉卷轴", 600, QualityTier.WHITE),
        scroll("recipe_octarine_core", "玲珑心卷轴", 1200, QualityTier.BLUE),
        scroll("recipe_crystalys", "水晶剑卷轴", 500, QualityTier.WHITE),
        scroll("recipe_daedalus", "代达罗斯之殇卷轴", 1000, QualityTier.BLUE),
        scroll("recipe_crimson_guard", "赤红甲卷轴", 800, QualityTier.BLUE),
        scroll("recipe_pipe_of_insight", "洞察烟斗卷轴", 900, QualityTier.BLUE),
        scroll("recipe_armlet", "莫尔迪基安的臂章卷轴", 700, QualityTier.WHITE),
        scroll("recipe_heart", "恐鳌之心卷轴", 1200, QualityTier.BLUE),
        scroll("recipe_witch_blade", "巫师之刃卷轴", 700, QualityTier.WHITE),
        scroll("recipe_rod_of_atos", "圣斧卷轴", 1100, QualityTier.BLUE),
        scroll("recipe_viper_bite", "怪蛇之息卷轴", 1100, QualityTier.BLUE),
        scroll("recipe_force_staff", "原力法杖卷轴", 600, QualityTier.WHITE),
        scroll("recipe_hurricane_pike", "飓风长戟卷轴", 1000, QualityTier.BLUE),
        scroll("recipe_black_king_bar", "黑皇杖卷轴", 1200, QualityTier.BLUE)
    ) + ControlEquipmentContent.scrolls + SchoolEquipmentContent.scrolls

    // ================================================================
    // 三、5 件中间装备
    // ================================================================

    private val vanguardShield = EquipmentDefinition(
        id = "vanguard_shield", name = "先锋盾", category = EquipmentCategory.INTERMEDIATE,
        quality = QualityTier.BLUE, width = 2, height = 2, price = 1800,
        stats = EquipmentStats(health = 250.0, healthRegen = 5.0),
        effects = listOf(
            EffectDefinition(
                id = "vanguard.block", kind = EffectKind.PHYSICAL_BLOCK, trigger = EffectTrigger.PASSIVE,
                params = mapOf("amount" to EffectValue.n(30), "chance" to EffectValue.n(1.0)),
                uniqueGroup = "physical_block"
            )
        ),
        recipe = CraftingRecipe("vanguard_shield", "vanguard_shield",
            mapOf("ring_of_health" to 1, "vitality_booster" to 1)),
        minimumShopClears = 1,
        description = "生命、恢复、普通攻击固定格挡；格挡不是护盾，同类格挡择强"
    )

    private val oblivionStaff = EquipmentDefinition(
        id = "oblivion_staff", name = "空明杖", category = EquipmentCategory.INTERMEDIATE,
        quality = QualityTier.BLUE, width = 1, height = 3, price = 1600,
        stats = EquipmentStats(intelligence = 10.0, attackSpeed = 15.0, manaRegen = 1.5),
        recipe = CraftingRecipe("oblivion_staff", "oblivion_staff",
            mapOf("quarterstaff" to 1, "sage_mask" to 1, "robe_of_magi" to 1)),
        minimumShopClears = 1,
        description = "智力、固定攻速、回蓝"
    )

    private val soulBooster = EquipmentDefinition(
        id = "soul_booster", name = "振魂石", category = EquipmentCategory.INTERMEDIATE,
        quality = QualityTier.BLUE, width = 2, height = 2, price = 3200,
        effects = listOf(
            EffectDefinition(
                id = "soul.boost", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("health" to EffectValue.n(450), "manaFlat" to EffectValue.n(400))
            )
        ),
        recipe = CraftingRecipe("soul_booster", "soul_booster",
            mapOf("vitality_booster" to 1, "energy_booster" to 1, "point_booster" to 1)),
        minimumShopClears = 1,
        description = "生命和魔法上限"
    )

    private val dragonLance = EquipmentDefinition(
        id = "dragon_lance", name = "魔龙枪", category = EquipmentCategory.INTERMEDIATE,
        quality = QualityTier.BLUE, width = 1, height = 3, price = 1900,
        stats = EquipmentStats(agility = 12.0, strength = 6.0),
        effects = listOf(
            // 射程收益改为普攻伤害收益；数值为可调配置，未定案
            EffectDefinition(
                id = "lance.attackBonus", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attack" to EffectValue.n(10))
            )
        ),
        recipe = CraftingRecipe("dragon_lance", "dragon_lance",
            mapOf("blade_of_alacrity" to 1, "belt_of_strength" to 1, "recipe_dragon_lance" to 1)),
        minimumShopClears = 1,
        description = "敏捷/力量；射程效果在本游戏改为普攻伤害收益（数值可调）"
    )

    private val expertArray = EquipmentDefinition(
        id = "expert_array", name = "专家阵列", category = EquipmentCategory.INTERMEDIATE,
        quality = QualityTier.BLUE, width = 2, height = 2, price = 2000,
        stats = EquipmentStats(agility = 10.0, attack = 15.0),
        effects = listOf(
            EffectDefinition(
                id = "array.extraHit", kind = EffectKind.ADD_PHYSICAL_DAMAGE,
                trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(30), "chance" to EffectValue.n(0.30), "maxTargets" to EffectValue.c(1)),
                damageType = DamageType.PHYSICAL, attackClass = AttackClass.PHYSICAL_SKILL,
                internalCooldownMs = 0L,
                flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false),
                uniqueGroup = "expert_array"
            )
        ),
        recipe = CraftingRecipe("expert_array", "expert_array",
            mapOf("blade_of_alacrity" to 1, "broadsword" to 1, "recipe_expert_array" to 1)),
        minimumShopClears = 1,
        description = "敏捷/攻击；单挑概率追加物理伤害，不暴击/不吸血/不触发命中效果"
    )

    // ================================================================
    // 四、24 件主要装备
    // ================================================================

    private val maskOfMadness = EquipmentDefinition(
        id = "mask_of_madness", name = "疯狂面具", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 2, height = 2, price = 2900,
        stats = EquipmentStats(attack = 10.0),
        effects = listOf(
            EffectDefinition(
                id = "mom.lifesteal", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.15))
            ),
            // 自动狂热：+60 攻速 4 秒 / CD 8 秒 / 自沉默 / -3 护甲
            EffectDefinition(
                id = "mom.berserk", kind = EffectKind.ATTACK_SPEED_STACK, trigger = EffectTrigger.ACTIVE,
                params = mapOf("amount" to EffectValue.n(60)),
                stackDurationMs = 4000L, internalCooldownMs = 8000L,
                uniqueGroup = "mask_madness", dispelClass = DispelClass.BASIC
            ),
            EffectDefinition(
                id = "mom.selfSilence", kind = EffectKind.SELF_SILENCE, trigger = EffectTrigger.ACTIVE,
                params = mapOf("durationMs" to EffectValue.c(4000)),
                uniqueGroup = "mask_madness"
            ),
            EffectDefinition(
                id = "mom.armorDown", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.ACTIVE,
                params = mapOf("armor" to EffectValue.n(-3.0)),
                uniqueGroup = "mask_madness"
            )
        ),
        recipe = CraftingRecipe("mask_of_madness", "mask_of_madness",
            mapOf("morbid_mask" to 1, "broadsword" to 1)),
        minimumShopClears = 1,
        description = "吸血、攻击；自动狂热加攻速并自沉默，不允许手动切模式绕过自沉默"
    )

    private val maelstrom = EquipmentDefinition(
        id = "maelstrom", name = "漩涡", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 2, height = 2, price = 2900,
        stats = EquipmentStats(attack = 24.0, attackSpeed = 15.0),
        effects = listOf(
            EffectDefinition(
                id = "maelstrom.chain", kind = EffectKind.ADD_SPELL_DAMAGE, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(70), "chance" to EffectValue.n(0.25), "maxTargets" to EffectValue.c(1), "bounce" to EffectValue.f(false)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false),
                uniqueGroup = "maelstrom"
            )
        ),
        recipe = CraftingRecipe("maelstrom", "maelstrom",
            mapOf("mithril_hammer" to 1, "javelin" to 1, "gloves_of_haste" to 1)),
        minimumShopClears = 1,
        description = "概率附加魔法闪电；单挑命中当前目标，独立魔伤不算攻击吸血"
    )

    private val mjollnir = EquipmentDefinition(
        id = "mjollnir", name = "雷神之锤", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 5600,
        stats = EquipmentStats(attack = 24.0, attackSpeed = 70.0),
        effects = listOf(
            EffectDefinition(
                id = "mjollnir.chain", kind = EffectKind.ADD_SPELL_DAMAGE, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(120), "chance" to EffectValue.n(0.25), "maxTargets" to EffectValue.c(1), "bounce" to EffectValue.f(true)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false),
                uniqueGroup = "maelstrom"
            ),
            // 静电充能：受击反击，最多 3 目标、0.5 秒内部 CD（首版单挑实际只命中当前敌人）
            EffectDefinition(
                id = "mjollnir.static", kind = EffectKind.ADD_SPELL_DAMAGE, trigger = EffectTrigger.DAMAGE_TAKEN,
                params = mapOf("amount" to EffectValue.n(60), "chance" to EffectValue.n(0.20), "maxTargets" to EffectValue.c(3)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                internalCooldownMs = 500L,
                flags = EffectFlags(canCrit = false, canLifesteal = false, isReflect = true),
                uniqueGroup = "mjollnir_static"
            )
        ),
        recipe = CraftingRecipe("mjollnir", "mjollnir",
            mapOf("maelstrom" to 1, "hyperstone" to 1, "recipe_mjollnir" to 1)),
        minimumShopClears = 2,
        description = "强化闪电并预留多单位弹跳；静电充能受击电击支持有限数量反击目标"
    )

    private val kaya = EquipmentDefinition(
        id = "kaya", name = "慧光", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 1, height = 3, price = 2100,
        stats = EquipmentStats(intelligence = 16.0, manaRegen = 2.0),
        effects = listOf(
            EffectDefinition(
                id = "kaya.spellAmp", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.10)),
                uniqueGroup = "kaya_family"
            ),
            EffectDefinition(
                id = "kaya.manaAmp", kind = EffectKind.MANA_REGEN, trigger = EffectTrigger.PASSIVE,
                params = mapOf("amount" to EffectValue.n(1.0)),
                uniqueGroup = "kaya_family"
            )
        ),
        recipe = CraftingRecipe("kaya", "kaya",
            mapOf("staff_of_wizardry" to 1, "robe_of_magi" to 1, "recipe_kaya" to 1)),
        minimumShopClears = 1,
        description = "智力、技能增伤、回蓝增强"
    )

    private val sange = EquipmentDefinition(
        id = "sange", name = "散华", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 1, height = 3, price = 2100,
        stats = EquipmentStats(strength = 16.0, health = 100.0),
        effects = listOf(
            EffectDefinition(
                id = "sange.healAmp", kind = EffectKind.HEAL_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.20)),
                uniqueGroup = "sange_family"
            ),
            EffectDefinition(
                id = "sange.resist", kind = EffectKind.STATUS_RESIST, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.16)),
                uniqueGroup = "sange_family"
            )
        ),
        recipe = CraftingRecipe("sange", "sange",
            mapOf("ogre_axe" to 1, "belt_of_strength" to 1, "recipe_sange" to 1)),
        minimumShopClears = 1,
        description = "力量、恢复增幅、状态抗性"
    )

    private val yasha = EquipmentDefinition(
        id = "yasha", name = "夜叉", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 1, height = 3, price = 2100,
        stats = EquipmentStats(agility = 16.0, attackSpeed = 15.0),
        effects = listOf(
            // 移速收益改为额外攻速
            EffectDefinition(
                id = "yasha.speed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(8.0)),
                uniqueGroup = "yasha_family"
            )
        ),
        recipe = CraftingRecipe("yasha", "yasha",
            mapOf("blade_of_alacrity" to 1, "band_of_elvenskin" to 1, "recipe_yasha" to 1)),
        minimumShopClears = 1,
        description = "敏捷、攻速；移速收益改为额外攻速"
    )

    private val sangeAndYasha = EquipmentDefinition(
        id = "sange_and_yasha", name = "散夜对剑", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4100,
        stats = EquipmentStats(strength = 16.0, agility = 16.0, health = 100.0, attackSpeed = 15.0),
        effects = listOf(
            EffectDefinition(
                id = "snY.healAmp", kind = EffectKind.HEAL_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.24)), uniqueGroup = "sange_family"
            ),
            EffectDefinition(
                id = "snY.resist", kind = EffectKind.STATUS_RESIST, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.20)), uniqueGroup = "sange_family"
            ),
            EffectDefinition(
                id = "snY.speed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(12.0)), uniqueGroup = "yasha_family"
            )
        ),
        recipe = CraftingRecipe("sange_and_yasha", "sange_and_yasha", mapOf("sange" to 1, "yasha" to 1)),
        minimumShopClears = 2,
        description = "继承两刀属性，强化生存/普攻；家族特殊效果择强，基础属性相加"
    )

    private val kayaAndSange = EquipmentDefinition(
        id = "kaya_and_sange", name = "散慧对剑", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4100,
        stats = EquipmentStats(strength = 16.0, intelligence = 16.0, health = 100.0, manaRegen = 2.0),
        effects = listOf(
            EffectDefinition(
                id = "knS.spellAmp", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.14)), uniqueGroup = "kaya_family"
            ),
            EffectDefinition(
                id = "knS.healAmp", kind = EffectKind.HEAL_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.22)), uniqueGroup = "sange_family"
            ),
            EffectDefinition(
                id = "knS.resist", kind = EffectKind.STATUS_RESIST, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.18)), uniqueGroup = "sange_family"
            )
        ),
        recipe = CraftingRecipe("kaya_and_sange", "kaya_and_sange", mapOf("sange" to 1, "kaya" to 1)),
        minimumShopClears = 2,
        description = "继承两刀属性，强化法术/恢复"
    )

    private val yashaAndKaya = EquipmentDefinition(
        id = "yasha_and_kaya", name = "慧夜对剑", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4100,
        stats = EquipmentStats(agility = 16.0, intelligence = 16.0, attackSpeed = 15.0, manaRegen = 2.0),
        effects = listOf(
            EffectDefinition(
                id = "ynK.spellAmp", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.14)), uniqueGroup = "kaya_family"
            ),
            EffectDefinition(
                id = "ynK.speed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(12.0)), uniqueGroup = "yasha_family"
            )
        ),
        recipe = CraftingRecipe("yasha_and_kaya", "yasha_and_kaya", mapOf("kaya" to 1, "yasha" to 1)),
        minimumShopClears = 2,
        description = "继承两刀属性，强化普攻/施法混合"
    )

    private val octarineCore = EquipmentDefinition(
        id = "octarine_core", name = "玲珑心", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 5900,
        effects = listOf(
            EffectDefinition(
                id = "octarine.health", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("health" to EffectValue.n(250), "manaFlat" to EffectValue.n(250))
            ),
            EffectDefinition(
                id = "octarine.cdr", kind = EffectKind.COOLDOWN_REDUCTION, trigger = EffectTrigger.PASSIVE,
                params = mapOf("flatSeconds" to EffectValue.n(0.0), "percent" to EffectValue.n(0.25)),
                uniqueGroup = "cooldown_reduction"
            ),
            EffectDefinition(
                id = "octarine.regen", kind = EffectKind.MANA_REGEN, trigger = EffectTrigger.PASSIVE,
                params = mapOf("amount" to EffectValue.n(2.0))
            )
        ),
        recipe = CraftingRecipe("octarine_core", "octarine_core",
            mapOf("soul_booster" to 1, "perseverance" to 1, "recipe_octarine_core" to 1)),
        minimumShopClears = 2,
        description = "生命、魔法、回蓝、冷却缩减；影响卡/大招/装备。同名只取一个"
    )

    private val crystalys = EquipmentDefinition(
        id = "crystalys", name = "水晶剑", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 2, height = 2, price = 2200,
        stats = EquipmentStats(attack = 30.0),
        effects = listOf(
            // 独立暴击来源；不按唯一组排除大炮（多源按倍率优先）
            EffectDefinition(
                id = "crystalys.crit", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.20), "multiplier" to EffectValue.n(1.60))
            )
        ),
        recipe = CraftingRecipe("crystalys", "crystalys",
            mapOf("claymore" to 1, "blades_of_attack" to 1, "recipe_crystalys" to 1)),
        minimumShopClears = 1,
        description = "攻击、独立概率暴击；不按唯一组排除大炮"
    )

    private val daedalus = EquipmentDefinition(
        id = "daedalus", name = "代达罗斯之殇", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 5200,
        stats = EquipmentStats(attack = 60.0),
        effects = listOf(
            EffectDefinition(
                id = "daedalus.crit", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.25), "multiplier" to EffectValue.n(2.20))
            )
        ),
        recipe = CraftingRecipe("daedalus", "daedalus",
            mapOf("crystalys" to 1, "demon_edge" to 1, "recipe_daedalus" to 1)),
        minimumShopClears = 2,
        description = "高攻击、较高暴击倍率；多源暴击按既定顺序判定，不连乘"
    )

    private val crimsonGuard = EquipmentDefinition(
        id = "crimson_guard", name = "赤红甲", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 3700,
        stats = EquipmentStats(health = 250.0, armor = 8.0, healthRegen = 5.0),
        effects = listOf(
            EffectDefinition(
                id = "crimson.block", kind = EffectKind.PHYSICAL_BLOCK, trigger = EffectTrigger.PASSIVE,
                params = mapOf("amount" to EffectValue.n(40), "chance" to EffectValue.n(1.0)),
                uniqueGroup = "physical_block"
            ),
            // 主动物理盾 8 秒 / CD 10 秒，不是直接减伤或治疗
            EffectDefinition(
                id = "crimson.activeShield", kind = EffectKind.GRANT_SHIELD, trigger = EffectTrigger.ACTIVE,
                params = mapOf("amount" to EffectValue.n(200), "durationMs" to EffectValue.c(8000)),
                shieldKind = ShieldKind.PHYSICAL, internalCooldownMs = 10000L,
                uniqueGroup = "crimson_guard"
            )
        ),
        recipe = CraftingRecipe("crimson_guard", "crimson_guard",
            mapOf("vanguard_shield" to 1, "helm_of_iron_will" to 1, "recipe_crimson_guard" to 1)),
        minimumShopClears = 2,
        description = "生命/护甲/格挡；主动给予物理盾（不是直接减伤或治疗）"
    )

    private val pipeOfInsight = EquipmentDefinition(
        id = "pipe_of_insight", name = "洞察烟斗", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 3400,
        effects = listOf(
            EffectDefinition(
                id = "pipe.magicResist", kind = EffectKind.MAGIC_RESIST, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.20))
            ),
            EffectDefinition(
                id = "pipe.regen", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("healthRegen" to EffectValue.n(4.0))
            ),
            // 主动魔法盾 8 秒 / CD 10 秒
            EffectDefinition(
                id = "pipe.activeShield", kind = EffectKind.GRANT_SHIELD, trigger = EffectTrigger.ACTIVE,
                params = mapOf("amount" to EffectValue.n(300), "durationMs" to EffectValue.c(8000)),
                shieldKind = ShieldKind.MAGICAL, internalCooldownMs = 10000L,
                uniqueGroup = "pipe_of_insight"
            )
        ),
        recipe = CraftingRecipe("pipe_of_insight", "pipe_of_insight",
            mapOf("cloak" to 1, "voodoo_mask" to 1, "shawl" to 1, "recipe_pipe_of_insight" to 1)),
        minimumShopClears = 2,
        description = "魔抗/恢复；主动给予魔法盾"
    )

    private val armlet = EquipmentDefinition(
        id = "armlet_of_mordiggian", name = "莫尔迪基安的臂章", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 2, height = 2, price = 2500,
        stats = EquipmentStats(armor = 5.0, attackSpeed = 15.0, attack = 9.0),
        effects = listOf(
            // 自动开启：力量/攻击加成
            EffectDefinition(
                id = "armlet.power", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.ACTIVE,
                params = mapOf("strength" to EffectValue.n(25.0), "attack" to EffectValue.n(25.0)),
                uniqueGroup = "armlet"
            ),
            // 自损：不致死，单列来源
            EffectDefinition(
                id = "armlet.selfDamage", kind = EffectKind.ADD_PURE_DAMAGE, trigger = EffectTrigger.ACTIVE,
                params = mapOf("amount" to EffectValue.n(40)),
                damageType = DamageType.PURE, attackClass = AttackClass.SELF_DAMAGE,
                flags = EffectFlags(isSelfDamage = true, canCrit = false, canLifesteal = false),
                uniqueGroup = "armlet"
            )
        ),
        recipe = CraftingRecipe("armlet_of_mordiggian", "armlet_of_mordiggian",
            mapOf("helm_of_iron_will" to 1, "gloves_of_haste" to 1, "blades_of_attack" to 1, "recipe_armlet" to 1)),
        minimumShopClears = 1,
        description = "开战自动开，不反复切换；自损单列来源、不致死，不利用换装比例刷血"
    )

    private val heartOfTarrasque = EquipmentDefinition(
        id = "heart_of_tarrasque", name = "恐鳌之心", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 5200,
        stats = EquipmentStats(strength = 40.0, health = 500.0),
        effects = listOf(
            EffectDefinition(
                id = "heart.regen", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("healthRegen" to EffectValue.n(10.0))
            )
        ),
        recipe = CraftingRecipe("heart_of_tarrasque", "heart_of_tarrasque",
            mapOf("reaver" to 1, "voodoo_mask" to 1, "recipe_heart" to 1)),
        minimumShopClears = 2,
        description = "力量、生命、持续恢复；仅战斗时间恢复"
    )

    private val satanic = EquipmentDefinition(
        id = "satanic", name = "撒旦之邪力", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 5600,
        stats = EquipmentStats(strength = 25.0, attack = 40.0),
        effects = listOf(
            EffectDefinition(
                id = "satanic.lifesteal", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.25))
            ),
            // 低血开启：弱驱散 + 限时高吸血（12 秒 CD / 3 秒）
            EffectDefinition(
                id = "satanic.active", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.LOW_HP,
                params = mapOf("percent" to EffectValue.n(1.00), "thresholdPercent" to EffectValue.n(0.40)),
                internalCooldownMs = 12000L, uniqueGroup = "satanic"
            ),
            EffectDefinition(
                id = "satanic.dispel", kind = EffectKind.DISPEL, trigger = EffectTrigger.LOW_HP,
                params = mapOf("level" to EffectValue.e("BASIC")),
                internalCooldownMs = 12000L, uniqueGroup = "satanic"
            )
        ),
        recipe = CraftingRecipe("satanic", "satanic",
            mapOf("reaver" to 1, "claymore" to 1, "morbid_mask" to 1)),
        minimumShopClears = 2,
        description = "普通吸血；低血开启弱驱散及限时高吸血（可驱散的缴械不等于永远不能发动）"
    )

    private val butterfly = EquipmentDefinition(
        id = "butterfly", name = "蝴蝶", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 6200,
        stats = EquipmentStats(agility = 35.0, attack = 25.0),
        effects = listOf(
            // 基础攻速增幅是**乘区**，与固定攻速加法区分，不能混进 attackSpeed
            EffectDefinition(
                id = "butterfly.baseSpeedAmp", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeedPercent" to EffectValue.n(0.30)),
                uniqueGroup = "butterfly_speed"
            ),
            EffectDefinition(
                id = "butterfly.evasion", kind = EffectKind.EVASION, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.35))
            ),
            EffectDefinition(
                id = "butterfly.speed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(30.0))
            )
        ),
        recipe = CraftingRecipe("butterfly", "butterfly",
            mapOf("eaglesong" to 1, "talisman_of_evasion" to 1, "claymore" to 1)),
        minimumShopClears = 2,
        description = "敏捷、攻击、基础攻速增幅、闪避；闪避可多源乘算"
    )

    private val witchBlade = EquipmentDefinition(
        id = "witch_blade", name = "巫师之刃", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 2, height = 2, price = 2600,
        stats = EquipmentStats(intelligence = 12.0, attack = 20.0, armor = 4.0),
        effects = listOf(
            // 附毒参数为新毒审阅稿
            EffectDefinition(
                id = "witch.poison", kind = EffectKind.APPLY_NORMAL_POISON, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(6), "ticks" to EffectValue.c(3)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                internalCooldownMs = 4000L, dispelClass = DispelClass.BASIC,
                uniqueGroup = "poison_blade", requiresReview = false
            )
        ),
        recipe = CraftingRecipe("witch_blade", "witch_blade",
            mapOf("oblivion_staff" to 1, "chainmail" to 1, "orb_of_venom" to 1, "recipe_witch_blade" to 1)),
        minimumShopClears = 1, requiresReview = false,
        description = "攻击按内部 CD 附毒；与圣斧唯一，同组共享 CD（附毒首轮值已采用）"
    )

    private val rodOfAtos = EquipmentDefinition(
        id = "rod_of_atos", name = "圣斧", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4600,
        stats = EquipmentStats(intelligence = 25.0, attack = 25.0, armor = 6.0),
        effects = listOf(
            // 强化附毒（拟升级为剧毒）+ 降魔抗，与巫师之刃同组择强
            EffectDefinition(
                id = "atos.virulent", kind = EffectKind.APPLY_VIRULENT_POISON, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(8), "spawnTicks" to EffectValue.c(3)),
                internalCooldownMs = 4000L, dispelClass = DispelClass.STRONG_ONLY,
                uniqueGroup = "poison_blade", requiresReview = false
            ),
            EffectDefinition(
                id = "atos.magicResistDown", kind = EffectKind.MAGIC_RESIST, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(-0.15)),
                uniqueGroup = "poison_blade", requiresReview = false
            )
        ),
        recipe = CraftingRecipe("rod_of_atos", "rod_of_atos",
            mapOf("witch_blade" to 1, "mystic_staff" to 1, "recipe_rod_of_atos" to 1)),
        minimumShopClears = 2, requiresReview = false,
        description = "强化附毒并降魔抗，择强生效；毒按本游戏叠层（附毒首轮值已采用）"
    )

    private val viperBite = EquipmentDefinition(
        id = "viper_bite", name = "怪蛇之息", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4800,
        stats = EquipmentStats(agility = 20.0, attack = 30.0),
        effects = listOf(
            // 单挑：单体追加物理伤害，不暴击/不吸血/不触发命中效果
            EffectDefinition(
                id = "viper.extraHit", kind = EffectKind.ADD_PHYSICAL_DAMAGE, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(50), "chance" to EffectValue.n(0.35), "maxTargets" to EffectValue.c(1)),
                damageType = DamageType.PHYSICAL, attackClass = AttackClass.PHYSICAL_SKILL,
                flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false),
                uniqueGroup = "expert_array"
            ),
            EffectDefinition(
                id = "viper.poison", kind = EffectKind.APPLY_NORMAL_POISON, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(8), "ticks" to EffectValue.c(3)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                dispelClass = DispelClass.BASIC, requiresReview = false
            )
        ),
        recipe = CraftingRecipe("viper_bite", "viper_bite",
            mapOf("expert_array" to 1, "dragon_lance" to 1, "orb_of_venom" to 1, "recipe_viper_bite" to 1)),
        minimumShopClears = 2, requiresReview = false,
        description = "保留中毒；单挑采用单体追加物理伤害，追加段不暴击/吸血/触发命中效果（附毒首轮值已采用）"
    )

    private val forceStaff = EquipmentDefinition(
        id = "force_staff", name = "原力法杖", category = EquipmentCategory.MAIN,
        quality = QualityTier.PURPLE, width = 1, height = 3, price = 2200,
        stats = EquipmentStats(intelligence = 10.0, healthRegen = 2.0),
        effects = listOf(
            // 受控触发普攻保护：固定时长，不解控，必中不穿透
            EffectDefinition(
                id = "force.protection", kind = EffectKind.ATTACK_PROTECTION,
                trigger = EffectTrigger.ON_CONTROLLED,
                params = mapOf("durationMs" to EffectValue.c(2000)),
                internalCooldownMs = 12000L, uniqueGroup = "force_staff"
            )
        ),
        recipe = CraftingRecipe("force_staff", "force_staff",
            mapOf("staff_of_wizardry" to 1, "fluffy_hat" to 1, "recipe_force_staff" to 1)),
        minimumShopClears = 1,
        description = "受控时固定时长普攻保护，远程 CD 更短；不解控；推开特效不影响攻击距离判定"
    )

    private val hurricanePike = EquipmentDefinition(
        id = "hurricane_pike", name = "飓风长戟", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4400,
        stats = EquipmentStats(intelligence = 10.0, agility = 10.0, attack = 15.0, healthRegen = 2.0),
        effects = listOf(
            EffectDefinition(
                id = "pike.protection", kind = EffectKind.ATTACK_PROTECTION,
                trigger = EffectTrigger.ON_CONTROLLED,
                params = mapOf("durationMs" to EffectValue.c(2000)),
                internalCooldownMs = 9000L, uniqueGroup = "force_staff"
            ),
            EffectDefinition(
                id = "pike.speed", kind = EffectKind.ATTACK_SPEED_STACK,
                trigger = EffectTrigger.ON_CONTROLLED,
                params = mapOf("amount" to EffectValue.n(40)),
                stackDurationMs = 3000L, uniqueGroup = "force_staff"
            )
        ),
        recipe = CraftingRecipe("hurricane_pike", "hurricane_pike",
            mapOf("force_staff" to 1, "dragon_lance" to 1, "recipe_hurricane_pike" to 1)),
        minimumShopClears = 2,
        description = "同组唯一保护 + 短时攻速；取代原力法杖版本"
    )

    private val blackKingBar = EquipmentDefinition(
        id = "black_king_bar", name = "黑皇杖", category = EquipmentCategory.MAIN,
        quality = QualityTier.GOLD, width = 2, height = 3, price = 4000,
        stats = EquipmentStats(strength = 10.0, attack = 24.0),
        effects = listOf(
            // 弱驱散 + 减益免疫 + 魔抗；眩晕/妖术/禁用物品时不可使用
            EffectDefinition(
                id = "bkb.dispel", kind = EffectKind.DISPEL, trigger = EffectTrigger.ACTIVE,
                params = mapOf("level" to EffectValue.e("BASIC")),
                internalCooldownMs = 12000L, uniqueGroup = "bkb"
            ),
            EffectDefinition(
                id = "bkb.immunity", kind = EffectKind.IMMUNITY, trigger = EffectTrigger.ACTIVE,
                params = mapOf("durationMs" to EffectValue.c(3000), "magicResistPercent" to EffectValue.n(0.20)),
                internalCooldownMs = 12000L, uniqueGroup = "bkb"
            )
        ),
        recipe = CraftingRecipe("black_king_bar", "black_king_bar",
            mapOf("ogre_axe" to 1, "mithril_hammer" to 1, "recipe_black_king_bar" to 1)),
        minimumShopClears = 2,
        description = "力量、攻击；手动/自动，弱驱散+减益免疫+魔抗；眩晕/妖术/禁用物品时不可使用"
    )

    // ================================================================
    // 汇总
    // ================================================================

    /** 全部组件（不含已在 inventory.json 存在的六件）。 */
    val components: List<EquipmentDefinition> = listOf(
        crown, diadem, beltOfStrength, bandOfElvenskin, robeOfMagi,
        ogreAxe, bladeOfAlacrity, staffOfWizardry, reaver, eaglesong, mysticStaff,
        bladesOfAttack, claymore, mithrilHammer, demonEdge, javelin,
        glovesOfHaste, quarterstaff, hyperstone, orbOfVenom,
        chainmail, helmOfIronWill, cloak, shawl, fluffyHat, talismanOfEvasion,
        ringOfHealth, ringOfRegen, vitalityBooster, energyBooster, pointBooster,
        voodooMask, morbidMask, sageMask, voidStone, perseverance
    )

    /** 中间装备。 */
    val intermediates: List<EquipmentDefinition> = listOf(
        vanguardShield, oblivionStaff, soulBooster, dragonLance, expertArray
    )

    /** 原24件主要装备及首批3件控制装备。 */
    val mains: List<EquipmentDefinition> = listOf(
        maskOfMadness, maelstrom, mjollnir, kaya, sange, yasha,
        sangeAndYasha, kayaAndSange, yashaAndKaya, octarineCore,
        crystalys, daedalus, crimsonGuard, pipeOfInsight,
        armlet, heartOfTarrasque, satanic, butterfly,
        witchBlade, rodOfAtos, viperBite, forceStaff, hurricanePike, blackKingBar
    ) + ControlEquipmentContent.mains + SchoolEquipmentContent.mains

    /** 本文件定义的全部条目（组件 + 卷轴 + 中间 + 主要）。 */
    val all: List<EquipmentDefinition> = components + scrolls + intermediates + mains

    /** 组件效果登记表：组件 ID → 附加效果（非纯属性能力单独登记，避免污染 stats）。 */
    val componentEffects: Map<String, List<EffectDefinition>> = mapOf(
        "cloak" to listOf(cloakEffect),
        "talisman_of_evasion" to listOf(talismanEffect),
        "morbid_mask" to listOf(morbidEffect),
        "point_booster" to listOf(pointBoosterEffect),
        "energy_booster" to listOf(energyBoosterEffect)
        , "orb_of_venom" to listOf(EffectDefinition("orb.normalPoison", EffectKind.APPLY_NORMAL_POISON, EffectTrigger.ATTACK_LAND,
            mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(1), "ticks" to EffectValue.c(3)),
            damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL, dispelClass = DispelClass.BASIC)),
        "shawl" to listOf(EffectDefinition("shawl.resist", EffectKind.MAGIC_RESIST, EffectTrigger.PASSIVE,
            mapOf("percent" to EffectValue.n(.10))))
    )

    /** 全部配方（中间 + 主要）。 */
    val recipes: List<CraftingRecipe> = (intermediates + mains).map { it.recipe!! }

    /** 唯一组名清单，供 UI 提示与择强接入核对。 */
    val uniqueGroups: Set<String> =
        all.mapNotNull { it.uniqueGroup }.toSet() +
            all.flatMap { it.effects.mapNotNull { e -> e.uniqueGroup } }.toSet()

    /** 数值待审的条目（附毒类）。 */
    val pendingReview: List<EquipmentDefinition> =
        all.filter { it.requiresReview || it.effects.any { e -> e.requiresReview } }
}
