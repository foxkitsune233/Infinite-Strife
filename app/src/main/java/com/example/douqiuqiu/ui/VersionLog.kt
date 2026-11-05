package com.example.douqiuqiu.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.example.douqiuqiu.ui.components.GameCatalogSheet
import com.example.douqiuqiu.ui.components.GameUi

/** Release notes are deliberately keyed by semantic version, never by the short build index. */
object VersionLog {
    data class Entry(val version: String, val title: String, val lines: List<String>)

    private val entries = listOf(
        Entry("0.1.3", "首次可玩测试版", listOf(
            "完成五战远征、宝箱鉴定、整理与安全箱基础循环。",
            "加入六名首发英雄、装备合成、技能卡与基础战斗表现。",
            "支持战斗日志、独立测试存档与本地备份。"
        )),
        Entry("0.1.4", "体验与远征更新", listOf(
            "节点掉落改为手动拖入背包；敌人和事件均可掉落物品与本局金币。",
            "补齐鉴定演出、快速整理、保存配装、锁定/快速出售和更清晰的物品详情。",
            "战斗、整备、宝箱和设置页面统一为高饱和卡通视觉。"
        )),
        Entry("0.1.5", "内容扩展", listOf(
            "新增敌法师、斧王、虚空假面与军团指挥官。",
            "怒火与寒霜加入流派构筑；追加流派装备、事件、卡牌和敌方构筑。",
            "追加六档挑战、敌方装备预览与新版战斗平衡。"
        )),
        Entry("0.1.6", "任务系统启用", listOf(
            "任务系统上线：24 个节点、40 个任务，含难度分级与三选一，进度在结算时自动推进。",
            "新增英雄等级（金币升级），营地可直接进入委托任务与英雄等级面板。",
            "装备与技能栏强化、存档升级；修复打开整备卡顿，任务一次只显示当前节点。",
            "修复英雄等级升级后返回时露出升级前面板的问题。"
        )),
        Entry("0.1.7", "转生爬塔 · 机制落地", listOf(
            "转生爬塔规则层：20 层塔结构、通关累加上限（第 2 层起每层 +5，至 100 级）与死亡卖出规则。",
            "楼层奖励口径：第 5 层魔晶、第 11–20 层每层转生道具、第 15 层神杖、第 20 层圣剑；等级上限与历史奖励永久保留。",
            "升级金币改为指数增长（每级约为上一级的 1.2 倍）；等级上限改为存档动态值，由爬塔历史最高通关层驱动。",
            "新增待授予池与营地「转生授予」面板：魔晶与神杖一次性手动授予后永久生效。",
            "逐英雄魔晶/神杖效果、塔内连战生命与道中选择、炼狱难度与圣剑装备效果将在后续构建陆续开放。"
        )),
        Entry("1.0.0", "无尽纷争 · Android 稳定版", listOf(
            "补齐任务完成后的剧情演出、英雄加入对话与登顶结局字幕，形成可完整收束的远征叙事。",
            "完成 20 层转生爬塔、楼层奖励、魔晶/神杖授予、圣剑与英雄永久成长循环。",
            "扩充刃甲、血棘、深渊之刃、羊刀、虚灵之刃、希瓦、达贡、金箍棒、永恒之盘、否决、风杖和大隐刀等成装及合成路线。",
            "主动装备、驱散与破被动等机制进入统一战斗触发系统，并补齐 17 张原创装备图标。",
            "本版本作为 Android 线稳定收尾；后续主要开发转入 Godot，Android 版本继续作为迁移对照。"
        ))
    )

    /** Kept internal so offline unit tests can verify build-index normalization. */
    internal fun semanticVersion(versionName: String?): String = versionName?.substringBefore('-') ?: "0.0.0"
    private fun semanticVersion(context: Context): String = semanticVersion(context.packageManager
        .getPackageInfo(context.packageName, 0).versionName)

    private fun versionCode(value: String): List<Int> = value.split('.').map { it.toIntOrNull() ?: 0 }
    /** Numeric comparison: 0.1.10 comes after 0.1.9. */
    internal fun notAfter(version: String, current: String): Boolean {
        val left = versionCode(version)
        val right = versionCode(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val delta = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (delta != 0) return delta < 0
        }
        return true
    }

    fun showIfUnread(activity: AppCompatActivity): Boolean = showIfUnread(activity, semanticVersion(activity))

    /** Debug-only caller supplies a version to reproduce first-open behaviour without reinstalling. */
    internal fun showIfUnread(activity: AppCompatActivity, version: String): Boolean {
        if (AppSettings.lastSeenRelease(activity) == version || entries.none { it.version == version }) return false
        show(activity, isFirstOpen = true, currentVersion = version)
        AppSettings.setLastSeenRelease(activity, version)
        return true
    }

    fun show(activity: AppCompatActivity, isFirstOpen: Boolean = false) = show(activity, isFirstOpen, semanticVersion(activity))

    /** The optional version is used only by the debug fixture screen. */
    internal fun show(activity: AppCompatActivity, isFirstOpen: Boolean, currentVersion: String) {
        val sheet = GameCatalogSheet(activity, if (isFirstOpen) "版本更新" else "版本日志",
            "从 0.1.3 开始记录 · 当前 $currentVersion", "返回设置", stoneHeader = true)
        val visibleEntries = entries.filter { notAfter(it.version, currentVersion) }.reversed()
        if (visibleEntries.isEmpty()) {
            sheet.body.addView(GameUi.panel(activity).apply {
                addView(GameUi.label(activity, "当前版本没有可显示的历史记录。", 14, GameUi.ink))
            })
        }
        visibleEntries.forEach { entry ->
            val panel = GameUi.panel(activity, entry.version == currentVersion)
            panel.addView(GameUi.label(activity, "${entry.version} · ${entry.title}", 17, GameUi.gold, true))
            entry.lines.forEach { panel.addView(GameUi.label(activity, "• $it", 13, GameUi.ink)) }
            sheet.body.addView(panel)
        }
        sheet.show()
    }
}
