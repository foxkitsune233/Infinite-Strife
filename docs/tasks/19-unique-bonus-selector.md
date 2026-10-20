# 外围任务19：唯一增幅按组择强

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/equipment/UniqueBonusSelector.kt` 与 `app/src/test/java/com/example/douqiuqiu/equipment/UniqueBonusSelectorTest.kt`。不改现有装备/战斗/库存/配置/存档，也不调用17。

```kotlin
data class UniqueBonusCandidate(val instanceId: String, val groupId: String, val effectId: String, val value: Double, val active: Boolean = true)
data class SelectedUniqueBonus(val groupId: String, val effectId: String, val instanceId: String, val value: Double)
object UniqueBonusSelector {
    fun select(candidates: List<UniqueBonusCandidate>): List<SelectedUniqueBonus>
}
```

本工具只处理“数值越高越强、明确同组唯一”的特殊增幅，例如散华家族恢复增强、状态抗性；夜叉家族攻速增幅；慧光家族法术增幅。同一(groupId,effectId)只选一个active候选，value最高；同值选instanceId字典序最小。按groupId、effectId字典序输出。不同组即使effectId相同也分别保留，上层再按该效果自己的叠加公式计算，不能在这里直接相加。

一把双刀可分别为两个家族提供候选；同一件装备同一家族也可提供不同effectId。不能整件只留下“最贵的一把”。基础力量/敏捷/智力/攻击等正常属性由主任务另行全部相加，不放入本工具。普通多源吸血/暴击/闪避也不错误地放进唯一组。本工具不比较主动技能或“越低越好”的CD；这些有专用规则。

全量校验：instanceId/groupId/effectId非空白；value为finite且>=0；(instanceId,groupId,effectId)组合不得重复，同instanceId提供不同效果/家族合法。inactive不参与结果但仍校验。0数值合法，可作为该组唯一获选值。输入和输出独立，不持有可变输入集合引用。

至少测试：单刀与双刀同家族择高；双刀两个家族独立保留；一装备多效果；同效果不同组不合并；同值稳定选ID；inactive排除；输入重排一致；0值；空输入；非法Double/空ID/重复组合拒绝，但同实例不同键合法；输入隔离。验收 `:app:testDebugUnitTest --tests '*UniqueBonusSelectorTest' :shared:compileKotlinJs`。
