package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.QualityTier
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

class EnemyBuildsTest {
    private val definitions = (CardCombat.enabledCards.map {
        ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label)
    } + listOf(
        ItemDefinition("iron_branch", "枝干", ItemKind.EQUIPMENT, 1, 1, "白", price = 20),
        ItemDefinition("gauntlets", "手套", ItemKind.EQUIPMENT, 1, 1, "白", price = 50),
        ItemDefinition("bracer", "护腕", ItemKind.EQUIPMENT, 1, 1, "蓝", price = 180),
        ItemDefinition("unfinished", "未实现装备", ItemKind.EQUIPMENT, 1, 1, "金"),
        ItemDefinition("unknown_card", "未实现卡", ItemKind.CARD, 1, 1)
    )).associateBy { it.id }

    private fun run(round: Int = 1, difficulty: Int = 0, seed: Long = 1729) = JourneyState(
        "fixture", "juggernaut", TargetChest("chest", "蓝", difficulty, 60, emptyList()),
        seed, 1000.0, 1000.0, round
    )

    @Test fun previewIsStableWithReorderedCatalogAndDoesNotConsumeRunRandomness() {
        val source = run(5, 2)
        val before = source.copy()
        val preview = EnemyBuilds.preview(source, definitions)
        assertEquals(preview, EnemyBuilds.preview(source.copy(), definitions.entries.reversed().associate { it.toPair() }))
        assertEquals(before, source)
        assertNotEquals(source.heroId, preview.heroId)
    }

    @Test fun firstBattleNeverReceivesAdvancedCardsOrFullLoadout() {
        for (difficulty in 0..2) for (seed in 1L..40L) {
            val build = EnemyBuilds.preview(run(1, difficulty, seed), definitions)
            val cards = build.inventory.items.filter { it.place.container == Container.SKILLS }
            val cardBudget = if (ChallengeBalance.enhanced(run(1, difficulty, seed))) ChallengeBalance.budget(run(1, difficulty, seed)).cards else 2
            assertTrue("difficulty=$difficulty seed=$seed cards=${cards.size} budget=$cardBudget", cards.size in 1..cardBudget)
            assertTrue(cards.all { it.star == 1 && CardCombat.definitions.getValue(it.definitionId).quality == QualityTier.BLUE })
            // v4 首战预算可能补入非本流派的蓝卡，只要求主流派有牌。
            assertTrue(cards.any { build.schoolId in CardCombat.definitions.getValue(it.definitionId).tags })
            // v4 增强难度会提升首战装备预算；断言“不超过预算且至少一件”，不再写死 1。
            val gearCount = build.inventory.items.count { it.place.container == Container.EQUIPMENT }
            val budget = if (ChallengeBalance.enhanced(run(1, difficulty, seed))) ChallengeBalance.budget(run(1, difficulty, seed)).gear else 1
            assertTrue("difficulty=$difficulty seed=$seed gear=$gearCount budget=$budget", gearCount in 1..budget)
        }
    }

    @Test fun allRoundsRespectLiveContentAndSlotLimitsWithoutGrantingPlayerProgress() {
        for (difficulty in 0..2) for (round in 1..5) for (seed in 1L..20L) {
            val build = EnemyBuilds.preview(run(round, difficulty, seed), definitions)
            assertNull(InventoryRules(definitions).validate(build.inventory))
            assertEquals(0, build.inventory.gold)
            assertEquals(0, build.inventory.progress.clears)
            assertTrue(build.inventory.items.none { it.definitionId in setOf("unfinished", "unknown_card") })
            assertTrue(build.inventory.items.all { it.definitionId in definitions })
            assertTrue(build.inventory.items.filter { it.place.container == Container.EQUIPMENT }.all { it.place.x < 6 })
            assertTrue(build.inventory.items.count { it.place.container == Container.SKILLS } <= 8)
        }
    }

    @Test fun sparseCatalogFallsBackToAvailableSchoolAndNeverInventsItems() {
        val sparse = definitions.filterKeys { it == "A1" || it == "gauntlets" }
        val build = EnemyBuilds.preview(run(5, 2), sparse)
        assertEquals("basic", build.schoolId)
        assertEquals(setOf("A1", "gauntlets"), build.inventory.items.map { it.definitionId }.toSet())
        assertNull(InventoryRules(sparse).validate(build.inventory))
        assertTrue(EnemyBuilds.preview(run(), emptyMap()).inventory.items.isEmpty())
    }

    @Test fun laterRoundsIncreaseBuildComplexity() {
        val first = EnemyBuilds.preview(run(1, 2), definitions).inventory
        val last = EnemyBuilds.preview(run(5, 2), definitions).inventory
        assertTrue(last.items.count { it.place.container == Container.SKILLS } > first.items.count { it.place.container == Container.SKILLS })
        assertTrue(last.items.any { it.star == 2 })
    }
}
