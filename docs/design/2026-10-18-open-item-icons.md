# Game-icons.net物品图标接入

## 素材与许可

采用官方 `https://github.com/game-icons/icons`，归档实际commit为 `82d948812bfe3f269ef8f731dcdb07b08160edc4`（从ZIP comment核对），归档SHA256为 `b8024011acae3cd0c601b75c3c48479f1bb14f37f5fd5e7fb74839f88cb825fe`。

选择63个SVG，来自Lorc与Delapouite，按上游声明使用CC BY 3.0。原始SVG、选择表、归档和逐图哈希保存在 `art-source/icons/game-icons/`；这是开放授权美术素材，不是新增软件运行依赖。

官方依据：
- https://game-icons.net/faq.html
- https://github.com/game-icons/icons/blob/82d948812bfe3f269ef8f731dcdb07b08160edc4/license.txt
- https://creativecommons.org/licenses/by/3.0/

随APK打包 `assets/licenses/game-icons-credits.txt`、`game-icons-upstream-license.txt`、`game-icons-cc-by-3.0.txt`。设置→开源图标作者与许可可查看逐图来源、作者和全文。适配变化明确标为：移除黑色背景、SVG转换Android VectorDrawable、重新着色、卷轴产物小标组合。

## 实现

- 63个VectorDrawable XML约118KB（未压缩），48类基础图形映射及18个具体物品覆盖。不同装备可以共用基础轮廓与不同配色，不是每个物品都新增一幅独立插画。
- `tools/import_game_icons.py` 核对归档哈希、实际版本及SVG语法，移除明确的全画布黑色路径；不执行下载仓库中的脚本。遇到不支持的变换/描边/填色等必须停止，而不是默默损坏图形。
- `OpenIconResources.kt` 是生成的显式映射，`OpenItemIcons`负责着色和卷轴复合，`ItemArtwork`为统一入口。
- 已覆盖仓库/整备格子、商店及合成列表、详情弹窗、完全揭示的宝箱物品、鉴定完成物品、图鉴和战斗六装备按钮。隐藏信息阶段不绘制具体图标。
- 卡牌仍用30张现有卡面；19类状态图标仍用原生图形。原GameIconDrawable保留作状态与缺失资源回退。
- 当前素材不是Dota 2官方装备原图，图形语义对应本作物品；同名装备的唯一组、属性、价格、占格和配方均未因此改变。

## 预览与重建

`art-source/icons/game-icons/preview.png` 是基于已转换路径生成的样式总览，轮换配色方便检查；它不是Android页面截图，也不代表每个物品的实际最终配色。

```powershell
python tools/import_game_icons.py
node tools/preview_game_icons.cjs
```

导入依赖Python标准库；预览需要Node sharp。SVG原件保留，可供以后网页端使用。运行时离线，不访问图标网站。

## 当前剩余工作（核对至本次接入）

1. 最新29号测试单记录：战斗约7秒停滞仍待复现定位；不能用旧的自动测试全绿代替这个问题的实机结论。
2. 推推棒已有受控检查、保护与长戟攻速逻辑，当前缺少明确的推开表现事件/动画；正常敌方构筑中的触发路径还需复核，不能笼统说保护系统完全没做。
3. 医棚交物范围需与测试单复核。当前medicines集合明确包含药膏、吃树、仙灵之火、奶酪，奶酪被接受是代码行为；是否属于规则缺陷尚不能仅凭报告确认。
4. 鉴定等待与恢复、容量分级、攻击反馈、六英雄图集均已接入；其强杀/磁盘不足/坏收据、小屏和大字、武器透视及大招姿态切换仍有人工验收缺口。鉴定收据与游戏存档的两次提交仍有崩溃窗口，后续应合并事务。
5. 音频播放、完整循环BGM/角色音效未接入；CC0备用声音已下载。当前场景和开箱演出是原生矢量首版，继续精绘和动作细化属于表现打磨。
6. 宝箱扩大后的收益、鉴定涨价、救济与成长速度联动仍需平衡样本。网页版界面、召唤/幻象、更多英雄流派为后续扩展。

以上区分“未做”“已接但待验收”“测试报告待复核”，不把已经落地的宝箱/图集重复记作未实现。
