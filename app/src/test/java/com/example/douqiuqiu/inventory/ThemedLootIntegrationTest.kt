package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.loot.MaterialPityCounter
import com.example.douqiuqiu.loot.MaterialPityState
import org.junit.Assert.*
import org.junit.Test

/**
 * 外围任务23：主题宝箱与材料保底验收（L01~L25）。
 *
 * 只做断言与报告，不改生产代码。期待值按任务表人工写入，
 * 不通过与生产相同算法计算预期再自比较。
 *
 * 注意：ChestFactory.replace 会为被刷新的箱分配新的批次序列号（box-N 自增），
 * 因此刷新后按「原位索引」定位该箱，其余箱按原 ID 核对完全保持。
 */
class ThemedLootIntegrationTest {

    private val definitions = mapOf(
        "essence" to ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        "backpack_fabric" to ItemDefinition("backpack_fabric", "织件", ItemKind.MATERIAL, 1, 2, boundOnAcquire = true),
        "skill_shard" to ItemDefinition("skill_shard", "刻片", ItemKind.MATERIAL, 1, 1, boundOnAcquire = true),
        "safe_lock" to ItemDefinition("safe_lock", "锁芯", ItemKind.MATERIAL, 3, 2, boundOnAcquire = true),
        "safe_frame" to ItemDefinition("safe_frame", "框架", ItemKind.MATERIAL, 3, 3, boundOnAcquire = true),
        "blade" to ItemDefinition("blade", "短刀", ItemKind.EQUIPMENT, 1, 1, price = 50),
        "cardA" to ItemDefinition("cardA", "技能卡", ItemKind.CARD, 1, 1, price = 100),
        "potion" to ItemDefinition("potion", "药水", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, price = 30),
        "scrollA" to ItemDefinition("scrollA", "卷轴", ItemKind.SCROLL, 1, 1, price = 80)
    )
    private val inventory = InventoryRules(definitions)
    private val journey = JourneyRules(inventory)
    // These fixtures test material eligibility, independent of the new quest entry gate.
    private fun unlockedNodes(nodes: Int = 23) = com.example.douqiuqiu.quest.QuestContent.all.filter { it.node <= nodes }
        .groupBy { it.node }.values.map { it.first().id }.toSet()

    private fun pity(vararg pairs: Pair<String, MaterialPityCounter>) = MaterialPityState(mapOf(*pairs))

    private fun box(
        id: String, theme: ChestTheme, difficulty: Int, ticket: Int,
        items: List<ChestItem> = listOf(ChestItem("blade", 0, 0, 2)),
        appraisals: Int = 0, guaranteed: Set<String> = emptySet()
    ) = TargetChest(id, "白", difficulty, ticket, items, appraisals, theme, guaranteed)

    /** 进入指定宝箱并直接结算第 5 战奖励，走真实 complete/finish/record 链路。 */
    /** 完成当前节点掉落的鉴定，供手动摆放测试使用。 */
    private fun revealAll(s: InventoryState): InventoryState {
        var state = s
        val loot = state.progress.run?.nodeLoot ?: return state
        val duration = NodeLootRules.duration(loot, definitions)
        while (state.progress.run!!.nodeLoot!!.inspectionMs < duration) {
            val stepped = NodeLootRules.inspect(state, inventory, 500)
            assertTrue(stepped.error ?: "ok", stepped.ok)
            state = stepped.state
        }
        return state
    }

    private fun clearTarget(s: InventoryState, target: TargetChest, hero: String = "juggernaut"): InventoryState {
        val withBox = s.copy(phase = InventoryPhase.HOME, progress = s.progress.copy(chests = listOf(target), run = null))
        val entered = journey.enter(withBox, target.id, hero).state
        val run = entered.progress.run!!
        var state = entered.copy(progress = entered.progress.copy(
            run = run.copy(round = 5, stage = JourneyStage.REWARD, fight = null)))
        // 0.1.4 起战后三选一取消：先落地掉落并完成鉴定，再确认丢弃进入通关结算。
        state = journey.battleLoot(state)
        while (state.progress.run!!.nodeLoot!!.let { it.inspectionMs < NodeLootRules.duration(it, definitions) }) {
            val stepped = NodeLootRules.inspect(state, inventory, 500)
            assertTrue(stepped.error ?: "ok", stepped.ok); state = stepped.state
        }
        return journey.finishLoot(state, true).state
    }

    /** 刷新原位箱；返回（刷新后状态, 新箱, 其他箱）。 */
    private fun refreshOne(
        s: InventoryState, target: TargetChest, rescue: Boolean = false
    ): Triple<InventoryState, TargetChest, List<TargetChest>> {
        val index = s.progress.chests.indexOf(target)
        assertTrue("待刷新箱不在货架上", index >= 0)
        val r = journey.replaceChest(s, target.id, rescue)
        assertTrue(r.error ?: "ok", r.ok)
        val boxes = r.state.progress.chests
        return Triple(r.state, boxes[index], boxes.filterIndexed { i, _ -> i != index })
    }

    // ---------------- 生成与刷新 ----------------

    @Test fun l01BatchSizeFollowsClearsAndKeepsFiveFights() {
        for ((clears, count) in listOf(0 to 4, 2 to 4, 3 to 6, 9 to 6, 10 to 8)) {
            val s = InventoryState(gold = 100000, progress = CampaignProgress(claimedQuests = unlockedNodes(if (clears < 3) 16 else 23), clears = clears))
            val batch = journey.ensureBatch(s)
            val boxes = batch.progress.chests
            assertEquals("clears=$clears 箱数", count, boxes.size)
            assertTrue("clears=$clears 至少2品质", boxes.map { it.quality }.toSet().size >= 2)
            val cheapest = boxes.filter { it.difficulty == 0 }.minOfOrNull { it.ticket }
            assertNotNull("clears=$clears 低价入口", cheapest)
            assertEquals("clears=$clears 120金币入口", 120, cheapest)
            // 一局仍 5 战：第 5 个奖励节点结算即收官
            val target = boxes.first()
            val entered = journey.enter(batch, target.id, "juggernaut").state
            val run = entered.progress.run!!
            val lastFight = entered.copy(progress = entered.progress.copy(
                run = run.copy(round = 5, stage = JourneyStage.REWARD, fight = null)))
            val done = LootFlowTestSupport.finishReward(journey, lastFight)
            assertTrue("clears=$clears 收官: ${done.error}", done.ok)
            assertNull("clears=$clears run清除", done.state.progress.run)
            assertEquals("clears=$clears 通关+1", clears + 1, done.state.progress.clears)
        }
    }

    @Test fun l02SixBoxesCoverAllThemesWithoutOverlap() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 3))
        val boxes = journey.ensureBatch(s).progress.chests
        assertEquals(6, boxes.size)
        assertEquals(ChestTheme.entries.toSet(), boxes.map { it.theme }.toSet())
        assertEquals(6, boxes.map { it.id }.toSet().size)
        for (c in boxes) {
            val occupied = mutableSetOf<Pair<Int, Int>>()
            for (item in c.items) {
                val d = definitions.getValue(item.definitionId)
                assertTrue("${c.id} x越界", item.x in 0..(6 - d.width))
                assertTrue("${c.id} y越界", item.y in 0..(6 - d.height))
                for (x in item.x until item.x + d.width) for (y in item.y until item.y + d.height) {
                    assertTrue("${c.id} 重叠 ($x,$y)", occupied.add(x to y))
                }
            }
        }
    }

    @Test fun l03SameSeedSameSnapshotIsStableAndDoesNotReconsumePity() {
        val base = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1))
        val a = journey.ensureBatch(base)
        val b = journey.ensureBatch(base)
        assertEquals(a, b)
        val withPity = a.copy(progress = a.progress.copy(
            materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        val again = journey.ensureBatch(withPity)
        assertEquals(withPity, again)
        assertEquals(MaterialPityCounter(4, true), again.progress.materialPity.counters["backpack"])
        assertEquals(a.progress.batch, again.progress.batch)
    }

    @Test fun l04AppraiseAndSingleBoxRefreshTouchOnlyThatBox() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 3))
        val batch = journey.ensureBatch(s)
        val target = batch.progress.chests[1]
        val others = batch.progress.chests.filter { it.id != target.id }
        // 鉴定只改揭示/票价
        val appraised = journey.appraise(batch, target.id)
        assertTrue(appraised.error ?: "ok", appraised.ok)
        assertEquals(others, appraised.state.progress.chests.filter { it.id != target.id })
        val changed = appraised.state.progress.chests.single { it.id == target.id }
        assertEquals(target.appraisals + 1, changed.appraisals)
        target.items.indices.forEach { i ->
            assertEquals(target.items[i].definitionId, changed.items[i].definitionId)
            assertEquals(target.items[i].x, changed.items[i].x)
            assertEquals(target.items[i].y, changed.items[i].y)
            assertTrue("揭示只增不减", changed.items[i].reveal >= target.items[i].reveal)
        }
        // 单箱刷新：保主题/难度、清鉴定、其他箱完全保持
        val (refreshed, newBox, kept) = refreshOne(appraised.state, changed)
        assertEquals(target.theme, newBox.theme)
        assertEquals(target.difficulty, newBox.difficulty)
        assertEquals(0, newBox.appraisals)
        assertEquals(others, kept)
    }

    @Test fun l05ReliefReplacesOnlyTargetBoxWithWhiteSuppliesOneHundredTwenty() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 3,
            economy = HomeEconomyState(initialized = true, refreshCharges = 2,
                reliefCharges = 1, reliefClaims = 3, vouchers = 1)))
        val batch = journey.ensureBatch(s)
        val last = batch.progress.chests.last()
        val others = batch.progress.chests.dropLast(1)
        val (state, rescued, kept) = refreshOne(batch, last, rescue = true)
        assertEquals(others, kept)
        assertEquals(ChestTheme.SUPPLIES, rescued.theme)
        assertEquals(0, rescued.difficulty)
        assertEquals("白", rescued.quality)
        assertEquals(120, rescued.ticket)
        val e = state.progress.economy
        assertEquals(2, e.refreshCharges)
        assertEquals(1, e.reliefCharges)
        assertEquals(3, e.reliefClaims)
        assertEquals(1, e.vouchers)
    }

    @Test fun l06RefreshingBackpackBoxConsumesOnlyBackpackEligibility() {
        val base = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true))
        val batch = journey.ensureBatch(base)
        val backpackBox = batch.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        val others = batch.progress.chests.filter { it.id != backpackBox.id }
        val pending = batch.copy(progress = batch.progress.copy(materialPity = pity(
            "backpack" to MaterialPityCounter(4, true), "safe" to MaterialPityCounter(6, true))))
        val (state, newBox, kept) = refreshOne(pending, backpackBox)
        assertEquals(batch.progress.chests.size, state.progress.chests.size)
        assertEquals(others, kept)
        assertEquals(setOf("backpack"), newBox.guaranteedCategories)
        assertTrue(newBox.items.any { it.definitionId == "backpack_fabric" })
        assertEquals(MaterialPityCounter(0, false), state.progress.materialPity.counters["backpack"])
        assertEquals(MaterialPityCounter(6, true), state.progress.materialPity.counters["safe"])
    }

    @Test fun l07BatchNearIntMaxValueStaysPositiveAndUnique() {
        val near = Int.MAX_VALUE - 6
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 3, batch = near))
        val batch = journey.ensureBatch(s)
        val serials = batch.progress.chests.map { it.id.removePrefix("box-").toInt() }
        assertEquals(6, serials.size)
        assertEquals(serials.sorted(), serials)
        assertTrue(serials.all { it > 0 })
        assertEquals(serials.toSet().size, serials.size)
        // 到达上限后刷新明确失败且不修改输入
        val limited = batch.copy(progress = batch.progress.copy(batch = Int.MAX_VALUE))
        val r = journey.replaceChest(limited, batch.progress.chests.first().id)
        assertFalse(r.ok)
        assertEquals(limited, r.state)
        // 无法继续生成时抛错，输入不被修改、未提交中间状态
        val overflow = InventoryState(gold = 100000,
            progress = CampaignProgress(clears = 3, batch = Int.MAX_VALUE - 1))
        assertThrows(IllegalArgumentException::class.java) { journey.ensureBatch(overflow) }
        assertEquals(Int.MAX_VALUE - 1, overflow.progress.batch)
        assertTrue(overflow.progress.chests.isEmpty())
    }

    // ---------------- 资格与生成消耗 ----------------

    @Test fun l08NothingEligibleBeforeExtractionOrOpenTier() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 0, extracted = false))
        assertTrue(MaterialProgression.eligible(s, definitions).isEmpty())
        // 未开放类别不因普通通关累计 miss
        val cleared = clearTarget(s, box("box-l08", ChestTheme.SUPPLIES, 0, 60))
        assertTrue(cleared.progress.materialPity.counters.isEmpty())
    }

    @Test fun l09BackpackEligibleOnlyWhileWarehouseShort() {
        fun state(fabrics: Int) = InventoryState(gold = 100000,
            items = (1..fabrics).map { StoredItem("f$it", "backpack_fabric", ItemPlace(Container.WAREHOUSE)) },
            progress = CampaignProgress(clears = 1, extracted = true))
        assertTrue(MaterialProgression.eligible(state(0), definitions).contains("backpack"))
        assertFalse(MaterialProgression.eligible(state(2), definitions).contains("backpack"))
        // 仓库已足额：待保底不被不合格箱消费（clears=3 才能让六主题齐备）
        val full = InventoryState(gold = 100000, items = (1..2).map {
            StoredItem("f$it", "backpack_fabric", ItemPlace(Container.WAREHOUSE)) },
            progress = CampaignProgress(clears = 3, extracted = true))
        val batch = journey.ensureBatch(full)
        val box = batch.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        val pending = batch.copy(progress = batch.progress.copy(
            materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        val (state2, newBox, _) = refreshOne(pending, box)
        assertTrue(newBox.guaranteedCategories.isEmpty())
        assertEquals(MaterialPityCounter(4, true), state2.progress.materialPity.counters["backpack"])
    }

    @Test fun l10BackpackTierLockedByClearsDoesNotAccumulate() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 0, extracted = true))
            .copy(backpackWidth = 4, backpackHeight = 3)
        assertFalse(MaterialProgression.eligible(s, definitions).contains("backpack"))
        // 不能只看未满容量就累计
        val cleared = clearTarget(s, box("box-l10", ChestTheme.BACKPACK, 0, 60))
        assertTrue(cleared.progress.materialPity.counters.isEmpty())
    }

    @Test fun l11SkillEligibilityFollowsSlotsClearsAndShards() {
        fun state(slots: Int, clears: Int, shards: Int) =
            InventoryState(gold = 100000,
                items = (1..shards).map { StoredItem("s$it", "skill_shard", ItemPlace(Container.WAREHOUSE)) },
                progress = CampaignProgress(clears = clears, extracted = true)).copy(skillSlots = slots)
        assertTrue(MaterialProgression.eligible(state(6, 3, 1), definitions).contains("skills"))
        assertFalse(MaterialProgression.eligible(state(6, 2, 1), definitions).contains("skills"))
        assertTrue(MaterialProgression.eligible(state(7, 6, 3), definitions).contains("skills"))
        assertFalse(MaterialProgression.eligible(state(7, 5, 3), definitions).contains("skills"))
        assertFalse(MaterialProgression.eligible(state(8, 9, 0), definitions).contains("skills"))
        assertFalse(MaterialProgression.eligible(state(6, 3, 2), definitions).contains("skills"))
    }

    @Test fun l12SafeEligibilityFollowsSizeClearsAndBackpack() {
        fun state(safe: Pair<Int, Int>, clears: Int, backpack: Pair<Int, Int>, frames: Int = 0) =
            InventoryState(gold = 100000,
                items = (1..frames).map { StoredItem("fr$it", "safe_frame", ItemPlace(Container.WAREHOUSE)) },
                progress = CampaignProgress(clears = clears, extracted = true))
                .copy(safeWidth = safe.first, safeHeight = safe.second,
                    backpackWidth = backpack.first, backpackHeight = backpack.second)
        assertTrue(MaterialProgression.eligible(state(2 to 2, 1, 4 to 2), definitions).contains("safe"))
        assertFalse(MaterialProgression.eligible(state(2 to 2, 0, 4 to 2), definitions).contains("safe"))
        assertTrue(MaterialProgression.eligible(state(3 to 2, 5, 4 to 3), definitions).contains("safe"))
        assertFalse(MaterialProgression.eligible(state(3 to 2, 4, 4 to 3), definitions).contains("safe"))
        assertFalse(MaterialProgression.eligible(state(3 to 2, 5, 4 to 2), definitions).contains("safe"))
        assertFalse(MaterialProgression.eligible(state(3 to 3, 9, 6 to 4), definitions).contains("safe"))
    }

    @Test fun l13PendingBackpackConsumesAtGenerationNotAtClear() {
        val base = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true))
        val batch = journey.ensureBatch(base)
        val box = batch.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        val pending = batch.copy(progress = batch.progress.copy(
            materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        val (state, newBox, _) = refreshOne(pending, box)
        assertEquals("未通关", 1, state.progress.clears)
        assertEquals(MaterialPityCounter(0, false), state.progress.materialPity.counters["backpack"])
        assertEquals(setOf("backpack"), newBox.guaranteedCategories)
        assertTrue(newBox.items.any { it.definitionId == "backpack_fabric" })
    }

    @Test fun l14SkillsAndSafeGuaranteeConsumeTheirOwnCategoryOnly() {
        val base = InventoryState(gold = 100000, progress = CampaignProgress(claimedQuests = unlockedNodes(), clears = 3, extracted = true))
            .copy(skillSlots = 6)
        val batch = journey.ensureBatch(base)
        val skillsBox = batch.progress.chests.single { it.theme == ChestTheme.SKILLS }
        val safeBox = batch.progress.chests.single { it.theme == ChestTheme.SAFE }
        val pending = batch.copy(progress = batch.progress.copy(materialPity = pity(
            "skills" to MaterialPityCounter(4, true), "safe" to MaterialPityCounter(6, true))))
        val (s1, newSkills, _) = refreshOne(pending, skillsBox)
        assertEquals(setOf("skills"), newSkills.guaranteedCategories)
        assertTrue(newSkills.items.any { it.definitionId == "skill_shard" })
        assertEquals(MaterialPityCounter(0, false), s1.progress.materialPity.counters["skills"])
        assertEquals(MaterialPityCounter(6, true), s1.progress.materialPity.counters["safe"])
        val (s2, newSafe, _) = refreshOne(s1, safeBox)
        assertEquals(setOf("safe"), newSafe.guaranteedCategories)
        assertTrue(newSafe.items.any { it.definitionId == "safe_lock" })
        assertTrue("Safe 最低难度 1", newSafe.difficulty >= 1)
        assertEquals(MaterialPityCounter(0, false), s2.progress.materialPity.counters["safe"])
        // 低成本（难度0）SAFE 箱不消费 safe 资格
        val index = s2.progress.chests.indexOfFirst { it.id == newSafe.id }
        val cheapSafe = s2.progress.chests.mapIndexed { i, c -> if (i == index) c.copy(difficulty = 0) else c }
        val cheap = s2.copy(progress = s2.progress.copy(
            chests = cheapSafe, materialPity = pity("safe" to MaterialPityCounter(6, true))))
        val r3 = journey.replaceChest(cheap, newSafe.id)
        assertTrue(r3.error ?: "ok", r3.ok)
        assertTrue(r3.state.progress.chests[index].guaranteedCategories.isEmpty())
        assertEquals(MaterialPityCounter(6, true), r3.state.progress.materialPity.counters["safe"])
    }

    @Test fun l15UnopenedOrSatisfiedNeedNeverConsumesPending() {
        // 成长未开放：尚未撤离
        var s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 3, extracted = false))
        var batch = journey.ensureBatch(s)
        var box = batch.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        var pending = batch.copy(progress = batch.progress.copy(
            materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        var (state, newBox, _) = refreshOne(pending, box)
        assertTrue(newBox.guaranteedCategories.isEmpty())
        assertEquals(MaterialPityCounter(4, true), state.progress.materialPity.counters["backpack"])
        // 安全箱已满档
        s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 6, extracted = true))
            .copy(safeWidth = 3, safeHeight = 3, backpackWidth = 6, backpackHeight = 4)
        batch = journey.ensureBatch(s)
        box = batch.progress.chests.single { it.theme == ChestTheme.SAFE }
        pending = batch.copy(progress = batch.progress.copy(
            materialPity = pity("safe" to MaterialPityCounter(6, true))))
        val r = refreshOne(pending, box)
        state = r.first; newBox = r.second
        assertTrue(newBox.guaranteedCategories.isEmpty())
        assertEquals(MaterialPityCounter(6, true), state.progress.materialPity.counters["safe"])
    }

    @Test fun l16OneGuaranteePerBatchAndNaturalDropsAreNotGuarantees() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 10, extracted = true))
            .copy(safeWidth = 3, safeHeight = 3, skillSlots = 8)
        val pending = s.copy(progress = s.progress.copy(
            materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        val batch = journey.ensureBatch(pending)
        val boxes = batch.progress.chests
        assertEquals(8, boxes.size)
        val guaranteed = boxes.filter { "backpack" in it.guaranteedCategories }
        assertEquals("一份资格最多一箱", 1, guaranteed.size)
        assertEquals(ChestTheme.BACKPACK, guaranteed.single().theme)
        assertTrue(guaranteed.single().items.any { it.definitionId == "backpack_fabric" })
        // 另一个 BACKPACK 箱可能自然掉落织件，但没有保底标记
        for (b in boxes.filter { it.theme == ChestTheme.BACKPACK }.filterNot { it.id == guaranteed.single().id }) {
            assertTrue(b.guaranteedCategories.isEmpty())
        }
        assertEquals(MaterialPityCounter(0, false), batch.progress.materialPity.counters["backpack"])
        // 连续刷新同一主题：第二次不再有资格
        val (_, newBox, _) = refreshOne(batch, guaranteed.single())
        assertTrue(newBox.guaranteedCategories.isEmpty())
    }

    // ---------------- 终局计数 ----------------

    @Test fun l17QualifiedClearMissAccumulatesToThreshold() {
        var s = InventoryState(gold = 100000, progress = CampaignProgress(claimedQuests = unlockedNodes(), clears = 1, extracted = true))
        repeat(4) { s = clearTarget(s, box("box-b$it", ChestTheme.BACKPACK, 0, 60)) }
        assertEquals(MaterialPityCounter(4, true), s.progress.materialPity.counters["backpack"])
        // 非对应主题不加
        val frozen = s.progress.materialPity
        s = clearTarget(s, box("box-sup", ChestTheme.SUPPLIES, 0, 60))
        assertEquals(frozen, s.progress.materialPity)
        // 不合格难度不加
        s = clearTarget(s, box("box-sf0", ChestTheme.SAFE, 0, 60))
        assertEquals(frozen, s.progress.materialPity)
        // safe 6 次 -> pending
        var t = InventoryState(gold = 100000, progress = CampaignProgress(claimedQuests = unlockedNodes(), clears = 1, extracted = true))
        repeat(6) { t = clearTarget(t, box("box-sf$it", ChestTheme.SAFE, 1, 150)) }
        assertEquals(MaterialPityCounter(6, true), t.progress.materialPity.counters["safe"])
        // skills 4 次 -> pending
        var u = InventoryState(gold = 100000, progress = CampaignProgress(claimedQuests = unlockedNodes(), clears = 3, extracted = true))
            .copy(skillSlots = 6)
        repeat(4) { u = clearTarget(u, box("box-sk$it", ChestTheme.SKILLS, 1, 150)) }
        assertEquals(MaterialPityCounter(4, true), u.progress.materialPity.counters["skills"])
    }

    @Test fun l18DeathAndNormalExtractionAddNoMiss() {
        val target = box("box-d", ChestTheme.BACKPACK, 0, 60)
        val base = InventoryState(gold = 100000,
            items = listOf(StoredItem("f0", "backpack_fabric", ItemPlace(Container.WAREHOUSE))),
            progress = CampaignProgress(clears = 1, extracted = true,
                materialPity = pity("backpack" to MaterialPityCounter(2, false))))
        val withBox = base.copy(progress = base.progress.copy(chests = listOf(target)))
        val entered = journey.enter(withBox, target.id, "juggernaut").state
        val dead = journey.abandon(entered).state
        assertEquals(MaterialPityCounter(2, false), dead.progress.materialPity.counters["backpack"])
        // 第三战后的普通撤离
        val entered2 = journey.enter(withBox, target.id, "juggernaut").state
        val resting = entered2.copy(progress = entered2.progress.copy(
            run = entered2.progress.run!!.copy(round = 3, stage = JourneyStage.REST, fight = null)))
        val out = journey.extract(resting)
        assertTrue(out.error ?: "ok", out.ok)
        assertEquals(MaterialPityCounter(2, false), out.state.progress.materialPity.counters["backpack"])
    }

    @Test fun l19MaterialInSafeResetsButBackpackLossDoesNot() {
        fun start() = InventoryState(gold = 100000,
            progress = CampaignProgress(clears = 1, extracted = true,
                materialPity = pity("backpack" to MaterialPityCounter(2, false))))
            .copy(backpackWidth = 4, backpackHeight = 2)
        val target = box("box-e", ChestTheme.ARSENAL, 0, 60)
        fun runWithEventMaterial(): InventoryState {
            val from = start()
            val withBox = from.copy(progress = from.progress.copy(chests = listOf(target)))
            val entered = journey.enter(withBox, target.id, "juggernaut").state
            val r = entered.progress.run!!
            return entered.copy(progress = entered.progress.copy(run = r.copy(
                stage = JourneyStage.EVENT, eventRoll = 10, pendingItem = "backpack_fabric", fight = null)))
        }
        // 放安全箱后死亡：实际带回 -> 重置
        var s = revealAll(journey.event(runWithEventMaterial(), true).state)
        val fabric = s.progress.run!!.nodeLoot!!.remaining.first { it.definitionId == "backpack_fabric" }
        s = NodeLootRules.collect(s, inventory, fabric.instanceId, ItemPlace(Container.SAFE, 0, 0)).state
        assertTrue(s.items.single { it.instanceId == fabric.instanceId }.place.container == Container.SAFE)
        var dead = journey.abandon(s).state
        assertEquals(MaterialPityCounter(0, false), dead.progress.materialPity.counters["backpack"])
        // 留在背包中死亡：丢失 -> 不重置
        s = revealAll(journey.event(runWithEventMaterial(), true).state)
        val kept = s.progress.run!!.nodeLoot!!.remaining.first { it.definitionId == "backpack_fabric" }
        s = NodeLootRules.collect(s, inventory, kept.instanceId, ItemPlace(Container.BACKPACK, 0, 0)).state
        assertTrue(s.items.any { it.place.container == Container.BACKPACK && it.definitionId == "backpack_fabric" })
        dead = journey.abandon(s).state
        assertEquals(MaterialPityCounter(2, false), dead.progress.materialPity.counters["backpack"])
    }

    @Test fun l20BroughtMaterialResetsRegardlessOfSourceTheme() {
        // 目标箱通关带出（来源并非材料主题）
        var s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true,
            materialPity = pity("backpack" to MaterialPityCounter(3, false))))
        s = clearTarget(s, box("box-mix", ChestTheme.ARSENAL, 0, 60,
            listOf(ChestItem("backpack_fabric", 0, 0, 2))))
        assertEquals(MaterialPityCounter(0, false), s.progress.materialPity.counters["backpack"])
        // 其他主题奖励撤离带回
        val base = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true,
            materialPity = pity("backpack" to MaterialPityCounter(2, false))))
        val target = box("box-x", ChestTheme.ARSENAL, 0, 60)
        val withBox = base.copy(progress = base.progress.copy(chests = listOf(target)))
        val entered = journey.enter(withBox, target.id, "juggernaut").state
        val r = entered.progress.run!!
        val ev = entered.copy(progress = entered.progress.copy(run = r.copy(
            stage = JourneyStage.EVENT, eventRoll = 10, pendingItem = "backpack_fabric", fight = null)))
        var got = revealAll(journey.event(ev, true).state)
        val fabric = got.progress.run!!.nodeLoot!!.remaining.first { it.definitionId == "backpack_fabric" }
        got = NodeLootRules.collect(got, inventory, fabric.instanceId, ItemPlace(Container.BACKPACK, 0, 0)).state
        got = journey.finishLoot(got, true).also { assertTrue(it.error ?: "ok", it.ok) }.state
        val resting = got.copy(progress = got.progress.copy(
            run = got.progress.run!!.copy(round = 3, stage = JourneyStage.REST)))
        val out = journey.extract(resting)
        assertTrue(out.error ?: "ok", out.ok)
        assertEquals(MaterialPityCounter(0, false), out.state.progress.materialPity.counters["backpack"])
    }

    @Test fun l21OldWarehouseMaterialsCannotResetMisses() {
        val old = StoredItem("old-f", "backpack_fabric", ItemPlace(Container.WAREHOUSE))
        var s = InventoryState(gold = 100000, items = listOf(old),
            progress = CampaignProgress(clears = 1, extracted = true,
                materialPity = pity("backpack" to MaterialPityCounter(2, false))))
        s = clearTarget(s, box("box-old", ChestTheme.BACKPACK, 0, 60))
        assertEquals(MaterialPityCounter(3, false), s.progress.materialPity.counters["backpack"])
        // 仓库已足额时不再有缺额，也不累计
        val two = listOf(old, StoredItem("old-f2", "backpack_fabric", ItemPlace(Container.WAREHOUSE)))
        var t = InventoryState(gold = 100000, items = two,
            progress = CampaignProgress(clears = 1, extracted = true,
                materialPity = pity("backpack" to MaterialPityCounter(2, false))))
        t = clearTarget(t, box("box-full", ChestTheme.BACKPACK, 0, 60))
        assertEquals(MaterialPityCounter(2, false), t.progress.materialPity.counters["backpack"])
    }

    @Test fun l22SuccessWinsOverMissInSameOutcome() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true,
            materialPity = pity("backpack" to MaterialPityCounter(2, false))))
        val out = clearTarget(s, box("box-both", ChestTheme.BACKPACK, 0, 60,
            listOf(ChestItem("backpack_fabric", 0, 0, 2))))
        assertEquals(MaterialPityCounter(0, false), out.progress.materialPity.counters["backpack"])
    }

    @Test fun l23ConsumedGuaranteeIsNotRefundedAndCountingRestarts() {
        val base = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true))
        val batch = journey.ensureBatch(base)
        val box = batch.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        var cur = batch.copy(progress = batch.progress.copy(
            materialPity = pity("backpack" to MaterialPityCounter(4, true))))
        cur = refreshOne(cur, box).first
        assertEquals(MaterialPityCounter(0, false), cur.progress.materialPity.counters["backpack"])
        // 再次刷新、放弃都不返还
        val current = cur.progress.chests.single { it.theme == ChestTheme.BACKPACK }
        cur = refreshOne(cur, current).first
        val withBox = cur.copy(progress = cur.progress.copy(
            chests = listOf(box("box-z", ChestTheme.BACKPACK, 0, 60))))
        cur = journey.abandon(journey.enter(withBox, "box-z", "juggernaut").state).state
        assertEquals(MaterialPityCounter(0, false), cur.progress.materialPity.counters["backpack"])
        // 之后合格未获得的通关重新计数
        repeat(4) { cur = clearTarget(cur, box("box-z$it", ChestTheme.BACKPACK, 0, 60)) }
        assertEquals(MaterialPityCounter(4, true), cur.progress.materialPity.counters["backpack"])
    }

    @Test fun l24DuplicateSettlementIsRejectedAndDoesNotDoubleCount() {
        var s = InventoryState(gold = 100000, progress = CampaignProgress(clears = 1, extracted = true))
        s = clearTarget(s, box("box-dup", ChestTheme.BACKPACK, 0, 60))
        val pity = s.progress.materialPity
        val blades = s.items.count { it.definitionId == "blade" }
        assertNull(s.progress.run)
        assertFalse(journey.reward(s, RewardChoice.GOLD).ok)
        assertFalse(journey.reward(s, RewardChoice.ITEM).ok)
        assertFalse(journey.extract(s).ok)
        assertFalse(journey.abandon(s).ok)
        assertEquals(pity, s.progress.materialPity)
        assertEquals(blades, s.items.count { it.definitionId == "blade" })
    }

    @Test fun l25SoleSafeNeedStillGetsDifficultyOneBoxAndCheapEntry() {
        val s = InventoryState(gold = 100000, progress = CampaignProgress(claimedQuests = unlockedNodes(), clears = 6, extracted = true))
            .copy(backpackWidth = 6, backpackHeight = 4, skillSlots = 8, safeWidth = 3, safeHeight = 2)
        val batch = journey.ensureBatch(s)
        val boxes = batch.progress.chests
        assertEquals(6, boxes.size)
        val safe = boxes.single { it.theme == ChestTheme.SAFE }
        assertTrue("SAFE 难度>=1", safe.difficulty >= 1)
        assertTrue("SAFE 票价>=150", safe.ticket >= 150)
        val cheap = boxes.single { it.difficulty == 0 }
        assertEquals(120, cheap.ticket)
        assertEquals(ChestTheme.SUPPLIES, cheap.theme)
        // 累计 5 次后第 6 次合格未获得 -> pending
        var t = s.copy(progress = s.progress.copy(
            materialPity = pity("safe" to MaterialPityCounter(5, false))))
        t = clearTarget(t, box("box-sfl", ChestTheme.SAFE, 1, 150))
        assertEquals(MaterialPityCounter(6, true), t.progress.materialPity.counters["safe"])
        // 兑现：刷新 SAFE 箱得到当前需要的框架
        val withChests = t.copy(progress = t.progress.copy(chests = boxes))
        val (state, newSafe, _) = refreshOne(withChests, safe)
        assertEquals(setOf("safe"), newSafe.guaranteedCategories)
        assertTrue(newSafe.items.any { it.definitionId == "safe_frame" })
        assertEquals(MaterialPityCounter(0, false), state.progress.materialPity.counters["safe"])
    }
}
