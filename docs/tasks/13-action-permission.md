# 外围任务13：动作使用权限检查

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/status/ActionPermission.kt` 与 `app/src/test/java/com/example/douqiuqiu/status/ActionPermissionTest.kt`。纯Kotlin，只判断状态权限；不扣魔法、不算CD、不驱散、不执行攻击或触发自动物品，不修改模型/引擎/UI/Gradle。

```kotlin
enum class CombatAction { HERO_SKILL, ACTIVE_ITEM, CONSUMABLE, BASIC_ATTACK }
enum class ActionBlock { DEAD, BANISHED, STUNNED, HEXED, SILENCED, MUTED, DISARMED }
data class ActionControlState(
    val dead: Boolean = false, val banished: Boolean = false,
    val stunned: Boolean = false, val hexed: Boolean = false,
    val silenced: Boolean = false, val muted: Boolean = false, val disarmed: Boolean = false
)
data class ActionPermissionResult(val allowed: Boolean, val blockedBy: Set<ActionBlock>)
object ActionPermission {
    fun check(state: ActionControlState, action: CombatAction, allowStunnedHeroSkill: Boolean = false): ActionPermissionResult
}
```

规则表：
- 死亡或放逐/隐藏：所有动作禁止。分别返回DEAD/BANISHED原因，不清空其他有效阻止原因。
- 英雄技能：眩晕、妖术、沉默禁止；禁用物品或缴械本身不禁技能。只有allowStunnedHeroSkill=true可忽略眩晕，不能忽略妖术/沉默/死亡/放逐。
- 主动物品及消耗品：眩晕、妖术、禁用物品禁止；沉默、缴械本身不禁止。allowStunnedHeroSkill对这两类没有作用，不能给BKB开后门。
- 普通攻击：眩晕、妖术、缴械禁止；沉默、禁用物品本身不禁止。此处不处理无敌斩期间普通攻击锁定，特殊技能流程由主任务额外约束。
- 收集本动作的全部阻止原因，allowed当且仅当blockedBy为空；原因按ActionBlock枚举声明顺序形成稳定集合。
- 无光之盾传HERO_SKILL且允许眩晕特例；普通大招不传特例。BKB/撒旦传ACTIVE_ITEM，仙灵之火/奶酪传CONSUMABLE。
- 原力法杖受控触发是被动，不通过本工具的主动物品入口；不要修改已有推推棒机制。
- blockedBy为独立结果集合，不执行任何副作用。所有状态组合都是合法输入，不因同时眩晕/沉默就抛异常。

测试至少：全空允许四类动作；逐个状态×四类动作矩阵；眩晕护盾特例允许；眩晕+沉默/妖术仍禁止护盾；特例不影响物品或普攻；沉默可用BKB/药品；缴械可用撒旦；禁用物品不禁大招/普攻；死亡/放逐全禁止；多个原因完整保留且顺序稳定；结果无副作用。

验收：`:app:testDebugUnitTest --tests '*ActionPermissionTest'`、`:shared:compileKotlinJs`。不自行加入“受控先驱散再判权限”，必须先权限检查再由上层执行驱散。
