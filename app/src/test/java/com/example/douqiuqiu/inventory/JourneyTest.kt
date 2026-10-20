package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

class JourneyTest {
    private val inventory = InventoryRules(listOf(
        ItemDefinition("A1", "迅击训练", ItemKind.CARD, 1, 1),
        ItemDefinition("gauntlets", "力量手套", ItemKind.EQUIPMENT, 1, 1, price = 60, shopStock = 3)
    ).associateBy { it.id })
    private val rules = JourneyRules(inventory)
    private fun prepared(): InventoryState = rules.ensureBatch(InventoryState(items = listOf(
        StoredItem("safe", "A1", ItemPlace(Container.SAFE)),
        StoredItem("gear", "gauntlets", ItemPlace(Container.EQUIPMENT)))))
    private fun entered(): InventoryState {
        val s = prepared()
        return rules.enter(s, s.progress.chests.first().id, "juggernaut").state
    }

    @Test fun enteringPaysOnceLocksTargetAndPreservesWarehouse() {
        val s = prepared()
        val chest = s.progress.chests.first()
        val entered = rules.enter(s, chest.id, "juggernaut")
        assertTrue(entered.ok)
        assertEquals(s.gold - chest.ticket, entered.state.gold)
        assertEquals(chest, entered.state.progress.run!!.target)
        assertTrue(entered.state.progress.chests.isEmpty())
        assertFalse(rules.enter(entered.state, chest.id, "juggernaut").ok)
        assertFalse(rules.enter(s.copy(gold = 0), chest.id, "juggernaut").ok)
    }
    @Test fun appraisalOnlyRevealsAndNeverChangesContents() {
        val s = prepared()
        val first = s.progress.chests.first()
        val result = rules.appraise(s, first.id)
        assertTrue(result.ok)
        val after = result.state.progress.chests.first()
        assertEquals(first.items.map { it.definitionId }, after.items.map { it.definitionId })
        assertEquals(first.items.map { it.x to it.y }, after.items.map { it.x to it.y })
        assertTrue(after.ticket > first.ticket)
        assertEquals(s.gold - 10, result.state.gold)
    }
    @Test fun savedBattleAdvancesDeterministicallyAndCannotBeStartedAgain() {
        val started = rules.startBattle(entered()).state
        assertEquals(JourneyStage.BATTLE, started.progress.run!!.stage)
        assertFalse(rules.startBattle(started).ok)
        val a = rules.tick(started)
        val b = rules.tick(started)
        assertEquals(a, b)
        assertEquals(1, a.progress.run!!.fight!!.tick)
    }
    @Test fun extractionAllowedOnlyAtThirdBattleRestAndKeepsGoldExactlyOnce() {
        val entered = entered()
        assertFalse(rules.extract(entered).ok)
        val atRest = entered.copy(runGold = 123, progress = entered.progress.copy(run = entered.progress.run!!.copy(stage = JourneyStage.REST, round = 3)))
        val result = rules.extract(atRest)
        assertTrue(result.ok)
        assertNull(result.state.progress.run)
        assertEquals(atRest.gold + 123, result.state.gold)
        assertTrue(result.state.items.all { it.place.container == Container.WAREHOUSE })
        assertFalse(rules.extract(result.state).ok)
    }
    @Test fun fifthRewardBanksFixedChestWithoutBackpackCapacityLimit() {
        val entered = entered()
        val ready = entered.copy(progress = entered.progress.copy(run = entered.progress.run!!.copy(stage = JourneyStage.REWARD, round = 5)))
        val result = LootFlowTestSupport.finishReward(rules, ready)
        assertTrue(result.ok)
        assertNull(result.state.progress.run)
        assertEquals(1, result.state.progress.clears)
        assertEquals(entered.items.size + entered.progress.run!!.target.items.size, result.state.items.size)
        assertTrue(result.state.items.all { it.place.container == Container.WAREHOUSE })
        assertFalse(rules.reward(result.state, RewardChoice.GOLD).ok)
    }
    @Test fun deathKeepsSafeButLosesBoundEquipmentAndRunGold() {
        val s = entered().copy(runGold = 77)
        val result = rules.abandon(s)
        assertTrue(result.ok)
        assertEquals(listOf("safe"), result.state.items.map { it.instanceId })
        assertEquals(s.gold, result.state.gold)
        assertEquals(0, result.state.runGold)
    }
    @Test fun timeoutTieAndSimultaneousDeathAreFailure() {
        val s = rules.startBattle(entered()).state
        val fight = s.progress.run!!.fight!!
        val tied = fight.copy(tick = 600, player = fight.player.copy(hp = 500.0, maxHp = 1000.0), enemy = fight.enemy.copy(hp = 250.0, maxHp = 500.0))
        assertEquals(false, DuelEngine.step(tied).won)
        assertEquals(false, DuelEngine.step(fight.copy(player = fight.player.copy(hp = 0.0), enemy = fight.enemy.copy(hp = 0.0))).won)
    }
}
