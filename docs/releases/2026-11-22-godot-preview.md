# Godot 迁移预览版候选说明

状态：源码候选已推送至codex/godot-migration，未创建GitHub Release、未生成本轮安装包；不使用Android 1.0稳定版称谓。

本批包括：两位初始英雄与八位任务英雄、旧档漏发补领与事务回滚；六生效/三备用装备隔离；十英雄魔晶与神杖接入；主要按键适配及成长页中文材料、字号和滚动修补。部分强化数值仍是Godot初始平衡值。

2026-11-22使用Godot 4.7.1经tools/verify_migration.ps1执行100项测试，失败0，逐项退出0且无SCRIPT ERROR/ERROR。本轮塔界面正常退出。结果在tools/build/migration-verification.json。此前间歇原生退出、音频退出警告仍需观察，不能宣称已根治。

## 正式发布前仍需完成

- Android真机的密度、刘海/挖孔、系统手势安全区、触控与完整流程验收。
- 匹配4.7.1的导出模板、最终包名/版本号、签名、安装更新、安装包及SHA256。
- 正式音源：当前战斗为程序合成临时音效，音乐/胜负短乐开关没有可播放源。
- 核对历史255a1b2其他未进入1.0主干的塔/转生/商店差异。恢复英雄任务不代表整个历史分支都已迁移。
- 推送位置已由用户确定：foxkitsune233/Infinite-Strife的codex/godot-migration分支，根目录为Godot工程；Android main保留不变。当前说明不代表已创建Release。

## 推送范围建议

保留源文件、测试、文档、data和assets；排除.godot、exports、tools/build、临时日志和签名凭据。assets约174.5MiB，最大单个文件约3MiB。素材遵循现有登记，不额外引入图片。当前没有绑定远程仓库/发行版本。

Windows导出依用户此前要求继续搁置。
