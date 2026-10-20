package com.example.douqiuqiu.journey

import com.example.douqiuqiu.combat.PoisonCombat
import com.example.douqiuqiu.equipment.EquipmentBattleState
import org.junit.Assert.*
import org.junit.Test

class AttackFeedbackTest {
    private fun idle(id: String) = Fighter(id, 1000.0, 1000.0, 0.0, 100.0, 20.0, 100.0, 1.7, 0.0, 0.0, 0.0, 0.0,
        attackIn = 99.0, ultIn = 99.0)
    @Test fun a01HitReportsActualLifeLoss() {
        val frame = DuelEngine.stepWithCues(DuelState(idle("phantom_assassin").copy(attackIn = 0.0), idle("skywrath"), 1729))
        val hit = frame.cues.single { it.kind == DuelCueKind.HIT }
        assertEquals(1000.0 - frame.state.enemy.hp, hit.lifeDamage, .000001)
        assertEquals(0.0, hit.shieldDamage, 0.0)
        assertTrue(frame.cues.any { it.kind == DuelCueKind.ATTACK })
    }
    @Test fun a02ShieldOnlyCriticalDoesNotReportBloodOrLifeDamage() {
        val frame = DuelEngine.stepWithCues(DuelState(idle("phantom_assassin").copy(attackIn = 0.0, criticalReady = 4.0),
            idle("skywrath").copy(shield = 500.0), 1729))
        val hit = frame.cues.single { it.kind == DuelCueKind.HIT }
        assertTrue(hit.critical)
        assertEquals(0.0, hit.lifeDamage, 0.0)
        assertTrue(hit.shieldDamage > 0)
        assertTrue(frame.cues.none { it.kind == DuelCueKind.PA_CRITICAL_HIT })
    }
    @Test fun a03ProtectionHasNoHitCueAndDoesNotDamageTarget() {
        val frame = DuelEngine.stepWithCues(DuelState(idle("phantom_assassin").copy(attackIn = 0.0),
            idle("skywrath").copy(equipment = EquipmentBattleState(protectionMs = 1000)), 1729))
        assertTrue(frame.cues.any { it.kind == DuelCueKind.ATTACK_BLOCKED })
        assertTrue(frame.cues.none { it.kind == DuelCueKind.HIT })
        assertEquals(1000.0, frame.state.enemy.hp, 0.0)
    }
    @Test fun a04PoisonLayersMergeFeedbackWithoutLosingDamage() {
        val target = PoisonCombat.apply(idle("skywrath"), 0, "card:P1", 0, 0, false, 20, 3.0)
        val frame = DuelEngine.stepWithCues(DuelState(idle("phantom_assassin"), target, 1729, tick = 9))
        val poison = frame.cues.single { it.kind == DuelCueKind.POISON_HIT }
        assertEquals(60.0, poison.lifeDamage, .000001)
        assertEquals(1000.0 - frame.state.enemy.hp, poison.lifeDamage, .000001)
        assertTrue(frame.cues.none { it.kind == DuelCueKind.ATTACK })
    }
    @Test fun a05CuesNeverAlterStateOrRandomSequence() {
        val initial = DuelState(idle("phantom_assassin").copy(attackIn = 0.0), idle("skywrath").copy(attackIn = 0.0), 1729)
        var plain = initial; var observed = initial
        repeat(30) {
            plain = DuelEngine.step(plain)
            observed = DuelEngine.stepWithCues(observed).state
            assertEquals(plain, observed)
        }
    }
}
