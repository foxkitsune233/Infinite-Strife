package com.example.douqiuqiu.content

import org.junit.Assert.*
import org.junit.Test

/** 效果模型契约的行为测试（H01-A）。 */
class ContentModelTest {

    // ---------------- 契约覆盖 ----------------

    @Test fun m01_everyEffectKindHasAContract() {
        for (kind in EffectKind.entries) {
            assertNotNull("${kind} 缺少参数契约", EffectContracts.ALL[kind])
        }
    }

    @Test fun m02_missingRequiredParamIsRejected() {
        val e = EffectDefinition(
            id = "bad", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
            params = mapOf("chance" to EffectValue.n(0.2)) // 缺 multiplier
        )
        val single = ContentValidator.validate(
            cards = listOf(
                CardDefinition(
                    id = "X", name = "x", quality = QualityTier.BLUE, tags = setOf("basic"),
                    width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 1,
                    effects = listOf(e)
                )
            ),
            equipments = emptyList(),
            expectedCardIds = setOf("X")
        )
        assertFalse(single.valid)
        assertTrue(
            single.issues.any { it.kind == ContentIssueKind.EFFECT_MISSING_REQUIRED && it.detail.contains("multiplier") }
        )
    }

    @Test fun m03_unknownParamIsRejected() {
        val result = validateCardEffect(
            EffectDefinition(
                id = "bad", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.2), "multiplier" to EffectValue.n(1.5), "critPower" to EffectValue.n(9))
            )
        )
        assertFalse(result.valid)
        assertTrue(result.issues.any { it.kind == ContentIssueKind.EFFECT_UNKNOWN_PARAM })
    }

    @Test fun m04_badEnumChoiceIsRejected() {
        val result = validateCardEffect(
            EffectDefinition(
                id = "bad", kind = EffectKind.DISPEL, trigger = EffectTrigger.ACTIVE,
                params = mapOf("level" to EffectValue.e("WEAK"))
            )
        )
        assertFalse(result.valid)
        assertTrue(result.issues.any { it.kind == ContentIssueKind.EFFECT_BAD_ENUM_CHOICE })
    }

    @Test fun m05_validEnumChoicePasses() {
        val result = validateCardEffect(
            EffectDefinition(
                id = "ok", kind = EffectKind.DISPEL, trigger = EffectTrigger.ACTIVE,
                params = mapOf("level" to EffectValue.e("BASIC"))
            )
        )
        assertFalse(result.issues.any { it.kind == ContentIssueKind.EFFECT_BAD_ENUM_CHOICE })
    }

    @Test fun m06_damageEffectNeedsTypeAndClass() {
        val noType = validateCardEffect(
            EffectDefinition(
                id = "noType", kind = EffectKind.ADD_SPELL_DAMAGE, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(10))
            )
        )
        assertTrue(noType.issues.any { it.kind == ContentIssueKind.EFFECT_MISSING_DAMAGE_TYPE })
        assertTrue(noType.issues.any { it.kind == ContentIssueKind.EFFECT_MISSING_ATTACK_CLASS })

        val ok = validateCardEffect(
            EffectDefinition(
                id = "ok", kind = EffectKind.ADD_SPELL_DAMAGE, trigger = EffectTrigger.ATTACK_LAND,
                params = mapOf("amount" to EffectValue.n(10)),
                damageType = DamageType.MAGICAL, attackClass = AttackClass.SPELL
            )
        )
        assertFalse(ok.issues.any { it.kind == ContentIssueKind.EFFECT_MISSING_DAMAGE_TYPE })
        assertFalse(ok.issues.any { it.kind == ContentIssueKind.EFFECT_MISSING_ATTACK_CLASS })
    }

    @Test fun m07_shieldEffectNeedsShieldKind() {
        val result = validateCardEffect(
            EffectDefinition(
                id = "noKind", kind = EffectKind.GRANT_SHIELD, trigger = EffectTrigger.BATTLE_START,
                params = mapOf("amount" to EffectValue.n(60))
            )
        )
        assertTrue(result.issues.any { it.kind == ContentIssueKind.EFFECT_MISSING_SHIELD_KIND })
    }

    @Test fun m08_healNeedsAmountOrPercent() {
        val neither = validateCardEffect(
            EffectDefinition(
                id = "empty", kind = EffectKind.HEAL, trigger = EffectTrigger.INTERVAL,
                params = mapOf("thresholdPercent" to EffectValue.n(0.3))
            )
        )
        assertTrue(neither.issues.any { it.kind == ContentIssueKind.EFFECT_HEAL_NEEDS_AMOUNT })

        val byPercent = validateCardEffect(
            EffectDefinition(
                id = "pct", kind = EffectKind.HEAL, trigger = EffectTrigger.POISON_TICK,
                params = mapOf("percent" to EffectValue.n(0.1))
            )
        )
        assertFalse(byPercent.issues.any { it.kind == ContentIssueKind.EFFECT_HEAL_NEEDS_AMOUNT })
    }

    // ---------------- 定义自身的约束 ----------------

    @Test fun m09_perStarMustCoverAllThreeStars() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectDefinition(
                id = "bad", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
                params = mapOf("chance" to EffectValue.n(0.1), "multiplier" to EffectValue.n(1.5)),
                perStar = mapOf(1 to mapOf("chance" to EffectValue.n(0.1), "multiplier" to EffectValue.n(1.5))),
                scaling = StarScaling.PER_STAR
            )
        }
    }

    @Test fun m10_nonPositiveIntervalIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectDefinition(
                id = "bad", kind = EffectKind.HEAL, trigger = EffectTrigger.INTERVAL,
                params = mapOf("amount" to EffectValue.n(10)), intervalMs = 0L
            )
        }
    }

    @Test fun m11_blankIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectDefinition(
                id = " ", kind = EffectKind.HEAL, trigger = EffectTrigger.INTERVAL,
                params = mapOf("amount" to EffectValue.n(10))
            )
        }
    }

    @Test fun m12_paramsAtFallsBackToOneStarParamsWhenFlat() {
        val e = EffectDefinition(
            id = "e", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
            params = mapOf("attackSpeed" to EffectValue.n(12)),
            scaling = StarScaling.FLAT
        )
        assertEquals(12.0, (e.paramsAt(3)["attackSpeed"] as EffectValue.Num).value, 1e-9)
    }

    @Test fun m16_linearScalingActuallyMultiplies() {
        val e = EffectDefinition(
            id = "e", kind = EffectKind.STAT_BONUS, trigger = EffectTrigger.PASSIVE,
            params = mapOf("attackSpeed" to EffectValue.n(12)),
            scaling = StarScaling.LINEAR_1_2_4
        )
        assertEquals(12.0, (e.paramsAt(1)["attackSpeed"] as EffectValue.Num).value, 1e-9)
        assertEquals(24.0, (e.paramsAt(2)["attackSpeed"] as EffectValue.Num).value, 1e-9)
        assertEquals(48.0, (e.paramsAt(3)["attackSpeed"] as EffectValue.Num).value, 1e-9)
    }

    @Test fun m17_linearScalingLeavesThresholdsAndCountsAlone() {
        val e = EffectDefinition(
            id = "e", kind = EffectKind.ATTACK_SPEED_STACK, trigger = EffectTrigger.ATTACK_COUNT,
            params = mapOf("amount" to EffectValue.n(15), "everyN" to EffectValue.c(4)),
            scaling = StarScaling.LINEAR_1_2_4,
            maxStacks = 3, stackDurationMs = 4000L
        )
        assertEquals(60.0, (e.paramsAt(3)["amount"] as EffectValue.Num).value, 1e-9)
        assertEquals("层数条件不随星级放大", 4, (e.paramsAt(3)["everyN"] as EffectValue.Count).value)
        assertEquals("层数上限不随星级放大", 3, e.maxStacks)
        assertEquals("单层时长不随星级放大", 4000L, e.stackDurationMs)
    }

    @Test fun m13_paramsAtHonoursPerStarOverride() {
        val e = EffectDefinition(
            id = "e", kind = EffectKind.CRIT_SOURCE, trigger = EffectTrigger.PASSIVE,
            params = mapOf("chance" to EffectValue.n(0.12), "multiplier" to EffectValue.n(1.6)),
            perStar = mapOf(
                1 to mapOf("chance" to EffectValue.n(0.12), "multiplier" to EffectValue.n(1.6)),
                2 to mapOf("chance" to EffectValue.n(0.18), "multiplier" to EffectValue.n(1.6)),
                3 to mapOf("chance" to EffectValue.n(0.24), "multiplier" to EffectValue.n(1.6))
            ),
            scaling = StarScaling.PER_STAR
        )
        assertEquals(0.24, (e.paramsAt(3)["chance"] as EffectValue.Num).value, 1e-9)
        assertEquals(0.12, (e.paramsAt(1)["chance"] as EffectValue.Num).value, 1e-9)
    }

    // ---------------- 标记不默认全开 ----------------

    @Test fun m14_effectFlagsDefaultToConservativeValues() {
        val f = EffectFlags()
        assertFalse("默认不可暴击", f.canCrit)
        assertFalse("默认不可吸血", f.canLifesteal)
        assertFalse("默认不触发命中效果", f.triggersOnHit)
        assertFalse("默认不是反射", f.isReflect)
        assertFalse("默认不是自损", f.isSelfDamage)
        assertFalse("默认不穿透普攻保护", f.piercesAttackProtection)
        assertFalse("默认不计入攻击次数", f.countsAsAttack)
        assertFalse("默认不触发二级效果", f.canTriggerSecondary)
    }

    @Test fun m15_typedValuesDoNotAcceptNumericStrings() {
        // 数值只能走 Num/Count，Choice 只承载枚举名；用 Choice 装数字会被枚举校验拦下
        val result = validateCardEffect(
            EffectDefinition(
                id = "bad", kind = EffectKind.DISPEL, trigger = EffectTrigger.ACTIVE,
                params = mapOf("level" to EffectValue.e("3"))
            )
        )
        assertTrue(result.issues.any { it.kind == ContentIssueKind.EFFECT_BAD_ENUM_CHOICE })
    }

    // ---------------- 辅助 ----------------

    private fun validateCardEffect(effect: EffectDefinition): ContentValidationResult =
        ContentValidator.validate(
            cards = listOf(
                CardDefinition(
                    id = "T", name = "t", quality = QualityTier.BLUE, tags = setOf("basic"),
                    width = 1, height = 1, price = 100, shopShelf = "ROTATING", shopStock = 1,
                    effects = listOf(effect)
                )
            ),
            equipments = emptyList(),
            expectedCardIds = setOf("T")
        )
}
