package com.example.douqiuqiu.journey

import com.example.douqiuqiu.content.EffectTrigger
import com.example.douqiuqiu.content.EffectKind
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.equipment.EquippedGear
import com.example.douqiuqiu.inventory.Container

/** Only revealed IDs reach presentation/accessibility. No RNG and no mutable scouting state. */
data class EnemyIntel(val revealedIds: List<String>, val concealedCount: Int?,
    val roles: List<String>, val complete: Boolean, val inlineDetails: Boolean)

object EnemyScouting {
    fun inspect(build: EnemyBuild, difficulty: Int): EnemyIntel {
        require(difficulty in ChallengeBalance.difficulties)
        val gear = EquipmentBattleState(items = build.inventory.items.filter { it.place.container == Container.EQUIPMENT }
            .map { EquippedGear(it.instanceId, it.definitionId) })
        val active = EquipmentCombat.activeItems(gear).map { it.definitionId }.sorted()
        val visible = when (difficulty) { 0, 1 -> active.size; 2 -> (active.size + 1) / 2; 3 -> minOf(1, active.size); else -> 0 }
        val roles = if (difficulty == 4) active.flatMap { id ->
            EquipmentCombat.definitions.getValue(id).effects.filter { !it.requiresReview && it.trigger in setOf(EffectTrigger.ACTIVE, EffectTrigger.LOW_HP) }.map { effect -> when (effect.kind) {
                EffectKind.APPLY_CONTROL -> "控制"
                EffectKind.IMMUNITY, EffectKind.DISPEL, EffectKind.GRANT_SHIELD, EffectKind.ATTACK_PROTECTION -> "防护"
                EffectKind.LIFESTEAL -> "续航"
                else -> "强化"
            } }
        }.distinct().sorted() else emptyList()
        return EnemyIntel(active.take(visible), if (difficulty <= 3) active.size - visible else null,
            roles, difficulty <= 1, difficulty == 0)
    }
}
