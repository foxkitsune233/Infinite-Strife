package com.example.douqiuqiu.cards

import com.example.douqiuqiu.content.*
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.journey.*
import org.junit.Assert.*
import org.junit.Test

class CardRevision015Test {
    private val defs = CardCatalog.all.associate { it.id to ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height) }
    private val journey = JourneyRules(InventoryRules(defs))
    private fun actor(vararg ids: String) = journey.fighter(InventoryState(skillSlots = 8,
        items = ids.mapIndexed { i, id -> StoredItem("c$i", id, ItemPlace(Container.SKILLS, i)) }), "juggernaut")
        .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun target() = journey.fighter(InventoryState(), "abaddon").copy(
        hp = 100000.0, maxHp = 100000.0, mana = 0.0, manaRegen = 0.0, regen = 0.0,
        armor = 0.0, magicResist = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun number(id: String, effect: String, key: String, star: Int): Double {
        val value = CardCatalog.byId.getValue(id).effects.single { it.id == effect }.paramsAt(star).getValue(key)
        return when (value) { is EffectValue.Num -> value.value; is EffectValue.Count -> value.value.toDouble(); else -> error("not numeric") }
    }
    private fun expected(id: String, effect: String, key: String, values: List<Double>) = values.forEachIndexed { i, value ->
        assertEquals("$effect/$key star=${i+1}", value, number(id, effect, key, i+1), 0.000001)
    }
    @Test fun rageStacksGrowWithoutChangingLayerCapOrDuration() {
        expected("RG2", "RG2.stack", "amount", listOf(8.0,16.0,32.0))
        assertEquals(3, CardCatalog.byId.getValue("RG2").effects.single().maxStacks)
        assertEquals(4000L, CardCatalog.byId.getValue("RG2").effects.single().stackDurationMs)
    }
    @Test fun extraDamageHasBoundedStarGrowth() = expected("RG3", "RG3.extra", "damagePercent", listOf(0.4,0.6,0.8))
    @Test fun freezeHasExplicitBoundedDurationGrowth() {
        expected("FS3", "FS3.freeze", "durationMs", listOf(600.0,800.0,1000.0))
        assertEquals(5000L, CardCatalog.byId.getValue("FS3").effects.single().intervalMs)
    }
    @Test fun d9HasExplicitChanceGrowthAndFixedMultiplier() {
        expected("D9", "D9.crit", "chance", listOf(0.1,0.15,0.2))
        expected("D9", "D9.crit", "multiplier", listOf(1.5,1.5,1.5))
        expected("D9", "D9.stack", "amount", listOf(8.0,16.0,32.0))
    }
    @Test fun lowHealthGrowthDoesNotScaleTriggerThresholdOrCooldown() {
        expected("D11", "D11.heal", "percent", listOf(0.04,0.06,0.08))
        expected("D11", "D11.heal", "thresholdPercent", listOf(0.5,0.5,0.5))
        expected("D11", "D11.regen", "healthRegen", listOf(3.0,6.0,12.0))
        assertEquals(12000L, CardCatalog.byId.getValue("D11").effects.single { it.id == "D11.heal" }.internalCooldownMs)
    }
    @Test fun dualRageStacksGrowButVulnerabilityStaysBounded() {
        expected("D13", "D13.stack", "amount", listOf(8.0,16.0,32.0))
        expected("D13", "D13.vuln", "percent", listOf(18.0,18.0,18.0))
    }
    @Test fun onlyOpeningUltIsIntentionallyUnchangedAcrossAllStars() {
        val fixed = CardCatalog.all.filter { card ->
            fun signature(star: Int) = CardCombat.effects(CardBattleState(listOf(CombatCard("c", card.id, star))))
                .map { it.params to it.effect.intervalMs }
            signature(1) == signature(3)
        }.map { it.id }
        assertEquals(listOf("U4"), fixed)
    }
    @Test fun frostDamageDoesNotHealThroughPoisonLifesteal() {
        var state = DuelEngine.step(DuelState(actor("FS4", "D5").copy(hp = 500.0, attackIn = 0.0), target(), 1729))
        repeat(10) { state = DuelEngine.step(state.copy(player = state.player.copy(attackIn = 999.0))) }
        assertEquals("D5 must not convert frost DOT into healing", 500.0, state.player.hp, 0.00001)
    }
    @Test fun frostAndPoisonBonusesDoNotLeakAcrossSchoolsOrShieldAmplification() {
        var state = DuelEngine.step(DuelState(actor("FS4", "FS1", "FS3", "P1", "P2", "S1", "S2", "S3")
            .copy(attackIn = 0.0), target(), 1729))
        repeat(9) { state = DuelEngine.step(state.copy(player = state.player.copy(attackIn = 999.0))) }
        val frame = DuelEngine.stepWithCues(state.copy(player = state.player.copy(attackIn = 999.0)))
        assertTrue("Frost DOT must have a distinct cue", frame.cues.any { it.kind.name == "FROST_HIT" })
        assertEquals(8.0 * 1.27, frame.cues.filter { it.kind.name == "FROST_HIT" }.sumOf { it.lifeDamage }, 0.00001)
        assertEquals(3.0 * 1.25, frame.cues.filter { it.kind == DuelCueKind.POISON_HIT }.sumOf { it.lifeDamage }, 0.00001)
    }
    @Test fun omniCritCanTriggerBothCritSpeedAndCritMana() {
        val frame = DuelEngine.stepWithCues(DuelState(actor("D2", "D3").copy(
            invulnerable = 2.0, criticalReady = 4.0), target(), 1729, tick = 10))
        assertTrue(frame.cues.any { it.kind == DuelCueKind.OMNI_SLASH })
        assertEquals(8.0, frame.state.player.mana, 0.00001)
        assertEquals(15.0, CardCombat.buffAmount(frame.state.player.cardState, CardBuffKind.ATTACK_SPEED), 0.00001)
    }

    @Test fun oldSavedEightPointStacksRemainValidUntilTheyExpire() {
        for ((id, effect) in listOf("RG2" to "RG2.stack", "D9" to "D9.stack", "D13" to "D13.stack")) {
            val state = CardBattleState(listOf(CombatCard("legacy", id, 2)), started = true,
                buffs = listOf(CardBuff("legacy:$effect", CardBuffKind.ATTACK_SPEED, 8.0, 20)))
            CardCombat.validate(state)
            val frame = DuelEngine.step(DuelState(actor().copy(cardState = state), target(), 1729, tick = 10))
            assertEquals(8.0, CardCombat.buffAmount(frame.player.cardState, CardBuffKind.ATTACK_SPEED), 0.00001)
            CardCombat.validate(frame.player.cardState)
            assertThrows(IllegalArgumentException::class.java) {
                CardCombat.validate(state.copy(buffs = listOf(state.buffs.single().copy(amount = 999.0))))
            }
        }
    }

    @Test fun frostStatusUsesSnowInsteadOfPoisonAndOldSourceIdsStillWork() {
        val f = target().copy(poisonPool = com.example.douqiuqiu.status.PoisonCorePool(normal = listOf(
            com.example.douqiuqiu.status.PoisonCoreLayer("frost", com.example.douqiuqiu.status.PoisonKind.NORMAL,
                "c0:FS4.frostDot", "0", 8.0, 3, 1000L, false))))
        val entries = com.example.douqiuqiu.ui.components.BattleStatusCatalog.entries(f, 0)
        assertTrue(entries.any { it.kind == com.example.douqiuqiu.ui.components.StatusKind.FROST_DOT })
        assertFalse(entries.any { it.kind == com.example.douqiuqiu.ui.components.StatusKind.NORMAL_POISON })
        assertTrue(CardCombat.isFrostDotSource("synergy.frost.4"))
        assertFalse(CardCombat.isFrostDotSource("c1:P1.attackPoison"))
    }

    @Test fun threeStarRageExtraDamageActuallyDealsEightyPercentWithoutRecursion() {
        val player = actor("RG3").let { it.copy(attack = 100.0, attackIn = 0.0, broken = 999.0,
            cardState = it.cardState.copy(cards = it.cardState.cards.map { card -> card.copy(star = 3) })) }
        val frame = DuelEngine.stepWithCues(DuelState(player, target(), 1729))
        assertEquals(180.0, frame.cues.filter { it.target == 1 }.sumOf { it.lifeDamage }, 0.00001)
        assertEquals(1, frame.state.player.cardState.attackCount)
    }

    @Test fun threeStarLowHealthHealingIsEightPercentOnceDuringCooldown() {
        val player = actor("D11").let { it.copy(hp = 200.0, maxHp = 1000.0,
            cardState = it.cardState.copy(cards = it.cardState.cards.map { card -> card.copy(star = 3) })) }
        val first = DuelEngine.step(DuelState(player, target(), 1729))
        assertEquals(280.0, first.player.hp, 0.00001)
        assertEquals(280.0, DuelEngine.step(first).player.hp, 0.00001)
    }

    @Test fun schoolTooltipsDescribeSeparateDotBonuses() {
        val poison = CardCatalog.synergyTiers.single { it.schoolId == "poison" && it.tier == 2 }.effects.single()
        val frost = CardCatalog.synergyTiers.single { it.schoolId == "frost" && it.tier == 3 }.effects.single()
        assertTrue(com.example.douqiuqiu.ui.components.SynergyPresentation.describe(poison).contains("不强化冰霜"))
        assertTrue(com.example.douqiuqiu.ui.components.SynergyPresentation.describe(frost).contains("不强化中毒"))
    }
}
