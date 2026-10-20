# v1.0 界面用户友好度审计与改进方案（45 号）

- 审计时间：2026-10-19 08:34–09:10
- 环境：Pixel36 模拟器（1080×2400 / 393dp），`dist/v1.0-preview/DouQiuQiu-v1.0-preview.apk`（SHA256 `22db8629…d14cd2`），沙箱测试存档 c11_six
- 方法：实机走查「主界面 → 战斗 → 奖励 → 整备 → 营地」全流程 + 对照生产代码（MainActivity / JourneyActivity / InventoryActivity / InventoryBoardView / BattleSceneView / GameUi / DuelEngine）
- 截图证据：`docs/testing/results/shots/45-*.png`（main / battle / reward / inv-top / camp）

## 一、问题清单

| # | 界面 | 现象 | 根因（代码位置） | 严重度 |
|---|---|---|---|---|
| UX-01 | 整备 | 拖动配装极其困难：仓库在滚动区最底、装备栏在最上，源与目标永远不可同屏；Android 拖拽不会触发 ScrollView 自动滚动；拖影只是纯文本 | `InventoryActivity.warehouse()`（2 列大卡：64–108dp 图 + 3–4 行文字 + minHeight 100dp，每行约 160–200dp）与 `board(EQUIPMENT)` 分居滚动区首尾 | 高 |
| UX-02 | 整备 | 装备栏仅 62% 宽（右侧 38% 是 218dp 高英雄立绘），3 列 cell≈81dp，触控目标偏小 | `InventoryActivity.render()` 配装 tab：row 权重 0.38 / 0.62 | 高 |
| UX-03 | 整备 | 交互范式过载：点选→点空格 / 长按拖动 / 双击容器列表 / 「更多」7 项面板 / 「旋转」/ 「移动物品」→容器对话框→位置列表（三层深） | `InventoryBoardView` + `InventoryActivity.edit()/choosePosition()` | 高 |
| UX-04 | 整备 | 「补给/合成/成长」（动作）与「配装/技能/携带物资」（页签）两行按钮同样式，分不清哪个是 tab | `InventoryActivity.render()` tools / tabs | 中 |
| UX-05 | 主界面 | 三个近义入口：横幅标题「继续远征」、主按钮「继续这次远征 →」、底部 tab「远征」 | `MainActivity` + `ExpeditionBannerView` + `GameUi` 导航 | 中 |
| UX-06 | 主界面 | 双金币（营地 100000 / 本局 999）关系无说明；大数字无千分位 | `MainActivity.render()` | 中 |
| UX-07 | 战斗 | 战斗日志泄露内部 ID：`控制gear.fixture:enemy:1:orchid.silence] EXPIRED` | `DuelEngine.log()` 原文，`JourneyActivity` 日志渲染未做可读化 | 高 |
| UX-08 | 战斗 | HUD「通 0 · 物 0 · 法 0」无图例（实为三类护盾） | `BattleSceneView` 护盾行 | 中 |
| UX-09 | 全界面 | 预览包顶栏显示「测试存档 · c11_six · 种子1729」+ 调试时钟条，对试玩用户无意义且可疑 | `GameUi` 顶栏（TestProfile.active 时） | 中 |
| UX-10 | 商店 | 购买要两步：tile「查看商品」→对话框「购买」；售罄/金币不足也照样能点开 | `InventoryActivity.shop()` | 中 |
| UX-11 | 奖励 | 三张奖励卡各带动作按钮，卡片本体不可点，点选关系不明 | `JourneyActivity.rewardCard()` | 低 |
| UX-12 | 战斗 | 战斗中底部导航「整备/营地」可点，但整备锁定、仓库封存，点进去是死路 | phase 锁定逻辑（JourneyActivity / InventoryActivity） | 中 |
| UX-13 | 主界面 | 统计卡（营地金币/成功通关/已解锁英雄）占据首屏显著位置，信息价值低 | `MainActivity.render()` | 低 |

## 二、改进方案

### P0 文案与链路（本轮已执行；只碰 UI 呈现层，不动战斗/经济/存档逻辑）

| # | 改动 | 文件 | 对应问题 |
|---|---|---|---|
| 1 | 主按钮「继续这次远征 →」→「继续第 N 战 →」；本局金币补规则「失败清零、通关才带出」 | MainActivity | UX-05 / UX-06 |
| 2 | 营地金币千分位（100,000） | MainActivity | UX-06 |
| 3 | 「打开整备台」→「打开整备」，与底部 tab 同名 | MainActivity | UX-05 |
| 4 | 顶栏调试行简化为「调试存档 · 测试模式」，不再暴露场景名与种子 | GameUi | UX-09 |
| 5 | 护盾 HUD 加前缀：「护盾 通0 · 物0 · 法0」 | BattleSceneView | UX-08 |
| 6 | 战斗日志可读化：`控制[内部ID] EXPIRED` → 「对手沉默结束」；无法识别的行原样返回；journal 原始行不动 | JourneyActivity `friendlyLog()` | UX-07 |
| 7 | 商店购买一步直达：tile 主按钮「购买 / 售罄 / 金币不足」（禁用态置灰），「详情」次级按钮保留原说明弹窗 | InventoryActivity `shop()` | UX-10 |
| 8 | 奖励卡整卡可点（卡内按钮保留） | JourneyActivity `rewardCard()` | UX-11 |

### P1 结构改动（需确认后执行；需重跑 44 号 U 系列设备回归）

| # | 改动 | 风险 |
|---|---|---|
| 1 | 配装 tab 装备栏全宽：立绘缩为 64dp 头像或移入标题行，cell 81dp→131dp | 布局回归，U01/U02 重跑 |
| 2 | 仓库紧凑化 + 源目标同屏：3 列小格（图标+名+数）或顶部常驻横条；长按拖拽降级为辅助手段 | 交互绑定改动大，U04/U06/U07 重跑 |
| 3 | 选中物品后底部操作条直接给「放入装备 1–6 / 技能 1–4」快捷按钮，废弃三层对话框链路 | `edit()/choosePosition()` 调用面收敛，需回归 |
| 4 | 战斗中底部导航「整备/营地」置灰 + 提示「战斗中不可用」 | 低 |
| 5 | 工具行与页签行视觉区分（页签主色/下划线） | 低 |

### P2 打磨（待方向）

- 小屏/分屏按目标设备清单验证（320dp 已在 44 号覆盖）
- 操作反馈与过渡动画
- 持续 BGM：曲风、循环点、与战斗节奏配合、设置开关（待定）

## 三、回归与风险说明

- P0 只改 UI 呈现：MainActivity / GameUi / BattleSceneView / JourneyActivity（展示层）/ InventoryActivity（商店面板）。未改 shared、DuelEngine、InventoryRules、存档格式；journal 字段不变（`friendlyLog` 仅在 UI 侧做文本映射）。
- 回归范围：`testDebugUnitTest` + `assembleDebug`（结果见下节）。

## 四、外部复验执行记录

| 项 | 结果 | 证据 |
|---|---|---|
| 编译 + 单测 | <!--BUILD_RESULT--> | `docs/testing/results/sampling/_run-45-full.log` |
| 设备走查（P0 前基线） | 13 项问题定位 | `docs/testing/results/shots/45-*.png` |
