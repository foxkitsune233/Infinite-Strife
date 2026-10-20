# Infinite Strife

Android 单人远征与卡牌构筑游戏。

仓库：https://github.com/foxkitsune233/Infinite-Strife

## 项目方向

当前 Android 版是可提交的课程设计版本，后续仍会继续修复问题、平衡数值和补充内容。

项目将并行推进一个 Godot 原创设定版本：先完成 Windows 与 Android 原生运行的战斗闭环，再逐步替代 Android 专属界面与运行时。Godot 版不会直接搬运第三方角色、美术或名称；它以原创角色、世界观、卡图和音画资产为前提。完整范围、阶段门槛和迁移条件见 [Godot 过渡路线图](docs/ROADMAP.md)。

原生 iOS 包不在当前承诺范围内。未来只有在具备合规 macOS/Xcode 环境与真实 iPhone 验收设备后才启动。网页导出属于 Godot 版完成后的可选分发渠道，不是迁移前置条件。

## 开发

- 工程模块：`app`（Android）与 `shared`（共享规则）。
- 环境：JDK 17或更高版本、Android SDK；SDK路径配置在本地 `local.properties`。
- 构建：Windows运行 `./gradlew.bat :app:assembleDebug`。
- 测试由外部执行，验收清单位于 `docs/testing/`；构建成功不表示测试已通过。
- 美术资源不随源码分发，获取及补齐说明见 [ART-ASSETS](docs/ART-ASSETS.md)。

## 命名与版本

应用显示名 `Infinite Strife`，工程及仓库名 `Infinite-Strife`。Android包名沿用 `com.example.douqiuqiu`，保持覆盖安装与存档兼容。

新安装包使用 `Infinite-Strife-0.1.5-XXXX-XXXX-XXXX.apk` 格式；版本从 `app/version.properties` 读取。具体约定见 [命名规范](docs/releases/NAMING.md) 和 [修改索引](docs/releases/MODIFICATION_INDEX.md)。

当前工作副本目录可以保留旧名 `DouQiuQiu`；新克隆推荐目录名 `Infinite-Strife`。历史包和测试记录保留原名以便追溯。
