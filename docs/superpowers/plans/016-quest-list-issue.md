# Infinite Strife 0.1.6 任务清单（40 项）

> 用途：本文件为 GitHub Issue 草稿。建 issue 时标题建议 `[0.1.6] 任务系统 · 英雄等级 · 栏位强化 开发清单`，正文直接粘贴下方内容。
> 设计依据：`docs/superpowers/plans/2026-10-21-016-quest-level-guard.md`
> 状态：初稿，待维护者逐条补充/调整（勾选状态仅表示起草时的分组规划）。

## A · 框架与数据层（6）

- [ ] A1 新建 `shared/quest/QuestModels.kt`：QuestKind / ObjectiveType / QuestObjective / QuestReward / Quest 数据类（纯 Kotlin，风格对齐 `growth` 包）
- [ ] A2 新建 `shared/quest/QuestContent.kt`：任务配置表（40 条任务全部配置化，逻辑零硬编码；含 `byId()` 查询）
- [ ] A3 新建 `shared/quest/QuestTracker.kt`：纯函数快照推进器 `record(state, event)`，覆盖全部 9 类事件（战斗胜负/通关/撤离/事件选择/商店/合成/消耗品/材料获得/英雄升级）
- [ ] A4 `CampaignProgress` 新增字段：`questCounters`、`claimedQuests`、`heroLevels`、`equipmentGuardLevel`、`skillGuardLevel`、`guardedEquipment`、`guardedSkills`（全部带默认值，旧存档零迁移）
- [ ] A5 `CampaignBinaryCodec` 升 v15：7 个新字段编解码，旧档缺失取默认值（对齐 `unlockedHeroes` 的编码风格）
- [ ] A6 单测：旧档读回（v14 存档无新字段）、计数器单调性、可重复任务不限流、一次性任务领取后不再计数

## B · 英雄等级（5）

- [ ] B1 新建 `shared/hero/HeroLevel.kt`：`HeroLevelRules`（`LEVEL_CAP=10`、`MAX_LEVEL_DESIGN=100`、`upgradeCost = 100×等级`、四个属性换算纯函数）
- [ ] B2 `upgrade(state, heroId, rules)` 纯函数事务：扣营地金币、升一级；金币不足/已达锁/校验失败均拒绝且零变更（不引入新货币、不加经验条）
- [ ] B3 属性接入 `JourneyRules.fighter()`：攻/血乘系数、护甲与魔抗加法叠加（攻 +1.5%/级、血 +2.0%/级、甲 +0.4/级、魔抗 +0.5%/级、每 5 级 +1% 全属性）；战斗内不读等级，回放可复现
- [ ] B4 等级 UI：整备工具「更多」面板追加「英雄等级」项 → GameCatalogSheet 每已解锁英雄一张卡（头像 + 等级 + 当前加成 + 升级按钮）
- [ ] B5 单测：费用曲线（到 Lv10 共 4500）、10 级锁、level-1 语义边界（Lv1 无增益 / Lv5 里程碑 / Lv10 = 攻 1.155 血 1.20 甲 3.6 魔抗 6.5）、多英雄独立、旧档默认 1 级

## C · 引导任务链：英雄解锁改任务（8）

- [ ] C1 `GrowthUpgrade` 增加 `questId` 字段；`GrowthRules.status()` HERO 分支改为校验任务已领取（不再要求金币/材料）
- [ ] C2 `GrowthContent` 四条 HERO 项目配置改写：gold 归零、essence 移除、挂对应 questId
- [ ] C3 引导链 A（天怒法师，2 环）：A-1「初试锋芒」胜利 3 场 → A-2「稳妥撤离」安全撤离 1 次 → 奖励解锁天怒法师
- [ ] C4 引导链 B（冥界亚龙，2 环）：B-1「 difficulty 普通通关 1 次」→ B-2「使用恢复品 3 次」→ 奖励解锁冥界亚龙
- [ ] C5 引导链 C（幻影刺客，3 环）：C-1「困难难度胜利 5 场」→ C-2「合成装备 3 次」→ C-3「携带 1000+ 金币通关 1 次」→ 奖励解锁幻影刺客
- [ ] C6 引导链 D（瘟疫法师，3 环）：D-1「累计获得 10 份材料」→ D-2「事件选择 8 次」→ D-3「精英难度通关 1 次」→ 奖励解锁瘟疫法师
- [ ] C7 任务面板入口：整备工具 sheet 追加第五项「委托任务」，面板用 `GameCatalogSheet`（同"营地成长"），三类任务分组展示（STAR/CUP/BOX 图标）
- [ ] C8 单测 + 实机：任务完成后永久成长面板出现对应英雄、资格文案正确显示任务名、领取后不可重复领

## D · 挑战任务（12）

- [ ] D1 难度系列 1：「简单征服者」简单难度通关 3 次 → 金币 200
- [ ] D2 难度系列 2：「困难征服者」困难难度通关 2 次 → skill_shard ×2
- [ ] D3 难度系列 3：「大师征服者」大师难度通关 1 次 → equipment_guard_core ×2 + 金币 500
- [ ] D4 难度系列 4：「噩梦征服者」噩梦难度通关 1 次 → skill_guard_core ×2 + 金币 800
- [ ] D5 英雄专精 1：「幻影之锋」幻影刺客胜利 6 场 → 英雄经验 300
- [ ] D6 英雄专精 2：「亚龙之毒」冥界亚龙胜利 6 场 → 英雄经验 300
- [ ] D7 英雄专精 3：「天怒之耀」天怒法师胜利 6 场 → 英雄经验 300
- [ ] D8 等级目标 1：「十级门槛」任意英雄达到 5 级 → 金币 300 + 英雄经验 200
- [ ] D9 等级目标 2：「百级之基」任意英雄达到 10 级（版本锁上限）→ skill_guard_core ×2
- [ ] D10 经济向：「淘宝专家」商店购买 15 次 → backpack_fabric ×2
- [ ] D11 事件向：「遗迹探索者」事件选择 20 次 → essence ×5
- [ ] D12 挑战向：「向死而生」携带受保护物品死亡并成功保留 1 次 → equipment_guard_core ×1（与 F 组联动验收）

## E · 安全箱材料转化任务（3）

- [ ] E1 CONVERSION 任务「箱锁回收」（可重复）：上交 safe_lock ×1 → equipment_guard_core ×1；发放走原子扣除，失败不回滚计数器
- [ ] E2 CONVERSION 任务「箱架回收」（可重复）：上交 safe_frame ×1 → skill_guard_core ×1
- [ ] E3 一次性优惠任务「箱体检录」：上交 safe_lock ×2 + safe_frame ×2 → 自选护芯 ×3；仅安全箱满级（3×3）后可见

> 附带调整（不单列条目）：`MaterialProgression.policy` 的 `"skills"` 阈值 4 → 3，缓解 skill_shard 获取周期（改一个常量，随 E 组提交）。

## F · 装备栏 / 技能栏强化（6）

- [ ] F1 道具定义：`equipment_guard_core`（装备护芯，紫，堆叠 9）、`skill_guard_core`（技能护芯，蓝，堆叠 9）加入物品目录
- [ ] F2 图标映射：`ItemIconCatalog` 追加 SHIELD/GOLD/variant1 与 SHARD/PURPLE/variant1（零新增素材）
- [ ] F3 `GrowthKind` 扩 `EQUIPMENT_GUARD` / `SKILL_GUARD`，`GrowthContent.upgrades` 追加 7 档（装备 3 档、技能 4 档，minClears 2/4/6/8），`level()` 与 `nextChoices()` 同步
- [ ] F4 保护标记操作：整备页装备/技能栏长按菜单追加「设为受保护 / 取消保护」，受保护物品绘制金色盾牌角标（SHIELD glyph 缩略）
- [ ] F5 `InventoryRules.validate` 增加 4 条校验（装备/技能档位范围、数量不超档位；标记是死亡白名单，不约束位置，陈旧 ID 容忍）；`settle` 死亡分支保留受保护物品，**标记永久保留不释放**
- [ ] F6 单测 + 实机：满档保护（3 装备 + 4 技能）死亡全保留、超档位被拒、取消保护后死亡掉落、标记死亡后正确释放

## G · 收尾（合并进上各组，不占条目）

- 数值走查（§7 配置表逐项对账）
- 全量回归（基线 782 tests，目标全绿）
- `version.properties` → 0.1.6 / versionCode 1015
- 设备验收记录回填 `docs/testing/`
- 发布说明并入 `docs/releases/MODIFICATION_INDEX.md`
