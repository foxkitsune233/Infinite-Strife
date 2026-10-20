# 外围任务24：v6主题/保底存档验收（测试任务）

只新增 `app/src/test/java/com/example/douqiuqiu/inventory/ProfileV6MigrationTest.kt` 与 `docs/testing/results/24-profile-v6.md`。不改22的ProfileV5MigrationTest及报告；不写生产代码/Gradle/配置，不清玩家数据。使用TemporaryFolder。

当前写入格式v6：每个宝箱原字段后追加theme UTF、guaranteedCategories计数与值；根CampaignProgress原v5尾部后追加materialPity类别/misses/pending。读取旧v1～v5不读取这些新增字段，旧箱默认SUPPLIES、guaranteed空、pity空。

| ID | 操作 | 预期 |
| --- | --- | --- |
| V01 | v6保存混合六主题、箱子揭示/票价、pending材料计数 | 重建store读取完全等值，主题/保底来源/计数不丢失 |
| V02 | v6保存进行中挑战，target是工程主题且带生成保底来源 | 恢复同一target、战斗tick/RNG/状态；不重新生成或二次消费资格 |
| V03 | 独立构造真实v5旧schema，已有箱子/鉴定、商店、成长、次数 | 读取原数据全保留，旧箱默认综合、计数空；不重抽旧内容或按新主题改价 |
| V04 | v5进行中挑战，恢复并结算 | 原目标获物不变，重复结算拒绝；默认综合不凭空累计工程主题miss |
| V05 | v1～v4合法旧schema | 旧物资/进度仍保留，无重复赠品；再存成v6可读取 |
| V06 | 无效pity：未知类别、负miss、超过阈值、pending与阈值不一致 | 保存拒绝；原有效主档仍可读 |
| V07 | 无效箱元数据：错误主题对应guaranteed、缺强制材料、一个箱多个保底类别 | 保存拒绝，不留下半份消费资格 |
| V08 | v6主档损坏，旧版有效备份可用 | 恢复并报告备份；不静默重建账户；再次保存保留有效代际 |
| V09 | 读取后仅打开主页/存档/重新实例化store | 保底计数和已生成箱不变化，不能因重建页面再发保底 |
| V10 | 高于当前版本号、未知enum或坏CRC | 高版本明确拒绝；损坏按既有备份规则处理，不覆盖无法读取的原档 |

旧版夹具必须遵循旧schema和正确CRC，不能只改新文件版本号。22负责成长/货架与历史迁移，本任务专门核对新增v6字段，不与22竞争同文件。

命令：`./gradlew.bat --offline :app:testDebugUnitTest --tests '*ProfileV6MigrationTest'`。报告版本夹具构造方式、逐ID结果、实际数量与最小复现；不得删除失败用例或越界改业务。
