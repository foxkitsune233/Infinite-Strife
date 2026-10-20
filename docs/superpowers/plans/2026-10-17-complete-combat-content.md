# 新毒、装备、事件与敌方构筑实施计划

**Goal:** 将已有内容与纯工具接入Android单局，状态统一保存为v9（保留外部已交付v8的布局）。
**Architecture:** shared内复用PoisonEngine、EventCatalog、EquipmentCooldownTracker和UniqueBonusSelector；DuelEngine按100ms推进，Android只负责操作与二进制存档。四块分开文件，主任务统一集成。
**Tech Stack:** Kotlin 2.0.21 commonMain/JVM/JS，Android原生View，既有JUnit4。

规则授权：用户要求继续全部四块，并明确采用poison.md整套卡牌/羁绊/Viper首轮数值。既定事件和装备数值按v1基线执行，配方先查证后投放。用户要求测试外部执行，因此这里只编译，测试表与用例交外部。

- [x] 新毒：CardCatalog30卡启用，CardEffectRuntime附毒；Fighter新增nullable新毒池及序号，旧战斗保留旧毒续完；DuelEngine逐tick推进/驱散/毒伤恢复；P2逐星间隔8/6/4秒。
- [x] 装备：EquipmentCombat汇总六生效栏、UniqueBonusSelector按家族/效果择强；EquipmentRuntime保存共享CD、激活期限与暴击计数；接主动、命中、反击和受控保护。跨战只减30秒，整备换位保留CD，新一局清空。
- [x] 配方：核对Liquipedia/可靠源真实组件信息，记录版本证据；保留已有ID兼容，适配后的游戏数值单列。
- [x] 八事件：JourneyEvents生成固定实例、求条件、原子交付/代价/占格；JourneyRules与UI复用，同一节点不能重抽，满包先整理。
- [x] 敌方构筑：EnemyBuilds以本局seed/场次/难度稳定生成实际卡装；READY展示英雄/主流派，开战使用同一构筑。
- [x] v9：每Fighter附毒/装备状态，每Journey附事件与跨战CD；兼容v1～v8真实布局，校验集合/来源/期限，不通过修改旧头部伪造迁移。
- [x] 外部验收表覆盖30卡/装备唯一组/主动/毒驱散/8事件/预览一致/存档与回归；仅执行assembleDebug :shared:compileKotlinJs :app:compileDebugUnitTestKotlin。

实施与编译完成。最终命令 assembleDebug :shared:compileKotlinJs :app:compileDebugUnitTestKotlin 返回 BUILD SUCCESSFUL；未执行测试或模拟器。实际自动化与手动验收仍由外部按最终基础测试表完成。
