import io

# 1) 恢复 4-6-8 候选箱（设计文档 economy-progression.md:17）
p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/ChestFactory.kt"
s = io.open(p, encoding="utf-8").read()
old = "        val count = if (s.progress.clears >= 10) 8 else ChallengeBalance.COUNT"
new = "        // 初始 4 箱，累计 3 次通关扩至 6 箱，10 次扩至 8 箱（design/economy-progression.md）。\n        val count = when { s.progress.clears >= 10 -> 8; s.progress.clears >= 3 -> 6; else -> 4 }"
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("chest batch restored")

# 2) EnemyBuildsTest：首战卡数按 v4 预算，不再写死 1..2
p = "app/src/test/java/com/example/douqiuqiu/inventory/EnemyBuildsTest.kt"
s = io.open(p, encoding="utf-8").read()
old = "            assertTrue(cards.size in 1..2)"
new = "            val cardBudget = if (ChallengeBalance.enhanced(run(1, difficulty, seed))) ChallengeBalance.budget(run(1, difficulty, seed)).cards else 2\n            assertTrue(\"difficulty=$difficulty seed=$seed cards=${cards.size} budget=$cardBudget\", cards.size in 1..cardBudget)"
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("enemy cards fixed")

# 3) JourneyEvents：safe_lock 可能重复出现，改为 any + bound
p = "app/src/test/java/com/example/douqiuqiu/inventory/JourneyEventsIntegrationTest.kt"
s = io.open(p, encoding="utf-8").read()
old = '''            if (kind == EventKind.SAFE_WRECKAGE) {
                assertTrue(run.nodeLoot!!.items.single { it.definitionId == "safe_lock" }.bound)
            }'''
new = '''            if (kind == EventKind.SAFE_WRECKAGE) {
                assertTrue(run.nodeLoot!!.items.any { it.definitionId == "safe_lock" && it.bound })
            }'''
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("safe lock assertion fixed")

# 4) LoadoutAndPackingTest：整理按面积从大到小，断言改为大件优先且可重复执行
p = "app/src/test/java/com/example/douqiuqiu/inventory/LoadoutAndPackingTest.kt"
s = io.open(p, encoding="utf-8").read()
old = '''        assertTrue(result.error, result.ok)
        assertEquals(ItemPlace(Container.BACKPACK, 0, 0), result.state.items.single { it.instanceId == "a" }.place)
        assertEquals(setOf("a", "b", "c"), result.state.items.map { it.instanceId }.toSet())
        assertNull(rules.validate(result.state))'''
new = '''        assertTrue(result.error, result.ok)
        // 现行策略：面积大的先放（2×2 的盾牌占 0,0），其余按 instanceId 稳定摆放。
        assertEquals(ItemPlace(Container.BACKPACK, 0, 0), result.state.items.single { it.instanceId == "c" }.place)
        assertEquals(setOf("a", "b", "c"), result.state.items.map { it.instanceId }.toSet())
        assertEquals(result.state, rules.organize(result.state, Container.BACKPACK).state)
        assertNull(rules.validate(result.state))'''
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("loadout organize fixed")

# 5) ThemedLoot：l19/l20 改走地面掉落 + 鉴定 + 拾取/离开
p = "app/src/test/java/com/example/douqiuqiu/inventory/ThemedLootIntegrationTest.kt"
s = io.open(p, encoding="utf-8").read()

helper = '''    /** 完成当前节点掉落的鉴定，供手动摆放测试使用。 */
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

'''
anchor = "    private fun clearTarget("
assert anchor in s
s = s.replace(anchor, helper + anchor, 1)

old19 = '''        // 放安全箱后死亡：实际带回 -> 重置
        var s = journey.event(runWithEventMaterial(), true).state
        assertTrue(s.items.any { it.instanceId == "run-box-e:event:1" && it.definitionId == "backpack_fabric" })
        s = inventory.move(s, "run-box-e:event:1", ItemPlace(Container.SAFE, 0, 0)).state
        assertTrue(s.items.single { it.instanceId == "run-box-e:event:1" }.place.container == Container.SAFE)
        var dead = journey.abandon(s).state
        assertEquals(MaterialPityCounter(0, false), dead.progress.materialPity.counters["backpack"])
        // 留在背包中死亡：丢失 -> 不重置
        s = journey.event(runWithEventMaterial(), true).state
        assertTrue(s.items.any { it.place.container == Container.BACKPACK && it.definitionId == "backpack_fabric" })
        dead = journey.abandon(s).state
        assertEquals(MaterialPityCounter(2, false), dead.progress.materialPity.counters["backpack"])'''
new19 = '''        // 放安全箱后死亡：实际带回 -> 重置
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
        assertEquals(MaterialPityCounter(2, false), dead.progress.materialPity.counters["backpack"])'''
assert old19 in s
s = s.replace(old19, new19, 1)

old20 = '''        val got = journey.event(ev, true).state
        val resting = got.copy(progress = got.progress.copy(
            run = got.progress.run!!.copy(round = 3, stage = JourneyStage.REST)))
        val out = journey.extract(resting)'''
new20 = '''        var got = revealAll(journey.event(ev, true).state)
        val fabric = got.progress.run!!.nodeLoot!!.remaining.first { it.definitionId == "backpack_fabric" }
        got = NodeLootRules.collect(got, inventory, fabric.instanceId, ItemPlace(Container.BACKPACK, 0, 0)).state
        got = journey.finishLoot(got, true).also { assertTrue(it.error ?: "ok", it.ok) }.state
        val resting = got.copy(progress = got.progress.copy(
            run = got.progress.run!!.copy(round = 3, stage = JourneyStage.REST)))
        val out = journey.extract(resting)'''
assert old20 in s
s = s.replace(old20, new20, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("themed l19/l20 rewritten")
