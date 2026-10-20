package com.example.douqiuqiu.inventory

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.content.EquipmentCategory
import com.example.douqiuqiu.equipment.EquipmentCooldownState
import com.example.douqiuqiu.event.EventCatalog
import com.example.douqiuqiu.event.EventCost
import com.example.douqiuqiu.event.EventKind
import com.example.douqiuqiu.event.EventOutcome
import com.example.douqiuqiu.event.NodeEventInstance
import com.example.douqiuqiu.journey.CampaignProgress
import com.example.douqiuqiu.journey.ChestItem
import com.example.douqiuqiu.journey.DuelState
import com.example.douqiuqiu.journey.EnemyBuilds
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.journey.JourneyEvents
import com.example.douqiuqiu.journey.JourneyRules
import com.example.douqiuqiu.journey.JourneyStage
import com.example.douqiuqiu.journey.JourneyState
import com.example.douqiuqiu.journey.RewardChoice
import com.example.douqiuqiu.journey.TargetChest
import com.example.douqiuqiu.status.PoisonCoreLayer
import com.example.douqiuqiu.status.PoisonCorePool
import com.example.douqiuqiu.status.PoisonKind
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32

/**
 * 28号 C 组：局内生命周期与旧档衔接验收（C01～C06）。
 *
 * 全部用例都经应用实际入口推进 [JourneyRules]（enter/startBattle/tick/reward/event/rest/
 * extract/abandon）与 [InventoryStore] 存档往返；概率内容用固定 seed（1729）与可解释轨迹，
 * 战斗以 100ms tick 推进。旧档夹具按 v8 真实字节布局独立书写，不改版本头、不复用既有夹具。
 */
class JourneyLifecycleAcceptanceTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private val defs = (EquipmentCatalog.all.map { d ->
        ItemDefinition(d.id, d.name,
            if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT,
            d.width, d.height, d.quality.label, stats = d.stats ?: EquipmentStats(), price = d.price)
    } + CardCombat.enabledCards.map {
        ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label)
    } + listOf(
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("skill_shard", "技能刻片", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("safe_lock", "保险锁", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("safe_frame", "保险箱架", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("backpack_fabric", "背包布料", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("salve", "治疗药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3),
        ItemDefinition("tango", "树之祭祀", ItemKind.CONSUMABLE, 1, 1, maxStack = 3),
        ItemDefinition("faerie_fire", "仙灵之火", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, combatUsable = true),
        ItemDefinition("cheese", "奶酪", ItemKind.CONSUMABLE, 1, 1, maxStack = 3, combatUsable = true)
    )).associateBy { it.id }
    private val rules = InventoryRules(defs)
    private val journey = JourneyRules(rules)
    private fun store(file: File) = InventoryStore(file, rules) { InventoryState() }

    /** 固定目标箱（难度 0，票价 60），内容真实可解析。 */
    private fun box(id: String = "box-fixed") = TargetChest(id, "蓝", 0, 60,
        listOf(ChestItem("crown", 0, 0), ChestItem("salve", 1, 0)))

    /** 强攻玩家构筑：3 代达罗斯之殇 + 3 撒旦之邪力（攻击 381、生命 2678、被动吸血 0.75）。 */
    private fun strongBars(): List<StoredItem> =
        listOf("daedalus", "daedalus", "daedalus", "satanic", "satanic", "satanic")
            .mapIndexed { i, id -> StoredItem("g$i", id, ItemPlace(Container.EQUIPMENT, i)) } +
            StoredItem("c0", "P1", ItemPlace(Container.SKILLS, 0), star = 3)

    /** 进入挑战后的 PREPARATION 状态（第 1 场 READY）。 */
    private fun entered(items: List<StoredItem> = strongBars()): InventoryState {
        val s = journey.ensureBatch(InventoryState(items = items))
        val chest = s.progress.chests.first()
        val result = journey.enter(s, chest.id, "juggernaut")
        assertTrue(result.error, result.ok)
        return result.state
    }

    private fun run(s: InventoryState) = s.progress.run!!

    /** 事件安全选项：优先零代价零变动的离开类；本局金币够时走交金币；最后才回落纯扣血。 */
    private fun safeOptionId(s: InventoryState): String {
        val node = run(s).nodeEvent!!
        val options = EventCatalog.definition(node.kind).options
        val free = options.firstOrNull { o ->
            val out = node.outcomes[o.id]
            o.cost == null && !o.requiresExplicitItemChoice && o.itemRequirement == null &&
                out != null && out.goldDelta == 0 && out.hpDeltaPercentOfMax >= 0.0 &&
                out.grantDefinitionIds.isEmpty() && out.consumeInstanceIds.isEmpty()
        }
        if (free != null) return free.id
        val paid = options.firstOrNull { o -> o.cost is EventCost.Gold && s.runGold >= node.priceGold }
        if (paid != null) return paid.id
        return options.first { o -> o.cost == null && (node.outcomes[o.id]?.hpDeltaPercentOfMax ?: 0.0) < 0.0 }.id
    }

    /**
     * 一局的完整驱动结果。[bankedRunGold] 是挑战终止前最后一个 run 非空状态的本局金币，
     * 即结算实际入库的量（撤离/通关带入，死亡不带回）。
     */
    private class Flow(val state: InventoryState, val lastRun: JourneyState?, val bankedRunGold: Int)

    private fun drive(s0: InventoryState, extractAtRest: Boolean): Flow {
        var s = s0
        var last: JourneyState? = null
        var banked = 0
        var guard = 0
        while (s.progress.run != null && guard++ < 20000) {
            last = s.progress.run
            val stage = s.progress.run!!.stage
            val goldBefore = s.gold
            s = when (stage) {
                JourneyStage.READY -> journey.startBattle(s).also { assertTrue(it.error, it.ok) }.state
                JourneyStage.BATTLE -> journey.tick(s)
                JourneyStage.REWARD -> LootFlowTestSupport.finishReward(journey, s).also { assertTrue(it.error, it.ok) }.state
                JourneyStage.EVENT -> {
                    val r = s.progress.run!!
                    if (r.nodeEvent == null) journey.event(s, false).also { assertTrue(it.error, it.ok) }.state
                    else journey.event(s, safeOptionId(s)).also {
                        assertTrue(it.error, it.ok)
                        assertTrue("事件结算后必须存活", it.state.progress.run!!.hp > 0.0)
                    }.state
                }
                JourneyStage.REST -> if (extractAtRest) journey.extract(s).also { assertTrue(it.error, it.ok) }.state
                    else journey.rest(s).also { assertTrue(it.error, it.ok) }.state
            }
            // 终止步（撤离/通关/死亡）在同一调用里结算：带入量按钱包增量计
            banked = if (s.progress.run == null) s.gold - goldBefore else s.runGold
            assertNull("stage=$stage 后状态必须合法", rules.validate(s))
        }
        return Flow(s, last, banked)
    }

    // ---------------- C01：新八事件与旧 boolean 接口 ----------------

    @Test fun eightEventNodeRefusesTheLegacyBooleanInterfaceWithoutTouchingAnything() {
        val base = InventoryState(gold = 1200, runGold = 40, phase = InventoryPhase.PREPARATION,
            items = listOf(StoredItem("g0", "daedalus", ItemPlace(Container.EQUIPMENT, 0))),
            progress = CampaignProgress(run = JourneyState("run-c01", "juggernaut", box("box-c01"), 1729,
                900.0, 1000.0, round = 2, stage = JourneyStage.EVENT, eventRoll = 25)))
        val node = JourneyEvents.generate(base, run(base), defs)
        val s = base.copy(progress = base.progress.copy(run = run(base).copy(nodeEvent = node)))
        assertNull(rules.validate(s))

        // 旧 boolean 搜索/放弃都被拒绝，且库存、本局金币、节点快照完全不变
        for (search in listOf(true, false)) {
            val result = journey.event(s, search)
            assertFalse("nodeEvent 存在时不得走旧 boolean 分支", result.ok)
            assertEquals("请按当前事件的具体选项结算", result.error)
            assertEquals(s, result.state)
        }
        assertEquals(40, s.runGold)
        assertEquals(node, run(s).nodeEvent)

        // 改走真实八事件选项后正常推进
        val resolved = journey.event(s, safeOptionId(s))
        assertTrue(resolved.error, resolved.ok)
        assertNull(run(resolved.state).nodeEvent)
        assertEquals(JourneyStage.READY, run(resolved.state).stage)
        assertEquals(3, run(resolved.state).round)
        assertNull(rules.validate(resolved.state))
    }

    @Test fun legacySupplyNodeWithoutSnapshotStillResolvesThroughTheBooleanInterface() {
        fun legacy(eventRoll: Int) = InventoryState(gold = 1200, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(run = JourneyState("run-c01b", "juggernaut", box("box-c01b"), 1729,
                900.0, 1000.0, round = 2, stage = JourneyStage.EVENT, pendingItem = "salve", eventRoll = eventRoll)))

        // roll=50 < 70：搜索命中，药膏先落地，生命不变，回合在整理后推进
        val hit = journey.event(legacy(50), true)
        assertTrue(hit.error, hit.ok)
        assertTrue("落地奖励不属于玩家", hit.state.items.isEmpty())
        val loot = run(hit.state).nodeLoot!!
        assertTrue(loot.items.any { it.definitionId == "salve" })
        assertEquals(900.0, run(hit.state).hp, 1e-9)
        assertEquals(2, run(hit.state).round)
        assertEquals(JourneyStage.REWARD, run(hit.state).stage)
        assertNull(rules.validate(hit.state))

        // roll=80 >= 70：搜索受伤，扣 10% 最大生命且不发物品
        val hurt = journey.event(legacy(80), true)
        assertTrue(hurt.error, hurt.ok)
        assertTrue(hurt.state.items.isEmpty())
        assertEquals(800.0, run(hurt.state).hp, 1e-9)
        assertEquals(3, run(hurt.state).round)

        // 放弃搜索：直接进入下一场，生命与库存不变
        val skip = journey.event(legacy(50), false)
        assertTrue(skip.error, skip.ok)
        assertTrue(skip.state.items.isEmpty())
        assertEquals(900.0, run(skip.state).hp, 1e-9)
        assertEquals(3, run(skip.state).round)
        assertEquals(JourneyStage.READY, run(skip.state).stage)
    }

    @Test fun lethalEventSettlesOnceThroughTheAppEntryAndKeepsOnlyTheSafeBox() {
        val nodeId = "run-c02:event:2"
        val node = NodeEventInstance(nodeId, EventKind.BLOCKER, roll = 70, priceGold = 40, outcomes = mapOf(
            "blocker.gold" to EventOutcome(nodeId, "blocker.gold", goldDelta = -40),
            "blocker.item" to EventOutcome(nodeId, "blocker.item"),
            "blocker.rush" to EventOutcome(nodeId, "blocker.rush", hpDeltaPercentOfMax = -25.0, possibleDeath = true)))
        val s = InventoryState(gold = 1200, runGold = 77, phase = InventoryPhase.PREPARATION,
            items = listOf(
                StoredItem("gear", "daedalus", ItemPlace(Container.EQUIPMENT, 0)),
                StoredItem("bp", "crown", ItemPlace(Container.BACKPACK, 0, 0)),
                StoredItem("safe", "salve", ItemPlace(Container.SAFE, 0, 0))),
            progress = CampaignProgress(run = JourneyState("run-c02", "juggernaut", box("box-c02"), 1729,
                10.0, 1000.0, round = 2, stage = JourneyStage.EVENT, nodeEvent = node)))
        assertNull(rules.validate(s))

        // 强闯扣 25% 最大生命（250）> 剩余 10 → 致死，统一走 abandon→settle
        val dead = journey.event(s, "blocker.rush")
        assertTrue(dead.error, dead.ok)
        assertNull("挑战必须终止", dead.state.progress.run)
        assertEquals(InventoryPhase.HOME, dead.state.phase)
        assertEquals(1200, dead.state.gold)
        assertEquals(0, dead.state.runGold)
        assertEquals(listOf("safe"), dead.state.items.map { it.instanceId })
        assertEquals(Container.WAREHOUSE, dead.state.items.single().place.container)
        assertEquals("挑战失败，仅安全箱物品带回", dead.state.progress.lastEnding)
        assertFalse(dead.state.progress.extracted)
        assertEquals(0, dead.state.progress.clears)

        // 重读、重结算都不再发奖或再扣一次
        val file = temp.root.resolve("c02.dat")
        store(file).save(dead.state)
        val loaded = store(file).load()
        assertEquals(dead.state, loaded.state)
        assertFalse(loaded.recoveredFromBackup)
        for (again in listOf(
            journey.abandon(loaded.state), journey.event(loaded.state, "blocker.rush"),
            journey.reward(loaded.state, RewardChoice.GOLD), journey.extract(loaded.state))) {
            assertFalse(again.ok)
            assertEquals(loaded.state, again.state)
        }
        store(file).save(loaded.state)
        assertEquals(loaded.state, store(file).load().state)
    }

    // ---------------- C03：预览敌人到实际开战，再存档恢复 ----------------

    @Test fun previewedEnemyMatchesTheRealBattleAndSurvivesSaveReloadWithoutRerolling() {
        val s = entered()
        val before = run(s)
        val build = EnemyBuilds.preview(before, defs)

        val started = journey.startBattle(s).also { assertTrue(it.error, it.ok) }.state
        val fight = run(started).fight!!
        // 英雄、主要流派、卡/装备构筑与预览完全一致
        assertEquals(build.heroId, fight.enemy.heroId)
        val previewCards = build.inventory.items.filter { it.place.container == Container.SKILLS }
            .map { it.definitionId to it.star }
        assertEquals(previewCards, fight.enemy.cardState.cards.map { it.cardId to it.star })
        val previewGear = build.inventory.items.filter { it.place.container == Container.EQUIPMENT }
            .map { it.definitionId }
        assertEquals(previewGear, fight.enemy.equipment.items.map { it.definitionId })
        // 主要流派落在敌人实际持卡上（EnemyBuilds 先按流派选卡）
        val firstCardId = fight.enemy.cardState.cards.first().cardId
        assertTrue("主要流派 $build.schoolId 应体现在首张敌人卡上",
            build.schoolId in CardCombat.definitions.getValue(firstCardId).tags)
        // 玩家按实际技能栏/生效装备栏构筑并真实生效
        val playerCards = s.items.filter { it.place.container == Container.SKILLS }
            .map { it.instanceId to it.definitionId to it.star }
        assertEquals(playerCards, fight.player.cardState.cards.map { it.instanceId to it.cardId to it.star })
        val playerGear = s.items.filter { it.place.container == Container.EQUIPMENT }
            .map { it.instanceId to it.definitionId }
        assertEquals(playerGear, fight.player.equipment.items.map { it.instanceId to it.definitionId })
        assertTrue(fight.player.attack > FirstRunContent.hero("juggernaut").baseAttack.toDouble())

        // 开战不改玩家资产
        assertEquals(s.gold, started.gold)
        assertEquals(s.items, started.items)

        // 存档恢复：敌人不重抽、战斗与未保存支路逐 tick 一致
        val file = temp.root.resolve("c03.dat")
        store(file).save(started)
        val loaded = store(file).load().state
        assertEquals(started, loaded)
        assertEquals(fight.enemy, run(loaded).fight!!.enemy)
        assertEquals(journey.tick(started), journey.tick(loaded))

        // 真实生效证据：数 tick 后敌人真的在攻击，玩家的毒卡也真的附到了敌人身上
        var stepped = loaded
        repeat(6) { stepped = journey.tick(stepped) }
        val live = run(stepped).fight!!
        if (live.won == null) {
            assertTrue("敌人必须真实普攻", live.enemy.cardState.attackCount >= 1)
            assertTrue("玩家毒卡必须真实附毒", live.enemy.poisonPool?.normal?.isNotEmpty() == true)
        }
    }

    // ---------------- C04：第三战撤离 / 第五战通关完整流程 ----------------

    @Test fun extractionAfterTheThirdBattleBanksRunGoldAndNeverCarriesAnythingIntoTheNextRun() {
        val s = entered()
        val enteredGold = s.gold
        val enteredItems = s.items.size
        val targetId = run(s).target.id

        val flow = drive(s, extractAtRest = true)
        val end = flow.state
        assertNull("第三战后必须可以终止挑战", end.progress.run)
        assertEquals(InventoryPhase.HOME, end.phase)
        assertTrue(end.progress.extracted)
        assertEquals(0, end.progress.clears)
        assertEquals("成功撤离，物资和本局金币已入库", end.progress.lastEnding)
        // 途中所得全部回仓；本局金币按带入量原样入库
        assertEquals(enteredItems, end.items.size)
        assertTrue(end.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals(enteredGold + flow.bankedRunGold, end.gold)
        assertTrue("撤离必须带回本局金币", flow.bankedRunGold > 0)
        assertEquals(0, end.runGold)

        // 重复结算：库存与金币不变
        val file = temp.root.resolve("c04a.dat")
        store(file).save(end)
        val loaded = store(file).load().state
        assertEquals(end, loaded)
        for (again in listOf(journey.extract(loaded), journey.abandon(loaded), journey.rest(loaded))) {
            assertFalse(again.ok)
            assertEquals(loaded, again.state)
        }

        // 下一局：事件快照与跨战 CD 不串用，RNG 已前进
        val next = journey.ensureBatch(loaded)
        val nextRun = run(journey.enter(next, next.progress.chests.first().id, "juggernaut").state)
        assertEquals(JourneyStage.READY, nextRun.stage)
        assertNull(nextRun.nodeEvent)
        assertEquals(EquipmentCooldownState(), nextRun.equipmentCooldowns)
        assertEquals(1, nextRun.round)
        assertNotEquals(targetId, nextRun.target.id)
    }

    @Test fun clearingAllFiveBattlesBanksTheTargetChestAndKeepsSettlementIdempotent() {
        val s = entered()
        val enteredGold = s.gold
        val enteredItems = s.items.size
        val targetItems = run(s).target.items.size

        val flow = drive(s, extractAtRest = false)
        val end = flow.state
        assertNull(end.progress.run)
        assertEquals(InventoryPhase.HOME, end.phase)
        assertEquals(1, end.progress.clears)
        assertTrue(end.progress.extracted)
        assertEquals("全部通关，目标宝箱和本局物资已入库", end.progress.lastEnding)
        // 最终奖励与目标箱按规则带出，全部回仓
        assertEquals(enteredItems + targetItems, end.items.size)
        assertTrue(end.items.all { it.place.container == Container.WAREHOUSE })
        assertTrue(end.items.any { it.instanceId.startsWith("${run(s).id}:chest:") })
        assertEquals(enteredGold + flow.bankedRunGold, end.gold)
        assertTrue("通关必须带回本局金币", flow.bankedRunGold > 0)
        assertEquals(0, end.runGold)

        // 重复结算与重读：库存、金币、通关数不变
        val file = temp.root.resolve("c04b.dat")
        store(file).save(end)
        val loaded = store(file).load().state
        assertEquals(end, loaded)
        for (again in listOf(journey.abandon(loaded), journey.extract(loaded),
            journey.reward(loaded, RewardChoice.ITEM))) {
            assertFalse(again.ok)
            assertEquals(loaded, again.state)
        }
        store(file).save(loaded)
        val twice = store(file).load().state
        assertEquals(1, twice.progress.clears)
        assertEquals(loaded.gold, twice.gold)
        assertEquals(loaded.items.size, twice.items.size)

        // 下一局从零开始：默认 CD、无事件快照
        val next = journey.ensureBatch(loaded)
        val nextRun = run(journey.enter(next, next.progress.chests.first().id, "juggernaut").state)
        assertEquals(EquipmentCooldownState(), nextRun.equipmentCooldowns)
        assertNull(nextRun.nodeEvent)
    }

    // ---------------- C05：独立真实旧 v8 夹具 ----------------

    /** v8 READY 夹具：第 2 场整备界面，带真实生效装备栏与技能栏。v8 布局不持久化跨战 CD，固定写空态。 */
    private fun v8Ready(file: File, round: Int): InventoryState {
        val items = listOf(
            StoredItem("g0", "black_king_bar", ItemPlace(Container.EQUIPMENT, 0)),
            StoredItem("g1", "daedalus", ItemPlace(Container.EQUIPMENT, 1)),
            StoredItem("g2", "butterfly", ItemPlace(Container.EQUIPMENT, 2)),
            StoredItem("g3", "satanic", ItemPlace(Container.EQUIPMENT, 3)),
            StoredItem("c0", "P1", ItemPlace(Container.SKILLS, 0), star = 2))
        val s = InventoryState(gold = 1200, phase = InventoryPhase.PREPARATION, items = items,
            progress = CampaignProgress(run = JourneyState("run-v8r", "juggernaut", box("box-v8r"), 1729,
                900.0, 1000.0, round = round, stage = JourneyStage.READY,
                equipmentCooldowns = EquipmentCooldownState())))
        assertNull(rules.validate(s))
        writeV8(file, s)
        return store(file).load().state
    }

    @Test fun v8ReadySaveContinuesToTheNextBattleLegallyAndRebuildsGearFromTheLiveBars() {
        val file = temp.root.resolve("v8-ready.dat")
        val loaded = v8Ready(file, round = 2)
        // 旧档没有跨战 CD 记录：默认 lastPreparedBattle = 0
        assertEquals(0, loaded.progress.run!!.equipmentCooldowns.lastPreparedBattle)

        val build = EnemyBuilds.preview(loaded.progress.run!!, defs)
        val started = journey.startBattle(loaded)
        assertTrue(started.error, started.ok)
        val fight = run(started.state).fight!!
        assertEquals(2, run(started.state).round)
        assertEquals(JourneyStage.BATTLE, run(started.state).stage)
        // 旧档迁移只盖一次章：0 →（第 1 场）→ 第 2 场整备，不抛「跳号」
        assertEquals(2, fight.player.equipment.cooldowns.lastPreparedBattle)
        // 新场从实际生效栏重建装备与技能
        assertEquals(loaded.items.filter { it.place.container == Container.EQUIPMENT }
            .map { it.instanceId to it.definitionId }, fight.player.equipment.items.map { it.instanceId to it.definitionId })
        assertEquals(listOf(Triple("c0", "P1", 2)), fight.player.cardState.cards.map { Triple(it.instanceId, it.cardId, it.star) })
        // 上一场（v8 未持久化）的激活效果不注入新场
        assertTrue(fight.player.equipment.activeGroups.isEmpty())
        assertEquals(0L, fight.player.equipment.immunityMs)
        // 敌人按第 2 场预算真实生成，且与开战前预览一致
        assertEquals(build.heroId, fight.enemy.heroId)
        assertNull(rules.validate(started.state))
    }

    @Test fun v8ReadySaveReadsNoFabricatedCrossBattleCooldown() {
        val file = temp.root.resolve("v8-ready-cd.dat")
        // v8 不持久化跨战 CD：读回必须是「空记录」，而不是编造出任何冷却
        val loaded = v8Ready(file, round = 2)
        assertEquals(EquipmentCooldownState(), run(loaded).equipmentCooldowns)
        assertEquals(0, run(loaded).equipmentCooldowns.lastPreparedBattle)
        assertTrue(run(loaded).equipmentCooldowns.remainingByGroup.isEmpty())

        // 直接从读回态开战：不注入任何遗留冷却，只盖一次整备章（0 → 2，不抛跳号）
        val started = journey.startBattle(loaded).also { assertTrue(it.error, it.ok) }.state
        val cooldowns = run(started).fight!!.player.equipment.cooldowns
        assertEquals(2, cooldowns.lastPreparedBattle)
        assertTrue("旧档读回不得虚构冷却", cooldowns.remainingByGroup.isEmpty())

        // 整备章只推进一次：立刻再次开战不被允许（同一场不能重复整备）
        val again = journey.startBattle(started)
        assertFalse(again.ok)
    }

    @Test fun v9SaveCarriesCrossBattleCooldownAndDeductsItExactlyOnce() {
        val file = temp.root.resolve("v9-carry-cd.dat")
        // v9 持久化跨战 CD：上一场遗留 BKB 冷却 45 秒 + 已整备到第 1 场
        val items = listOf(StoredItem("g0", "black_king_bar", ItemPlace(Container.EQUIPMENT, 0)))
        val s = InventoryState(gold = 1200, phase = InventoryPhase.PREPARATION, items = items,
            progress = CampaignProgress(run = JourneyState("run-v9cd", "juggernaut", box("box-v9cd"), 1729,
                900.0, 1000.0, round = 2, stage = JourneyStage.READY,
                equipmentCooldowns = EquipmentCooldownState(mapOf("bkb" to 45000L), 1))))
        assertNull(rules.validate(s))
        store(file).save(s)
        val loaded = store(file).load().state
        // 持久化往返：45 秒与场次原样保留（v8 做不到，这正是 v8/v9 分界）
        assertEquals(EquipmentCooldownState(mapOf("bkb" to 45000L), 1), run(loaded).equipmentCooldowns)

        // 开战整备：每场只抵扣一次 30 秒：45000 → 15000，不是 0 也不是翻倍
        val started = journey.startBattle(loaded).also { assertTrue(it.error, it.ok) }.state
        val cooldowns = run(started).fight!!.player.equipment.cooldowns
        assertEquals(2, cooldowns.lastPreparedBattle)
        assertEquals(15000L, cooldowns.remainingByGroup["bkb"])

        // 存档往返不触发重新整备：CD 原样保留，下一 tick 与未保存支路一致
        val file2 = temp.root.resolve("v9-carry-cd2.dat")
        store(file2).save(started)
        val reloaded = store(file2).load().state
        assertEquals(15000L, run(reloaded).fight!!.player.equipment.cooldowns.remainingByGroup["bkb"])
        assertEquals(journey.tick(started), journey.tick(reloaded))
    }

    @Test fun v8RewardSaveKeepsTheFixedRunGoldPriceAndTheGeneratedEventSnapshot() {
        val file = temp.root.resolve("v8-reward.dat")
        val items = listOf(StoredItem("g0", "daedalus", ItemPlace(Container.EQUIPMENT, 0)))
        val s = InventoryState(gold = 1200, runGold = 5, phase = InventoryPhase.PREPARATION, items = items,
            progress = CampaignProgress(run = JourneyState("run-v8w", "juggernaut", box("box-v8w"), 1729,
                800.0, 1000.0, round = 2, stage = JourneyStage.REWARD)))
        assertNull(rules.validate(s))
        writeV8(file, s)
        val loaded = store(file).load().state
        assertEquals(5, loaded.runGold)

        // 第 2 场难度 0 奖励固定 30 本局金币
        val rewarded = LootFlowTestSupport.finishReward(journey, loaded)
        assertTrue(rewarded.error, rewarded.ok)
        assertEquals(35, rewarded.state.runGold)
        assertEquals(JourneyStage.EVENT, run(rewarded.state).stage)
        val node = run(rewarded.state).nodeEvent!!
        assertEquals("run-v8w:event:2", node.nodeId)
        assertEquals(2, run(rewarded.state).round)
        assertEquals(40, node.priceGold)
        assertNull(rules.validate(rewarded.state))

        // 离开类选项结算后进入第 3 场整备，场号合法
        val resolved = journey.event(rewarded.state, safeOptionId(rewarded.state))
        assertTrue(resolved.error, resolved.ok)
        assertNull(run(resolved.state).nodeEvent)
        assertEquals(3, run(resolved.state).round)
        assertEquals(JourneyStage.READY, run(resolved.state).stage)
        val started = journey.startBattle(resolved.state)
        assertTrue(started.error, started.ok)
        assertEquals(3, run(started.state).fight!!.player.equipment.cooldowns.lastPreparedBattle)
        assertNull(rules.validate(started.state))
    }

    @Test fun v8RestSaveRestsToRoundFourAndCanStillExtractWithEverythingBanked() {
        val file = temp.root.resolve("v8-rest.dat")
        val items = listOf(
            StoredItem("g0", "daedalus", ItemPlace(Container.EQUIPMENT, 0)),
            StoredItem("bp", "crown", ItemPlace(Container.BACKPACK, 0, 0)),
            StoredItem("safe", "salve", ItemPlace(Container.SAFE, 0, 0)))
        val s = InventoryState(gold = 1200, runGold = 90, phase = InventoryPhase.PREPARATION, items = items,
            progress = CampaignProgress(run = JourneyState("run-v8s", "juggernaut", box("box-v8s"), 1729,
                500.0, 1000.0, round = 3, stage = JourneyStage.REST)))
        assertNull(rules.validate(s))
        writeV8(file, s)
        val loaded = store(file).load().state

        // 休息：回到第 4 场并恢复 35% 最大生命（500 + 350）
        val rested = journey.rest(loaded)
        assertTrue(rested.error, rested.ok)
        assertEquals(4, run(rested.state).round)
        assertEquals(JourneyStage.READY, run(rested.state).stage)
        assertEquals(850.0, run(rested.state).hp, 1e-9)
        val started = journey.startBattle(rested.state)
        assertTrue(started.error, started.ok)
        assertEquals(4, run(started.state).fight!!.player.equipment.cooldowns.lastPreparedBattle)
        assertNull(rules.validate(started.state))

        // 旧档同样可以直接撤离：物资与本局金币入库
        val extracted = journey.extract(loaded)
        assertTrue(extracted.error, extracted.ok)
        assertNull(extracted.state.progress.run)
        assertEquals(1290, extracted.state.gold)
        assertEquals(0, extracted.state.runGold)
        assertTrue(extracted.state.progress.extracted)
        assertEquals(3, extracted.state.items.size)
        assertTrue(extracted.state.items.all { it.place.container == Container.WAREHOUSE })
        assertEquals("成功撤离，物资和本局金币已入库", extracted.state.progress.lastEnding)
    }

    @Test fun v8MidBattleSaveKeepsItsPoisonPoolButNeverInventsEquipmentEffects() {
        val file = temp.root.resolve("v8-battle.dat")
        // 旧档战斗快照：玩家 Viper 带真实毒池（普通毒 1 层 + 剧毒 1 层），双方都不行动
        val pool = PoisonCorePool(
            normal = listOf(PoisonCoreLayer("p:1:7:0", PoisonKind.NORMAL, "hero:viper:skin", "1", 6.0, 3, 2100L)),
            virulent = listOf(PoisonCoreLayer("p:1:8:0", PoisonKind.VIRULENT, "hero:viper:ultimate", "1", 3.0, 0, 2100L)))
        val player = journey.fighter(InventoryState(), "viper")
            .copy(hp = 4000.0, maxHp = 4000.0, mana = 0.0, manaRegen = 0.0, regen = 0.0,
                attackIn = 999.0, ultIn = 999.0, poisonPool = pool, poisonSerial = 9)
        val enemy = journey.fighter(InventoryState(), "juggernaut")
            .copy(hp = 100000.0, maxHp = 100000.0, mana = 0.0, manaRegen = 0.0, regen = 0.0,
                attackIn = 999.0, ultIn = 999.0)
        val items = listOf(
            StoredItem("g0", "daedalus", ItemPlace(Container.EQUIPMENT, 0)),
            StoredItem("g1", "black_king_bar", ItemPlace(Container.EQUIPMENT, 1)))
        val s = InventoryState(gold = 1200, phase = InventoryPhase.BATTLE, items = items,
            progress = CampaignProgress(run = JourneyState("run-v8b", "viper", box("box-v8b"), 1729,
                4000.0, 4000.0, round = 3, stage = JourneyStage.BATTLE,
                fight = DuelState(player, enemy, 1729, tick = 20))))
        assertNull(rules.validate(s))
        writeV8(file, s)
        val loaded = store(file).load().state
        val fight = run(loaded).fight!!

        // v8 快照保留毒池与场次，但不携带装备栏
        assertEquals(20, fight.tick)
        assertEquals(pool, fight.player.poisonPool)
        assertEquals(9, fight.player.poisonSerial)
        assertTrue(fight.player.equipment.items.isEmpty())
        assertTrue(fight.player.equipment.activeGroups.isEmpty())
        assertEquals(items, loaded.items)

        // 推进一个 tick：毒核心按真实规则结算（普通毒跳 1 次、剧毒产出 1 层），
        // 中途旧战斗不会因为状态里有装备就注入新效果
        val stepped = journey.tick(loaded)
        val after = run(stepped).fight!!
        val afterPool = after.player.poisonPool!!
        assertEquals(2, afterPool.normal.size)
        assertEquals(1, afterPool.virulent.size)
        val original = afterPool.normal.single { it.instanceId == "p:1:7:0" }
        assertEquals(2, original.ticksRemaining)
        assertEquals(3100L, original.nextTickAtMs)
        val spawned = afterPool.normal.single { it.instanceId != "p:1:7:0" }
        assertEquals("p:1:8:0~2100", spawned.instanceId)
        assertEquals(3.0, spawned.damagePerTick, 1e-9)
        assertTrue(after.player.hp < fight.player.hp)
        assertTrue(after.player.equipment.items.isEmpty())
        assertTrue(after.player.equipment.activeGroups.isEmpty())

        // 当前v10重存后往返一致，后续tick与未保存支路一致
        val file2 = temp.root.resolve("v8-battle-9.dat")
        store(file2).save(loaded)
        val reloaded = store(file2).load().state
        assertEquals(loaded, reloaded)
        assertEquals(journey.tick(loaded), journey.tick(reloaded))
        DataInputStream(FileInputStream(file2)).use { it.readInt(); assertEquals(InventoryStore.VERSION, it.readInt()) }
    }

    // ---------------- C06：v9 非零保护 / 毒池 / PRD / 手动状态同时保存 ----------------

    @Test fun v9SaveRoundTripsNonZeroProtectionPoisonPoolPrdAndManualStateTogether() {
        val items = listOf(
            StoredItem("bkb", "black_king_bar", ItemPlace(Container.EQUIPMENT, 0)),
            StoredItem("crit", "daedalus", ItemPlace(Container.EQUIPMENT, 1)),
            StoredItem("staff", "force_staff", ItemPlace(Container.EQUIPMENT, 2)))
        val pool = PoisonCorePool(
            normal = listOf(PoisonCoreLayer("p:1:7:0", PoisonKind.NORMAL, "hero:viper:skin", "1", 6.0, 3, 2100L)),
            virulent = listOf(PoisonCoreLayer("p:1:8:0", PoisonKind.VIRULENT, "hero:viper:ultimate", "1", 3.0, 0, 2100L)))
        val base = journey.fighter(InventoryState(items = items), "juggernaut")
        val player = base.copy(hp = 5000.0, maxHp = 5000.0, poisonPool = pool, poisonSerial = 9,
            equipment = base.equipment.copy(
                immunityMs = 2000L,
                protectionMs = 1500L,
                cooldowns = EquipmentCooldownState(mapOf("bkb" to 11000L), 1),
                activeGroups = mapOf("bkb" to 2000L),
                started = true,
                manual = setOf("bkb"),
                criticalFailures = mapOf("gear:crit:daedalus.crit" to 2)))
        val enemy = journey.fighter(InventoryState(), "phantom_assassin").copy(hp = 5000.0, maxHp = 5000.0)
        val s = InventoryState(gold = 1200, phase = InventoryPhase.BATTLE, items = items,
            progress = CampaignProgress(manualItems = setOf("bkb"),
                run = JourneyState("run-c06", "juggernaut", box("box-c06"), 1729,
                    5000.0, 5000.0, round = 3, stage = JourneyStage.BATTLE,
                    fight = DuelState(player, enemy, 1729, tick = 20),
                    equipmentCooldowns = player.equipment.cooldowns)))
        assertNull(rules.validate(s))

        val file = temp.root.resolve("c06.dat")
        store(file).save(s)
        val loaded = store(file).load().state
        assertEquals(s, loaded)
        val gear = run(loaded).fight!!.player.equipment
        // 非零保护与全部并发现态逐一往返相等（不只默认 0）
        assertEquals(1500L, gear.protectionMs)
        assertEquals(2000L, gear.immunityMs)
        assertEquals(mapOf("bkb" to 2000L), gear.activeGroups)
        assertEquals(mapOf("bkb" to 11000L), gear.cooldowns.remainingByGroup)
        assertEquals(setOf("bkb"), gear.manual)
        assertEquals(setOf("bkb"), loaded.progress.manualItems)
        assertEquals(mapOf("gear:crit:daedalus.crit" to 2), gear.criticalFailures)
        assertEquals(pool, run(loaded).fight!!.player.poisonPool)
        assertEquals(9, run(loaded).fight!!.player.poisonSerial)

        // 下一 tick 与未保存支路完全一致；毒池真实参与结算
        assertEquals(journey.tick(s), journey.tick(loaded))
        var stepped = loaded
        repeat(3) { stepped = journey.tick(stepped) }
        val after = run(stepped).fight!!
        if (after.won == null) {
            val afterPool = after.player.poisonPool!!
            assertTrue("保存的毒池必须真实推进", afterPool.normal.any { it.ticksRemaining < 3 })
            assertEquals(1, afterPool.virulent.size)
        }
    }

    // ---------------- v8 夹具字节布局（独立书写，不使用 v9 编解码器） ----------------

    private fun writeV8(file: File, s: InventoryState) {
        val r = s.progress.run!!
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(s.gold); out.writeInt(s.runGold)
            listOf(s.backpackWidth, s.backpackHeight, s.safeWidth, s.safeHeight, s.skillSlots).forEach(out::writeInt)
            out.writeUTF(s.phase.name); out.writeInt(s.items.size)
            s.items.forEach { i -> out.writeUTF(i.instanceId); out.writeUTF(i.definitionId); out.writeUTF(i.place.container.name)
                out.writeInt(i.place.x); out.writeInt(i.place.y); out.writeBoolean(i.place.rotated)
                out.writeInt(i.star); out.writeBoolean(i.bound); out.writeInt(i.count) }
            val p = s.progress
            out.writeLong(p.seed); out.writeInt(p.batch); out.writeInt(p.clears); out.writeBoolean(p.extracted)
            out.writeUTF(p.lastEnding); out.writeLong(p.shopCycle); out.writeLong(p.shopSeen)
            out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeBoolean(true)
            out.writeUTF(r.id); out.writeUTF(r.heroId)
            with(r.target) { out.writeUTF(id); out.writeUTF(quality); out.writeInt(difficulty); out.writeInt(ticket); out.writeInt(appraisals)
                out.writeInt(items.size); items.forEach { out.writeUTF(it.definitionId); out.writeInt(it.x); out.writeInt(it.y); out.writeInt(it.reveal) }
                out.writeUTF(theme.name); out.writeInt(0) }
            out.writeLong(r.rng); out.writeDouble(r.hp); out.writeDouble(r.maxHp); out.writeInt(r.round); out.writeUTF(r.stage.name)
            out.writeUTF(r.pendingItem); out.writeInt(r.eventRoll)
            val fight = r.fight
            if (fight != null) {
                out.writeBoolean(true)
                fighterV8(out, fight.player); fighterV8(out, fight.enemy)
                out.writeLong(fight.rng); out.writeInt(fight.tick); out.writeInt(-1); out.writeInt(0)
            } else out.writeBoolean(false)
            with(p.economy) { out.writeBoolean(initialized); out.writeInt(refreshCharges); out.writeLong(refreshAnchorMs)
                out.writeInt(reliefCharges); out.writeLong(reliefAnchorMs); out.writeInt(reliefClaims); out.writeInt(vouchers) }
            out.writeBoolean(false)
            out.writeInt(p.unlockedHeroes.size); p.unlockedHeroes.sorted().forEach(out::writeUTF)
            out.writeInt(0)
        }
        val payload = bytes.toByteArray()
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x44515131); out.writeInt(8); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        }
    }

    private fun fighterV8(out: DataOutputStream, f: Fighter) {
        out.writeUTF(f.heroId)
        listOf(f.hp, f.maxHp, f.mana, f.maxMana, f.attack, f.speed, f.bat, f.armor, f.magicResist, f.regen, f.manaRegen,
            f.shield, f.magicShield, f.skyShield, f.attackIn, f.ultIn, f.invulnerable, f.ghost, f.slashIn, f.criticalReady,
            f.broken, f.slowTime, f.slow, f.skinIn, f.flareIn).forEach(out::writeDouble)
        out.writeInt(f.flareLeft); out.writeInt(f.poisons.size)
        f.poisons.forEach { out.writeDouble(it.dps); out.writeDouble(it.remaining); out.writeUTF(it.source); out.writeBoolean(it.dispellable) }
        out.writeDouble(f.skinSlowTime)
        out.writeDouble(f.physicalShield); out.writeBoolean(f.shieldSources != null)
        f.shieldSources?.let { pool ->
            out.writeInt(pool.entries.size)
            pool.entries.forEach { entry ->
                out.writeUTF(entry.sourceId); out.writeUTF(entry.kind.name); out.writeDouble(entry.amount)
                out.writeBoolean(entry.remainingMs != null); entry.remainingMs?.let(out::writeLong)
            }
        }
        with(f.control) { listOf(dead, banished, stunned, hexed, silenced, muted, disarmed).forEach(out::writeBoolean) }
        with(f.cardState) {
            out.writeInt(cards.size)
            cards.forEach { out.writeUTF(it.instanceId); out.writeUTF(it.cardId); out.writeInt(it.star) }
            out.writeBoolean(started); out.writeInt(attackCount)
            out.writeInt(readyAt.size); readyAt.keys.sorted().forEach { out.writeUTF(it); out.writeInt(readyAt.getValue(it)) }
            out.writeInt(healed.size); healed.keys.sorted().forEach { out.writeUTF(it); out.writeDouble(healed.getValue(it)) }
            out.writeInt(buffs.size); buffs.forEach { out.writeUTF(it.sourceKey); out.writeUTF(it.kind.name); out.writeDouble(it.amount); out.writeInt(it.expiresAtTick) }
            out.writeInt(criticalFailures.size); criticalFailures.keys.sorted().forEach { out.writeUTF(it); out.writeInt(criticalFailures.getValue(it)) }
        }
        out.writeInt(f.poisonSerial)
        val pool = f.poisonPool
        if (pool != null) {
            out.writeBoolean(true)
            out.writeInt(pool.normal.size); pool.normal.forEach { poisonLayerV8(out, it) }
            out.writeInt(pool.virulent.size); pool.virulent.forEach { poisonLayerV8(out, it) }
        } else out.writeBoolean(false)
        out.writeLong(f.equipment.immunityMs)
    }

    private fun poisonLayerV8(out: DataOutputStream, layer: PoisonCoreLayer) {
        out.writeUTF(layer.instanceId); out.writeUTF(layer.kind.name); out.writeUTF(layer.sourceId); out.writeUTF(layer.casterUnitId)
        out.writeDouble(layer.damagePerTick); out.writeInt(layer.ticksRemaining); out.writeLong(layer.nextTickAtMs)
        out.writeBoolean(layer.piercesDebuffImmunity)
    }
}
