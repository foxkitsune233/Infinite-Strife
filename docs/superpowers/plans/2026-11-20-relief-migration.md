# Android 1.0 破产救济迁移

目标：复用 Android HomeEconomyRules 和 JourneyRules.enter 的救济条件与抵扣规则。

- [x] 新增 ReliefRules：只检查当前 runs_started 批次，金币低于最低门票且未绑定物资回收价小于120；卡牌星级按1/3/9份计价。两次上限、两小时恢复、时钟回退不重复恢复。
- [x] 领取绑定阔剑和两份药膏（当前无堆叠，保存为两个实例）；加一张券。如果没有票价不超过120的简单白箱，用当前批次最后一个目标替换成救济箱。
- [x] AppSession 事务提交领取和出发；符合简单白箱条件自动抵扣最多120，失败不损失券、金币、物资、次数或随机种子。
- [x] 选箱页接入救济按钮、条件和恢复提示；详情和确认框显示实际支付及补助。
- [x] 先编写回归测试并确认缺失接口失败，再实现，运行救济、宝箱、远征和导航回归。

边界：不在本批引入仓库堆叠、携币、商店自动刷新或Windows导出。复用现有按钮皮肤及物品图标。

验证：Godot 4.7.1 headless 下 test_relief、test_relief_screen、test_chest_refresh、test_chest_appraisal、test_campaign_session、test_expedition_screen、test_core_navigation 全部通过，无 SCRIPT ERROR/ERROR。UI测试执行领取、详情、确认及出发操作；本批尚未进行设备视觉验收。
