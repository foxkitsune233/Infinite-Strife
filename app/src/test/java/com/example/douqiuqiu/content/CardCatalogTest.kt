package com.example.douqiuqiu.content

import com.example.douqiuqiu.cards.SynergyCard
import com.example.douqiuqiu.cards.SynergyCounter
import org.junit.Assert.*
import org.junit.Test

/** 30 张卡内容表与羁绊档位的行为测试（H01-B）。 */
class CardCatalogTest {

    // ---------------- 规模与标识 ----------------

    @Test fun c01_exactlyThirtyCardsWithExpectedIds() {
        assertEquals(45, CardCatalog.all.size)
        assertEquals(ContentValidator.EXPECTED_CARD_IDS, CardCatalog.all.map { it.id }.toSet())
    }

    @Test fun c02_noDuplicateCardIds() {
        val ids = CardCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun c03_tagsAreOneOrTwoValidSchools() {
        for (card in CardCatalog.all) {
            assertTrue("${card.id} 标签数应为 1~2", card.tags.size in 1..2)
            assertTrue("${card.id} 含未知流派", card.tags.all { it in School.IDS })
        }
    }

    @Test fun c04_dualSchoolCardsAreOneByTwo() {
        val dual = CardCatalog.all.filter { it.isDualSchool }
        assertEquals(setOf("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9", "D10", "D11", "D12", "D13"),
            dual.map { it.id }.toSet())
        for (card in dual) {
            assertEquals("${card.id} 双流派卡应为 1x2", 1 to 2, card.width to card.height)
        }
    }

    // ---------------- 待审隔离 ----------------

    @Test fun c05_poisonCardsWereApprovedByUser() {
        val pending = CardCatalog.pendingReview.map { it.id }.toSet()
        assertTrue(pending.isEmpty())
    }

    @Test fun c06_confirmedOnlyExcludesPendingCards() {
        val confirmed = CardCatalog.confirmedOnly.map { it.id }.toSet()
        assertEquals(45, confirmed.size)
        for (id in CardCatalog.pendingReview.map { it.id }) {
            assertFalse("$id 是待审卡，不应进入 confirmedOnly", id in confirmed)
        }
    }

    @Test fun c07_approvedPoisonEffectsHaveNoReviewGate() {
        for (id in setOf("P1", "P2", "P3", "P4", "D1", "D5")) {
            val card = CardCatalog.byId.getValue(id)
            assertTrue("$id 已批准，效果不再被隔离", card.effects.none { it.requiresReview })
        }
    }

    @Test fun c08_validatorReportsPendingAsListNotAsIssue() {
        val result = ContentValidator.validate(CardCatalog.all, EquipmentCatalog.all)
        // 待审不是错误，但必须出现在待审清单里
        assertFalse(result.pendingReview.any { it.startsWith("card:") })
        assertFalse("待审不应导致校验失败", result.issues.any { it.source.startsWith("P1") })
    }

    // ---------------- 星级参数 ----------------

    @Test fun c09_u3FixedCooldownReductionIsPerStar() {
        val cd = CardCatalog.byId.getValue("U3").effects.single { it.kind == EffectKind.COOLDOWN_REDUCTION }
        assertEquals(StarScaling.PER_STAR, cd.scaling)
        assertEquals(0.5, num(cd.paramsAt(1), "flatSeconds"), 1e-9)
        assertEquals(1.0, num(cd.paramsAt(2), "flatSeconds"), 1e-9)
        assertEquals(2.0, num(cd.paramsAt(3), "flatSeconds"), 1e-9)
    }

    @Test fun c10_c1CritChanceScalesButMultiplierDoesNot() {
        val crit = CardCatalog.byId.getValue("C1").effects.single { it.kind == EffectKind.CRIT_SOURCE }
        assertEquals(0.12, num(crit.paramsAt(1), "chance"), 1e-9)
        assertEquals(0.18, num(crit.paramsAt(2), "chance"), 1e-9)
        assertEquals(0.24, num(crit.paramsAt(3), "chance"), 1e-9)
        assertEquals(1.60, num(crit.paramsAt(3), "multiplier"), 1e-9)
    }

    @Test fun c11_linearScalingCardTriplesAtThreeStars() {
        val a1 = CardCatalog.byId.getValue("A1").effects.single()
        assertEquals(12.0, num(a1.paramsAt(1), "attackSpeed"), 1e-9)
        assertEquals(24.0, (a1.paramsAt(2)["attackSpeed"] as EffectValue.Num).value, 1e-9)
    }

    @Test fun c12_a4StacksAreIndependentAndCapped() {
        val stack = CardCatalog.byId.getValue("A4").effects.single()
        assertEquals(3, stack.maxStacks)
        assertEquals(4000L, stack.stackDurationMs)
        assertEquals(4, (stack.params["everyN"] as EffectValue.Count).value)
        assertEquals(EffectTrigger.ATTACK_COUNT, stack.trigger)
    }

    @Test fun c13_s4ConsumeRatioIsFixedWhileMultiplierScales() {
        val s4 = CardCatalog.byId.getValue("S4").effects.single()
        for (star in 1..3) {
            assertEquals(0.30, num(s4.paramsAt(star), "consumePercent"), 1e-9)
        }
        assertEquals(1.20, num(s4.paramsAt(1), "damageMultiplier"), 1e-9)
        assertEquals(1.80, num(s4.paramsAt(2), "damageMultiplier"), 1e-9)
        assertEquals(2.40, num(s4.paramsAt(3), "damageMultiplier"), 1e-9)
    }

    @Test fun c14_u4FreeUltIsUniqueGrouped() {
        val u4 = CardCatalog.byId.getValue("U4").effects.single()
        assertEquals(EffectKind.FREE_ULT_CAST, u4.kind)
        assertEquals("ult_free_cast", u4.uniqueGroup)
        assertEquals(50.0, num(u4.params, "firstCastPenaltyPercent"), 1e-9)
    }

    // ---------------- 羁绊 ----------------

    @Test fun c15_sixSchoolsHaveThreeTiersEach() {
        val tiers = CardCatalog.synergyTiers
        assertEquals(24, tiers.size)
        for (school in School.entries) {
            val forSchool = tiers.filter { it.schoolId == school.id }.map { it.tier }
            assertEquals("${school.id} 应有 2/3/4 档", listOf(2, 3, 4), forSchool.sorted())
        }
    }

    @Test fun c16_poisonSynergyTiersAreApproved() {
        val poison = CardCatalog.synergyTiers.filter { it.schoolId == School.POISON.id }
        assertEquals(3, poison.size)
        for (t in poison) {
            assertTrue("中毒 ${t.tier} 档已批准", t.effects.none { it.requiresReview })
        }
    }

    @Test fun c17_basicAttackTier4DoesNotRecurseIntoAttackCount() {
        val tier4 = CardCatalog.synergyTiers.single { it.schoolId == School.BASIC_ATTACK.id && it.tier == 4 }
        val extra = tier4.effects.single { it.kind == EffectKind.EXTRA_ATTACK }
        assertFalse("追加攻击不得计入每N次攻击，否则递归", extra.flags.countsAsAttack)
        assertFalse(extra.flags.triggersOnHit)
        assertEquals(5, (extra.params["everyN"] as EffectValue.Count).value)
    }

    // ---------------- 与既有计数器的兼容 ----------------

    @Test fun c18_catalogTagsWorkWithSynergyCounter() {
        val cards = CardCatalog.all.map { c ->
            SynergyCard(instanceId = "${c.id}#1", cardId = c.id, tags = c.tags, star = 1, active = true)
        }
        val result = SynergyCounter.count(cards, CardCatalog.synergyThresholds)
        val basic = result.single { it.schoolId == School.BASIC_ATTACK.id }
        // 普攻：A1~A4（4 种）+ D1/D2（双标签）+ D7（basic+rage 双标签）共 7 种
        assertEquals(setOf("A1", "A2", "A3", "A4", "D1", "D2", "D7"), basic.uniqueCardIds)
        assertEquals(listOf(2, 3, 4), basic.activeTiers)
    }

    @Test fun c19_duplicateStarsStillCountAsOneSchoolCard() {
        val cards = listOf(
            SynergyCard("A1#1", "A1", setOf("basic"), 1),
            SynergyCard("A1#2", "A1", setOf("basic"), 3)
        )
        val result = SynergyCounter.count(cards, CardCatalog.synergyThresholds)
        assertEquals(1, result.single().uniqueCardIds.size)
        assertTrue(result.single().activeTiers.isEmpty())
    }

    @Test fun c20_fullCardCatalogPassesValidator() {
        val result = ContentValidator.validate(CardCatalog.all, EquipmentCatalog.all, EXISTING_ITEM_IDS)
        val cardIssues = result.issues.filter { it.source.startsWith("A") || it.source.startsWith("U") ||
            it.source.startsWith("C") || it.source.startsWith("S") || it.source.startsWith("R") ||
            it.source.startsWith("D") || it.source.startsWith("F") }
        assertTrue("卡表问题: $cardIssues", cardIssues.isEmpty())
    }

    // ---------------- 辅助 ----------------

    private fun num(params: Map<String, EffectValue>, key: String): Double =
        (params[key] as EffectValue.Num).value

    companion object {
        /** inventory.json 当前已存在的物品 ID（用于查重，不在内容表重复建立别名）。 */
        val EXISTING_ITEM_IDS: Set<String> = setOf(
            "A1", "A2", "S1", "R1",
            "iron_branch", "gauntlets", "slippers", "mantle", "circlet", "broadsword",
            "bracer", "wraith_band", "null_talisman",
            "recipe_bracer", "recipe_wraith_band", "recipe_null_talisman",
            "salve", "tango", "faerie_fire", "cheese",
            "essence", "backpack_fabric", "skill_shard", "safe_lock", "safe_frame"
        )
    }
}
