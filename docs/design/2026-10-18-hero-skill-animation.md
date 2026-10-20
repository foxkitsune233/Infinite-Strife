# 六英雄简化技能表现交付

按用户明确指定的简化动作实施，新增三张透明PNG：亚巴顿右手举剑、天怒同侧双手插杖、Viper鼓喉蓄力。保留原idle/attack及四帧审阅图；生成使用内置imagegen，完整最终提示词及生成来源见同目录 `2026-10-18-hero-skill-prompts.json`。首轮网络失败后在同一工具重试成功，未切换服务商。

运行入口：DuelEngine.stepWithCues返回DuelFrame（原DuelState＋瞬时语义事件）。原step继续返回同一战斗状态；不存储事件、不改存档schema、不调用额外随机数。JourneyRules.tick可选交出此帧，Activity保存成功后才绑定到画布。

事件类型：ULTIMATE_CAST、ATTACK、OMNI_SLASH、FLARE_TICK、PA_CRITICAL_HIT。带tick、actor、target和heroId；不解析中文日志。PA飙血只由确保暴击实际造成生命伤害触发。事件列表只在View内保留最近15tick，并去重；页面重建后仅按现有快照恢复持续状态。

表现：主宰无敌期间淡出本体，以真实斩击事件画目标刀光；天怒插杖短动作与目标法阵六段光柱；PA目标标记和红色粒子；亚巴顿举剑、驱散脉冲、护盾壳及冲击；NEC幽魂期间绿色半透、光环且冻结普攻姿势；Viper先charge再attack，约180ms蓄力、280ms弹道和命中散开。Viper原有毒/破坏即时施加不延后，状态栏可先于视觉弹道命中更新。

时钟：普通动画由战斗tick与最多100ms绘制插值驱动；后台停止tick不继续消耗效果。最终胜负已保存后允许最多900ms只播放收尾，再打开真实结算；期间移除主动操作栏，不追加伤害、掉落或领取。onPause取消待切页任务，返回直接读取保存后的阶段。

GameArt为pose扩展采样加载，沿用12MiB缓存和缺图回退。DuelIllustration负责姿势和脚底锚点，HeroSpellEffects负责程序特效。系统关闭动画时保留静态持续状态和HUD，不反复调度特效帧。没有加入音效。

验证：三张图已查看，角落alpha=0；源码及外部测试源码编译通过；`assembleDebug :app:compileDebugUnitTestKotlin :shared:compileKotlinJs` BUILD SUCCESSFUL。APK压缩包核对含三个新素材。新增9项回归源码和14项设备检查均尚未执行，不宣称行为/视觉已验收。
