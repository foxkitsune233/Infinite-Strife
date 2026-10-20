# 掉落与事件内容表（0.1.4 / X05）

对应 `docs/tasks/0.1.4-external-handoff.md` 的 X05。本文只登记**已实现的规则与数值**，供平衡调整与外部验收对照；不引入新机制。所有数值均从下列源码位置提取，改动时必须同步本表与对应单测。

代码落点：

| 内容 | 文件 |
|---|---|
| 战后掉落生成 | `shared/.../journey/NodeLoot.kt`（`NodeLootRules.generate`） |
| 战斗金币 | `shared/.../journey/JourneyRules.kt`（`battleLoot`） |
| 金币数值 | `shared/.../journey/EconomyBalance.kt`（`goldReward` / `ticket`） |
| 事件产出 | `shared/.../journey/JourneyEvents.kt`（`outcome` / `pools`） |
| 鉴定计时 | `shared/.../journey/ChestLayout.kt`（`AppraisalTiming`） |
| 敌人档位 | `shared/.../journey/EnemyBuilds.kt`（`budgets`） |
| 目标箱例外 | `shared/.../journey/JourneyRules.kt`（`complete`） |
| 结算（死亡/撤离） | `shared/.../inventory/Inventory.kt`（`settle`） |

---

## 1. 总览：战后三选一已废除

旧版战后「回血 / 金币 / 物品」三选一（`JourneyRules.reward`）已永久取消，调用直接返回
`"战后三选一已取消，请整理掉落后继续"`。现行链路：

```
战斗胜利 → battleLoot() 生成地面掉落 + 发放战斗金币 → REWARD 阶段（鉴定→拾取/放下）→ finishLoot() 进入下一节点
```

- 掉落物先落**地面**（不属于玩家），经鉴定后才可拾取；批量放入见 X01（`collectRevealedIntoBackpack`）。
- 战斗金币在胜利当刻直接计入 `runGold`（本局资金），与地面物品相互独立。

## 2. 敌人档位与掉落规模

敌档由**宝箱难度** `target.difficulty ∈ {0,1,2}` 与**回合** `round ∈ 1..5` 共同决定
（`EnemyBuilds.budgets`，先难度后回合）：

| 难度 | 回合 | 卡牌 | 二星卡 | 金卡 | 装备件数 | 装备价值上限 | 敌我缩放 |
|---|---|---|---|---|---|---|---|
| 0（低） | 1 | 1 | 0 | 0 | 1 | 60 | 0.66 |
| 0 | 2 | 2 | 0 | 0 | 1 | 100 | 0.70 |
| 0 | 3 | 3 | 0 | 0 | 2 | 230 | 0.74 |
| 0 | 4 | 4 | 0 | 1 | 2 | 360 | 0.78 |
| 0 | 5 | 4 | 1 | 1 | 3 | 540 | 0.82 |
| 1（中） | 1 | 2 | 0 | 0 | 1 | 80 | 0.82 |
| 1 | 2 | 3 | 0 | 0 | 2 | 160 | 0.86 |
| 1 | 3 | 4 | 0 | 0 | 2 | 360 | 0.90 |
| 1 | 4 | 4 | 1 | 1 | 3 | 460 | 0.94 |
| 1 | 5 | 5 | 2 | 2 | 3 | 540 | 0.98 |
| 2（高） | 1 | 2 | 0 | 0 | 1 | 80 | 0.98 |
| 2 | 2 | 3 | 0 | 0 | 2 | 230 | 1.02 |
| 2 | 3 | 4 | 1 | 1 | 3 | 410 | 1.06 |
| 2 | 4 | 5 | 2 | 1 | 3 | 540 | 1.10 |
| 2 | 5 | 6 | 3 | 2 | 4 | 720 | 1.14 |

缩放公式 `scale = 0.62 + round × 0.04 + difficulty × 0.16`（作用于敌人生命与攻击）。

**掉落数量**（`NodeLootRules.generate`）：

| 来源 | 数量 |
|---|---|
| 战斗掉落（回合 1–4） | 1 + rand(3) → **1–3 件** |
| 战斗掉落（回合 5） | 3 + rand(3) → **3–5 件** |
| 事件搜索掉落 | 2 + rand(5) → **2–6 件** |

## 3. 物品池

- 候选池 = 全部物品定义中 `minimumDropClears ≤ 当前通关数 clears` 的定义（按 id 排序）。
  当前配置 26 个定义：EQUIPMENT 9 / CARD 4 / CONSUMABLE 4 / SCROLL 3 / MATERIAL 5；
  门禁分布 `clears 0 → 23 个、clears 1 → 1 个、clears 3 → 1 个`（无 LOOT 类型定义入池）。
- 品质基础权重：金 3 / 紫 12 / 蓝 35 / 白及其他 80。
- 价格惩罚：`weight = base ÷ (1 + price ÷ (250 + difficulty × 150))`，下限 1。
  **难度越高，高价物品权重越大**（分母放大），回合不影响权重。
- 掉落**可含任意品质与类型**（装备、卡牌、消耗品、卷轴、材料），绑定随定义 `boundOnAcquire`。
- 事件搜索掉落可指定保底物（`guaranteed`，如分配的怜悯物品 `pendingItem`）。

## 4. 金币范围

### 4.1 战斗金币（胜利当刻计入 runGold）

`EconomyBalance.goldReward`，版本 2（仅对新进入的挑战生效）：

| 难度 ＼ 回合 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| 0（低） | 20 | 30 | 45 | 60 | 80 |
| 1（中） | 30 | 45 | 65 | 90 | 120 |
| 2（高） | 45 | 65 | 90 | 125 | 165 |

单局理论上限：五场全胜 = 低 235 / 中 350 / 高 490，加事件搜索产出。

### 4.2 事件搜索金币

事件选项掉落物品时，额外发放 `goldReward ÷ 2`（同上表折半），同样计入 runGold。

### 4.3 入场与携带

| 项目 | 规则 | 代码 |
|---|---|---|
| 入场门票 | 宝箱票价 `ticket`，救济券可抵最高 120 | `JourneyRules.enter` |
| 携带金币 | `carryGold ≥ 0` 且 `钱包 ≥ 票价 + carryGold`；支付后从钱包扣除、等额进入 runGold，**无额外上限** | `JourneyRules.enter` |
| 事件入场费 | 事件选项消耗**本局金币** `priceGold = 20 + round × 10 + difficulty × 10`；不足返回「本局金币不足」 | `JourneyEvents.generate` / `EventCost.Gold` |

票价表 `EconomyBalance.ticket`（公式 `round10(基础价 × 难度倍率 ÷ 2)`，基础价 白 120 / 蓝 220 / 紫 420 / 金 700，难度倍率 2/3/4，取整到 10）：

| 品质 ＼ 难度 | 0 | 1 | 2 |
|---|---|---|---|
| 白 | 120 | 180 | 240 |
| 蓝 | 220 | 330 | 440 |
| 紫 | 420 | 630 | 840 |
| 金 | 700 | 1050 | 1400 |

## 5. 鉴定需求

- 单件鉴定时长 `durationMs = clamp(500 + 宽×高×65 + 品质阶×160 + ln(1+价格)×45, 600, 2400)`，
  品质阶：白 0 / 蓝 1 / 紫 2 / 金 3。
- **三个并行鉴定台**：第 n 件占用最先空闲的台（`AppraisalTiming.startsMs`），节点总时长 = 最晚完成时刻。
- 事件掉落额外 800ms 开场（`NodeLootRules.openingMs`），战斗掉落无开场。
- 未鉴定物品不可拾取（`"物品正在鉴定，请稍候"`）；批量放入会将其列入待鉴定清单（X01）。
- 离开掉落节点要求鉴定全部完成（`finishLoot` 校验）。

## 6. 事件产出（八类事件）

事件由 `JourneyEvents.generate` 按种子选类（池非空才可选），选项产出如下
（`hp` 为对最大生命的百分比，负值受伤；`roll` 为事件快照内预存随机数）：

| 事件 | 选项 | 产出 | 消耗/风险 |
|---|---|---|---|
| 废弃仓库 | search | roll<70 得 1 件恢复品（药水/ tango / 仙灵火 / 奶酪），否则 -10% 生命 | 无 |
| 锈蚀工坊 | pry | roll<60 得 1 件组件，否则 -12% 生命 | 无 |
| 锈蚀工坊 | pay | 必得 1 件组件 | 本局金币 priceGold |
| 废弃图书馆 | delve | 得 1 张技能卡；roll≥60 再 -15% 生命 | 无 |
| 废弃图书馆 | study | 必得 1 张技能卡 | 需交付 1 张本局技能卡（显式选择） |
| 安全箱残骸 | core | 得 1 件核心材料（safe_lock，clears≥1） | 无 |
| 安全箱残骸 | salvage | 得 1 件精华（essence） | 需交付指定类型本局物品 |
| 科技终端 | convert | 得 1 个技能碎片（skill_shard） | 需交付 1 件本局物品 |
| 科技终端 | gamble | roll<50 得 1 个技能碎片，否则 -15% 生命 | 本局金币 priceGold |
| 诊所 | gold | +25% 生命 | 本局金币 priceGold |
| 诊所 | supply | +40% 生命 | 需交付 1 件恢复品 |
| 前进障碍 | rush | roll<60 -10% 生命，否则 -25% 生命 | 无 |

产出物走**地面掉落 + 鉴定**链路（`NodeLootSource.EVENT`，2–6 件，其中保底 1 件为选项产出）；
生命归零即死亡（仅安全箱物品带回）。

## 7. 目标箱例外（第五战）

- 第五战胜利后 `complete()` 将目标宝箱物品**直接写入仓库**（`Container.WAREHOUSE`），
  **不受背包容量与格子形状限制**，绑定随定义。
- 同次结算还有：本局全部随身物品入库、runGold 全额入账（`settle(survived=true)`）、
  `clears + 1`、记录通关ending。

## 8. 死亡与撤离规则

| 情形 | 处理 | 代码 |
|---|---|---|
| 战斗死亡 / 事件死亡 | `abandon` → `settle(survived=false)`：**仅仓库与安全箱物品带回**，runGold 清零不入账 | `JourneyRules.abandon` |
| 第三战后撤离 | `extract`（REST 节点）→ `settle(survived=true)`：全部物品 + runGold 入账，标记 extracted | `JourneyRules.extract` |
| 通关 | 见上节目标箱例外 | `JourneyRules.complete` |

## 9. 验收对照（外部执行）

| 样例 | 预期 |
|---|---|
| 低/中/高档各打一场 | 金币按 4.1 表到账；掉落件数符合第 2 节 |
| 跨多个 clears 后进节点 | 高门禁物品（clears 1 / 3）才入池 |
| 事件选项扣费 | 本局金币不足时拒绝且不产出 |
| 携带金币入场 | 钱包扣除票价+携带额，runGold 等额增加 |
| 第五战通关 | 目标箱直接入库，背包满也照样入库 |
| 死亡 | 仅安全箱+仓库物品带回，runGold 清零 |
