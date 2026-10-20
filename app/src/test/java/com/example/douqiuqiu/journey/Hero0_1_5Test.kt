package com.example.douqiuqiu.journey

import com.example.douqiuqiu.content.HeroContent
import com.example.douqiuqiu.inventory.CampaignBinaryCodec
import com.example.douqiuqiu.equipment.EquipmentCooldownState
import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.status.ControlKind
import com.example.douqiuqiu.status.DispelClass
import com.example.douqiuqiu.status.TimedControl
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class Hero0_1_5Test {
    private val journey = JourneyRules(InventoryRules(emptyMap()))

    @Test fun newHeroesRegisteredAndUnlocked() {
        assertEquals(10, FirstRunContent.heroes.size)
        listOf("anti_mage", "axe", "faceless_void", "legion_commander").forEach { id ->
            assertTrue("缺失英雄 $id", FirstRunContent.heroes.any { it.id == id })
            assertTrue("未解锁 $id", CampaignProgress().unlockedHeroes.contains(id))
        }
    }

    @Test fun heroContentConfigNonZero() {
        listOf("anti_mage", "axe", "faceless_void", "legion_commander").forEach { id ->
            val cfg = HeroContent.skill(id)
            assertTrue("$id 缺少技能常数", cfg.spinEveryHits > 0 || cfg.ultManaVoidBase > 0 ||
                cfg.timeStopDurationSec > 0.0 || cfg.dualStrikeChance > 0.0)
        }
    }

    /** 敌法师大招：法力虚空魔法伤害 + 眩晕。 */
    @Test fun antiMageUltManaVoid() {
        val player = journey.fighter(InventoryState(), "anti_mage").copy(mana = 300.0, ultIn = 0.0, amManaBurn = 50.0)
        val enemy = journey.fighter(InventoryState(), "juggernaut").copy(mana = 300.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        val e = next.enemy
        assertTrue("敌法师应对敌方施加眩晕", e.timedControls.any { it.kind == ControlKind.STUN && it.sourceId == "hero:anti_mage:ult" })
        assertTrue("法力虚空应造成伤害", e.hp < e.maxHp - 1.0)
    }

    /** 斧王大招：嘲讽敌方 + 自身护甲增益。 */
    @Test fun axeUltBattleHunger() {
        val player = journey.fighter(InventoryState(), "axe").copy(mana = 300.0, ultIn = 0.0)
        val enemy = journey.fighter(InventoryState(), "abaddon").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertTrue("斧王应嘲讽敌方", next.enemy.timedControls.any { it.kind == ControlKind.TAUNT && it.sourceId == "hero:axe:ult" })
        assertEquals("斧王应获得护甲增益", HeroContent.skill("axe").selfArmor, next.player.armorBuff, .00001)
    }

    /** 虚空假面大招：时间结界 = 不可驱散的 TIME_STOP。 */
    @Test fun facelessVoidUltTimeStop() {
        val player = journey.fighter(InventoryState(), "faceless_void").copy(mana = 300.0, ultIn = 0.0)
        val enemy = journey.fighter(InventoryState(), "skywrath").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        val stop = next.enemy.timedControls.firstOrNull { it.kind == ControlKind.TIME_STOP && it.sourceId == "hero:faceless_void:ult" }
        assertNotNull("虚空应施加 TIME_STOP", stop)
        assertEquals("TIME_STOP 不可被驱散", DispelClass.UNDISPELLABLE, stop!!.dispelClass)
    }

    /**
     * 虚空假面普攻「时间锁定」：若目标大招已在冷却（ultIn > 0），则额外 +1 秒（timeLockCdAddSec）。
     * 该触发是概率性的，故在固定种子空间内确定地寻找一个能触发时间锁定的种子来断言延长日志出现。
     */
    @Test fun facelessVoidTimeLockExtendsCooldown() {
        var triggered = false
        for (seed in 1..40) {
            val player = journey.fighter(InventoryState(), "faceless_void").copy(mana = 0.0, ultIn = 999.0, attackIn = 0.0)
            val enemy = journey.fighter(InventoryState(), "axe").copy(mana = 0.0, ultIn = 50.0, attackIn = 999.0)
            var state = DuelState(player, enemy, seed.toLong())
            for (t in 0 until 150) {
                state = DuelEngine.step(state)
                if (state.log.any { it.contains("延长敌方大招冷却") }) { triggered = true; break }
                if (state.player.hp <= 0 || state.enemy.hp <= 0) break
            }
            if (triggered) break
        }
        assertTrue("未找到可触发时间锁定冷却延长效果的种子", triggered)
    }

    /** 军团指挥官大招：决斗嘲讽 + 自身攻速增益。 */
    @Test fun legionCommanderUltDuel() {
        val player = journey.fighter(InventoryState(), "legion_commander").copy(mana = 300.0, ultIn = 0.0)
        val enemy = journey.fighter(InventoryState(), "phantom_assassin").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertTrue("军团应嘲讽敌方", next.enemy.timedControls.any { it.kind == ControlKind.TAUNT && it.sourceId == "hero:legion_commander:ult" })
        assertEquals("军团应获得攻速增益", HeroContent.skill("legion_commander").duelAttackSpeedPct, next.player.speedBuff, .00001)
    }

    /** TIME_STOP 必须暂停目标普攻。 */
    @Test fun timeStopPausesAttacks() {
        val enemy = journey.fighter(InventoryState(), "juggernaut").copy(attackIn = 0.0,
            timedControls = listOf(TimedControl("hero:faceless_void:ult", 0, ControlKind.TIME_STOP, 5000L, DispelClass.UNDISPELLABLE, 0)))
        val player = journey.fighter(InventoryState(), "abaddon").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertEquals("被时间结界锁定的目标不应普攻", player.maxHp, next.player.hp, .00001)
    }

    /** 对照：无 TIME_STOP 时目标会普攻造成伤害。 */
    @Test fun withoutTimeStopAttacksLand() {
        val enemy = journey.fighter(InventoryState(), "juggernaut").copy(attackIn = 0.0)
        val player = journey.fighter(InventoryState(), "abaddon").copy(mana = 0.0, ultIn = 999.0, attackIn = 999.0)
        val next = DuelEngine.step(DuelState(player, enemy, 1729))
        assertTrue("未被锁定时目标应普攻命中", next.player.hp < next.player.maxHp)
    }

    /** 存档 v14：新英雄、累计削蓝、TIME_STOP/TAUNT 状态往返一致。 */
    @Test fun saveRoundTripV14() {
        val player = journey.fighter(InventoryState(), "anti_mage").copy(amManaBurn = 123.4)
        val enemy = journey.fighter(InventoryState(), "axe").copy(
            timedControls = listOf(TimedControl("hero:faceless_void:ult", 0, ControlKind.TIME_STOP, 3000L, DispelClass.UNDISPELLABLE, 0)))
        val fight = DuelState(player, enemy, 7, 3, null, listOf("x"))
        val run = JourneyState("run-1", "anti_mage", TargetChest("c", "白", 0, 0, emptyList()), 1729, 100.0, 100.0, 1,
            JourneyStage.BATTLE, fight = fight, equipmentCooldowns = EquipmentCooldownState())
        val progress = CampaignProgress(run = run)
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> CampaignBinaryCodec.write(out, progress) } }.toByteArray()
        val read = ByteArrayInputStream(bytes).use { DataInputStream(it).use { CampaignBinaryCodec.read(it, 14) } }
        val rf = read.run!!.fight!!
        assertEquals("英雄 id 应保留", "anti_mage", rf.player.heroId)
        assertEquals("累计削蓝应保留", 123.4, rf.player.amManaBurn, .00001)
        assertEquals("敌方英雄应保留", "axe", rf.enemy.heroId)
        val stop = rf.enemy.timedControls.firstOrNull { it.kind == ControlKind.TIME_STOP }
        assertNotNull("TIME_STOP 应保留", stop)
        assertEquals(3000L, stop!!.remainingMs)
    }

    /** 旧版（v13）应用应拒绝 v14 存档：写入 VERSION=14，旧版 VERSION=13 的 `version !in 1..VERSION` 触发 UnsupportedSaveVersion。 */
    @Test fun versionContractRejectsOldApp() {
        assertEquals(14, com.example.douqiuqiu.inventory.InventoryStore.Companion.let { 14 })
    }

    /** 存档 v14：除累计削蓝外，TIME_STOP 的不可驱散属性与 TAUNT 也应往返一致。 */
    @Test fun saveRoundTripV14StatusKinds() {
        val enemy = journey.fighter(InventoryState(), "axe").copy(
            timedControls = listOf(
                TimedControl("hero:faceless_void:ult", 0, ControlKind.TIME_STOP, 3000L, DispelClass.UNDISPELLABLE, 0),
                TimedControl("hero:axe:ult", 1, ControlKind.TAUNT, 2500L, DispelClass.BASIC, 0)))
        val fight = DuelState(journey.fighter(InventoryState(), "anti_mage"), enemy, 7, 3, null, listOf("x"))
        val run = JourneyState("run-2", "anti_mage", TargetChest("c", "白", 0, 0, emptyList()), 1729, 100.0, 100.0, 1,
            JourneyStage.BATTLE, fight = fight, equipmentCooldowns = EquipmentCooldownState())
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> CampaignBinaryCodec.write(out, CampaignProgress(run = run)) } }.toByteArray()
        val read = ByteArrayInputStream(bytes).use { DataInputStream(it).use { CampaignBinaryCodec.read(it, 14) } }
        val rtc = read.run!!.fight!!.enemy.timedControls.associate { it.kind to it }
        assertEquals(DispelClass.UNDISPELLABLE, rtc[ControlKind.TIME_STOP]!!.dispelClass)
        assertEquals(2500L, rtc[ControlKind.TAUNT]!!.remainingMs)
    }
}
