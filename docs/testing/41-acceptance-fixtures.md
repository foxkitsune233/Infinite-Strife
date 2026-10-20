# 41号：专项验收夹具

状态：已实现，交付前仅编译。以下测试/模拟器操作由外部执行，不预填PASS。

## Debug设备入口

安装`app/build/outputs/apk/debug/douqiuqiu-acceptance-fixtures-debug.apk`，保留正式存档时使用覆盖安装，不卸载、不清应用数据。
设置 → 存档与诊断 → 通用测试模式 → **41号专项夹具**。

一键按钮会先显示该场景说明，再载入独立测试存档；不会改正式金币与挑战。测试模式中的“退出测试模式”恢复正式存档。重新载入同一预设会重新开始，不能用重载代替保存恢复验收。

也可点“粘贴配置JSON并载入”，使用`docs/testing/fixtures/41-*.json`的内容。例如：

```json
{"fixture":"s04_full","seed":1729}
```

旧版完整配置JSON仍兼容，缺少fixture时按原通用模式。专项配置只使用fixture和seed，其余参数按预设固定，避免上次的弱敌/永久控制污染本轮。

| 入口 | 构造的起始状态 | 外部操作及判据 |
|---|---|---|
| T06 装备与卷轴彩绘／回退对照 | 六项同屏可滚动：碎颅、紫怨、天堂及三个卷轴；左正常物品渲染，右显式调用开源回退渲染 | 截图检查名称/颜色/主体与卷轴对应。右侧为强制渲染器对照，不是删除资源后的自然缺图回退；正常物品彩绘不会替换为抽象图标 |
| S04 双满奖励后放弃 | 第4战后保险库；4×2背包每格树枝占满；安全箱仍有空位；锁芯/精华奖励已固定 | 依次搬锁芯、拆解，均应提示容量不足，物品/金币/事件不变；最后放弃正常进入第5战整备，不发物品。可重进事件页验证没有重抽 |
| C02 双碎颅高攻速 | PA双碎颅+双振奋+龙心，无技能卡；敌方龙心且不攻击/施法 | 观察普通攻击和共享CD，日志来源应只属于唯一获选的一把；此UI场景不保证某次概率成功。固定roll与无敌斩分类另跑下方单测 |
| C10 自然沉默触发推推 | PA持推推，敌方紫怨首帧自动施放，无其他解控 | 新沉默进入时同帧保护2000ms、后撤演出；沉默仍持续，不被推推驱散；记录scenario_loaded和首帧JSONL |
| C10 推推与长戟同组 | 同时携带推推与长戟，敌方同上 | 长戟唯一获选、保护2000ms、攻速+40；不触发两份保护或独立CD |
| C11 六主动与双来源状态 | BKB/撒旦/疯狂面具/臂章/笛子/赤红甲均为手动；双方60秒内不攻击施法；两来源预置沉默30/20秒 | 先看状态合并与来源详情，再点击六主动、切自动开关、退出应用重进检查CD/模式。BKB可弱驱散两层沉默。30/20秒是方便截图的预置显示状态，不是紫怨正式时长；不适用于测紫怨持续时间 |

C11限时最长60秒，重进会继续当前状态；需要重新截图时重载预设。主动装备排序按现有instanceId，不能把控件显示位置猜成来源编号。

T06对照之外，真实仓库里也备有全部物品，可回到整备查看对应物品详情。若要验证自动缺图路径，需另做资源缺失注入；本批不删除/篡改APK素材。

## 固定输入单测夹具（无需模拟器）

新增`app/src/test/java/com/example/douqiuqiu/acceptance/AcceptanceFixtureTest.kt`。调用真实TimedControls/EquipmentEffectRuntime/DuelEngine/JourneyRules/EnemyBuilds/InventoryStore；Probe只接收引擎回调并固定roll，不重写控制算法。

| 用例 | 覆盖 | 预期 |
|---|---|---|
| c02DoubleBasherRollsOnceAndStartsOnlyOneSharedCooldown | C02同组唯一 | 一次roll、一个来源、CD3000；同CD再击不掷骰 |
| c02MeleeAndRangedStrictRollBoundariesAndSlashConsumesNoRoll | C01/C02 | 0.25/0.10恰等于阈值失败，略小成功；无敌斩回调不roll不加控 |
| c02FullyBlockedLandedAttackCanStunButProtectedOrEvadedAttackCannot | C02命中管线 | 先锋完全格挡仍可触发；保护/虚无/无敌/闪避整击失败不触发。使用固定1–128种子集合找可观测分支，不跑平衡矩阵 |
| c05TwoRealSourcesExpireSeparatelyAndRefreshUsesMaximumNotAddition | C05 | 两个真实紫怨来源独立到期；短刷新不缩短、长刷新取新值，不相加 |
| c05BothNewControlsReceiveFullDurationInTheSameFrame | C05双方时序 | 同帧互相沉默都保留3000ms，不因先后出手少100ms |
| c06ResistanceAppliedOnceQuantizedAndSaveResumeDoesNotReapplyIt | C06 | 无抗800ms；散华按真实抗性向上对齐100ms；极短效果最低100ms；保存恢复不重算抗性或RNG。100%仅验证公式，当前没有合法100%抗性装备，不宣称端到端施加覆盖 |
| c10FreshControlTriggersForceOrPikeInTheSameFrameWithoutRemovingControl | C10 | 近/远程、推推/长戟四组合；新控制同帧触发保护、控制仍在，唯一CD与攻速一致 |
| c11SixActivesAndMergedSourcesKeepManualModesAndCooldownsAfterRestart | C11逻辑 | 6主动、合并两来源显示最长计时；真实点击后6组CD，v10保存恢复不丢手动模式，重复BKB不刷新 |
| c12EnemyMatrixUsesProductionGeneratorAndRespectsControlLimits | C12 | 3难度×5战×128种子；低成本/前三战0件、常规最多1、进阶最多2，至少存在真实后期控制出装；同输入预览一致。不把25%候选概率当实际出装频率 |
| s04BothCapacityRefusalsAreAtomicThenAbandonAdvancesWithoutGrantingItems | S04规则 | 两次容量拒绝原状态完全相等，放弃后进度+1、不发物、不改RNG、重复结算拒绝 |

```powershell
# 先只跑本批10项
.\gradlew.bat --offline testDebugUnitTest --tests 'com.example.douqiuqiu.acceptance.AcceptanceFixtureTest'
# 然后全量（原772项，本批新增10项；最终以XML实际数量为准）
.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs
```

需要强制取得新执行证据时再加`--rerun-tasks`；UP-TO-DATE本身不是缓存故障。

## 外部复验执行记录（2026-10-19 实跑）

**环境**：emulator-5554（Pixel36）；夹具 APK `douqiuqiu-acceptance-fixtures-debug.apk`，SHA256 `ae6a19feb2f2bd47c9ebc2882cedfd6cb6fae423dbd6838c74383ec1cc3e7b22`，覆盖安装（`-r`，未卸载、未清数据）。入口：设置 → 存档与诊断 → 通用测试模式 → 41号专项夹具。单测/全量日志：`docs/testing/results/sampling/_run-41-unit.log`、`_run-41-full.log`；设备截图：`docs/testing/results/shots/41-*.png`；JSONL：`docs/testing/results/journal/`。

### 单测（T01–T05）

| 项 | 命令 | 结果 |
|---|---|---|
| T01–T04 | `testDebugUnitTest --tests 'com.example.douqiuqiu.acceptance.AcceptanceFixtureTest'` | **PASS**：XML `tests=10 failures=0 errors=0 skipped=0`（c02×3 / c05×2 / c06 / c10 / c11 / c12 / s04 逐项 PASS，`TEST-...AcceptanceFixtureTest.xml` 23:50:57 新鲜产物，`> Task :app:testDebugUnitTest` 真实执行） |
| T05 | `testDebugUnitTest assembleDebug :shared:compileKotlinJs` | **PASS**：全量 XML **782 tests / 0 failures / 0 errors / 0 skipped**（60 类，772+10）；`testDebugUnitTest` executed，`assembleDebug`/`compileKotlinJs` UP-TO-DATE（文档约定不算缓存故障，二者 22:24/23:40 产物随当前源码） |

### 设备（T06 / S04 / C02 / C10×2 / C11）

| 项 | 结果 | 证据与判据 |
|---|---|---|
| **T06** 彩绘/回退对照 | **PASS** | `41-t06-top.png`：六项同屏（碎颅锤/紫怨/天堂之戟 + 三卷轴）。左列为正常物品彩绘（蓝灰锤、紫花、红绿长戟、三卷轴绘本），右列为强制调用开源回退渲染器的抽象剪影（红锤/紫波/琥珀锤）；名称、颜色、主体与卷轴一一对应，正常彩绘未被抽象图标替换。右侧为强制渲染器对照，**不等于已验证自然缺图自动回退**（本批不删除/篡改 APK 素材） |
| **S04** 双满放弃 | **PASS** | `41-s04b-*`：载入后第4战保险库残骸（`s04_full · 种子1729`，本局999金币）；点"搬走锁芯" toast「背包放不下，请先整理再重试，或选择其他事件选项」（`41-s04b-toast-move.png`），点"拆解取通用材料"同 toast（`41-s04b-toast-salvage.png`），两次后事件页/金币/进度均不变；应用退前台再重进（`41-s04c-reenter-event.png`）事件仍未结算、三选项一致（**不重抽**）；点"放弃"推进到第5/5战（`41-s04e-abandoned.png`，对手冥界亚龙）；整备台背包含物截图（`41-s04g-bag.png`）：4×2 八格全为铁树枝枝干、安全箱仅 1 瓶预置物品——**未发放锁芯/精华** |
| **C02** 双碎颅高攻速 | **PASS** | `journal/c02b-*.jsonl`（282 tick）：`scenario_loaded c02_double`，玩家装备 `skull_basher×2 + hyperstone×2 + heart`，敌人 `heart`，无技能卡。80 条 stun timedControls **source 全部为 `gear:fixture:player:0:basher.stun`（唯一来源）**，无同时两条 stun；眩晕 `appliedAtTick=1`、remainingMs 800→100（0.8s）；`equipmentCooldowns.basher` 单键 3000→2300（128 帧，共享 CD 3000ms），CD 期间无第二次施加。本局概率触发成功（UI 场景不保证必中，确定性边界由单测覆盖） |
| **C10** 自然沉默触发推推 | **PASS** | `journal/c10f-*.jsonl`（601 tick）：tick 0→1 同帧 `protectionMs 0→2000` + `silence False→True`；沉默 source `gear:fixture:enemy:0:orchid.silence`，3000ms 全程持续、**零驱散事件**（不被推推驱散）；保护在 tick 1/121/241/361/481 五次同帧触发（紫怨每 12s 自动施放）。`41-c10f-early.png` 抓到游戏内控制日志条 `orchid.silence APPLIED 3000ms → EXPIRED → APPLIED 2000`。后撤演出为瞬时视觉，静态截图无法定格，以同帧保护字段为证 |
| **C10** 推推与长戟同组 | **PASS** | `journal/c10p-*.jsonl`（568 tick）：同帧保护 2000ms + 沉默持续零驱散；**长戟唯一获选的直接证据**——组 CD 起始值 c10f=**12000ms**（推推 internalCooldownMs）、c10p=**9000ms**（长戟 internalCooldownMs），两件共享 `uniqueGroup="force_staff"` 故 CD 同键；攻速 +40%：攻击间隔 14 tick（仅推推）→ 主导 10 tick（÷1.4）；protectionMs 单一 2000 非双份、无独立 CD |
| **C11** 六主动与双来源状态 | **部分**（夹具状态与持久化已证；点击/BKB驱散/自动切换未能在设备定格，逻辑由单测覆盖） | 已证：`41-c11-early.png` 六主动（黑皇杖/撒旦/疯狂面具/臂章/笛子/赤红甲）**全部手动**且标注"手动 · 点按切换"；四局 journal 均见双来源预置沉默 `enemy:0` 30000ms + `enemy:1` 20000ms（tick 0 起，30/20 秒为夹具显示值非紫怨正式时长）；双方 60 秒不攻击施法（600 tick 满血，超时判胜）；退前台战斗暂停、重进续战（第三局墙钟 207.7s 含 124s 暂停跳跃），重进后状态/选项保持一致。未证：设备上点击六主动出 CD、BKB 弱驱散两层沉默、自动开关触控、退出重进后 CD/模式视觉——重载后战斗自动开始且仅约 84s 墙钟（游戏 60s），多次交互尝试均被计时窗口/导航 dump 耗时耗光（每次 uiautomator dump 5–10s）；该部分逻辑由单测 `c11SixActivesAndMergedSourcesKeepManualModesAndCooldownsAfterRestart`（PASS）确定覆盖 |

### 过程中观察到的现象（非缺陷结论，记录备查）

- 重载同一专项预设会**自动开始对应战斗**并仅在前台计时（退后台暂停）；若上一局同场景战斗已结算，重载后 UI 可能不自动跳转战斗页，需经"远征 → 继续这次远征"进入。
- 底部导航栏（y≈2250）偶发误触将应用切到桌面（进程不退出、无 crash，logcat 无 FATAL）；战斗结束瞬间 UI 从战斗页跳奖励页，观感类似"闪退"，实为 60s 限时判胜后的正常转场。

## 结果回填要求与边界

逐项记录命令、配置JSON、APK哈希、实际XML数量/失败原文；设备项附截图与JSONL。失败不能仅凭类名归为旧问题。
本批S04验证真实事件连续拒绝→放弃的路径；没有声称已强制覆盖独立BalanceSampler进程的两次拒绝循环，采样器同分支如需专项证据可用此满背包状态另接工具夹具。
C02无敌斩分支固定roll检查的是引擎调用的装备回调分类；完整大招动作时序仍可另补设备验证。C11视觉与自动切换触控仍需设备。

## 后续42号说明（优先于前文推断）

为补C11设备操作证据，新增仅测试模式可用的暂停/单步/继续，C11默认第0帧暂停；操作表见`42-debug-battle-clock.md`。本机仅编译，不把新功能标为设备通过。

保留原始41号截图和日志，但收窄以下解释：80条stun快照不是80次独立施控；长戟为加法攻速+40，14→10tick不直接证明总攻速提高40%；同帧保护不证明后撤动画已验；无FATAL且PID存活只表明当次日志未见进程崩溃证据，不能据此排除Activity异常或把用户看到的退出桌面统一解释为正常转场。相关设备观察继续待取证。
