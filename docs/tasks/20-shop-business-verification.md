# 外围任务20：商店业务验收（测试任务）

本批按用户要求由外部执行测试。只允许修改/扩展 `app/src/test/java/com/example/douqiuqiu/inventory/ShopIntegrationTest.kt`，并新增 `docs/testing/results/20-shop-business.md` 报告。不得改生产代码、其他测试、Gradle、配置、日志或存档。与21/22互相独立，Gradle错峰运行。

先读 shared/shop/ShopRules.kt、journey/SupplyRules.kt、app/inventory/HomeEconomyRules.kt；现有5项ShopIntegrationTest已经写好并编译，主任务没有在用户改变测试偏好后执行它们。

## 测试表

用纯Kotlin夹具：常备component基准100/库存3；8张不同ROTATING卡各100/3；RARE物品1000/1、minimumShopClears=3。InventoryState默认金币1200，恢复时间1000ms。不可用生产实现计算期待值再比较自身。

| ID | 条件与操作 | 预期 |
| --- | --- | --- |
| S01 | 首次economy.recover | 常备在架；轮换恰6个且无重复；未3通关不出珍品；component锁价90 |
| S02 | buy component一次 | 钱包1110、库存2、购买计数1，锁价仍90；实例入仓且ID唯一 |
| S03 | 买未抽中的轮换卡、重复实例ID、金币89、售空商品 | 各失败，整个输入快照不变 |
| S04 | 连买3次component后第4次 | 前3次各扣90，最后库存0；第4次失败不扣物资 |
| S05 | 同时间/同时间格reopen与时间回拨 | 货架、价格、库存、nextSeed、revision均不重抽 |
| S06 | 旧快照无shop、shopStock component=0，同格首次迁移 | component仍售空，不能借升级补货 |
| S07 | 旧快照无shop但已跨自动格 | 正常生成新批/恢复库存；不是永久保留过期售空状态 |
| S08 | 买component一次，再在3600000ms付费刷新 | 扣80后金币1030，新revision+1；需求计数使下一轮锁价93 |
| S09 | 再到7200000ms | 自动新批revision再+1，不再扣80；下次自动时点未推迟 |
| S10 | 钱包0且同时到自动边界，调用refreshShop | 自动优先，成功免费更新 |
| S11 | 未到边界且金币79，付费刷新 | 失败且原快照完全不变 |
| S12 | 旧revision报价跨格调用economy.buy | 拒绝过期报价、不扣款；重新打开得到新货架后可按新价买 |
| S13 | 挑战进行中buy/refreshShop/recover | 购买/刷新拒绝；recover不改变挑战或库存 |
| S14 | 货架生成前后对比已有目标箱与CampaignProgress.seed | 内容、坐标、鉴定状态与挑战seed均不变，商店用独立seed |
| S15 | 拥有3通关，若干固定seed生成珍品 | 只出现合格珍品，最多1份；无货不是伪造商品。不要用随机抽样断言必须30%精确命中 |
| S16 | 卖出物品与升星卡 | 回收按基础价格40%，一/二/三星用1/3/9成本；不按锁定涨价重复套利，绑定不可卖 |

验收命令（由你执行）：`./gradlew.bat --offline :app:testDebugUnitTest --tests '*ShopIntegrationTest'`。记录实际tests/failures/errors；若编译被其他并发文件阻断，报告文件/行号与命令，不越界修复。不为迎合当前实现修改预期。

报告格式：环境/命令、逐ID通过/失败/未执行、实际测试数、失败最小快照与断言、疑似生产问题位置。不得只交BUILD SUCCESSFUL。
