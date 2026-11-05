# 黑幕字幕结局验证

日期：2026-11-04。

## 当前交付

- 十英雄各 12 段结局正文 + 3 段共通尾声；运行时内容为 `HeroEndingCatalog`，完整文稿见 `docs/design/2026-11-04-hero-endings-cinematic.md`。
- Android 独立 `EndingActivity`：全黑背景、白字淡入、逐段点击、跳过和返回；淡入中点击补全当前段，不越过未读段。
- 跟随 AppSettings 文字倍率和动画开关；支持系统动画关闭、长文滚动、安全区、横竖屏重建后的当前页恢复。
- Debug 设置 → 存档与诊断 → 通用测试模式 → 结局演出预览，有十位英雄入口，打开或结束预览不操作战役存档。
- 当前 Android 未提供正式爬塔 UI，未接入登顶自动播放；普通任务剧情仍为空表。

## 自动验证结果

命令：

```powershell
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.douqiuqiu.quest.HeroEndingTest --tests com.example.douqiuqiu.quest.QuestStoryCatalogTest --tests com.example.douqiuqiu.quest.QuestTrackerTest :app:assembleDebug
```

结果：BUILD SUCCESSFUL；27 例通过，0 失败、0 错误。其中结局与翻页 5 例、既有剧情模型 3 例、既有任务 19 例。

结局测试覆盖所有可用英雄正确选段、未知英雄无错误回退、末页需要主动结束、页码恢复及边界钳制、跳过后停止推进、空字幕序列拒绝。

产物：`app/build/outputs/apk/debug/app-debug.apk`。没有登记新的发布版本或修改 versionCode。

## 尚未完成的设备验收

ADB 已正常启动，但 `adb devices -l` 没有列出连接设备；没有宣称已进行真机／模拟器视觉验收。

设备可用后需检查：

1. 十个入口分别打开对应英雄结局；无立绘与普通对话框，全屏背景为黑。
2. 淡入期间点按补全，正文已显示后点按进入下一段；末页点击返回预览列表。
3. 跳过与系统返回正常退出，不改变金币、库存、英雄解锁或塔奖励。
4. 第五段旋转屏幕、切后台后恢复，仍停留同一段；正文可完整阅读。
5. 标准／最大文字倍率、系统最大字号、横屏窄高窗口下，长段可滚动，继续和跳过保持可达。
6. 关闭动画后正文立即出现；TalkBack 可读当前正文和继续／跳过操作。

这些是设备验收清单，不是已经通过的测试记录。
