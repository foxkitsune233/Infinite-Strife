package com.example.douqiuqiu.inventory

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/** Debug-only selection; production and sandbox never share a profile or inspection receipt. */
object TestProfile {
    fun available(c: Context) = c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private fun prefs(c: Context) = c.getSharedPreferences("debug-scenario-session", Context.MODE_PRIVATE)
    fun active(c: Context) = available(c) && prefs(c).getBoolean("active", false)
    fun label(c: Context) = if (active(c)) prefs(c).getString("scenario", "测试存档") ?: "测试存档" else "正式存档"
    fun attempt(c: Context) = if (active(c)) prefs(c).getString("attempt", "") ?: "" else ""
    fun battlePaused(c: Context) = active(c) && prefs(c).getBoolean("battlePaused", false)
    fun setBattlePaused(c: Context, paused: Boolean) {
        check(active(c)) { "调试时钟仅独立测试存档可用" }
        check(prefs(c).edit().putBoolean("battlePaused", paused).commit()) { "调试时钟设置失败" }
    }
    fun file(c: Context, sandbox: Boolean = active(c)) = File(c.filesDir,
        if (sandbox) "profile/test-inventory-v1.dat" else "profile/inventory-v1.dat")
    fun inspectionPreferences(c: Context) = if (active(c)) "test-inspection-presentation-v1" else "inspection-presentation-v1"
    fun activate(c: Context, scenario: String, config: String, startPaused: Boolean = false) {
        check(available(c)) { "测试模式仅调试包可用" }
        check(prefs(c).edit().putBoolean("active", true).putString("scenario", scenario).putString("config", config)
            .putString("attempt", java.util.UUID.randomUUID().toString().take(8))
            .putBoolean("battlePaused", startPaused).commit()) { "测试模式设置失败" }
    }
    fun exit(c: Context) {
        check(available(c))
        check(prefs(c).edit().putBoolean("active", false).putBoolean("battlePaused", false).commit()) { "退出测试模式失败" }
    }
    fun lastConfig(c: Context) = prefs(c).getString("config", "") ?: ""
    fun resetSandboxPresentation(c: Context) {
        check(available(c))
        check(c.getSharedPreferences("test-inspection-presentation-v1", Context.MODE_PRIVATE).edit().clear().commit()) { "测试演出重置失败" }
        check(c.getSharedPreferences("test-event-result-v1", Context.MODE_PRIVATE).edit().clear().commit()) { "测试事件演出重置失败" }
    }
}
