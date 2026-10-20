# 外围任务06：战斗基础公式工具

## 范围与文件

实现只计算数字的纯Kotlin工具，不操作战斗单位、随机数、状态计时、事件、配置或存档。当前DuelEngine中有临时内联计算，交付后由主任务替换；不要修改引擎。

仅新增：`shared/src/commonMain/kotlin/com/example/douqiuqiu/combat/CombatMath.kt`。

仅新增测试：`app/src/test/java/com/example/douqiuqiu/combat/CombatMathTest.kt`。不增加依赖，JUnit4.13.2已在app。

## 接口

```kotlin
object CombatMath {
    fun physicalDamageMultiplier(armor: Double): Double
    fun magicDamageMultiplier(baseResistance: Double, independentResistances: List<Double>): Double
    fun restorationMultiplier(amplifications: List<Double>, reductions: List<Double>): Double
    fun statusDurationMultiplier(resistances: List<Double>): Double
    fun spellOutputMultiplier(genericAmplifications: List<Double>, poisonAmplifications: List<Double>, isPoison: Boolean): Double
    fun attackLifestealBase(lifeDamage: Double, shieldDamage: Double, lifestealRates: List<Double>): Double
    fun rescaleHealth(currentHealth: Double, oldMaxHealth: Double, newMaxHealth: Double): Double
}
```

全部百分比用小数，20%传0.2；不四舍五入，浮点测试用误差比较。所有Double输入必须finite；溢出的计算结果不能静默返回Infinity/NaN，require拒绝。

## 公式与边界

1. 物理伤害倍率：`1 - 0.06*A/(1+0.06*abs(A))`。护甲可为负，负护甲增加伤害。armor=0得1，10得0.625，-10得1.375。
2. 魔法伤害倍率：`(1-baseResistance)*Π(1-independentResistance)`。抗性每项允许[-1,1]，即能表示魔法易伤；独立魔抗不先相加。基础25%加独立20%时伤害倍率0.6。
3. 恢复倍率：`A=1-Π(1-a)`、`R=1-Π(1-r)`、`1+clamp(A-R,-1,1)`。本工具a/r各限制[0,1]；多个20%增幅+30%抑制得1.06。不要用(1+A)*(1-R)。输入已经由上层完成唯一组择强和恢复类型分类，不在此实现家族/同名去重。超出该工具幅度范围的未来效果由主任务另作扩展。
4. 状态时长倍率：`Π(1-resistance)`，每项[0,1]；两个20%得0.64。不加50%抗性上限，不施加技能CD下限。工具不决定哪种状态受影响，也不决定毒的tick压缩方式。
5. 法术输出倍率：`1+sum(generic)+（isPoison时sum(poison)，否则0）`。加成各项非负、可超过1；不是两个增幅池相乘。本工具只算输出增伤，不包括目标魔抗、护盾、生命移除资格或特殊独立乘区。
6. 攻击吸血基础恢复：`(lifeDamage+0.5*shieldDamage)*sum(lifestealRates)`。两个伤害量及吸血率各非负；吸血率合计可大于100%。传入生命伤害已由调用方截断过量击杀，护盾伤害不属于lifeDamage，不重复计。这里不再加恢复增幅或限制缺失生命。
7. 换装生命：`(currentHealth/oldMaxHealth)*newMaxHealth`。max>0，current在[0,oldMax]；死亡保持0，不夹到1。先除后乘，避免中间乘法溢出；不触发治疗。

空列表乘积1、和0；相同数值可来自不同有效来源，必须保留重复项。

## 必须测试

上述数字例子逐条验证；空列表；负护甲；独立魔抗；恢复增强和抑制各自递减；100%抑制；100%状态抗性得0时长；毒和非毒选择；重复吸血率相加；全打盾时按50%折算；生命0与满生命比例换算；反复换装不因取整刷血；NaN/Infinity/非法范围/非正最大生命抛错；极端有限输入导致结果溢出时明确抛错。

规则依据为仓库内 `docs/rules/restoration-and-status.md`，不自行采用别的Dota版本或新设上限。验收命令：`:app:testDebugUnitTest --tests '*CombatMathTest'`。本任务公式工具不等于完整战斗规则已实现。
