package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.combat.ShieldPool
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.CRC32

/** New format round trips and an independently written real v6 binary fixture. External execution only. */
class ProfileV7CardStateTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private val definitions = CardCombat.enabledCards.associate { it.id to ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height) }
    private val rules = InventoryRules(definitions)
    private val journey = JourneyRules(rules)
    private fun snapshot(): InventoryState {
        val items = listOf(StoredItem("one", "D2", ItemPlace(Container.SKILLS, 0)), StoredItem("two", "D4", ItemPlace(Container.SKILLS, 1)))
        val inventory = InventoryState(items = items, phase = InventoryPhase.BATTLE)
        var player = journey.fighter(inventory, "phantom_assassin").copy(hp = 500.0)
        player = player.copy(cardState = player.cardState.copy(started = true, attackCount = 3,
            readyAt = mapOf("one:D2.critSpeed" to 25), healed = mapOf("two:D4.healToShield" to 70.0),
            buffs = listOf(CardBuff("one:D2.critSpeed", CardBuffKind.ATTACK_SPEED, 15.0, 35))))
        val enemy = journey.fighter(InventoryState(), "juggernaut").copy(cardState = CardBattleState(criticalFailures = mapOf("hero:blade-dance" to 2)))
        val fight = DuelState(player, enemy, 12345, 20)
        val target = TargetChest("old-box", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0, 1)))
        return inventory.copy(progress = CampaignProgress(run = JourneyState("run", player.heroId, target, fight.rng,
            player.hp, player.maxHp, stage = JourneyStage.BATTLE, fight = fight)))
    }
    private fun store(file: File) = InventoryStore(file, rules) { InventoryState() }
    @Test fun allCardRuntimeFieldsSurviveSaveRecreation() {
        val file = temp.root.resolve("v7.dat")
        val expected = snapshot()
        store(file).save(expected)
        // 当前写入v10；独立手写的v6旧档夹具仍按原格式读取。
        DataInputStream(FileInputStream(file)).use { it.readInt(); assertEquals(InventoryStore.VERSION, it.readInt()) }
        assertEquals(expected, store(file).load().state)
    }
    @Test fun restoredBattleContinuesSameStateAndRng() {
        val file = temp.root.resolve("resume.dat")
        val original = snapshot()
        store(file).save(original)
        val loaded = store(file).load().state
        assertEquals(journey.tick(original), journey.tick(loaded))
    }
    @Test fun invalidRuntimeCannotReplaceValidSave() {
        val file = temp.root.resolve("invalid.dat")
        val original = snapshot()
        val storage = store(file)
        storage.save(original)
        val run = original.progress.run!!
        val fight = run.fight!!
        val bad = fight.player.copy(cardState = fight.player.cardState.copy(
            buffs = listOf(CardBuff("missing", CardBuffKind.ATTACK_SPEED, 1000000.0, Int.MAX_VALUE))))
        val corrupt = original.copy(progress = original.progress.copy(run = run.copy(fight = fight.copy(player = bad))))
        assertThrows(IllegalArgumentException::class.java) { storage.save(corrupt) }
        assertEquals(original, storage.load().state)
    }
    @Test fun futureVersionStillRejectsWithoutFallingBack() {
        val file = temp.root.resolve("future.dat")
        val storage = store(file)
        storage.save(snapshot()); storage.save(snapshot())
        // A future schema must never fall back to an older backup.
        RandomAccessFile(file, "rw").use { it.seek(4); it.writeInt(999) }
        assertThrows(UnsupportedSaveVersion::class.java) { storage.load() }
    }
    @Test fun realV6FightKeepsPanelButDoesNotReplayNewCardOpening() {
        val file = temp.root.resolve("legacy6.dat")
        val original = snapshot()
        writeV6(file, original)
        val loaded = store(file).load().state
        assertEquals(original.progress.run!!.fight!!.player.hp, loaded.progress.run!!.fight!!.player.hp, .00001)
        assertEquals(CardBattleState(), loaded.progress.run!!.fight!!.player.cardState)
        assertEquals(CardBattleState(), loaded.progress.run!!.fight!!.enemy.cardState)
        assertEquals(original.items, loaded.items)
        store(file).save(loaded)
        assertEquals(loaded, store(file).load().state)
    }

    /** Actual v6 layout: no new card-state bytes are written at all, not a v7 header rewrite. */
    private fun writeV6(file: File, s: InventoryState) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(s.gold); out.writeInt(s.runGold)
            listOf(s.backpackWidth, s.backpackHeight, s.safeWidth, s.safeHeight, s.skillSlots).forEach(out::writeInt)
            out.writeUTF(s.phase.name); out.writeInt(s.items.size)
            s.items.forEach { item ->
                out.writeUTF(item.instanceId); out.writeUTF(item.definitionId); out.writeUTF(item.place.container.name)
                out.writeInt(item.place.x); out.writeInt(item.place.y); out.writeBoolean(item.place.rotated)
                out.writeInt(item.star); out.writeBoolean(item.bound); out.writeInt(item.count)
            }
            val p = s.progress
            out.writeLong(p.seed); out.writeInt(p.batch); out.writeInt(p.clears); out.writeBoolean(p.extracted)
            out.writeUTF(p.lastEnding); out.writeLong(p.shopCycle); out.writeLong(p.shopSeen)
            out.writeInt(0) // fixture: empty shopStock
            out.writeInt(0) // manualItems
            out.writeInt(0) // current chests, because a run is active
            val run = p.run!!
            out.writeBoolean(true); out.writeUTF(run.id); out.writeUTF(run.heroId)
            val chest = run.target
            out.writeUTF(chest.id); out.writeUTF(chest.quality); out.writeInt(chest.difficulty); out.writeInt(chest.ticket); out.writeInt(chest.appraisals)
            out.writeInt(chest.items.size); chest.items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
            out.writeUTF(chest.theme.name); out.writeInt(0)
            out.writeLong(run.rng); out.writeDouble(run.hp); out.writeDouble(run.maxHp); out.writeInt(run.round); out.writeUTF(run.stage.name)
            out.writeUTF(run.pendingItem); out.writeInt(run.eventRoll); out.writeBoolean(true)
            val fight = run.fight!!
            oldFighter(out, fight.player); oldFighter(out, fight.enemy)
            out.writeLong(fight.rng); out.writeInt(fight.tick); out.writeInt(-1); out.writeInt(fight.log.size); fight.log.forEach(out::writeUTF)
            with(p.economy) {
                out.writeBoolean(initialized); out.writeInt(refreshCharges); out.writeLong(refreshAnchorMs)
                out.writeInt(reliefCharges); out.writeLong(reliefAnchorMs); out.writeInt(reliefClaims); out.writeInt(vouchers)
            }
            out.writeBoolean(false) // no shop snapshot in fixture
            out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF)
            out.writeInt(0) // material pity
        }
        val payload = bytes.toByteArray()
        DataOutputStream(FileOutputStream(file)).use {
            it.writeInt(0x44515131); it.writeInt(6); it.writeInt(payload.size)
            it.writeLong(CRC32().apply { update(payload) }.value); it.write(payload)
        }
    }
    private fun oldFighter(out: DataOutputStream, f: Fighter) {
        out.writeUTF(f.heroId)
        listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist, f.regen, f.manaRegen,
            f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn, f.invulnerable, f.ghost, f.slashIn, f.criticalReady,
            f.broken, f.slowTime, f.slow, f.skinIn, f.flareIn).forEach(out::writeDouble)
        out.writeInt(f.flareLeft); out.writeInt(f.poisons.size)
        f.poisons.forEach { out.writeDouble(it.dps); out.writeDouble(it.remaining); out.writeUTF(it.source); out.writeBoolean(it.dispellable) }
        out.writeDouble(f.skinSlowTime); out.writeDouble(f.physicalShield); out.writeBoolean(f.shieldSources != null)
        f.shieldSources?.let { pool: ShieldPool ->
            out.writeInt(pool.entries.size); pool.entries.forEach {
                out.writeUTF(it.sourceId); out.writeUTF(it.kind.name); out.writeDouble(it.amount)
                out.writeBoolean(it.remainingMs != null); it.remainingMs?.let(out::writeLong)
            }
        }
        with(f.control) { listOf(dead, banished, stunned, hexed, silenced, muted, disarmed).forEach(out::writeBoolean) }
    }
}
