# 36号：经济v2、常规控制与v10存档外部验收

状态：**未执行**。本轮只编译，未运行单元测试、模拟器或平衡模拟。35号设计稿中的控制/经济条目由本表接续。

## 执行入口

使用`douqiuqiu-economy-controls-debug.apk`。Debug入口：设置→存档与诊断→通用测试模式→直接战斗→配装“自然控制与解控”；控制夹具选“无”，关闭弱敌。双方都有三件控制装、推推、BKB和撑血装备，观察真实施控而非永久布尔夹具。可以把玩家BKB改手动，分别检查自动与手动。

四／八卡流派及金币测试仍走实际规则。测试存档100000金币不代表正式新档600金币；Debug固定金箱6×6不代表首个白箱。

## 经济及宝箱

| ID | 场景 | 预期 |
|---|---|---|
| E01 | 建立全新正式等价存档 | 600金币、原9件绑定物品；旧档余额不变 |
| E02 | 首次生成、刷新同品质同难度 | 基础票价一致：白120、蓝220、紫420、金700；常规×1.5、进阶×2并向上取整到10 |
| E03 | 逐档新箱与原第6行/列旧箱 | 新箱4×3/5×4/6×5/6×6，旧箱原坐标保留并显示“旧版宝箱布局”；最大6×6 |
| E04 | 连续鉴定、重启、放弃 | 费10/20/30/40/50/50…；涨票规则保留，不退款，不重抽，演出不能跳过 |
| E05 | 低成本/常规/进阶逐战选金币 | 新局分别20/30/45/60/80、30/45/65/90/120、45/65/90/125/165；界面与实付一致 |
| E06 | v9正在第3战奖励页升级后领金币，随后新开一局 | 旧局原奖励版本1仍40或70；新局版本2为45/65/90。保存重启不改变版本 |
| E07 | 免费换箱耗尽、余额不足、付费换档 | 换前白20/蓝30/紫50/金80；失败不扣款，免费次数不错误消耗；其他箱不变 |
| E08 | 商店定时边界与付费刷新 | 付费50，自动优先免费；下一自动时间不推迟；按钮/确认/余额一致 |
| E09 | 破产救济，只有昂贵或鉴定涨价的目标 | 自动提供低成本白箱；120补助覆盖白票。仅白+难度0可用，蓝箱即使难度0也不能消耗补助；差额不够不消耗 |
| E10 | 白/蓝大量抽样，含成长保底 | 白/蓝不直接出现主要成装；普通单价上限300/1200，惊喜上限700/2000；材料保底不受价格阈值阻挡，收藏品按独立池 |
| E11 | 紫/金抽样 | 主要成装进入独立子池，候选抽取机会5%/10%；不是整箱出货率，不保证每次放得下 |

## 控制、权限与可视状态

| ID | 场景 | 预期 |
|---|---|---|
| C01 | 碎颅近战/远程，固定roll边界 | 近战roll<0.25、远程<0.10才触发；0.25/0.10不触发；不额外加伤，眩晕0.8秒，内置CD3秒；失败不启动CD |
| C02 | 两把碎颅、高攻速、无敌斩、完全格挡、闪避与普攻保护 | 同组只判一次；无敌斩不判；闪避/普攻保护整击失败不判；完全格挡但成功命中仍判 |
| C03 | 紫怨自动和手动 | 沉默3秒/CD12秒、不耗蓝；自动不重复覆盖沉默或对眩晕/妖术/放逐/无敌/免疫/死亡目标使用；手动允许合法刷新同来源 |
| C04 | 天堂对近战/远程 | 缴械2/3秒、CD12秒；不可弱/强驱散；禁止普攻但允许技能和普通物品；不穿BKB |
| C05 | 两来源同类控制、同来源重施 | 独立到期；同来源取更长剩余，不累加；最后来源消失才解除；每帧统一先推进双方计时，不能一方刚施加就被减100ms |
| C06 | 0/部分/100%抗性，退出恢复 | 施加时结算抗性，向上到100ms，最低一帧；恢复不重新计算抗性或重掷概率 |
| C07 | 亚巴顿受到碎颅，再附沉默/妖术 | 单独眩晕可施盾并强驱散；叠加沉默/妖术禁盾；强驱散不清天堂缴械 |
| C08 | BKB自动，眩晕结束只剩已过期状态 | 眩晕中不能开，负面已结束不追溯；沉默/缴械时可用，沉默弱驱散移除；不可驱散缴械保留计时但免疫期间抑制 |
| C09 | BKB期间拒绝新控制，旧长控制跨免疫结束 | 新控制不施加、主动不扣CD；已有控制继续计时，免疫结束尚未到期才恢复；不会暂停或延长 |
| C10 | 新控制命中推推/长戟持有者 | 当帧启动被动普攻保护并演出后撤；控制本身仍存在，不影响强/弱驱散分类 |
| C11 | 六主动、自动/手动切换、重启 | 可点击、CD不刷；状态条合并同类并显示最长倒计时及来源详情；免疫提示抑制数量 |
| C12 | 低成本及常规/进阶后期敌人 | 低成本/前三战无新增控制装；常规后期最多1件、进阶最多2件；常规第5战装备预算下限3000，控制候选选择机会25%，不保证出装 |
| C13 | 导出JSONL | before/after包含有效control与timedControls（source/caster/kind/remainingMs/dispel/appliedAtTick/suppressed）；摘要含APPLIED/拒绝原因/EXPIRED/DISPELLED/STRONG_DISPELLED/IMMUNITY_* |

## 存档与内容

| ID | 场景 | 预期 |
|---|---|---|
| V01 | v10非零控制+毒+护盾+装备CD+奖励版本往返 | 保存后字段完全一致，推进下一帧与未保存分支一致 |
| V02 | 独立按真实v9布局构建旧档 | 新控制池为空，旧布尔控制保留；奖励版本1；不能把v10文件头改9伪装迁移 |
| V03 | v1–v8旧档、损坏主档/备用、未来版本11 | 旧迁移不回放开场/赠送奖励；未来档拒绝且不回退覆盖；损坏档按现有备份策略 |
| V04 | 超24层、重复来源+施加者+种类、负/非帧时长、未来施加tick、非法枚举、来源不属于施加方装备/类型或驱散等级伪造 | 拒绝保存/读取，不覆盖良好原档 |
| A01 | 合成三控制装、购买、掉落，卷轴绑定继承 | 32条扩展配方含3条新增草案；原29条配方原样；27件主要装备。新增配方不称已核验Dota原版 |
| A02 | 查看三件装备/三卷轴、鹰歌弓、圣斧卷轴、代达罗斯卷轴 | 新图匹配；鹰歌明确为弓而非活鸟。图鉴、背包、商店与合成一致，未鉴定不泄图 |

## 外部采样工具

项目根目录执行（需要本机Kotlin编译器、JDK、Python；不改Gradle）：

```powershell
# 仅构建工具，不做采样
.\tools\sample_balance.ps1 -CompileOnly
# 先用小样本检查输出；扩大样本前保留/移走同配置CSV，工具拒绝覆盖
.\tools\sample_balance.ps1 -Mode boxes -Samples 100
.\tools\sample_balance.ps1 -Mode runs -Samples 10
```

boxes覆盖5个通关阶段×4档×3难度×6主题×Samples；调用真实`ChestFactory.sample`分支，不提交玩家存档；普通样本没有人为制造pending保底，因此保底另执行E10。

runs覆盖6英雄×6流派×4/8卡×治疗/金币策略×3难度×Samples；实际走五战、奖励、无风险事件与休息，不做第三战主动撤离。4卡为一星参考、8卡为二星参考，同档各流派使用同一组中性装备；钱包100000便于购票，不代表正式新手。不能把这种参考胜率当作新档600金币真实生存率。默认各装备自动，无手动玩家优势。

输出：`docs/testing/results/sampling/boxes-v2-<config>.csv`及`runs-v2-<config>.csv`。config为基础目录及shared源码摘要。工具从inventory.json导出基础TSV，额外内容读取真实shared卡/装备/收藏品目录，不镜像战斗/掉落算法。

每局最后一条战斗行才填钱包/可售物资/净资产变化，前面的行留空，防止汇总重复计算。失败时保留部分CSV，不把部分文件当完整样本。原始文件可能很大，先10/100，再按35号矩阵放大到100/1000。

### 旧自动化断言迁移提醒

现存测试未自动改写或执行。外部验收需先更新**新规则断言**，不要删除用例或改旧schema夹具：

- 当前保存版本9→10，未来版本10→11（ProfileV7CardStateTest、ProfileV9ExtensionTest、JourneyLifecycleAcceptanceTest等）。原始v1–v9夹具版本号保持原样。
- EquipmentCatalog主要装备24→27、扩展配方29→32，卷轴+3；原29条配方组件断言保持。
- HomeEconomyTest/ShopIntegrationTest/JourneyTest等硬编码60票、100补助、30换箱、80刷店、1200启动和旧奖励的“新生成”断言，按E表调整。旧存档固定目标仍保持旧票价；新建手工JourneyState默认奖励版本2，测旧奖励须显式version1。
- Profile未来档拒绝不能再把10当未来；必须写11。不要仅为全绿把真实生产状态改回旧值。

---

## 设备验收记录（2026-10-18）

状态：**部分执行**。APK `douqiuqiu-economy-controls-debug.apk`（19:11）已卸载重装到 `emulator-5554`（Android 16 / API 36，WHPX），系统动画倍率复位 1。调试入口：设置 → 存档与诊断 → 通用测试模式。

工具修正：`tools/_s33_drive.py` 的 `LOADOUTS` 映射补入 `CONTROL → 自然控制与解控`（之前缺此项，驱动会找不到入口）。新增 `tools/_parse_control.py` 解析战斗日志中的控制事件（`summary` 嵌套在 `before`/`after` 内，非顶层字段）。

### C 系列（控制 / 权限 / 可视状态）

样本：`自然控制与解控` 配装，主宰(玩家) vs 亚巴顿(敌方)，双方均带 `skull_basher / orchid / heavens_halberd / force_staff / black_king_bar / heart_of_tarrasque`，共 469 tick（日志 `test-battle-records/357c7148_24670bb9_ee45a3ca_1.jsonl`）。

| ID | 结果 | 关键证据（来自日志） |
|---|---|---|
| C01 | 部分验证 | 快照按战斗/目标/来源/施加帧去重可观察到玩家碎颅6次、敌方5次；这是下限。原46/80为滚动摘要重复统计，撤回事件次数结论。概率、CD和远程边界未实测 |
| C03 | 部分验证 | 沉默真实出现、摘要有APPLIED 3000ms；自动排重、受控/免疫拒绝及手动刷新尚待专项验证，不以读源码代替实测 |
| C04 | 部分验证（近战） | 两方向各可观察到4次缴械快照；远程3秒、不可驱散和权限矩阵尚待专项验证 |
| C07 | 部分验证 | 摘要有STRONG_DISPELLED语义；82为摘要重复出现数而非82次驱散。叠加沉默/妖术禁盾及不可驱散缴械仍需单独验证 |
| C08 / C09 | 部分验证 | 有DEBUFF_IMMUNE/IMMUNITY_SUPPRESSED语义；现有快照suppressed标记与免疫字段一致。原132/586不能作为独立事件次数，长控制跨免疫恢复及不追溯分支未验证 |
| C13 | 部分验证 | before/after共488次控制实例观察，0缺字段、0抑制标记不一致；去重可观察22次施控下限。摘要出现6类语义，但未见IMMUNITY_ENDED，6行上限不能证明完整事件无遗漏 |

**尚未用专门场景设备验证（仅源码确认或需特定夹具）：** C02（两件碎颅同组只判一次 / 无敌斩不判 / 完全格挡仍判）、C05（双来源同类独立到期、同来源取更长不累加、每帧先推进双方计时）、C06（抗性结算）、C10（推推/长戟持有者被控后撤）、C11（六主动状态条合并与来源详情，属可视）、C12（后期敌人控制装数量上限）。

### 采样工具

- `boxes` 模式：**完成** → `docs/testing/results/sampling/boxes-v2-8bc1925643c8e6b6.csv`（各矩阵单元 100 样本，config `8bc1925643c8e6b6`）。支撑 E10/E11 的箱格分布抽样。
- `runs` 模式：**失败，根因已确认（非“未知流派”）**。复跑 `BalanceSampler runs 10` 取证，完整堆栈见 `docs/testing/results/sampling/runs-v2-7a04542b93454a1e.csv.failure.txt`。真实抛点为采样脚本 EVENT 分支（`BalanceSampler.kt:116` 的 `require(it.ok){"Event transaction rejected: ..."}`），根因是**事件选项选择逻辑盲目**：当某事件无 NONE 风险可选项时回退到 `options.first()`，而该选项（`SAFE_WRECKAGE` 的 `wreckage.core`）要求背包空位、玩家背包已满，事件事务被正确拒绝并抛 `IllegalArgumentException: 背包放不下，请先整理再重试，或选择其他事件选项`。这是**采样脚本健壮性 bug**（真实游玩时玩家手选有空位的选项，校验本身正确），不是生产缺陷、也不是流派目录问题。首个案例 `juggernaut/basic/eight/HEAL/0/9`（round 4 EVENT）。旧 CSV 249 行、仅 basic 单流派，矩阵不完整。

### 待补（按优先级）

1. ~~使用37号补充诊断取得runs完整异常堆栈~~ 堆栈已确认（见上 runs 取证文件）。修复采样脚本 EVENT 分支：选项选取前应先用 `rules.validate(s)` 过滤掉会被事务拒绝（背包放不下等）的选项；若无可用选项则跳过该局而非崩溃；随后重跑 `BalanceSampler runs`（建议 100）补全矩阵。E10/E11主要由boxes支撑，runs用于整局胜率/收益，二者不能混用。
2. C02/C05/C06/C10 需构造专门夹具 battle（两件碎颅、抗性源、推推持有者被控等）——当前 `CONTROL` 配装各控制装仅一件，需改 `ScenarioFixtures` 或加调试参数。
3. E01–E11 经济/宝箱：**底层规则单测已全过**（HomeEconomyTest 11 / ShopIntegrationTest 21 / ShopPriceLockerTest 29 / ShopShelfSelectorTest 23 / ShopRefreshClockTest 14 / ThemedLootIntegrationTest 25 / ValuableLootIntegrationTest 8 / MaterialPityTrackerTest 15 等，见下方"单测全量复核"节）。**设备场景验收仍未做**：需驱动 HOME/APPRAISAL/REWARD 场景，核对 600 金币/9 绑定、票价阶梯、箱格布局、鉴定涨价、奖励版本 1↔2、换箱/刷店、破产救济，以及"界面与实付一致 / 演出不能跳过"等 UI 集成项（单测覆盖不到）。
4. V01–V04 存档：**单测已全过**（ProfileV5 12 / ProfileV6 10 / ProfileV7 5 / ProfileV9 4 / ProfileBackupTest 6 / JourneyTest 7 / JourneyLifecycleAcceptanceTest 13 / InventoryStoreTest 9）。覆盖 v10 字段往返、v9 旧档布局、v1–v8/损坏/未来版本拒绝、备份恢复。设备端 v10 存档往返仍可在模拟器补一次实机确认。
5. A01–A02 合成：**单测 + 脚本批验已全过**。EquipmentCatalogTest 25（27 mains + 5 intermediates + 三控制装、配方图无环/无缺组件、唯一组/效果建模）+ CraftingTest 2 + RecipeGraphValidatorTest 17（配方图完整性）+ OpenItemIconTest 3 + UiIconCatalogTest 4；另跑只读审计 `tools/audit_item_art.py`：131 个物品全部 `independent` 且已接线、0 `unwired`。原 29 条配方组件断言由 32 条扩展（含 3 条新增）承接，未称已核验 Dota 原版。
6. C11/C12 可视与敌人生成规则的设备核对。

### 单测全量复核（2026-10-18 晚，重要更正）

重新全量执行 `gradlew --offline testDebugUnitTest`：**772 tests, 0 failures, 0 errors, 0 skipped，BUILD SUCCESSFUL**（日志 `_run-full2.log`；XML 统计见 `app/build/test-results/testDebugUnitTest/`）。

**更正 39 号 F10 的"预存失败"结论**：那次失败跑的是**旧版测试源码**。铁证：旧日志失败行为 `EquipmentCatalogTest > e01_twentyFourMainsAndFiveIntermediates`（期望 24 mains），而当前源码为 `e01_twentySevenMainsAndFiveIntermediates`（`assertEquals(27, EquipmentCatalog.mains.size)`）。两次运行之间，测试断言已按新规则迁移（24→27 mains、29→32 配方、旧经济票价→新阶梯、v10 存档字段），故现全量全绿。**这些失败并非"待排查的预存问题"，而是已被修复**；也不属于护盾/吸血修复的回归（ShieldLedgerTest / CombatMathTest / DamagePipelineTest 本就通过）。

A/V 相关另以 `--tests` 单独跑 108 个用例（EquipmentCatalogTest 25 / CraftingTest 2 / RecipeGraphValidatorTest 17 / OpenItemIconTest 3 / UiIconCatalogTest 4 / ProfileV5·V6·V7·V9 + ProfileBackup + Journey + JourneyLifecycle 57）同样 0 失败，与全量一致。

据此：A01/A02、V01–V04 与 E 系列底层规则的**单测层面已全部通过**；剩余为设备场景集成（E 系列 UI 驱动、C02/C05/C06/C10 专门夹具、S04 双满放弃构造），需模拟器或专用夹具，不在单测范畴。

### 38号容量拒绝修正（以此节为准）

已按确切堆栈修正采样器，编译通过、未本机重跑。SAFE_WRECKAGE的wreckage.core本来就是risk=NONE；输入rules.validate(s)==null不证明奖励放得下。容量拒绝后，现在保持原状态，尝试其他合法免费无风险选项（拆解/放弃），并写.events.csv。其它错误仍保留证据并停止，不能跳过失败局以免偏置胜率。先跑种子9单例，再跑完整小样本；命令与S01–S06见`results/38-sampler-capacity-fallback.md`。原失败CSV保留，不改为通过。

### 39/40号后当前状态（优先于前文历史待办）

- `runs-v2-bd330604a9b7aaeb.csv.status.txt`为complete、cases=expected=4320、dataRows=16528。38号容量回退和39号护盾负伤害修复已通过该完整小样本；旧失败产物只作历史证据，不再代表当前阻塞。
- 40号明确修订了旧经济/目录/v10断言、死亡入场夹具及控制装备图标映射。外部全量结果已核对为59类772项、0失败/错误/跳过；不是测试顺序污染，也不能把早先失败笼统归为预存问题。
- 剩余优先做E01–E11设备交互与C11六主动/状态详情视觉验收。C12敌方控制装备数量与S04双满放弃原则上可用确定性单测/脚本夹具验证，不必一律依赖模拟器；当前仍缺专项证据。
- C02/C05/C06/C10专门场景继续记录待验，不阻塞上述验收。全量通过不等于这些分支已有针对性覆盖。
- 前文V统计中ProfileV5/V6/V7/V9、ProfileBackup、Journey、JourneyLifecycle合计57项；再计InventoryStoreTest 9项则为66项，不能仍称57项。总量以772项XML为准。

