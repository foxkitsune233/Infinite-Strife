package com.example.douqiuqiu.equipment

import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.combat.CooldownReductionSource
import com.example.douqiuqiu.combat.CooldownReduction
import com.example.douqiuqiu.journey.Fighter

data class GearEffect(val item: EquippedGear, val effect: EffectDefinition) {
    val key: String get() = "gear:${item.instanceId}:${effect.id}"
    val group: String get() = effect.uniqueGroup ?: key
    fun number(name: String, default: Double = 0.0): Double = when (val value = effect.params[name]) {
        is EffectValue.Num -> value.value
        is EffectValue.Count -> value.value.toDouble()
        else -> default
    }
}

/** Six active slots only. Base stats stack; special families select by effect, not by whole item. */
object EquipmentCombat {
    val definitions = EquipmentCatalog.all.associateBy { it.id }
    private val upgradeGroups = mapOf("poison_blade" to listOf("rod_of_atos", "witch_blade"),
        "force_staff" to listOf("hurricane_pike", "force_staff"), "maelstrom" to listOf("mjollnir", "maelstrom"),
        "expert_array" to listOf("viper_bite", "expert_array"), "basher" to listOf("skull_basher"))

    fun effects(state: EquipmentBattleState): List<GearEffect> {
        val all = state.items.flatMap { item ->
            (definitions[item.definitionId]?.effects.orEmpty() + EquipmentCatalog.componentEffects[item.definitionId].orEmpty())
                .distinctBy { it.id }.filterNot { it.requiresReview }.map { GearEffect(item, it) }
        }
        return all.filter { candidate ->
            val priority = upgradeGroups[candidate.effect.uniqueGroup] ?: return@filter true
            val winner = state.items.filter { it.definitionId in priority }.minWithOrNull(
                compareBy<EquippedGear> { priority.indexOf(it.definitionId) }.thenBy { it.instanceId })
            candidate.item == winner
        }.sortedBy { it.key }
    }
    fun passiveValues(state: EquipmentBattleState, kind: EffectKind, param: String): List<Double> {
        val all = effects(state).filter { it.effect.trigger == EffectTrigger.PASSIVE && it.effect.kind == kind && param in it.effect.params }
        val plain = all.filter { it.effect.uniqueGroup == null }.map { it.number(param) }
        val grouped = all.filter { it.effect.uniqueGroup != null }
        val positive = grouped.filter { it.number(param) >= 0 }
        val chosen = UniqueBonusSelector.select(positive.map { UniqueBonusCandidate(it.item.instanceId, it.group, "${kind.name}:$param", it.number(param)) })
        return plain + chosen.map { it.value } + grouped.filter { it.number(param) < 0 }.groupBy { it.group }.values.map { list -> list.minOf { it.number(param) } }
    }
    fun passive(state: EquipmentBattleState, kind: EffectKind, param: String) = passiveValues(state, kind, param).sum()
    fun cooldownSources(state: EquipmentBattleState) = effects(state).filter {
        it.effect.kind == EffectKind.COOLDOWN_REDUCTION && it.effect.trigger == EffectTrigger.PASSIVE
    }.map { CooldownReductionSource(it.key, it.number("percent"), it.effect.uniqueGroup) }
    fun cooldown(state: EquipmentBattleState, seconds: Double, minimum: Double = .5) =
        CooldownReduction.calculate(seconds.coerceAtLeast(.000001), cooldownSources(state), minimum).effectiveSeconds
    fun statusDuration(f: Fighter, seconds: Double): Double = seconds * com.example.douqiuqiu.combat.CombatMath.statusDurationMultiplier(
        passiveValues(f.equipment, EffectKind.STATUS_RESIST, "percent"))
    fun healingMultiplier(f: Fighter, ghost: Boolean = f.ghost > 0) = com.example.douqiuqiu.combat.CombatMath.restorationMultiplier(
        passiveValues(f.equipment, EffectKind.HEAL_AMP, "percent") + if (ghost) listOf(.5) else emptyList(), emptyList())
    fun active(state: EquipmentBattleState, group: String) = (state.activeGroups[group] ?: 0) > 0
    fun speed(state: EquipmentBattleState): Double = (if (active(state, "mask_madness")) 60.0 else 0.0) +
        (if (active(state, "force_staff")) 40.0 else 0.0)
    fun lifesteal(state: EquipmentBattleState): Double = passive(state, EffectKind.LIFESTEAL, "percent") +
        if (active(state, "satanic")) 1.0 else 0.0
    fun armletAttack(f: Fighter): Double = if (active(f.equipment, "armlet")) 25.0 + if (f.heroId == "abaddon") 11.25 else 0.0 else 0.0
    fun activeItems(state: EquipmentBattleState): List<EquippedGear> = state.items.filter { item -> effects(state).any {
        it.item == item && (it.effect.trigger == EffectTrigger.ACTIVE || it.effect.trigger == EffectTrigger.LOW_HP)
    } }.sortedBy { it.instanceId }

    fun elapse(state: EquipmentBattleState): EquipmentBattleState = state.copy(
        immunityMs = (state.immunityMs - 100).coerceAtLeast(0), protectionMs = (state.protectionMs - 100).coerceAtLeast(0),
        cooldowns = EquipmentCooldownTracker.elapse(state.cooldowns, 100),
        activeGroups = state.activeGroups.mapValues { (_, ms) -> (ms - 100).coerceAtLeast(0) }.filterValues { it > 0 })

    fun validate(state: EquipmentBattleState) {
        require(state.immunityMs in 0..60000 && state.protectionMs in 0..60000)
        require(state.items.size <= 6 && state.items.map { it.instanceId }.distinct().size == state.items.size)
        require(state.items.all { it.instanceId.isNotBlank() && it.instanceId.length <= 128 && it.definitionId in definitions })
        val known = definitions.values.flatMap { d -> (d.effects + EquipmentCatalog.componentEffects[d.id].orEmpty()).map { it.uniqueGroup } }.filterNotNull().toSet()
        val owned = effects(state).map { it.group }.toSet()
        require(state.cooldowns.lastPreparedBattle in 0..5 && state.cooldowns.remainingByGroup.size <= 100)
        require(state.cooldowns.remainingByGroup.all { (key, ms) -> key.isNotBlank() && key.length <= 256 && ms in 0..3600000 })
        require(state.activeGroups.size <= 20 && state.activeGroups.all { (group, ms) -> group in known && group in owned && ms in 1..60000 })
        require(state.manual.all { id -> state.items.any { it.instanceId == id } })
        val critKeys = effects(state).filter { it.effect.kind == EffectKind.CRIT_SOURCE }.map { it.key }.toSet()
        require(state.criticalFailures.size <= 6 && state.criticalFailures.all { (key, count) -> key in critKeys && count in 0..10000 })
    }
}
