# 0.1.7 设计稿：开放英雄等级上限

> 状态：脚手架已就位，实现待「新上限档位」决策后启动。

## 1. 定位与目标

- 0.1.6 把英雄等级锁在 `LEVEL_CAP = 10`（设计上限 `MAX_LEVEL_DESIGN = 100`）。0.1.7 提升这把锁，让「永久战斗属性」养成继续向上走。
- 延续 0.1.6 的养成框架：不加经验条、不引入新货币、只花营地金币（`HeroLevelRules.upgrade`）。
- 这是 0.1.7 的确认锚点（路线图原文「当前版本锁 10 级（下一版本开放）」）。

## 2. 关键决策（待维护者拍板）

新上限档位。曲线在 `HeroLevel.kt` 里已是按 `level` 的线性公式，改 `LEVEL_CAP` 一个常量即可延续，但档位直接决定永久属性强度，需要平衡定稿。

| 方案 | 新 `LEVEL_CAP` | 满级永久加成（攻/生/护甲/魔抗） | 说明 |
| --- | --- | --- | --- |
| A 全开到设计上限 | 100 | +168.5% / +218% / +39.6 / +54.5% | 与设计上限一致，但永久属性过强，可能击穿远征平衡；不推荐直接 |
| B 分档·中（推荐） | 30 | +48.5% / +58% / +11.6 / +19.5% | 养成纵深足够，不击穿平衡 |
| C 分档·小（保守） | 20 | +33.5% / +38% / +7.6 / +14.5% | 给后续再抬空间 |

数值按现有曲线推算：`atk=1+0.015*(L-1)+0.01*(L/5)`；`hp=1+0.020*(L-1)+0.01*(L/5)`；`armor=0.4*(L-1)`；`mr=0.5*(L-1)+1.0*(L/5)`。

## 3. 改动清单（精确到位置）

- `shared/hero/HeroLevel.kt`
  - `LEVEL_CAP` `10` → 新值（**唯一生产改动**）。
  - 更新文件头注释：把「当前版本锁 10 级……下一版本提升锁时只改这一个常量」改为反映已开放，并注明本版本锁到哪。
  - 升级拒绝文案 `英雄等级已达当前版本上限（$LEVEL_CAP 级）` 自动跟随常量。
  - 曲线函数（`upgradeCost` / `attackMultiplier` / `healthMultiplier` / `armorBonus` / `magicResistBonus`）**无需改**——已是按 `level` 的线性公式，`>10` 自然延续。
- `app/.../ui/InventoryActivity.kt` 的 `heroLevels()`：标题「上限 ${LEVEL_CAP} 级」与「等级 $level / ${LEVEL_CAP}」引用同一常量，自动跟随；仅回归确认显示正确，无需改代码。

## 4. 任务拆解 / 里程碑

- **M1** 改 `LEVEL_CAP` 常量 + 文件头注释（分钟级）。
- **M2** 测试对齐（见 §5）。
- **M3** 单元 + 设备回归：`HeroLevelTest`、`InventoryStoreTest`、`CombatModuleIntegrationTest` 等受等级换算影响的用例全绿；`SingleRun` 红项维持为平衡健康度信号。
- **M4** 版本日志（`VersionLog.kt` 补 0.1.7 条目）+ 打包（分配真实 `buildIndex`，`versionCode 1018` 已递增）。

## 5. 测试对齐（受 §2 决策影响）

- `app/src/test/.../hero/HeroLevelTest.kt`
  - `levelCapTenBlocksFurtherUpgrade`：断言的 `10` 改为新 `LEVEL_CAP`（或重命名为 `levelCapBlocksFurtherUpgrade`）。
  - 现有 Lv10 乘数断言（`1.155 / 1.20 / 3.6 / 6.5`）在档位 ≥ 10 时不变；选 B/C 需补 Lv20/Lv30 断言，选 A 补 Lv100 断言。
  - 升级费用用例（`upgradeCost(1)=100` … 累计 `4500`）与封顶无关，保持不变。
- 其它受等级换算影响的用例随曲线自然延续，跑全量确认。

## 6. 风险与平衡

- **永久属性过强**：按 §2 表核对满级加成，建议 B/C 分档，避免远征被高等级英雄击穿。
- **旧档兼容**：`heroLevels` 是 `Map<heroId,Int>`，纯整数，无 schema 变更，旧档零迁移。
- **回放可复现**：等级只在战斗初始化写入 `Fighter`，战斗内不读等级；改 `LEVEL_CAP` 不影响 `DuelState` 回放。

## 7. Godot 共享

规则在 `shared/hero/HeroLevel.kt`（`commonMain`），Godot 阶段 B–C 直接继承常量与曲线，无需从 Android UI 反推。

## 8. 当前状态

| 项 | 状态 |
| --- | --- |
| 版本号 / 路线图脚手架 | ✅ 已就位（version.properties `0.1.7` / `versionCode 1018`；ROADMAP 0.1.6 标记已发布、补 0.1.7 行） |
| 新上限档位决策 | ❌ 待维护者定稿 |
| M1–M4 实现 | ❌ 未开始（等决策） |
