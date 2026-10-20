package com.example.douqiuqiu.equipment

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import com.example.douqiuqiu.status.ActionControlState
import org.junit.Assert.*
import org.junit.Test

/** Compile-only here. Execute with the external acceptance table. */
class EquipmentBattleIntegrationTest {
    private val defs = (EquipmentCatalog.all.map { d -> ItemDefinition(d.id, d.name,
        if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT, d.width, d.height,
        d.quality.label, stats = d.stats ?: EquipmentStats(), price = d.price) } +
        CardCombat.enabledCards.map { ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label) } +
        listOf("iron_branch", "circlet", "gauntlets", "slippers", "mantle", "broadsword").map {
            ItemDefinition(it, it, ItemKind.EQUIPMENT, 1, 1, "白", price = 20)
        }).associateBy { it.id }
    private val rules = InventoryRules(defs)
    private val journey = JourneyRules(rules)
    private fun inventory(vararg ids: String) = InventoryState(items = ids.mapIndexed { slot, id -> StoredItem("g$slot", id, ItemPlace(Container.EQUIPMENT, slot)) })
    private fun actor(vararg ids: String, hero: String = "phantom_assassin") = journey.fighter(inventory(*ids), hero)
    private fun idle(f: Fighter) = f.copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun duel(f: Fighter) = DuelState(f, idle(actor()).copy(hp = 100000.0, maxHp = 100000.0, armor = 0.0, magicResist = 0.0), 1729)

    @Test fun allThirtySixRecipesHaveLiveDefinitions() {
        assertEquals(36, EquipmentCatalog.recipes.size)
        assertTrue(RecipeGraphValidator.validate(defs.keys, EquipmentCatalog.recipes).valid)
        assertEquals(setOf("ring_of_health", "vitality_booster"), EquipmentCatalog.recipes.single { it.outputId == "vanguard_shield" }.ingredients.keys)
    }
    @Test fun reserveSeventhSlotContributesNoStatsOrEffects() {
        val first = inventory("sange")
        val reserved = first.copy(items = first.items + StoredItem("reserve", "butterfly", ItemPlace(Container.EQUIPMENT, 6)))
        assertEquals(journey.fighter(first, "juggernaut"), journey.fighter(reserved, "juggernaut"))
    }
    @Test fun eachBladeFamilySelectsItsOwnStrongestEffect() {
        val state = actor("sange", "sange_and_yasha", "kaya_and_sange", "yasha_and_kaya").equipment
        assertEquals(.24, EquipmentCombat.passive(state, EffectKind.HEAL_AMP, "percent"), 1e-9)
        assertEquals(.20, EquipmentCombat.passive(state, EffectKind.STATUS_RESIST, "percent"), 1e-9)
        assertEquals(.14, EquipmentCombat.passive(state, EffectKind.DAMAGE_AMP, "percent"), 1e-9)
        assertEquals(12.0, EquipmentCombat.passive(state, EffectKind.STAT_BONUS, "attackSpeed"), 1e-9)
    }
    @Test fun duplicateOrdinaryLifestealAddsAndCritSourcesStayIndependent() {
        assertEquals(.50, EquipmentCombat.lifesteal(actor("satanic", "satanic").equipment), 1e-9)
        assertEquals(2, EquipmentCombat.effects(actor("crystalys", "daedalus").equipment).count { it.effect.kind == EffectKind.CRIT_SOURCE })
    }
    @Test fun pipeAndCrimsonActivateAsDifferentShieldTypes() {
        val next = DuelEngine.step(duel(idle(actor("pipe_of_insight", "crimson_guard"))))
        assertEquals(300.0, next.player.magicShield, 1e-9)
        assertEquals(200.0, next.player.physicalShield, 1e-9)
        assertEquals(0.0, next.player.shield, 1e-9)
    }
    @Test fun bkbCannotStartStunnedAndDoesNotReplayExpiredControl() {
        val stunned = idle(actor("black_king_bar")).copy(control = ActionControlState(stunned = true))
        val blocked = DuelEngine.useItem(duel(stunned), "g0")
        assertEquals(0L, blocked.player.equipment.immunityMs)
        val step = DuelEngine.step(blocked)
        assertEquals(0L, step.player.equipment.immunityMs)
        val recovered = DuelEngine.step(step.copy(player = step.player.copy(control = ActionControlState())))
        assertEquals(0L, recovered.player.equipment.immunityMs)
    }
    @Test fun bkbUsesWeakDispelWhenSilencedAndSatanicCanDispelDisarm() {
        val bkb = DuelEngine.useItem(duel(idle(actor("black_king_bar")).copy(control = ActionControlState(silenced = true))), "g0")
        assertEquals(3000L, bkb.player.equipment.immunityMs)
        assertFalse(bkb.player.control.silenced)
        val satanic = DuelEngine.useItem(duel(idle(actor("satanic")).copy(control = ActionControlState(disarmed = true))), "g0")
        assertFalse(satanic.player.control.disarmed)
        assertEquals(1.25, EquipmentCombat.lifesteal(satanic.player.equipment), 1e-9)
    }
    @Test fun forceProtectionIsPassiveAndRangedCooldownIsShorter() {
        val ranged = DuelEngine.step(duel(idle(actor("force_staff", hero = "skywrath")).copy(control = ActionControlState(stunned = true))))
        val melee = DuelEngine.step(duel(idle(actor("force_staff")).copy(control = ActionControlState(stunned = true))))
        assertEquals(2000L, ranged.player.equipment.protectionMs)
        assertTrue(ranged.player.control.stunned)
        assertEquals(8000L, ranged.player.equipment.cooldowns.remainingByGroup["force_staff"])
        assertEquals(12000L, melee.player.equipment.cooldowns.remainingByGroup["force_staff"])
    }
    @Test fun pikeReplacesForceAndAddsOneAttackSpeedBuff() {
        val next = DuelEngine.step(duel(idle(actor("force_staff", "hurricane_pike")).copy(control = ActionControlState(stunned = true))))
        assertEquals(2000L, next.player.equipment.protectionMs)
        assertEquals(40.0, EquipmentCombat.speed(next.player.equipment), 1e-9)
        assertEquals(9000L, next.player.equipment.cooldowns.remainingByGroup["force_staff"])
    }
    @Test fun parasmaReplacesWitchPoisonAndSharesCooldown() {
        val first = DuelEngine.step(duel(idle(actor("witch_blade", "rod_of_atos")).copy(attackIn = 0.0)))
        assertEquals(1, first.enemy.poisonPool!!.virulent.size)
        assertTrue(first.enemy.poisonPool!!.normal.isEmpty())
        assertEquals(4000L, first.player.equipment.cooldowns.remainingByGroup["poison_blade"])
        val next = DuelEngine.step(first.copy(player = first.player.copy(attackIn = 0.0)))
        assertEquals(1, next.enemy.poisonPool!!.virulent.size)
    }
    @Test fun octarineAffectsUltimateAndCardIntervalsWithoutMultiplyingDuplicateCopies() {
        val one = actor("octarine_core").equipment
        val two = actor("octarine_core", "octarine_core").equipment
        assertEquals(6.0, EquipmentCombat.cooldown(one, 8.0), 1e-9)
        assertEquals(6.0, EquipmentCombat.cooldown(two, 8.0), 1e-9)
    }
    @Test fun crossingBattleDeductsThirtySecondsExactlyOnce() {
        val source = inventory("black_king_bar").copy(phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(run = JourneyState("run", "juggernaut", TargetChest("target", "蓝", 0, 60, listOf(ChestItem("A1", 0, 0))),
                1729, 1000.0, 1000.0, round = 2, equipmentCooldowns = EquipmentCooldownState(mapOf("bkb" to 40000L), 1))))
        val first = journey.startBattle(source)
        assertTrue(first.error, first.ok)
        assertEquals(10000L, first.state.progress.run!!.fight!!.player.equipment.cooldowns.remainingByGroup["bkb"])
        assertEquals(first.state, journey.startBattle(first.state).state)
    }
    @Test fun armletActivatesOnceKeepsHealthRatioAndNeverSelfKills() {
        val start = idle(actor("armlet_of_mordiggian")).copy(hp = 1.0)
        val used = DuelEngine.useItem(duel(start), "g0")
        assertEquals(start.hp / start.maxHp, used.player.hp / used.player.maxHp, 1e-9)
        assertEquals(used, DuelEngine.useItem(used, "g0"))
        assertTrue(DuelEngine.step(used).player.hp >= 1.0)
    }
    @Test fun fabricatedActiveGroupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { EquipmentCombat.validate(EquipmentBattleState(activeGroups = mapOf("satanic" to 3000L))) }
    }
    @Test fun ghostAndSangeLifeRestorationCombineDiminishingly() {
        val f = actor("sange").copy(ghost = 3.0)
        assertEquals(1.6, EquipmentCombat.healingMultiplier(f), 1e-9)
    }
    @Test fun targetingNeverTurnsUnusedBouncesIntoRepeatedMainTargetHits() {
        assertEquals(listOf(1), EquipmentTargeting.select(1, listOf(EquipmentTarget(1)), 3))
        assertEquals(listOf(2, 1), EquipmentTargeting.select(2, listOf(EquipmentTarget(3, alive = false), EquipmentTarget(2), EquipmentTarget(1)), 3))
    }
}
