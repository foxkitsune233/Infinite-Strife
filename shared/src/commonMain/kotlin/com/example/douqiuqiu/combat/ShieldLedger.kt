package com.example.douqiuqiu.combat

/** 护盾类型：通用 / 仅挡物理 / 仅挡魔法。 */
enum class ShieldKind { UNIVERSAL, PHYSICAL, MAGICAL }

/** 同来源重新施加时的合并模式。 */
enum class ShieldRefreshMode {
    /** 普通重新施加：与当前剩余取较高值，时长刷新 */
    MAX_REMAINING,
    /** 天怒等明确例外：同来源剩余 + 新增，受 cap 封顶 */
    ADD_CAPPED
}

/** 伤害类型（已完成减伤后传入）。 */
enum class ShieldDamageKind { PHYSICAL, MAGICAL, PURE }

/**
 * 一条护盾。
 *
 * @property sourceId 来源 ID（池内唯一；不同来源独立叠加）
 * @property kind 护盾类型
 * @property amount 当前吸收量
 * @property remainingMs 剩余毫秒；null 表示本场无到期时间
 */
data class ShieldEntry(
    val sourceId: String,
    val kind: ShieldKind,
    val amount: Double,
    val remainingMs: Long? = null
)

/** 护盾池。 */
data class ShieldPool(val entries: List<ShieldEntry> = emptyList())

/** 一次吸收的结果。 */
data class ShieldHitResult(
    val pool: ShieldPool,
    val absorbed: Double,
    val remainingDamage: Double
)

/**
 * 分来源护盾账本（外围任务 11）。
 *
 * 只算护盾的施加/刷新/到期/吸收，不计算实际生命、过量击杀或吸血；
 * 吸收的输入伤害必须是已完成护甲/魔抗减伤后的值。
 */
object ShieldLedger {

    /**
     * 施加一条护盾。
     *
     * - 新来源直接新增；同来源类型必须一致。
     * - [ShieldRefreshMode.MAX_REMAINING]：与当前剩余取较高值，时长刷新为 incoming 的时长；cap 必须为 null。
     * - [ShieldRefreshMode.ADD_CAPPED]：同来源剩余 + 新增并按 cap 封顶（只限制该来源）；cap 必须有限为正。
     * - incoming.amount = 0 为无操作：不新增也不刷新。
     */
    fun apply(
        pool: ShieldPool,
        incoming: ShieldEntry,
        mode: ShieldRefreshMode = ShieldRefreshMode.MAX_REMAINING,
        cap: Double? = null
    ): ShieldPool {
        validatePool(pool)
        require(incoming.sourceId.isNotBlank()) { "sourceId 不能为空白" }
        requireFinite(incoming.amount, "amount")
        require(incoming.amount >= 0.0) { "amount 不能为负: ${incoming.amount}" }
        require(incoming.remainingMs == null || incoming.remainingMs > 0L) {
            "remainingMs 必须为 null 或 > 0: ${incoming.remainingMs}"
        }
        when (mode) {
            ShieldRefreshMode.ADD_CAPPED ->
                require(cap != null && cap.isFinite() && cap > 0.0) { "ADD_CAPPED 必须传有限正 cap: $cap" }
            ShieldRefreshMode.MAX_REMAINING ->
                require(cap == null) { "MAX_REMAINING 模式 cap 必须为 null: $cap" }
        }
        // amount = 0：无操作，不新增也不刷新（连时长都不动）
        if (incoming.amount == 0.0) return snapshot(pool)

        val index = pool.entries.indexOfFirst { it.sourceId == incoming.sourceId }
        if (index < 0) {
            val next = ArrayList<ShieldEntry>(pool.entries)
            next.add(if (mode == ShieldRefreshMode.ADD_CAPPED) incoming.copy(amount = minOf(incoming.amount, cap!!)) else incoming)
            return ShieldPool(next)
        }
        val existing = pool.entries[index]
        require(existing.kind == incoming.kind) {
            "同来源类型不得改变: ${incoming.sourceId} -> ${existing.kind} vs ${incoming.kind}"
        }

        val merged = when (mode) {
            ShieldRefreshMode.MAX_REMAINING -> existing.copy(
                amount = maxOf(existing.amount, incoming.amount),
                remainingMs = incoming.remainingMs
            )
            ShieldRefreshMode.ADD_CAPPED -> {
                val total = existing.amount + incoming.amount
                requireFinite(total, "ADD_CAPPED 累计")
                existing.copy(
                    amount = minOf(total, cap!!),
                    // 与 MAX 模式一致：重新施加刷新时长
                    remainingMs = incoming.remainingMs
                )
            }
        }
        val next = ArrayList<ShieldEntry>(pool.entries)
        next[index] = merged
        return ShieldPool(next)
    }

    /**
     * 按伤害类型吸收：物理先 PHYSICAL 后 UNIVERSAL，魔法先 MAGICAL 后 UNIVERSAL，PURE 不消耗任何盾。
     * 同类内部按最早到期优先（null 最后）、同到期按 sourceId 字典序；扣空的条目移除，其余保持原池相对顺序。
     */
    fun absorb(pool: ShieldPool, damage: Double, kind: ShieldDamageKind): ShieldHitResult {
        validatePool(pool)
        requireFinite(damage, "damage")
        require(damage >= 0.0) { "damage 不能为负: $damage" }
        if (damage == 0.0 || kind == ShieldDamageKind.PURE) {
            return ShieldHitResult(snapshot(pool), 0.0, damage)
        }

        val preferred = if (kind == ShieldDamageKind.PHYSICAL) ShieldKind.PHYSICAL else ShieldKind.MAGICAL
        val remaining = ArrayList<Double>(pool.entries.size)
        for (e in pool.entries) remaining.add(e.amount)

        // 每次只扣不超过剩余伤害的量，避免累计吸收量的浮点舍入
        // 超过原始伤害，向生命/吸血管线泄漏微小负值。
        var remainingDamage = damage
        // 先专用后通用
        for (k in listOf(preferred, ShieldKind.UNIVERSAL)) {
            if (remainingDamage <= 0.0) break
            val candidates = pool.entries.indices
                .filter { pool.entries[it].kind == k }
                .sortedWith(
                    compareBy(
                        { pool.entries[it].remainingMs == null },
                        { expiryRank(pool.entries[it].remainingMs) },
                        { pool.entries[it].sourceId }
                    )
                )
            for (i in candidates) {
                if (remainingDamage <= 0.0) break
                val take = minOf(remaining[i], remainingDamage)
                remaining[i] = remaining[i] - take
                remainingDamage -= take
            }
        }

        val next = ArrayList<ShieldEntry>(pool.entries.size)
        for (i in pool.entries.indices) {
            val amt = remaining[i]
            if (amt > 0.0) next.add(pool.entries[i].copy(amount = amt))
        }
        val absorbedTotal = damage - remainingDamage
        return ShieldHitResult(ShieldPool(next), absorbedTotal, remainingDamage)
    }

    /** 按战斗推进时间扣剩余时长，扣到 0 移除；永久条目（null）不变。 */
    fun elapse(pool: ShieldPool, elapsedMs: Long): ShieldPool {
        validatePool(pool)
        require(elapsedMs >= 0L) { "elapsedMs 不能为负: $elapsedMs" }
        if (elapsedMs == 0L) return snapshot(pool)

        val next = ArrayList<ShieldEntry>(pool.entries.size)
        for (e in pool.entries) {
            val rem = e.remainingMs
            if (rem == null) {
                next.add(e)
            } else {
                val left = rem - elapsedMs
                if (left > 0L) next.add(e.copy(remainingMs = left))
            }
        }
        return ShieldPool(next)
    }

    /** 三类护盾当前 amount 之和。 */
    fun total(pool: ShieldPool): Double {
        validatePool(pool)
        var sum = 0.0
        for (e in pool.entries) {
            sum += e.amount
            requireFinite(sum, "total")
        }
        return sum
    }

    /** 到期排序权重：null（永久）排最后。 */
    private fun expiryRank(remainingMs: Long?): Long = remainingMs ?: Long.MAX_VALUE

    private fun snapshot(pool: ShieldPool): ShieldPool =
        ShieldPool(ArrayList<ShieldEntry>(pool.entries))

    private fun validatePool(pool: ShieldPool) {
        val seen = HashSet<String>()
        for (e in pool.entries) {
            require(e.sourceId.isNotBlank()) { "sourceId 不能为空白" }
            require(seen.add(e.sourceId)) { "sourceId 池内重复: ${e.sourceId}" }
            requireFinite(e.amount, "amount")
            require(e.amount > 0.0) { "已有池条目 amount 必须 > 0: ${e.sourceId} -> ${e.amount}" }
            require(e.remainingMs == null || e.remainingMs > 0L) {
                "remainingMs 必须为 null 或 > 0: ${e.sourceId} -> ${e.remainingMs}"
            }
        }
    }

    private fun requireFinite(value: Double, name: String) {
        require(value.isFinite()) { "$name 必须为有限值: $value" }
    }
}
