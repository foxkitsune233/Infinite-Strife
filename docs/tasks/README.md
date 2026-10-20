# 可分配的外围任务

## 当前：27号通过，28号补直接行为覆盖

27号报告与现有XML已核对：**46类661例，0失败/错误/跳过**，B1及审批旧断言关闭。主任务未重跑测试。完整首版基础已接入；后续重点是直接实战覆盖、设备交互验收与平衡。

[28号补充验收](28-foundation-coverage-supplement.md)提供三个独占测试文件：真实毒卡/英雄、装备触发、局内生命周期/旧档衔接，可分别交外部。部分旧覆盖映射仅有模型/工具证据，28号补足直接运行断言。报告写results/28-foundation-coverage-supplement.md；M01～M05仍待设备执行。以下“待27验收”等段落保留为历史。

## 最新基础接入与待外部验收

主任务已接30卡/六羁绊、新毒/Viper、完整首版装备/29配方、八事件、敌方构筑与v9存档；本批APK/JVM/JS及测试源码只做编译，不自动执行测试/模拟器。外部下一步按[最终基础测试表](../testing/2026-10-17-final-foundations-test-plan.md)分为装备配方、新毒30卡、事件敌人、v9兼容四组执行，统一报告到results/27-final-foundations.md。新增EquipmentBattleIntegrationTest16例、ProfileV9ExtensionTest4例；既有事件9例/敌人5例保留。

外部26号641例/6失败是历史结果：五个毒卡旧审批契约已依用户批准修改；B1空护盾池遮蔽的原因已修；这些尚需本批真实回归，不把源码修复称为零失败。20～26原报告不改写。

## 最新主任务实现：原生整备交互与H02首批

营地/宝箱/整备/战斗界面已改造；支持点选放置、长按拖放、落点提示、边缘滚动、底部操作面板和状态条。H02的DamagePipeline与18暴击工具已实际接DuelEngine；10/17/19及完整卡装备效果仍未接入。本轮仅APK/JS/测试源码编译成功，15项新增单测与UI运行验收交外部：[测试总表](../testing/2026-10-17-native-ui-damage-test-plan.md)。现有38类576项为此前外部基线，不代表本轮运行通过。

当前主任务负责Android核心业务。04～19已交付；20～24外部自动化验收已交付并核对报告与现有XML：全工程31类439项，0失败、0错误、0跳过。主任务未重复运行测试。手动UI验收仍未执行；新毒规则与修订卡牌尚未实现，不在本轮通过范围内。

## 剩余开发交接

用户因用量限制改由外部继续实现，完成后交主任务校验。完整范围、H01～H10依赖/改动文件/验收要求见[剩余实现交接清单](2026-10-17-remaining-implementation-handoff.md)。这不是可同时修改核心文件的十个独立任务；按文档安排集成人和落地顺序。

H03/H08纯模块修复已由外部交付报告（514项基线）；H01内容模型/30卡/装备表也已交付，现有XML38类576项零失败。最新主任务已接入24张非毒卡、五流派羁绊、PRD及v7运行状态存档，新增27例测试源码并编译，等待[外部卡牌验收](../testing/2026-10-17-card-runtime-v7-test-plan.md)。配方核验未完成、6张毒卡仍隔离；新毒与8事件纯模块尚未接实战。新内容恢复流派采用restore；life/evade只留旧原型兼容，不加入新六流派。

## 最新交付：20～24（自动化验收完成）

| 任务 | 内容 | 独占测试文件 |
| --- | --- | --- |
| [20](20-shop-business-verification.md) | 货架/锁价/库存/付费与自动刷新/过期报价 | inventory/ShopIntegrationTest.kt（扩展已有） |
| [21](21-growth-business-verification.md) | 英雄与容量成长、资格/精确消耗/原子失败 | inventory/GrowthIntegrationTest.kt |
| [22](22-profile-v5-verification.md) | v1～v4迁移v5、备份/损坏/进度保留 | inventory/ProfileV5MigrationTest.kt |
| [23](23-themed-loot-verification.md) | 六主题/4-6-8箱/材料保底生成与终局计数 | inventory/ThemedLootIntegrationTest.kt |
| [24](24-profile-v6-verification.md) | v6主题/保底字段、旧v5原箱迁移 | inventory/ProfileV6MigrationTest.kt |

详细总表见[商店成长测试表](../testing/2026-10-17-shop-growth-test-plan.md)与[主题保底测试表](../testing/2026-10-17-themed-loot-test-plan.md)。报告位于[results](../testing/results/)，20～24分别21/16/12/25/10项通过。下列分工约定保留供追溯，本批无需重复派发。22原范围保留，v6专门由24验收。

## 上一批：17～19已交付

| 任务 | 内容 | 独占主体文件 |
| --- | --- | --- |
| [17](17-cooldown-reduction.md) | 多源乘算减CD、唯一组择高、常规/幽魂下限 | combat/CooldownReduction.kt |
| [18](18-critical-strike-resolver.md) | 高倍率优先、成功停止、暴击增量、不连乘 | combat/CriticalStrikeResolver.kt |
| [19](19-unique-bonus-selector.md) | 单刀/双刀家族按效果分别择强 | equipment/UniqueBonusSelector.kt |

## 上一批：14～16已验收

| 任务 | 内容 | 独占主体文件 |
| --- | --- | --- |
| [14](14-shop-shelf-selector.md) | 常备/6轮换/1珍品、进度与确定性选货 | shop/ShopShelfSelector.kt |
| [15](15-shop-price-locker.md) | 阶段需求系数限幅、整批锁价、基础回收价 | shop/ShopPriceLocker.kt |
| [16](16-growth-material-planner.md) | 仓库精确材料实例选择、绑定优先、原子缺额 | growth/GrowthMaterialPlanner.kt |

## 上一批：11～13已交付

| 任务 | 内容 | 独占主体文件 |
| --- | --- | --- |
| [11](11-shield-ledger.md) | 分来源三类盾、同源覆盖/天怒累计、吸收顺序 | combat/ShieldLedger.kt |
| [12](12-poison-timeline.md) | 独立毒层、连续伤害、各层到期 | status/PoisonTimeline.kt |
| [13](13-action-permission.md) | 眩晕/沉默/妖术/禁用物品下的动作资格 | status/ActionPermission.kt |

## 上一批：08～10已交付

| 任务 | 内容 | 独占主体文件 |
| --- | --- | --- |
| [08](08-synergy-counter.md) | 卡种去重、双标签、2/3/4累加羁绊 | cards/SynergyCounter.kt |
| [09](09-dispel-resolver.md) | 按来源标记执行弱/强驱散筛选 | status/DispelResolver.kt |
| [10](10-equipment-cooldown-tracker.md) | 唯一组冷却、跨战减30秒且不重复抵扣 | equipment/EquipmentCooldownTracker.kt |

## 上一批：04～07已交付

| 任务 | 内容 | 难度 | 独占主体文件 |
| --- | --- | --- | --- |
| [04](04-fixed-grid-packer.md) | 矩形格子自动摆放/旋转 | 小～中 | shared的grid/FixedGridPacker.kt |
| [05](05-recipe-graph-validator.md) | 缺失组件/循环配方/构建顺序检查 | 中 | shared的inventory/RecipeGraphValidator.kt |
| [06](06-combat-math.md) | 护甲魔抗/恢复/增伤/吸血/换装公式 | 小～中 | shared的combat/CombatMath.kt |
| [07](07-material-pity-tracker.md) | 分类保底累计与生成消耗 | 中 | shared的loot/MaterialPityTracker.kt |

## 统一交付约定

- 20～24只允许自己的测试和报告两个文件；01～19生产工具批次按原需求主体+JUnit两个文件。不要顺手修改其他文件或工作日志。
- 生产代码放shared/commonMain，只用Kotlin通用标准库；禁止Android、java.*、org.json、系统时钟、文件/网络、未注入的系统随机数和UI依赖。14仅按输入seed执行明确约定的确定性算法。
- 测试放app/src/test，现有JUnit4.13.2和shared依赖可直接用。按任务给定--tests过滤执行，报告实际用例数，不只说BUILD SUCCESSFUL。
- 不改Gradle、核心现有模型、Activity、配置、InventoryStore、JourneyRules或DuelEngine；不移动/改写任务01～03的文件。
- 不把交付自动接入业务。主任务复核后统一接入，防止多个开发者同时改库存/战斗/存档。
- 多人用独立工作目录交付最好；若共用同一目录，每人严格只写自己的两个文件，Gradle构建错峰运行。不要各自运行覆盖式配置生成脚本。
- 实现前先阅读本任务需求，不需要通读整个历史对话。接口可内部优化，外部签名保持约定；发现规则矛盾先反馈，不自行新增玩法。

## 可复制的派发提示

> 请在项目 `D:\android_project\DouQiuQiu` 中完成 `docs/tasks/20-shop-business-verification.md` 指定的外部验收。只修改指定测试文件并新增指定报告，不改生产代码、其他测试、Gradle或配置。按测试表写真实行为断言并执行自己的过滤测试，交付实际测试数量、逐项结果及可复现问题。发现主任务问题只报告，不降低预期或越界修复。

把示例中的20文件名换为21/22/23/24即可派给其他开发者。

## 前序任务

01 ChargeRecovery、02 ShopRefreshClock、03 GrowthRequirementChecker已交付并核对。01～03现阶段仍位于app/system，之后由主任务协调迁移，不与本批任务交叉。
