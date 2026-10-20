# 六英雄动作图集外部验收

按用户要求，主任务不运行测试和模拟器。已执行的资源导出/完整性检查不等同于以下测试通过。

新增源码：AttackAnimationClockTest 3例、AttackAtlasAssetTest 2例，共5例。

最终编译记录（2026-10-18）：`assembleDebug :app:compileDebugUnitTestKotlin :shared:compileKotlinJs` → BUILD SUCCESSFUL，38项任务（1执行、37为最新）。包含最终图集、发射点与测试源码；未执行任何测试。初次编译暴露的拖尾旧变量引用和Android测试编译环境不含桌面ImageIO问题已修正，最终编译无此两项错误。

```powershell
.\gradlew.bat --offline testDebugUnitTest --tests '*AttackAnimationClockTest' --tests '*AttackAtlasAssetTest' --tests '*DuelVisualCueTest' --tests '*AttackFeedbackTest'
.\gradlew.bat --offline testDebugUnitTest assembleDebug :shared:compileKotlinJs
```

| ID | 自动检查 | 预期 |
|---|---|---|
| A01 | 420ms阶段边界 | 待机→蓄力→出手→收势，负时间与结束回待机 |
| A02 | 高攻速 | 按实际间隔压缩；60ms内仍可访问全部4帧 |
| A03 | 非法参数 | NaN、无穷、负间隔、范围外时长抛错 |
| A04 | 运行图集 | 清单涵盖6张文件；1024×1024、8bit RGBA PNG |
| A05 | 独立帧 | 24张512×512 RGBA；每英雄4张内容哈希不同 |

| ID | 设备操作 | 预期 |
|---|---|---|
| M01 | 六英雄正常攻速各观察20次 | 普攻显示四阶段后回待机，无整图闪入，无相邻角色/帧残片 |
| M02 | 双方交换站位，暂停观察 | 整体朝向敌人；天怒杖同手，PA双手与刀柄关系清楚，不出现单帧翻转 |
| M03 | 低/高攻速与临时攻速叠加 | 不重置攻击结算；动画时长固定到本次攻击结束，后续攻击使用新间隔 |
| M04 | 观察脚底基线、头身比例、武器 | 没有原先整人滑动；重点审主宰蓄力刀长、PA右手、亚巴顿挥剑、Viper颈部 |
| M05 | 天怒/NEC/Viper远程普攻 | 发射点对应出手/提灯/嘴部；敌方镜像后仍正确；只有真实命中有伤害反馈 |
| M06 | 亚巴顿/天怒大招、Viper技能、主宰无敌斩 | 专用姿态优先；结束恢复普通动作；无敌斩本体淡化不乱播普攻 |
| M07 | NEC幽魂护罩、死亡、关闭动画/系统动画0 | 本体静止于待机帧，状态/生命与真实模拟一致 |
| M08 | 战斗中后台再回来、结束后进入下一战 | 不重播旧攻击；没有残留镜像/透明度/上一场帧号 |
| M09 | 小屏设备持续运行并切换英雄 | PNG正常采样，无持续解码导致明显卡顿；两张常驻1024图集约8MiB解码内存，缓存上限仍12MiB |

资源导出记录：6张图集、24帧，地面锚点最大舍入误差小于0.5px；保留导出JSON和PNG总览。此记录不保证源画的武器透视比例完全一致，画工/运动连续性须以M04审阅为准。
