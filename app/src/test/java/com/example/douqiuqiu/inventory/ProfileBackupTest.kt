package com.example.douqiuqiu.inventory

import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private val rules = InventoryRules(mapOf("card" to ItemDefinition("card", "卡", ItemKind.CARD, 1, 1)))
    private val initial = InventoryState(gold = 333, items = listOf(StoredItem("a", "card", ItemPlace(Container.SAFE), 2, true)))
    private fun store(file: java.io.File) = InventoryStore(file, rules) { initial }

    @Test fun exportAndInspectRoundTripWithoutReplacingCurrentProfile() {
        val file = temp.root.resolve("save.dat"); val store = store(file); store.save(initial)
        val snapshot = store.exportBackup(); val before = file.readBytes()
        assertEquals(initial, store.readBackup(snapshot)); assertArrayEquals(before, file.readBytes())
    }
    @Test fun corruptChecksumCannotReplaceCurrentProfile() {
        val file = temp.root.resolve("save.dat"); val store = store(file); store.save(initial)
        val bytes = store.exportBackup(); val before = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(IOException::class.java) { store.readBackup(bytes) }; assertArrayEquals(before, file.readBytes())
    }
    @Test fun validChecksumWithInvalidBusinessStateIsRejected() {
        val file = temp.root.resolve("save.dat"); val store = store(file); store.save(initial)
        val bytes = store.exportBackup(); val before = file.readBytes()
        ByteBuffer.wrap(bytes).putInt(20, -1)
        ByteBuffer.wrap(bytes).putLong(12, CRC32().apply { update(bytes, 20, bytes.size - 20) }.value)
        assertThrows(IOException::class.java) { store.readBackup(bytes) }; assertArrayEquals(before, file.readBytes())
    }
    @Test fun futureVersionDoesNotReplaceCurrentProfile() {
        val file = temp.root.resolve("save.dat"); val store = store(file); store.save(initial)
        val bytes = store.exportBackup(); val before = file.readBytes(); ByteBuffer.wrap(bytes).putInt(4, 999)
        assertThrows(UnsupportedSaveVersion::class.java) { store.readBackup(bytes) }; assertArrayEquals(before, file.readBytes())
    }
    @Test fun oversizedAndTruncatedBackupsAreRejected() {
        val file = temp.root.resolve("save.dat"); val store = store(file); store.save(initial)
        assertThrows(IOException::class.java) { store.readBackup(ByteArray(1024 * 1024 + 21)) }
        val bytes = store.exportBackup()
        assertThrows(IOException::class.java) { store.readBackup(bytes.copyOf(bytes.size - 1)) }
        assertEquals(initial, store.load().state)
    }
    @Test fun restoringValidatedBackupKeepsPreviousGenerationRecoverable() {
        val file = temp.root.resolve("save.dat"); val store = store(file); store.save(initial)
        val old = store.exportBackup(); val current = initial.copy(gold = 555); store.save(current)
        store.save(store.readBackup(old)); assertEquals(initial, store.load().state)
        file.writeText("simulate corruption")
        val recovered = store.load(); assertTrue(recovered.recoveredFromBackup); assertEquals(current, recovered.state)
    }
}
