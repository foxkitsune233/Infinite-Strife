package com.example.douqiuqiu.status

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.combat.PoisonCombat
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.CRC32

/**
 * H03 新毒核心的战斗接入：施加门禁、引擎推进时序、剧毒产毒、v8 存档往返、
 * v7 旧档读取与篡改拒绝。毒数值仍以 docs/rules/poison.md 审阅稿为参数来源，
 * 本测试只验证机制，不固定任何待审数值。
 */
class PoisonCoreBattleTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private val definitions = CardCombat.enabledCards.associate { it.id to ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height) }
    private val rules = InventoryRules(definitions)
    private val journey = JourneyRules(rules)
    private fun store(file: File) = InventoryStore(file, rules) { InventoryState() }
    private fun idle(f: Fighter) = f.copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun advance(s: DuelState, count: Int): DuelState { var current = s; repeat(count) { current = DuelEngine.step(current) }; return current }

    private fun dummy(): Fighter = idle(journey.fighter(InventoryState(), "juggernaut"))
    private fun patient(): Fighter = idle(journey.fighter(InventoryState(), "phantom_assassin"))
        .copy(hp = 500.0, maxHp = 500.0, magicResist = 0.0)

    @Test fun normalPoisonLayerCarriesFirstTickDelayAndThreeTicks() {
        val target = patient()
        val applied = PoisonCombat.apply(target, sourceUnit = 1, sourceId = "card:P1", serial = 7, nowTick = 0,
            virulent = false, layers = 1, damage = 12.0)
        val layer = applied.poisonPool!!.normal.single()
        assertEquals("p:1:7:0", layer.instanceId)
        assertEquals(PoisonKind.NORMAL, layer.kind)
        assertEquals("card:P1", layer.sourceId)
        assertEquals("1", layer.casterUnitId)
        assertEquals(12.0, layer.damagePerTick, .00001)
        assertEquals(3, layer.ticksRemaining)
        assertEquals(1000L, layer.nextTickAtMs)
        assertTrue(applied.poisonPool!!.virulent.isEmpty())
        assertNull(target.poisonPool)
    }

    @Test fun immunityInvulnerabilityAndDeathBlockApplicationButPiercePasses() {
        val base = patient()
        val immune = base.copy(equipment = EquipmentBattleState(immunityMs = 5000))
        assertNull(PoisonCombat.apply(immune, 1, "card:P1", 0, 0, false, 1, 10.0).poisonPool)
        assertNull(PoisonCombat.apply(base.copy(invulnerable = 1.0), 1, "card:P1", 0, 0, false, 1, 10.0).poisonPool)
        assertNull(PoisonCombat.apply(base.copy(hp = 0.0), 1, "card:P1", 0, 0, false, 1, 10.0).poisonPool)
        assertEquals(1, PoisonCombat.apply(immune, 1, "card:P1", 0, 0, false, 1, 10.0, pierces = true)
            .poisonPool!!.normal.size)
        assertTrue(PoisonCombat.apply(immune, 1, "card:P1", 0, 0, false, 1, 10.0, pierces = true)
            .poisonPool!!.normal.single().piercesDebuffImmunity)
    }

    @Test fun serialKeepsInstanceIdsUniqueAcrossApplies() {
        var f = patient()
        repeat(3) { serial -> f = PoisonCombat.apply(f, 1, "card:P1", serial, 0, false, 2, 5.0) }
        assertEquals(6, f.poisonPool!!.normal.size)
        assertEquals(6, f.poisonPool!!.normal.map { it.instanceId }.distinct().size)
    }

    @Test fun engineTicksNormalPoisonAtOneSecondAndStopsAfterThreeHits() {
        val poisoned = PoisonCombat.apply(patient(), 1, "card:P1", 0, 0, false, 1, 12.0)
        var s = DuelState(poisoned, dummy(), 1729, 0)
        s = advance(s, 9)
        assertEquals(500.0, s.player.hp, .00001)
        s = advance(s, 1)
        assertEquals(488.0, s.player.hp, .00001)
        s = advance(s, 9)
        assertEquals(488.0, s.player.hp, .00001)
        s = advance(s, 1)
        assertEquals(476.0, s.player.hp, .00001)
        s = advance(s, 9)
        assertEquals(476.0, s.player.hp, .00001)
        s = advance(s, 1)
        assertEquals(464.0, s.player.hp, .00001)
        s = advance(s, 10)
        assertEquals(464.0, s.player.hp, .00001)
        assertNull(s.player.poisonPool)
    }

    @Test fun virulentSpawnsNormalPoisonOnScheduleWithoutDirectDamage() {
        val poisoned = PoisonCombat.apply(patient(), 1, "card:D5", 0, 0, virulent = true, layers = 1, damage = 8.0)
        var s = DuelState(poisoned, dummy(), 1729, 0)
        s = advance(s, 10)
        assertEquals(500.0, s.player.hp, .00001)
        val pool = s.player.poisonPool!!
        assertEquals(1, pool.virulent.size)
        assertEquals(1, pool.normal.size)
        assertEquals("p:1:0:0~1000", pool.normal.single().instanceId)
        s = advance(s, 10)
        assertEquals(492.0, s.player.hp, .00001)
        s = advance(s, 10)
        assertEquals(476.0, s.player.hp, .00001)
        s = advance(s, 10)
        assertEquals(452.0, s.player.hp, .00001)
        val late = s.player.poisonPool!!
        assertEquals(1, late.virulent.size)
        assertEquals(3, late.normal.size)
    }

    @Test fun d5ConvertsOwnPoisonTickDamageIntoHealing() {
        val holder = idle(journey.fighter(InventoryState(items = listOf(StoredItem("d5", "D5", ItemPlace(Container.SKILLS, 0)))), "phantom_assassin"))
            .copy(hp = 400.0, maxHp = 500.0, magicResist = 0.0)
        val victim = dummy().copy(magicResist = 0.0)
        val poisoned = PoisonCombat.apply(victim, sourceUnit = 0, sourceId = "D5", serial = 0, nowTick = 0,
            virulent = false, layers = 1, damage = 12.0)
        var s = DuelState(holder, poisoned, 1729, 0)
        s = advance(s, 10)
        assertEquals(1016.0, s.enemy.hp, .00001)
        assertEquals(400.6, s.player.hp, .00001)
        s = advance(s, 10)
        assertEquals(1004.0, s.enemy.hp, .00001)
        assertEquals(401.2, s.player.hp, .00001)
    }

    @Test fun poisonRuntimeSurvivesV8SaveRoundTrip() {
        var player = journey.fighter(InventoryState(items = listOf(StoredItem("one", "D2", ItemPlace(Container.SKILLS, 0)))), "phantom_assassin")
            .copy(hp = 500.0)
        player = player.copy(cardState = player.cardState.copy(started = true))
        player = PoisonCombat.apply(player, 1, "card:P1", 3, 20, false, 1, 12.0)
        player = PoisonCombat.apply(player, 1, "card:D5", 4, 20, true, 2, 8.0)
        player = player.copy(poisonSerial = 5, equipment = EquipmentBattleState(immunityMs = 2500))
        val enemy = journey.fighter(InventoryState(), "juggernaut")
        val fight = DuelState(player, enemy, 12345, 20)
        val state = InventoryState(phase = InventoryPhase.BATTLE, progress = CampaignProgress(run = JourneyState("run",
            "phantom_assassin", TargetChest("old-box", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0, 1))), fight.rng,
            player.hp, player.maxHp, stage = JourneyStage.BATTLE, fight = fight)))
        val file = temp.root.resolve("v8.dat")
        store(file).save(state)
        DataInputStream(FileInputStream(file)).use { it.readInt(); assertEquals(InventoryStore.VERSION, it.readInt()) }
        val loaded = store(file).load().state
        assertEquals(state, loaded)
        val pool = loaded.progress.run!!.fight!!.player.poisonPool!!
        assertEquals(1, pool.normal.size)
        assertEquals(2, pool.virulent.size)
        assertEquals(3000L, pool.normal.single().nextTickAtMs)
        assertEquals(5, loaded.progress.run!!.fight!!.player.poisonSerial)
        assertEquals(2500L, loaded.progress.run!!.fight!!.player.equipment.immunityMs)
    }

    @Test fun v7SaveLoadsWithoutPoisonStateAndKeepsLegacyPoison() {
        var player = Fighter(heroId = "phantom_assassin", hp = 500.0, maxHp = 500.0, mana = 100.0, maxMana = 100.0,
            attack = 40.0, speed = 100.0, bat = 1.7, armor = 5.0, magicResist = .25, regen = .5, manaRegen = .1,
            attackIn = 10.0, ultIn = 10.0, poisons = listOf(Poison(12.0, 2.0, "curse")))
        val enemy = journey.fighter(InventoryState(), "juggernaut")
        val fight = DuelState(player, enemy, 12345, 20)
        val state = InventoryState(phase = InventoryPhase.BATTLE, progress = CampaignProgress(run = JourneyState("run",
            "phantom_assassin", TargetChest("old-box", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0, 1))), fight.rng,
            player.hp, player.maxHp, stage = JourneyStage.BATTLE, fight = fight)))
        val file = temp.root.resolve("legacy7.dat")
        writeV7(file, state)
        val loaded = store(file).load().state
        val loadedPlayer = loaded.progress.run!!.fight!!.player
        assertEquals(listOf(Poison(12.0, 2.0, "curse")), loadedPlayer.poisons)
        assertNull(loadedPlayer.poisonPool)
        assertEquals(0, loadedPlayer.poisonSerial)
        assertEquals(EquipmentBattleState(), loadedPlayer.equipment)
        store(file).save(loaded)
        assertEquals(loaded, store(file).load().state)
    }

    @Test fun tamperedPoisonRuntimeIsRejectedWithoutOverwritingValidSave() {
        val file = temp.root.resolve("tamper.dat")
        val storage = store(file)
        var player = PoisonCombat.apply(patient(), 1, "card:P1", 3, 20, false, 1, 12.0)
        player = player.copy(equipment = EquipmentBattleState(immunityMs = 2500))
        val fight = DuelState(player, journey.fighter(InventoryState(), "juggernaut"), 12345, 20)
        val good = InventoryState(phase = InventoryPhase.BATTLE, progress = CampaignProgress(run = JourneyState("run",
            "phantom_assassin", TargetChest("old-box", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0, 1))), fight.rng,
            player.hp, player.maxHp, stage = JourneyStage.BATTLE, fight = fight)))
        storage.save(good)
        fun corrupt(mutate: (Fighter) -> Fighter): InventoryState {
            val run = good.progress.run!!; val old = run.fight!!
            return good.copy(progress = good.progress.copy(run = run.copy(fight = old.copy(player = mutate(old.player)))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            storage.save(corrupt { it.copy(poisonPool = it.poisonPool!!.copy(normal = listOf(it.poisonPool!!.normal.single().copy(ticksRemaining = 5)))) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            storage.save(corrupt { it.copy(equipment = EquipmentBattleState(immunityMs = -1)) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            storage.save(corrupt { it.copy(poisonPool = it.poisonPool!!.copy(normal = listOf(it.poisonPool!!.normal.single().copy(nextTickAtMs = 999999)))) })
        }
        assertEquals(good, storage.load().state)
    }

    /** Actual v7 layout: v6 body plus the card-state block after each fighter; no poison bytes at all. */
    private fun writeV7(file: File, s: InventoryState) {
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
            out.writeInt(0); out.writeInt(0); out.writeInt(0)
            val run = p.run!!
            out.writeBoolean(true); out.writeUTF(run.id); out.writeUTF(run.heroId)
            val chest = run.target
            out.writeUTF(chest.id); out.writeUTF(chest.quality); out.writeInt(chest.difficulty); out.writeInt(chest.ticket); out.writeInt(chest.appraisals)
            out.writeInt(chest.items.size); chest.items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
            out.writeUTF(chest.theme.name); out.writeInt(0)
            out.writeLong(run.rng); out.writeDouble(run.hp); out.writeDouble(run.maxHp); out.writeInt(run.round); out.writeUTF(run.stage.name)
            out.writeUTF(run.pendingItem); out.writeInt(run.eventRoll); out.writeBoolean(true)
            val fight = run.fight!!
            oldFighter(out, fight.player); cardStateV7(out)
            oldFighter(out, fight.enemy); cardStateV7(out)
            out.writeLong(fight.rng); out.writeInt(fight.tick); out.writeInt(-1); out.writeInt(fight.log.size); fight.log.forEach(out::writeUTF)
            with(p.economy) {
                out.writeBoolean(initialized); out.writeInt(refreshCharges); out.writeLong(refreshAnchorMs)
                out.writeInt(reliefCharges); out.writeLong(reliefAnchorMs); out.writeInt(reliefClaims); out.writeInt(vouchers)
            }
            out.writeBoolean(false)
            out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF)
            out.writeInt(0)
        }
        val payload = bytes.toByteArray()
        DataOutputStream(FileOutputStream(file)).use {
            it.writeInt(0x44515131); it.writeInt(7); it.writeInt(payload.size)
            it.writeLong(CRC32().apply { update(payload) }.value); it.write(payload)
        }
    }
    private fun cardStateV7(out: DataOutputStream) {
        out.writeInt(0); out.writeBoolean(true); out.writeInt(0)
        out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeInt(0)
    }
    private fun oldFighter(out: DataOutputStream, f: Fighter) {
        out.writeUTF(f.heroId)
        listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist, f.regen, f.manaRegen,
            f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn, f.invulnerable, f.ghost, f.slashIn, f.criticalReady,
            f.broken, f.slowTime, f.slow, f.skinIn, f.flareIn).forEach(out::writeDouble)
        out.writeInt(f.flareLeft); out.writeInt(f.poisons.size)
        f.poisons.forEach { out.writeDouble(it.dps); out.writeDouble(it.remaining); out.writeUTF(it.source); out.writeBoolean(it.dispellable) }
        out.writeDouble(f.skinSlowTime)
        out.writeDouble(f.physicalShield); out.writeBoolean(f.shieldSources != null)
        f.shieldSources?.let { pool ->
            out.writeInt(pool.entries.size); pool.entries.forEach {
                out.writeUTF(it.sourceId); out.writeUTF(it.kind.name); out.writeDouble(it.amount)
                out.writeBoolean(it.remainingMs != null); it.remainingMs?.let(out::writeLong)
            }
        }
        with(f.control) { listOf(dead, banished, stunned, hexed, silenced, muted, disarmed).forEach(out::writeBoolean) }
    }
}
