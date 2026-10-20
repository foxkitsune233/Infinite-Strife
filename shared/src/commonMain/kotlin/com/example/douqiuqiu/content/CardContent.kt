package com.example.douqiuqiu.content

/**
 * 30 张技能卡的内容定义与六流派羁绊档位（H01 非毒部分）。
 *
 * 数据来源：`docs/design/combat-content.md`（v1 内容表）。
 *
 * 重要边界：
 * - 2026-10-17 用户已明确采用毒卡/羁绊与Viper整套首轮参数，30卡均为CONFIRMED。
 *   接入层仍走 [CardCatalog.confirmedOnly]，未来新增待审内容继续隔离。
 * - 本文件只做**配置与模型**，不做结算；结算由 H02/H04 按 [EffectDefinition] 契约实现。
 * - 价格、货架与掉落门槛属首轮配置值（内容表未逐张给出），标注为可调，不声称已平衡。
 */

// ---------------------------------------------------------------- 流派

/**
 * 六流派。ID 与 `config/schools.json` 已有的 basic/ultimate/crit/shield 保持一致，
 * 新增 restore（恢复）与 poison（中毒）。旧的 life/evade 属早期草案，不在 v1 内容表内。
 */
enum class School(val id: String, val label: String) {
    BASIC_ATTACK("basic", "普攻"),
    ULTIMATE("ultimate", "大招"),
    CRIT("crit", "暴击"),
    SHIELD("shield", "护盾"),
    RESTORE("restore", "恢复"),
    POISON("poison", "中毒"),
    RAGE("rage", "怒火"),
    FROST("frost", "寒霜");

    companion object {
        val IDS: Set<String> = entries.map { it.id }.toSet()

        fun byId(id: String): School = entries.singleOrNull { it.id == id }
            ?: error("未知流派 ID: $id")
    }
}

/** 品质档位。 */
enum class QualityTier(val label: String) {
    WHITE("白"), BLUE("蓝"), PURPLE("紫"), GOLD("金")
}

/** 内容审阅状态。 */
enum class ReviewStatus {
    /** 已定案，可直接接入 */
    CONFIRMED,

    /** 数值来自审阅稿，接入前需用户批准 */
    PENDING_REVIEW
}

// ---------------------------------------------------------------- 卡牌定义

/**
 * 一张技能卡的完整定义。
 *
 * @property effects 效果列表；同名卡不同星级使用同一份定义，按 [EffectDefinition.paramsAt] 取星级参数
 * @property review 审阅状态；[ReviewStatus.PENDING_REVIEW] 的卡不进入战斗
 */
data class CardDefinition(
    val id: String,
    val name: String,
    val quality: QualityTier,
    val tags: Set<String>,
    val width: Int,
    val height: Int,
    val price: Int,
    val shopShelf: String,
    val shopStock: Int,
    val minimumShopClears: Int = 0,
    val minimumDropClears: Int = 0,
    val effects: List<EffectDefinition>,
    val review: ReviewStatus = ReviewStatus.CONFIRMED,
    val description: String = ""
) {
    init {
        require(id.isNotBlank()) { "卡 ID 不能为空白" }
        require(tags.size in 1..2) { "每张卡必须有 1~2 个流派标签: $id -> ${tags.size}" }
        require(tags.all { it in School.IDS }) { "未知流派标签: $id -> $tags" }
        require(width in 1..6 && height in 1..4) { "卡片尺寸超出上限: $id -> ${width}x$height" }
        require(effects.isNotEmpty()) { "卡必须至少有一条效果: $id" }
        require(effects.map { it.id }.toSet().size == effects.size) { "卡内效果 ID 重复: $id" }
        require(price > 0) { "价格必须为正: $id -> $price" }
    }

    /** 是否双流派。 */
    val isDualSchool: Boolean get() = tags.size == 2
}

// ---------------------------------------------------------------- 羁绊档位

/**
 * 流派羁绊档位效果（档位**累加**，不随卡星变化）。
 *
 * 计数规则沿用已交付的 `cards/SynergyCounter.kt`：卡种去重、双标签分别计入两个流派。
 */
data class SynergyTier(
    val schoolId: String,
    val tier: Int,
    val effects: List<EffectDefinition>
)

// ---------------------------------------------------------------- 卡表

object CardCatalog {

    // ---------------- 普攻（basic） ----------------

    private val A1 = CardDefinition(
        id = "A1", name = "迅击训练", quality = QualityTier.BLUE,
        tags = setOf(School.BASIC_ATTACK.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "固定攻速 +12（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "A1.attackSpeed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(12)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val A2 = CardDefinition(
        id = "A2", name = "精准打击", quality = QualityTier.BLUE,
        tags = setOf(School.BASIC_ATTACK.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "攻击 +10（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "A2.attack", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attack" to EffectValue.n(10)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val A3 = CardDefinition(
        id = "A3", name = "连击节奏", quality = QualityTier.PURPLE,
        tags = setOf(School.BASIC_ATTACK.id),
        width = 1, height = 1, price = 250, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "每第 5 次成功普通攻击额外造成 25 物理技能伤害",
        effects = listOf(
            EffectDefinition(
                id = "A3.everyFifth", kind = EffectKind.ADD_PHYSICAL_DAMAGE,
                trigger = EffectTrigger.ATTACK_COUNT,
                params = mapOf("amount" to EffectValue.n(25), "everyN" to EffectValue.c(5)),
                scaling = StarScaling.LINEAR_1_2_4,
                damageType = DamageType.PHYSICAL, attackClass = AttackClass.PHYSICAL_SKILL,
                flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false)
            )
        )
    )

    private val A4 = CardDefinition(
        id = "A4", name = "战意激昂", quality = QualityTier.GOLD,
        tags = setOf(School.BASIC_ATTACK.id),
        width = 1, height = 1, price = 600, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "每 4 次成功普通攻击获得 1 层 +15 攻速，最多 3 层，每层独立 4 秒；满层替换最早到期层",
        effects = listOf(
            EffectDefinition(
                id = "A4.stack", kind = EffectKind.ATTACK_SPEED_STACK,
                trigger = EffectTrigger.ATTACK_COUNT,
                params = mapOf("amount" to EffectValue.n(15), "everyN" to EffectValue.c(4)),
                scaling = StarScaling.LINEAR_1_2_4,
                maxStacks = 3, stackDurationMs = 4000L,
                dispelClass = DispelClass.BASIC
            )
        )
    )

    // ---------------- 大招（ultimate） ----------------

    private val U1 = CardDefinition(
        id = "U1", name = "奥术源泉", quality = QualityTier.BLUE,
        tags = setOf(School.ULTIMATE.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "每秒回蓝 +1.5（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "U1.manaRegen", kind = EffectKind.MANA_REGEN, trigger = EffectTrigger.PASSIVE,
                params = mapOf("amount" to EffectValue.n(1.5)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val U2 = CardDefinition(
        id = "U2", name = "法力储备", quality = QualityTier.BLUE,
        tags = setOf(School.ULTIMATE.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "魔法上限 +60、初始魔法 +15（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "U2.manaPool", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("manaFlat" to EffectValue.n(60)),
                scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "U2.initialMana", kind = EffectKind.MANA_RESTORE,
                trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(15)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val U3 = CardDefinition(
        id = "U3", name = "余波", quality = QualityTier.PURPLE,
        tags = setOf(School.ULTIMATE.id),
        width = 1, height = 1, price = 250, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "大招 CD 固定减少 0.5/1/2 秒（按星级，多张相加）；大招开始生效后追加 40/80/160 魔法伤害",
        effects = listOf(
            EffectDefinition(
                id = "U3.cdReduction", kind = EffectKind.COOLDOWN_REDUCTION,
                trigger = EffectTrigger.PASSIVE,
                params = mapOf("flatSeconds" to EffectValue.n(0.5)),
                perStar = mapOf(
                    1 to mapOf("flatSeconds" to EffectValue.n(0.5)),
                    2 to mapOf("flatSeconds" to EffectValue.n(1.0)),
                    3 to mapOf("flatSeconds" to EffectValue.n(2.0))
                ),
                scaling = StarScaling.PER_STAR
            ),
            EffectDefinition(
                id = "U3.afterUlt", kind = EffectKind.ADD_SPELL_DAMAGE,
                trigger = EffectTrigger.ULT_START,
                params = mapOf("amount" to EffectValue.n(40)),
                scaling = StarScaling.LINEAR_1_2_4,
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                flags = EffectFlags(canCrit = false, canLifesteal = false)
            )
        )
    )

    private val U4 = CardDefinition(
        id = "U4", name = "奥术回响", quality = QualityTier.GOLD,
        tags = setOf(School.ULTIMATE.id),
        width = 1, height = 1, price = 600, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "开场免费施放一次正常大招；仅开场之后首次大招冷却 +50%，之后正常。同名开场与惩罚均唯一",
        effects = listOf(
            EffectDefinition(
                id = "U4.freeCast", kind = EffectKind.FREE_ULT_CAST,
                trigger = EffectTrigger.BATTLE_START,
                params = mapOf("count" to EffectValue.c(1), "firstCastPenaltyPercent" to EffectValue.n(50)),
                scaling = StarScaling.FLAT,
                uniqueGroup = "ult_free_cast"
            )
        )
    )

    // ---------------- 暴击（crit） ----------------

    private val C1 = CardDefinition(
        id = "C1", name = "锐意", quality = QualityTier.BLUE,
        tags = setOf(School.CRIT.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "独立暴击来源：12% 概率 160%；星级概率 12/18/24%，倍率不变",
        effects = listOf(
            EffectDefinition(
                id = "C1.critSource", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.12), "multiplier" to EffectValue.n(1.60)),
                perStar = mapOf(
                    1 to mapOf("chance" to EffectValue.n(0.12), "multiplier" to EffectValue.n(1.60)),
                    2 to mapOf("chance" to EffectValue.n(0.18), "multiplier" to EffectValue.n(1.60)),
                    3 to mapOf("chance" to EffectValue.n(0.24), "multiplier" to EffectValue.n(1.60))
                ),
                scaling = StarScaling.PER_STAR
            )
        )
    )

    private val C2 = CardDefinition(
        id = "C2", name = "致命研习", quality = QualityTier.BLUE,
        tags = setOf(School.CRIT.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "已有暴击倍率 +15 个百分点，不凭空赋予暴击",
        effects = listOf(
            EffectDefinition(
                id = "C2.critMult", kind = EffectKind.CRIT_MULTIPLIER, trigger = EffectTrigger.PASSIVE,
                params = mapOf("points" to EffectValue.n(0.15)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val C3 = CardDefinition(
        id = "C3", name = "血腥预兆", quality = QualityTier.PURPLE,
        tags = setOf(School.CRIT.id),
        width = 1, height = 1, price = 250, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "每次成功暴击后额外造成 20 物理技能伤害",
        effects = listOf(
            EffectDefinition(
                id = "C3.afterCrit", kind = EffectKind.ADD_PHYSICAL_DAMAGE,
                trigger = EffectTrigger.CRIT_SUCCESS,
                params = mapOf("amount" to EffectValue.n(20)),
                scaling = StarScaling.LINEAR_1_2_4,
                damageType = DamageType.PHYSICAL, attackClass = AttackClass.PHYSICAL_SKILL,
                flags = EffectFlags(canCrit = false, canLifesteal = false)
            )
        )
    )

    private val C4 = CardDefinition(
        id = "C4", name = "弱点洞察", quality = QualityTier.GOLD,
        tags = setOf(School.CRIT.id),
        width = 1, height = 1, price = 600, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "暴击使敌人护甲 -1，最多 3 层，持续本场、可驱散；星级提高减甲量",
        effects = listOf(
            EffectDefinition(
                id = "C4.armorShred", kind = EffectKind.ARMOR_REDUCTION,
                trigger = EffectTrigger.CRIT_SUCCESS,
                params = mapOf("amount" to EffectValue.n(1)),
                scaling = StarScaling.LINEAR_1_2_4,
                maxStacks = 3, dispelClass = DispelClass.BASIC
            )
        )
    )

    // ---------------- 护盾（shield） ----------------

    private val S1 = CardDefinition(
        id = "S1", name = "备战护盾", quality = QualityTier.BLUE,
        tags = setOf(School.SHIELD.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "开场 60 通用盾，持续整场或耗尽",
        effects = listOf(
            EffectDefinition(
                id = "S1.startShield", kind = EffectKind.GRANT_SHIELD,
                trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(60)),
                scaling = StarScaling.LINEAR_1_2_4,
                shieldKind = ShieldKind.GENERAL
            )
        )
    )

    private val S2 = CardDefinition(
        id = "S2", name = "物理屏障", quality = QualityTier.BLUE,
        tags = setOf(School.SHIELD.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "每 5 秒获得 50 物理盾，持续 4 秒，首次在第 5 秒",
        effects = listOf(
            EffectDefinition(
                id = "S2.periodicShield", kind = EffectKind.GRANT_SHIELD,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf("amount" to EffectValue.n(50), "durationMs" to EffectValue.c(4000)),
                scaling = StarScaling.LINEAR_1_2_4,
                shieldKind = ShieldKind.PHYSICAL, intervalMs = 5000L
            )
        )
    )

    private val S3 = CardDefinition(
        id = "S3", name = "护盾震荡", quality = QualityTier.PURPLE,
        tags = setOf(School.SHIELD.id),
        width = 1, height = 1, price = 250, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "每 4 秒按当前护盾总量 15% 造成魔法伤害，不耗盾；比例 15/22.5/30%",
        effects = listOf(
            EffectDefinition(
                id = "S3.shieldDamage", kind = EffectKind.SHIELD_TO_DAMAGE,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf("percent" to EffectValue.n(0.15)),
                perStar = mapOf(
                    1 to mapOf("percent" to EffectValue.n(0.15)),
                    2 to mapOf("percent" to EffectValue.n(0.225)),
                    3 to mapOf("percent" to EffectValue.n(0.30))
                ),
                scaling = StarScaling.PER_STAR,
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                intervalMs = 4000L,
                flags = EffectFlags(canCrit = false, canLifesteal = false)
            )
        )
    )

    private val S4 = CardDefinition(
        id = "S4", name = "破盾重锤", quality = QualityTier.GOLD,
        tags = setOf(School.SHIELD.id),
        width = 1, height = 1, price = 600, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "每 8 秒消耗各来源当前盾量 30%，造成消耗量 120% 的物理技能伤害；星级倍率 120/180/240%，消耗比例不变",
        effects = listOf(
            EffectDefinition(
                id = "S4.consumeShield", kind = EffectKind.CONSUME_SHIELD_DAMAGE,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf(
                    "consumePercent" to EffectValue.n(0.30),
                    "damageMultiplier" to EffectValue.n(1.20)
                ),
                perStar = mapOf(
                    1 to mapOf("consumePercent" to EffectValue.n(0.30), "damageMultiplier" to EffectValue.n(1.20)),
                    2 to mapOf("consumePercent" to EffectValue.n(0.30), "damageMultiplier" to EffectValue.n(1.80)),
                    3 to mapOf("consumePercent" to EffectValue.n(0.30), "damageMultiplier" to EffectValue.n(2.40))
                ),
                scaling = StarScaling.PER_STAR,
                damageType = DamageType.PHYSICAL, attackClass = AttackClass.PHYSICAL_SKILL,
                intervalMs = 8000L,
                flags = EffectFlags(canCrit = false, canLifesteal = false)
            )
        )
    )

    // ---------------- 恢复（restore） ----------------

    private val R1 = CardDefinition(
        id = "R1", name = "生机", quality = QualityTier.BLUE,
        tags = setOf(School.RESTORE.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "每秒生命恢复 +3（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "R1.healthRegen", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("healthRegen" to EffectValue.n(3)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val R2 = CardDefinition(
        id = "R2", name = "嗜血", quality = QualityTier.BLUE,
        tags = setOf(School.RESTORE.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "攻击吸血 +6%（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "R2.lifesteal", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.06)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val R3 = CardDefinition(
        id = "R3", name = "愈合脉冲", quality = QualityTier.PURPLE,
        tags = setOf(School.RESTORE.id),
        width = 1, height = 1, price = 250, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "每 5 秒治疗 45 生命，首次在第 5 秒",
        effects = listOf(
            EffectDefinition(
                id = "R3.periodicHeal", kind = EffectKind.HEAL, trigger = EffectTrigger.INTERVAL,
                params = mapOf("amount" to EffectValue.n(45)),
                scaling = StarScaling.LINEAR_1_2_4,
                intervalMs = 5000L
            )
        )
    )

    private val R4 = CardDefinition(
        id = "R4", name = "绝境回生", quality = QualityTier.GOLD,
        tags = setOf(School.RESTORE.id),
        width = 1, height = 1, price = 600, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "低于 35% 生命时治疗 100，内部 CD 12 秒；只强化治疗量",
        effects = listOf(
            EffectDefinition(
                id = "R4.lowHpHeal", kind = EffectKind.HEAL, trigger = EffectTrigger.LOW_HP,
                params = mapOf("amount" to EffectValue.n(100), "thresholdPercent" to EffectValue.n(0.35)),
                scaling = StarScaling.LINEAR_1_2_4,
                internalCooldownMs = 12000L
            )
        )
    )

    // ---------------- 中毒（poison，数值待审） ----------------

    private val P1 = CardDefinition(
        id = "P1", name = "淬毒", quality = QualityTier.BLUE,
        tags = setOf(School.POISON.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        review = ReviewStatus.CONFIRMED,
        description = "成功普通攻击附 1 层普通毒，每次 3/6/12 伤害、共 3 次",
        effects = listOf(
            EffectDefinition(
                id = "P1.attackPoison", kind = EffectKind.APPLY_NORMAL_POISON,
                trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf(
                    "layers" to EffectValue.c(1),
                    "damagePerTick" to EffectValue.n(3),
                    "ticks" to EffectValue.c(3)
                ),
                perStar = mapOf(
                    1 to mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(3), "ticks" to EffectValue.c(3)),
                    2 to mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(6), "ticks" to EffectValue.c(3)),
                    3 to mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(12), "ticks" to EffectValue.c(3))
                ),
                scaling = StarScaling.PER_STAR,
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                dispelClass = DispelClass.BASIC,
                requiresReview = false
            )
        )
    )

    private val P2 = CardDefinition(
        id = "P2", name = "剧毒孢子", quality = QualityTier.BLUE,
        tags = setOf(School.POISON.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        review = ReviewStatus.CONFIRMED,
        description = "每 8/6/4 秒施加 1 层剧毒；其生成的普通毒每次 3 伤害、共 3 次",
        effects = listOf(
            EffectDefinition(
                id = "P2.virulent", kind = EffectKind.APPLY_VIRULENT_POISON,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf(
                    "layers" to EffectValue.c(1),
                    "spawnDamagePerTick" to EffectValue.n(3),
                    "spawnTicks" to EffectValue.c(3)
                ),
                perStar = mapOf(
                    1 to mapOf("layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(3), "spawnTicks" to EffectValue.c(3)),
                    2 to mapOf("layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(3), "spawnTicks" to EffectValue.c(3)),
                    3 to mapOf("layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(3), "spawnTicks" to EffectValue.c(3))
                ),
                scaling = StarScaling.PER_STAR,
                dispelClass = DispelClass.STRONG_ONLY,
                intervalMs = 8000L,
                requiresReview = false
            )
        )
    )

    private val P3 = CardDefinition(
        id = "P3", name = "毒液喷涌", quality = QualityTier.PURPLE,
        tags = setOf(School.POISON.id),
        width = 1, height = 1, price = 250, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        review = ReviewStatus.CONFIRMED,
        description = "每 5 秒施加 3 层普通毒，每层每次 5/10/20 伤害、共 3 次；不需要普攻命中",
        effects = listOf(
            EffectDefinition(
                id = "P3.burstPoison", kind = EffectKind.APPLY_NORMAL_POISON,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf(
                    "layers" to EffectValue.c(3),
                    "damagePerTick" to EffectValue.n(5),
                    "ticks" to EffectValue.c(3)
                ),
                perStar = mapOf(
                    1 to mapOf("layers" to EffectValue.c(3), "damagePerTick" to EffectValue.n(5), "ticks" to EffectValue.c(3)),
                    2 to mapOf("layers" to EffectValue.c(3), "damagePerTick" to EffectValue.n(10), "ticks" to EffectValue.c(3)),
                    3 to mapOf("layers" to EffectValue.c(3), "damagePerTick" to EffectValue.n(20), "ticks" to EffectValue.c(3))
                ),
                scaling = StarScaling.PER_STAR,
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                dispelClass = DispelClass.BASIC,
                intervalMs = 5000L,
                requiresReview = false
            )
        )
    )

    private val P4 = CardDefinition(
        id = "P4", name = "蚀骨剧毒", quality = QualityTier.GOLD,
        tags = setOf(School.POISON.id),
        width = 1, height = 1, price = 600, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        review = ReviewStatus.CONFIRMED,
        description = "每第 4 次成功普通攻击施加 1 层剧毒；其生成的普通毒每次 5/10/20 伤害、共 3 次",
        effects = listOf(
            EffectDefinition(
                id = "P4.virulentOn4th", kind = EffectKind.APPLY_VIRULENT_POISON,
                trigger = EffectTrigger.ATTACK_COUNT,
                params = mapOf(
                    "everyN" to EffectValue.c(4),
                    "layers" to EffectValue.c(1),
                    "spawnDamagePerTick" to EffectValue.n(5),
                    "spawnTicks" to EffectValue.c(3)
                ),
                perStar = mapOf(
                    1 to mapOf("everyN" to EffectValue.c(4), "layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(5), "spawnTicks" to EffectValue.c(3)),
                    2 to mapOf("everyN" to EffectValue.c(4), "layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(10), "spawnTicks" to EffectValue.c(3)),
                    3 to mapOf("everyN" to EffectValue.c(4), "layers" to EffectValue.c(1), "spawnDamagePerTick" to EffectValue.n(20), "spawnTicks" to EffectValue.c(3))
                ),
                scaling = StarScaling.PER_STAR,
                dispelClass = DispelClass.STRONG_ONLY,
                requiresReview = false
            )
        )
    )

    // ---------------- 双流派卡（1×2） ----------------

    private val D1 = CardDefinition(
        id = "D1", name = "毒牙连击", quality = QualityTier.PURPLE,
        tags = setOf(School.BASIC_ATTACK.id, School.POISON.id),
        width = 1, height = 2, price = 300, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        review = ReviewStatus.CONFIRMED,
        description = "每第 3 次成功普通攻击附 2 层普通毒，每层每次 5/10/20 伤害、共 3 次",
        effects = listOf(
            EffectDefinition(
                id = "D1.poisonOn3rd", kind = EffectKind.APPLY_NORMAL_POISON,
                trigger = EffectTrigger.ATTACK_COUNT,
                params = mapOf(
                    "everyN" to EffectValue.c(3),
                    "layers" to EffectValue.c(2),
                    "damagePerTick" to EffectValue.n(5),
                    "ticks" to EffectValue.c(3)
                ),
                perStar = mapOf(
                    1 to mapOf("everyN" to EffectValue.c(3), "layers" to EffectValue.c(2), "damagePerTick" to EffectValue.n(5), "ticks" to EffectValue.c(3)),
                    2 to mapOf("everyN" to EffectValue.c(3), "layers" to EffectValue.c(2), "damagePerTick" to EffectValue.n(10), "ticks" to EffectValue.c(3)),
                    3 to mapOf("everyN" to EffectValue.c(3), "layers" to EffectValue.c(2), "damagePerTick" to EffectValue.n(20), "ticks" to EffectValue.c(3))
                ),
                scaling = StarScaling.PER_STAR,
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                dispelClass = DispelClass.BASIC,
                requiresReview = false
            )
        )
    )

    private val D2 = CardDefinition(
        id = "D2", name = "猎杀节奏", quality = QualityTier.PURPLE,
        tags = setOf(School.BASIC_ATTACK.id, School.CRIT.id),
        width = 1, height = 2, price = 300, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "普通攻击暴击后获得 +15 攻速 2 秒；独立触发 CD 1 秒",
        effects = listOf(
            EffectDefinition(
                id = "D2.critSpeed", kind = EffectKind.ATTACK_SPEED_STACK,
                trigger = EffectTrigger.CRIT_SUCCESS,
                params = mapOf("amount" to EffectValue.n(15)),
                scaling = StarScaling.LINEAR_1_2_4,
                maxStacks = 1, stackDurationMs = 2000L, internalCooldownMs = 1000L,
                dispelClass = DispelClass.BASIC
            )
        )
    )

    private val D3 = CardDefinition(
        id = "D3", name = "奥术暴击", quality = QualityTier.GOLD,
        tags = setOf(School.ULTIMATE.id, School.CRIT.id),
        width = 1, height = 2, price = 650, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "普攻暴击恢复 8 魔法；每份卡内部 CD 0.5 秒；不让魔法伤害暴击",
        effects = listOf(
            EffectDefinition(
                id = "D3.critMana", kind = EffectKind.MANA_RESTORE,
                trigger = EffectTrigger.CRIT_SUCCESS,
                params = mapOf("amount" to EffectValue.n(8)),
                scaling = StarScaling.LINEAR_1_2_4,
                internalCooldownMs = 500L
            )
        )
    )

    private val D4 = CardDefinition(
        id = "D4", name = "生命屏障", quality = QualityTier.PURPLE,
        tags = setOf(School.SHIELD.id, School.RESTORE.id),
        width = 1, height = 2, price = 300, shopShelf = "ROTATING", shopStock = 2,
        minimumShopClears = 1, minimumDropClears = 1,
        description = "累计实际治疗 100 生命后获得 40 通用盾；内部 CD 2 秒，溢出治疗不计",
        effects = listOf(
            EffectDefinition(
                id = "D4.healToShield", kind = EffectKind.GRANT_SHIELD,
                trigger = EffectTrigger.HEAL_ACCUMULATED,
                params = mapOf("amount" to EffectValue.n(40), "requiredHeal" to EffectValue.n(100)),
                scaling = StarScaling.LINEAR_1_2_4,
                shieldKind = ShieldKind.GENERAL, internalCooldownMs = 2000L
            )
        )
    )

    private val D5 = CardDefinition(
        id = "D5", name = "毒血回流", quality = QualityTier.GOLD,
        tags = setOf(School.RESTORE.id, School.POISON.id),
        width = 1, height = 2, price = 650, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        review = ReviewStatus.CONFIRMED,
        description = "自身普通毒伤害 5/10/20% 转治疗，打盾部分按 50% 折算；含剧毒生成的普通毒，剧毒本体产毒不治疗",
        effects = listOf(
            EffectDefinition(
                id = "D5.poisonHeal", kind = EffectKind.HEAL,
                trigger = EffectTrigger.POISON_TICK,
                params = mapOf("percent" to EffectValue.n(0.05), "shieldAbsorbedRatio" to EffectValue.n(0.5)),
                perStar = mapOf(
                    1 to mapOf("percent" to EffectValue.n(0.05), "shieldAbsorbedRatio" to EffectValue.n(0.5)),
                    2 to mapOf("percent" to EffectValue.n(0.10), "shieldAbsorbedRatio" to EffectValue.n(0.5)),
                    3 to mapOf("percent" to EffectValue.n(0.20), "shieldAbsorbedRatio" to EffectValue.n(0.5))
                ),
                scaling = StarScaling.PER_STAR,
                requiresReview = false
            )
        )
    )

    private val D6 = CardDefinition(
        id = "D6", name = "奥术护壁", quality = QualityTier.GOLD,
        tags = setOf(School.ULTIMATE.id, School.SHIELD.id),
        width = 1, height = 2, price = 650, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "施放大招获得 60 魔法盾，持续 5 秒",
        effects = listOf(
            EffectDefinition(
                id = "D6.ultShield", kind = EffectKind.GRANT_SHIELD,
                trigger = EffectTrigger.ULT_START,
                params = mapOf("amount" to EffectValue.n(60), "durationMs" to EffectValue.c(5000)),
                scaling = StarScaling.LINEAR_1_2_4,
                shieldKind = ShieldKind.MAGICAL
            )
        )
    )

    // ---------------- 怒火（rage） ----------------

    private val RG1 = CardDefinition(
        id = "RG1", name = "怒意沸腾", quality = QualityTier.BLUE,
        tags = setOf(School.RAGE.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "攻击 +10、攻速 +12（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "RG1.attack", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attack" to EffectValue.n(10)), scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "RG1.attackSpeed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(12)), scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val RG2 = CardDefinition(
        id = "RG2", name = "连战激怒", quality = QualityTier.BLUE,
        tags = setOf(School.RAGE.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "每次普攻命中获得一层 +8/16/32 攻速，最多 3 层，每层独立持续 4 秒",
        effects = listOf(
            EffectDefinition(
                id = "RG2.stack", kind = EffectKind.ATTACK_SPEED_STACK, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(8)), scaling = StarScaling.LINEAR_1_2_4,
                maxStacks = 3, stackDurationMs = 4000
            )
        )
    )

    private val RG3 = CardDefinition(
        id = "RG3", name = "狂战回响", quality = QualityTier.PURPLE,
        tags = setOf(School.RAGE.id),
        width = 1, height = 1, price = 320, shopShelf = "ROTATING", shopStock = 2,
        description = "普攻命中追加攻击力 40/60/80% 的物理伤害；不暴击、不吸血、不再次触发命中",
        effects = listOf(
            EffectDefinition(
                id = "RG3.extra", kind = EffectKind.EXTRA_ATTACK, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("damagePercent" to EffectValue.n(0.40)),
                perStar = mapOf(
                    1 to mapOf("damagePercent" to EffectValue.n(0.40)),
                    2 to mapOf("damagePercent" to EffectValue.n(0.60)),
                    3 to mapOf("damagePercent" to EffectValue.n(0.80))
                ), scaling = StarScaling.PER_STAR,
                flags = EffectFlags(countsAsAttack = false, triggersOnHit = false)
            )
        )
    )

    private val RG4 = CardDefinition(
        id = "RG4", name = "不屈战魂", quality = QualityTier.PURPLE,
        tags = setOf(School.RAGE.id),
        width = 1, height = 1, price = 320, shopShelf = "ROTATING", shopStock = 2,
        description = "开场获得 80 通用盾；生命 +60（1/2/4 倍成长）",
        effects = listOf(
            EffectDefinition(
                id = "RG4.shield", kind = EffectKind.GRANT_SHIELD, trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(80)), shieldKind = ShieldKind.GENERAL
            ),
            EffectDefinition(
                id = "RG4.health", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("health" to EffectValue.n(60)), scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    // ---------------- 寒霜（frost） ----------------

    private val FS1 = CardDefinition(
        id = "FS1", name = "霜痕", quality = QualityTier.BLUE,
        tags = setOf(School.FROST.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "普攻命中减速目标 15%（2 秒，1/2/4 倍减速成长）",
        effects = listOf(
            EffectDefinition(
                id = "FS1.slow", kind = EffectKind.SLOW, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("slowPercent" to EffectValue.n(15), "durationMs" to EffectValue.c(2000)),
                scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val FS2 = CardDefinition(
        id = "FS2", name = "凛冬甲壳", quality = QualityTier.BLUE,
        tags = setOf(School.FROST.id),
        width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 3,
        description = "护甲 +6（1/2/4 倍成长）；开场获得 80 通用盾",
        effects = listOf(
            EffectDefinition(
                id = "FS2.armor", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("armor" to EffectValue.n(6)), scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "FS2.shield", kind = EffectKind.GRANT_SHIELD, trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(80)), shieldKind = ShieldKind.GENERAL
            )
        )
    )

    private val FS3 = CardDefinition(
        id = "FS3", name = "冰封脉冲", quality = QualityTier.PURPLE,
        tags = setOf(School.FROST.id),
        width = 1, height = 1, price = 320, shopShelf = "ROTATING", shopStock = 2,
        description = "每 5 秒冻结目标 0.6/0.8/1 秒；受状态抗性与减益免疫约束",
        effects = listOf(
            EffectDefinition(
                id = "FS3.freeze", kind = EffectKind.FREEZE, trigger = EffectTrigger.INTERVAL,
                params = mapOf("durationMs" to EffectValue.c(600)),
                perStar = mapOf(
                    1 to mapOf("durationMs" to EffectValue.c(600)),
                    2 to mapOf("durationMs" to EffectValue.c(800)),
                    3 to mapOf("durationMs" to EffectValue.c(1000))
                ), scaling = StarScaling.PER_STAR, intervalMs = 5000L
            )
        )
    )

    private val FS4 = CardDefinition(
        id = "FS4", name = "绝对零度", quality = QualityTier.GOLD,
        tags = setOf(School.FROST.id),
        width = 1, height = 1, price = 650, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "普攻命中附加冰霜 DOT（3 跳）并使目标易伤 15%（2 秒）",
        effects = listOf(
            EffectDefinition(
                id = "FS4.frostDot", kind = EffectKind.FROST_DOT, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("damagePerTick" to EffectValue.n(8), "ticks" to EffectValue.c(3)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "FS4.vuln", kind = EffectKind.VULNERABILITY, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("percent" to EffectValue.n(15), "durationMs" to EffectValue.c(2000))
            )
        )
    )

    // ---------------- 新增双流派（D7–D13，1x2） ----------------

    private val D7 = CardDefinition(
        id = "D7", name = "沸血连斩", quality = QualityTier.BLUE,
        tags = setOf(School.BASIC_ATTACK.id, School.RAGE.id),
        width = 1, height = 2, price = 180, shopShelf = "ROTATING", shopStock = 2,
        description = "攻速 +12（1/2/4）；普攻命中追加 30% 攻击力伤害",
        effects = listOf(
            EffectDefinition(
                id = "D7.attackSpeed", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(12)), scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "D7.extra", kind = EffectKind.EXTRA_ATTACK, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("damagePercent" to EffectValue.n(0.30)),
                flags = EffectFlags(countsAsAttack = false, triggersOnHit = false)
            )
        )
    )

    private val D8 = CardDefinition(
        id = "D8", name = "霜影咏唱", quality = QualityTier.PURPLE,
        tags = setOf(School.ULTIMATE.id, School.FROST.id),
        width = 1, height = 2, price = 360, shopShelf = "ROTATING", shopStock = 2,
        description = "大招伤害 +15%；普攻命中减速目标 12%（2 秒）",
        effects = listOf(
            EffectDefinition(
                id = "D8.amp", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.ULT_DAMAGE,
                params = mapOf("percent" to EffectValue.n(0.15)), scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "D8.slow", kind = EffectKind.SLOW, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("slowPercent" to EffectValue.n(12), "durationMs" to EffectValue.c(2000))
            )
        )
    )

    private val D9 = CardDefinition(
        id = "D9", name = "致命沸点", quality = QualityTier.PURPLE,
        tags = setOf(School.CRIT.id, School.RAGE.id),
        width = 1, height = 2, price = 360, shopShelf = "ROTATING", shopStock = 2,
        description = "独立暴击来源：10/15/20% 概率、1.5 倍率；普攻命中每层 +8/16/32 攻速，最多 3 层，每层 4 秒",
        effects = listOf(
            EffectDefinition(
                id = "D9.crit", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.10), "multiplier" to EffectValue.n(1.50)),
                perStar = mapOf(
                    1 to mapOf("chance" to EffectValue.n(0.10), "multiplier" to EffectValue.n(1.50)),
                    2 to mapOf("chance" to EffectValue.n(0.15), "multiplier" to EffectValue.n(1.50)),
                    3 to mapOf("chance" to EffectValue.n(0.20), "multiplier" to EffectValue.n(1.50))
                ), scaling = StarScaling.PER_STAR
            ),
            EffectDefinition(
                id = "D9.stack", kind = EffectKind.ATTACK_SPEED_STACK, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(8)), scaling = StarScaling.LINEAR_1_2_4,
                maxStacks = 3, stackDurationMs = 4000
            )
        )
    )

    private val D10 = CardDefinition(
        id = "D10", name = "冰甲反噬", quality = QualityTier.BLUE,
        tags = setOf(School.SHIELD.id, School.FROST.id),
        width = 1, height = 2, price = 180, shopShelf = "ROTATING", shopStock = 2,
        description = "开场获得 80 通用盾；护甲 +6（1/2/4）",
        effects = listOf(
            EffectDefinition(
                id = "D10.shield", kind = EffectKind.GRANT_SHIELD, trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(80)), shieldKind = ShieldKind.GENERAL
            ),
            EffectDefinition(
                id = "D10.armor", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("armor" to EffectValue.n(6)), scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val D11 = CardDefinition(
        id = "D11", name = "逆境热血", quality = QualityTier.PURPLE,
        tags = setOf(School.RESTORE.id, School.RAGE.id),
        width = 1, height = 2, price = 360, shopShelf = "ROTATING", shopStock = 2,
        description = "生命低于 50% 时治疗最大生命的 4/6/8%，内部 CD 12 秒；每秒生命回复 +3/6/12",
        effects = listOf(
            EffectDefinition(
                id = "D11.heal", kind = EffectKind.HEAL, trigger = EffectTrigger.LOW_HP,
                params = mapOf("percent" to EffectValue.n(0.04), "thresholdPercent" to EffectValue.n(0.50)),
                perStar = mapOf(
                    1 to mapOf("percent" to EffectValue.n(0.04), "thresholdPercent" to EffectValue.n(0.50)),
                    2 to mapOf("percent" to EffectValue.n(0.06), "thresholdPercent" to EffectValue.n(0.50)),
                    3 to mapOf("percent" to EffectValue.n(0.08), "thresholdPercent" to EffectValue.n(0.50))
                ), scaling = StarScaling.PER_STAR,
                internalCooldownMs = 12000L
            ),
            EffectDefinition(
                id = "D11.regen", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("healthRegen" to EffectValue.n(3)), scaling = StarScaling.LINEAR_1_2_4
            )
        )
    )

    private val D12 = CardDefinition(
        id = "D12", name = "冻毒蔓延", quality = QualityTier.PURPLE,
        tags = setOf(School.POISON.id, School.FROST.id),
        width = 1, height = 2, price = 360, shopShelf = "ROTATING", shopStock = 2,
        description = "普攻命中附加 1 层普通毒（3 跳，每跳 6 伤害）并减速 12%（2 秒）",
        effects = listOf(
            EffectDefinition(
                id = "D12.poison", kind = EffectKind.APPLY_NORMAL_POISON, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("layers" to EffectValue.c(1), "damagePerTick" to EffectValue.n(6), "ticks" to EffectValue.c(3)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                scaling = StarScaling.LINEAR_1_2_4
            ),
            EffectDefinition(
                id = "D12.slow", kind = EffectKind.SLOW, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("slowPercent" to EffectValue.n(12), "durationMs" to EffectValue.c(2000))
            )
        )
    )

    private val D13 = CardDefinition(
        id = "D13", name = "极温崩解", quality = QualityTier.GOLD,
        tags = setOf(School.RAGE.id, School.FROST.id),
        width = 1, height = 2, price = 650, shopShelf = "RARE", shopStock = 1,
        minimumShopClears = 2, minimumDropClears = 2,
        description = "普攻命中每层 +8/16/32 攻速，最多 4 层，每层 4 秒；使目标易伤 18%（2 秒，不随星级增加）",
        effects = listOf(
            EffectDefinition(
                id = "D13.stack", kind = EffectKind.ATTACK_SPEED_STACK, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(8)), scaling = StarScaling.LINEAR_1_2_4,
                maxStacks = 4, stackDurationMs = 4000
            ),
            EffectDefinition(
                id = "D13.vuln", kind = EffectKind.VULNERABILITY, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("percent" to EffectValue.n(18), "durationMs" to EffectValue.c(2000))
            )
        )
    )

    /** 全部 45 张卡（30 旧 + 怒火/寒霜 8 单流派 + D7–D13 双流派）。 */
    val all: List<CardDefinition> = listOf(
        A1, A2, A3, A4,
        U1, U2, U3, U4,
        C1, C2, C3, C4,
        S1, S2, S3, S4,
        R1, R2, R3, R4,
        P1, P2, P3, P4,
        D1, D2, D3, D4, D5, D6,
        RG1, RG2, RG3, RG4,
        FS1, FS2, FS3, FS4,
        D7, D8, D9, D10, D11, D12, D13
    )

    val byId: Map<String, CardDefinition> = all.associateBy { it.id }

    /** 已定案、可直接接入战斗的卡（排除新毒审阅稿条目）。 */
    val confirmedOnly: List<CardDefinition> =
        all.filter { it.review == ReviewStatus.CONFIRMED }

    /** 数值仍在审阅的卡（毒系）。接入前必须由用户批准。 */
    val pendingReview: List<CardDefinition> =
        all.filter { it.review == ReviewStatus.PENDING_REVIEW }

    // ---------------- 六流派羁绊档位（档位累加） ----------------

    private fun tier(school: School, tier: Int, effects: List<EffectDefinition>) =
        SynergyTier(school.id, tier, effects)

    /** 全部羁绊档位：每流派 2/3/4 档，档位效果累加。 */
    val synergyTiers: List<SynergyTier> = listOf(
        // 普攻
        tier(School.BASIC_ATTACK, 2, listOf(
            EffectDefinition(
                id = "synergy.basic.2", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(10))
            )
        )),
        tier(School.BASIC_ATTACK, 3, listOf(
            EffectDefinition(
                id = "synergy.basic.3", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attack" to EffectValue.n(12))
            )
        )),
        tier(School.BASIC_ATTACK, 4, listOf(
            EffectDefinition(
                id = "synergy.basic.4", kind = EffectKind.EXTRA_ATTACK,
                trigger = EffectTrigger.ATTACK_COUNT,
                params = mapOf("damagePercent" to EffectValue.n(0.50), "everyN" to EffectValue.c(5)),
                flags = EffectFlags(canCrit = false, canLifesteal = false, triggersOnHit = false, countsAsAttack = false)
            )
        )),

        // 大招
        tier(School.ULTIMATE, 2, listOf(
            EffectDefinition(
                id = "synergy.ultimate.2", kind = EffectKind.MANA_REGEN, trigger = EffectTrigger.PASSIVE,
                params = mapOf("amount" to EffectValue.n(1.5))
            )
        )),
        tier(School.ULTIMATE, 3, listOf(
            EffectDefinition(
                id = "synergy.ultimate.3", kind = EffectKind.DAMAGE_AMP,
                trigger = EffectTrigger.ULT_DAMAGE,
                params = mapOf("percent" to EffectValue.n(0.15))
            )
        )),
        tier(School.ULTIMATE, 4, listOf(
            EffectDefinition(
                id = "synergy.ultimate.4", kind = EffectKind.MANA_RESTORE,
                trigger = EffectTrigger.ULT_START,
                params = mapOf("amount" to EffectValue.n(15))
            )
        )),

        // 暴击
        tier(School.CRIT, 2, listOf(
            EffectDefinition(
                id = "synergy.crit.2", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.10), "multiplier" to EffectValue.n(1.50))
            )
        )),
        tier(School.CRIT, 3, listOf(
            EffectDefinition(
                id = "synergy.crit.3", kind = EffectKind.CRIT_MULTIPLIER, trigger = EffectTrigger.PASSIVE,
                params = mapOf("points" to EffectValue.n(0.20))
            )
        )),
        tier(School.CRIT, 4, listOf(
            EffectDefinition(
                id = "synergy.crit.4", kind = EffectKind.ADD_PHYSICAL_DAMAGE,
                trigger = EffectTrigger.CRIT_SUCCESS,
                params = mapOf("amount" to EffectValue.n(30)),
                damageType = DamageType.PHYSICAL, attackClass = AttackClass.PHYSICAL_SKILL,
                internalCooldownMs = 500L,
                flags = EffectFlags(canCrit = false, canLifesteal = false)
            )
        )),

        // 护盾
        tier(School.SHIELD, 2, listOf(
            EffectDefinition(
                id = "synergy.shield.2", kind = EffectKind.GRANT_SHIELD,
                trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(80)),
                shieldKind = ShieldKind.GENERAL
            )
        )),
        tier(School.SHIELD, 3, listOf(
            EffectDefinition(
                id = "synergy.shield.3", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.15))
            )
        )),
        tier(School.SHIELD, 4, listOf(
            EffectDefinition(
                id = "synergy.shield.4", kind = EffectKind.SHIELD_TO_DAMAGE,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf("percent" to EffectValue.n(0.20)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                intervalMs = 5000L,
                flags = EffectFlags(canCrit = false, canLifesteal = false)
            )
        )),

        // 恢复
        tier(School.RESTORE, 2, listOf(
            EffectDefinition(
                id = "synergy.restore.2", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("healthRegen" to EffectValue.n(3))
            )
        )),
        tier(School.RESTORE, 3, listOf(
            EffectDefinition(
                id = "synergy.restore.3", kind = EffectKind.LIFESTEAL, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.08))
            )
        )),
        tier(School.RESTORE, 4, listOf(
            EffectDefinition(
                id = "synergy.restore.4", kind = EffectKind.HEAL, trigger = EffectTrigger.INTERVAL,
                params = mapOf("percent" to EffectValue.n(0.04)),
                intervalMs = 6000L
            )
        )),

        // 中毒（数值待审）
        tier(School.POISON, 2, listOf(
            EffectDefinition(
                id = "synergy.poison.2", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.10)),
                requiresReview = false
            )
        )),
        tier(School.POISON, 3, listOf(
            EffectDefinition(
                id = "synergy.poison.3", kind = EffectKind.APPLY_VIRULENT_POISON,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf(
                    "layers" to EffectValue.c(1),
                    "spawnDamagePerTick" to EffectValue.n(3),
                    "spawnTicks" to EffectValue.c(3)
                ),
                dispelClass = DispelClass.STRONG_ONLY, intervalMs = 6000L,
                requiresReview = false
            )
        )),
        tier(School.POISON, 4, listOf(
            EffectDefinition(
                id = "synergy.poison.4", kind = EffectKind.APPLY_NORMAL_POISON,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf(
                    "layers" to EffectValue.c(3),
                    "damagePerTick" to EffectValue.n(6),
                    "ticks" to EffectValue.c(3)
                ),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                dispelClass = DispelClass.BASIC, intervalMs = 5000L,
                requiresReview = false
            )
        )),

        // 怒火（rage）
        tier(School.RAGE, 2, listOf(
            EffectDefinition(
                id = "synergy.rage.2", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attackSpeed" to EffectValue.n(10))
            )
        )),
        tier(School.RAGE, 3, listOf(
            EffectDefinition(
                id = "synergy.rage.3", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("attack" to EffectValue.n(12))
            )
        )),
        tier(School.RAGE, 4, listOf(
            EffectDefinition(
                id = "synergy.rage.4", kind = EffectKind.EXTRA_ATTACK,
                trigger = EffectTrigger.ATTACK_COUNT,
                params = mapOf("damagePercent" to EffectValue.n(0.40), "everyN" to EffectValue.c(5)),
                flags = EffectFlags(canCrit = false, canLifesteal = false, countsAsAttack = false, triggersOnHit = false)
            )
        )),

        // 寒霜（frost）
        tier(School.FROST, 2, listOf(
            EffectDefinition(
                id = "synergy.frost.2", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
                params = mapOf("armor" to EffectValue.n(6))
            )
        )),
        tier(School.FROST, 3, listOf(
            EffectDefinition(
                id = "synergy.frost.3", kind = EffectKind.DAMAGE_AMP, trigger = EffectTrigger.PASSIVE,
                params = mapOf("percent" to EffectValue.n(0.12))
            )
        )),
        tier(School.FROST, 4, listOf(
            EffectDefinition(
                id = "synergy.frost.4", kind = EffectKind.FROST_DOT,
                trigger = EffectTrigger.INTERVAL,
                params = mapOf("damagePerTick" to EffectValue.n(6), "ticks" to EffectValue.c(3)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL,
                intervalMs = 5000L
            )
        ))
    )

    /** 羁绊档位门槛：2/3/4 种卡。 */
    val synergyThresholds: List<Int> = listOf(2, 3, 4)
}
