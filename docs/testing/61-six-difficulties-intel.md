# 61 六档难度与敌方主动装备侦察——外部验收

状态：源码接入；Android生产/现有测试源码及共享JS编译检查。以下用例、胜率采样、模拟器未由主任务执行，不能标为PASS。上一轮60号中“仅困难强化/三档采样/4500成装进困难”被本表及设计文档替代，配装保存与羁绊说明验收仍沿用60号。

设计与精确数值：`docs/design/2026-10-20-six-difficulties.md`。

| ID | 操作/输入 | 预期 |
|---|---|---|
| D01 | 新档生成一批；通关10次后再生成 | 首批6箱覆盖0..5各一档；后期8箱仍覆盖全部六档；六个中文难度清楚显示 |
| D02 | 每档选同品质箱，看详情与出发检查 | 名称/档位/票价一致，白箱票价120/180/240/300/360/480 |
| D03 | 固定英雄和基础构筑，比较所有难度同一轮scale | 生命、攻击倍率严格递增；普通比旧普通增强；简单倍率不变 |
| D04 | rewardVersion=4，六档逐战preview，种子1..100 | 装备<=6，卡种<=8；无负预算，无重复实例；有效主动数>=设计表目标（正式完整目录） |
| D05 | 把preview中装备映射到EquipmentBattleState并调用activeItems | 被动数值件/碎颅被动/纯ON_CONTROLLED保护不冒充主动；共享覆盖后计数正确 |
| D06 | 带BKB/撒旦/紫怨/天堂/疯狂面具/冰川护甲等敌人开战 | 能按真实条件自动使用；CD、受控限制、免疫、驱散及毒伤规则与玩家一致，无“只有图标没效果” |
| D07 | READY截图、访问详情、再实际开战 | 展示的已识别装备确实在敌方六格内；详情效果与正式目录一致 |
| D08 | 六档同类构筑侦察 | 简单效果内联；普通名称可点；困难半数向上；精英最多1；大师仅用途；噩梦不暴露数量/用途/型号 |
| D09 | 重进READY、重启、反复打开装备详情 | 构筑和可见项稳定；rng、金币、局内状态不变；没有靠重开收集全部情报 |
| D10 | 检查大师/噩梦UI树、contentDescription、点击 | 隐藏装备名称/ID不存在于标签、无障碍描述与详情入口；无主动也不误显示“已确认无主动” |
| D11 | 320dp、常规屏、字体1.3；敌人带4主动 | 面板可滚动、不挤掉开战和整备按钮；返回详情不影响挑战 |
| D12 | 逐档生成大量箱/随机节点/事件 | 成装及卷轴遵从六档价格门槛；原品质/通关条件也生效；成长保底不被价格门槛拦截 |
| D13 | v1/v2/v3在途存档加载继续 | v1/v2原曲线；v3困难旧hardScales，普通仍旧曲线；v3随机掉落旧门槛；冻结物品、战斗不变 |
| D14 | v4六档 READY/BATTLE/LOOT/EVENT 存读 | 档位、规则版本、rng、实际构筑与冻结结果不变；-1/6难度拒绝；旧规则高三档在途拒绝 |
| D15 | 老箱批次升级后查看/新一批生成 | 老箱物品/票价不重抽，下一批出现全部六档；单箱刷新仍保留该箱难度 |
| D16 | 运行采样脚本仅CompileOnly | 采样器也编译，Case允许0..5，boxes/runs预期总数同步六档 |
| D17 | 固定种子小样本，先runs Samples=1再Samples=10 | 完整矩阵=英雄数×流派数×2配置×2策略×6难度×Samples；10英雄8流派时Samples10为19200局；boxes Samples10为7200箱；故障保留证据，不跳过失败 |
| D18 | 对比采样：各难度各战胜率、时长、控制占比、净金币 | 有总体难度梯度、普通后程不过分宽松、高档控制链不无限压制；单一随机种子不要求逐局胜率严格排序。记录失衡构筑，不凭单局调参 |

建议外部命令（JDK21，先编译后按成本决定采样）：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs
.\tools\sample_balance.ps1 -Mode runs -Samples 1 -CompileOnly
.\tools\sample_balance.ps1 -Mode runs -Samples 1
```

旧断言迁移注意：新默认rewardVersion=4，新首批6箱；测试旧三档行为须明确rewardVersion=1/2/3；仍想断言旧首次4箱的测试应改为六档产品要求。不得改低难度来掩盖真实战斗失败。物理存档schema版本未改，不要把规则v4误当成存档格式版本。

## 主任务编译记录

- 强制重编：`tools/build/build-six-difficulties-final.log`，Android生产/测试源码及shared JVM/JS编译成功，20/20任务实际执行。
- 随后补齐控制装备识别（含新增冰川等APPLY_CONTROL装备，避免绕过普通前3战限制），受影响源码重编：`tools/build/build-six-difficulties-control-audit.log`，BUILD SUCCESSFUL，5任务实际执行。
- 外部采样器仅编译：`tools/build/compile-six-difficulties-sampler.log`，`Sampler compiled; simulation not executed`。
- 没有执行单测/设备/胜率采样，没有生成或安装新APK，没有修改应用发布版本配置。
