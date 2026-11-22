# Godot / Android 双项目继续开发计划

## 2026-11-22 发布准备（当前）

- [x] 核对本地仓库状态、远程目标与验证脚本。
- [x] 修正全量验证入口和README引擎说明。
- [x] 最终全量回归：2026-11-22共100项退出0，失败0；既有间歇退出未宣称根治。
- [x] 发布候选说明：docs/releases/2026-11-22-godot-preview.md，含真机/导出/仓库缺口。
- [x] 接入用户指定的新分支codex/godot-migration并推送源码；尚不创建Release或导出安装包。

以下Goal和阶段状态为历史，不代表当前仍有活动Goal。Windows导出仍按用户此前要求暂缓；本次先准备源码候选，不能宣称正式版已验收。

## 2026-11-20 Goal（取代下方早期状态）

Android 1.0源码为基线。优先剧情功能及既有文本导入；随后携币/材料保底、商店、卡牌与仓库、事件差异、成长/转生塔/永久奖励、设置与辅助页。Windows导出暂缓。用户已授权自主推进，不逐步确认。

- [x] 核对任务剧情代码空槽、文档正文与10条已实现结局。
- [x] 可重复导出序章、初见、24任务、塔里程碑、补充对话和10位英雄结局。
- [x] 剧情队列/页码/已读存档，领奖同事务，失败回滚和断点恢复。
- [x] 原营地背景/左右立绘/发言高亮/翻页/跳过；黑幕字幕渐显和末页确认。
- [x] 领奖、英雄查看、剧情回顾及恢复入口；真实塔登顶触发随塔批次接入。
- [ ] 测试与实际截图验证、更新差异表。

后续每一批均执行源码对照、实现、测试、更新差异表。塔尚未迁移，不能提前声称登顶触发完成。下方为历史记录。

## Goal

将 `D:\android_project\DouQiuQiu` 的完整玩家功能持续迁移到 `D:\GODOT\InfiniteStrife`，后续新开发以 Godot 为主；迁移目标跟随 Android 当前 0.1.7 及过渡期的新版本，先完成迁移设计与范围确认，再分批实施。

## Current Phase

Phase 1 — 文档、工作区与开发文件核对（complete）

## Phases

1. 文档、工作区与开发文件核对 — complete
2. 迁移架构与范围设计 — in_progress
3. 实施下一步 Godot 功能 — pending
4. 运行 Godot 头less测试、导出/静态验收 — pending

## Findings Summary

- Android 参考工程：`D:\android_project\DouQiuQiu`，当前基线为 0.1.6，最新文档仍要求后续以 Godot 迁移和覆盖补齐为主。
- Godot 工程：`D:\GODOT\InfiniteStrife`，竖屏 720×1280，Compatibility renderer，启动场景为 MainMenu。
- Godot P1–P6 已有实现：整备、9 装备位、8 技能卡位、3 套方案、网格物资、合成、商店、五节点远征、掉落、战斗基础与 JSON 存档。
- `docs/superpowers/plans/2026-10-21-android-parity-rebuild.md` 将 P7（存档和发布）标记为进行中；剩余为导出、设备验收和发布证据。
- `D:\GODOT\InfiniteStrife` 当前不是独立 Git 工作树；不可在其中执行独立提交。

## Next Step

确认是否需要将现有 Android 用户存档迁入 Godot；随后建立版本差异账本并提交首个迁移批次设计。

## Decisions

- 2026-10-22：迁移期间允许 Godot 暂时复用 Android 的全部现有美术资源；后续再评估和替换。资源登记仍需保留来源、用途及替换状态。
- 2026-10-22：Android 与 Godot 并行保留。Godot 完整追平 Android 后，经过存档、导出和设备验收再替代 Android；过渡期 Android 是功能和验收基线。
- 2026-10-22：对齐目标跟随 Android 当前 0.1.7 和过渡期后续更新，不再锁定 0.1.5/0.1.6。迁移设计需维护逐版本差异账本。

## Errors Encountered

| Error | Attempt | Resolution |
|---|---:|---|
| Godot 项目目录无 Git 元数据 | 1 | 记录为环境事实；后续不在该目录提交，先进行文件级开发检查 |
| Godot headless 测试进程 signal 11 | 1 | 4.6.1 Mono Console 与 4.7.1 Mono Console 均复现；尚未修复，需先确定运行时入口/兼容性 |
| 阶段 A 验证脚本硬编码不存在的 4.6.2 可执行文件 | 1 | 已确认本机实际存在 4.6.1/4.7.1 文件；待确认后纳入 P7 修复设计 |
