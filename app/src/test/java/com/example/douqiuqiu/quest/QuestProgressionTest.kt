package com.example.douqiuqiu.quest

import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.hero.HeroLevelRules
import com.example.douqiuqiu.content.EquipmentCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuestProgressionTest {
    private val materials = listOf("essence", "backpack_fabric", "skill_shard", "safe_lock", "safe_frame", "equipment_guard_core", "skill_guard_core")
    private val rules = InventoryRules((materials.map { ItemDefinition(it, it, ItemKind.MATERIAL, 1, 1, boundOnAcquire = true) } +
        listOf("gauntlets", "circlet", "bracer", "chainmail", "broadsword", "black_king_bar").map { ItemDefinition(it, it, ItemKind.EQUIPMENT, 1, 1) } +
        listOf(ItemDefinition("A1", "A1", ItemKind.CARD, 1, 1), ItemDefinition("recipe_bracer", "卷轴", ItemKind.SCROLL, 1, 1),
            ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, price = 50, shopStock = 5))).associateBy { it.id })
    private val journey = JourneyRules(rules)
    private val box = TargetChest("box", "白", 0, 120, listOf(ChestItem("A1", 0, 0)))
    private fun owned(id: String, def: String, container: Container = Container.WAREHOUSE) = StoredItem(id, def, ItemPlace(container))
    private fun claimedThrough(node: Int) = QuestContent.all.groupBy { it.node }.filterKeys { it <= node }.values.map { it.first().id }.toSet()
    private fun completedState(node: Int) = InventoryState(progress = CampaignProgress(
        claimedQuests = claimedThrough(node), questCounters = QuestContent.all.associate { it.id to it.objective.target }))
    private fun ok(result: InventoryResult): InventoryState { assertTrue(result.error, result.ok); return result.state }
    private fun entered(): InventoryState = ok(journey.enter(InventoryState(progress = CampaignProgress(chests = listOf(box))), box.id, "juggernaut"))

    @Test fun exactlyFortyUniqueTasksInTwentyFourOrderedNodesWithEightChoices() {
        val all = QuestContent.all
        assertEquals(40, all.size); assertEquals(40, all.map { it.id }.distinct().size)
        assertEquals((1..24).toList(), all.map { it.node }.distinct())
        assertEquals(16, all.groupBy { it.node }.count { it.value.size == 1 })
        assertEquals(8, all.groupBy { it.node }.count { it.value.size == 3 })
        assertTrue(all.all { !it.repeatable && it.objective.target > 0 })
        for (group in all.groupBy { it.node }.values) assertEquals(1, group.map { it.rewards }.distinct().size)
        val json = listOf(File("src/main/assets/config/inventory.json"), File("app/src/main/assets/config/inventory.json")).first { it.exists() }.readText()
        val actualIds = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toSet() + EquipmentCatalog.all.map { it.id }
        all.flatMap { it.rewards }.forEach { reward ->
            reward.materialId?.let { assertTrue("Unknown reward $it", it in actualIds) }
            reward.heroId?.let { assertTrue(it, FirstRunContent.heroes.any { hero -> hero.id == it }) }
        }
    }

    @Test fun cannotSkipNodesAndOnlyOneAlternativeCanBeClaimed() {
        var s = completedState(0)
        assertEquals(listOf("journey.1"), QuestTracker.claimable(s).map { it.id })
        assertEquals(s, QuestTracker.claim(s, "journey.13.1", rules).state)
        for (node in 1..12) s = ok(QuestTracker.claim(s, "journey.$node", rules))
        assertEquals(3, QuestTracker.claimable(s).size)
        s = ok(QuestTracker.claim(s, "journey.13.2", rules))
        for (id in listOf("journey.13.1", "journey.13.2", "journey.13.3")) {
            val denied = QuestTracker.claim(s, id, rules); assertFalse(denied.ok); assertEquals(s, denied.state)
        }
        assertEquals(13, QuestProgression.completedNodes(s))
        assertEquals(s, QuestTracker.record(s, QuestEvent.EventChosen)) // sibling is closed; saturated later goals
    }

    @Test fun everyNodeCanBeClaimedWithUniqueRewardInstancesAndNoRepeat() {
        var s = completedState(0)
        for (quests in QuestContent.all.groupBy { it.node }.values) {
            s = ok(QuestTracker.claim(s, quests.last().id, rules))
            assertNull(rules.validate(s))
            assertEquals(s.items.size, s.items.map { it.instanceId }.distinct().size)
            assertEquals(s, QuestTracker.claim(s, quests.last().id, rules).state)
        }
        assertEquals(24, QuestProgression.completedNodes(s))
        assertTrue(s.progress.unlockedHeroes.containsAll(listOf("skywrath", "viper", "phantom_assassin", "necrophos")))
        assertTrue(s.items.any { it.definitionId == "black_king_bar" })
        assertTrue(s.items.filter { it.definitionId in materials }.all { it.bound && it.count == 1 && it.place.container == Container.WAREHOUSE })
    }

    @Test fun ownedHeroUsesCompensationAndKeepsExistingUnlocks() {
        val s = completedState(6).let { it.copy(progress = it.progress.copy(unlockedHeroes = it.progress.unlockedHeroes + "skywrath")) }
        val next = ok(QuestTracker.claim(s, "journey.7", rules))
        assertEquals(s.gold + 500, next.gold)
        assertEquals(s.progress.unlockedHeroes, next.progress.unlockedHeroes)
    }

    @Test fun failedRewardRollsBackGoldHeroItemsCountersAndClaim() {
        val s = completedState(6)
        val missing = InventoryRules(rules.definitions - "backpack_fabric")
        val result = QuestTracker.claim(s, "journey.7", missing)
        assertFalse(result.ok); assertEquals(s, result.state)
        val overflowing = s.copy(gold = Int.MAX_VALUE)
        assertEquals(overflowing, QuestTracker.claim(overflowing, "journey.7", rules).state)
        val full = s.copy(items = (1..10000).map { owned("f$it", "A1") })
        assertEquals(full, QuestTracker.claim(full, "journey.7", rules).state)
        val run = entered()
        assertFalse(QuestTracker.claim(run, "journey.1", rules).ok)
    }

    @Test fun observeMilestonesOnSuccessfulMovesAndKeepThemAfterLoss() {
        val initial = InventoryState(items = listOf(owned("gear", "gauntlets"), owned("card", "A1"), owned("safe", "salve")))
        assertEquals(initial, rules.move(initial, "card", ItemPlace(Container.EQUIPMENT)).state)
        var s = ok(rules.move(initial, "gear", ItemPlace(Container.EQUIPMENT)))
        s = ok(rules.move(s, "card", ItemPlace(Container.SKILLS)))
        s = ok(rules.move(s, "safe", ItemPlace(Container.SAFE)))
        for (id in listOf("journey.1", "journey.2", "journey.3")) assertEquals(1, s.progress.questCounters[id])
        s = QuestTracker.observe(s.copy(items = emptyList()))
        for (id in listOf("journey.1", "journey.2", "journey.3")) assertEquals(1, s.progress.questCounters[id])
    }

    @Test fun everyDifficultyChecksThresholdBeforeChargingAndOldRunsCanContinue() {
        assertEquals(listOf(0, 6, 12, 16, 20, 23), QuestProgression.requiredNodes)
        for (difficulty in 0..5) {
            val need = QuestProgression.requiredNodes[difficulty]
            val chest = box.copy(difficulty = difficulty)
            val ready = InventoryState(progress = CampaignProgress(chests = listOf(chest), claimedQuests = claimedThrough(need)))
            val accepted = ok(journey.enter(ready, chest.id, "juggernaut", 20))
            assertEquals(ready.gold - 140, accepted.gold)
            if (need > 0) {
                val locked = ready.copy(progress = ready.progress.copy(claimedQuests = claimedThrough(need - 1)))
                val result = journey.enter(locked, chest.id, "juggernaut", 20)
                assertFalse(result.ok); assertEquals(locked, result.state)
            }
            val oldRun = accepted.copy(progress = accepted.progress.copy(claimedQuests = emptySet()))
            assertTrue(journey.startBattle(oldRun).ok)
        }
    }

    @Test fun batchHasOnlyUnlockedDifficultiesAndAlwaysOffersSimple() {
        for (nodes in 0..24) {
            val s = InventoryState(progress = CampaignProgress(claimedQuests = claimedThrough(nodes)))
            val batch = journey.ensureBatch(s)
            assertEquals(0, batch.progress.chests.first().difficulty)
            assertEquals((0..QuestProgression.maxDifficulty(s)).toSet(), batch.progress.chests.map { it.difficulty }.toSet())
            assertEquals(batch, journey.ensureBatch(batch)) // displayed boxes never reroll
        }
    }

    @Test fun mixedBackpackAndSafeNeedsKeepSafePityReachableBeforeNodeTwelve() {
        for (nodes in 6..11) {
            val s = InventoryState(progress = CampaignProgress(clears = 1, extracted = true,
                claimedQuests = claimedThrough(nodes),
                materialPity = com.example.douqiuqiu.loot.MaterialPityState(counters = mapOf("safe" to com.example.douqiuqiu.loot.MaterialPityCounter(6, true)))))
            assertTrue(MaterialProgression.eligible(s, rules.definitions).containsAll(listOf("backpack", "safe")))
            val batch = journey.ensureBatch(s)
            assertEquals(ChestTheme.SUPPLIES, batch.progress.chests.first().theme)
            assertEquals(0, batch.progress.chests.first().difficulty)
            assertEquals(setOf(0, 1), batch.progress.chests.map { it.difficulty }.toSet())
            val safe = batch.progress.chests.first { it.theme == ChestTheme.SAFE }
            assertEquals(1, safe.difficulty)
            assertTrue(safe.items.any { it.definitionId == "safe_lock" })
            assertEquals(false, batch.progress.materialPity.counters["safe"]?.pending)
        }
    }

    @Test fun eachChoiceHasReachableAlternativeAndDifficultyGoalNeverRequiresItsOwnUnlock() {
        for ((node, tasks) in QuestContent.all.groupBy { it.node }) {
            val maxDifficulty = QuestProgression.maxDifficulty(InventoryState(progress = CampaignProgress(claimedQuests = claimedThrough(node - 1))))
            tasks.filter { it.objective.type == ObjectiveType.CLEAR_DIFFICULTY }.forEach {
                assertTrue(it.id, it.objective.difficulty <= maxDifficulty)
            }
            if (tasks.size == 3) assertTrue(tasks.any { it.objective.type != ObjectiveType.CLEAR_DIFFICULTY })
        }
    }

    @Test fun winsRecordedOnceAtLootCreationAndNotAgainOnResume() {
        val s = entered()
        assertEquals(1, s.progress.questCounters["journey.4"])
        assertEquals(s, journey.enter(s, box.id, "juggernaut").state)
        val ready = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REWARD)))
        val won = journey.battleLoot(ready)
        assertEquals(1, won.progress.questCounters["journey.5"])
        assertEquals(1, won.progress.questCounters["journey.13.2"])
        assertEquals(won, journey.battleLoot(won))
        assertEquals(s, journey.battleLoot(s))
    }

    @Test fun pickupCountsSuccessfulDropsIncludingBatchButNotPreviouslyOwnedPutDownItems() {
        val s = entered()
        var reward = journey.battleLoot(s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REWARD))))
        val loot = reward.progress.run!!.nodeLoot!!
        val id = loot.items.first().instanceId
        assertEquals(reward, NodeLootRules.collect(reward, rules, id, ItemPlace(Container.BACKPACK)).state)
        reward = reward.copy(progress = reward.progress.copy(run = reward.progress.run!!.copy(nodeLoot = loot.copy(inspectionMs = NodeLootRules.duration(loot, rules.definitions)))))
        assertFalse(NodeLootRules.collect(reward, rules, id, ItemPlace(Container.BACKPACK, 100)).ok)
        val picked = NodeLootRules.collectRevealedIntoBackpack(reward, rules)!!.state
        assertEquals(1, picked.progress.questCounters["journey.6"])
        assertEquals(picked, NodeLootRules.collect(picked, rules, id, ItemPlace(Container.SAFE)).state)
        val down = ok(NodeLootRules.putDown(picked, rules, id))
        val again = ok(NodeLootRules.collect(down, rules, id, ItemPlace(Container.SAFE)))
        assertEquals(picked.progress.questCounters["journey.6"], again.progress.questCounters["journey.6"])
        assertEquals(1, again.progress.questCounters["journey.3"])
    }

    @Test fun extractionAndClearOnlyCountTheirSuccessfulSettlementOnce() {
        val s = entered()
        assertFalse(journey.extract(s).ok)
        val rest = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REST, round = 3)))
        val extracted = ok(journey.extract(rest))
        assertEquals(1, extracted.progress.questCounters["journey.7"])
        assertFalse(journey.extract(extracted).ok)
        assertNull(journey.abandon(s).state.progress.questCounters["journey.7"])
        val last = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REWARD, round = 5)))
        val cleared = ok(LootFlowTestSupport.finishReward(journey, last))
        assertEquals(1, cleared.progress.questCounters["journey.10"])
        assertEquals(1, cleared.progress.questCounters["journey.18"])
        assertNull(cleared.progress.questCounters["journey.7"])
        assertFalse(journey.finishLoot(cleared).ok)
    }

    @Test fun craftingAndGrowthRecordOnlySuccessfulTransactions() {
        val crafting = CraftingRules(rules, listOf(CraftingRecipe("bracer", "bracer", mapOf("gauntlets" to 1, "circlet" to 1, "recipe_bracer" to 1))))
        val s = InventoryState(items = listOf(owned("a", "gauntlets"), owned("b", "circlet"), owned("c", "recipe_bracer")))
        assertEquals(s, crafting.craft(s, "bracer", listOf("a", "b"), "out").state)
        val made = ok(crafting.craft(s, "bracer", listOf("a", "b", "c"), "out"))
        assertEquals(1, made.progress.questCounters["journey.9"])
        assertEquals(made, crafting.craft(made, "bracer", listOf("a", "b", "c"), "out").state)
        val before = InventoryState(items = listOf(owned("f1", "backpack_fabric"), owned("f2", "backpack_fabric")), progress = CampaignProgress(extracted = true))
        val growth = GrowthRules(rules)
        assertFalse(growth.purchase(before.copy(gold = 0), "backpack:1").ok)
        val upgraded = ok(growth.purchase(before, "backpack:1"))
        assertEquals(1, upgraded.progress.questCounters["journey.8"])
        assertEquals(upgraded, growth.purchase(upgraded, "backpack:1").state)
        assertFalse(HeroLevelRules.upgrade(before.copy(gold = 0), "juggernaut", rules).ok)
        assertFalse(HeroLevelRules.upgrade(before, "skywrath", rules).ok)
        assertEquals(2, ok(HeroLevelRules.upgrade(before, "juggernaut", rules)).progress.questCounters["journey.12"])
    }

    @Test fun existingGrowthAndClearsSatisfyMilestonesWithoutFabricatingEvents() {
        val s = InventoryState(backpackWidth = 6, backpackHeight = 4, safeWidth = 3, safeHeight = 3, skillSlots = 8,
            progress = CampaignProgress(clears = 10, heroLevels = mapOf("juggernaut" to 5)))
        val observed = QuestTracker.observe(s)
        for (id in listOf("journey.8", "journey.10", "journey.11", "journey.12", "journey.14", "journey.16", "journey.18", "journey.20"))
            assertEquals(id, QuestContent.byId(id)!!.objective.target, observed.progress.questCounters[id])
        assertNull(observed.progress.questCounters["journey.5"])
        assertNull(observed.progress.questCounters["journey.7"])
        assertEquals(observed, QuestTracker.observe(observed))
    }

    @Test fun eventsOnlyCountSuccessfulChoices() {
        val s = entered()
        assertEquals(s, journey.event(s, "missing").state)
        val event = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.EVENT)))
        val selected = ok(journey.event(event, false))
        assertEquals(1, selected.progress.questCounters["journey.13.3"])
        assertEquals(selected, journey.event(selected, false).state)
    }
    @Test fun shopPurchaseCountsOnlyAcceptedCurrentQuote() {
        val economy = HomeEconomyRules(rules)
        val s = economy.recover(InventoryState(), 1000)
        val revision = s.progress.shop!!.revision
        val stale = economy.buy(s, "salve", "bought", 7_200_000, revision)
        assertFalse(stale.ok); assertEquals(s, stale.state)
        val bought = ok(economy.buy(s, "salve", "bought", 1000, revision))
        assertEquals(1, bought.progress.questCounters["journey.22.3"])
        assertEquals(bought, economy.buy(bought, "salve", "bought", 1000, revision).state)
    }

    @Test fun tutorialCanFundRequiredUpgradesFromSixHundredGoldAndItsOwnRewards() {
        var s = InventoryState(gold = 600, items = listOf(owned("starter-gear", "gauntlets"), owned("starter-card", "A1"), owned("starter-safe", "circlet")))
        fun claim(node: Int) { s = ok(QuestTracker.claim(s, "journey.$node", rules)) }
        s = ok(rules.move(s, "starter-gear", ItemPlace(Container.EQUIPMENT))); claim(1)
        s = ok(rules.move(s, "starter-card", ItemPlace(Container.SKILLS))); claim(2)
        s = ok(rules.move(s, "starter-safe", ItemPlace(Container.SAFE))); claim(3)
        s = s.copy(progress = s.progress.copy(chests = listOf(box)))
        s = ok(journey.enter(s, box.id, "juggernaut"))
        // Exercise the real reward/pickup/settlement path; combat outcome itself is covered separately.
        s = journey.battleLoot(s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REWARD))))
        val loot = s.progress.run!!.nodeLoot!!
        s = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(nodeLoot = loot.copy(inspectionMs = NodeLootRules.duration(loot, rules.definitions)))))
        s = NodeLootRules.collectRevealedIntoBackpack(s, rules)!!.state
        s = ok(journey.finishLoot(s, true))
        s = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REST, round = 3, nodeEvent = null)))
        s = ok(journey.extract(s))
        for (node in 4..7) claim(node)
        assertEquals(1, QuestProgression.maxDifficulty(s))
        s = ok(GrowthRules(rules).purchase(s, "backpack:1")); claim(8)
        val recipe = CraftingRecipe("bracer", "bracer", mapOf("gauntlets" to 1, "circlet" to 1, "recipe_bracer" to 1))
        val parts = recipe.ingredients.keys.map { def -> s.items.first { it.definitionId == def }.instanceId }
        s = ok(CraftingRules(rules, listOf(recipe)).craft(s, recipe.id, parts, "crafted")); claim(9)
        s = s.copy(progress = s.progress.copy(chests = listOf(box)))
        s = ok(journey.enter(s, box.id, "juggernaut"))
        s = s.copy(progress = s.progress.copy(run = s.progress.run!!.copy(stage = JourneyStage.REWARD, round = 5)))
        s = ok(LootFlowTestSupport.finishReward(journey, s)); claim(10)
        s = ok(GrowthRules(rules).purchase(s, "safe:1")); claim(11)
        s = ok(HeroLevelRules.upgrade(s, "juggernaut", rules)); claim(12)
        assertEquals(12, QuestProgression.completedNodes(s))
        assertEquals(2, QuestProgression.maxDifficulty(s))
        assertTrue(s.gold > 0)
        assertTrue(s.progress.unlockedHeroes.containsAll(listOf("skywrath", "viper")))
    }

}
