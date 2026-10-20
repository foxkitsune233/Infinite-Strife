package com.example.douqiuqiu.combat

import com.example.douqiuqiu.content.AttackClass
import com.example.douqiuqiu.content.DamageType
import com.example.douqiuqiu.content.EffectFlags
import org.junit.Assert.*
import org.junit.Test

/** External execution only; expected amounts are hand-calculated. */
class DamagePipelineTest {
    private fun event(type: DamageType, kind: AttackClass = AttackClass.SPELL, flags: EffectFlags = EffectFlags()) =
        DamageEvent("player", "enemy", "test", 100.0, type, kind, flags)
    private fun target(pool: ShieldPool = ShieldPool()) = DamageDefense(100.0, 0.0, .25, pool)

    @Test fun magicalDamageConsumesSpecializedShieldThenUniversal() {
        val pool = ShieldPool(listOf(ShieldEntry("magic", ShieldKind.MAGICAL, 30.0), ShieldEntry("all", ShieldKind.UNIVERSAL, 20.0)))
        val hit = DamagePipeline.resolve(event(DamageType.MAGICAL), target(pool))
        assertEquals(25.0, hit.lifeDamage, .00001)
        assertEquals(50.0, hit.shieldDamage, .00001)
        assertEquals(75.0, hit.hp, .00001)
    }
    @Test fun pureDamageBypassesShields() {
        val pool = ShieldPool(listOf(ShieldEntry("all", ShieldKind.UNIVERSAL, 500.0)))
        val hit = DamagePipeline.resolve(event(DamageType.PURE), target(pool))
        assertEquals(0.0, hit.hp, .00001)
        assertEquals(pool, hit.shields)
    }
    @Test fun hpRemovalIsMagicalAndCannotTriggerRecovery() {
        val hit = DamagePipeline.resolve(event(DamageType.HP_REMOVAL, AttackClass.HP_REMOVAL, EffectFlags(canLifesteal = true)), target())
        assertEquals(75.0, hit.lifeDamage, .00001)
        assertFalse(hit.canLifesteal); assertFalse(hit.canTriggerDamageResponse); assertFalse(hit.canConvertToShield)
    }
    @Test fun invulnerabilityBlocksPureDamageToo() {
        assertTrue(DamagePipeline.resolve(event(DamageType.PURE), target().copy(invulnerable = true)).blocked)
    }
    @Test fun etherealBlocksPhysicalSkills() {
        val hit = DamagePipeline.resolve(event(DamageType.PHYSICAL, AttackClass.PHYSICAL_SKILL), target().copy(ethereal = true))
        assertEquals(100.0, hit.hp, .00001)
    }
    @Test fun magicalVulnerabilityIsAppliedAfterResistance() {
        val hit = DamagePipeline.resolve(event(DamageType.MAGICAL), target().copy(magicVulnerability = .2))
        assertEquals(90.0, hit.lifeDamage, .00001)
    }
    @Test fun overkillIsNotLifeDamageOrShieldConversion() {
        val hit = DamagePipeline.resolve(event(DamageType.MAGICAL), target().copy(hp = 10.0))
        assertEquals(10.0, hit.lifeDamage, .00001)
    }
    @Test fun fullyShieldedSpellCannotConvertToSkyShield() {
        val hit = DamagePipeline.resolve(event(DamageType.MAGICAL), target(ShieldPool(listOf(ShieldEntry("all", ShieldKind.UNIVERSAL, 100.0)))))
        assertFalse(hit.canConvertToShield)
    }
    @Test fun attackProtectionBlocksEntireAttackEvenWithLegacyPiercingFlag() {
        val hit = DamagePipeline.resolve(event(DamageType.PHYSICAL, AttackClass.ATTACK,
            EffectFlags(canLifesteal = true, triggersOnHit = true, countsAsAttack = true, piercesAttackProtection = true)),
            target().copy(attackProtected = true))
        assertTrue(hit.blocked); assertFalse(hit.triggersOnHit); assertFalse(hit.countsAsAttack); assertFalse(hit.canLifesteal)
    }
    @Test fun attackLifestealCountsHalfShieldDamage() {
        val hit = DamagePipeline.resolve(event(DamageType.PHYSICAL, AttackClass.ATTACK, EffectFlags(canLifesteal = true)),
            target(ShieldPool(listOf(ShieldEntry("all", ShieldKind.UNIVERSAL, 100.0)))))
        assertTrue(hit.canLifesteal)
        assertEquals(10.0, hit.lifestealBase(listOf(.2)), .00001)
    }
    @Test fun reflectCannotLifestealConvertOrTriggerResponse() {
        val hit = DamagePipeline.resolve(event(DamageType.MAGICAL, AttackClass.REFLECT,
            EffectFlags(canLifesteal = true, triggersOnHit = true, isReflect = true)), target())
        assertFalse(hit.canConvertToShield); assertFalse(hit.canLifesteal); assertFalse(hit.canTriggerDamageResponse); assertFalse(hit.triggersOnHit)
    }
    @Test fun selfDamageDoesNotKillAndCannotTriggerResponse() {
        val hit = DamagePipeline.resolve(event(DamageType.PURE, AttackClass.SELF_DAMAGE), target().copy(hp = 10.0))
        assertEquals(1.0, hit.hp, .00001); assertFalse(hit.canTriggerDamageResponse)
    }
    @Test fun negativeDamageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { DamagePipeline.resolve(event(DamageType.PHYSICAL).copy(amount = -1.0), target()) }
    }
    @Test fun zeroDamageDoesNotTriggerAnything() {
        val hit = DamagePipeline.resolve(event(DamageType.PHYSICAL, AttackClass.ATTACK, EffectFlags(triggersOnHit = true)).copy(amount = 0.0), target())
        assertFalse(hit.triggersOnHit); assertFalse(hit.canTriggerDamageResponse)
    }
    @Test fun inputSnapshotsRemainUnchanged() {
        val defense = target()
        DamagePipeline.resolve(event(DamageType.MAGICAL), defense)
        assertEquals(100.0, defense.hp, .00001)
    }
}
