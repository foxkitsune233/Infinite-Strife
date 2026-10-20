# X06 外部验收与平衡样本（0.1.4）

对应 `docs/tasks/0.1.4-external-handoff.md` 的 X06。本任务不改生产代码，只运行测试并回传证据。
执行时间：2026-10-20 00:40–01:10。工作区：`D:/android_project/DouQiuQiu`。

## 1. 全量回归（单元测试）

命令：`./gradlew.bat --offline testDebugUnitTest`

| 指标 | 数值 |
|---|---|
| 总用例 | **862** |
| 失败 | **28**（13 个套件） |
| 我新增（X01–X05） | 37 例，全绿 |

**失败均为既有问题，与 X01–X05 无关**：覆盖我改动文件的 8 个套件
（ShopIntegration / HomeEconomy / NodeLootContract / BatchLootPickup / HeroStatsSnapshot /
LootAndEconomyTable / ShopStaleQuoteRecovery / CraftingMaterialLayering）全部通过。

失败清单与归类（均指向 2026-10-19 11:22 的 `JourneyRules/JourneyEvents` 改写及后续存档格式调整，
这些文件本次未改动；最后一次全绿回归记录为 2026-10-18 22:24）：

| 套件 | 失败数 | 根因 |
|---|---|---|
| ThemedLootIntegrationTest | 9 | `reward()` 三选一已废止，旧用例仍走旧接口 |
| JourneyLifecycleAcceptanceTest | 5 | 同上（v8 奖励存档用例） |
| JourneyEventsIntegrationTest | 3 | 事件产出断言与现行 `JourneyEvents.outcome` 不一致 |
| ProfileV7CardStateTest / ProfileV9ExtensionTest / PoisonCoreBattleTest | 1+2+1 | v8 存档往返 RNG 差异（expected 10 but was 13） |
| ProfileV5MigrationTest / ProfileV6MigrationTest | 1+1 | 旧档迁移后走已废止的 `reward()` |
| JourneyTest | 1 | `fifthRewardBanksFixedChest…` 调 `reward(GOLD)` 期望成功 |
| AcceptanceFixtureTest | 1 | s04 事件容量拒绝的原子性断言 |
| LoadoutAndPackingTest | 1 | `organize` 现按面积降序整理，旧用例期望稳定首放 |
| SingleRunIntegrationTest / ValuableLootIntegrationTest | 1+1 | 通关链路经已废止 `reward()` |

**结论**：862/28。X01–X05 交付物零失败；28 个失败需要主任务决定是更新旧用例还是修行为
（`JourneyRules.reward` 存根、v8 存档 RNG、`organize` 排序三处行为已变，用例没跟上）。

## 2. 常规控制链

命令：`testDebugUnitTest --tests "com.example.douqiuqiu.status.*" --tests "com.example.douqiuqiu.combat.*"`

| 指标 | 数值 |
|---|---|
| 用例 | **206** |
| 失败 | 1（`poisonRuntimeSurvivesV8SaveRoundTrip`，即上表存档往返问题） |

各链条覆盖（全部通过，除上述存档往返例外）：

| 控制链 | 覆盖套件 |
|---|---|
| 眩晕 / 沉默 / 缴械（行动许可） | `status.ActionPermissionTest` |
| 弱驱散 / 强驱散 | `status.DispelResolverTest` |
| BKB（免疫期与技能封锁） | `status.PoisonCoreBattleTest`、`combat.EquipmentProcAcceptanceTest` |
| 状态抗性（控制时长减免） | `combat.CombatMathTest` 及 status 包各套件 |
| 毒运行 / 时间线 | `status.PoisonEngineTest`、`PoisonTimelineTest`、`PoisonEngineReviewTest` |

## 3. 经济样本

采样器：`tools/_economy_sampler_test.kt.bak`（临时 JUnit，读真实 `inventory.json`，
战斗/事件掉落各 1500 次生成；已移出测试目录，需要时拷回 `app/src/test/.../journey/` 运行）。

| 项目 | 数值 |
|---|---|
| 新档启动金币 | **1200**（`InventoryState` 默认值；`EconomyBalance.INITIAL_GOLD=600` 是**未使用的死配置**，建议二选一） |
| 宝箱票价（白/蓝/紫/金 × 难度 0/1/2） | 白 120/180/240；蓝 220/330/440；紫 420/630/840；金 700/1050/1400 |
| 战斗掉落平均目录价值（每节点） | 低 170 / 中 174 / 高 174 金币 |
| 战斗掉落平均件数 | 2.4 件/节点（单件均值 71–72 金币） |
| 五场全胜战斗金币 | 低 235 / 中 350 / 高 490 |
| 事件掉落 | 平均 290 金币/次、约 4 件；另发 `goldReward÷2`（中档三战约 32） |
| 商店回收价 | 目录价 **40%**（iron_branch 20 → 8） |
| 破产救济 | 绑定阔剑 + 2 药膏 + 1 张抵扣券（上限 120），次数 2 次 / 2 小时恢复 1 |

**平衡观察**（供主任务参考，未改任何数值）：
- 难度几乎不改变掉落价值（170/174/174），难度主要体现在金币（235/350/490）与敌人预算上。
- 单次通关掉落总值约 400–420 金币，介于低档票价（120–240）与中档票价（220–440）之间，
  属于「打得过就不亏」的量级；高档票价 700–1400 需要多局积累或事件补给。
- 事件掉落单价（290）显著高于战斗掉落（174），与「事件可更多」的设计一致。

## 4. 0.1.4 UI 设备验收（待主任务合入后执行）

**状态：阻塞**。0.1.4 的 UI 页面（一级/二级预览、属性页）由主任务实现中，
当前 `dist/v1.0-preview/DouQiuQiu-v1.0-preview-inventory.apk` 是 47 号旧版，
不含 0.1.4 新页面，无法按新验收表执行。合入后按以下步骤执行（验收表见 `docs/testing/56-ui-v0.1.4.md`）：

| 步骤 | 内容 |
|---|---|
| 1 | 装包：`adb install -r dist/0.1.4/DouQiuQiu-0.1.4-*.apk`，记录 APK 名与 SHA256 |
| 2 | 屏幕矩阵：320dp（`wm size 960x2400`）、常规 360dp、字体 1.3（`settings put system font_scale 1.3`） |
| 3 | 流程：远征 → 整备 → 掉落 → 拖拽 → 旋转 → 一键拾取（X01 规则）→ 返回远征 |
| 4 | 属性页（X02 快照）：裸体/装备/卡牌三态数值与 `HeroStatsSnapshotTest` 断言一致 |
| 5 | 商店陈旧报价（X03）：跨 2 小时格后旧报价拒绝、界面立即刷新新货架 |
| 6 | 合成材料分层（X04）：锁定/背包/堆叠三种"拿不到"在界面可区分 |
| 7 | 每步截图落 `docs/testing/results/shots/`，journal 落 `docs/testing/results/journal/` |
| 8 | 回填 56 号文档，写明 APK 名、存档类型、步骤、结果 |

## 5. 证据文件

- 回归 XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`（862 例汇总）
- 新增测试：`BatchLootPickupTest`（10）、`HeroStatsSnapshotTest`（6）、
  `ShopStaleQuoteRecoveryTest`（5）、`CraftingMaterialLayeringTest`（9）、
  `LootAndEconomyTableTest`（7）
- 设计文档：`docs/design/2026-10-20-loot-and-event-table.md`（X05 掉落表）
