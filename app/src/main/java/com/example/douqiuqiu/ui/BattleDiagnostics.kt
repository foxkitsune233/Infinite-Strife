package com.example.douqiuqiu.ui

import android.content.Context
import com.example.douqiuqiu.inventory.InventoryState
import java.io.File

/** Local diagnostic snapshot only. Never edits the profile or advances simulation. */
object BattleDiagnostics {
    private fun file(c: Context) = File(c.filesDir, if (com.example.douqiuqiu.inventory.TestProfile.active(c)) "test-battle-last-diagnostic.txt" else "battle-last-diagnostic.txt")
    fun record(c: Context, reason: String, state: InventoryState?, failure: Exception? = null) {
        val run = state?.progress?.run
        val note = "reason=$reason\nrun=${run?.id} stage=${run?.stage} round=${run?.round} tick=${run?.fight?.tick}\n" +
            "playerHP=${run?.fight?.player?.hp} enemyHP=${run?.fight?.enemy?.hp}\n" + (failure?.stackTraceToString() ?: "")
        android.util.Log.w("DouQiuQiuBattle", note, failure)
        try { file(c).writeText(note.take(32_000)) } catch (_: java.io.IOException) { /* Retain Logcat evidence. */ }
    }
    fun read(c: Context, sandbox: Boolean = com.example.douqiuqiu.inventory.TestProfile.active(c)): String = try {
        val record = File(c.filesDir, if (sandbox) "test-battle-last-diagnostic.txt" else "battle-last-diagnostic.txt")
        if (record.exists()) record.readText() else "尚未记录到战斗异常。"
    }
        catch (_: java.io.IOException) { "诊断记录暂不可读。" }
}
