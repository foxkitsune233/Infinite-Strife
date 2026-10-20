package com.example.douqiuqiu.inventory

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InventoryStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val rules = InventoryRules(mapOf("card" to ItemDefinition("card", "卡", ItemKind.CARD, 1, 1)))
    private fun initial() = InventoryState(items = listOf(StoredItem("a", "card", ItemPlace(Container.SAFE), 2, true)))

    @Test fun roundTripRetainsIdentityBindingStarAndPosition() {
        val file = temp.root.resolve("profile.dat")
        val store = InventoryStore(file, rules, ::initial)
        assertEquals(initial(), store.load().state)
        store.save(initial().copy(gold = 333))
        assertEquals(initial().copy(gold = 333), InventoryStore(file, rules, ::initial).load().state)
    }

    @Test fun corruptPrimaryRecoversPreviousValidSaveAndReportsRecovery() {
        val file = temp.root.resolve("profile.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(initial())
        store.save(initial().copy(gold = 500))
        file.writeText("truncated")
        val recovered = store.load()
        assertTrue(recovered.recoveredFromBackup)
        assertEquals(initial(), recovered.state)
        store.save(recovered.state.copy(gold = 700))
        file.writeText("truncated again")
        assertEquals(initial(), store.load().state)
    }

    @Test fun bothBrokenSavesRaiseErrorInsteadOfGrantingNewStarterItems() {
        val file = temp.root.resolve("profile.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(initial())
        file.writeText("bad")
        assertThrows(java.io.IOException::class.java) { store.load() }
        assertEquals("bad", file.readText())
    }

    @Test fun invalidSnapshotCannotReplaceValidSave() {
        val file = temp.root.resolve("profile.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(initial())
        assertThrows(IllegalArgumentException::class.java) { store.save(initial().copy(items = initial().items + initial().items)) }
        assertEquals(initial(), store.load().state)
    }

    @Test fun aNewerVersionMustNotSilentlyRollBackToOlderBackup() {
        val file = temp.root.resolve("profile.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(initial())
        store.save(initial().copy(gold = 500))
        java.io.RandomAccessFile(file, "rw").use { it.seek(4); it.writeInt(999) }
        assertThrows(UnsupportedSaveVersion::class.java) { store.load() }
    }

    @Test fun entireBattleAndChestInformationSurvivesRestart() {
        val file = temp.root.resolve("profile.dat")
        val store = InventoryStore(file, rules, ::initial)
        val journey = com.example.douqiuqiu.journey.JourneyRules(rules)
        val batch = journey.ensureBatch(initial())
        val entered = journey.enter(batch, batch.progress.chests.first().id, "juggernaut").state
        val fighting = journey.tick(journey.startBattle(entered).state)
        store.save(fighting)
        assertEquals(fighting, InventoryStore(file, rules, ::initial).load().state)
    }

    @Test fun reliefAndRefreshCountersSurviveRestartWithoutRefilling() {
        val file = temp.root.resolve("economy.dat")
        val economy = com.example.douqiuqiu.journey.HomeEconomyState(true, 0, 1234, 1, 5678, 4, 2)
        val state = initial().copy(progress = initial().progress.copy(economy = economy))
        val store = InventoryStore(file, rules, ::initial)
        store.save(state)
        assertEquals(state, InventoryStore(file, rules, ::initial).load().state)
    }

    @Test fun v3AbaddonBattleShieldDoesNotBecomeAnExtraSourceOnNextCast() {
        val file = temp.root.resolve("legacyBattle.dat")
        val payload = java.io.ByteArrayOutputStream().also { buffer ->
            java.io.DataOutputStream(buffer).use { out ->
                out.writeInt(333); out.writeInt(0); listOf(4, 2, 2, 2, 4).forEach(out::writeInt)
                out.writeUTF("BATTLE"); out.writeInt(0)
                out.writeLong(1729); out.writeInt(1); out.writeInt(0); out.writeBoolean(false)
                out.writeUTF(""); out.writeLong(0); out.writeLong(0); out.writeInt(0); out.writeInt(0); out.writeInt(0)
                out.writeBoolean(true); out.writeUTF("run-old"); out.writeUTF("abaddon")
                // v3 target: one fixed card in a white low-cost chest.
                out.writeUTF("box-old"); out.writeUTF("白"); out.writeInt(0); out.writeInt(60); out.writeInt(0)
                out.writeInt(1); out.writeUTF("card"); out.writeInt(0); out.writeInt(0); out.writeInt(0)
                out.writeLong(1729); out.writeDouble(1000.0); out.writeDouble(1000.0); out.writeInt(1); out.writeUTF("BATTLE")
                out.writeUTF("card"); out.writeInt(0); out.writeBoolean(true)
                for (hero in listOf("abaddon", "phantom_assassin")) {
                    out.writeUTF(hero)
                    // hp/maxhp/mana/maxmana/attack/speed/bat/armor/mr/regen/mpregen,
                    // three shields, twelve legacy combat timers/values.
                    listOf(1000.0, 1000.0, if (hero == "abaddon") 100.0 else 0.0, 500.0,
                        0.0, 100.0, 1.7, 0.0, 0.0, 0.0, 0.0, if (hero == "abaddon") 120.0 else 0.0, 0.0, 0.0,
                        10.0, if (hero == "abaddon") 0.0 else 10.0,
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0).forEach(out::writeDouble)
                    out.writeInt(0); out.writeInt(0); out.writeDouble(0.0)
                }
                out.writeLong(1729); out.writeInt(120); out.writeInt(-1); out.writeInt(0)
            }
        }.toByteArray()
        java.io.DataOutputStream(java.io.FileOutputStream(file)).use { out ->
            out.writeInt(0x44515131); out.writeInt(3); out.writeInt(payload.size)
            out.writeLong(java.util.zip.CRC32().apply { update(payload) }.value); out.write(payload)
        }
        val loaded = InventoryStore(file, rules, ::initial).load().state
        val fight = com.example.douqiuqiu.journey.DuelEngine.step(loaded.progress.run!!.fight!!)
        assertEquals(180.0, fight.player.shield, .00001)
        assertEquals(946.0, fight.enemy.hp, .00001)
    }

    @Test fun legacyV1V2AndV3ProfilesMigrateWithoutGrantingStarterItemsAgain() {
        for (version in listOf(1, 2, 3)) {
            val file = temp.root.resolve("legacy$version.dat")
            val payload = java.io.ByteArrayOutputStream().also { buffer ->
                java.io.DataOutputStream(buffer).use { out ->
                    out.writeInt(333); out.writeInt(0)
                    listOf(4, 2, 2, 2, 4).forEach(out::writeInt)
                    out.writeUTF("HOME"); out.writeInt(0)
                    if (version >= 2) {
                        out.writeLong(1729); out.writeInt(2); out.writeInt(1); out.writeBoolean(true)
                        out.writeUTF("legacy complete"); out.writeLong(0); out.writeLong(0)
                        out.writeInt(0)
                        if (version >= 3) out.writeInt(0) // manual item modes introduced in v3
                        out.writeInt(0); out.writeBoolean(false)
                    }
                }
            }.toByteArray()
            java.io.DataOutputStream(java.io.FileOutputStream(file)).use { out ->
                out.writeInt(0x44515131); out.writeInt(version); out.writeInt(payload.size)
                out.writeLong(java.util.zip.CRC32().apply { update(payload) }.value); out.write(payload)
            }
            val store = InventoryStore(file, rules, ::initial)
            val loaded = store.load().state
            assertEquals(333, loaded.gold)
            assertTrue(loaded.items.isEmpty())
            assertEquals(if (version >= 2) 1 else 0, loaded.progress.clears)
            store.save(loaded)
            assertEquals(loaded, store.load().state)
        }
    }
}
