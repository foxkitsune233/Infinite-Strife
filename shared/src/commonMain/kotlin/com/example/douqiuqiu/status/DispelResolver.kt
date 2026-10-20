package com.example.douqiuqiu.status

/** 状态的可驱散分类（由调用方打标，本工具不按名称猜测）。 */
enum class DispelClass { BASIC, STRONG_ONLY, UNDISPELLABLE }

/** 驱散强度。 */
enum class DispelStrength { BASIC, STRONG }

/** 驱散目标阵营。 */
enum class DispelTarget { SELF_OR_ALLY, ENEMY }

/**
 * 一个状态实例。
 *
 * @property instanceId 实例唯一 ID
 * @property sourceId 来源 ID（同来源可有多个实例乃至不同驱散标记）
 * @property positive 是否为增益
 * @property dispelClass 可驱散分类
 */
data class DispelEffect(
    val instanceId: String,
    val sourceId: String,
    val positive: Boolean,
    val dispelClass: DispelClass
)

/**
 * 驱散筛选结果：retained 与 removed 互斥，合起来恰好等于输入实例。
 */
data class DispelResult(
    val retained: List<DispelEffect>,
    val removed: List<DispelEffect>
)

/**
 * 按来源标记筛选驱散状态（外围任务 09）。
 *
 * 只做筛选：不施放技能、不判断施放权限、不赋予免疫、不推进计时或重挂光环。
 *
 * - 己方/友方：只清负面；弱清 BASIC，强清 BASIC + STRONG_ONLY；UNDISPELLABLE 与全部正面保留。
 * - 敌方：只清 BASIC 正面；负面一律保留；强驱散不会扩大到 STRONG_ONLY 增益
 *   （强驱散的额外能力只用于解友方负面）。
 */
object DispelResolver {

    fun resolve(
        effects: List<DispelEffect>,
        strength: DispelStrength,
        target: DispelTarget
    ): DispelResult {
        val seen = HashSet<String>()
        for (e in effects) {
            require(e.instanceId.isNotBlank()) { "instanceId 不能为空白" }
            require(e.sourceId.isNotBlank()) { "sourceId 不能为空白" }
            require(seen.add(e.instanceId)) { "instanceId 重复: ${e.instanceId}" }
        }

        val retained = ArrayList<DispelEffect>(effects.size)
        val removed = ArrayList<DispelEffect>()
        for (e in effects) {
            if (shouldDispel(e, strength, target)) removed.add(e) else retained.add(e)
        }
        return DispelResult(retained, removed)
    }

    private fun shouldDispel(e: DispelEffect, strength: DispelStrength, target: DispelTarget): Boolean =
        when (target) {
            DispelTarget.SELF_OR_ALLY -> {
                if (e.positive) {
                    false // 增益一律保留
                } else {
                    when (e.dispelClass) {
                        DispelClass.BASIC -> true
                        DispelClass.STRONG_ONLY -> strength == DispelStrength.STRONG
                        DispelClass.UNDISPELLABLE -> false
                    }
                }
            }
            DispelTarget.ENEMY -> {
                // 敌方只清 BASIC 增益；强度不扩大范围，负面一律保留
                e.positive && e.dispelClass == DispelClass.BASIC
            }
        }
}
