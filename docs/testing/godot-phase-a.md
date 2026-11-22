# Godot 阶段 A 验收

## 目标

验证原创 Godot 原型可在 Windows 和 Android 以 Debug 配置导出，并且启动场景、资产来源登记、固定种子战斗和菜单到战斗导航均可复现。

## 本地命令

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\verify_phase_a.ps1
```

脚本会写入已忽略的 `tools/build/phase-a-verify.txt`，其中包含测试状态、Godot 版本和两个导出产物的 SHA-256。

## 当前边界

- 仅使用 Godot Control 节点和原创文本占位；
- 不包含 Android 工程中的美术、名称、数据或运行时代码；
- iOS 与网页导出均不在阶段 A 验收范围内。

## 执行记录（2026-10-21）

- 启动场景、资产登记、固定种子战斗与菜单导航共四项无窗口测试通过；
- `Windows Debug` 已导出为 `exports/windows/InfiniteStrife.exe`，并以无窗口模式启动验证；
- `Android Debug` 尚未生成：Godot 已识别 `D:\Android\Sdk`、JDK 21 与调试密钥库，但 Android 导出器仍返回未展开的预设配置错误。该项保留为阶段 A 的环境排查，不影响现有 Windows 原型或 Android 课程设计版本。
