# 40号：v10 / 经济v2测试契约与控制装备图标

## 原始证据与结论边界

39号实际采样的`bd330604a9b7aaeb`完整状态为4320/4320，16528行数据；旧失败证据继续保留。
外部文字报告称24个单测失败，当前磁盘TEST-*.xml实际合计772项、23个failure、0个error。
本次修改前的23个失败节点完整保存为`results/40-failures-before.json`，不把数量差异隐藏或补成24。

不能仅凭失败的类名断定“不调用护盾/全部预存”；以下按具体失败行与生产规则逐项分析。

| 分类 | 数量 | 根因与处理 |
|---|---:|---|
| 装备/配方目录 | 2 | 36号增加3件控制装备后已为27主要装备/32配方；更新旧24/29断言，保留配方图校验，并核对3个新增ID |
| HomeEconomy | 5 | 首次鉴定10、最低入口120、补助120、商店刷新50；更新旧价格断言；补差价29不足/30刚好与失败状态不变；刷新后49金币才能继续验证不足50的原子拒绝 |
| ShopIntegration | 4 | 刷新80改50，因此1200-90-50=1060；不足费用夹具79改49；不改变定价需求与自动刷新边界断言 |
| Journey鉴定 | 1 | 首次鉴定20改10，仅更新费用，保留内容/坐标不变断言 |
| 主题宝箱 | 3 | 低价与救济箱60改120；旧手写目标箱的60不改，继续验证旧目标兼容 |
| 存档当前版本/未来版本 | 5 | 当前新写文件应为v10；未来版本拒绝使用v11；独立手写v6/v7/v8旧格式夹具不改 |
| 死亡与永久成长 | 1 | 夹具100金币不足新入场费120，实际没有入场。改为200，并显式断言入场与放弃成功，再检查仅仓库/安全箱保留、成长不丢 |
| 图标 | 2 | ItemIconCatalog缺少碎颅锤/紫怨/天堂之戟的明确映射，导致对应卷轴映射也为null；补3个成装映射，沿既有recipe派生规则生成卷轴。已有独立彩绘素材仍为常规物品展示入口 |

没有为了适配断言回退生产经济、装备目录、存档版本或护盾修复。生产改动仅3条图标元数据；其余为原测试契约及夹具修订。未删除失败用例，不忽略断言。

## 外部复验表

| ID | 操作 | 预期 |
|---|---|---|
| T01 | 跑EquipmentCatalogTest / EquipmentBattleIntegrationTest | 27主要装备、5中间件、32配方；所有材料/卷轴有定义、图无环 |
| T02 | 跑HomeEconomyTest / ShopIntegrationTest / JourneyTest / ThemedLootIntegrationTest | 新经济费用一致；不足时原状态不变、免费次数/自动刷新边界不变；旧手写箱仍按旧费用 |
| T03 | 跑ProfileV5MigrationTest / ProfileV7CardStateTest / ProfileV9ExtensionTest / PoisonCoreBattleTest / JourneyLifecycleAcceptanceTest | v10往返、真实旧格式迁移、v11拒绝、毒池/RNG不丢；死亡案例确实已入场，背包risk丢失，keep/safe与永久成长保留 |
| T04 | 跑OpenItemIconTest / UiIconCatalogTest | 全目录包含3件控制装及卷轴明确映射，无空指针；仍使用既有授权图标资源 |
| T05 | 全量测试与编译命令如下 | 失败归零；若出现新的后续断言失败，保留逐例XML，不得直接标为预存或忽略 |
| T06 | 真机/模拟器打开3件控制装及对应卷轴详情 | 正常显示已有彩绘图；图标回退路径也能显示；名称、卷轴与产品对应 |

```powershell
.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs
```

本轮只编译生产与测试源码，未执行上表。修复不涉及shared或采样器代码，无需仅为图标与测试更新重跑4320局；若另改战斗/经济逻辑，需重新取证。

## 外部复验执行记录（2026-10-18 22:24，--rerun-tasks 强制重跑）

命令（T05）：`.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs --rerun-tasks`，日志 `results/sampling/_run-t05b.log`。

- 首次普通命令的测试任务为 **UP-TO-DATE**，表示本次未重跑；这不能单独证明缓存误判。40号修订已早于22:02的`_run-full2`全量运行落地，后续输入未变时复用该轮结果符合Gradle增量行为。本次为取得新的执行证据使用`--rerun-tasks`：`42 actionable tasks: 42 executed`，全部重新编译并真实执行。保留首次日志，不将正常增量状态称为假成功或缓存缺陷。
- 结果：**772 tests, 0 failures, 0 errors, 0 skipped，BUILD SUCCESSFUL**；XML 时间戳 22:24:40、APK 22:24:32 均为本次新鲜产物（含图标修正的新 APK）。

| ID | 相关类（本次通过数） | 结果 |
|---|---|---|
| T01 | EquipmentCatalogTest 25 + EquipmentBattleIntegrationTest 16 | PASS（27 主要装备/32 配方/配方图无环无缺） |
| T02 | HomeEconomyTest 11 + ShopIntegrationTest 21 + JourneyTest 7 + ThemedLootIntegrationTest 25 | PASS（新经济费用一致、不足时状态不变、旧手写箱仍按旧费用） |
| T03 | ProfileV5 12 + ProfileV7 5 + ProfileV9 4 + PoisonCoreBattle 9 + JourneyLifecycle 13 | PASS（v10 往返、真实旧格式迁移、v11 拒绝、毒池/RNG 不丢、死亡案例确已入场） |
| T04 | OpenItemIconTest 3 + UiIconCatalogTest 4 | PASS（3 控制装及卷轴映射非空、无空指针） |
| T05 | 全量 + assembleDebug + :shared:compileKotlinJs | PASS（0 失败；APK 与共享 JS 均构建成功） |
| T06 | 真机/模拟器打开 3 控制装及卷轴详情 | **未做**（需设备；新 APK 已就绪 `app/build/outputs/apk/debug/app-debug.apk`） |

失败数口径：**23**（非 24，与 `40-failures-before.json` 及磁盘 XML 一致）。修正后 23 项全部转绿，且为强制重跑的真实结果，非 UP-TO-DATE 旧产物。

仍待：38号S04“两项容量都不足后放弃”强制场景；39号F01–F06的新小数/极值组合不能只因既有同类测试通过就声称全部逐项验证。
