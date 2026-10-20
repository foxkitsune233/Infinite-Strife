package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.combat.ShieldEntry
import com.example.douqiuqiu.combat.ShieldKind
import com.example.douqiuqiu.combat.ShieldPool
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.shop.ShopShelf
import com.example.douqiuqiu.shop.ShopSnapshot
import com.example.douqiuqiu.shop.StoredShopOffer
import com.example.douqiuqiu.status.ActionControlState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.CRC32

/**
 * 外围任务22：v5 存档与升级验收（P01~P12）。
 *
 * 旧版本夹具按对应 schema 独立构造（不是把 v5 版本号改小），
 * 使用 JUnit TemporaryFolder，不碰真实存档。
 */
class ProfileV5MigrationTest {

    @get:Rule val temp = TemporaryFolder()

    private val definitions = mapOf(
        "card" to ItemDefinition("card", "卡", ItemKind.CARD, 1, 1),
        "blade" to ItemDefinition("blade", "刀", ItemKind.EQUIPMENT, 1, 1, price = 50),
        "essence" to ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        "component" to ItemDefinition("component", "组件", ItemKind.EQUIPMENT, 1, 1, price = 100, shopStock = 3),
        "rare" to ItemDefinition("rare", "珍品", ItemKind.EQUIPMENT, 1, 1, price = 1000)
    ) + (0..6).associate { "c$it" to ItemDefinition("c$it", "轮$it", ItemKind.CARD, 1, 1) }
    private val rules = InventoryRules(definitions)
    private fun initial() = InventoryState()

    private fun chest(id: String = "box-1", item: String = "blade") =
        TargetChest(id, "白", 0, 60, listOf(ChestItem(item, 0, 0, 2)), 1)

    // ---------------- 旧版本二进制夹具（按各自 schema 独立构造） ----------------

    private fun writeSave(file: File, version: Int, payload: ByteArray) {
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x44515131); out.writeInt(version); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }

    private fun writeFighter(out: DataOutputStream, f: Fighter, version: Int) {
        out.writeUTF(f.heroId)
        listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist,
            f.regen, f.manaRegen, f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn,
            f.invulnerable, f.ghost, f.slashIn, f.criticalReady, f.broken, f.slowTime, f.slow,
            f.skinIn, f.flareIn).forEach(out::writeDouble)
        out.writeInt(f.flareLeft)
        out.writeInt(f.poisons.size)
        f.poisons.forEach {
            out.writeDouble(it.dps); out.writeDouble(it.remaining); out.writeUTF(it.source); out.writeBoolean(it.dispellable)
        }
        if (version >= 3) out.writeDouble(f.skinSlowTime)
        if (version >= 4) {
            out.writeDouble(f.physicalShield)
            out.writeBoolean(f.shieldSources != null)
            f.shieldSources?.let { pool ->
                out.writeInt(pool.entries.size)
                pool.entries.forEach { entry ->
                    out.writeUTF(entry.sourceId); out.writeUTF(entry.kind.name); out.writeDouble(entry.amount)
                    out.writeBoolean(entry.remainingMs != null); entry.remainingMs?.let(out::writeLong)
                }
            }
            with(f.control) { listOf(dead, banished, stunned, hexed, silenced, muted, disarmed).forEach(out::writeBoolean) }
        }
    }

    private fun writeChest(out: DataOutputStream, c: TargetChest) {
        out.writeUTF(c.id); out.writeUTF(c.quality); out.writeInt(c.difficulty); out.writeInt(c.ticket); out.writeInt(c.appraisals)
        out.writeInt(c.items.size)
        c.items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
    }

    private fun writeProgress(out: DataOutputStream, p: CampaignProgress, version: Int) {
        out.writeLong(p.seed); out.writeInt(p.batch); out.writeInt(p.clears); out.writeBoolean(p.extracted)
        out.writeUTF(p.lastEnding); out.writeLong(p.shopCycle); out.writeLong(p.shopSeen)
        out.writeInt(p.shopStock.size); p.shopStock.forEach { (id, n) -> out.writeUTF(id); out.writeInt(n) }
        if (version >= 3) { out.writeInt(p.manualItems.size); p.manualItems.sorted().forEach(out::writeUTF) }
        out.writeInt(p.chests.size); p.chests.forEach { writeChest(out, it) }
        out.writeBoolean(p.run != null)
        p.run?.let { r ->
            out.writeUTF(r.id); out.writeUTF(r.heroId); writeChest(out, r.target); out.writeLong(r.rng)
            out.writeDouble(r.hp); out.writeDouble(r.maxHp); out.writeInt(r.round); out.writeUTF(r.stage.name)
            out.writeUTF(r.pendingItem); out.writeInt(r.eventRoll); out.writeBoolean(r.fight != null)
            r.fight?.let { f ->
                writeFighter(out, f.player, version); writeFighter(out, f.enemy, version)
                out.writeLong(f.rng); out.writeInt(f.tick)
                out.writeInt(if (f.won == null) -1 else if (f.won == true) 1 else 0)
                out.writeInt(f.log.size); f.log.forEach(out::writeUTF)
            }
        }
        if (version >= 4) with(p.economy) {
            out.writeBoolean(initialized); out.writeInt(refreshCharges); out.writeLong(refreshAnchorMs)
            out.writeInt(reliefCharges); out.writeLong(reliefAnchorMs); out.writeInt(reliefClaims); out.writeInt(vouchers)
        }
        if (version >= 5) {
            out.writeBoolean(p.shop != null)
            p.shop?.let { shelf ->
                out.writeInt(shelf.revision); out.writeLong(shelf.nextSeed)
                out.writeInt(shelf.offers.size)
                shelf.offers.forEach {
                    out.writeUTF(it.definitionId); out.writeUTF(it.shelf.name); out.writeInt(it.price); out.writeInt(it.stock)
                }
                out.writeInt(shelf.purchases.size); shelf.purchases.forEach { (id, n) -> out.writeUTF(id); out.writeInt(n) }
            }
            out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF)
        }
    }

    private fun payload(state: InventoryState, version: Int): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(state.gold); out.writeInt(state.runGold)
            listOf(state.backpackWidth, state.backpackHeight, state.safeWidth, state.safeHeight, state.skillSlots)
                .forEach(out::writeInt)
            out.writeUTF(state.phase.name); out.writeInt(state.items.size)
            state.items.forEach { item ->
                out.writeUTF(item.instanceId); out.writeUTF(item.definitionId)
                out.writeUTF(item.place.container.name); out.writeInt(item.place.x); out.writeInt(item.place.y)
                out.writeBoolean(item.place.rotated); out.writeInt(item.star); out.writeBoolean(item.bound); out.writeInt(item.count)
            }
            if (version >= 2) writeProgress(out, state.progress, version)
        }
    }.toByteArray()

    private fun fighter(
        heroId: String, hp: Double = 800.0, maxHp: Double = 1000.0,
        poisons: List<Poison> = emptyList(), shields: ShieldPool? = null,
        control: ActionControlState = ActionControlState()
    ): Fighter {
        val base = Fighter(heroId, hp, maxHp, 200.0, 400.0, 60.0, 100.0, 1.7, 2.0, .25, 1.0, 1.0, control = control)
            .copy(poisons = poisons)
        return if (shields == null) base else FighterEffects.withShields(base, shields)
    }

    // ---------------- P01~P12 ----------------

    @Test fun p01V5ShopStateSurvivesRecreationWithoutRestockOrReprice() {
        val file = temp.root.resolve("v5.dat")
        val state = InventoryState(
            gold = 880,
            items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE))),
            progress = CampaignProgress(
                seed = 4242, batch = 3, clears = 2, extracted = true, lastEnding = "成功撤离",
                shopCycle = 5, shopSeen = 12_000_000, shopStock = mapOf("component" to 1),
                economy = HomeEconomyState(true, 1, 5_000_000, 0, 9_000_000, 2, 2),
                shop = ShopSnapshot(7, 999_999_999L, listOf(
                    StoredShopOffer("component", ShopShelf.STAPLE, 87, 3),
                    StoredShopOffer("card", ShopShelf.ROTATING, 93, 2)), mapOf("component" to 2)),
                unlockedHeroes = setOf("juggernaut", "abaddon", "skywrath")))
        InventoryStore(file, rules, ::initial).save(state)
        val reloaded = InventoryStore(file, rules, ::initial).load()
        assertFalse(reloaded.recoveredFromBackup)
        assertEquals(state, reloaded.state)
        val shelf = reloaded.state.progress.shop!!
        assertEquals(7, shelf.revision)
        assertEquals(87, shelf.offers.single { it.definitionId == "component" }.price)
        assertEquals(1, reloaded.state.progress.shopStock["component"])
        assertEquals(2, shelf.purchases["component"])
        assertEquals(setOf("juggernaut", "abaddon", "skywrath"), reloaded.state.progress.unlockedHeroes)
    }

    @Test fun p02V4EconomyCountersSurviveAndOnlyNewFieldsDefault() {
        val file = temp.root.resolve("v4.dat")
        val state = InventoryState(
            gold = 640, runGold = 12,
            items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE), bound = true)),
            progress = CampaignProgress(
                seed = 77, batch = 2, clears = 4, extracted = true,
                shopCycle = 3, shopSeen = 20_000_000, shopStock = mapOf("component" to 2),
                economy = HomeEconomyState(true, 1, 4_000_000, 0, 8_000_000, 3, 2)))
        writeSave(file, 4, payload(state, 4))
        val loaded = InventoryStore(file, rules, ::initial).load().state
        assertEquals(640, loaded.gold)
        assertEquals(12, loaded.runGold)
        assertEquals(listOf("i1"), loaded.items.map { it.instanceId })
        assertTrue(loaded.items.single().bound)
        val e = loaded.progress.economy
        assertTrue(e.initialized)
        assertEquals(1, e.refreshCharges)
        assertEquals(4_000_000L, e.refreshAnchorMs)
        assertEquals(0, e.reliefCharges)
        assertEquals(8_000_000L, e.reliefAnchorMs)
        assertEquals(3, e.reliefClaims)
        assertEquals(2, e.vouchers)
        assertEquals(3L, loaded.progress.shopCycle)
        assertEquals(20_000_000L, loaded.progress.shopSeen)
        assertEquals(mapOf("component" to 2), loaded.progress.shopStock)
        assertNull(loaded.progress.shop)
        assertEquals(setOf("juggernaut", "abaddon"), loaded.progress.unlockedHeroes)
    }

    @Test fun p03FirstRecoverOnV4GeneratesShelfOnceAndKeepsSoldOutStock() {
        val file = temp.root.resolve("v4b.dat")
        val state = InventoryState(gold = 500, progress = CampaignProgress(
            shopStock = mapOf("component" to 0),
            economy = HomeEconomyState(true, 3, 1_000, 2, 1_000, 0, 0)))
        writeSave(file, 4, payload(state, 4))
        val loaded = InventoryStore(file, rules, ::initial).load().state
        val economy = HomeEconomyRules(rules)
        val first = economy.recover(loaded, 1_000)
        assertNotNull(first.progress.shop)
        assertEquals(0, first.progress.shopStock["component"])
        val shelf = first.progress.shop!!
        val items = first.items
        val second = economy.recover(first, 1_000)
        assertEquals(shelf, second.progress.shop)
        assertEquals(first.progress.shopStock, second.progress.shopStock)
        assertEquals(items, second.items)
    }

    @Test fun p04LegacyV1ToV3KeepProgressAndRewriteAsV5() {
        for (version in listOf(1, 2, 3)) {
            val file = temp.root.resolve("legacy$version.dat")
            val state = InventoryState(
                gold = 333,
                items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE))),
                progress = CampaignProgress(seed = 55, batch = 1, clears = 2, extracted = true,
                    lastEnding = "legacy", manualItems = setOf("i1")))
            writeSave(file, version, payload(state, version))
            val loaded = InventoryStore(file, rules, ::initial).load().state
            assertEquals("v$version gold", 333, loaded.gold)
            assertEquals("v$version items", listOf("i1"), loaded.items.map { it.instanceId })
            if (version >= 2) {
                assertEquals("v$version clears", 2, loaded.progress.clears)
                assertTrue("v$version extracted", loaded.progress.extracted)
                assertEquals("v$version seed", 55L, loaded.progress.seed)
                if (version >= 3) assertEquals("v$version manual", setOf("i1"), loaded.progress.manualItems)
            } else {
                assertEquals("v$version clears", 0, loaded.progress.clears)
                assertEquals("v$version seed", 1729L, loaded.progress.seed)
            }
            assertNull("v$version shop", loaded.progress.shop)
            assertEquals("v$version heroes", setOf("juggernaut", "abaddon"), loaded.progress.unlockedHeroes)
            assertEquals("v$version economy", HomeEconomyState(), loaded.progress.economy)
            val store = InventoryStore(file, rules, ::initial)
            store.save(loaded)
            assertEquals("v$version rewrite", loaded, store.load().state)
        }
    }

    @Test fun p05V4BattleSnapshotKeepsPoisonsShieldsAndControl() {
        val file = temp.root.resolve("v4battle.dat")
        val player = fighter(
            "juggernaut", hp = 620.0,
            poisons = listOf(Poison(12.5, 3.5, "viper", true), Poison(4.0, 1.25, "necrophos", false)),
            shields = ShieldPool(listOf(
                ShieldEntry("src-a", ShieldKind.UNIVERSAL, 120.0, 5000L),
                ShieldEntry("src-b", ShieldKind.PHYSICAL, 80.0, null),
                ShieldEntry("src-c", ShieldKind.MAGICAL, 40.0, 2500L))),
            control = ActionControlState(dead = false, banished = false, stunned = true,
                hexed = false, silenced = true, muted = false, disarmed = false))
        val fight = DuelState(player, fighter("viper"), 12345L, tick = 37, won = null, log = listOf("log-1", "log-2"))
        val run = JourneyState("run-1", "juggernaut", chest(), 88888L, 620.0, 1000.0, 2,
            JourneyStage.BATTLE, fight, "blade", 42)
        val state = InventoryState(gold = 100, phase = InventoryPhase.BATTLE,
            progress = CampaignProgress(seed = 88888, batch = 1, run = run,
                economy = HomeEconomyState(true, 2, 1000, 1, 2000, 0, 0)))
        writeSave(file, 4, payload(state, 4))
        val loaded = InventoryStore(file, rules, ::initial).load().state
        val f = loaded.progress.run!!.fight!!
        assertEquals(620.0, f.player.hp, 1e-9)
        assertEquals(1000.0, f.player.maxHp, 1e-9)
        assertEquals(37, f.tick)
        assertEquals(12345L, f.rng)
        assertEquals(listOf("log-1", "log-2"), f.log)
        assertEquals(2, f.player.poisons.size)
        assertEquals(12.5, f.player.poisons[0].dps, 1e-9)
        assertEquals(3.5, f.player.poisons[0].remaining, 1e-9)
        assertEquals("viper", f.player.poisons[0].source)
        assertFalse(f.player.poisons[1].dispellable)
        val pool = f.player.shieldSources!!
        assertEquals(3, pool.entries.size)
        assertEquals(120.0, f.player.shield, 1e-9)
        assertEquals(80.0, f.player.physicalShield, 1e-9)
        assertEquals(40.0, f.player.magicShield, 1e-9)
        assertTrue(f.player.control.stunned)
        assertTrue(f.player.control.silenced)
        assertFalse(f.player.control.dead)
        assertFalse(JourneyRules(rules).startBattle(loaded).ok)
    }

    @Test fun p06V5RunStageSurvivesRestartAndRewardConvertsOnce() {
        val journey = JourneyRules(rules)
        // 奖励待领取
        val rewardFile = temp.root.resolve("v5reward.dat")
        val rewardRun = JourneyState("run-9", "juggernaut", chest("box-9"), 424242L, 500.0, 1000.0, 1,
            JourneyStage.REWARD, null, "blade", 7)
        val rewardState = InventoryState(gold = 200, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(seed = 424242, batch = 1, run = rewardRun,
                economy = HomeEconomyState(true, 3, 1000, 2, 2000, 0, 0)))
        writeSave(rewardFile, 5, payload(rewardState, 5))
        val loaded = InventoryStore(rewardFile, rules, ::initial).load().state
        assertEquals(JourneyStage.REWARD, loaded.progress.run!!.stage)
        assertEquals("box-9", loaded.progress.run!!.target.id)
        assertEquals("blade", loaded.progress.run!!.pendingItem)
        val gold = LootFlowTestSupport.finishReward(journey, loaded)
        assertTrue(gold.error ?: "ok", gold.ok)
        assertEquals(20, gold.state.runGold)
        assertFalse(journey.reward(gold.state, RewardChoice.GOLD).ok)
        val settled = rules.settle(gold.state, true)
        assertEquals(220, settled.state.gold)
        assertFalse(rules.settle(settled.state, true).ok)
        // 第三战休息节点
        val restFile = temp.root.resolve("v5rest.dat")
        val restRun = JourneyState("run-8", "juggernaut", chest("box-8"), 515151L, 500.0, 1000.0, 3,
            JourneyStage.REST, null, "blade", 3)
        val restState = InventoryState(gold = 200, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(seed = 515151, batch = 1, run = restRun,
                economy = HomeEconomyState(true, 3, 1000, 2, 2000, 0, 0)))
        writeSave(restFile, 5, payload(restState, 5))
        val restLoaded = InventoryStore(restFile, rules, ::initial).load().state
        assertEquals(JourneyStage.REST, restLoaded.progress.run!!.stage)
        assertEquals(3, restLoaded.progress.run!!.round)
        val extracted = journey.extract(restLoaded)
        assertTrue(extracted.error ?: "ok", extracted.ok)
        assertTrue(extracted.state.progress.extracted)
        assertNull(extracted.state.progress.run)
        assertFalse(journey.extract(extracted.state).ok)
    }

    @Test fun p07PermanentGrowthSurvivesDeathAfterRestart() {
        val file = temp.root.resolve("v5growth.dat")
        val state = InventoryState(
            gold = 200, skillSlots = 6, backpackWidth = 5, backpackHeight = 3,
            safeWidth = 3, safeHeight = 2,
            items = listOf(
                StoredItem("keep", "blade", ItemPlace(Container.WAREHOUSE)),
                StoredItem("risk", "blade", ItemPlace(Container.BACKPACK, 0, 0)),
                StoredItem("safe", "essence", ItemPlace(Container.SAFE, 0, 0))),
            progress = CampaignProgress(batch = 1, unlockedHeroes = setOf("juggernaut", "abaddon", "viper")))
        InventoryStore(file, rules, ::initial).save(state)
        val loaded = InventoryStore(file, rules, ::initial).load().state
        assertEquals(state, loaded)
        val journey = JourneyRules(rules)
        val batch = journey.ensureBatch(loaded)
        val entry = journey.enter(batch, batch.progress.chests.first().id, "viper")
        assertTrue(entry.error ?: "entry", entry.ok)
        assertNotNull(entry.state.progress.run)
        val death = journey.abandon(entry.state)
        assertTrue(death.error ?: "death", death.ok)
        val dead = death.state
        assertEquals(6, dead.skillSlots)
        assertEquals(5 to 3, dead.backpackWidth to dead.backpackHeight)
        assertEquals(3 to 2, dead.safeWidth to dead.safeHeight)
        assertTrue(dead.progress.unlockedHeroes.contains("viper"))
        assertEquals(listOf("keep", "safe"), dead.items.map { it.instanceId }.sorted())
    }

    @Test fun p08CorruptPrimaryRecoversPreviousValidGeneration() {
        val file = temp.root.resolve("v5bak.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(InventoryState(gold = 111, progress = CampaignProgress(batch = 1)))
        store.save(InventoryState(gold = 222, progress = CampaignProgress(batch = 2)))
        file.writeText("broken")
        val recovered = store.load()
        assertTrue(recovered.recoveredFromBackup)
        assertEquals(111, recovered.state.gold)
    }

    @Test fun p09BothBrokenRaiseAndPreserveFiles() {
        val file = temp.root.resolve("v5both.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(InventoryState(gold = 321, progress = CampaignProgress(batch = 1)))
        store.save(InventoryState(gold = 654, progress = CampaignProgress(batch = 2)))
        val backup = File(file.path + ".bak")
        file.writeText("bad-primary")
        backup.writeText("bad-backup")
        assertThrows(IOException::class.java) { store.load() }
        assertEquals("bad-primary", file.readText())
        assertEquals("bad-backup", backup.readText())
    }

    @Test fun p10NewerVersionThrowsInsteadOfRollingBack() {
        val file = temp.root.resolve("v5new.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(InventoryState(gold = 321, progress = CampaignProgress(batch = 1)))
        store.save(InventoryState(gold = 654, progress = CampaignProgress(batch = 2)))
        java.io.RandomAccessFile(file, "rw").use { it.seek(4); it.writeInt(999) }
        assertThrows(UnsupportedSaveVersion::class.java) { store.load() }
        // 备份是更高版本时同样抛出，不偷偷回滚到备份
        val file2 = temp.root.resolve("v5new2.dat")
        val store2 = InventoryStore(file2, rules, ::initial)
        store2.save(InventoryState(gold = 1, progress = CampaignProgress(batch = 1)))
        store2.save(InventoryState(gold = 2, progress = CampaignProgress(batch = 2)))
        java.io.RandomAccessFile(File(file2.path + ".bak"), "rw").use { it.seek(4); it.writeInt(999) }
        java.io.RandomAccessFile(file2, "rw").use { it.seek(20); it.writeByte(0x7F) }
        assertThrows(UnsupportedSaveVersion::class.java) { store2.load() }
    }

    @Test fun p11InvalidShelfAndHeroUnlocksCannotReplaceValidSave() {
        val file = temp.root.resolve("v5invalid.dat")
        val store = InventoryStore(file, rules, ::initial)
        val valid = InventoryState(gold = 400, progress = CampaignProgress(batch = 1))
        store.save(valid)
        fun snapshot(vararg offers: StoredShopOffer) = ShopSnapshot(1, 12345L, offers.toList(), emptyMap())
        fun shopState(shop: ShopSnapshot) = valid.copy(progress = valid.progress.copy(shop = shop))
        val badShelves = listOf(
            "重复ID" to snapshot(StoredShopOffer("component", ShopShelf.STAPLE, 90, 3),
                StoredShopOffer("component", ShopShelf.ROTATING, 90, 3)),
            "超6轮换" to snapshot(*(0..6).map { StoredShopOffer("c$it", ShopShelf.ROTATING, 90, 3) }.toTypedArray()),
            "多珍品" to snapshot(StoredShopOffer("rare", ShopShelf.RARE, 900, 1),
                StoredShopOffer("component", ShopShelf.RARE, 90, 3)),
            "未知商品" to snapshot(StoredShopOffer("ghost", ShopShelf.STAPLE, 90, 3)),
            "负价" to snapshot(StoredShopOffer("component", ShopShelf.STAPLE, -5, 3))
        )
        for ((label, shop) in badShelves) {
            assertThrows(label, IllegalArgumentException::class.java) { store.save(shopState(shop)) }
        }
        assertThrows("未知英雄", IllegalArgumentException::class.java) {
            store.save(valid.copy(progress = valid.progress.copy(
                unlockedHeroes = setOf("juggernaut", "abaddon", "ghost"))))
        }
        assertThrows("缺初始英雄", IllegalArgumentException::class.java) {
            store.save(valid.copy(progress = valid.progress.copy(unlockedHeroes = setOf("abaddon"))))
        }
        assertEquals(400, store.load().state.gold)
    }

    @Test fun p12RecreatedStoreKeepsEconomyGrowthAndGrantsNothingExtra() {
        val file = temp.root.resolve("v5reopen.dat")
        val state = InventoryState(
            gold = 700, skillSlots = 5, backpackWidth = 4, backpackHeight = 3,
            items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE))),
            progress = CampaignProgress(batch = 2,
                economy = HomeEconomyState(true, 1, 5_000_000, 0, 5_000_000, 2, 2),
                unlockedHeroes = setOf("juggernaut", "abaddon", "necrophos")))
        InventoryStore(file, rules, ::initial).save(state)
        val reopened = InventoryStore(file, rules, ::initial).load().state
        assertEquals(state, reopened)
        val economy = HomeEconomyRules(rules)
        val again = economy.recover(reopened, 5_000_000)
        assertEquals(reopened.items, again.items)
        assertEquals(reopened.gold, again.gold)
        assertEquals(1, again.progress.economy.refreshCharges)
        assertEquals(2, again.progress.economy.vouchers)
        assertEquals(setOf("juggernaut", "abaddon", "necrophos"), again.progress.unlockedHeroes)
        assertEquals(5, again.skillSlots)
        assertEquals(4 to 3, again.backpackWidth to again.backpackHeight)
        // 再次 recover 不重复生成货架
        val third = economy.recover(again, 5_000_000)
        assertEquals(again.progress.shop, third.progress.shop)
    }
}
