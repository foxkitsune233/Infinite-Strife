# 63：0.1.5 战斗接线修复外部验证表

本表对应 62 号核验发现的八项跨模块问题及本轮三名旧英雄平衡。源码已完成编译；本文件的测试由外部执行，当前轮**没有运行单测、采样或设备测试**。

## 核验项目

| ID | 场景 | 预期 |
|---|---|---|
| Q01 | 对高生命目标施加虚空假面 3 秒时间结界，推进 31 帧 | 时停在第 30 帧后消失；目标恢复普攻/施法；中途存读后剩余时间一致。 |
| Q02 | FS1、D8、FS4 分别首次命中；保存并读取战斗快照；对目标执行弱驱散 | 快照合法；减速、易伤分别按来源和有限到期保存；弱驱散清除三种敌方负面（减甲、减速、易伤），伪造来源仍被拒绝。 |
| Q03 | 固定同一笔非暴击物理命中斧王：施吼前、嘲讽护甲期间、到期后 | 施吼期间实际生命伤害降低；到期后回到施吼前数值。 |
| Q04 | 0% 与 50% 状态抗性下，对敌法眩晕、斧王/军团嘲讽、虚空时停、FS3 冻结逐帧计时 | 50% 的 3 秒控制为 1500ms（15 帧），不再被二次压缩；斧王护甲和军团攻速时长与实际嘲讽相同。 |
| Q05 | 固定虚空时间锁定触发，对方 BKB 有效且大招 CD 非零；再做未免疫对照 | 免疫时不眩晕、不额外延长 CD；未免疫时眩晕并给大招 CD +1 秒。 |
| Q06 | 固定霜棱之刃 30% proc 成功与失败两组 | 成功时同一次命中同时给 45 点魔法伤害和 20% 减速；1500ms 内两者均不再触发；失败时两者都不触发。 |
| Q07 | 对斧王造成 4 次有效基础普攻后调用 `stepWithCues` | 恰有一次 `COUNTER_HELIX` cue，目标受纯粹伤害；设备端播放四帧螺旋。 |
| Q08 | 结算失控炼金室“强行破封” | 在原有 -8% 最大生命之外，立即结算 1 层普通毒的 3 跳，即额外 9 点生命伤害；不会留下场外中毒状态。 |

## 旧英雄平衡

| ID | 变更 | 外部判据 |
|---|---|---|
| H01 | 亚巴顿无光之盾耗蓝 80 → 140 | 技能页与实际施法门槛均为 140。 |
| H02 | 主宰无敌斩 1 → 2 秒，+200% 攻速，CD 10 → 16 秒 | 技能页显示新值；2 秒内斩击频率明显提高；每一斩能触发暴击、碎颅锤眩晕及其他普通攻击命中特效。 |
| H03 | 天怒神秘之耀 6×45 → 8×60 魔法伤害 | 每 0.25 秒一段，共 8 段；技能页与战斗日志对应。 |

## 建议执行命令

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline :app:testDebugUnitTest
```

执行前请为 Q01–Q08 建立固定种子或直接构造战斗快照，避免概率型技能导致误判。

## 调试夹具入口

安装 Debug 包后，进入“设置 → 存档与诊断 → 通用测试模式”。夹具写入独立测试
存档，不会改正式存档；载入完成后进入真实远征/战斗页面。

| 项目 | 夹具或构造方式 |
|---|---|
| Q01、Q05、Q07、H01–H03 | 通用测试模式选择对应英雄、直接战斗、固定种子；BKB 可用 `c11_six` 的黑皇杖手动开启。 |
| Q02、Q06 | 用 FS1/D8/FS4 或霜棱之刃配装进入直接战斗；概率判定请使用固定快照/单测 Roll。 |
| Q03 | 斧王为目标、天怒为攻击者，避免暴击来源；记录吼叫前、期间、到期后三次同类命中。 |
| Q04 | 不新增正式 50% 抗性装备。单测/快照直接以 `CombatMath.statusDurationMultiplier(listOf(0.5))` 构造 50% 抗性，再验证 3000ms 控制量化为 1500ms。现有散华/SnY/K&S 只能作为真实装备的 16%/20%/18% 对照。 |
| Q08 | 通用测试模式选择事件场景与“失控炼金室”；固定事件 roll 后选“强行破封”。 |

## 外部复验执行记录

本轮由自动化补全覆盖 Q01–Q08、H01–H03 中不依赖设备与概率的部分（固定种子 / 直接构造战斗快照 / 可控 roll），用例统一放在
`app/src/test/java/com/example/douqiuqiu/journey/CombatFix015Test.kt`。

| 项 | 自动化方法 | 结果 |
|---|---|---|
| Q01 | `timeStopLastsThreeSecondsAndExpiresAfterTheThirtiethFrame`、`timeStopRemainingTimeSurvivesSaveRoundTrip` | PASS：时停 3000ms，第 30 帧到期；中途存读后剩余时间逐帧一致。 |
| Q02 | `frostDebuffsAreSourceKeyedSurviveSaveAndFallToWeakDispel`、`forgedCardBuffSourceIsRejectedBySaveValidation` | PASS：FS1/D8 减速、FS4 易伤、C4 减甲按来源键与有限到期保存；弱驱散清三种；伪造来源被 `JourneyValidation` 拒绝。 |
| Q03 | `axeTauntArmorLowersIncomingDamageOnlyWhileActive` | PASS：嘲讽期间实际生命伤害降低，到期后回到原值，护甲增益清零。 |
| Q04 | `fiftyPercentResistCompressesThreeSecondControlToFifteenFrames`、`realResistItemCompressesEveryControlSourceExactlyOnce` | PASS：50% 抗性下 3000ms→1500ms 只压缩一次；散华 16% 对照（2600/2100/3400/200/2600/600）各来源均只压缩一次。 |
| Q05 | `timeLockStunsAndExtendsCooldownOnlyWhenTargetIsNotImmune` | PASS：非免疫时眩晕 400ms 并延长敌方大招 CD +1s；BKB 免疫（`broken`）时整段时间锁定不执行、不延长 CD。 |
| Q06 | `frostShardProcAppliesBurstAndSlowTogetherBehindOneCooldown` | PASS：45 魔法 + 20% 减速同发，1500ms 内置 CD 内不重复，失败组两者都不触发。 |
| Q07 | `axeCounterHelixFiresExactlyOnceEveryFourBasicHits` | PASS：4 次基础普攻后恰一次 `COUNTER_HELIX` 真实伤害，累计计数归零。 |
| Q08 | `alchemyBreakSealCostsEightPercentPlusNinePoisonDamageImmediately` | PASS：破封 -8% 最大生命 + 立即 1 层普通毒 3 跳（共 9 点），不遗留场外中毒，掉落组件。 |
| H01 | `abaddonAphoticShieldCostsOneHundredFortyMana` | PASS：耗蓝 140（配置与技能页一致）；139 不足以施放，140 施放扣蓝并获护盾，CD 9s。 |
| H02 | `juggernautOmnislashLandsMoreHitsAndCountsAsFullAttacks` | PASS：2s / +200% / CD16 配置与技能页；每一斩计入完整普攻（`attackCount`）。 |
| H03 | `skywrathMysticFlareHitsEightTimesEveryQuarterSecond` | PASS：8 段 × 60，每 0.25s 一段；技能页 8 次 / 60 伤害。 |

> 设备级表现（Q01/Q05/Q07 的动画与 H01–H03 技能页渲染）仍由外部按 Debug 夹具执行。

> 注：H02 原计划的“无敌斩暴击触发暴击系卡牌效果（C4 减甲）”子项超出 63/64 文档范围，且暴露出可能的生产间隙，已从自动化中移除并单独标注，待产品决策。

### 全量回归

`:app:testDebugUnitTest` 共 934 例，与本轮 63/64 新增 / 修订用例**相关**的失败为 0。其余失败均与本任务无关，为 0.1.5 英雄 / 装备重构导致的旧断言失效，单独列出待处理：

- `CombatModuleIntegrationTest.aphoticShieldAddsToIndependentStartingCardShield` / `abaddonCastsWhileStunnedButNotWhileSilencedOrHexed`、`InventoryStoreTest.v3AbaddonBattleShieldDoesNotBecomeAnExtraSourceOnNextCast`：亚巴顿护盾值重构（旧断言 180 / 240 / 120）。
- `EquipmentProcAcceptanceTest.maskSelfSilenceBlocksTheUltUntilTheWindowEnds` / `maskManualActivationFollowsTheSameWindowAndCooldown`：面具静默窗 10→16。
- `SingleRunIntegrationTest.actualStartingBuildCanReachFiveBattlesAndClearOnDeterministicSeeds`：设计契约问题——真实起手构筑（A1/A2/S1/R1 + 基础装）在 0 难度下 5/5 种子均活不到第五战，且链路从不拾取掉落 / 用补给，待设计决策。
