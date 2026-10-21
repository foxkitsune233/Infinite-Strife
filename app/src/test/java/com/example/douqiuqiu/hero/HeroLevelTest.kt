package com.example.douqiuqiu.hero

import com.example.douqiuqiu.inventory.InventoryRules
import com.example.douqiuqiu.inventory.InventoryState
import com.example.douqiuqiu.journey.CampaignProgress
import org.junit.Assert.*
import org.junit.Test

/**
 * 0.1.6 英雄等级（金币升级制）验收。
 * 数值期望按设计稿人工写入，不用被测函数自身生成预期。
 */
class HeroLevelTest {

    private val inventory = InventoryRules(emptyMap())

    @Test fun upgradeCostIsLinearFromLevel() {
        // 100 × 当前等级：Lv1→2 = 100，Lv9→10 = 900，到 10 级共 4500。
        assertEquals(100, HeroLevelRules.upgradeCost(1))
        assertEquals(900, HeroLevelRules.upgradeCost(9))
        assertEquals(4500, (1..9).sumOf { HeroLevelRules.upgradeCost(it) })
    }

    @Test fun missingHeroDefaultsToLevelOne() {
        val s = InventoryState()
        assertEquals(1, HeroLevelRules.level(s, "juggernaut"))
    }

    @Test fun upgradeSpendsExactGoldAndRaisesLevel() {
        val s = InventoryState(gold = 1000)
        val r = HeroLevelRules.upgrade(s, "juggernaut", inventory)
        assertTrue(r.error ?: "ok", r.ok)
        assertEquals(900, r.state.gold)
        assertEquals(2, HeroLevelRules.level(r.state, "juggernaut"))
    }

    @Test fun insufficientGoldIsRejectedWithoutChange() {
        val s = InventoryState(gold = 99)
        val r = HeroLevelRules.upgrade(s, "juggernaut", inventory)
        assertFalse(r.ok)
        assertEquals(s, r.state)
        assertEquals(99, s.gold)
    }

    @Test fun levelCapTenBlocksFurtherUpgrade() {
        var s = InventoryState(gold = 100000)
        repeat(9) { s = HeroLevelRules.upgrade(s, "juggernaut", inventory).state }
        assertEquals(10, HeroLevelRules.level(s, "juggernaut"))
        val r = HeroLevelRules.upgrade(s, "juggernaut", inventory)
        assertFalse(r.ok)
        assertTrue(r.error!!, r.error!!.contains("10"))
        assertEquals(10, HeroLevelRules.level(s, "juggernaut"))
    }

    @Test fun levelOneHasNoBonus() {
        assertEquals(1.0, HeroLevelRules.attackMultiplier(1), 1e-9)
        assertEquals(1.0, HeroLevelRules.healthMultiplier(1), 1e-9)
        assertEquals(0.0, HeroLevelRules.armorBonus(1), 1e-9)
        assertEquals(0.0, HeroLevelRules.magicResistBonus(1), 1e-9)
    }

    @Test fun levelTenTotalsMatchDesign() {
        // level-1 语义（Lv1 无增益）：攻 1+0.135+0.02=1.155；血 1+0.18+0.02=1.20；
        // 甲 0.4×9=3.6；魔抗 0.5×9+2=6.5。
        assertEquals(1.155, HeroLevelRules.attackMultiplier(10), 1e-9)
        assertEquals(1.20, HeroLevelRules.healthMultiplier(10), 1e-9)
        assertEquals(3.6, HeroLevelRules.armorBonus(10), 1e-9)
        assertEquals(6.5, HeroLevelRules.magicResistBonus(10), 1e-9)
    }

    @Test fun milestoneKicksInAtFive() {
        // Lv5：攻 1+0.06+0.01 = 1.07；Lv4 无里程碑 = 1.045。
        assertEquals(1.07, HeroLevelRules.attackMultiplier(5), 1e-9)
        assertEquals(1.045, HeroLevelRules.attackMultiplier(4), 1e-9)
    }

    @Test fun differentHeroesLevelIndependently() {
        var s = InventoryState(gold = 1000)
        s = HeroLevelRules.upgrade(s, "juggernaut", inventory).state
        assertEquals(2, HeroLevelRules.level(s, "juggernaut"))
        assertEquals(1, HeroLevelRules.level(s, "abaddon"))
    }

    @Test fun blankHeroIsRejected() {
        val r = HeroLevelRules.upgrade(InventoryState(gold = 1000), "  ", inventory)
        assertFalse(r.ok)
    }

    @Test fun progressSurvivesSerializationDefaults() {
        // 旧档无 heroLevels 字段 → CampaignProgress 默认空 map → 全 1 级。
        val p = CampaignProgress()
        assertTrue(p.heroLevels.isEmpty())
        assertEquals(1, HeroLevelRules.level(InventoryState(progress = p), "viper"))
    }
}
