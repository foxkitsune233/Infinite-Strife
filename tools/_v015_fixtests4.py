import io

# 1) 快照校验允许冻结的中毒结果（失控炼金室 break_seal 会带 1 层毒）
p = "shared/src/commonMain/kotlin/com/example/douqiuqiu/journey/JourneyValidation.kt"
s = io.open(p, encoding="utf-8").read()
old = "                        result.applyNormalPoisonLayers != 0"
new = "                        result.applyNormalPoisonLayers !in 0..10 || result.poisonDamagePerTick !in 0.0..100.0"
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("poison snapshot allowed")

# 2) ProfileV9：奖励改到地面
p = "app/src/test/java/com/example/douqiuqiu/inventory/ProfileV9ExtensionTest.kt"
s = io.open(p, encoding="utf-8").read()
old = '''        val result = JourneyEvents.resolve(loaded, rules, "workshop.pay")
        assertTrue(result.error, result.ok)
        assertEquals("crown", result.state.items.single().definitionId)
        assertEquals(20, result.state.runGold)'''
new = '''        val result = JourneyEvents.resolve(loaded, rules, "workshop.pay")
        assertTrue(result.error, result.ok)
        // 奖励先落地，读档后仍按冻结结果发放，不因目录扩充重抽。
        assertTrue(result.state.items.isEmpty())
        assertTrue(result.state.progress.run!!.nodeLoot!!.items.any { it.definitionId == "crown" })
        assertEquals(20 + result.state.progress.run!!.nodeLoot!!.goldAwarded, result.state.runGold)'''
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("v9 updated")

# 3) Lifecycle 旧布尔事件：命中落地面，受伤才推进
p = "app/src/test/java/com/example/douqiuqiu/inventory/JourneyLifecycleAcceptanceTest.kt"
s = io.open(p, encoding="utf-8").read()
start = s.index("    @Test fun legacySupplyNodeWithoutSnapshotStillResolvesThroughTheBooleanInterface() {")
end = s.index("    @Test fun ", start + 10)
new_block = '''    @Test fun legacySupplyNodeWithoutSnapshotStillResolvesThroughTheBooleanInterface() {
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

'''
s = s[:start] + new_block + s[end:]
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("legacy supply test rewritten")
