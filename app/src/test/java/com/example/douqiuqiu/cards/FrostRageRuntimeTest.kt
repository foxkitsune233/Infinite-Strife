package com.example.douqiuqiu.cards

import com.example.douqiuqiu.content.HeroContent
import com.example.douqiuqiu.equipment.EquipmentBattleState
import com.example.douqiuqiu.equipment.EquipmentCooldownState
import com.example.douqiuqiu.inventory.CampaignBinaryCodec
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.CampaignProgress
import com.example.douqiuqiu.journey.ChestItem
import com.example.douqiuqiu.journey.DuelEngine
import com.example.douqiuqiu.journey.DuelState
import com.example.douqiuqiu.journey.FirstRunContent
import com.example.douqiuqiu.journey.JourneyRules
import com.example.douqiuqiu.journey.JourneyStage
import com.example.douqiuqiu.journey.JourneyState
import com.example.douqiuqiu.journey.JourneyValidation
import com.example.douqiuqiu.journey.TargetChest
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.status.ActionBlock
import com.example.douqiuqiu.status.ActionControlState
import com.example.douqiuqiu.status.ActionPermission
import com.example.douqiuqiu.status.CombatAction
import com.example.douqiuqiu.status.ControlKind
import com.example.douqiuqiu.status.DispelClass
import com.example.douqiuqiu.status.TimedControl
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * 0.1.5 怒火/寒霜卡牌运行时（P3）与新控制状态测试。
 *
 * 覆盖：减速、冻结、冰霜 DOT、冰霜易伤的施加/免疫/叠加上限，
 * 时停对技能与装备 CD 的暂停，以及 hero/卡牌来源控制在存档校验中的放行。
 */
class FrostRageRuntimeTest {
    private val journey = JourneyRules(InventoryRules(emptyMap()))

    private fun base(hero: String): Fighter = journey.fighter(InventoryState(), hero)
        .copy(mana = 0.0, manaRegen = 0.0, regen = 0.0, attackIn = 60.0, ultIn = 60.0)

    private fun withCard(hero: String, cardId: String, star: Int = 1, instance: String = "t1"): Fighter =
        base(hero).copy(cardState = CardBattleState(cards = listOf(CombatCard(instance, cardId, star)), started = true))

    /** 推进到条件满足或达到上限；返回终止时的状态。 */
    private fun advance(state: DuelState, ticks: Int, seen: (DuelState) -> Boolean): DuelState {
        var s = state
        for (t in 0 until ticks) {
            s = DuelEngine.step(s)
            if (seen(s)) return s
            if (s.player.hp <= 0 || s.enemy.hp <= 0) break
        }
        return s
    }

    // ---------------- 减速（FS1 / D8 / D12） ----------------

    @Test fun frostSlowAppliesOnAttackLand() {
        val player = withCard("juggernaut", "FS1").copy(attackIn = 0.0)
        val state = advance(DuelState(player, base("abaddon"), 1729), 30) {
            it.enemy.cardState.buffs.any { b -> b.kind == CardBuffKind.SLOW }
        }
        val slow = state.enemy.cardState.buffs.firstOrNull { it.kind == CardBuffKind.SLOW }
        assertNotNull("FS1 普攻命中应施加减速", slow)
        assertEquals("1 星减速应为 15%", 15.0, slow!!.amount, .00001)
    }

    @Test fun frostSlowStarScalingDoublesAmount() {
        val player = withCard("juggernaut", "FS1", star = 2).copy(attackIn = 0.0)
        val state = advance(DuelState(player, base("abaddon"), 1729), 30) {
            it.enemy.cardState.buffs.any { b -> b.kind == CardBuffKind.SLOW }
        }
        assertEquals("2 星减速应翻倍为 30%", 30.0,
            state.enemy.cardState.buffs.first { it.kind == CardBuffKind.SLOW }.amount, .00001)
    }

    @Test fun frostSlowExtendsAttackInterval() {
        val enemy = base("abaddon").copy(attackIn = 0.0)
        val player = base("viper")
        val plain = DuelEngine.step(DuelState(player, enemy, 1729)).enemy.attackIn
        val slowed = DuelEngine.step(DuelState(player, enemy.copy(cardState = enemy.cardState.copy(
            buffs = listOf(CardBuff("x", CardBuffKind.SLOW, 15.0, Int.MAX_VALUE)))), 1729)).enemy.attackIn
        assertTrue("减速 15% 应延长攻击间隔", slowed > plain * 1.1 && slowed < plain * 1.3)
    }

    @Test fun frostSlowRefreshDoesNotStackSameSource() {
        val player = withCard("juggernaut", "FS1").copy(attackIn = 0.0)
        val state = advance(DuelState(player, base("abaddon"), 1729), 200) { s ->
            s.enemy.cardState.buffs.count { it.kind == CardBuffKind.SLOW } >= 2
        }
        assertTrue("同一来源重复命中不得叠层", state.enemy.cardState.buffs.count { it.kind == CardBuffKind.SLOW } <= 1)
    }

    // ---------------- 冻结（FS3） ----------------

    @Test fun frostFreezeAppliesEveryIntervalAndIsUndispellable() {
        val player = withCard("viper", "FS3")
        val state = advance(DuelState(player, base("abaddon"), 1729), 60) {
            it.enemy.timedControls.any { c -> c.kind == ControlKind.FREEZE }
        }
        val freeze = state.enemy.timedControls.firstOrNull { it.kind == ControlKind.FREEZE }
        assertNotNull("FS3 应按 5 秒周期施加冻结", freeze)
        assertEquals("冻结不可被驱散", DispelClass.UNDISPELLABLE, freeze!!.dispelClass)
    }

    @Test fun frozenTargetCannotAttack() {
        val enemy = base("juggernaut").copy(attackIn = 0.0,
            timedControls = listOf(TimedControl("frost:probe", 0, ControlKind.FREEZE, 3000L, DispelClass.UNDISPELLABLE, 0)))
        val player = base("abaddon")
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertEquals("被冻结的目标不应普攻", player.maxHp, next.player.hp, .00001)
    }

    // ---------------- 冰霜 DOT 与易伤（FS4 / D13） ----------------

    @Test fun frostDotAndVulnerabilityApplyOnAttackLand() {
        val player = withCard("phantom_assassin", "FS4").copy(attackIn = 0.0)
        val state = advance(DuelState(player, base("abaddon"), 1729), 30) { s ->
            s.enemy.poisonPool?.layers?.any { it.sourceId.contains("FS4.frostDot") } == true &&
                s.enemy.cardState.buffs.any { it.kind == CardBuffKind.VULNERABILITY }
        }
        assertNotNull("FS4 应附加冰霜 DOT",
            state.enemy.poisonPool?.layers?.firstOrNull { it.sourceId.contains("FS4.frostDot") })
        val vuln = state.enemy.cardState.buffs.firstOrNull { it.kind == CardBuffKind.VULNERABILITY }
        assertNotNull("FS4 应施加冰霜易伤", vuln)
        assertEquals("易伤应为 15%", 15.0, vuln!!.amount, .00001)
    }

    @Test fun frostDotTicksDamageWithoutFurtherAttacks() {
        val player = withCard("phantom_assassin", "FS4").copy(attackIn = 0.0)
        var state = advance(DuelState(player, base("abaddon"), 1729), 30) {
            it.enemy.poisonPool?.layers?.any { l -> l.sourceId.contains("FS4.frostDot") } == true
        }
        val afterHit = state.enemy.hp
        state = advance(state, 15) { it.enemy.hp < afterHit - 1.0 }
        assertTrue("冰霜 DOT 应持续跳伤", state.enemy.hp < afterHit - 1.0)
    }

    @Test fun vulnerabilityAmplifiesIncomingDamage() {
        val enemy = base("abaddon")
        val player = base("juggernaut").copy(attackIn = 0.0)
        val plain = DuelEngine.step(DuelState(player, enemy, 1729)).enemy.hp
        val vulned = DuelEngine.step(DuelState(player, enemy.copy(cardState = enemy.cardState.copy(
            buffs = listOf(CardBuff("v", CardBuffKind.VULNERABILITY, 15.0, Int.MAX_VALUE)))), 1729)).enemy.hp
        assertTrue("易伤应放大受到伤害", vulned < plain - 1.0)
    }

    // ---------------- BKB 免疫交互 ----------------

    @Test fun bkbBlocksFrostSlowAndFreeze() {
        val player = withCard("juggernaut", "FS1")
        val enemy = base("abaddon").copy(equipment = EquipmentBattleState(immunityMs = 100000L))
        val state = advance(DuelState(player, enemy, 1729), 30) { false }
        assertFalse("BKB 应阻止减速", state.enemy.cardState.buffs.any { it.kind == CardBuffKind.SLOW })
        val freezePlayer = withCard("viper", "FS3")
        val freezeState = advance(DuelState(freezePlayer, base("abaddon").copy(
            equipment = EquipmentBattleState(immunityMs = 100000L)), 1729), 60) { false }
        assertFalse("BKB 应阻止冻结", freezeState.enemy.timedControls.any { it.kind == ControlKind.FREEZE })
    }

    // ---------------- 时停（TIME_STOP）语义补全 ----------------

    @Test fun timeStopPausesSkillAndEquipmentCooldown() {
        val enemy = base("juggernaut").copy(attackIn = 3.0, ultIn = 5.0,
            timedControls = listOf(TimedControl("hero:faceless_void:ult", 0, ControlKind.TIME_STOP, 5000L, DispelClass.UNDISPELLABLE, 0)),
            equipment = EquipmentBattleState(cooldowns = EquipmentCooldownState(remainingByGroup = mapOf("bkb" to 3000L))))
        val player = base("abaddon")
        val next = DuelEngine.step(DuelState(player, enemy, 1729)).enemy
        assertEquals("时停应暂停大招 CD", 5.0, next.ultIn, .00001)
        assertEquals("时停应暂停普攻 CD", 3.0, next.attackIn, .00001)
        assertEquals("时停应暂停装备 CD", 3000L, next.equipment.cooldowns.remainingByGroup["bkb"])
    }

    @Test fun frozenAndTimeStoppedBlockEveryAction() {
        listOf(ActionControlState(frozen = true), ActionControlState(timeStopped = true)).forEach { state ->
            listOf(CombatAction.BASIC_ATTACK, CombatAction.HERO_SKILL, CombatAction.ACTIVE_ITEM, CombatAction.CONSUMABLE)
                .forEach { action -> assertFalse("$state 应禁止 $action", ActionPermission.check(state, action).allowed) }
        }
        assertTrue("冻结即使允许眩晕施法也应拦截",
            ActionPermission.check(ActionControlState(frozen = true), CombatAction.HERO_SKILL, allowStunnedHeroSkill = true)
                .blockedBy.contains(ActionBlock.FROZEN))
        assertTrue("TAUNT 单挑语义下不禁止动作",
            ActionPermission.check(ActionControlState(taunted = true), CombatAction.BASIC_ATTACK).allowed)
    }

    // ---------------- 存档校验：hero 与卡牌来源控制放行 ----------------

    /** 合法最小宝箱（validBox 要求 1..36 件物品且定义可见）。 */
    private val defs = mapOf(
        "blade" to com.example.douqiuqiu.inventory.ItemDefinition("blade", "刃", com.example.douqiuqiu.inventory.ItemKind.EQUIPMENT, 1, 1))
    private fun chest() = TargetChest("c", "白", 0, 0, listOf(ChestItem("blade", 0, 0, 2)))

    @Test fun heroAndCardControlsPassSaveValidation() {
        // 斧王持寒霜卡：敌方同时带有英雄技能嘲讽与卡牌冻结，两类来源都应通过存档校验。
        val player = withCard("axe", "FS3", instance = "c1")
        val enemy = base("abaddon").copy(
            timedControls = listOf(
                TimedControl("c1:FS3.freeze", 0, ControlKind.FREEZE, 600L, DispelClass.UNDISPELLABLE, 3),
                TimedControl("hero:axe:ult", 0, ControlKind.TAUNT, 2500L, DispelClass.BASIC, 3)))
        val fight = DuelState(player, enemy, 7, 3, null, listOf("x"))
        val run = JourneyState("run-frost", "axe", chest(), 1729, 100.0, 100.0, 1, JourneyStage.BATTLE, fight = fight)
        val progress = CampaignProgress(run = run)
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> CampaignBinaryCodec.write(out, progress) } }.toByteArray()
        val read = ByteArrayInputStream(bytes).use { DataInputStream(it).use { CampaignBinaryCodec.read(it, 14) } }
        assertNull("英雄/卡牌来源的控制应通过存档校验", JourneyValidation.check(read, defs))
        val kinds = read.run!!.fight!!.enemy.timedControls.associate { it.kind to it.dispelClass }
        assertEquals(DispelClass.UNDISPELLABLE, kinds[ControlKind.FREEZE])
        assertEquals(DispelClass.BASIC, kinds[ControlKind.TAUNT])
    }

    @Test fun forgedControlStillRejected() {
        val enemy = base("abaddon").copy(
            timedControls = listOf(TimedControl("hero:axe:ult", 0, ControlKind.TAUNT, 2500L, DispelClass.BASIC, 3)))
        val fight = DuelState(base("viper"), enemy, 7, 3, null, listOf("x"))
        val run = JourneyState("run-forged", "viper", chest(), 1729, 100.0, 100.0, 1, JourneyStage.BATTLE, fight = fight)
        assertNotNull("非斧王不应能施加斧王嘲讽", JourneyValidation.check(CampaignProgress(run = run), defs))
    }

    @Test fun heroControlConstantsComeFromContentConfig() {
        // 存档校验表与引擎施放的 sourceId 必须一一对应。
        assertEquals(5, HeroContent.controlSources.size)
        listOf("hero:anti_mage:ult", "hero:faceless_void:timelock", "hero:axe:ult",
            "hero:legion_commander:ult", "hero:faceless_void:ult").forEach {
            assertTrue("缺少控制来源 $it", HeroContent.controlSources.containsKey(it))
        }
        assertTrue(FirstRunContent.heroes.any { it.id == "faceless_void" })
    }
}
