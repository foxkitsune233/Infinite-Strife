package com.example.douqiuqiu.inventory

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileV13MigrationTest {
    @get:Rule val temp = TemporaryFolder()
    private val rules = InventoryRules(mapOf("gear" to ItemDefinition("gear", "装备", ItemKind.EQUIPMENT, 1, 1)))
    private val state = InventoryState(gold = 57, items = listOf(StoredItem("owned", "gear", ItemPlace(Container.WAREHOUSE))))
    private fun store(name: String = "profile.dat") = InventoryStore(temp.root.resolve(name), rules) { state }

    @Test fun v13RoundTripRetainsOwnedAndConsumedLocksAndExportsCurrentSchema() {
        val store = store()
        val locked = state.copy(progress = state.progress.copy(lockedItems = setOf("owned", "consumed")))
        store.save(locked)
        assertEquals(locked, store.load().state)
        val bytes = store.exportBackup()
        assertEquals(InventoryStore.VERSION, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(locked, store.readBackup(bytes))
    }

    @Test fun v12ProfileMigratesWithEmptyLocksAndPreservesAllExistingFields() {
        val store = store()
        val payload = store.exportBackup().copyOfRange(20, store.exportBackup().size - 4)
        val legacy = envelope(12, payload)
        assertEquals(state, store.readBackup(legacy))
        temp.root.resolve("profile.dat").writeBytes(legacy)
        store.save(store.load().state)
        assertEquals(InventoryStore.VERSION, ByteBuffer.wrap(store.exportBackup()).getInt(4))
        assertEquals(state, store.load().state)
    }

    @Test fun invalidLocksCannotReplaceAValidSaveAndDuplicateSerializedIdsAreRejected() {
        val store = store()
        store.save(state)
        for (ids in listOf(setOf(" "), setOf("x".repeat(129)), (0..10000).map { "i$it" }.toSet())) {
            assertThrows(IllegalArgumentException::class.java) { store.save(state.copy(progress = state.progress.copy(lockedItems = ids))) }
            assertEquals(state, store.load().state)
        }
        val bytes = store.exportBackup()
        val legacyPayload = bytes.copyOfRange(20, bytes.size - 4)
        val tail = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out -> out.writeInt(2); out.writeUTF("owned"); out.writeUTF("owned") }
        }.toByteArray()
        assertThrows(IOException::class.java) { store.readBackup(envelope(13, legacyPayload + tail)) }
        val invalidCount = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> out.writeInt(10001) } }.toByteArray()
        assertThrows(IOException::class.java) { store.readBackup(envelope(13, legacyPayload + invalidCount)) }
        assertEquals(state, store.load().state)
    }

    private fun envelope(version: Int, payload: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(0x44515131); out.writeInt(version); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }.toByteArray()
}
