# Findings — Godot / Android 项目核对

## 2026-10-22

### Android 参考工程

- 路径：`D:\android_project\DouQiuQiu`
- 作用：行为、数据规模和验收语义参考，不应把其运行时代码直接复制到 Godot。
- 最新提交显示 Android 0.1.6 已包含任务面板、英雄等级、ANR 修复和营地直达等内容。
- 迁移基线要求 Godot 保留 Android 0.1.5 的核心流程，但使用原创名称、美术、音频和数据。

### Godot 工程

- 路径：`D:\GODOT\InfiniteStrife`
- `project.godot`：竖屏 720×1280，Compatibility renderer，主场景 `res://scenes/MainMenu.tscn`，自动加载 `AppSession`。
- `AppSession.gd` 已连接整备状态、远征状态、战斗开始/结束和存档读写。
- `PreparationScreen.gd` 已提供配装/技能/物资分区、英雄切换、3 套方案、仓库与背包交互。
- `test_preparation_screen.gd` 已覆盖 9 个装备槽、8 个卡槽、3 个方案和 UI 基础交互。
- `test_packing_rules.gd` 已覆盖占位冲突、旋转、锁定、快速出售、批量出售和整理。
- `docs/superpowers/specs/2026-10-21-android-parity-design.md` 把 P1–P7 定义为 Android 对齐路线。
- `docs/superpowers/plans/2026-10-21-android-parity-rebuild.md` 标记 P1–P6 为完成，P7 进行中。

### 初步判断

当前最直接的下一步不是再扩展内容，而是完成 P7 的发布/恢复验收闭环：修正验证脚本的 Godot 可执行文件入口，补齐存档往返和启动恢复测试，再决定是否实施导出修复或真实设备验收。

### 验证基线异常

- `export_presets.cfg` 存在 Windows Debug 和 Android Debug 预设。
- `tools/verify_phase_a.ps1` 存在，但硬编码路径 `Godot_v4.6.2-stable_win64.exe` 不存在；本机可见 4.6.1 Mono 和 4.7.1 Mono 可执行文件。
- 使用 4.6.1 Mono Console 和 4.7.1 Mono Console 执行最小 `test_project_boot.gd` 均触发 signal 11；因此目前不能把测试失败归因于业务代码。
- 尚未修改 Godot 运行时代码或 Android 运行时代码。

### 迁移范围决策

- 用户确认：Godot 迁移期间可暂时复用 Android 的全部美术资源，后续再考虑替换。
- 该决定取代当前 Godot 文档“只使用原创资源”的实施边界；公开分发许可和来源记录仍应独立保留，不能由临时复用自动推定。
- 用户确认：Android 与 Godot 并行保留，直到 Godot 完整追平 Android 后再替代 Android。
- 用户确认：追平目标是 Android 当前正在更新的 0.1.7；过渡期内继续跟随 Android 新进度。


## 2026-11-20 剧情与经济核对

- QuestStory.kt仍是24空槽；现有正文在Android docs/design/2026-11-04-endless-strife-story-script.md。用户本次明确授权将已写文本导入。HeroEndings.kt含10×15页正文，按代码精确解码导出。
- 新增导出器带源SHA256、--check。41段/131句；回顾按任务领奖与英雄可用解锁，塔剧情按正式里程碑解锁。
- Godot原启动页未恢复营地存档；已修正启动恢复，同时保护损坏存档不被新游戏覆盖。首存失败序章保留内存并在翻页时重试。
- 结算原先先改状态后忽略保存失败；已加入begin/complete/leave/extract事务checkpoint，原地恢复状态对象避免UI保留旧引用。
- 不同远征drop_serial从0重复，旧ID没有远征标识，可能覆盖仓库同类掉落；加入journey.seed，已有存档实例ID不修改。
- 材料保底阈值backpack/skills/safe=4/4/6，原版只对合格主题通关未得累计；本趟新材料实际带出可清零（包括死亡时安全箱）。旧活跃存档无入场物品基线时保守不给途中已拾取材料清零信用，不能猜测携入来源。
