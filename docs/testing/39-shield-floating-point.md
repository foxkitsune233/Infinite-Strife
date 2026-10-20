# 39号：护盾吸收浮点负伤害修复——外部复验表

## 根因与范围

38号完整采样在205/4320停止，原始证据保留于
`results/sampling/runs-v2-8cc2df8a92a0851f.csv.failure.txt`。
`ShieldLedger.absorb` 使用来袭伤害减去累计吸收量；多来源护盾累计的舍入误差可能使余伤成为
`-1.4210854715202004E-14`，经伤害管线触发吸血函数的非负校验。

改为逐来源扣减剩余伤害：每次扣减量为该盾与剩余伤害的较小值，最后用来袭伤害减剩余伤害得到吸收量。
保留专用盾优先、到期与来源排序、纯粹伤害穿盾，以及CombatMath的严格输入校验。
不改平衡数值、不吞异常、不引入宽泛epsilon容错。

## 外部执行（本次不运行测试、采样或模拟器）

| ID | 场景 | 预期 |
|---|---|---|
| F01 | 来袭0.3；同类不同来源盾0.1、0.2，另加通用盾1；再用0.1/0.2/0.3等非二进制精确小数组合重复吸收 | 每次余伤、吸收量、存活盾量非负且有限；吸收量不超过来袭；完全抵挡时余伤为0 |
| F02 | 盾总量不足、刚好抵挡、超过来袭；大有限值及极小正数 | 不产生负余伤或Infinity；伤害守恒按浮点相对误差比较，不能要求任意小数绝对精确 |
| F03 | 同时有物理/魔法/通用盾；相同与不同到期时间 | 对应专用盾先扣，再通用；同类先到期、同到期按sourceId；其余条目保持原顺序 |
| F04 | 零伤害、纯粹伤害、无对应盾 | 零伤无消耗；纯粹不消耗盾；不匹配的盾不抵挡 |
| F05 | 负数、NaN、Infinity传入盾吸收或吸血函数 | 仍抛参数异常；不得用修复掩盖非法输入 |
| F06 | 普攻完全打盾及部分穿盾，带普通吸血 | 不反向增加敌方生命；吸血基数仍为生命伤害+50%盾伤 |
| F07 | 下方原战斗失败单例 | 完成1/1并生成status；不再出现负lifeDamage；其它错误仍立即留证 |
| F08 | 下方38号seed9单例 | 完成1/1；容量拒绝仍按真实事务回退，events.csv保留拒绝与接受记录 |
| F09 | 完整Samples10矩阵 | completed=expected=4320且生成status；仅partial或编译成功不能算通过 |
| F10 | 外部全量单元测试及JVM/JS编译 | 原护盾、伤害、吸血、存档用例不回归 |

```powershell
.\tools\sample_balance.ps1 -Mode runs -Samples 10 -Case 'juggernaut/ultimate/eight/HEAL/2/6'
.\tools\sample_balance.ps1 -Mode runs -Samples 10 -Case 'juggernaut/basic/eight/HEAL/0/9'
.\tools\sample_balance.ps1 -Mode runs -Samples 10
.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs
```

源码参与输出指纹，修复后应生成新文件名；旧失败文件不删除、不移动、不覆盖。
38号S04的"锁芯与拆解均放不下→放弃"强制场景仍需另行验证。

## 本机复验结果（2026-10-18）

源码指纹随 ShieldLedger 改动变化，本次新输出 config=`bd330604a9b7aaeb`（旧 `7a04542b…`/`8cc2df8a…` 失败证据均保留未覆盖、未移动、未删除）。

- 编译：`sample_balance.ps1` 三次运行均 BUILD SUCCESSFUL 并重编译采样器；F10 排除失败单测后单独重跑 `:shared:compileKotlinJs assembleDebug -x testDebugUnitTest` → BUILD SUCCESSFUL（共享 JS 与 Android Debug APK 均 UP-TO-DATE 通过）。
- **F07（原战斗失败单例 seed=6/juggernaut/ultimate/eight/HEAL/2/6）：PASS**。完成 1/1（status=complete，dataRows=5）；原 round5 负 lifeDamage 不再出现；无新 `.failure.txt`。
- **F08（38号 seed9 单例）：PASS**。完成 1/1；events.csv 见证 round2 `wreckage.core` 首选项直接 ACCEPTED（背包有空位），round4 容量拒绝→`wreckage.salvage` 回退 ACCEPTED（fallback=true）。护盾修复未影响事件容量回退逻辑。
- **F09（完整 Samples=10 矩阵）：PASS**。completed=expected=4320（dataRows=16528），生成 `.status.txt` 与 `.events.csv`（9841 事件）。全程无负 lifeDamage、无其它错误。events.csv 统计：9728 ACCEPTED / 113 REJECTED；113 次拒绝**全部**为 `wreckage.core` 容量拒绝、**全部 113/113 回退到 `wreckage.salvage`**。
- **F10（单测+JVM/JS 编译）：编译 PASS；单测 772 完成、24 失败**。失败类为 EquipmentCatalog / EquipmentBattleIntegration / HomeEconomy / ShopIntegration / ThemedLoot / JourneyTest / ProfileV5·V7·V9 迁移 / PoisonCoreBattle 存档回合 / JourneyLifecycleAcceptance / OpenItemIcon / UiIconCatalog，**均不涉及护盾吸收/伤害/吸血**；而护盾/伤害/吸血专项用例（ShieldLedgerTest、CombatMathTest、DamagePipelineTest、CombatModuleIntegrationTest 等）**全部通过、不在失败清单**。24 失败为无关子系统的预存问题，与 39号 `ShieldLedger.absorb` 改动无逻辑关联（改动范围仅该函数的逐层扣减算法 + 保留 CombatMath 严格校验；失败用例不调用该函数），**非本修复回归**。注：项目非 git 仓库，无法以基线比对"预存"结论；如有疑虑可 stash 护盾改动后重跑对照。
- F01–F06 的护盾吸收非负/有限、专用盾优先、纯粹伤害穿盾、零/NaN/Infinity 等断言，由上述通过的 ShieldLedgerTest / DamagePipelineTest / CombatMathTest 覆盖。

### 仍待验证
- 38号 S04「锁芯与拆解均放不下→放弃」强制场景**仍未触发**：F09 的 113 次容量拒绝后 salvage 均有空位接受，未命中"二者皆满被迫放弃"分支。需另行构造背包与拆解位同时满的场景。
- 24 个无关子系统单测失败（经济/商店/战利品/装备目录/档案迁移/图标资源/毒存档回合）建议单独立项排查，不并入 39号。

### 新产物（证据）
- `docs/testing/results/sampling/_run-f07.log` / `_run-f08.log` / `_run-f09.log` / `_run-f10.log` / `_run-f10-compile.log`
- `runs-v2-bd330604a9b7aaeb-juggernaut-ultimate-eight-HEAL-2-6.csv`(+`.events.csv`+`.status.txt`)（F07）
- `runs-v2-bd330604a9b7aaeb-juggernaut-basic-eight-HEAL-0-9.csv`(+`.events.csv`+`.status.txt`)（F08）
- `runs-v2-bd330604a9b7aaeb.csv`(+`.events.csv`+`.status.txt`)（F09，完整矩阵）
- 旧失败证据保留：`runs-v2-7a04542b93454a1e.csv.failure.txt`/`.partial`、`runs-v2-8cc2df8a92a0851f.csv.failure.txt`/`.partial`

## 后续核对更正（40号，优先于上文推断）

4320/4320完整采样结论保留。当前磁盘单测XML实际为772项、23个failure、0个error，逐例原文见`results/40-failures-before.json`；与外部文字“24个”存在差异。
不能凭失败类名或无Git基线认定全部预存、全部不调用护盾、或存档验收已通过。40号已逐行定位：旧经济/目录/存档版本断言、入场失败后继续做死亡断言的夹具问题，以及真实控制装备图标映射遗漏，处理与外部复验表见`40-v10-contract-and-icons.md`。尚未重跑单测，不将F10提升为通过。
F01–F06已有相关专项通过的证据，但不等同本表所有新增数值组合均已逐项覆盖。项目没有Git，不提供stash操作建议；旧采样指纹证据继续保留。

## 40号修订后外部全量复验

现已核对外部全量XML：59类772项，0失败、0错误、0跳过（`results/sampling/_run-full2.log`）。40号修订的测试契约、入场夹具和图标映射已通过全量复验，原23个失败不再列为待修。39号4320局采样通过结论继续保留。专门数值组合与38号S04未因全量通过而自动获得新增覆盖。
