# 54：方块合成图鉴、仓库整理与出发检查

## 验证口径

用户已确认样式图与功能设计。仅本地编译与打包；以下单测及设备操作交外部执行，不标记PASS。

单测命令：

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest --tests "com.example.douqiuqiu.inventory.CraftingPresentationTest" --tests "com.example.douqiuqiu.inventory.CraftingTest" --tests "com.example.douqiuqiu.inventory.LoadoutAndPackingTest" --rerun-tasks
```

全量回归按需要运行：`testDebugUnitTest assembleDebug :shared:compileKotlinJs`。UP-TO-DATE表示复用已认定有效的产物，不等于缓存误判；需要新执行证据时使用`--rerun-tasks`，并核对日志和XML。

## 设备验收表

| ID | 操作 | 预期 | 状态 |
|---|---|---|---|
| F01 | 营地→整备→更多→装备合成 | 青蓝石板标题；三列方块；可合成分区在前，金边及勾；待收集项有暗遮罩与缺件数 | 待执行 |
| F02 | 切换“全部/可合成”，滚动到底 | 筛选正确；无材料时提供空态指引；不足项仍可打开 | 待执行 |
| F03 | 打开一个可合成配方 | 产物图、说明、每个组件图与仓库持有/所需量；底部确认按钮固定 | 待执行 |
| F04 | 点击材料，再返回；打开缺材料配方 | 材料说明可读；不足时合成按钮禁用；背包/安全箱的材料不计入 | 待执行 |
| F05 | 合成一次，返回图鉴，再尝试同一配方 | 仅扣一次匹配材料；产物入仓；金币不变；实时重算缺件与分区，不能重复用旧材料 | 待执行 |
| F06 | 用含绑定材料的配方合成 | 继承绑定；同类优先使用未绑定实例，沿用既有规则 | 待执行 |
| W01 | 仓库分类为全部，点击“一键整理” | 按种类、品质降序、名称、星级降序排列；滚动回顶部 | 待执行 |
| W02 | 切分类整理→关闭重开 | 排序持久化；数量、绑定、星级不变；配装、技能、背包与安全箱位置不变 | 待执行 |
| W03 | 副本中打开物资整理 | 不开放局外仓库一键整理；原背包/安全箱整理保留 | 待执行 |
| D01 | 选择宝箱后点击进入挑战 | 打开出发检查，不扣门票，不创建run | 待执行 |
| D02 | 在检查内切换已解锁英雄 | 自动套用该英雄最近方案并刷新属性/9装备/8技能预览；锁定技能槽清晰 | 待执行 |
| D03 | 切换方案1/2/3 | 已保存方案可切；未保存项禁用；缺失物品报错并保留原配装 | 待执行 |
| D04 | 切换英雄或方案后关闭、再打开 | 已切换配置保留；门票与金币没有扣除 | 待执行 |
| D05 | 输入超额、空、负数、0及有效携入数 | 无效输入原地提示；允许0；合法数值才调用既有购票事务 | 待执行 |
| D06 | 点击确认出发（含有补助和无补助两种） | 门票/补助/携入金币按现规则一次结算；run英雄与预览一致 | 待执行 |
| D07 | 点击前往整理，改配装后返回并重开检查 | 可顺畅往返；不自动用旧方案覆盖刚手动调整的装备 | 待执行 |
| M01 | 320dp竖屏、系统字号1.3；再切横屏 | 图标保持方块，字可换行；确认与关闭可触达；正文可滚动 | 待执行 |

新增共享工具不改变存档schema（仍v12）；仓库顺序复用原items序列存储。

## 本地构建记录

- 命令：`--offline --rerun-tasks assembleDebug :app:compileDebugUnitTestKotlin :app:compileReleaseKotlin :shared:compileKotlinJs`
- 结果：BUILD SUCCESSFUL，52 actionable tasks: 52 executed。仅编译单测源码，未执行测试。
- 日志：`docs/testing/results/_build-54-crafting-departure.log`。
- 分发包：`dist/0.1.3/DouQiuQiu-0.1.3-7AAB-25FE-0FC4.apk`，versionCode=1008，155126247字节。
- aapt核对包内版本，apksigner验证成功，签名沿用原Debug证书；分发副本与构建源SHA256一致。
- SHA256：`FFFEE4042ABEC27E5D476EA0DE0C7A5BF88A14D1987EC5EE2D2737ADFA431ADF`。
- 尚未安装本包、运行单测或执行设备操作；上表仍为待执行。
