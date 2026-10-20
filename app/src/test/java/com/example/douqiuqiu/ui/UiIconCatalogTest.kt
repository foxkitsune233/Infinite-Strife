package com.example.douqiuqiu.ui

import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.journey.Fighter
import com.example.douqiuqiu.status.ActionControlState
import com.example.douqiuqiu.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class UiIconCatalogTest {
    @Test fun everyEquipmentDefinitionHasExplicitIcon() {
        EquipmentCatalog.all.forEach { assertNotNull("Missing ${it.id}", ItemIconCatalog.explicit(it.id)) }
    }
    @Test fun starterGearConsumablesAndMaterialsHaveExplicitIcons() {
        val ids = "iron_branch gauntlets slippers mantle circlet broadsword bracer wraith_band null_talisman recipe_bracer recipe_wraith_band recipe_null_talisman salve tango faerie_fire cheese essence backpack_fabric skill_shard safe_lock safe_frame"
        ids.split(' ').forEach { assertNotNull("Missing $it", ItemIconCatalog.explicit(it)) }
    }
    @Test fun threeDualSwordsAreVisuallyDistinctAndPoisonKindsDiffer() {
        assertEquals(3, listOf("sange_and_yasha", "kaya_and_sange", "yasha_and_kaya").map { ItemIconCatalog.explicit(it) }.distinct().size)
        assertNotEquals(BattleStatusCatalog.icon(StatusKind.NORMAL_POISON), BattleStatusCatalog.icon(StatusKind.VIRULENT_POISON))
    }
    @Test fun typedStatusesContainTimersAndIndependentControlStates() {
        val fighter = Fighter("phantom_assassin", 100.0, 100.0, 20.0, 100.0, 10.0, 100.0, 1.7, 0.0, .25, 0.0, 0.0,
            ghost = 1.2, criticalReady = 3.0, control = ActionControlState(stunned = true, silenced = true, disarmed = true))
        val states = BattleStatusCatalog.entries(fighter, 20)
        assertTrue(states.map { it.kind }.containsAll(listOf(StatusKind.GHOST, StatusKind.CRITICAL_READY, StatusKind.STUN, StatusKind.SILENCE, StatusKind.DISARMED)))
        assertEquals("2s", states.single { it.kind == StatusKind.GHOST }.badge)
        assertEquals(states.size, states.map { it.key }.distinct().size)
    }
}
