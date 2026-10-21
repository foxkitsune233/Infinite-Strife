package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.quest.*

import com.example.douqiuqiu.journey.FirstRunContent

/** References existing item instances only; it never duplicates items or their stars/bindings. */
data class LoadoutPreset(
    val equipment: List<String?> = List(9) { null },
    val skills: List<String?> = List(8) { null },
    val manualItems: Set<String> = emptySet()
)

/** Camp-only preset transitions. Every rejected transition returns the exact input state. */
class LoadoutRules(private val inventory: InventoryRules) {
    companion object { const val PRESET_COUNT = 3 }

    fun save(state: InventoryState, heroId: String, slot: Int): InventoryResult {
        inventory.validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase != InventoryPhase.HOME) return InventoryResult(state, "挑战中不能保存局外配装")
        if (!validHero(state, heroId)) return InventoryResult(state, "英雄尚未解锁")
        if (slot !in 0 until PRESET_COUNT) return InventoryResult(state, "方案栏位无效")
        val preset = LoadoutPreset(
            equipment = (0..8).map { index -> state.items.singleOrNull { it.place == ItemPlace(Container.EQUIPMENT, index) }?.instanceId },
            skills = (0..7).map { index -> state.items.singleOrNull { it.place == ItemPlace(Container.SKILLS, index) }?.instanceId },
            manualItems = state.progress.manualItems.filterTo(linkedSetOf()) { id ->
                state.items.any { it.instanceId == id && it.place.container == Container.EQUIPMENT }
            }
        )
        val slots = state.progress.loadouts[heroId].orEmpty().toMutableList()
        while (slots.size < PRESET_COUNT) slots += LoadoutPreset()
        slots[slot] = preset
        val progress = state.progress.copy(activeHeroId = heroId,
            loadouts = state.progress.loadouts + (heroId to slots),
            loadoutSelections = state.progress.loadoutSelections + (heroId to slot))
        return checked(state, state.copy(progress = progress))
    }

    /** Switches the camp hero and applies its most recently used preset. */
    fun activateHero(state: InventoryState, heroId: String): InventoryResult {
        if (!validHero(state, heroId)) return InventoryResult(state, "英雄尚未解锁")
        val slot = state.progress.loadoutSelections[heroId] ?: 0
        return apply(state, heroId, slot)
    }

    fun apply(state: InventoryState, heroId: String, slot: Int): InventoryResult {
        inventory.validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase != InventoryPhase.HOME) return InventoryResult(state, "挑战中不能套用局外配装")
        if (!validHero(state, heroId)) return InventoryResult(state, "英雄尚未解锁")
        if (slot !in 0 until PRESET_COUNT) return InventoryResult(state, "方案栏位无效")
        val savedPreset = state.progress.loadouts[heroId]?.getOrNull(slot) ?: LoadoutPreset()
        // Presets deliberately keep instance IDs. Crafting, sale, death loss or replacement can
        // legitimately invalidate one, so prune only missing IDs and never substitute by definition.
        val heldIds = state.items.mapTo(linkedSetOf()) { it.instanceId }
        val missing = (savedPreset.equipment + savedPreset.skills).filterNotNull().filter { it !in heldIds }.distinct()
        val preset = if (missing.isEmpty()) savedPreset else savedPreset.copy(
            equipment = savedPreset.equipment.map { id -> id?.takeIf { it in heldIds } },
            skills = savedPreset.skills.map { id -> id?.takeIf { it in heldIds } },
            manualItems = savedPreset.manualItems.filterTo(linkedSetOf()) { it in heldIds }
        )
        val referenced = preset.equipment + preset.skills
        val ids = referenced.filterNotNull()
        if (ids.distinct().size != ids.size) return InventoryResult(state, "方案包含重复物品")
        for ((index, id) in preset.equipment.withIndex()) if (id != null) {
            val item = state.items.singleOrNull { it.instanceId == id } ?: return InventoryResult(state, "方案物品资料无效")
            val definition = inventory.definitions.getValue(item.definitionId)
            if (definition.kind != ItemKind.EQUIPMENT && !(definition.kind == ItemKind.CONSUMABLE && definition.combatUsable))
                return InventoryResult(state, "方案装备栏包含不可战斗使用的物品")
            if (index !in 0..8) return InventoryResult(state, "方案装备栏无效")
        }
        for ((index, id) in preset.skills.withIndex()) if (id != null) {
            val item = state.items.singleOrNull { it.instanceId == id } ?: return InventoryResult(state, "方案物品资料无效")
            if (inventory.definitions.getValue(item.definitionId).kind != ItemKind.CARD || index >= state.skillSlots)
                return InventoryResult(state, "方案技能栏与当前技能栏不兼容")
        }
        val equipmentTargets = preset.equipment.withIndex().mapNotNull { (index, id) -> id?.let { it to ItemPlace(Container.EQUIPMENT, index) } }.toMap()
        val skillTargets = preset.skills.withIndex().mapNotNull { (index, id) -> id?.let { it to ItemPlace(Container.SKILLS, index) } }.toMap()
        val targets = equipmentTargets + skillTargets
        // If every referenced item is gone, retain the current slots rather than silently unequipping
        // the player. The preset is still sanitized and can be saved again immediately.
        val keepCurrentSlots = missing.isNotEmpty() && targets.isEmpty()
        val nextItems = if (keepCurrentSlots) state.items else state.items.map { item ->
            when {
                item.instanceId in targets -> item.copy(place = targets.getValue(item.instanceId))
                item.place.container == Container.EQUIPMENT || item.place.container == Container.SKILLS -> item.copy(place = ItemPlace(Container.WAREHOUSE))
                else -> item
            }
        }
        val sanitizedLoadouts = if (missing.isEmpty()) state.progress.loadouts else state.progress.loadouts +
            (heroId to state.progress.loadouts.getValue(heroId).toMutableList().also { it[slot] = preset })
        val progress = state.progress.copy(activeHeroId = heroId, loadouts = sanitizedLoadouts,
            loadoutSelections = state.progress.loadoutSelections + (heroId to slot),
            manualItems = if (keepCurrentSlots) state.progress.manualItems
                else preset.manualItems.filterTo(linkedSetOf()) { it in equipmentTargets })
        val result = checked(state, state.copy(items = nextItems, progress = progress))
        return if (result.ok && missing.isNotEmpty()) result.copy(notice = "方案已清理${missing.size}件不再持有的物品") else result
    }

    private fun validHero(state: InventoryState, heroId: String) =
        heroId in state.progress.unlockedHeroes && FirstRunContent.heroes.any { it.id == heroId }

    private fun checked(original: InventoryState, next: InventoryState) =
        inventory.validate(next)?.let { InventoryResult(original, it) } ?: InventoryResult(QuestTracker.observe(next))
}
