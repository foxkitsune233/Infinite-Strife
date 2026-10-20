package com.example.douqiuqiu.combat

/**
 * 外围任务18：多来源暴击优先判定（纯 Kotlin common，无 Android / java.* / 随机数 / 时钟依赖）。
 *
 * 规则依据 confirmed-rules：多来源按倍率从高到低判定，一次命中只能有一个
 * 暴击，不连乘；同倍率按 instanceId 字典序。对每个 active 来源比较
 * `roll < threshold`，首次成功立即停止，后续来源不再检查。
 *
 * threshold 可代表上层伪随机累积后的当次阈值，不把它固定解释为面板基础
 * 概率；PRD 参数校准及状态推进留给后续主任务。roll 是上层已提供的判定值，
 * 本工具不读随机数、不推进 seed、不更新伪随机失败次数。
 *
 * bonusMultiplier 是暴击倍率的绝对增量（PA +30 个百分点传 0.30）：仅成功
 * 暴击时加到获选倍率上，不加到普通 1.0 攻击上。PA 确保暴击以
 * multiplier=3.0 / threshold=1 的来源表达，与其他来源仍择强判定。
 * 本工具不扣除「下次攻击」状态，该状态仅成功攻击后由上层消费。
 */
object CriticalStrikeResolver {

    fun resolve(
        sources: List<CriticalStrikeSource>,
        bonusMultiplier: Double = 0.0
    ): CriticalStrikeResult {
        require(bonusMultiplier.isFinite() && bonusMultiplier >= 0.0) {
            "bonusMultiplier 必须 finite 且 >= 0，当前 $bonusMultiplier"
        }

        val seenIds = HashSet<String>()
        for (s in sources) {
            require(s.instanceId.isNotBlank()) { "instanceId 不能为空白" }
            require(seenIds.add(s.instanceId)) { "instanceId 重复：${s.instanceId}" }
            require(s.multiplier.isFinite() && s.multiplier > 1.0) {
                "multiplier 必须 finite 且 > 1，当前 ${s.multiplier}（${s.instanceId}）"
            }
            require(s.threshold.isFinite() && s.threshold in 0.0..1.0) {
                "threshold 必须 finite 且在 [0,1]，当前 ${s.threshold}（${s.instanceId}）"
            }
            require(s.roll.isFinite() && s.roll >= 0.0 && s.roll < 1.0) {
                "roll 必须 finite 且在 [0,1)，当前 ${s.roll}（${s.instanceId}）"
            }
        }

        // 倍率从高到低，同倍率按 instanceId 字典序；一次命中只判定一个来源
        val ordered = sources.filter { it.active }
            .sortedWith(
                compareByDescending<CriticalStrikeSource> { it.multiplier }.thenBy { it.instanceId }
            )

        val evaluated = ArrayList<String>(ordered.size)
        for (s in ordered) {
            evaluated.add(s.instanceId)
            if (s.roll < s.threshold) {
                // 仅成功暴击才加增量；相加溢出明确抛错，不静默返回 Infinity
                val multiplier = s.multiplier + bonusMultiplier
                require(multiplier.isFinite()) {
                    "暴击倍率相加溢出：${s.multiplier} + $bonusMultiplier"
                }
                return CriticalStrikeResult(
                    critical = true,
                    multiplier = multiplier,
                    sourceId = s.instanceId,
                    evaluatedSourceIds = evaluated
                )
            }
        }

        // 无 active 或全失败：普通攻击倍率 1.0，不因无用加法制造溢出
        return CriticalStrikeResult(
            critical = false,
            multiplier = 1.0,
            sourceId = null,
            evaluatedSourceIds = evaluated
        )
    }
}

data class CriticalStrikeSource(
    val instanceId: String,
    val multiplier: Double,
    val threshold: Double,
    val roll: Double,
    val active: Boolean = true
)

data class CriticalStrikeResult(
    val critical: Boolean,
    val multiplier: Double,
    val sourceId: String?,
    val evaluatedSourceIds: List<String>
)
