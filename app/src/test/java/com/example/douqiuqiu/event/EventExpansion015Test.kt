package com.example.douqiuqiu.event

import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * 0.1.5 事件扩充（8 → 16）：目录完整性、结局冻结、去重规则、存档往返与失败零改动。
 */
class EventExpansion015Test {

    private val inventory = InventoryRules(listOf(
        ItemDefinition("salve", "药膏", ItemKind.CONSUMABLE, 1, 1, maxStack = 3),
        ItemDefinition("gauntlets", "力量手套", ItemKind.EQUIPMENT, 1, 1),
        ItemDefinition("RG1", "怒意沸腾", ItemKind.CARD, 1, 1),
        ItemDefinition("RG2", "受创激怒", ItemKind.CARD, 1, 1),
        ItemDefinition("RG3", "狂战回响", ItemKind.CARD, 1, 1),
        ItemDefinition("FS1", "霜痕", ItemKind.CARD, 1, 1),
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("skill_shard", "刻片", ItemKind.MATERIAL, 1, 1)
    ).associateBy { it.id })

    private fun state(kind: EventKind, roll: Int = 0, hp: Double = 80.0,
                      items: List<StoredItem> = emptyList(), recent: List<EventKind> = emptyList()): InventoryState {
        val chest = TargetChest("target", "蓝", 0, 60, listOf(ChestItem("RG1", 0, 0)))
        val node = NodeEventInstance("run:event:1", kind, roll, "essence", 30)
        return InventoryState(runGold = 100, phase = InventoryPhase.PREPARATION, items = items,
            progress = CampaignProgress(clears = 2, run = JourneyState("run", "juggernaut", chest,
                1729L, hp, 100.0, stage = JourneyStage.EVENT, nodeEvent = node, recentEventKinds = recent)))
    }

    // ---------------- 目录完整性 ----------------

    @Test fun sixteenKindsHaveDefinitionsAndThreeOptions() {
        assertEquals(16, EventKind.entries.size)
        assertEquals(EventKind.entries.toSet(), EventCatalog.all.map { it.kind }.toSet())
        assertEquals(16, EventCatalog.all.size)
        for (kind in EventKind.entries) {
            val definition = EventCatalog.definition(kind)
            assertEquals("$kind 结局 ID 必须唯一", definition.options.size, definition.options.map { it.id }.toSet().size)
        }
        val newKinds = setOf(EventKind.FROZEN_SHRINE, EventKind.WAR_BANNER_RUINS, EventKind.ROVING_CARAVAN,
            EventKind.ROPEWAY_BRIDGE, EventKind.PACT_ALTAR, EventKind.ALCHEMY_LAB,
            EventKind.HUNTER_CAMP, EventKind.MOONWELL_ECHO)
        for (kind in newKinds) assertEquals("$kind 应固定三个结局", 3, EventCatalog.definition(kind).options.size)
    }

    @Test fun newEventArtKeysAreStableAndDistinct() {
        val expected = mapOf(
            EventKind.FROZEN_SHRINE to listOf("frozen_shrine.attune", "frozen_shrine.pray", "frozen_shrine.leave"),
            EventKind.WAR_BANNER_RUINS to listOf("war_banner.claim", "war_banner.blood_rite", "war_banner.leave"),
            EventKind.ROVING_CARAVAN to listOf("roving_caravan.buy", "roving_caravan.sell", "roving_caravan.depart"),
            EventKind.ROPEWAY_BRIDGE to listOf("ropeway_bridge.cross", "ropeway_bridge.repair", "ropeway_bridge.leave"),
            EventKind.PACT_ALTAR to listOf("pact_altar.offer_medicine", "pact_altar.offer_blood", "pact_altar.refuse"),
            EventKind.ALCHEMY_LAB to listOf("alchemy_lab.distill", "alchemy_lab.break_seal", "alchemy_lab.leave"),
            EventKind.HUNTER_CAMP to listOf("hunter_camp.barter", "hunter_camp.share_rations", "hunter_camp.leave"),
            EventKind.MOONWELL_ECHO to listOf("moonwell_echo.resonate", "moonwell_echo.draw_echo", "moonwell_echo.leave")
        )
        for ((kind, keys) in expected) {
            val actual = EventCatalog.definition(kind).options.map { EventCatalog.artKey(kind, it.id) }
            assertEquals(keys, actual)
        }
        // 旧八类无配图，键为空，不能误指向新图。
        assertEquals("", EventCatalog.artKey(EventKind.ABANDONED_DEPOT, "depot.search"))
    }

    // ---------------- 结局冻结与去重 ----------------

    @Test fun generatedOutcomesAreFrozenWithOutcomeIdAndArtKey() {
        val before = state(EventKind.FROZEN_SHRINE, items = listOf(
            StoredItem("frost", "FS1", ItemPlace(Container.SKILLS))))
        val run = before.progress.run!!
        val first = JourneyEvents.generate(before, run, inventory.definitions)
        val second = JourneyEvents.generate(before, run, inventory.definitions)
        assertEquals("同 seed 必须生成同一事件", first, second)
        val outcomes = first.outcomes.values.toList()
        assertEquals(3, outcomes.size)
        for ((optionId, outcome) in first.outcomes) {
            assertEquals(optionId, outcome.outcomeId)
            assertEquals("结局插画键必须与共享表一致",
                EventCatalog.artKey(first.kind, optionId), outcome.artKey)
        }
    }

    @Test fun recentTwoEventKindsAreNotRepeated() {
        val recent = listOf(EventKind.FROZEN_SHRINE, EventKind.WAR_BANNER_RUINS)
        val before = state(EventKind.FROZEN_SHRINE, items = listOf(
            StoredItem("frost", "FS1", ItemPlace(Container.SKILLS)),
            StoredItem("rage", "RG1", ItemPlace(Container.SKILLS, 1, 0))), recent = recent)
        for (seed in 1L..60L) {
            val run = before.progress.run!!.copy(rng = seed)
            val node = JourneyEvents.generate(before, run, inventory.definitions)
            assertFalse("最近两次的事件不应立刻重复: $node", node.kind in recent)
        }
    }

    // ---------------- 结算语义 ----------------

    @Test fun caravanSellConsumesChosenItemAndPaysItsPrice() {
        val before = state(EventKind.ROVING_CARAVAN,
            items = listOf(StoredItem("sale", "gauntlets", ItemPlace(Container.BACKPACK))))
        val result = JourneyEvents.resolve(before, inventory, "roving_caravan.sell", EventItemSelection("sale"))
        assertTrue(result.error, result.ok)
        assertTrue("物品应被卖出", result.state.items.none { it.instanceId == "sale" })
        assertTrue("应获得至少 1 金币", result.state.runGold > before.runGold)
    }

    @Test fun missingMedicineLeavesStateUntouched() {
        val before = state(EventKind.PACT_ALTAR)
        val result = JourneyEvents.resolve(before, inventory, "pact_altar.offer_medicine",
            EventItemSelection("none"))
        assertFalse(result.ok)
        assertEquals(before, result.state)
    }

    @Test fun moonwellResonateNeedsTwoTierSynergy() {
        val before = state(EventKind.MOONWELL_ECHO, items = listOf(
            StoredItem("c1", "RG1", ItemPlace(Container.SKILLS)),
            StoredItem("c2", "RG1", ItemPlace(Container.SKILLS, 1, 0))))
        val view = JourneyEvents.views(before, inventory).single { it.option.id == "moonwell_echo.resonate" }
        assertFalse("单张卡不构成两档羁绊", view.enabled)
        val before2 = before.copy(items = listOf(
            StoredItem("c1", "RG1", ItemPlace(Container.SKILLS)),
            StoredItem("c2", "RG2", ItemPlace(Container.SKILLS, 1, 0)),
            StoredItem("c3", "RG3", ItemPlace(Container.SKILLS, 2, 0))))
        val view2 = JourneyEvents.views(before2, inventory).single { it.option.id == "moonwell_echo.resonate" }
        assertTrue("三张同流派卡应达到两档羁绊", view2.enabled)
    }

    @Test fun breakSealAppliesPoisonAndGrantsComponent() {
        val before = state(EventKind.ALCHEMY_LAB, roll = 10)
        val result = JourneyEvents.resolve(before, inventory, "alchemy_lab.break_seal")
        assertTrue(result.error, result.ok)
        val frozen = result.state.progress.run
        assertTrue("破封应有生命代价或掉落", frozen == null || frozen.hp < before.progress.run!!.hp || frozen.nodeLoot != null)
    }

    // ---------------- 存档往返 ----------------

    @Test fun frozenOutcomesAndRecentKindsSurviveSaveRoundTrip() {
        val before = state(EventKind.FROZEN_SHRINE, items = listOf(
            StoredItem("frost", "FS1", ItemPlace(Container.SKILLS))),
            recent = listOf(EventKind.WAR_BANNER_RUINS, EventKind.PACT_ALTAR))
        val generated = JourneyEvents.generate(before, before.progress.run!!, inventory.definitions)
        val withNode = before.copy(progress = before.progress.copy(
            run = before.progress.run!!.copy(nodeEvent = generated)))
        val bytes = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use { CampaignBinaryCodec.write(it, withNode.progress) }
        }.toByteArray()
        val read = ByteArrayInputStream(bytes).use { DataInputStream(it).use { CampaignBinaryCodec.read(it, InventoryStore.VERSION) } }
        assertEquals(withNode.progress, read)
        val restored = read.run!!.nodeEvent!!
        assertEquals(generated.outcomes.keys, restored.outcomes.keys)
        for ((id, outcome) in generated.outcomes) {
            assertEquals(outcome.artKey, restored.outcomes.getValue(id).artKey)
            assertEquals(outcome.outcomeId, restored.outcomes.getValue(id).outcomeId)
        }
        assertEquals(listOf(EventKind.WAR_BANNER_RUINS, EventKind.PACT_ALTAR), read.run!!.recentEventKinds)
    }
}
