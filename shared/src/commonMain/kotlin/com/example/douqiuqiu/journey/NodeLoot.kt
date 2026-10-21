package com.example.douqiuqiu.journey

import com.example.douqiuqiu.quest.*

import com.example.douqiuqiu.inventory.*

enum class NodeLootSource { BATTLE, EVENT }

/** Ground items are not owned. Keep the frozen list after pickup so inspection timing never shifts. */
data class NodeLoot(
    val id: String, val source: NodeLootSource, val items: List<StoredItem>,
    val picked: Set<String> = emptySet(), val inspectionMs: Long = 0, val goldAwarded: Int = 0,
    val putDown: List<StoredItem> = emptyList()
) {
    val remaining get() = items.filterNot { it.instanceId in picked } + putDown
}

/**
 * Batch pickup outcome. The three id lists are mutually exclusive and together cover every
 * instance that was on the ground when the batch started, so the caller can always tell
 * "would not fit" apart from "still being appraised".
 */
data class BatchLootResult(
    val state: InventoryState,
    val pickedIds: List<String>,
    val remainingIds: List<String>,
    val pendingInspectionIds: List<String>
) {
    val pickedCount get() = pickedIds.size
    val remainingCount get() = remainingIds.size
    val pendingCount get() = pendingInspectionIds.size
}

object NodeLootRules {
    fun openingMs(loot: NodeLoot) = if (loot.source == NodeLootSource.EVENT) 800L else 0L
    fun revealAt(loot: NodeLoot, definitions: Map<String, ItemDefinition>): Map<String, Long> {
        val ds = loot.items.map { definitions.getValue(it.definitionId) }
        val starts = AppraisalTiming.startsMs(ds)
        return loot.items.indices.associate { i -> loot.items[i].instanceId to (openingMs(loot) + starts[i] + AppraisalTiming.durationMs(ds[i])) }
    }
    fun duration(loot: NodeLoot, definitions: Map<String, ItemDefinition>) = revealAt(loot, definitions).values.maxOrNull() ?: openingMs(loot)
    fun revealed(loot: NodeLoot, id: String, definitions: Map<String, ItemDefinition>) =
        loot.putDown.any { it.instanceId == id } || revealAt(loot, definitions)[id]?.let { loot.inspectionMs >= it } == true

    /** Pure deterministic first-round distribution. All types and qualities may appear; progression gates remain. */
    fun generate(s: InventoryState, defs: Map<String, ItemDefinition>, source: NodeLootSource,
                 seed: Long, guaranteed: List<String> = emptyList()): Pair<NodeLoot, Long> {
        val r = s.progress.run ?: error("没有挑战")
        var random = seed
        fun next(n: Int): Int { random = FirstRunContent.next(random); return (random % n).toInt() }
        val count = if (source == NodeLootSource.EVENT) 2 + next(5) else if (r.round == 5) 3 + next(3) else 1 + next(3)
        val pool = defs.values.filter { it.minimumDropClears <= s.progress.clears &&
            (r.rewardVersion < 3 || ChallengeBalance.allowsDrop(it, r.target.difficulty, r.rewardVersion)) }.sortedBy { it.id }
        require(pool.isNotEmpty())
        fun weight(d: ItemDefinition): Int {
            val base = when (d.quality) { "金" -> 3; "紫" -> 12; "蓝" -> 35; else -> 80 }
            return (base / (1 + d.price / (250 + r.target.difficulty * 150))).coerceAtLeast(1)
        }
        val total = pool.sumOf(::weight)
        val ids = guaranteed.toMutableList()
        while (ids.size < count) {
            var roll = next(total)
            ids += pool.first { d -> roll -= weight(d); roll < 0 }.id
        }
        val key = "${r.id}:${source.name.lowercase()}:${r.round}"
        return NodeLoot(key, source, ids.mapIndexed { i, id ->
            StoredItem("$key:drop:$i", id, ItemPlace(Container.WAREHOUSE), bound = defs.getValue(id).boundOnAcquire)
        }) to random
    }

    fun collect(s: InventoryState, inventory: InventoryRules, id: String, target: ItemPlace): InventoryResult {
        fun fail(reason: String) = InventoryResult(s, reason)
        inventory.validate(s)?.let { return fail(it) }
        val run = s.progress.run ?: return fail("没有挑战")
        val loot = run.nodeLoot ?: return fail("没有待拾取物品")
        if (s.phase != InventoryPhase.PREPARATION || run.stage != JourneyStage.REWARD) return fail("当前不能拾取")
        val item = loot.remaining.find { it.instanceId == id } ?: return fail("掉落不存在或已拾取")
        if (id in loot.picked || s.items.any { it.instanceId == id }) return fail("物品已拾取")
        if (!revealed(loot, id, inventory.definitions)) return fail("物品正在鉴定，请稍候")
        if (target.container == Container.WAREHOUSE) return fail("途中掉落必须放入随身容器")
        val slotted = target.container == Container.EQUIPMENT || target.container == Container.SKILLS
        val displaced = if (slotted) s.items.find { it.place.container == target.container && it.place.x == target.x } else null
        val fromPutDown = loot.putDown.any { it.instanceId == id }
        // The incoming ground item frees one putDown place before the outgoing item takes it.
        val vacated = if (!fromPutDown) s else s.copy(progress = s.progress.copy(run = run.copy(
            nodeLoot = loot.copy(putDown = loot.putDown.filterNot { it.instanceId == id }))))
        val base = if (displaced == null) vacated else {
            val down = putDown(vacated, inventory, displaced.instanceId)
            if (!down.ok) return fail(down.error ?: "旧物无法放到待拾取区")
            down.state
        }
        val activeRun = base.progress.run!!
        val activeLoot = activeRun.nodeLoot!!
        val next = base.copy(items = base.items + item.copy(place = target), progress = base.progress.copy(run = activeRun.copy(
            nodeLoot = if (fromPutDown) activeLoot else activeLoot.copy(picked = activeLoot.picked + id))))
        return inventory.validate(next)?.let(::fail) ?: InventoryResult(QuestTracker.observe(
            if (fromPutDown) next else QuestTracker.record(next, QuestEvent.LootPicked)))
    }

    /**
     * Deterministic batch pickup into the ordinary backpack, walking the ground list in its own order.
     * Each item is tried in its normal orientation first and then rotated 90 degrees; anything that
     * still does not fit is left on the ground while the loop moves on to the next one. Items already
     * in the backpack are never moved, packed, equipped, slotted or dropped, and every instance keeps
     * its id, stars, binding and stack size. Only a state is returned, so a failed save can never
     * duplicate or lose an instance and repeated invocations stay idempotent.
     * Returns null when batch pickup does not apply to the current phase or stage.
     */
    fun collectRevealedIntoBackpack(s: InventoryState, inventory: InventoryRules): BatchLootResult? {
        inventory.validate(s)?.let { return null }
        val run = s.progress.run ?: return null
        val loot = run.nodeLoot ?: return null
        if (s.phase != InventoryPhase.PREPARATION || run.stage != JourneyStage.REWARD) return null
        val width = s.backpackWidth
        val height = s.backpackHeight
        val occupied = Array(height) { BooleanArray(width) }
        fun occupy(place: ItemPlace, def: ItemDefinition) {
            val w = if (place.rotated) def.height else def.width
            val h = if (place.rotated) def.width else def.height
            for (x in place.x until place.x + w) for (y in place.y until place.y + h) {
                if (y in 0 until height && x in 0 until width) occupied[y][x] = true
            }
        }
        for (item in s.items) if (item.place.container == Container.BACKPACK) occupy(item.place, inventory.definitions.getValue(item.definitionId))
        /** Top-to-bottom, left-to-right first fit; rotating is only meaningful for non-square items. */
        fun firstFit(def: ItemDefinition): ItemPlace? {
            val rotations = if (def.width == def.height) listOf(false) else listOf(false, true)
            for (rotated in rotations) {
                val w = if (rotated) def.height else def.width
                val h = if (rotated) def.width else def.height
                if (w > width || h > height) continue
                for (y in 0..(height - h)) for (x in 0..(width - w)) {
                    if ((x until x + w).all { cx -> (y until y + h).all { cy -> !occupied[cy][cx] } })
                        return ItemPlace(Container.BACKPACK, x, y, rotated)
                }
            }
            return null
        }
        var current = s
        val picked = mutableListOf<String>()
        val remaining = mutableListOf<String>()
        val pending = mutableListOf<String>()
        // Snapshot the ordering: the ground list shrinks as each pickup lands.
        for (item in loot.remaining) {
            val id = item.instanceId
            if (!revealed(loot, id, inventory.definitions)) { pending += id; continue }
            val def = inventory.definitions[item.definitionId] ?: continue
            val target = firstFit(def)
            if (target == null) { remaining += id; continue }
            val result = collect(current, inventory, id, target)
            if (!result.ok) { remaining += id; continue }
            current = result.state
            picked += id
            occupy(target, def)
        }
        return BatchLootResult(current, picked, remaining, pending)
    }

    /** Explicitly putting down an owned item removes its effects/protection until picked up again. */
    fun putDown(s: InventoryState, inventory: InventoryRules, id: String): InventoryResult {
        inventory.validate(s)?.let { return InventoryResult(s, it) }
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        val loot = r.nodeLoot ?: return InventoryResult(s, "当前没有掉落区")
        if (r.stage != JourneyStage.REWARD || s.phase != InventoryPhase.PREPARATION) return InventoryResult(s, "当前不能放下物品")
        if (loot.remaining.any { it.instanceId == id }) return InventoryResult(s)
        val owned = s.items.find { it.instanceId == id } ?: return InventoryResult(s, "找不到物品")
        if (owned.place.container == Container.WAREHOUSE) return InventoryResult(s, "挑战中不能访问仓库")
        if (loot.putDown.size >= 64 && loot.items.none { it.instanceId == id }) return InventoryResult(s, "当前掉落区物品过多，请先取舍")
        val changed = if (loot.items.any { it.instanceId == id }) loot.copy(picked = loot.picked - id,
            items = loot.items.map { if (it.instanceId == id) owned.copy(place = ItemPlace(Container.WAREHOUSE)) else it })
            else loot.copy(putDown = loot.putDown + owned.copy(place = ItemPlace(Container.WAREHOUSE)))
        val next = s.copy(items = s.items.filterNot { it.instanceId == id }, progress = s.progress.copy(run = r.copy(nodeLoot = changed)))
        return inventory.validate(next)?.let { InventoryResult(s, it) } ?: InventoryResult(next)
    }

    fun inspect(s: InventoryState, inventory: InventoryRules, deltaMs: Long): InventoryResult {
        inventory.validate(s)?.let { return InventoryResult(s, it) }
        if (deltaMs !in 0..500) return InventoryResult(s, "鉴定时间步长无效")
        val r = s.progress.run ?: return InventoryResult(s, "没有挑战")
        val loot = r.nodeLoot ?: return InventoryResult(s, "没有待鉴定物品")
        if (r.stage != JourneyStage.REWARD || s.phase != InventoryPhase.PREPARATION) return InventoryResult(s, "当前不能鉴定")
        val elapsed = (loot.inspectionMs + deltaMs).coerceAtMost(duration(loot, inventory.definitions))
        return InventoryResult(s.copy(progress = s.progress.copy(run = r.copy(nodeLoot = loot.copy(inspectionMs = elapsed)))))
    }
}
