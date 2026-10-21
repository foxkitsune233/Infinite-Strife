# Issue #1 云端验证记录

日期：2026-10-21。基线：`91cf54320bc6ba466cac23a544e7949f817a90ef`。

## 已执行

在 dot 云端独立检出基线，应用修复后重新编译与运行，而非仅复用其他环境的测试结果。

- `git apply --check` 与 `git diff --check` 通过。
- OpenJDK 21；Kotlin JVM 编译器 2.0.21，目标字节码 17；JUnit 4.13.2。
- 编译所有 shared/commonMain 源码，以及 app 中不依赖 Android 的 engine、model、system、inventory、ui/components 源码与 JVM 测试。
- 基线：988 项测试，979 通过、9 失败。
- 修复版：1010 项测试，1001 通过、9 失败。失败名称与基线完全相同，没有新增失败。
- 专项测试：`QuestTrackerTest`、`QuestProgressionTest`、`QuestPersistenceTest`、`ProfileV13MigrationTest`，40 项全部通过。

专项覆盖40任务/24节点、16单节点/8三选一、奖励定义、顺序领奖、互斥与重复领取、失败回滚、每档副本门槛、普通开放阶段安全箱材料保底、成功/失败事件计数、低难度替代路径、旧档成长认可、v14旧档和进行中远征、存档重启与备份恢复。

## 基线已有失败

- `AttackAtlasAssetTest` 的2项：仓库不含所需攻击图集资源。
- `EquipmentProcAcceptanceTest.maskSelfSilenceBlocksTheUltUntilTheWindowEnds`
- `EquipmentProcAcceptanceTest.maskManualActivationFollowsTheSameWindowAndCooldown`
- `PoisonCardHeroAcceptanceTest.poisonAndSpellAmplificationShareOneLinearPool`
- `CombatModuleIntegrationTest.abaddonCastsWhileStunnedButNotWhileSilencedOrHexed`
- `CombatModuleIntegrationTest.aphoticShieldAddsToIndependentStartingCardShield`
- `InventoryStoreTest.v3AbaddonBattleShieldDoesNotBecomeAnExtraSourceOnNextCast`
- `SingleRunIntegrationTest.actualStartingBuildCanReachFiveBattlesAndClearOnDeterministicSeeds`

这些既有战斗数值/资源问题不属于本次任务系统范围，未掩盖或修改断言。

## 验证边界

本记录是直接 Kotlin/JUnit 验证，不等同于完整 Android Gradle 构建。`OpenItemIconTest` 与 `VersionLogTest` 共6项依赖 Android UI 或生成的资源类，未纳入上述两组对照。Android Activity 与 PlayerRepository Android入口仅完成代码审查，未进行本环境 Android 编译、APK安装或设备视觉验收。

当前环境没有 Android SDK，尚未接受新 SDK 许可。获得许可并准备 SDK36/Build Tools35.0.0 后，仍需执行 `:app:testDebugUnitTest`、`:app:assembleDebug`，并人工检查任务面板、奖励刷新、三选一关闭和难度提示。此前其他环境产生的 APK 不作为本次最终代码的构建证明。

## 复现方式

标准环境使用项目的 Gradle8.12、AGP8.10.0、Kotlin2.0.21 与 SDK36：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

本次无SDK检查使用 Maven Central 的 Kotlin compiler embeddable2.0.21 及其依赖、JUnit4.13.2、Hamcrest1.3，通过 `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17` 编译上述纯JVM源码，再由 `org.junit.runner.JUnitCore` 从仓库根目录运行测试。Android依赖文件及上述两类UI测试被明确排除；未生成假Android类或替换被测逻辑。
