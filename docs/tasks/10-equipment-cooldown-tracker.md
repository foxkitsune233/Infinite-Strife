# 外围任务10：装备唯一组冷却与跨战抵扣

仅新增主体：`shared/src/commonMain/kotlin/com/example/douqiuqiu/equipment/EquipmentCooldownTracker.kt`。
仅新增测试：`app/src/test/java/com/example/douqiuqiu/equipment/EquipmentCooldownTrackerTest.kt`。
纯Kotlin、不读时钟、不改引擎/物品/存档，使用Long毫秒。英雄技能、消耗品数量、减CD比例不在职责内。

```kotlin
data class EquipmentCooldownState(val remainingByGroup: Map<String, Long> = emptyMap(), val lastPreparedBattle: Int = 0)
data class CooldownStartResult(val started: Boolean, val state: EquipmentCooldownState)
object EquipmentCooldownTracker {
    fun tryStart(state: EquipmentCooldownState, groupId: String, cooldownMs: Long): CooldownStartResult
    fun elapse(state: EquipmentCooldownState, elapsedBattleMs: Long): EquipmentCooldownState
    fun prepareBattle(state: EquipmentCooldownState, battleIndex: Int, recoveryMs: Long = 30_000): EquipmentCooldownState
}
```

规则：
- 缺失组剩余按0。tryStart仅就绪时started=true并设置正cooldownMs；未就绪返回false及不变状态。不同物品使用相同groupId即共享CD，换位/升级不改groupId。
- elapse对已有组做max(0,剩余-elapsedBattleMs)，不删除归零项。只能由上层传实际战斗推进量，现实等待/整备不得调用；elapsed可0、不得负。
- prepareBattle首次lastPreparedBattle=0，第1场传1；此后只接受相同场号或上次+1。相同场号不变；进入新场各组max(0,剩余-recoveryMs)，记住本次场号，重启/页面重建不能重复减。
- 倒退或跳号均require拒绝；比较递增时避免Int.MAX_VALUE+1溢出。新挑战由上层创建空状态，不能继承旧挑战场号。
- recoveryMs可0、默认30_000，不受百分比减CD影响；cooldownMs由上层提前算好最终CD，本工具不重复应用。
- 所有ID非空白、剩余>=0、lastPreparedBattle>=0；cooldownMs>0、battleIndex>0、recoveryMs>=0。无关组负数也非法，require抛IllegalArgumentException。
- Long运算以比较后减法避免溢出，不读时间或修改输入，返回Map为独立快照。不凭空恢复消耗品或新增英雄CD。

必须测试：就绪启动/冷却中失败不覆盖；同组共享/异组独立；elapse部分/归零/超过/0；剩45秒跨战到15秒；重复prepare不再扣；下一场再扣；0抵扣；新挑战空状态；Long.MAX_VALUE边界；倒退/跳号/负数/空组报错；Int.MAX_VALUE场号边界；输入结果隔离。

验收：`:app:testDebugUnitTest --tests '*EquipmentCooldownTrackerTest'`，可加`:shared:compileKotlinJs`。不新增依赖或自行接UI，由主任务统一集成。
