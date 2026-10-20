package com.example.douqiuqiu.content

import com.example.douqiuqiu.inventory.RecipeGraphValidator

/**
 * 内容配置校验（H01）。
 *
 * 只做**结构与契约校验**，不做数值平衡判断：
 * - 效果参数是否符合 [EffectContracts]（必需齐全、无未登记参数、枚举取值合法）
 * - 伤害类效果是否声明了 damageType / attackClass；护盾类是否声明了 shieldKind
 * - 卡表 / 装备表 ID 是否重复、是否与既有物品 ID 冲突
 * - 配方图是否缺件、重复产物、有环（委托已交付的 [RecipeGraphValidator]）
 *
 * 待审条目（[EffectDefinition.requiresReview]）**不算问题**，只汇总进 [ContentValidationResult.pendingReview]，
 * 由接入层决定是否在批准前排除。
 */

/** 内容问题类型。 */
enum class ContentIssueKind {
    DUPLICATE_CARD_ID,
    DUPLICATE_EQUIPMENT_ID,
    DUPLICATE_EFFECT_ID,
    MISSING_CARD,
    UNKNOWN_SCHOOL_TAG,
    EQUIPMENT_ID_CONFLICT,
    EFFECT_MISSING_REQUIRED,
    EFFECT_UNKNOWN_PARAM,
    EFFECT_BAD_ENUM_CHOICE,
    EFFECT_MISSING_DAMAGE_TYPE,
    EFFECT_MISSING_ATTACK_CLASS,
    EFFECT_MISSING_SHIELD_KIND,
    EFFECT_HEAL_NEEDS_AMOUNT,
    RECIPE_GRAPH,
    NEGATIVE_STACK_DURATION
}

/** 一条内容问题。 */
data class ContentIssue(
    val kind: ContentIssueKind,
    val source: String,
    val detail: String = ""
)

/** 校验结果。 */
data class ContentValidationResult(
    val valid: Boolean,
    val issues: List<ContentIssue>,
    /** 数值待审的条目（卡 ID / 装备 ID / 效果 ID），需用户批准后才可接入 */
    val pendingReview: List<String>
)

object ContentValidator {

    /** v1 内容表要求的 30 张卡 ID。 */
    val EXPECTED_CARD_IDS: Set<String> = setOf(
        "A1", "A2", "A3", "A4",
        "U1", "U2", "U3", "U4",
        "C1", "C2", "C3", "C4",
        "S1", "S2", "S3", "S4",
        "R1", "R2", "R3", "R4",
        "P1", "P2", "P3", "P4",
        "D1", "D2", "D3", "D4", "D5", "D6",
        "RG1", "RG2", "RG3", "RG4",
        "FS1", "FS2", "FS3", "FS4",
        "D7", "D8", "D9", "D10", "D11", "D12", "D13"
    )

    /**
     * 校验全部内容。
     *
     * @param cards 卡表
     * @param equipments 装备/组件/卷轴表
     * @param existingItemIds 既有物品 ID（如 inventory.json 已有定义），用于查重冲突
     * @param expectedCardIds 期望的卡 ID 集合，默认 v1 的 30 张
     */
    fun validate(
        cards: List<CardDefinition>,
        equipments: List<EquipmentDefinition>,
        existingItemIds: Set<String> = emptySet(),
        expectedCardIds: Set<String> = EXPECTED_CARD_IDS
    ): ContentValidationResult {
        val issues = ArrayList<ContentIssue>()
        val pending = ArrayList<String>()

        // ---------- 卡表 ----------
        val cardIds = HashSet<String>()
        for (card in cards) {
            if (!cardIds.add(card.id)) {
                issues += ContentIssue(ContentIssueKind.DUPLICATE_CARD_ID, card.id)
            }
            if (card.tags.any { it !in School.IDS }) {
                issues += ContentIssue(ContentIssueKind.UNKNOWN_SCHOOL_TAG, card.id, card.tags.toString())
            }
            validateEffects(card.id, card.effects, issues, pending)
            if (card.review == ReviewStatus.PENDING_REVIEW) pending += "card:${card.id}"
        }
        for (missing in (expectedCardIds - cardIds).sorted()) {
            issues += ContentIssue(ContentIssueKind.MISSING_CARD, missing)
        }

        // ---------- 装备表 ----------
        val equipmentIds = HashSet<String>()
        for (eq in equipments) {
            if (!equipmentIds.add(eq.id)) {
                issues += ContentIssue(ContentIssueKind.DUPLICATE_EQUIPMENT_ID, eq.id)
            }
            if (eq.id in existingItemIds) {
                issues += ContentIssue(
                    ContentIssueKind.EQUIPMENT_ID_CONFLICT, eq.id,
                    "与既有物品 ID 冲突，应复用现有定义而不是建立别名"
                )
            }
            validateEffects(eq.id, eq.effects, issues, pending)
            if (eq.requiresReview || eq.effects.any { it.requiresReview }) pending += "equipment:${eq.id}"
        }

        // ---------- 配方图（结构正确性，不代表原版核验） ----------
        val itemIds = equipmentIds + existingItemIds
        val recipes = equipments.mapNotNull { it.recipe }
        if (recipes.isNotEmpty()) {
            val graph = RecipeGraphValidator.validate(itemIds, recipes)
            if (!graph.valid) {
                for (issue in graph.issues) {
                    issues += ContentIssue(
                        ContentIssueKind.RECIPE_GRAPH,
                        issue.recipeId,
                        "${issue.kind} @ ${issue.itemId}${if (issue.path.isNotEmpty()) " path=${issue.path}" else ""}"
                    )
                }
            }
        }

        return ContentValidationResult(
            valid = issues.isEmpty(),
            issues = issues,
            pendingReview = pending.sorted()
        )
    }

    // ---------------- 效果校验 ----------------

    private fun validateEffects(
        owner: String,
        effects: List<EffectDefinition>,
        issues: MutableList<ContentIssue>,
        pending: MutableList<String>
    ) {
        val seen = HashSet<String>()
        for (e in effects) {
            if (!seen.add(e.id)) {
                issues += ContentIssue(ContentIssueKind.DUPLICATE_EFFECT_ID, "$owner/${e.id}")
            }
            val contract = EffectContracts.of(e.kind)

            for (key in contract.required) {
                if (key !in e.params) {
                    issues += ContentIssue(
                        ContentIssueKind.EFFECT_MISSING_REQUIRED, "$owner/${e.id}",
                        "${e.kind} 缺少必需参数 $key"
                    )
                }
            }
            val allowed = contract.required + contract.optional
            for (key in e.params.keys) {
                if (key !in allowed) {
                    issues += ContentIssue(
                        ContentIssueKind.EFFECT_UNKNOWN_PARAM, "$owner/${e.id}",
                        "${e.kind} 未登记参数 $key"
                    )
                }
            }
            // 枚举取值：Choice 必须落在候选集内
            for ((key, choices) in contract.enumChoices) {
                val value = e.params[key]
                if (value is EffectValue.Choice && value.value !in choices) {
                    issues += ContentIssue(
                        ContentIssueKind.EFFECT_BAD_ENUM_CHOICE, "$owner/${e.id}",
                        "$key=${value.value} 不在 $choices"
                    )
                }
            }
            if (contract.requiresDamageType && e.damageType == null) {
                issues += ContentIssue(ContentIssueKind.EFFECT_MISSING_DAMAGE_TYPE, "$owner/${e.id}", e.kind.toString())
            }
            if (contract.requiresAttackClass && e.attackClass == null) {
                issues += ContentIssue(ContentIssueKind.EFFECT_MISSING_ATTACK_CLASS, "$owner/${e.id}", e.kind.toString())
            }
            if (contract.requiresShieldKind && e.shieldKind == null) {
                issues += ContentIssue(ContentIssueKind.EFFECT_MISSING_SHIELD_KIND, "$owner/${e.id}", e.kind.toString())
            }
            // 治疗：固定量与比例量至少有一个，否则无法结算
            if (e.kind == EffectKind.HEAL && !e.params.containsKey("amount") && !e.params.containsKey("percent")) {
                issues += ContentIssue(ContentIssueKind.EFFECT_HEAL_NEEDS_AMOUNT, "$owner/${e.id}")
            }
            e.stackDurationMs?.let {
                if (it <= 0) issues += ContentIssue(ContentIssueKind.NEGATIVE_STACK_DURATION, "$owner/${e.id}")
            }
            if (e.requiresReview) pending += "effect:$owner/${e.id}"
        }
    }
}
