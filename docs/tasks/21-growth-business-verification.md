# 外围任务21：永久成长验收（测试任务）

只新增 `app/src/test/java/com/example/douqiuqiu/inventory/GrowthIntegrationTest.kt` 与 `docs/testing/results/21-growth-business.md`。不改生产代码、配置、现有测试、Gradle或日志。不操作真实玩家存档。

阅读shared/growth/GrowthContent、GrowthMaterialPlanner与app/inventory/GrowthRules、system/GrowthRequirementChecker。构造InventoryRules材料定义：essence1×1、backpack_fabric1×2、skill_shard1×1、safe_lock3×2、safe_frame3×3，均ItemKind.MATERIAL/maxStack1；另加用于验证无关物品保留的普通装备/卡。StoredItem默认放WAREHOUSE，实例ID不重复。

## 测试表

| ID | 输入/操作 | 预期 |
| --- | --- | --- |
| G01 | 300金币、仓库3精华、已撤离，购买hero:skywrath | 钱包0、恰消耗3实例、保留原两英雄并永久加入天怒 |
| G02 | 重复买同英雄 | 失败，金币/材料/解锁记录不变 |
| G03 | 同时缺金币、材料、撤离、通关资格 | status列全部适用缺口，不只第一项；purchase失败保持原快照 |
| G04 | 同名材料在仓库/安全箱/背包 | 只统计并消耗仓库；安全箱/背包不能当免费升级资源 |
| G05 | 足量绑定与未绑定同名材料混合 | 绑定优先、同绑定按ID，恰取成本份数；其他实例不变 |
| G06 | 未买skills:1，直接买skills:2 | 拒绝跳档，即使金币/材料/通关足够 |
| G07 | 按序购买4次技能栏 | 4→5→6→7→8；每次精确消耗；重买/第9栏不存在均拒绝 |
| G08 | 按序购买4次背包 | 4×2→4×3→5×3→6×3→6×4；已有物品ID/坐标/旋转状态保留 |
| G09 | 首通后600金币+1锁芯，买safe:1 | 2×2→3×2；只扣该锁芯与600金币 |
| G10 | safe:2需要5通关、背包至少4×3及前档安全箱 | 任一缺失都拒绝；全部满足扣1200+1框架，变3×3 |
| G11 | 四个英雄门槛 | 天怒首次撤离300+3精华；Viper1通关500+5；PA3通关700+7；NEC5通关900+9；逐个测试缺1通关/缺1金币/缺1精华失败 |
| G12 | READY/BATTLE/REWARD/REST等局内阶段购买 | 全部拒绝，不提前消耗资源 |
| G13 | purchase成功后死亡/放弃挑战 | 英雄、技能槽、背包及安全箱成长保留，不回退；新挑战重新按容量验证 |
| G14 | 解锁英雄后JourneyRules.enter | 新英雄可以进入；未解锁英雄仍拒绝；固定目标箱不重抽 |
| G15 | 锁芯3×2尝试入初始2×2安全箱、框架3×3入3×2安全箱 | 即使旋转仍拒绝；成长后正确容量才接受，绑定不等于死亡保护 |
| G16 | 重复物品实例ID等非法快照购买 | 验证失败，不生成半份扣资源结果 |

G07/G08/G11的具体成本从已批准表人工写入测试期望，不能用GrowthContent自身生成预期。技能栏成本250+3精华、500+5、900+8+2刻片、1500+12+4刻片；背包成本200+2织件、400+3、700+5、1000+8。首次扩展需撤离，后档通关门槛1/3/6。

命令：`./gradlew.bat --offline :app:testDebugUnitTest --tests '*GrowthIntegrationTest'`。只运行自己的过滤测试，报告实际数量与每ID结果。发现产品问题仅报告最小输入及路径，不修业务代码、不降低断言。测试源码和报告是本任务两个交付文件。
