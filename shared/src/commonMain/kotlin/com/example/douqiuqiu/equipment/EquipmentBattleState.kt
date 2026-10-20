package com.example.douqiuqiu.equipment

/**
 * 战斗内的装备运行时状态。
 *
 * 先落 H06 需要的减益免疫计时（黑皇杖 BKB）：[immunityMs] > 0 时**新的**减益
 * （普通毒/剧毒等）无法施加，已经存在的持续伤害与状态不受影响；需要穿透免疫的
 * 来源必须在施加参数里显式声明（如毒层的 `piercesDebuffImmunity`），这里不提供
 * 「一律穿透」的旁路。
 *
 * 计时由战斗引擎每 tick 扣减（100ms），存档随 v8 一并持久化；H05/H06 接入具体
 * 装备被动物品时再扩展其余字段，不预造占位。
 */
data class EquippedGear(val instanceId: String, val definitionId: String)

data class EquipmentBattleState(
    val immunityMs: Long = 0L,
    val items: List<EquippedGear> = emptyList(),
    val cooldowns: EquipmentCooldownState = EquipmentCooldownState(),
    val activeGroups: Map<String, Long> = emptyMap(),
    val protectionMs: Long = 0L,
    val started: Boolean = true,
    val manual: Set<String> = emptySet(),
    val criticalFailures: Map<String, Int> = emptyMap()
)
