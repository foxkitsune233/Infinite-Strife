# 设置、备份与旋转预览验收

日期：2026-11-20。范围为本轮四项修复及相关界面，不代表完整迁移或正式版发布验收。

结论：目标功能回归通过，视觉验收未通过。未修改玩法或界面实现，只新增验收脚本和截图。

> 修补后复验：下方“不通过”保留为修补前记录。本轮三项已修补并通过实际渲染复验，截图位于 `after-fix/`。本结论仅覆盖720×1280逻辑布局与本轮场景，不替代Android真机验收。

## 修补后复验

- 战斗HUD采用HP/MP分行，英雄名称、盾量和技能文字允许换行，状态摘要限制两行。最大字号、多项长时Buff同时存在的边界测试通过，HUD位于屏幕内且不覆盖操作区。
- 图鉴使用KEEP_ASPECT_CENTERED，保留卡面与装备原始比例，实际截图不再横向拉伸。
- 备份导出/恢复/摘要、恢复默认设置按钮统一蓝金边框并增加点击高度；物品详情主体和关闭按钮同步主题。
- 新增tests/test_visual_acceptance.gd，修补前复现225条失败（含逐图标重复检查），修补后0失败。
- 相关test_battle_screen、test_tower_screen、test_settings_screen、test_settings_effects、test_settings_backup、test_item_detail、test_story_player与新增视觉回归均已有通过结果。
- 本轮塔场景批量测试一次打印通过后返回异常，单独复跑EXIT=0；原因仍未定位，不因重跑通过而判定为非代码问题。战斗场景测试仍有ObjectDB退出泄漏警告。
- 重新真实渲染9张截图，错误日志为空，已人工检查HUD、图鉴、旋转预览的修补后画面。

## 环境与方法

- Godot 4.7.1 Compatibility，Windows OpenGL / RTX 4060，720×1280启动参数，项目720×1280逻辑布局。
- 使用tools/acceptance_visual.gd实际渲染9张截图，程序化执行页签、预览按钮和设置操作；不是headless占位渲染，也不是人工鼠标全流程测试。
- 隔离验收存档，截图脚本结束时恢复原展示偏好，未修改主存档。
- 另运行test_settings_backup、test_settings_effects、test_settings_screen、test_item_detail，4项通过，无脚本错误。截图运行错误日志为空。

## 通过

- 设置三页签可以构建切换；备份导出/校验/恢复及内存同步回归通过。
- 仓库矩形物品预览按钮能切换尺寸与图片方向；断言原物品rotated和仓库位置未改变。
- 最大字号下，本次J07剧情对话与E02结局首屏可读，未见遮挡。
- 关闭动画后结局立即显示，战斗表现控制器关闭动画；关闭sound后音效生成帧数为0。
- 关闭battleLog时记录按钮不显示；关闭statusIcons后采用文字摘要。

## 不通过与整改优先级

1. **P1 最大字号战斗HUD越界**：battle-largest-no-effects.png中，双方HUD总宽度超过视口，左侧英雄名/血量前缀及右侧内容被裁切。应约束两个面板可用宽度、允许文本换行/分行，并覆盖最大字号的矩形边界测试。位置：scripts/ui/BattleScreen.gd的字号缩放、scenes/Battle.tscn的TopHud。
2. **P2 图鉴图像拉伸**：settings-gallery.png中，卡面和装备图标被拉成长条。SettingsScreen._build_gallery创建TextureRect但未设置保持比例的stretch_mode。应保留原比例并明确图标容器尺寸。
3. **P2 按钮与弹层样式未统一**：settings-backup.png的导出/恢复/摘要按钮、settings-largest.png的重置按钮仍为默认扁平深色；item-original/rotated.png的详情主体与关闭按钮仍为Godot默认灰色，和已有蓝金主题不一致。

## 限制

- 背景音乐和胜负短乐没有音源，不能验收为可播放；sound检查为生成门控，不替代实际听音验收。
- Windows没有移动端振动硬件，未验证真机触感。
- 未做Android实体设备、所有剧情长段落、全部图鉴项或完整远征流程的视觉验收。
- 测试通过不排除偶发退出崩溃；本轮截图与4项针对回归未出现退出崩溃。

## 截图

- settings-normal.png、settings-backup.png、settings-gallery.png、settings-largest.png
- item-original.png、item-rotated.png
- story-largest.png、ending-no-animation.png、battle-largest-no-effects.png
