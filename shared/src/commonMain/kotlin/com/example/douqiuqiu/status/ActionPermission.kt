package com.example.douqiuqiu.status

/** 可执行的动作类型。 */
enum class CombatAction { HERO_SKILL, ACTIVE_ITEM, CONSUMABLE, BASIC_ATTACK }

/** 阻止原因（声明顺序即结果集合的稳定顺序）。 */
enum class ActionBlock { DEAD, BANISHED, STUNNED, HEXED, SILENCED, MUTED, DISARMED, FROZEN, TIME_STOPPED }

/**
 * 控制状态快照（由调用方按当前生效状态提供）。
 */
data class ActionControlState(
    val dead: Boolean = false,
    val banished: Boolean = false,
    val stunned: Boolean = false,
    val hexed: Boolean = false,
    val silenced: Boolean = false,
    val muted: Boolean = false,
    val disarmed: Boolean = false,
    val taunted: Boolean = false,
    val frozen: Boolean = false,
    val timeStopped: Boolean = false
)

/** 权限检查结果；allowed 当且仅当 blockedBy 为空。 */
data class ActionPermissionResult(
    val allowed: Boolean,
    val blockedBy: Set<ActionBlock>
)

/**
 * 动作使用权限检查（外围任务 13）。
 *
 * 只判断状态权限：不扣魔法、不算 CD、不驱散、不执行攻击或触发自动物品。
 * 上层必须先做权限检查，再执行驱散等后续动作。
 *
 * 规则表：
 * - 死亡 / 放逐：禁止所有动作，分别记 DEAD / BANISHED，不清空其他有效原因。
 * - 英雄技能：眩晕、妖术、沉默、冻结、时停禁止；禁用物品、缴械不禁。
 * - 主动物品 / 消耗品：眩晕、妖术、禁用物品、冻结、时停禁止；沉默、缴械不禁。
 * - 普通攻击：眩晕、妖术、缴械、冻结、时停禁止；沉默、禁用物品不禁。
 * - TAUNT：单挑语义下不改变目标、不禁止动作，仅作为状态保留（为多目标预留）。
 * - allowStunnedHeroSkill 仅对英雄技能生效（无光之盾类特例），对物品/普攻无效，也不豁免妖术/沉默/冻结/时停。
 */
object ActionPermission {

    fun check(
        state: ActionControlState,
        action: CombatAction,
        allowStunnedHeroSkill: Boolean = false
    ): ActionPermissionResult {
        // 按 ActionBlock 声明顺序收集，保证稳定顺序
        val blocked = LinkedHashSet<ActionBlock>()
        if (state.dead) blocked.add(ActionBlock.DEAD)
        if (state.banished) blocked.add(ActionBlock.BANISHED)
        // 时停暂停目标的一切行动：普攻、技能施放与物品使用全部禁止。
        if (state.timeStopped) blocked.add(ActionBlock.TIME_STOPPED)
        when (action) {
            CombatAction.HERO_SKILL -> {
                if (state.stunned && !allowStunnedHeroSkill) blocked.add(ActionBlock.STUNNED)
                if (state.hexed) blocked.add(ActionBlock.HEXED)
                if (state.silenced) blocked.add(ActionBlock.SILENCED)
                if (state.frozen) blocked.add(ActionBlock.FROZEN)
            }
            CombatAction.ACTIVE_ITEM, CombatAction.CONSUMABLE -> {
                // 特例对物品无效，不给 BKB 开后门
                if (state.stunned) blocked.add(ActionBlock.STUNNED)
                if (state.hexed) blocked.add(ActionBlock.HEXED)
                if (state.muted) blocked.add(ActionBlock.MUTED)
                if (state.frozen) blocked.add(ActionBlock.FROZEN)
            }
            CombatAction.BASIC_ATTACK -> {
                if (state.stunned) blocked.add(ActionBlock.STUNNED)
                if (state.hexed) blocked.add(ActionBlock.HEXED)
                if (state.disarmed) blocked.add(ActionBlock.DISARMED)
                if (state.frozen) blocked.add(ActionBlock.FROZEN)
            }
        }
        return ActionPermissionResult(blocked.isEmpty(), blocked)
    }
}
