package com.example.douqiuqiu.equipment

/**
 * 外围任务19：唯一增幅按组择强（纯 Kotlin common，无 Android / java.* / 随机数 / 时钟依赖）。
 *
 * 只处理「数值越高越强、明确同组唯一」的特殊增幅，例如散华家族恢复增强、
 * 状态抗性；夜叉家族攻速增幅；慧光家族法术增幅。同一 (groupId, effectId)
 * 只选一个 active 候选：value 最高，同值取 instanceId 字典序最小。按
 * groupId、effectId 字典序输出。
 *
 * 不同组即使 effectId 相同也分别保留，上层再按该效果自己的叠加公式计算，
 * 不能在这里直接相加。一把双刀可分别为两个家族提供候选；同一件装备同一
 * 家族也可提供不同 effectId，不能整件只留下「最贵的一把」。
 *
 * 基础力量/敏捷/智力/攻击等正常属性由主任务另行全部相加，不放入本工具；
 * 普通多源吸血/暴击/闪避也不错误地放进唯一组。本工具不比较主动技能或
 * 「越低越好」的 CD，这些有专用规则。
 *
 * 全量校验：instanceId/groupId/effectId 非空白；value 为 finite 且 >= 0；
 * (instanceId, groupId, effectId) 组合不得重复（同 instanceId 提供不同
 * 效果/家族合法）。inactive 不参与结果但仍校验。0 数值合法，可作为该组
 * 唯一获选值。输入和输出独立，不持有可变输入集合引用。
 */
object UniqueBonusSelector {

    fun select(candidates: List<UniqueBonusCandidate>): List<SelectedUniqueBonus> {
        val seenKeys = HashSet<Triple<String, String, String>>()
        for (c in candidates) {
            require(c.instanceId.isNotBlank()) { "instanceId 不能为空白" }
            require(c.groupId.isNotBlank()) { "groupId 不能为空白（${c.instanceId}）" }
            require(c.effectId.isNotBlank()) { "effectId 不能为空白（${c.instanceId}）" }
            require(c.value.isFinite() && c.value >= 0.0) {
                "value 必须 finite 且 >= 0，当前 ${c.value}（${c.instanceId}）"
            }
            val key = Triple(c.instanceId, c.groupId, c.effectId)
            require(seenKeys.add(key)) { "候选组合重复：(${c.instanceId}, ${c.groupId}, ${c.effectId})" }
        }

        // 同一 (groupId, effectId) 只选一个 active 候选：value 最高，同值 instanceId 字典序最小
        val winners = HashMap<Pair<String, String>, UniqueBonusCandidate>()
        for (c in candidates) {
            if (!c.active) continue
            val key = Pair(c.groupId, c.effectId)
            val current = winners[key]
            if (current == null ||
                c.value > current.value ||
                (c.value == current.value && c.instanceId < current.instanceId)
            ) {
                winners[key] = c
            }
        }

        // 按 groupId、effectId 字典序输出；跨组同效果不合并
        return winners.values
            .sortedWith(compareBy<UniqueBonusCandidate> { it.groupId }.thenBy { it.effectId })
            .map { SelectedUniqueBonus(it.groupId, it.effectId, it.instanceId, it.value) }
    }
}

data class UniqueBonusCandidate(
    val instanceId: String,
    val groupId: String,
    val effectId: String,
    val value: Double,
    val active: Boolean = true
)

data class SelectedUniqueBonus(
    val groupId: String,
    val effectId: String,
    val instanceId: String,
    val value: Double
)
