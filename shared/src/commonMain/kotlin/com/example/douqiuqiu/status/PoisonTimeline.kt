package com.example.douqiuqiu.status

import com.example.douqiuqiu.status.DispelClass

/**
 * 一层独立的毒。
 *
 * @property instanceId 实例唯一 ID（一次施毒一个，同来源可多层）
 * @property sourceId 来源 ID（可重复）
 * @property damagePerSecond 每秒伤害（未减伤的基础魔法法术伤害）；可 0 不可负
 * @property remainingMs 剩余毫秒，必须 > 0
 * @property dispelClass 驱散标记，原样保留交由任务 09 筛选
 */
data class PoisonLayer(
    val instanceId: String,
    val sourceId: String,
    val damagePerSecond: Double,
    val remainingMs: Long,
    val dispelClass: DispelClass = DispelClass.BASIC
)

/** 毒池。 */
data class PoisonPool(val layers: List<PoisonLayer> = emptyList())

/** 单层本次造成的伤害。 */
data class PoisonDamage(
    val instanceId: String,
    val sourceId: String,
    val amount: Double
)

/** 推进结果：剩余层 + 本次伤害明细。 */
data class PoisonAdvanceResult(
    val pool: PoisonPool,
    val damage: List<PoisonDamage>
)

/**
 * 逐层中毒时间推进（外围任务 12）。
 *
 * 只推进时间并计算未减伤的基础魔法法术伤害：不读时钟、不随机、
 * 不计算法术增伤/魔抗/护盾/吸血，不检查破坏或免疫。
 * 驱散由任务 09 按 instanceId 筛选后，由主任务更新 pool。
 */
object PoisonTimeline {

    /** 新增一层毒；instanceId 不得与池中已有层重复。 */
    fun add(pool: PoisonPool, layer: PoisonLayer): PoisonPool {
        validateLayers(pool.layers)
        validateLayer(layer)
        require(pool.layers.none { it.instanceId == layer.instanceId }) {
            "instanceId 重复: ${layer.instanceId}"
        }
        val next = ArrayList<PoisonLayer>(pool.layers)
        next.add(layer)
        return PoisonPool(next)
    }

    /**
     * 推进 elapsedMs：单层伤害 = damagePerSecond × (min(elapsedMs, remainingMs) / 1000.0)。
     * 到期层移除，未到期层只扣本次实际推进量；damage 仅含 amount > 0 的层并按输入顺序。
     */
    fun advance(pool: PoisonPool, elapsedMs: Long): PoisonAdvanceResult {
        validateLayers(pool.layers)
        require(elapsedMs >= 0L) { "elapsedMs 不能为负: $elapsedMs" }
        if (elapsedMs == 0L) {
            return PoisonAdvanceResult(PoisonPool(ArrayList<PoisonLayer>(pool.layers)), emptyList())
        }

        val damages = ArrayList<PoisonDamage>(pool.layers.size)
        val survivors = ArrayList<PoisonLayer>(pool.layers.size)
        for (layer in pool.layers) {
            val advancedMs = minOf(elapsedMs, layer.remainingMs)
            // 先转小数秒，保留不足一秒的伤害
            val seconds = advancedMs / 1000.0
            val amount = layer.damagePerSecond * seconds
            require(amount.isFinite()) { "毒伤计算溢出: ${layer.instanceId} -> $amount" }
            if (amount > 0.0) {
                damages.add(PoisonDamage(layer.instanceId, layer.sourceId, amount))
            }
            val left = layer.remainingMs - advancedMs
            if (left > 0L) survivors.add(layer.copy(remainingMs = left))
        }
        return PoisonAdvanceResult(PoisonPool(survivors), damages)
    }

    private fun validateLayers(layers: List<PoisonLayer>) {
        val seen = HashSet<String>()
        for (layer in layers) {
            validateLayer(layer)
            require(seen.add(layer.instanceId)) { "instanceId 重复: ${layer.instanceId}" }
        }
    }

    private fun validateLayer(layer: PoisonLayer) {
        require(layer.instanceId.isNotBlank()) { "instanceId 不能为空白" }
        require(layer.sourceId.isNotBlank()) { "sourceId 不能为空白" }
        require(layer.damagePerSecond.isFinite()) { "damagePerSecond 必须有限: ${layer.damagePerSecond}" }
        require(layer.damagePerSecond >= 0.0) { "damagePerSecond 不能为负: ${layer.damagePerSecond}" }
        require(layer.remainingMs > 0L) { "remainingMs 必须 > 0: ${layer.remainingMs}" }
    }
}
