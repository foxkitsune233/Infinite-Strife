package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.quest.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.tower.TowerLoot
import com.example.douqiuqiu.tower.TowerRunState
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class QuestPersistenceTest {
    @get:Rule val temp = TemporaryFolder()
    private val rules = InventoryRules(mapOf("A1" to ItemDefinition("A1", "卡", ItemKind.CARD, 1, 1)))
    private val box = TargetChest("box", "白", 5, 120, listOf(ChestItem("A1", 0, 0)))
    private fun store(name: String = "profile.dat") = InventoryStore(temp.root.resolve(name), rules) { InventoryState() }

    @Test fun restartAndBackupRetainCountersChoicesAndAlreadyGrantedRewards() {
        val s = InventoryState(progress = CampaignProgress(
            claimedQuests = (1..12).map { "journey.$it" }.toSet(), questCounters = mapOf("journey.13.2" to 20, "journey.13.3" to 10)))
        // Reward definitions are deliberately local: exercise the real transaction and v15 codec.
        val choices = QuestContent.all.map { if (it.node == 13) it.copy(rewards = listOf(QuestReward(RewardType.GOLD, 400))) else it }
        val claimed = QuestTracker.claim(s, "journey.13.2", rules, choices)
        assertTrue(claimed.error, claimed.ok)
        store().save(claimed.state)
        val reopened = store().load().state
        assertEquals(claimed.state, reopened)
        assertEquals(13, QuestProgression.completedNodes(reopened))
        for (id in listOf("journey.13.1", "journey.13.2", "journey.13.3")) {
            assertFalse(QuestTracker.claim(reopened, id, rules, choices).ok)
        }
        assertEquals(reopened, store().readBackup(store().exportBackup()))
        store().save(reopened.copy(gold = reopened.gold + 1))
        temp.root.resolve("profile.dat").writeText("broken")
        val backup = store().load()
        assertTrue(backup.recoveredFromBackup); assertEquals(reopened, backup.state)
        assertFalse(QuestTracker.claim(backup.state, "journey.13.3", rules, choices).ok)
    }

    @Test fun v14HomeMigrationPreservesAllExistingFieldsAndDisplayedChests() {
        val legacy = InventoryState(gold = 2345, backpackWidth = 6, backpackHeight = 4, safeWidth = 3, safeHeight = 3, skillSlots = 8,
            items = listOf(StoredItem("item", "A1", ItemPlace(Container.WAREHOUSE), star = 3, bound = true)),
            progress = CampaignProgress(chests = listOf(box), batch = 12, clears = 15, extracted = true,
                unlockedHeroes = FirstRunContent.heroes.map { it.id }.toSet(), activeHeroId = "skywrath", lockedItems = setOf("item")))
        store().save(legacy.copy(progress = legacy.progress.copy(heroLevels = mapOf("skywrath" to 8), questCounters = mapOf("journey.1" to 1))))
        val bytes = LegacyQuestSaveFixture.envelope(14, LegacyQuestSaveFixture.payloadThrough(store().exportBackup(), 14))
        assertEquals(legacy, store().readBackup(bytes))
        temp.root.resolve("legacy.dat").writeBytes(bytes)
        val loaded = store("legacy.dat").load().state
        assertEquals(legacy, JourneyRules(rules).ensureBatch(loaded))
        store("legacy.dat").save(loaded)
        assertEquals(legacy, store("legacy.dat").load().state)
        val observed = QuestTracker.observe(loaded)
        assertEquals(2, observed.progress.questCounters["journey.20"])
        assertEquals(legacy.gold, observed.gold)
        assertEquals(legacy.items, observed.items)
    }

    @Test fun v14ActiveHighDifficultyBattleResumesWithoutNewQuestGate() {
        val journey = JourneyRules(rules)
        val fighter = journey.fighter(InventoryState(), "juggernaut")
        val enemy = journey.fighter(InventoryState(), "abaddon")
        val old = InventoryState(gold = 777, runGold = 45, phase = InventoryPhase.BATTLE,
            items = listOf(StoredItem("safe", "A1", ItemPlace(Container.SAFE))),
            progress = CampaignProgress(clears = 4, run = JourneyState("run", "juggernaut", box, 1234, fighter.hp, fighter.maxHp,
                stage = JourneyStage.BATTLE, fight = DuelState(fighter, enemy, 1234))))
        store().save(old)
        val bytes = LegacyQuestSaveFixture.envelope(14, LegacyQuestSaveFixture.payloadThrough(store().exportBackup(), 14))
        val loaded = store().readBackup(bytes)
        assertEquals(old, loaded)
        assertEquals(journey.tick(old), journey.tick(loaded))
        assertEquals(1, journey.tick(loaded).progress.run!!.fight!!.tick)
        val rest = loaded.copy(phase = InventoryPhase.PREPARATION, progress = loaded.progress.copy(run = loaded.progress.run!!.copy(stage = JourneyStage.REST, fight = null, round = 3)))
        assertTrue(journey.extract(rest).ok)
    }

    @Test fun v16TowerProgressRoundTripsAndV15SavesFallBackToDefaults() {
        val s = InventoryState(gold = 500, progress = CampaignProgress(
            levelCap = 25, towerHighestFloor = 4,
            towerShard = setOf("juggernaut"), towerAghs = setOf("abaddon"),
            towerRapier = false, pendingRebirthItems = listOf("rebirth:a", "rebirth:b")))
        store().save(s)
        val bytes = store().exportBackup()
        assertEquals(s, store().readBackup(bytes))
        // v15 旧档没有塔字段 → 默认值回退，其余字段保持。
        val legacy = LegacyQuestSaveFixture.envelope(15, LegacyQuestSaveFixture.payloadThrough(bytes, 15))
        val loaded = store().readBackup(legacy)
        assertEquals(10, loaded.progress.levelCap)
        assertEquals(0, loaded.progress.towerHighestFloor)
        assertTrue(loaded.progress.towerShard.isEmpty())
        assertTrue(loaded.progress.towerAghs.isEmpty())
        assertFalse(loaded.progress.towerRapier)
        assertTrue(loaded.progress.pendingRebirthItems.isEmpty())
        assertEquals(s.gold, loaded.gold)
    }

    @Test fun v17TowerRunRoundTripsAndV16SavesDropIt() {
        val s = InventoryState(gold = 500, progress = CampaignProgress(
            towerRun = TowerRunState(floor = 7, heroId = "viper", rng = 99, clearedFloors = 6,
                loot = listOf(TowerLoot("tower_f1", 1, 140)))))
        store().save(s)
        val bytes = store().exportBackup()
        assertEquals(s, store().readBackup(bytes))
        // v16 旧档没有塔轮次字段 → 回退 null，其余字段保持。
        val legacy = LegacyQuestSaveFixture.envelope(16, LegacyQuestSaveFixture.payloadThrough(bytes, 16))
        val loaded = store().readBackup(legacy)
        assertNull(loaded.progress.towerRun)
        assertEquals(s.gold, loaded.gold)
    }

    @Test fun v18PendingRewardPoolRoundTripsAndV17SavesFallBackToDefaults() {
        val s = InventoryState(gold = 500, progress = CampaignProgress(
            towerShard = setOf("juggernaut"),
            pendingShardHeroes = listOf("viper", "viper"), pendingAghanimHeroes = listOf("abaddon")))
        store().save(s)
        val bytes = store().exportBackup()
        assertEquals(s, store().readBackup(bytes))
        // v17 旧档没有待授予池 → 回退空列表，已授予集合保持。
        val legacy = LegacyQuestSaveFixture.envelope(17, LegacyQuestSaveFixture.payloadThrough(bytes, 17))
        val loaded = store().readBackup(legacy)
        assertTrue(loaded.progress.pendingShardHeroes.isEmpty())
        assertTrue(loaded.progress.pendingAghanimHeroes.isEmpty())
        assertEquals(setOf("juggernaut"), loaded.progress.towerShard)
        assertEquals(s.gold, loaded.gold)
    }

    @Test fun v19TowerRunRoundTripsContinuousHealthSupplyAndForgeBonus() {
        val s = InventoryState(gold = 500, progress = CampaignProgress(
            towerRun = TowerRunState(floor = 8, heroId = "viper", rng = 99, clearedFloors = 7,
                loot = listOf(TowerLoot("tower_f7", 1, 500)), currentHp = 412.0,
                pendingSupplyRegen = true, towerPrimaryBonus = 16)))
        store().save(s)
        assertEquals(s, store().readBackup(store().exportBackup()))
    }

    @Test fun malformedCountersAndMultipleChoicesCannotReplaceSave() {
        val store = store()
        val good = InventoryState()
        store.save(good)
        for (progress in listOf(CampaignProgress(questCounters = mapOf("journey.1" to -1)),
            CampaignProgress(claimedQuests = setOf("journey.13.1", "journey.13.2")))) {
            assertThrows(IllegalArgumentException::class.java) { store.save(good.copy(progress = progress)) }
            assertEquals(good, store.load().state)
        }
    }
}
