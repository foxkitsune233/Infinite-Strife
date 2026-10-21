package com.example.douqiuqiu.inventory

import java.io.*
import java.util.zip.CRC32

class UnsupportedSaveVersion(version: Int) : IOException("存档版本 $version 暂不支持，请保留存档并更新应用")
data class LoadedInventory(val state: InventoryState, val recoveredFromBackup: Boolean = false)

/** Internal app storage only. Explicit schema and checksum; never reset an unreadable save. */
class InventoryStore(
    private val file: File, private val rules: InventoryRules,
    private val initial: () -> InventoryState
) {
    private val backup = File(file.path + ".bak")

    @Synchronized fun exportBackup(): ByteArray = encode(load().state)
    /** Decode and validate without touching the current profile or its previous generation. */
    fun readBackup(bytes: ByteArray): InventoryState = decodeStream(ByteArrayInputStream(bytes), bytes.size.toLong())

    @Synchronized fun load(): LoadedInventory {
        if (!file.exists() && !backup.exists()) return LoadedInventory(initial().also { require(rules.validate(it) == null) })
        val failure = try {
            if (file.exists()) return LoadedInventory(decode(file))
            IOException("主存档缺失")
        } catch (e: UnsupportedSaveVersion) { throw e }
        catch (e: IOException) { e }
        if (backup.exists()) {
            try { return LoadedInventory(decode(backup), true) }
            catch (e: UnsupportedSaveVersion) { throw e }
            catch (e: IOException) { failure.addSuppressed(e) }
        }
        throw IOException("存档无法读取，原文件已保留", failure)
    }

    @Synchronized fun save(state: InventoryState) {
        require(rules.validate(state) == null) { rules.validate(state) ?: "存档无效" }
        val bytes = encode(state)
        // Validate the previous generation before touching either file. If primary is
        // broken, preserve the good recovered generation rather than backing up junk.
        val previous = if (file.exists() || backup.exists()) encode(load().state) else null
        file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("无法创建存档目录") }
        if (previous != null) replace(backup, previous)
        replace(file, bytes)
    }

    private fun replace(target: File, bytes: ByteArray) {
        val temp = File(target.path + ".tmp")
        FileOutputStream(temp).use { out -> out.write(bytes); out.fd.sync() }
        // Android/Linux rename replaces atomically. Windows needs removal first;
        // a verified backup remains available during that window.
        if (!temp.renameTo(target)) {
            if (target.exists() && !target.delete()) throw IOException("无法替换存档")
            if (!temp.renameTo(target)) throw IOException("无法提交存档")
        }
    }

    private fun encode(state: InventoryState): ByteArray {
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(state.gold); out.writeInt(state.runGold)
                out.writeInt(state.backpackWidth); out.writeInt(state.backpackHeight)
                out.writeInt(state.safeWidth); out.writeInt(state.safeHeight); out.writeInt(state.skillSlots)
                out.writeUTF(state.phase.name); out.writeInt(state.items.size)
                state.items.forEach { item ->
                    out.writeUTF(item.instanceId); out.writeUTF(item.definitionId)
                    out.writeUTF(item.place.container.name); out.writeInt(item.place.x); out.writeInt(item.place.y)
                    out.writeBoolean(item.place.rotated); out.writeInt(item.star); out.writeBoolean(item.bound); out.writeInt(item.count)
                }
                CampaignBinaryCodec.write(out, state.progress)
            }
        }.toByteArray()
        if (payload.size > MAX_BYTES) throw IOException("存档体积超出限制")
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(payload.size)
                out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
            }
        }.toByteArray()
    }

    private fun decode(source: File): InventoryState = FileInputStream(source).use { decodeStream(it, source.length()) }

    private fun decodeStream(stream: InputStream, totalBytes: Long): InventoryState {
        if (totalBytes !in 20..(MAX_BYTES + 20).toLong()) throw IOException("存档长度无效")
        try {
            DataInputStream(stream.buffered()).use { input ->
                if (input.readInt() != MAGIC) throw IOException("存档标识无效")
                val version = input.readInt()
                if (version !in 1..VERSION) throw UnsupportedSaveVersion(version)
                val length = input.readInt()
                if (length !in 1..MAX_BYTES || totalBytes != length.toLong() + 20) throw IOException("存档长度无效")
                val checksum = input.readLong()
                val payload = ByteArray(length).also { input.readFully(it) }
                if (CRC32().apply { update(payload) }.value != checksum) throw IOException("存档校验失败")
                DataInputStream(ByteArrayInputStream(payload)).use { data ->
                    val gold = data.readInt(); val runGold = data.readInt()
                    val bw = data.readInt(); val bh = data.readInt(); val sw = data.readInt(); val sh = data.readInt()
                    val slots = data.readInt(); val phase = InventoryPhase.valueOf(data.readUTF())
                    val count = data.readInt()
                    if (count !in 0..10000) throw IOException("物品数量无效")
                    val items = List(count) {
                        val id = data.readUTF(); val def = data.readUTF()
                        val place = ItemPlace(Container.valueOf(data.readUTF()), data.readInt(), data.readInt(), data.readBoolean())
                        StoredItem(id, def, place, data.readInt(), data.readBoolean(), data.readInt())
                    }
                    val progress = if (version >= 2) CampaignBinaryCodec.read(data, version) else com.example.douqiuqiu.journey.CampaignProgress(
                        unlockedHeroes = setOf("juggernaut", "abaddon"))
                    val state = InventoryState(gold, runGold, bw, bh, sw, sh, slots, phase, items, progress)
                    if (data.available() != 0) throw IOException("存档含未知数据")
                    rules.validate(state)?.let { throw IOException(it) }
                    return state
                }
            }
        } catch (e: IllegalArgumentException) { throw IOException("存档内容无效", e) }
    }

    companion object {
        private const val MAGIC = 0x44515131
        const val VERSION = 15
        private const val MAX_BYTES = 1024 * 1024
    }
}
