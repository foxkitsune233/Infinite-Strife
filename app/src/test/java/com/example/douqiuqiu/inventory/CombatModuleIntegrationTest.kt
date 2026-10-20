package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.combat.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test
import com.example.douqiuqiu.status.*

class CombatModuleIntegrationTest {
    private val journey = JourneyRules(InventoryRules(emptyMap()))
    private fun idle(hero: String) = journey.fighter(InventoryState(), hero).copy(
        mana = 0.0, manaRegen = 0.0, regen = 0.0, attack = 0.0, attackIn = 10.0, ultIn = 10.0)

    @Test fun firstSkyShieldGrantCannotExceedCap() {
        val result = ShieldLedger.apply(ShieldPool(), ShieldEntry("sky", ShieldKind.MAGICAL, 300.0), ShieldRefreshMode.ADD_CAPPED, 100.0)
        assertEquals(100.0, ShieldLedger.total(result), 0.00001)
    }

    @Test fun aphoticShieldAddsToIndependentStartingCardShield() {
        val abaddon = idle("abaddon").copy(mana = 100.0, ultIn = 0.0, shield = 60.0)
        val enemy = idle("phantom_assassin").copy(armor = 0.0, magicResist = 0.0)
        val result = DuelEngine.step(DuelState(abaddon, enemy, 1729))
        assertEquals(240.0, result.player.shield, 0.00001)
        assertEquals(enemy.hp - 72.0, result.enemy.hp, 0.00001)
    }

    @Test fun abaddonCastsWhileStunnedButNotWhileSilencedOrHexed() {
        val enemy = idle("phantom_assassin")
        val player = idle("abaddon").copy(mana = 100.0, ultIn = 0.0)
        val stunned = DuelEngine.step(DuelState(player.copy(control = ActionControlState(stunned = true)), enemy, 1729)).player
        assertEquals(180.0, stunned.shield, .00001)
        assertFalse(stunned.control.stunned)
        for (control in listOf(ActionControlState(silenced = true), ActionControlState(hexed = true))) {
            val blocked = DuelEngine.step(DuelState(player.copy(control = control), enemy, 1729)).player
            assertEquals(100.0, blocked.mana, .00001)
            assertEquals(0.0, blocked.shield, .00001)
        }
    }

    @Test fun physicalShieldIsUsedBeforeUniversalAndBothSourcesSurviveIndependently() {
        val player = FighterEffects.withShields(idle("phantom_assassin"), ShieldPool(listOf(
            ShieldEntry("card:S1", ShieldKind.UNIVERSAL, 60.0), ShieldEntry("physical", ShieldKind.PHYSICAL, 20.0))))
            .copy(armor = 0.0)
        val enemy = idle("phantom_assassin").copy(attack = 50.0, attackIn = 0.0)
        val result = DuelEngine.step(DuelState(player, enemy, 1729)).player
        assertEquals(player.hp, result.hp, .00001)
        assertEquals(0.0, result.physicalShield, .00001)
        assertEquals(30.0, result.shield, .00001)
    }

    @Test fun independentPoisonLayersKeepPartialLastTickAndStrongDispelPreservesViperPoison() {
        val player = idle("phantom_assassin").copy(magicResist = 0.0,
            poisons = listOf(Poison(100.0, .05, "skin"), Poison(20.0, .2, "viper", false)))
        val next = DuelEngine.step(DuelState(player, idle("abaddon"), 1729)).player
        assertEquals(player.hp - 7.0, next.hp, .00001)
        assertEquals(1, next.poisons.size)
        assertEquals(.1, next.poisons.single().remaining, .00001)
        val cleansed = FighterEffects.strongDispel(player)
        assertEquals(listOf("viper"), cleansed.poisons.map { it.source })
    }

    @Test fun anExpiringShieldWithMaximumDurationStillPrecedesPermanentShield() {
        val pool = ShieldPool(listOf(ShieldEntry("a-permanent", ShieldKind.UNIVERSAL, 10.0),
            ShieldEntry("z-expiring", ShieldKind.UNIVERSAL, 10.0, Long.MAX_VALUE)))
        val result = ShieldLedger.absorb(pool, 10.0, ShieldDamageKind.PHYSICAL)
        assertEquals("a-permanent", result.pool.entries.single().sourceId)
    }

    @Test fun consumedBattleItemIsBlockedByStunButAllowedUnderSilence() {
        val inv = InventoryRules(mapOf("faerie_fire" to ItemDefinition("faerie_fire", "仙灵之火", ItemKind.CONSUMABLE, 1, 1,
            combatUsable = true, stats = EquipmentStats(attack = 2.0))))
        val rules = JourneyRules(inv)
        val batch = rules.ensureBatch(InventoryState(items = listOf(StoredItem("fire", "faerie_fire", ItemPlace(Container.EQUIPMENT)))))
        val started = rules.startBattle(rules.enter(batch, batch.progress.chests.first().id, "abaddon").state).state
        val run = started.progress.run!!
        val fight = run.fight!!
        fun controlled(control: ActionControlState): InventoryState = started.copy(progress = started.progress.copy(run = run.copy(
            fight = fight.copy(player = fight.player.copy(hp = 500.0, control = control)), hp = 500.0)))
        val stunned = controlled(ActionControlState(stunned = true))
        assertEquals(stunned, rules.useConsumable(stunned, "fire").state)
        assertFalse(rules.useConsumable(stunned, "fire").ok)
        val used = rules.useConsumable(controlled(ActionControlState(silenced = true)), "fire")
        assertTrue(used.ok)
        assertTrue(used.state.items.isEmpty())
        assertEquals(600.0, used.state.progress.run!!.hp, .00001)
        assertFalse(rules.useConsumable(used.state, "fire").ok)
    }
}
