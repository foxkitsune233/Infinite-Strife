package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.loot.MaterialPityCounter
import com.example.douqiuqiu.loot.MaterialPityState
import com.example.douqiuqiu.shop.ShopShelf
import com.example.douqiuqiu.shop.ShopSnapshot
import com.example.douqiuqiu.shop.StoredShopOffer
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
 * 外围任务24：v6 主题/保底存档验收（V01~V10）。
 *
 * 旧版本夹具按对应 schema 独立构造（不是把 v6 版本号改小），
 * 使用 JUnit TemporaryFolder，不碰真实存档。
 * 与任务22的 ProfileV5MigrationTest 文件不冲突：本类只验新增 v6 字段。
 */
class ProfileV6MigrationTest {

    @get:Rule val temp = TemporaryFolder()

    private val definitions = mapOf(
        "essence" to ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        "backpack_fabric" to ItemDefinition("backpack_fabric", "织件", ItemKind.MATERIAL, 1, 2, boundOnAcquire = true),
        "skill_shard" to ItemDefinition("skill_shard", "刻片", ItemKind.MATERIAL, 1, 1, boundOnAcquire = true),
        "safe_lock" to ItemDefinition("safe_lock", "锁芯", ItemKind.MATERIAL, 3, 2, boundOnAcquire = true),
        "safe_frame" to ItemDefinition("safe_frame", "框架", ItemKind.MATERIAL, 3, 3, boundOnAcquire = true),
        "blade" to ItemDefinition("blade", "刀", ItemKind.EQUIPMENT, 1, 1, price = 50),
        "cardA" to ItemDefinition("cardA", "技能卡", ItemKind.CARD, 1, 1, price = 100),
        "potion" to ItemDefinition("potion", "药水", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, price = 30),
        "component" to ItemDefinition("component", "组件", ItemKind.EQUIPMENT, 1, 1, price = 100, shopStock = 3)
    )
    private val rules = InventoryRules(definitions)
    private val journey = JourneyRules(rules)
    private fun initial() = InventoryState()

    private fun pity(vararg pairs: Pair<String, MaterialPityCounter>) = MaterialPityState(mapOf(*pairs))

    private fun chest(
        id: String, theme: ChestTheme, difficulty: Int, ticket: Int,
        items: List<ChestItem>, appraisals: Int = 0, guaranteed: Set<String> = emptySet()
    ) = TargetChest(id, "白", difficulty, ticket, items, appraisals, theme, guaranteed)

    // ---------------- 旧版本二进制夹具（按各自 schema 独立构造） ----------------

    private fun writeSave(file: File, version: Int, payload: ByteArray) {
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x44515131); out.writeInt(version); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }

    private fun writeChest(out: DataOutputStream, c: TargetChest, version: Int, themeOverride: String? = null) {
        out.writeUTF(c.id); out.writeUTF(c.quality); out.writeInt(c.difficulty); out.writeInt(c.ticket); out.writeInt(c.appraisals)
        out.writeInt(c.items.size)
        c.items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
        if (version >= 6) {
            out.writeUTF(themeOverride ?: c.theme.name)
            out.writeInt(c.guaranteedCategories.size); c.guaranteedCategories.sorted().forEach(out::writeUTF)
        }
    }

    private fun writeProgress(out: DataOutputStream, p: CampaignProgress, version: Int, themeOverride: String? = null) {
        out.writeLong(p.seed); out.writeInt(p.batch); out.writeInt(p.clears); out.writeBoolean(p.extracted)
        out.writeUTF(p.lastEnding); out.writeLong(p.shopCycle); out.writeLong(p.shopSeen)
        out.writeInt(p.shopStock.size); p.shopStock.forEach { (id, n) -> out.writeUTF(id); out.writeInt(n) }
        if (version >= 3) { out.writeInt(p.manualItems.size); p.manualItems.sorted().forEach(out::writeUTF) }
        out.writeInt(p.chests.size); p.chests.forEach { writeChest(out, it, version, themeOverride) }
        out.writeBoolean(p.run != null)
        p.run?.let { r ->
            out.writeUTF(r.id); out.writeUTF(r.heroId); writeChest(out, r.target, version, themeOverride); out.writeLong(r.rng)
            out.writeDouble(r.hp); out.writeDouble(r.maxHp); out.writeInt(r.round); out.writeUTF(r.stage.name)
            out.writeUTF(r.pendingItem); out.writeInt(r.eventRoll); out.writeBoolean(r.fight != null)
            r.fight?.let { f ->
                out.writeUTF(f.player.heroId); out.writeUTF(f.enemy.heroId)
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
                out.writeInt(shelf.revision); out.writeLong(shelf.nextSeed); out.writeInt(shelf.offers.size)
                shelf.offers.forEach {
                    out.writeUTF(it.definitionId); out.writeUTF(it.shelf.name); out.writeInt(it.price); out.writeInt(it.stock)
                }
                out.writeInt(shelf.purchases.size); shelf.purchases.forEach { (id, n) -> out.writeUTF(id); out.writeInt(n) }
            }
            out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF)
        }
        if (version >= 6) {
            out.writeInt(p.materialPity.counters.size)
            p.materialPity.counters.toSortedMap().forEach { (category, counter) ->
                out.writeUTF(category); out.writeInt(counter.misses); out.writeBoolean(counter.pending)
            }
        }
    }

    private fun payload(state: InventoryState, version: Int, themeOverride: String? = null): ByteArray =
        ByteArrayOutputStream().also { buffer ->
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
                if (version >= 2) writeProgress(out, state.progress, version, themeOverride)
            }
        }.toByteArray()

    // ---------------- V01~V10 ----------------

    @Test fun v01V6MixedThemesAndPityCountersSurviveRecreation() {
        val file = temp.root.resolve("v6mixed.dat")
        val boxes = listOf(
            chest("box-1", ChestTheme.SUPPLIES, 0, 60, listOf(ChestItem("blade", 0, 0, 2), ChestItem("potion", 1, 0, 0)), 1),
            chest("box-2", ChestTheme.BACKPACK, 0, 80, listOf(ChestItem("backpack_fabric", 0, 0, 2)), 0, setOf("backpack")),
            chest("box-3", ChestTheme.SKILLS, 1, 150, listOf(ChestItem("skill_shard", 0, 0, 1)), 2, setOf("skills")),
            chest("box-4", ChestTheme.SAFE, 1, 180, listOf(ChestItem("safe_lock", 0, 0, 0)), 0, setOf("safe")),
            chest("box-5", ChestTheme.ARSENAL, 2, 350, listOf(ChestItem("cardA", 0, 0, 2))),
            chest("box-6", ChestTheme.ARCANE, 2, 370, listOf(ChestItem("potion", 0, 0, 1)))
        )
        val state = InventoryState(
            gold = 880,
            items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE), bound = true)),
            backpackWidth = 5, backpackHeight = 3, safeWidth = 3, safeHeight = 2, skillSlots = 6,
            progress = CampaignProgress(
                seed = 4242, batch = 6, chests = boxes, clears = 4, extracted = true,
                lastEnding = "成功撤离", shopCycle = 3, shopSeen = 20_000_000,
                shopStock = mapOf("component" to 2),
                economy = HomeEconomyState(true, 1, 4_000_000, 0, 8_000_000, 3, 2),
                shop = ShopSnapshot(7, 999_999_999L,
                    listOf(StoredShopOffer("component", ShopShelf.STAPLE, 87, 3)), mapOf("component" to 2)),
                unlockedHeroes = setOf("juggernaut", "abaddon", "skywrath"),
                materialPity = pity(
                    "backpack" to MaterialPityCounter(4, true),
                    "skills" to MaterialPityCounter(2, false),
                    "safe" to MaterialPityCounter(6, true))))
        InventoryStore(file, rules, ::initial).save(state)
        val loaded = InventoryStore(file, rules, ::initial).load()
        assertFalse(loaded.recoveredFromBackup)
        assertEquals(state, loaded.state)
        assertEquals(listOf(ChestTheme.SUPPLIES, ChestTheme.BACKPACK, ChestTheme.SKILLS,
            ChestTheme.SAFE, ChestTheme.ARSENAL, ChestTheme.ARCANE), loaded.state.progress.chests.map { it.theme })
        assertEquals(listOf(emptySet(), setOf("backpack"), setOf("skills"), setOf("safe"), emptySet(), emptySet()),
            loaded.state.progress.chests.map { it.guaranteedCategories })
        assertEquals(listOf(1, 0, 2, 0, 0, 0), loaded.state.progress.chests.map { it.appraisals })
        assertEquals(mapOf(
            "backpack" to MaterialPityCounter(4, true),
            "skills" to MaterialPityCounter(2, false),
            "safe" to MaterialPityCounter(6, true)), loaded.state.progress.materialPity.counters)
    }

    @Test fun v02V6InRunTargetKeepsThemeAndGuaranteeWithoutReconsuming() {
        val file = temp.root.resolve("v6run.dat")
        val base = InventoryState(gold = 100000, progress = CampaignProgress(clears = 3, extracted = true))
            .copy(skillSlots = 6)
        val batch = journey.ensureBatch(base)
        val target = batch.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        val withBox = base.copy(progress = base.progress.copy(chests = listOf(target)))
        val battling = journey.startBattle(journey.enter(withBox, target.id, "juggernaut").state).state
        val run = battling.progress.run!!
        val state = battling.copy(progress = battling.progress.copy(
            run = run.copy(target = run.target.copy(guaranteedCategories = setOf("backpack"))),
            materialPity = pity("backpack" to MaterialPityCounter(0, false))))
        InventoryStore(file, rules, ::initial).save(state)
        val loaded = InventoryStore(file, rules, ::initial).load().state
        assertEquals(state, loaded)
        val r = loaded.progress.run!!
        assertEquals(target.id, r.target.id)
        assertEquals(ChestTheme.BACKPACK, r.target.theme)
        assertEquals(setOf("backpack"), r.target.guaranteedCategories)
        assertEquals(JourneyStage.BATTLE, r.stage)
        assertEquals(run.fight!!.tick, r.fight!!.tick)
        assertEquals(run.fight!!.rng, r.fight!!.rng)
        assertEquals(run.fight!!.player.hp, r.fight!!.player.hp, 1e-9)
        assertEquals(run.fight!!.enemy.hp, r.fight!!.enemy.hp, 1e-9)
        assertEquals(MaterialPityCounter(0, false), loaded.progress.materialPity.counters["backpack"])
        // 不重新生成货架、不二次消费资格
        assertEquals(loaded, journey.ensureBatch(loaded))
        assertEquals(MaterialPityCounter(0, false), journey.ensureBatch(loaded).progress.materialPity.counters["backpack"])
    }

    @Test fun v03RealV5SchemaKeepsDataAndDefaultsOldBoxesToSupplies() {
        val file = temp.root.resolve("v5old.dat")
        val state = InventoryState(
            gold = 640,
            items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE), bound = true)),
            backpackWidth = 5, backpackHeight = 3, safeWidth = 3, safeHeight = 2, skillSlots = 6,
            progress = CampaignProgress(
                seed = 77, batch = 2, clears = 4, extracted = true,
                lastEnding = "成功撤离", shopCycle = 3, shopSeen = 20_000_000,
                shopStock = mapOf("component" to 2), manualItems = setOf("i1"),
                chests = listOf(TargetChest("box-1", "蓝", 1, 150,
                    listOf(ChestItem("blade", 0, 0, 2), ChestItem("potion", 1, 0, 0)), 2)),
                economy = HomeEconomyState(true, 1, 4_000_000, 0, 8_000_000, 3, 2),
                shop = ShopSnapshot(7, 999_999_999L,
                    listOf(StoredShopOffer("component", ShopShelf.STAPLE, 87, 3)), mapOf("component" to 2)),
                unlockedHeroes = setOf("juggernaut", "abaddon", "skywrath")))
        writeSave(file, 5, payload(state, 5))
        val loaded = InventoryStore(file, rules, ::initial).load().state
        assertEquals(640, loaded.gold)
        assertEquals(listOf("i1"), loaded.items.map { it.instanceId })
        assertTrue(loaded.items.single().bound)
        assertEquals(5 to 3, loaded.backpackWidth to loaded.backpackHeight)
        assertEquals(3 to 2, loaded.safeWidth to loaded.safeHeight)
        assertEquals(6, loaded.skillSlots)
        val old = loaded.progress.chests.single()
        assertEquals(ChestTheme.SUPPLIES, old.theme)
        assertTrue(old.guaranteedCategories.isEmpty())
        assertEquals(2, old.appraisals)
        assertEquals(150, old.ticket)
        assertEquals(listOf("blade", "potion"), old.items.map { it.definitionId })
        assertEquals(listOf(2, 0), old.items.map { it.reveal })
        assertEquals(7, loaded.progress.shop!!.revision)
        assertEquals(87, loaded.progress.shop!!.offers.single().price)
        assertEquals(1, loaded.progress.economy.refreshCharges)
        assertEquals(3, loaded.progress.economy.reliefClaims)
        assertEquals(setOf("juggernaut", "abaddon", "skywrath"), loaded.progress.unlockedHeroes)
        assertTrue(loaded.progress.materialPity.counters.isEmpty())
    }

    @Test fun v04V5InRunSettlesOnceAndDefaultsAddNoThemeMiss() {
        val file = temp.root.resolve("v5run.dat")
        val run = JourneyState("run-1", "juggernaut",
            TargetChest("box-1", "白", 0, 60, listOf(ChestItem("blade", 0, 0, 2))), 424242L, 500.0, 1000.0, 5,
            JourneyStage.REWARD, null, "blade", 7)
        val state = InventoryState(gold = 200, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(seed = 424242, batch = 1, run = run, clears = 2, extracted = true,
                economy = HomeEconomyState(true, 3, 1000, 2, 2000, 0, 0)))
        writeSave(file, 5, payload(state, 5))
        val loaded = InventoryStore(file, rules, ::initial).load().state
        assertEquals(ChestTheme.SUPPLIES, loaded.progress.run!!.target.theme)
        assertTrue(loaded.progress.run!!.target.guaranteedCategories.isEmpty())
        val done = LootFlowTestSupport.finishReward(journey, loaded)
        assertTrue(done.error ?: "ok", done.ok)
        assertTrue(done.state.items.any { it.instanceId == "run-1:chest:0" })
        assertNull(done.state.progress.run)
        assertTrue("默认综合不凭空累计工程主题 miss", done.state.progress.materialPity.counters.isEmpty())
        assertFalse(rules.settle(done.state, true).ok)
    }

    @Test fun v05LegacyV1ToV4KeepProgressAndRewriteAsV6() {
        for (version in listOf(1, 2, 3, 4)) {
            val file = temp.root.resolve("legacy$version.dat")
            val state = InventoryState(
                gold = 333,
                items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE))),
                progress = CampaignProgress(
                    seed = 55, batch = 1, clears = 2, extracted = true,
                    lastEnding = "legacy", manualItems = setOf("i1"),
                    chests = listOf(TargetChest("box-1", "白", 0, 60, listOf(ChestItem("blade", 0, 0, 2)))),
                    economy = HomeEconomyState(true, 2, 1000, 1, 2000, 0, 0),
                    shop = ShopSnapshot(3, 12345L, listOf(StoredShopOffer("component", ShopShelf.STAPLE, 90, 3)), emptyMap()),
                    unlockedHeroes = setOf("juggernaut", "abaddon", "viper")))
            writeSave(file, version, payload(state, version))
            val loaded = InventoryStore(file, rules, ::initial).load().state
            assertEquals("v$version gold", 333, loaded.gold)
            assertEquals("v$version items", listOf("i1"), loaded.items.map { it.instanceId })
            if (version >= 2) {
                assertEquals("v$version clears", 2, loaded.progress.clears)
                assertTrue("v$version extracted", loaded.progress.extracted)
                assertEquals("v$version chests", 1, loaded.progress.chests.size)
                assertEquals("v$version theme", ChestTheme.SUPPLIES, loaded.progress.chests.single().theme)
                assertTrue("v$version guaranteed", loaded.progress.chests.single().guaranteedCategories.isEmpty())
            } else {
                assertEquals("v$version default clears", 0, loaded.progress.clears)
                assertTrue("v$version default chests", loaded.progress.chests.isEmpty())
            }
            if (version >= 3) assertEquals("v$version manual", setOf("i1"), loaded.progress.manualItems)
            if (version >= 4) assertEquals("v$version refresh", 2, loaded.progress.economy.refreshCharges)
            assertEquals("v$version heroes", setOf("juggernaut", "abaddon"), loaded.progress.unlockedHeroes)
            assertTrue("v$version pity", loaded.progress.materialPity.counters.isEmpty())
            val store = InventoryStore(file, rules, ::initial)
            store.save(loaded)
            assertEquals("v$version v6 round-trip", loaded, store.load().state)
        }
    }

    @Test fun v06InvalidPityIsRejectedAndValidSaveSurvives() {
        val file = temp.root.resolve("v6pity.dat")
        val store = InventoryStore(file, rules, ::initial)
        val valid = InventoryState(gold = 400, progress = CampaignProgress(batch = 1,
            materialPity = pity("backpack" to MaterialPityCounter(2, false))))
        store.save(valid)
        val bads = listOf(
            "未知类别" to MaterialPityState(mapOf("ghost" to MaterialPityCounter(1, false))),
            "负miss" to MaterialPityState(mapOf("backpack" to MaterialPityCounter(-1, false))),
            "超阈值" to MaterialPityState(mapOf("backpack" to MaterialPityCounter(5, false))),
            "pending与阈值不一致" to MaterialPityState(mapOf("backpack" to MaterialPityCounter(2, true))),
            "safe超阈值" to MaterialPityState(mapOf("safe" to MaterialPityCounter(7, true)))
        )
        for ((label, p) in bads) {
            assertThrows(label, IllegalArgumentException::class.java) {
                store.save(valid.copy(progress = valid.progress.copy(materialPity = p)))
            }
        }
        assertEquals(valid, store.load().state)
    }

    @Test fun v07InvalidBoxMetadataIsRejectedWithoutHalfConsumingPity() {
        val file = temp.root.resolve("v6box.dat")
        val store = InventoryStore(file, rules, ::initial)
        val valid = InventoryState(gold = 400, progress = CampaignProgress(batch = 1,
            materialPity = pity("safe" to MaterialPityCounter(6, true))))
        store.save(valid)
        fun withBox(c: TargetChest) = valid.copy(progress = valid.progress.copy(chests = listOf(c)))
        val bads = listOf(
            "主题与保底来源不匹配" to withBox(chest("box-1", ChestTheme.ARSENAL, 0, 60,
                listOf(ChestItem("blade", 0, 0, 2)), guaranteed = setOf("backpack"))),
            "缺强制材料" to withBox(chest("box-2", ChestTheme.BACKPACK, 0, 60,
                listOf(ChestItem("blade", 0, 0, 2)), guaranteed = setOf("backpack"))),
            "一个箱多个保底类别" to withBox(chest("box-3", ChestTheme.BACKPACK, 0, 60,
                listOf(ChestItem("backpack_fabric", 0, 0, 2)), guaranteed = setOf("backpack", "skills")))
        )
        for ((label, s) in bads) {
            assertThrows(label, IllegalArgumentException::class.java) { store.save(s) }
        }
        val reloaded = store.load().state
        assertEquals(valid, reloaded)
        assertEquals(MaterialPityCounter(6, true), reloaded.progress.materialPity.counters["safe"])
    }

    @Test fun v08CorruptV6PrimaryRecoversBackupAndKeepsValidGeneration() {
        val file = temp.root.resolve("v6bak.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(InventoryState(gold = 111, progress = CampaignProgress(batch = 1,
            materialPity = pity("backpack" to MaterialPityCounter(3, false)))))
        store.save(InventoryState(gold = 222, progress = CampaignProgress(batch = 2,
            materialPity = pity("backpack" to MaterialPityCounter(4, true)))))
        file.writeText("broken")
        val recovered = store.load()
        assertTrue(recovered.recoveredFromBackup)
        assertEquals(111, recovered.state.gold)
        assertEquals(MaterialPityCounter(3, false), recovered.state.progress.materialPity.counters["backpack"])
        // 再次保存保留有效代际
        store.save(InventoryState(gold = 333, progress = CampaignProgress(batch = 3)))
        val again = store.load()
        assertFalse(again.recoveredFromBackup)
        assertEquals(333, again.state.gold)
        assertTrue(again.state.progress.materialPity.counters.isEmpty())
    }

    @Test fun v09ReopeningStoreDoesNotChangePityOrGeneratedBoxes() {
        val file = temp.root.resolve("v6reopen.dat")
        val box = chest("box-1", ChestTheme.BACKPACK, 0, 60, listOf(ChestItem("backpack_fabric", 0, 0, 2)),
            guaranteed = setOf("backpack"))
        val state = InventoryState(
            gold = 700, skillSlots = 5, backpackWidth = 4, backpackHeight = 3,
            items = listOf(StoredItem("i1", "blade", ItemPlace(Container.WAREHOUSE))),
            progress = CampaignProgress(batch = 2, chests = listOf(box), clears = 1, extracted = true,
                economy = HomeEconomyState(true, 1, 5_000_000, 0, 5_000_000, 2, 2),
                materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        InventoryStore(file, rules, ::initial).save(state)
        val first = InventoryStore(file, rules, ::initial).load().state
        val second = InventoryStore(file, rules, ::initial).load().state
        assertEquals(first, second)
        assertEquals(state, first)
        // 重建页面不会重新生成货架或再发保底
        assertEquals(first, journey.ensureBatch(first))
        assertEquals(MaterialPityCounter(4, true), first.progress.materialPity.counters["backpack"])
        assertEquals(1, first.progress.chests.size)
        assertEquals(setOf("backpack"), first.progress.chests.single().guaranteedCategories)
    }

    @Test fun v10NewerVersionUnknownEnumAndBadCrcAreHandledByBackupRules() {
        // 高于当前版本号：明确拒绝，不偷偷回滚备份
        val file = temp.root.resolve("v6new.dat")
        val store = InventoryStore(file, rules, ::initial)
        store.save(InventoryState(gold = 1, progress = CampaignProgress(batch = 1)))
        store.save(InventoryState(gold = 2, progress = CampaignProgress(batch = 2)))
        java.io.RandomAccessFile(file, "rw").use { it.seek(4); it.writeInt(999) }
        assertThrows(UnsupportedSaveVersion::class.java) { store.load() }
        // 坏 CRC：按既有备份规则恢复，且不覆盖无法读取的原档
        val file2 = temp.root.resolve("v6crc.dat")
        val store2 = InventoryStore(file2, rules, ::initial)
        store2.save(InventoryState(gold = 11, progress = CampaignProgress(batch = 1)))
        store2.save(InventoryState(gold = 22, progress = CampaignProgress(batch = 2)))
        java.io.RandomAccessFile(file2, "rw").use { it.seek(30); it.writeByte(0x7F) }
        val before = file2.readBytes()
        val rec = store2.load()
        assertTrue(rec.recoveredFromBackup)
        assertEquals(11, rec.state.gold)
        assertArrayEquals(before, file2.readBytes())
        // 未知 enum：主档读取失败转备份，原档保持
        val file3 = temp.root.resolve("v6enum.dat")
        InventoryStore(file3, rules, ::initial).save(InventoryState(gold = 55, progress = CampaignProgress(batch = 1)))
        InventoryStore(file3, rules, ::initial).save(InventoryState(gold = 66, progress = CampaignProgress(batch = 2)))
        val brokenState = InventoryState(gold = 77, progress = CampaignProgress(batch = 1,
            chests = listOf(TargetChest("box-1", "白", 0, 60, listOf(ChestItem("blade", 0, 0, 2))))))
        val broken = payload(brokenState, 6, themeOverride = "UNKNOWN_THEME")
        writeSave(file3, 6, broken)
        val before3 = file3.readBytes()
        val rec3 = InventoryStore(file3, rules, ::initial).load()
        assertTrue(rec3.recoveredFromBackup)
        assertEquals(55, rec3.state.gold)
        assertArrayEquals(before3, file3.readBytes())
    }
}
