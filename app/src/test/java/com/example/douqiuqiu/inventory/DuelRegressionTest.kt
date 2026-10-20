package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

class DuelRegressionTest {
    private val journey = JourneyRules(InventoryRules(emptyMap()))
    @Test fun skinCannotReplaceTheStillActiveUltimateSlow() {
        val player = journey.fighter(InventoryState(), "juggernaut").copy(slow = 30.0, slowTime = 3.0,
            speed = 120.0, mana = 0.0, ultIn = 999.0)
        val enemy = journey.fighter(InventoryState(), "viper").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertEquals(30.0, next.player.slow, .00001)
        assertEquals(2.0, next.player.attackIn, .00001)
    }
    @Test fun ghostShroudAmplifiesNaturalManaRecovery() {
        val player = journey.fighter(InventoryState(), "necrophos").copy(ghost = 3.0, mana = 0.0,
            manaRegen = 10.0, ultIn = 999.0)
        val enemy = journey.fighter(InventoryState(), "abaddon").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        assertEquals(1.5, DuelEngine.step(DuelState(player, enemy, 1729)).player.mana, .00001)
    }
}
