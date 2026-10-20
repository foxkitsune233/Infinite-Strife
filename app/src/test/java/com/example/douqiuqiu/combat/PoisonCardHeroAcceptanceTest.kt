package com.example.douqiuqiu.combat

import com.example.douqiuqiu.cards.CardCombat
import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.content.EquipmentCategory
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.EquipmentStats
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind
import com.example.douqiuqiu.inventory.ItemPlace
import com.example.douqiuqiu.inventory.StoredItem
import com.example.douqiuqiu.journey.DuelEngine
import com.example.douqiuqiu.journey.DuelState
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.journey.FighterEffects
import com.example.douqiuqiu.journey.JourneyRules
import org.junit.Assert.*
import org.junit.Test

/**
 * 28号 A 组：真实毒卡与 Viper 英雄的战斗行为验收（A01～A09）。
 *
 * 全部用例都用**实际技能栏持卡角色**推进 [DuelEngine]（100ms 战斗 tick），
 * 木桩敌人隔离回血/回蓝/普攻与英雄被动；时间轴以毒核心配置为准：
 * 施加于 tick T 的层首跳/首产毒在 T*100+1000ms，随后每 1000ms 一次。
 * 概率性内容不使用；固定 seed 1729 只决定行动顺序，两侧中只有一侧会行动。
 */
class PoisonCardHeroAcceptanceTest {
    private val defs = (EquipmentCatalog.all.map { d ->
        ItemDefinition(d.id, d.name,
            if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT,
            d.width, d.height, d.quality.label, stats = d.stats ?: EquipmentStats(), price = d.price)
    } + CardCombat.enabledCards.map {
        ItemDefinition(it.id, it.name, ItemKind.CARD, it.width, it.height, it.quality.label)
    }).associateBy { it.id }
    private val rules = InventoryRules(defs)
    private val journey = JourneyRules(rules)

    private fun idle(f: Fighter) = f.copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0)
    private fun advance(s: DuelState, count: Int): DuelState {
        var current = s
        repeat(count) { current = DuelEngine.step(current) }
        return current
    }
    private fun duel(player: Fighter, enemy: Fighter = victim()) = DuelState(player, enemy, 1729)

    /** 不行动、不吃治疗的木桩；magicResist/armor 归零以保证毒伤数字可精确断言。 */
    private fun victim(hero: String = "juggernaut", hp: Double = 500.0, magicResist: Double = 0.0, armor: Double = 0.0) =
        idle(journey.fighter(InventoryState(), hero)).copy(hp = hp, maxHp = hp, magicResist = magicResist, armor = armor)

    /** 技能栏持卡角色；cards 按序占栏，star 1..3，实例 ID 固定为 c0/c1/… */
    private fun holder(hero: String, vararg cards: Pair<String, Int>): Fighter {
        val items = cards.mapIndexed { i, (id, star) -> StoredItem("c$i", id, ItemPlace(Container.SKILLS, i), star = star) }
        return idle(journey.fighter(InventoryState(skillSlots = 8, items = items), hero))
    }

    /** 生效装备栏持装角色。 */
    private fun gearHolder(hero: String, vararg gearIds: String): Fighter {
        val items = gearIds.mapIndexed { i, id -> StoredItem("g$i", id, ItemPlace(Container.EQUIPMENT, i)) }
        return idle(journey.fighter(InventoryState(items = items), hero)).copy(hp = 5000.0, maxHp = 5000.0)
    }

    /** 推进一次玩家普攻（强制 attackIn=0），随后停放攻击以隔离后续 tick。 */
    private fun attackTurn(s: DuelState): DuelState {
        val stepped = DuelEngine.step(s.copy(player = s.player.copy(attackIn = 0.0)))
        return stepped.copy(player = stepped.player.copy(attackIn = 999.0))
    }

    // ---------------- A01：P1 命中附毒 ----------------

    @Test fun p1AppliesOneNormalLayerPerSuccessfulAttackWithStarDamage() {
        for (star in 1..3) {
            val expected = listOf(3.0, 6.0, 12.0)[star - 1]
            var s = duel(holder("phantom_assassin", "P1" to star), victim(hp = 100000.0))
            s = attackTurn(s)
            val first = s.enemy.poisonPool!!
            assertEquals("star=$star 每次成功普攻只附 1 层", 1, first.normal.size)
            assertTrue(first.virulent.isEmpty())
            val layer = first.normal.single()
            assertEquals(expected, layer.damagePerTick, 1e-9)
            assertEquals(3, layer.ticksRemaining)
            assertTrue(layer.sourceId.startsWith("c0:P1.attackPoison"))
            s = attackTurn(s)
            val second = s.enemy.poisonPool!!
            assertEquals(2, second.normal.size)
            assertEquals(2, second.normal.map { it.instanceId }.distinct().size)
            assertTrue(second.normal.all { it.damagePerTick == expected && it.ticksRemaining == 3 })
        }
    }

    @Test fun p1LayersTickIndependentlyThreeTimesEach() {
        var s = duel(holder("phantom_assassin", "P1" to 3), victim(hp = 100000.0))
        s = attackTurn(s)
        s = attackTurn(s)
        assertEquals(2, s.enemy.poisonPool!!.normal.size)
        val base = s.enemy.hp
        // 第 1 层（tick 1 施加）首跳在 tick 11，第 2 层（tick 2 施加）首跳在 tick 12：各自独立 3 跳
        s = advance(s, 9)
        assertEquals(base - 12.0, s.enemy.hp, 1e-9)
        s = advance(s, 1)
        assertEquals(base - 24.0, s.enemy.hp, 1e-9)
        s = advance(s, 9)
        assertEquals(base - 36.0, s.enemy.hp, 1e-9)
        s = advance(s, 1)
        assertEquals(base - 48.0, s.enemy.hp, 1e-9)
        s = advance(s, 9)
        assertEquals(base - 60.0, s.enemy.hp, 1e-9)
        s = advance(s, 1)
        assertEquals(base - 72.0, s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(base - 72.0, s.enemy.hp, 1e-9)
        assertNull(s.enemy.poisonPool)
    }

    @Test fun invalidAttackAppliesNoPoisonAndNoAttackCount() {
        // 无敌：整次攻击在入口被拒
        var s = DuelState(holder("phantom_assassin", "P1" to 3).copy(attackIn = 0.0), victim().copy(invulnerable = 5.0), 1729)
        s = DuelEngine.step(s)
        assertNull(s.enemy.poisonPool)
        assertEquals(0, s.player.cardState.attackCount)
        assertEquals(500.0, s.enemy.hp, 1e-9)
        // 普攻保护：同样整次无效
        val protected = victim().copy(equipment = com.example.douqiuqiu.equipment.EquipmentBattleState(protectionMs = 2000))
        var p = DuelState(holder("phantom_assassin", "P1" to 3).copy(attackIn = 0.0), protected, 1729)
        p = DuelEngine.step(p)
        assertNull(p.enemy.poisonPool)
        assertEquals(0, p.player.cardState.attackCount)
        assertEquals(500.0, p.enemy.hp, 1e-9)
    }

    // ---------------- A02：P2 剧毒孢子 ----------------

    @Test fun p2AppliesVirulentOnStarIntervalAndSpawnsNormalWithoutSelfDamage() {
        for (star in 1..3) {
            val fire = listOf(80, 60, 40)[star - 1]
            var s = duel(holder("phantom_assassin", "P2" to star), victim(hp = 100000.0))
            s = advance(s, fire - 1)
            assertNull("star=$star 首次产毒前不应存在任何毒层", s.enemy.poisonPool)
            s = advance(s, 1)
            val pool = s.enemy.poisonPool!!
            assertEquals(1, pool.virulent.size)
            assertTrue(pool.normal.isEmpty())
            assertEquals(3.0, pool.virulent.single().damagePerTick, 1e-9)
            assertEquals(0, pool.virulent.single().ticksRemaining)
            assertEquals(100000.0, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            val spawned = s.enemy.poisonPool!!.normal
            assertEquals(1, spawned.size)
            assertTrue(spawned.single().instanceId.endsWith("~" + (fire * 100L + 1000L)))
            assertEquals(100000.0, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(3.0, 100000.0 - s.enemy.hp, 1e-9)
        }
    }

    // ---------------- A03：P3 / P4 / D1 ----------------

    @Test fun p3AppliesThreeNormalLayersEveryFiveSeconds() {
        for (star in 1..3) {
            val expected = listOf(5.0, 10.0, 20.0)[star - 1]
            var s = duel(holder("phantom_assassin", "P3" to star), victim(hp = 100000.0))
            s = advance(s, 49)
            assertNull(s.enemy.poisonPool)
            s = advance(s, 1)
            val layers = s.enemy.poisonPool!!.normal
            assertEquals(3, layers.size)
            assertTrue(layers.all { it.damagePerTick == expected && it.ticksRemaining == 3 })
            assertTrue(layers.all { it.sourceId.startsWith("c0:P3.burstPoison") })
            val base = s.enemy.hp
            s = advance(s, 10)
            assertEquals(base - 3 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 6 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 9 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 9 * expected, s.enemy.hp, 1e-9)
        }
    }

    @Test fun p4AppliesVirulentOnEveryFourthSuccessfulAttack() {
        for (star in 1..3) {
            val expected = listOf(5.0, 10.0, 20.0)[star - 1]
            var s = duel(holder("phantom_assassin", "P4" to star), victim(hp = 100000.0))
            repeat(3) { s = attackTurn(s) }
            assertNull("前 3 次成功普攻不应附剧毒", s.enemy.poisonPool)
            assertEquals(3, s.player.cardState.attackCount)
            s = attackTurn(s)
            val pool = s.enemy.poisonPool!!
            assertEquals(1, pool.virulent.size)
            assertTrue(pool.normal.isEmpty())
            assertEquals(expected, pool.virulent.single().damagePerTick, 1e-9)
            assertTrue(pool.virulent.single().sourceId.startsWith("c0:P4.virulentOn4th"))
            assertEquals(4, s.player.cardState.attackCount)
            val base = s.enemy.hp
            // 剧毒每 1000ms 产一层普通毒：tick 14 产第 1 层（tick 24 首跳），tick 24 产第 2 层（tick 34 首跳）……
            s = advance(s, 11)
            assertEquals(1, s.enemy.poisonPool!!.normal.size)
            assertEquals(base, s.enemy.hp, 1e-9)
            s = advance(s, 9)
            val at24 = s.enemy.poisonPool!!.normal
            assertEquals("tick 24：第 1 层首跳，剧毒同时产出第 2 层", 2, at24.size)
            assertTrue(at24.all { it.damagePerTick == expected && it.sourceId.startsWith("c0:P4.virulentOn4th") })
            assertEquals("只有第 1 层跳了一次", base - expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            val at34 = s.enemy.poisonPool!!.normal
            assertEquals("tick 34：第 1 层第 2 跳 + 第 2 层首跳 + 产出第 3 层", 3, at34.size)
            assertEquals("第 1 层跳 2 次 + 第 2 层首跳，共 3 跳", base - 3 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            val at44 = s.enemy.poisonPool!!.normal
            assertEquals("tick 44：第 1 层末跳耗尽移除，第 2、3 层继续跳，产出第 4 层", 3, at44.size)
            assertEquals(1, at44.count { it.ticksRemaining == 1 })
            assertEquals(1, at44.count { it.ticksRemaining == 2 })
            assertEquals(1, at44.count { it.ticksRemaining == 3 })
            assertEquals("前三层的 6 次跳全部独立结算", base - 6 * expected, s.enemy.hp, 1e-9)
        }
    }

    @Test fun d1AppliesTwoNormalLayersOnEveryThirdSuccessfulAttack() {
        for (star in 1..3) {
            val expected = listOf(5.0, 10.0, 20.0)[star - 1]
            var s = duel(holder("phantom_assassin", "D1" to star), victim(hp = 100000.0))
            repeat(2) { s = attackTurn(s) }
            assertNull("前 2 次成功普攻不应附毒", s.enemy.poisonPool)
            s = attackTurn(s)
            val layers = s.enemy.poisonPool!!.normal
            assertEquals(2, layers.size)
            assertTrue(layers.all { it.damagePerTick == expected && it.ticksRemaining == 3 })
            assertTrue(layers.all { it.sourceId.startsWith("c0:D1.poisonOn3rd") })
            val base = s.enemy.hp
            s = advance(s, 10)
            assertEquals(base - 2 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 4 * expected, s.enemy.hp, 1e-9)
            s = advance(s, 10)
            assertEquals(base - 6 * expected, s.enemy.hp, 1e-9)
        }
    }

    @Test fun cardExtraDamageDoesNotInflateTheSuccessfulAttackCount() {
        // P4 每第 4 次成功普攻附剧毒；A3 在第 5 次追加 25 物理技能伤害（固定值，可精确对账）
        var s = duel(holder("phantom_assassin", "P4" to 1, "A3" to 1), victim(hp = 100000.0))
        val start = s.enemy.hp
        repeat(8) { s = attackTurn(s) }
        assertEquals("追加伤害不计入普攻次数", 8, s.player.cardState.attackCount)
        assertEquals("仅第 4、8 次触发剧毒", 2, s.enemy.poisonPool!!.virulent.size)
        // 8 次普攻 68 × 8 + A3 追加 25；若追加被当成普攻，P4 会多触发一次
        assertEquals(8 * 68.0 + 25.0, start - s.enemy.hp, 1e-6)
    }

    // ---------------- A04：毒羁绊 2/3/4 档 ----------------

    @Test fun poisonSynergyTierTwoAmplifiesRealPoisonTicks() {
        var solo = duel(holder("phantom_assassin", "P1" to 1), victim(hp = 100000.0))
        solo = attackTurn(solo)
        val soloBase = solo.enemy.hp
        solo = advance(solo, 10)
        assertEquals("单卡无羁绊不增伤", 3.0, soloBase - solo.enemy.hp, 1e-9)

        var pair = duel(holder("phantom_assassin", "P1" to 1, "P2" to 1), victim(hp = 100000.0))
        pair = attackTurn(pair)
        val pairBase = pair.enemy.hp
        pair = advance(pair, 10)
        assertEquals("2 种毒卡去重计 2 档：毒跳 +10%", 3.3, pairBase - pair.enemy.hp, 1e-9)
    }

    @Test fun poisonSynergyTierThreeAddsVirulentWhileTierTwoStillAmplifies() {
        var s = duel(holder("phantom_assassin", "P1" to 1, "P2" to 1, "P4" to 1), victim(hp = 100000.0))
        s = advance(s, 59)
        assertNull(s.enemy.poisonPool)
        s = advance(s, 1)
        val pool = s.enemy.poisonPool!!
        assertEquals(1, pool.virulent.size)
        assertEquals("synergy.poison.3", pool.virulent.single().sourceId)
        assertEquals(3.0, pool.virulent.single().damagePerTick, 1e-9)
        assertEquals(100000.0, s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals(100000.0, s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertEquals("2 档增伤对 3 档产出的普通毒仍然有效", 3.3, 100000.0 - s.enemy.hp, 1e-9)
    }

    @Test fun poisonSynergyTierFourAddsNormalLayersAndAllTiersAccumulate() {
        var s = duel(holder("phantom_assassin", "P1" to 1, "P2" to 1, "P3" to 1, "P4" to 1), victim(hp = 100000.0))
        s = advance(s, 49)
        assertNull(s.enemy.poisonPool)
        s = advance(s, 1)
        val layers = s.enemy.poisonPool!!.normal
        assertEquals(6, layers.size)
        assertEquals(3, layers.count { it.sourceId == "synergy.poison.4" && it.damagePerTick == 6.0 })
        assertEquals(3, layers.count { it.sourceId.startsWith("c2:P3.burstPoison") && it.damagePerTick == 5.0 })
        val base = s.enemy.hp
        s = advance(s, 10)
        assertEquals("P3×3 与 4 档×3 同时首跳，均享 2 档 +10%", 3 * 5.5 + 3 * 6.6, base - s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(2 * (3 * 5.5 + 3 * 6.6), base - s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertTrue(s.enemy.poisonPool!!.virulent.any { it.sourceId == "synergy.poison.3" })
        assertEquals("第三跳 + 3 档产出层首跳", 3 * (3 * 5.5 + 3 * 6.6) + 3.3, base - s.enemy.hp, 1e-9)
    }

    // ---------------- A05：增伤池与产毒事件 ----------------

    @Test fun poisonAndSpellAmplificationShareOneLinearPool() {
        // P1+P2 → 毒 2 档（+10%）；S1+S2+S3 → 护盾 3 档（synergy.shield.3 +15%）
        var s = duel(holder("phantom_assassin", "P1" to 1, "P2" to 1, "S1" to 1, "S2" to 1, "S3" to 1), victim(hp = 100000.0))
        s = attackTurn(s)
        val base = s.enemy.hp
        s = advance(s, 10)
        assertEquals("同类增伤线性相加 1+10%+15%，无额外乘算", 3.75, base - s.enemy.hp, 1e-9)
    }

    @Test fun virulentSpawnIsNeitherDamageNorHealing() {
        var s = duel(holder("phantom_assassin", "P2" to 1), victim(hp = 500.0))
        val playerBase = s.player.hp
        s = advance(s, 80)
        assertEquals(1, s.enemy.poisonPool!!.virulent.size)
        assertEquals(500.0, s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals("产毒事件本身不结算伤害", 500.0, s.enemy.hp, 1e-9)
        assertEquals("产毒事件本身不产生治疗", playerBase, s.player.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(497.0, s.enemy.hp, 1e-9)
    }

    // ---------------- A06：Viper 皮肤与大招 ----------------

    @Test fun viperSkinPoisonsAndSlowsTheAttackerWithHalfSecondCooldown() {
        val attacker = idle(journey.fighter(InventoryState(), "phantom_assassin")).copy(hp = 5000.0, maxHp = 5000.0)
        var s = DuelState(attacker, victim("viper", hp = 5000.0), 1729)
        s = attackTurn(s)
        val layer = s.player.poisonPool!!.normal.single()
        assertEquals("hero:viper:skin", layer.sourceId)
        assertEquals("1", layer.casterUnitId)
        assertEquals(6.0, layer.damagePerTick, 1e-9)
        assertEquals(3, layer.ticksRemaining)
        assertEquals(0.5, s.enemy.skinIn, 1e-9)
        assertEquals(3.0, s.player.skinSlowTime, 1e-9)
        // 0.5s CD = 5 次 0.1s 流逝；浮点残差（0.5 - 0.1x5 ~= 3.3e-17 > 0）使第 6 次命中仍被门禁挡住
        repeat(5) { s = attackTurn(s) }
        assertEquals("0.5 秒内置 CD 内不再附毒", 1, s.player.poisonPool!!.normal.size)
        s = attackTurn(s)
        assertEquals("CD 结束后再次命中重新附毒", 2, s.player.poisonPool!!.normal.size)
    }

    @Test fun viperUltimateAppliesVirulentWithIndependentBrokenAndSlow() {
        val viper = holder("viper").copy(mana = 100.0, ultIn = 0.0)
        var s = DuelState(viper, victim(hp = 5000.0), 1729)
        s = DuelEngine.step(s)
        val pool = s.enemy.poisonPool!!
        assertEquals(1, pool.virulent.size)
        assertEquals("hero:viper:ultimate", pool.virulent.single().sourceId)
        assertEquals("0", pool.virulent.single().casterUnitId)
        assertEquals(3.0, pool.virulent.single().damagePerTick, 1e-9)
        assertTrue(pool.normal.isEmpty())
        assertEquals(3.0, s.enemy.broken, 1e-9)
        assertEquals(3.0, s.enemy.slowTime, 1e-9)
        assertEquals(30.0, s.enemy.slow, 1e-9)
        // journey.fighter 只把初始 mana 钳到 30（min(mana, 30)），maxMana 保留满值；
        // 用例随后把 mana 改回 100，tick 内的 min(maxMana, mana) 不再生效，扣 75 点大招消耗后余 25
        assertEquals(25.0, s.player.mana, 1e-9)
        assertEquals(9.0, s.player.ultIn, 1e-9)
        s = advance(s, 10)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals(5000.0, s.enemy.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(4997.0, s.enemy.hp, 1e-9)
    }

    // ---------------- A07：弱/强驱散 ----------------

    @Test fun dispelsFollowTheirOwnRulesFromRealBattleState() {
        val viper = holder("viper", "P1" to 1).copy(mana = 100.0, ultIn = 0.0)
        var s = DuelState(viper, victim(hp = 5000.0), 1729)
        s = attackTurn(s)
        val before = s.enemy
        assertEquals(1, before.poisonPool!!.normal.size)
        assertEquals(1, before.poisonPool!!.virulent.size)
        assertTrue(before.broken > 0.0 && before.slowTime > 0.0)

        val weak = FighterEffects.weakDispel(before)
        assertTrue(weak.poisonPool!!.normal.isEmpty())
        assertEquals(1, weak.poisonPool!!.virulent.size)
        assertEquals(0.0, weak.slowTime, 1e-9)
        assertEquals(0.0, weak.skinSlowTime, 1e-9)
        assertEquals("破坏不随普通毒层被误删", before.broken, weak.broken, 1e-9)
        assertEquals(0L, weak.equipment.immunityMs)

        var afterWeak = DuelState(s.player, weak, s.rng, s.tick, s.won, s.log)
        afterWeak = attackTurn(afterWeak)
        assertEquals("无额外免疫窗口：弱驱散后仍可附普通毒", 1, afterWeak.enemy.poisonPool!!.normal.size)
        afterWeak = advance(afterWeak, 10)
        assertTrue("剧毒在弱驱散后继续产毒", afterWeak.enemy.poisonPool!!.normal.size >= 2)

        val strong = FighterEffects.strongDispel(afterWeak.enemy)
        assertNull(strong.poisonPool)
        assertEquals("强驱散不清破坏：broken 不是可驱散减益，只随时间流逝", afterWeak.enemy.broken, strong.broken, 1e-9)
        assertEquals(0.0, strong.slowTime, 1e-9)
        assertEquals(0L, strong.equipment.immunityMs)
        val afterStrong = advance(DuelState(afterWeak.player, strong, afterWeak.rng, afterWeak.tick, afterWeak.won, afterWeak.log), 30)
        assertNull(afterStrong.enemy.poisonPool)
    }

    // ---------------- A08：BKB 免疫与产毒门禁 ----------------

    @Test fun bkbImmunityBlocksVirulentSpawnsWithoutBackfilling() {
        var s = DuelState(gearHolder("phantom_assassin", "black_king_bar"),
            // viper manaCost=75；maxMana 需显式放开，否则大招放不出来
            victim("viper", hp = 5000.0).copy(mana = 100.0, maxMana = 100.0, ultIn = 0.0), 1729)
        s = DuelEngine.step(s)
        assertEquals(1, s.player.poisonPool!!.virulent.size)
        s = DuelEngine.useItem(s, "g0")
        assertEquals(3000L, s.player.equipment.immunityMs)
        assertEquals("弱驱散不清剧毒", 1, s.player.poisonPool!!.virulent.size)
        assertTrue(s.player.poisonPool!!.normal.isEmpty())
        s = advance(s, 29)
        assertTrue("免疫期内不穿透的新普通毒全部被挡", s.player.poisonPool!!.normal.isEmpty())
        assertEquals(5000.0, s.player.hp, 1e-9)
        s = advance(s, 1)
        assertEquals("免疫结束后恢复产毒", 1, s.player.poisonPool!!.normal.size)
        s = advance(s, 10)
        // 幻影刺客自身魔抗 0.25 + 15x0.001 = 0.265；BKB 的 20% 魔抗只随减益免疫生效，此时已结束
        assertEquals("只结算存留层的一次跳（含自身魔抗），不补发被挡层", 3.0 * (1 - s.player.magicResist), 5000.0 - s.player.hp, 1e-9)
        assertEquals(2, s.player.poisonPool!!.normal.size)
    }

    @Test fun bkbImmunityBlocksNewNormalPoisonApplicationUntilItEnds() {
        var s = DuelState(gearHolder("phantom_assassin", "black_king_bar").copy(attackIn = 0.0), victim("viper", hp = 5000.0), 1729)
        s = attackTurn(s)
        assertEquals(1, s.player.poisonPool!!.normal.size)
        assertEquals(3.0, s.player.skinSlowTime, 1e-9)
        s = DuelEngine.useItem(s, "g0")
        assertEquals(3000L, s.player.equipment.immunityMs)
        assertNull("开启时弱驱散清掉已附普通毒", s.player.poisonPool)
        repeat(29) { s = attackTurn(s) }
        assertNull("免疫期内新的普通毒施加被门禁拒绝", s.player.poisonPool)
        s = attackTurn(s)
        assertEquals("免疫结束后新施加放行", 1, s.player.poisonPool!!.normal.size)
    }

    // ---------------- A09：D5 毒血回流 ----------------

    @Test fun d5ConvertsOwnRealPoisonTickDamageIntoHealing() {
        // 真实 P1 普攻附毒；D5 与 P1 为两种毒卡，2 档增伤 +10% 一并生效
        var s = DuelState(holder("phantom_assassin", "D5" to 3, "P1" to 3).copy(hp = 400.0, maxHp = 500.0).copy(attackIn = 0.0),
            victim(hp = 5000.0), 1729)
        s = attackTurn(s)
        val enemyBase = s.enemy.hp
        val playerBase = s.player.hp
        s = advance(s, 10)
        assertEquals(enemyBase - 13.2, s.enemy.hp, 1e-9)
        assertEquals(playerBase + 2.64, s.player.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(enemyBase - 26.4, s.enemy.hp, 1e-9)
        assertEquals(playerBase + 5.28, s.player.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(enemyBase - 39.6, s.enemy.hp, 1e-9)
        assertEquals(playerBase + 7.92, s.player.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(enemyBase - 39.6, s.enemy.hp, 1e-9)
        assertEquals(playerBase + 7.92, s.player.hp, 1e-9)
    }

    @Test fun d5HealCountsOnlyOwnTicksNotForeignPoisonDamage() {
        val player = holder("phantom_assassin", "D5" to 3).copy(hp = 400.0, maxHp = 500.0)
        var enemy = victim(hp = 5000.0)
        enemy = PoisonCombat.apply(enemy, sourceUnit = 0, sourceId = "card:P1", serial = 0, nowTick = 0, virulent = false, layers = 1, damage = 12.0)
        enemy = PoisonCombat.apply(enemy, sourceUnit = 1, sourceId = "foreign", serial = 0, nowTick = 0, virulent = false, layers = 1, damage = 20.0)
        var s = DuelState(player, enemy, 1729)
        s = advance(s, 10)
        assertEquals("自身 12 + 外来 20", 5000.0 - 32.0, s.enemy.hp, 1e-9)
        assertEquals("只按自身实际毒伤 12 的 20% 治疗", 400.0 + 2.4, s.player.hp, 1e-9)
    }

    @Test fun d5ShieldAbsorbedPortionConvertsAtFiftyPercent() {
        val player = holder("phantom_assassin", "D5" to 3).copy(hp = 400.0, maxHp = 500.0)
        val shielded = idle(journey.fighter(InventoryState(skillSlots = 8,
            items = listOf(StoredItem("s1", "S1", ItemPlace(Container.SKILLS, 0), star = 3))), "juggernaut"))
            .copy(hp = 5000.0, maxHp = 5000.0, magicResist = 0.0, armor = 0.0)
        assertEquals(240.0, ShieldLedger.total(FighterEffects.shields(shielded)), 1e-9)
        var enemy = PoisonCombat.apply(shielded, 0, "card:P1", 0, 0, false, 1, 12.0)
        var s = DuelState(player, enemy, 1729)
        s = advance(s, 10)
        assertEquals("本跳全部被护盾吸收，不掉血", 5000.0, s.enemy.hp, 1e-9)
        assertEquals(228.0, ShieldLedger.total(FighterEffects.shields(s.enemy)), 1e-9)
        assertEquals("打盾部分按 50% 折算：(0 + 12×50%)×20%", 400.0 + 1.2, s.player.hp, 1e-9)
    }

    @Test fun d5OverkillDoesNotInflateHealing() {
        val player = holder("phantom_assassin", "D5" to 3).copy(hp = 400.0, maxHp = 500.0)
        var enemy = PoisonCombat.apply(victim(hp = 5.0), 0, "card:P1", 0, 0, false, 1, 12.0)
        var s = DuelState(player, enemy, 1729)
        s = advance(s, 10)
        assertEquals(0.0, s.enemy.hp, 1e-9)
        assertEquals("只按实际掉血 5 的 20% 治疗", 400.0 + 1.0, s.player.hp, 1e-9)
        assertEquals(true, s.won)
    }

    @Test fun d5VirulentSpawnItselfDoesNotHeal() {
        var s = DuelState(holder("phantom_assassin", "D5" to 3, "P2" to 1).copy(hp = 400.0, maxHp = 500.0), victim(hp = 5000.0), 1729)
        val base = s.player.hp
        s = advance(s, 80)
        assertEquals(1, s.enemy.poisonPool!!.virulent.size)
        assertEquals(base, s.player.hp, 1e-9)
        s = advance(s, 10)
        assertEquals(1, s.enemy.poisonPool!!.normal.size)
        assertEquals("产毒事件不治疗", base, s.player.hp, 1e-9)
        s = advance(s, 10)
        assertEquals("剧毒生成的普通毒跳按 20% 治疗（含 2 档 +10%）", base + 0.66, s.player.hp, 1e-9)
    }

    @Test fun deadPoisonCasterIsNotResurrectedByOwnTick() {
        val player = holder("phantom_assassin", "D5" to 3).copy(hp = 5.0, maxHp = 500.0)
        var enemy = PoisonCombat.apply(victim(hp = 5000.0), 0, "card:P1", 0, 0, false, 1, 12.0)
        val dying = PoisonCombat.apply(player, 1, "enemy-poison", 0, 0, false, 1, 20.0)
        var s = DuelState(dying, enemy, 1729)
        s = advance(s, 10)
        assertEquals("同一 tick 内先死于敌方毒跳", 0.0, s.player.hp, 1e-9)
        assertEquals("自身毒跳仍对敌人结算", 5000.0 - 12.0, s.enemy.hp, 1e-9)
        assertEquals(false, s.won)
    }
}
