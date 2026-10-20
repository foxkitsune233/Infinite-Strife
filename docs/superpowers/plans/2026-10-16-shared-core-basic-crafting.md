# 共享核心与基础成装 Implementation Plan

> **For agentic workers:** Execute with executing-plans task-by-task; use TDD and verification-before-completion. No Git repository is available, so work in the existing checkout without creating Git history.

**Goal:** 以 Android 正在使用的同一份库存逻辑建立 JVM/JS 共享核心，加入护腕、怨灵系带、空灵挂件和真实消耗卷轴的局外合成。

**Architecture:** 新增 :shared Kotlin Multiplatform（JVM17 + JS browser）模块，移动纯领域代码，保持包名和外部工具实现不变。Android 界面、JSON读取、文件存档留在 :app；JS验证共享核心可编译，不声称完整网页客户端已完成。

**Tech Stack:** 现有 Kotlin2.0.21、Gradle8.12、Android Kotlin/XML、JUnit4.13.2；不升级已有工具链。

## Global Constraints

- 用户已采用F1/F2/F3和共享核心方案，新加三件基础成装；初始赠品不擅自增加成装或卷轴。
- ChargeRecovery/ShopRefreshClock/GrowthRequirementChecker由用户提供，保持实现及包名、原地保留app/system，不迁移或改写。03已明确交付，可以验收。
- 局外合成消耗精确组件及卷轴、无额外合成费、不拆解；绑定继承；失败不扣物不发产物。
- 只将纯Kotlin代码放入commonMain；java.io/org.json/Android留在平台侧。

### Task 1: 双端核心边界

Files: 新增shared/build.gradle.kts；修改settings/root/app Gradle；只移动Inventory.kt至shared/src/commonMain/kotlin/相同包名路径；三个外围工具和平台InventoryStore仍在app。

- [ ] 保留现有测试与接口，建立JVM+JS目标和Android项目依赖。
- [ ] 验证现有全部单测与Android构建，编译JS目标。

### Task 2: 基础合成与装备配置

Files: shared的Crafting.kt/Inventory.kt；app库存JSON和InventoryCatalog；app JVM测试CraftingTest与配置测试。

Interfaces: CraftingRules(inventoryRules, recipes).craft(state, recipeId, ingredientInstanceIds, outputInstanceId): InventoryResult；输入只读，返回新快照或原状态与错误。属性采用独立EquipmentStats，不与旧原型混算。

- [ ] 写失败测试：三种真实配方、缺卷轴、重复ID、错材料、绑定、阶段、原子失败、重放不能再合成。
- [ ] 实现数据驱动配方与校验，添加圆环、三件成装和三种卷轴。
- [ ] 为需要来源版本的属性/配方记录核对结果，游戏价格独立配置。

### Task 3: 可操作入口与交付

Files: InventoryActivity、装备说明与设计决策/进度文件。

- [ ] 显示属性及配方材料，新增局外合成入口，提交存档成功后才刷新界面。
- [ ] 提供基本组件/卷轴获取方式，避免合成只能写测试而无法在应用尝试；不绕过初始赠品或绑定规则。
- [ ] 独立代码复核；APK/JVM测试/JS编译；记录未完成的商店轮换、完整网页UI、旧战斗接入等限制。
