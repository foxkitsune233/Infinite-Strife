package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.equipment.*
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.CRC32

/** New format and independently written actual v8 schema. External execution only. */
class ProfileV9ExtensionTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private val defs = (EquipmentCatalog.all.map { d -> ItemDefinition(d.id, d.name,
        if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT, d.width, d.height,
        d.quality.label, stats = d.stats ?: EquipmentStats()) } + CardCombat.enabledCards.map {
        ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height)
    }).associateBy { it.id }
    private val rules = InventoryRules(defs)
    private val journey = JourneyRules(rules)
    private val target = TargetChest("box", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0)))
    private fun store(file: File) = InventoryStore(file, rules) { InventoryState() }
    private fun battle(): InventoryState {
        val items = listOf(StoredItem("bkb", "black_king_bar", ItemPlace(Container.EQUIPMENT)),
            StoredItem("crit", "daedalus", ItemPlace(Container.EQUIPMENT, 1)))
        var f = journey.fighter(InventoryState(items = items), "juggernaut")
        f = f.copy(equipment = f.equipment.copy(immunityMs = 2000, cooldowns = EquipmentCooldownState(mapOf("bkb" to 11000L), 1),
            activeGroups = mapOf("bkb" to 2000L), started = true, manual = setOf("bkb"), criticalFailures = mapOf("gear:crit:daedalus.crit" to 2)))
        val enemy = journey.fighter(InventoryState(), "phantom_assassin")
        return InventoryState(items = items, phase = InventoryPhase.BATTLE, progress = CampaignProgress(run = JourneyState("run", f.heroId,
            target, 1729, f.hp, f.maxHp, stage = JourneyStage.BATTLE, fight = DuelState(f, enemy, 1729, 20), equipmentCooldowns = f.equipment.cooldowns)))
    }
    @Test fun equipmentAndResumeRngRoundTripWithoutLosingGroupsOrCritCounters() {
        val file = temp.root.resolve("v9.dat")
        val source = battle()
        store(file).save(source)
        val loaded = store(file).load().state
        assertEquals(source, loaded)
        assertEquals(journey.tick(source), journey.tick(loaded))
        DataInputStream(FileInputStream(file)).use { it.readInt(); assertEquals(InventoryStore.VERSION, it.readInt()) }
    }
    @Test fun eventStoresActualRewardSoCatalogExpansionCannotRerollIt() {
        val node = NodeEventInstance("run:event:1", EventKind.RUSTED_WORKSHOP, 12, priceGold = 30,
            outcomes = mapOf("workshop.pay" to EventOutcome("run:event:1", "workshop.pay", goldDelta = -30, grantDefinitionIds = listOf("crown"))))
        val source = InventoryState(runGold = 50, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(run = JourneyState("run", "juggernaut", target, 1729, 500.0, 1000.0,
                stage = JourneyStage.EVENT, nodeEvent = node)))
        val file = temp.root.resolve("node.dat")
        store(file).save(source)
        val loaded = store(file).load().state
        assertEquals(node, loaded.progress.run!!.nodeEvent)
        val result = JourneyEvents.resolve(loaded, rules, "workshop.pay")
        assertTrue(result.error, result.ok)
        // 奖励先落地，读档后仍按冻结结果发放，不因目录扩充重抽。
        assertTrue(result.state.items.isEmpty())
        assertTrue(result.state.progress.run!!.nodeLoot!!.items.any { it.definitionId == "crown" })
        assertEquals(20 + result.state.progress.run!!.nodeLoot!!.goldAwarded, result.state.runGold)
    }
    @Test fun forgedGearBuffCannotReplaceGoodSaveAndBackupKeepsRuntime() {
        val file = temp.root.resolve("backup.dat")
        val source = battle(); val storage = store(file)
        storage.save(source); storage.save(source)
        val run = source.progress.run!!; val fight = run.fight!!
        val bad = source.copy(progress = source.progress.copy(run = run.copy(fight = fight.copy(
            enemy = fight.enemy.copy(equipment = EquipmentBattleState(activeGroups = mapOf("satanic" to 3000L)))))))
        assertThrows(IllegalArgumentException::class.java) { storage.save(bad) }
        RandomAccessFile(file, "rw").use { it.setLength(0) }
        val recovered = storage.load()
        assertTrue(recovered.recoveredFromBackup)
        assertEquals(source, recovered.state)
    }
    @Test fun actualV8LayoutRetainsImmunityButDoesNotInventEquipmentEffects() {
        val file = temp.root.resolve("actual-v8.dat")
        val source = battle()
        writeV8(file, source)
        val loaded = store(file).load().state
        assertEquals(2000L, loaded.progress.run!!.fight!!.player.equipment.immunityMs)
        assertTrue(loaded.progress.run!!.fight!!.player.equipment.items.isEmpty())
        assertEquals(source.items, loaded.items)
        store(file).save(loaded)
        assertEquals(loaded, store(file).load().state)
    }

    /** v8 layout copied as a fixture schema, no v9 codec use and no version-header relabeling. */
    private fun writeV8(file: File, s: InventoryState) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(s.gold); out.writeInt(s.runGold)
            listOf(s.backpackWidth, s.backpackHeight, s.safeWidth, s.safeHeight, s.skillSlots).forEach(out::writeInt)
            out.writeUTF(s.phase.name); out.writeInt(s.items.size)
            s.items.forEach { i -> out.writeUTF(i.instanceId); out.writeUTF(i.definitionId); out.writeUTF(i.place.container.name)
                out.writeInt(i.place.x); out.writeInt(i.place.y); out.writeBoolean(i.place.rotated); out.writeInt(i.star); out.writeBoolean(i.bound); out.writeInt(i.count) }
            val p = s.progress; val r = p.run!!; val fight = r.fight!!
            out.writeLong(p.seed); out.writeInt(p.batch); out.writeInt(p.clears); out.writeBoolean(p.extracted)
            out.writeUTF(p.lastEnding); out.writeLong(p.shopCycle); out.writeLong(p.shopSeen)
            out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeBoolean(true)
            out.writeUTF(r.id); out.writeUTF(r.heroId)
            with(r.target) { out.writeUTF(id); out.writeUTF(quality); out.writeInt(difficulty); out.writeInt(ticket); out.writeInt(appraisals)
                out.writeInt(items.size); items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
                out.writeUTF(theme.name); out.writeInt(0) }
            out.writeLong(r.rng); out.writeDouble(r.hp); out.writeDouble(r.maxHp); out.writeInt(r.round); out.writeUTF(r.stage.name)
            out.writeUTF(r.pendingItem); out.writeInt(r.eventRoll); out.writeBoolean(true)
            fighterV8(out, fight.player); fighterV8(out, fight.enemy)
            out.writeLong(fight.rng); out.writeInt(fight.tick); out.writeInt(-1); out.writeInt(fight.log.size); fight.log.forEach(out::writeUTF)
            with(p.economy) { out.writeBoolean(initialized); out.writeInt(refreshCharges); out.writeLong(refreshAnchorMs)
                out.writeInt(reliefCharges); out.writeLong(reliefAnchorMs); out.writeInt(reliefClaims); out.writeInt(vouchers) }
            out.writeBoolean(false); out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF); out.writeInt(0)
        }
        val payload = bytes.toByteArray()
        DataOutputStream(FileOutputStream(file)).use { out -> out.writeInt(0x44515131); out.writeInt(8); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload) }
    }
    private fun fighterV8(out: DataOutputStream, f: Fighter) {
        out.writeUTF(f.heroId)
        listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist, f.regen, f.manaRegen,
            f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn, f.invulnerable, f.ghost, f.slashIn, f.criticalReady,
            f.broken, f.slowTime, f.slow, f.skinIn, f.flareIn).forEach(out::writeDouble)
        out.writeInt(f.flareLeft); out.writeInt(0); out.writeDouble(f.skinSlowTime)
        out.writeDouble(f.physicalShield); out.writeBoolean(false)
        with(f.control) { listOf(dead, banished, stunned, hexed, silenced, muted, disarmed).forEach(out::writeBoolean) }
        out.writeInt(0); out.writeBoolean(true); out.writeInt(0)
        repeat(4) { out.writeInt(0) } // ready, healed, buffs, crit failures
        out.writeInt(0); out.writeBoolean(false); out.writeLong(f.equipment.immunityMs)
    }
}
