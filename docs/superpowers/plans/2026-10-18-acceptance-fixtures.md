# 41号验收夹具实施计划

用户已要求为T06/S04/C02/C05/C06/C10/C11/C12提供可执行夹具；沿用已批准的通用测试模式与独立存档，不另改玩法。

- [x] Debug场景新增可选fixture标识，旧JSON缺省为none；入口提供一键专项预设与说明。
- [x] 新增S04满背包事件、双碎颅、自然控制触发推推/长戟、六主动+双来源状态场景；所有后续交互走真实规则。
- [x] T06提供正常彩绘与强制回退渲染对照，不改正式资源或宣称自然缺图路径已命中。
- [x] 外部单测提供固定roll、独立到期/刷新、抗性边界、双向同帧时序、敌人生成矩阵；100%抗性作为纯公式夹具，当前没有合法100%抗性装备，不伪造可保存来源。
- [x] 只编译Debug/Release Kotlin、测试源码及shared JS，输出测试表、JSON配置、APK；不运行测试/模拟器/采样。

静态复核未发现确定缺陷；外部执行结果仍待回填。

文件边界：`app/src/debug/.../ScenarioFixtures.kt`、`AcceptanceFixtures.kt`、`TestModeActivity.kt`；新外部测试`app/src/test/.../AcceptanceFixtureTest.kt`；`docs/testing/41-acceptance-fixtures.md`及配置JSON。shared生产逻辑、正式存档与Gradle不改。
