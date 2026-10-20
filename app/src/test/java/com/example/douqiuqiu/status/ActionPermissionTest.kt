package com.example.douqiuqiu.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPermissionTest {

    private val clear = ActionControlState()

    private fun state(
        dead: Boolean = false, banished: Boolean = false, stunned: Boolean = false,
        hexed: Boolean = false, silenced: Boolean = false, muted: Boolean = false,
        disarmed: Boolean = false
    ) = ActionControlState(dead, banished, stunned, hexed, silenced, muted, disarmed)

    @Test
    fun `全空状态允许四类动作`() {
        for (a in CombatAction.values()) {
            val r = ActionPermission.check(clear, a)
            assertTrue("$a 应被允许", r.allowed)
            assertTrue(r.blockedBy.isEmpty())
        }
    }

    @Test
    fun `逐个状态乘四类动作矩阵`() {
        val flags = listOf("dead", "banished", "stunned", "hexed", "silenced", "muted", "disarmed")
        for (flag in flags) {
            val s = when (flag) {
                "dead" -> state(dead = true)
                "banished" -> state(banished = true)
                "stunned" -> state(stunned = true)
                "hexed" -> state(hexed = true)
                "silenced" -> state(silenced = true)
                "muted" -> state(muted = true)
                else -> state(disarmed = true)
            }
            for (a in CombatAction.values()) {
                val r = ActionPermission.check(s, a)
                // 死亡/放逐禁止一切；其余按规则表断言 allowed 与 blockedBy
                when (flag) {
                    "dead" -> {
                        assertTrue(r.blockedBy.contains(ActionBlock.DEAD))
                        assertTrue("$a", !r.allowed)
                    }
                    "banished" -> {
                        assertTrue(r.blockedBy.contains(ActionBlock.BANISHED))
                        assertTrue("$a", !r.allowed)
                    }
                    "stunned" -> assertTrue("$a", !r.allowed && r.blockedBy == setOf(ActionBlock.STUNNED))
                    "hexed" -> assertTrue("$a", !r.allowed && r.blockedBy == setOf(ActionBlock.HEXED))
                    "silenced" -> {
                        // 沉默禁大招，其余三类不禁
                        val expected = a != CombatAction.HERO_SKILL
                        assertEquals("$a", expected, r.allowed)
                        if (expected) assertTrue(r.blockedBy.isEmpty())
                        else assertEquals(setOf(ActionBlock.SILENCED), r.blockedBy)
                    }
                    "muted" -> {
                        val expected = a == CombatAction.HERO_SKILL || a == CombatAction.BASIC_ATTACK
                        assertEquals("$a", expected, r.allowed)
                        if (expected) assertTrue(r.blockedBy.isEmpty())
                        else assertEquals(setOf(ActionBlock.MUTED), r.blockedBy)
                    }
                    "disarmed" -> {
                        val expected = a != CombatAction.BASIC_ATTACK
                        assertEquals("$a", expected, r.allowed)
                        if (expected) assertTrue(r.blockedBy.isEmpty())
                        else assertEquals(setOf(ActionBlock.DISARMED), r.blockedBy)
                    }
                }
            }
        }
    }

    @Test
    fun `眩晕护盾特例允许英雄技能`() {
        val r = ActionPermission.check(state(stunned = true), CombatAction.HERO_SKILL, allowStunnedHeroSkill = true)
        assertTrue(r.allowed)
        assertTrue(r.blockedBy.isEmpty())
    }

    @Test
    fun `特例不能豁免沉默与妖术`() {
        val silenced = ActionPermission.check(
            state(stunned = true, silenced = true), CombatAction.HERO_SKILL, allowStunnedHeroSkill = true
        )
        assertTrue(!silenced.allowed)
        assertEquals(setOf(ActionBlock.SILENCED), silenced.blockedBy)

        val hexed = ActionPermission.check(
            state(stunned = true, hexed = true), CombatAction.HERO_SKILL, allowStunnedHeroSkill = true
        )
        assertTrue(!hexed.allowed)
        assertEquals(setOf(ActionBlock.HEXED), hexed.blockedBy)
    }

    @Test
    fun `特例不影响物品与普攻`() {
        for (a in listOf(CombatAction.ACTIVE_ITEM, CombatAction.CONSUMABLE, CombatAction.BASIC_ATTACK)) {
            val r = ActionPermission.check(state(stunned = true), a, allowStunnedHeroSkill = true)
            assertTrue("$a", !r.allowed)
            assertEquals(setOf(ActionBlock.STUNNED), r.blockedBy)
        }
    }

    @Test
    fun `沉默不禁物品与药品`() {
        assertTrue(ActionPermission.check(state(silenced = true), CombatAction.ACTIVE_ITEM).allowed)
        assertTrue(ActionPermission.check(state(silenced = true), CombatAction.CONSUMABLE).allowed)
        // 但禁大招
        assertTrue(!ActionPermission.check(state(silenced = true), CombatAction.HERO_SKILL).allowed)
    }

    @Test
    fun `缴械可用主动物品但不能普攻`() {
        assertTrue(ActionPermission.check(state(disarmed = true), CombatAction.ACTIVE_ITEM).allowed)
        val r = ActionPermission.check(state(disarmed = true), CombatAction.BASIC_ATTACK)
        assertTrue(!r.allowed)
        assertEquals(setOf(ActionBlock.DISARMED), r.blockedBy)
    }

    @Test
    fun `禁用物品不禁大招与普攻`() {
        assertTrue(ActionPermission.check(state(muted = true), CombatAction.HERO_SKILL).allowed)
        assertTrue(ActionPermission.check(state(muted = true), CombatAction.BASIC_ATTACK).allowed)
        // 但禁物品
        assertTrue(!ActionPermission.check(state(muted = true), CombatAction.ACTIVE_ITEM).allowed)
        assertTrue(!ActionPermission.check(state(muted = true), CombatAction.CONSUMABLE).allowed)
    }

    @Test
    fun `死亡与放逐全禁止且保留其他原因`() {
        for (a in CombatAction.values()) {
            val dead = ActionPermission.check(state(dead = true, silenced = true), a)
            assertTrue("$a", !dead.allowed)
            // DEAD 之外不清空其他有效原因
            if (a == CombatAction.HERO_SKILL) {
                assertEquals(listOf(ActionBlock.DEAD, ActionBlock.SILENCED), dead.blockedBy.toList())
            } else {
                assertEquals(listOf(ActionBlock.DEAD), dead.blockedBy.toList())
            }
            val banished = ActionPermission.check(state(banished = true, stunned = true), a)
            assertTrue("$a", !banished.allowed)
            assertTrue(banished.blockedBy.contains(ActionBlock.BANISHED))
        }
    }

    @Test
    fun `多个原因完整保留且按枚举声明顺序`() {
        val s = state(dead = true, banished = true, stunned = true, hexed = true, silenced = true, muted = true, disarmed = true)
        val skill = ActionPermission.check(s, CombatAction.HERO_SKILL)
        assertEquals(
            listOf(ActionBlock.DEAD, ActionBlock.BANISHED, ActionBlock.STUNNED, ActionBlock.HEXED, ActionBlock.SILENCED),
            skill.blockedBy.toList()
        )
        val item = ActionPermission.check(s, CombatAction.ACTIVE_ITEM)
        assertEquals(
            listOf(ActionBlock.DEAD, ActionBlock.BANISHED, ActionBlock.STUNNED, ActionBlock.HEXED, ActionBlock.MUTED),
            item.blockedBy.toList()
        )
        val attack = ActionPermission.check(s, CombatAction.BASIC_ATTACK)
        assertEquals(
            listOf(ActionBlock.DEAD, ActionBlock.BANISHED, ActionBlock.STUNNED, ActionBlock.HEXED, ActionBlock.DISARMED),
            attack.blockedBy.toList()
        )
    }

    @Test
    fun `结果无副作用且集合独立`() {
        val s = state(stunned = true)
        val r1 = ActionPermission.check(s, CombatAction.HERO_SKILL)
        val r2 = ActionPermission.check(s, CombatAction.HERO_SKILL)
        assertEquals(r1, r2)
        // 输入状态对象未被修改
        assertEquals(ActionControlState(false, false, true, false, false, false, false), s)
        // 返回集合可安全修改而不影响下次结果
        (r1.blockedBy as MutableSet<ActionBlock>).add(ActionBlock.MUTED)
        assertEquals(setOf(ActionBlock.STUNNED), r2.blockedBy)
    }
}
