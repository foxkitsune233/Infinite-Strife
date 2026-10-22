# 0.1.7 M4 英雄强化交接清单（魔晶/神杖/圣剑/转生道具）

> 状态：框架待建，逐英雄接线为**重复性工作，交维护者执行**（2026-10-22 约定）。
> 上游：设计稿 `2026-10-22-017-open-hero-level-cap.md` §2.5；已落地：待授予池 + 「转生授予」面板（提交 `f5c2398`）。
> 本文所有文件路径、行号、函数名均已对照当前代码核实；效果原文照抄用户给定，未改写。

## 0. 分工与总览

| 部分 | 性质 | 负责 |
| --- | --- | --- |
| M4 框架（Fighter 字段 + 编解码 v18 fighter 分支 + 神杖主动技状态机 + BattleActivity 按钮 + 转生道具选星 UI） | 一次性框架 | **WorkBuddy**（你说一声就建） |
| M4a 魔晶逐英雄效果（10 项） | 重复接线 | **你** |
| M4b 神杖逐英雄效果（10 项） | 重复接线 | **你** |
| M4c 圣剑数值（「大量」具体值） | 数值决策 | **你** |
| M4d 转生道具「使用」交互（选一张技能卡星级直满） | 一次性 UI + 1 条 consume 规则 | 框架我做，消耗规则可你写 |

验收总标准：每个英雄接线后跑 `.\gradlew.bat :app:testDebugUnitTest --tests "<你的测试类>" --rerun-tasks --offline`，全绿后统一全量回归（当前基线 **1047 例 / 仅 SingleRun 保留红**，新增测试应全绿）。

## 1. 通用接入模板（每个英雄都按这 5 步，照抄改内容即可）

已核实的接入点：

| 接入点 | 路径 | 说明 |
| --- | --- | --- |
| 大招/技能分派 | `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/DuelEngine.kt:349` `when (hero.id) { ... }` | 每个英雄的 ult 逻辑都在这个 when 里 |
| 被动/触发分支 | 同文件 `c.heroId == "xxx"` 判断处（如 viper 毒 223 行、pa 暴击 252/263 行、axe 连击 232 行、legion 双持 302 行） | 魔晶被动插在对应分支旁边 |
| 控制门控 | `shared/src/commonMain/kotlin/com/example/douqiuqiu/status/ActionPermission.kt`（`permission(f, action)`，已有 `allowStunnedHeroSkill`） | 神杖「被控禁放」复用此门控 |
| 驱散/治疗等工具 | `shared/src/commonMain/kotlin/com/example/douqiuqiu/status/FighterEffects.kt`（`strongDispel` / `heal` 等） | 效果实现优先复用既有函数 |
| 英雄配置取值 | `HeroContent.legacy(id)` / `HeroContent.skill(id)` | 新数值放这里，别硬编码在引擎 |
| 战斗初始化 | `shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyRules.kt` 的 `fighter(state, heroId)` | shard/aghs 标记在此写入 Fighter |
| 存档序列化 | `app/src/main/java/com/example/douqiuqiu/inventory/CampaignBinaryCodec.kt` 的 `fighter(input, version)` | 新 Fighter 字段必须加 `if (version >= 18)` 分支，旧档回退默认值 |
| 授予状态 | `progress.towerShard` / `progress.towerAghs`（`Set<String>`，英雄 id） | 已由「转生授予」面板写入，直接读 |

**5 步模板**（以第一个英雄为例，其余照抄）：

```kotlin
// ① shared/journey/JourneyModels.kt — Fighter data class 加字段（框架统一加一次）
val shard: Boolean = false,      // 魔晶已授予且本场生效
val aghsUsed: Boolean = false,   // 神杖本场已用（每场一次）

// ② shared/journey/JourneyRules.kt — fighter() 初始化时绑定授予状态
.copy(shard = state.progress.towerShard.contains(heroId))

// ③ app/.../inventory/CampaignBinaryCodec.kt — fighter() 读写加 v18 分支（框架统一加一次）
val shard = if (version >= 18) input.readBoolean() else false   // read 侧
out.writeBoolean(f.shard)                                        // write 侧

// ④ DuelEngine.kt — 在对应英雄分支写效果（每个英雄改这一处）
// 示例：剑圣魔晶「暴击获得40%的额外吸血加成」
if (critical && hit.lifeDamage > 0 && c.heroId == "juggernaut" && c.shard) {
    heal(i, hit.lifeDamage * 0.40)
}

// ⑤ app/src/test/java/com/example/douqiuqiu/combat/ — 每个英雄至少 1 个用例：
//    授予前无效 / 授予后生效 / （神杖）每场一次 / 被控禁放
```

## 2. M4a 魔晶接线清单（10 项，效果原文照抄）

统一前置：`c.shard == true` 才生效（shard 由 `towerShard` 授予驱动）。

| # | 英雄（id） | 效果原文 | 建议接入点（DuelEngine.kt） | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 剑圣 `juggernaut` | 暴击获得40%的额外吸血加成 | 暴击命中分支（252/263 行附近，`multiplier > 1` 且 `hit.lifeDamage > 0` 时 `heal(i, hit.lifeDamage * 0.40)`） | 与天赋/卡牌吸血叠加方式需定（相加 or 独立乘区） |
| 2 | 天怒 `skywrath` | 大招改为不需要蓝量释放，持续的时间改为持续指数扣除蓝量直到扣完结束大招 | 大招分派 351 行分支 + `ActionPermission.permission` 的蓝量判断 | 「指数扣除」基数/频率需定（建议每秒扣 `剩余蓝 × 15%`） |
| 3 | 幻影刺客 `phantom_assassin` | 自身获得30%闪避 | 命中判定处加闪避豁免（需 Fighter 新增 `evasion` 或在该分支 `roll()` 抵消） | 注意与既有闪避来源叠加口径 |
| 4 | 瘟疫法师 `necrophos` | 获得被动的生命值扣除提供50%的治疗量 | 139 行 necrophos 被动扣血处，造成该伤害时 `heal(i, dmg * 0.50)` | 口径已定：按**造成伤害回血**（非自身承伤） |
| 5 | 亚巴顿 `abaddon` | 平a获得冰冻伤害，可以触发冰冻特效 | 普攻伤害分支（321 行 abaddon 分支附近）附加冰冻伤害 + 现有冰冻/减速特效入口 | 需确认「冰冻特效」指现有哪个 status |
| 6 | 冥界亚龙 `viper` | 毒会额外降低护甲 | 223 行 viper 毒分支，毒命中时给目标叠加破甲 debuff | 破甲数值/时长需定 |
| 7 | 敌法师 `anti_mage` | 魔抗会提高，且第一次受到的技能会返还回去 | 魔抗：`JourneyRules.fighter()` 初始化 +X%；技能返还：受伤分支加首次反弹标记 | 「第一次受到的技能」范围需定（建议仅敌方大招） |
| 8 | 虚空假面 `faceless_void` | 每隔一段时间释放一次时间膨胀，提高敌人的cd，持续造成伤害 | 290 行 faceless_void 分支扩展周期触发 | 周期/数值需定（建议每 6 秒） |
| 9 | 斧王 `axe` | 基于敌人战斗饥渴状态，提高敌人的易伤持续扣血 | 232 行 axe 分支；「战斗饥渴」游戏内暂无对应机制 | **需你定**：新增 debuff 还是复用现有 slow/broken |
| 10 | 军团指挥官 `legion_commander` | 每隔一段时间获得一次驱散 | 302 行 legion 分支加周期 `FighterEffects.strongDispel(f[i])` | 周期需定（建议每 8 秒） |

## 3. M4b 神杖接线清单（10 项，效果原文照抄）

统一前置：`c.aghsReady`（本场未用）且 `FighterEffects.permission(f, CombatAction.HERO_SKILL)` 通过（被控禁放）；释放后置 `aghsUsed = true`。全局规则：**一场战斗仅一次、无 CD、无蓝量消耗**。

| # | 英雄（id） | 效果原文 | 实现要点 |
| --- | --- | --- | --- |
| 1 | 剑圣 `juggernaut` | 释放一次只有一秒的无敌斩 | `invulnerable = 1.0` + 多段高伤（复用 350 行 omnislash 思路） |
| 2 | 天怒 `skywrath` | 沉默敌人两秒 | 目标 `control.copy(silenced = true)` 持续 2.0 |
| 3 | 亚巴顿 `abaddon` | 血量低于0之后的接下来一秒内收到的伤害改为回复，一秒后回复 | 新增 1 秒「死亡延迟」窗：HP<0 不立即判负，受击转治疗，1 秒后结算（扩展现有 safeguard 思路） |
| 4 | 幻影刺客 `phantom_assassin` | 进行一次5连击且必定暴击，但是接下来5秒内攻击不能触发暴击 | 5 段必暴连击 + 5 秒「暴击封锁」标记（普攻暴击源禁用） |
| 5 | 冥界亚龙 `viper` | 接下来5秒获得伤害减免和魔力强化 | 5 秒减伤 buff + 魔力强化（口径待定：攻/法强 or mana 增益） |
| 6 | 瘟疫法师 `necrophos` | 释放脉冲，制造伤害回复血量 | 立即脉冲伤害（按自身属性换算）+ 等量治疗 |
| 7 | 敌法师 `anti_mage` | 接下来0.5秒内收到的技能将会反弹回去 | 0.5 秒 `reflectSkills` 窗，敌方大招/技能卡反射回施法者 |
| 8 | 虚空假面 `faceless_void` | 回到2秒前的血量并强驱散 | 每 tick 快照自身 hp，释放回滚到 2 秒前快照 + `strongDispel` |
| 9 | 斧王 `axe` | 造成400点伤害 | 400 固定伤害（建议真实伤害，穿透护甲/魔抗——待确认） |
| 10 | 军团指挥官 `legion_commander` | 造成150点伤害并获得攻速加成3秒 | 150 伤害 + 3 秒 `speed`/攻速增益（复用既有 speedBuff 字段） |

## 4. M4c 圣剑（纪念品装备）——数值待你定

定位已定（`f5c2398` 前已落稿）：通关第 20 层发放、永久保留不回收、提高**大量攻击力/法强/攻速**。实现二选一：

- **A（推荐，简单）**：`progress.towerRapier == true` 时在 `JourneyRules.fighter()` 直接加成人间属性（attack/magicPower/speed 各 +X%），全模式生效；
- **B（重）**：做成真装备定义进 `EquipmentCatalog`，可穿戴/掉落——与「纪念品」定位矛盾，不建议。

需要你给三个数：攻击 +?% / 法强 +?% / 攻速 +?%（参考：一件高级装备约 +30~60% 单项，「大量」建议 80~150% 量级）。

## 5. M4d 转生道具使用（框架我做，规则可你review）

- 存档已有 `pendingRebirthItems: List<String>`（第 11–20 层每层一个，「转生授予」面板已展示计数）。
- 使用交互：营地选一张技能卡 → 星级直满（3 星）→ 消耗 1 个道具。建议挂在技能卡详情弹层（`InventoryActivity` 技能卡操作区）。
- consume 规则：`pendingRebirthItems = pendingRebirthItems.drop(1)`，目标卡 `star = 3`；绑定卡不可用、已在 3 星的卡不可用。

## 6. 待你拍板的决策（不阻塞框架，阻塞对应英雄接线）

| # | 决策 | 影响项 | 建议 |
| --- | --- | --- | --- |
| 1 | 魔晶/神杖作用范围：全模式 vs 仅塔内 | 全部 20 项 | 全模式（设计稿 §6 #7 默认建议） |
| 2 | 圣剑三项数值 | M4c | 攻/法强/攻速各 +100% 起步试平衡 |
| 3 | 斧王「战斗饥渴」：新增 debuff or 复用 | 魔晶 #9 | 复用现有易伤/持续扣血机制 |
| 4 | 敌法魔晶「第一次受到的技能」范围 | 魔晶 #7 | 仅敌方大招 |
| 5 | 天怒魔晶「指数扣蓝」基数 | 魔晶 #2 | 每秒扣剩余蓝 15% |
| 6 | 斧王/军团神杖伤害是否穿透护甲魔抗 | 神杖 #9/#10 | 真实伤害 |
| 7 | 虚空/军团周期触发间隔 | 魔晶 #8/#10 | 6 秒 / 8 秒 |

## 7. 每项接线后的自检命令（可直接复制）

```powershell
# 单类测试（把你的测试类名换进去）
Set-Location "D:\android_project\DouQiuQiu"; & .\gradlew.bat :app:testDebugUnitTest --tests "com.example.douqiuqiu.combat.你的测试类" --rerun-tasks --offline

# 全量回归（基线：1047 例，仅 SingleRunIntegrationTest.kt:57 允许红）
Set-Location "D:\android_project\DouQiuQiu"; & .\gradlew.bat :app:testDebugUnitTest --rerun-tasks --offline

# 提交（本机 Bash 坏，用 PowerShell 内联 git；提交信息写 _commitmsg.txt）
$g = "C:\Users\HP\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe"; Set-Location "D:\android_project\DouQiuQiu"
& $g add <文件>; $t = (& $g write-tree); $h = (& $g rev-parse HEAD)
$c = (& $g commit-tree $t -p $h -F "_commitmsg.txt"); Set-Content .git/refs/heads/main $c -NoNewline -Encoding ascii

# 推送远程（你来执行）
Set-Location "D:\android_project\DouQiuQiu"; & "C:\Users\HP\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe" push origin main
```

## 8. 当前仓库状态

- HEAD = `f5c2398`（M3e 待授予池 + 转生授予面板），工作区干净，**未 push**。
- 0.1.7 已落地：M1 指数费用 / M2 动态上限 / M3 爬塔逻辑 + 存档 v18 / M3e 授予入口。
- 未落地：M3 UI（TowerActivity + 营地爬塔入口）、M4 全部、M5 版本日志+打包、M6 设备验收。
