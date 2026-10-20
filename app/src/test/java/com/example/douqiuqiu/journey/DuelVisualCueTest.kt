package com.example.douqiuqiu.journey

import com.example.douqiuqiu.status.ActionControlState
import org.junit.Assert.*
import org.junit.Test

/** External runner: ./gradlew.bat --offline testDebugUnitTest --tests '*DuelVisualCueTest' */
class DuelVisualCueTest {
    private fun fighter(id: String) = Fighter(id, 10000.0, 10000.0, 200.0, 200.0, 10.0,
        100.0, 1.7, 0.0, .25, 0.0, 0.0, attackIn = 99.0, ultIn = 99.0)
    private fun battle(caster: Fighter, target: Fighter = fighter("abaddon")) = DuelState(caster, target, 1729, tick = 10)

    @Test fun castCueCarriesActorTargetAndCurrentTick() {
        val frame = DuelEngine.stepWithCues(battle(fighter("abaddon").copy(ultIn = 0.0)))
        val cast = frame.cues.single { it.kind == DuelCueKind.ULTIMATE_CAST }
        assertEquals(DuelVisualCue(11, 0, 1, "abaddon", DuelCueKind.ULTIMATE_CAST), cast)
        assertTrue(frame.state.player.shield > 0)
    }

    @Test fun silencedCasterHasNoCastCue() {
        val frame = DuelEngine.stepWithCues(battle(fighter("skywrath").copy(ultIn = 0.0, control = ActionControlState(silenced = true))))
        assertTrue(frame.cues.none { it.kind == DuelCueKind.ULTIMATE_CAST })
    }

    @Test fun stunnedAbaddonCanCastButHexedAbaddonCannot() {
        val stunned = DuelEngine.stepWithCues(battle(fighter("abaddon").copy(ultIn = 0.0, control = ActionControlState(stunned = true))))
        val hexed = DuelEngine.stepWithCues(battle(fighter("abaddon").copy(ultIn = 0.0, control = ActionControlState(hexed = true))))
        assertTrue(stunned.cues.any { it.kind == DuelCueKind.ULTIMATE_CAST })
        assertTrue(hexed.cues.none { it.kind == DuelCueKind.ULTIMATE_CAST })
    }

    @Test fun flareProducesExactlySixActualSegmentCues() {
        var state = battle(fighter("skywrath").copy(mana = 0.0, flareLeft = 6, flareIn = 0.0))
        val cues = mutableListOf<DuelVisualCue>()
        repeat(16) { val frame = DuelEngine.stepWithCues(state); state = frame.state; cues += frame.cues }
        assertEquals(6, cues.count { it.kind == DuelCueKind.FLARE_TICK })
        assertEquals(0, state.player.flareLeft)
    }

    @Test fun guaranteedCriticalOnShieldDoesNotEmitBloodCue() {
        val frame = DuelEngine.stepWithCues(battle(fighter("phantom_assassin").copy(criticalReady = 4.0, attackIn = 0.0),
            fighter("abaddon").copy(shield = 1000.0)))
        assertEquals(0.0, frame.state.player.criticalReady, 0.0)
        assertEquals(10000.0, frame.state.enemy.hp, 0.0)
        assertTrue(frame.cues.none { it.kind == DuelCueKind.PA_CRITICAL_HIT })
    }

    @Test fun guaranteedCriticalOnHealthEmitsOneBloodCue() {
        val frame = DuelEngine.stepWithCues(battle(fighter("phantom_assassin").copy(criticalReady = 4.0, attackIn = 0.0)))
        assertEquals(1, frame.cues.count { it.kind == DuelCueKind.PA_CRITICAL_HIT })
        assertTrue(frame.state.enemy.hp < 10000.0)
    }

    @Test fun omnislashCuesFollowRealAttacksAndStopAtSkillEnd() {
        var state = battle(fighter("juggernaut").copy(invulnerable = 1.0, slashIn = 0.0, speed = 400.0, bat = 1.0))
        val cues = mutableListOf<DuelVisualCue>()
        repeat(12) { val frame = DuelEngine.stepWithCues(state); state = frame.state; cues += frame.cues }
        assertTrue(cues.count { it.kind == DuelCueKind.OMNI_SLASH } >= 3)
        assertEquals(0.0, state.player.invulnerable, 0.0)
        assertTrue(DuelEngine.stepWithCues(state).cues.none { it.kind == DuelCueKind.OMNI_SLASH })
    }

    @Test fun finishedFightDoesNotReplayCues() {
        val state = battle(fighter("abaddon")).copy(won = true)
        val frame = DuelEngine.stepWithCues(state)
        assertEquals(state, frame.state); assertTrue(frame.cues.isEmpty())
    }

    @Test fun collectingCuesDoesNotChangeStateOrRandomSequence() {
        var plain = battle(fighter("juggernaut").copy(ultIn = 0.0, attackIn = 0.0), fighter("skywrath").copy(ultIn = 0.0, attackIn = 0.0))
        var observed = plain
        repeat(30) {
            plain = DuelEngine.step(plain)
            observed = DuelEngine.stepWithCues(observed).state
            assertEquals(plain, observed)
        }
    }
}
