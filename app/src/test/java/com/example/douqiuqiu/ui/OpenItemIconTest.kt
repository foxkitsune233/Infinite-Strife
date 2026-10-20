package com.example.douqiuqiu.ui

import com.example.douqiuqiu.content.EquipmentCatalog
import com.example.douqiuqiu.inventory.*
import com.example.douqiuqiu.loot.ValuableLootCatalog
import com.example.douqiuqiu.ui.components.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class OpenItemIconTest {
    private val assets get() = listOf(File("src/main/assets"), File("app/src/main/assets"))
        .first { File(it, "licenses/game-icons-credits.txt").isFile }
    @Test fun allGlyphsHaveOpenResources() {
        IconGlyph.entries.forEach { glyph -> assertNotNull(glyph.name, OpenItemIcons.resourceId("test", IconSpec(glyph, 0))) }
    }
    @Test fun everyEquipmentScrollAndCollectibleResolvesToOpenResources() {
        EquipmentCatalog.all.forEach { gear ->
            assertNotNull(gear.id, OpenItemIcons.resourceId(gear.id, ItemIconCatalog.explicit(gear.id)!!))
        }
        ValuableLootCatalog.definitions.forEach { d -> assertNotNull(d.id, OpenItemIcons.resourceId(d.id, ItemIconCatalog.spec(d))) }
        "salve tango faerie_fire cheese essence backpack_fabric skill_shard safe_lock safe_frame".split(' ').forEach { id ->
            assertNotNull(id, OpenItemIcons.resourceId(id, ItemIconCatalog.explicit(id)!!))
        }
    }
    @Test fun licensesAndPerIconCreditsArePackaged() {
        val credits = File(assets, "licenses/game-icons-credits.txt").readText()
        assertTrue(credits.contains("Lorc")); assertTrue(credits.contains("Delapouite"))
        assertTrue(credits.contains("https://creativecommons.org/licenses/by/3.0/"))
        assertTrue(credits.contains("82d948812bfe3f269ef8f731dcdb07b08160edc4"))
        assertTrue(credits.contains("background"))
        assertEquals(63, credits.lineSequence().count { it.startsWith("https://game-icons.net/1x1/") })
        assertTrue(File(assets, "licenses/game-icons-cc-by-3.0.txt").readText().contains("Attribution 3.0 Unported"))
        assertTrue(File(assets, "licenses/game-icons-upstream-license.txt").isFile)
    }
}
