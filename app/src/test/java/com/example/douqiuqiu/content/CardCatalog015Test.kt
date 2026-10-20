package com.example.douqiuqiu.content

import com.example.douqiuqiu.cards.SynergyCard
import com.example.douqiuqiu.cards.SynergyCounter
import org.junit.Assert.*
import org.junit.Test

/**
 * 0.1.5 批次 P2：怒火（rage）/ 寒霜（frost）流派与卡池扩充的行为测试（H01-B 续）。
 *
 * 仅覆盖数据层：流派枚举、卡定义、羁绊档位、校验器。新增效果（SLOW/FREEZE/FROST_DOT/
 * VULNERABILITY）的战斗结算属于 P3，本批不触发战斗。
 */
class CardCatalog015Test {

    // ---------------- 流派枚举 ----------------

    @Test fun p2_schoolsTotalEight() {
        assertEquals(8, School.entries.size)
        assertTrue(School.IDS.containsAll(listOf("rage", "frost")))
        assertNotNull(School.byId("rage"))
        assertNotNull(School.byId("frost"))
    }

    // ---------------- 新单流派卡 ----------------

    @Test fun p2_rageSingleCardsRegistered() {
        for (id in listOf("RG1", "RG2", "RG3", "RG4")) {
            val card = CardCatalog.byId.getValue(id)
            assertEquals("怒火单流派卡应为单标签", 1, card.tags.size)
            assertEquals(setOf(School.RAGE.id), card.tags)
        }
    }

    @Test fun p2_frostSingleCardsRegistered() {
        for (id in listOf("FS1", "FS2", "FS3", "FS4")) {
            val card = CardCatalog.byId.getValue(id)
            assertEquals("寒霜单流派卡应为单标签", 1, card.tags.size)
            assertEquals(setOf(School.FROST.id), card.tags)
        }
    }

    @Test fun p2_rageCardsUseOnlyTypedEffectsNotStrings() {
        // 怒火四卡不得依赖任何未登记/中文描述驱动的效果；所有 kind 必须落在已登记契约内
        for (id in listOf("RG1", "RG2", "RG3", "RG4")) {
            for (e in CardCatalog.byId.getValue(id).effects) {
                assertNotNull("${id}/${e.id} 效果种类应有参数契约", EffectContracts.of(e.kind))
            }
        }
    }

    @Test fun p2_frostCardsReferenceNewEffectKinds() {
        val fs = listOf("FS1", "FS3", "FS4").map { CardCatalog.byId.getValue(it) }
        val kinds = fs.flatMap { it.effects }.map { it.kind }.toSet()
        assertTrue("寒霜卡应引用 SLOW/FREEZE/FROST_DOT/VULNERABILITY",
            kinds.containsAll(listOf(EffectKind.SLOW, EffectKind.FREEZE, EffectKind.FROST_DOT, EffectKind.VULNERABILITY)))
    }

    // ---------------- 新双流派卡 ----------------

    @Test fun p2_newDualCardsHaveExpectedTags() {
        val expected = mapOf(
            "D7" to setOf("basic", "rage"),
            "D8" to setOf("ultimate", "frost"),
            "D9" to setOf("crit", "rage"),
            "D10" to setOf("shield", "frost"),
            "D11" to setOf("restore", "rage"),
            "D12" to setOf("poison", "frost"),
            "D13" to setOf("rage", "frost")
        )
        for ((id, tags) in expected) {
            val card = CardCatalog.byId.getValue(id)
            assertEquals("$id 应为双流派", 2, card.tags.size)
            assertEquals("$id 标签应为 $tags", tags, card.tags)
            assertEquals("$id 双流派卡应为 1x2", 1 to 2, card.width to card.height)
        }
    }

    @Test fun p2_dualCardsContributeToBothSchoolsInCounter() {
        val cards = CardCatalog.all
            .filter { it.id in setOf("D7", "D8", "D9", "D10", "D11", "D12", "D13") }
            .map { SynergyCard(instanceId = "${it.id}#1", cardId = it.id, tags = it.tags, star = 1, active = true) }
        val result = SynergyCounter.count(cards, CardCatalog.synergyThresholds)
        for (school in listOf("rage", "frost")) {
            val s = result.singleOrNull { it.schoolId == school }
            assertNotNull("$school 应有羁绊计数", s)
            assertTrue("$school 应计到新双流派卡", s!!.uniqueCardIds.isNotEmpty())
        }
    }

    // ---------------- 羁绊档位 ----------------

    @Test fun p2_rageAndFrostHaveThreeTiersEach() {
        for (school in listOf("rage", "frost")) {
            val tiers = CardCatalog.synergyTiers.filter { it.schoolId == school }.map { it.tier }
            assertEquals("$school 应有 2/3/4 档", listOf(2, 3, 4), tiers.sorted())
        }
    }

    @Test fun p2_synergyTiersUseTypedEffects() {
        for (t in CardCatalog.synergyTiers.filter { it.schoolId in setOf("rage", "frost") }) {
            for (e in t.effects) assertNotNull("${t.schoolId}.${t.tier} 效果应有契约", EffectContracts.of(e.kind))
        }
    }

    // ---------------- 校验器 ----------------

    @Test fun p2_fullCatalogPassesValidator() {
        val result = ContentValidator.validate(CardCatalog.all, EquipmentCatalog.all, CardCatalogTest.EXISTING_ITEM_IDS)
        val cardIssues = result.issues.filter { it.source.startsWith("A") || it.source.startsWith("U") ||
            it.source.startsWith("C") || it.source.startsWith("S") || it.source.startsWith("R") ||
            it.source.startsWith("D") || it.source.startsWith("F") }
        assertTrue("新卡表问题: $cardIssues", cardIssues.isEmpty())
        // 新效果种类（SLOW/FREEZE/FROST_DOT/VULNERABILITY）尚未接入战斗结算，但不应进入待审清单
        assertTrue(result.pendingReview.isEmpty())
    }
}
