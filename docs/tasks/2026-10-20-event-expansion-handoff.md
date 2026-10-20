# 0.1.5 事件扩充外围实现交接

主任务负责结局插画和内容审校；本文件交给外围 Kotlin 实现。

## 文件边界

- `shared/src/commonMain/kotlin/com/example/douqiuqiu/event/EventCatalog.kt`
- `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyEvents.kt`
- 对应 commonTest / app 测试。

事件内容和稳定 ID 见 [内容契约](../design/2026-10-20-event-expansion-content.md)。不要把数值规则写进 `JourneyActivity`。

## 数据与兼容

1. `EventKind` 追加八值，旧值和旧存档兼容。
2. `EventOutcome` 加不可空 `outcomeId`、`artKey`，或等价展示对象。旧档缺字段读作空串，UI 回退事件入口图。
3. `NodeEventInstance.outcomes` 在进入节点时冻结；价格、交物、掉落、结果图不允许重抽。
4. 迁移只追加字段，不能调整旧二进制块读写顺序。

## 确定性生成

```text
candidates = allKinds
  .filter(eligibleByStageAndUnlock)
  .filter(notRepeatedInLastTwoEventNodes)
  .filter(hasAtLeastOnePotentiallyEnabledOption)
kind = deterministicWeightedPick(seed, nodeId, baseWeight * needBias)
instance = freeze(kind, stableHash(seed,nodeId,kind)%100, price, requiredItem, outcomes)
```

- 哈希只依赖 seed、nodeId、kind，不能读系统时间或 UI 顺序。
- 最近两次排除同 kind；候选空时仅放宽为排除最近一次。
- `needBias` 只影响未生成节点的权重：缺药偏医棚/炼金，空间紧张降低多物品事件，成型羁绊提高月井权重。

## 原子结算

```text
validate unresolved + option + condition + selectedItem + currency + placement
if invalid: return rejected(oldState)
apply all currency/hp/consume/grant changes to nextState
persist nextState + resolvedOptionId + outcomeId/artKey atomically
```

- 交物使用 `EventItemSelection` 精确选择实例；默认不得取安全箱。
- 奖励放不下时零改动并进入整理，不能自动转安全箱。
- `possibleDeath` 与选项风险披露一致；潜在致死 label 含“可能死亡”。
- 结果页从 receipt 的保存值读取 `outcomeId/artKey`，绝不再调生成器。

## UI 映射建议

```kotlin
data class EventOutcomeArtwork(val artKey: String, val assetPath: String)
object EventOutcomeArt { fun pathFor(artKey: String): String? }
```

`JourneyActivity.presentEventResult` 优先显示 receipt 的结果图；无 key 时退回 `SceneArtwork.event(kind)`。图内不放文字。

## 最低测试表

| ID | 断言 |
|---|---|
| EV01 | 16 事件均有唯一 kind；新增 24 个 outcomeId/artKey 均非空。 |
| EV02 | 同 seed/node 连续生成 100 次完全一致。 |
| EV03 | 读档、重进后价格、交物、奖励、artKey 不变。 |
| EV04 | 最近两次不重复；候选不足只放宽到排除最近一次。 |
| EV05 | 空间、金币、交物校验失败时状态逐字段不变。 |
| EV06 | 月井共鸣无达标羁绊时禁用，达标后启用。 |
| EV07 | 旧档缺 outcomeId/artKey 能读，UI 回退入口图。 |
| EV08 | 交物事件未经明确授权不能消耗安全箱物品。 |
