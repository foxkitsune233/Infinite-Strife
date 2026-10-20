package com.example.douqiuqiu.journey

import org.junit.Assert.*
import org.junit.Test

/** External execution only; these tests do not advance a battle. */
class AttackAnimationClockTest {
    @Test fun phasesHaveStableBoundariesAndReturnToReady() {
        assertEquals(0, AttackAnimationClock.frameAt(-1, 420))
        assertEquals(0, AttackAnimationClock.frameAt(0, 420))
        assertEquals(0, AttackAnimationClock.frameAt(58, 420))
        assertEquals(1, AttackAnimationClock.frameAt(59, 420))
        assertEquals(1, AttackAnimationClock.frameAt(142, 420))
        assertEquals(2, AttackAnimationClock.frameAt(143, 420))
        assertEquals(2, AttackAnimationClock.frameAt(285, 420))
        assertEquals(3, AttackAnimationClock.frameAt(286, 420))
        assertEquals(3, AttackAnimationClock.frameAt(419, 420))
        assertEquals(0, AttackAnimationClock.frameAt(420, 420))
    }
    @Test fun fastAttacksCompressTheWholeSequence() {
        assertEquals(420L, AttackAnimationClock.durationMs(1.7))
        assertEquals(180L, AttackAnimationClock.durationMs(.2))
        assertEquals(60L, AttackAnimationClock.durationMs(0.0))
        assertEquals(setOf(0, 1, 2, 3), (0L until 60L).map { AttackAnimationClock.frameAt(it, 60) }.toSet())
    }
    @Test fun invalidDurationsAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { value ->
            assertTrue(runCatching { AttackAnimationClock.durationMs(value) }.exceptionOrNull() is IllegalArgumentException)
        }
        listOf(0L, 59L, 421L).forEach { value ->
            assertTrue(runCatching { AttackAnimationClock.frameAt(1, value) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
