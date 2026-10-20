package com.example.douqiuqiu.cards

/**
 * 一张已放置的技能卡实例。
 *
 * @property instanceId 实例唯一 ID（全局唯一，仅用于区分多实例）
 * @property cardId 卡种 ID（同名不同星、同卡种多实例，在同一流派里只算一种）
 * @property tags 1~2 个流派标签；双标签分别贡献两个流派
 * @property star 星级 1..3
 * @property active 是否处于生效位置（技能栏）；背包/安全箱/仓库传 false
 */
data class SynergyCard(
    val instanceId: String,
    val cardId: String,
    val tags: Set<String>,
    val star: Int = 1,
    val active: Boolean = true
)

/**
 * 某个流派的羁绊统计结果。
 *
 * @property schoolId 流派 ID（等于标签名）
 * @property uniqueCardIds 该流派下去重后的卡种 ID，按字典序
 * @property activeTiers 已达到的档位（取 [thresholds] 中 <= uniqueCardIds.size 的部分）
 */
data class SchoolSynergy(
    val schoolId: String,
    val uniqueCardIds: Set<String>,
    val activeTiers: List<Int>
)

/**
 * 技能卡羁绊计数（外围任务 08）。
 *
 * 只做「卡种去重 + 标签归类 + 档位判定」，不应用任何属性、不触发技能。
 * 输入里 active = false 的卡不参与计数，但仍参与合法性校验。
 */
object SynergyCounter {

    /**
     * @param cards 技能卡实例列表（含 inactive，调用方按技能栏提供）
     * @param thresholds 档位门槛，必须非空、全为正数且严格递增
     */
    fun count(cards: List<SynergyCard>, thresholds: List<Int> = listOf(2, 3, 4)): List<SchoolSynergy> {
        validateThresholds(thresholds)
        validateCards(cards)
        if (cards.isEmpty()) return emptyList()

        // 流派 -> 去重卡种集合（只收 active 的卡）
        val bySchool = HashMap<String, MutableSet<String>>()
        for (c in cards) {
            if (!c.active) continue
            for (tag in c.tags) {
                bySchool.getOrPut(tag) { HashSet() }.add(c.cardId)
            }
        }

        return bySchool.entries
            .sortedBy { it.key }
            .map { (schoolId, ids) ->
                // 字典序稳定集合：先排序再放入 LinkedHashSet，不引用输入集合
                val sortedIds = LinkedHashSet<String>()
                for (id in ids.sorted()) sortedIds.add(id)
                val tiers = ArrayList<Int>()
                for (t in thresholds) {
                    if (sortedIds.size >= t) tiers.add(t)
                }
                SchoolSynergy(schoolId, sortedIds, tiers)
            }
    }

    private fun validateThresholds(thresholds: List<Int>) {
        require(thresholds.isNotEmpty()) { "thresholds 不能为空" }
        var prev = 0
        for (t in thresholds) {
            require(t > 0) { "thresholds 必须全为正数: $thresholds" }
            require(t > prev) { "thresholds 必须严格递增: $thresholds" }
            prev = t
        }
    }

    private fun validateCards(cards: List<SynergyCard>) {
        val seenInstances = HashSet<String>()
        val tagsByCard = HashMap<String, Set<String>>()
        for (c in cards) {
            require(c.instanceId.isNotBlank()) { "instanceId 不能为空白" }
            require(seenInstances.add(c.instanceId)) { "instanceId 重复: ${c.instanceId}" }
            require(c.cardId.isNotBlank()) { "cardId 不能为空白" }
            require(c.star in 1..3) { "star 必须在 1..3: ${c.instanceId} -> ${c.star}" }
            require(c.tags.size in 1..2) { "每张卡必须有 1~2 个标签: ${c.instanceId} -> ${c.tags.size}" }
            for (t in c.tags) {
                require(t.isNotBlank()) { "标签不能为空白: ${c.instanceId}" }
            }
            val known = tagsByCard.put(c.cardId, c.tags)
            if (known != null) {
                require(known == c.tags) { "同名卡标签冲突: ${c.cardId} -> $known vs ${c.tags}" }
            }
        }
    }
}
