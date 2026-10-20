# 外围任务22：v5存档与升级验收（测试任务）

更新说明：当前主任务新增主题与保底后写入版本已为v6。本任务仍负责原v5货架/成长业务字段和v1～v4兼容，不把“头部必须为5”作为断言。新增主题/保底与真实v5→v6迁移由24负责，文件不冲突。原v5字段顺序不变，读取旧格式仍按版本分支。

只新增 `app/src/test/java/com/example/douqiuqiu/inventory/ProfileV5MigrationTest.kt` 与 `docs/testing/results/22-profile-v5.md`。使用JUnit TemporaryFolder，不改InventoryStoreTest等已有文件，不碰真实存档、配置、Gradle或日志。

读取InventoryStore、CampaignBinaryCodec、JourneyModels、ShopSnapshot及原InventoryStoreTest旧版二进制夹具写法。文件采用magic/version/length/CRC32/payload；旧版本夹具按对应schema独立构造，不能只把v5版本号改成4而留下新增尾部数据。需要构造助手时写在本任务测试类内，不另建共享文件。

## 测试表

| ID | 操作 | 预期 |
| --- | --- | --- |
| P01 | 保存v5：有货架/锁价/部分售空/nextSeed/购买计数/额外英雄 | 重新创建store读取，完整InventoryState等值，库存不恢复、价格不重算 |
| P02 | v4无shop但有消费过的刷新/救济次数、锚点与凭证 | load保留全部计数/金币/物资；仅新字段默认两名初始英雄、shop=null |
| P03 | 对P02首次economy.recover，同格迁移 | 生成一次货架，旧售空库存保留；再次recover不重抽、不重复发启动物资 |
| P04 | v1/v2/v3旧档 | 钱包/物品/通关与已有战斗字段保留；默认新字段合理；写v5后可再读 |
| P05 | v4正在BATTLE，含毒/三类护盾来源/控制状态 | 升级后hp/mana/tick/rng/剩余毒层和护盾/控制全保留，下一步不可重新开战 |
| P06 | v5进行中的挑战，奖励待领取或第三战休息 | 重启仍同一目标/奖励/阶段；领取或撤离一次后不能再领、金币只转一次 |
| P07 | 多次成长成功后保存，再死一次/重启 | 英雄和容量保留，风险物品按位置损失，不回滚永久成长 |
| P08 | 主存档损坏且备份有效 | 恢复上代有效快照并给recoveredFromBackup=true，不重建新账号 |
| P09 | 主档和备份均损坏 | 明确抛读取失败，原文件保持，不给1200或启动赠品 |
| P10 | 比当前高的版本号+旧备份 | 抛UnsupportedSaveVersion，不偷偷回滚备份 |
| P11 | 保存非法货架（重复ID/超6轮换/多珍品/未知商品/负价）、非法解锁英雄 | 拒绝保存且原有效档不变 |
| P12 | 保存后kill/reopen的等价纯store重建 | 钱包、免费次数、凭证与成长不重置；相同快照不触发额外发物 |

外部手动补充（可标未执行）：更新APK到拥有旧v4真实开发存档的模拟器（不清数据），核对余额/次数/英雄后打开商店。禁止为制造材料/成长条件覆盖用户真实存档；自动化使用临时夹具即可。

命令：`./gradlew.bat --offline :app:testDebugUnitTest --tests '*ProfileV5MigrationTest'`。每个迁移版本报告夹具schema来源、实际断言与计数；发现损坏/版本问题报告最小文件生成代码，不替生产代码加特殊测试入口。
