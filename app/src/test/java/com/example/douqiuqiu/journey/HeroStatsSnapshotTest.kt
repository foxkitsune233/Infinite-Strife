package com.example.douqiuqiu.journey

import com.example.douqiuqiu.inventory.*
import org.junit.Assert.*
import org.junit.Test

/**
 * X02：英雄详细数值快照验收，对应 docs/tasks/0.1.4-external-handoff.md。
 * 只验证只读计算模型；不依赖 Android 或墙上时钟。
 */
class HeroStatsSnapshotTest {

    private val inventory = InventoryRules(
        listOf(
            ItemDefinition("blade", "刀", ItemKind.EQUIPMENT, 1, 1, price = 100,
                stats = EquipmentStats(attack = 17.0, armor = 3.0)),
            ItemDefinition("A1", "迅击训练", ItemKind.CARD, 1, 1, price = 80)
        ).associateBy { it.id }
    )
    private val journey = JourneyRules(inventory)

    /** 一把装在装备栏第 0 位的刀：+17 攻击、+3 护甲。 */
    private val gearItem = StoredItem("e1", "blade", ItemPlace(Container.EQUIPMENT, 0))

    /** 一张装在技能栏第 0 位的卡：提供攻速加成。 */
    private val cardItem = StoredItem("c1", "A1", ItemPlace(Container.SKILLS, 0))

    private fun of(items: List<StoredItem>) = InventoryState(items = items)

    private fun snap(s: InventoryState, battle: Fighter? = null) =
        HeroStats.snapshot(s, inventory, "juggernaut", battle)

    @Test fun `裸体英雄的 base 等于总值且其它来源为零`() {
        val s = snap(of(emptyList()))
        assertEquals(s.hp.total, s.hp.base!!, 1e-9)
        assertEquals(0.0, s.hp.equipment!!, 1e-9)
        assertEquals(0.0, s.hp.cards!!, 1e-9)
        assertFalse(s.inBattle)
        assertTrue(s.hp.hasBreakdown)
    }

    @Test fun `装上装备后 equipment 反映该装备数值`() {
        val bare = snap(of(emptyList()))
        val with = snap(of(listOf(gearItem)))
        assertEquals("刀应提供 17 攻击", 17.0, with.attack.equipment!! - bare.attack.equipment!!, 1e-9)
        assertEquals("刀应提供 3 护甲", 3.0, with.armor.equipment!! - bare.armor.equipment!!, 1e-9)
        assertTrue("攻击总值应提高", with.attack.total > bare.attack.total)
        assertTrue("护甲总值应提高", with.armor.total > bare.armor.total)
    }

    @Test fun `放进技能卡后 cards 反映卡牌与羁绊贡献`() {
        val bare = snap(of(emptyList()))
        val with = snap(of(listOf(cardItem)))
        assertTrue("卡牌攻速贡献应大于 0", with.attackSpeed.cards!! > 0.0)
        assertTrue("攻速总值应提高", with.attackSpeed.total > bare.attackSpeed.total)
        assertEquals("护甲的卡牌贡献应为 0", 0.0, with.armor.cards!!, 1e-9)
    }

    @Test fun `战斗快照只给实时总值且不伪造来源`() {
        val live = journey.fighter(InventoryState(), "juggernaut")
        val s = snap(InventoryState(), live)
        assertTrue(s.inBattle)
        assertEquals(live.maxHp, s.hp.total, 1e-9)
        assertNull("战斗中无法做消融对照，来源必须为 null", s.hp.base)
        assertNull(s.hp.equipment)
        assertNull(s.hp.cards)
        assertFalse("无来源拆分时 hasBreakdown 应为 false", s.hp.hasBreakdown)
    }

    @Test fun `所有数值有限且不为 NaN`() {
        val s = snap(of(listOf(gearItem, cardItem)))
        val all = listOf(s.hp, s.mana, s.attack, s.armor, s.magicResistance, s.attackSpeed,
            s.healthRegen, s.manaRegen, s.lifesteal, s.cooldownReduction, s.statusResistance)
        all.forEach { v ->
            assertTrue("总值必须有限：" + v.total, v.total.isFinite())
            val parts = listOf(v.base, v.equipment, v.cards, v.synergy)
            parts.forEach { part -> if (part != null) assertTrue("来源必须有限：$part", part.isFinite()) }
        }
        assertTrue(all.all { it.hasBreakdown })
    }

    @Test fun `战斗实时读数不改写局外配装结果`() {
        val base = of(listOf(gearItem, cardItem))
        val outside = snap(base)
        val damaged = journey.fighter(base, "juggernaut").copy(hp = 1.0)
        val live = snap(base, damaged)
        assertTrue(live.inBattle)
        assertEquals("局外最大值不受战斗实时值影响", outside.hp.total, live.hp.total, 1e-9)
        assertEquals("重复计算局外快照结果一致", outside.attack.total, snap(base).attack.total, 1e-9)
    }
}
