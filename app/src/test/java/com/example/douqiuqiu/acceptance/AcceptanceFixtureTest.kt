package com.example.douqiuqiu.acceptance

import com.example.douqiuqiu.cards.*
import com.example.douqiuqiu.combat.CombatMath
import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.equipment.*
import com.example.douqiuqiu.event.*
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.status.ControlKind
import com.example.douqiuqiu.status.TimedControls
import com.example.douqiuqiu.status.DispelClass
import com.example.douqiuqiu.ui.components.BattleStatusCatalog
import com.example.douqiuqiu.ui.components.StatusKind
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Dedicated deterministic fixtures. Compile locally; execution belongs to external acceptance. */
class AcceptanceFixtureTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private val defs = (EquipmentCatalog.all.map { d -> ItemDefinition(d.id, d.name,
        if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT,
        d.width, d.height, d.quality.label, stats = d.stats ?: EquipmentStats(), price = d.price,
        minimumShopClears = d.minimumShopClears) } + CardCombat.enabledCards.map {
        ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label)
    } + listOf(ItemDefinition("iron_branch", "树枝", ItemKind.EQUIPMENT, 1, 1, price = 50),
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1),
        ItemDefinition("safe_lock", "锁芯", ItemKind.MATERIAL, 2, 2))).associateBy { it.id }
    private val inventory = InventoryRules(defs)
    private val journey = JourneyRules(inventory)
    private fun gear(vararg ids: String, prefix: String = "item") = ids.mapIndexed { i, id ->
        StoredItem("$prefix:$i", id, ItemPlace(Container.EQUIPMENT, i))
    }
    private fun fighter(vararg ids: String, hero: String = "phantom_assassin", prefix: String = "item") =
        journey.fighter(InventoryState(items = gear(*ids, prefix = prefix)), hero)
            .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 60.0)
    private fun source(f: Fighter, id: String = "orchid.silence") = EquipmentCombat.effects(f.equipment).first { it.effect.id == id }.key
    private fun apply(target: Fighter, key: String, seconds: Double, kind: ControlKind = ControlKind.SILENCE, tick: Int = 0) =
        TimedControls.apply(target, key, 1, kind, seconds, if (kind == ControlKind.SILENCE) DispelClass.BASIC else DispelClass.STRONG_ONLY, tick).fighter

    private class Probe(player: Fighter, enemy: Fighter) : CardEffectHost {
        val actors = mutableListOf(player, enemy)
        var tick = 1
        val applied = mutableListOf<String>()
        override val cardTick get() = tick
        override fun fighter(index: Int) = actors[index]
        override fun update(index: Int, fighter: Fighter) { actors[index] = fighter }
        override fun controlEvent(source: Int, target: Int, sourceId: String, result: String, durationMs: Long) {
            if (result == "APPLIED") applied += sourceId
        }
        override fun roll(): Double = 0.0
        override fun healCard(index: Int, amount: Double) = error("Unexpected healing in control probe")
        override fun damageCard(source: Int, target: Int, amount: Double, type: DamageType, attackClass: AttackClass, flags: EffectFlags, effectId: String) = error("Unexpected damage in control probe")
        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) = error("Unexpected poison in control probe")
    }

    @Test fun c02DoubleBasherRollsOnceAndStartsOnlyOneSharedCooldown() {
        val host = Probe(fighter("skull_basher", "skull_basher"), fighter())
        var rolls = 0
        EquipmentEffectRuntime.trigger(host, 0, EffectTrigger.ATTACK_LAND, { rolls++; 0.0 })
        assertEquals(1, rolls)
        assertEquals(1, host.applied.size)
        assertEquals(1, host.fighter(1).timedControls.size)
        assertEquals(3000L, host.fighter(0).equipment.cooldowns.remainingByGroup["basher"])
        EquipmentEffectRuntime.trigger(host, 0, EffectTrigger.ATTACK_LAND, { rolls++; 0.0 })
        assertEquals(1, rolls)
        assertEquals(1, host.applied.size)
    }

    @Test fun c02MeleeAndRangedStrictRollBoundariesAndSlashRollsBasherLikeFullAttack() {
        for ((hero, threshold) in listOf("phantom_assassin" to .25, "skywrath" to .10)) {
            val blocked = Probe(fighter("skull_basher", hero = hero), fighter())
            EquipmentEffectRuntime.trigger(blocked, 0, EffectTrigger.ATTACK_LAND, { threshold })
            assertTrue(blocked.applied.isEmpty())
            assertFalse(blocked.fighter(0).equipment.cooldowns.remainingByGroup.containsKey("basher"))
            val hit = Probe(fighter("skull_basher", hero = hero), fighter())
            EquipmentEffectRuntime.trigger(hit, 0, EffectTrigger.ATTACK_LAND, { threshold - .000001 })
            assertEquals(800L, hit.fighter(1).timedControls.single().remainingMs)
        }
        // 0.1.5 H02：无敌斩每一斩视为一次完整普通攻击，可触发碎颅锤眩晕与共享冷却。
        val slash = Probe(fighter("skull_basher", "skull_basher"), fighter())
        EquipmentEffectRuntime.trigger(slash, 0, EffectTrigger.ATTACK_LAND, { 0.0 }, slash = true)
        assertEquals(1, slash.applied.size)
        assertEquals(1, slash.fighter(1).timedControls.size)
        assertEquals(800L, slash.fighter(1).timedControls.single().remainingMs)
        assertEquals(3000L, slash.fighter(0).equipment.cooldowns.remainingByGroup["basher"])
    }

    @Test fun c02FullyBlockedLandedAttackCanStunButProtectedOrEvadedAttackCannot() {
        val attacker = fighter("skull_basher").copy(attack = 1.0, attackIn = 0.0)
        val defender = fighter("vanguard_shield")
        assertTrue(EquipmentCombat.passive(defender.equipment, EffectKind.PHYSICAL_BLOCK, "amount") >= 1.0)
        // Fixed finite seed set: finds a proc without depending on an undocumented roll offset.
        val landed = (1L..128L).map { DuelEngine.step(DuelState(attacker, defender, it)) }
            .firstOrNull { it.enemy.timedControls.isNotEmpty() }
        assertNotNull("No deterministic blocked-hit proc in fixture seeds", landed)
        assertEquals(defender.hp, landed!!.enemy.hp, 0.0)
        assertEquals(1, landed.enemy.timedControls.size)
        for (protected in listOf(defender.copy(equipment = defender.equipment.copy(protectionMs = 1000)),
            defender.copy(ghost = 1.0), defender.copy(invulnerable = 1.0))) {
            val next = DuelEngine.step(DuelState(attacker, protected, 1))
            assertTrue(next.enemy.timedControls.isEmpty())
            assertFalse(next.player.equipment.cooldowns.remainingByGroup.containsKey("basher"))
        }
        val dodger = fighter("butterfly")
        val evaded = (1L..128L).map { DuelEngine.stepWithCues(DuelState(attacker, dodger, it)) }
            .firstOrNull { frame -> frame.cues.any { it.kind == DuelCueKind.EVADED } }
        assertNotNull("No evade in fixture seeds", evaded)
        assertTrue(evaded!!.state.enemy.timedControls.isEmpty())
        assertFalse(evaded.state.player.equipment.cooldowns.remainingByGroup.containsKey("basher"))
    }

    @Test fun c05TwoRealSourcesExpireSeparatelyAndRefreshUsesMaximumNotAddition() {
        val caster = fighter("orchid", "orchid", prefix = "caster")
        val keys = EquipmentCombat.effects(caster.equipment).filter { it.effect.id == "orchid.silence" }.map { it.key }
        var target = apply(apply(fighter(), keys[0], .6), keys[1], .3)
        TimedControls.validateSources(target, caster, 0)
        repeat(3) { target = target.copy(timedControls = TimedControls.elapse(target.timedControls)) }
        assertEquals(keys[0], target.timedControls.single().sourceId)
        assertEquals(300L, target.timedControls.single().remainingMs)
        assertTrue(TimedControls.current(target).silenced)
        target = apply(target, keys[0], .2, tick = 3)
        assertEquals(300L, target.timedControls.single().remainingMs)
        target = apply(target, keys[0], .5, tick = 3)
        assertEquals(500L, target.timedControls.single().remainingMs)
        repeat(5) { target = target.copy(timedControls = TimedControls.elapse(target.timedControls)) }
        assertFalse(TimedControls.current(target).silenced)
    }

    @Test fun c05BothNewControlsReceiveFullDurationInTheSameFrame() {
        val player = fighter("orchid", prefix = "p")
        val enemy = fighter("orchid", prefix = "e")
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertEquals(3000L, next.player.timedControls.single().remainingMs)
        assertEquals(3000L, next.enemy.timedControls.single().remainingMs)
        assertEquals(1, next.player.timedControls.single().appliedAtTick)
        assertEquals(1, next.enemy.timedControls.single().appliedAtTick)
    }

    @Test fun c06ResistanceAppliedOnceQuantizedAndSaveResumeDoesNotReapplyIt() {
        val caster = fighter("orchid", prefix = "caster")
        val key = source(caster)
        val plain = apply(fighter(), key, .8)
        assertEquals(800L, plain.timedControls.single().remainingMs)
        val resistant = fighter("sange")
        val factor = CombatMath.statusDurationMultiplier(EquipmentCombat.passiveValues(resistant.equipment, EffectKind.STATUS_RESIST, "percent"))
        val expected = (kotlin.math.ceil(.8 * factor * 10).toLong() * 100).coerceAtLeast(100)
        val target = apply(resistant, key, .8)
        assertEquals(expected, target.timedControls.single().remainingMs)
        assertEquals(100L, apply(resistant, key, .001).timedControls.single().remainingMs)
        // No live equipment grants 100% resistance: this boundary is formula-only, not a forged save.
        assertEquals(0.0, CombatMath.statusDurationMultiplier(listOf(1.0)), 0.0)
        val state = InventoryState(phase = InventoryPhase.BATTLE, items = gear("sange"),
            progress = CampaignProgress(run = JourneyState("fixture-resume", target.heroId,
                TargetChest("target", "白", 0, 120, listOf(ChestItem("iron_branch", 0, 0))),
                1729, target.hp, target.maxHp, stage = JourneyStage.BATTLE, fight = DuelState(target, caster, 1729))))
        val file = File(temp.root, "resistance.dat")
        val store = InventoryStore(file, inventory) { InventoryState() }
        store.save(state)
        val restored = store.load().state
        assertEquals(state, restored)
        val next = journey.tick(restored)
        assertEquals(journey.tick(state), next)
        assertEquals(expected - 100, next.progress.run!!.fight!!.player.timedControls.single().remainingMs)
    }

    @Test fun c10FreshControlTriggersForceOrPikeInTheSameFrameWithoutRemovingControl() {
        for (pike in listOf(false, true)) for (ranged in listOf(false, true)) {
            val ids = if (pike) arrayOf("force_staff", "hurricane_pike") else arrayOf("force_staff")
            val player = fighter(*ids, hero = if (ranged) "skywrath" else "phantom_assassin")
            val next = DuelEngine.step(DuelState(player, fighter("orchid", prefix = "e"), 1729))
            assertEquals(2000L, next.player.equipment.protectionMs)
            assertEquals(3000L, next.player.timedControls.single().remainingMs)
            assertTrue(TimedControls.current(next.player).silenced)
            val base = if (pike) 9000L else 12000L
            assertEquals(if (ranged) base * 2 / 3 else base, next.player.equipment.cooldowns.remainingByGroup["force_staff"])
            assertEquals(if (pike) 40.0 else 0.0, EquipmentCombat.speed(next.player.equipment), 0.0)
        }
    }

    @Test fun c12EnemyMatrixUsesProductionGeneratorAndRespectsControlLimits() {
        val ids = ControlEquipmentContent.mains.map { it.id }.toSet()
        var observedLateControl = false
        for (difficulty in 0..2) for (round in 1..5) for (seed in 1L..128L) {
            val run = JourneyState("enemy-fixture", "juggernaut",
                TargetChest("target", "金", difficulty, 700, listOf(ChestItem("iron_branch", 0, 0))),
                seed, 1000.0, 1000.0, round = round)
            val build = EnemyBuilds.preview(run, defs)
            assertEquals(build, EnemyBuilds.preview(run, defs))
            val controls = build.inventory.items.count { it.place.container == Container.EQUIPMENT && it.definitionId in ids }
            // v4 起高难度提前投放控制装备：上限随难度与回合增长，必须与生产规则一致。
            val cap = if (difficulty == 0) 0 else if (difficulty == 1) (if (round >= 4) 1 else 0)
                else (difficulty - 1 + (round - 1) / 2).coerceAtMost(3)
            assertTrue("difficulty=$difficulty round=$round seed=$seed controls=$controls", controls <= cap)
            if (controls > 0) observedLateControl = true
        }
        assertTrue("Control catalog must actually enter at least one eligible build", observedLateControl)
    }

    @Test fun c11SixActivesAndMergedSourcesKeepManualModesAndCooldownsAfterRestart() {
        val ids = arrayOf("black_king_bar", "satanic", "mask_of_madness", "armlet_of_mordiggian", "pipe_of_insight", "crimson_guard")
        val enemy = fighter("orchid", "orchid", prefix = "caster")
        val keys = EquipmentCombat.effects(enemy.equipment).filter { it.effect.id == "orchid.silence" }.map { it.key }
        var player = fighter(*ids)
        player = player.copy(equipment = player.equipment.copy(manual = player.equipment.items.map { it.instanceId }.toSet()))
        player = apply(apply(player, keys[0], 3.0), keys[1], 2.0)
        assertEquals(6, EquipmentCombat.activeItems(player.equipment).size)
        val silence = BattleStatusCatalog.entries(player, 0).single { it.kind == StatusKind.SILENCE }
        assertEquals("3s", silence.badge)
        assertTrue(silence.detail.contains(keys[0]))
        assertTrue(silence.detail.contains(keys[1]))
        var fight = DuelState(player, enemy, 1729)
        // Use BKB before mask so the probe also checks real weak dispel of both sources.
        for (item in EquipmentCombat.activeItems(player.equipment)) fight = DuelEngine.useItem(fight, item.instanceId)
        assertTrue(fight.player.timedControls.isEmpty())
        assertEquals(6, fight.player.equipment.cooldowns.remainingByGroup.size)
        val s = InventoryState(items = gear(*ids), phase = InventoryPhase.BATTLE,
            progress = CampaignProgress(manualItems = player.equipment.manual, run = JourneyState("six", player.heroId,
                TargetChest("target", "白", 0, 120, listOf(ChestItem("iron_branch", 0, 0))),
                1729, fight.player.hp, fight.player.maxHp, stage = JourneyStage.BATTLE, fight = fight)))
        val file = File(temp.root, "six.dat")
        val store = InventoryStore(file, inventory) { InventoryState() }
        store.save(s)
        val restored = store.load().state
        assertEquals(s, restored)
        assertEquals(6, restored.progress.manualItems.size)
        assertEquals(s, journey.useEquipment(restored, "item:0").state)
    }

    @Test fun s04FullBackpackKeepsEventLootOnGroundThenFinishAdvancesWithoutGrantingItems() {
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
    }
}
