# 安装包版本与修改索引

## 编号规则

- 测试阶段使用0开头的基础版本。**当前已发布为0.1.5。** 同一基础版本的小更新仅更换随机索引；基础版本按用户明确决定升级。
- 当前完整安装版本：`0.1.5-F300-7A82-8B2F`。后缀为三个独立随机16位数，以四位大写十六进制表示，共48位索引。
- 随机索引用于查表，不表示新旧顺序，也不是源码哈希。新旧由Android `versionCode`比较。
- 当前 `versionCode=1014`；之后每次对外发包必须递增，供Android覆盖安装排序，**内部构建号不代表基础版本升级**。相同源码重新编译不自动生成新索引。
- 唯一配置源为 `app/version.properties`。当前配置为0.1.5；后续发包才更新versionName、buildIndex及递增versionCode，登记后构建；新索引应检查本表不重复。
- APK文件名、Android系统显示版本、设置页版本须一致。同一次构建复制文件不能换索引冒充新版本；SHA256用于核对实际文件。
- 原误标0.1.4与0.1.5的修改统一归入0.1.3。旧二进制及原日志保留取证，表中明确原误标，不能仅改文件名伪造包内版本。新分发使用修正后的0.1.3包；不编造0.1.0～0.1.2发行记录。

0.1.4范围见[搜刮一键放入与数值查看计划](../superpowers/plans/2026-10-20-v0.1.4-update.md)。三个四位十六进制随机索引继续保留；后续计划不等于已发布记录。

## 修改索引表

| 发布日期 | 版本／构建号 | 修改索引 | 主要修改 | 安装包 | 验证与遗留 |
|---|---|---|---|---|---|
| 2026-10-21 | **0.1.5／1014（当前）** | **F300-7A82-8B2F** | 69：15张GPT重绘卡图接入；怒火及相关双流派去刀剑，以拳势、兽魂、热血和冷热核心区分；测试内容不变 | `dist/0.1.5/DouQiuQiu-0.1.5-F300-7A82-8B2F.apk` | assembleDebug成功、包内版本/v2签名核对、15张资源与源文件逐字节一致；SHA256 `E26C29DA1EFFBC227E7076072922266F51CBDCBD0C0B47330DC3E934DDE77A98`；按用户要求未运行测试，外部沿用原清单 |
| 2026-10-21 | 0.1.5／1013 | **43F5-2A26-0D22** | 68：六卡逐星收益、冰霜/毒伤隔离、无敌斩D2/D3、冰霜状态飘字、旧层兼容；重写66测试清单 | `dist/0.1.5/DouQiuQiu-0.1.5-43F5-2A26-0D22.apk` | 受影响专项102例通过、JS/APK构建通过、包内版本/v2签名核对；SHA256 `D1A81942D3EE30A9D2ED4D7EB02AD61E50FE09FFFB36B76D0186D5972DF56FDD`；设备待测、全量未重跑，新旧卡整体强度仍待平衡 |
| 2026-10-21 | 0.1.5／1012 | 29BD-3008-6B37 | 65/66：军团同帧追加攻击存档拒绝、D11内部冷却、U4优先于自动装备、首帧恢复与失效配装引用；附67卡牌审计 | `dist/0.1.5/DouQiuQiu-0.1.5-29BD-3008-6B37.apk` | 针对性35例通过、JS/APK构建通过、包内版本/v2签名核对；SHA256 `89636195114565A5499DCBDE88F549B6044B2D6921CAE902E5458FAC03699EA1`；设备待测、全量未重跑，审计待议数值未调整 |
| 2026-10-20 | 0.1.5／1011 | B5E7-3C9A-71F4 | 63/64：四名新英雄、怒火/寒霜卡池、16类事件、流派装备、六档挑战、版本日志与测试夹具；亚巴顿/主宰/天怒平衡调整 | `dist/0.1.5/DouQiuQiu-0.1.5-B5E7-3C9A-71F4.apk` | Debug 构建通过；SHA256 `C6F4E390…3E11E51`；63/64专项通过，完整回归仍有6项已知红项待后续处理 |
| 2026-10-20 | **0.1.4／1010（当前）** | **D63A-8FD4-53E8** | 56：一键放入已鉴定掉落、角色数值详情、55号紧凑整备与宝箱二级页收尾 | `dist/0.1.4/DouQiuQiu-0.1.4-D63A-8FD4-53E8.apk` | 强制构建、包内版本、v2签名与SHA256已核对；掉落鉴定和部分放入仍待专用夹具补验，不阻塞本次发布 |
| 2026-10-19 | **0.1.3／1009（当前）** | **C1BE-10FF-C10D** | 55：真实占格仓库/掉落、统一格子尺度、旋转同步；含本轮已写入锁定/快售批售/v13、商店/事件/鉴定、技能HUD、副手关键帧；进一步打磨留明天 | `dist/0.1.3/DouQiuQiu-0.1.3-C1BE-10FF-C10D.apk` | 功能操作与新单测待外部执行，见55号表；数值面板未制作 |
| 2026-10-19 | **0.1.3／1008** | **7AAB-25FE-0FC4** | 54：方块合成图鉴、可合成置顶与缺件暗遮罩、组件预览、仓库一键整理、出发检查内切换英雄与方案 | `dist/0.1.3/DouQiuQiu-0.1.3-7AAB-25FE-0FC4.apk` | 设备与行为测试见54号表，待外部执行 |
| 2026-10-19 | **0.1.3／1007** | **CFCC-44F7-E58C** | 53：英雄切换自动套用最近方案；每英雄三套长按保存方案；背包／安全箱稳定整理；装备不可升星、卡牌专属升星文案；v12存档 | `dist/0.1.3/DouQiuQiu-0.1.3-CFCC-44F7-E58C.apk` | APK版本与签名已核对；设备操作与新单测待外部执行 |
| 2026-10-19 | **0.1.3／1006** | **19A1-44B2-E23F** | 52：修正小更新编号；合并保留50节点掉落与51自动替换全部功能；基础版本保持0.1.3 | `dist/0.1.3/DouQiuQiu-0.1.3-19A1-44B2-E23F.apk` | 包内版本重新构建核对；旧功能运行验收状态不因改号而提升 |
| 2026-10-19 | **0.1.3归属／1005** | **CFD4-9A87-D795** | 51：占用装备／技能槽自动替换，槽间交换，旧物按来源回置；待拾取替换旧物回地面；失败原子回滚；单格“替换”提示 | 误标历史包：`dist/0.1.5/DouQiuQiu-0.1.5-CFD4-9A87-D795.apk` | 原包内误标0.1.5，仅归档，使用当前1006包；13项专项用例待外部执行；7995428E… |
| 2026-10-19 | **0.1.3归属／1004** | **2CD4-0A19-AFE1** | 50：取消三选一；敌人金币/物品掉落；事件多件全类型品质；手动拾取、放下取舍、不可跳过鉴定及事件开箱；入场携币；v11存档 | 误标历史包：`dist/0.1.4/DouQiuQiu-0.1.4-2CD4-0A19-AFE1.apk` | 原包内误标0.1.4，仅归档，使用当前1006包；旧测试契约待外部迁移；B4C293BC… |
| 2026-10-19 | **0.1.3／1003** | **2CD1-1DBC-D18D** | 统一测试版编号，设置显示可复制完整版本；继承48号拖放、47号紧凑仓库与二级预览、46号BGM | `dist/0.1.3/DouQiuQiu-0.1.3-2CD1-1DBC-D18D.apk` | 构建结果与校验值见下方交付记录；拖放设备验收待完成；仓库闪退缺对应日志，未声称修复 |
| 2026-10-19 | 历史1.0／1 | 未分配 | 48：250ms静止起拖、指针上方拖影、当前目标单格绿/红提示、拒绝原因 | `dist/v1.0-preview/DouQiuQiu-v1.0-preview-drag.apk` | 编译/签名通过，触摸测试待执行；0993C06E… |
| 2026-10-19 | 历史1.0／1 | 未分配 | 47：固定配装、密集仓库、8技能、并排背包安全箱、二级预览 | `dist/v1.0-preview/DouQiuQiu-v1.0-preview-inventory.apk` | 编译/签名通过；84B8DBE9… |
| 2026-10-19 | 历史1.0／1 | 未分配 | 46：循环BGM、独立开关、后台暂停与焦点处理 | `dist/v1.0-preview/DouQiuQiu-v1.0-preview-bgm.apk` | 编译/签名及音频解码检查；9F9E2011… |
| 2026-10-19 | 历史1.0／1 | 未分配 | 原始试玩闭环、主宰头像图标、六主动装备紧凑栏 | `dist/v1.0-preview/DouQiuQiu-v1.0-preview.apk` | 原历史包保留；22DB8629… |

## 0.1.3索引关联的详细修改

| 修改项 | 代码／配置 | 说明／外部测试 |
|---|---|---|
| 测试版本、构建号与随机索引 | `app/version.properties`、`app/build.gradle.kts` | 系统版本与包文件名一致；配置固定，不在每次Gradle执行时随机变化 |
| 设置页版本显示 | `ui/SettingsActivity.kt` | 从已安装PackageInfo读取，可长按复制，方便问题反馈 |
| 仓库拖放与单格反馈 | `InventoryDragGesture.kt`、`InventoryBoardView.kt`、`ItemDragShadow.kt` | `docs/testing/48-inventory-drag.md` |
| 紧凑整备与二级预览 | `InventoryActivity.kt`、`GameUi.kt` | `docs/testing/47-compact-inventory.md` |
| 循环背景音乐 | `GameMusic.kt`、`DouQiuQiuApplication.kt`、`assets/audio/bgm.ogg` | `docs/testing/46-looping-bgm.md` |
| 英雄方案与格子整理 | `Loadouts.kt`、`Inventory.kt`、`CampaignBinaryCodec.kt`、`InventoryActivity.kt` | `docs/testing/53-loadout-and-packing.md` |
| 方块合成与出发检查 | `CraftingPresentation.kt`、`CraftingSheet.kt`、`DepartureSheet.kt`、`InventoryActivity.kt`、`JourneyActivity.kt` | `docs/testing/54-crafting-departure.md`；仓库一键排序不移动其它容器 |

## 当前0.1.3／C1BE-10FF-C10D 交付校验

- 55号背包尺寸收尾；用户要求其余打磨明天继续，未制作完整数值面板。
- 强制编译52项全部实际执行，Debug APK、Release Kotlin、单测源码及共享JS均编译成功。未运行单测或设备验收。
- versionCode1009，155773011字节，aapt包内版本及apksigner验证成功；沿用原Debug证书。
- SHA256：`B177DF183DADD7475C20F844E4B3359AA3EFFDF6ED3CC1FD238F47FD43F8F9A1`，构建源与分发副本一致。
- 外部验收表55；日志`docs/testing/results/_build-55-usability.log`；明日清单`docs/tasks/2026-10-20-usability-followup.md`。

## 历史0.1.3／7AAB-25FE-0FC4 交付校验

- 强制构建52项全部实际执行，Android Debug APK、Release Kotlin、单测源码、共享JS均编译成功；没有执行单测或设备验收。
- 155126247字节；包内版本0.1.3-7AAB-25FE-0FC4，versionCode=1008；apksigner验证通过，沿用原Debug证书。
- SHA256：`FFFEE4042ABEC27E5D476EA0DE0C7A5BF88A14D1987EC5EE2D2737ADFA431ADF`。构建源与分发副本一致。
- 54号外部测试表及日志`_build-54-crafting-departure.log`已提供。

## 历史0.1.3／CFCC-44F7-E58C 交付校验

- 英雄切换在营地自动套用该英雄最近保存的方案；每名英雄可保存3套。方案引用物品实例，不复制装备、卡牌、星级或绑定信息。缺失物品时整个方案原子拒绝。
- 整理仅压紧背包／安全箱；整备阶段允许整理，战斗中锁定。装备说明明确“不可升星；通过配方合成”，卡牌说明明确三张同名同星卡升星。
- APK：155108839字节；SHA256 `ACF8EC1F96C37C235EB9AFBE13C03DA5FD9A942DE7FEF11BE9FBC675A89717BC`。aapt确认`versionName=0.1.3-CFCC-44F7-E58C`、`versionCode=1007`；apksigner确认同Debug证书。
- 构建日志：`docs/testing/results/_build-53-loadout-and-packing.log`，BUILD SUCCESSFUL in 26s，52 actionable tasks全部执行。仅完成编译；测试和设备操作见`docs/testing/53-loadout-and-packing.md`，待外部执行。

代码路径未写完整包前缀的，均在 `app/src/main/java/com/example/douqiuqiu/` 内；完整交付校验记录将追加在当前构建段。

## 0.1.3／2CD1-1DBC-D18D 交付校验

- APK：155071671字节；SHA256 `BD0C6C0885B7E4165FB7F444EA0032DDE777EF5418D4E3A200A59515732FFAA5`。
- aapt读取APK：`versionName=0.1.3-2CD1-1DBC-D18D`、`versionCode=1003`、包名`com.example.douqiuqiu`，与配置及文件名一致。
- apksigner verify通过，证书SHA256 `a6e94638435256e01a352a5cd80c825840c047036c09f4fdb8627b4efde446bf`，与历史包一致。
- 构建日志：`docs/testing/results/_build-49-version-index.log`，BUILD SUCCESSFUL in 26s，51 actionable tasks: 18 executed, 33 up-to-date。Debug/Release Kotlin、测试源码与APK编译通过；未执行单测或设备验收。
- 外部检查：同签名覆盖安装保留存档；系统应用信息显示完整版本；设置页复制版本正确；再次编译不更换随机索引。以上设备操作仍待执行。

## 0.1.3归属／2CD4-0A19-AFE1 原始交付校验（原误标0.1.4）

核心改动：`journey/NodeLoot.kt`、`JourneyRules.kt`、`JourneyEvents.kt`、`JourneyValidation.kt`、`inventory/Inventory.kt`；存档`CampaignBinaryCodec.kt`、`InventoryStore.kt`、`PlayerRepository.kt`；客户端`InventoryActivity.kt`、`JourneyActivity.kt`、`InventoryBoardView.kt`、`NodeLootHeaderView.kt`。shared路径根为 `shared/src/commonMain/kotlin/com/example/douqiuqiu/`，客户端为 `app/src/main/java/com/example/douqiuqiu/`。

- 固定节点掉落与picked/putDown/inspectionMs分离保存，原生物品已用数量放回后不恢复；旧REWARD节点在加载时转换一次。
- 战斗金币自动给付；事件物品收益附带同战次金币的一半；入场携币钱包转runGold，死亡损失，成功带出。
- 三选一已从界面删除，旧reward接口明确拒绝，旧测试必须按新契约迁移。目标箱仍通关直接入库。
- APK SHA256：`B4C293BCA131457E5C89BF57AB6CA7EBD82C5145941EB2143972A3914EC0D15B`，155091779字节。aapt核对版本/构建号，apksigner验证同Debug证书通过。
- 最终编译日志：`docs/testing/results/_build-50-final.log`，BUILD SUCCESSFUL in 11s，52 actionable tasks: 23 executed, 29 up-to-date。
- 采样器仅编译日志：`docs/testing/results/_compile-50-sampler.log`，Sampler compiled; simulation not executed。新策略LEAVE_LOOT/PICK_LOOT，旧4320局不代表新规则平衡。
- 外部测试与迁移：`docs/testing/50-node-loot.md`；新`NodeLootContractTest`13项只编译、未执行。没有本轮全量测试/设备通过结论。

## 0.1.3归属／CFD4-9A87-D795 原始交付校验（原误标0.1.5）

- 核心：`InventoryRules.move` 新增占用槽原子替换；`NodeLootRules.collect` 的旧物原子回地面，包含满池腾位边界；装备/技能之外格子保持原摆放规则。
- 客户端：目标单格显示“替换”，快捷装备保持填空栏。新13项`SlotReplacementTest`源码只编译；测试表`docs/testing/51-slot-replacement.md`。
- APK：155463973字节，SHA256 `7995428EFCCB06BB8CF5B3002DE7BD86B4A3E652A5E8ECCFE1BB9BC5330B2B54`；aapt版本/构建号正确，apksigner同证书验证通过。
- 最终构建：`docs/testing/results/_build-51-swap-final.log`，BUILD SUCCESSFUL in 8s，52 actionable tasks: 9 executed, 43 up-to-date。首次构建实际编译shared JVM/JS及Debug/Release，最终只对收尾修改增量编译。未执行单测或拖放设备验收。

## 当前0.1.3／19A1-44B2-E23F 编号修正交付

- 根据用户更正，小更新不改基础版本；将两批功能均归入0.1.3。新的编号修正使用新索引19A1-44B2-E23F与内部构建1006，不复用旧文件哈希。
- APK：155094299字节，SHA256 `D9C15E1EFFF365387BA218A1AF38BD1207FA9EEEED1507B423461B52495ECF4B`。aapt确认versionName=0.1.3-19A1-44B2-E23F、versionCode=1006；apksigner确认同Debug证书。
- 构建日志：`docs/testing/results/_build-52-version-correction.log`，BUILD SUCCESSFUL in 7s，51 actionable tasks: 12 executed, 39 up-to-date。只修正版本配置与文档，未更改功能规则，未执行功能测试。
