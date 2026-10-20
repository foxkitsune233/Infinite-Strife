# 外围任务11：分来源护盾账本

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/combat/ShieldLedger.kt` 与 `app/src/test/java/com/example/douqiuqiu/combat/ShieldLedgerTest.kt`。纯Kotlin，不修改既有Fighter/引擎/配置/存档/Gradle，交付后由主任务接入。

```kotlin
enum class ShieldKind { UNIVERSAL, PHYSICAL, MAGICAL }
enum class ShieldRefreshMode { MAX_REMAINING, ADD_CAPPED }
enum class ShieldDamageKind { PHYSICAL, MAGICAL, PURE }
data class ShieldEntry(val sourceId: String, val kind: ShieldKind, val amount: Double, val remainingMs: Long? = null)
data class ShieldPool(val entries: List<ShieldEntry> = emptyList())
data class ShieldHitResult(val pool: ShieldPool, val absorbed: Double, val remainingDamage: Double)
object ShieldLedger {
    fun apply(pool: ShieldPool, incoming: ShieldEntry, mode: ShieldRefreshMode = ShieldRefreshMode.MAX_REMAINING, cap: Double? = null): ShieldPool
    fun absorb(pool: ShieldPool, damage: Double, kind: ShieldDamageKind): ShieldHitResult
    fun elapse(pool: ShieldPool, elapsedMs: Long): ShieldPool
    fun total(pool: ShieldPool): Double
}
```

规则：
- sourceId非空白、池内唯一；同来源类型不得改变。不同来源独立叠加，不能把S1开场盾和无光之盾混成同来源。
- 普通重新施加：当前剩余与新amount取较高值，时长刷新为incoming.remainingMs；不同来源直接新增。null时长表示本场无到期时间。
- 天怒等明确例外用ADD_CAPPED：同来源剩余+新增，上限cap，只限制该来源，不挤占别的盾；必须传有限正cap。MAX模式cap必须null，避免含糊。
- incoming.amount=0为无操作，不新增或刷新；已有池条目必须amount>0，剩余时长null或>0。elapsed可0，时间均Long非负。
- 吸收输入为完成减伤后的伤害，不再算护甲或魔抗。物理先PHYSICAL后UNIVERSAL；魔法先MAGICAL后UNIVERSAL；PURE不消耗任何盾。
- 同类盾内部按最早到期优先（null最后），同到期按sourceId字典序；扣完移除空条目，其余输出保持原池相对顺序。
- absorbed+remainingDamage等于输入，池盾减少量等于absorbed，不计算实际生命、过量击杀或吸血。总量是三类当前amount之和。
- elapse只按传入战斗时间减剩余，扣到0移除；永久条目不变。战斗结束清空由调用方创建空池，不在此加新回合规则。
- 所有Double须finite，伤害/新增盾非负，溢出不得返回Infinity/NaN。非法参数require抛IllegalArgumentException，输入不变、输出独立快照。

测试至少：专用优先再通用；纯粹全穿；同源取高/刷新、不相加；异源相加；ADD_CAPPED累计/本来源封顶不影响别人；最早到期和同到期稳定顺序；盾清零删除；永久和有限时长；0伤害/0新增；守恒；类型冲突/重复ID/非法时间/NaN/Infinity报错；输入隔离。

验收：`:app:testDebugUnitTest --tests '*ShieldLedgerTest'`、`:shared:compileKotlinJs`。规则依据confirmed-rules与v1定稿，不额外改变伤害类型。
