package com.example.douqiuqiu.combat

import com.example.douqiuqiu.content.AttackClass
import com.example.douqiuqiu.content.DamageType
import com.example.douqiuqiu.content.EffectFlags

/** An already rolled/scaled damage request. No clock, RNG or secondary effects run here. */
data class DamageEvent(
    val sourceUnitId: String, val targetUnitId: String, val effectId: String,
    val amount: Double, val type: DamageType, val attackClass: AttackClass,
    val flags: EffectFlags = EffectFlags()
)

data class DamageDefense(
    val hp: Double, val armor: Double, val baseMagicResistance: Double,
    val shields: ShieldPool = ShieldPool(),
    val independentMagicResistances: List<Double> = emptyList(),
    val invulnerable: Boolean = false, val ethereal: Boolean = false,
    val attackProtected: Boolean = false, val magicVulnerability: Double = 0.0
)

data class DamageResolution(
    val hp: Double, val shields: ShieldPool, val lifeDamage: Double, val shieldDamage: Double,
    val blocked: Boolean, val canLifesteal: Boolean, val canConvertToShield: Boolean,
    val canTriggerDamageResponse: Boolean, val triggersOnHit: Boolean, val countsAsAttack: Boolean
) {
    fun lifestealBase(rates: List<Double>): Double =
        CombatMath.attackLifestealBase(if (canLifesteal) lifeDamage else 0.0, if (canLifesteal) shieldDamage else 0.0, rates)
}

/** H02 first integration: mitigation -> source-aware shields -> actual HP loss -> eligibility. */
object DamagePipeline {
    fun resolve(event: DamageEvent, defense: DamageDefense): DamageResolution {
        require(event.sourceUnitId.isNotBlank() && event.targetUnitId.isNotBlank() && event.effectId.isNotBlank())
        require(event.amount.isFinite() && event.amount >= 0)
        require(defense.hp.isFinite() && defense.hp >= 0)
        require(defense.armor.isFinite())
        require(defense.magicVulnerability.isFinite() && defense.magicVulnerability >= 0)
        val magicMultiplier = CombatMath.magicDamageMultiplier(defense.baseMagicResistance, defense.independentMagicResistances)
        ShieldLedger.total(defense.shields)
        val blocked = event.amount == 0.0 || defense.hp == 0.0 || defense.invulnerable ||
            (defense.ethereal && event.type == DamageType.PHYSICAL) ||
            (defense.attackProtected && event.attackClass == AttackClass.ATTACK)
        // True strike never pierces attack protection. The legacy H01 flag cannot override this rule.
        if (blocked) return DamageResolution(defense.hp, defense.shields, 0.0, 0.0, true, false, false, false, false, false)
        val removal = event.type == DamageType.HP_REMOVAL || event.attackClass == AttackClass.HP_REMOVAL
        val reflected = event.flags.isReflect || event.attackClass == AttackClass.REFLECT
        val selfDamage = event.flags.isSelfDamage || event.attackClass == AttackClass.SELF_DAMAGE
        val multiplier = when (event.type) {
            DamageType.PHYSICAL -> CombatMath.physicalDamageMultiplier(defense.armor)
            DamageType.MAGICAL, DamageType.HP_REMOVAL -> magicMultiplier * (1 + defense.magicVulnerability)
            DamageType.PURE -> 1.0
        }
        val incoming = event.amount * multiplier
        require(incoming.isFinite()) { "伤害数值溢出" }
        val absorbed = ShieldLedger.absorb(defense.shields, incoming, when (event.type) {
            DamageType.PHYSICAL -> ShieldDamageKind.PHYSICAL
            DamageType.MAGICAL, DamageType.HP_REMOVAL -> ShieldDamageKind.MAGICAL
            DamageType.PURE -> ShieldDamageKind.PURE
        })
        val lifeDamage = minOf(absorbed.remainingDamage, if (selfDamage) (defense.hp - 1.0).coerceAtLeast(0.0) else defense.hp)
        val ordinary = !removal && !reflected && !selfDamage
        val landed = ordinary && (lifeDamage > 0 || absorbed.absorbed > 0)
        return DamageResolution(
            hp = defense.hp - lifeDamage, shields = absorbed.pool,
            lifeDamage = lifeDamage, shieldDamage = absorbed.absorbed, blocked = false,
            canLifesteal = ordinary && event.flags.canLifesteal,
            canConvertToShield = ordinary && event.attackClass == AttackClass.SPELL && lifeDamage > 0,
            canTriggerDamageResponse = ordinary && lifeDamage > 0,
            triggersOnHit = landed && event.flags.triggersOnHit,
            countsAsAttack = landed && event.flags.countsAsAttack
        )
    }
}
