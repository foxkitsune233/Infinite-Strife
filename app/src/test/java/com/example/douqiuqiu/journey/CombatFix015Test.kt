package com.example.douqiuqiu.journey

import com.example.douqiuqiu.cards.CardBuff
import com.example.douqiuqiu.cards.CardBuffKind
import com.example.douqiuqiu.cards.CardEffectHost
import com.example.douqiuqiu.combat.CombatMath
import com.example.douqiuqiu.content.AttackClass
import com.example.douqiuqiu.content.DamageType
import com.example.douqiuqiu.content.EffectFlags
import com.example.douqiuqiu.content.EffectKind
import com.example.douqiuqiu.content.EffectTrigger
import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.content.EquipmentCategory
import com.example.douqiuqiu.content.HeroContent
import com.example.douqiuqiu.content.HeroSkillInfo
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.equipment.EquipmentCombat
import com.example.douqiuqiu.equipment.EquipmentEffectRuntime
import com.example.douqiuqiu.equipment.EquippedGear
import com.example.douqiuqiu.event.EventKind
import com.example.douqiuqiu.event.NodeEventInstance
import com.example.douqiuqiu.inventory.CampaignBinaryCodec
import com.example.douqiuqiu.inventory.Container
import com.example.douqiuqiu.inventory.EquipmentStats
import com.example.douqiuqiu.inventory.InventoryPhase
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.inventory.InventoryStore
import com.example.douqiuqiu.inventory.ItemDefinition
import com.example.douqiuqiu.inventory.ItemKind
import com.example.douqiuqiu.inventory.ItemPlace
import com.example.douqiuqiu.inventory.StoredItem
import com.example.douqiuqiu.status.ControlKind
import com.example.douqiuqiu.status.DispelClass
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.ceil

/**
 * 63 号外部验证表的自动化部分（0.1.5 战斗接线修复 + 旧英雄平衡）。
 *
 * 覆盖 Q01–Q08 与 H01–H03 中不依赖设备与概率的部分：
 * 固定种子、直接构造快照或可控 roll；设备级项目（播放表现、技能页渲染）由外部执行。
 */
class CombatFix015Test {
    private val defs = (EquipmentCatalog.all.map { d ->
        ItemDefinition(d.id, d.name,
            if (d.category == EquipmentCategory.SCROLL) ItemKind.SCROLL else ItemKind.EQUIPMENT,
            d.width, d.height, d.quality.label, stats = d.stats ?: EquipmentStats(),
            price = d.price, minimumShopClears = d.minimumShopClears)
    } + listOf(ItemDefinition("blade", "刃", ItemKind.EQUIPMENT, 1, 1),
        ItemDefinition("essence", "精华", ItemKind.MATERIAL, 1, 1))).associateBy { it.id }
    private val inventory = InventoryRules(defs)
    private val journey = JourneyRules(inventory)

    /** 不行动的对手：无蓝、大招与普攻均在冷却。 */
    private fun idle(hero: String): Fighter = journey.fighter(InventoryState(), hero)
        .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 60.0)

    private fun gearFighter(gearId: String, hero: String): Fighter =
        journey.fighter(InventoryState(items = listOf(StoredItem("g1", gearId, ItemPlace(Container.EQUIPMENT, 0)))), hero)
            .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 60.0)

    private fun cardFighter(hero: String, vararg cards: Pair<String, String>): Fighter =
        journey.fighter(InventoryState(items = cards.mapIndexed { i, (id, _) ->
            StoredItem("t${i + 1}", id, ItemPlace(Container.SKILLS, i))
        }), hero).copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 60.0)

    private fun chest() = TargetChest("c", "白", 0, 0, listOf(ChestItem("blade", 0, 0, 2)))

    /** 战斗快照存档往返；返回读档后的战斗状态。 */
    private fun roundTrip(state: DuelState): DuelState {
        val run = JourneyState("run-rt", state.player.heroId, chest(), state.rng,
            state.player.hp, state.player.maxHp, stage = JourneyStage.BATTLE, fight = state)
        val bytes = ByteArrayOutputStream().also {
            DataOutputStream(it).use { out -> CampaignBinaryCodec.write(out, CampaignProgress(run = run)) }
        }.toByteArray()
        return ByteArrayInputStream(bytes).use {
            DataInputStream(it).use { CampaignBinaryCodec.read(it, InventoryStore.VERSION) }
        }.run!!.fight!!
    }

    // ---------------- Q01 时间结界 ----------------

    @Test fun timeStopLastsThreeSecondsAndExpiresAfterTheThirtiethFrame() {
        val player = journey.fighter(InventoryState(), "faceless_void").copy(mana = 300.0, ultIn = 0.0)
        val enemy = journey.fighter(InventoryState(), "abaddon").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        var state = DuelEngine.step(DuelState(player, enemy, 1729))
        val stop = state.enemy.timedControls.single {
            it.kind == ControlKind.TIME_STOP && it.sourceId == "hero:faceless_void:ult"
        }
        assertEquals("时间结界应为 3 秒", 3000L, stop.remainingMs)
        assertEquals(DispelClass.UNDISPELLABLE, stop.dispelClass)
        repeat(29) { state = DuelEngine.step(state) }
        assertTrue("第 29 帧时停应仍在", state.enemy.timedControls.any { it.kind == ControlKind.TIME_STOP })
        state = DuelEngine.step(state)
        assertTrue("第 30 帧后时停应消失", state.enemy.timedControls.none { it.kind == ControlKind.TIME_STOP })
        val resumed = state.copy(enemy = state.enemy.copy(attackIn = 0.0))
        assertTrue("恢复后目标应能普攻", DuelEngine.step(resumed).player.hp < resumed.player.maxHp)
    }

    @Test fun timeStopRemainingTimeSurvivesSaveRoundTrip() {
        val player = journey.fighter(InventoryState(), "faceless_void").copy(mana = 300.0, ultIn = 0.0)
        val enemy = journey.fighter(InventoryState(), "abaddon").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        var state = DuelEngine.step(DuelState(player, enemy, 1729))
        repeat(9) { state = DuelEngine.step(state) }
        val before = state.enemy.timedControls.single { it.kind == ControlKind.TIME_STOP }.remainingMs
        // 施放帧不流逝，之后每 tick 减 100ms：施放后第 9 个 tick 剩余 2100ms（30 帧总时长到期）。
        assertEquals("施放后第 9 个 tick 应剩余 2100ms", 2100L, before)
        val restored = roundTrip(state)
        assertEquals("中途存读后剩余时间应一致", before,
            restored.enemy.timedControls.single { it.kind == ControlKind.TIME_STOP }.remainingMs)
        var original = state
        var read = restored
        repeat(21) {
            original = DuelEngine.step(original)
            read = DuelEngine.step(read)
            assertEquals("读档后每帧剩余时间应与原档一致",
                original.enemy.timedControls.firstOrNull { it.kind == ControlKind.TIME_STOP }?.remainingMs,
                read.enemy.timedControls.firstOrNull { it.kind == ControlKind.TIME_STOP }?.remainingMs)
        }
        assertTrue("读档后时停仍应在第 30 帧到期", read.enemy.timedControls.none { it.kind == ControlKind.TIME_STOP })
    }

    // ---------------- Q02 寒霜负面、存档与弱驱散 ----------------

    @Test fun frostDebuffsAreSourceKeyedSurviveSaveAndFallToWeakDispel() {
        val player = cardFighter("phantom_assassin", "FS1" to "t1", "D8" to "t2", "FS4" to "t3", "C4" to "t4")
            .copy(ultIn = 999.0, attackIn = 0.0, criticalReady = 4.0)
        var state = DuelState(player, idle("abaddon"), 1729)
        var guard = 0
        while (guard++ < 60) {
            state = DuelEngine.step(state.copy(player = state.player.copy(attackIn = 0.0, criticalReady = 4.0)))
            val buffs = state.enemy.cardState.buffs
            if (buffs.any { it.kind == CardBuffKind.SLOW } && buffs.any { it.kind == CardBuffKind.VULNERABILITY } &&
                buffs.any { it.kind == CardBuffKind.ARMOR_REDUCTION }) break
        }
        val buffs = state.enemy.cardState.buffs
        val slows = buffs.filter { it.kind == CardBuffKind.SLOW }.sortedBy { it.sourceKey }
        assertEquals("FS1 与 D8 应分别按来源施加减速", listOf("0:t1:FS1.slow", "0:t2:D8.slow"), slows.map { it.sourceKey })
        assertEquals(15.0, slows[0].amount, 1e-9)
        assertEquals(12.0, slows[1].amount, 1e-9)
        val vuln = buffs.single { it.kind == CardBuffKind.VULNERABILITY }
        assertEquals("0:t3:FS4.vuln", vuln.sourceKey)
        assertEquals(15.0, vuln.amount, 1e-9)
        val shred = buffs.single { it.kind == CardBuffKind.ARMOR_REDUCTION }
        assertEquals("0:t4:C4.armorShred", shred.sourceKey)
        assertTrue("减速与易伤应按有限到期保存",
            slows.all { it.expiresAtTick in (state.tick + 1)..(state.tick + 20) } && vuln.expiresAtTick in (state.tick + 1)..(state.tick + 20))
        val read = roundTrip(state)
        val rb = read.enemy.cardState.buffs.associate { it.sourceKey to it }
        assertEquals(buffs.map { it.sourceKey }.sorted(), rb.keys.sorted())
        for (b in buffs) {
            assertEquals(b.amount, rb.getValue(b.sourceKey).amount, 1e-9)
            assertEquals("有限到期时间应随存档保留", b.expiresAtTick, rb.getValue(b.sourceKey).expiresAtTick)
        }
        val progress = CampaignProgress(run = JourneyState("run-q02", read.player.heroId, chest(), read.rng,
            read.player.hp, read.player.maxHp, stage = JourneyStage.BATTLE, fight = read))
        assertNull("快照应通过存档校验", JourneyValidation.check(progress, defs))
        val dispelled = FighterEffects.weakDispel(read.enemy)
        assertTrue("弱驱散应清除减甲、减速、易伤三种敌方负面",
            dispelled.cardState.buffs.none { it.kind == CardBuffKind.ARMOR_REDUCTION || it.kind == CardBuffKind.SLOW || it.kind == CardBuffKind.VULNERABILITY })
    }

    @Test fun forgedCardBuffSourceIsRejectedBySaveValidation() {
        val enemy = idle("abaddon").copy(cardState = idle("abaddon").cardState.copy(
            buffs = listOf(CardBuff("0:ghost:C4.armorShred", CardBuffKind.ARMOR_REDUCTION, 1.0, Int.MAX_VALUE))))
        val fight = DuelState(idle("viper"), enemy, 7, 3, null, listOf("x"))
        val run = JourneyState("run-q02f", "viper", chest(), 1729, 100.0, 100.0, 1,
            JourneyStage.BATTLE, fight = fight)
        assertNotNull("伪造来源的减甲层应被存档校验拒绝", JourneyValidation.check(CampaignProgress(run = run), defs))
    }

    // ---------------- Q03 斧王嘲讽护甲 ----------------

    @Test fun axeTauntArmorLowersIncomingDamageOnlyWhileActive() {
        val axe = journey.fighter(InventoryState(), "axe")
            .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 999.0,
                hp = 99999.0, maxHp = 99999.0)
        val attacker = journey.fighter(InventoryState(), "skywrath")
            .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 0.0, ultIn = 999.0)
        val first = DuelEngine.step(DuelState(attacker, axe, 1729))
        val dmgBefore = axe.hp - first.enemy.hp
        assertTrue("天怒普攻应造成非暴击物理伤害", dmgBefore > 0)
        val roared = DuelEngine.step(DuelState(attacker.copy(attackIn = 0.0),
            first.enemy.copy(mana = 300.0, ultIn = 0.0, attackIn = 999.0), 1729))
        assertTrue("斧王应嘲讽攻击者", roared.player.timedControls.any { it.kind == ControlKind.TAUNT })
        assertEquals("施吼期间应获得 12 点护甲", HeroContent.skill("axe").selfArmor, roared.enemy.armorBuff, 1e-9)
        val second = DuelEngine.step(roared.copy(player = roared.player.copy(attackIn = 0.0)))
        val dmgDuring = roared.enemy.hp - second.enemy.hp
        assertTrue("嘲讽护甲期间实际生命伤害应降低（$dmgDuring < $dmgBefore）", dmgDuring < dmgBefore - 1.0)
        var state = second
        repeat(25) { state = DuelEngine.step(state.copy(player = state.player.copy(attackIn = 0.0))) }
        assertEquals("到期后护甲增益应清零", 0.0, state.enemy.armorBuff, 1e-9)
        assertTrue("到期后嘲讽应移除", state.player.timedControls.none { it.kind == ControlKind.TAUNT })
        val third = DuelEngine.step(state.copy(player = state.player.copy(attackIn = 0.0)))
        assertEquals("到期后伤害应回到施吼前数值", dmgBefore, state.enemy.hp - third.enemy.hp, 0.5)
    }

    // ---------------- Q04 状态抗性 ----------------

    @Test fun fiftyPercentResistCompressesThreeSecondControlToFifteenFrames() {
        assertEquals(0.5, CombatMath.statusDurationMultiplier(listOf(0.5)), 1e-9)
        val compressed = ceil(3.0 * CombatMath.statusDurationMultiplier(listOf(0.5)) * 10).toLong() * 100
        assertEquals("50% 抗性下 3000ms 控制应量化为 1500ms", 1500L, compressed)
    }

    @Test fun realResistItemCompressesEveryControlSourceExactlyOnce() {
        // 散华 16% 抗性：3 秒控制量化为 2600ms；若被二次压缩会是 2080ms。
        val resisted = idle("abaddon").copy(equipment = EquipmentBattleState(items = listOf(EquippedGear("g1", "sange"))))
        val applied = com.example.douqiuqiu.status.TimedControls.apply(resisted, "hero:axe:ult", 0,
            ControlKind.TAUNT, 3.0, DispelClass.BASIC, 0)
        assertEquals("3 秒 × (1-16%) 应向上取整到 2600ms", 2600L, applied.durationMs)
        var controls = applied.fighter.timedControls
        repeat(25) { controls = com.example.douqiuqiu.status.TimedControls.elapse(controls) }
        assertTrue("第 25 帧控制应仍在", controls.isNotEmpty())
        controls = com.example.douqiuqiu.status.TimedControls.elapse(controls)
        assertTrue("第 26 帧后控制应消失", controls.isEmpty())
        // 斧王护甲与军团攻速增益时长跟随实际嘲讽时长。
        val axe = journey.fighter(InventoryState(), "axe").copy(mana = 300.0, ultIn = 0.0)
        val taunted = DuelEngine.step(DuelState(axe, resisted, 1729))
        assertEquals("2.5 秒嘲讽在 16% 抗性下应为 2100ms", 2100L,
            taunted.enemy.timedControls.single { it.kind == ControlKind.TAUNT }.remainingMs)
        assertEquals("自身护甲时长应与实际嘲讽相同", 2.1, taunted.player.armorBuffMs, 1e-9)
        val legion = journey.fighter(InventoryState(), "legion_commander").copy(mana = 300.0, ultIn = 0.0)
        val duel = DuelEngine.step(DuelState(legion, resisted, 1729))
        assertEquals("4 秒决斗嘲讽在 16% 抗性下应为 3400ms", 3400L,
            duel.enemy.timedControls.single { it.kind == ControlKind.TAUNT }.remainingMs)
        assertEquals("攻速增益时长应与实际嘲讽相同", 3.4, duel.player.speedBuffMs, 1e-9)
        // 敌法眩晕 / 虚空时停 / FS3 冻结同样只压缩一次。
        val am = journey.fighter(InventoryState(), "anti_mage").copy(mana = 300.0, ultIn = 0.0, amManaBurn = 50.0)
        val voided = DuelEngine.step(DuelState(am, resisted, 1729))
        val stun = voided.enemy.timedControls.single { it.sourceId == "hero:anti_mage:ult" }
        assertEquals("0.2 秒眩晕在 16% 抗性下应为 200ms", 200L, stun.remainingMs)
        val fv = journey.fighter(InventoryState(), "faceless_void").copy(mana = 300.0, ultIn = 0.0)
        val stopped = DuelEngine.step(DuelState(fv, resisted, 1729))
        assertEquals("3 秒时间结界在 16% 抗性下应为 2600ms", 2600L,
            stopped.enemy.timedControls.single { it.kind == ControlKind.TIME_STOP }.remainingMs)
        val frost = cardFighter("viper", "FS3" to "t1")
        var freezeState = DuelState(frost, resisted, 1729)
        var guard = 0
        while (guard++ < 60 && freezeState.enemy.timedControls.none { it.kind == ControlKind.FREEZE }) {
            freezeState = DuelEngine.step(freezeState)
        }
        val freeze = freezeState.enemy.timedControls.single { it.kind == ControlKind.FREEZE }
        assertEquals("0.6 秒冻结在 16% 抗性下应为 600ms", 600L, freeze.remainingMs)
    }

    // ---------------- Q05 时间锁定与 BKB ----------------

    @Test fun timeLockStunsAndExtendsCooldownOnlyWhenTargetIsNotImmune() {
        fun duel(seed: Long, immune: Boolean): DuelState {
            val player = journey.fighter(InventoryState(), "faceless_void")
                .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, ultIn = 999.0, attackIn = 0.0)
            val enemy = journey.fighter(InventoryState(), "axe")
                .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, ultIn = 50.0, attackIn = 999.0,
                    equipment = if (immune) EquipmentBattleState(immunityMs = 100000L) else EquipmentBattleState())
            return DuelEngine.step(DuelState(player, enemy, seed))
        }
        var seed = 0L
        for (candidate in 1..60) {
            if (duel(candidate.toLong(), false).enemy.timedControls.any { it.sourceId == "hero:faceless_void:timelock" }) {
                seed = candidate.toLong(); break
            }
        }
        assertTrue("应能找到触发时间锁定的种子", seed > 0)
        val landed = duel(seed, false)
        val stun = landed.enemy.timedControls.single { it.sourceId == "hero:faceless_void:timelock" }
        assertEquals(ControlKind.STUN, stun.kind)
        assertEquals("0.35 秒眩晕应向上取整到 400ms", 400L, stun.remainingMs)
        assertTrue("命中时应延长敌方大招冷却", landed.log.any { it.contains("时间锁定延长敌方大招冷却") })
        assertTrue("延长后大招 CD 应高于单纯递减", landed.enemy.ultIn > 50.0)
        val blocked = duel(seed, true)
        assertTrue("BKB 免疫时不应眩晕", blocked.enemy.timedControls.none { it.sourceId == "hero:faceless_void:timelock" })
        assertFalse("BKB 免疫时不应延长大招 CD", blocked.log.any { it.contains("时间锁定延长敌方大招冷却") })
        assertEquals("免疫时大招 CD 只随战斗时钟递减", 49.9, blocked.enemy.ultIn, 1e-9)
        // BKB 通过上游 broken 判定使整段时间锁定不执行（不施放、不延长 CD），由上方两项覆盖。
    }

    // ---------------- Q06 霜棱之刃 proc ----------------

    private class FrostProbe(player: Fighter, enemy: Fighter) : CardEffectHost {
        val actors = mutableListOf(player, enemy)
        var tick = 1
        val damage = mutableListOf<Triple<Double, DamageType, AttackClass>>()
        var rollValue = 0.0
        override val cardTick get() = tick
        override fun fighter(index: Int) = actors[index]
        override fun update(index: Int, fighter: Fighter) { actors[index] = fighter }
        override fun controlEvent(source: Int, target: Int, sourceId: String, result: String, durationMs: Long) = Unit
        override fun roll(): Double = rollValue
        override fun healCard(index: Int, amount: Double) = Unit
        override fun damageCard(source: Int, target: Int, amount: Double, type: DamageType,
                                attackClass: AttackClass, flags: EffectFlags, effectId: String) {
            damage += Triple(amount, type, attackClass)
            actors[target] = actors[target].copy(hp = (actors[target].hp - amount).coerceAtLeast(0.0))
        }
        override fun poisonCard(source: Int, target: Int, sourceId: String, virulent: Boolean, layers: Int, damage: Double) = Unit
    }

    @Test fun frostShardProcAppliesBurstAndSlowTogetherBehindOneCooldown() {
        val attacker = gearFighter("frost_shard_blade", "phantom_assassin")
        val target = idle("abaddon")
        val proc = FrostProbe(attacker, target)
        proc.rollValue = 0.0
        EquipmentEffectRuntime.trigger(proc, 0, EffectTrigger.ATTACK_LAND, { proc.rollValue })
        assertEquals("成功时应同时给 45 点魔法伤害", 1, proc.damage.size)
        assertEquals(45.0, proc.damage.single().first, 1e-9)
        assertEquals(DamageType.MAGICAL, proc.damage.single().second)
        assertEquals(AttackClass.SPELL, proc.damage.single().third)
        assertEquals("同一次命中应施加减速", 20.0, proc.fighter(1).slow, 1e-9)
        assertEquals("减速时长应为 1500ms", 1.5, proc.fighter(1).slowTime, 1e-9)
        assertEquals(1500L, proc.fighter(0).equipment.cooldowns.remainingByGroup["frost_shard"])
        EquipmentEffectRuntime.trigger(proc, 0, EffectTrigger.ATTACK_LAND, { proc.rollValue })
        assertEquals("1500ms 内置 CD 内两者均不再触发", 1, proc.damage.size)
        assertEquals("1500ms 内减速不应被刷新", 1.5, proc.fighter(1).slowTime, 1e-9)
        repeat(15) { proc.update(0, proc.fighter(0).copy(equipment = EquipmentCombat.elapse(proc.fighter(0).equipment))) }
        EquipmentEffectRuntime.trigger(proc, 0, EffectTrigger.ATTACK_LAND, { proc.rollValue })
        assertEquals("内置 CD 结束后应恢复触发", 2, proc.damage.size)
        val miss = FrostProbe(attacker, target)
        miss.rollValue = 0.9
        EquipmentEffectRuntime.trigger(miss, 0, EffectTrigger.ATTACK_LAND, { miss.rollValue })
        assertTrue("失败时两者都不触发", miss.damage.isEmpty())
        assertEquals(0.0, miss.fighter(1).slow, 1e-9)
        assertFalse(miss.fighter(0).equipment.cooldowns.remainingByGroup.containsKey("frost_shard"))
    }

    // ---------------- Q07 反击螺旋 ----------------

    @Test fun axeCounterHelixFiresExactlyOnceEveryFourBasicHits() {
        val axe = idle("axe").copy(hp = 99999.0, maxHp = 99999.0)
        val attacker = journey.fighter(InventoryState(), "skywrath")
            .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 0.0, ultIn = 999.0)
        var state = DuelState(attacker, axe, 1729)
        val cues = mutableListOf<com.example.douqiuqiu.journey.DuelVisualCue>()
        var hpBefore = 0.0
        var found = false
        for (t in 0 until 60) {
            hpBefore = state.player.hp
            val frame = DuelEngine.stepWithCues(state)
            state = frame.state
            cues += frame.cues
            if (cues.any { it.kind == com.example.douqiuqiu.journey.DuelCueKind.COUNTER_HELIX }) {
                found = true
                break
            }
        }
        assertTrue("4 次基础普攻后应触发反击螺旋", found)
        assertEquals("应恰有一次 COUNTER_HELIX cue", 1,
            cues.count { it.kind == com.example.douqiuqiu.journey.DuelCueKind.COUNTER_HELIX })
        assertEquals("触发后累计计数应归零", 0, state.enemy.axeHitsSinceSpin)
        assertTrue("反击螺旋应造成纯粹伤害", state.player.hp <= hpBefore - 50.0)
        assertTrue(state.log.any { it.contains("反击螺旋") && it.contains("真实伤害") })
    }

    // ---------------- Q08 失控炼金室强行破封 ----------------

    @Test fun alchemyBreakSealCostsEightPercentPlusNinePoisonDamageImmediately() {
        val chest = TargetChest("target", "蓝", 0, 60, listOf(ChestItem("blade", 0, 0)))
        val node = NodeEventInstance("run:event:1", EventKind.ALCHEMY_LAB, 10, "", 30)
        val before = InventoryState(runGold = 100, phase = InventoryPhase.PREPARATION,
            progress = CampaignProgress(clears = 2, run = JourneyState("run", "juggernaut", chest,
                1729L, 50.0, 100.0, stage = JourneyStage.EVENT, nodeEvent = node)))
        val result = JourneyEvents.resolve(before, inventory, "alchemy_lab.break_seal")
        assertTrue(result.error, result.ok)
        val run = result.state.progress.run!!
        assertEquals("破封应结算 -8% 最大生命并立即跳 1 层普通毒 3 次（1×3×3=9）", 33.0, run.hp, 1e-9)
        assertEquals(JourneyStage.REWARD, run.stage)
        assertTrue("应掉落组件奖励", run.nodeLoot!!.items.any { it.definitionId in EquipmentCatalog.components.map { c -> c.id } })
        assertNull("事件不留下场外中毒状态", run.nodeEvent)
        assertTrue("背包物品不应被改动", result.state.items.isEmpty())
    }

    // ---------------- H01 亚巴顿耗蓝 ----------------

    @Test fun abaddonAphoticShieldCostsOneHundredFortyMana() {
        assertEquals("亚巴顿无光之盾耗蓝应为 140", 140, FirstRunContent.hero("abaddon").manaCost)
        assertEquals("技能页应显示 140", 140, HeroSkillInfo.of("abaddon").manaCost)
        val enemy = idle("viper")
        val poor = journey.fighter(InventoryState(), "abaddon")
            .copy(mana = 139.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 0.0)
        val rich = journey.fighter(InventoryState(), "abaddon")
            .copy(mana = 140.0, manaRegen = 0.0, regen = 0.0, attackIn = 999.0, ultIn = 0.0)
        val blocked = DuelEngine.step(DuelState(poor, enemy, 1729))
        assertEquals("139 点魔法不足以施放", 139.0, blocked.player.mana, 1e-9)
        assertEquals(0.0, blocked.player.shield, 1e-9)
        val cast = DuelEngine.step(DuelState(rich, enemy, 1729))
        assertEquals("施放应扣除 140 点魔法", 0.0, cast.player.mana, 1e-9)
        assertTrue("应获得无光之盾", cast.player.shield > 100.0)
        assertEquals("大招 CD 应为 9 秒", 9.0, cast.player.ultIn, 1e-9)
    }

    // ---------------- H02 主宰无敌斩 ----------------

    @Test fun juggernautOmnislashLandsMoreHitsAndCountsAsFullAttacks() {
        val cfg = HeroContent.legacy("juggernaut")
        assertEquals(2.0, cfg.durationSec, 1e-9)
        assertEquals(200.0, cfg.attackSpeedPercent, 1e-9)
        assertEquals("无敌斩 CD 应为 16 秒", 16.0, FirstRunContent.hero("juggernaut").cooldown, 1e-9)
        val view = HeroSkillInfo.of("juggernaut")
        assertTrue(view.values.any { it.label == "无敌时长" && it.value.startsWith("2 ") })
        assertTrue(view.values.any { it.label == "攻速" && it.value == "+200%" })
        val player = journey.fighter(InventoryState(), "juggernaut")
            .copy(mana = 300.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 0.0)
        val enemy = idle("viper").copy(hp = 99999.0, maxHp = 99999.0)
        var state = DuelState(player, enemy, 1729)
        var slashes = 0
        var frames = 0
        // 大招在第一步内施放，先推进一帧再按无敌状态循环。
        var frame = DuelEngine.stepWithCues(state)
        state = frame.state
        frames++
        slashes += frame.cues.count { it.kind == com.example.douqiuqiu.journey.DuelCueKind.OMNI_SLASH }
        while (state.player.invulnerable > 0 && frames < 40) {
            frame = DuelEngine.stepWithCues(state)
            state = frame.state
            frames++
            slashes += frame.cues.count { it.kind == com.example.douqiuqiu.journey.DuelCueKind.OMNI_SLASH }
        }
        assertTrue("2 秒无敌期内斩击频率应明显提高（实际 $slashes 次）", slashes >= 4)
        assertEquals("每一斩应计为一次完整普通攻击", slashes, state.player.cardState.attackCount)
        assertEquals("无敌结束后斩击停止", 0,
            DuelEngine.stepWithCues(state).cues.count { it.kind == com.example.douqiuqiu.journey.DuelCueKind.OMNI_SLASH })
    }

    // ---------------- H03 天怒神秘之耀 ----------------

    @Test fun skywrathMysticFlareHitsEightTimesEveryQuarterSecond() {
        val cfg = HeroContent.legacy("skywrath")
        assertEquals(8, cfg.hits)
        assertEquals(60.0, cfg.damagePerHit, 1e-9)
        val view = HeroSkillInfo.of("skywrath")
        assertTrue(view.values.any { it.label == "打击次数" && it.value.startsWith("8 ") })
        assertTrue(view.values.any { it.label == "单次伤害" && it.value.contains("60") })
        val player = journey.fighter(InventoryState(), "skywrath")
            .copy(mana = 300.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 0.0)
        val enemy = idle("viper").copy(hp = 99999.0, maxHp = 99999.0)
        var state = DuelState(player, enemy, 1729)
        var flareTicks = 0
        var guard = 0
        while (guard++ < 60) {
            val frame = DuelEngine.stepWithCues(state)
            state = frame.state
            flareTicks += frame.cues.count { it.kind == com.example.douqiuqiu.journey.DuelCueKind.FLARE_TICK }
            if (flareTicks >= 8 && state.player.flareLeft <= 0) break
        }
        assertEquals("神秘之耀应共 8 段", 8, flareTicks)
        assertEquals("段数耗尽后不应有剩余", 0, state.player.flareLeft)
    }
}
