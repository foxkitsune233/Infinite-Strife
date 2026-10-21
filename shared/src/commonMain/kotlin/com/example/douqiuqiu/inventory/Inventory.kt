package com.example.douqiuqiu.inventory

enum class ItemKind { EQUIPMENT, CARD, MATERIAL, SCROLL, CONSUMABLE, LOOT }
enum class Container { WAREHOUSE, BACKPACK, SAFE, EQUIPMENT, SKILLS }
enum class InventoryPhase { HOME, PREPARATION, BATTLE }

data class ItemDefinition(
    val id: String, val name: String, val kind: ItemKind,
    val width: Int, val height: Int, val quality: String = "蓝",
    val maxStack: Int = 1, val combatUsable: Boolean = false,
    val stats: EquipmentStats = EquipmentStats(), val price: Int = 0, val shopStock: Int = 0,
    val shopShelf: com.example.douqiuqiu.shop.ShopShelf = com.example.douqiuqiu.shop.ShopShelf.STAPLE,
    val minimumShopClears: Int = 0, val boundOnAcquire: Boolean = false, val minimumDropClears: Int = 0
) {
    init {
        require(id.isNotBlank() && width in 1..6 && height in 1..6)
        require(maxStack in 1..3 && (maxStack == 1 || kind == ItemKind.CONSUMABLE))
        require(price >= 0 && shopStock in 0..100)
        require(minimumShopClears >= 0 && minimumDropClears >= 0)
    }
}

/** x is the zero-based slot in equipment/skills, or grid column; y is the grid row. */
data class ItemPlace(val container: Container, val x: Int = 0, val y: Int = 0, val rotated: Boolean = false)
data class StoredItem(
    val instanceId: String, val definitionId: String, val place: ItemPlace,
    val star: Int = 1, val bound: Boolean = false, val count: Int = 1
)
data class InventoryState(
    val gold: Int = 1200, val runGold: Int = 0,
    val backpackWidth: Int = 4, val backpackHeight: Int = 2,
    val safeWidth: Int = 2, val safeHeight: Int = 2, val skillSlots: Int = 4,
    val phase: InventoryPhase = InventoryPhase.HOME,
    val items: List<StoredItem> = emptyList(),
    val progress: com.example.douqiuqiu.journey.CampaignProgress = com.example.douqiuqiu.journey.CampaignProgress()
)
/**
 * 一次不可变事务的结果。
 *
 * 被拒绝时 [state] 始终是操作前的原始快照（调用方不得提交它）。
 * [refreshed] 是失败时**可供 UI 立即重读**的最新快照（例如商店已跨格刷新后的新库存/价格/倒计时状态），
 * 只用于刷新界面展示，成功路径恒为 null，任何情况下都不得当作成功态保存。
 * [notice] 是成功后可向玩家展示的一次性说明，例如旧配装被安全清理了失效引用；它不改变事务成败。
 */
data class InventoryResult(
    val state: InventoryState,
    val error: String? = null,
    val refreshed: InventoryState? = null,
    val notice: String? = null
) {
    val ok: Boolean get() = error == null
}

/** Immutable transitions: a rejected operation always returns the original state. */
class InventoryRules(val definitions: Map<String, ItemDefinition>) {
    fun validate(state: InventoryState): String? {
        if (state.gold < 0 || state.runGold < 0) return "金币数据无效"
        if ((state.backpackWidth to state.backpackHeight) !in setOf(4 to 2, 4 to 3, 5 to 3, 6 to 3, 6 to 4) ||
            (state.safeWidth to state.safeHeight) !in setOf(2 to 2, 3 to 2, 3 to 3) || state.skillSlots !in 4..8) return "容器容量无效"
        if (state.items.size > 10000 || state.items.map { it.instanceId }.toSet().size != state.items.size) return "物品实例重复或超出数量限制"
        val occupied = mutableSetOf<Triple<Container, Int, Int>>()
        val uncollected = state.progress.run?.nodeLoot?.remaining?.map { it.instanceId }?.toSet().orEmpty()
        if (state.items.any { it.instanceId in uncollected }) return "未拾取掉落不能同时属于玩家"
        for (item in state.items) {
            val def = definitions[item.definitionId] ?: return "未知物品：${item.definitionId}"
            if (item.instanceId.isBlank() || item.instanceId.length > 128 || item.star !in 1..3 ||
                (def.kind != ItemKind.CARD && item.star != 1) || item.count !in 1..def.maxStack) return "物品属性无效"
            val p = item.place
            if (p.x < 0 || p.y < 0) return "位置不能为负数"
            if (p.container == Container.WAREHOUSE) {
                if (p.x != 0 || p.y != 0 || p.rotated) return "仓库位置无效"
                continue
            }
            if (p.container == Container.EQUIPMENT || p.container == Container.SKILLS) {
                val equipment = p.container == Container.EQUIPMENT
                if (equipment && def.kind != ItemKind.EQUIPMENT && !(def.kind == ItemKind.CONSUMABLE && def.combatUsable)) return "此物品不能放进装备栏"
                if (!equipment && def.kind != ItemKind.CARD) return "技能栏只能放技能卡"
                if (p.y != 0 || p.rotated || p.x >= if (equipment) 9 else state.skillSlots) return "栏位超出范围"
                if (!occupied.add(Triple(p.container, p.x, 0))) return "栏位已被占用"
                continue
            }
            val width = if (p.container == Container.BACKPACK) state.backpackWidth else state.safeWidth
            val height = if (p.container == Container.BACKPACK) state.backpackHeight else state.safeHeight
            val w = if (p.rotated) def.height else def.width
            val h = if (p.rotated) def.width else def.height
            // Subtraction prevents overflow from a damaged persisted coordinate.
            if (p.x > width - w || p.y > height - h) return "物品超出格子范围"
            for (x in p.x until p.x + w) for (y in p.y until p.y + h) {
                if (!occupied.add(Triple(p.container, x, y))) return "格子已被占用"
            }
        }
        validateLoadouts(state)?.let { return it }
        validateGuards(state)?.let { return it }
        return com.example.douqiuqiu.journey.JourneyValidation.check(state.progress, definitions)
    }

    /**
     * 0.1.6 保护槽校验：只校验档位范围与数量上限。
     * 保护标记是**死亡白名单**，不约束物品当前位置（玩家可把受保护装备卸回仓库；
     * 物品被消耗后留下的陈旧 ID 同样容忍，与 lockedItems 的既有约定一致）。
     */
    private fun validateGuards(state: InventoryState): String? {
        val p = state.progress
        if (p.equipmentGuardLevel !in 0..3) return "装备保护档位无效"
        if (p.skillGuardLevel !in 0..4) return "技能保护档位无效"
        if (p.guardedEquipment.size > p.equipmentGuardLevel) return "装备保护数量超过档位"
        if (p.guardedSkills.size > p.skillGuardLevel) return "技能保护数量超过档位"
        return null
    }

    /**
     * 0.1.6 设置/取消死亡保护。保护为**永久有效直到手动取消**，不随死亡释放。
     * 装备与技能分别受 [CampaignProgress.equipmentGuardLevel] /
     * [CampaignProgress.skillGuardLevel] 的档位上限约束。
     */
    fun setGuard(s: InventoryState, instanceId: String, guarded: Boolean): InventoryResult {
        validate(s)?.let { return InventoryResult(s, it) }
        if (s.phase == InventoryPhase.BATTLE) return InventoryResult(s, "战斗中不能调整保护")
        val item = s.items.singleOrNull { it.instanceId == instanceId } ?: return InventoryResult(s, "找不到物品")
        val isEquipment = item.place.container == Container.EQUIPMENT
        val isSkill = item.place.container == Container.SKILLS
        if (!isEquipment && !isSkill) return InventoryResult(s, "只能保护装备栏或技能栏内的物品")
        val next = if (isEquipment) {
            if (guarded) {
                if (instanceId in s.progress.guardedEquipment) return InventoryResult(s, "该装备已受保护")
                if (s.progress.guardedEquipment.size >= s.progress.equipmentGuardLevel)
                    return InventoryResult(s, "装备保护槽已满（${s.progress.equipmentGuardLevel}格），请升级装备栏强化")
                s.copy(progress = s.progress.copy(guardedEquipment = s.progress.guardedEquipment + instanceId))
            } else s.copy(progress = s.progress.copy(guardedEquipment = s.progress.guardedEquipment - instanceId))
        } else {
            if (guarded) {
                if (instanceId in s.progress.guardedSkills) return InventoryResult(s, "该技能卡已受保护")
                if (s.progress.guardedSkills.size >= s.progress.skillGuardLevel)
                    return InventoryResult(s, "技能保护槽已满（${s.progress.skillGuardLevel}格），请升级技能栏强化")
                s.copy(progress = s.progress.copy(guardedSkills = s.progress.guardedSkills + instanceId))
            } else s.copy(progress = s.progress.copy(guardedSkills = s.progress.guardedSkills - instanceId))
        }
        return checked(s, next)
    }

    fun move(state: InventoryState, id: String, target: ItemPlace): InventoryResult {
        validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase == InventoryPhase.BATTLE) return InventoryResult(state, "战斗中不能调整物品")
        val item = state.items.singleOrNull { it.instanceId == id } ?: return InventoryResult(state, "找不到物品")
        if (state.phase != InventoryPhase.HOME && (item.place.container == Container.WAREHOUSE || target.container == Container.WAREHOUSE))
            return InventoryResult(state, "挑战中不能访问局外仓库")
        val slotted = target.container == Container.EQUIPMENT || target.container == Container.SKILLS
        val displaced = if (slotted) state.items.find { it.instanceId != id && it.place.container == target.container && it.place.x == target.x } else null
        if (displaced != null) {
            // Validate the incoming placement first; an incompatible item must never displace anything.
            val base = state.copy(items = state.items.filterNot { it.instanceId == displaced.instanceId }
                .map { if (it.instanceId == id) it.copy(place = target) else it })
            validate(base)?.let { return InventoryResult(state, it) }
            fun candidate(place: ItemPlace) = state.copy(items = state.items.map {
                when (it.instanceId) { id -> it.copy(place = target); displaced.instanceId -> it.copy(place = place); else -> it }
            })
            val gridSource = item.place.container == Container.BACKPACK || item.place.container == Container.SAFE
            val origins = if (gridSource) listOf(item.place, item.place.copy(rotated = !item.place.rotated)) else listOf(item.place)
            for (origin in origins) {
                val next = candidate(origin)
                if (validate(next) == null) return InventoryResult(next)
            }
            if (gridSource) {
                // Only the ordinary backpack is a fallback: never silently move an item into protection.
                for (y in 0 until state.backpackHeight) for (x in 0 until state.backpackWidth) for (rotated in listOf(false, true)) {
                    val next = candidate(ItemPlace(Container.BACKPACK, x, y, rotated))
                    if (validate(next) == null) return InventoryResult(next)
                }
            }
            return InventoryResult(state, "无法替换：旧物无法放回原位置，普通背包也没有可用空间")
        }
        val next = state.copy(items = state.items.map { if (it.instanceId == id) it.copy(place = target) else it })
        return checked(state, next)
    }

    fun mergeCards(state: InventoryState, ids: List<String>): InventoryResult {
        validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase != InventoryPhase.HOME) return InventoryResult(state, "仅局外可升星")
        if (ids.size != 3 || ids.toSet().size != 3) return InventoryResult(state, "需要三个不同实例")
        val cards = ids.map { id -> state.items.singleOrNull { it.instanceId == id } ?: return InventoryResult(state, "找不到材料卡") }
        if (ids.any { it in state.progress.lockedItems }) return InventoryResult(state, "锁定物品不可用于升星")
        val first = cards.first()
        if (cards.any { it.place.container != Container.WAREHOUSE || it.definitionId != first.definitionId || it.star != first.star } ||
            definitions.getValue(first.definitionId).kind != ItemKind.CARD || first.star >= 3) return InventoryResult(state, "需要仓库内三张同名同星卡，最高三星")
        val upgraded = first.copy(star = first.star + 1, bound = cards.any { it.bound })
        val next = state.copy(items = state.items.filterNot { it.instanceId in ids }.plus(upgraded))
        return checked(state, next)
    }

    /** Packs one in-run grid deterministically without touching equipped, warehouse, or the other protected grid. */
    fun organize(state: InventoryState, container: Container): InventoryResult {
        validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase == InventoryPhase.BATTLE) return InventoryResult(state, "战斗中不能整理物品")
        if (container !in setOf(Container.BACKPACK, Container.SAFE)) return InventoryResult(state, "只能整理背包或安全箱")
        val width = if (container == Container.BACKPACK) state.backpackWidth else state.safeWidth
        val height = if (container == Container.BACKPACK) state.backpackHeight else state.safeHeight
        val targets = state.items.filter { it.place.container == container }
            .sortedWith(compareByDescending<StoredItem> { item -> definitions.getValue(item.definitionId).width * definitions.getValue(item.definitionId).height }
                .thenBy { it.instanceId })
        val occupied = Array(height) { BooleanArray(width) }
        val placements = mutableMapOf<String, ItemPlace>()
        for (item in targets) {
            val definition = definitions.getValue(item.definitionId)
            val rotations = listOf(item.place.rotated, !item.place.rotated).distinct()
            var placed: ItemPlace? = null
            for (rotated in rotations) {
                val itemWidth = if (rotated) definition.height else definition.width
                val itemHeight = if (rotated) definition.width else definition.height
                for (y in 0..(height - itemHeight)) for (x in 0..(width - itemWidth)) {
                    if ((x until x + itemWidth).all { cx -> (y until y + itemHeight).all { cy -> !occupied[cy][cx] } }) {
                        placed = ItemPlace(container, x, y, rotated)
                        break
                    }
                }
                if (placed != null) break
            }
            if (placed == null) return InventoryResult(state, "无法整理：${definition.name}没有连续空位")
            val itemWidth = if (placed.rotated) definition.height else definition.width
            val itemHeight = if (placed.rotated) definition.width else definition.height
            for (x in placed.x until placed.x + itemWidth) for (y in placed.y until placed.y + itemHeight) occupied[y][x] = true
            placements[item.instanceId] = placed
        }
        return checked(state, state.copy(items = state.items.map { item -> placements[item.instanceId]?.let { item.copy(place = it) } ?: item }))
    }

    /** Call once after battle resolution, never while battle is still accepting input. */
    fun settle(state: InventoryState, survived: Boolean): InventoryResult {
        validate(state)?.let { return InventoryResult(state, it) }
        if (state.phase != InventoryPhase.PREPARATION) return InventoryResult(state, "当前阶段不能结算挑战")
        if (survived && state.gold.toLong() + state.runGold > Int.MAX_VALUE) return InventoryResult(state, "金币超出上限")
        val retained = if (survived) state.items.map { it.copy(place = ItemPlace(Container.WAREHOUSE)) }
            else {
                // 0.1.6：仓库与安全箱照旧全保留；受保护装备/技能卡按档位保留（保护为永久标记，不随死亡释放）。
                val keep = state.progress.guardedEquipment + state.progress.guardedSkills
                state.items.filter { it.place.container == Container.WAREHOUSE || it.place.container == Container.SAFE || it.instanceId in keep }
                    .map { it.copy(place = ItemPlace(Container.WAREHOUSE)) }
            }
        return checked(state, state.copy(gold = state.gold + if (survived) state.runGold else 0,
            runGold = 0, phase = InventoryPhase.HOME, items = retained))
    }

    private fun checked(original: InventoryState, next: InventoryState): InventoryResult =
        validate(next)?.let { InventoryResult(original, it) } ?: InventoryResult(next)

    private fun validateLoadouts(state: InventoryState): String? {
        val progress = state.progress
        if (progress.activeHeroId !in progress.unlockedHeroes) return "当前英雄未解锁"
        if (progress.loadouts.size > com.example.douqiuqiu.journey.FirstRunContent.heroes.size) return "配装数量无效"
        for ((heroId, presets) in progress.loadouts) {
            if (heroId !in progress.unlockedHeroes || presets.size > LoadoutRules.PRESET_COUNT) return "配装英雄资料无效"
            for (preset in presets) {
                if (preset.equipment.size != 9 || preset.skills.size != 8) return "配装栏位数量无效"
                val ids = (preset.equipment + preset.skills).filterNotNull()
                if (ids.distinct().size != ids.size || preset.manualItems.any { it !in preset.equipment }) return "配装物品资料无效"
            }
        }
        if (progress.loadoutSelections.any { (hero, slot) -> hero !in progress.unlockedHeroes || slot !in 0 until LoadoutRules.PRESET_COUNT }) return "配装选择资料无效"
        return null
    }
}
