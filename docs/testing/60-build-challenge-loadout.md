# 60：羁绊收益、保存套装与困难分层外部验收

## 实现文件

- UI：`SynergyPresentation.kt`、`SynergySheet.kt`、`InventoryActivity.kt`、`DepartureSheet.kt`、`JourneyActivity.kt`。
- 规则：`ChallengeBalance.kt`、`EnemyBuilds.kt`、`JourneyRules.kt`、`EconomyBalance.kt`、`ChestFactory.kt`、`NodeLoot.kt`、`JourneyEvents.kt`。
- 存档读取条数：`CampaignBinaryCodec.kt`（英雄表由固定6改为正式目录长度，无布局变化）。
- 数值初稿和兼容边界：`docs/design/2026-10-20-build-challenge-v3.md`。

## 设备表（全部待外部执行）

| ID | 操作 | 验收要求 |
|---|---|---|
| UI01 | 技能页点击“羁绊效果” | 8个流派，分别列2/3/4档；未携带流派也能查看收益 |
| UI02 | 配置1/2/3/4种普攻卡 | 进度正确；依次显示攻速+10、攻击+12、每5次普攻追加50%伤害；已激活逐行高亮 |
| UI03 | 同卡不同星重复2张，再换双标签卡 | 重复不多计，双标签分别计入两流派；星级不改变档位收益 |
| UI04 | 查看毒/盾/恢复/寒霜说明 | 有跳数/间隔/实际增幅与治疗比例；不只显示“3档” |
| UI05 | 出发确认页打开羁绊，再返回 | 显示当前套装的实时收益；购票和携入资金未被重复提交 |
| UI06 | 配装“保存”→方案2→确认；改变装备后套用方案2 | 恢复9格装备/备用、技能卡及自动设置；没有复制物品 |
| UI07 | 技能页“保存套装”→已有方案→取消 | 取消不覆盖；重新确认才保存 |
| UI08 | 保存后退出重启，切换其他英雄再切回 | 对应英雄最近方案仍在；原有实例丢失时明确拒绝而不造新物品 |
| UI09 | 挑战中点击保存；模拟保存失败 | 挑战中不可保存局外套装；持久化失败不提示保存成功 |
| UI10 | 320dp、常规屏、字体1.3 | 保存/属性/三个方案可点；羁绊滚动区域不遮底部按钮 |

## 规则/存档表（全部待外部执行）

| ID | 输入/夹具 | 断言 |
|---|---|---|
| R01 | 难度0/1，round1..5，版本1/2/3，相同seed | 原倍率与预算/筛选顺序一致，无额外攻速/护甲倍率；不要把新增寒霜护甲漏算修复误判为难度倍率改动 |
| R02 | 难度2、v3、round1..5 | scale与设计表完全一致；HP/攻击递增，round5为2.4/1.8，攻速+32/护甲+4 |
| R03 | 难度2、v1/v2旧远征、round1..5 | 原倍率0.98/1.02/1.06/1.10/1.14，旧卡/装备预算保留 |
| R04 | v3困难敌人，多seed，每战统计 | 卡种不重复，星级/金卡数不超预算；装备不超过6个生效格，总价不超预算；控制装数量上限0/0/1/1/2 |
| R05 | 同一run重复preview100次 | EnemyBuild完全相同，不改变run.rng；不得按玩家现有装备反向加数值 |
| R06 | 新箱所有品质/主题/难度，多seed | 简单无主要成装及专属卷轴；普通不出现售价>=4500主要成装/卷轴；困难保留原品质和进度条件 |
| R07 | v3地面战斗/事件随机掉落，多seed | 同R06来源门槛；普通材料、保底材料与小件高价值战利品仍合法 |
| R08 | 手写旧箱/旧地面/旧已冻结事件奖励，保存再读 | 原物品、ID、鉴定进度不重算；明确奖励继续兑现；旧箱不因新池筛选被删除 |
| R09 | 10英雄解锁、10英雄各保存3套、各有选择索引，存档往返 | 不再因固定6条解码上限被拒绝；超正式目录条数仍拒绝 |
| R10 | 所有8流派3档，调用SynergyPresentation.schools | 24条均有可读具体效果，不含“效果说明待补充”；数据值取实时Catalog，不写死第二份数字 |
| R11 | F2/寒霜2档单独与组合，读取fighter.armor | 护甲实际增加，与说明一致，移除后恢复；已存在静态装备护甲不重复计算 |
| R12 | v1/v2/v3各round各difficulty的goldReward | v1保持旧表，v2/v3逐项相等；票价/费用/启动金币未改 |

## 建议执行顺序

1. 编译后按UI表验保存与说明。
2. 为R01～R12补专用断言；旧测试若固定“VERSION=2”或低难度必出成装，更新为新契约，保留旧v1/v2兼容用例。
3. 全量执行，不用过滤跑的XML代替全量：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat --offline --rerun-tasks testDebugUnitTest assembleDebug :shared:compileKotlinJs
```

4. 固定种子采样：先Samples=1冒烟，再Samples=10，对比同构筑简单/普通曲线和困难各战胜率、时长、敌人预算使用率、金币净值。脚本模式仍为boxes/runs，输出版本v3及源码指纹，保留旧CSV。

```powershell
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\tools\sample_balance.ps1 -Mode boxes -Samples 10
.\tools\sample_balance.ps1 -Mode runs -Samples 1
.\tools\sample_balance.ps1 -Mode runs -Samples 10
```

当前正式目录为10英雄/8流派，完整runs矩阵为10×8×2套装×2拾取策略×3难度×10种子=9600局；不要沿用旧4320局口径。异常留failure/partial与战斗日志，不跳过失败局。

## 主任务已执行

源码接入已完成；最终强制编译日志 `tools/build/build-construct-challenge-final.log`：Android生产/现有测试源码与shared JS均编译成功，20 actionable tasks全部实际执行。首次编译被并发新增的 `SchoolEquipmentContent` 驱散枚举名称错误阻塞，该文件修正后重编通过，原失败日志仍保留。未执行本表的单测、采样和设备用例；实际外部结果另填，不能以“编译通过”替代平衡结论。
# 后续规则覆盖说明

2026-10-20 用户追加要求：普通也加强、扩至六档、敌人主动装备与侦察遮蔽。涉及难度、掉落门槛及采样口径改按 `61-six-difficulties-intel.md`；本表配装保存、羁绊说明及十英雄存档验收保留。旧v3数据属于兼容复验，不是当前新入场规则。

