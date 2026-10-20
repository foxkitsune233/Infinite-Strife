package com.example.douqiuqiu.content

/**
 * 战斗效果的**模型契约**（H01 非毒部分）。
 *
 * 这一层只定义「效果用什么类型表达」，不包含任何战斗结算逻辑，也不解析中文描述字符串。
 * 结算由 H02 的战斗效果基础按本契约实现；装备/卡牌/羁绊/英雄技能全部复用同一套类型。
 *
 * 约束：
 * - 纯 Kotlin common：无 Android / `java.*` / 时钟 / 随机数 / IO 依赖。
 * - 所有数值、开关、枚举选择都通过 [EffectValue] 的密封子类表达，禁止用字符串承载语义。
 * - 唯一组（[EffectDefinition.uniqueGroup]）只声明归属，择强与合并规则由已交付的
 *   `equipment/UniqueBonusSelector.kt` 与 H02 的效果聚合共同决定，本文件不重复实现。
 */

// ---------------------------------------------------------------- 伤害与攻击归类

/** 伤害类型。纯穿盾：[PURE]；生命移除：[HP_REMOVAL]（不吃法术增伤、不触发吸血与受伤反应）。 */
enum class DamageType { PHYSICAL, MAGICAL, PURE, HP_REMOVAL }

/**
 * 攻击/伤害归类。供 H02 的伤害事件使用：
 * 是否触发命中效果、暴击、吸血、反击都按此归类判定，而不是按伤害类型猜。
 */
enum class AttackClass {
    /** 普通攻击 */
    ATTACK,

    /** 物理技能伤害（如追加伤害段、破盾重锤） */
    PHYSICAL_SKILL,

    /** 法术伤害 */
    SPELL,

    /** 反射伤害（不触发吸血、不递归） */
    REFLECT,

    /** 自损（不致死、不触发吸血） */
    SELF_DAMAGE,

    /** 生命移除 */
    HP_REMOVAL
}

/** 护盾类型。三类盾分别显示、分别消耗。 */
enum class ShieldKind { GENERAL, PHYSICAL, MAGICAL }

/** 驱散等级。普通毒为 [BASIC]，剧毒为 [STRONG_ONLY]。 */
enum class DispelClass { NOT_DISPELLABLE, BASIC, STRONG_ONLY }

// ---------------------------------------------------------------- 触发时机

/**
 * 效果触发时机。
 *
 * 同一 tick 内的宏观执行次序由 H02 规定（先到期状态/恢复/CD → 持续伤害 → 即时防护反应 →
 * 可用主动与英雄技能 → 攻击事件），本枚举只描述「效果在什么事件上触发」。
 */
enum class EffectTrigger {
    /** 常驻：属性加成、抗性、吸血率、暴击来源等，不依赖事件 */
    PASSIVE,

    /** 开场一次性（护盾、免费大招、战意类初始状态） */
    BATTLE_START,

    /** 一次普通攻击成功命中（被普攻保护拦截时不触发） */
    ATTACK_LAND,

    /** 每第 N 次成功普通攻击；N 由参数 `everyN` 给出 */
    ATTACK_COUNT,

    /** 暴击成功结算后 */
    CRIT_SUCCESS,

    /** 大招开始施放（免费施法、追加伤害、护盾获得） */
    ULT_START,

    /** 大招伤害结算（伤害增幅） */
    ULT_DAMAGE,

    /** 固定周期；周期由 `intervalMs` 给出，首次触发时刻 = 间隔（不是 0） */
    INTERVAL,

    /** 生命低于阈值；阈值由 `thresholdPercent` 给出，带 `internalCooldownMs` 重新检查 */
    LOW_HP,

    /** 累计实际治疗达到阈值（不含溢出治疗） */
    HEAL_ACCUMULATED,

    /** 受到有效生命伤害（毒性皮肤反击等） */
    DAMAGE_TAKEN,

    /** 自身毒跳造成伤害（毒血回流等） */
    POISON_TICK,

    /** 主动装备：手动或自动触发，受动作权限与共享 CD 约束 */
    ACTIVE,

    /** 受控时触发的被动保护（原力法杖/飓风长戟） */
    ON_CONTROLLED
}

// ---------------------------------------------------------------- 效果种类

/**
 * 效果种类。
 *
 * 每个种类对应一份参数契约（见 [EffectContract]），加载时校验，避免「参数名写错但静默生效」。
 */
enum class EffectKind {
    /** Source-aware, timed negative control; evaluated by the equipment runtime. */
    APPLY_CONTROL,
    /** 固定属性加成（力量/敏捷/智力/攻击/护甲/攻速/生命/回血/回蓝/魔法上限%） */
    STAT_BONUS,

    /** 追加一次物理技能伤害 */
    ADD_PHYSICAL_DAMAGE,

    /** 追加一次法术伤害 */
    ADD_SPELL_DAMAGE,

    /** 追加纯粹伤害 */
    ADD_PURE_DAMAGE,

    /** 给予护盾；同来源按既定覆盖规则，天怒被动为明确例外 */
    GRANT_SHIELD,

    /** 治疗；溢出部分不计入 [EffectTrigger.HEAL_ACCUMULATED] */
    HEAL,

    /** 攻击吸血率加成（%） */
    LIFESTEAL,

    /** 独立暴击来源（概率 + 倍率） */
    CRIT_SOURCE,

    /** 已触发暴击的倍率增量（百分点），不凭空赋予暴击 */
    CRIT_MULTIPLIER,

    /** 护甲削减，可叠层 */
    ARMOR_REDUCTION,

    /** 临时攻速层，每层独立计时、互不刷新 */
    ATTACK_SPEED_STACK,

    /** 立即恢复魔法 */
    MANA_RESTORE,

    /** 每秒回蓝 */
    MANA_REGEN,

    /** 伤害增幅（法术/技能增伤等） */
    DAMAGE_AMP,

    /** 恢复增幅 */
    HEAL_AMP,

    /** 状态抗性 */
    STATUS_RESIST,

    /** 冷却缩减（固定秒 / 百分比，两段分离） */
    COOLDOWN_REDUCTION,

    /** 施加普通毒（三跳）；伤害/次数参数属新毒审阅稿范围，需标 requiresReview */
    APPLY_NORMAL_POISON,

    /** 施加剧毒（不伤害、每秒产普通毒、仅强驱散） */
    APPLY_VIRULENT_POISON,

    /** 按当前护盾总量造成伤害，不消耗盾 */
    SHIELD_TO_DAMAGE,

    /** 消耗各来源盾量并造成伤害 */
    CONSUME_SHIELD_DAMAGE,

    /** 普通攻击固定格挡（先锋盾/赤红甲同类择强） */
    PHYSICAL_BLOCK,

    /** 闪避（多源乘算） */
    EVASION,

    /** 普攻保护：整次普攻及其附带效果全无效，必中不穿透 */
    ATTACK_PROTECTION,

    /** 驱散 */
    DISPEL,

    /** 减益免疫/魔抗等限时状态 */
    IMMUNITY,

    /** 破坏：只抑制英雄被动，不关闭装备/卡牌/羁绊 */
    BREAK,

    /** 对自身施加沉默（疯狂面具狂热） */
    SELF_SILENCE,

    /** 开场免费大招（同名唯一，首次惩罚只应用一次） */
    FREE_ULT_CAST,

    /** 追加一次攻击（不递归触发「每 N 次攻击」与额外攻击） */
    EXTRA_ATTACK,

    /** 魔法抗性（独立来源，乘算） */
    MAGIC_RESIST,

    /** 减速：降低目标攻击／移动速度（寒霜系）；受状态抗性，可被驱散 */
    SLOW,

    /** 冻结：寒霜系硬控，独立于眩晕；受状态抗性，但不可被普通／强驱散清除（0.1.5） */
    FREEZE,

    /** 冰霜持续法术伤害（寒霜系 DOT，0.1.5） */
    FROST_DOT,

    /** 冰霜易伤：提高目标受到伤害（寒霜系，0.1.5） */
    VULNERABILITY
}

// ---------------------------------------------------------------- 类型化参数

/**
 * 效果参数值。
 *
 * 禁止用字符串承载数值或开关：字符串只允许表达**枚举选择**（[EffectValue.Choice]），
 * 且取值必须在对应契约声明的候选集内。
 */
sealed class EffectValue {
    /** 数值（允许小数；百分比用小数，如 0.15 表示 15%） */
    data class Num(val value: Double) : EffectValue()

    /** 次数 / 层数 / 整数毫秒 */
    data class Count(val value: Int) : EffectValue()

    /** 开关 */
    data class Flag(val value: Boolean) : EffectValue()

    /** 枚举选择，取值由契约的候选项约束 */
    data class Choice(val value: String) : EffectValue()

    companion object {
        fun n(value: Double) = Num(value)
        fun n(value: Int) = Num(value.toDouble())
        fun c(value: Int) = Count(value)
        fun f(value: Boolean) = Flag(value)
        fun e(value: String) = Choice(value)
    }
}

/** 参数名 → 参数值。键固定小写驼峰，见 [EffectContract.required]/[optional]。 */
typealias EffectParams = Map<String, EffectValue>

// ---------------------------------------------------------------- 星级成长

/**
 * 星级成长方式。
 *
 * 规则（内容表定案）：输出/治疗/盾量/固定属性按 1/2/4 倍；
 * 概率、持续时间、CD、间隔等属于**专用成长**，必须逐星级显式给出，不统一乘倍数。
 */
enum class StarScaling {
    /** 一星数值 × 1 / 2 / 4 */
    LINEAR_1_2_4,

    /** 三个星级各自显式给值（[EffectDefinition.perStar] 必须覆盖 1..3） */
    PER_STAR,

    /** 不随星级变化 */
    FLAT
}

// ---------------------------------------------------------------- 效果标记

/**
 * 一次效果产生的伤害/攻击如何参与后续结算。
 *
 * 这些标记必须显式声明，不能被结算层按「默认全部为真」猜出来——
 * 追加段、闪电、反射、自损各自的资格不同，猜错就会造成递归或错误吸血。
 */
data class EffectFlags(
    /** 可否暴击 */
    val canCrit: Boolean = false,

    /** 可否攻击吸血 */
    val canLifesteal: Boolean = false,

    /** 可否触发命中类效果（装备命中、附毒等） */
    val triggersOnHit: Boolean = false,

    /** 是否为反射伤害（不再触发反击/吸血） */
    val isReflect: Boolean = false,

    /** 是否为自损（不致死、不吸血） */
    val isSelfDamage: Boolean = false,

    /** 旧契约预留字段；本游戏必中不穿普攻保护，结算层不得用此字段绕过保护。 */
    val piercesAttackProtection: Boolean = false,

    /** 是否计入「每 N 次成功普通攻击」计数 */
    val countsAsAttack: Boolean = false,

    /** 是否可触发同类二级效果（默认 false，防止盾→治疗→盾无限链） */
    val canTriggerSecondary: Boolean = false
)

// ---------------------------------------------------------------- 效果定义

/**
 * 一条效果定义。
 *
 * @property id 效果实例 ID，在所属卡/装备内唯一，用于来源追踪与存档
 * @property kind 效果种类，决定参数契约
 * @property trigger 触发时机
 * @property params 一星（默认）参数
 * @property perStar 二/三星参数覆盖；[StarScaling.PER_STAR] 时必须覆盖 1..3
 * @property scaling 星级成长方式
 * @property damageType 造成伤害时的伤害类型（仅伤害类效果需要）
 * @property attackClass 造成伤害时的攻击归类（仅伤害类效果需要）
 * @property shieldKind 护盾类型（仅 [EffectKind.GRANT_SHIELD] 需要）
 * @property dispelClass 该效果附加状态的驱散等级
 * @property uniqueGroup 唯一组 ID；同组内择强，基础属性仍相加
 * @property flags 结算资格标记
 * @property intervalMs 周期触发间隔（[EffectTrigger.INTERVAL] 必需）
 * @property internalCooldownMs 内部冷却（0 表示无）
 * @property maxStacks 最大层数（叠层类效果）
 * @property stackDurationMs 单层持续时间（叠层类效果，各层独立计时）
 * @property requiresReview true 表示本条效果的数值来自**审阅稿**，尚未获批准；
 *                          校验器会把它计入待审清单，接入层不得当作定案数值使用
 */
data class EffectDefinition(
    val id: String,
    val kind: EffectKind,
    val trigger: EffectTrigger,
    val params: EffectParams,
    val perStar: Map<Int, EffectParams> = emptyMap(),
    val scaling: StarScaling = StarScaling.FLAT,
    val damageType: DamageType? = null,
    val attackClass: AttackClass? = null,
    val shieldKind: ShieldKind? = null,
    val dispelClass: DispelClass = DispelClass.NOT_DISPELLABLE,
    val uniqueGroup: String? = null,
    val flags: EffectFlags = EffectFlags(),
    val intervalMs: Long? = null,
    val internalCooldownMs: Long? = null,
    val maxStacks: Int? = null,
    val stackDurationMs: Long? = null,
    val requiresReview: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "effectId 不能为空白" }
        require(params.isNotEmpty()) { "效果参数不能为空: $id" }
        if (scaling == StarScaling.PER_STAR) {
            require(perStar.keys.containsAll(listOf(1, 2, 3))) {
                "PER_STAR 必须覆盖 1/2/3 星: $id -> ${perStar.keys}"
            }
        }
        intervalMs?.let { require(it > 0) { "intervalMs 必须为正: $id -> $it" } }
        internalCooldownMs?.let { require(it >= 0) { "internalCooldownMs 不能为负: $id -> $it" } }
        maxStacks?.let { require(it > 0) { "maxStacks 必须为正: $id -> $it" } }
        stackDurationMs?.let { require(it > 0) { "stackDurationMs 必须为正: $id -> $it" } }
    }

    /**
     * 取指定星级（1..3）的参数：先取 [perStar] 覆盖，缺失则回落到一星参数。
     *
     * [StarScaling.LINEAR_1_2_4] 会在此处**真正放大**可成长参数（输出/治疗/盾量/固定属性），
     * 避免接入层忘记乘倍数导致「二星卡与一星卡一样强」。
     * 不可成长的参数（everyN / ticks / layers / 时长 / CD / 门槛）不在白名单内，保持原值。
     */
    fun paramsAt(star: Int): EffectParams {
        require(star in 1..3) { "star 必须在 1..3: $star" }
        val base = perStar[star] ?: params
        if (scaling != StarScaling.LINEAR_1_2_4 || star == 1) return base
        val factor = LINEAR_FACTORS.getValue(star)
        return base.mapValues { (key, value) ->
            if (key in SCALABLE_PARAMS && value is EffectValue.Num) {
                EffectValue.Num(value.value * factor)
            } else {
                value
            }
        }
    }

    companion object {
        /** 一/二/三星的成长倍率。 */
        val LINEAR_FACTORS: Map<Int, Double> = mapOf(1 to 1.0, 2 to 2.0, 3 to 4.0)

        /**
         * 允许随星级 1/2/4 放大的参数名。
         *
         * 不含 everyN / ticks / layers / durationMs / internalCooldownMs / thresholdPercent /
         * requiredHeal —— 门槛、次数与时长属于「专用成长」，必须逐星级显式写，不能连倍数一起乘。
         */
        val SCALABLE_PARAMS: Set<String> = setOf(
            "amount", "damagePerTick", "spawnDamagePerTick", "points", "percent", "slowPercent",
            "strength", "agility", "intelligence", "attack", "armor", "attackSpeed",
            "health", "healthRegen", "manaFlat", "manaPercent", "manaRegen"
        )
    }
}

// ---------------------------------------------------------------- 参数契约

/**
 * 效果种类的参数契约：声明必需/可选参数名与允许的值类型。
 *
 * 加载期校验用；不校验数值平衡，只保证「参数齐全且类型正确」。
 */
data class EffectContract(
    val kind: EffectKind,
    val required: Set<String>,
    val optional: Set<String> = emptySet(),
    val enumChoices: Map<String, Set<String>> = emptyMap(),
    val requiresDamageType: Boolean = false,
    val requiresAttackClass: Boolean = false,
    val requiresShieldKind: Boolean = false
)

/** 各效果种类的参数契约表。 */
object EffectContracts {

    val ALL: Map<EffectKind, EffectContract> = mapOf(
        EffectKind.APPLY_CONTROL to EffectContract(EffectKind.APPLY_CONTROL,
            required = setOf("control", "durationMs", "rangedDurationMs", "chance", "rangedChance"),
            enumChoices = mapOf("control" to setOf("STUN", "HEX", "SILENCE", "DISARM", "FREEZE"))),
        EffectKind.STAT_BONUS to EffectContract(
            EffectKind.STAT_BONUS,
            required = emptySet(),
            optional = setOf(
                "strength", "agility", "intelligence", "attack", "armor",
                "attackSpeed", "attackSpeedPercent", "health", "healthRegen",
                "manaFlat", "manaPercent", "manaRegen"
            )
        ),
        // 物理技能伤害：chance 为追加段触发概率，maxTargets 为多单位预留
        EffectKind.ADD_PHYSICAL_DAMAGE to EffectContract(
            EffectKind.ADD_PHYSICAL_DAMAGE,
            required = setOf("amount"),
            optional = setOf("everyN", "chance", "maxTargets"),
            requiresDamageType = true,
            requiresAttackClass = true
        ),
        // 法术伤害：chance 为触发概率；maxTargets/bounce 为多单位预留（首版单挑只用 maxTargets=1）
        EffectKind.ADD_SPELL_DAMAGE to EffectContract(
            EffectKind.ADD_SPELL_DAMAGE,
            required = setOf("amount"),
            optional = setOf("everyN", "chance", "maxTargets", "bounce"),
            requiresDamageType = true,
            requiresAttackClass = true
        ),
        EffectKind.ADD_PURE_DAMAGE to EffectContract(
            EffectKind.ADD_PURE_DAMAGE,
            required = setOf("amount"),
            requiresDamageType = true,
            requiresAttackClass = true
        ),
        EffectKind.GRANT_SHIELD to EffectContract(
            EffectKind.GRANT_SHIELD,
            required = setOf("amount"),
            optional = setOf("durationMs", "requiredHeal"),
            requiresShieldKind = true
        ),
        // 治疗：固定量与比例量二选一（校验器保证至少存在一个），门槛与护盾折算是可选项
        EffectKind.HEAL to EffectContract(
            EffectKind.HEAL,
            required = emptySet(),
            optional = setOf("amount", "percent", "thresholdPercent", "shieldAbsorbedRatio")
        ),
        // 吸血：thresholdPercent 用于「低血才生效」的主动吸血（撒旦）
        EffectKind.LIFESTEAL to EffectContract(
            EffectKind.LIFESTEAL, required = setOf("percent"), optional = setOf("thresholdPercent")
        ),
        EffectKind.CRIT_SOURCE to EffectContract(
            EffectKind.CRIT_SOURCE, required = setOf("chance", "multiplier")
        ),
        EffectKind.CRIT_MULTIPLIER to EffectContract(
            EffectKind.CRIT_MULTIPLIER, required = setOf("points")
        ),
        EffectKind.ARMOR_REDUCTION to EffectContract(
            EffectKind.ARMOR_REDUCTION, required = setOf("amount")
        ),
        // 攻速层：everyN 用于「每 N 次攻击获得一层」
        EffectKind.ATTACK_SPEED_STACK to EffectContract(
            EffectKind.ATTACK_SPEED_STACK, required = setOf("amount"), optional = setOf("everyN")
        ),
        EffectKind.MANA_RESTORE to EffectContract(
            EffectKind.MANA_RESTORE, required = setOf("amount")
        ),
        EffectKind.MANA_REGEN to EffectContract(
            EffectKind.MANA_REGEN, required = setOf("amount")
        ),
        EffectKind.DAMAGE_AMP to EffectContract(
            EffectKind.DAMAGE_AMP, required = setOf("percent")
        ),
        EffectKind.HEAL_AMP to EffectContract(
            EffectKind.HEAL_AMP, required = setOf("percent")
        ),
        EffectKind.STATUS_RESIST to EffectContract(
            EffectKind.STATUS_RESIST, required = setOf("percent")
        ),
        EffectKind.COOLDOWN_REDUCTION to EffectContract(
            EffectKind.COOLDOWN_REDUCTION, required = setOf("flatSeconds"), optional = setOf("percent")
        ),
        EffectKind.APPLY_NORMAL_POISON to EffectContract(
            EffectKind.APPLY_NORMAL_POISON,
            required = setOf("layers", "damagePerTick", "ticks"), optional = setOf("everyN")
        ),
        EffectKind.APPLY_VIRULENT_POISON to EffectContract(
            EffectKind.APPLY_VIRULENT_POISON,
            required = setOf("layers", "spawnDamagePerTick", "spawnTicks"), optional = setOf("everyN")
        ),
        EffectKind.SHIELD_TO_DAMAGE to EffectContract(
            EffectKind.SHIELD_TO_DAMAGE,
            required = setOf("percent"),
            requiresDamageType = true,
            requiresAttackClass = true
        ),
        EffectKind.CONSUME_SHIELD_DAMAGE to EffectContract(
            EffectKind.CONSUME_SHIELD_DAMAGE,
            required = setOf("consumePercent", "damageMultiplier"),
            requiresDamageType = true,
            requiresAttackClass = true
        ),
        EffectKind.PHYSICAL_BLOCK to EffectContract(
            EffectKind.PHYSICAL_BLOCK, required = setOf("amount"), optional = setOf("chance")
        ),
        EffectKind.EVASION to EffectContract(
            EffectKind.EVASION, required = setOf("percent")
        ),
        EffectKind.ATTACK_PROTECTION to EffectContract(
            EffectKind.ATTACK_PROTECTION, required = setOf("durationMs")
        ),
        EffectKind.DISPEL to EffectContract(
            EffectKind.DISPEL,
            required = setOf("level"),
            enumChoices = mapOf("level" to setOf("BASIC", "STRONG"))
        ),
        EffectKind.IMMUNITY to EffectContract(
            EffectKind.IMMUNITY,
            required = setOf("durationMs"),
            optional = setOf("magicResistPercent")
        ),
        EffectKind.BREAK to EffectContract(
            EffectKind.BREAK, required = setOf("durationMs")
        ),
        EffectKind.SELF_SILENCE to EffectContract(
            EffectKind.SELF_SILENCE, required = setOf("durationMs")
        ),
        EffectKind.FREE_ULT_CAST to EffectContract(
            EffectKind.FREE_ULT_CAST, required = setOf("count"), optional = setOf("firstCastPenaltyPercent")
        ),
        EffectKind.EXTRA_ATTACK to EffectContract(
            EffectKind.EXTRA_ATTACK, required = setOf("damagePercent")
        ),
        EffectKind.MAGIC_RESIST to EffectContract(
            EffectKind.MAGIC_RESIST, required = setOf("percent")
        ),
        // 寒霜系（0.1.5）：减速／冻结／冰霜 DOT／冰霜易伤；战斗结算（CardEffectRuntime）在 P3 接入
        EffectKind.SLOW to EffectContract(
            EffectKind.SLOW, required = setOf("slowPercent", "durationMs"), optional = setOf("chance")
        ),
        EffectKind.FREEZE to EffectContract(
            EffectKind.FREEZE, required = setOf("durationMs"), optional = setOf("chance")
        ),
        EffectKind.FROST_DOT to EffectContract(
            EffectKind.FROST_DOT, required = setOf("damagePerTick", "ticks"),
            optional = setOf("durationMs", "chance"),
            requiresDamageType = true, requiresAttackClass = true
        ),
        EffectKind.VULNERABILITY to EffectContract(
            EffectKind.VULNERABILITY, required = setOf("percent", "durationMs"), optional = setOf("chance", "maxStacks")
        )
    )

    /** 取某效果种类的契约；未登记的种类视为缺陷（新增种类必须同时补契约）。 */
    fun of(kind: EffectKind): EffectContract =
        ALL[kind] ?: error("效果种类缺少参数契约: $kind")
}
