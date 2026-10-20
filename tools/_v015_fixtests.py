import io

p = "app/src/test/java/com/example/douqiuqiu/acceptance/AcceptanceFixtureTest.kt"
s = io.open(p, encoding="utf-8").read()
start = s.index("    @Test fun s04BothCapacityRefusalsAreAtomicThenAbandonAdvancesWithoutGrantingItems() {")
end = s.index("\n}", start)
new = '''    @Test fun s04FullBackpackKeepsEventLootOnGroundThenFinishAdvancesWithoutGrantingItems() {
        val filled = (0..1).flatMap { y -> (0..3).map { x -> StoredItem("fill:$x:$y", "iron_branch", ItemPlace(Container.BACKPACK, x, y)) } }
        val nodeId = "full:event:4"
        val node = NodeEventInstance(nodeId, EventKind.SAFE_WRECKAGE, 0, outcomes = mapOf(
            "wreckage.core" to EventOutcome(nodeId, "wreckage.core", grantDefinitionIds = listOf("safe_lock")),
            "wreckage.salvage" to EventOutcome(nodeId, "wreckage.salvage", grantDefinitionIds = listOf("essence")),
            "wreckage.abandon" to EventOutcome(nodeId, "wreckage.abandon")))
        val s = InventoryState(items = filled, phase = InventoryPhase.PREPARATION, runGold = 99,
            progress = CampaignProgress(clears = 1, run = JourneyState("full", "juggernaut",
                TargetChest("target", "白", 0, 120, listOf(ChestItem("iron_branch", 0, 0))), 1729,
                1000.0, 1000.0, round = 4, stage = JourneyStage.EVENT, nodeEvent = node)))
        assertNull(inventory.validate(s))
        // 现行规则：事件奖励落到地面，背包满不阻止结算，也不改动任何已持有物品。
        val resolved = journey.event(s, "wreckage.core")
        assertTrue(resolved.error, resolved.ok)
        assertEquals(s.items, resolved.state.items)
        assertEquals(s.gold, resolved.state.gold)
        val loot = resolved.state.progress.run!!.nodeLoot!!
        assertTrue(loot.items.any { it.definitionId == "safe_lock" })
        assertEquals(s.runGold + loot.goldAwarded, resolved.state.runGold)
        assertEquals(JourneyStage.REWARD, resolved.state.progress.run!!.stage)
        assertFalse("已结算的事件不能重复领奖", journey.event(resolved.state, "wreckage.core").ok)
        assertFalse("已结算的事件不能改走放弃分支", journey.event(resolved.state, "wreckage.abandon").ok)
        // 整理完地面掉落才推进：事件节点 +1 回合并回到整备。
        var ready = resolved.state
        while (NodeLootRules.duration(ready.progress.run!!.nodeLoot!!, inventory.definitions) > ready.progress.run!!.nodeLoot!!.inspectionMs) {
            ready = NodeLootRules.inspect(ready, inventory, 500).also { assertTrue(it.error ?: "ok", it.ok) }.state
        }
        val left = journey.finishLoot(ready, true)
        assertTrue(left.error ?: "ok", left.ok)
        assertEquals(resolved.state.items, left.state.items)
        assertEquals(5, left.state.progress.run!!.round)
        assertEquals(JourneyStage.READY, left.state.progress.run!!.stage)
    }'''
s = s[:start] + new + s[end:]
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("s04 rewritten")

p = "app/src/test/java/com/example/douqiuqiu/inventory/JourneyEventsIntegrationTest.kt"
s = io.open(p, encoding="utf-8").read()
old = '''    @Test fun fullBackpackRollsBackPaymentAndAllowsRetryWithSameReward() {
        val before = state(EventKind.RUSTED_WORKSHOP).copy(items = (0..7).map {
            StoredItem("filler$it", "A1", ItemPlace(Container.BACKPACK, it % 4, it / 4))
        })
        val rejected = JourneyEvents.resolve(before, inventory, "workshop.pay")
        assertFalse(rejected.ok)
        assertEquals(before, rejected.state)
        val organized = before.copy(items = before.items.dropLast(1))
        val result = JourneyEvents.resolve(organized, inventory, "workshop.pay")
        assertTrue(result.error, result.ok)
        assertEquals(20, result.state.runGold)
        assertEquals("gauntlets", result.state.items.last().definitionId)
        assertTrue(result.state.items.last().instanceId.startsWith("run:event:1:"))
    }'''
new = '''    @Test fun fullBackpackKeepsEventRewardOnGroundAndChargesOnce() {
        val before = state(EventKind.RUSTED_WORKSHOP).copy(items = (0..7).map {
            StoredItem("filler$it", "A1", ItemPlace(Container.BACKPACK, it % 4, it / 4))
        })
        // 0.1.4 起奖励先落地：背包满不阻止结算，扣费只发生一次，物品零改动。
        val result = JourneyEvents.resolve(before, inventory, "workshop.pay")
        assertTrue(result.error, result.ok)
        assertEquals(before.items, result.state.items)
        val loot = result.state.progress.run!!.nodeLoot!!
        assertTrue(loot.items.any { it.definitionId == "gauntlets" })
        assertEquals(before.runGold - 30 + loot.goldAwarded, result.state.runGold)
        assertEquals(JourneyStage.REWARD, result.state.progress.run!!.stage)
        assertFalse(JourneyEvents.resolve(result.state, inventory, "workshop.pay").ok)
    }'''
assert old in s
s = s.replace(old, new, 1)

old2 = '''        val result = JourneyEvents.resolve(before, inventory, "terminal.convert", EventItemSelection("material"))
        assertTrue(result.error, result.ok)
        assertEquals(setOf("A1", "skill_shard"), result.state.items.map { it.definitionId }.toSet())'''
new2 = '''        val result = JourneyEvents.resolve(before, inventory, "terminal.convert", EventItemSelection("material"))
        assertTrue(result.error, result.ok)
        // 交付的材料被消耗，奖励刻片先落地待拾取。
        assertEquals(setOf("A1"), result.state.items.map { it.definitionId }.toSet())
        assertTrue(result.state.progress.run!!.nodeLoot!!.items.any { it.definitionId == "skill_shard" })'''
assert old2 in s
s = s.replace(old2, new2, 1)

old3 = '''        for ((kind, option) in choices) {
            val result = JourneyEvents.resolve(state(kind), inventory, option)
            assertTrue("$kind: ${result.error}", result.ok)
            assertEquals(2, result.state.progress.run!!.round)
            if (kind == EventKind.SAFE_WRECKAGE) assertTrue(result.state.items.single().bound)
        }'''
new3 = '''        for ((kind, option) in choices) {
            val result = JourneyEvents.resolve(state(kind), inventory, option)
            assertTrue("$kind: ${result.error}", result.ok)
            val run = result.state.progress.run!!
            if (run.nodeLoot != null) {
                // 有奖励的选项进入掉落整理，回合在离开节点时才推进。
                assertEquals("$kind", JourneyStage.REWARD, run.stage)
                assertEquals("$kind", 1, run.round)
            } else {
                assertEquals("$kind", 2, run.round)
            }
            if (kind == EventKind.SAFE_WRECKAGE) {
                assertTrue(run.nodeLoot!!.items.single { it.definitionId == "safe_lock" }.bound)
            }
        }'''
assert old3 in s
s = s.replace(old3, new3, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("events integration rewritten")
