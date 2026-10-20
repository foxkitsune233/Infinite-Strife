package com.example.douqiuqiu.equipment

/**
 * 装备唯一组冷却状态。
 *
 * @property remainingByGroup 各组剩余冷却（毫秒）；缺失组按 0（就绪）处理，归零项保留不删除
 * @property lastPreparedBattle 上次执行过跨战抵扣的场号；0 表示尚未进入过任何一场
 */
data class EquipmentCooldownState(
    val remainingByGroup: Map<String, Long> = emptyMap(),
    val lastPreparedBattle: Int = 0
)

/** [EquipmentCooldownTracker.tryStart] 的结果。 */
data class CooldownStartResult(
    val started: Boolean,
    val state: EquipmentCooldownState
)

/**
 * 装备唯一组冷却与跨战抵扣（外围任务 10）。
 *
 * 纯 Kotlin、不读时钟；时间单位统一 Long 毫秒。
 * 职责边界：英雄技能、消耗品数量、减 CD 比例都不在这里——cooldownMs 由上层算好最终值再传入。
 */
object EquipmentCooldownTracker {

    /**
     * 尝试启动某组的冷却。仅当该组当前就绪（剩余按 0 算）时启动并写入正 cooldownMs；
     * 冷却中返回 started = false 且状态不变（不覆盖已有剩余）。
     */
    fun tryStart(state: EquipmentCooldownState, groupId: String, cooldownMs: Long): CooldownStartResult {
        validateState(state)
        require(groupId.isNotBlank()) { "groupId 不能为空白" }
        require(cooldownMs > 0) { "cooldownMs 必须为正: $cooldownMs" }

        val remaining = state.remainingByGroup[groupId] ?: 0L
        if (remaining > 0) {
            return CooldownStartResult(false, snapshot(state))
        }
        val next = LinkedHashMap(state.remainingByGroup)
        next[groupId] = cooldownMs
        return CooldownStartResult(true, state.copy(remainingByGroup = next))
    }

    /**
     * 按实际战斗推进量扣减已有组的剩余：max(0, 剩余 - elapsedBattleMs)，归零项不删除、不新增缺失组。
     * 只能由上层传战斗推进量调用；现实等待与整备不得调用。
     */
    fun elapse(state: EquipmentCooldownState, elapsedBattleMs: Long): EquipmentCooldownState {
        validateState(state)
        require(elapsedBattleMs >= 0) { "elapsedBattleMs 不能为负: $elapsedBattleMs" }
        if (elapsedBattleMs == 0L) return snapshot(state)

        val next = LinkedHashMap<String, Long>(state.remainingByGroup.size)
        for ((groupId, remaining) in state.remainingByGroup) {
            next[groupId] = subFloorZero(remaining, elapsedBattleMs)
        }
        return state.copy(remainingByGroup = next)
    }

    /**
     * 进入一场战斗前的整备抵扣：每组 max(0, 剩余 - recoveryMs)，并记住本次场号。
     *
     * 场号规则：首次 lastPreparedBattle = 0 时必须传 1；此后只接受相同场号（幂等，不重复抵扣）
     * 或上次 +1；倒退、跳号均拒绝。重启/页面重建用相同场号重复调用不会二次扣减。
     */
    fun prepareBattle(
        state: EquipmentCooldownState,
        battleIndex: Int,
        recoveryMs: Long = 30_000
    ): EquipmentCooldownState {
        validateState(state)
        require(battleIndex > 0) { "battleIndex 必须为正: $battleIndex" }
        require(recoveryMs >= 0) { "recoveryMs 不能为负: $recoveryMs" }

        val last = state.lastPreparedBattle
        // 用 Long 比较递增，避免 Int.MAX_VALUE + 1 溢出成负数后被误判为合法
        val sameIndex = battleIndex.toLong() == last.toLong()
        val nextIndex = battleIndex.toLong() == last.toLong() + 1L
        require(sameIndex || nextIndex) { "battleIndex 倒退或跳号: last=$last, given=$battleIndex" }
        if (sameIndex) return snapshot(state)
        if (recoveryMs == 0L) {
            return state.copy(lastPreparedBattle = battleIndex)
        }

        val next = LinkedHashMap<String, Long>(state.remainingByGroup.size)
        for ((groupId, remaining) in state.remainingByGroup) {
            next[groupId] = subFloorZero(remaining, recoveryMs)
        }
        return state.copy(remainingByGroup = next, lastPreparedBattle = battleIndex)
    }

    /** 先比较再减法，杜绝下溢；结果恒在 0..remaining。 */
    private fun subFloorZero(remaining: Long, amount: Long): Long =
        if (remaining <= amount) 0L else remaining - amount

    private fun snapshot(state: EquipmentCooldownState): EquipmentCooldownState =
        state.copy(remainingByGroup = LinkedHashMap(state.remainingByGroup))

    private fun validateState(state: EquipmentCooldownState) {
        require(state.lastPreparedBattle >= 0) { "lastPreparedBattle 不能为负: ${state.lastPreparedBattle}" }
        for ((groupId, remaining) in state.remainingByGroup) {
            require(groupId.isNotBlank()) { "groupId 不能为空白" }
            require(remaining >= 0) { "剩余冷却不能为负: $groupId -> $remaining" }
        }
    }
}
