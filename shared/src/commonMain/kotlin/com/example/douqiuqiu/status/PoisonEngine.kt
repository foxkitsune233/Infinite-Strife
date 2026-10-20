package com.example.douqiuqiu.status

/**
 * 新毒核心引擎（剩余实现交接清单 H03）。
 *
 * 规则依据 `docs/rules/poison.md` 的「已确认核心机制」一节：
 * - 普通毒：魔法类型的法术伤害，每层恰好结算 3 次伤害后消失（正常共 3 秒）；
 *   可弱驱散，强驱散也可清除。
 * - 剧毒：本体不造成伤害；每秒按**当前剧毒层数**附加等量的普通毒；持续本场；
 *   只能强驱散。
 *
 * 与旧 `PoisonTimeline` 的区别（旧模型无法表达新规则，仅改持续时间不算完成）：
 * - 旧模型按 `damagePerSecond × 推进秒数` 每 100ms 连续扣血；
 * - 新模型按**离散跳**：普通毒维护「剩余跳数 + 下次跳时刻」，剧毒维护「产毒计时」。
 *
 * 职责边界：
 * - 不读系统时钟：`nowMs` 由调用方传入，约定为「本场战斗内经过的毫秒」，
 *   这样存档恢复后同一快照继续推进仍得到相同结果；
 * - 不随机、不推进 RNG；
 * - 不计算法术增伤 / 魔抗 / 护盾 / 吸血，不检查破坏或减益免疫，不写战斗日志。
 *   产出的 [PoisonCoreTick] 是**未减伤的基础魔法法术伤害**，由上层送进 H02 的
 *   伤害管线结算（按 isPoison = true 走 `CombatMath.spellOutputMultiplier`）。
 *
 * 参数化约定：
 * - 「首次伤害/产毒延后 1 秒」是 poison.md 的**建议值而非定案**，做成
 *   [PoisonCoreConfig.firstTickDelayMs]，默认 1000，待确认后由上层统一注入；
 * - P1～P4 / D1 / D5、毒羁绊、巫师之刃 / 圣斧 / 怪蛇及 Viper 的具体附毒参数
 *   仍在审阅，**本文件不内置任何待审数值**，全部由调用方以 Spec 传入。
 */
object PoisonEngine {

    /** 单次推进允许补算的最大跳数上限，防止 nowMs 跳变导致无限循环。 */
    const val MAX_CATCHUP_TICKS: Int = 600

    /** 剧毒生成的普通毒层 ID 分隔符；外部施加的 ID 禁止包含它（保留给生成命名空间）。 */
    const val SPAWN_SEPARATOR: String = "~"

    /** 生成 ID 冲突时的去歧义前缀。 */
    const val SPAWN_DISAMBIGUATOR: String = "#"

    // ---------------- 施加 ----------------

    /**
     * 施加一层普通毒。
     *
     * 每层独立计时：首跳在 `nowMs + config.firstTickDelayMs`，之后每
     * `config.tickIntervalMs` 跳一次，`spec.ticks` 次后该层消失。
     * **新增层不会刷新任何旧层的剩余次数或下次跳时刻。**
     */
    fun addNormal(
        pool: PoisonCorePool,
        spec: NormalPoisonSpec,
        nowMs: Long,
        config: PoisonCoreConfig = PoisonCoreConfig()
    ): PoisonCorePool {
        require(nowMs >= 0L) { "nowMs 不能为负: $nowMs" }
        validatePool(pool)
        validateNormalSpec(spec)
        require(spec.ticks > 0) { "普通毒层跳数必须为正: ${spec.instanceId} -> ${spec.ticks}" }
        requireNewInstanceId(pool, spec.instanceId)

        val layer = PoisonCoreLayer(
            instanceId = spec.instanceId,
            kind = PoisonKind.NORMAL,
            sourceId = spec.sourceId,
            casterUnitId = spec.casterUnitId,
            damagePerTick = spec.damagePerTick,
            ticksRemaining = spec.ticks,
            nextTickAtMs = nowMs + config.firstTickDelayMs,
            piercesDebuffImmunity = spec.piercesDebuffImmunity
        )
        return PoisonCorePool(pool.normal + layer, ArrayList(pool.virulent))
    }

    /**
     * 施加一层剧毒。
     *
     * 剧毒本体 0 伤害、持续本场（`ticksRemaining` 固定为 0，不参与跳数逻辑），
     * 首次产毒在 `nowMs + config.firstTickDelayMs`，之后每 `config.tickIntervalMs`
     * 生成一层普通毒。不同强度的剧毒各自独立产毒，不按最强层替换全部层。
     */
    fun addVirulent(
        pool: PoisonCorePool,
        spec: VirulentPoisonSpec,
        nowMs: Long,
        config: PoisonCoreConfig = PoisonCoreConfig()
    ): PoisonCorePool {
        require(nowMs >= 0L) { "nowMs 不能为负: $nowMs" }
        validatePool(pool)
        validateVirulentSpec(spec)
        requireNewInstanceId(pool, spec.instanceId)

        val layer = PoisonCoreLayer(
            instanceId = spec.instanceId,
            kind = PoisonKind.VIRULENT,
            sourceId = spec.sourceId,
            casterUnitId = spec.casterUnitId,
            damagePerTick = spec.damagePerTick,
            ticksRemaining = 0,
            nextTickAtMs = nowMs + config.firstTickDelayMs,
            piercesDebuffImmunity = spec.piercesDebuffImmunity
        )
        return PoisonCorePool(ArrayList(pool.normal), pool.virulent + layer)
    }

    // ---------------- 推进 ----------------

    /**
     * 推进毒池到 [nowMs]，结算所有到期跳并移除耗尽层。
     *
     * 顺序固定：先由剧毒产毒，再结算普通毒。这样「剧毒产出的普通毒从自身施加
     * 时刻计时，不在生成瞬间额外跳伤害」——正常情况下两层剧毒在第 1 秒生成两层
     * 普通毒，第 2 秒这些普通毒才首次伤害。
     *
     * 若 [nowMs] 跨越多个跳时刻（存档恢复、跳帧），会按真实间隔逐跳补算；
     * 单层单次补算超过 [MAX_CATCHUP_TICKS] 视为非法跳变，直接抛错而不是静默丢弃。
     *
     * **批量补算只是「到期事件集合」，不是一整场的伤害管线。** 输出的 [PoisonCoreTick]
     * 已按 [PoisonCoreTick.atMs] 排序，但接入层仍必须按固定战斗 tick 推进，
     * 让毒伤与其它战斗事件（治疗 / 转盾 / 死亡判定）按同一时间轴交错结算；
     * 不得把后台现实的经过时间当作毒的战斗经过时间，也不得把一次批量输出
     * 越过其它事件直接补算到底。
     *
     * 生成的普通毒层在 [PoisonCoreAdvance.spawned] 中单独返回：
     * **产毒行为不是伤害事件、不是普攻、不触发暴击/吸血/反击/普攻计数**，
     * 上层不得把它当成一次伤害送进伤害管线。
     */
    fun advance(
        pool: PoisonCorePool,
        nowMs: Long,
        config: PoisonCoreConfig = PoisonCoreConfig()
    ): PoisonCoreAdvance {
        validatePool(pool)
        require(nowMs >= 0L) { "nowMs 不能为负: $nowMs" }

        val spawned = ArrayList<PoisonCoreLayer>()
        // 生成命名空间：已有层 ID + 本轮已生成的层 ID。外部 ID 在入口已被禁止占用
        // 该命名空间（见 requireExternalInstanceId），此处再兜一层，保证任何情况下
        // 都不会产出重复 ID，也就不会返回「下次操作必被 validatePool 拒绝」的损坏快照。
        val takenIds = HashSet<String>()
        for (layer in pool.layers) takenIds.add(layer.instanceId)

        // 1) 剧毒产毒
        val virulent = ArrayList<PoisonCoreLayer>(pool.virulent.size)
        for (v in pool.virulent) {
            var cursor = v
            var guard = 0
            while (cursor.nextTickAtMs <= nowMs) {
                val spawnAt = cursor.nextTickAtMs
                spawned.add(
                    PoisonCoreLayer(
                        instanceId = newSpawnInstanceId(takenIds, cursor.instanceId, spawnAt),
                        kind = PoisonKind.NORMAL,
                        sourceId = cursor.sourceId,
                        casterUnitId = cursor.casterUnitId,
                        damagePerTick = cursor.damagePerTick,
                        ticksRemaining = config.ticksPerLayer,
                        nextTickAtMs = spawnAt + config.firstTickDelayMs,
                        piercesDebuffImmunity = cursor.piercesDebuffImmunity
                    )
                )
                cursor = cursor.copy(nextTickAtMs = cursor.nextTickAtMs + config.tickIntervalMs)
                guard++
                if (guard > MAX_CATCHUP_TICKS) {
                    throw IllegalArgumentException(
                        "单次 advance 补算跳数超过上限 $MAX_CATCHUP_TICKS（nowMs 跳变过大）: ${v.instanceId}"
                    )
                }
            }
            virulent.add(cursor)
        }

        // 2) 普通毒结算（含本轮新生成的层）
        val ticks = ArrayList<PoisonCoreTick>()
        val survivors = ArrayList<PoisonCoreLayer>()
        val queued = ArrayList<PoisonCoreLayer>(pool.normal)
        queued.addAll(spawned)
        for (n in queued) {
            var cursor = n
            var guard = 0
            while (cursor.ticksRemaining > 0 && cursor.nextTickAtMs <= nowMs) {
                ticks.add(
                    PoisonCoreTick(
                        instanceId = cursor.instanceId,
                        sourceId = cursor.sourceId,
                        casterUnitId = cursor.casterUnitId,
                        amount = cursor.damagePerTick,
                        atMs = cursor.nextTickAtMs,
                        piercesDebuffImmunity = cursor.piercesDebuffImmunity
                    )
                )
                cursor = cursor.copy(
                    ticksRemaining = cursor.ticksRemaining - 1,
                    nextTickAtMs = cursor.nextTickAtMs + config.tickIntervalMs
                )
                guard++
                if (guard > MAX_CATCHUP_TICKS) {
                    throw IllegalArgumentException(
                        "单次 advance 补算跳数超过上限 $MAX_CATCHUP_TICKS（nowMs 跳变过大）: ${n.instanceId}"
                    )
                }
            }
            if (cursor.ticksRemaining > 0) survivors.add(cursor)
        }

        // 按「实际结算时刻」排序，同刻按 instanceId 字典序——该规则与池内层的排列顺序
        // 无关，因此交换池内顺序、或改为逐小步推进，得到的伤害事件序列完全一致。
        val orderedTicks = ticks.sortedWith(
            compareBy<PoisonCoreTick> { it.atMs }.thenBy { it.instanceId }
        )
        val orderedSpawned = spawned.sortedWith(compareBy<PoisonCoreLayer> { it.instanceId })
        return PoisonCoreAdvance(PoisonCorePool(survivors, virulent), orderedTicks, orderedSpawned)
    }

    // ---------------- 驱散与结束 ----------------

    /**
     * 按驱散强度筛选毒层。
     *
     * 普通毒打 [DispelClass.BASIC]（弱驱散即可清除），剧毒打
     * [DispelClass.STRONG_ONLY]（只有强驱散能清除）。两者都按
     * [DispelTarget.SELF_OR_ALLY] 的负面处理；对敌方目标（[DispelTarget.ENEMY]）
     * 一律保留，符合「不能驱散敌方负面」。
     *
     * 弱驱散只清普通毒、不清剧毒，剧毒之后仍会继续产毒；强驱散同时清普通毒与
     * 剧毒，但**不提供后续免疫窗口**。普通毒不会因为源自剧毒就变成仅强驱散。
     */
    fun dispel(
        pool: PoisonCorePool,
        strength: DispelStrength,
        target: DispelTarget = DispelTarget.SELF_OR_ALLY
    ): PoisonCoreDispel {
        validatePool(pool)
        val effects = (pool.normal + pool.virulent).map { it.toDispelEffect() }
        val result = DispelResolver.resolve(effects, strength, target)
        val removed = result.removed.map { it.instanceId }.toHashSet()
        return PoisonCoreDispel(
            pool = PoisonCorePool(
                normal = pool.normal.filterNot { it.instanceId in removed },
                virulent = pool.virulent.filterNot { it.instanceId in removed }
            ),
            removedInstanceIds = result.removed.map { it.instanceId },
            retainedInstanceIds = result.retained.map { it.instanceId }
        )
    }

    /** 本场结束：清空全部普通毒与剧毒层。 */
    fun endBattle(pool: PoisonCorePool): PoisonCorePool = PoisonCorePool()

    // ---------------- 只读查询 ----------------

    /** 汇总当前层数与下次结算时刻，供 H10 界面展示。 */
    fun counts(pool: PoisonCorePool): PoisonCoreCounts = PoisonCoreCounts(
        normalLayers = pool.normal.size,
        virulentLayers = pool.virulent.size,
        pendingNormalTicks = pool.normal.sumOf { it.ticksRemaining },
        nextNormalTickAtMs = pool.normal.minOfOrNull { it.nextTickAtMs },
        nextVirulentSpawnAtMs = pool.virulent.minOfOrNull { it.nextTickAtMs }
    )

    // ---------------- 校验 ----------------

    /** 新层 ID 不得与池中任何已有层（跨普通毒/剧毒两列）重复。 */
    private fun requireNewInstanceId(pool: PoisonCorePool, instanceId: String) {
        require(pool.layers.none { it.instanceId == instanceId }) {
            "instanceId 与池中已有层重复: $instanceId"
        }
    }

    /**
     * 剧毒产出的普通毒层 ID：`<剧毒层ID>~<产出时刻>`，冲突时追加 `#1` / `#2`…
     *
     * 该 ID 只依赖「剧毒层 ID + 产出时刻 + 冲突序号」，不依赖池内排列顺序或推进方式，
     * 因此存档恢复、跳帧补算、逐小步推进都能得到同一份可持久化的 ID。
     */
    private fun newSpawnInstanceId(takenIds: MutableSet<String>, virulentId: String, spawnAt: Long): String {
        val base = "$virulentId$SPAWN_SEPARATOR$spawnAt"
        if (takenIds.add(base)) return base
        var n = 1
        while (true) {
            val candidate = "$base$SPAWN_DISAMBIGUATOR$n"
            if (takenIds.add(candidate)) return candidate
            n++
        }
    }

    /**
     * 外部施加的毒层 ID 不得占用生成命名空间。
     *
     * 生成命名空间为 `<ID>~<时刻>`；若允许外部 ID 含 [SPAWN_SEPARATOR]，
     * 就可能出现「施加时 ID 不同、推进后产出的层 ID 恰好撞上外部层」的重复
     * （如外部普通毒 `v~1000` 与剧毒 `v` 共存，advance 到 1000ms 时产出 `v~1000`）。
     * 在入口拒绝，比在推进时才发现要干净：**拒绝时输入保持不变**，不返回损坏快照。
     */
    private fun requireExternalInstanceId(instanceId: String) {
        require(SPAWN_SEPARATOR !in instanceId) {
            "外部毒层 ID 不得占用生成命名空间（不能包含 \"$SPAWN_SEPARATOR\"）: $instanceId"
        }
    }

    private fun validateNormalSpec(spec: NormalPoisonSpec) {
        require(spec.instanceId.isNotBlank()) { "instanceId 不能为空白" }
        requireExternalInstanceId(spec.instanceId)
        require(spec.sourceId.isNotBlank()) { "sourceId 不能为空白（${spec.instanceId}）" }
        require(spec.casterUnitId.isNotBlank()) { "casterUnitId 不能为空白（${spec.instanceId}）" }
        require(spec.damagePerTick.isFinite() && spec.damagePerTick >= 0.0) {
            "damagePerTick 必须 finite 且 >= 0: ${spec.instanceId} -> ${spec.damagePerTick}"
        }
    }

    private fun validateVirulentSpec(spec: VirulentPoisonSpec) {
        require(spec.instanceId.isNotBlank()) { "instanceId 不能为空白" }
        requireExternalInstanceId(spec.instanceId)
        require(spec.sourceId.isNotBlank()) { "sourceId 不能为空白（${spec.instanceId}）" }
        require(spec.casterUnitId.isNotBlank()) { "casterUnitId 不能为空白（${spec.instanceId}）" }
        require(spec.damagePerTick.isFinite() && spec.damagePerTick >= 0.0) {
            "damagePerTick 必须 finite 且 >= 0: ${spec.instanceId} -> ${spec.damagePerTick}"
        }
    }

    internal fun validatePool(pool: PoisonCorePool) {
        val seen = HashSet<String>()
        for (layer in pool.normal) {
            validateLayer(layer)
            require(layer.kind == PoisonKind.NORMAL) {
                "普通毒列出现非普通毒层: ${layer.instanceId} -> ${layer.kind}"
            }
            require(layer.ticksRemaining > 0) {
                "普通毒层剩余跳数必须为正: ${layer.instanceId} -> ${layer.ticksRemaining}"
            }
            require(seen.add(layer.instanceId)) { "instanceId 重复: ${layer.instanceId}" }
        }
        for (layer in pool.virulent) {
            validateLayer(layer)
            require(layer.kind == PoisonKind.VIRULENT) {
                "剧毒列出现非剧毒层: ${layer.instanceId} -> ${layer.kind}"
            }
            require(layer.ticksRemaining == 0) {
                "剧毒层不使用跳数，ticksRemaining 必须为 0: ${layer.instanceId} -> ${layer.ticksRemaining}"
            }
            require(seen.add(layer.instanceId)) { "instanceId 重复: ${layer.instanceId}" }
        }
    }

    private fun validateLayer(layer: PoisonCoreLayer) {
        require(layer.instanceId.isNotBlank()) { "instanceId 不能为空白" }
        require(layer.sourceId.isNotBlank()) { "sourceId 不能为空白（${layer.instanceId}）" }
        require(layer.casterUnitId.isNotBlank()) { "casterUnitId 不能为空白（${layer.instanceId}）" }
        require(layer.damagePerTick.isFinite() && layer.damagePerTick >= 0.0) {
            "damagePerTick 必须 finite 且 >= 0: ${layer.instanceId} -> ${layer.damagePerTick}"
        }
        require(layer.nextTickAtMs >= 0L) { "nextTickAtMs 不能为负: ${layer.instanceId} -> ${layer.nextTickAtMs}" }
    }
}

/** 毒层的类型。 */
enum class PoisonKind { NORMAL, VIRULENT }

/**
 * 新毒核心的一层。
 *
 * @property instanceId 实例唯一 ID（一次施毒一个；剧毒产出的普通毒层用
 *           `<剧毒层ID>~<产出时刻>` 派生，保证确定性且不重复）
 * @property kind 普通毒 / 剧毒
 * @property sourceId 来源卡 / 装备 / 英雄 ID（可重复）
 * @property casterUnitId 原施毒单位 ID
 * @property damagePerTick 普通毒为每次伤害；剧毒为其产出的普通毒每次伤害
 * @property ticksRemaining 普通毒剩余跳数；剧毒恒为 0（不直接伤害）
 * @property nextTickAtMs 下次结算的战斗内绝对毫秒
 * @property piercesDebuffImmunity 是否穿透减益免疫；与能否驱散是两个独立标记
 */
data class PoisonCoreLayer(
    val instanceId: String,
    val kind: PoisonKind,
    val sourceId: String,
    val casterUnitId: String,
    val damagePerTick: Double,
    val ticksRemaining: Int,
    val nextTickAtMs: Long,
    val piercesDebuffImmunity: Boolean = false
) {
    /** 普通毒可弱驱散；剧毒仅强驱散。 */
    val dispelClass: DispelClass
        get() = if (kind == PoisonKind.VIRULENT) DispelClass.STRONG_ONLY else DispelClass.BASIC

    fun toDispelEffect(): DispelEffect =
        DispelEffect(instanceId, sourceId, positive = false, dispelClass = dispelClass)
}

/** 普通毒层与剧毒层分列，互不混排；两者 instanceId 合起来才做唯一性校验。 */
data class PoisonCorePool(
    val normal: List<PoisonCoreLayer> = emptyList(),
    val virulent: List<PoisonCoreLayer> = emptyList()
) {
    val layers: List<PoisonCoreLayer> get() = normal + virulent
    fun isEmpty(): Boolean = normal.isEmpty() && virulent.isEmpty()
}

/** 施加一层普通毒的参数。 */
data class NormalPoisonSpec(
    val instanceId: String,
    val sourceId: String,
    val casterUnitId: String,
    val damagePerTick: Double,
    val ticks: Int,
    val piercesDebuffImmunity: Boolean = false
)

/** 施加一层剧毒的参数。 */
data class VirulentPoisonSpec(
    val instanceId: String,
    val sourceId: String,
    val casterUnitId: String,
    val damagePerTick: Double,
    val piercesDebuffImmunity: Boolean = false
)

/**
 * 一次普通毒跳伤害（未减伤的基础魔法法术伤害）。
 *
 * @property atMs 该跳的**实际结算时刻**（战斗内绝对毫秒）。H02 接入时必须用它把毒伤
 *           与其它战斗事件（治疗 / 转盾 / 死亡判定）一起排序，不能按「本轮返回顺序」排。
 * @property piercesDebuffImmunity 是否穿透减益免疫。层在最后一跳后会被移除，
 *           上层无法再从返回池追回该标记，因此逐跳携带。
 */
data class PoisonCoreTick(
    val instanceId: String,
    val sourceId: String,
    val casterUnitId: String,
    val amount: Double,
    val atMs: Long,
    val piercesDebuffImmunity: Boolean = false
)

/** 推进结果：新池 + 本次伤害跳 + 本次由剧毒生成的普通毒层。 */
data class PoisonCoreAdvance(
    val pool: PoisonCorePool,
    val ticks: List<PoisonCoreTick>,
    val spawned: List<PoisonCoreLayer>
) {
    /** 上层送进伤害管线时应使用的伤害跳（0 伤害不产生伤害事件）。 */
    val effectiveTicks: List<PoisonCoreTick> get() = ticks.filter { it.amount > 0.0 }
}

/** 驱散结果：剩余池 + 被清除 / 被保留的实例 ID。 */
data class PoisonCoreDispel(
    val pool: PoisonCorePool,
    val removedInstanceIds: List<String>,
    val retainedInstanceIds: List<String>
)

/** 当前毒状态汇总。 */
data class PoisonCoreCounts(
    val normalLayers: Int,
    val virulentLayers: Int,
    val pendingNormalTicks: Int,
    val nextNormalTickAtMs: Long?,
    val nextVirulentSpawnAtMs: Long?
)

/**
 * 毒引擎运行参数。
 *
 * 默认值全部来自 `docs/rules/poison.md` 的**建议稿**，不是已定案数值；
 * 主任务确认后应统一注入同一份配置，不能在这里悄悄固化。
 */
data class PoisonCoreConfig(
    /** 一层普通毒的跳数（建议 3）。 */
    val ticksPerLayer: Int = 3,
    /** 相邻两跳的间隔（建议 1000ms）。 */
    val tickIntervalMs: Long = 1_000L,
    /** 施加后首次结算的延迟（建议延后 1000ms，待确认）。 */
    val firstTickDelayMs: Long = 1_000L
) {
    init {
        require(ticksPerLayer > 0) { "ticksPerLayer 必须为正: $ticksPerLayer" }
        require(tickIntervalMs > 0L) { "tickIntervalMs 必须为正: $tickIntervalMs" }
        require(firstTickDelayMs >= 0L) { "firstTickDelayMs 不能为负: $firstTickDelayMs" }
    }

    companion object {
        /** poison.md 建议稿默认值（待用户确认后统一注入）。 */
        val REVIEW_DRAFT: PoisonCoreConfig = PoisonCoreConfig()
    }
}
