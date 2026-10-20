package com.example.douqiuqiu.cards

import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.equipment.*
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

/** Reproductions for the exported battle journal from 2026-10-20. */
class BattleReportedBugsTest {
    private val definitions = CardCombat.enabledCards.associate {
        it.id to ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height)
    }
    private val journey = JourneyRules(InventoryRules(definitions))
    private fun fighter(hero: String, vararg cards: String): Fighter = journey.fighter(
        InventoryState(skillSlots = 8, items = cards.mapIndexed { i, id ->
            StoredItem("card-$i", id, ItemPlace(Container.SKILLS, i))
        }), hero).copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun dummy() = fighter("phantom_assassin").copy(hp = 100000.0, maxHp = 100000.0)

    @Test fun openingUltimatePrecedesAutomaticMaskSelfSilence() {
        val player = fighter("juggernaut", "U4").copy(equipment = EquipmentBattleState(
            items = listOf(EquippedGear("mask", "mask_of_madness"))))
        val frame = DuelEngine.stepWithCues(DuelState(player, dummy(), 1729))
        assertTrue("U4 must cast before the automatic mask silences its owner", frame.state.player.invulnerable > 0)
        assertEquals(0.0, frame.state.player.mana, 0.00001)
        assertTrue(frame.cues.any { it.actor == 0 && it.kind == DuelCueKind.ULTIMATE_CAST })
    }

    @Test fun legionExtraAttackInFirstFrameProducesValidCardSnapshot() {
        var observed = false
        for (seed in 1L..100L) {
            val next = DuelEngine.step(DuelState(dummy(), fighter("legion_commander", "D9").copy(attackIn = 0.0), seed))
            if (next.enemy.cardState.attackCount == 2) {
                observed = true
                CardCombat.validateAt(next.enemy.cardState, next.player.cardState, next.tick, 0)
                break
            }
        }
        assertTrue("Fixture must exercise Legion's two attacks in one frame", observed)
    }

    @Test fun lowHealthD11DoesNotHealAgainOnEveryDamageCallback() {
        var s = DuelState(fighter("juggernaut").copy(attack = 100.0, attackIn = 0.0, broken = 999.0),
            fighter("abaddon", "D11").copy(hp = 490.0, maxHp = 1000.0, armor = 0.0), 1729)
        val first = DuelEngine.step(s)
        assertEquals(430.0, first.enemy.hp, 0.00001)
        s = DuelEngine.step(first.copy(player = first.player.copy(attackIn = 0.0)))
        assertEquals("Second hit must not immediately trigger another 4% heal", 330.0, s.enemy.hp, 0.00001)
    }

    @Test fun d11CooldownEventuallyAllowsAnotherHealAndDoesNotPreventDeath() {
        val hurt = fighter("abaddon", "D11").copy(hp = 100.0, maxHp = 1000.0)
        var s = DuelEngine.step(DuelState(dummy(), hurt, 1729))
        assertEquals(140.0, s.enemy.hp, 0.00001)
        repeat(119) { s = DuelEngine.step(s) }
        assertEquals(140.0, s.enemy.hp, 0.00001)
        s = DuelEngine.step(s)
        assertEquals(180.0, s.enemy.hp, 0.00001)
        val dead = DuelEngine.step(s.copy(player = s.player.copy(attack = 10000.0, attackIn = 0.0)))
        assertEquals(0.0, dead.enemy.hp, 0.00001)
        assertEquals(true, dead.won)
    }

    @Test fun resumedBattleDoesNotReplayOpeningUltimate() {
        val player = fighter("juggernaut", "U4").copy(cardState = fighter("juggernaut", "U4").cardState.copy(started = true))
        val frame = DuelEngine.stepWithCues(DuelState(player, dummy(), 1729, tick = 10))
        assertFalse(frame.cues.any { it.kind == DuelCueKind.ULTIMATE_CAST })
        assertEquals(0.0, frame.state.player.invulnerable, 0.00001)
    }

    @Test fun impossibleAttackCountStillFailsSnapshotValidation() {
        val player = fighter("legion_commander", "D9")
        assertThrows(IllegalArgumentException::class.java) {
            CardCombat.validateAt(player.cardState.copy(started = true, attackCount = 3), dummy().cardState, 1, 0)
        }
    }
}
