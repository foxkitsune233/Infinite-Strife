# Infinite Strife — Godot

这是 **Infinite Strife / 无尽纷争** 的 Godot 迁移工程。与 Android 原生版本并行开发，对齐 Android 1.0；英雄任务改版按用户确认的历史提交 `255a1b2` 恢复。验收追平后由 Godot 接替。Godot 使用独立新存档，当前复用 Android 美术。

## 当前开发入口

- 双端预览版下载：[Godot 0.1.0-preview.1（Windows / Android）](https://github.com/foxkitsune233/Infinite-Strife/releases/tag/godot-v0.1.0-preview.1)。含Windows ZIP、Android APK和SHA256校验值；Android尚未完成真机验收，详见发行说明。
- Android请使用[0.1.0-preview.2竖屏修正版](https://github.com/foxkitsune233/Infinite-Strife/releases/tag/godot-v0.1.0-preview.2)，可直接覆盖preview.1保留存档；Windows继续使用preview.1的ZIP。

- Godot 开发分支：`codex/godot-migration`，该分支根目录为Godot工程；仓库`main`继续保留Android版本。两端存档独立，暂不合并Godot分支到Android主线。

- Android 参考仓库：[foxkitsune233/Infinite-Strife](https://github.com/foxkitsune233/Infinite-Strife)。迁移基线见 `docs/migration/android-baseline.json`。
- 功能迁移状态与剩余项见 `docs/migration/parity-ledger.md`。
- `./tools/capture_android_baseline.ps1` 更新源文件哈希及本地开发变更。
- `./tools/export_android_content.ps1` 导出真实物品、配方、卡牌及羁绊；`-Check` 核对快照。
- `./tools/sync_android_art.ps1` 增量同步美术。
- `./tools/verify_migration.ps1` 等待实际 Godot 进程完成，检查退出码及错误日志，输出 `tools/build/migration-verification.json`。

旧阶段 A 的四项检查和“原创资源”边界为历史记录；当前范围以 2026-10-22 完整迁移计划和迁移账本为准。

## 阶段 A

- 原创占位菜单与战斗原型；
- 固定随机种子的一对一战斗日志；
- Windows 与 Android Debug 导出；
- 每个导入资源必须先登记来源、授权和替换状态。

当前复用的 Android 资源通过 `assets/android/manifest.json` 及 `assets/ASSET_REGISTER.md` 记录，后续逐步替换。

## 本地验证

当前验证使用 Godot 4.7.1 Stable。运行全量验证，可显式传入引擎路径，或设置 `GODOT_BIN`：

```powershell
./tools/verify_migration.ps1 -GodotPath '你的Godot控制台程序绝对路径'
```
