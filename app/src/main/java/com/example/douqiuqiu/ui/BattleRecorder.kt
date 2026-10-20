package com.example.douqiuqiu.ui

import android.content.Context
import android.os.SystemClock
import com.example.douqiuqiu.journey.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bounded, asynchronous diagnostic journal, independent of the campaign and its RNG. */
object BattleRecorder {
    private val session = UUID.randomUUID().toString().take(8)
    private val serial = AtomicLong()
    private val lost = AtomicInteger()
    private val writer = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64)) { task ->
        Thread(task, "battle-journal").apply { isDaemon = true }
    }
    private fun directory(c: Context, sandbox: Boolean) = File(c.filesDir, if (sandbox) "test-battle-records" else "battle-records").apply { mkdirs() }
    fun frame(c: Context, runId: String, round: Int, before: DuelState, after: DuelFrame,
              action: String = "tick", committed: Boolean = true) {
        val app = c.applicationContext
        val sandbox = com.example.douqiuqiu.inventory.TestProfile.active(c)
        val scenario = com.example.douqiuqiu.inventory.TestProfile.label(c)
        val attempt = com.example.douqiuqiu.inventory.TestProfile.attempt(c)
        val seq = serial.incrementAndGet()
        val wall = System.currentTimeMillis(); val uptime = SystemClock.uptimeMillis()
        try {
            writer.execute {
                val missing = lost.getAndSet(0)
                try {
                    val record = JSONObject().put("schema", 1).put("session", session).put("sequence", seq)
                        .put("profile", if (sandbox) "sandbox" else "normal").put("scenario", scenario).put("scenarioAttempt", attempt)
                        .put("wallMs", wall).put("uptimeMs", uptime).put("runId", runId).put("round", round)
                        .put("action", action).put("committed", committed).put("droppedBefore", missing)
                        .put("before", state(before)).put("after", state(after.state))
                        .put("events", JSONArray().apply { after.cues.forEach { cue ->
                            put(JSONObject().put("tick", cue.tick).put("sequence", cue.sequence).put("actor", cue.actor)
                                .put("target", cue.target).put("hero", cue.heroId).put("kind", cue.kind.name)
                                .put("lifeDamage", cue.lifeDamage).put("shieldDamage", cue.shieldDamage)
                                .put("damageType", cue.damageType?.name ?: "").put("critical", cue.critical))
                        } })
                    val dir = directory(app, sandbox)
                    val key = "${session}_${attempt}_${runId.hashCode().toUInt().toString(16)}_$round"
                    var file = File(dir, "$key.jsonl")
                    var part = 0
                    while (file.length() >= 8 * 1024 * 1024) { part++; file = File(dir, "${key}_$part.jsonl") }
                    file.appendText(record.toString() + "\n")
                    if (seq % 64L == 0L) {
                        var size = dir.listFiles().orEmpty().sumOf { it.length() }
                        dir.listFiles().orEmpty().sortedBy { it.lastModified() }.forEach { old ->
                            if (size > 48L * 1024 * 1024 && old != file) { val n = old.length(); if (old.delete()) size -= n }
                        }
                    }
                } catch (e: Exception) { lost.addAndGet(missing + 1); android.util.Log.w("BattleJournal", "record failed", e) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { lost.incrementAndGet() }
    }
    private fun state(s: DuelState) = JSONObject().put("tick", s.tick).put("rng", s.rng)
        .put("won", s.won ?: JSONObject.NULL).put("player", fighter(s.player)).put("enemy", fighter(s.enemy))
        .put("summary", JSONArray(s.log))
    private fun fighter(f: Fighter) = JSONObject().put("hero", f.heroId).put("hp", f.hp).put("maxHp", f.maxHp)
        .put("mana", f.mana).put("maxMana", f.maxMana).put("attack", f.attack).put("speed", f.speed)
        .put("attackIn", f.attackIn).put("ultimateIn", f.ultIn)
        .put("shields", JSONObject().put("universal", f.shield).put("physical", f.physicalShield).put("magic", f.magicShield + f.skyShield))
        .put("control", com.example.douqiuqiu.status.TimedControls.current(f).let { control ->
            JSONObject().put("stun", control.stunned).put("hex", control.hexed).put("silence", control.silenced)
                .put("disarm", control.disarmed).put("muted", control.muted).put("banished", control.banished) })
        .put("timedControls", JSONArray().apply { f.timedControls.forEach { entry ->
            put(JSONObject().put("source", entry.sourceId).put("caster", entry.caster).put("kind", entry.kind.name)
                .put("remainingMs", entry.remainingMs).put("dispel", entry.dispelClass.name)
                .put("appliedAtTick", entry.appliedAtTick).put("suppressed", f.equipment.immunityMs > 0))
        } })
        .put("equipment", JSONArray().apply { f.equipment.items.forEach { put(JSONObject().put("instance", it.instanceId).put("id", it.definitionId)) } })
        .put("equipmentCooldowns", JSONObject(f.equipment.cooldowns.remainingByGroup))
        .put("manualItems", JSONArray(f.equipment.manual.toList()))
        .put("immunityMs", f.equipment.immunityMs).put("protectionMs", f.equipment.protectionMs)
        .put("poisonState", f.poisonPool?.toString() ?: "").put("cardState", f.cardState.toString())
        .put("rawFighter", f.toString())
    /** Export runs behind prior frame writes. Caller owns and closes the destination stream. */
    fun export(c: Context, destination: OutputStream, profile: () -> ByteArray, done: (Exception?) -> Unit) {
        val sandbox = com.example.douqiuqiu.inventory.TestProfile.active(c)
        try { writer.execute {
            try {
                ZipOutputStream(destination).use { zip ->
                    fun add(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                    directory(c, sandbox).listFiles().orEmpty().sortedBy { it.name }.forEach { add("frames/${it.name}", it.readBytes()) }
                    add("scenario-config.json", (if (sandbox) com.example.douqiuqiu.inventory.TestProfile.lastConfig(c) else "{}").toByteArray())
                    add("current-profile.dqq", profile())
                    add("last-diagnostic.txt", BattleDiagnostics.read(c, sandbox).toByteArray())
                    add("inspection-receipt.json", com.example.douqiuqiu.ui.components.InspectionDialog.diagnostic(c, sandbox).toByteArray())
                    add("README.txt", ("Battle journal v1; JSONL before/after states and semantic cues.\n" +
                        "Raw fighter/card/poison fields are Kotlin diagnostic snapshots, not a stable replay schema.\n" +
                        "Retains about 48MiB; session=$session; unreportedDropped=${lost.get()}.\n" +
                        "Historical files may include multiple app sessions. No audio/visual observation changes battle RNG.\n").toByteArray())
                }
                done(null)
            } catch (e: Exception) { try { destination.close() } catch (_: Exception) { }; done(e) }
        } } catch (e: java.util.concurrent.RejectedExecutionException) {
            try { destination.close() } catch (_: Exception) { }; done(e)
        }
    }
}
