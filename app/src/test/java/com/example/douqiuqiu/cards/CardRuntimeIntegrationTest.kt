package com.example.douqiuqiu.cards

import com.example.douqiuqiu.combat.*
import com.example.douqiuqiu.content.HeroContent
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

/** Compiled here; execution is delegated to the external acceptance task. */
class CardRuntimeIntegrationTest {
    private val definitions = CardCombat.enabledCards.associate { it.id to ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height) }
    private val journey = JourneyRules(InventoryRules(definitions))
    private fun actor(vararg cards: Pair<String, Int>, hero: String = "phantom_assassin"): Fighter = journey.fighter(
        InventoryState(skillSlots = maxOf(4, cards.size), items = cards.mapIndexed { index, card -> StoredItem("c$index", card.first, ItemPlace(Container.SKILLS, index), card.second) }), hero)
    private fun idle(f: Fighter) = f.copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun duel(f: Fighter, tick: Int = 0): DuelState = DuelState(f, idle(actor()).copy(hp = 100000.0, maxHp = 100000.0, armor = 0.0), 1729, tick)
    private fun advance(s: DuelState, count: Int): DuelState { var current = s; repeat(count) { current = DuelEngine.step(current) }; return current }

    @Test fun allApprovedCardsAreEnabled() {
        assertEquals(45, CardCombat.enabledCards.size)
        assertTrue("A4" in definitions && "U3" in definitions)
        assertTrue(definitions.keys.containsAll(setOf("P1", "P2", "P3", "P4", "D1", "D5")))
    }
    @Test fun duplicateNumericEffectsStackButSynergyCountsKinds() {
        val base = actor()
        val f = actor("A1" to 2, "A2" to 1, "A2" to 1)
        assertEquals(base.speed + 24 + 10, f.speed, .00001)
        assertEquals(base.attack + 20, f.attack, .00001)
        assertEquals(2, CardCombat.synergies(f.cardState).single().uniqueCardIds.size)
    }
    @Test fun duplicateStartShieldsAreOneAggregatedSource() {
        val f = actor("S1" to 1, "S1" to 2)
        assertEquals(180.0, f.shield, .00001)
        assertEquals(1, FighterEffects.shields(f).entries.size)
    }
    @Test fun shieldTiersAreCumulativeAndEnhanceAcquiredShield() {
        assertEquals(161.0, actor("S1" to 1, "S2" to 1, "S3" to 1).shield, .00001)
    }
    @Test fun manaPoolAndInitialManaGrowByStar() {
        assertEquals(actor().maxMana + 120, actor("U2" to 2).maxMana, .00001)
        assertEquals(60.0, actor("U2" to 2).mana, .00001)
    }
    @Test fun periodicShieldDoesNotFireBeforeFiveSeconds() {
        val before = advance(duel(idle(actor("S2" to 1))), 49)
        assertEquals(0.0, before.player.physicalShield, .00001)
        val at = DuelEngine.step(before)
        assertEquals(50.0, at.player.physicalShield, .00001)
        assertEquals(0.0, advance(at, 40).player.physicalShield, .00001)
    }
    @Test fun periodicHealFiresAtFiveSeconds() {
        val start = idle(actor("R3" to 1)).copy(hp = 100.0)
        assertEquals(100.0, advance(duel(start), 49).player.hp, .00001)
        assertEquals(145.0, advance(duel(start), 50).player.hp, .00001)
    }
    @Test fun lowHpHealCannotFireAgainDuringCooldown() {
        val first = DuelEngine.step(duel(idle(actor("R4" to 1)).copy(hp = 100.0, maxHp = 1000.0)))
        assertEquals(200.0, first.player.hp, .00001)
        assertEquals(200.0, advance(first, 10).player.hp, .00001)
    }
    @Test fun duplicatedOpeningUltimateCastsOnceAndCostsNoMana() {
        val start = idle(actor("U4" to 1, "U4" to 2, hero = "skywrath"))
        val next = DuelEngine.step(duel(start))
        assertEquals(0.0, next.player.mana, .00001)
        assertEquals(12.0, next.player.ultIn, .00001)
        // 0.1.5 H03：神秘之耀 6×45 → 8×60，段数从内容表读取。
        // 施放当帧第一发耀即触发，计数器 8→7；下一 tick 再 →6（段数总量以 FLARE_TICK cue 计为 8）。
        assertEquals(HeroContent.legacy("skywrath").hits - 1, next.player.flareLeft)
        assertTrue(next.player.cardState.started)
        // 第二 tick（flareIn=0.25）尚未到点，故再走一帧仍为 7（= hits-1）。
        assertEquals(HeroContent.legacy("skywrath").hits - 1, DuelEngine.step(next).player.flareLeft)
        assertEquals(11.9, DuelEngine.step(next).player.ultIn, .00001)
    }
    @Test fun openingFreeUltimateUsesBattleClockEvenWhenCardRuntimeWasRestored() {
        // A battle restored before its first simulated frame can legitimately carry started=true.
        // U4 still belongs to the battle-opening contract; it must not silently become a normal,
        // mana-gated Juggernaut cast.
        val restored = idle(actor("U4" to 1, hero = "juggernaut")).copy(
            cardState = actor("U4" to 1, hero = "juggernaut").cardState.copy(started = true)
        )
        val frame = DuelEngine.stepWithCues(duel(restored))
        assertTrue("第一战斗帧仍须触发无敌斩", frame.state.player.invulnerable > 0.0)
        assertEquals("免费开局大招不应扣蓝", 0.0, frame.state.player.mana, .00001)
        assertTrue("应输出大招表现事件", frame.cues.any { it.kind == DuelCueKind.ULTIMATE_CAST })
    }
    @Test fun flatCooldownReductionStacksBeforeOpeningPenalty() {
        val f = idle(actor("U3" to 1, "U3" to 2, "U4" to 1, hero = "skywrath"))
        assertEquals(9.75, DuelEngine.step(duel(f)).player.ultIn, .00001)
    }
    @Test fun shieldDamageContributesHalfToAttackLifesteal() {
        val player = idle(actor("R2" to 1)).copy(hp = 500.0, maxHp = 1000.0, attack = 100.0, attackIn = 0.0)
        var s = duel(player)
        s = s.copy(enemy = FighterEffects.withShields(s.enemy, ShieldPool(listOf(ShieldEntry("test", ShieldKind.UNIVERSAL, 1000.0)))))
        assertEquals(503.0, DuelEngine.step(s).player.hp, .00001)
    }
    @Test fun healingCreditCountsActualHealingNotOverheal() {
        var s = duel(idle(actor("D4" to 1)).copy(hp = 950.0, maxHp = 1000.0, regen = 1000.0))
        s = DuelEngine.step(s)
        assertEquals(0.0, s.player.shield, .00001)
        s = DuelEngine.step(s.copy(player = s.player.copy(hp = 950.0)))
        assertEquals(40.0, s.player.shield, .00001)
        assertEquals(0.0, s.player.cardState.healed.getValue("c0:D4.healToShield"), .00001)
    }
    @Test fun c4IsCappedAtThreeLayersAndStrongDispelRemovesIt() {
        var s = duel(idle(actor("C4" to 1)).copy(attack = 10.0))
        repeat(4) { s = DuelEngine.step(s.copy(player = s.player.copy(criticalReady = 4.0, attackIn = 0.0))) }
        assertEquals(3, s.enemy.cardState.buffs.count { it.kind == CardBuffKind.ARMOR_REDUCTION })
        assertTrue(FighterEffects.strongDispel(s.enemy).cardState.buffs.isEmpty())
    }
    @Test fun shieldConsumptionUsesAllSourcesWithoutBeingIncomingDamage() {
        val player = FighterEffects.withShields(idle(actor("S4" to 1)), ShieldPool(listOf(
            ShieldEntry("a", ShieldKind.UNIVERSAL, 50.0), ShieldEntry("b", ShieldKind.MAGICAL, 50.0))))
        val next = DuelEngine.step(duel(player, 79))
        assertEquals(70.0, ShieldLedger.total(FighterEffects.shields(next.player)), .00001)
        assertEquals(99964.0, next.enemy.hp, .00001)
    }
    @Test fun breakDoesNotDisableCardCrit() {
        val player = idle(actor("C1" to 1)).copy(attack = 100.0, attackIn = 0.0, broken = 3.0,
            cardState = actor("C1" to 1).cardState.copy(criticalFailures = mapOf("c0:C1.critSource" to 100)))
        assertEquals(99840.0, DuelEngine.step(duel(player)).enemy.hp, .00001)
    }
    @Test fun guaranteedHigherCritDoesNotConsumeLowerSourcePrd() {
        var player = idle(actor("C1" to 1)).copy(criticalReady = 4.0, attackIn = 0.0)
        player = player.copy(cardState = player.cardState.copy(criticalFailures = mapOf("c0:C1.critSource" to 2)))
        assertEquals(2, DuelEngine.step(duel(player)).player.cardState.criticalFailures.getValue("c0:C1.critSource"))
    }
    @Test fun prdIncreasesWithFailuresAndHasConfiguredMean() {
        assertTrue(CriticalPrd.threshold(.25, 1) > CriticalPrd.threshold(.25, 0))
        var survival = 1.0
        var expectation = 0.0
        for (failures in 0..100) { expectation += survival; survival *= 1 - CriticalPrd.threshold(.25, failures) }
        assertEquals(.25, 1 / expectation, .0000001)
    }
    @Test fun fabricatedSpeedSourceCannotPassValidation() {
        assertThrows(IllegalArgumentException::class.java) {
            CardCombat.validate(CardBattleState(buffs = listOf(CardBuff("missing", CardBuffKind.ATTACK_SPEED, 1000000.0, Int.MAX_VALUE))))
        }
    }
    @Test fun fullA4StacksReplaceEarliestExpiryAndKeepOtherTimers() {
        var s = duel(idle(actor("A4" to 1)).copy(attack = 10.0))
        repeat(16) { s = DuelEngine.step(s.copy(player = s.player.copy(attackIn = 0.0))) }
        val layers = s.player.cardState.buffs.filter { it.kind == CardBuffKind.ATTACK_SPEED }
        assertEquals(listOf(48, 52, 56), layers.map { it.expiresAtTick }.sorted())
        assertEquals(45.0, layers.sumOf { it.amount }, .00001)
    }
    @Test fun fifthAttackBonusDoesNotCountAsAnotherNormalAttack() {
        var s = duel(idle(actor("A3" to 1)).copy(attack = 10.0))
        repeat(5) { s = DuelEngine.step(s.copy(player = s.player.copy(attackIn = 0.0))) }
        assertEquals(99925.0, s.enemy.hp, .00001)
        assertEquals(5, s.player.cardState.attackCount)
    }
    @Test fun necrophosFloorAppliesAfterFlatReductionAndOpeningPenalty() {
        val cards = List(7) { "U3" to 3 } + ("U4" to 1)
        val f = idle(actor(*cards.toTypedArray(), hero = "necrophos"))
        assertEquals(10.0, DuelEngine.step(duel(f)).player.ultIn, .00001)
    }
    @Test fun ultimateShieldExpiresWithoutRemovingSkywrathPassiveShield() {
        val first = DuelEngine.step(duel(idle(actor("D6" to 1, "U4" to 1, hero = "skywrath"))))
        val pool = FighterEffects.shields(first.player)
        assertEquals(60.0, pool.entries.single { it.sourceId.startsWith("card:D6:") }.amount, .00001)
        assertTrue(pool.entries.any { it.sourceId == "hero:sky" })
        val after = advance(first, 50)
        assertFalse(FighterEffects.shields(after.player).entries.any { it.sourceId.startsWith("card:D6:") })
        assertTrue(FighterEffects.shields(after.player).entries.any { it.sourceId == "hero:sky" })
    }
}
