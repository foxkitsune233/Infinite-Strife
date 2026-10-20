# 42号：独立测试存档的战斗时钟

## 目标与范围

41号外部记录中全量782项通过；C11只验证了初始状态，设备点击六主动、BKB驱散和手动/自动切换仍未完成。为避免导航和截图消耗60秒战斗窗口，增加调试时钟；不延长正式限时、不修改shared规则、不重新生成战斗或RNG。

仅Debug且已激活独立测试存档时显示控制条。暂停标记保存在测试模式专用preferences，不加入正式战斗存档schema；重新载入预设按配置重置标记，退出测试模式清除标记。正式存档始终按原时钟运行。

## 使用

安装`app/build/outputs/apk/debug/douqiuqiu-debug-clock-debug.apk`（覆盖安装，不卸载/清数据）。

设置 → 存档与诊断 → 通用测试模式 → C11六主动：**现在默认载入即暂停在第0帧**。
旧的`41-c11-six.json`同样采用该默认值，也可使用：

```json
{"fixture":"c11_six","seed":1729,"startPaused":true}
```

通用模式表单增加“载入后暂停战斗时钟”；完整旧JSON缺省仍不暂停。专项JSON缺省按预设设置，显式startPaused可覆盖。

战斗页顶部显示“调试时钟：暂停/运行 · 帧N · N×100ms”及三个按钮：

- **暂停**：取消后续tick；看状态详情、截图、导航或重启均不消耗游戏时间，watchdog不自动续跑。
- **单步100ms**：仅暂停时可点，每次走一次真实JourneyRules.tick，先保存再显示，再次保持暂停。
- **继续**：从当前持久帧继续每100ms调度，不跳过暂停时间，不追赶墙钟。

暂停时允许真实手动物品与模式切换：手动物品即时生效并保存，冷却在游戏时间推进后才减少；切成自动后不会立即运行自动逻辑，需单步或继续。不要把暂停时不减CD当作缺陷。

日志操作新增`debug_clock:pause`、`debug_clock:step`、`debug_clock:resume`。手动物品/模式切换仍沿原JSONL记录，不把暂停/继续记成战斗tick。

## 外部验收表（未执行）

| ID | 操作 | 预期 |
|---|---|---|
| D01 | 重新载入C11，等待30秒、截图、多次dump | 始终暂停、tick=0；预置两来源沉默30000/20000ms，六主动手动，未超时结算 |
| D02 | 点击BKB | tick仍0；immunityMs=3000，两层可弱驱散沉默清除，BKB冷却开始；其他物品仍可点 |
| D03 | 依次点击其余5件主动，记录CD，再暂停等待 | 六组CD；不随墙钟减少。疯狂面具沉默不禁止普通主动装备；截图各槽不挤压遮挡 |
| D04 | 暂停状态单步一次，然后等待 | tick恰+1，已激活的免疫/控制/装备计时按真实规则减100ms；只出现一条debug_clock:step，之后不继续跑 |
| D05 | 切一件未使用的主动为自动，暂停等待，然后单步 | 等待期间不使用；单步时按真实自动条件/权限触发；显示模式和日志一致 |
| D06 | 仍暂停时退出应用/重开，不重载夹具 | 同一战斗、tick、CD、模式及暂停标记保留；不再从0开始，也不因watchdog推进 |
| D07 | 继续运行，等几帧后暂停，重复继续 | 单一循环推进，无双倍tick；暂停立即生效，继续只从当前帧推进，不补发墙钟流逝 |
| D08 | 暂停→继续至第600帧 | 仍按60秒规则结算一次，最终视觉尾帧后正确进入奖励；不会无限战斗 |
| D09 | 退出测试模式，进入正式挑战；另装release作静态/设备对照 | 无调试条、无单步能力，测试暂停标记不抑制正式挑战时钟 |
| D10 | 重新载入C10或普通未勾暂停的场景 | 自动运行，旧C10首帧控制与保护不变；重新载入C11则恢复默认暂停 |

建议D05单独重载C11，再选笛子或赤红甲切自动，避免D03已消耗CD影响判断。D06保持同一次attempt，不能点“重复上次场景”当作恢复。

全量回归由外部执行：

```powershell
.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs
```

本批未新增单测，不因42号编译成功改变782项历史通过结果。重点是UI调度与生命周期设备验证；无需重跑4320局采样。

## 外部复验执行记录（2026-10-19 实跑）

**环境**：emulator-5554（Pixel36）；APK `app/build/outputs/apk/debug/douqiuqiu-debug-clock-debug.apk`，SHA256 `584a3b35e75c047bfbe94c7935944c2dc7698b6785f5730997f5a64b42e7550c`（与 `app-debug.apk` 同构建同哈希），覆盖安装（`-r`，未卸载、未清数据）。入口：设置 → 存档与诊断 → 通用测试模式 → 专项夹具。回归日志 `docs/testing/results/sampling/_run-42-full.log`；截图 `docs/testing/results/shots/42-*.png`；JSONL `docs/testing/results/journal/42-*.jsonl`。

### 全量回归

| 项 | 命令 | 结果 |
|---|---|---|
| 回归 | `gradlew --offline --rerun-tasks testDebugUnitTest assembleDebug :shared:compileKotlinJs` | **PASS**：`42 actionable tasks: 42 executed`，BUILD SUCCESSFUL in 1m34s；XML 合计 **782 tests / 0 failures / 0 errors**（60 类）。本批未新增单测，782 与历史一致 |

### 设备验收（D01–D10）

| ID | 结果 | 证据与判据 |
|---|---|---|
| **D01** 重载即暂停/墙钟不耗 | **PASS** | `42-d01-loaded.png`：调试时钟「暂停 · 帧 0 · 0ms」+ 三按钮；t+10/20/30s 三次 dump 始终 暂停/帧0，未结算；`42-d01-status-detail.png` 状态详情「沉默 · 2来源 30.0秒」+ `gear:fixture:enemy:0:orchid.silence · 30.0秒 · BASIC` + `enemy:1 · 20.0秒 · BASIC`；journal `scenario_loaded`：双沉默 30000/20000 BASIC、六主动全手动、attempt=b39eb4f9 |
| **D02** BKB 驱散 | **PASS** | 点黑皇杖后时钟仍 帧0；journal `use_equipment:fixture:player:0`：`immunityMs=3000`、`timedControls=[]`（两层 BASIC 沉默全清）、`equipmentCooldowns.bkb=12000`；撒旦按钮仍显示「立即使用」（其余可点）。BKB 点击发生在双沉默生效期——**沉默不禁用主动装备**（权限表仅眩晕/妖术/禁用物品禁 ACTIVE_ITEM） |
| **D03** 六主动 CD | **PASS** | 依次点撒旦/疯狂面具/臂章/笛子/赤红甲，每次时钟仍 帧0；journal 末态六组 CD 与定义逐一相符：`bkb=12000, satanic=12000, mask_madness=8000, armlet=60000, pipe_of_insight=10000, crimson_guard=10000`；再等 15s 墙钟，CD/免疫/沉默剩余**逐值不变**（暂停不随墙钟衰减）；臂章开启后 maxHp 2004→2554（+550）。`42-d03-six-used.png` 六槽同屏无挤压遮挡 |
| **D04** 单步 | **PASS** | 单步一次：tick 0→1，沉默 30000→29900；BKB 后再单步：tick→2，`immunityMs 3000→2900`、`bkb 12000→11900`（真实 JourneyRules.tick 规则）；journal 仅 2 条 `debug_clock:step`、0 条 `tick`；单步后等 10s 记录数不变（不自动续跑） |
| **D05** 自动切换 | **PASS** | 重载 C11（恢复暂停帧0）后把洞察烟斗切「自动 · 点按切换」，journal `mode:fixture:player:4:automatic`；暂停等 8s 无使用、记录数不变；单步一次：`pipe_of_insight=10000` + 魔法盾 300 同帧落地（按真实自动条件触发）；模式标签显示与日志一致 |
| **D06** 退出重开 | **PASS** | HOME 重开（进程存活）：时钟仍 暂停/帧1、笛子自动标签在、attempt=9a58d58f 不变、6s 无新记录（watchdog 不推进）；force-stop 冷启动：回主菜单「继续这次远征」→ 同一战斗 tick=1、暂停标记在、`pipe CD=10000`、manualItems 已移除笛子（自动保留）、attempt 不变；再等 6s 仍帧1。持久化全程经 durable save，无 watchdog 补推 |
| **D07** 继续/暂停循环 | **PASS** | journal 计时（运行中不 dump）：继续 5s → 41 条 tick 严格 +1（均速 138ms/tick）；暂停立即生效（帧41），3s 墙钟 tick 不变、0 新记录；再继续 3s → 新增 26 条，tick 41→67（Δ=26=新增条数，**无补发墙钟爆发**）；全序列 67 条严格 +1 无双倍 |
| **D08** 600 帧结算 | **PASS** | 继续跑满：journal 末帧 `tick=600, won=true`（尾帧 玩家 1994.1/2004 vs 敌人 913.6/1028，与41期轨迹一致）；`42-d08-reward.png` 奖励页「第 1 战 · 胜利 / 剩余生命 1994 / 2004」+ 生命补给/金币补给/神秘战利品三卡；无无限战斗 |
| **D09** 正式对照 | **PASS** | 退出测试模式 → 正式主菜单（远征营地，480 金币，无测试存档/调试时钟）；经「挑选目标宝箱 → 综合补给 120 金币 → 购票入场 → 开始第 1 战」进入正式挑战：战斗页无调试时钟控件，`files/battle-records/`（正式 journal 目录，与沙箱 `test-battle-records` 分开）234 条 tick 正常推进（profile=normal，scenario=正式存档）——**测试暂停标记不抑制正式时钟**。release 对照：`assembleRelease -x lintVitalRelease`（离线缺 lint-gradle 插件）BUILD SUCCESSFUL，用 AGP 注入签名（debug keystore）产出 `app-release.apk` 覆盖安装：主菜单与正式战斗均无调试条/无测试模式入口，正式战斗跑完进奖励页（`42-d09-release-*.png`）。生产发布签名仍需正式密钥 |
| **D10** C10/C11 重载 | **PASS** | 重载 C10：`42-d10-c10-running.png` 时钟「**运行** · 帧 391 · 39100ms」（自动运行无暂停），417 ticks 无继续操作；journal tick1 沉默=True + `protectionMs=2000`（与41期首帧控制/保护一致）；重载 C11：`42-d10-c11-reload.png` 恢复「暂停 · 帧 0 · 0ms」默认暂停 |

### 过程中记录的现象与坑（非缺陷结论）

- **HUD 折叠线**：战斗页滚动内容中，竞技场计时「N / 60 秒」与 HUD 护盾/状态行位于折叠线下方（被底部固定配件栏遮住），需上滑内容才能看到；调试时钟标签与三按钮位于顶部常驻可见。D01 状态详情需先上滑再点状态行。
- **运行中 dump 失效**：战斗运行时 UI 每 ~140ms 刷新，`uiautomator dump` 取不到 idle 快照连续失败返回空节点——曾造成两次自动化误判（D01 in-battle 检测、D07 首跑），均非应用缺陷；运行态证据改用 `screencap` + journal。
- **正式 journal 分目录**：`BattleRecorder` 按 `TestProfile.active` 分流——沙箱写 `files/test-battle-records/`，正式写 `files/battle-records/`；release 包不可 `run-as`（非 debuggable），其证据以截图为准。
- **退出按钮位置**：「退出测试模式 · 恢复正式存档」位于测试模式表单底部（y≈2062，固定栏上方），需滚动到可见再点。
- **tick 节拍**：模拟器上 100ms 调度 + 处理开销 ≈ 138–148ms/tick；暂停/继续不追赶墙钟（D07 Δ=新增条数已证）。

## 41号证据解释补充

- 快照中80次stun出现是重复观察，不是80次独立施控；独立事件至少按source/caster/kind/appliedAtTick去重。
- 长戟给的是加法攻速+40，不是总攻速×1.4。14→10 tick还受基础属性、长戟属性、增益有效窗口和100ms离散影响，不能凭两局主导间隔证明“提高40%”；9000/12000ms共享组CD及已通过固定输入单测支持获选者差异。
- 未抓到FATAL且PID存活，只能说该日志窗口未发现进程崩溃证据；不能排除Activity退出/重建、导航错误或其他异常。用户观察到的退出桌面现象继续保留为待定位，不统一解释成正常结算转场。
- 同帧protectionMs变化证明保护状态触发，不等同已验证后撤动画。动画仍需录像或合适帧截图。
