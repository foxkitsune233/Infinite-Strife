# 外围任务18：多来源暴击优先判定

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/combat/CriticalStrikeResolver.kt` 与 `app/src/test/java/com/example/douqiuqiu/combat/CriticalStrikeResolverTest.kt`。不改DuelEngine、模型、RNG、命中、装备或存档。

```kotlin
data class CriticalStrikeSource(val instanceId: String, val multiplier: Double, val threshold: Double, val roll: Double, val active: Boolean = true)
data class CriticalStrikeResult(val critical: Boolean, val multiplier: Double, val sourceId: String?, val evaluatedSourceIds: List<String>)
object CriticalStrikeResolver {
    fun resolve(sources: List<CriticalStrikeSource>, bonusMultiplier: Double = 0.0): CriticalStrikeResult
}
```

规则依据confirmed-rules：多来源按倍率从高到低判定，一次命中只能有一个暴击，不连乘；同倍率按instanceId字典序。对每个active来源比较`roll < threshold`，首次成功立即停止。evaluatedSourceIds只列实际检查到的来源，保留检查顺序。threshold=1确保成功，threshold=0必败。roll是上层已提供的判定值，本工具不读随机数、不推进seed、不更新伪随机失败次数。

threshold可代表上层伪随机累积后的当次阈值，不把它固定解释为面板基础概率；PRD参数校准及状态推进留给后续主任务，禁止在本工具偷换成另一个随机系统。

bonusMultiplier是暴击倍率的绝对增量：PA+30个百分点传0.30；仅成功暴击时加到获选倍率，不加到普通1.0攻击上。PA确保暴击以multiplier=3.0/threshold=1来源表达，与其他来源仍择强判定，不额外相乘。此工具不扣除“下次攻击”状态；该状态仅成功攻击后由上层消费。

输入验证：全输入instanceId唯一且非空白；multiplier为finite且>1；threshold为finite且0..1；roll为finite且0<=roll<1；bonusMultiplier为finite且>=0。inactive也校验。成功倍率相加若溢出则require抛IllegalArgumentException；未成功时不因无用加法制造溢出。无active或全失败返回critical=false、multiplier=1.0、sourceId=null。输出与输入集合独立。

至少测试：高倍率先成功并停止；高失败后低成功；同倍率稳定次序；不连乘；PA确保与更高概率暴击竞争；全失败；0/1阈值与严格小于边界；被动增量仅增强已触发暴击；inactive；输入重排一致；非法参数/重复ID/成功倍率溢出；输入隔离。验收 `:app:testDebugUnitTest --tests '*CriticalStrikeResolverTest' :shared:compileKotlinJs`。
