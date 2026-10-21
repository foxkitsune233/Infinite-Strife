# Infinite Strife 0.1.6 设计文档：任务系统 · 英雄等级 · 栏位强化

> 版本：v0.1.6-draft1（2026-10-21）
> 状态：待维护者审阅
> 前置版本：0.1.5（F300 / 1014）
> 硬约束：**严格复用现有美术资源与代码框架**，不引入新依赖、不新增 PNG 素材、不改动 `commonMain` 之外不必要的平台代码。

---

## 1. 系统总览

0.1.6 由三个相互咬合的系统组成：

| 系统 | 定位 | 接入点 |
|---|---|---|
| **英雄等级** | 长期成长主线。每英雄独立等级，提供永久战斗属性（现有成长体系明确「不加永久战斗属性」，等级系统补上这一块） | `CampaignProgress` 新字段 + `JourneyRules` 经验发放 |
| **任务系统** | 新的挑战节点与引导层。替代金币成为英雄解锁通道；产出技能栏碎片与两种护芯 | 新 `shared/quest` 包 + 在现有结算点挂 `QuestTracker` |
| **栏位强化** | 安全箱升满后的新追求。装备护芯 / 技能护芯开启死亡保护槽，失败不再掉光核心资产 | `GrowthKind` 扩两项 + `InventoryRules.settle` 分支改造 |

三者的关系：**任务系统是总线**——英雄解锁、护芯、技能碎片全部经任务发放；英雄等级是任务的目标类型之一（"任意英雄达到 N 级"）；栏位强化是任务奖励的消费出口。

---

## 2. 英雄等级系统

### 2.1 数据结构

### 2.1 数据结构

存放位置：`CampaignProgress.heroLevels: Map<String, Int>`（heroId → 等级，默认 `emptyMap`，读取时缺省视为 1 级，**旧存档零迁移成本**）。纯整数映射，不带经验字段——升级消耗见 §2.2。

### 2.2 等级锁、上限与升级消耗

```kotlin
object HeroLevelRules {
    const val MAX_LEVEL_DESIGN = 100   // 设计上限，下一个版本开放
    const val LEVEL_CAP = 10           // 当前版本锁，达到后不再接受升级
    fun upgradeCost(level: Int): Int = 100 * level   // Lv1→2 = 100，Lv9→10 = 900，到 10 级共 4500
}
```

**升级消耗营地金币**（`state.gold`），不引入新货币、不加经验条（2026-10-21 维护者定稿）。`LEVEL_CAP` 用 `const val` 显式声明为"版本锁"，注释标明下一个版本开放到 `MAX_LEVEL_DESIGN`，与现有 `ChallengeBalance.COUNT` 的同级写法保持一致。

### 2.3 每级增益

定位为**温和的永久成长**，10 级封顶时总增益约 +15% 攻击 / +20% 生命，不破坏 0.1.5 调平的战斗曲线。等级语义为 `level - 1`（**Lv1 无增益**）：

| 属性 | 每级 | Lv10 合计 |
|---|---|---|
| 攻击 | +1.5% | +15.5%（9 级 + 里程碑 2%） |
| 生命 | +2.0% | +20.0%（9 级 + 里程碑 2%） |
| 护甲 | +0.4 | +3.6 |
| 魔法抗性 | +0.5% | +6.5%（9 级 + 里程碑 2%） |
| 里程碑 | 每 5 级额外 +1% 全属性 | Lv5 / Lv10 各触发一次 |

实现方式：`HeroLevelRules.attackMultiplier / healthMultiplier / armorBonus / magicResistBonus` 四个纯函数，在**英雄快照构造处**（`JourneyRules.fighter()`，即战斗初始化时把等级换算后的数值写入 `Fighter`）一次性结算。**战斗内不再读等级**——保证 `DuelState` 的可复现性不受影响（战斗记录回放依旧有效）。

接入公式（已落地）：

```
finalAttack = baseAttack * (1 + 0.015 * (level - 1) + 0.01 * milestoneCount)
finalMaxHp  = baseMaxHp  * (1 + 0.020 * (level - 1) + 0.01 * milestoneCount)
milestoneCount = level / 5   // 整数除法，Lv5→1，Lv10→2
```

护甲为加法叠加（+0.4/级，保留一位小数）；魔法抗性为百分点加法叠加（+0.5/级）。

### 2.4 升级操作

`HeroLevelRules.upgrade(state, heroId, rules): InventoryResult` 纯函数事务：金币不足、已达版本锁、存档校验失败均返回操作前快照，不产生任何变更。UI 侧入口见 §2.5。

### 2.5 UI 复用

| 元素 | 复用现有 |
|---|---|
| 等级面板 | 整备工具 sheet 追加「英雄等级」项 → `GameCatalogSheet`（同"营地成长"），每已解锁英雄一张卡：头像 + 等级 + 当前加成 + 升级按钮 |
| 英雄头像 | `art/heroes/<id>-idle-v1.png`（现成，`GameArt.hero`） |
| 属性加成展示 | `GameUi.label` 两行文本，无新组件 |
| 入口 | 整备工具 sheet（`InventoryActivity` 的"更多"面板）追加「委托任务」「英雄等级」两项，**不新增页面** |

---

## 3. 任务系统

### 3.1 数据模型

新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/quest/QuestModels.kt`（纯 Kotlin，与 `growth` 包同层的配置驱动风格）：

```kotlin
enum class QuestKind { GUIDE, CHALLENGE, CONVERSION }

enum class ObjectiveType {
    WIN_BATTLES,        // 战斗胜利累计场次
    CLEAR_COUNT,        // 通关次数
    CLEAR_DIFFICULTY,   // 指定及以上难度通关次数
    EXTRACT_COUNT,      // 安全撤离次数
    HERO_WINS,          // 指定英雄胜利场次
    HERO_LEVEL,         // 任意（或指定）英雄达到等级
    MATERIAL_GAINED,    // 指定材料累计获得数（仓库 + 本局）
    EVENT_CHOICES,      // 事件选项选择次数
    SHOP_PURCHASE,      // 商店购买次数
    CRAFT_COUNT,        // 装备合成次数
    CONSUMABLE_USED,    // 战斗内使用恢复品次数
    SURVIVE_DEATHS      // 携带受保护物品死亡并保留的次数
}

data class QuestObjective(
    val type: ObjectiveType,
    val target: Int,
    val heroId: String? = null,     // HERO_WINS / HERO_LEVEL 专用
    val difficulty: Int = 0,        // CLEAR_DIFFICULTY 专用，0=简单起
    val materialId: String? = null  // MATERIAL_GAINED 专用
)

enum class RewardType { GOLD, HERO_UNLOCK, MATERIAL, GUARD_CORE, HERO_LEVEL_UP }

data class QuestReward(
    val type: RewardType,
    val amount: Int = 0,
    val heroId: String? = null,     // HERO_UNLOCK 专用
    val materialId: String? = null  // MATERIAL 专用
)

data class Quest(
    val id: String,                            // 如 "guide.skywrath"
    val name: String,
    val description: String,                   // 给玩家看的Plain文本
    val kind: QuestKind,
    val objective: QuestObjective,
    val rewards: List<QuestReward>,
    val prerequisiteQuestIds: List<String> = emptyList(),
    val minClears: Int = 0,                    // 与成长表一致的全局门槛
    val repeatable: Boolean = false            // CONVERSION 类为 true
)
```

任务内容表放 `QuestContent`（对象，风格对齐 `GrowthContent` / `EventCatalog`），配置与逻辑分离，维护者改任务不改代码。

### 3.2 进度存档

`CampaignProgress` 新增两字段：

```kotlin
/** 任务计数器：questId → 当前进度值。完成或领取后保留终值。 */
val questCounters: Map<String, Int> = emptyMap(),
/** 已领取奖励的任务 ID。 */
val claimedQuests: Set<String> = emptySet()
```

同样**旧存档零迁移**（默认空 Map / 空 Set）。计数器语义：

- 一次性任务：进度达到 `target` 即"可领取"，领取后写入 `claimedQuests`，不再计数
- 可重复任务（CONVERSION）：不写入 claimed，靠发放奖励时的材料扣除自然限流

### 3.3 触发钩子（全部挂现有函数）

`QuestTracker`（`shared/quest/QuestTracker.kt`）设计为**纯函数快照推进器**，与 `GrowthRules` 同风格：输入旧 state + 一次事件，输出新 state。

```kotlin
object QuestTracker {
    fun record(state: InventoryState, event: QuestEvent): InventoryState
    // event 覆盖：战斗胜/负、通关、撤离、事件选择、商店购买、合成、消耗品使用、
    // 材料获得、英雄升级（供 HERO_LEVEL 目标检查）
}
```

挂点清单（**不新增流程文件，全部在现有函数尾部插一行**）：

| 现有函数 | 位置 | 记录的事件 |
|---|---|---|
| `JourneyRules` 战斗结束 | 胜负分支 | `WIN_BATTLES` / `HERO_WINS` |
| 远征通关结算 | 通关分支 | `CLEAR_COUNT` / `CLEAR_DIFFICULTY` |
| `InventoryRules.settle` | survived=false 分支 | `GUARDED_DEATH`（带受保护物品死亡并保留时） |
| `EventCatalog` 选项结算 | 选项 effect 应用处 | `EVENT_CHOICES` |
| 商店购买函数 | 购买成功处 | `SHOP_PURCHASE` |
| 合成函数 | 合成成功处 | `CRAFT_COUNT` |
| 消耗品使用 | 战斗内使用处 | `CONSUMABLE_USED` |
| 材料入库 | 统一发放处 | `MATERIAL_GAINED` |
| `HeroLevelRules` 升级 | 升级成功处 | `HERO_LEVEL` 目标检查 |

### 3.4 英雄解锁改造（金币 → 任务）

`GrowthUpgrade` 增加字段：

```kotlin
val questId: String? = null   // 非空时该成长项目的资格 = 对应任务已领取
```

`GrowthRules.status()` 的 HERO 分支改为（已落地）：

```kotlin
if (u.kind == GrowthKind.HERO) {
    if (u.heroId in s.progress.unlockedHeroes) return GrowthStatus(false, "英雄已解锁")
    // 配了 questId → 任务通道（领取任务即解锁，不再要求金币与材料）；
    // 未配 questId → 沿用原金币+材料判定（任务内容补齐后逐英雄切换）。
    val questId = u.questId
    if (questId != null) {
        val quest = QuestContent.byId(questId) ?: return GrowthStatus(false, "英雄解锁配置缺失")
        if (questId !in s.progress.claimedQuests) return GrowthStatus(false, "需完成任务「${quest.name}」")
        return GrowthStatus(true, "资格与资源充足")
    }
}
```

**四名成长英雄当前仍走原金币通道**：`questId` 字段已就位但暂不挂值，等任务内容表补齐后逐英雄切换（`GrowthContent` 里把 `questId` 填上、`gold` 归零即可，代码无需再改）。切换后各配一条引导任务链，难度递进，替代原来的 300/500/700/900 金币梯度：

| 英雄 | 原条件 | 新条件（任务链） |
|---|---|---|
| 天怒法师 | 300 金 + 3 精华 + 撤离 | 引导任务链 A（2 环） |
| 冥界亚龙 | 500 金 + 5 精华 + 1 通 | 引导任务链 B（2 环） |
| 幻影刺客 | 700 金 + 7 精华 + 3 通 | 引导任务链 C（3 环） |
| 瘟疫法师 | 900 金 + 9 精华 + 5 通 | 引导任务链 D（3 环） |

`essence` 材料保留为其他消耗（现有事件/掉落不删），但不再是英雄门槛——解决"卡英雄 = 卡essence 难刷"的隐性问题。

### 3.5 技能碎片与护芯的产出

**skill_shard 难获取的根因**：`MaterialProgression` 的 pity 只保证"该类别会掉"，不保证数量；技能栏 3/4 档各需 2/4 片，实际周期长。0.1.6 的解法：

1. 新增 3 条 CHALLENGE 任务直接奖励 `skill_shard`（各 1–2 片，一次性）
2. `MaterialProgression.policy` 的 `"skills"` 阈值从 4 降到 3（同类材料更快触发 pity），**只改一个常量**
3. 任务奖励的 `MATERIAL` 类型走统一入库，自动计入 pity 的"已获得"逻辑（`MaterialProgression.record` 已按 `run.id` 前缀判定本局获得）

**护芯产出**：安全箱满级（3×3）后 `safe` 类别从 pity 的 `neededItem` 自然退役（`MaterialProgression.neededItem` 的 `safe` 分支在满级后返回 null，现有代码已处理）。此后 `safe_lock` / `safe_frame` 通过 **CONVERSION 任务**转化：

| 转化任务 | 上交 | 获得 | 重复 |
|---|---|---|---|
| 箱锁回收 | `safe_lock ×1` | `equipment_guard_core ×1` | 可重复 |
| 箱架回收 | `safe_frame ×1` | `skill_guard_core ×1` | 可重复 |
| 箱体检录 | `safe_lock ×2 + safe_frame ×2` | 任选护芯 ×3 | 一次性，给一次打包优惠 |

奖励发放处调用现有材料扣除逻辑（`GrowthMaterialPlanner` 同款的实例选择/原子扣除），失败不回滚计数器（计数器只在成功后 +1）。

### 3.6 UI 复用（不新增页面）

| 元素 | 复用现有 |
|---|---|
| 任务列表面板 | `GameCatalogSheet`（"营地成长"同款底部弹层，InventoryActivity 已用） |
| 任务图标 | `IconGlyph` 程序化图标：引导=STAR、挑战=CUP、转化=BOX（无需 PNG） |
| 进度显示 | `GameUi.label` + 现有进度条 |
| 奖励图标 | `ItemIconCatalog` 扩展映射（见 §5） |
| 入口 | 整备工具 sheet 现有四项（"补给商店/装备合成/永久成长/补齐空装备与技能栏"）追加第五项「委托任务」，**同层追加** |
| 完成提示 | 现有 Toast / message 通道 |

### 3.7 任务表（40 条，供 GitHub Issue）

完整清单见随本文件同目录的 `016-quest-list-issue.md`，此处只列分类结构：

| 分类 | 条数 | 说明 |
|---|---|---|
| A 框架与数据 | 6 | 数据模型、追踪器、UI 面板、存档 |
| B 英雄等级 | 5 | 等级字段、经验挂点、属性换算、UI、锁 |
| C 引导任务链 | 8 | 四英雄解锁链（2/2/3/3 环） |
| D 挑战任务 | 12 | 难度通关、英雄专精、事件、商店、合成等 |
| E 转化任务 | 3 | 安全箱材料回收 |
| F 栏位强化 | 6 | 护芯道具、保护槽、死亡结算改造、UI 标记 |
| 合计 | **40** | |

---

## 4. 装备栏 / 技能栏强化（死亡保护槽）

### 4.1 新道具定义

`ItemDefinition` 新增两条（现有 catalog 文件内追加，**无新美术**）：

```kotlin
"equipment_guard_core" to ItemDefinition("equipment_guard_core", "装备护芯", MATERIAL, 1, 1, "紫", maxStack = 9)
"skill_guard_core"    to ItemDefinition("skill_guard_core",    "技能护芯", MATERIAL, 1, 1, "蓝", maxStack = 9)
```

图标映射追加到 `ItemIconCatalog`：

```kotlin
putAll("equipment_guard_core", IconGlyph.SHIELD, GOLD, variant = 1)   // 金盾变体
putAll("skill_guard_core",    IconGlyph.SHARD,  PURPLE, variant = 1)  // 紫碎片变体
```

`IconGlyph.SHIELD` / `SHARD` 与对应 variant 配色**均已存在**（safe_lock 用 LOCK/GOLD、skill_shard 用 SHARD/BLUE），换 glyph + 换 tint 即是新外观，零素材。

### 4.2 强化档位

`GrowthKind` 扩两项：`EQUIPMENT_GUARD`、`SKILL_GUARD`。档位表追加到 `GrowthContent.upgrades`：

```kotlin
// 装备栏强化：保护槽 0→1→2→3
GrowthUpgrade("equip_guard:1", "装备保护 1 格", EQUIPMENT_GUARD, 1, 300, mapOf("equipment_guard_core" to 1), 2),
GrowthUpgrade("equip_guard:2", "装备保护 2 格", EQUIPMENT_GUARD, 2, 700, mapOf("equipment_guard_core" to 2), 4),
GrowthUpgrade("equip_guard:3", "装备保护 3 格", EQUIPMENT_GUARD, 3, 1200, mapOf("equipment_guard_core" to 3), 6),
// 技能栏强化：保护槽 0→1→2→3→4
GrowthUpgrade("skill_guard:1", "技能保护 1 格", SKILL_GUARD, 1, 300, mapOf("skill_guard_core" to 1), 2),
GrowthUpgrade("skill_guard:2", "技能保护 2 格", SKILL_GUARD, 2, 700, mapOf("skill_guard_core" to 2), 4),
GrowthUpgrade("skill_guard:3", "技能保护 3 格", SKILL_GUARD, 3, 1200, mapOf("skill_guard_core" to 3), 6),
GrowthUpgrade("skill_guard:4", "技能保护 4 格", SKILL_GUARD, 4, 1800, mapOf("skill_guard_core" to 4), 8),
```

`minClears` 门槛（2/4/6/8）让保护槽随游戏进度逐步开放，避免开局即满配。`GrowthContent.level()` 增加两个分支：

```kotlin
GrowthKind.EQUIPMENT_GUARD -> s.progress.guardedEquipment.size  // 用集合大小反推档位不可靠，改为显式字段
```

**显式字段更稳**（玩家可以取消保护再穿上，集合大小会抖动）：

```kotlin
// CampaignProgress
val equipmentGuardLevel: Int = 0,   // 0..3
val skillGuardLevel: Int = 0,       // 0..4
val guardedEquipment: Set<String> = emptySet(),  // 受保护的装备 instanceId
val guardedSkills: Set<String> = emptySet(),     // 受保护的技能卡 instanceId
```

档位上限校验进 `InventoryRules.validate()`：

```kotlin
if (state.progress.equipmentGuardLevel !in 0..3) return "装备保护档位无效"
if (state.progress.skillGuardLevel !in 0..4) return "技能保护档位无效"
if (state.progress.guardedEquipment.size > state.progress.equipmentGuardLevel) return "装备保护数量超过档位"
if (state.progress.guardedSkills.size > state.progress.skillGuardLevel) return "技能保护数量超过档位"
```

**不做位置校验**：保护标记是死亡白名单，不约束物品当前位置——玩家可把受保护装备卸回仓库（局外整备是合法操作），物品被出售/合成消耗后留下的陈旧 ID 同样容忍（与 `lockedItems` 的「Stale IDs are allowed」约定一致）。若强制「必须在栏位」，`settle` 把受保护物品统一移回仓库时会立刻被自己的校验打回。

### 4.3 死亡结算改造

`InventoryRules.settle(state, survived)` 的死亡分支，由：

```kotlin
val retained = if (survived) ... else state.items.filter { it.place.container == Container.WAREHOUSE || it.place.container == Container.SAFE }...
```

改为：

```kotlin
val retained = if (survived) {
    state.items.map { it.copy(place = ItemPlace(Container.WAREHOUSE)) }
} else {
    val keep = buildSet {
        add(Container.WAREHOUSE); add(Container.SAFE)
        state.progress.guardedEquipment.forEach { add(it) }   // instanceId 级白名单
        state.progress.guardedSkills.forEach { add(it) }
    }
    state.items.filter { it.place.container in keep || it.instanceId in keep }
        .map { it.copy(place = ItemPlace(Container.WAREHOUSE)) }
}
```

同时**保护标记永久保留、不随死亡释放**（2026-10-21 维护者定稿：永久有效直到手动取消）。受保护物品回仓库后仍带标记，下次出发继续生效；玩家想换保护目标需手动取消——这给了"护芯是长期投资"的实感，也让槽位成为需要主动管理的资源。

### 4.4 玩家操作入口

整备页（`InventoryActivity`）物品操作菜单（`edit()`，双击/编辑入口）对装备栏 / 技能栏内的物品追加「设为死亡保护 / 取消死亡保护」一项，文案按当前保护状态动态切换；sheet 副标题显示"已受死亡保护"。保护中的物品角标绘制留待 UI 打磨轮次（操作入口与结算逻辑已完备，不影响机制验收）。

---

## 5. 美术资源复用总表（零新增素材）

| 需求 | 现有资源 | 用法 |
|---|---|---|
| 任务面板 | `GameCatalogSheet` + `GameUi` | 底部弹层，同"营地成长" |
| 三类任务图标 | `IconGlyph.STAR / CUP / BOX` | 程序化绘制，已存在 |
| 装备护芯 / 技能护芯 | `IconGlyph.SHIELD`(GOLD, variant 1) / `SHARD`(PURPLE, variant 1) | catalog 追加两行映射 |
| 英雄等级条 | `GameUi` 进度条组件 | 同战斗 HUD |
| 保护角标 | `IconGlyph.SHIELD` 缩略 | 格子角标绘制 |
| 任务完成反馈 | 现有 Toast / message / 飘字通道 | 直接调用 |
| 英雄头像 | `art/heroes/*-idle-v1.png` | 现成 |

**唯一"新"内容是文本**（任务名、描述、UI 文案），全部走现有 strings 机制。

---

## 6. 存档兼容

`CampaignBinaryCodec` 升一个版本（当前 v14 → **v15**）：

| 新字段 | 编码 | 旧档读取 |
|---|---|---|
| `heroLevels` | int map（heroId, level, exp, totalExp） | 缺失 → 空 map（视为全 1 级） |
| `questCounters` | int map（questId, value） | 缺失 → 空 |
| `claimedQuests` | utf set | 缺失 → 空 |
| `equipmentGuardLevel` / `skillGuardLevel` | byte | 缺失 → 0 |
| `guardedEquipment` / `guardedSkills` | utf set | 缺失 → 空 |

编码风格完全对齐现有 `unlockedHeroes` / `lockedItems` 的写法（`writeInt(size)` + 逐项 `writeUTF`）。**旧存档升级 = 全部取默认值**，无数据迁移逻辑。编解码层的版本分发模式现有代码已具备（`LOG_CAP` 注释里提到的"旧版按 10 上限读回，v14 起放宽"即同款先例）。

---

## 7. 数值配置汇总

| 项 | 值 | 位置 |
|---|---|---|
| 等级锁 | `LEVEL_CAP = 10`（设计上限 100） | `HeroLevelRules` |
| 升级消耗 | `100 × 当前等级` 营地金币（到 Lv10 共 4500，不引入新货币） | 同上 |
| 每级增益（level-1 语义） | 攻 +1.5% / 血 +2.0% / 甲 +0.4 / 魔抗 +0.5%；每 5 级 +1% 全属性 | 同上 |
| Lv10 合计 | 攻 +15.5% / 血 +20.0% / 甲 +3.6 / 魔抗 +6.5% | 同上（单测断言） |
| 技能碎片 pity 阈值 | 4 → 3（附带调整，随 E 组提交） | `MaterialProgression.policy` 改一字符 |
| 保护槽上限 | 装备 3 / 技能 4 | `InventoryRules.validate` |
| 强化门槛（通关数） | 2 / 4 / 6 / 8 | `GrowthContent.upgrades` |
| 护芯价格（商店回收参考） | 150 金币 | `config/inventory.json` |

---

## 8. 里程碑与验收

| 里程碑 | 内容 | 验收标准 |
|---|---|---|
| **M1 数据层** | `HeroLevel` + `quest` 包 + `GrowthContent` 扩展 + 编解码 v15 | 单测覆盖：旧档读回、档位校验、保护槽上限、死亡保留与标记释放 |
| **M2 挂点** | §3.3 全部钩子 + 经验发放 | 单测覆盖每个事件的计数与奖励发放；全量回归 782→ 全绿 |
| **M3 UI** | 任务面板、等级条、保护标记、整备入口 | Pixel36 实机：任务可领、等级可升、保护槽可操作、死亡后保留 |
| **M4 收尾** | 数值走查、版本簿记、发布说明 | `version.properties` 1015；设备验收记录回填文档 |

**风险提示**：

1. `InventoryRules.validate` 新增的保护槽校验要跑在 `settle` **之后**（settle 内部调 `checked`），否则死亡当帧集合大小超标会被自己的校验打回——保护标记在 settle 返回前就要清空。
2. `HERO_LEVEL` 类目标依赖升级事件，而升级发生在经验发放函数内——追踪器要注意调用顺序（先发经验 → 再检查等级目标），否则同帧任务不计数。
3. 40 条任务只是初稿，具体数值与文案维护者过目后可能调整——所以任务表全部走 `QuestContent` 配置，不硬编码进逻辑。

---

## 9. 维护者口径确认记录

| # | 口径 | 结论（2026-10-21） |
|---|---|---|
| 1 | 等级锁 10 级是否 0.1.6 最终形态 | **是**。系统本体做完、锁不开放，下一版本提升到 100 |
| 2 | 升级消耗 | **营地金币**，`100 × 当前等级`；不引入新货币、不加经验条 |
| 3 | 保护标记是否一次性 | **否，永久有效直到手动取消**；死亡后标记保留，回仓库继续生效 |
| 4 | 引导任务链难度递进（2/2/3/3 环） | 待任务内容补充时定稿 |
| 5 | 任务英雄经验 | 不双轨：任务不发经验，`HERO_LEVEL` 目标检查金币升级的结果 |
